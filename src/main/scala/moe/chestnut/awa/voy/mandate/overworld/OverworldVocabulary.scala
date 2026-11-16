package moe.chestnut.awa.voy.mandate.overworld

import net.minecraft.block.{BlockState, Blocks}
import net.minecraft.item.{ItemStack, Items}
import net.minecraft.registry.tag.BlockTags

import moe.chestnut.awa.voy.blocks.{GastrodiaBedBlock, VoyBlocks}

/** The world-side vocabulary the Mandate's inputs are expressed in: how much
  * land stress a block break is worth, and which held item can mean `接种` /
  * `再接种` (design draft §1.1, §1.2; design note 06 §7.1).
  *
  * Pure classification: no engine state, no side effects, nothing but the
  * block state / stack it is handed.
  */
object OverworldVocabulary:
  /** Compute the stress injection dose for breaking a block in the Overworld. */
  def stressDoseForBlock(state: BlockState): Double =
    if state.isOf(Blocks.TNT) || state.isOf(Blocks.ANCIENT_DEBRIS) || state.isOf(Blocks.BUDDING_AMETHYST) then
      0.06
    else if isOreBlock(state) then
      0.04
    else if state.isIn(BlockTags.BASE_STONE_OVERWORLD) then
      0.01
    else
      0.0

  private def isOreBlock(state: BlockState): Boolean =
    state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE) ||
      state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE) ||
      state.isOf(Blocks.COPPER_ORE) || state.isOf(Blocks.DEEPSLATE_COPPER_ORE) ||
      state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE) ||
      state.isOf(Blocks.REDSTONE_ORE) || state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE) ||
      state.isOf(Blocks.LAPIS_ORE) || state.isOf(Blocks.DEEPSLATE_LAPIS_ORE) ||
      state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE) ||
      state.isOf(Blocks.EMERALD_ORE) || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE)

  /** Wild fungal inoculum items that seed baseline mycelial sustenance (`M = 0.05`)
    * or colonize stripped timber in the field (design note 06 §7.1).
    */
  private[overworld] def isWildInoculum(stack: ItemStack): Boolean =
    stack.isOf(Items.GLOW_LICHEN) || stack.isOf(Items.BROWN_MUSHROOM) ||
      stack.isOf(Items.RED_MUSHROOM) || stack.isOf(Items.MYCELIUM)

  /** Fungal inoculum items that can seed mycelial sustenance (`M`) into a
    * depleted chunk (design note 06 §7.1, narrative "re-inoculation").
    */
  private[overworld] def isInoculum(stack: ItemStack): Boolean =
    isWildInoculum(stack) || stack.isOf(VoyBlocks.InoculatedTimberItem)

  /** Debarked timber blocks that can be colonized in-situ into `菌材`
    * (`VoyBlocks.InoculatedTimber`) when the chunk's mycelial network is intact.
    */
  private[overworld] def isStrippedTimber(state: BlockState): Boolean =
    state.isOf(Blocks.STRIPPED_OAK_LOG) || state.isOf(Blocks.STRIPPED_SPRUCE_LOG) ||
      state.isOf(Blocks.STRIPPED_BIRCH_LOG) || state.isOf(Blocks.STRIPPED_JUNGLE_LOG) ||
      state.isOf(Blocks.STRIPPED_ACACIA_LOG) || state.isOf(Blocks.STRIPPED_DARK_OAK_LOG) ||
      state.isOf(Blocks.STRIPPED_MANGROVE_LOG) || state.isOf(Blocks.STRIPPED_CHERRY_LOG)

  /** Ground blocks that accept inoculum (the soil/wetland continuity the
    * mycelium lives in, note 06 §2.1, plus optional Farmer's Delight rich soil).
    */
  private[overworld] def isInoculableSoil(state: BlockState): Boolean =
    state.isIn(BlockTags.DIRT) || state.isOf(Blocks.FARMLAND) ||
      state.isOf(Blocks.MUD) || state.isOf(Blocks.MOSS_BLOCK) ||
      GastrodiaBedBlock.isCompatRichSoil(state)
end OverworldVocabulary
