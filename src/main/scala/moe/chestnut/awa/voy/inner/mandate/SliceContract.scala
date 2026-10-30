package moe.chestnut.awa.voy.inner.mandate

import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest

/** The cycle windows the relational layer is allowed to name.
  *
  * `absolute` and `relative` are the two `Voy.World.Profile` already
  * declares; `late` is the recovery tail §1.3 and §1.1.4 both describe
  * ("恢复晚期 ≳65%") but the stock never named. Bounds are the *declared*
  * ones; the measured behaviour at each window's midpoint lives next to
  * them in the contract oracle, and the two are compared rather than
  * assumed equal.
  */
enum PhaseWindow(val wire: String, val lo: Double, val hi: Double):
  case Absolute extends PhaseWindow("absolute", 0.20, 0.30)
  case Relative extends PhaseWindow("relative", 0.40, 0.60)
  case Late extends PhaseWindow("late", 0.75, 1.00)

  def mid: Double = 0.5 * (lo + hi)
  def contains(phase: Double): Boolean = lo <= phase && phase <= hi

/** Versioned, cross-stack contract for the 0.1 vertical slice.
  *
  * `contracts/mandate-slice-v1.json` is the single artifact both stacks
  * read: Scala owns the numeric oracle (Hopf point, measured period, the
  * phase-response table, c0 footprints, chain behaviour) and Elixir owns
  * the symbolic rules. Every number here is either
  *
  *   - **analytic** (closed form, no integration), or
  *   - **measured** at a declared step, with its step convergence recorded
  *     next to it and an independent reference to compare against;
  *
  * and every design-draft claim the slice depends on is recorded with the
  * verdict the measurement supports. Disagreement is data: the contract
  * exists to carry it across the stack, not to hide it.
  */
