package moe.chestnut.awa.voy.inner.veins

/** Tuning parameters for the discrete pulse shelter window and cadence guard.
  *
  * Times are in simulation-time units (`1.0` unit = `1.0` second at 20 TPS
  * with default `tickDt = 0.05`).
  *
  * @param shelterWindow
  *   Duration `W_0` for which an accepted counted pulse opens a shelter
  *   (stagnation-suppression) window (~40 s, one natural FHN oscillation period).
  * @param minInterval
  *   Minimum safe interval `I_min` (cooldown) between consecutive counted
  *   pulses on a cell (~1.6x Bell kernel `tau = 5.0`). A pulse arriving before
  *   `minInterval` has elapsed is refused, collapses any open shelter window,
  *   and refreshes the cooldown.
  * @param overloadPenaltyTicks
  *   Server-tick duration of the immediate `voy:pulse_saturation` backlash
  *   applied to players when striking inside the cooldown window.
  */
final case class PulseParam(
    shelterWindow: Double = 40.0,
    minInterval: Double = 8.0,
    overloadPenaltyTicks: Int = 120
)

/** Outcome of settling one counted pulse against a cell's [[PulseStamp]]. */
enum PulseSettlement:
  /** Accepted: a shelter window of `windowDuration` simulation-time units was opened. */
  case Sheltered(windowDuration: Double)

  /** Refused: the pulse arrived inside the cooldown window; shelter was collapsed
    * and the cooldown was refreshed for `cooldownDuration`.
    */
  case CooldownRefused(cooldownDuration: Double)

/** Per-cell discrete pulse memory: two closed-form deadlines on `simTime`.
  *
  *   - `shelterUntil`: until when this cell is sheltered from `沉滞` (Stagnation);
  *   - `cooldownUntil`: until when this cell is in refractory pulse cooldown.
  *
  * "Never pulsed / closed" is represented by [[Double.NegativeInfinity]] so
  * standard IEEE equality (`-Inf == -Inf`) holds across snapshot round-trips.
  */
final case class PulseStamp(
    shelterUntil: Double = Double.NegativeInfinity,
    cooldownUntil: Double = Double.NegativeInfinity
):
  /** Whether the cell has an active shelter window at simulation time `t`. */
  def isShelteredAt(t: Double): Boolean =
    !shelterUntil.isNegInfinity && !t.isNaN && t < shelterUntil

  /** Remaining shelter duration at `t` (`0.0` when not sheltered). */
  def remainingShelterAt(t: Double): Double =
    if isShelteredAt(t) then shelterUntil - t else 0.0

  /** Whether a new counted pulse arriving at `t` would fall inside the cooldown. */
  def isInCooldownAt(t: Double): Boolean =
    !cooldownUntil.isNegInfinity && !t.isNaN && t < cooldownUntil

  /** Remaining cooldown duration at `t` (`0.0` when ready). */
  def remainingCooldownAt(t: Double): Double =
    if isInCooldownAt(t) then cooldownUntil - t else 0.0

  /** Settle one COUNTED pulse at simulation time `t`.
    *
    *   - If in cooldown (`isInCooldownAt(t)`): refuses the pulse, collapses any
    *     open shelter window, and refreshes the cooldown to `t + p.minInterval`.
    *   - Otherwise: opens a fresh shelter window `t + p.shelterWindow` and
    *     starts the cooldown `t + p.minInterval`.
    */
  def onCountedPulse(
      t: Double,
      p: PulseParam = PulseParam()
  ): (PulseStamp, PulseSettlement) =
    if isInCooldownAt(t) then
      val next = PulseStamp(
        shelterUntil = Double.NegativeInfinity,
        cooldownUntil = t + p.minInterval
      )
      (next, PulseSettlement.CooldownRefused(p.minInterval))
    else
      val next = PulseStamp(
        shelterUntil = t + p.shelterWindow,
        cooldownUntil = t + p.minInterval
      )
      (next, PulseSettlement.Sheltered(p.shelterWindow))

object PulseStamp:
  val zero: PulseStamp = PulseStamp()
