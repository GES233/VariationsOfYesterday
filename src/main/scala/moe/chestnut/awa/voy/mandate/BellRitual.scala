package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.inner.veins.*

/** Bell / resonance-vessel strike adjudication (design note 11 §1.5).
  *
  * Pure rules over [[CellReadout]]: no Minecraft classes, no mutation. The
  * bell's role has shifted from "opening a shelter window" to **pacing and
  * phase-correcting the mountain's heartbeat**; the pulse always enters the
  * equations. The cadence guard (`overdriveGuard` = 1.6 × τ_bell) reads the
  * per-cell `lastBellStrikeAt` stamp — the old `PulseStamp` cooldown is gone.
  */
object BellRitual:
  /** Standard c0 level provided by an intact village bell (§1.2, SliceContract.c0Intact). */
  val BellBaselineCalming: Double = 0.03

  /** Standard pulse area delivered when ringing a bell. */
  val BellPulseArea: Double = 12.0

  /** Phase- and cadence-gated outcome of striking a bell / resonance vessel on a cell. */
  enum BellStrikeOutcome(
      val anchorsC0: Boolean,
      val baselineCalming: Double,
      val pulseArea: Double,
      val triggersOverloadPenalty: Boolean = false
  ):
    /** Chronic ecological burnout (`mycelium.isDead` or `isSlowOffsetBelowKnee`):
      * the mycelial network or excitability is collapsed, so ringing the bell
      * cannot anchor `c0`. No penalty: dead meat gives no echo.
      */
    case DeadMyceliumBurnout
        extends BellStrikeOutcome(false, 0.0, BellPulseArea, false)

    /** Struck inside the overdrive guard (`simTime - lastBellStrikeAt <
      * `overdriveGuard`, ~8 s): the pulse still enters the equations, no c0
      * anchoring, and the striker takes an immediate `voy:pulse_saturation`
      * recoil.
      */
    case PulseOverloadBurnout
        extends BellStrikeOutcome(false, 0.0, BellPulseArea, true)

    /** Unanchored oscillator (`c0 <= 0`, `Regime.Oscillating`) struck during
      * `PhaseLabel.Excitation`: the pulse is swallowed by the active positive
      * spike and `c0` fails to re-anchor. Kept for its narrative information:
      * striking at the wrong phase does not anchor.
      */
    case SpikeSwallowed
        extends BellStrikeOutcome(false, 0.0, BellPulseArea, false)

    /** Everything else (Calm / Strained / refractory phase / saturated but
      * living): anchors/maintains `c0` and fires the pulse. Saturated cells
      * CAN anchor now — re-anchoring is part of the burnout rescue chain
      * (note 06 §7: recovery through a costed path, note 11 ruling 13).
      */
    case AnchoredOrMaintained
        extends BellStrikeOutcome(true, BellBaselineCalming, BellPulseArea, false)

  /** Pure rule for evaluating a bell / resonance strike on a cell. */
  def evaluateBellStrike(
      readout: CellReadout,
      p: HeartbeatParam = HeartbeatParam()
  ): BellStrikeOutcome =
    if readout.mycelium.isDead || readout.isSlowOffsetBelowKnee then
      BellStrikeOutcome.DeadMyceliumBurnout
    else if inOverdriveGuard(readout, p) then
      BellStrikeOutcome.PulseOverloadBurnout
    else if readout.baselineCalming < MandatePhenomena.C0ShelterLevel &&
      readout.regime == Regime.Oscillating
    then
      readout.phase match
        case PhaseReading.Cycle(PhaseLabel.Excitation, _) =>
          BellStrikeOutcome.SpikeSwallowed
        case _ =>
          BellStrikeOutcome.AnchoredOrMaintained
    else
      BellStrikeOutcome.AnchoredOrMaintained

  private def inOverdriveGuard(readout: CellReadout, p: HeartbeatParam): Boolean =
    !readout.lastBellStrikeAt.isNaN &&
      readout.simTime - readout.lastBellStrikeAt < p.overdriveGuard
