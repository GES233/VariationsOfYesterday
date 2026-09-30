package moe.chestnut.awa.voy

import java.util.concurrent.ConcurrentLinkedQueue

import net.minecraft.server.MinecraftServer
import org.apache.pekko.actor.typed.ActorSystem
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}
import moe.chestnut.awa.voy.inner.mandate.*

/** Game-side bridge between Minecraft and the mandate engine actor
  * (design draft §1.4, 2026-09-30): owns the ActorSystem lifecycle, the
  * tick gate, snapshot publication, and the action drain.
  *
  * Discipline: the game thread only TELLS events and READS the published
  * snapshot; the actor thread is the single writer of the lattice.
  * Outbound actions are queued by the actor and drained back onto the
  * server thread at the next tick.
  *
  * TODO: the prototype lattice is fixed to world chunks [-16, 15] in both
  * axes, with its disturbance hotspot around chunk (0, 0). Future iterations
  * should anchor it to the spawn point, follow the player's local region, or
  * derive cells from real world topology and persist them per dimension.
  */
object MandateRuntime:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val width = 32
  private val height = 32
  private val originX = -width / 2
  private val originZ = -height / 2

  /** Fixed simulation step per game tick: 20 tps x 0.05 = 1 time unit per
    * game second. Provisional mapping pending calibration against the
    * "first catastrophe at 3-5 play hours" target (§1.1.3, §7 item 5).
    */
  private val TickDt = 0.05

  @volatile private var system: Option[ActorSystem[EventDTO]] = None
  @volatile private var snapshot: Map[(Int, Int), MandateCurrent] =
    Map.empty
  private var lastTickRate: Float = 20.0f
  private val pendingActions =
    new ConcurrentLinkedQueue[MandateActionProtocol]()

  /** Prototype lattice: disturbance hotspot around its centre, calming ring
    * further out. Constructed per engine start so restarts get a fresh
    * world state. */
  private def buildGrid(): MandateGrid = MandateGrid.rectangular(
    width,
    height,
    diffusion = 0.002,
    coord =>
      val localX = coord.x - width / 2
      val localZ = coord.z - height / 2
      val distance = Math.sqrt(localX * localX + localZ * localZ)
      val initialStress = Math.max(0.0, 0.18 - distance * 0.004)
      val state = MandateCurrent(0.0, 0.0, initialStress, 0.0)
      val cell = MandateState(
        state,
        MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
        time_step = 0.01
      )
      cell.disturbance = if distance < 2.0 then 0.08 else 0.002
      cell.baselineCalming = if distance < 5.0 then 0.03 else 0.0
      cell
  )

  /** Start the engine. Idempotent while running. */
  def start(): Unit = synchronized {
    if system.isEmpty then
      logger.info("Starting mandate engine actor system.")
      system = Some(
        ActorSystem(
          MandateAgent(buildGrid(), publishSnapshot, pendingActions.add(_)),
          "voy-mandate"
        )
      )
  }

  /** Stop the engine and drop the published state. */
  def stop(): Unit = synchronized {
    system.foreach(_.terminate())
    system = None
    snapshot = Map.empty
  }

  /** One server tick: drain pending actions back onto the game thread, then
    * deliver the tick event. No players online -> no tick events, the
    * simulation pauses (design draft §1.4 gate: the 3-5h first-catastrophe
    * target is measured in play time).
    *
    * The nominal tick rate is polled each tick and stamped into the event
    * (§1.4: the actor must stay a pure function of the event stream, so the
    * rate may not be measured wall-clock-side). Changes are logged once.
    */
  def onTick(server: MinecraftServer): Unit =
    drainActions(server)
    val tickRate = server.getTickManager.getTickRate
    if tickRate != lastTickRate then
      logger.info(s"Tick rate changed: $lastTickRate -> $tickRate")
      lastTickRate = tickRate
    if !server.getPlayerManager.getPlayerList.isEmpty then
      system.foreach(_ ! EventMsg.TickArrived(TickDt, tickRate))

  /** HUD / external read: world chunk -> state; zero state outside the
    * lattice or while the engine is stopped.
    */
  def stateAt(chunkX: Int, chunkZ: Int): MandateCurrent =
    snapshot.getOrElse((chunkX, chunkZ), MandateCurrent(0.0, 0.0, 0.0, 0.0))

  /** Actor-thread callback: republish the grid-local snapshot with world
    * chunk coordinates. The map is immutable, so the volatile handoff is
    * safe for render-thread readers.
    */
  private def publishSnapshot(cells: Map[CellCoord, MandateCurrent]): Unit =
    snapshot = cells.iterator.map { case (coord, state) =>
      (coord.x + originX, coord.z + originZ) -> state
    }.toMap

  /** Outbound actions produced by the engine; consumed on the server
    * thread. No producers exist yet — the queue defines the direction.
    */
  private def drainActions(server: MinecraftServer): Unit =
    var action = pendingActions.poll()
    while action != null do
      logger.info(s"Mandate action: $action")
      action = pendingActions.poll()
