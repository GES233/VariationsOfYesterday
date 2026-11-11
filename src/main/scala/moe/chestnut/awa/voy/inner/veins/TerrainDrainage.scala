package moe.chestnut.awa.voy.inner.veins

/** Bounded D8 survey. Missing neighbours remain open survey boundaries;
  * flats are deliberately unresolved rather than assigned invented rivers.
  */
object TerrainDrainage:
  enum EndKind:
    case OpenBoundary, Flat, Depression

  case class Outlet(cell: CellCoord, kind: EndKind)
  case class Survey(
      downstream: Map[CellCoord, CellCoord],
      accumulation: Map[CellCoord, Int],
      outlets: Map[CellCoord, Outlet]
  )

  // Clockwise from north; stable ordering resolves equal slopes.
  private val offsets = Vector(
    (0, -1), (1, -1), (1, 0), (1, 1),
    (0, 1), (-1, 1), (-1, 0), (-1, -1)
  )

  def survey(heights: Map[CellCoord, Double]): Survey =
    require(heights.values.forall(_.isFinite), "Terrain heights must be finite")
    val downstream = scala.collection.mutable.Map.empty[CellCoord, CellCoord]
    val ends = scala.collection.mutable.Map.empty[CellCoord, Outlet]
    for (cell, elevation) <- heights do
      val neighbours = offsets.map { (dx, dz) =>
        (CellCoord(cell.x + dx, cell.z + dz), math.sqrt(dx * dx + dz * dz))
      }
      if neighbours.exists((next, _) => !heights.contains(next)) then
        ends(cell) = Outlet(cell, EndKind.OpenBoundary)
      else
        val slopes = neighbours.map { (next, distance) =>
          (next, (elevation - heights(next)) / distance)
        }
        val (next, slope) = slopes.maxBy(_._2)
        if slope > 0 then downstream(cell) = next
        else
          val kind = if neighbours.exists((n, _) => heights(n) == elevation)
            then EndKind.Flat else EndKind.Depression
          ends(cell) = Outlet(cell, kind)

    val descending = heights.keys.toVector.sortBy(c => (-heights(c), c.x, c.z))
    val accumulation = scala.collection.mutable.Map.from(heights.keys.map(_ -> 1))
    for cell <- descending do
      downstream.get(cell).foreach { next =>
        accumulation(next) += accumulation(cell)
      }
    val outlets = scala.collection.mutable.Map.from(ends)
    for cell <- descending.reverse do
      downstream.get(cell).foreach(next => outlets(cell) = outlets(next))
    Survey(downstream.toMap, accumulation.toMap, outlets.toMap)
