package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.VoyConfig

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import net.minecraft.server.MinecraftServer
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.action.MandateActionProtocol
import moe.chestnut.awa.voy.inner.actor.ActorRef
import moe.chestnut.awa.voy.inner.event.{EventDTO, EventMsg}
import moe.chestnut.awa.voy.inner.veins.*
import moe.chestnut.awa.voy.mandate.overworld.OverworldVocabulary

/** Game-side bridge between Minecraft and the mandate engine actor: owns the
  * engine actor lifecycle, the tick gate, readout publication, and the action
  * drain.
  */
object MandateRuntime:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private def TickDt = VoyConfig.current.tickDt

  @volatile private var fieldStrategy: MandateField = PrototypeValleyField
  @volatile private var system: Option[ActorRef[EventDTO]] = None
  @volatile private var readoutSnapshot: Map[(Int, Int), CellReadout] =
    Map.empty
  private var lastTickRate: Float = 20.0f
  private val pendingActions =
    new ConcurrentLinkedQueue[MandateActionProtocol]()

  private val flushLatch = new AtomicReference[CountDownLatch](null)

  def worldToGrid(chunkX: Int, chunkZ: Int): CellCoord =
    fieldStrategy.worldToGrid(chunkX, chunkZ, VoyConfig.current)

  def gridToWorld(cell: CellCoord): (Int, Int) =
    fieldStrategy.gridToWorld(cell, VoyConfig.current)

  def isLiveChunk(chunkX: Int, chunkZ: Int): Boolean =
    fieldStrategy.isLiveChunk(chunkX, chunkZ, readoutSnapshot, VoyConfig.current)

  def liveChunks: Iterable[(Int, Int)] = readoutSnapshot.keys

  def tell(event: EventDTO): Unit =
    system.foreach(_ ! event)

  def injectStressAt(chunkX: Int, chunkZ: Int, amount: Double): Unit =
    tell(EventMsg.StressInject(worldToGrid(chunkX, chunkZ), amount))

  def strikeBellAt(
      chunkX: Int,
      chunkZ: Int,
      blockX: Int,
      blockY: Int,
      blockZ: Int,
      strikerId: java.util.UUID
  ): Unit =
    tell(
      EventMsg.StrikeBell(
        worldToGrid(chunkX, chunkZ),
        blockX,
        blockY,
        blockZ,
        Some(strikerId)
      )
    )

  def setBaselineCalmingAt(chunkX: Int, chunkZ: Int, level: Double): Unit =
    tell(EventMsg.SetBaselineCalming(worldToGrid(chunkX, chunkZ), level))

  def setSustenanceAt(chunkX: Int, chunkZ: Int, level: Double): Unit =
    tell(EventMsg.SetSustenance(worldToGrid(chunkX, chunkZ), level))

  def start(server: MinecraftServer): Unit = synchronized {
    if system.isEmpty then
      val cfg = VoyConfig.current
      val overworld = server.getOverworld
      val persistence = overworld.getPersistentStateManager
        .getOrCreate(MandatePersistence.StateType, MandatePersistence.Id)

      // Baseline-calming sources (design note 11 ruling 11): load the
      // persisted chunk ↔ source registry and keep it saved on every change.
      // The world scan in `rebalance` heals anything the save missed.
      MandateCalming.load(
        persistence.sources,
        sources => {
          persistence.sources = sources
          persistence.markDirty()
        }
      )

      val expectedGeometry = fieldStrategy.geometrySignature(cfg)
      val restored = persistence.snapshot.filter { _ =>
        val matches = persistence.geometry == expectedGeometry
        if !matches then
          logger.warn(
            s"Discarding saved mandate lattice: geometry ${persistence.geometry} " +
              s"does not match the active lattice $expectedGeometry."
          )
        matches
      }

      val (grid, links, initialSimTime, initialRhythmEvidence) = restored match
        case Some(snap) =>
          logger.info(
            s"Restoring mandate lattice from saved state " +
              s"(${snap.cells.size} cells, ${snap.links.size} links, " +
              s"simTime = ${snap.simTime})."
          )
          val grid = MandateSnapshot.restoreGrid(snap)
          val rhythmEvidence = MandateSnapshot.restoreRhythmEvidence(snap)
          logSavedParameters(snap)
          (grid, snap.links, snap.simTime, rhythmEvidence)
        case None =>
          val fresh = fieldStrategy.buildInitialGrid(
            cfg,
            (cx, cz) => OverworldVocabulary.ecologyAtChunk(overworld, cx, cz)
          )
          logger.info(
            s"Starting fresh mandate lattice (${fresh.cells.size} cells); " +
              s"parameters from config `${VoyConfig.FileName}` " +
              "(core defaults if absent)."
          )
          (fresh, Nil, 0.0, Map.empty[CellCoord, RhythmEvidence])

      persistence.geometry = expectedGeometry
      persistence.snapshot = Some(
        MandateSnapshot.capture(grid, links, initialSimTime, initialRhythmEvidence)
      )
      persistence.markDirty()

      logger.info("Starting mandate engine actor.")
      system = Some(
        MandateAgent(
          grid,
          readoutSink = publishReadout,
          actionSink = pendingActions.add(_),
          persistSink = snap => {
            persistence.snapshot = Some(snap)
            persistence.markDirty()
            val pending = flushLatch.getAndSet(null)
            if pending != null then pending.countDown()
          },
          config = AgentConfig(
            links = links,
            initialSimTime = initialSimTime,
            defaultFastParam = VoyConfig.current.fastParam,
            defaultStressParam = VoyConfig.current.stressParam,
            initialRhythmEvidence = initialRhythmEvidence,
            rhythmParam = VoyConfig.current.rhythmParam,
            calmingParam = VoyConfig.current.calmingParam
          )
        )
      )
  }

  private def logSavedParameters(snap: MandateSnapshot): Unit =
    val sets = MandateSnapshot.parameterSets(snap)
    logger.info(
      s"Saved parameters: ${sets.size} distinct set(s) over ${snap.cells.size} cells " +
        "(restore precedence: save > config > default)."
    )
    val shown = sets.take(4)
    shown.foreach { case (fast, stress, count) =>
      logger.info(s"  saved by $count cell(s): fast=$fast stress=$stress")
    }
    if sets.size > shown.size then
      logger.info(s"  ... ${sets.size - shown.size} more saved set(s) omitted")

    val config = VoyConfig.current
    val matching = sets
      .filter { case (fast, stress, _) =>
        fast == config.fastParam && stress == config.stressParam
      }
      .map(_._3)
      .sum
    if matching == snap.cells.size then
      logger.info(
        "  config parameters match the save: editing them affects newly registered cells only."
      )
    else
      logger.warn(
        s"  config parameters (fast=${config.fastParam} stress=${config.stressParam}) " +
          s"match $matching/${snap.cells.size} saved cells; the saved values win for the rest, " +
          "the config applies to newly registered cells only."
      )

  def stop(): Unit = synchronized {
    system.foreach(_.stop())
    system = None
    readoutSnapshot = Map.empty
    pendingActions.clear()
    MandateNetworking.clearAll()
  }

  def onTick(server: MinecraftServer): Unit =
    drainActions(server)
    val tickRate = server.getTickManager.getTickRate
    if tickRate != lastTickRate then
      logger.info(s"Tick rate changed: $lastTickRate -> $tickRate")
      lastTickRate = tickRate
    if !server.getPlayerManager.getPlayerList.isEmpty then
      system.foreach(_ ! EventMsg.TickArrived(TickDt, tickRate))
      MandateOverworld.onServerTick(server)
      MandateNetworking.onServerTick(server)

  def flush(): Unit =
    system match
      case None => ()
      case Some(ref) =>
        val latch = new CountDownLatch(1)
        flushLatch.set(latch)
        ref ! EventMsg.FlushNow
        try
          if !latch.await(2, TimeUnit.SECONDS) then
            logger.warn(
              "Persistence flush timed out; this save may lag the live lattice " +
                "by up to one persist period."
            )
          else logger.debug("Persistence flushed for save.")
        finally flushLatch.set(null)

  def readoutAt(chunkX: Int, chunkZ: Int): CellReadout =
    readoutSnapshot.getOrElse((chunkX, chunkZ), CellReadout.zero)

  private def publishReadout(cells: Map[CellCoord, CellReadout]): Unit =
    val cfg = VoyConfig.current
    readoutSnapshot = cells.iterator.map { case (coord, readout) =>
      fieldStrategy.gridToWorld(coord, cfg) -> readout
    }.toMap

  private def drainActions(server: MinecraftServer): Unit =
    var action = pendingActions.poll()
    while action != null do
      logger.info(s"Mandate action: $action")
      MandateOverworld.onAction(server, action)
      action = pendingActions.poll()
