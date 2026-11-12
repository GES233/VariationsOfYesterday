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
  * must never be collapsed into a shared "undefined".
  *
  * Batch 1 (docs/work-in-progress-mechanism-decoupling.md §3.3) split the
  * former `BurnoutNoCycle` into one READOUT-RULE condition and one MECHANISM
  * rule. Both are facts about this cell (not about which one the display
  * happens to show): they can hold at the same time, they can hold
  * independently, and a consumer must be able to query each one separately.
  *
  * Boundary (learned the hard way): the threshold condition lives on
  * `effectiveA`, which is a READOUT-side quantity (it folds in a stacked-pulse
  * correction). The ODE integrates `a = alpha - depth*(1-M) - coupling*s` and
  * sees pulse drive as an explicit `dv/dt` term instead. So crossing the
  * threshold means "this rule labels the cell no-cycle", NOT "the trajectory
  * has been proven to have no limit cycle" — a cell can keep oscillating
  * while the rule says no-cycle (design note 08 measured exactly that).
  */
enum UndefinedReason:
  /** Current readout rule: the effective recovery offset `a_eff` (which
    * includes the readout-side stacked-pulse correction) sits at or below the
    * burnout knee `burnoutA`. It may be chronic (mycelial depletion, land
    * stress) or transient (stacked pulse drive above a single bell kernel),
    * so this alone does not mean the mycelium is dead — and it does not by
    * itself prove the ODE trajectory stopped oscillating.
    */
  case OffsetBelowKnee

  /** Mechanism rule: the cell's pulse ledger holds an active acute overload
    * lockout. The bare trajectory may still be oscillating; this reason is
    * the discrete rule's verdict, not an observation of the equations.
    */
  case OverloadLocked

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
  * not decide whether a phase exists — that is the recovery offset, below; the
  * slice reaches `s = stress_max = 1.0` and still spikes.
  *
  * Boundary: the offset that gates a phase is the READOUT-rule `effectiveA`
  * (it folds in a stacked-pulse correction that the governing equations do not
  * contain), while the ODE integrates `slowA`. Crossing [[Readout.burnoutA]]
  * on `effectiveA` is a rule verdict ("this readout has no cycle phase here"),
  * not a proof about the trajectory.
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

  /** Per-cell fact: the mycelial network still holds viable threads (`M` at or
    * above the Allee floor). This is NETWORK SURVIVAL — independent of whether
    * the dynamics currently admit a cycle phase and independent of any pulse
    * ledger rule. It does not say the land is healthy: `M` between the floor
    * and 0.60 is a damaged network that still lives (design note 06 §4.3).
    */
  def isMyceliumViable(sustenance: Double, floor: Double): Boolean =
    sustenance >= floor

  /** Per-cell fact: the ODE's own recovery offset `a_slow` sits at or below the
    * burnout knee. This is the dynamical-side statement — as close as the
    * readout gets to "this offset admits no limit cycle". It is NOT the
    * quantity that gates the displayed phase (that is the readout-rule
    * [[effectiveA]]/[[isOffsetBelowKnee]]), and it does NOT mean the network is
    * dead: `M` is the separate fact [[isMyceliumViable]]. A cell can be below
    * the knee and still alive, and can be alive while the readout rule labels
    * it no-cycle.
    */
  def isSlowOffsetBelowKnee(
      stress: Double,
      sustenance: Double,
      fast: MandateParam,
      coupling: Double,
      depth: Double
  ): Boolean =
    slowA(fast, stress, sustenance, coupling, depth) <= burnoutA(fast)

  /** The ecological-death guard: dead network or a collapsed slow offset. The
    * agent's "dead cells do not bookkeep" rule calls this directly; the bell
    * dispatch reaches the same two clauses through [[isEcologicalBurnout]].
    * Raw numbers so the agent can ask before a [[CellReadout]] snapshot exists;
    * the snapshot overload is [[isEcologicalDeath(readout:CellReadout)]].
    *
    * This is a guard, not a damage model: it does not add a load-to-damage
    * path. The physical route `p -> v -> q -> s -> M` is the only way a pulse
    * currently reaches `M` (docs/work-in-progress-mechanism-decoupling.md §3.7).
    */
  def isEcologicalDeath(
      sustenance: Double,
      stress: Double,
      fast: MandateParam,
      coupling: Double,
      depth: Double,
      floor: Double
  ): Boolean =
    !isMyceliumViable(sustenance, floor) ||
      isSlowOffsetBelowKnee(stress, sustenance, fast, coupling, depth)

  /** READOUT-RULE recovery offset: the ODE's `slowA` minus a correction for
    * stacked pulse drive above a single bell peak.
    *
    * The pulse term is **not** in the governing equations: the ODE integrates
    * `a = alpha - depth*(1-M) - coupling*s` and applies pulse drive as an
    * explicit `dv/dt` term (`impulse + synapseP`). This number therefore
    * belongs to the phase-gating rule only. `effectiveA <= burnoutA` means
    * "this rule labels the cell no-cycle"; it is not evidence that the
    * trajectory has no limit cycle (note 08 measured cells that keep spiking
    * under such a label).
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

  /** The current readout rule's threshold condition for one cell: its
    * `effectiveA` (readout-side, see [[effectiveA]]) is at or below
    * [[burnoutA]]. This is the criterion behind
    * `UndefinedReason.OffsetBelowKnee`, evaluated on the cell's own published
    * facts, so it stays true even when the display publishes the mechanism
    * reason instead.
    */
  def isOffsetBelowKnee(readout: CellReadout): Boolean =
    val aEff = effectiveA(
      readout.fastParam,
      readout.current.stress,
      readout.sustenance,
      readout.stressParam.coupling,
      readout.stressParam.sustenanceDepth,
      readout.pulseDrive,
      readout.pulseParam
    )
    aEff <= burnoutA(readout.fastParam)

  /** Per-cell fact: the mycelial network is still viable (`M >= floor`, the
    * cell's own Allee floor). See the raw overload for the boundary.
    */
  def isMyceliumViable(readout: CellReadout): Boolean =
    isMyceliumViable(readout.sustenance, readout.stressParam.sustenanceFloor)

  /** Per-cell fact: the network is truly gone (`M <= 0`, the absorbing state).
    * STRICTER than `!isMyceliumViable`: the dynamics snap `M` to `0` only when
    * it falls below the Allee floor AND the local trend is non-positive
    * (`MandateState`), so a patch at the edge of a dead spot can be refilled by
    * diffusion from living neighbours, or set by the narrative `SetSustenance`
    * channel, and sit at `0 < M < floor` in live play. The silent signature
    * binds to THIS fact (`M <= 0`), not to the Allee floor (design note 06 §3).
    */
  def isMyceliumAbsent(readout: CellReadout): Boolean =
    readout.sustenance <= 0.0

  /** Per-cell fact: `a_slow` — the offset the ODE actually integrates — is at
    * or below the knee, using the cell's published parameters. Unlike
    * [[isOffsetBelowKnee]] this carries no stacked-pulse correction, so it is
    * the closer-to-dynamics statement; it still says nothing about `M`.
    */
  def isSlowOffsetBelowKnee(readout: CellReadout): Boolean =
    isSlowOffsetBelowKnee(
      readout.current.stress,
      readout.sustenance,
      readout.fastParam,
      readout.stressParam.coupling,
      readout.stressParam.sustenanceDepth
    )

  /** Per-cell fact: the cell has passed the ecological-death guard (dead
    * network or `a_slow` below the knee), using the cell's own parameters.
    */
  def isEcologicalDeath(readout: CellReadout): Boolean =
    isEcologicalDeath(
      readout.sustenance,
      readout.current.stress,
      readout.fastParam,
      readout.stressParam.coupling,
      readout.stressParam.sustenanceDepth,
      readout.stressParam.sustenanceFloor
    )

  /** Per-cell fact: the cell carries a POSITIVE spike (`v >= spikeLevel`). The
    * negative repolarisation overshoot is not a spike (same convention as
    * [[label]]); consumers that need "any excursion" test `|v|` explicitly, as
    * the seep classifier does.
    */
  def hasActivePositiveSpike(readout: CellReadout, p: ReadoutParam = ReadoutParam()): Boolean =
    readout.current.dissonance >= p.spikeLevel

  /** Chronic burnout (mycelial depletion or the ODE's slow offset across the
    * knee), as opposed to an acute pulse-overload lockout.
    *
    * Batch 2 restates the three clauses as named facts:
    *   - dead network: `!isMyceliumViable` (the raw overload keeps the
    *     historical explicit-parameter seam for the frozen-slice tests);
    *   - `a_slow` across the knee: [[isSlowOffsetBelowKnee]];
    *   - the readout rule with no stacked pulse drive beyond one bell kernel:
    *     [[isOffsetBelowKnee]] AND `pulseDrive <= singlePulsePeak` (at or below
    *     one kernel `a_eff == a_slow`, so this clause is then a consequence of
    *     the second one; it stays explicit because it is the intent).
    *
    * Note on parameters: the explicit-argument overload defaults `fast` to the
    * CORE default and `coupling`/`depth` to their defaults, exactly as before.
    * A caller that wants the cell's own numbers must pass them (the bell
    * dispatch does); the snapshot overlays ([[isEcologicalDeath(readout:CellReadout)]],
    * `readout.isEcologicalDeath`) use the cell's published set.
    */
  def isEcologicalBurnout(
      readout: CellReadout,
      fast: MandateParam = MandateAgent.DefaultFastParam,
      coupling: Double = 1.0,
      depth: Double = StressParam().sustenanceDepth,
      pulseParam: PulseToleranceParam = PulseToleranceParam()
  ): Boolean =
    isEcologicalDeath(
      readout.sustenance,
      readout.current.stress,
      fast,
      coupling,
      depth,
      readout.stressParam.sustenanceFloor
    ) ||
      (readout.isOffsetBelowKnee && readout.pulseDrive <= pulseParam.singlePulsePeak)

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
    * mycelial network, no no-cycle reason, and no active positive spike.
    *
    * Batch 2 spells the four clauses out as named facts (`pulseSuppressed`,
    * [[isMyceliumViable]], [[CellReadout.isBurnoutPhase]],
    * [[hasActivePositiveSpike]]). The VALUE is unchanged: both no-cycle reasons
    * still block shelter, so an acute overload lockout still withdraws the
    * shelter it just collapsed. Whether a *short* lockout should block shelter
    * at all is a pending gameplay decision, not an implementation detail — see
    * docs/mechanism-state-responsibilities.md §8.4/§8.5.
    *
    * This remains the single entry point for shelter, economy and player
    * effects (couplings C8/C9/C13 in the same document). Splitting consumers
    * onto the individual facts is the stage-C decision that batch 2 only
    * prepared, not performed.
    *
    * Edge (outside the domain): NaN input makes the negated facts behave
    * differently from the pre-batch strict comparisons. Normal finite config
    * and save data never produce NaN, but a non-finite config value, an
    * extreme `tickDt` overflowing RK4, or a corrupt save double can. The readout
    * is only defined for finite values, so this is documented, not compensated.
    */
  def isMaintained(
      readout: CellReadout,
      p: ReadoutParam = ReadoutParam()
  ): Boolean =
    readout.pulseSuppressed &&
      readout.isMyceliumViable &&
      !readout.isBurnoutPhase &&
      !readout.hasActivePositiveSpike(p)

  /** Cycle fraction `[0, 1)` -> 20-division dial index `0..19`. */
  def dialTick(phase: PhaseReading, divisions: Int = 20): Option[Int] =
    phase match
      case PhaseReading.Cycle(_, fraction) =>
        val normalized = ((fraction % 1.0) + 1.0) % 1.0
        Some(Math.min(divisions - 1, Math.floor(normalized * divisions).toInt))
      case _ => None