object SliceContract:
  val contractId: String = "voy.mandate.slice"
  val version: Int = 1
  val fileName: String = "contracts/mandate-slice-v1.json"

  val fast: MandateParam = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
  val dt: Double = 0.02

  final case class WindowRow(window: PhaseWindow, trial: Trial)
  final case class SafeTick(tick: Int, phase: Double, delta: Double, window: String)
  final case class KindRow(
      kind: PulseKind,
      role: String,
      fire: Option[(Double, Double)],
      delay: Option[(Double, Double)],
      absorb: Option[(Double, Double)],
      table: Seq[WindowRow],
      safeTicks: Seq[SafeTick]
  )
  final case class PeriodRow(dt: Double, measured: Double)
  final case class RegimeSample(stress: Double, activity: Double, regime: Regime)
  final case class Claim(id: String, draft: String, measured: String, verdict: String)
  final case class OnsetComparison(
      duty: DutyCycle,
      midTrial: Trial
  )
  final case class Data(
      digest: String,
      hopfV: Double,
      hopfA: Double,
      hopfS: Double,
      relaxation: Double,
      oscA: Double,
      oscStress: Double,
      period: Double,
      duty: DutyCycle,
      onset: OnsetComparison,
      controlSpike: Option[Spike],
      periods: Seq[PeriodRow],
      kinds: Seq[KindRow],
      fireThreshold: Option[Double],
      collapsed: SliceReplay.Truth,
      intact: SliceReplay.Truth,
      chain: SliceReplay.ChainObservation,
      samples: Seq[RegimeSample]
  ):
    def kind(wire: String): Option[KindRow] = kinds.find(_.kind.wire == wire)

    /** Design-draft claims the slice leans on, each with what the
      * measurement says about it. Generated, never hand-written.
      */
    def claims: Seq[Claim] =
      val alpha = kind(PulseKind.AlphaShock.wire)
      def outcomeAt(w: PhaseWindow): String =
        alpha.flatMap(_.table.find(_.window == w)).map(_.trial.outcome.toString).getOrElse("Unmeasured")
      val anyPremature = kinds.exists(_.fire.isDefined)
      Seq(
        Claim(
          "hopf.a_c",
          "a_c = 0.467, s_c = 0.233 (§1.1.1)",
          f"$hopfA%.6f / $hopfS%.6f from the exact trace condition",
          if Math.abs(hopfA - 0.467) < 0.005 then "supported" else "refuted"
        ),
        Claim(
          "prc.vulnerable_window_fires",
          "a pulse landing at 40-60% of the cycle fires a premature full surge (§1.1.4)",
          s"measured outcome for alpha_shock at the relative window midpoint: ${outcomeAt(PhaseWindow.Relative)}",
          if outcomeAt(PhaseWindow.Relative) == "Premature" then "supported" else "refuted"
        ),
        Claim(
          "prc.absolute_window_delays",
          "a pulse landing at 20-30% of the cycle delays the next surge (§1.1.4)",
          s"measured outcome for alpha_shock at the absolute window midpoint: ${outcomeAt(PhaseWindow.Absolute)}",
          if outcomeAt(PhaseWindow.Absolute) == "Delayed" then "supported" else "refuted"
        ),
        Claim(
          "prc.window_structure_exists",
          "the phase response has a fire window somewhere in the cycle (§1.1.4)",
          if anyPremature then "a premature window exists for at least one declared pulse"
          else "no declared pulse fires anywhere; the response is monotone in phase",
          if anyPremature then "supported" else "refuted"
        ),
        Claim(
          "period.singular_limit",
          "the eps->0 slow-manifold integral reproduces the cycle period",
          f"reference $relaxation%.3f vs measured $period%.3f",
          if period > 0 && Math.abs(period - relaxation) / period < 0.10 then "supported"
          else "refuted-at-eps=0.08"
        )
      )

  /** The two c0 states of the slice: the settlement either kept its
    * baseline calming or lost it (§1.1.3, §7.8).
    */
  val c0Collapsed: Double = 0.0
  val c0Intact: Double = 0.03

  def regimeSamples: Seq[RegimeSample] =
    Seq((0.00, 0.00), (0.17, 0.00), (0.18, 0.00), (0.45, 0.00), (0.50, 0.39),
      (0.50, 0.40), (0.94, 0.00), (0.95, 0.00), (0.99, 0.90), (0.10, 0.50))
      .map { case (s, q) =>
        RegimeSample(s, q, Readout.regime(MandateCurrent(0.0, 0.0, s, q)))
      }

  // ==== measurement ==== //

  /** Run every experiment the contract records. Expensive (a few seconds);
    * call once per test run.
    */
  def measure(): Data =
    val (vc, ac) = PhaseResponse.hopfPoint(fast)
    val pulse = Some((300.0, PulseKind.AlphaShock))
    val collapsed = SliceReplay.truth(
      site = "branch_01_baishui/up",
      c0 = c0Collapsed,
      tEnd = 4000.0,
      pulse = pulse,
      measureFrom = 2500.0
    )
    val intact = SliceReplay.truth(
      site = "branch_01_baishui/up",
      c0 = c0Intact,
      tEnd = 4000.0,
      pulse = pulse,
      measureFrom = 2500.0
    )

    // The oscillator runs where the slice actually oscillates: the recovery
    // offset implied by the mean stress of the ignited replay.
    val a = round3(fast.alpha - collapsed.meanStress)
    val osc = Oscillator(a, fast)
    val (snapshot, period) = PhaseResponse.reference(osc, dt, warmup = 2500.0, cycles = 4)
    val control = PhaseResponse.controlSpike(osc, snapshot, dt)
    val duty = PhaseResponse.dutyCycle(osc, snapshot, dt, period)

    // Near-Hopf onset oscillator (s = 0.30, a = 0.40) for duty-cycle
    // inversion comparison: here the depolarised plateau is short (< 35% of T)
    // so phase = 0.50 lands on the hyperpolarised left recovery branch (v < 0).
    val oscOnset = Oscillator(0.40, fast)
    val (snapOnset, periodOnset) = PhaseResponse.reference(oscOnset, dt, warmup = 2500.0, cycles = 4)
    val controlOnset = PhaseResponse.controlSpike(oscOnset, snapOnset, dt)
    val dutyOnset = PhaseResponse.dutyCycle(oscOnset, snapOnset, dt, periodOnset)
    val midTrialOnset = PhaseResponse.trial(
      oscOnset,
      snapOnset,
      dt,
      periodOnset,
      PhaseWindow.Relative.mid,
      PulseKind.AlphaShock,
      controlOnset
    )

    val periods = Seq(dt, dt / 2.0, dt / 4.0).map { d =>
      PeriodRow(d, PhaseResponse.reference(osc, d, warmup = 2500.0, cycles = 4)._2)
    }

    val kinds = PulseKind.values.toSeq.map { k =>
      val trials = PhaseResponse.sweep(osc, snapshot, dt, period, k, steps = 40)
      val table = PhaseWindow.values.toSeq.map { w =>
        WindowRow(w, PhaseResponse.trial(osc, snapshot, dt, period, w.mid, k, control))
      }
      val safeTicks = PhaseResponse.tickTrials(osc, snapshot, dt, period, k).flatMap { case (tick, trial) =>
        trial.delta.filter(_ => trial.outcome == PulseOutcome.Delayed).map { delta =>
          val window = PhaseWindow.values.find(_.contains(trial.phase)).map(_.wire).getOrElse("other")
          SafeTick(tick, trial.phase, delta, window)
        }
      }
      KindRow(
        kind = k,
        role = if k == PulseKind.AlphaShockWeak then "control" else "world",
        fire = PhaseResponse.span(trials, PulseOutcome.Premature),
        delay = PhaseResponse.span(trials, PulseOutcome.Delayed),
        absorb = PhaseResponse.span(trials, PulseOutcome.Absorbed),
        table = table,
        safeTicks = safeTicks
      )
    }

    Data(
      digest = sourceDigest,
      hopfV = vc,
      hopfA = ac,
      hopfS = fast.alpha - ac,
      relaxation = PhaseResponse.relaxationPeriod(a, fast),
      oscA = a,
      oscStress = collapsed.meanStress,
      period = period,
      duty = duty,
      onset = OnsetComparison(dutyOnset, midTrialOnset),
      controlSpike = control,
      periods = periods,
      kinds = kinds,
      fireThreshold = PhaseResponse.fireThreshold(
        osc,
        snapshot,
        dt,
        period,
        phase = PhaseWindow.Relative.mid,
        tau = 5.0,
        control = control
      ),
      collapsed = collapsed,
      intact = intact,
      chain = SliceReplay.chainObservation(advection = 0.001, tEnd = 9000.0),
      samples = regimeSamples
    )

  // ==== rendering ==== //

  def render(d: Data): String = Json.render(toJson(d))

  def toJson(d: Data): Json.J =
    import Json.*
    Obj(
      Seq(
        "contract" -> Str(contractId),
        "version" -> Num(version.toDouble),
        "provenance" -> Obj(
          Seq(
            "numeric_sources" -> Arr(
              Seq(
                "src/main/scala/moe/chestnut/awa/voy/inner/mandate/MandateState.scala",
                "src/main/scala/moe/chestnut/awa/voy/helpers/ODESolver.scala"
              ).map(Str(_))
            ),
            "numeric_source_digest" -> Str(d.digest),
            "state0" -> Obj(
              Seq(
                "dissonance" -> Num(-1.2),
                "entrench" -> Num(-0.6),
                "stress" -> Num(0.0),
                "activity" -> Num(0.0)
              )
            ),
            "param" -> Obj(
              Seq(
                "alpha" -> Num(fast.alpha),
                "beta" -> Num(fast.beta),
                "epsilon" -> Num(fast.epsilon)
              )
            ),
            "step" -> Num(dt),
            "section_level" -> Num(PhaseResponse.section),
            "spike_level" -> Num(PhaseResponse.spikeLevel),
            "spike_rule" ->
              Str(
                "a genuine excursion = section upcrossing followed by a peak >= spike_level " +
                  "before the fast variable falls back through zero; a driven crossing that " +
                  "stalls below spike_level is not a spike"
              ),
            "regeneration" ->
              Str(
                "gradlew test -Dvoy.contract.write=true (deliberate, reviewed change only); " +
                  "verified by SliceContractSpec on every run"
              )
          )
        ),
        "analysis" -> Obj(
          Seq(
            "hopf" -> Obj(
              Seq(
                "formula" -> Str("trace(J)=0 => v_c=-sqrt(1-eps*beta); a_c=beta*(v_c-v_c^3/3)-v_c"),
                "v_c" -> Num(d.hopfV),
                "a_c" -> Num(d.hopfA),
                "s_c" -> Num(d.hopfS)
              )
            ),
            "relaxation" -> Obj(
              Seq(
                "a" -> Num(d.oscA),
                "period" -> Num(d.relaxation),
                "formula" -> Str("eps->0 slow-manifold integral over [-2/3, 2/3]"),
                "caveat" -> Str("ignores the O(eps) jump time; a reference, not a verification")
              )
            )
          )
        ),
        "oracle" -> Obj(
          Seq(
            "oscillator" -> Obj(
              Seq(
                "a" -> Num(d.oscA),
                "stress" -> Num(round3(d.oscStress)),
                "plateau_duration" -> Num(d.duty.plateauDuration),
                "plateau_fraction" -> Num(d.duty.plateauFraction),
                "v_at_mid_cycle" -> Num(d.duty.vAtMidCycle),
                "whence" -> Str("alpha - mean stress of the ignited c0-collapsed replay")
              )
            ),
            "onset_comparison" -> Obj(
              Seq(
                "a" -> Num(d.onset.duty.a),
                "stress" -> Num(round3(fast.alpha - d.onset.duty.a)),
                "period" -> Num(d.onset.duty.period),
                "plateau_duration" -> Num(d.onset.duty.plateauDuration),
                "plateau_fraction" -> Num(d.onset.duty.plateauFraction),
                "v_at_mid_cycle" -> Num(d.onset.duty.vAtMidCycle),
                "mid_cycle_outcome" -> Str(d.onset.midTrial.outcome.toString),
                "mid_cycle_delta" -> optNum(d.onset.midTrial.delta),
                "mid_cycle_v_peak" -> Num(d.onset.midTrial.vPeak)
              )
            ),
            "period" -> Obj(
              Seq(
                "measured" -> Num(d.period),
                "control_spike_at" -> Num(d.controlSpike.map(_.at).getOrElse(Double.NaN)),
                "control_spike_peak" -> Num(d.controlSpike.map(_.peak).getOrElse(Double.NaN)),
                "rows" -> Arr(d.periods.map { r =>
                  Obj(Seq("dt" -> Num(r.dt), "measured" -> Num(r.measured)))
                }),
                "reference" -> Num(d.relaxation),
                "relative_error" -> Num(relError(d.period, d.relaxation))
              )
            ),
            "fire_threshold" -> Obj(
              Seq(
                "phase" -> Num(PhaseWindow.Relative.mid),
                "tau" -> Num(5.0),
                "area" -> optNum(d.fireThreshold),
                "peak" -> optNum(d.fireThreshold.map(a => a / (Math.E * 5.0))),
                "declared_alpha_shock_area" -> Num(PulseKind.AlphaShock.shape.area)
              )
            ),
            "c0" -> Obj(
              Seq(
                "collapsed" -> truthJson(d.collapsed),
                "intact" -> truthJson(d.intact)
              )
            ),
            "kinds" -> Arr(d.kinds.map(kindJson)),
            "chain" -> Obj(
              Seq(
                "advection" -> Num(d.chain.advection),
                "upstream_period" -> optNum(d.chain.upstreamPeriod),
                "downstream_period" -> optNum(d.chain.downstreamPeriod),
                "lag_early" -> optNum(d.chain.lagEarly),
                "lag_late" -> optNum(d.chain.lagLate),
                "drift_per_cycle" -> optNum(d.chain.driftPerCycle),
                "lag_drift_cycles" -> optNum(d.chain.lagDriftCycles),
                "relative_period_gap" -> optNum(d.chain.relativePeriodGap),
                "composable" -> Bool(d.chain.composable)
              )
            ),
            "regime_samples" -> Arr(d.samples.map { s =>
              Obj(
                Seq(
                  "stress" -> Num(s.stress),
                  "activity" -> Num(s.activity),
                  "regime" -> Str(s.regime.toString)
                )
              )
            })
          )
        ),
        "symbolic" -> Obj(
          Seq(
            "declared_windows" -> Obj(
              PhaseWindow.values.toSeq.map(w => w.wire -> Arr(Seq(Num(w.lo), Num(w.hi))))
            ),
            "claims" -> Arr(
              d.claims.map(c =>
                Obj(
                  Seq(
                    "id" -> Str(c.id),
                    "draft" -> Str(c.draft),
                    "measured" -> Str(c.measured),
                    "verdict" -> Str(c.verdict)
                  )
                )
              )
            )
          )
        )
      )
    )

  private def truthJson(t: SliceReplay.Truth): Json.J =
    import Json.*
    Obj(
      Seq(
        "c0" -> Num(t.c0),
        "disturbance" -> Num(t.disturbance),
        "impacts" -> Num(t.impactTimes.size.toDouble),
        "first_impact" -> optNum(t.impactTimes.headOption),
        "period" -> optNum(t.period),
        "settled_period" -> optNum(t.settledPeriod),
        "mean_stress" -> Num(t.meanStress),
        "final_stress" -> Num(t.finalStress),
        "regime" -> Str(t.regime.toString),
        "max_abs_v" -> Num(t.maxAbsV)
      )
    )

  private def kindJson(k: KindRow): Json.J =
    import Json.*
    Obj(
      Seq(
        "pulse" -> Str(k.kind.wire),
        "role" -> Str(k.role),
        "area" -> Num(k.kind.shape.area),
        "tau" -> Num(k.kind.shape.tau),
        "taps" -> Num(k.kind.shape.taps.toDouble),
        "gap" -> Num(k.kind.shape.gap),
        "peak" -> Num(k.kind.shape.peak),
        "fire_span" -> spanJson(k.fire),
        "delay_span" -> spanJson(k.delay),
        "absorb_span" -> spanJson(k.absorb),
        "outcomes" -> Arr(k.table.map { r =>
          Obj(
            Seq(
              "window" -> Str(r.window.wire),
              "phase" -> Num(r.trial.phase),
              "outcome" -> Str(r.trial.outcome.toString),
              "delta" -> optNum(r.trial.delta),
              "v_peak" -> Num(r.trial.vPeak),
              "v_min" -> Num(r.trial.vMin)
            )
          )
        }),
        "safe_ticks" -> Arr(k.safeTicks.map { r =>
          Obj(
            Seq(
              "tick" -> Num(r.tick.toDouble),
              "phase" -> Num(r.phase),
              "outcome" -> Str(PulseOutcome.Delayed.toString),
              "delta" -> Num(r.delta),
              "window" -> Str(r.window)
            )
          )
        })
      )
    )

  private def spanJson(span: Option[(Double, Double)]): Json.J =
    import Json.*
    span match
      case Some((lo, hi)) => Arr(Seq(Num(lo), Num(hi)))
      case None => Arr(Seq())

  // ==== helpers ==== //

  private def round3(x: Double): Double = Math.round(x * 1000.0) / 1000.0

  private def relError(measured: Double, reference: Double): Double =
    if reference == 0.0 then Double.NaN else Math.abs(measured - reference) / reference

  private def optNum(value: Option[Double]): Json.J =
    value match
      case Some(x) => Json.Num(x)
      case None => Json.Null()

  /** SHA-256 over the frozen numeric sources, so the contract records *which*
    * integrator produced its numbers rather than a mutable claim about it.
    */
  def sourceDigest: String =
    val files = Seq(
      "src/main/scala/moe/chestnut/awa/voy/inner/mandate/MandateState.scala",
      "src/main/scala/moe/chestnut/awa/voy/helpers/ODESolver.scala"
    )
    val md = MessageDigest.getInstance("SHA-256")
    files.foreach { relative =>
      md.update(repoRoot.resolve(relative).toString.getBytes("UTF-8"))
      md.update(Files.readAllBytes(repoRoot.resolve(relative)))
    }
    val hex = new StringBuilder
    md.digest().foreach(b => hex.append(String.format("%02x", Integer.valueOf(b & 0xff))))
    hex.toString

  private var cachedRoot: Option[Path] = None

  /** Repository root, found by walking up from the working directory until
    * the build files are visible. Both sides of the stack resolve the same
    * artifact this way, so neither hard-codes a machine path (and the
    * contract directory itself may not exist yet on a first generation).
    */
  def repoRoot: Path =
    cachedRoot.getOrElse {
      var dir = Paths.get("").toAbsolutePath
      var found: Option[Path] = None
      while found.isEmpty && dir != null do
        if Files.isRegularFile(dir.resolve("settings.gradle")) && Files.isDirectory(dir.resolve("src"))
        then found = Some(dir)
        dir = dir.getParent
      val root = found.getOrElse(
        throw new IllegalStateException(
          "repository root not found: no ancestor of the working directory looks like the project"
        )
      )
      cachedRoot = Some(root)
      root
    }

  def contractPath: Path = repoRoot.resolve(fileName)

  /** The contract as currently checked in. */
  def checkedIn(): String =
    new String(Files.readAllBytes(contractPath), "UTF-8").replace("\r\n", "\n")

  /** Whether the test run was asked to rewrite the golden (a deliberate,
    * reviewed act — never part of an ordinary test pass).
    */
  def writeRequested: Boolean = System.getProperty("voy.contract.write", "false") == "true"

  def write(contents: String): Unit =
    Files.createDirectories(contractPath.getParent)
    Files.write(contractPath, contents.getBytes("UTF-8"))

