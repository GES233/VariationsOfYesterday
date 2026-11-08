package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Heartbeat pulse bookkeeping (design note 08): a well-spaced pulse opens a
  * finite stagnation-suppression window; a pulse arriving too fast collapses
  * the window, locks the cell into acute `BurnoutNoCycle` overload, and
  * spikes tolerance so later pulses shelter for less time. All transitions
  * are pure functions of simulation time.
  */
class PulseToleranceSpec:
  private val p = PulseToleranceParam()

  @Test def `a first pulse opens the full suppression window`(): Unit =
    val (next, verdict) = PulseToleranceState.zero.onPulse(100.0, p)
    verdict match
      case PulseStrikeVerdict.Suppressed(window, tol) =>
        assertEquals(p.suppressionWindow, window, 1e-12, "fresh land answers with the full window")
        assertEquals(p.normalToleranceStep, tol, 1e-12)
      case other => fail(s"first pulse must suppress, got $other")
    assertTrue(next.isSuppressedAt(100.0))
    assertTrue(next.isSuppressedAt(100.0 + p.suppressionWindow - 0.01))
    assertFalse(
      next.isSuppressedAt(100.0 + p.suppressionWindow + 0.01),
      "the heartbeat window must expire"
    )

  @Test def `a pulse inside the safe interval overloads the cell`(): Unit =
    val (afterFirst, _) = PulseToleranceState.zero.onPulse(100.0, p)
    val dt = p.minSafeInterval - 1.0
    val (afterSecond, verdict) = afterFirst.onPulse(100.0 + dt, p)
    verdict match
      case PulseStrikeVerdict.Overloaded(tol, duration) =>
        assertEquals(
          p.normalToleranceStep * Math.exp(-dt / p.toleranceDecayTau) + p.overpulseToleranceStep,
          tol,
          1e-9,
          "overload spikes the (decayed) tolerance by the overpulse step"
        )
        assertEquals(p.overloadBurnoutDuration, duration, 1e-12)
      case other => fail(s"a fast second pulse must overload, got $other")
    assertFalse(
      afterSecond.isSuppressedAt(101.0),
      "overload collapses the active suppression window"
    )
    assertTrue(afterSecond.isOverloadedAt(100.0 + p.minSafeInterval - 0.5))
    assertFalse(
      afterSecond.isOverloadedAt(100.0 + p.minSafeInterval + p.overloadBurnoutDuration + 1.0),
      "the acute overload lockout must expire"
    )

  @Test def `a well-spaced pulse refreshes the window`(): Unit =
    val (afterFirst, _) = PulseToleranceState.zero.onPulse(100.0, p)
    val t2 = 100.0 + p.minSafeInterval + 4.0
    val (afterSecond, verdict) = afterFirst.onPulse(t2, p)
    assertTrue(
      verdict.isInstanceOf[PulseStrikeVerdict.Suppressed],
      s"a pulse slower than minSafeInterval must be accepted, got $verdict"
    )
    val window = afterFirst.effectiveWindowAt(t2, p)
    assertTrue(afterSecond.isSuppressedAt(t2 + window - 1.0))
    assertFalse(
      afterSecond.isSuppressedAt(t2 + window + 1.0),
      "the refreshed window ends after the tolerance-shortened duration"
    )

  @Test def `tolerance decays exponentially and shortens later windows`(): Unit =
    val (afterFirst, _) = PulseToleranceState.zero.onPulse(0.0, p)
    val halfLife = p.toleranceDecayTau * Math.log(2.0)
    val tolLater = afterFirst.toleranceAt(halfLife, p)
    assertEquals(
      p.normalToleranceStep / 2.0,
      tolLater,
      1e-9,
      "one half-life of decay must halve the accumulated tolerance"
    )

    // At high tolerance the same well-spaced pulse shelters for less time.
    val worn = PulseToleranceState(lastPulseTime = 0.0, tolerance = 0.8)
    val window = worn.effectiveWindowAt(p.minSafeInterval + 1.0, p)
    assertTrue(
      window < p.suppressionWindow,
      s"accumulated tolerance must shorten the window, got $window"
    )
    assertTrue(window >= 0.0)

  @Test def `saturated tolerance rejects every further pulse`(): Unit =
    val saturated = PulseToleranceState(lastPulseTime = 0.0, tolerance = 1.2)
    // Residual tolerance at t = 1.0 is still ~1.17 > invertTolerance (1.0).
    assertTrue(saturated.wouldOverloadAt(1.0, p), "saturated land refuses fast pulses")
    val (next, verdict) = saturated.onPulse(1.0, p)
    assertTrue(verdict.isInstanceOf[PulseStrikeVerdict.Overloaded])
    assertTrue(
      next.tolerance <= p.maxTolerance,
      "tolerance is hard-capped so the decay back is bounded"
    )
    assertEquals(
      0.0,
      saturated.effectiveWindowAt(1.0, p),
      1e-12,
      "at or above the invert threshold a pulse shelters nothing"
    )

  @Test def `the zero state is inert`(): Unit =
    val z = PulseToleranceState.zero
    assertFalse(z.isSuppressedAt(0.0))
    assertFalse(z.isOverloadedAt(0.0))
    assertEquals(0.0, z.toleranceAt(12345.0, p), 1e-12)
    assertEquals(p.suppressionWindow, z.effectiveWindowAt(0.0, p), 1e-12)
    assertFalse(z.wouldOverloadAt(0.0, p))
