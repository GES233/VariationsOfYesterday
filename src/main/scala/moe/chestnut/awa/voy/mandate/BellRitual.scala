package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.VoyConfig
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
    * `bookkeepsHeartbeat` gates the pulse-*ledger* only (not the physics):
    * every strike fires the pulse, but a strike the dispatch refuses to count
    * (`SpikeSwallowed`, dead mycelium) must not open a stagnation window.
    * Overload still bookkeeps because the overload verdict itself has to be
    * recorded (it collapses any window and locks the cell).
    */
  enum BellStrikeOutcome(
      val anchorsC0: Boolean,
      val baselineCalming: Double,
      val pulseArea: Double,
      val bookkeepsHeartbeat: Boolean = false,
      val triggersOverloadPenalty: Boolean = false
  ):
    /** Chronic ecological burnout (`M -> 0` or `a_slow <= burnoutA`): the
      * mycelial network is dead, so ringing the bell cannot anchor `c0` or
      * establish a heartbeat suppression window. Recovery requires relieving
      * acid stress and re-inoculating mycelium.
      */
    case DeadMyceliumBurnout
        extends BellStrikeOutcome(false, 0.0, BellPulseArea, false, false)

    /** Acute pulse overload (`BurnoutNoCycle` triggered by pulsing too fast
      * or while tolerance-saturated): the strike is recorded (it collapses
      * stagnation suppression, deepens subsequent tolerance, and locks the
      * cell), and applies an immediate `voy:pulse_saturation` backlash.
      */
    case PulseOverloadBurnout
        extends BellStrikeOutcome(false, 0.0, BellPulseArea, true, true)

    /** Saturated incapacitation (`Regime.Burnout`, `s >= 0.95`) on living
      * mycelium: the fast system is still inside its oscillatory window and a
      * well-spaced pulse temporarily suppresses `沉滞` for the heartbeat
      * window, but the bell cannot permanently re-anchor `c0` while slow
      * stress stays saturated.
      */
    case SaturatedNoAnchor
        extends BellStrikeOutcome(false, 0.0, BellPulseArea, true, false)

    /** Unanchored oscillator (`c0 <= 0`, `Regime.Oscillating`) struck during
      * `PhaseLabel.Excitation`: the pulse is swallowed by the active positive
      * spike, `c0` fails to re-anchor, and the strike is **not** a heartbeat
      * (the pulse still enters the equations).
      */
    case SpikeSwallowed
        extends BellStrikeOutcome(false, 0.0, BellPulseArea, false, false)

    /** Unanchored oscillator (`c0 <= 0`, `Regime.Oscillating`) struck inside
      * the refractory delay window (`AbsoluteRefractory` | `RelativeRefractory`):
      * re-anchors `c0`, delays the spike, and grants the pulse suppression window.
      */
    case RefractoryReanchored
        extends BellStrikeOutcome(true, BellBaselineCalming, BellPulseArea, true, false)

    /** Calm / Strained / Resting or already-anchored cell: anchors/maintains
      * `c0`, fires the pulse, and grants the pulse suppression window.
      */
    case AnchoredOrMaintained
        extends BellStrikeOutcome(true, BellBaselineCalming, BellPulseArea, true, false)

  /** Pure rule for evaluating a bell / resonance strike on a cell (design
    * note 08):
    *
    *  - Ecological burnout (`M -> 0` or `a_slow <= burnoutA`): dead mycelium
    *    cannot carry a pulse (`DeadMyceliumBurnout`).
    *  - Pulse too fast (`dt < minSafeInterval`, tolerance saturated, or
    *    already in acute pulse `BurnoutNoCycle`): triggers depolarization
    *    block overload (`PulseOverloadBurnout`), spiking tolerance and
    *    applying an immediate minor penalty.
    *  - `Regime.Burnout`: suppresses stagnation for the heartbeat window,
    *    but slow `c0` cannot re-anchor while stress stays saturated.
    *  - Unanchored oscillator (`baselineCalming <= 0.0` and `Regime.Oscillating`):
    *    - `Cycle(AbsoluteRefractory | RelativeRefractory, _)`: re-anchors `c0`
    *      and grants the pulse suppression window.
    *    - `Cycle(Excitation, _)`: swallowed by the active positive spike.
    *  - `Calm` / `Strained` / `Resting` or already-intact `c0`: anchors `c0`
    *    and grants the pulse suppression window (`W_eff(T)`).
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
    else if readout.pulseState.wouldOverloadAt(readout.simTime, pulseParam) ||
      readout.phase == PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle)
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
