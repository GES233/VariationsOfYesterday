package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The three discrete pulse mechanisms (design note 08) after the batch-1
  * decoupling: a shelter window, a tolerance book and an acute overload
  * lockout are separate facts with one explicit transition each.
  *
  * The pre-decoupling assertions are kept in meaning (a well-spaced pulse
  * opens a finite window; a too-fast pulse collapses it into a lockout and
  * spikes tolerance; tolerance decays and shortens later windows). The new
  * cases pin the rules that used to be implicit in one fused label.
  */
class PulseLedgerSpec:
  private val p = PulseToleranceParam()

  @Test def `a first pulse opens the full shelter window`(): Unit =
    val (next, settlement) = PulseLedger.zero.onCountedPulse(100.0, p)
    settlement match
      case PulseSettlement.Sheltered(window, tol) =>
        assertEquals(p.suppressionWindow, window, 1e-12, "fresh land answers with the full window")
        assertEquals(p.normalToleranceStep, tol, 1e-12)
      case other => fail(s"first pulse must shelter, got $other")
    assertTrue(next.shelteredAt(100.0))
    assertTrue(next.shelteredAt(100.0 + p.suppressionWindow - 0.01))
    assertFalse(
      next.shelteredAt(100.0 + p.suppressionWindow + 0.01),
      "the shelter window must expire"
    )

  @Test def `a pulse inside the safe interval is refused into an overload lockout`(): Unit =
    val (afterFirst, _) = PulseLedger.zero.onCountedPulse(100.0, p)
    val dt = p.minSafeInterval - 1.0
    val (afterSecond, settlement) = afterFirst.onCountedPulse(100.0 + dt, p)
    settlement match
      case PulseSettlement.Overloaded(cause, duration, tol) =>
        assertEquals(
          OverloadCause.PulseTooSoon,
          cause,
          "the cadence guard is what refused the pulse"
        )
        assertEquals(
          p.normalToleranceStep * Math.exp(-dt / p.toleranceDecayTau) + p.overpulseToleranceStep,
          tol,
          1e-9,
          "overload spikes the (decayed) tolerance by the overpulse step"
        )
        assertEquals(p.overloadBurnoutDuration, duration, 1e-12)
      case other => fail(s"a fast second pulse must overload, got $other")
    assertEquals(
      ShelterWindow.closed,
      afterSecond.shelter,
      "the transition itself collapses the open window, so no query has to mask it"
    )
    assertFalse(afterSecond.shelteredAt(101.0))
    assertTrue(afterSecond.overloadedAt(100.0 + p.minSafeInterval - 0.5))
    assertFalse(
      afterSecond.overloadedAt(100.0 + p.minSafeInterval + p.overloadBurnoutDuration + 1.0),
      "the acute overload lockout must expire"
    )

  @Test def `a well-spaced pulse refreshes the window`(): Unit =
    val (afterFirst, _) = PulseLedger.zero.onCountedPulse(100.0, p)
    val t2 = 100.0 + p.minSafeInterval + 4.0
    val (afterSecond, settlement) = afterFirst.onCountedPulse(t2, p)
    assertTrue(
      settlement.isInstanceOf[PulseSettlement.Sheltered],
      s"a pulse slower than minSafeInterval must be accepted, got $settlement"
    )
    val window = afterFirst.effectiveWindowAt(t2, p)
    assertTrue(afterSecond.shelteredAt(t2 + window - 1.0))
    assertFalse(
      afterSecond.shelteredAt(t2 + window + 1.0),
      "the refreshed window ends after the tolerance-shortened duration"
    )

  @Test def `tolerance decays exponentially and shortens later windows`(): Unit =
    val (afterFirst, _) = PulseLedger.zero.onCountedPulse(0.0, p)
    val halfLife = p.toleranceDecayTau * Math.log(2.0)
    val tolLater = afterFirst.toleranceAt(halfLife, p)
    assertEquals(
      p.normalToleranceStep / 2.0,
      tolLater,
      1e-9,
      "one half-life of decay must halve the accumulated tolerance"
    )

    // At high tolerance the same well-spaced pulse shelters for less time.
    val worn = PulseLedger(tolerance = ToleranceBook(amount = 0.8, lastPulseTime = 0.0))
    val window = worn.effectiveWindowAt(p.minSafeInterval + 1.0, p)
    assertTrue(
      window < p.suppressionWindow,
      s"accumulated tolerance must shorten the window, got $window"
    )
    assertTrue(window >= 0.0)

  @Test def `saturated tolerance refuses every further pulse`(): Unit =
    val saturated = PulseLedger(tolerance = ToleranceBook(amount = 1.2, lastPulseTime = 0.0))
    // Residual tolerance at t = 1.0 is still ~1.17 > invertTolerance (1.0).
    assertTrue(saturated.wouldOverloadAt(1.0, p), "saturated land refuses fast pulses")
    val (next, settlement) = saturated.onCountedPulse(1.0, p)
    assertTrue(settlement.isInstanceOf[PulseSettlement.Overloaded])
    assertTrue(
      next.tolerance.amount <= p.maxTolerance,
      "tolerance is hard-capped so the decay back is bounded"
    )
    assertEquals(
      0.0,
      saturated.effectiveWindowAt(1.0, p),
      1e-12,
      "at or above the invert threshold a pulse shelters nothing"
    )

  @Test def `the refusal names the guard that fired, in evaluation order`(): Unit =
    // 1. Cadence: a pulse inside minSafeInterval is refused as too soon, even
    //    when the land is also saturated (the cadence guard is checked first).
    val saturated = PulseLedger(tolerance = ToleranceBook(amount = 1.2, lastPulseTime = 0.0))
    assertEquals(
      Some(OverloadCause.PulseTooSoon),
      saturated.overloadCauseAt(1.0, p),
      "the cadence guard is evaluated before saturation"
    )
    // 2. Saturation: spaced past minSafeInterval, but the decayed load still
    //    sits above the invert threshold (1.5 * exp(-9/45) ≈ 1.23 >= 1.0).
    val loaded = PulseLedger(tolerance = ToleranceBook(amount = 1.5, lastPulseTime = 0.0))
    assertEquals(
      Some(OverloadCause.ToleranceSaturated),
      loaded.overloadCauseAt(9.0, p),
      "the saturation guard names itself"
    )

  // ==== The three mechanisms as separate facts (batch-1 decoupling) ==== //

  @Test def `shelter, tolerance and overload answer independently`(): Unit =
    val windowOnly = PulseLedger(shelter = ShelterWindow(50.0))
    assertTrue(windowOnly.shelteredAt(10.0))
    assertFalse(windowOnly.overloadedAt(10.0))
    assertEquals(0.0, windowOnly.toleranceAt(10.0, p), 1e-12, "a bare window carries no tolerance")

    val toleranceOnly = PulseLedger(tolerance = ToleranceBook(amount = 0.5, lastPulseTime = 0.0))
    assertFalse(toleranceOnly.shelteredAt(1.0))
    assertFalse(toleranceOnly.overloadedAt(1.0))
    assertTrue(toleranceOnly.toleranceAt(1.0, p) > 0.0)

    val lockedOnly = PulseLedger(overload = OverloadLock(50.0))
    assertTrue(lockedOnly.overloadedAt(10.0))
    assertFalse(lockedOnly.shelteredAt(10.0))

  @Test def `a hand-built ledger with a stale window still never reads as sheltered while locked`(): Unit =
    // The transition closes the window, so this state cannot arise from
    // `onCountedPulse`; the query rule is kept explicit and documented so a
    // restored or hand-built state cannot grant shelter during a lockout.
    val inconsistent = PulseLedger(shelter = ShelterWindow(100.0), overload = OverloadLock(50.0))
    assertFalse(inconsistent.shelteredAt(10.0), "the active lockout wins over a stale open window")
    assertEquals(0.0, inconsistent.remainingShelterAt(10.0), 1e-12)

  @Test def `an already-locked cell reports AlreadyLocked when the cadence guard no longer fires`(): Unit =
    // With the stock parameters the lockout duration equals minSafeInterval,
    // so an expired cadence always coincides with an expired lockout. A
    // longer lockout makes the third guard reachable, and the cause has to be
    // named then.
    val longLock = p.copy(overloadBurnoutDuration = 20.0)
    val (first, _) = PulseLedger.zero.onCountedPulse(0.0, longLock)
    val (locked, lockedSettlement) = first.onCountedPulse(1.0, longLock)
    assertEquals(
      Some(OverloadCause.PulseTooSoon),
      lockedSettlement match
        case PulseSettlement.Overloaded(cause, _, _) => Some(cause)
        case _                                       => None
    )
    val (rearmed, settlement) = locked.onCountedPulse(10.0, longLock)
    settlement match
      case PulseSettlement.Overloaded(cause, _, _) =>
        assertEquals(OverloadCause.AlreadyLocked, cause, "a pulse inside a lockout re-arms it")
      case other => fail(s"a pulse inside an active lockout must be refused, got $other")
    assertEquals(OverloadLock(30.0), rearmed.overload, "the re-armed lockout starts at the pulse time")
    assertEquals(ShelterWindow.closed, rearmed.shelter)

  @Test def `an accepted pulse clears an expired lockout explicitly`(): Unit =
    val expired =
      PulseLedger(
        shelter = ShelterWindow.closed,
        overload = OverloadLock(5.0)
      )
    val (next, settlement) = expired.onCountedPulse(10.0, p)
    assertTrue(settlement.isInstanceOf[PulseSettlement.Sheltered])
    assertEquals(OverloadLock.closed, next.overload, "acceptance closes the lockout as part of the transition")
    assertTrue(next.shelteredAt(10.0))

  @Test def `the zero ledger is inert`(): Unit =
    val z = PulseLedger.zero
    assertFalse(z.shelteredAt(0.0))
    assertFalse(z.overloadedAt(0.0))
    assertEquals(0.0, z.toleranceAt(12345.0, p), 1e-12)
    assertEquals(p.suppressionWindow, z.effectiveWindowAt(0.0, p), 1e-12)
    assertFalse(z.wouldOverloadAt(0.0, p))
