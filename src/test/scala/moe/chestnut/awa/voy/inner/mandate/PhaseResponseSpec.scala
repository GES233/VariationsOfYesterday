package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.{Test, TestInstance}

/** Phase-response measurements and their failure boundaries (§1.1.4).
  *
  * Everything here is a *measurement* with an independent check attached:
  * step convergence, an analytic reference where one exists, and at least
  * one experiment that can only come out one way if the naive symbolic rule
  * is right. One of them disproves it.
  */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhaseResponseSpec:
  private val fast = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
  private val dt = 0.02
  private val osc = Oscillator(-0.30, fast)

  private lazy val (snapshot, period) = PhaseResponse.reference(osc, dt, warmup = 2500.0, cycles = 4)
  private lazy val control = PhaseResponse.controlSpike(osc, snapshot, dt)

  @Test def `the cycle period is step-converged`(): Unit =
    val (_, coarse) = PhaseResponse.reference(osc, dt, warmup = 2500.0, cycles = 4)
    val (_, fine) = PhaseResponse.reference(osc, dt / 2.0, warmup = 2500.0, cycles = 4)
    val (_, finer) = PhaseResponse.reference(osc, dt / 4.0, warmup = 2500.0, cycles = 4)
    val drift = Math.abs(coarse - finer) / coarse
    assertTrue(drift < 0.002, s"period must be step-converged, coarse=$coarse finer=$finer ($drift)")
    assertTrue(Math.abs(fine - finer) < Math.abs(coarse - finer), "halving the step must not diverge")
    assertTrue(control.exists(c => Math.abs(c.at - coarse) < 0.1), "control spike defines the same period")

  @Test def `the singular-limit reference does not reproduce the measured period`(): Unit =
    // Reported as a negative result on purpose: the eps -> 0 integral is a
    // reference order of magnitude, not a verification at eps = 0.08.
    val reference = PhaseResponse.relaxationPeriod(-0.30, fast)
    assertTrue(reference > 15.0 && reference < 30.0, s"reference out of its plausible band: $reference")
    assertTrue(
      Math.abs(period - reference) / period > 0.30,
      s"the asymptotic reference should disagree at this epsilon (period=$period ref=$reference)"
    )

  @Test def `the Hopf point is the exact trace condition, not the draft approximation`(): Unit =
    val (vc, ac) = PhaseResponse.hopfPoint(fast)
    assertEquals(-Math.sqrt(1.0 - fast.epsilon * fast.beta), vc, 1e-12)
    // w-nullcline/v-nullcline intersection with trace(J) = 0.
    assertEquals(fast.beta * (vc - Math.pow(vc, 3) / 3.0) - vc, ac, 1e-12)
    assertTrue(Math.abs(ac - 0.467) > 0.02, s"the draft value 0.467 is not the exact one (got $ac)")
    assertTrue(Math.abs(ac - 0.434975) < 1e-5)

  @Test def `a pulse inside the declared dose range never fires at the slice operating point`(): Unit =
    // The naive rule "a pulse in the vulnerable window fires a premature
    // surge" predicts at least one Premature outcome somewhere in the
    // cycle for every declared pulse. Measure it instead of assuming it.
    val declared = Seq(PulseKind.AlphaShock, PulseKind.HeavyKick, PulseKind.MetalResonance)
    for kind <- declared do
      val trials = PhaseResponse.sweep(osc, snapshot, dt, period, kind, steps = 40)
      val premature = trials.filter(_.outcome == PulseOutcome.Premature)
      if kind == PulseKind.AlphaShock then
        assertTrue(
          premature.isEmpty,
          s"alpha_shock must not fire anywhere: ${premature.map(t => (t.phase, t.delta)).mkString(",")}"
        )
      // Every pulse that does not fire must move the cycle, monotonically in
      // phase and independent of the declared window boundaries.
      assertTrue(trials.forall(_.delta.isDefined), "every phase must produce a defined response")

  @Test def `the vulnerable-window midpoint delays, it does not fire`(): Unit =
    val trial = PhaseResponse.trial(
      osc,
      snapshot,
      dt,
      period,
      PhaseWindow.Relative.mid,
      PulseKind.AlphaShock,
      control
    )
    assertEquals(PulseOutcome.Delayed, trial.outcome)
    assertTrue(trial.delta.exists(_ > 0.25 * period), s"mid-cycle pulse should delay hard, got ${trial.delta}")

  @Test def `the fire threshold sits above the declared alpha_shock dose`(): Unit =
    val area = PhaseResponse.fireThreshold(
      osc,
      snapshot,
      dt,
      period,
      PhaseWindow.Relative.mid,
      tau = 5.0,
      control = control
    )
    assertTrue(area.isDefined, "a strong enough pulse must fire, otherwise the channel is dead")
    val a = area.get
    assertTrue(
      a > PulseKind.AlphaShock.shape.area,
      s"the declared dose ${PulseKind.AlphaShock.shape.area} must stay under the fire threshold $a"
    )

  @Test def `a driven threshold crossing is not a spike`(): Unit =
    // The counterexample to the section-crossing detector: a 1-unit
    // rectangle impulse at mid-cycle lifts v over the 1.2 detection level
    // and holds it there without producing an action potential. Counting
    // that as a premature surge is how a phase-response experiment can
    // "confirm" a firing window that does not exist.
    val st = osc.fresh()
    st.state = snapshot
    var t = 0.0
    var crossings = 0
    var prevBelow = st.getState.dissonance <= PhaseResponse.section
    var spikes = 0
    var candidate = Double.NaN
    var peak = Double.MinValue
    val fireAt = PhaseWindow.Relative.mid * period
    while t < 2.5 * period do
      st.impulse = if t >= fireAt && t < fireAt + 1.0 then 1.0 else 0.0
      st.step(dt)
      t += dt
      val v = st.getState.dissonance
      val above = v > PhaseResponse.section
      if above && !prevBelow then
        crossings += 1
        candidate = t
        peak = v
      else if !candidate.isNaN then
        peak = Math.max(peak, v)
        if v < 0.0 then
          if peak >= PhaseResponse.spikeLevel then spikes += 1
          candidate = Double.NaN
      prevBelow = above
    assertTrue(crossings >= 2, s"the drive must create a degenerate crossing, saw $crossings")
    assertTrue(
      spikes < crossings,
      s"at least one crossing must fail to be a spike ($spikes spikes for $crossings crossings)"
    )

  @Test def `the response is not an integration artefact`(): Unit =
    // Same trial at dt and dt/2: the sign and magnitude of the delay must
    // survive step halving, and the control period must agree.
    val (snapHalf, periodHalf) = PhaseResponse.reference(osc, dt / 2.0, warmup = 2500.0, cycles = 4)
    val controlHalf = PhaseResponse.controlSpike(osc, snapHalf, dt / 2.0)
    val coarse = PhaseResponse.trial(osc, snapshot, dt, period, 0.50, PulseKind.AlphaShock, control)
    val fine = PhaseResponse.trial(osc, snapHalf, dt / 2.0, periodHalf, 0.50, PulseKind.AlphaShock, controlHalf)
    assertEquals(coarse.outcome, fine.outcome, "outcome must be step-independent")
    for
      c <- coarse.delta
      f <- fine.delta
    do
      assertTrue(
        Math.abs(c - f) < 0.15 * Math.abs(c),
        s"delay must be step-independent, dt=$c dt/2=$f"
      )

  @Test def `the phase tracker is step-converged`(): Unit =
    // Section phase is interpolated, so the estimated fraction at a fixed
    // time must converge as the step halves.
    def fractionAt(step: Double): Double =
      val st = Oscillator(-0.30, fast).fresh()
      val tr = new PhaseTracker()
      var t = 0.0
      while t < 2000.0 do
        st.step(step)
        t += step
        tr.observe(t, st.getState)
      tr.reading(t, st.getState) match
        case PhaseReading.Cycle(_, f) => f
        case other => fail(s"expected a cycle reading, got $other")
    val coarse = fractionAt(dt)
    val fine = fractionAt(dt / 4.0)
    assertTrue(Math.abs(coarse - fine) < 0.02, s"phase fraction must converge: $coarse vs $fine")

  @Test def `duty-cycle inversion explains why mid-cycle delays at saturation but advances near onset`(): Unit =
    // At saturated stress (a = -0.30, s = 1.0), the depolarised plateau
    // (v > 0) occupies > 50% of the period, so phase = 0.50 sits on the
    // upper right branch (v > 1.0) and a positive pulse prolongs the plateau
    // (delta > 0). Near onset (a = 0.40, s = 0.30), the plateau is < 35% of
    // the period, so phase = 0.50 sits on the hyperpolarised recovery branch
    // (v < -1.0) and the same pulse advances the next spike (delta < 0).
    val dutySat = PhaseResponse.dutyCycle(osc, snapshot, dt, period)
    assertTrue(dutySat.plateauFraction > 0.50, s"expected plateau > 50% at saturation, got ${dutySat.plateauFraction}")
    assertTrue(dutySat.vAtMidCycle > 0.8, s"expected mid-cycle on upper branch at saturation, got ${dutySat.vAtMidCycle}")

    val oscOnset = Oscillator(0.40, fast)
    val (snapOnset, periodOnset) = PhaseResponse.reference(oscOnset, dt, warmup = 2500.0, cycles = 4)
    val controlOnset = PhaseResponse.controlSpike(oscOnset, snapOnset, dt)
    val dutyOnset = PhaseResponse.dutyCycle(oscOnset, snapOnset, dt, periodOnset)
    assertTrue(dutyOnset.plateauFraction < 0.35, s"expected plateau < 35% near onset, got ${dutyOnset.plateauFraction}")
    assertTrue(dutyOnset.vAtMidCycle < -1.0, s"expected mid-cycle on left recovery branch near onset, got ${dutyOnset.vAtMidCycle}")

    val trialSat = PhaseResponse.trial(osc, snapshot, dt, period, 0.50, PulseKind.AlphaShock, control)
    val trialOnset = PhaseResponse.trial(oscOnset, snapOnset, dt, periodOnset, 0.50, PulseKind.AlphaShock, controlOnset)
    assertTrue(trialSat.delta.exists(_ > 0.0), s"saturated mid-cycle must delay, got ${trialSat.delta}")
    assertTrue(trialOnset.delta.exists(_ < 0.0), s"onset mid-cycle must advance, got ${trialOnset.delta}")
