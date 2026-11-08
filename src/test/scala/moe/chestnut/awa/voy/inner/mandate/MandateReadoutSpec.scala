package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.MandateOverworld
import moe.chestnut.awa.voy.MandateOverworld.BellStrikeOutcome

/** Defined-domain behaviour of the readout (design draft §1.4 + §1.2).
  *
  * The claim under test: a cycle phase exists only where a limit cycle
  * exists, and every other state gets an explicit non-phase answer. The
  * tests name the four states the design distinguishes — periodic orbit,
  * resting basin, strong transient, burnout — and assert a *different*
  * answer for each, so no two of them can be collapsed into one
  * "undefined".
  */
class MandateReadoutSpec:
  private val fast = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
  private val dt = 0.02

  private def cell(stress: Double, coupling: Double = 1.0): MandateState =
    MandateState(
      state = MandateCurrent(dissonance = -1.2, entrench = -0.6, stress = stress),
      param = fast,
      stressParam = StressParam(gamma = 0.0, coupling = coupling)
    )

  /** Settle a cell and report the final time, state and tracker. */
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
      tr.observe(t, st.getState)
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
    // c0 intact: the load stays below the forgiving capacity, so the cell
    // settles near s ~ 0.09 and a = 0.61 > a_c: no limit cycle exists.
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

    // Push the coupling up to make the narrative burnout channel real
    // (§1.2) and check the readout refuses a phase there.
    val st = cell(1.0, coupling = 2.0)
    val (t, cur, tr) = settle(st, 600.0, coupling = 2.0)
    assertEquals(PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle), tr.reading(t, cur))

  @Test def `the declared stress partition calls a spiking cell burnt out`(): Unit =
    // Measured fact, recorded against the *stock* partition: at s = 1.0 the
    // cell is labelled Burnout and still spikes every ~50 time units.
    val st = cell(1.0)
    val (t, cur, tr) = settle(st, 4000.0)
    assertTrue(tr.excursions >= 5, s"the cell should still spike, saw ${tr.excursions}")
    assertEquals(Regime.Burnout, Readout.regime(cur), "the stock cut calls s=1.0 burnout")
    assertTrue(
      Readout.oscillating(fast.alpha - cur.stress, fast),
      "and yet a limit cycle exists: the stock regime and the phase domain disagree"
    )
    assertTrue(tr.reading(t, cur).isInstanceOf[PhaseReading.Cycle])

  @Test def `pulse window shelter and 20-division dial tick semantics`(): Unit =
    val calmUnpulsed = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      PhaseReading.Basin(PhaseLabel.Resting),
      baselineCalming = 0.0
    )
    assertFalse(calmUnpulsed.isMaintained, "no fresh pulse -> stagnant")

    val calmMaintained = calmUnpulsed.copy(baselineCalming = 0.03)
    assertFalse(
      calmMaintained.isMaintained,
      "c0 alone no longer shelters: only a fresh pulse window does"
    )

    val pulsed = calmMaintained.copy(pulseSuppressed = true)
    assertTrue(pulsed.isMaintained, "a fresh pulse window shelters the cell")

    val spikingPulsed = pulsed.copy(
      current = MandateCurrent(1.9, 0.2, 0.6, 0.5),
      regime = Regime.Oscillating,
      phase = PhaseReading.Cycle(PhaseLabel.Excitation, 0.05)
    )
    assertFalse(spikingPulsed.isMaintained, "a positive spike excursion suspends pulse shelter")

    val negativeOvershoot = pulsed.copy(
      current = MandateCurrent(-1.9, 0.2, 0.6, 0.5),
      regime = Regime.Oscillating,
      phase = PhaseReading.Cycle(PhaseLabel.Resting, 0.75)
    )
    assertTrue(
      negativeOvershoot.isMaintained,
      "the negative repolarisation overshoot (|v| > 1.8, v < 0) is NOT a spike"
    )

    val burned = pulsed.copy(phase = PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle))
    assertFalse(burned.isMaintained, "a burnt-out cell cannot be sheltered by pulses")

    val deadMycelium = pulsed.copy(sustenance = 0.0)
    assertFalse(deadMycelium.isMaintained, "a dead mycelial network cannot be sheltered")

    val refractoryCycle = pulsed.copy(
      current = MandateCurrent(0.5, 0.8, 0.6, 0.5),
      regime = Regime.Oscillating,
      phase = PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory, 0.25)
    )
    assertTrue(refractoryCycle.isMaintained, "between spikes, pulse shelter holds")
    assertEquals(Some(5), refractoryCycle.dialTick, "0.25 of 20 divisions is tick 5")
    assertEquals(None, calmMaintained.dialTick, "resting basin has no cycle dial tick")

  @Test def `phase-gated bell strike re-anchoring across Calm, Oscillating, and Burnout`(): Unit =
    val calmUnanchored = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      PhaseReading.Basin(PhaseLabel.Resting),
      baselineCalming = 0.0
    )
    val calmOutcome = MandateOverworld.evaluateBellStrike(calmUnanchored)
    assertEquals(BellStrikeOutcome.AnchoredOrMaintained, calmOutcome)
    assertTrue(calmOutcome.anchorsC0)
    assertEquals(MandateOverworld.BellBaselineCalming, calmOutcome.baselineCalming, 1e-12)

    val oscExcitationUnanchored = CellReadout(
      MandateCurrent(1.9, 0.2, 0.65, 0.55),
      Regime.Oscillating,
      PhaseReading.Cycle(PhaseLabel.Excitation, 0.05),
      baselineCalming = 0.0
    )
    val spikeOutcome = MandateOverworld.evaluateBellStrike(oscExcitationUnanchored)
    assertEquals(BellStrikeOutcome.SpikeSwallowed, spikeOutcome)
    assertFalse(spikeOutcome.anchorsC0, "striking during Excitation must fail to re-anchor c0")
    assertEquals(0.0, spikeOutcome.baselineCalming, 1e-12)

    val oscRefractoryUnanchored = CellReadout(
      MandateCurrent(0.4, 0.8, 0.65, 0.55),
      Regime.Oscillating,
      PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory, 0.25),
      baselineCalming = 0.0
    )
    val reanchorOutcome = MandateOverworld.evaluateBellStrike(oscRefractoryUnanchored)
    assertEquals(BellStrikeOutcome.RefractoryReanchored, reanchorOutcome)
    assertTrue(reanchorOutcome.anchorsC0, "striking in refractory window re-anchors c0")
    assertEquals(MandateOverworld.BellBaselineCalming, reanchorOutcome.baselineCalming, 1e-12)

    val burnoutCell = CellReadout(
      MandateCurrent(1.1, 0.4, 0.98, 0.0),
      Regime.Burnout,
      // A saturated cell is still oscillating (a = -0.28 > burnoutA): the
      // refusal to anchor is functional incapacitation, not a dead zone.
      PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory, 0.25),
      baselineCalming = 0.0
    )
    val burnoutOutcome = MandateOverworld.evaluateBellStrike(burnoutCell)
    assertEquals(BellStrikeOutcome.SaturatedNoAnchor, burnoutOutcome)
    assertFalse(burnoutOutcome.anchorsC0, "saturated cell cannot re-anchor c0 while the stress stays high")
    assertEquals(0.0, burnoutOutcome.baselineCalming, 1e-12)
    assertTrue(
      burnoutOutcome.suppressesStagnation,
      "a saturated but LIVING cell still answers the pulse with a heartbeat window"
    )

  @Test def `bell strike on dead mycelium answers nothing`(): Unit =
    val deadCell = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.3, 0.0),
      Regime.Strained,
      PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle),
      baselineCalming = 0.0,
      sustenance = 0.0 // chronic ecological burnout: the network is gone
    )
    val outcome = MandateOverworld.evaluateBellStrike(deadCell)
    assertEquals(BellStrikeOutcome.DeadMyceliumBurnout, outcome)
    assertFalse(outcome.anchorsC0, "dead mycelium cannot carry a baseline anchor")
    assertFalse(outcome.suppressesStagnation, "dead mycelium cannot carry a heartbeat")
    assertFalse(outcome.triggersOverloadPenalty, "death is not the striker's fault")

  @Test def `striking inside the refractory interval of the previous pulse overloads the cell`(): Unit =
    // The previous pulse landed at simTime 5.0; striking again at 6.0 is
    // faster than minSafeInterval (8.0) — acute pulse-overload burnout.
    val overpulsed = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      PhaseReading.Basin(PhaseLabel.Resting),
      baselineCalming = 0.03,
      pulseState = PulseToleranceState(lastPulseTime = 5.0),
      simTime = 6.0
    )
    val outcome = MandateOverworld.evaluateBellStrike(overpulsed)
    assertEquals(BellStrikeOutcome.PulseOverloadBurnout, outcome)
    assertFalse(outcome.anchorsC0)
    assertFalse(outcome.suppressesStagnation, "overload collapses the suppression window")
    assertTrue(outcome.triggersOverloadPenalty, "the striker feels the recoil")

  @Test def `stacked pulse drive burns a healthy cell across the knee`(): Unit =
    // A healthy calm cell (a_slow = 0.65) with three overlapping bell
    // kernels still ringing (p = 2.1): a_eff = 0.65 - 1.6*(2.1 - 0.883)
    // ≈ -1.30, well across the right knee (burnoutA = -0.4667).
    val tracker = new PhaseTracker(
      ReadoutParam(),
      fast,
      coupling = 1.0,
      sustenance = () => 1.0,
      depth = StressParam().sustenanceDepth,
      pulseDrive = () => 2.1
    )
    val cur = MandateCurrent(-1.2, -0.6, 0.05, 0.0)
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle),
      tracker.reading(100.0, cur),
      "excess stacked pulse drive must push a_eff across the burnout knee"
    )

    // A single bell kernel (p = 0.88 at peak) must NOT burn the same cell.
    val single = new PhaseTracker(
      ReadoutParam(),
      fast,
      coupling = 1.0,
      sustenance = () => 1.0,
      depth = StressParam().sustenanceDepth,
      pulseDrive = () => 0.8
    )
    assertNotEquals(
      PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle),
      single.reading(100.0, cur),
      "one bell strike alone never burns a healthy cell"
    )

  @Test def `an overload-locked cell reads BurnoutNoCycle regardless of state`(): Unit =
    val tracker = new PhaseTracker(
      ReadoutParam(),
      fast,
      coupling = 1.0,
      sustenance = () => 1.0,
      depth = StressParam().sustenanceDepth,
      pulseDrive = () => 0.0,
      overloaded = () => true
    )
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle),
      tracker.reading(100.0, MandateCurrent(-1.2, -0.6, 0.05, 0.0)),
      "the acute overload lockout must read as BurnoutNoCycle"
    )


