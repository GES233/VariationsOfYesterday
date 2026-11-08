package moe.chestnut.awa.voy.inner.mandate

/** Semantic readout of the continuous state (design draft §1.4).
  *
  * This is the *only* place where continuous `(v, w, s, q)` becomes the
  * discrete vocabulary the relational layer is allowed to name. It exists
  * because the relational side (`tools/voy_world`) already declares that
  * vocabulary (`Voy.World.Profile`, `Voy.World.Dynamics`) and treats it as
  * pinned: once this readout exists, this implementation is authoritative
  * and the Elixir profile is kept only as a cross-check.
  *
  * Two properties are load-bearing and deliberately explicit:
  *
  *  1. every threshold lives in [[ReadoutParam]] (no scattered constants);
  *  2. a phase is only *defined* for states that actually have one. The
  *     resting basin, a single transient excursion, an unsettled trace and
  *     the burnout corner get an explicit non-phase reading instead of a
  *     fabricated cycle fraction. See [[PhaseReading]].
  */
enum Regime:
  case Calm, Strained, Oscillating, Burnout

/** The four-phase vocabulary of the action-potential analogy (§1.4). */
enum PhaseLabel:
  case Resting, Excitation, AbsoluteRefractory, RelativeRefractory

/** Why a state carries no cycle phase. Each case is a *different* answer and
  * must never be collapsed into a shared "undefined" or an empty result. */
enum UndefinedReason:
  /** The fixed point has left the oscillatory window on the far side
    * (`a(s) <= burnout_a`): the fast subsystem sits on the right branch and
    * does not oscillate, so there is no cycle to measure a phase against. */
  case BurnoutNoCycle

  /** An excursion with no repeat: a transient after a kick, or a trace that
    * has not settled into a regular cycle (the early-game "omen"). */
  case TransientNoCycle

  /** Not enough observed cycles to define a phase at all. */
  case NoCycleReference

/** Reading of the fast subsystem at one instant.
  *
  * `Cycle` is the only case that carries a fraction: a fraction is a
  * statement about a *periodic* orbit. `Basin` names a class without a
  * fraction (a resting cell is not at "0.37 of a period"); `Undefined`
  * names the reason.
  */
sealed trait PhaseReading
object PhaseReading:
  final case class Cycle(label: PhaseLabel, fraction: Double) extends PhaseReading
  final case class Basin(label: PhaseLabel) extends PhaseReading
  final case class Undefined(reason: UndefinedReason) extends PhaseReading

/** Every threshold of the readout, in one place.
  *
  * The stress cut points and `oscillatingQ` mirror `Voy.World.Profile`
  * (`stress: %{calm: 0.18, strained: 0.45, burnout: 0.95, oscillating_q:
  * 0.4}`) so the two sides can be pinned by goldens. They are *not* used to
  * decide whether a phase exists: a stress level is not a dynamics
  * statement, and the slice reaches `s = stress_max = 1.0` while the cell
  * still spikes (measured, see the contract oracle). Phase existence is
  * decided by the recovery offset `a(s)`, below.
  */
case class ReadoutParam(
    calm: Double = 0.18,
    strained: Double = 0.45,
    burnout: Double = 0.95,
    oscillatingQ: Double = 0.4,
    sectionLevel: Double = 1.2,
    spikeLevel: Double = 1.8,
    fireWindowLo: Double = 0.40,
    fireWindowHi: Double = 0.60,
    regularityTol: Double = 0.20,
    basinTol: Double = 0.25,
    minPeriod: Double = 1.0,
    staleCycles: Double = 2.5,
    transientCycles: Double = 1.5
)

