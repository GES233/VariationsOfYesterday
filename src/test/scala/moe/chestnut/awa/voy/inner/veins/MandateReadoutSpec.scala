package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.inner.event.PulseBookkeeping
import moe.chestnut.awa.voy.mandate.BellRitual
import moe.chestnut.awa.voy.mandate.BellRitual.BellStrikeOutcome

/** Defined-domain behaviour of the 3-axis readout and bell strike adjudication. */
class MandateReadoutSpec:
  private val fast = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
  private val dt = 0.02

  private def cell(stress: Double, coupling: Double = 1.0): MandateState =
    MandateState(
      state = MandateCurrent(dissonance = -1.2, entrench = -0.6, stress = stress),
      param = fast,
      stressParam = StressParam(gamma = 0.0, coupling = coupling)
    )

  private def settle(
      st: MandateState,
      tEnd: Double,
      coupling: Double = 1.0
  ): (Double, MandateCurrent, PhaseTracker) =
    val tr = new PhaseTracker(ReadoutParam(), fast, coupling)
    var t = 0.0
    while t < tEnd do
      st.step(dt)
      t += dt
      tr.observe(t, st.getState, st.sustenance)
    (t, st.getState, tr)

  @Test def `a periodic cell gets a cycle phase with a fraction in [0,1)`(): Unit =
    val st = cell(1.0)
    val (t, cur, tr) = settle(st, 6000.0)
    assertTrue(tr.excursions >= 5, s"expected a self-sustained cycle, saw ${tr.excursions} crossings")
    tr.reading(t, cur) match
      case PhaseReading.Cycle(_, fraction) =>
        assertTrue(fraction >= 0.0 && fraction < 1.0, s"fraction out of range: $fraction")
      case other => fail(s"a self-sustained cycle must yield a cycle phase, got $other")

  @Test def `a resting cell in a non-oscillating window gets a basin class, not a fraction`(): Unit =
    val st = cell(0.0)
    st.disturbance = 0.05
    st.baselineCalming = 0.03
    val (t, cur, tr) = settle(st, 2000.0)
    assertEquals(PhaseReading.Basin(PhaseLabel.Resting), tr.reading(t, cur))
    assertTrue(cur.stress < 0.2, s"the cell should stay in the tolerant basin, s=${cur.stress}")

  @Test def `a single excursion is a transient, never a cycle phase`(): Unit =
    val st = cell(0.0)
    st.disturbance = 0.05
    st.baselineCalming = 0.03
    st.synapseTau = 5.0
    st.firePulse(PulseKind.AlphaShock.shape.area)
    val tr = new PhaseTracker(ReadoutParam(), fast)
    var t = 0.0
    var sawTransient = false
    while t < 300.0 do
      st.step(dt)
      t += dt
      tr.observe(t, st.getState)
      tr.reading(t, st.getState) match
        case PhaseReading.Undefined(UndefinedReason.TransientNoCycle) => sawTransient = true
        case _ => ()
    assertTrue(sawTransient, "an excursion without a cycle must read as a transient")
    tr.reading(t, st.getState) match
      case PhaseReading.Cycle(_, f) => fail(s"a single excursion must not become a cycle at $f")
      case _ => ()

  @Test def `burnout is decided by the dynamics, not by the stress cut`(): Unit =
    assertEquals(
      fast.beta * (2.0 / 3.0) - 1.0,
      Readout.burnoutA(fast),
      1e-12,
      "burnout_a must come from the right knee of the cubic"
    )
    assertTrue(
      fast.alpha - StressParam().stressMax > Readout.burnoutA(fast),
      "with the frozen parameters the clamped stress can never reach burnout"
    )

    val st = cell(1.0, coupling = 2.0)
    val (t, cur, tr) = settle(st, 600.0, coupling = 2.0)
    assertEquals(PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee), tr.reading(t, cur))

  @Test def `the spike threshold has a single source`(): Unit =
    assertEquals(PhaseDetection.SpikeLevel, ReadoutParam().spikeLevel, 0.0)
    assertEquals(PhaseDetection.SpikeLevel, PhaseResponse.spikeLevel, 0.0)

  @Test def `the declared stress partition calls a spiking cell burnt out`(): Unit =
    val st = cell(1.0)
    val (t, cur, tr) = settle(st, 4000.0)
    assertTrue(tr.excursions >= 5, s"the cell should still spike, saw ${tr.excursions}")
    assertEquals(Regime.Burnout, Readout.regime(cur), "the stock cut calls s=1.0 burnout")
    assertTrue(
      Readout.oscillating(fast.alpha - cur.stress, fast),
      "and yet a limit cycle exists: the stock regime and the phase domain disagree"
    )
    assertTrue(tr.reading(t, cur).isInstanceOf[PhaseReading.Cycle])

  @Test def `mycelial health 3-tier classification`(): Unit =
    assertEquals(MycelialHealth.Dead, Readout.mycelialHealth(0.0))
    assertEquals(MycelialHealth.Dead, Readout.mycelialHealth(0.5 * StressParam().sustenanceFloor))
    assertEquals(MycelialHealth.Damaged, Readout.mycelialHealth(StressParam().sustenanceFloor))
    assertEquals(MycelialHealth.Damaged, Readout.mycelialHealth(0.35))
    assertEquals(MycelialHealth.Intact, Readout.mycelialHealth(0.60))
    assertEquals(MycelialHealth.Intact, Readout.mycelialHealth(1.0))

  @Test def `pulse window shelter and 20-division dial tick semantics`(): Unit =
    val calmUnpulsed = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      PhaseReading.Basin(PhaseLabel.Resting),
      baselineCalming = 0.0
    )
    assertFalse(calmUnpulsed.isSheltered, "no fresh pulse -> stagnant")

    val calmMaintained = calmUnpulsed.copy(baselineCalming = 0.03)
    assertFalse(
      calmMaintained.isSheltered,
      "c0 alone does not shelter: a fresh pulse window is required"
    )

    val pulsed = calmMaintained.copy(
      pulseStamp = PulseStamp(shelterUntil = 40.0, cooldownUntil = 8.0),
      simTime = 5.0
    )
    assertTrue(pulsed.isSheltered, "a fresh pulse window shelters the cell")

    val spikingPulsed = pulsed.copy(
      current = MandateCurrent(1.9, 0.2, 0.6, 0.5),
      regime = Regime.Oscillating,
      phase = PhaseReading.Cycle(PhaseLabel.Excitation, 0.05)
    )
    assertFalse(spikingPulsed.isSheltered, "a positive spike excursion suspends pulse shelter")

    val negativeOvershoot = pulsed.copy(
      current = MandateCurrent(-1.9, 0.2, 0.6, 0.5),
      regime = Regime.Oscillating,
      phase = PhaseReading.Cycle(PhaseLabel.Resting, 0.75)
    )
    assertTrue(
      negativeOvershoot.isSheltered,
      "the negative repolarisation overshoot (|v| > 1.8, v < 0) is NOT a spike"
    )

    val deadMycelium = pulsed.copy(sustenance = 0.0)
    assertFalse(deadMycelium.isSheltered, "a dead mycelial network cannot be sheltered")

    val collapsedOffset = pulsed.copy(
      phase = PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee)
    )
    assertFalse(collapsedOffset.isSheltered, "a cell with collapsed slow offset cannot be sheltered")

    val refractoryCycle = pulsed.copy(
      current = MandateCurrent(0.5, 0.8, 0.6, 0.5),
      regime = Regime.Oscillating,
      phase = PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory, 0.25)
    )
    assertTrue(refractoryCycle.isSheltered, "between spikes, pulse shelter holds")
    assertEquals(Some(5), refractoryCycle.dialTick, "0.25 of 20 divisions is tick 5")
    assertEquals(None, calmMaintained.dialTick, "resting basin has no cycle dial tick")

  @Test def `phase-gated bell strike re-anchoring across Calm, Oscillating, and Burnout`(): Unit =
    val calmUnanchored = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      PhaseReading.Basin(PhaseLabel.Resting),
      baselineCalming = 0.0
    )
    val calmOutcome = BellRitual.evaluateBellStrike(calmUnanchored)
    assertEquals(BellStrikeOutcome.AnchoredOrMaintained, calmOutcome)
    assertTrue(calmOutcome.anchorsC0)
    assertEquals(BellRitual.BellBaselineCalming, calmOutcome.baselineCalming, 1e-12)

    val oscExcitationUnanchored = CellReadout(
      MandateCurrent(1.9, 0.2, 0.65, 0.55),
      Regime.Oscillating,
      PhaseReading.Cycle(PhaseLabel.Excitation, 0.05),
      baselineCalming = 0.0
    )
    val spikeOutcome = BellRitual.evaluateBellStrike(oscExcitationUnanchored)
    assertEquals(BellStrikeOutcome.SpikeSwallowed, spikeOutcome)
    assertFalse(spikeOutcome.anchorsC0, "striking during Excitation must fail to re-anchor c0")
    assertEquals(0.0, spikeOutcome.baselineCalming, 1e-12)

    val oscRefractoryUnanchored = CellReadout(
      MandateCurrent(0.4, 0.8, 0.65, 0.55),
      Regime.Oscillating,
      PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory, 0.25),
      baselineCalming = 0.0
    )
    val reanchorOutcome = BellRitual.evaluateBellStrike(oscRefractoryUnanchored)
    assertEquals(BellStrikeOutcome.RefractoryReanchored, reanchorOutcome)
    assertTrue(reanchorOutcome.anchorsC0, "striking in refractory window re-anchors c0")
    assertEquals(BellRitual.BellBaselineCalming, reanchorOutcome.baselineCalming, 1e-12)

    val burnoutCell = CellReadout(
      MandateCurrent(1.1, 0.4, 0.98, 0.0),
      Regime.Burnout,
      PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory, 0.25),
      baselineCalming = 0.0
    )
    val burnoutOutcome = BellRitual.evaluateBellStrike(burnoutCell)
    assertEquals(BellStrikeOutcome.SaturatedNoAnchor, burnoutOutcome)
    assertFalse(burnoutOutcome.anchorsC0, "saturated cell cannot re-anchor c0 while stress stays high")
    assertEquals(PulseBookkeeping.Counted, burnoutOutcome.bookkeeping)

  @Test def `bell strike on dead mycelium answers nothing`(): Unit =
    val deadCell = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.3, 0.0),
      Regime.Strained,
      PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee),
      baselineCalming = 0.0,
      sustenance = 0.0
    )
    val outcome = BellRitual.evaluateBellStrike(deadCell)
    assertEquals(BellStrikeOutcome.DeadMyceliumBurnout, outcome)
    assertFalse(outcome.anchorsC0)
    assertEquals(PulseBookkeeping.PhysicsOnly, outcome.bookkeeping)
    assertFalse(outcome.triggersOverloadPenalty)

  @Test def `striking inside the cooldown interval triggers overload backlash`(): Unit =
    val inCooldown = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      PhaseReading.Basin(PhaseLabel.Resting),
      baselineCalming = 0.03,
      pulseStamp = PulseStamp(shelterUntil = 45.0, cooldownUntil = 13.0),
      simTime = 6.0
    )
    val outcome = BellRitual.evaluateBellStrike(inCooldown)
    assertEquals(BellStrikeOutcome.PulseOverloadBurnout, outcome)
    assertFalse(outcome.anchorsC0)
    assertEquals(PulseBookkeeping.Counted, outcome.bookkeeping)
    assertTrue(outcome.triggersOverloadPenalty)
