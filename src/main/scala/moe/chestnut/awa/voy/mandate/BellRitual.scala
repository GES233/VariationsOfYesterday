package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.inner.event.PulseBookkeeping
import moe.chestnut.awa.voy.inner.veins.*

/** Bell / resonance-vessel strike adjudication.
  *
  * Pure rules over [[CellReadout]]: no Minecraft classes, no mutation, and
  * shares the exact cooldown predicate (`pulseStamp.isInCooldownAt`) with
  * the engine's [[PulseStamp.onCountedPulse]].
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
      val bookkeeping: PulseBookkeeping = PulseBookkeeping.PhysicsOnly,
      val triggersOverloadPenalty: Boolean = false
  ):
    /** Chronic ecological burnout (`mycelium.isDead` or `isSlowOffsetBelowKnee`):
      * the mycelial network or excitability is collapsed, so ringing the bell
      * cannot anchor `c0` or open a shelter window.
      */
    case DeadMyceliumBurnout
        extends BellStrikeOutcome(
          false,
          0.0,
          BellPulseArea,
          PulseBookkeeping.PhysicsOnly,
          false
        )

    /** Struck inside the refractory cooldown (`pulseStamp.isInCooldownAt(simTime)`):
      * the strike is counted (collapsing the shelter window and refreshing the
      * cooldown) and applies an immediate `voy:pulse_saturation` backlash.
      */
    case PulseOverloadBurnout
        extends BellStrikeOutcome(
          false,
          0.0,
          BellPulseArea,
          PulseBookkeeping.Counted,
          true
        )

    /** Saturated incapacitation (`Regime.Burnout`, `s >= 0.95`) on living
      * mycelium: shelters for the window, but cannot re-anchor `c0` while slow
      * stress stays saturated.
      */
    case SaturatedNoAnchor
        extends BellStrikeOutcome(
          false,
          0.0,
          BellPulseArea,
          PulseBookkeeping.Counted,
          false
        )

    /** Unanchored oscillator (`c0 <= 0`, `Regime.Oscillating`) struck during
      * `PhaseLabel.Excitation`: the pulse is swallowed by the active positive
      * spike, `c0` fails to re-anchor, and the strike is not counted.
      */
    case SpikeSwallowed
        extends BellStrikeOutcome(
          false,
          0.0,
          BellPulseArea,
          PulseBookkeeping.PhysicsOnly,
          false
        )

    /** Unanchored oscillator (`c0 <= 0`, `Regime.Oscillating`) struck inside
      * the refractory delay window (`AbsoluteRefractory` | `RelativeRefractory`):
      * re-anchors `c0`, delays the spike, and opens the shelter window.
      */
    case RefractoryReanchored
        extends BellStrikeOutcome(
          true,
          BellBaselineCalming,
          BellPulseArea,
          PulseBookkeeping.Counted,
          false
        )

    /** Calm / Strained / Resting or already-anchored cell: anchors/maintains
      * `c0`, fires the pulse, and opens the shelter window.
      */
    case AnchoredOrMaintained
        extends BellStrikeOutcome(
          true,
          BellBaselineCalming,
          BellPulseArea,
          PulseBookkeeping.Counted,
          false
        )

  /** Pure rule for evaluating a bell / resonance strike on a cell. */
  def evaluateBellStrike(readout: CellReadout): BellStrikeOutcome =
    if readout.mycelium.isDead || readout.isSlowOffsetBelowKnee then
      BellStrikeOutcome.DeadMyceliumBurnout
    else if readout.pulseStamp.isInCooldownAt(readout.simTime) then
      BellStrikeOutcome.PulseOverloadBurnout
    else if readout.regime == Regime.Burnout then
      BellStrikeOutcome.SaturatedNoAnchor
    else if readout.baselineCalming <= 0.0 && readout.regime == Regime.Oscillating then
      readout.phase match
        case PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory | PhaseLabel.RelativeRefractory, _) =>
          BellStrikeOutcome.RefractoryReanchored
        case PhaseReading.Cycle(PhaseLabel.Excitation, _) =>
          BellStrikeOutcome.SpikeSwallowed
        case _ =>
          BellStrikeOutcome.AnchoredOrMaintained
    else
      BellStrikeOutcome.AnchoredOrMaintained
