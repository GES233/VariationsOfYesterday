package moe.chestnut.awa.voy.inner.veins

/** Pure mathematical Poincaré section-phase tracker for one cell:
  * `fraction = (t - t_cross) / T_est (mod 1)`.
  *
  * Free of gameplay lockouts and stacked-pulse rules: observes only the
  * continuous trajectory `(v, w, s)` and sustenance `M`.
  */
class PhaseTracker(
    param: ReadoutParam = ReadoutParam(),
    fast: FastParam = MandateAgent.DefaultFastParam,
    coupling: Double = 1.0,
    depth: Double = StressParam.BareCore.sustenanceDepth
):
  private var tPrev = Double.NaN
  private var vPrev = Double.NaN
  private var lastCross = Double.NaN
  private var crossTimes: List[Double] = Nil
  private var excursionCount = 0

  // RhythmVerdict statistics (design note 11, Step 0: observed only, no
  // behavioural effect). `periodHistory` is the bounded ring of recent
  // inter-upcrossing intervals, newest first; duty accumulates over the
  // current open cycle with the same rectangle rule as the lab probes.
  private var bornAtT = Double.NaN
  private var cycleStart = Double.NaN
  private var highTime = 0.0
  private var lastDutyV = Double.NaN
  private var periodHistory: Vector[Double] = Vector.empty

  /** Interpolated upcrossing times, newest first (bounded to the ring
    * capacity plus one, enough for every consumer of [[period]]).
    */
  def crossings: List[Double] = crossTimes

  /** Last cycle period estimate, from up to two most recent intervals. */
  def period: Option[Double] =
    periodHistory match
      case v if v.size >= 2 => Some(0.5 * (v(0) + v(1)))
      case v if v.size == 1 => Some(v(0))
      case _                => None

  private def intervals: List[Double] = periodHistory.take(2).toList

  /** Number of section upcrossings seen: one per excursion. */
  def excursions: Int = excursionCount

  /** Immutable rhythm statistics snapshot for the rhythm classifier
    * ([[RhythmVerdict.classify]]); pure data, safe to hand across threads.
    */
  def rhythmEvidence: RhythmEvidence =
    RhythmEvidence(periodHistory, lastDutyV, lastCross, bornAtT)

  /** Restore rhythm statistics from a snapshot (design note 11 risk 2: a
    * reload must not reset every cell to a fresh Warming grace). The raw
    * crossing list and the excursion counter are not persisted — only the
    * period ring, duty, and the Warming anchors matter downstream.
    */
  def restoreRhythmEvidence(stats: RhythmEvidence): Unit =
    periodHistory = stats.periods.take(PhaseTracker.PeriodHistoryCap)
    lastDutyV = stats.lastDuty
    lastCross = stats.lastCross
    bornAtT = stats.bornAt
    cycleStart = stats.lastCross
    highTime = 0.0
    crossTimes = Nil

  def observe(t: Double, cur: MandateCurrent, sustenance: Double = 1.0): PhaseReading =
    val v = cur.excitation
    if bornAtT.isNaN then bornAtT = t
    if !tPrev.isNaN then
      if v >= param.spikeLevel then highTime += t - tPrev
      val l = param.sectionLevel
      if vPrev <= l && v > l then
        val frac = PhaseDetection.upcrossingFraction(vPrev, v, l)
        val tc = tPrev + frac * (t - tPrev)
        if lastCross.isNaN || tc - lastCross > param.minPeriod then
          if !cycleStart.isNaN then
            val cycleLen = tc - cycleStart
            if cycleLen > 0.0 then lastDutyV = highTime / cycleLen
            periodHistory =
              (tc - lastCross) +: periodHistory.take(PhaseTracker.PeriodHistoryCap - 1)
          lastCross = tc
          cycleStart = tc
          highTime = 0.0
          excursionCount += 1
          crossTimes = (tc :: crossTimes).take(PhaseTracker.PeriodHistoryCap + 1)
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
        Math.abs(cur.excitation - vStar) <= param.basinTol &&
          Math.abs(cur.recovery - wStar) <= param.basinTol
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

object PhaseTracker:
  /** Ring capacity for recent inter-upcrossing intervals; comfortably covers
    * `RhythmParam.minCycles` with margin for drift inspection.
    */
  val PeriodHistoryCap: Int = 8
