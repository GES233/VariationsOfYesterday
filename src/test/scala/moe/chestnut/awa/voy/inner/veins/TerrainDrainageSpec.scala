package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerrainDrainageSpec:
  private def patch(f: (Int, Int) => Double): Map[CellCoord, Double] =
    (for x <- -2 to 2; z <- -2 to 2 yield CellCoord(x, z) -> f(x, z)).toMap

  @Test def slopeRoutesToOpenBoundaryAndConservesContributingArea(): Unit =
    val heights = patch((x, _) => -x.toDouble)
    val result = TerrainDrainage.survey(heights)
    assertEquals(CellCoord(1, 0), result.downstream(CellCoord(0, 0)))
    assertEquals(TerrainDrainage.Outlet(CellCoord(2, 0), TerrainDrainage.EndKind.OpenBoundary), result.outlets(CellCoord(0, 0)))
    assertEquals(4, result.accumulation(CellCoord(2, 0)))
    val terminals = heights.keySet -- result.downstream.keySet
    assertEquals(heights.size, terminals.toSeq.map(result.accumulation).sum)

  @Test def diagonalUsesDistanceRatherThanLargestDrop(): Unit =
    val heights = patch((_, _) => 10.0)
      .updated(CellCoord(1, 0), 8.0)
      .updated(CellCoord(1, 1), 7.5)
    assertEquals(CellCoord(1, 0), TerrainDrainage.survey(heights).downstream(CellCoord(0, 0)))

  @Test def flatsAndDepressionsRemainDistinct(): Unit =
    val flat = patch((_, _) => 10.0)
    assertEquals(TerrainDrainage.EndKind.Flat, TerrainDrainage.survey(flat).outlets(CellCoord(0, 0)).kind)
    val bowl = patch((x, z) => (x * x + z * z).toDouble)
    val result = TerrainDrainage.survey(bowl)
    assertEquals(TerrainDrainage.EndKind.Depression, result.outlets(CellCoord(0, 0)).kind)
    assertEquals(9, result.accumulation(CellCoord(0, 0)))

  @Test def missingNeighbourPreventsFalseClosedBasin(): Unit =
    val heights = patch((x, z) => (x * x + z * z).toDouble) - CellCoord(1, 1)
    val result = TerrainDrainage.survey(heights)
    assertEquals(TerrainDrainage.EndKind.OpenBoundary, result.outlets(CellCoord(0, 0)).kind)
    assertFalse(result.downstream.contains(CellCoord(0, 0)))
    assertTrue(TerrainDrainage.survey(Map.empty).outlets.isEmpty)
