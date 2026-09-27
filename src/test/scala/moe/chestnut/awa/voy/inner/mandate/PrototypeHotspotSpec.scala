package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Regression guard for the in-game observation that the prototype hotspot
  * stays subcritical: the calming annulus plus spatial spreading cap the
  * peak stress far below the 0-D ignition threshold (design draft section
  * 1.1 numbers are single-cell; the lattice suppresses ignition). The finite
  * test grid has a closed boundary: only mandate cells participate in the
  * transport graph.
  */
class PrototypeHotspotSpec:
  private val dt = 0.05

  @Test def `calming annulus keeps the prototype hotspot subcritical`(): Unit =
    val half = 4 // 9x9 cut around the hotspot, roomy enough for the annulus
    val cells = (for
      x <- 0 to half * 2
      z <- 0 to half * 2
      coord = CellCoord(x, z)
    yield
      val dx = x - half
      val dz = z - half
      val distance = Math.sqrt(dx * dx + dz * dz)
      val cell = MandateState(
        state =
          MandateCurrent(0.0, 0.0, Math.max(0.0, 0.18 - distance * 0.004), 0.0),
        param = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
        time_step = dt
      )
      cell.disturbance = if distance < 2.0 then 0.08 else 0.002
      cell.baselineCalming = if distance < 5.0 then 0.03 else 0.0
      coord -> cell).toMap

    val edges = for
      coord <- cells.keys.toSeq.sortBy(c => (c.x, c.z))
      (dx, dz) <- Seq((1, 0), (0, 1))
      neighbour = CellCoord(coord.x + dx, coord.z + dz)
      if cells.contains(neighbour)
    yield LatticeEdge(coord, neighbour, diffusion = 0.002)
    val grid = MandateGrid(cells, edges)
    val center = CellCoord(half, half)

    var maxStress = 0.0
    var maxAbsV = 0.0
    for _ <- 1 to 18000 do
      grid.step(dt)
      maxStress = Math.max(maxStress, cells(center).getState.stress)
      for (_, cell) <- cells do
        maxAbsV = Math.max(maxAbsV, Math.abs(cell.getState.dissonance))

    // 15 minutes of simulation time: the seed grows, but the annulus caps
    // the peak and the fast subsystem never fires.
    assertTrue(
      maxStress > 0.19,
      s"hotspot should grow beyond its 0.18 seed, peaked at $maxStress"
    )
    assertTrue(
      maxStress < 0.30,
      s"annulus must cap the peak below ignition range, got $maxStress"
    )
    assertTrue(
      maxAbsV < 1.8,
      s"hotspot must never spike, got |v| = $maxAbsV"
    )
