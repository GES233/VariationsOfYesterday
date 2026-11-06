package moe.chestnut.awa.voy.inner.actor

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/** Contract of the in-repo actor kernel: FIFO delivery on one worker thread,
  * fire-and-forget `tell`, and a `stop` that refuses later messages.
  */
class ActorSpec:

  @Test def messagesAreDeliveredInSubmissionOrder(): Unit =
    val received = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
    val latch = new CountDownLatch(100)
    val actor = Actor.spawn[Int]("test-order") { message =>
      received.add(message)
      latch.countDown()
    }
    try
      for i <- 1 to 100 do actor ! i
      assertTrue(latch.await(5, TimeUnit.SECONDS), "all messages should be handled")
      import scala.jdk.CollectionConverters.*
      assertEquals((1 to 100).toList, received.asScala.toList)
    finally actor.stop()

  @Test def stopRejectsLaterMessagesAndIsIdempotent(): Unit =
    val handled = new AtomicInteger(0)
    val actor = Actor.spawn[Int]("test-stop") { _ => handled.incrementAndGet() }
    actor.stop()
    actor.stop()
    assertFalse(actor.isRunning)
    actor ! 1
    Thread.sleep(100)
    assertEquals(0, handled.get(), "messages sent after stop must be dropped")

  @Test def tellIsNonBlockingWhileTheHandlerRuns(): Unit =
    val gate = new CountDownLatch(1)
    val entered = new CountDownLatch(1)
    val done = new CountDownLatch(2)
    val actor = Actor.spawn[Int]("test-busy") { _ =>
      if entered.getCount > 0 then
        entered.countDown()
        gate.await(5, TimeUnit.SECONDS)
      done.countDown()
    }
    try
      actor ! 1
      assertTrue(entered.await(5, TimeUnit.SECONDS), "handler should start")
      actor ! 2
      assertEquals(
        2L,
        done.getCount,
        "second message must still be queued behind the blocked handler"
      )
      gate.countDown()
      assertTrue(done.await(5, TimeUnit.SECONDS))
    finally actor.stop()
