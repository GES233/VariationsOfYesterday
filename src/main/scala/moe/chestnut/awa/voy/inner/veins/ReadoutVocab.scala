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
enum SustenanceTier(val word: String):
  case Intact extends SustenanceTier("完好")
  case Damaged extends SustenanceTier("受损")
  case Dead extends SustenanceTier("枯绝")

  def isIntact: Boolean = this == SustenanceTier.Intact
  def isViable: Boolean = this != SustenanceTier.Dead
  def isDead: Boolean = this == SustenanceTier.Dead

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
  def sustenanceTier(
      sustenance: Double,
      floor: Double = StressParam().sustenanceFloor
  ): SustenanceTier =
    if sustenance < Math.max(floor, 1e-9) then SustenanceTier.Dead
    else if sustenance < IntactSustenance then SustenanceTier.Damaged
    else SustenanceTier.Intact

  /** Recovery offset where the cubic's right knee (`v = +1`) removes the middle
    * branch (`a = -0.4666...` with default parameters).
    */
  def burnoutA(fast: FastParam): Double =
    fast.beta * (1.0 - 1.0 / 3.0) - 1.0

  /** Slow recovery offset `a(s, M)` integrated by the ODE. */
  def slowA(
      fast: FastParam,
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
      fast: FastParam = MandateAgent.DefaultFastParam,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth
  ): Boolean =
    slowA(fast, stress, sustenance, coupling, depth) <= burnoutA(fast)

  /** Shared ecological incapacitation guard: dead mycelial network OR `a_slow`
    * collapsed below the knee.
    */
  def isEcologicallyDead(
      sustenance: Double,
      stress: Double,
      fast: FastParam = MandateAgent.DefaultFastParam,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth,
      floor: Double = StressParam().sustenanceFloor
  ): Boolean =
    sustenanceTier(sustenance, floor).isDead ||
      isSlowOffsetBelowKnee(stress, sustenance, fast, coupling, depth)

  /** Whether a limit cycle exists at this recovery offset. */
  def oscillating(a: Double, fast: FastParam): Boolean =
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
    if cur.excitation >= p.spikeLevel then PhaseLabel.Excitation
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

