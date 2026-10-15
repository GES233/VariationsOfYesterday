package moe.chestnut.awa.voy.inner.mandate

/** Slow-subsystem stress regime (design draft §1.3, §2.2).
  *
  * Presentation and gameplay code branches on this discrete enum rather than
  * inspecting raw `stress` / `activity` floats directly.
  */
enum StressRegime:
  case Calm, Strained, Oscillating, Burnout

/** Fast-subsystem action-potential four-phase classification (design draft
  * §1.1.4, §1.3, §2.2).
  *
  *   - `Excitation`: active spike excursion (`|v| >= vSpike` or depolarized
  *     upstroke `v > vExcitationFloor`)
  *   - `AbsoluteRefractory`: post-spike early recovery (~20-30% of period,
  *     `v < 0` with elevated recovery variable `w > wRefractorySplit`);
  *     alpha pulses here DELAY the next surge ("defibrillation / delay window")
  *   - `RelativeRefractory`: mid-recovery vulnerable window (~40-60% of
  *     period, `wVulnerableFloor <= w <= wRefractorySplit` while stressed/active);
  *     positive pulses here FIRE a premature surge
  *   - `Resting`: quiescent attractor neighbourhood or late recovery where
  *     impulses are absorbed
  */
enum ExcitablePhase:
  case Resting, Excitation, AbsoluteRefractory, RelativeRefractory

/** Centralized tuning table for mapping continuous `(v, w, s, q)` state onto
  * discrete presentation regimes and phases.
  *
  * Adjust this single parameter block when retuning ODE parameters; presentation
  * call sites remain unchanged.
  */
case class ReadoutParam(
    strainedStress: Double = 0.18,
    oscillatingStress: Double = 0.45,
    oscillatingActivity: Double = 0.15,
    burnoutStress: Double = 1.15,
    burnoutVFloor: Double = 0.8,
    vExcitationFloor: Double = 1.65,
    vRefractoryFloor: Double = 1.25,
    wVulnerableFloor: Double = -0.35,
    activePhaseStressFloor: Double = 0.15,
    activePhaseActivityFloor: Double = 0.02
)

/** Combined semantic readout of a cell's instantaneous state. */
case class MandateReadout(
    regime: StressRegime,
    phase: ExcitablePhase
)

object MandateReadout:
  val DefaultParam: ReadoutParam = ReadoutParam()

  /** Pure projection from continuous state `(v, w, s, q)` to semantic
    * `(StressRegime, ExcitablePhase)`.
    */
  def classify(
      state: MandateCurrent,
      param: ReadoutParam = DefaultParam
  ): MandateReadout =
    val regime = classifyRegime(state, param)
    val phase = classifyPhase(state, param)
    MandateReadout(regime, phase)

  def classifyRegime(
      state: MandateCurrent,
      param: ReadoutParam = DefaultParam
  ): StressRegime =
    if state.stress >= param.burnoutStress && state.dissonance >= param.burnoutVFloor && state.activity < param.oscillatingActivity
    then StressRegime.Burnout
    else if state.stress >= param.oscillatingStress || state.activity >= param.oscillatingActivity
    then StressRegime.Oscillating
    else if state.stress >= param.strainedStress || state.activity >= param.activePhaseActivityFloor
    then StressRegime.Strained
    else StressRegime.Calm

  def classifyPhase(
      state: MandateCurrent,
      param: ReadoutParam = DefaultParam
  ): ExcitablePhase =
    val v = state.dissonance
    val w = state.entrench
    if v >= param.vExcitationFloor then
      ExcitablePhase.Excitation
    else if v >= param.vRefractoryFloor && w >= 0.0 then
      ExcitablePhase.AbsoluteRefractory
    else if w >= param.wVulnerableFloor &&
      (state.stress >= param.activePhaseStressFloor || state.activity >= param.activePhaseActivityFloor)
    then
      ExcitablePhase.RelativeRefractory
    else
      ExcitablePhase.Resting
