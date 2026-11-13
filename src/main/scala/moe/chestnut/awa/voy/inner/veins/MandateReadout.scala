package moe.chestnut.awa.voy.inner.veins

/** Dimension 1: Slow-variable land stress regime (design draft §1.4). */
enum Regime:
  case Calm, Strained, Oscillating, Burnout

/** Dimension 2: Mycelial network health (`M`), unified across all consumers.
  *
  * Replaces the three formerly separate `M` threshold checks with one
  * canonical 3-tier partition:
  *   - `Intact` (`M >= 0.60`): healthy network capable of feeding symbiotic
  *     Gastrodia beds and emitting night-time bioluminescence;
  *   - `Damaged` (`floor <= M < 0.60`): viable living threads that self-regrow
  *     once stress drops below the acid burn threshold, while beds stall;
  *   - `Dead` (`M < floor`): depleted / absorbing state requiring external
  *     re-inoculation or neighbour diffusion.
  */
enum MycelialHealth(val word: String):
  case Intact extends MycelialHealth("完好")
  case Damaged extends MycelialHealth("受损")
  case Dead extends MycelialHealth("枯绝")

  def isIntact: Boolean = this == MycelialHealth.Intact
  def isViable: Boolean = this != MycelialHealth.Dead
  def isDead: Boolean = this == MycelialHealth.Dead

/** The four-phase vocabulary of the action-potential analogy (§1.4). */
enum PhaseLabel:
  case Resting, Excitation, AbsoluteRefractory, RelativeRefractory

/** Pure dynamical reasons why a state carries no cycle phase. */
enum UndefinedReason:
  /** The ODE's recovery offset `a_slow` sits at or below the burnout knee
    * `burnoutA` (due to mycelial depletion or high stress).
    */
  case SlowOffsetBelowKnee

  /** A non-repeating excursion, or a trace not settled into a regular cycle. */
  case TransientNoCycle

  /** Not enough observed cycles to define a phase. */
  case NoCycleReference

/** A cycle carries a fraction; `Basin` names a class without one; `Undefined`
  * names the dynamical reason. Only `Cycle` may carry a period fraction.
  */
sealed trait PhaseReading
object PhaseReading:
  final case class Cycle(label: PhaseLabel, fraction: Double) extends PhaseReading
  final case class Basin(label: PhaseLabel) extends PhaseReading
  final case class Undefined(reason: UndefinedReason) extends PhaseReading

/** Every readout threshold, in one place. */
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
  /** Minimum sustenance (`M`) for an intact mycelial network ("完好"). */
  val IntactSustenance: Double = 0.60

  /** Bisection steps for the resting fixed point. */
  private val RestPointBisectionSteps = 64

  /** Slow-variable regime partition, mirroring `Voy.World.Profile.regime/3`. */
  def regime(cur: MandateCurrent, p: ReadoutParam = ReadoutParam()): Regime =
    if cur.stress >= p.burnout then Regime.Burnout
    else if cur.activity >= p.oscillatingQ then Regime.Oscillating
    else if cur.stress >= p.strained then Regime.Strained
    else Regime.Calm

  /** Classify mycelial network health (`M`) into the canonical 3-tier enum. */
  def mycelialHealth(
      sustenance: Double,
      floor: Double = StressParam().sustenanceFloor
  ): MycelialHealth =
    if sustenance < Math.max(floor, 1e-9) then MycelialHealth.Dead
    else if sustenance < IntactSustenance then MycelialHealth.Damaged
    else MycelialHealth.Intact

  /** Recovery offset where the cubic's right knee (`v = +1`) removes the middle
    * branch (`a = -0.4666...` with default parameters).
    */
  def burnoutA(fast: MandateParam): Double =
    fast.beta * (1.0 - 1.0 / 3.0) - 1.0

  /** Slow recovery offset `a(s, M)` integrated by the ODE. */
  def slowA(
      fast: MandateParam,
      stress: Double,
      sustenance: Double = 1.0,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth
  ): Double =
    fast.alpha - depth * (1.0 - sustenance) - coupling * stress

  /** Whether the ODE's recovery offset `a_slow` sits at or below the burnout knee. */
  def isSlowOffsetBelowKnee(
      stress: Double,
      sustenance: Double,
      fast: MandateParam = MandateAgent.DefaultFastParam,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth
  ): Boolean =
    slowA(fast, stress, sustenance, coupling, depth) <= burnoutA(fast)

  /** Shared ecological incapacitation guard: dead mycelial network OR `a_slow`
    * collapsed below the knee.
    */
  def isEcologicalDeath(
      sustenance: Double,
      stress: Double,
      fast: MandateParam = MandateAgent.DefaultFastParam,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth,
      floor: Double = StressParam().sustenanceFloor
  ): Boolean =
    mycelialHealth(sustenance, floor).isDead ||
      isSlowOffsetBelowKnee(stress, sustenance, fast, coupling, depth)

  /** Whether a limit cycle exists at this recovery offset. */
  def oscillating(a: Double, fast: MandateParam): Boolean =
    val (_, ac) = PhaseResponse.hopfPoint(fast)
    a < ac && a > burnoutA(fast)

  /** Stable left-branch rest point for recovery offset `a`. */
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

  /** Cycle-fraction label. `Excitation` is the positive spike level (`v >= spikeLevel`). */
  def label(cur: MandateCurrent, fraction: Double, p: ReadoutParam = ReadoutParam()): PhaseLabel =
    if cur.dissonance >= p.spikeLevel then PhaseLabel.Excitation
    else if fraction < p.fireWindowLo then PhaseLabel.AbsoluteRefractory
    else if fraction <= p.fireWindowHi then PhaseLabel.RelativeRefractory
    else PhaseLabel.Resting

  /** Cycle fraction `[0, 1)` -> 20-division dial index `0..19`. */
  def dialTick(phase: PhaseReading, divisions: Int = 20): Option[Int] =
    phase match
      case PhaseReading.Cycle(_, fraction) =>
        val normalized = ((fraction % 1.0) + 1.0) % 1.0
        Some(Math.min(divisions - 1, Math.floor(normalized * divisions).toInt))
      case _ => None
