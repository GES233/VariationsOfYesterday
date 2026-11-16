package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.inner.veins.*

/** Bell / resonance-vessel strike adjudication (design note 11 §1.5 as amended by
  * design note 12 §4; **ruling 16**).
  *
  * The bell is a **pacemaker, not a phase sniper**. The outcome depends only on
  * state the engine owns — whether the cell is ecologically dead, and whether it
  * was struck inside the overdrive guard — never on where the current cycle
  * happens to be. Two independent pieces of evidence say the same thing: the
  * measured pacemaker table (design note 11 §2.2: 8 s block, 12/20 s 2:1, 30–60 s
  * 1:1, 90 s bradycardia) follows the *interval*, and ruling 1 already states
  * "entrained cells are paced; no extra criterion". The old phase gate
  * (`SpikeSwallowed`) was a design-note-08 leftover that contradicted both.
  *
  * The pulse always enters the equations, and a landed strike always re-anchors
  * the chunk's `c0` stock. Whether the strike *called a beat* is no longer a
  * verdict: it becomes a delayed observation (`PulseOutcome` echo, note 12 §4.3).
  */
object BellRitual:
  /** Standard pulse area delivered when ringing a bell. */
  val BellPulseArea: Double = 12.0

  /** State-gated outcome of striking a bell / resonance vessel on a cell. */
  enum BellOutcome(val anchorsC0: Boolean, val triggersOverloadPenalty: Boolean = false):
    /** Chronic ecological burnout (`SustenanceTier.Dead` or the slow recovery
      * offset collapsed below the knee): the network or the excitability is
      * gone, so the strike cannot anchor `c0`. No penalty — dead meat gives no
      * echo.
      */
    case NoAnswer extends BellOutcome(false)

    /** Struck inside the overdrive guard (`simTime - lastBellStrikeAt <
      * overdriveGuard`, ~8 s): the pulse still enters the equations, no `c0`
      * anchoring, and the striker takes an immediate `voy:pulse_saturation`
      * recoil. Note the granularity (note 12 §5.3): the **guard belongs to the
      * cell** — the vein cannot take two strikes in 8 s no matter who rings —
      * while the **recoil belongs to the striker**.
      */
    case Overdriven extends BellOutcome(false, true)

    /** Everything else: anchors/maintains `c0` and fires the pulse. Saturated
      * but living cells CAN anchor — re-anchoring is part of the burnout rescue
      * chain (note 06 §7, note 11 ruling 13).
      */
    case Struck extends BellOutcome(true)

  /** Pure rule for evaluating a bell / resonance strike on a cell. */
  def evaluateBellStrike(
      readout: CellReadout,
      p: RhythmParam = RhythmParam()
  ): BellOutcome =
    if isEcologicallyDead(readout) then BellOutcome.NoAnswer
    else if inOverdriveGuard(readout, p) then BellOutcome.Overdriven
    else BellOutcome.Struck

  /** Dead mycelial network, or collapsed recovery offset.
    *
    * NOTE (note 12 §4.1 item 3 / §5.3): `CellReadout.isSlowOffsetBelowKnee`
    * currently derives partly from the phase reading, a second source of truth
    * for the same predicate. That is folded into a quantity-only path in the
    * atomisation step (note 12 S3); this eviction deliberately does not change
    * the predicate's behaviour.
    */
  private def isEcologicallyDead(readout: CellReadout): Boolean =
    readout.mycelium.isDead || readout.isSlowOffsetBelowKnee

  private def inOverdriveGuard(readout: CellReadout, p: RhythmParam): Boolean =
    !readout.lastBellStrikeAt.isNaN &&
      readout.simTime - readout.lastBellStrikeAt < p.overdriveGuard
