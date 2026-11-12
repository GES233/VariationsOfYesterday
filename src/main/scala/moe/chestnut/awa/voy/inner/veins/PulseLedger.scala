package moe.chestnut.awa.voy.inner.veins

/** Parameters of the three discrete pulse mechanisms (design note 08).
  *
  * They are deliberately in one bag because they are one tuning surface, not
  * because the mechanisms are one mechanism: [[ShelterWindow]],
  * [[ToleranceBook]] and [[OverloadLock]] each own their own fact, and this
  * case class only says what a counted pulse does to each of them.
  *
  * Times are in simulation-time units (`1.0` unit = `1.0` second at 20 TPS
  * with default `tickDt = 0.05`).
  *
  * @param suppressionWindow
  *   Base duration `W_0` for which a counted pulse opens a shelter
  *   (stagnation-suppression) window, anchored to one natural FHN
  *   oscillation period (~40 s).
  * @param minSafeInterval
  *   Minimum safe interval `I_min` between consecutive counted pulses on a
  *   cell (~1.6x Bell kernel `tau = 5.0`). A pulse arriving before
  *   `minSafeInterval` has elapsed is refused and drives acute overload.
  * @param toleranceDecayTau
  *   Exponential decay time constant `tau_tol` of accumulated pulse
  *   tolerance, anchored to the cell's relaxation cycle scale.
  * @param normalToleranceStep
  *   Gentle tolerance increment added by an accepted (well-spaced) pulse.
  * @param overpulseToleranceStep
  *   Heavy tolerance increment added when a pulse is refused and drives the
  *   cell into acute overload, shrinking subsequent shelter windows.
  * @param invertTolerance
  *   Tolerance threshold at which shelter effectiveness reaches zero and any
  *   further counted pulse is refused as saturated.
  * @param maxTolerance
  *   Hard cap on accumulated tolerance.
  * @param overloadBurnoutDuration
  *   Simulation-time duration of one acute overload lockout.
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

/** Why a counted pulse was refused (the guard that fired, in evaluation
  * order). Recorded as a one-shot fact on [[PulseSettlement.Overloaded]]; it
  * is not stored in [[OverloadLock]], because the lockout's memory is its
  * deadline and the cause is an explanation of one transition, not state.
  */
enum OverloadCause:
  /** The pulse arrived before `minSafeInterval` had elapsed (cadence guard). */
  case PulseTooSoon

  /** The cell was already locked: a pulse inside a lockout re-arms it. */
  case AlreadyLocked

  /** Accumulated (decayed) tolerance had reached `invertTolerance`. */
  case ToleranceSaturated

/** Shelter (庇护), own fact: until when a counted pulse shelters this cell
  * from `沉滞` (Stagnation).
  *
  * It deliberately carries no opinion about overload: closing the window on
  * overload is a rule of the transition ([[PulseLedger.onCountedPulse]]), not
  * a mask applied here. Queries are closed-form against simulation time.
  */
final case class ShelterWindow(until: Double = Double.NegativeInfinity):
  def holdsAt(t: Double): Boolean =
    !until.isNegInfinity && !t.isNaN && t < until

  def remainingAt(t: Double): Double =
    if holdsAt(t) then until - t else 0.0

object ShelterWindow:
  /** No window was ever opened (or the last one was collapsed). */
  val closed: ShelterWindow = ShelterWindow()

/** Tolerance (耐受), own fact: accumulated pulse load that decays
  * exponentially from the last counted pulse and shrinks the shelter window a
  * later pulse would grant.
  *
  * "Never pulsed" is [[Double.NegativeInfinity]], NOT `NaN`: `-Inf == -Inf`
  * holds under IEEE/case-class equality while `NaN != NaN` breaks snapshot
  * round-trip comparisons.
  */