object Readout:
  /** Bisection steps for the resting fixed point: the bracket is < 8 wide, so
    * 64 halvings reach machine precision with margin (the old 200 was a hot
    * loop called per cell per tick in the non-oscillating branch). */
  private val RestPointBisectionSteps = 64
  /** Slow-variable regime partition, mirroring `Voy.World.Profile.regime/3`
    * field for field. Kept as the cross-stack cross-check only. */
  def regime(cur: MandateCurrent, p: ReadoutParam = ReadoutParam()): Regime =
    if cur.stress >= p.burnout then Regime.Burnout
    else if cur.activity >= p.oscillatingQ then Regime.Oscillating
    else if cur.stress >= p.strained then Regime.Strained
    else Regime.Calm

  /** Recovery offset at which the fixed point crosses the right knee of the
    * cubic (`v = +1`), where the fast subsystem loses the middle-branch
    * crossing it needs in order to oscillate. Below it the cell is burnt
    * out: high stress, no cycle.
    *
    * With the frozen parameters this is `a = -0.4666...`, i.e. `s = 1.1666`
    * — *outside* `stressMax = 1.0`. The clamp therefore guarantees the
    * slice cannot reach burnout through the ordinary stress channel alone;
    * crossing the knee requires either slow mycelial depletion (`M -> 0`) or
    * acute over-pulsing (`pulseDrive > singlePulsePeak`).
    */
  def burnoutA(fast: MandateParam): Double =
    fast.beta * (1.0 - 1.0 / 3.0) - 1.0

  /** Slow-variable recovery offset `a(s, M) = alpha - depth * (1 - M) - coupling * s`
    * without transient pulse drive. Crossing [[burnoutA]] here indicates
    * chronic ecological incapacitation (mycelial depletion).
    */
  def slowA(
      fast: MandateParam,
      stress: Double,
      sustenance: Double = 1.0,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth
  ): Double =
    fast.alpha - depth * (1.0 - sustenance) - coupling * stress

  /** Effective recovery offset including excess fast-channel pulse drive:
    * stacked pulses above a single standard bell peak (`singlePulsePeak`)
    * push `a_eff` downward across the right knee (`burnoutA`), producing
    * acute pulse-overload depolarization block (`BurnoutNoCycle`).
    */
  def effectiveA(
      fast: MandateParam,
      stress: Double,
      sustenance: Double = 1.0,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth,
      pulseDrive: Double = 0.0,
      pulseParam: PulseToleranceParam = PulseToleranceParam()
  ): Double =
    val excessPulse = Math.max(pulseDrive - pulseParam.singlePulsePeak, 0.0)
    slowA(fast, stress, sustenance, coupling, depth) -
      pulseParam.pulseBurnoutGain * excessPulse

  /** Whether a cell is in chronic ecological burnout (mycelial network
    * depleted so that the slow offset itself sits below the burnout knee),
    * as opposed to an acute pulse-overload lockout.
    */
  def isEcologicalBurnout(
      readout: CellReadout,
      fast: MandateParam = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth,
      pulseParam: PulseToleranceParam = PulseToleranceParam()
  ): Boolean =
    val slow = slowA(fast, readout.current.stress, readout.sustenance, coupling, depth)
    readout.sustenance < readout.stressParam.sustenanceFloor ||
      slow <= burnoutA(fast) ||
      (readout.phase == PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle) &&
        !readout.pulseState.isOverloadedAt(readout.simTime) &&
        readout.pulseDrive <= pulseParam.singlePulsePeak)

  /** Whether a limit cycle exists at this recovery offset. */
  def oscillating(a: Double, fast: MandateParam): Boolean =
    val (_, ac) = PhaseResponse.hopfPoint(fast)
    a < ac && a > burnoutA(fast)

  /** Stable rest point of the fast subsystem for recovery offset `a`, from
    * the closed form `v - v^3/3 = (v + a)/beta`, `w = (v + a)/beta`.
    *
    * The residual `h(v) = v - v^3/3 - (v + a)/beta` is strictly decreasing
    * (`h'(v) = 1 - v^2 - 1/beta < 0`), so bisection on a bracket that
    * straddles the root converges to the unique left-branch fixed point.
    */
  def restPoint(a: Double, beta: Double): (Double, Double) =
    def h(v: Double): Double = v - Math.pow(v, 3) / 3.0 - (v + a) / beta
    var lo = -8.0
    var hi = -0.4
    var i = 0
    while i < RestPointBisectionSteps do
      val mid = 0.5 * (lo + hi)
      if h(mid) > 0.0 then lo = mid else hi = mid
      i += 1
    val v = 0.5 * (lo + hi)
    (v, (v + a) / beta)

  /** The label of a cycle fraction, given the declared windows. `Excitation`
    * is decided by the positive spike level (`v >= spikeLevel`), not by
    * `|v|` or the fraction: the depolarising upstroke is measured, and the
    * negative repolarisation overshoot must not be mislabelled as a spike.
    */
  def label(cur: MandateCurrent, fraction: Double, p: ReadoutParam = ReadoutParam()): PhaseLabel =
    if cur.dissonance >= p.spikeLevel then PhaseLabel.Excitation
    else if fraction < p.fireWindowLo then PhaseLabel.AbsoluteRefractory
    else if fraction <= p.fireWindowHi then PhaseLabel.RelativeRefractory
    else PhaseLabel.Resting

  /** Whether a cell is currently sheltered from Overworld stagnation (`沉滞`).
    *
    * Under the heartbeat model (design note 08), shelter is granted for a
    * finite window by a valid pulse (`pulseSuppressed`) and expires once too
    * long passes without a new pulse. Shelter also requires a living
    * mycelial network (`sustenance >= floor`), a non-burnout phase
    * (`phase != BurnoutNoCycle`), and no active positive spike excursion
    * (`v < spikeLevel`).
    */
  def isMaintained(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): Boolean =
    readout.pulseSuppressed &&
      readout.sustenance >= readout.stressParam.sustenanceFloor &&
      readout.phase != PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle) &&
      readout.current.dissonance < p.spikeLevel

  /** Convert a cycle fraction `[0, 1)` into the 20-division dial index
    * `0..19` used by the field notebook and contract tick trials.
    */
  def dialTick(phase: PhaseReading, divisions: Int = 20): Option[Int] =
    phase match
      case PhaseReading.Cycle(_, fraction) =>
        val normalized = ((fraction % 1.0) + 1.0) % 1.0
        Some(Math.min(divisions - 1, Math.floor(normalized * divisions).toInt))
      case _ => None
