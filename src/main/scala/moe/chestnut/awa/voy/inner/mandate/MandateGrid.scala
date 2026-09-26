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
  */
class MandateGrid(
    val cells: Map[CellCoord, MandateState],
    val edges: Seq[LatticeEdge]
):
  def step(dt: Double): Unit =
    cells.values.foreach(_.stressFlux = 0.0)
    for edge <- edges do
      val a = cells(edge.from)
      val b = cells(edge.to)
      val sa = a.getState.stress
      val sb = b.getState.stress
      val diff = edge.diffusion * (sb - sa)
      a.stressFlux += diff - edge.advection * sa
      b.stressFlux += -diff + edge.advection * sa
    cells.values.foreach(_.step(dt))

  def totalStress: Double = cells.values.map(_.getState.stress).sum

object MandateGrid:
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
