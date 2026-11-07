package moe.chestnut.awa.voy.inner.actor

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.LoggerFactory

/** A handle to one single-writer actor.
  *
  * Keeps the semantics the mandate engine relied on: messages are queued in
  * submission order and consumed by one worker thread, so the actor is the
  * only writer of its state and the message order is total. `tell` never
  * blocks and never throws; messages sent after `stop` are ignored.
  *
  * @param name worker thread name suffix, for thread dumps.
  */
final class ActorRef[T] private[actor] (
    private val name: String,
    private val queue: LinkedBlockingQueue[T],
    private val stopped: AtomicBoolean,
    private val worker: Thread
):

  /** Fire-and-forget delivery, in the same form as the previous actor API. */
  def !(message: T): Unit = tell(message)

  def tell(message: T): Unit =
    if !stopped.get() then queue.offer(message)

  /** Stop the worker. Queued messages are dropped and the call blocks until
    * the worker exits (bounded by a short join).
    */
  def stop(): Unit =
    if stopped.compareAndSet(false, true) then
      queue.clear()
      worker.interrupt()
      worker.join(1000L)

  /** Whether this handle still accepts messages. */
  def isRunning: Boolean = !stopped.get()

  override def toString: String = s"ActorRef($name, running=$isRunning)"

object Actor:

  private val logger = LoggerFactory.getLogger(getClass)

  /** Start an actor with `handler` as its message loop.
    *
    * The worker is a daemon thread, so a shutdown path that forgets to call
    * `stop` still lets the JVM exit. A handler failure is logged (SLF4J) and
    * stops the actor instead of silently killing the worker; there is no
    * supervision — the state freezes at the last completed message.
    */
  def spawn[T](name: String)(handler: T => Unit): ActorRef[T] =
    val queue = new LinkedBlockingQueue[T]()
    val stopped = new AtomicBoolean(false)
    val worker = new Thread(
      () =>
        try
          while true do handler(queue.take())
        catch
          case _: InterruptedException => ()
          case failure: Throwable =>
            stopped.set(true)
            logger.error(
              "actor '{}' died on an unhandled failure; state is frozen at the last completed message",
              name,
              failure
            )
      ,
      s"voy-$name"
    )
    worker.setDaemon(true)
    worker.start()
    new ActorRef[T](name, queue, stopped, worker)
