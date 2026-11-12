package moe.chestnut.awa.voy.inner.veins

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, TimeUnit}

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

import org.junit.jupiter.api.{AfterEach, Test}
import org.junit.jupiter.api.Assertions.*

import moe.chestnut.awa.voy.inner.action.{ActionMsgPool, MandateActionProtocol}
import moe.chestnut.awa.voy.inner.actor.ActorRef
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg, PulseBookkeeping}

/** Wiring contract of [[MandateAgent]] (design draft §1.4, 2026-09-30):
  * tick events drive integration deterministically, one published snapshot
  * per tick, no tick no integration; channel events apply in mailbox order
  * and are dropped deterministically when addressed outside the lattice.
  */
class MandateAgentSpec:
  /** Actors started by one test; stopped afterwards so worker threads never
    * leak between cases. */
  private val spawned = ArrayBuffer.empty[() => Unit]

  @AfterEach def shutdown(): Unit =
    spawned.foreach(_())
    spawned.clear()

  private def track[E](ref: ActorRef[E]): ActorRef[E] =
    spawned += (() => ref.stop())
    ref

  private val origin = CellCoord(0, 0)

  private def mkCell(disturbance: Double): MandateState =
    val cell = MandateState(
      MandateCurrent(0.0, 0.0, 0.0, 0.0),
      MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
    )
    cell.disturbance = disturbance
    cell

  private def singleCellGrid(cell: MandateState): MandateGrid =
    MandateGrid(Map(origin -> cell), Seq.empty)

  /** Spawn an agent whose snapshots are captured into `published`, with one
    * latch countdown per publication. */
  private def spawnCapturing(
      grid: MandateGrid,
      published: AtomicReference[Map[CellCoord, MandateCurrent]],
      latch: CountDownLatch
  ): ActorRef[EventDTO] =
    track(
      MandateAgent(
        grid,
        snapshot => { published.set(snapshot); latch.countDown() }
      )
    )

  @Test def tickArrivedDrivesDeterministicIntegration(): Unit =
    val ticks = 20
    val published =
      new AtomicReference[Map[CellCoord, MandateCurrent]](Map.empty)
    val latch = new CountDownLatch(ticks)
    val agent = spawnCapturing(singleCellGrid(mkCell(0.08)), published, latch)
    for _ <- 1 to ticks do agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(
      latch.await(5, TimeUnit.SECONDS),
      "actor should publish one snapshot per tick event"
    )

    // Same fixed dt on both sides: actor-driven integration must match a
    // directly stepped reference exactly.
    val reference = mkCell(0.08)
    for _ <- 1 to ticks do reference.step(0.05)
    val expected = reference.getState
    val actual = published.get()(origin)
    assertEquals(expected.stress, actual.stress, 1e-12)
    assertEquals(expected.dissonance, actual.dissonance, 1e-12)
    assertTrue(actual.stress > 0.0, "disturbance should accumulate stress")

  @Test def noTickNoIntegration(): Unit =
    val published =
      new AtomicReference[Map[CellCoord, MandateCurrent]](Map.empty)
    val agent = track(
      MandateAgent(singleCellGrid(mkCell(0.08)), published.set(_))
    )
    agent ! EventMsg.FirePulse(origin, 10.0)
    // Negative assertion: give the actor a moment to (not) react.
    Thread.sleep(300)
    assertTrue(
      published.get().isEmpty,
      "channel events must not trigger integration or publication"
    )

  @Test def channelEventsApplyInMailboxOrder(): Unit =
    val ticks = 5
    val published =
      new AtomicReference[Map[CellCoord, MandateCurrent]](Map.empty)
    val latch = new CountDownLatch(ticks)
    val agent = spawnCapturing(singleCellGrid(mkCell(0.0)), published, latch)
    // Injection lands BETWEEN ticks; the following tick must see it.
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.StressInject(origin, 0.5)
    for _ <- 2 to ticks do agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val reference = mkCell(0.0)
    reference.step(0.05)
    reference.injectStress(0.5)
    for _ <- 2 to ticks do reference.step(0.05)
    assertEquals(
      reference.getState.stress,
      published.get()(origin).stress,
      1e-12,
      "event applied between ticks must match the same order applied directly"
    )

  @Test def eventsOutsideLatticeAreDropped(): Unit =
    val ticks = 10
    val published =
      new AtomicReference[Map[CellCoord, MandateCurrent]](Map.empty)
    val latch = new CountDownLatch(ticks)
    val agent = spawnCapturing(singleCellGrid(mkCell(0.0)), published, latch)
    val outside = CellCoord(9, 9)
    agent ! EventMsg.StressInject(outside, 0.9)
    agent ! EventMsg.SetDisturbance(outside, 1.0)
    agent ! EventMsg.FirePulse(outside, 10.0)
    for _ <- 1 to ticks do agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))
    assertEquals(
      0.0,
      published.get()(origin).stress,
      1e-12,
      "events addressed outside the lattice must not leak into live cells"
    )

  @Test def publishesCellReadoutAndEmitsOutboundActions(): Unit =
    val readouts =
      new AtomicReference[Map[CellCoord, CellReadout]](Map.empty)
    val actions = new java.util.concurrent.ConcurrentLinkedQueue[
      MandateActionProtocol
    ]()
    val latch = new CountDownLatch(2)
    val agent = track(
      MandateAgent(
        singleCellGrid(mkCell(0.0)),
        _ => (),
        actions.add(_),
        r => { readouts.set(r); latch.countDown() },
        // This case asserts the immediate-announce semantics; the dwell
        // debounce has its own cases below.
        regimeDwell = 0.0
      )
    )

    agent ! EventMsg.SetBaselineCalming(origin, 0.03)
    agent ! EventMsg.StressInject(origin, 0.50)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.FirePulse(origin, 25.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)

    assertTrue(latch.await(5, TimeUnit.SECONDS))
    val r = readouts.get()(origin)
    assertEquals(0.03, r.baselineCalming, 1e-12)
    assertEquals(Regime.Strained, r.regime)
    assertFalse(actions.isEmpty, "regime transition from Calm to Strained should emit an outbound action")

  /** Stress 0.5 classifies Strained (threshold 0.45) and decays negligibly
    * over the test horizon, so the raw classification stays Strained
    * throughout. */
  @Test def regimeTransitionsAreDebouncedByDwell(): Unit =
    val dwell = 0.5 // candidate recorded at simTime 0.05 announces at 0.55
    val readouts =
      new AtomicReference[Map[CellCoord, CellReadout]](Map.empty)
    val actions = new java.util.concurrent.ConcurrentLinkedQueue[
      MandateActionProtocol
    ]()
    // Swap in a fresh latch per phase so each phase's publication count is
    // awaited exactly.
    val latchRef = new AtomicReference[CountDownLatch](new CountDownLatch(9))
    val agent = track(
      MandateAgent(
        singleCellGrid(mkCell(0.0)),
        _ => (),
        actions.add(_),
        r => { readouts.set(r); latchRef.get().countDown() },
        regimeDwell = dwell
      )
    )

    agent ! EventMsg.StressInject(origin, 0.50)
    // simTime 0.45: 0.40 into the dwell — the crossing must not announce yet,
    // and the published readout must still show the previous regime.
    for _ <- 1 to 9 do agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(awaitPhase(latchRef))
    assertTrue(
      actions.asScala.forall(!_.isInstanceOf[ActionMsgPool.RegimeTransitioned]),
      "a reclassification younger than the dwell must not emit a transition"
    )
    assertEquals(
      Regime.Calm,
      readouts.get()(origin).regime,
      "the published regime lags the raw classification until the dwell elapses"
    )

    // simTime 0.70: the candidate has held for 0.65 >= dwell; it announces
    // exactly once and the readout follows.
    latchRef.set(new CountDownLatch(5))
    for _ <- 1 to 5 do agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(awaitPhase(latchRef))

    val transitions = actions.asScala.collect {
      case t: ActionMsgPool.RegimeTransitioned => t
    }.toList
    assertEquals(1, transitions.size, "the crossing must announce exactly once")
    assertEquals(Regime.Calm, transitions.head.from)
    assertEquals(Regime.Strained, transitions.head.to)
    assertEquals(Regime.Strained, readouts.get()(origin).regime)

  @Test def relapsedRegimeCandidateNeverAnnounces(): Unit =
    val readouts =
      new AtomicReference[Map[CellCoord, CellReadout]](Map.empty)
    val actions = new java.util.concurrent.ConcurrentLinkedQueue[
      MandateActionProtocol
    ]()
    val latch = new CountDownLatch(44)
    val agent = track(
      MandateAgent(
        singleCellGrid(mkCell(0.0)),
        _ => (),
        actions.add(_),
        r => { readouts.set(r); latch.countDown() },
        regimeDwell = 1.0
      )
    )

    agent ! EventMsg.StressInject(origin, 0.50)
    // The candidate is recorded but the dwell (1.0) is not met within 4 ticks.
    for _ <- 1 to 4 do agent ! EventMsg.TickArrived(0.05, 20.0f)
    // Strong calming drains stress back below the threshold (~0.25s at
    // ds/dt ≈ -0.2) well before the dwell elapses: the candidate must be
    // dropped, not announced late.
    agent ! EventMsg.SetCalming(origin, 20.0)
    for _ <- 1 to 40 do agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    assertTrue(
      actions.asScala.forall(!_.isInstanceOf[ActionMsgPool.RegimeTransitioned]),
      "a reclassification that relapses inside the dwell must never announce"
    )
    assertEquals(Regime.Calm, readouts.get()(origin).regime)

  @Test def reRegisteringCellClearsPulseBookkeeping(): Unit =
    val readouts =
      new AtomicReference[Map[CellCoord, CellReadout]](Map.empty)
    val latchRef = new AtomicReference[CountDownLatch](new CountDownLatch(1))
    val agent = track(
      MandateAgent(
        singleCellGrid(mkCell(0.0)),
        _ => (),
        _ => (),
        r => { readouts.set(r); latchRef.get().countDown() }
      )
    )

    agent ! EventMsg.FirePulse(origin, 25.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(awaitPhase(latchRef))
    assertTrue(
      readouts.get()(origin).pulseTolerance > 0.05,
      "a counted pulse must open the tolerance ledger"
    )

    latchRef.set(new CountDownLatch(1))
    agent ! EventMsg.RegisterCell(origin)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(awaitPhase(latchRef))
    assertEquals(
      PulseLedger.zero,
      readouts.get()(origin).pulseLedger,
      "re-registration resets the dynamics, and the pulse ledger goes with it"
    )

  private def awaitPhase(latchRef: AtomicReference[CountDownLatch]): Boolean =
    latchRef.get().await(5, TimeUnit.SECONDS)

  private def mkOscillatingCell(): MandateState =
    // The saturated working point of the design-note-07 rigs (a = -0.30,
    // s = 1.0): the q feedback pins the stress and the cell keeps spiking.
    val cell = MandateState(
      MandateCurrent(-1.2, -0.6, 1.0, 0.0),
      MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
    )
    cell.disturbance = 0.05
    cell

  @Test def topologyEventsRebuildTheLatticeInMailboxOrder(): Unit =
    val ticks = 40
    val published =
      new AtomicReference[Map[CellCoord, MandateCurrent]](Map.empty)
    val latch = new CountDownLatch(ticks)
    val agent = spawnCapturing(singleCellGrid(mkCell(0.0)), published, latch)
    val neighbour = CellCoord(1, 0)
    agent ! EventMsg.RegisterCell(neighbour)
    agent ! EventMsg.UpsertEdge(LatticeEdge(origin, neighbour, diffusion = 0.5))
    agent ! EventMsg.StressInject(origin, 0.8)
    for _ <- 1 to ticks do agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))
    val snap = published.get()
    assertTrue(snap.contains(neighbour), "registered cell must join the published lattice")
    assertTrue(
      snap(neighbour).stress > 0.01,
      s"the new diffusion edge must carry stress into the new cell, got ${snap(neighbour).stress}"
    )

  @Test def removeCellDropsTheCellAndItsIncidentEdges(): Unit =
    val neighbour = CellCoord(1, 0)
    val grid = MandateGrid(
      Map(origin -> mkCell(0.0), neighbour -> mkCell(0.0)),
      Seq(LatticeEdge(origin, neighbour, diffusion = 0.5))
    )
    val agent = track(MandateAgent(grid, _ => ()))
    agent ! EventMsg.RemoveCell(neighbour)
    // Negative-phase assertion style used by this spec: give the mailbox a
    // beat, then check what did (not) happen.
    Thread.sleep(300)
    assertFalse(grid.cells.contains(neighbour), "removed cell must leave the lattice")
    assertTrue(grid.edges.isEmpty, "incident edges must be removed with the cell")

  @Test def linkTopologyEventsRewireTheRunnerAtRuntime(): Unit =
    // A saturated oscillator (origin) and a quiet bystander. The bystander
    // must diverge from an unlinked control only while the link exists.
    val ticks = 3000
    val bystander = CellCoord(1, 0)
    val link = PulseLink(origin, bystander, area = 2.0, delay = 0.5)

    def runBystander(linkEvents: EventDTO*): Double =
      val grid = MandateGrid(
        Map(origin -> mkOscillatingCell(), bystander -> mkCell(0.0)),
        Nil
      )
      val published =
        new AtomicReference[Map[CellCoord, MandateCurrent]](Map.empty)
      val latch = new CountDownLatch(ticks)
      val agent = spawnCapturing(grid, published, latch)
      linkEvents.foreach(agent ! _)
      for _ <- 1 to ticks do agent ! EventMsg.TickArrived(0.05, 20.0f)
      assertTrue(latch.await(5, TimeUnit.SECONDS))
      published.get()(bystander).dissonance

    val control = runBystander()
    val linked = runBystander(EventMsg.AddLink(link))
    val removed = runBystander(EventMsg.AddLink(link), EventMsg.RemoveLink(link.src, link.dst))

    assertTrue(
      Math.abs(linked - control) > 1e-3,
      s"an active link must perturb the bystander (control=$control, linked=$linked)"
    )
    assertEquals(
      control,
      removed,
      1e-12,
      "a link removed before the first spike must leave the bystander untouched"
    )

  // ==== sustenance (SetSustenance, design note 06 §7.1) ==== //

  /** Spawn an agent whose per-tick readouts are appended to `seen`, in
    * publication order, with one latch countdown per tick. */
  private def spawnReadoutLog(
      grid: MandateGrid,
      seen: java.util.concurrent.ConcurrentLinkedQueue[CellReadout],
      latch: CountDownLatch
  ): ActorRef[EventDTO] =
    track(
      MandateAgent(
        grid,
        _ => (),
        _ => (),
        r => { seen.add(r(origin)); latch.countDown() }
      )
    )

  @Test def fullWithdrawalBurnsTheCellWithoutAnythingInjected(): Unit =
    val ticks = 12
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(ticks)
    val grid = singleCellGrid(mkOscillatingCell())
    val agent = spawnReadoutLog(grid, seen, latch)
    for _ <- 1 to 10 do agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.SetSustenance(origin, 0.0)
    for _ <- 11 to ticks do agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    assertEquals(
      0.0,
      grid.cells(origin).sustenance,
      1e-12,
      "full withdrawal sets M = 0"
    )
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee),
      seen.toArray(Array.empty[CellReadout]).last.phase,
      "a fully withdrawn SATURATED cell reads OffsetBelowKnee — nothing was injected, something was removed"
    )

  @Test def partialWithdrawalBurnsOnlyWithEnoughStress(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(3)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    // M = 0.8 alone at s = 0: a = 0.7 - 0.24 = 0.46, inside the window.
    agent ! EventMsg.SetSustenance(origin, 0.8)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    // Saturation alone never burns (a = -0.3); together they do:
    // a = 0.7 - 0.24 - 1.0 = -0.54 < burnoutA.
    agent ! EventMsg.StressInject(origin, 1.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    // Easing back to M = 0.9 lifts the cell out again even at full stress:
    // a = 0.7 - 0.12 - 1.0 = -0.42 > burnoutA.
    agent ! EventMsg.SetSustenance(origin, 0.9)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val burnout = PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee)
    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertNotEquals(burnout, stages(0).phase, "partial withdrawal alone must not burn a calm cell")
    assertEquals(burnout, stages(1).phase, "partial withdrawal plus saturation must burn")
    assertNotEquals(burnout, stages(2).phase, "easing the withdrawal must lift the cell out again")

  @Test def drainageAloneNeverRevivesAWithdrawnCell(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(3)
    val grid = singleCellGrid(mkOscillatingCell()) // saturated, s = 1.0
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.SetSustenance(origin, 0.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    // Drain the stress completely: a = -0.5 at s = 0, STILL below the knee.
    agent ! EventMsg.StressInject(origin, -2.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    // Narrative re-inoculation: the offset returns to the registered base.
    agent ! EventMsg.SetSustenance(origin, 1.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val burnout = PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee)
    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertEquals(burnout, stages(0).phase)
    assertEquals(burnout, stages(1).phase, "draining stress alone must not revive a withdrawn cell")
    assertNotEquals(burnout, stages(2).phase, "restoring the sustenance must lift the offset back")
    assertEquals(
      1.0,
      grid.cells(origin).sustenance,
      1e-12,
      "restoration sets M back to native"
    )

  @Test def reRegistrationResetsSustenanceToNative(): Unit =
    val grid = singleCellGrid(mkCell(0.0))
    val agent = track(MandateAgent(grid, _ => ()))
    agent ! EventMsg.SetSustenance(origin, 0.0)
    agent ! EventMsg.RegisterCell(origin)
    // Negative-phase assertion style used by this spec: give the mailbox a
    // beat, then check what did (not) happen.
    Thread.sleep(300)
    assertEquals(
      1.0,
      grid.cells(origin).sustenance,
      1e-12,
      "a re-registered cell starts at native sustenance"
    )

  // ==== pulse ledger bookkeeping (design note 08; batch-1 decoupling) ==== //

  @Test def wellSpacedPulsesOpenSuppressionWindows(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    // One tick to publish the baseline, one after the pulse.
    val latch = new CountDownLatch(2)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertFalse(stages.head.pulseSuppressed, "no pulse yet -> no suppression window")
    assertTrue(stages.last.pulseSuppressed, "a counted pulse opens the shelter window")
    assertTrue(stages.last.pulseTolerance > 0.0, "a pulse leaves a trace of tolerance")
    assertTrue(
      stages.last.pulseDrive > 0.0,
      s"a Counted pulse also feeds the equations (p -> v), got drive ${stages.last.pulseDrive}"
    )

  @Test def swallowedStrikeDoesNotOpenAWindow(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(2)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    // Design note 08 §3: a pulse swallowed by an active spike still enters the
    // equations, but it is PhysicsOnly and must not touch the pulse ledger.
    agent ! EventMsg.FirePulse(origin, 12.0, bookkeeping = PulseBookkeeping.PhysicsOnly)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertFalse(
      stages.last.pulseSuppressed,
      "a swallowed strike must not open a suppression window"
    )
    assertFalse(stages.last.isMaintained, "no counted pulse -> no shelter")
    // Batch-1 decoupling: the physics/ledger split is visible. The pulse did
    // reach the fast variable (kernel drive > 0) and the ledger stayed inert
    // (no window, no tolerance, no lockout).
    assertTrue(
      stages.last.pulseDrive > 0.0,
      s"a PhysicsOnly pulse still enters the equations, got drive ${stages.last.pulseDrive}"
    )
    assertEquals(
      PulseLedger.zero,
      stages.last.pulseLedger,
      "a PhysicsOnly pulse must leave the whole pulse ledger untouched"
    )

  @Test def rapidPulsesDriveTheCellIntoAcuteBurnout(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(1)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    // Two strikes in the same instant: the second lands far inside the
    // previous pulse's safe interval -> acute overload -> OverloadLocked.
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val r = seen.toArray(Array.empty[CellReadout]).last
    // [BATCH-1 REGRESSION LOCK] the lid on an over-pulsed cell is the ledger's
    // mechanism rule (not a trajectory burn-out), and it still withdraws the
    // shelter. Whether a short lockout should is pending (§8.4).
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.OverloadLocked),
      r.phase,
      "over-rapid pulsing must read as an acute overload lockout, not as a burnt-out trajectory"
    )
    assertFalse(r.pulseSuppressed, "the overload collapses the shelter window")
    assertFalse(r.isMaintained, "an over-pulsed cell grants no shelter")

  @Test def deadMyceliumIgnoresPulsesEntirely(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(1)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.SetSustenance(origin, 0.0)
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val r = seen.toArray(Array.empty[CellReadout]).last
    assertFalse(
      r.pulseSuppressed,
      "a dead network carries no pulse: no window is opened"
    )
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.OffsetBelowKnee),
      r.phase,
      "M = 0 is chronic burnout regardless of pulses"
    )

  // ==== parameter provenance (save > config > default) ==== //

  @Test def registerCellUsesTheInjectedDefaultParameters(): Unit =
    val grid = singleCellGrid(mkCell(0.0))
    val fast = MandateParam(alpha = 0.61, beta = 0.72, epsilon = 0.09)
    val stress =
      StressParam(gamma = 7e-4, sustenanceGrowth = 1e-3, sustenanceBurn = 4e-3)
    val added = CellCoord(1, 0)

    MandateAgent.applyEvent(grid, EventMsg.RegisterCell(added), fast, stress)

    assertEquals(fast, grid.cells(added).getParam)
    assertEquals(stress, grid.cells(added).stressParam)

  @Test def explicitRegisterCellParametersWinOverTheInjectedDefaults(): Unit =
    val grid = singleCellGrid(mkCell(0.0))
    val explicit = MandateParam(alpha = 0.5, beta = 0.6, epsilon = 0.1)
    val added = CellCoord(2, 0)

    MandateAgent.applyEvent(
      grid,
      EventMsg.RegisterCell(added, None, Some(explicit)),
      MandateAgent.DefaultFastParam,
      MandateAgent.DefaultStressParam
    )

    assertEquals(explicit, grid.cells(added).getParam)
    assertEquals(
      MandateAgent.DefaultStressParam,
      grid.cells(added).stressParam,
      "the stress set has no per-event override, so the default still applies"
    )

  // ==== persistence control (save-time flush) ==== //

  @Test def flushNowPublishesTheCurrentLatticeWithoutTicking(): Unit =
    val grid = singleCellGrid(mkCell(0.0))
    val latch = new AtomicReference(new CountDownLatch(1))
    val published = new AtomicReference[MandateSnapshot](null)
    val agent = track(
      MandateAgent(
        grid,
        _ => (),
        persistSink = snap => {
          published.set(snap)
          latch.get.countDown()
        }
      )
    )

    // The first cadence point is the restored clock, so this tick publishes.
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.get.await(5, TimeUnit.SECONDS), "a tick publishes a snapshot")
    val ticked = published.get()

    latch.set(new CountDownLatch(1))
    agent ! EventMsg.SetBaselineCalming(origin, 0.9)
    agent ! EventMsg.FlushNow
    assertTrue(
      latch.get.await(5, TimeUnit.SECONDS),
      "FlushNow must publish without a tick event"
    )

    val flushed = published.get()
    assertEquals(
      ticked.simTime,
      flushed.simTime,
      1e-12,
      "the flush must not advance the integration clock"
    )
    assertEquals(
      0.9,
      flushed.cells.find(_.coord == origin).get.baselineCalming,
      1e-12,
      "the flush must capture the lattice as of the mailbox order"
    )


