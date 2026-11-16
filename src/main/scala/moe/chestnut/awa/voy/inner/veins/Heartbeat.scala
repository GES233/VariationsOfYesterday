package moe.chestnut.awa.voy.inner.veins

/** Which rhythm keeps a cell beating (design note 11 §1.1).
  *
  * Story-wise an intrinsic heartbeat (the cell sits inside its own
  * oscillation band) and an externally paced one (a bell, a drive source or a
  * crystal link marching it along) are different things, so rewards and
  * wording can tell them apart; mechanically both shelter alike for now
  * (2026-11-16 ruling).
  */
enum BeatSource:
  case Intrinsic
  case Paced

/** Third readout axis: the rhythm verdict over a cell's recent
  * section-crossing statistics (design note 11 §1.1). Replaces the discrete
  * pulse-shelter window (`PulseStamp`) — the beat itself is the shelter.
  */
enum Heartbeat:
  /** Grace period before a verdict is possible (new / re-inoculated /
    * freshly loaded cell). Event-driven, no duration constant: it ends when
    * enough cycles are observed or when the cell provably has no beat.
    */
  case Warming

  /** Healthy beat: period in band, stable over `minCycles` cycles, duty
    * within bounds.
    */
  case Beating(period: Double, duty: Double, source: BeatSource)

  /** Tachycardia: period < periodMin (incl. the 8–30 s overdriven band). */
  case Fast(period: Double)

  /** Bradycardia: period > periodMax, or no crossing for too long
    * (`period` is NaN when no period was ever measured).
    */
  case Slow(period: Double)

  /** Arrhythmia: period jitter beyond tolerance, or a single cycle's duty
    * beyond bounds (burst-type).
    */
  case Arrhythmic(lastPeriod: Double, duty: Double)

  def isBeating: Boolean = this.isInstanceOf[Heartbeat.Beating]
  def isWarming: Boolean = this == Heartbeat.Warming

  /** Whether this verdict counts as acute overdrive (bodily strain / spiking
    * economy tier under the heartbeat model).
    */
  def isAcute: Boolean = this match
    case Heartbeat.Fast(_) | Heartbeat.Arrhythmic(_, _) => true
    case _                                              => false

  /** Whether this verdict is arrhythmia: the only verdict a single cycle can
    * flip (one oversized duty cycle, or one outlier in the period window),
    * and therefore the only one the exit hysteresis guards.
    */
  def isArrhythmic: Boolean = this.isInstanceOf[Heartbeat.Arrhythmic]

