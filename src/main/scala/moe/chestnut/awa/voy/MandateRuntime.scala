package moe.chestnut.awa.voy

import java.util.concurrent.ConcurrentLinkedQueue

import net.minecraft.server.MinecraftServer
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import moe.chestnut.awa.voy.inner.actor.ActorRef
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}
import moe.chestnut.awa.voy.inner.mandate.*

/** Game-side bridge between Minecraft and the mandate engine actor
  * (design draft §1.4, 2026-09-30): owns the engine actor lifecycle, the
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
  * derive cells from real world topology. The saved snapshot records the
  * geometry it was captured under (`MandatePersistence.geometry`), so a
  * lattice-geometry change discards the stale save loudly instead of
  * silently misplacing cells.
  *
  * Persistence (2026-11-07): the lattice survives restarts via the
  * dimension-scoped `PersistentState` carrier `MandatePersistence`. The
  * actor publishes a full `MandateSnapshot` on a slow cadence and the
  * manager serializes the latest one on world save; on start the lattice,
  * crystal links and the integration clock are restored from it.
  */
object MandateRuntime:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  // Lattice geometry and tuning come from the external config
  // (`VoyConfig`, loaded during mod init); they are defs so every read
  // sees the active configuration.
  private def lattice = VoyConfig.current.lattice
  private def width = lattice.width
  private def height = lattice.height
  private def originX = lattice.originX
  private def originZ = lattice.originZ

  /** Fixed simulation step per game tick: 20 tps x 0.05 = 1 time unit per
    * game second by default. Provisional mapping pending calibration
    * against the "first catastrophe at 3-5 play hours" target — now a
    * runtime config knob (`tickDt` / `stressParam.gamma`), no longer a
    * contract change (draft-01 §7.3).
    */
  private def TickDt = VoyConfig.current.tickDt

  @volatile private var system: Option[ActorRef[EventDTO]] = None
  @volatile private var snapshot: Map[(Int, Int), MandateCurrent] =
    Map.empty
  @volatile private var readoutSnapshot: Map[(Int, Int), CellReadout] =
    Map.empty
  private var lastTickRate: Float = 20.0f
  private val pendingActions =
    new ConcurrentLinkedQueue[MandateActionProtocol]()

  /** Convert world chunk coordinates to lattice-local cell coordinates. */
  def worldToGrid(chunkX: Int, chunkZ: Int): CellCoord =
    CellCoord(chunkX - originX, chunkZ - originZ)

  /** Convert lattice-local cell coordinates back to world chunk coordinates. */
  def gridToWorld(cell: CellCoord): (Int, Int) =
    (cell.x + originX, cell.z + originZ)

  /** Whether a world chunk lies inside the active prototype lattice. */
  def isLiveChunk(chunkX: Int, chunkZ: Int): Boolean =
    val c = worldToGrid(chunkX, chunkZ)
    c.x >= 0 && c.x < width && c.z >= 0 && c.z < height

  /** Forward one channel event to the running Mandate actor, if started. */
  def tell(event: EventDTO): Unit =
    system.foreach(_ ! event)

  def injectStressAt(chunkX: Int, chunkZ: Int, amount: Double): Unit =
    tell(EventMsg.StressInject(worldToGrid(chunkX, chunkZ), amount))

  def firePulseAt(chunkX: Int, chunkZ: Int, area: Double): Unit =
    tell(EventMsg.FirePulse(worldToGrid(chunkX, chunkZ), area))

  def setBaselineCalmingAt(chunkX: Int, chunkZ: Int, level: Double): Unit =
    tell(EventMsg.SetBaselineCalming(worldToGrid(chunkX, chunkZ), level))

  def setDisturbanceAt(chunkX: Int, chunkZ: Int, rate: Double): Unit =
    tell(EventMsg.SetDisturbance(worldToGrid(chunkX, chunkZ), rate))


  /** Prototype lattice: disturbance hotspot around its centre, calming ring
    * further out. Constructed per engine start so restarts get a fresh
    * world state; all geometry and tuning come from `VoyConfig`.
    */
  private def buildGrid(): MandateGrid = MandateGrid.rectangular(
    width,
    height,
    diffusion = lattice.diffusion,
    coord =>
      val localX = coord.x - width / 2
      val localZ = coord.z - height / 2
      val distance = Math.sqrt(localX * localX + localZ * localZ)
      val initialStress = Math.max(
        0.0,
        lattice.initialStressPeak - distance * lattice.initialStressFalloff
      )
      val state = MandateCurrent(0.0, 0.0, initialStress, 0.0)
      val cell = MandateState(
        state,
        VoyConfig.current.fastParam,
        // The game opts into the mycelial sustenance dynamics (design note
        // 06 §7.1); the frozen defaults keep the contract slice and the
        // fingerprint rigs on the bare FHN core.
        VoyConfig.current.stressParam
      )
      cell.disturbance =
        if distance < lattice.hotspotRadius then lattice.hotspotDisturbance
        else lattice.ambientDisturbance
      cell.baselineCalming =
        if distance < lattice.calmingRadius then lattice.baselineCalming else 0.0
      cell
  )

  /** Start the engine, restoring the saved lattice when one exists.
    * Idempotent while running.
    */
  def start(server: MinecraftServer): Unit = synchronized {
    if system.isEmpty then
      val persistence = server.getOverworld.getPersistentStateManager
        .getOrCreate(MandatePersistence.StateType, MandatePersistence.Id)

      // Restore only a snapshot captured under the same lattice geometry;
      // a geometry change (the prototype lattice is still anchored to
      // fixed chunks) discards the stale save loudly.
      val expectedGeometry = (originX, originZ, width, height)
      val restored = persistence.snapshot.filter { _ =>
        val matches = persistence.geometry == expectedGeometry
        if !matches then
          logger.warn(
            s"Discarding saved mandate lattice: geometry ${persistence.geometry} " +
              s"does not match the active lattice $expectedGeometry."
          )
        matches
      }

      val (grid, links, initialSimTime) = restored match
        case Some(snap) =>
          logger.info(
            s"Restoring mandate lattice from saved state " +
              s"(${snap.cells.size} cells, ${snap.links.size} links, " +
              s"simTime = ${snap.simTime})."
          )
          val grid = MandateSnapshot.restoreGrid(snap)
          // Restore semantics: STATE from the save, PARAMETERS from the
          // config — tuning a knob (e.g. the gamma time-scale calibration)
          // applies to existing worlds without wiping their history.
          grid.cells.values.foreach { cell =>
            cell.param = VoyConfig.current.fastParam
            cell.stressParam = VoyConfig.current.stressParam
          }
          (grid, snap.links, snap.simTime)
        case None =>
          (buildGrid(), Nil, 0.0)

      persistence.geometry = expectedGeometry
      // Seed the carrier immediately so a save landing before the first
      // cadence publish never writes an empty payload over a valid save.
      persistence.snapshot = Some(
        MandateSnapshot.capture(grid, links, initialSimTime)
      )
      persistence.markDirty()

      logger.info("Starting mandate engine actor.")
      system = Some(
        MandateAgent(
          grid,
          publishSnapshot,
          pendingActions.add(_),
          publishReadout,
          links = links,
          initialSimTime = initialSimTime,
          persistSink = snap => {
            persistence.snapshot = Some(snap)
            persistence.markDirty()
          }
        )
      )
  }

  /** Stop the engine and drop the published state. */
  def stop(): Unit = synchronized {
    system.foreach(_.stop())
    system = None
    snapshot = Map.empty
    readoutSnapshot = Map.empty
    pendingActions.clear()
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
      MandateOverworld.onServerTick(server)

  /** HUD / external read: world chunk -> state; zero state outside the
    * lattice or while the engine is stopped.
    */
  def stateAt(chunkX: Int, chunkZ: Int): MandateCurrent =
    snapshot.getOrElse((chunkX, chunkZ), MandateCurrent(0.0, 0.0, 0.0, 0.0))

  /** HUD / Overworld read: world chunk -> semantic readout (`Regime`,
    * `PhaseReading`, and `baselineCalming`); `CellReadout.zero` outside the
    * lattice or while the engine is stopped.
    */
  def readoutAt(chunkX: Int, chunkZ: Int): CellReadout =
    readoutSnapshot.getOrElse((chunkX, chunkZ), CellReadout.zero)

  /** Actor-thread callback: republish the grid-local snapshot with world
    * chunk coordinates. The map is immutable, so the volatile handoff is
    * safe for render-thread readers.
    */
  private def publishSnapshot(cells: Map[CellCoord, MandateCurrent]): Unit =
    snapshot = cells.iterator.map { case (coord, state) =>
      (coord.x + originX, coord.z + originZ) -> state
    }.toMap

  private def publishReadout(cells: Map[CellCoord, CellReadout]): Unit =
    readoutSnapshot = cells.iterator.map { case (coord, readout) =>
      (coord.x + originX, coord.z + originZ) -> readout
    }.toMap

  /** Outbound actions produced by the engine; consumed on the server
    * thread.
    */
  private def drainActions(server: MinecraftServer): Unit =
    var action = pendingActions.poll()
    while action != null do
      logger.info(s"Mandate action: $action")
      MandateOverworld.onAction(server, action)
      action = pendingActions.poll()

