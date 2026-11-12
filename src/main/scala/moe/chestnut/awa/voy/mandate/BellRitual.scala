package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.VoyConfig
import moe.chestnut.awa.voy.inner.event.PulseBookkeeping
import moe.chestnut.awa.voy.inner.veins.*

/** Bell / resonance-vessel strike adjudication (design note 08).
  *
  * Pure rules over [[CellReadout]]: no Minecraft classes, no mutation, so the
  * whole dispatch table is unit-testable. The wiring (firing the pulse,
  * re-anchoring `c0`, applying the overload backlash) lives in
  * [[MandateOverworld]]; the player-facing wording lives in
  * [[MandateWording]].
  */
object BellRitual:
  /** Standard c0 level provided by an intact village bell (§1.2, SliceContract.c0Intact). */
  val BellBaselineCalming: Double = 0.03

  /** Standard pulse area delivered when ringing a bell (matches SliceContract alpha_shock/resonance scale). */
  val BellPulseArea: Double = 12.0

  /** Phase- and cadence-gated outcome of striking a bell / resonance vessel on a cell.
    *
    * `bookkeeping` gates the pulse-*ledger* only (not the physics): every
    * strike fires the pulse, but a strike the dispatch refuses to count
    * (`SpikeSwallowed`, dead mycelium) must not open a shelter window or
    * charge tolerance. Overload still bookkeeps because the refusal itself
    * has to be recorded (it collapses any window, spikes tolerance and locks
    * the cell) — the ledger, not the dispatch, owns those consequences.
    */
  enum BellStrikeOutcome(
      val anchorsC0: Boolean,
      val baselineCalming: Double,
      val pulseArea: Double,
      val bookkeeping: PulseBookkeeping = PulseBookkeeping.PhysicsOnly,
      val triggersOverloadPenalty: Boolean = false
  ):
    /** Chronic ecological burnout (`M -> 0` or `a_slow <= burnoutA`): the
      * mycelial network is dead, so ringing the bell cannot anchor `c0` or
      * establish a shelter window. Recovery requires relieving acid stress
      * and re-inoculating mycelium. The pulse still enters the equations.
      */
    case DeadMyceliumBurnout
        extends BellStrikeOutcome(
          false,
          0.0,
          BellPulseArea,
          PulseBookkeeping.PhysicsOnly,
          false
        )

    /** Acute pulse overload (refused by cadence, by an existing lockout, or by
      * saturated tolerance): the strike is counted (it collapses shelter,
      * deepens subsequent tolerance and locks the cell), and applies an
      * immediate `voy:pulse_saturation` backlash.
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
      * mycelium: the fast system is still inside its oscillatory window and a
      * well-spaced pulse temporarily shelters the cell from `沉滞`, but the
      * bell cannot permanently re-anchor `c0` while slow stress stays
      * saturated.
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
      * spike, `c0` fails to re-anchor, and the strike is **not counted** (the
      * pulse still enters the equations).
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
      * re-anchors `c0`, delays the spike, and grants the shelter window.
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
      * `c0`, fires the pulse, and grants the shelter window.
      */
    case AnchoredOrMaintained
        extends BellStrikeOutcome(
          true,
          BellBaselineCalming,
          BellPulseArea,
          PulseBookkeeping.Counted,
          false
        )

  /** Pure rule for evaluating a bell / resonance strike on a cell (design
    * note 08):
    *
    *  - Ecological burnout (`M -> 0` or `a_slow <= burnoutA`): dead mycelium
    *    cannot carry a pulse (`DeadMyceliumBurnout`).
    *  - Pulse refused (cadence, active lockout, or saturated tolerance): the
    *    ledger's guard fires and the strike drives overload
    *    (`PulseOverloadBurnout`).
    *  - No cycle phase for the trajectory reason (`OffsetBelowKnee`, e.g.
    *    stacked pulse drive across the knee): same overload settlement.
    *  - `Regime.Burnout`: shelters for the window, but slow `c0` cannot
    *    re-anchor while stress stays saturated.
    *  - Unanchored oscillator (`baselineCalming <= 0.0` and `Regime.Oscillating`):
    *    - `Cycle(AbsoluteRefractory | RelativeRefractory, _)`: re-anchors `c0`
    *      and grants the shelter window.
    *    - `Cycle(Excitation, _)`: swallowed by the active positive spike.
    *  - `Calm` / `Strained` / `Resting` or already-intact `c0`: anchors `c0`
    *    and grants the shelter window (`W_eff(T)`).
    */
  def evaluateBellStrike(
      readout: CellReadout,
      pulseParam: PulseToleranceParam = VoyConfig.current.pulseParam
  ): BellStrikeOutcome =
    if Readout.isEcologicalBurnout(
        readout,
        readout.fastParam,
        readout.stressParam.coupling,
        readout.stressParam.sustenanceDepth,
        pulseParam
      )
    then BellStrikeOutcome.DeadMyceliumBurnout
    else if readout.pulseLedger.wouldOverloadAt(readout.simTime, pulseParam) ||
      readout.isBurnoutPhase
    then BellStrikeOutcome.PulseOverloadBurnout
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
