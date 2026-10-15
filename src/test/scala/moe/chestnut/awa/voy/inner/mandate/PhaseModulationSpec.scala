package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Phase-modulation regression gate (design draft section 1.1.4).
  *
  * Impulses on the fast variable (ritual bells, modulator devices) are
  * phase-sensitive: inside the vulnerable window (~40-60% of the inter-surge
  * period) they fire a premature surge; in the refractory phase and late
  * recovery they are absorbed. A single strong kick on a resting cell rings
  * down after exactly one excursion (the early-game "omen" event).
  *
  * Method: phase 0 is an upcrossing of v > 1.2 (mid-upstroke, steep and
  * unambiguous); the period T is the mean of several consecutive inter-surge
  * intervals; each trial restores a snapshot at phase 0 and measures the
  * interval containing the impulse.
  */
class PhaseModulationSpec:
  private val dt = 0.02

  private def freshState(): MandateState =
    MandateState(
      state = MandateCurrent(dissonance = -1.2, entrench = -0.6),
      param = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
    )

  private val crossLevel = 1.2
  private val refractory = 5.0

  /** Warm up, then find `n` consecutive upcrossings; return (state at the
    * last upcrossing, mean interval).
    */
  private def phaseZero(u: Double, n: Int): (MandateCurrent, Double) =
    val st = freshState()
    var t = 0.0
    while t < 3000.0 do
      st.disturbance = u
      st.step(dt)
      t += dt
    val crossings = scala.collection.mutable.ArrayBuffer.empty[Double]
    var lastCross = Double.NegativeInfinity
    var prevBelow = true
    while crossings.size < n && t < 9000.0 do
      st.disturbance = u
      st.step(dt)
      t += dt
      val above = st.getState.dissonance > crossLevel
      if above && prevBelow && t - lastCross > refractory then
        crossings += t
        lastCross = t
      prevBelow = !above
    val intervals = crossings.sliding(2).map(p => p(1) - p(0)).toList
    (st.getState, intervals.sum / intervals.size)

  private final case class Trial(
      interval: Option[Double],
      vMaxInWindow: Double
  )

  /** Restore snapshot (phase 0 at an upcrossing). Find the next two
    * upcrossings t1, t2 (t1 is the surge right after phase 0); fire the
    * impulse at phi after t1; report t2 - t1.
    */
  private def trial(
      snapshot: MandateCurrent,
      u: Double,
      phi: Double,
      dur: Double,
      amp: Double,
      tWindow: Double
  ): Trial =
    val st = freshState()
    st.state = snapshot
    var t = 0.0
    var lastCross = Double.NegativeInfinity
    var prevBelow = true
    var t1 = Double.NaN
    var t2 = Double.NaN
    var vMax = Double.MinValue
    while t < tWindow && (t2.isNaN || t2 < t1 + phi) do
      st.disturbance = u
      if !t1.isNaN && t >= t1 + phi && t < t1 + phi + dur then st.impulse = amp
      else st.impulse = 0.0
      st.step(dt)
      t += dt
      val v = st.getState.dissonance
      if !t1.isNaN && t2.isNaN then vMax = Math.max(vMax, v)
      val above = v > crossLevel
      if above && prevBelow && t - lastCross > refractory then
        if t1.isNaN then t1 = t
        else t2 = t
        lastCross = t
      prevBelow = !above
    Trial(if t2.isNaN then None else Some(t2 - t1), vMax)

  @Test def `impulse in the vulnerable window fires a premature surge`(): Unit =
    val (snapshot, period) = phaseZero(u = 0.05, n = 6)
    val r = trial(snapshot, u = 0.05, phi = 0.5 * period, dur = 1.0, amp = 1.0, tWindow = 400.0)
    assertTrue(r.interval.isDefined, "a surge must follow the impulse")
    assertTrue(
      r.interval.get < 0.65 * period,
      s"impulse at mid-period should fire a premature surge (interval ${r.interval.get} vs period $period)"
    )
    assertTrue(r.vMaxInWindow > 1.5, "the premature excursion must be a full surge")

  @Test def `impulse during the refractory phase is absorbed`(): Unit =
    val (snapshot, period) = phaseZero(u = 0.05, n = 6)
    val control = trial(snapshot, u = 0.05, phi = -10.0, dur = 0.0, amp = 0.0, tWindow = 400.0)
    val r = trial(snapshot, u = 0.05, phi = 0.15 * period, dur = 1.0, amp = 1.0, tWindow = 400.0)
    assertTrue(
      Math.abs(r.interval.get - control.interval.get) < 0.1 * period,
      s"early-phase impulse should be absorbed (interval ${r.interval.get} vs control ${control.interval.get})"
    )

  @Test def `impulse during late recovery is absorbed`(): Unit =
    val (snapshot, period) = phaseZero(u = 0.05, n = 6)
    val control = trial(snapshot, u = 0.05, phi = -10.0, dur = 0.0, amp = 0.0, tWindow = 400.0)
    val r = trial(snapshot, u = 0.05, phi = 0.75 * period, dur = 1.0, amp = 1.0, tWindow = 400.0)
    assertTrue(
      Math.abs(r.interval.get - control.interval.get) < 0.15 * period,
      s"late-phase impulse should be absorbed (interval ${r.interval.get} vs control ${control.interval.get})"
    )

  @Test def `single strong kick on a resting cell rings down after one excursion`(): Unit =
    val st = freshState()
    var t = 0.0
    var crossings = 0
    var lastCross = Double.NegativeInfinity
    var prevBelow = true
    var maxV = 0.0
    while t < 300.0 do
      st.impulse = if t < 1.0 then 1.0 else 0.0
      st.step(dt)
      t += dt
      val v = st.getState.dissonance
      maxV = Math.max(maxV, v)
      val above = v > crossLevel
      if above && prevBelow && t - lastCross > refractory then
        crossings += 1
        lastCross = t
      prevBelow = !above
    assertEquals(1, crossings, "one kick must produce exactly one excursion")
    assertTrue(maxV > 1.5, "the excursion must be a full surge")
    assertTrue(
      Math.abs(st.getState.dissonance - -1.185) < 0.2,
      s"fast variable should settle back near rest, got ${st.getState.dissonance}"
    )
    assertTrue(st.getState.stress < 0.05, "a single omen must not build up stress")

  /** Alpha-kernel trial: fire one pulse of area `area` at phi after the
    * reference upcrossing; kernel time constant set on the fresh state.
    */
  private def alphaTrial(
      snapshot: MandateCurrent,
      u: Double,
      phi: Double,
      area: Double,
      tau: Double,
      tWindow: Double
  ): Trial =
    val st = freshState()
    st.synapseTau = tau
    st.state = snapshot
    var t = 0.0
    var lastCross = Double.NegativeInfinity
    var prevBelow = true
    var t1 = Double.NaN
    var t2 = Double.NaN
    var vMax = Double.MinValue
    var fired = false
    while t < tWindow && (t2.isNaN || t2 < t1 + phi) do
      st.disturbance = u
      if !t1.isNaN && !fired && t >= t1 + phi then
        st.firePulse(area)
        fired = true
      st.step(dt)
      t += dt
      val v = st.getState.dissonance
      if !t1.isNaN && t2.isNaN then vMax = Math.max(vMax, v)
      val above = v > crossLevel
      if above && prevBelow && t - lastCross > refractory then
        if t1.isNaN then t1 = t
        else t2 = t
        lastCross = t
      prevBelow = !above
    Trial(if t2.isNaN then None else Some(t2 - t1), vMax)

  @Test def `alpha kernel peaks at tau with height area over e times tau`(): Unit =
    val st = freshState()
    st.synapseTau = 5.0
    st.firePulse(area = 1.0)
    var t = 0.0
    var peak = 0.0
    var peakT = 0.0
    var area = 0.0
    while t < 60.0 do
      st.step(dt)
      t += dt
      area += st.synapseOutput * dt
      if st.synapseOutput > peak then
        peak = st.synapseOutput
        peakT = t
    assertEquals(5.0, peakT, 0.15, "kernel must peak at t = tau")
    assertEquals(1.0 / (Math.E * 5.0), peak, 1e-3, "peak height must be area / (e * tau)")
    assertEquals(1.0, area, 0.01, "kernel area must equal the fired area")

  @Test def `alpha pulse in the vulnerable window fires a premature surge`(): Unit =
    val (snapshot, period) = phaseZero(u = 0.05, n = 6)
    // peak-normalized: area = peak * e * tau keeps the peak drive at 1.0
    val r = alphaTrial(snapshot, u = 0.05, phi = 0.5 * period, area = Math.E * 5.0, tau = 5.0, tWindow = 400.0)
    assertTrue(r.interval.isDefined, "a surge must follow the pulse")
    assertTrue(
      r.interval.get < 0.65 * period,
      s"mid-period pulse should fire a premature surge (interval ${r.interval.get} vs period $period)"
    )
    assertTrue(r.vMaxInWindow > 1.5, "the premature excursion must be a full surge")

  @Test def `alpha pulse in the refractory phase delays the next surge`(): Unit =
    val (snapshot, period) = phaseZero(u = 0.05, n = 6)
    val control = alphaTrial(snapshot, u = 0.05, phi = -10.0, area = 0.0, tau = 5.0, tWindow = 400.0)
    val r = alphaTrial(snapshot, u = 0.05, phi = 0.2 * period, area = Math.E * 5.0, tau = 5.0, tWindow = 400.0)
    assertTrue(
      r.interval.get > control.interval.get + 0.05 * period,
      s"refractory-phase pulse should delay the next surge (interval ${r.interval.get} vs control ${control.interval.get})"
    )
    assertTrue(
      r.vMaxInWindow > 1.5,
      "the delayed surge must still be a full surge (delay, not cancellation)"
    )

  @Test def `semantic readout traverses all four action-potential phases aligned with timing windows`(): Unit =
    val (snapshot, period) = phaseZero(u = 0.05, n = 6)
    val st = freshState()
    st.state = snapshot
    st.disturbance = 0.05

    // Step through one full oscillation cycle and record which phases appear and when.
    var t = 0.0
    val seenPhases = scala.collection.mutable.Set.empty[ExcitablePhase]
    var phaseAt22Pct: Option[ExcitablePhase] = None
    var phaseAt50Pct: Option[ExcitablePhase] = None

    while t < period do
      val readout = MandateReadout.classify(st.getState)
      assertEquals(StressRegime.Oscillating, readout.regime, "self-sustained cell should read as Oscillating")
      seenPhases += readout.phase
      if phaseAt22Pct.isEmpty && t >= 0.22 * period then phaseAt22Pct = Some(readout.phase)
      if phaseAt50Pct.isEmpty && t >= 0.50 * period then phaseAt50Pct = Some(readout.phase)
      st.step(dt)
      t += dt

    assertEquals(
      Some(ExcitablePhase.AbsoluteRefractory),
      phaseAt22Pct,
      "22% period (delay window) must classify as AbsoluteRefractory"
    )
    assertEquals(
      Some(ExcitablePhase.RelativeRefractory),
      phaseAt50Pct,
      "50% period (vulnerable window) must classify as RelativeRefractory"
    )
    assertTrue(
      seenPhases.contains(ExcitablePhase.Excitation) && seenPhases.contains(ExcitablePhase.Resting),
      s"cycle should traverse Excitation and Resting as well, saw $seenPhases"
    )

  @Test def `consecutive refractory-phase pulses cumulatively stretch cycle periods and reduce surge count`(): Unit =
    val (snapshot, period) = phaseZero(u = 0.05, n = 6)

    // Compare 4 cycles of unmodulated self-sustained oscillation against 4 cycles
    // where the player strikes the bell once in each AbsoluteRefractory window
    // (~20% period after each surge upcrossing).
    def runCycles(strikeBellInRefractory: Boolean): (Int, Double) =
      val st = freshState()
      st.synapseTau = 5.0
      st.state = snapshot
      st.disturbance = 0.0
      var t = 0.0
      val tEnd = 4.2 * period
      var lastCross = 0.0 // snapshot is at an upcrossing
      var prevBelow = false
      var armedForThisCycle = true
      val crossings = scala.collection.mutable.ArrayBuffer[Double](0.0)

      while t < tEnd do
        if strikeBellInRefractory && armedForThisCycle && (t - lastCross) >= 0.20 * period then
          st.firePulse(Math.E * 5.0)
          armedForThisCycle = false
        st.step(dt)
        t += dt
        val above = st.getState.dissonance > crossLevel
        if above && prevBelow && t - lastCross > refractory then
          crossings += t
          lastCross = t
          armedForThisCycle = true
        prevBelow = !above
      val intervals = crossings.sliding(2).map(p => p(1) - p(0)).toList
      (crossings.size - 1, intervals.sum / intervals.size)

    val (unmodulatedSpikes, unmodulatedMeanPeriod) = runCycles(strikeBellInRefractory = false)
    val (modulatedSpikes, modulatedMeanPeriod) = runCycles(strikeBellInRefractory = true)

    assertTrue(
      modulatedSpikes < unmodulatedSpikes,
      s"consecutive refractory pulses should reduce surge count in fixed window ($modulatedSpikes vs $unmodulatedSpikes)"
    )
    assertTrue(
      modulatedMeanPeriod > unmodulatedMeanPeriod + 0.10 * period,
      s"consecutive refractory pulses must cumulatively stretch mean period ($modulatedMeanPeriod vs $unmodulatedMeanPeriod)"
    )

