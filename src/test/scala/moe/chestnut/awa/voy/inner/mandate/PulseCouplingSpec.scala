package moe.chestnut.awa.voy.inner.mandate

import scala.collection.mutable.ArrayBuffer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.{Test, TestInstance}

import moe.chestnut.awa.voy.inner.event.EventMsg

/** Migration of the cloud agent's pulse-coupling prototype
  * (`mandate/coupling.py`, design note 07).
  *
  * The numbers asserted here were measured with that prototype and are
  * reproduced by this port at the same step: a bidirectional link collapses
  * an initial phase offset onto a **stable non-zero** lag; there is a
  * coupling-area threshold around 0.5; and a link that has locked is torn
  * apart once the downstream stress starts moving (cycle slip), which is the
  * design's whole argument for needing a programmable circuit rather than a
  * fixed repeater.
  */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PulseCouplingSpec:
  private val fast = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
  private val dt = 0.02

  /** Saturation operating point of the slice (`a = -0.30`, `s = 1.0`). */
  private val satA = -0.30

  /** The oscillator period measured in PhaseResponseSpec / the contract. */
  private val period = 39.475

  private val a = CellCoord(0, 0)
  private val b = CellCoord(1, 0)

  private final case class LagTail(mean: Double, std: Double, n: Int)

  // ==== helpers: the prototype's frozen-cell scaffolding ==== //

  private def frozen(recovery: Double): MandateState =
    MandateState(
      MandateCurrent(-1.2, -0.6, (fast.alpha - recovery) / 1.0, 0.0),
      fast,
      StressParam(gamma = 0.0)
    )

  private def advance(st: MandateState, tEnd: Double, step: Double = dt): Unit =
    var t = 0.0
    while t < tEnd do
      st.step(step)
      t += step

  /** Settle, then stop on the next section upcrossing: the prototype's
    * phase-0 reference. */
  private def settleToSection(st: MandateState, warmup: Double, step: Double = dt): Unit =
    var t = 0.0
    while t < warmup do
      st.step(step)
      t += step
    var prev = st.getState.dissonance
    var found = false
    while !found do
      st.step(step)
      t += step
      val v = st.getState.dissonance
      if prev <= PhaseResponse.section && v > PhaseResponse.section then found = true
      prev = v

  private def offsetClone(base: MandateState, fraction: Double, step: Double = dt): MandateState =
    val clone = frozen(0.0)
    clone.state = base.getState
    advance(clone, fraction * period, step)
    clone

  private def tailStats(lags: Seq[(Double, Double)], from: Double, to: Double): LagTail =
    val tail = lags.collect { case (t, lag) if t >= from && t <= to => lag }
    if tail.isEmpty then LagTail(Double.NaN, Double.NaN, 0)
    else
      val mean = tail.sum / tail.size
      val std = Math.sqrt(tail.map(x => (x - mean) * (x - mean)).sum / tail.size)
      LagTail(mean, std, tail.size)

  // ==== lag folding ==== //

  @Test def `lagSeries folds a lag into the nearest half period`(): Unit =
    val lags = PulseCoupling.lagSeries(
      ref = Seq(10.0, 50.0),
      other = Seq(49.0, 90.0),
      period = 40.0
    )
    assertEquals(Seq(10.0 -> -1.0, 50.0 -> 0.0), lags)

  // ==== E1: bidirectional coupling locks onto a non-zero offset ==== //

  private def e1Tail(area: Double, step: Double = dt): LagTail =
    val left = frozen(satA)
    settleToSection(left, 3000.0, step)
    val right = offsetClone(left, 0.33, step)
    val links =
      if area <= 0.0 then Nil else Seq(PulseLink(a, b, area), PulseLink(b, a, area))
    val trace = PulseCoupling.runCoupled(Map(a -> left, b -> right), links, step, 1500.0)
    tailStats(PulseCoupling.lagSeries(trace.crossings(a), trace.crossings(b), period), 700.0, 1500.0)

  @Test def `bidirectional coupling locks onto a stable non-zero lag`(): Unit =
    val free = e1Tail(0.0)
    val coupled = e1Tail(1.0)
    // Un-coupled: the offset never moves (identical periods, no exchange).
    assertTrue(free.n > 10, s"the free run must produce a lag series, got $free")
    assertEquals(-13.033, free.mean, 0.10, "the initial 0.33 T offset must survive")
    assertTrue(free.std < 0.05, s"free lag must be constant, std=${free.std}")
    // Coupled: locked, but NOT at zero — the balance point encodes the link
    // latency and the delayed PRC of the operating point (E1).
    assertTrue(coupled.n > 10, s"the coupled run must produce a lag series, got $coupled")
    assertEquals(-2.748, coupled.mean, 0.20, "the prototype's locked offset")
    assertTrue(coupled.std < 0.05, s"locking must be tight, std=${coupled.std}")
    assertTrue(coupled.mean < -0.5, "a physical link cannot lock at zero lag: it has latency")

  // ==== E2: the coupling threshold is an area of about 0.5 ==== //

  private def lockingRun(area: Double, tEnd: Double = 2500.0, step: Double = dt): LagTail =
    val left = frozen(satA)
    settleToSection(left, 3000.0, step)
    val right = frozen(satA)
    settleToSection(right, 3000.0, step)
    val links = if area <= 0.0 then Nil else Seq(PulseLink(a, b, area))
    val trace = PulseCoupling.runCoupled(Map(a -> left, b -> right), links, step, tEnd)
    tailStats(
      PulseCoupling.lagSeries(trace.crossings(a), trace.crossings(b), period),
      tEnd - 800.0,
      tEnd
    )

  @Test def `the coupling threshold is an area of about 0.5`(): Unit =
    val weak = lockingRun(0.25)
    val lock = lockingRun(0.5)
    // 0.25 is still converging inside the observation window; 0.5 has locked.
    assertTrue(weak.std > 0.2, s"below threshold the lag must still wander, std=${weak.std}")
    assertTrue(lock.std < 0.05, s"at threshold the lag must be tight, std=${lock.std}")
    assertEquals(-10.302, lock.mean, 0.20, "the prototype's locked mean lag at s = 1.0")
    assertTrue(
      Math.abs(weak.mean - lock.mean) > 0.5,
      s"the weak link must not have reached the locked offset: weak=${weak.mean} lock=${lock.mean}"
    )

  // ==== E4b: a locked link is torn apart by stress drift ==== //

  /** The E4b protocol at an arbitrary step: lock the downstream cell, then
    * calm it from `t = 1500` on. Returns (locked tail, post-slip tail,
    * final downstream stress). */
  private def e4bTails(step: Double): (LagTail, LagTail, Double) =
    val left = frozen(satA)
    settleToSection(left, 3000.0, step)
    val right = MandateState(MandateCurrent(-1.2, -0.6, 1.0, 0.0), fast) // live slow variable
    right.disturbance = 0.05
    settleToSection(right, 200.0, step)

    val runner = new PulseLinkRunner(
      Seq(PulseLink(a, b, area = 2.0, delay = 1.0)),
      criterion = SpikeCriterion.SectionCrossing
    )
    val grid = MandateGrid(Map(a -> left, b -> right), Nil)
    val leftCrossings = ArrayBuffer.empty[Double]
    val rightCrossings = ArrayBuffer.empty[Double]
    var t = 0.0
    while t < 9000.0 do
      // Lock first, then calm: the same two-phase protocol as E4b.
      if t >= 1500.0 && right.calming == 0.0 then right.calming = 0.10
      runner.duePulses(t).foreach(event => MandateAgent.applyEvent(grid, event))
      grid.step(step)
      t += step
      runner.observe(t, grid.cells).foreach { case (cell, at) =>
        if cell == a then leftCrossings += at else rightCrossings += at
      }

    val lags = PulseCoupling.lagSeries(leftCrossings.toSeq, rightCrossings.toSeq, period)
    (tailStats(lags, 1400.0, 1500.0), tailStats(lags, 7000.0, 9000.0), right.getState.stress)

  @Test def `a locked link slips apart when the downstream stress falls`(): Unit =
    val (locked, slipped, finalStress) = e4bTails(dt)

    // The calming is strong enough to drain the stress (c = 0.10 = 2u).
    assertTrue(finalStress < 1e-6, s"stress must reach zero, got $finalStress")
    assertEquals(-10.198, locked.mean, 0.30, "the link must be locked before the drift starts")
    assertEquals(5.959, slipped.mean, 0.30, "the prototype's post-slip offset")
    assertTrue(
      Math.abs(slipped.mean - locked.mean) > 10.0,
      s"the lag must slide by more than a quarter period: ${locked.mean} -> ${slipped.mean}"
    )

  // ==== the detector discipline ==== //

  /** Feed a hand-built dissonance trace to the detector; no ODE involved. */
  private def feed(trace: Seq[Double], criterion: SpikeCriterion): Seq[(CellCoord, Double)] =
    val cell = MandateState(MandateCurrent(0.0, -0.6, 1.0, 0.0), fast, StressParam(gamma = 0.0))
    val runner = new PulseLinkRunner(
      Seq(PulseLink(a, b, area = 1.0)),
      criterion = criterion
    )
    val cells = Map(a -> cell)
    var t = 0.0
    val out = ArrayBuffer.empty[(CellCoord, Double)]
    trace.foreach { v =>
      cell.state = MandateCurrent(v, -0.6, 1.0, 0.0)
      t += 0.05
      out ++= runner.observe(t, cells)
    }
    out.toSeq

  @Test def `a driven crossing that stalls below the spike level is not retransmitted`(): Unit =
    // Crosses 1.2 upwards and holds around 1.4: an artefact, not an action
    // potential (the counterexample PhaseResponseSpec already measures).
    val stalled = Seq(0.0, 0.5, 1.25, 1.4, 1.3, 1.0, 0.4, -0.2, -0.5)
    assertEquals(1, feed(stalled, SpikeCriterion.SectionCrossing).size, "a bare phase sensor relays it")
    assertTrue(feed(stalled, SpikeCriterion.PeakConfirmed).isEmpty, "the spike sensor must not")

    // A genuine excursion (peak > 1.8) is transmitted exactly once.
    val genuine = Seq(0.0, 0.5, 1.25, 1.5, 1.9, 1.6, 1.0, -0.2, -0.6)
    assertEquals(1, feed(genuine, SpikeCriterion.PeakConfirmed).size)
    assertEquals(1, feed(genuine, SpikeCriterion.SectionCrossing).size)

  @Test def `the reported spike time is interpolated between probes`(): Unit =
    // Cross between t = 0.10 (v = 1.0) and t = 0.15 (v = 1.4): the crossing
    // lands at the linear fraction (1.2-1.0)/(1.4-1.0) = 0.5 of the step.
    val spikes = feed(Seq(0.0, 1.0, 1.4, -0.5), SpikeCriterion.SectionCrossing)
    assertEquals(1, spikes.size)
    assertEquals(0.125, spikes.head._2, 1e-12, s"crossing time must be interpolated: ${spikes}")

  @Test def `a slow creep past the spike level is welded shut`(): Unit =
    // Crosses 1.2, then creeps 0.005 per probe: the spike level is reached
    // only after ~5.9 time units — far outside the ~1.2 units a genuine
    // upstroke needs (measured 0.6 saturated / 1.2 near-critical).
    val creep = Seq(0.0, 0.5) ++ (0 to 120).map(i => 1.21 + i * 0.005)
    assertTrue(
      feed(creep, SpikeCriterion.PeakConfirmed).isEmpty,
      "a driven creep must never be relayed, no matter how high it climbs"
    )
    // The phase sensor still relays it (documented prototype parity).
    assertEquals(1, feed(creep, SpikeCriterion.SectionCrossing).size)

  // ==== scheduling and event plumbing ==== //

  @Test def `links can be rewired at runtime`(): Unit =
    val runner = new PulseLinkRunner(Nil, criterion = SpikeCriterion.SectionCrossing)
    assertTrue(runner.links.isEmpty)
    runner.addLink(PulseLink(a, b, area = 1.0))
    assertEquals(1, runner.links.size)
    // Re-registering the same (src, dst) pair replaces its parameters.
    runner.addLink(PulseLink(a, b, area = 2.0))
    assertEquals(1, runner.links.size, "re-registering a (src, dst) pair must replace it")
    assertEquals(2.0, runner.links.head.area, 1e-12)
    runner.removeLink(a, b)
    assertTrue(runner.links.isEmpty)

  @Test def `a delivered pulse waits exactly the link latency`(): Unit =
    val cell = MandateState(MandateCurrent(0.0, -0.6, 1.0, 0.0), fast, StressParam(gamma = 0.0))
    val runner = new PulseLinkRunner(
      Seq(PulseLink(a, b, area = 0.5, delay = 2.0)),
      criterion = SpikeCriterion.SectionCrossing
    )
    val cells = Map(a -> cell)
    // Cross at t = 0.1 (second probe).
    Seq(0.0, 1.3).zipWithIndex.foreach { case (v, i) =>
      cell.state = MandateCurrent(v, -0.6, 1.0, 0.0)
      val t = 0.05 * (i + 1)
      runner.observe(t, cells)
      // The pulse enqueued at the crossing is not due before crossing + delay.
      assertTrue(runner.duePulses(t).isEmpty, s"no pulse may be due at t=$t")
    }
    val expected = EventMsg.FirePulse(b, 0.5, PulseTransmitter.CrystalLink)
    assertEquals(Seq(expected), runner.duePulses(2.1))
    assertTrue(runner.duePulses(2.1).isEmpty, "a pulse is delivered once")

  @Test def `FirePulse names a transmitter kind, not a number`(): Unit =
    val cell = MandateState(MandateCurrent(-1.2, -0.6, 1.0, 0.0), fast, StressParam(gamma = 0.0))
    val grid = MandateGrid(Map(a -> cell), Nil)

    // An unnamed transmitter is the bell's alpha-shock kernel.
    MandateAgent.applyEvent(grid, EventMsg.FirePulse(a, area = 1.0))
    assertEquals(PulseTransmitter.Bell.tau, cell.synapseTau, 1e-12)

    // A crystal link names its narrow kernel.
    MandateAgent.applyEvent(grid, EventMsg.FirePulse(a, area = 1.0, PulseTransmitter.CrystalLink))
    assertEquals(PulseTransmitter.CrystalLink.tau, cell.synapseTau, 1e-12)

    // And the narrow kernel lives earlier: at t = tau_crystal it is near its
    // own peak while the wide kernel is still climbing, and it has decayed
    // away by the time the wide kernel peaks.
    val narrow = MandateState(MandateCurrent(-1.2, -0.6, 1.0, 0.0), fast)
    narrow.synapseTau = 2.0
    narrow.firePulse(1.0)
    val wide = MandateState(MandateCurrent(-1.2, -0.6, 1.0, 0.0), fast)
    wide.synapseTau = 5.0
    wide.firePulse(1.0)
    narrow.step(2.0)
    wide.step(2.0)
    assertTrue(
      narrow.synapseOutput > wide.synapseOutput,
      s"tau=2 kernel must peak earlier and higher: ${narrow.synapseOutput} vs ${wide.synapseOutput}"
    )
    narrow.step(10.0)
    wide.step(10.0)
    assertTrue(
      narrow.synapseOutput < wide.synapseOutput,
      s"the narrow kernel must die away first: ${narrow.synapseOutput} vs ${wide.synapseOutput}"
    )


  // ==== game step (dt = 0.05) goldens ==== //
  // The fingerprints above were measured at the prototype step dt = 0.02;
  // the game integrates at dt = 0.05. These goldens pin what the same rigs
  // produce at the game step, so drift in either direction is caught.

  private val gameDt = 0.05

  @Test def `game-dt E1 still locks onto a non-zero offset`(): Unit =
    val coupled = e1Tail(1.0, step = gameDt)
    assertEquals(-2.726, coupled.mean, 0.10, "locked offset at the game step")
    assertTrue(coupled.std < 0.05, s"locked lag must stay tight at the game step, std=${coupled.std}")
    assertTrue(coupled.mean < -0.5, "a physical link cannot lock at zero lag")

  @Test def `game-dt E2 keeps the area threshold near 0.5`(): Unit =
    val weak = lockingRun(0.25, step = gameDt)
    val lock = lockingRun(0.5, step = gameDt)
    assertTrue(weak.std > 0.2, s"below threshold the lag must still wander, std=${weak.std}")
    assertTrue(lock.std < 0.05, s"at threshold the lag must be tight, std=${lock.std}")
    assertEquals(-10.329, lock.mean, 0.10, "locked mean at the game step")
    assertTrue(
      Math.abs(weak.mean - lock.mean) > 0.5,
      s"the weak link must not have reached the locked offset: weak=${weak.mean} lock=${lock.mean}"
    )

  @Test def `game-dt E4b still slips when the downstream stress falls`(): Unit =
    val (locked, slipped, finalStress) = e4bTails(gameDt)
    assertTrue(finalStress < 1e-6, s"stress must reach zero, got $finalStress")
    assertEquals(-10.216, locked.mean, 0.30, "locked before the drift, game step")
    assertEquals(5.960, slipped.mean, 0.30, "post-slip offset, game step")
    assertTrue(
      Math.abs(slipped.mean - locked.mean) > 10.0,
      s"the lag must slide by more than a quarter period: ${locked.mean} -> ${slipped.mean}"
    )