final case class ToleranceBook(
    amount: Double = 0.0,
    lastPulseTime: Double = Double.NegativeInfinity
):
  /** Current tolerance at simulation time `t`, after exponential decay since
    * `lastPulseTime`.
    */
  def at(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Double =
    if amount <= 0.0 || lastPulseTime.isNegInfinity || t.isNaN || t <= lastPulseTime then
      Math.max(0.0, amount)
    else if p.toleranceDecayTau <= 0.0 then 0.0
    else amount * Math.exp(-(t - lastPulseTime) / p.toleranceDecayTau)

  /** Whether the decayed tolerance has reached the invert threshold. */
  def saturatedAt(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Boolean =
    at(t, p) >= p.invertTolerance

  /** Closed-form factor `[0, 1]` by which tolerance shrinks a shelter window
    * at simulation time `t`.
    */
  def windowFactorAt(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Double =
    val tol = at(t, p)
    if p.invertTolerance <= 0.0 then 0.0
    else Math.max(0.0, 1.0 - tol / p.invertTolerance)

  /** Elapsed simulation time since the last counted pulse; `+Inf` if there
    * never was one.
    */
  def elapsedSinceLastPulseAt(t: Double): Double =
    if lastPulseTime.isNegInfinity || t.isNaN then Double.PositiveInfinity
    else t - lastPulseTime

  /** Charge one counted pulse at simulation time `t`, decaying the old load
    * first and hard-capping the result.
    */
  def chargedAt(
      t: Double,
      step: Double,
      p: PulseToleranceParam = PulseToleranceParam()
  ): ToleranceBook =
    ToleranceBook(Math.min(p.maxTolerance, at(t, p) + step), t)

object ToleranceBook:
  val empty: ToleranceBook = ToleranceBook()

/** Acute overload lockout (过载), own fact: until when the cell's fast system
  * is held in a depolarization block by pulsing too fast.
  *
  * The lockout is a mechanism rule, not a dynamical observation: it can hold
  * even while the bare trajectory would still oscillate.
  */
final case class OverloadLock(until: Double = Double.NegativeInfinity):
  def holdsAt(t: Double): Boolean =
    !until.isNegInfinity && !t.isNaN && t < until

object OverloadLock:
  val closed: OverloadLock = OverloadLock()

/** What one counted pulse did to the cell: the two cases are mutually
  * exclusive, so "sheltered" and "overloaded" can never both be produced by
  * the same arrival.
  */
enum PulseSettlement:
  /** Accepted: a shelter window of `effectiveWindow` simulation-time units
    * was opened (its start is the pulse time) and tolerance advanced.
    */
  case Sheltered(effectiveWindow: Double, toleranceAfter: Double)

  /** Refused: the cell was locked for `lockoutDuration`, any open shelter
    * window was collapsed, and tolerance spiked.
    */
  case Overloaded(cause: OverloadCause, lockoutDuration: Double, toleranceAfter: Double)

/** The three discrete pulse mechanisms of one cell, as separate facts:
  * shelter ([[ShelterWindow]]), tolerance ([[ToleranceBook]]) and acute
  * overload lockout ([[OverloadLock]]).
  *
  * Every transition is explicit here — this is the single place that says how
  * one counted pulse settles against all three — and every query is a
  * closed-form function of simulation time, so no per-tick mutation is needed
  * between pulses and the state serializes as four doubles in
  * [[MandateSnapshot]].
  *
  * Batch-1 semantics (behaviour unchanged from the former fused
  * `PulseToleranceState`, see docs/work-in-progress-mechanism-decoupling.md):
  *  - an accepted pulse opens the shelter window, leaves any expired lockout
  *    closed, and charges `normalToleranceStep`;
  *  - a refused pulse closes the shelter window, arms the lockout for
  *    `overloadBurnoutDuration`, and charges `overpulseToleranceStep`;
  *  - an open window shelters unless the cell is in an active lockout.
  */
final case class PulseLedger(
    shelter: ShelterWindow = ShelterWindow(),
    tolerance: ToleranceBook = ToleranceBook(),
    overload: OverloadLock = OverloadLock()
):
  /** Whether the cell is sheltered from `沉滞` at simulation time `t`. */
  def shelteredAt(t: Double): Boolean =
    !overloadedAt(t) && shelter.holdsAt(t)

  /** Whether the cell is in an active acute overload lockout at `t`. */
  def overloadedAt(t: Double): Boolean = overload.holdsAt(t)

  /** Remaining shelter duration at `t` (`0.0` when not sheltered). */
  def remainingShelterAt(t: Double): Double =
    if shelteredAt(t) then shelter.remainingAt(t) else 0.0

  /** Decayed tolerance at `t`. */
  def toleranceAt(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Double =
    tolerance.at(t, p)

  /** Shelter duration a would-be accepted pulse at `t` would grant given the
    * current decayed tolerance.
    */
  def effectiveWindowAt(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Double =
    p.suppressionWindow * tolerance.windowFactorAt(t, p)

  /** The guard that would refuse a pulse arriving at `t`, if any. Guards are
    * evaluated in cadence -> lockout -> saturation order (same predicates as
    * the former fused implementation).
    */
  def overloadCauseAt(
      t: Double,
      p: PulseToleranceParam = PulseToleranceParam()
  ): Option[OverloadCause] =
    if tolerance.elapsedSinceLastPulseAt(t) < p.minSafeInterval then
      Some(OverloadCause.PulseTooSoon)
    else if overloadedAt(t) then Some(OverloadCause.AlreadyLocked)
    else if tolerance.saturatedAt(t, p) then Some(OverloadCause.ToleranceSaturated)
    else None

  /** Whether a pulse arriving at `t` would be refused (too fast, already
    * locked, or tolerance-saturated).
    */
  def wouldOverloadAt(t: Double, p: PulseToleranceParam = PulseToleranceParam()): Boolean =
    overloadCauseAt(t, p).isDefined

  /** Settle one COUNTED pulse at simulation time `t`: this is the explicit
    * transition table of the three mechanisms. Callers that only fire a
    * physical pulse (`PulseBookkeeping.PhysicsOnly`) never reach here.
    */
  def onCountedPulse(
      t: Double,
      p: PulseToleranceParam = PulseToleranceParam()
  ): (PulseLedger, PulseSettlement) =
    overloadCauseAt(t, p) match
      case Some(cause) =>
        // Refused: the overload rule collapses the shelter window as part of
        // this transition (no later query has to mask it), re-arms the lock
        // and spikes the tolerance.
        val tol = tolerance.chargedAt(t, p.overpulseToleranceStep, p)
        val next = PulseLedger(
          shelter = ShelterWindow.closed,
          tolerance = tol,
          overload = OverloadLock(t + p.overloadBurnoutDuration)
        )
        (next, PulseSettlement.Overloaded(cause, p.overloadBurnoutDuration, tol.amount))
      case None =>
        // Accepted: open the window, clear any expired lockout, charge the
        // gentle tolerance step.
        val window = effectiveWindowAt(t, p)
        val tol = tolerance.chargedAt(t, p.normalToleranceStep, p)
        val next = PulseLedger(
          shelter = ShelterWindow(t + window),
          tolerance = tol,
          overload = OverloadLock.closed
        )
        (next, PulseSettlement.Sheltered(window, tol.amount))

object PulseLedger:
  val zero: PulseLedger = PulseLedger()
