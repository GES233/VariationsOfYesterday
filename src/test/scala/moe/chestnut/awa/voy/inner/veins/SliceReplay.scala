package moe.chestnut.awa.voy.inner.veins

import scala.collection.mutable.ArrayBuffer

import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}

/** Replay of the vertical slice's raw event stream, and the two facts the
  * symbolic layer needs from it.
  *
  * The slice is one gully, three sites, one directed advection chain
  * (`LatticeEdge(from -> to, advection = ...)`), driven exactly like the
  * game drives it: channel events + a fixed-step tick (§1.4). Nothing here
  * reads a clock, so the same event stream replays to the same trajectory.
  */
object SliceReplay:
  /** The fixed simulation step of a game tick (§1.4: 20 tps × 0.05 = 1
    * simulation time unit per game second). */
  val tickDt: Double = 0.05

  /** One event stamped with the tick it is delivered before. */
  final case class Scripted(tick: Int, event: EventDTO)

  /** Ground truth of a single node, as the numeric layer settles it.
    *
    * `impactTimes` are the moments the fast variable crossed the instrument
    * section: what a frame-saturating pendulum rig would have logged. The
    * observers downstream of this file only ever see records derived from
    * these numbers.
    */
  final case class Truth(
      site: String,
      c0: Double,
      disturbance: Double,
      impactTimes: Seq[Double],
      period: Option[Double],
      settledPeriod: Option[Double],
      finalStress: Double,
      meanStress: Double,
      regime: Regime,
      maxAbsV: Double
  ):
    def spikes: Int = impactTimes.size

  def nodeState(c0: Double): MandateState =
    val st = MandateState(
      state = MandateCurrent(dissonance = -1.2, entrench = -0.6),
      param = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
    )
    st.baselineCalming = c0
    st

  /** Run one node under a sustained load `u` (and optionally one pulse) and
    * report what the world did.
    */
  def truth(
      site: String,
      c0: Double,
      disturbance: Double = 0.05,
      dt: Double = tickDt,
      tEnd: Double = 2400.0,
      pulse: Option[(Double, PulseKind)] = None,
      measureFrom: Double = 600.0
  ): Truth =
    val st = nodeState(c0)
    st.disturbance = disturbance
    st.calming = 0.0
    val tracker = new PhaseTracker()
    var t = 0.0
    var firedPulse = false
    var stressSum = 0.0
    var stressCount = 0
    var maxAbsV = 0.0
    val impacts = ArrayBuffer.empty[Double]
    var lastImpact = Double.NegativeInfinity
    while t < tEnd do
      pulse.foreach { case (at, kind) =>
        if !firedPulse && t >= at then
          st.synapseTau = kind.shape.tau
          for _ <- 0 until kind.shape.taps do st.firePulse(kind.shape.area)
          firedPulse = true
      }
      st.step(dt)
      t += dt
      val cur = st.getState
      val before = tracker.excursions
      tracker.observe(t, cur)
      val v = cur.dissonance
      maxAbsV = Math.max(maxAbsV, Math.abs(v))
      if tracker.excursions > before && t - lastImpact > 5.0 then
        impacts += t
        lastImpact = t
      if t >= measureFrom then
        stressSum += cur.stress
        stressCount += 1
    val meanStress = if stressCount == 0 then 0.0 else stressSum / stressCount
    val intervals = impacts.sliding(2).filter(_.size == 2).map(p => p(1) - p(0)).toList
    val period = if intervals.isEmpty then None else Some(intervals.sum / intervals.size)
    val settledImpacts = impacts.filter(_ >= measureFrom)
    val settledIntervals =
      settledImpacts.sliding(2).filter(_.size == 2).map(p => p(1) - p(0)).toList
    val settledPeriod =
      if settledIntervals.isEmpty then None
      else Some(settledIntervals.sum / settledIntervals.size)
    Truth(
      site = site,
      c0 = c0,
      disturbance = disturbance,
      impactTimes = impacts.toSeq,
      period = period,
      settledPeriod = settledPeriod,
      finalStress = st.getState.stress,
      meanStress = meanStress,
      regime = Readout.regime(st.getState),
      maxAbsV = maxAbsV
    )

  /** The slice's scripted channel event stream: the sustained load, the
    * baseline calming of the settlement, and the one pulse event.
    */
  def script(c0: Double, pulseTick: Int, kind: PulseKind): Seq[Scripted] =
    Seq(
      Scripted(0, EventMsg.SetDisturbance(CellCoord(0, 0), 0.05)),
      Scripted(0, EventMsg.SetBaselineCalming(CellCoord(0, 0), c0)),
      Scripted(pulseTick, EventMsg.FirePulse(CellCoord(0, 0), kind.shape.area))
    )

  /** Replay a script against a lattice: events apply before the tick they
    * are stamped with, then the tick integrates. Returns one snapshot per
    * tick.
    */
  def replay(
      grid: MandateGrid,
      events: Seq[Scripted],
      ticks: Int,
      dt: Double = tickDt
  ): Vector[Map[CellCoord, MandateCurrent]] =
    val byTick = events.groupBy(_.tick)
    val out = Vector.newBuilder[Map[CellCoord, MandateCurrent]]
    var k = 0
    while k < ticks do
      byTick.get(k).foreach(_.foreach(s => MandateAgent.applyEvent(grid, s.event)))
      grid.step(dt)
      out += grid.cells.iterator.map { case (c, cell) => c -> cell.getState }.toMap
      k += 1
    out.result()

  // ==== the directed advection chain ==== //

  final case class ChainObservation(
      advection: Double,
      upstreamPeriod: Option[Double],
      downstreamPeriod: Option[Double],
      lagEarly: Option[Double],
      lagLate: Option[Double],
      driftPerCycle: Option[Double],
      relativePeriodGap: Option[Double]
  ):
    /** Phase composition (`downstream phase = upstream phase + constant lag`)
      * is only a legitimate symbolic rule while the measured lag actually
      * stays put. The criterion is direct: the lag may move at most a tenth
      * of a cycle across the whole observation. Anything looser would let a
      * drift of seconds-per-cycle pass as "constant", which is exactly the
      * claim under test.
      */
    def composable: Boolean =
      (for
        early <- lagEarly
        late <- lagLate
        period <- upstreamPeriod
      yield Math.abs(late - early) <= 0.10 * period).getOrElse(false)

    /** How far the lag moved, in cycles. */
    def lagDriftCycles: Option[Double] =
      for
        early <- lagEarly
        late <- lagLate
        period <- upstreamPeriod
      yield Math.abs(late - early) / period

  def chain(advection: Double, cells: Int = 3): MandateGrid =
    val coords = (0 until cells).map(i => CellCoord(i, 0)).toSeq
    val map = coords.map(c => c -> nodeState(0.0)).toMap
    val edges = coords.sliding(2).map { case Seq(a, b) => LatticeEdge(a, b, advection = advection) }.toSeq
    MandateGrid(map, edges)

  /** Bidirectional amplitude probe used ONLY by this offline chain experiment:
    * it fires on either branch excursion to follow the period/lag of the
    * coupled pair. It is deliberately not the runtime spike criterion (which is
    * positive `v >= 1.8` on a section upcrossing, design note 08).
    */
  private val ChainProbeAmplitude = 1.9

  /** Ignite the upstream cell only, and watch what the chain does. */
  def chainObservation(
      advection: Double,
      dt: Double = tickDt,
      tEnd: Double = 12000.0
  ): ChainObservation =
    val grid = chain(advection)
    grid.cells(CellCoord(0, 0)).disturbance = 0.08
    val spikes = grid.cells.keys.map(_ -> ArrayBuffer.empty[Double]).toMap
    val wasAbove = scala.collection.mutable.Map.empty[CellCoord, Boolean]
    grid.cells.keys.foreach(c => wasAbove(c) = false)
    var t = 0.0
    while t < tEnd do
      grid.step(dt)
      t += dt
      for (coord, cell) <- grid.cells do
        val above = Math.abs(cell.getState.dissonance) > ChainProbeAmplitude
        if above && !wasAbove(coord) then spikes(coord) += t
        wasAbove(coord) = above
    val up = spikes(CellCoord(0, 0)).toSeq
    val down = spikes(CellCoord(1, 0)).toSeq
    val n = Math.min(up.size, down.size)
    val lags = (0 until n).map(i => down(i) - up(i))
    val early = if lags.size >= 3 then Some(lags(2)) else None
    val late = if lags.size >= 3 then Some(lags.last) else None
    val drift =
      if lags.size >= 4 then Some((lags.last - lags(1)) / (lags.size - 2).toDouble) else None
    ChainObservation(
      advection = advection,
      upstreamPeriod = meanInterval(up),
      downstreamPeriod = meanInterval(down),
      lagEarly = early,
      lagLate = late,
      driftPerCycle = drift,
      relativePeriodGap = for
        pu <- meanInterval(up)
        pd <- meanInterval(down)
      yield Math.abs(pu - pd) / pu
    )

  private def meanInterval(times: Seq[Double]): Option[Double] =
    val iv = times.sliding(2).filter(_.size == 2).map(p => p(1) - p(0)).toList
    if iv.isEmpty then None else Some(iv.sum / iv.size)
