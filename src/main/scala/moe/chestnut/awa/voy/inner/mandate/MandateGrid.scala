package moe.chestnut.awa.voy.inner.mandate

/** A cell coordinate on the stress lattice. Chunk-grained when wired into
  * the game; here it is an abstract integer pair.
  */
case class CellCoord(x: Int, z: Int)

/** Spatial coupling between two lattice cells.
  *
  * `diffusion` is symmetric (stress flows from high to low).
  * `advection` is directed `from -> to` (rivers carry stress downstream
  * only). Transport terms are added raw to ds/dt (they already carry the
  * transport coefficient); local dynamics stay scaled by gamma.
  *
  * Stability note: explicit in time, so keep coefficient * dt well below
  * 1/2 per edge (CFL).
  */
case class LatticeEdge(
    from: CellCoord,
    to: CellCoord,
    diffusion: Double = 0.0,
    advection: Double = 0.0
)

/** The spatial stress lattice: one MandateState per cell, coupled by
  * diffusion/advection edges. Transport fluxes are computed from pre-step
  * states (operator splitting), then every cell is stepped.
  *
  * Topology is dynamic (topology events, design draft §1.4), but changes are
  * rare — worldgen registration and player wiring, never per-tick — so they
  * rebuild the immutable cell/edge views wholesale instead of putting a
  * mutable structure on the hot path. All mutation happens on the actor
  * thread via `MandateAgent.applyEvent`.
  */
class MandateGrid(
    initialCells: Map[CellCoord, MandateState],
    initialEdges: Seq[LatticeEdge]
):
  // @volatile so game-thread readers see topology changes made on the actor
  // thread without an extra handshake.
  @volatile private var cellsV: Map[CellCoord, MandateState] = initialCells
  @volatile private var edgesV: Seq[LatticeEdge] = initialEdges

  def cells: Map[CellCoord, MandateState] = cellsV
  def edges: Seq[LatticeEdge] = edgesV

  /** Insert a cell, or fully reset the one already at `coord`. Transport
    * edges are kept. */
  def registerCell(coord: CellCoord, state: MandateState): Unit =
    cellsV = cellsV.updated(coord, state)

  /** Remove a cell together with every edge incident to it. */
  def removeCell(coord: CellCoord): Unit =
    cellsV = cellsV - coord
    edgesV = edgesV.filterNot(e => e.from == coord || e.to == coord)

  /** Insert or replace the edge `from -> to` (keyed by the endpoint pair). */
  def upsertEdge(edge: LatticeEdge): Unit =
    edgesV = edgesV.filterNot(e => e.from == edge.from && e.to == edge.to) :+ edge

  /** Remove the edge `from -> to`, if present. */
  def removeEdge(from: CellCoord, to: CellCoord): Unit =
    edgesV = edgesV.filterNot(e => e.from == from && e.to == to)
  def step(dt: Double): Unit =
    cells.values.foreach { c =>
      c.stressFlux = 0.0
      c.sustenanceFlux = 0.0
    }
    for edge <- edges do
      val a = cells(edge.from)
      val b = cells(edge.to)
      val sa = a.getState.stress
      val sb = b.getState.stress
      val diff = edge.diffusion * (sb - sa)
      a.stressFlux += diff - edge.advection * sa
      b.stressFlux += -diff + edge.advection * sa
      // Sustenance diffuses along the same terrain continuity as stress
      // (mycelium follows soil and water), only far slower; advection
      // carries no living tissue.
      val mdiff = edge.diffusion * MandateGrid.SustenanceMobility * (b.sustenance - a.sustenance)
      a.sustenanceFlux += mdiff
      b.sustenanceFlux += -mdiff
    cells.values.foreach(_.step(dt))

  def totalStress: Double = cells.values.map(_.getState.stress).sum

object MandateGrid:
  /** Sustenance diffusivity relative to the stress-edge coefficient: the
    * mycelium creeps along the same terrain continuity as the acid, only
    * far slower. With the game's edge diffusion 0.002 this gives an
    * effective D = 8e-5 — a single dead cell is re-seeded from its rim in
    * ~20 minutes, a three-cell patch in ~an hour (measured 2026-11-07). */
  val SustenanceMobility: Double = 0.04

  /** Rectangular grid with 4-neighbour symmetric diffusion. */
  def rectangular(
      width: Int,
      height: Int,
      diffusion: Double,
      mkState: CellCoord => MandateState
  ): MandateGrid =
    val cells = (for
      x <- 0 until width
      z <- 0 until height
      coord = CellCoord(x, z)
    yield coord -> mkState(coord)).toMap
    val edges = for
      coord <- cells.keys.toSeq
      (dx, dz) <- Seq((1, 0), (0, 1))
      neighbor = CellCoord(coord.x + dx, coord.z + dz)
      if cells.contains(neighbor)
    yield LatticeEdge(coord, neighbor, diffusion = diffusion)
    MandateGrid(cells, edges)
