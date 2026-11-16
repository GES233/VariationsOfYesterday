package moe.chestnut.awa.voy.mandate

import scala.jdk.CollectionConverters.*

import net.minecraft.block.Blocks
import net.minecraft.block.BlockState
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

import moe.chestnut.awa.voy.VoyConfig
import moe.chestnut.awa.voy.inner.veins.{CalmingStock, MandateSource, MandateSourceKind}

/** Wiring for the baseline-calming stock (design note 11 ruling 11).
  *
  * `c0` is fed by **sources**, not owned by the block that happens to be
  * struck: the world is a `Chunk ↔ List[Source(kind, pos)]` map (bells today;
  * AMD/ritual structures later), and the stock itself leaks with a 45-minute
  * time constant. A bell being broken removes one source — it does not zero
  * the chunk; two bells lose one and the chunk keeps trickling at half rate.
  *
  * The stock math is pure ([[CalmingStock]]); this object only maintains the
  * registry, scans loaded chunks for the world truth, and broadcasts
  * rebalance events to the engine (~60 s cadence, full mailbox order).
  */
object MandateCalming:
  /** 60 s at 20 TPS: fast enough that placing/removing a source reads within
    * a minute, cheap enough (1024 cells) to keep the mailbox light.
    */
  val RebalanceTicks: Int = 1200
  private val RebalanceSeconds: Double = 60.0

  private val registry =
    scala.collection.mutable.HashMap.empty[(Int, Int), scala.collection.mutable.Set[MandateSource]]
  private var persistSink: List[MandateSource] => Unit = _ => ()

  /** Load the persisted source registry (world load) and bind the sink that
    * writes it back on every change.
    */
  def load(sources: List[MandateSource], sink: List[MandateSource] => Unit): Unit =
    registry.clear()
    sources.foreach(addSilently)
    persistSink = sink

  /** Block state → calming source kind. Bells are the first kind; extend
    * here when AMD/ritual structures land (ruling 14).
    */
  def sourceKindOf(state: BlockState): Option[MandateSourceKind] =
    if state.isOf(Blocks.BELL) then Some(MandateSourceKind.Bell) else None

  def sourceKindAt(world: ServerWorld, pos: BlockPos): Option[MandateSourceKind] =
    sourceKindOf(world.getBlockState(pos))

  /** Register one source (idempotent). */
  def register(source: MandateSource): Unit =
    if addSilently(source) then persist()

  /** Remove the source at `pos`, if any; returns whether one was removed. */
  def unregister(x: Int, y: Int, z: Int): Boolean =
    val key = (x >> 4, z >> 4)
    val removed = registry.get(key).exists { set =>
      val before = set.size
      set.filterInPlace(s => s.x != x || s.y != y || s.z != z)
      set.size != before
    }
    if removed then persist()
    removed

  /** Total trickle weight of the chunk's registered sources. */
  def weightAt(chunkX: Int, chunkZ: Int): Double =
    registry.getOrElse((chunkX, chunkZ), Set.empty).iterator.map(_.kind.trickleWeight).sum

  /** World truth pass: for every LOADED lattice chunk, rebuild that chunk's
    * source set from the block entities actually present. Unloaded chunks
    * keep their persisted registry untouched. Rebuilding (rather than only
    * adding) also heals sources destroyed by non-player means (explosions,
    * pistons) once the chunk is loaded again.
    */
  def scanLoaded(world: ServerWorld): Unit =
    val lattice = VoyConfig.current.lattice
    var changed = false
    var cz = lattice.originZ
    while cz < lattice.originZ + lattice.height do
      var cx = lattice.originX
      while cx < lattice.originX + lattice.width do
        if world.isChunkLoaded(cx, cz) then
          val found = scala.collection.mutable.Set.empty[MandateSource]
          world.getChunk(cx, cz).getBlockEntities.values.asScala.foreach { be =>
            sourceKindOf(be.getCachedState).foreach { kind =>
              val pos = be.getPos
              found.add(MandateSource(kind, pos.getX, pos.getY, pos.getZ))
            }
          }
          val key = (cx, cz)
          val existing = registry.getOrElse(key, scala.collection.mutable.Set.empty[MandateSource])
          if existing != found then
            if found.isEmpty then registry.remove(key) else registry(key) = found
            changed = true
        cx += 1
      cz += 1
    if changed then persist()

  /** Discrete ritual dose on an anchored strike (see [[CalmingStock.dose]]). */
  def dose(c0: Double): Double =
    CalmingStock.dose(c0, VoyConfig.current.calmingParam)

  /** One rebalance pass: scan the world truth, then step every lattice cell's
    * stock toward its source-driven equilibrium (leak included).
    */
  def rebalance(world: ServerWorld): Unit =
    scanLoaded(world)
    val lattice = VoyConfig.current.lattice
    val p = VoyConfig.current.calmingParam
    var cz = 0
    while cz < lattice.height do
      var cx = 0
      while cx < lattice.width do
        val chunkX = lattice.originX + cx
        val chunkZ = lattice.originZ + cz
        if MandateRuntime.isLiveChunk(chunkX, chunkZ) then
          val c0 = MandateRuntime.readoutAt(chunkX, chunkZ).baselineCalming
          val next = CalmingStock.step(c0, weightAt(chunkX, chunkZ), RebalanceSeconds, p)
          if Math.abs(next - c0) > 1e-9 then
            MandateRuntime.setBaselineCalmingAt(chunkX, chunkZ, next)
        cx += 1
      cz += 1

  private def addSilently(source: MandateSource): Boolean =
    val key = (source.x >> 4, source.z >> 4)
    registry.getOrElseUpdate(key, scala.collection.mutable.Set.empty).add(source)

  private def persist(): Unit =
    persistSink(registry.valuesIterator.flatten.toList.sortBy(s => (s.x, s.y, s.z)))