object Heartbeat:
  /** Stable wire ordinal for the heartbeat category (debug telemetry). */
  def wireOrdinal(h: Heartbeat): Int = h match
    case Heartbeat.Warming           => 0
    case Heartbeat.Beating(_, _, _)  => 1
    case Heartbeat.Fast(_)           => 2
    case Heartbeat.Slow(_)           => 3
    case Heartbeat.Arrhythmic(_, _)  => 4

  def wireName(ordinal: Int): String = ordinal match
    case 0 => "Warming"
    case 1 => "Beating"
    case 2 => "Fast"
    case 3 => "Slow"
    case 4 => "Arrhythmic"
    case _ => "?"

  /** The period a verdict carries, when it carries one. */
  def periodOf(h: Heartbeat): Option[Double] = h match
    case Heartbeat.Beating(p, _, _)   => Some(p)
    case Heartbeat.Fast(p)            => Some(p)
    case Heartbeat.Slow(p)            => if p.isNaN then None else Some(p)
    case Heartbeat.Arrhythmic(p, _)   => Some(p)
    case Heartbeat.Warming            => None

  /** The duty a verdict carries, when it carries one. */
  def dutyOf(h: Heartbeat): Option[Double] = h match
    case Heartbeat.Beating(_, d, _)  => if d.isNaN then None else Some(d)
    case Heartbeat.Arrhythmic(_, d)  => if d.isNaN then None else Some(d)
    case _                           => None

  /** Pure rhythm classifier over exported beat statistics (judgment table,
    * design note 11 §1.2 with the v2 anchor rule).
    *
    * @param selfSustained whether the cell's recovery offset sits inside its
    *   own oscillation band (intrinsic beat) versus being marched by an
    *   external drive (paced); records `BeatSource`, never gates the verdict
    */
  def classify(
      stats: BeatStats,
      simTime: Double,
      selfSustained: Boolean,
      p: HeartbeatParam = HeartbeatParam()
  ): Heartbeat =
    // v2 anchor: the last crossing when present, else the birth time — a
    // fresh healthy cell stays Warming until `minCycles` intervals are
    // observed, even past `staleCycles × nominalPeriod` since birth.
    val anchor = if stats.lastCross.isNaN then stats.bornAt else stats.lastCross
    val alive = !anchor.isNaN && simTime - anchor <= p.staleCycles * p.nominalPeriod
    if stats.periods.size < p.minCycles then
      if alive then Heartbeat.Warming
      else Heartbeat.Slow(stats.periods.headOption.getOrElse(Double.NaN))
    else
      val recent = stats.periods.take(p.minCycles)
      val period = recent.sum / recent.size
      if !alive then Heartbeat.Slow(period)
      else if period < p.periodMin then Heartbeat.Fast(period)
      else if period > p.periodMax then Heartbeat.Slow(period)
      else if cv(recent) > p.regularityTol then Heartbeat.Arrhythmic(period, stats.lastDuty)
      else if !stats.lastDuty.isNaN && stats.lastDuty > p.dutyMax then
        Heartbeat.Arrhythmic(period, stats.lastDuty)
      else
        Heartbeat.Beating(
          period,
          stats.lastDuty,
          if selfSustained then BeatSource.Intrinsic else BeatSource.Paced
        )

  /** Coefficient of variation (population std / mean) of recent periods. */
  private def cv(xs: Vector[Double]): Double =
    val mean = xs.sum / xs.size
    if mean <= 0.0 then Double.NaN
    else Math.sqrt(xs.map(x => (x - mean) * (x - mean)).sum / xs.size) / mean

  /** Exit rule on the published verdict (design note 11 §1.3, revised
    * 2026-11-16 evening).
    *
    * **Only `Arrhythmic` is held.** It is the one verdict a single cycle can
    * flip (one duty cycle above the bound, or one outlier in the period
    * window), so it needs a flicker guard. The hold is
    * `exitBadCycles` of the CELL'S OWN periods — `2 × periodOf(raw)`, not
    * `2 × nominalPeriod`. The nominal clock was wrong in both directions:
    * for a `Fast` cell it was four bad beats, for a `Slow` cell less than
    * one, so "two consecutive bad cycles" was never literally true.
    *
    * **`Fast` and `Slow` publish immediately.** `Fast` only exists after
    * `minCycles` short intervals, i.e. the classifier has already averaged;
    * holding it would delay the acute signal by another two beats. `Slow`
    * only exists after `staleCycles` (2.5) periods of silence or `minCycles`
    * long intervals; adding the exit hold counts the same wait twice (the
    * old rule published a silently stopped cell as `Beating` for ~178 s:
    * 99 s of staleness plus 79 s of hold).
    *
    * Entering `Beating` and every transition out of a non-`Beating` verdict
    * are immediate, as before. Time-based, not tick-based: one bad cycle
    * spans hundreds of ticks.
    *
    * @param badSince simTime when the current bad stretch started (NaN = not
    *   in a bad stretch)
    * @return the verdict to publish and the new `badSince`
    */
  def smooth(
      raw: Heartbeat,
      published: Heartbeat,
      badSince: Double,
      simTime: Double,
      p: HeartbeatParam = HeartbeatParam()
  ): (Heartbeat, Double) =
    if raw.isBeating then (raw, Double.NaN)
    else if !published.isBeating then (raw, Double.NaN)
    else if !raw.isArrhythmic then (raw, Double.NaN)
    else
      val since = if badSince.isNaN then simTime else badSince
      val period = periodOf(raw).filter(_ > 0.0).getOrElse(p.nominalPeriod)
      if simTime - since < p.exitBadCycles * period then (published, since)
      else (raw, Double.NaN)

