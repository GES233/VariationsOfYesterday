package moe.chestnut.awa.voy.inner.mandate

import scala.collection.mutable.ArrayBuffer

/** One game event as the channel pulse it becomes (§1.2).
  *
  * `area` is the α-kernel area the `FirePulse` channel already takes, `tau`
  * the kernel time constant. `taps`/`gap` cover events that are a *ritual
  * count* rather than a single report: 铜钲三叩 is three firings, not one
  * three-times-larger one, and the difference is observable.
  */
final case class PulseShape(area: Double, tau: Double, taps: Int = 1, gap: Double = 0.0):
  def totalArea: Double = area * taps

  /** Peak drive of one tap: `area / (e * tau)` (the kernel's closed form). */
  def peak: Double = area / (Math.E * tau)

/** The pulse identities the author layer already names, with their doses
  * drawn from the ranges `Voy.World.Profile` declares (`alpha_shock:
  * {1, 12}`, `heavy_kick: {0.5, 6}`, `alpha_metal_resonance: {1, 12}`,
  * `OverwhelmingNegativeSuppression: {12, 40}`).
  *
  * `AlphaShockWeak` is deliberately outside the story content: it exists so
  * the naive rule "a pulse in the vulnerable window fires" can be tested
  * against a pulse inside the declared dose range that is too weak to
  * matter. See `PhaseResponseSpec`.
  */
enum PulseKind(val wire: String, val shape: PulseShape):
  case AlphaShock
      extends PulseKind("alpha_shock", PulseShape(area = 12.0, tau = 5.0))
  case HeavyKick
      extends PulseKind("heavy_kick", PulseShape(area = 5.0, tau = 10.0))
  case MetalResonance
      extends PulseKind(
        "alpha_metal_resonance",
        PulseShape(area = 2.0, tau = 2.0, taps = 3, gap = 1.0)
      )
  case Overwhelming
      extends PulseKind("OverwhelmingNegativeSuppression", PulseShape(area = -25.0, tau = 5.0))
  case AlphaShockWeak
      extends PulseKind("alpha_shock_weak", PulseShape(area = 2.0, tau = 5.0))

/** What one event did to the cycle it landed in. */
enum PulseOutcome:
  /** A genuine excursion arrived earlier than the cycle would have produced
    * it: the event itself fired the cell. */
  case Premature

  /** The next genuine excursion arrived later: the event pushed the cycle
    * back. */
  case Delayed

  /** The next genuine excursion arrived on schedule: the event fizzled. */
  case Absorbed

  /** The excursion left the normal amplitude band (the negative
    * "suppression" channel, §1.2 honest boundary). */
  case Disrupted

  /** No genuine excursion inside the observation window. */
  case Unresolved

/** One genuine excursion: the fast variable crossed the section upwards and
  * then went on to a full-amplitude peak before falling back through zero.
  *
  * The distinction from a bare section crossing is the whole point of this
  * file. A drive strong enough to hold `v` just above the detection level
  * crosses the section without producing an action potential; counting that
  * as a spike is what makes a phase-response experiment agree with any
  * hypothesis it is given.
  */
final case class Spike(at: Double, peak: Double)

/** Result of one controlled event trial. */
final case class Trial(
    phase: Double,
    delta: Option[Double],
    vPeak: Double,
    vMin: Double,
    outcome: PulseOutcome
)

/** Duty-cycle geometry of one periodic orbit relative to the phase-0
  * upcrossing (`v > section`).
  *
  *   - `plateauDuration`: time from phase 0 until `v` first drops below 0
  *     (repolarisation from the upper right branch to the lower left branch);
  *   - `plateauFraction`: `plateauDuration / period`. When this exceeds 0.50
  *     (as at `a = -0.30, s = 1.0`), cycle fraction 0.50 sits *on the upper
  *     depolarised branch* before repolarisation rather than in the recovery
  *     trough, so a positive pulse prolongs the plateau (`Delayed`) instead of
  *     exciting from rest;
  *   - `vAtMidCycle`: fast variable `v` at `phase = 0.50`.
  */
final case class DutyCycle(
    a: Double,
    period: Double,
    plateauDuration: Double,
    plateauFraction: Double,
    vAtMidCycle: Double
)

/** A controlled excitable oscillator: the fast subsystem runs, the slow
  * variable is *frozen* (`gamma = 0`) at the stress that realizes the
  * requested recovery offset `a`.
  *
  * Freezing is what makes the phase response repeatable: with the slow
  * variable drifting, the cycle period is still changing and no fixed PRC
  * exists to measure.
  */
