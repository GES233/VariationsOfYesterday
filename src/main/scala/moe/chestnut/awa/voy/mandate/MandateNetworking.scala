package moe.chestnut.awa.voy.mandate

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.jdk.CollectionConverters.*

import net.fabricmc.fabric.api.networking.v1.{PayloadTypeRegistry, ServerPlayConnectionEvents, ServerPlayNetworking}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.world.World

import moe.chestnut.awa.voy.inner.veins.CellReadout

/** Server-side synchronization of live mandate cells within each player's
  * render/view distance.
  *
  * Sync cadence aligns with the 1 Hz readout publication (`SyncIntervalTicks = 20`),
  * while crossing into a new chunk or changing dimensions triggers an immediate
  * refresh so newly visible cells arrive without delay.
  */
object MandateNetworking:
  val MaxSyncRadius: Int = 12
  val SyncIntervalTicks: Int = 20
  private val OverworldId: Identifier = World.OVERWORLD.getValue

  final case class PlayerSyncState(
      dimensionId: Identifier,
      centerChunkX: Int,
      centerChunkZ: Int,
      radius: Int,
      includeDebug: Boolean,
      sent: Map[(Int, Int), SyncedCellEntry]
  )

  private val playerStates = new ConcurrentHashMap[UUID, PlayerSyncState]()
  private var tickCounter: Int = 0
  private var registered: Boolean = false

  def register(): Unit =
    if !registered then
      registered = true
      PayloadTypeRegistry.playS2C().register(MandateChunkSyncPayload.Id, MandateChunkSyncPayload.Codec)
      ServerPlayConnectionEvents.JOIN.register { (handler, _, server) =>
        syncPlayer(server, handler.getPlayer, forceFull = true)
      }
      ServerPlayConnectionEvents.DISCONNECT.register { (handler, _) =>
        playerStates.remove(handler.getPlayer.getUuid)
      }

  def clearAll(): Unit =
    playerStates.clear()
    tickCounter = 0

  def onServerTick(server: MinecraftServer): Unit =
    tickCounter += 1
    val periodicTick = tickCounter % SyncIntervalTicks == 0
    server.getPlayerManager.getPlayerList.asScala.foreach { player =>
      val prev = Option(playerStates.get(player.getUuid))
      val dimId = player.getWorld.getRegistryKey.getValue
      val chunkPos = player.getChunkPos
      val movedOrSwitched = prev.forall { s =>
        s.dimensionId != dimId ||
        s.centerChunkX != chunkPos.x ||
        s.centerChunkZ != chunkPos.z
      }
      if periodicTick || movedOrSwitched then
        syncPlayer(server, player, forceFull = false)
    }

  private def syncPlayer(
      server: MinecraftServer,
      player: ServerPlayerEntity,
      forceFull: Boolean
  ): Unit =
    val uuid = player.getUuid
    val prev = if forceFull then None else Option(playerStates.get(uuid))
    val dimId = player.getWorld.getRegistryKey.getValue
    val radius = Math.max(1, Math.min(server.getPlayerManager.getViewDistance, MaxSyncRadius))
    val includeDebug =
      FabricLoader.getInstance.isDevelopmentEnvironment || player.hasPermissionLevel(2)

    if dimId != OverworldId then
      val needsClear = prev.exists(_.sent.nonEmpty) || prev.isEmpty
      if needsClear then
        ServerPlayNetworking.send(player, MandateChunkSyncPayload(replaceAll = true, Vector.empty))
      playerStates.put(
        uuid,
        PlayerSyncState(
          dimensionId = dimId,
          centerChunkX = player.getChunkPos.x,
          centerChunkZ = player.getChunkPos.z,
          radius = radius,
          includeDebug = includeDebug,
          sent = Map.empty
        )
      )
    else
      val (maybePayload, nextState) = computeSyncBatch(
        prev = prev,
        dimId = dimId,
        centerX = player.getChunkPos.x,
        centerZ = player.getChunkPos.z,
        radius = radius,
        includeDebug = includeDebug,
        isLive = MandateRuntime.isLiveChunk,
        readoutAt = MandateRuntime.readoutAt
      )
      playerStates.put(uuid, nextState)
      maybePayload.foreach(ServerPlayNetworking.send(player, _))

  /** Pure diff/window computation for a player's render radius.
    *
    * When the player's dimension, center chunk, sync radius, or debug privilege
    * changes, emits a `replaceAll = true` snapshot of live cells in range so the
    * client drops stale out-of-range entries; otherwise emits only changed cells
    * (`replaceAll = false`).
    */
  def computeSyncBatch(
      prev: Option[PlayerSyncState],
      dimId: Identifier,
      centerX: Int,
      centerZ: Int,
      radius: Int,
      includeDebug: Boolean,
      isLive: (Int, Int) => Boolean,
      readoutAt: (Int, Int) => CellReadout
  ): (Option[MandateChunkSyncPayload], PlayerSyncState) =
    val windowChanged = prev.forall { s =>
      s.dimensionId != dimId ||
      s.centerChunkX != centerX ||
      s.centerChunkZ != centerZ ||
      s.radius != radius ||
      s.includeDebug != includeDebug
    }

    val currentBuilder = Map.newBuilder[(Int, Int), SyncedCellEntry]
    val deltaBuilder = Vector.newBuilder[SyncedCellEntry]
    val prevSent = prev.map(_.sent).getOrElse(Map.empty)

    var dz = -radius
    while dz <= radius do
      var dx = -radius
      while dx <= radius do
        val cx = centerX + dx
        val cz = centerZ + dz
        if isLive(cx, cz) then
          val readout = readoutAt(cx, cz)
          val view = ClientCellView.fromReadout(cx, cz, readout)
          val debug = if includeDebug then Some(DebugCellTelemetry.fromReadout(readout)) else None
          val entry = SyncedCellEntry(view, debug)
          currentBuilder += ((cx, cz) -> entry)
          if windowChanged || !prevSent.get((cx, cz)).contains(entry) then
            deltaBuilder += entry
        dx += 1
      dz += 1

    val currentMap = currentBuilder.result()
    val payloadCells = deltaBuilder.result()
    val nextState = PlayerSyncState(dimId, centerX, centerZ, radius, includeDebug, currentMap)

    val payload =
      if windowChanged then
        val hadEntries = prev.exists(_.sent.nonEmpty)
        if payloadCells.nonEmpty || hadEntries || prev.isEmpty then
          Some(MandateChunkSyncPayload(replaceAll = true, payloadCells))
        else None
      else if payloadCells.nonEmpty then
        Some(MandateChunkSyncPayload(replaceAll = false, payloadCells))
      else None

    (payload, nextState)
