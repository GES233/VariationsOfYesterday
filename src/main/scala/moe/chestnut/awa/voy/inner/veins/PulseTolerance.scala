package moe.chestnut.awa.voy.inner.veins

/** Parameters governing pulse-driven stagnation suppression ("heartbeat"
  * maintenance) and over-pulse tolerance / acute `BurnoutNoCycle` lockout
  * (design note 08).
  *
  * Times are in simulation-time units (`1.0` unit = `1.0` second at 20 TPS
  * with default `tickDt = 0.05`).
  *
  * @param suppressionWindow
  *   Base duration `W_0` for which a valid pulse suppresses `沉滞`
  *   (Stagnation), anchored to one natural FHN oscillation period (~40 s).
  * @param minSafeInterval
  *   Minimum safe interval `I_min` between consecutive pulses on a cell
  *   (~1.6x Bell kernel `tau = 5.0`). A pulse arriving before `minSafeInterval`
  *   has elapsed triggers acute `BurnoutNoCycle` overload.
  * @param toleranceDecayTau
  *   Exponential decay time constant `tau_tol` of accumulated pulse tolerance,
  *   anchored to the cell's relaxation cycle scale.
  * @param normalToleranceStep
  *   Gentle tolerance increment added by a well-spaced pulse.
  * @param overpulseToleranceStep
  *   Heavy tolerance increment added when pulsing too fast (`BurnoutNoCycle`
  *   overload), shrinking subsequent suppression windows.
  * @param invertTolerance
  *   Tolerance threshold at which suppression effectiveness reaches zero and
  *   any further pulse triggers overload lockout.
  * @param maxTolerance
  *   Hard cap on accumulated tolerance.
  * @param overloadBurnoutDuration
  *   Simulation-time duration for which an over-pulsed cell stays locked in
  *   acute `BurnoutNoCycle` depolarization block.
  * @param overloadPenaltyTicks
  *   Server-tick duration of the immediate `voy:pulse_saturation` penalty
  *   applied to players when over-pulsing.
  * @param singlePulsePeak
  *   Peak kernel output of a single standard Bell strike (`area = 12, tau = 5`:
  *   `12 / (e * 5) ≈ 0.8829`). Stacked kernel drive above this threshold
  *   pulls the effective recovery offset `a_eff` toward `burnoutA`.
  * @param pulseBurnoutGain
  *   Gain on excess pulse drive `max(p - singlePulsePeak, 0)` in `a_eff`.
  */
final case class PulseToleranceParam(
    suppressionWindow: Double = 40.0,
    minSafeInterval: Double = 8.0,
    toleranceDecayTau: Double = 45.0,
    normalToleranceStep: Double = 0.10,
    overpulseToleranceStep: Double = 0.35,
    invertTolerance: Double = 1.0,
    maxTolerance: Double = 1.5,
    overloadBurnoutDuration: Double = 8.0,
    overloadPenaltyTicks: Int = 120,
    singlePulsePeak: Double = 12.0 / (Math.E * 5.0),
    pulseBurnoutGain: Double = 1.6
)

/** Outcome of one positive pulse arriving at a cell's tolerance tracker. */
enum PulseStrikeVerdict:
  /** Valid heartbeat pulse: suppresses stagnation for `effectiveWindow`
    * simulation-time units and gently advances tolerance to `nextTolerance`.
    */
  case Suppressed(effectiveWindow: Double, nextTolerance: Double)

  /** Pulse arrived too fast (or while tolerance-saturated / already in
    * overload): drives the cell into acute `BurnoutNoCycle` for
    * `burnoutDuration`, collapses any active stagnation suppression window,
    * and spikes tolerance to `nextTolerance`.
    */
  case Overloaded(nextTolerance: Double, burnoutDuration: Double)