end Readout

/** Slim, self-contained semantic readout of one cell published to the game side.
  *
  * Exposes three orthogonal dimensions without leaking internal parameter bags:
  *   1. **Dynamics**: `regime`, `phase`, `isSpiking`, `isSlowOffsetBelowKnee`
  *   2. **Mycelium**: `mycelium` (`Intact` / `Damaged` / `Dead`), `isToxicSoil`
  *   3. **Shelter**: `isSheltered` (active `pulseStamp` window on viable,
  *      non-collapsed, non-spiking ground)
  */
final case class CellReadout(
    current: MandateCurrent,
    regime: Regime,
    phase: PhaseReading,
    sustenance: Double = 1.0,
    baselineCalming: Double = 0.0,
    pulseDrive: Double = 0.0,
    pulseStamp: PulseStamp = PulseStamp.zero,
    simTime: Double = 0.0,
    sustenanceFloor: Double = StressParam().sustenanceFloor,
    toxicityThreshold: Double = StressParam().sustenanceBurnThreshold
):
  /** Canonical 3-tier mycelial health of this cell. */
  def mycelium: MycelialHealth = Readout.mycelialHealth(sustenance, sustenanceFloor)

  /** Whether the cell is currently in a positive spike excursion (`v >= spikeLevel`). */
  def isSpiking: Boolean = current.dissonance >= PhaseDetection.SpikeLevel

  /** Whether the cell's slow recovery offset sits at or below the burnout knee. */
  def isSlowOffsetBelowKnee: Boolean =
    phase == PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee) ||
      Readout.isSlowOffsetBelowKnee(current.stress, sustenance)

  /** Whether soil stress is at or above the acid burn threshold that kills inoculum. */
  def isToxicSoil: Boolean = current.stress >= toxicityThreshold

  /** Whether the cell has an active pulse shelter window right now. */
  def isPulseSheltered: Boolean = pulseStamp.isShelteredAt(simTime)

  /** Dimension 3: Whether this chunk is sheltered from `沉滞` (Stagnation). */
  def isSheltered: Boolean =
    isPulseSheltered &&
      mycelium.isViable &&
      !isSlowOffsetBelowKnee &&
      !isSpiking

  /** 20-division mountain dial tick (`0..19`) when in a regular cycle. */
  def dialTick: Option[Int] = Readout.dialTick(phase)

object CellReadout:
  val zero: CellReadout = CellReadout(
    current = MandateCurrent(0.0, 0.0, 0.0, 0.0),
    regime = Regime.Calm,
    phase = PhaseReading.Basin(PhaseLabel.Resting),
    baselineCalming = 0.0
  )

/** Pure mathematical Poincaré section-phase tracker for one cell:
  * `fraction = (t - t_cross) / T_est (mod 1)`.
  *
  * Free of gameplay lockouts and stacked-pulse rules: observes only the
  * continuous trajectory `(v, w, s)` and sustenance `M`.
  */
class PhaseTracker(
    param: ReadoutParam = ReadoutParam(),
    fast: MandateParam = MandateAgent.DefaultFastParam,
    coupling: Double = 1.0,
    depth: Double = StressParam().sustenanceDepth
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

  def observe(t: Double, cur: MandateCurrent, sustenance: Double = 1.0): PhaseReading =
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
    reading(t, cur, sustenance)

  def reading(t: Double, cur: MandateCurrent, sustenance: Double = 1.0): PhaseReading =
    val aSlow = Readout.slowA(fast, cur.stress, sustenance, coupling, depth)
    if aSlow <= Readout.burnoutA(fast) then
      PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee)
    else if !Readout.oscillating(aSlow, fast) then
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
