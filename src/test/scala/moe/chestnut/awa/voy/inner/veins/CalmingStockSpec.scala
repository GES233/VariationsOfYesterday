package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Stock arithmetic of the baseline calming `c0` (design note 11 ruling 11):
  * sources trickle toward a contract-anchored equilibrium (0.03 per weight
  * unit), the stock leaks with a session-scale time constant, and a broken
  * source fades instead of snapping to zero.
  */
class CalmingStockSpec:
  private val p = CalmingParam()

  @Test def `one source trickles toward the contract anchor`(): Unit =
    // Long-run equilibrium of one weight unit is c0Intact = 0.03.
    var c0 = 0.0
    var t = 0.0
    while t < 6 * p.tau do
      c0 = CalmingStock.step(c0, 1.0, 60.0, p)
      t += 60.0
    assertEquals(0.03, c0, 1e-3, "one intact bell reproduces the old anchor as a steady state")

  @Test def `no source leaks toward zero`(): Unit =
    var c0 = 0.03
    var t = 0.0
    while t < 6 * p.tau do
      c0 = CalmingStock.step(c0, 0.0, 60.0, p)
      t += 60.0
    assertTrue(c0 < 1e-3, s"the stock must leak away, got $c0")

  @Test def `breaking one of two sources halves the trickle instead of zeroing it`(): Unit =
    // Regression for the reported bug: two bells, one removed.
    var c0 = 0.0
    var t = 0.0
    while t < 4 * p.tau do
      c0 = CalmingStock.step(c0, 2.0, 60.0, p)
      t += 60.0
    c0 = CalmingStock.step(c0, 1.0, 60.0, p)
    assertTrue(c0 > 0.03, s"strength must fall gradually, not to zero, got $c0")
    val afterOneStep = CalmingStock.step(c0, 1.0, 60.0, p)
    assertTrue(afterOneStep > 0.03 && afterOneStep < c0, s"one source still trickles, got $afterOneStep")

  @Test def `two sources cap out above the contract anchor`(): Unit =
    var c0 = 0.0
    var t = 0.0
    while t < 8 * p.tau do
      c0 = CalmingStock.step(c0, 2.0, 60.0, p)
      t += 60.0
    assertEquals(p.cap, c0, 1e-9, "extra sources have diminishing returns and cannot overshoot the cap")

  @Test def `the ritual dose tops up but never overshoots the cap`(): Unit =
    assertEquals(0.008, CalmingStock.dose(0.0, p), 1e-12)
    assertEquals(p.cap, CalmingStock.dose(p.cap, p), 1e-12)
    assertEquals(0.038, CalmingStock.dose(0.03, p), 1e-12)
    assertEquals(0.0, CalmingStock.dose(-1.0, p), 1e-12, "the stock is never negative")

  @Test def `the time constant is the session-scale memory`(): Unit =
    assertEquals(2700.0, p.tau, 1e-12)
    assertEquals(0.03 / 2700.0, p.tricklePerWeight, 1e-18)
    assertEquals(0.04, p.cap, 1e-12)