final case class Oscillator(
    a: Double,
    fast: MandateParam = MandateAgent.DefaultFastParam,
    coupling: Double = 1.0,
    readout: ReadoutParam = ReadoutParam()
):
  def stress: Double = (fast.alpha - a) / coupling

  def fresh(): MandateState =
    MandateState(
      state = MandateCurrent(dissonance = -1.2, entrench = -0.6, stress = stress),
      param = fast,
      stressParam = StressParam(gamma = 0.0, coupling = coupling)
    )

object PhaseResponse:
  /** Section level: the same reference upcrossing `PhaseModulationSpec` and
    * [[ReadoutParam]] use. Phase 0 is defined here. */
  /** Poincaré section level, shared with the readout and the link runner. */
  val section: Double = PhaseDetection.SectionLevel

  /** Peak a genuine excursion must reach. A driven crossing that stalls
    * below this is not a spike. */
  val spikeLevel: Double = PhaseDetection.SpikeLevel

  // ==== reference cycle ==== //

  /** Settle, then return the state just after a section upcrossing (phase 0)
    * together with the mean interval between the following genuine spikes.
    */
  def reference(
      osc: Oscillator,
      dt: Double,
      warmup: Double = 3000.0,
      cycles: Int = 4,
      tLimit: Double = 9000.0
  ): (MandateCurrent, Double) =
    val st = osc.fresh()
    var t = 0.0
    while t < warmup do
      st.step(dt)
      t += dt
    // Advance to the next upcrossing: that is phase 0.
    var prev = st.getState.dissonance
    var found = false
    while !found && t < tLimit do
      st.step(dt)
      t += dt
      val v = st.getState.dissonance
      if prev <= section && v > section then found = true
      prev = v
    val snapshot = st.getState
    // Measure the cycle from the snapshot: genuine spikes until `cycles`.
    val spikes = runSpikes(osc, snapshot, dt, tLimit, None, None)
    val intervals = spikes.map(_.at).sliding(2).filter(_.size == 2).map(p => p(1) - p(0)).toList
    val period =
      if intervals.isEmpty then Double.NaN
      else intervals.take(cycles).sum / intervals.take(cycles).size
    (snapshot, period)

  /** Genuine spikes within `tWindow` after the snapshot, optionally firing
    * one event at `pulseAt` (absolute time measured from the snapshot).
    */
  def spikes(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      tWindow: Double,
      pulseAt: Option[Double],
      kind: Option[PulseKind]
  ): Seq[Spike] = runSpikes(osc, snapshot, dt, tWindow, pulseAt, kind)

  private def runSpikes(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      tWindow: Double,
      pulseAt: Option[Double],
      kind: Option[PulseKind]
  ): Seq[Spike] =
    val st = osc.fresh()
    st.state = snapshot
    kind.foreach(k => st.synapseTau = k.shape.tau)
    val out = ArrayBuffer.empty[Spike]
    var t = 0.0
    var prev = st.getState.dissonance
    var candidate = Double.NaN
    var peak = Double.MinValue
    var taps = 0
    var vMin = Double.MaxValue
    while t < tWindow do
      (pulseAt, kind) match
        case (Some(at), Some(k)) =>
          if taps < k.shape.taps && t >= at + taps * k.shape.gap then
            st.firePulse(k.shape.area)
            taps += 1
        case _ => ()
      st.step(dt)
      t += dt
      val v = st.getState.dissonance
      vMin = Math.min(vMin, v)
      if prev <= section && v > section then
        // A new candidate excursion opens at this crossing.
        candidate = t
        peak = v
      else if !candidate.isNaN then
        peak = Math.max(peak, v)
        if v < 0.0 then
          if peak >= spikeLevel then out += Spike(candidate, peak)
          candidate = Double.NaN
      prev = v
    out.toSeq

  /** The first genuine spike after the pulse, and the excursion extremes of
    * the window. */
  private def afterPulse(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      tWindow: Double,
      pulseAt: Option[Double],
      kind: Option[PulseKind]
  ): (Option[Spike], Double, Double) =
    val all = runSpikes(osc, snapshot, dt, tWindow, pulseAt, kind)
    val first = pulseAt match
      case Some(at) => all.find(_.at >= at)
      case None => all.headOption
    (first, all.map(_.peak).foldLeft(Double.MinValue)(Math.max), all.size.toDouble)

  /** Fire one event at cycle fraction `phase` (measured from the phase-0
    * snapshot, so `phase * period` is the firing time) and compare the next
    * genuine spike with the unpulsed control.
    */
  def trial(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      period: Double,
      phase: Double,
      kind: PulseKind,
      control: Option[Spike],
      tWindow: Double = 400.0
  ): Trial =
    val at = phase * period
    val (first, vPeak, _) = afterPulse(osc, snapshot, dt, tWindow, Some(at), Some(kind))
    val vMin = extremeMin(osc, snapshot, dt, at, kind, tWindow)
    (control, first) match
      case (Some(c), Some(s)) =>
        val delta = s.at - c.at
        val amplitudeMoved = Math.abs(s.peak - c.peak) > 0.5
        val outcome =
          if amplitudeMoved then PulseOutcome.Disrupted
          else if delta < -0.30 * period then PulseOutcome.Premature
          else if delta > 0.05 * period then PulseOutcome.Delayed
          else PulseOutcome.Absorbed
        Trial(phase, Some(delta), s.peak, vMin, outcome)
      case _ =>
        Trial(phase, None, vPeak, vMin, PulseOutcome.Unresolved)

  private def extremeMin(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      at: Double,
      kind: PulseKind,
      tWindow: Double
  ): Double =
    val st = osc.fresh()
    st.state = snapshot
    st.synapseTau = kind.shape.tau
    var t = 0.0
    var taps = 0
    var vMin = Double.MaxValue
    while t < tWindow do
      if taps < kind.shape.taps && t >= at + taps * kind.shape.gap then
        st.firePulse(kind.shape.area)
        taps += 1
      st.step(dt)
      t += dt
      vMin = Math.min(vMin, st.getState.dissonance)
    vMin

  /** The unpulsed reference spike, measured once per sweep. */
  def controlSpike(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      tWindow: Double = 400.0
  ): Option[Spike] =
    runSpikes(osc, snapshot, dt, tWindow, None, None).headOption

  /** Measure the depolarised-plateau duty cycle (`v > 0` duration after
    * phase-0 upcrossing) and the fast variable `v` at `phase = 0.50`.
    */
  def dutyCycle(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      period: Double
  ): DutyCycle =
    val st = osc.fresh()
    st.state = snapshot
    var t = 0.0
    var prevV = st.getState.dissonance
    var repolarisedAt = Double.NaN
    var vMid = Double.NaN
    val midT = 0.50 * period
    while t <= period + dt do
      st.step(dt)
      t += dt
      val v = st.getState.dissonance
      if repolarisedAt.isNaN && prevV >= 0.0 && v < 0.0 then
        val frac = if v == prevV then 0.0 else (0.0 - prevV) / (v - prevV)
        repolarisedAt = (t - dt) + frac * dt
      if vMid.isNaN && t >= midT then
        vMid = v
      prevV = v
    DutyCycle(
      a = osc.a,
      period = period,
      plateauDuration = repolarisedAt,
      plateauFraction = repolarisedAt / period,
      vAtMidCycle = vMid
    )

  /** Classify one event over a cycle-fraction grid. */
  def sweep(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      period: Double,
      kind: PulseKind,
      steps: Int = 40
  ): Seq[Trial] =
    val control = controlSpike(osc, snapshot, dt)
    (1 until steps).map { i =>
      val phase = i.toDouble / steps
      trial(osc, snapshot, dt, period, phase, kind, control)
    }

  /** Classify the pulse at every integer simulation-time tick in one cycle.
    * Unlike a declared window midpoint, these are the exact operations a
    * seeded dossier may name.
    */
  def tickTrials(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      period: Double,
      kind: PulseKind
  ): Seq[(Int, Trial)] =
    val control = controlSpike(osc, snapshot, dt)
    (1 until Math.ceil(period).toInt).map { tick =>
      tick -> trial(osc, snapshot, dt, period, tick.toDouble / period, kind, control)
    }

  /** Smallest and largest cycle fraction whose outcome matches. */
  def span(trials: Seq[Trial], outcome: PulseOutcome): Option[(Double, Double)] =
    val hits = trials.filter(_.outcome == outcome).map(_.phase).sorted
    if hits.isEmpty then None else Some((hits.head, hits.last))

  /** Minimum single-tap area that still fires a genuine premature spike at
    * `phase`, by bisection on the area. Returns `None` if even `maxArea`
    * cannot fire: that answer is as informative as a number.
    */
  def fireThreshold(
      osc: Oscillator,
      snapshot: MandateCurrent,
      dt: Double,
      period: Double,
      phase: Double,
      tau: Double,
      control: Option[Spike],
      maxArea: Double = 400.0,
      tol: Double = 0.5
  ): Option[Double] =
    def firesArea(area: Double): Boolean =
      val st = osc.fresh()
      st.state = snapshot
      st.synapseTau = tau
      var t = 0.0
      var prev = st.getState.dissonance
      var candidate = Double.NaN
      var peak = Double.MinValue
      var fired = false
      var taps = 0
      while t < 400.0 && !fired do
        if taps == 0 && t >= phase * period then
          st.firePulse(area)
          taps = 1
        st.step(dt)
        t += dt
        val v = st.getState.dissonance
        if prev <= section && v > section then
          candidate = t
          peak = v
        else if !candidate.isNaN then
          peak = Math.max(peak, v)
          if v < 0.0 then
            if peak >= spikeLevel && candidate > phase * period - 1e-9 &&
              control.exists(c => candidate - c.at < -0.30 * period)
            then fired = true
            candidate = Double.NaN
        prev = v
      fired
    if !firesArea(maxArea) then None
    else
      var lo = 0.0
      var hi = maxArea
      while hi - lo > tol do
        val mid = 0.5 * (lo + hi)
        if firesArea(mid) then hi = mid else lo = mid
      Some(hi)

  // ==== independent references (no ODE integration) ==== //

  /** Recovery offset at which the fast subsystem loses stability, from the
    * closed form `trace(J) = 1 - v^2 - eps*beta = 0` together with the fixed
    * point conditions. Returns `(v_c, a_c)`.
    *
    * This is *not* the old `a_c ~ 0.467` approximation (the design draft's
    * pre-correction value): that number follows from `v_c ~ -1`, which drops
    * the `eps*beta` term. The corrected draft value is 0.4350.
    */
  def hopfPoint(fast: MandateParam): (Double, Double) =
    val vc = -Math.sqrt(1.0 - fast.epsilon * fast.beta)
    val ac = fast.beta * (vc - Math.pow(vc, 3) / 3.0) - vc
    (vc, ac)

  /** Branch root of `v - v^3/3 = w` on the left (`v <= -1`) or right
    * (`v >= 1`) slow branch.
    *
    * `f(v) = v - v^3/3 - w` is strictly decreasing on both outer branches,
    * so the bracket always has `f(lo) > 0 > f(hi)` and the same bisection
    * update applies to both: `f(mid) > 0` moves the lower end up.
    */
  def branchRoot(w: Double, left: Boolean): Double =
    def f(v: Double) = v - Math.pow(v, 3) / 3.0 - w
    var lo = if left then -12.0 else 1.0
    var hi = if left then -1.0 else 12.0
    var i = 0
    while i < 120 do
      val mid = 0.5 * (lo + hi)
      if f(mid) > 0.0 then lo = mid else hi = mid
      i += 1
    0.5 * (lo + hi)

  /** Period of the limit cycle in the singular limit `eps -> 0`, by
    * integrating the slow motion along the outer branches:
    *
    * {{{
    * T = (1/eps) * Int_{-2/3}^{2/3} [ 1/(vR(w) + a - b*w) + 1/(b*w - vL(w) - a) ] dw
    * }}}
    *
    * Independent of `ODESolver`, but only asymptotic: it drops the `O(eps)`
    * jump time, so it is a *reference order of magnitude*, not a
    * verification of the measured period. The contract records both and the
    * gap between them.
    */
  def relaxationPeriod(a: Double, fast: MandateParam, steps: Int = 4000): Double =
    val knee = 2.0 / 3.0
    val h = 2.0 * knee / steps
    def integrand(w: Double): Double =
      val vr = branchRoot(w, left = false)
      val vl = branchRoot(w, left = true)
      1.0 / (vr + a - fast.beta * w) + 1.0 / (fast.beta * w - vl - a)
    var acc = integrand(-knee) + integrand(knee)
    var i = 1
    while i < steps do
      val w = -knee + i * h
      acc += (if i % 2 == 0 then 2.0 else 4.0) * integrand(w)
      i += 1
    acc * h / 3.0 / fast.epsilon
