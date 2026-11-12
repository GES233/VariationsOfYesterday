package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.inner.event.PulseBookkeeping
import moe.chestnut.awa.voy.mandate.BellRitual
import moe.chestnut.awa.voy.mandate.BellRitual.BellStrikeOutcome

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
    assertEquals(PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee), tr.reading(t, cur))

  @Test def `the spike threshold has a single source`(): Unit =
    // Three consumers read "is this a spike": the agent's tremor edge
    // (`readoutParam.spikeLevel`), the link relay's confirmation, and the
    // offline PRC harness. The latter two default to
    // `PhaseDetection.SpikeLevel`; the readout parameter must keep pointing
    // at the same constant, or the player-facing tremor and the crystal
    // links silently diverge.
    assertEquals(PhaseDetection.SpikeLevel, ReadoutParam().spikeLevel, 0.0)
    assertEquals(PhaseDetection.SpikeLevel, PhaseResponse.spikeLevel, 0.0)

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

    // [BATCH-1 REGRESSION LOCK] Both no-cycle reasons still withdraw shelter;
    // whether a short lockout should is a pending decision (see
    // docs/mechanism-state-responsibilities.md §8.4).
    //
    // The facts are built from their own criteria, not from the phase slot:
    // stacked pulse drive pushes a_eff below the knee, and the ledger holds an
    // active lockout.
    val burned = pulsed.copy(
      pulseDrive = 2.1,
      phase = PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee)
    )
    assertTrue(burned.isOffsetBelowKnee, "the stacked drive crosses the readout threshold")
    assertFalse(burned.isMaintained, "a burnt-out cell cannot be sheltered by pulses")

    val overloadLocked = pulsed.copy(
      phase = PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      pulseLedger = PulseLedger(overload = OverloadLock(60.0)),
      simTime = 10.0
    )
    assertTrue(overloadLocked.isOverloadLocked, "the ledger holds the lockout")
    assertFalse(
      overloadLocked.isMaintained,
      "[BATCH-1 REGRESSION LOCK] an acute lockout still withdraws the shelter it collapsed"
    )

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
      // A saturated cell is still oscillating (a = -0.28 > burnoutA): the
      // refusal to anchor is functional incapacitation, not a dead zone.
      PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory, 0.25),
      baselineCalming = 0.0
    )
    val burnoutOutcome = BellRitual.evaluateBellStrike(burnoutCell)
    assertEquals(BellStrikeOutcome.SaturatedNoAnchor, burnoutOutcome)
    assertFalse(burnoutOutcome.anchorsC0, "saturated cell cannot re-anchor c0 while the stress stays high")
    assertEquals(0.0, burnoutOutcome.baselineCalming, 1e-12)
    assertEquals(
      PulseBookkeeping.Counted,
      burnoutOutcome.bookkeeping,
      "a saturated but LIVING cell still answers the pulse with a counted arrival"
    )

  @Test def `bell strike on dead mycelium answers nothing`(): Unit =
    val deadCell = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.3, 0.0),
      Regime.Strained,
      PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee),
      baselineCalming = 0.0,
      sustenance = 0.0 // chronic ecological burnout: the network is gone
    )
    val outcome = BellRitual.evaluateBellStrike(deadCell)
    assertEquals(BellStrikeOutcome.DeadMyceliumBurnout, outcome)
    assertFalse(outcome.anchorsC0, "dead mycelium cannot carry a baseline anchor")
    assertEquals(
      PulseBookkeeping.PhysicsOnly,
      outcome.bookkeeping,
      "dead mycelium cannot carry a counted pulse"
    )
    assertFalse(outcome.triggersOverloadPenalty, "death is not the striker's fault")

  @Test def `striking inside the refractory interval of the previous pulse overloads the cell`(): Unit =
    // The previous pulse landed at simTime 5.0; striking again at 6.0 is
    // faster than minSafeInterval (8.0) — acute pulse-overload burnout.
    val overpulsed = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      PhaseReading.Basin(PhaseLabel.Resting),
      baselineCalming = 0.03,
      pulseLedger = PulseLedger(tolerance = ToleranceBook(lastPulseTime = 5.0)),
      simTime = 6.0
    )
    val outcome = BellRitual.evaluateBellStrike(overpulsed)
    assertEquals(BellStrikeOutcome.PulseOverloadBurnout, outcome)
    assertFalse(outcome.anchorsC0)
    assertEquals(
      PulseBookkeeping.Counted,
      outcome.bookkeeping,
      "overload is still counted: the ledger records the refusal and collapses the window"
    )
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
      PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee),
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
      PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee),
      single.reading(100.0, cur),
      "one bell strike alone never burns a healthy cell"
    )

  @Test def `an overload-locked cell reads OverloadLocked regardless of state`(): Unit =
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
      PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      tracker.reading(100.0, MandateCurrent(-1.2, -0.6, 0.05, 0.0)),
      "the mechanism rule publishes its own reason, not the trajectory one"
    )

  @Test def `when both no-cycle reasons hold, both facts stay queryable`(): Unit =
    // M = 0 drives a_eff below the knee AND the ledger holds an active
    // lockout. `phase` has one slot and keeps its display priority (mechanism
    // first), but each fact must be answerable independently — a query must
    // never reverse-infer from the displayed reason.
    val tracker = new PhaseTracker(
      ReadoutParam(),
      fast,
      coupling = 1.0,
      sustenance = () => 0.0, // a_eff = 0.7 - 1.2 - 0.05, below the knee
      depth = StressParam().sustenanceDepth,
      pulseDrive = () => 0.0,
      overloaded = () => true
    )
    val phase = tracker.reading(100.0, MandateCurrent(-1.2, -0.6, 0.05, 0.0))
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      phase,
      "display priority: the mechanism rule wins the single phase slot"
    )
    val readout = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      phase,
      sustenance = 0.0,
      pulseLedger = PulseLedger(overload = OverloadLock(200.0)),
      simTime = 100.0
    )
    assertTrue(
      readout.isOffsetBelowKnee,
      "the threshold condition is true even though the display shows the other reason"
    )
    assertTrue(readout.isOverloadLocked, "the mechanism reason is true")
    assertTrue(readout.isBurnoutPhase, "the coarse predicate is the union of both")
    assertTrue(
      readout.pulseLedger.overloadedAt(readout.simTime),
      "the ledger still holds the lockout fact"
    )
    assertTrue(
      Readout.isEcologicalBurnout(readout, fast, 1.0, StressParam().sustenanceDepth),
      "the ecological burnout fact is decided by M / a_slow, not by the phase slot"
    )

  @Test def `dead network plus an active lockout: death wins the dispatch`(): Unit =
    // Scenario D of docs/mechanism-state-responsibilities.md §6: the two facts
    // coexist and neither cancels the other. Bell dispatch checks ecological
    // burnout first, so a dead network still answers nothing even while the
    // ledger shows a live lockout.
    val deadAndLocked = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.05, 0.0),
      Regime.Calm,
      PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      baselineCalming = 0.0,
      sustenance = 0.0, // the network is gone
      pulseLedger = PulseLedger(overload = OverloadLock(200.0)),
      simTime = 100.0
    )
    assertTrue(deadAndLocked.pulseLedger.overloadedAt(deadAndLocked.simTime))
    assertTrue(
      deadAndLocked.isOffsetBelowKnee,
      "M = 0 puts a_eff below the knee; the fact does not depend on the display"
    )
    assertTrue(deadAndLocked.isOverloadLocked)
    assertTrue(deadAndLocked.isBurnoutPhase)
    val outcome = BellRitual.evaluateBellStrike(deadAndLocked)
    assertEquals(
      BellStrikeOutcome.DeadMyceliumBurnout,
      outcome,
      "ecological death takes precedence over the overload dispatch"
    )
    assertEquals(PulseBookkeeping.PhysicsOnly, outcome.bookkeeping)
    assertFalse(outcome.triggersOverloadPenalty, "a dead cell is not an over-pulse fault")

  @Test def `survival, slow-offset and spike facts each answer from their own criterion`(): Unit =
    // Coupling C7 (docs/mechanism-state-responsibilities.md §5): `a_slow`
    // across the knee is not a dead network. M = 0.5, s = 0.7 gives
    // a_slow = 0.7 - 1.2*0.5 - 0.7 = -0.6 <= -0.4667, yet M >= floor.
    val aliveBelowKnee = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.7, 0.0),
      Regime.Strained,
      PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee),
      sustenance = 0.5
    )
    assertTrue(aliveBelowKnee.isMyceliumViable, "M = 0.5 is a living, damaged network")
    assertFalse(aliveBelowKnee.isMyceliumAbsent, "M = 0.5 is not the absorbing state")
    assertTrue(aliveBelowKnee.isSlowOffsetBelowKnee, "the ODE offset itself is across the knee")
    assertTrue(aliveBelowKnee.isEcologicalDeath, "the shared guard fires on the slow offset")
    assertTrue(
      Readout.isEcologicalBurnout(aliveBelowKnee, fast),
      "the dispatch fact agrees even though the network still lives"
    )

    // The readout rule and the dynamical fact are different quantities: stacked
    // pulse drive pushes a_eff across the knee while a_slow stays above it. The
    // displayed phase is deliberately unrelated here — facts never come from it.
    val stacked = CellReadout(
      MandateCurrent(-1.2, -0.6, 0.5, 0.0),
      Regime.Calm,
      PhaseReading.Basin(PhaseLabel.Resting),
      sustenance = 1.0,
      pulseDrive = 2.1
    )
    assertTrue(stacked.isOffsetBelowKnee, "the readout rule labels the cell no-cycle")
    assertFalse(stacked.isSlowOffsetBelowKnee, "but the ODE's own offset is above the knee")
    assertFalse(stacked.isEcologicalDeath, "so the shared death guard does not fire")
    assertFalse(
      Readout.isEcologicalBurnout(stacked, fast),
      "the dispatch fact trusts a_slow / M, not the stacked-drive readout label"
    )

    // A living cell keeps that fact even when the phase slot shows the
    // mechanism reason (the display is not evidence about M).
    val displayedLocked = aliveBelowKnee.copy(
      phase = PhaseReading.Undefined(UndefinedReason.OverloadLocked)
    )
    assertTrue(displayedLocked.isMyceliumViable)
    assertFalse(displayedLocked.isOverloadLocked, "no lockout is held by this ledger")

    // Positive-spike fact: only the positive excursion counts.
    def withV(v: Double): CellReadout =
      CellReadout(
        MandateCurrent(v, -0.6, 0.05, 0.0),
        Regime.Calm,
        PhaseReading.Basin(PhaseLabel.Resting)
      )
    assertTrue(withV(1.9).hasActivePositiveSpike(), "v >= spikeLevel is a spike")
    assertFalse(
      withV(-1.9).hasActivePositiveSpike(),
      "the negative repolarisation overshoot is NOT a spike"
    )
    assertFalse(withV(1.0).hasActivePositiveSpike(), "below the threshold is not a spike")
    assertTrue(
      withV(-1.9).copy(pulseSuppressed = true).isMaintained,
      "the shelter fact uses the positive-spike convention"
    )
    assertFalse(withV(1.9).copy(pulseSuppressed = true).isMaintained)

  @Test def `the composed shelter fact keeps the historical conjunction`(): Unit =
    // Differential check over a fact matrix: the batch-2 composition must agree
    // with the pre-batch expression for every combination of its four inputs.
    val p = ReadoutParam()
    def legacy(r: CellReadout): Boolean =
      r.pulseSuppressed &&
        r.sustenance >= r.stressParam.sustenanceFloor &&
        !(r.isOffsetBelowKnee || r.isOverloadLocked) &&
        r.current.dissonance < p.spikeLevel
    var checked = 0
    for
      m <- List(0.0, 0.00005, 0.3, 1.0)
      drive <- List(0.0, 2.1)
      v <- List(-1.9, 0.0, 1.9)
      suppressed <- List(false, true)
      locked <- List(false, true)
    do
      val r = CellReadout(
        MandateCurrent(v, -0.6, 0.7, 0.0),
        Regime.Strained,
        PhaseReading.Basin(PhaseLabel.Resting),
        pulseSuppressed = suppressed,
        sustenance = m,
        pulseDrive = drive,
        pulseLedger = if locked then PulseLedger(overload = OverloadLock(100.0)) else PulseLedger.zero,
        simTime = 10.0
      )
      assertEquals(
        legacy(r),
        r.isMaintained,
        s"M=$m drive=$drive v=$v suppressed=$suppressed locked=$locked"
      )
      checked += 1
    assertEquals(4 * 2 * 3 * 2 * 2, checked, "the matrix must cover every combination")

  @Test def `the ecological death guard equals the dispatch fact below one bell kernel`(): Unit =
    // Above a single kernel the dispatch fact deliberately adds the readout
    // rule (stacked drive); at or below it a_eff == a_slow, so the guard and
    // the dispatch fact must coincide. Keep that seam pinned.
    var checked = 0
    for
      m <- List(0.0, 0.00005, 0.3, 0.59, 1.0)
      s <- List(0.05, 0.45, 0.7, 1.0)
      drive <- List(0.0, 0.8)
    do
      val r = CellReadout(
        MandateCurrent(-1.2, -0.6, s, 0.0),
        Readout.regime(MandateCurrent(-1.2, -0.6, s, 0.0)),
        PhaseReading.Basin(PhaseLabel.Resting),
        sustenance = m,
        pulseDrive = drive
      )
      assertEquals(
        r.isEcologicalDeath,
        Readout.isEcologicalBurnout(r, fast),
        s"M=$m s=$s drive=$drive"
      )
      checked += 1
    assertEquals(5 * 4 * 2, checked)



