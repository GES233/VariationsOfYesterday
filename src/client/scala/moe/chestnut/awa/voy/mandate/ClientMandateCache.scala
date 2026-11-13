package moe.chestnut.awa.voy.mandate

import net.fabricmc.fabric.api.client.networking.v1.{ClientPlayConnectionEvents, ClientPlayNetworking}

/** Client-side cache of live mandate cells synchronized from the server via
  * [[MandateChunkSyncPayload]].
  *
  * Keeps client rendering and HUD decoupled from server memory (`MandateRuntime`)
  * so multiplayer / dedicated-server sessions behave identically to singleplayer.
  */
object ClientMandateCache:
  @volatile private var cells: Map[(Int, Int), SyncedCellEntry] = Map.empty
  private var registered: Boolean = false

  def register(): Unit =
    if !registered then
      registered = true
      ClientPlayNetworking.registerGlobalReceiver(
        MandateChunkSyncPayload.Id,
        (payload, _) => applySync(payload)
      )
      ClientPlayConnectionEvents.DISCONNECT.register((_, _) => clear())

  def applySync(payload: MandateChunkSyncPayload): Unit =
    val incoming = payload.cells.iterator.map { entry =>
      (entry.view.chunkX, entry.view.chunkZ) -> entry
    }
    cells =
      if payload.replaceAll then incoming.toMap
      else cells ++ incoming

  def clear(): Unit =
    cells = Map.empty

  def get(chunkX: Int, chunkZ: Int): Option[SyncedCellEntry] =
    cells.get((chunkX, chunkZ))

  def getView(chunkX: Int, chunkZ: Int): Option[ClientCellView] =
    cells.get((chunkX, chunkZ)).map(_.view)

  def size: Int = cells.size
