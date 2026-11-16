package moe.chestnut.awa.voy.inner.veins

/** A ritual calming source kind (design note 11 ruling 11). The baseline
  * calming `c0` is NOT owned by any single block: it is a slow stock
  * (potassium-ion style) fed by every registered source in a chunk and
  * leaking away with a session-scale time constant.
  *
  * Bells are the first kind; AMD/ritual structures (古堰、铜钲、石灰石沟) will
  * add their own weights later without changing the model.
  *
  * @param trickleWeight contribution of one unit to the equilibrium `c0`
  *   (weight 1.0 → equilibrium equals the contract's `c0Intact` 0.03)
  */
enum MandateSourceKind(val trickleWeight: Double):
  case Bell extends MandateSourceKind(1.0)

/** One registered calming source in the world: a chunk-local block of a
  * known [[MandateSourceKind]]. Pure data (no Minecraft types) so the stock
  * math stays testable; the wiring layer maps block states to kinds.
  */
final case class MandateSource(
    kind: MandateSourceKind,
    x: Int,
    y: Int,
    z: Int
)

/** Tuning of the baseline-calming stock (design note 11 ruling 11). */
final case class CalmingParam(
    /** derived: session-scale memory, 45 min — longer than one play-testing
      * session, short enough that a removed bell's calm actually fades.
      */
    tau: Double = 2700.0,
    /** derived: equilibrium of one weight unit equals the contract's
      * `c0Intact` work point (0.03), so an intact village bell reproduces the
      * old anchor value as a steady state rather than an instantaneous SET.
      */
    tricklePerWeight: Double = 0.03 / 2700.0,
    /** TODO(calibrate): ritual dose per anchored strike; Step 3 playtest. */
    dose: Double = 0.008,
    /** derived: cap just above the contract anchor — extra bells have
      * diminishing returns and strikes cannot overshoot the validated band.
      */
    cap: Double = 0.04
)

/** Pure stock arithmetic for [[CalmingParam]]: leaky integration toward a
  * source-driven equilibrium, plus the discrete ritual dose.
  */
object CalmingStock:
  /** One rebalance step of length `dt` (simulation seconds).
    *
    * `c0' = c0 + (equilibrium − c0) · (1 − e^(−dt/τ))`, clamped to `[0, cap]`,
    * where `equilibrium = tricklePerWeight · weight · τ`. With weight 1.0 the
    * equilibrium is 0.03; with no source it decays toward 0.
    */
  def step(
      c0: Double,
      weight: Double,
      dt: Double,
      p: CalmingParam = CalmingParam()
  ): Double =
    if dt <= 0.0 then clamp(c0, p)
    else
      val equilibrium = p.tricklePerWeight * Math.max(weight, 0.0) * p.tau
      val alpha = 1.0 - Math.exp(-dt / p.tau)
      clamp(c0 + (equilibrium - c0) * alpha, p)

  /** Discrete ritual dose: an anchored strike tops the stock up, capped. */
  def dose(c0: Double, p: CalmingParam = CalmingParam()): Double =
    clamp(c0 + p.dose, p)

  private def clamp(value: Double, p: CalmingParam): Double =
    Math.min(Math.max(value, 0.0), p.cap)
