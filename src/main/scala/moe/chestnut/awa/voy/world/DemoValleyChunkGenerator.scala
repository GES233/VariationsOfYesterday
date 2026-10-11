package moe.chestnut.awa.voy.world

import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

import com.mojang.serialization.{Codec, MapCodec}
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.block.{BlockState, Blocks}
import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{ChunkRegion, HeightLimitView, Heightmap}
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.{BiomeAccess, FixedBiomeSource}
import net.minecraft.world.gen.StructureAccessor
import net.minecraft.world.gen.chunk.{Blender, ChunkGenerator, VerticalBlockSample}
import net.minecraft.world.gen.noise.NoiseConfig
import net.minecraft.world.chunk.Chunk

/** Demo-only chunk generator: terrain is read from a bundled grayscale
  * heightmap instead of noise, so the recruiting-demo valley can be *designed*
  * block by block (one pixel = one column) without hand-placing anything.
  *
  * Scope quarantine: this is the staging world type for the demo, NOT the
  * mod's worldgen route (design draft §5 route A+ -> B still governs the real
  * world). Do not grow this into a general generator.
  *
  * Heightmap contract:
  *   - one pixel per block column, [Size]x[Size], centered on world origin
  *     (pixel (0,0) = block (-HalfSize, -HalfSize)); the region matches the
  *     mandate lattice extent (32x32 chunks)
  *   - gray value 0..255 maps linearly to surface y in [BaseHeight, TopHeight]
  *   - outside the region the world falls back to [PlainHeight] flat land, and
  *     the outermost [BlendWidth] blocks blend toward it so the region edge is
  *     a readable slope instead of a cliff
  */
class DemoValleyChunkGenerator(
    val biomeEntry: RegistryEntry[Biome],
    val heightmapId: Identifier
) extends ChunkGenerator(FixedBiomeSource(biomeEntry)):
  import DemoValleyChunkGenerator.*

  private lazy val heights: Array[Int] = loadHeights(heightmapId)

  /** Surface y of the terrain at a block column (topmost solid block). */
  def surfaceHeight(x: Int, z: Int): Int =
    val px = x + HalfSize
    val pz = z + HalfSize
    if px < 0 || px >= Size || pz < 0 || pz >= Size then PlainHeight
    else
      val raw = heights(pz * Size + px)
      val edge = math.min(math.min(px, Size - 1 - px), math.min(pz, Size - 1 - pz))
      if edge >= BlendWidth then raw
      else
        val t = edge.toDouble / BlendWidth
        val s = t * t * (3.0 - 2.0 * t) // smoothstep
        math.round(PlainHeight + (raw - PlainHeight) * s).toInt

  private def stateAt(y: Int, surface: Int): BlockState =
    if y == MinY then Bedrock
    else if y > math.max(surface, SeaLevel) then Air
    else if y > surface then Water
    else if y == surface then (if surface < SeaLevel then Dirt else GrassBlock)
    else if y > surface - 4 then Dirt
    else Stone

  override protected def getCodec: MapCodec[? <: ChunkGenerator] = CODEC

  override def carve(
      chunkRegion: ChunkRegion,
      seed: Long,
      noiseConfig: NoiseConfig,
      biomeAccess: BiomeAccess,
      structureAccessor: StructureAccessor,
      chunk: Chunk
  ): Unit = () // no carvers registered for the demo biome

  override def buildSurface(
      region: ChunkRegion,
      structures: StructureAccessor,
      noiseConfig: NoiseConfig,
      chunk: Chunk
  ): Unit =
    val pos = chunk.getPos
    val mutable = new BlockPos.Mutable()
    for lx <- 0 until 16 do
      for lz <- 0 until 16 do
        val x = pos.getStartX + lx
        val z = pos.getStartZ + lz
        val surface = surfaceHeight(x, z)
        mutable.set(x, 0, z)
        for y <- MinY to math.max(surface, SeaLevel) do
          mutable.setY(y)
          chunk.setBlockState(mutable, stateAt(y, surface), false)

  override def populateEntities(region: ChunkRegion): Unit = ()

  override def populateNoise(
      blender: Blender,
      noiseConfig: NoiseConfig,
      structureAccessor: StructureAccessor,
      chunk: Chunk
  ): CompletableFuture[Chunk] = CompletableFuture.completedFuture(chunk)

  override def getWorldHeight(): Int = WorldHeight

  override def getSeaLevel(): Int = SeaLevel

  override def getMinimumY(): Int = MinY

  override def getSpawnHeight(world: HeightLimitView): Int = surfaceHeight(0, 0) + 1

  override def getHeight(
      x: Int,
      z: Int,
      heightmap: Heightmap.Type,
      world: HeightLimitView,
      noiseConfig: NoiseConfig
  ): Int = surfaceHeight(x, z) + 1

  override def getColumnSample(
      x: Int,
      z: Int,
      world: HeightLimitView,
      noiseConfig: NoiseConfig
  ): VerticalBlockSample =
    val states = new Array[BlockState](world.getHeight)
    val surface = surfaceHeight(x, z)
    for i <- states.indices do
      states(i) = stateAt(world.getBottomY + i, surface)
    VerticalBlockSample(world.getBottomY, states)

  override def appendDebugHudText(
      text: java.util.List[String],
      noiseConfig: NoiseConfig,
      pos: BlockPos
  ): Unit = ()

