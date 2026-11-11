package moe.chestnut.awa.voy.mandate

import java.util.concurrent.atomic.AtomicBoolean
import net.fabricmc.fabric.api.attachment.v1.{AttachmentRegistry, AttachmentTarget, AttachmentType}
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.chunk.Chunk
import moe.chestnut.awa.voy.VoyConfig
import moe.chestnut.awa.voy.inner.veins.TerrainObservation
import org.slf4j.LoggerFactory
import scala.util.control.NonFatal

/** Generation-thread work stays on its own chunk; immutable attachments are
  * transferred and saved by Fabric. No world queries, neighbours or actor calls.
  */
object TerrainCapture:
  private val logger = LoggerFactory.getLogger(getClass)
  private val warned = new AtomicBoolean(false)
  private def id(path: String) = Identifier.of("variations-of-yesterday", path)
  val Early: AttachmentType[TerrainObservation] =
    AttachmentRegistry.createPersistent(id("terrain_surface_v1"), TerrainObservationCodec.codec)
  val Observed: AttachmentType[TerrainObservation] =
    AttachmentRegistry.createPersistent(id("terrain_observed_v1"), TerrainObservationCodec.codec)
  private val ignored = TagKey.of(RegistryKeys.BLOCK, id("terrain_ignored"))
  private val groundTag = TagKey.of(RegistryKeys.BLOCK, id("terrain_ground"))

  def register(): Unit =
    ServerChunkEvents.CHUNK_LOAD.register((world, chunk) => capture(world, chunk, false))

  def enabled(world: ServerWorld): Boolean =
    VoyConfig.current.terrain.allows(world.getRegistryKey.getValue.toString)

  def get(chunk: Chunk, early: Boolean): Option[TerrainObservation] =
    Option(chunk.asInstanceOf[AttachmentTarget].getAttached(if early then Early else Observed))

  /** Called after surface completion, or at the first full-chunk load.
    * A full load is intentionally NOT claimed to be pristine worldgen.
    */
  def capture(world: ServerWorld, chunk: Chunk, early: Boolean): Unit =
    if !enabled(world) || (early && !VoyConfig.current.terrain.earlyCapture) then return
    try
      if get(chunk, early).isEmpty then
        val generator = world.getChunkManager.getChunkGenerator.getClass.getName
        val sample = sampleChunk(chunk, early, generator)
        chunk.asInstanceOf[AttachmentTarget].setAttached(if early then Early else Observed, sample)
    catch
      case NonFatal(e) =>
        if warned.compareAndSet(false, true) then
          logger.warn("Terrain capture failed; leaving missing samples unresolved (further failures suppressed).", e)

  private def sampleChunk(chunk: Chunk, early: Boolean, generator: String): TerrainObservation =
    val bottom = chunk.getBottomY
    val top = chunk.getTopYInclusive
    val heightmap = if early then Heightmap.Type.WORLD_SURFACE_WG else Heightmap.Type.WORLD_SURFACE
    // Never populate a missing heightmap from an optional diagnostic hook.
    require(chunk.hasHeightmap(heightmap), s"Missing heightmap $heightmap")
    val surface = Vector.newBuilder[Int]
    val ground = Vector.newBuilder[Int]
    val depths = Vector.newBuilder[Int]
    val pos = new BlockPos.Mutable()
    for z <- 0 until 16; x <- 0 until 16 do
      val startY = math.min(top, chunk.sampleHeightmap(heightmap, x, z))
      val (surfaceY, groundY, depth) = TerrainObservation.scanColumn(bottom, startY) { y =>
        pos.set(chunk.getPos.getStartX + x, y, chunk.getPos.getStartZ + z)
        val state = chunk.getBlockState(pos)
        if state.isAir || state.isIn(ignored) then (false, false)
        // Match vanilla OCEAN_FLOOR's material predicate, with tag overrides.
        else (state.isIn(groundTag) || state.blocksMovement(), !state.getFluidState.isEmpty)
      }
      surface += surfaceY
      ground += groundY
      depths += depth
    TerrainObservation(
      if early then "surface_stage" else "first_full_observation",
      generator, bottom, top, surface.result(), ground.result(), depths.result()
    )
