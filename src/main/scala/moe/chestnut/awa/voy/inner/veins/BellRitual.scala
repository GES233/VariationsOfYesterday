package moe.chestnut.awa.voy.inner.veins

/** State-gated outcome of striking a bell / resonance vessel on a cell
  * (design note 11 §1.5 as amended by note 12 §4; **ruling 16**).
  */
enum BellOutcome(val anchorsC0: Boolean, val triggersOverloadPenalty: Boolean = false):
  /** Chronic ecological burnout (`SustenanceTier.Dead` or the slow recovery
    * offset collapsed below the knee): the network or excitability is gone, so
    * the strike cannot anchor `c0`. No penalty — dead meat gives no echo.
    */
  case NoAnswer extends BellOutcome(false)

  /** Struck inside the overdrive guard (`simTime - lastBellStrikeAt <
    * overdriveGuard`, ~8 s): the pulse still enters the equations, no `c0`
    * anchoring, and the striker takes an immediate `voy:pulse_saturation`
    * recoil. Granularity: the **guard belongs to the cell**, while the
    * **recoil belongs to the striker**.
    */
  case Overdriven extends BellOutcome(false, true)

  /** Everything else: anchors/maintains `c0` and fires the pulse. Saturated
    * but living cells CAN anchor — re-anchoring is part of the burnout rescue
    * chain.
    */
  case Struck extends BellOutcome(true)

/** Bell / resonance-vessel strike adjudication inside the engine actor.
  *
  * The bell is a **pacemaker, not a phase sniper**. The outcome depends only on
  * state the engine owns — whether the cell is ecologically dead, and whether it
  * was struck inside the overdrive guard — never on where the current cycle
  * happens to be.
  */
object BellRitual:
  /** Standard pulse area delivered when ringing a bell. */
  val BellPulseArea: Double = 12.0

  /** Pure rule for evaluating a bell / resonance strike on a cell. */
  def evaluate(
      ecologicallyDead: Boolean,
      lastBellStrikeAt: Double,
      simTime: Double,
      p: RhythmParam = RhythmParam()
  ): BellOutcome =
    if ecologicallyDead then BellOutcome.NoAnswer
    else if !lastBellStrikeAt.isNaN && simTime - lastBellStrikeAt < p.overdriveGuard then
      BellOutcome.Overdriven
    else BellOutcome.Struck