object DemoValleyChunkGenerator:
  final val GeneratorId: Identifier = Identifier.of("variations-of-yesterday", "demo_valley")
  private final val DefaultHeightmapId: Identifier =
    Identifier.of("variations-of-yesterday", "valley/heightmap")

  // Region and height constants (block units).
  final val Size = 512 // 32 x 32 chunks, matching the mandate lattice extent
  final val HalfSize = Size / 2
  final val BlendWidth = 48
  final val MinY = -64
  final val WorldHeight = 384
  final val SeaLevel = 63
  final val PlainHeight = 80
  final val BaseHeight = 48
  final val TopHeight = 144

  private final val Bedrock = Blocks.BEDROCK.getDefaultState
  private final val Stone = Blocks.STONE.getDefaultState
  private final val Dirt = Blocks.DIRT.getDefaultState
  private final val GrassBlock = Blocks.GRASS_BLOCK.getDefaultState
  private final val Water = Blocks.WATER.getDefaultState
  private final val Air = Blocks.AIR.getDefaultState

  val CODEC: MapCodec[DemoValleyChunkGenerator] = RecordCodecBuilder.mapCodec { instance =>
    instance
      .group(
        Biome.REGISTRY_CODEC.fieldOf("biome").forGetter((g: DemoValleyChunkGenerator) => g.biomeEntry),
        Identifier.CODEC
          .optionalFieldOf("heightmap", DefaultHeightmapId)
          .forGetter((g: DemoValleyChunkGenerator) => g.heightmapId)
      )
      .apply(instance, (b: RegistryEntry[Biome], h: Identifier) => new DemoValleyChunkGenerator(b, h))
  }

  private def loadHeights(id: Identifier): Array[Int] =
    val resourcePath = s"/data/${id.getNamespace}/${id.getPath}.png"
    val stream = Option(getClass.getResourceAsStream(resourcePath)).getOrElse {
      throw IllegalStateException(s"Missing heightmap resource: $resourcePath")
    }
    val image =
      try ImageIO.read(stream)
      finally stream.close()
    require(
      image != null && image.getWidth == Size && image.getHeight == Size,
      s"Heightmap $resourcePath must be ${Size}x$Size, got: $image"
    )
    val heights = new Array[Int](Size * Size)
    val raster = image.getRaster
    for pz <- 0 until Size do
      for px <- 0 until Size do
        // NB: getRGB on TYPE_BYTE_GRAY applies a gray->sRGB color conversion
        // (48 reads back as 120); raw sample access is the actual pixel value.
        val gray = raster.getSample(px, pz, 0)
        heights(pz * Size + px) = BaseHeight + (gray * (TopHeight - BaseHeight) + 127) / 255
    heights
