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

/** Wiring contract of [[MandateAgent]]: tick events drive integration
  * deterministically, one published snapshot per tick, no tick no integration;
  * channel events apply in mailbox order and are dropped deterministically
  * when addressed outside the lattice.
  */
class MandateAgentSpec:
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

  @Test def regimeTransitionsAreDebouncedByDwell(): Unit =
    val dwell = 0.5
    val readouts =
      new AtomicReference[Map[CellCoord, CellReadout]](Map.empty)
    val actions = new java.util.concurrent.ConcurrentLinkedQueue[
      MandateActionProtocol
    ]()
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
    for _ <- 1 to 4 do agent ! EventMsg.TickArrived(0.05, 20.0f)
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
      readouts.get()(origin).isPulseSheltered,
      "a counted pulse must open the pulse stamp shelter window"
    )

    latchRef.set(new CountDownLatch(1))
    agent ! EventMsg.RegisterCell(origin)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(awaitPhase(latchRef))
    assertEquals(
      PulseStamp.zero,
      readouts.get()(origin).pulseStamp,
      "re-registration resets the dynamics, and the pulse stamp goes with it"
    )

  private def awaitPhase(latchRef: AtomicReference[CountDownLatch]): Boolean =
    latchRef.get().await(5, TimeUnit.SECONDS)

  private def mkOscillatingCell(): MandateState =
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
    Thread.sleep(300)
    assertFalse(grid.cells.contains(neighbour), "removed cell must leave the lattice")
    assertTrue(grid.edges.isEmpty, "incident edges must be removed with the cell")

  @Test def linkTopologyEventsRewireTheRunnerAtRuntime(): Unit =
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

    assertEquals(0.0, grid.cells(origin).sustenance, 1e-12)
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee),
      seen.toArray(Array.empty[CellReadout]).last.phase
    )

  @Test def partialWithdrawalBurnsOnlyWithEnoughStress(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(3)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.SetSustenance(origin, 0.8)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.StressInject(origin, 1.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.SetSustenance(origin, 0.9)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val burnout = PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee)
    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertNotEquals(burnout, stages(0).phase)
    assertEquals(burnout, stages(1).phase)
    assertNotEquals(burnout, stages(2).phase)

  @Test def drainageAloneNeverRevivesAWithdrawnCell(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(3)
    val grid = singleCellGrid(mkOscillatingCell())
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.SetSustenance(origin, 0.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.StressInject(origin, -2.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.SetSustenance(origin, 1.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val burnout = PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee)
    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertEquals(burnout, stages(0).phase)
    assertEquals(burnout, stages(1).phase)
    assertNotEquals(burnout, stages(2).phase)
    assertEquals(1.0, grid.cells(origin).sustenance, 1e-12)

  @Test def reRegistrationResetsSustenanceToNative(): Unit =
    val grid = singleCellGrid(mkCell(0.0))
    val agent = track(MandateAgent(grid, _ => ()))
    agent ! EventMsg.SetSustenance(origin, 0.0)
    agent ! EventMsg.RegisterCell(origin)
    Thread.sleep(300)
    assertEquals(1.0, grid.cells(origin).sustenance, 1e-12)

  @Test def wellSpacedPulsesOpenShelterWindows(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(2)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertFalse(stages.head.isPulseSheltered)
    assertTrue(stages.last.isPulseSheltered)
    assertTrue(stages.last.pulseDrive > 0.0)

  @Test def swallowedStrikeDoesNotOpenAWindow(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(2)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    agent ! EventMsg.FirePulse(origin, 12.0, bookkeeping = PulseBookkeeping.PhysicsOnly)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertFalse(stages.last.isPulseSheltered)
    assertFalse(stages.last.isSheltered)
    assertTrue(stages.last.pulseDrive > 0.0)
    assertEquals(PulseStamp.zero, stages.last.pulseStamp)

  @Test def rapidPulsesCollapseShelterWindowWithoutFakingBurnoutPhase(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(1)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val r = seen.toArray(Array.empty[CellReadout]).last
    assertFalse(r.isPulseSheltered, "striking inside cooldown collapses the shelter window")
    assertFalse(r.isSheltered, "an over-pulsed cell grants no shelter")
    assertTrue(r.pulseStamp.isInCooldownAt(r.simTime), "the cooldown remains active")

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
    assertFalse(r.isPulseSheltered, "a dead network carries no pulse: no window is opened")
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee),
      r.phase
    )

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
    assertEquals(MandateAgent.DefaultStressParam, grid.cells(added).stressParam)

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

    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.get.await(5, TimeUnit.SECONDS))
    val ticked = published.get()

    latch.set(new CountDownLatch(1))
    agent ! EventMsg.SetBaselineCalming(origin, 0.9)
    agent ! EventMsg.FlushNow
    assertTrue(latch.get.await(5, TimeUnit.SECONDS))

    val flushed = published.get()
    assertEquals(ticked.simTime, flushed.simTime, 1e-12)
    assertEquals(0.9, flushed.cells.find(_.coord == origin).get.baselineCalming, 1e-12)
