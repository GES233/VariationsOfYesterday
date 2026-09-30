package moe.chestnut.awa.voy.inner.mandate

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.junit.jupiter.api.{AfterEach, Test}
import org.junit.jupiter.api.Assertions.*

import moe.chestnut.awa.voy.inner.event.EventMsg

/** Wiring contract of [[MandateAgent]] (design draft §1.4, 2026-09-30):
  * tick events drive integration deterministically, one published snapshot
  * per tick, no tick no integration.
  */
class MandateAgentSpec:
  private val testKit = ActorTestKit()

  @AfterEach def shutdown(): Unit = testKit.shutdownTestKit()

  private def mkCell(disturbance: Double): MandateState =
    val cell = MandateState(
      MandateCurrent(0.0, 0.0, 0.0, 0.0),
      MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
      time_step = 0.05
    )
    cell.disturbance = disturbance
    cell

  @Test def tickArrivedDrivesDeterministicIntegration(): Unit =
    val ticks = 20
    val published =
      new AtomicReference[Map[CellCoord, MandateCurrent]](Map.empty)
    val latch = new CountDownLatch(ticks)
    val grid = MandateGrid(Map(CellCoord(0, 0) -> mkCell(0.08)), Seq.empty)
    val agent = testKit.spawn(
      MandateAgent(
        grid,
        snapshot => { published.set(snapshot); latch.countDown() }
      )
    )
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
    val actual = published.get()(CellCoord(0, 0))
    assertEquals(expected.stress, actual.stress, 1e-12)
    assertEquals(expected.dissonance, actual.dissonance, 1e-12)
    assertTrue(actual.stress > 0.0, "disturbance should accumulate stress")

  @Test def noTickNoIntegration(): Unit =
    val published =
      new AtomicReference[Map[CellCoord, MandateCurrent]](Map.empty)
    val grid = MandateGrid(Map(CellCoord(0, 0) -> mkCell(0.08)), Seq.empty)
    val agent = testKit.spawn(MandateAgent(grid, published.set(_)))
    agent ! EventMsg.GenericEventDTO("player", "ring", "bell", Map.empty)
    // Negative assertion: give the actor a moment to (not) react.
    Thread.sleep(300)
    assertTrue(
      published.get().isEmpty,
      "non-tick events must not trigger integration or publication"
    )
