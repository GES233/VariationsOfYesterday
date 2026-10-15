package moe.chestnut.awa.voy

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import org.apache.pekko.actor.typed.ActorSystem
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}
import moe.chestnut.awa.voy.inner.mandate.*
import moe.chestnut.awa.voy.network.MandateHudPackets
import moe.chestnut.awa.voy.world.DemoValleyChunkGenerator

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
  * with initial conditions and D8 downhill advection edges shaped to the
  * valley — see buildGrid. The real thing anchors to Nodes/terrain and
  * persists per dimension (design draft section 1.2, section 8 items 1-2).
  */
object MandateRuntime:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  final val width = 32
  final val height = 32
  final val originX = -width / 2
  final val originZ = -height / 2

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
  private val hudSubscribers =
    ConcurrentHashMap.newKeySet[UUID]()

  /** Hydrologically conditioned chunk elevation for the demo valley:
    * combines the bundled heightmap's ridge/valley relief with the
    * north-to-south main-stem grade and east-to-west side-gully grade so
    * D8 steepest-descent paths carry stress from the side gully
    * (`chunk (6, -5)`) into the main valley (`chunk (0, -5)`) and down
    * through the central stockade basin (`chunk (0, 0)`).
    */
  def valleyElevation(
      heights: Array[Int],
      chunkX: Int,
      chunkZ: Int
  ): Double =
    val raw = DemoValleyChunkGenerator.chunkMeanHeight(heights, chunkX, chunkZ)
    val dMain = Math.abs(chunkX.toDouble)
    val dGully =
      if chunkX >= 0 && chunkX <= 8 then
        Math.hypot(
          Math.max(0.0, Math.abs(chunkX - 3.0) - 3.0),
          (chunkZ + 5).toDouble
        )
      else Double.PositiveInfinity
    val dChannel = Math.min(dMain, dGully)
    val wChannel = Math.exp(-Math.pow(dChannel / 2.2, 2))
    val base = DemoValleyChunkGenerator.BaseHeight.toDouble * wChannel + raw * (1.0 - wChannel)
    base - chunkZ * 1.8 + Math.abs(chunkX) * 0.8

  /** Demo lattice anchored to the valley terrain:
    *   - the valley runs north-south; upstream is -z, where the side gully
    *     (the engineering site) joins around block (96, -80) -> chunk (6, -5)
    *   - D8 downhill advection edges are derived from the valley heightmap
    *     so stress flows down ridges, out of the side gully, and southward
    *     along the main valley through the central basin
    *   - initial stress is low in the basin (the stockade at the origin) and
    *     rises toward the upstream end, staying below the theoretical
    *     ignition threshold s_c ~ 0.233 (design draft section 1.1.1)
    *   - the gully patch carries a standing disturbance (u = 0.08); the
    *     basin carries baseline calming (c0 = 0.03, the stockade's bell)
    *
    * Constructed per engine start so restarts get a fresh world state.
    */
  def buildGrid(): MandateGrid =
    val heights = DemoValleyChunkGenerator.loadDefaultHeights()
    MandateGrid.fromElevation(
      width,
      height,
      diffusion = 0.002,
      advection = 0.004,
      elevation = coord =>
        val chunkX = coord.x + originX
        val chunkZ = coord.z + originZ
        valleyElevation(heights, chunkX, chunkZ)
      ,
      mkState = coord =>
        val chunkX = coord.x + originX // world chunk x
        val chunkZ = coord.z + originZ // world chunk z
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
    hudSubscribers.clear()
  }

  /** Subscribe or unsubscribe a player from HUD snapshot streaming. */
  def setHudSubscribed(playerUuid: UUID, enabled: Boolean): Unit =
    if enabled then hudSubscribers.add(playerUuid)
    else hudSubscribers.remove(playerUuid)

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
    val players = server.getPlayerManager.getPlayerList
    if !players.isEmpty then
      system.foreach(_ ! EventMsg.TickArrived(TickDt, tickRate))
      simTime += TickDt
      syncHudSubscribers(server)

  private def syncHudSubscribers(server: MinecraftServer): Unit =
    if hudSubscribers.isEmpty then return
    val radius = MandateHudPackets.DefaultRadius
    val side = 2 * radius + 1
    val playerManager = server.getPlayerManager
    val it = hudSubscribers.iterator()
    while it.hasNext do
      val uuid = it.next()
      val player = playerManager.getPlayer(uuid)
      if player != null then
        val cx = player.getChunkPos.x
        val cz = player.getChunkPos.z
        val cells = Vector.newBuilder[MandateCurrent]
        cells.sizeHint(side * side)
        for dz <- -radius to radius do
          for dx <- -radius to radius do
            cells += stateAt(cx + dx, cz + dz)
        val payload = MandateHudPackets.HudSnapshotPayload(
          simTime = simTime,
          centerChunkX = cx,
          centerChunkZ = cz,
          radius = radius,
          cells = cells.result()
        )
        ServerPlayNetworking.send(player, payload)

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