/** Pure, immutable per-cell state tracking the heartbeat suppression window,
  * acute over-pulse burnout lockout, and exponentially decaying tolerance.
  *
  * All time queries are evaluated in closed form against `simTime`, so no
  * per-tick mutation is needed between pulses and the state serializes as
  * four doubles in [[MandateSnapshot]].
  *
  * "Never happened" is [[Double.NegativeInfinity]], NOT `NaN`: `-Inf == -Inf`
  * holds under IEEE/case-class equality while `NaN != NaN` breaks snapshot
  * round-trip comparisons.
  */
final case class PulseToleranceState(
    lastPulseTime: Double = Double.NegativeInfinity,
    suppressedUntil: Double = Double.NegativeInfinity,
    overloadUntil: Double = Double.NegativeInfinity,
    tolerance: Double = 0.0
):
  /** Current tolerance at simulation time `t`, after exponential decay since
    * `lastPulseTime`.
    */
  def toleranceAt(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Double =
    if tolerance <= 0.0 || lastPulseTime.isNegInfinity || t.isNaN || t <= lastPulseTime then
      Math.max(0.0, tolerance)
    else if p.toleranceDecayTau <= 0.0 then 0.0
    else tolerance * Math.exp(-(t - lastPulseTime) / p.toleranceDecayTau)

  /** Whether the cell is currently locked in acute pulse-overload
    * depolarization block (`BurnoutNoCycle`) at simulation time `t`.
    */
  def isOverloadedAt(t: Double): Boolean =
    !overloadUntil.isNegInfinity && !t.isNaN && t < overloadUntil

  /** Whether an active pulse suppression window is sheltering the cell from
    * `沉滞` (Stagnation) at simulation time `t`.
    */
  def isSuppressedAt(t: Double): Boolean =
    !isOverloadedAt(t) && !suppressedUntil.isNegInfinity && !t.isNaN && t < suppressedUntil

  /** Remaining stagnation-suppression duration at simulation time `t`. */
  def remainingSuppressionAt(t: Double): Double =
    if isSuppressedAt(t) then suppressedUntil - t else 0.0

  /** Effective suppression duration that a well-spaced pulse at time `t`
    * grants given the current decayed tolerance.
    */
  def effectiveWindowAt(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Double =
    val tol = toleranceAt(t, p)
    val factor =
      if p.invertTolerance <= 0.0 then 0.0
      else Math.max(0.0, 1.0 - tol / p.invertTolerance)
    p.suppressionWindow * factor

  /** Whether a pulse arriving at simulation time `t` would be too fast or hit
    * tolerance saturation.
    */
  def wouldOverloadAt(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Boolean =
    val tolNow = toleranceAt(t, p)
    val dt =
      if lastPulseTime.isNegInfinity || t.isNaN then Double.PositiveInfinity
      else t - lastPulseTime
    dt < p.minSafeInterval || isOverloadedAt(t) || tolNow >= p.invertTolerance

  /** Transition the tracker when a positive pulse arrives at simulation time `t`. */
  def onPulse(
      t: Double,
      p: PulseToleranceParam = PulseToleranceParam()
  ): (PulseToleranceState, PulseStrikeVerdict) =
    val tolNow = toleranceAt(t, p)
    if wouldOverloadAt(t, p) then
      val nextTol = Math.min(p.maxTolerance, tolNow + p.overpulseToleranceStep)
      val nextState = PulseToleranceState(
        lastPulseTime = t,
        suppressedUntil = Double.NegativeInfinity,
        overloadUntil = t + p.overloadBurnoutDuration,
        tolerance = nextTol
      )
      (nextState, PulseStrikeVerdict.Overloaded(nextTol, p.overloadBurnoutDuration))
    else
      val window = effectiveWindowAt(t, p)
      val nextTol = Math.min(p.maxTolerance, tolNow + p.normalToleranceStep)
      val nextState = PulseToleranceState(
        lastPulseTime = t,
        suppressedUntil = t + window,
        overloadUntil = Double.NegativeInfinity,
        tolerance = nextTol
      )
      (nextState, PulseStrikeVerdict.Suppressed(window, nextTol))

object PulseToleranceState:
  val zero: PulseToleranceState = PulseToleranceState()
