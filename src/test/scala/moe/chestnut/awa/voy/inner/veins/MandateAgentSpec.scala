package moe.chestnut.awa.voy.inner.veins

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, TimeUnit}

import scala.collection.mutable.ArrayBuffer

import org.junit.jupiter.api.{AfterEach, Test}
import org.junit.jupiter.api.Assertions.*

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import moe.chestnut.awa.voy.inner.actor.ActorRef
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}

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
        r => { readouts.set(r); latch.countDown() }
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
      PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle),
      seen.toArray(Array.empty[CellReadout]).last.phase,
      "a fully withdrawn SATURATED cell reads BurnoutNoCycle — nothing was injected, something was removed"
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

    val burnout = PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle)
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

    val burnout = PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle)
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

  // ==== heartbeat pulse bookkeeping (design note 08) ==== //

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
    assertTrue(stages.last.pulseSuppressed, "a bell pulse opens the heartbeat window")
    assertTrue(stages.last.pulseTolerance > 0.0, "a pulse leaves a trace of tolerance")

  @Test def swallowedStrikeDoesNotOpenAWindow(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(2)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    // Design note 08 §3: a pulse swallowed by an active spike still enters the
    // equations, but it is not a heartbeat and must not grant shelter.
    agent ! EventMsg.FirePulse(origin, 12.0, heartbeat = false)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val stages = seen.toArray(Array.empty[CellReadout]).toSeq
    assertFalse(
      stages.last.pulseSuppressed,
      "a swallowed strike must not open a suppression window"
    )
    assertFalse(stages.last.isMaintained, "no heartbeat -> no shelter")

  @Test def rapidPulsesDriveTheCellIntoAcuteBurnout(): Unit =
    val seen = new java.util.concurrent.ConcurrentLinkedQueue[CellReadout]()
    val latch = new CountDownLatch(1)
    val grid = singleCellGrid(mkCell(0.0))
    val agent = spawnReadoutLog(grid, seen, latch)
    // Two strikes in the same instant: the second lands far inside the
    // previous pulse's safe interval -> acute overload -> BurnoutNoCycle.
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.FirePulse(origin, 12.0)
    agent ! EventMsg.TickArrived(0.05, 20.0f)
    assertTrue(latch.await(5, TimeUnit.SECONDS))

    val r = seen.toArray(Array.empty[CellReadout]).last
    assertEquals(
      PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle),
      r.phase,
      "over-rapid pulsing must read as acute BurnoutNoCycle"
    )
    assertFalse(r.pulseSuppressed, "the overload collapses the suppression window")
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
      PhaseReading.Undefined(UndefinedReason.BurnoutNoCycle),
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