/** Immutable per-cell rhythm statistics exported by `PhaseTracker` once per
  * tick and handed to the pure classifier ([[Heartbeat.classify]]), keeping
  * the classifier free of tracker state.
  *
  * @param periods recent inter-upcrossing intervals, newest first
  * @param lastDuty duty fraction (`v >= spikeLevel`) of the most recently
  *   completed cycle; NaN if none completed
  * @param lastCross time of the most recent section upcrossing; NaN if none
  * @param bornAt time of the tracker's first observation; NaN if never
  */
final case class BeatStats(
    periods: Vector[Double],
    lastDuty: Double,
    lastCross: Double,
    bornAt: Double
):
  // NaN-safe value equality: the "never crossed / never measured" sentinels
  // are NaN, and snapshot round-trips must compare equal. `Double.compare`
  // (boxed semantics) treats NaN == NaN; Vector elements are boxed, so
  // period equality is already NaN-safe.
  override def equals(other: Any): Boolean = other match
    case o: BeatStats =>
      periods == o.periods &&
        java.lang.Double.compare(lastDuty, o.lastDuty) == 0 &&
        java.lang.Double.compare(lastCross, o.lastCross) == 0 &&
        java.lang.Double.compare(bornAt, o.bornAt) == 0
    case _ => false

  override def hashCode: Int =
    java.util.Objects.hash(
      periods,
      Double.box(lastDuty),
      Double.box(lastCross),
      Double.box(bornAt)
    )

object BeatStats:
  val zero: BeatStats = BeatStats(Vector.empty, Double.NaN, Double.NaN, Double.NaN)

/** Tuning for the heartbeat axis. Every literal carries its provenance
  * (2026-11-16 discipline); `HeartbeatParamSpec` pins the relations so the
  * thresholds track `nominalPeriod`.
  */
final case class HeartbeatParam(
    /** measured: contract `mandate-slice-v1.json` `settled_period` at the
      * s = 1.0 saturated work point; consistent with the geometric mean of
      * the natural band ends (43.7 / 36.4 ≈ 39.9).
      */
    nominalPeriod: Double = 39.47,
    /** derived: judging regularity takes 2 intervals, plus the current beat. */
    minCycles: Int = 3,
    /** derived: same source as `ReadoutParam.regularityTol`. */
    regularityTol: Double = 0.20,
    /** derived: same source as `ReadoutParam.staleCycles`. */
    staleCycles: Double = 2.5,
    /** derived: 1.6 × τ_bell (design note 08); the measured 8 s cadence falls
      * into depolarisation blockade — two independent evidences agree.
      */
    overdriveGuard: Double = 1.6 * PulseTransmitter.Bell.tau,
    /** TODO(calibrate): pure feel constant; Step 3 calibrates in 60–200 ticks. */
    overloadPenaltyTicks: Int = 120,
    /** derived: design note 11 §1.3 (rev. 2026-11-16) — the flicker guard for
      * `Arrhythmic` only (the one verdict a single cycle can flip): leaving
      * `Beating` takes this many of the CELL'S OWN periods. `Fast` and `Slow`
      * publish immediately; their evidence is already averaged/waited, so an
      * extra hold would only delay an acute signal or count the same wait
      * twice.
      */
    exitBadCycles: Int = 2
):
  /** derived: the fastest natural period is 36.4 s (s = 0.7); 25% faster than
    * that can only be an overdriven, hard-topped rhythm, not a natural one.
    */
  val periodMin: Double = 0.75 * nominalPeriod

  /** derived: "at most one dropped beat" — one response per two natural
    * periods is an event, not a rhythm.
    */
  val periodMax: Double = 2.0 * nominalPeriod

  /** derived: half the saturated plateau fraction (measured: 0.606, draft 01
    * §1.1); healthy beats measure 6%–13% duty.
    */
  val dutyMax: Double = 0.5 * HeartbeatParam.SaturationPlateauFraction

object HeartbeatParam:
  /** measured: high-plateau fraction of the saturated work point (draft 01 §1.1). */
  val SaturationPlateauFraction: Double = 0.606
