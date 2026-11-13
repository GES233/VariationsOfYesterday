package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Unit tests for [[PulseStamp]] and [[PulseParam]]: a well-spaced pulse opens
  * a fixed shelter window and starts the cooldown; a pulse arriving inside the
  * cooldown collapses the shelter window and refreshes the cooldown.
  */
class PulseLedgerSpec:
  private val p = PulseParam()

  @Test def `a first pulse opens the full shelter window and starts the cooldown`(): Unit =
    val (next, settlement) = PulseStamp.zero.onCountedPulse(100.0, p)
    assertEquals(PulseSettlement.Sheltered(p.shelterWindow), settlement)
    assertTrue(next.isShelteredAt(100.0))
    assertTrue(next.isShelteredAt(100.0 + p.shelterWindow - 0.01))
    assertFalse(
      next.isShelteredAt(100.0 + p.shelterWindow + 0.01),
      "the shelter window must expire"
    )
    assertTrue(next.isInCooldownAt(100.0 + p.minInterval - 0.01))
    assertFalse(next.isInCooldownAt(100.0 + p.minInterval + 0.01))

  @Test def `a pulse inside the cooldown collapses the shelter window and refreshes the cooldown`(): Unit =
    val (afterFirst, _) = PulseStamp.zero.onCountedPulse(100.0, p)
    val dt = p.minInterval - 1.0
    val (afterSecond, settlement) = afterFirst.onCountedPulse(100.0 + dt, p)
    assertEquals(PulseSettlement.CooldownRefused(p.minInterval), settlement)
    assertFalse(afterSecond.isShelteredAt(100.0 + dt), "striking in cooldown collapses the shelter window")
    assertEquals(0.0, afterSecond.remainingShelterAt(100.0 + dt), 1e-12)
    assertTrue(afterSecond.isInCooldownAt(100.0 + dt + p.minInterval - 0.5))
    assertFalse(
      afterSecond.isInCooldownAt(100.0 + dt + p.minInterval + 0.5),
      "the refreshed cooldown must expire after minInterval"
    )

  @Test def `a well-spaced pulse refreshes the full shelter window`(): Unit =
    val (afterFirst, _) = PulseStamp.zero.onCountedPulse(100.0, p)
    val t2 = 100.0 + p.minInterval + 4.0
    val (afterSecond, settlement) = afterFirst.onCountedPulse(t2, p)
    assertEquals(PulseSettlement.Sheltered(p.shelterWindow), settlement)
    assertTrue(afterSecond.isShelteredAt(t2 + p.shelterWindow - 0.5))
    assertFalse(afterSecond.isShelteredAt(t2 + p.shelterWindow + 0.5))

  @Test def `the zero stamp is inert`(): Unit =
    val z = PulseStamp.zero
    assertFalse(z.isShelteredAt(0.0))
    assertFalse(z.isInCooldownAt(0.0))
    assertEquals(0.0, z.remainingShelterAt(12345.0), 1e-12)
    assertEquals(0.0, z.remainingCooldownAt(12345.0), 1e-12)
