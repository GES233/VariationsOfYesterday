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
  * Demo staging: the lattice covers the demo valley region (32x32 chunks,
  * world chunks [-16, 15] on both axes, same extent as the demo heightmap)
  * with initial conditions hand-shaped to the valley — see buildGrid. The
  * real thing anchors to Nodes/terrain and persists per dimension (design
  * draft section 1.2, section 8 items 1-2).
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
  @volatile private var simTime = 0.0
  private var lastTickRate: Float = 20.0f
  private val pendingActions =
    new ConcurrentLinkedQueue[MandateActionProtocol]()

  /** Demo lattice anchored to the valley terrain:
    *   - the valley runs north-south; upstream is -z, where the side gully
    *     (the engineering site) joins around block (96, -80) -> chunk (6, -5)
    *   - initial stress is low in the basin (the stockade at the origin) and
    *     rises toward the upstream end, staying below the theoretical
    *     ignition threshold s_c ~ 0.233 (design draft section 1.1.1)
    *   - the gully patch carries a standing disturbance (u = 0.08); the
    *     basin carries baseline calming (c0 = 0.03, the stockade's bell)
    *
    * Constructed per engine start so restarts get a fresh world state.
    */
  private def buildGrid(): MandateGrid = MandateGrid.rectangular(
    width,
    height,
    diffusion = 0.002,
    coord =>
      val chunkX = coord.x - width / 2 // world chunk x
      val chunkZ = coord.z - height / 2 // world chunk z
      val basinDistance = math.hypot(chunkX.toDouble, chunkZ.toDouble)
      // 0 at the equator, 1 at the north (upstream) edge
      val upstream = math.max(0, -chunkZ) / (height / 2.0)
      val initialStress = 0.04 + 0.16 * upstream
      val state = MandateCurrent(0.0, 0.0, initialStress, 0.0)
      val cell = MandateState(
        state,
        MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
      )
      val gullyDistance = math.hypot((chunkX - 6).toDouble, (chunkZ + 5).toDouble)
      cell.disturbance = if gullyDistance < 2.5 then 0.08 else 0.002
      cell.baselineCalming = if basinDistance < 5.0 then 0.03 else 0.0
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
    simTime = 0.0
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
      simTime += TickDt

  /** Accumulated simulation time (time units), for HUD sampling aligned to
    * simulation time rather than wall clock.
    */
  def getSimTime: Double = simTime

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
