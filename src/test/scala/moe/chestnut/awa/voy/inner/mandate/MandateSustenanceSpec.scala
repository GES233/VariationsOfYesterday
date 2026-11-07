package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.inner.event.EventMsg

/** Fisher-KPP sustenance dynamics (design note 06 §7.1, parameters measured
  * 2026-11-07): logistic regrowth, acid burn above the stress threshold,
  * lattice diffusion; `M = 0` absorbing. Times are in simulation units
  * (1 unit = 1 second at the game tick).
  */
class MandateSustenanceSpec:
  private val fast = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
  private val dt = 0.05

  /** The tuned gameplay values (`MandateRuntime.buildGrid`); gamma frozen so
    * the stress level set at construction stays put.
    */
  private val mycelial =
    StressParam(gamma = 0.0, sustenanceGrowth = 1e-3, sustenanceBurn = 4e-3)

  private def mkCell(stress: Double): MandateState =
    val cell = MandateState(MandateCurrent(-1.2, -0.6, 0.0, 0.0), fast, mycelial)
    cell.injectStress(stress) // clamped to [0, stressMax]
    cell

  private def stepHours(cell: MandateState, hours: Double): Unit =
    val steps = (hours * 3600 / dt).toInt
    for _ <- 1 to steps do cell.step(dt)

  @Test def `acid burn extinguishes a saturated cell within hours, and extinction is absorbing`(): Unit =
    val cell = mkCell(1.0) // gamma = 0: the acid load stays pinned
    var t = 0.0
    while cell.sustenance > 0.0 && t < 12.0 * 3600 do
      cell.step(dt)
      t += dt
    assertEquals(0.0, cell.sustenance, 1e-12, "sustained acid must extinguish the mycelium")
    assertTrue(t > 1.0 * 3600, s"extinction must take hours, took ${t / 3600.0} h")
    // Absorbing: no regrowth even with the logistic term switched on.
    stepHours(cell, 2.0)
    assertEquals(0.0, cell.sustenance, 1e-12, "M = 0 must be absorbing without re-inoculation")

  @Test def `stress below the toxicity threshold never burns`(): Unit =
    val cell = mkCell(0.5) // below s_T = 0.6: tired, not toxic
    stepHours(cell, 12.0)
    assertEquals(1.0, cell.sustenance, 1e-12, "tired land is not toxic land")

  @Test def `a seeded patch regrows logistically within hours`(): Unit =
    val cell = mkCell(0.0)
    cell.sustenance = 0.05 // narrative re-inoculation seed
    stepHours(cell, 4.0)
    assertTrue(
      cell.sustenance > 0.9,
      s"a re-inoculated patch must regrow, M=${cell.sustenance}"
    )

  @Test def `chronic stress settles at a partial equilibrium`(): Unit =
    val cell = mkCell(0.7)
    stepHours(cell, 24.0)
    // Measured M* = 0.6 at s = 0.7: chronic semi-incapacitation, not death.
    assertEquals(
      0.6,
      cell.sustenance,
      0.05,
      s"chronic stress must settle mid-way, M=${cell.sustenance}"
    )

  @Test def `lattice diffusion re-seeds a small dead patch from its rim`(): Unit =
    val cells = (0 to 4).map(i => CellCoord(i, 0) -> mkCell(0.0)).toMap
    val grid = MandateGrid(
      cells,
      (0 to 3).map(i => LatticeEdge(CellCoord(i, 0), CellCoord(i + 1, 0), diffusion = 0.002))
    )
    cells(CellCoord(2, 0)).sustenance = 0.0
    val steps = (2.0 * 3600 / dt).toInt
    for _ <- 1 to steps do grid.step(dt)
    assertTrue(
      cells(CellCoord(2, 0)).sustenance > 0.3,
      s"the rim must re-seed the dead cell, M=${cells(CellCoord(2, 0)).sustenance}"
    )

  @Test def `a dead cell without living neighbours stays dead`(): Unit =
    val cell = mkCell(0.0)
    cell.sustenance = 0.0
    stepHours(cell, 4.0)
    assertEquals(0.0, cell.sustenance, 1e-12, "isolated extinction cannot self-seed")

  @Test def `SetSustenance writes the level directly, clamped to the unit interval`(): Unit =
    val cell = mkCell(0.0)
    val grid = MandateGrid(Map(CellCoord(0, 0) -> cell), Nil)
    MandateAgent.applyEvent(grid, EventMsg.SetSustenance(CellCoord(0, 0), 0.3))
    assertEquals(0.3, cell.sustenance, 1e-12)
    MandateAgent.applyEvent(grid, EventMsg.SetSustenance(CellCoord(0, 0), 2.0))
    assertEquals(1.0, cell.sustenance, 1e-12, "levels clamp to [0, 1]")
    MandateAgent.applyEvent(grid, EventMsg.SetSustenance(CellCoord(0, 0), -1.0))
    assertEquals(0.0, cell.sustenance, 1e-12, "levels clamp to [0, 1]")
