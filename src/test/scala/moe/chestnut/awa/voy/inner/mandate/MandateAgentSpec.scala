package moe.chestnut.awa.voy.inner.mandate

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.junit.jupiter.api.{AfterEach, Test}
import org.junit.jupiter.api.Assertions.*

import moe.chestnut.awa.voy.inner.event.EventMsg

/** Wiring contract of [[MandateAgent]] (design draft §1.4, 2026-09-30):
  * tick events drive integration deterministically, one published snapshot
  * per tick, no tick no integration; channel events apply in mailbox order
  * and are dropped deterministically when addressed outside the lattice.
  */
class MandateAgentSpec:
  private val testKit = ActorTestKit()

  @AfterEach def shutdown(): Unit = testKit.shutdownTestKit()

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
  ) =
    testKit.spawn(
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
    val agent = testKit.spawn(
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