end Readout

/** Combined semantic snapshot of one lattice cell published to the game
  * side: continuous state, slow-variable regime, fast-variable phase reading,
  * `c0` baseline calming, mycelial sustenance `M`, and pulse-suppression /
  * tolerance state.
  */
final case class CellReadout(
    current: MandateCurrent,
    regime: Regime,
    phase: PhaseReading,
    baselineCalming: Double = 0.0,
    sustenance: Double = 1.0,
    pulseDrive: Double = 0.0,
    pulseSuppressed: Boolean = false,
    pulseTolerance: Double = 0.0,
    pulseState: PulseToleranceState = PulseToleranceState.zero,
    simTime: Double = 0.0,
    // Per-cell parameter sets (save > config > core default): the game layer
    // must judge this cell with the parameters the cell actually integrates,
    // not with the current global config (design draft §7.3).
    fastParam: MandateParam = MandateAgent.DefaultFastParam,
    stressParam: StressParam = MandateAgent.DefaultStressParam
):
  def isMaintained: Boolean = Readout.isMaintained(this)
  def dialTick: Option[Int] = Readout.dialTick(phase)

object CellReadout:
  val zero: CellReadout = CellReadout(
    current = MandateCurrent(0.0, 0.0, 0.0, 0.0),
    regime = Regime.Calm,
    phase = PhaseReading.Basin(PhaseLabel.Resting),
    baselineCalming = 0.0
  )


/** Running phase reference for one cell.
  *
  * Phase is defined *relative to a Poincaré section* (`dissonance` crossing
  * [[ReadoutParam.sectionLevel]] upwards) and to the last observed cycle
  * period: `fraction = (t - t_cross) / T_est (mod 1)`. This is a
  * section-phase, not an isochron-exact phase — the choice is stated
  * because it is what the contract can actually verify, and no isochron map
  * for this system is available in closed form.
  *
  * Crossing times are interpolated between the two samples that straddle
  * the section, so the phase error is second order in the step for a smooth
  * trajectory; `PhaseResponseSpec` checks that by halving the step.
  */
