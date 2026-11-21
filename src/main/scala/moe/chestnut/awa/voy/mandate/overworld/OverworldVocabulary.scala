package moe.chestnut.awa.voy.mandate.overworld

import scala.jdk.OptionConverters.*

import net.minecraft.block.{BlockState, Blocks}
import net.minecraft.item.{ItemStack, Items}
import net.minecraft.particle.DustParticleEffect
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

import moe.chestnut.awa.voy.blocks.{GastrodiaBedBlock, VoyBlocks}
import moe.chestnut.awa.voy.inner.veins.EcologyKind

/** The world-side vocabulary the Mandate's inputs and sensory projections are
  * expressed in: how a chunk's biome maps to an [[EcologyKind]] adapter, how
  * much land stress a block break is worth, which held item can mean `接种` /
  * `再接种`, and which surface medium carries the 3-tier seep signature
  * (design draft §1.1, §1.2; design note 06 §7.1).
  *
  * Pure classification: no engine state, no side effects.
  */
object OverworldVocabulary:
  /** Seep dust palettes per ecological adapter:
    *  - Mountain: iron-ochre precipitate ("yellow boy", `0xc77e1f`);
    *  - Swamp: smoldering peat ash-umber fume (`0x5a4d41`);
    *  - Permafrost: dark thermokarst thaw-silt & methane mist (`0x3b4d54`);
    *  - Desert: wind-eroded saltation sand dust (`0xd6a85a`).
    */
  private val MountainSeepDust = new DustParticleEffect(0xc77e1f, 1.1f)
  private val SwampSeepDust = new DustParticleEffect(0x5a4d41, 1.2f)
  private val PermafrostSeepDust = new DustParticleEffect(0x3b4d54, 1.1f)
  private val DesertSeepDust = new DustParticleEffect(0xd6a85a, 1.15f)

  /** Resolve the ecological adapter at a loaded world position. */
  def ecologyAt(world: World, pos: BlockPos): EcologyKind =
    try
      world
        .getBiome(pos)
        .getKey
        .toScala
        .map(k => EcologyKind.fromBiomePath(k.getValue.getPath))
        .getOrElse(EcologyKind.Mountain)
    catch case _: Exception => EcologyKind.Mountain

  /** Resolve the ecological adapter at a chunk's centre without forcing chunk
    * generation or disk I/O (samples the generator's noise biome directly).
    */
  def ecologyAtChunk(world: ServerWorld, chunkX: Int, chunkZ: Int): EcologyKind =
    try
      val cm = world.getChunkManager
      val sampler = cm.getNoiseConfig.getMultiNoiseSampler
      val entry = cm.getChunkGenerator.getBiomeSource.getBiome(
        (chunkX << 2) + 2,
        64 >> 2,
        (chunkZ << 2) + 2,
        sampler
      )
      entry.getKey.toScala
        .map(k => EcologyKind.fromBiomePath(k.getValue.getPath))
        .getOrElse(EcologyKind.Mountain)
    catch case _: Exception => EcologyKind.Mountain

  /** Compute the stress injection dose for breaking a block in the Overworld
    * under the given ecological adapter.
    */
  def stressDoseForBlock(
      state: BlockState,
      ecology: EcologyKind = EcologyKind.Mountain
  ): Double =
    if state.isOf(Blocks.TNT) || state.isOf(Blocks.ANCIENT_DEBRIS) || state.isOf(Blocks.BUDDING_AMETHYST) then
      0.06
    else
      ecology match
        case EcologyKind.Swamp if isSwampPeatMatrix(state) =>
          0.03
        case EcologyKind.Permafrost if isPermafrostCoreIce(state) =>
          0.05
        case EcologyKind.Permafrost if isPermafrostSurfaceCover(state) =>
          0.025
        case EcologyKind.Desert if isDesertCrustAnchor(state) =>
          0.04
        case EcologyKind.Desert if isDesertCrustSubstrate(state) =>
          0.02
        case _ if isOreBlock(state) =>
          0.04
        case _ if state.isIn(BlockTags.BASE_STONE_OVERWORLD) =>
          0.01
        case _ =>
          0.0

  private def isSwampPeatMatrix(state: BlockState): Boolean =
    state.isOf(Blocks.MUD) || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS) ||
      state.isOf(Blocks.MANGROVE_ROOTS) || state.isOf(Blocks.CLAY) ||
      state.isOf(Blocks.MOSS_BLOCK)

  private def isPermafrostCoreIce(state: BlockState): Boolean =
    state.isOf(Blocks.BLUE_ICE) || state.isOf(Blocks.PACKED_ICE)

  private def isPermafrostSurfaceCover(state: BlockState): Boolean =
    state.isOf(Blocks.ICE) || state.isOf(Blocks.FROSTED_ICE) ||
      state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)

  private def isDesertCrustAnchor(state: BlockState): Boolean =
    state.isOf(Blocks.DEAD_BUSH) || state.isOf(Blocks.CACTUS) ||
      state.isOf(Blocks.MOSS_BLOCK) || state.isOf(Blocks.MOSS_CARPET) ||
      state.isOf(Blocks.AZALEA) || state.isOf(Blocks.FLOWERING_AZALEA)

  private def isDesertCrustSubstrate(state: BlockState): Boolean =
    state.isIn(BlockTags.SAND) || state.isOf(Blocks.SANDSTONE) ||
      state.isOf(Blocks.RED_SANDSTONE) || state.isIn(BlockTags.TERRACOTTA)

  private def isOreBlock(state: BlockState): Boolean =
    state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE) ||
      state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE) ||
      state.isOf(Blocks.COPPER_ORE) || state.isOf(Blocks.DEEPSLATE_COPPER_ORE) ||
      state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE) ||
      state.isOf(Blocks.REDSTONE_ORE) || state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE) ||
      state.isOf(Blocks.LAPIS_ORE) || state.isOf(Blocks.DEEPSLATE_LAPIS_ORE) ||
      state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE) ||
      state.isOf(Blocks.EMERALD_ORE) || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE)

  /** Wild fungal/pioneer inoculum items that seed baseline sustenance (`M = 0.05`)
    * or colonize stripped timber in the field (design note 06 §7.1).
    */
  private[overworld] def isWildInoculum(
      stack: ItemStack,
      ecology: EcologyKind = EcologyKind.Mountain
  ): Boolean =
    val base =
      stack.isOf(Items.GLOW_LICHEN) || stack.isOf(Items.BROWN_MUSHROOM) ||
        stack.isOf(Items.RED_MUSHROOM) || stack.isOf(Items.MYCELIUM)
    base || (ecology match
      case EcologyKind.Mountain => false
      case EcologyKind.Swamp =>
        stack.isOf(Items.MOSS_BLOCK) || stack.isOf(Items.MOSS_CARPET) ||
          stack.isOf(Items.MANGROVE_PROPAGULE) || stack.isOf(Items.VINE)
      case EcologyKind.Permafrost =>
        stack.isOf(Items.MOSS_CARPET) || stack.isOf(Items.SPORE_BLOSSOM) ||
          stack.isOf(Items.FERN)
      case EcologyKind.Desert =>
        stack.isOf(Items.MOSS_CARPET) || stack.isOf(Items.MOSS_BLOCK) ||
          stack.isOf(Items.HANGING_ROOTS))

  /** Inoculum items that can seed sustenance (`M`) into a depleted chunk
    * (design note 06 §7.1, narrative "re-inoculation").
    */
  private[overworld] def isInoculum(
      stack: ItemStack,
      ecology: EcologyKind = EcologyKind.Mountain
  ): Boolean =
    isWildInoculum(stack, ecology) || stack.isOf(VoyBlocks.InoculatedTimberItem)

  /** Debarked timber blocks that can be colonized in-situ into `菌材`
    * (`VoyBlocks.InoculatedTimber`) when the chunk's mycelial network is intact.
    */
  private[overworld] def isStrippedTimber(state: BlockState): Boolean =
    state.isOf(Blocks.STRIPPED_OAK_LOG) || state.isOf(Blocks.STRIPPED_SPRUCE_LOG) ||
      state.isOf(Blocks.STRIPPED_BIRCH_LOG) || state.isOf(Blocks.STRIPPED_JUNGLE_LOG) ||
      state.isOf(Blocks.STRIPPED_ACACIA_LOG) || state.isOf(Blocks.STRIPPED_DARK_OAK_LOG) ||
      state.isOf(Blocks.STRIPPED_MANGROVE_LOG) || state.isOf(Blocks.STRIPPED_CHERRY_LOG)

  /** Ground blocks that accept inoculum (the soil/wetland/permafrost/crust
    * continuity the living matrix inhabits, plus optional Farmer's Delight rich soil).
    */
  private[overworld] def isInoculableSoil(
      state: BlockState,
      ecology: EcologyKind = EcologyKind.Mountain
  ): Boolean =
    val base =
      state.isIn(BlockTags.DIRT) || state.isOf(Blocks.FARMLAND) ||
        state.isOf(Blocks.MUD) || state.isOf(Blocks.MOSS_BLOCK) ||
        GastrodiaBedBlock.isCompatRichSoil(state)
    base || (ecology match
      case EcologyKind.Mountain => false
      case EcologyKind.Swamp =>
        state.isOf(Blocks.CLAY) || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
      case EcologyKind.Permafrost =>
        state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW) ||
          state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.GRAVEL)
      case EcologyKind.Desert =>
        isDesertCrustSubstrate(state))

  /** Surface blocks that emit the 3-tier seep / fume / dust signature for `ecology`. */
  private[overworld] def isSeepCarrier(
      state: BlockState,
      ecology: EcologyKind = EcologyKind.Mountain
  ): Boolean =
    ecology match
      case EcologyKind.Mountain =>
        state.isOf(Blocks.WATER)
      case EcologyKind.Swamp =>
        state.isOf(Blocks.WATER) || state.isOf(Blocks.MUD) ||
          state.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
      case EcologyKind.Permafrost =>
        state.isOf(Blocks.WATER) || state.isOf(Blocks.ICE) ||
          state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.SNOW_BLOCK)
      case EcologyKind.Desert =>
        state.isOf(Blocks.WATER) || isDesertCrustSubstrate(state)

  /** Dust particle effect representing the 3-tier seep / fume / dust signature. */
  private[overworld] def seepDust(ecology: EcologyKind): DustParticleEffect =
    ecology match
      case EcologyKind.Mountain   => MountainSeepDust
      case EcologyKind.Swamp      => SwampSeepDust
      case EcologyKind.Permafrost => PermafrostSeepDust
      case EcologyKind.Desert     => DesertSeepDust
end OverworldVocabulary