/** Minimal JSON renderer. The contract needs stable, byte-comparable output
  * and no dependency the game runtime does not already carry, so a tiny
  * writer is cheaper than a library binding. Nothing here parses JSON; the
  * Elixir side owns the reading half.
  */
object Json:
  sealed trait J
  final case class Str(value: String) extends J
  final case class Num(value: Double) extends J
  final case class Bool(value: Boolean) extends J
  final case class Null() extends J
  final case class Arr(items: Seq[J]) extends J
  final case class Obj(fields: Seq[(String, J)]) extends J

  def render(j: J): String =
    val sb = new StringBuilder
    write(j, sb, 0)
    sb.append("\n")
    sb.toString

  private def write(j: J, sb: StringBuilder, indent: Int): Unit =
    j match
      case Str(s) => sb.append("\"").append(escape(s)).append("\"")
      case Num(d) => sb.append(number(d))
      case Bool(b) => sb.append(if b then "true" else "false")
      case Null() => sb.append("null")
      case Arr(items) =>
        if items.isEmpty then sb.append("[]")
        else
          sb.append("[\n")
          items.zipWithIndex.foreach { case (item, i) =>
            pad(sb, indent + 1)
            write(item, sb, indent + 1)
            if i < items.size - 1 then sb.append(",")
            sb.append("\n")
          }
          pad(sb, indent)
          sb.append("]")
      case Obj(fields) =>
        if fields.isEmpty then sb.append("{}")
        else
          sb.append("{\n")
          fields.zipWithIndex.foreach { case ((key, value), i) =>
            pad(sb, indent + 1)
            sb.append("\"").append(escape(key)).append("\": ")
            write(value, sb, indent + 1)
            if i < fields.size - 1 then sb.append(",")
            sb.append("\n")
          }
          pad(sb, indent)
          sb.append("}")

  private def pad(sb: StringBuilder, indent: Int): Unit =
    var i = 0
    while i < indent do
      sb.append("  ")
      i += 1

  /** Locale-independent, trimmed, fixed-precision number: the contract must
    * render byte-identically on every machine.
    */
  def number(d: Double): String =
    if d.isNaN || d.isInfinite then "null"
    else
      val rounded = java.math.BigDecimal
        .valueOf(d)
        .setScale(6, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
      val text = rounded.toPlainString
      if text == "-0" then "0" else text

  private def escape(s: String): String =
    val sb = new StringBuilder
    s.foreach {
      case '"' => sb.append("\\\"")
      case '\\' => sb.append("\\\\")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case c => sb.append(c)
    }
    sb.toString
