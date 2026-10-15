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
  private val CardinalOffsets: Seq[(Int, Int)] = Seq((1, 0), (0, 1))
  private val D8Offsets: Seq[(Int, Int)] = for
    dx <- -1 to 1
    dz <- -1 to 1
    if !(dx == 0 && dz == 0)
  yield (dx, dz)

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
      (dx, dz) <- CardinalOffsets
      neighbor = CellCoord(coord.x + dx, coord.z + dz)
      if cells.contains(neighbor)
    yield LatticeEdge(coord, neighbor, diffusion = diffusion)
    MandateGrid(cells, edges)

  /** Derive D8 steepest-descent receivers from a cell elevation map
    * (design draft §2.1, §2.4).
    *
    * Each cell examines its 8-connected neighbours present in `elevations`
    * (diagonal distance weighted by sqrt(2)) and selects the neighbour with
    * the largest strictly positive downhill slope `(e_from - e_to) / dist`.
    * Local minima and flat cells emit no receiver. Ties break deterministically
    * by D8 offset order.
    *
    * @return map from uphill cell to `(downhillReceiver, slope)`
    */
  def d8Flow(
      elevations: Map[CellCoord, Double]
  ): Map[CellCoord, (CellCoord, Double)] =
    elevations.flatMap { case (coord, elev) =>
      var bestReceiver: Option[CellCoord] = None
      var bestSlope = 0.0
      for (dx, dz) <- D8Offsets do
        val neighbor = CellCoord(coord.x + dx, coord.z + dz)
        elevations.get(neighbor).foreach { neighborElev =>
          val dist = if dx == 0 || dz == 0 then 1.0 else Math.sqrt(2.0)
          val slope = (elev - neighborElev) / dist
          if slope > bestSlope then
            bestSlope = slope
            bestReceiver = Some(neighbor)
        }
      bestReceiver.map(target => coord -> (target, bestSlope))
    }

  /** Rectangular grid combining 4-neighbour symmetric diffusion with D8
    * downhill advection edges derived from a cell elevation function.
    *
    * @param advection base directed transport rate along each D8 edge (or
    *   rate per unit slope when `scaleBySlope` is true)
    * @param scaleBySlope when true, each D8 edge rate is `min(maxAdvection,
    *   advection * slope)`; when false, every downhill D8 edge carries a
    *   uniform `advection` rate
    */
  def fromElevation(
      width: Int,
      height: Int,
      diffusion: Double,
      advection: Double,
      elevation: CellCoord => Double,
      mkState: CellCoord => MandateState,
      scaleBySlope: Boolean = false,
      maxAdvection: Double = 0.25
  ): MandateGrid =
    val coords = for
      x <- 0 until width
      z <- 0 until height
    yield CellCoord(x, z)
    val cells = coords.map(c => c -> mkState(c)).toMap
    val elevations = coords.map(c => c -> elevation(c)).toMap
    val diffusionEdges = for
      coord <- coords
      (dx, dz) <- CardinalOffsets
      neighbor = CellCoord(coord.x + dx, coord.z + dz)
      if cells.contains(neighbor)
    yield LatticeEdge(coord, neighbor, diffusion = diffusion)
    val advectionEdges = d8Flow(elevations).toSeq
      .sortBy { case (from, _) => (from.x, from.z) }
      .map { case (from, (to, slope)) =>
        val rate =
          if scaleBySlope then Math.min(maxAdvection, advection * slope)
          else advection
        LatticeEdge(from, to, advection = rate)
      }
    MandateGrid(cells, diffusionEdges ++ advectionEdges)

