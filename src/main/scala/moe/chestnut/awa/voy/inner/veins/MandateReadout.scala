package moe.chestnut.awa.voy.inner.veins

/** Semantic readout of the continuous state (design draft §1.4).
  *
  * The only place where continuous `(v, w, s, q)` becomes the discrete
  * vocabulary the relational layer may name. Two properties are load-bearing:
  * every threshold lives in [[ReadoutParam]], and a phase is only defined for
  * states that actually have one ([[PhaseReading]]).
  */
enum Regime:
  case Calm, Strained, Oscillating, Burnout

/** The four-phase vocabulary of the action-potential analogy (§1.4). */
enum PhaseLabel:
  case Resting, Excitation, AbsoluteRefractory, RelativeRefractory

/** Why a state carries no cycle phase. The cases are different answers and
  * must never be collapsed into a shared "undefined". */
enum UndefinedReason:
  /** Fixed point left the oscillatory window (`a(s) <= burnout_a`). */
  case BurnoutNoCycle

  /** A non-repeating excursion, or a trace not settled into a regular cycle. */
  case TransientNoCycle

  /** Not enough observed cycles to define a phase. */
  case NoCycleReference

/** A cycle carries a fraction; `Basin` names a class without one; `Undefined`
  * names the reason. Only `Cycle` may carry a period fraction. */
sealed trait PhaseReading
object PhaseReading:
  final case class Cycle(label: PhaseLabel, fraction: Double) extends PhaseReading
  final case class Basin(label: PhaseLabel) extends PhaseReading
  final case class Undefined(reason: UndefinedReason) extends PhaseReading

/** Every readout threshold, in one place.
  *
  * The stress cuts mirror `Voy.World.Profile` (Elixir also keeps `calm: 0.18`
  * for its own tolerance check; Scala does not partition on it). These cuts do
  * not decide whether a phase exists — that is the recovery offset `a(s)`,
  * below; the slice reaches `s = stress_max = 1.0` and still spikes.
  */
case class ReadoutParam(
    strained: Double = 0.45,
    burnout: Double = 0.95,
    oscillatingQ: Double = 0.4,
    sectionLevel: Double = PhaseDetection.SectionLevel,
    spikeLevel: Double = PhaseDetection.SpikeLevel,
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

  /** Recovery offset where the cubic's right knee (`v = +1`) removes the middle
    * branch. With frozen parameters this is `a = -0.4666...` (`s = 1.1666`,
    * outside `stressMax = 1.0`), so the slice cannot burn out through stress
    * alone: it needs mycelial depletion or acute over-pulsing. */
  def burnoutA(fast: MandateParam): Double =
    fast.beta * (1.0 - 1.0 / 3.0) - 1.0

  /** Slow recovery offset `a(s, M)` without transient pulse drive. */
  def slowA(
      fast: MandateParam,
      stress: Double,
      sustenance: Double = 1.0,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth
  ): Double =
    fast.alpha - depth * (1.0 - sustenance) - coupling * stress

  /** Effective recovery offset with stacked pulse drive above a single bell
    * peak; crossing [[burnoutA]] here is acute pulse-overload block. */
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

  /** Chronic burnout (mycelial depletion or the slow offset across the knee),
    * as opposed to an acute pulse-overload lockout. */
  def isEcologicalBurnout(
      readout: CellReadout,
      fast: MandateParam = MandateAgent.DefaultFastParam,
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

  /** Stable left-branch rest point for recovery offset `a`. The residual
    * `h(v) = v - v^3/3 - (v + a)/beta` is strictly decreasing
    * (`h'(v) = 1 - v^2 - 1/beta < 0`), so the bracket has a unique root. */
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

  /** Cycle-fraction label. `Excitation` is the positive spike level (`v >=
    * spikeLevel`); the negative repolarisation overshoot is not a spike. */
  def label(cur: MandateCurrent, fraction: Double, p: ReadoutParam = ReadoutParam()): PhaseLabel =
    if cur.dissonance >= p.spikeLevel then PhaseLabel.Excitation
    else if fraction < p.fireWindowLo then PhaseLabel.AbsoluteRefractory
    else if fraction <= p.fireWindowHi then PhaseLabel.RelativeRefractory
    else PhaseLabel.Resting

  /** Heartbeat shelter (design note 08): a fresh pulse window, a living
    * mycelial network, a non-burnout phase, and no active positive spike. */
  def isMaintained(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): Boolean =
    readout.pulseSuppressed &&
      readout.sustenance >= readout.stressParam.sustenanceFloor &&
      readout.phase != PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle) &&
      readout.current.dissonance < p.spikeLevel

  /** Cycle fraction `[0, 1)` -> 20-division dial index `0..19`. */
  def dialTick(phase: PhaseReading, divisions: Int = 20): Option[Int] =
    phase match
      case PhaseReading.Cycle(_, fraction) =>
        val normalized = ((fraction % 1.0) + 1.0) % 1.0
        Some(Math.min(divisions - 1, Math.floor(normalized * divisions).toInt))
      case _ => None
end Readout

/** One cell's semantic snapshot published to the game side, including the
  * per-cell parameters the cell actually integrates. */
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

/** Running section-phase reference for one cell: `fraction = (t - t_cross) /
  * T_est (mod 1)`. This is a section phase, not an isochron map (none is
  * available in closed form); crossing times are interpolated between the two
  * straddling samples, so the phase error is second order in the step.
  */
class PhaseTracker(
    param: ReadoutParam = ReadoutParam(),
    fast: MandateParam = MandateAgent.DefaultFastParam,
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
        val frac = PhaseDetection.upcrossingFraction(vPrev, v, l)
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