class PhaseTracker(
    param: ReadoutParam = ReadoutParam(),
    fast: MandateParam = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
    coupling: Double = 1.0,
    sustenance: () => Double = () => 1.0,
    depth: Double = StressParam().sustenanceDepth,
    pulseDrive: () => Double = () => 0.0,
    overloaded: () => Boolean = () => false,
    pulseParam: PulseToleranceParam = PulseToleranceParam()
):
  private var tPrev = Double.NaN
  private var vPrev = Double.NaN
  private var lastCross = Double.NaN
  private var crossTimes: List[Double] = Nil

  /** Interpolated upcrossing times, newest first. */
  def crossings: List[Double] = crossTimes

  /** Last cycle period estimate, from up to two most recent intervals. */
  def period: Option[Double] =
    intervals match
      case a :: b :: _ => Some(0.5 * (a + b))
      case a :: Nil => Some(a)
      case Nil => None

  private def intervals: List[Double] =
    crossTimes match
      case a :: b :: c :: _ => List(a - b, b - c)
      case a :: b :: _ => List(a - b)
      case _ => Nil

  /** Number of section upcrossings seen: one per excursion. */
  def excursions: Int = crossTimes.size

  def observe(t: Double, cur: MandateCurrent): PhaseReading =
    val v = cur.dissonance
    if !tPrev.isNaN then
      val l = param.sectionLevel
      if vPrev <= l && v > l then
        val frac = if v == vPrev then 0.0 else (l - vPrev) / (v - vPrev)
        val tc = tPrev + frac * (t - tPrev)
        if lastCross.isNaN || tc - lastCross > param.minPeriod then
          lastCross = tc
          crossTimes = tc :: crossTimes
    tPrev = t
    vPrev = v
    reading(t, cur)

  def reading(t: Double, cur: MandateCurrent): PhaseReading =
    val aSlow = Readout.slowA(fast, cur.stress, sustenance(), coupling, depth)
    val aEff = Readout.effectiveA(
      fast,
      cur.stress,
      sustenance(),
      coupling,
      depth,
      pulseDrive(),
      pulseParam
    )
    if aEff <= Readout.burnoutA(fast) || overloaded() then
      PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle)
    else if !Readout.oscillating(aSlow, fast) then
      // No limit cycle exists here: the only phase-free reading left is the
      // resting basin itself. An excursion in flight is a transient.
      val (vStar, wStar) = Readout.restPoint(aSlow, fast.beta)
      val atRest =
        Math.abs(cur.dissonance - vStar) <= param.basinTol &&
          Math.abs(cur.entrench - wStar) <= param.basinTol
      if atRest && lastCross.isNaN then PhaseReading.Basin(PhaseLabel.Resting)
      else PhaseReading.Undefined(UndefinedReason.TransientNoCycle)
    else
      val T = period
      val sinceCross = if lastCross.isNaN then Double.NaN else t - lastCross
      val stale = (for
        p <- T
        s = sinceCross
      yield !s.isNaN && s > param.staleCycles * p).getOrElse(false)
      if !lastCross.isNaN && !stale && isRegular then
        val p = T.get
        val fraction = ((t - lastCross) / p) % 1.0
        PhaseReading.Cycle(Readout.label(cur, fraction, param), fraction)
      else if !lastCross.isNaN && stale then PhaseReading.Undefined(UndefinedReason.TransientNoCycle)
      else if lastCross.isNaN then PhaseReading.Undefined(UndefinedReason.NoCycleReference)
      else PhaseReading.Undefined(UndefinedReason.TransientNoCycle)

  private def isRegular: Boolean =
    intervals match
      case i1 :: i2 :: _ =>
        val mean = 0.5 * (i1 + i2)
        mean > 0.0 && Math.abs(i1 - i2) <= param.regularityTol * mean
      case _ => false