end Readout

/** One cell's semantic snapshot published to the game side, including the
  * per-cell parameters the cell actually integrates.
  *
  * The pulse mechanisms are published as separate facts: `pulseLedger` owns
  * shelter, tolerance and overload ([[PulseLedger]]), and `pulseSuppressed` is
  * the precomputed derived convenience "sheltered right now".
  *
  * `phase` is a DISPLAY: it has one slot, so when a cell is both no-cycle by
  * the readout rule and locked out by the ledger, only one reason can be
  * shown. The two underlying facts are therefore queried independently —
  * `isOffsetBelowKnee` from this cell's own threshold condition and
  * `isOverloadLocked` from its ledger — and `isBurnoutPhase` is their union.
  * Never reverse-infer a fact from the displayed reason.
  *
  * Batch 2 adds the survival and dynamical facts next to the pulse ones:
  * [[isMyceliumViable]] / [[isMyceliumAbsent]] (network survival, two different
  * thresholds), [[isSlowOffsetBelowKnee]] (the ODE-side offset, closer to
  * dynamics than the readout rule), [[isEcologicalDeath]] (the guard shared by
  * the agent and the bell dispatch) and [[hasActivePositiveSpike]]. Each is
  * computed from its own criterion, so none of them is read off another.
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
    pulseLedger: PulseLedger = PulseLedger.zero,
    simTime: Double = 0.0,
    // Per-cell parameter sets (save > config > core default): the game layer
    // must judge this cell with the parameters the cell actually integrates,
    // not with the current global config (design draft §7.3).
    fastParam: MandateParam = MandateAgent.DefaultFastParam,
    stressParam: StressParam = MandateAgent.DefaultStressParam,
    // The pulse parameter set the cell's ledger was settled with. The readout
    // threshold rule folds the stacked-pulse correction into `effectiveA`, so
    // it needs the same numbers the ledger used — a config change must not
    // silently re-judge an old cell with new constants.
    pulseParam: PulseToleranceParam = PulseToleranceParam()
):
  def isMaintained: Boolean = Readout.isMaintained(this)
  def dialTick: Option[Int] = Readout.dialTick(phase)

  /** Current readout rule's threshold condition, evaluated on this cell's own
    * published facts (`effectiveA <= burnoutA`, stacked-pulse correction
    * included) — NOT inferred from `phase`.
    *
    * It can be true while the display publishes `OverloadLocked` (the
    * mechanism reason wins the single `phase` slot), and it is a readout rule,
    * not proof that the ODE trajectory has no limit cycle.
    */
  def isOffsetBelowKnee: Boolean = Readout.isOffsetBelowKnee(this)

  /** Mechanism rule: this cell's pulse ledger holds an active acute overload
    * lockout at `simTime`. Taken from the ledger itself, so it is true even
    * when the display publishes the offset-threshold reason instead.
    */
  def isOverloadLocked: Boolean = pulseLedger.overloadedAt(simTime)

  /** Network-survival fact (`M >= floor`): the mycelium still lives, whether
    * or not the dynamics have a phase and whether or not a pulse rule fires.
    */
  def isMyceliumViable: Boolean = Readout.isMyceliumViable(this)

  /** Absorbing-state fact (`M <= 0`): the network is gone, not merely
    * depleted. The silent signature binds to this, not to the Allee floor.
    */
  def isMyceliumAbsent: Boolean = Readout.isMyceliumAbsent(this)

  /** Dynamical fact: the ODE's own offset `a_slow` is at or below the knee
    * (no stacked-pulse correction; see [[Readout.isOffsetBelowKnee]] for the
    * readout rule that gates the displayed phase).
    */
  def isSlowOffsetBelowKnee: Boolean = Readout.isSlowOffsetBelowKnee(this)

  /** Shared death guard (dead network OR `a_slow` below the knee), evaluated
    * with this cell's published parameters. The agent calls this directly to
    * skip bookkeeping; the bell dispatch reaches it through
    * `Readout.isEcologicalBurnout`.
    */
  def isEcologicalDeath: Boolean = Readout.isEcologicalDeath(this)

  /** Positive-spike fact (`v >= spikeLevel`); the negative repolarisation
    * overshoot does not count.
    */
  def hasActivePositiveSpike(p: ReadoutParam = ReadoutParam()): Boolean =
    Readout.hasActivePositiveSpike(this, p)

  /** The coarse "no cycle phase, cell reads as burnt out" predicate the
    * batch-1 consumers share. Its value is the union of the two facts above,
    * which is exactly what the phase tracker publishes as the two no-cycle
    * reasons — so consumer behaviour is unchanged even though the facts are
    * now queried independently.
    *
    * It deliberately covers BOTH reasons: keeping today's gameplay outcomes
    * unchanged is the batch-1 constraint. Which consumer should keep treating
    * an acute lockout like a chronic burnout is a pending decision — see
    * docs/mechanism-state-responsibilities.md (待拍板).
    */
  def isBurnoutPhase: Boolean = isOffsetBelowKnee || isOverloadLocked

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
    // DISPLAY priority only: the mechanism rule is shown before the
    // readout-threshold reason because a lockout is not an observation of the
    // equations. This is the single-slot display, never the source of the
    // per-fact queries (`CellReadout.isOffsetBelowKnee` /
    // `isOverloadLocked` evaluate their own criteria).
    if overloaded() then
      PhaseReading.Undefined(UndefinedReason.OverloadLocked)
    else if aEff <= Readout.burnoutA(fast) then
      PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee)
    else if !Readout.oscillating(aSlow, fast) then
      // With the ODE's own recovery offset (no pulse drive folded in) this
      // operating point has no limit cycle, so the only phase-free reading
      // left is the resting basin itself. An excursion in flight is a
      // transient — note the offset here is `a_slow`, the quantity the ODE
      // actually integrates, not the readout-rule `a_eff`.
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
