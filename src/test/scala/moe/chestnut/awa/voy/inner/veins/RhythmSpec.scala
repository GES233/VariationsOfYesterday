package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Judgment table of the rhythm axis (design note 11 §1.2/§1.3) and the
  * shelter semantics built on it. Pure classifier cases fabricate
  * [[RhythmEvidence]] directly; one integration case runs a real cell through
  * [[PhaseTracker]].
  */
class RhythmSpec:
  private val p = RhythmParam()
  // nominalPeriod 39.47 -> periodMin ≈ 29.6, periodMax ≈ 78.9,
  // stale window = 2.5 × 39.47 ≈ 98.7 s.

  private def stats(
      periods: Vector[Double],
      duty: Double = 0.10,
      lastCross: Double = 90.0,
      bornAt: Double = 0.0
  ): RhythmEvidence = RhythmEvidence(periods, duty, lastCross, bornAt)

  private def healthyReadout(rhythm: RhythmVerdict): CellReadout =
    CellReadout(
      current = MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      regime = Regime.Calm,
      phase = PhaseReading.Basin(PhaseLabel.Resting),
      baselineCalming = 0.03,
      rhythm = rhythm,
      simTime = 5.0
    )

  @Test def `a stable 40 s beat at low duty reads Beating`(): Unit =
    val hb = RhythmVerdict.classify(stats(Vector(40.0, 40.0, 40.0)), 100.0, selfSustained = true, p)
    hb match
      case RhythmVerdict.Beating(period, duty, source) =>
        assertEquals(40.0, period, 1e-12)
        assertEquals(0.10, duty, 1e-12)
        assertEquals(RhythmSource.Intrinsic, source)
      case other => fail(s"expected Beating, got $other")

  @Test def `a 20 s period reads Fast`(): Unit =
    val hb = RhythmVerdict.classify(stats(Vector(20.0, 20.0, 20.0)), 100.0, selfSustained = false, p)
    hb match
      case RhythmVerdict.Fast(period) => assertEquals(20.0, period, 1e-12)
      case other                  => fail(s"expected Fast, got $other")

  @Test def `a 120 s period reads Slow`(): Unit =
    val hb = RhythmVerdict.classify(stats(Vector(120.0, 120.0, 120.0)), 100.0, selfSustained = true, p)
    hb match
      case RhythmVerdict.Slow(period) => assertEquals(120.0, period, 1e-12)
      case other                  => fail(s"expected Slow, got $other")

  @Test def `period jitter beyond tolerance reads Arrhythmic`(): Unit =
    // mean 44, CV ≈ 0.257 > 0.20
    val hb = RhythmVerdict.classify(stats(Vector(60.0, 36.0, 36.0)), 100.0, selfSustained = true, p)
    hb match
      case RhythmVerdict.Arrhythmic(lastPeriod, _) => assertEquals(44.0, lastPeriod, 1e-9)
      case other => fail(s"expected Arrhythmic, got $other")

  @Test def `a single-cycle duty above the bound reads Arrhythmic`(): Unit =
    val hb = RhythmVerdict.classify(
      stats(Vector(40.0, 40.0, 40.0), duty = 0.45),
      100.0,
      selfSustained = true,
      p
    )
    hb match
      case RhythmVerdict.Arrhythmic(_, duty) => assertEquals(0.45, duty, 1e-12)
      case other => fail(s"expected Arrhythmic, got $other")

  @Test def `no crossing past the grace anchor reads Slow`(): Unit =
    val hb = RhythmVerdict.classify(
      RhythmEvidence(Vector.empty, Double.NaN, Double.NaN, bornAt = 0.0),
      200.0,
      selfSustained = false,
      p
    )
    hb match
      case RhythmVerdict.Slow(period) => assertTrue(period.isNaN, "no period was ever measured")
      case other                  => fail(s"expected Slow, got $other")

  @Test def `fewer than minCycles cycles with a live anchor reads Warming and shelters`(): Unit =
    val hb = RhythmVerdict.classify(stats(Vector(40.0, 40.0)), 100.0, selfSustained = true, p)
    assertEquals(RhythmVerdict.Warming, hb)
    assertTrue(
      healthyReadout(RhythmVerdict.Warming).isSheltered,
      "the Warming grace shelters (it replaces the old first-strike window)"
    )

  @Test def `a fresh cell past the birth grace still warms while it keeps crossing`(): Unit =
    // v2 anchor rule: born at 0, last crossing at 90, now 110 — 110 > 98.7
    // since birth, but only 20 since the last crossing: still Warming.
    val hb = RhythmVerdict.classify(
      stats(Vector(40.0, 40.0), lastCross = 90.0, bornAt = 0.0),
      110.0,
      selfSustained = true,
      p
    )
    assertEquals(RhythmVerdict.Warming, hb)

  @Test def `Warming with dead mycelium is not sheltered`(): Unit =
    val r = healthyReadout(RhythmVerdict.Warming).copy(sustenance = 0.0)
    assertFalse(r.isSheltered, "mycelium and knee still take priority")

  @Test def `a below-knee cell is not sheltered even when Beating`(): Unit =
    val beating = RhythmVerdict.Beating(40.0, 0.10, RhythmSource.Intrinsic)
    val r = healthyReadout(beating).copy(
      phase = PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee)
    )
    assertFalse(r.isSheltered)

  @Test def `the spike phase does not suspend rhythm shelter`(): Unit =
    val beating = RhythmVerdict.Beating(40.0, 0.10, RhythmSource.Intrinsic)
    val r = healthyReadout(beating).copy(
      current = MandateCurrent(1.9, 0.2, 0.6, 0.5),
      regime = Regime.Oscillating,
      phase = PhaseReading.Cycle(PhaseLabel.Excitation, 0.05)
    )
    assertTrue(r.isSpiking)
    assertTrue(
      r.isSheltered,
      "the beat's high phase is part of the shelter (design note 11 §1.3)"
    )

  @Test def `a paced beat shelters alike`(): Unit =
    val hb = RhythmVerdict.classify(stats(Vector(40.0, 40.0, 40.0)), 100.0, selfSustained = false, p)
    hb match
      case RhythmVerdict.Beating(_, _, source) => assertEquals(RhythmSource.Paced, source)
      case other                           => fail(s"expected Beating, got $other")
    assertTrue(healthyReadout(hb).isSheltered, "Paced counts as shelter (ruling 1)")

  @Test def `a beat gone stale reads Slow`(): Unit =
    val hb = RhythmVerdict.classify(
      stats(Vector(40.0, 40.0, 40.0), lastCross = 0.0),
      200.0,
      selfSustained = true,
      p
    )
    hb match
      case RhythmVerdict.Slow(period) => assertEquals(40.0, period, 1e-12)
      case other                  => fail(s"expected Slow, got $other")

  @Test def `shelter follows the rhythm category alone`(): Unit =
    val beating = RhythmVerdict.Beating(40.0, 0.10, RhythmSource.Intrinsic)
    assertTrue(healthyReadout(beating).isSheltered)
    assertTrue(healthyReadout(RhythmVerdict.Warming).isSheltered, "the Warming grace shelters")
    assertFalse(healthyReadout(RhythmVerdict.Slow(Double.NaN)).isSheltered, "a silent heart does not")
    assertFalse(healthyReadout(RhythmVerdict.Fast(20.0)).isSheltered, "tachycardia is not shelter")
    assertFalse(
      healthyReadout(RhythmVerdict.Arrhythmic(44.0, 0.45)).isSheltered,
      "arrhythmia is not shelter"
    )

  @Test def `arrhythmia is held for two of the cell's own periods`(): Unit =
    val beating = RhythmVerdict.Beating(44.0, 0.10, RhythmSource.Intrinsic)
    val arr = RhythmVerdict.Arrhythmic(44.0, 0.45)
    val (pub1, since1) = RhythmVerdict.smooth(arr, beating, Double.NaN, 100.0, p)
    assertEquals(beating, pub1)
    assertEquals(100.0, since1, 1e-12)
    val (pub2, since2) = RhythmVerdict.smooth(arr, pub1, since1, 100.0 + 1.9 * 44.0, p)
    assertEquals(beating, pub2, "still inside the two-own-period grace (83.6 s < 88 s)")
    val (pub3, since3) = RhythmVerdict.smooth(arr, pub2, since2, 100.0 + 2.1 * 44.0, p)
    assertEquals(arr, pub3, "two of its own bad periods exit Beating (92.4 s > 88 s)")
    assertTrue(since3.isNaN)

  @Test def `arrhythmia hysteresis uses the raw verdict's period, not the nominal one`(): Unit =
    // 2 x 60 s = 120 s; the old 2 x nominalPeriod rule would have flipped at 79 s.
    val beating = RhythmVerdict.Beating(60.0, 0.10, RhythmSource.Intrinsic)
    val arr = RhythmVerdict.Arrhythmic(60.0, 0.45)
    val (pub1, since1) = RhythmVerdict.smooth(arr, beating, Double.NaN, 0.0, p)
    val (pub2, since2) = RhythmVerdict.smooth(arr, pub1, since1, 100.0, p)
    assertEquals(beating, pub2, "100 s < 2 x 60 s: still held")
    val (pub3, _) = RhythmVerdict.smooth(arr, pub2, since2, 130.0, p)
    assertEquals(arr, pub3, "130 s > 2 x 60 s: released")

  @Test def `an acute overdrive verdict leaves Beating immediately`(): Unit =
    val beating = RhythmVerdict.Beating(40.0, 0.10, RhythmSource.Intrinsic)
    val (pub, since) = RhythmVerdict.smooth(RhythmVerdict.Fast(20.0), beating, Double.NaN, 100.0, p)
    assertEquals(
      RhythmVerdict.Fast(20.0),
      pub,
      "Fast is already averaged over minCycles intervals: no extra hold"
    )
    assertTrue(since.isNaN)

  @Test def `silence leaves Beating immediately (no double wait)`(): Unit =
    val beating = RhythmVerdict.Beating(40.0, 0.10, RhythmSource.Intrinsic)
    val (pub, since) = RhythmVerdict.smooth(RhythmVerdict.Slow(40.0), beating, Double.NaN, 100.0, p)
    assertEquals(
      RhythmVerdict.Slow(40.0),
      pub,
      "Slow already waited staleCycles: the exit hold would double the wait"
    )
    assertTrue(since.isNaN)

  @Test def `arrhythmia recovery clears the bad stretch`(): Unit =
    val beating = RhythmVerdict.Beating(44.0, 0.10, RhythmSource.Intrinsic)
    val arr = RhythmVerdict.Arrhythmic(44.0, 0.45)
    val (_, since1) = RhythmVerdict.smooth(arr, beating, Double.NaN, 100.0, p)
    val (pub2, since2) = RhythmVerdict.smooth(beating, beating, since1, 150.0, p)
    assertEquals(beating, pub2)
    assertTrue(since2.isNaN)

  @Test def `exit hysteresis never delays transitions out of a non-Beating verdict`(): Unit =
    val (pub, since) =
      RhythmVerdict.smooth(RhythmVerdict.Slow(Double.NaN), RhythmVerdict.Warming, Double.NaN, 100.0, p)
    pub match
      case RhythmVerdict.Slow(per) =>
        assertTrue(per.isNaN, "Warming ends immediately when judged dead")
      case other => fail(s"expected Slow, got $other")
    assertTrue(since.isNaN)

  @Test def `a settled natural-band oscillator classifies Beating from live tracker statistics`(): Unit =
    // s = 0.40 sits mid natural band (§2.1: period ≈ 39.5 s, duty ≈ 4–6%).
    val fast = FastParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
    val st = MandateState(
      MandateCurrent(-1.2, -0.6, 0.40, 0.0),
      fast,
      StressParam(gamma = 0.0)
    )
    val tr = new PhaseTracker(ReadoutParam(), fast)
    var t = 0.0
    var hb = RhythmVerdict.Warming
    var hbAt50 = RhythmVerdict.Warming
    while t < 400.0 do
      st.step(0.02)
      t += 0.02
      tr.observe(t, st.getState, st.sustenance)
      val selfSustained = Readout.oscillating(
        Readout.slowA(fast, st.getState.stress, st.sustenance),
        fast
      )
      hb = RhythmVerdict.classify(tr.rhythmEvidence, t, selfSustained, p)
      if t < 51.0 then hbAt50 = hb
    assertEquals(
      RhythmVerdict.Warming,
      hbAt50,
      "collecting minCycles=3 intervals takes ~4 crossings: still Warming at t=50"
    )
    hb match
      case RhythmVerdict.Beating(period, duty, source) =>
        assertEquals(RhythmSource.Intrinsic, source)
        assertEquals(39.5, period, 3.0, s"natural-band period, got $period")
        assertTrue(duty > 0.0 && duty < p.dutyMax, s"healthy duty, got $duty")
      case other => fail(s"a settled natural-band cell must be Beating, got $other")
