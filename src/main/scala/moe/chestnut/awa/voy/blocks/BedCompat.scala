package moe.chestnut.awa.voy.blocks

import net.minecraft.block.BlockState
import net.minecraft.item.{Item, ItemStack, Items}
import net.minecraft.registry.{Registries, RegistryKeys}
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.world.World

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict

/** Soft-compat hooks and material vocabularies for `菌塘` (mycelial bed, design
  * note 06 §4.3), split out of the `GastrodiaBedBlock` companion so the block
  * file carries only the block lifecycle.
  *
  * Nothing here imports another mod: the Farmer's Delight rich-soil and knife
  * branches are tag/id driven and simply never match when those tags are
  * absent, which is the zero-dependency soft-compat contract note 06 §4.3 asks
  * for. Public entry points stay re-exported by `GastrodiaBedBlock` for callers
  * such as `OverworldVocabulary`.
  */
object BedCompat:
  /** Horizontal neighbours plus the block directly underneath (段木伴栽). */
  private val CompanionDirections: Array[Direction] = Array(
    Direction.NORTH,
    Direction.EAST,
    Direction.SOUTH,
    Direction.WEST,
    Direction.DOWN
  )

  def verdictToCondition(verdict: GastrodiaBedVerdict): Int = verdict match
    case GastrodiaBedVerdict.Growing => GastrodiaBedBlock.ConditionHealthy
    case GastrodiaBedVerdict.Stalled => GastrodiaBedBlock.ConditionStalled
    case GastrodiaBedVerdict.Withered => GastrodiaBedBlock.ConditionWithered

  /** Optional zero-dependency Farmer's Delight / Common tags soft-compat
    * hooks (design note 06 §4.3):
    *
    *  - `farmersdelight:rich_soil` / `rich_soil_farmland` directly underneath
    *    acts as a companion substrate alongside `inoculated_timber`;
    *  - Harvesting with a knife (`#c:tools/knife` / `#farmersdelight:tools/knives`)
    *    avoids disturbing the lower rhizomorph mat and yields +1 tuber.
    */
  private val FdRichSoilId: Identifier = Identifier.of("farmersdelight", "rich_soil")
  private val FdRichSoilFarmlandId: Identifier = Identifier.of("farmersdelight", "rich_soil_farmland")
  private val CommonKnifeTag: TagKey[Item] =
    TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "tools/knife"))
  private val CommonKnivesTag: TagKey[Item] =
    TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "tools/knives"))
  private val FdKnivesTag: TagKey[Item] =
    TagKey.of(RegistryKeys.ITEM, Identifier.of("farmersdelight", "tools/knives"))

  /** Whether an `inoculated_timber` (`菌材`) block borders this bed
    * horizontally or directly underneath (段木伴栽), or a Farmer's Delight
    * rich-soil block lies directly beneath it.
    */
  def hasCompanionTimber(world: World, pos: BlockPos): Boolean =
    var i = 0
    while i < CompanionDirections.length do
      val dir = CompanionDirections(i)
      val neighbor = world.getBlockState(pos.offset(dir))
      if neighbor.isOf(VoyBlocks.InoculatedTimber) then return true
      if dir == Direction.DOWN && isCompatRichSoil(neighbor) then return true
      i += 1
    false

  def isCompatRichSoil(state: BlockState): Boolean =
    val id = Registries.BLOCK.getId(state.getBlock)
    id == FdRichSoilId || id == FdRichSoilFarmlandId

  def isHarvestKnife(stack: ItemStack): Boolean =
    stack.isIn(CommonKnifeTag) || stack.isIn(CommonKnivesTag) || stack.isIn(FdKnivesTag)

  def isRefurbishMaterial(stack: ItemStack): Boolean =
    stack.isOf(VoyBlocks.InoculatedTimberItem) ||
      stack.isOf(Items.GLOW_LICHEN) ||
      stack.isOf(Items.BROWN_MUSHROOM) ||
      stack.isOf(Items.RED_MUSHROOM) ||
      stack.isOf(Items.MYCELIUM)

  def isNutrientMaterial(stack: ItemStack): Boolean =
    stack.isOf(Items.BONE_MEAL) ||
      stack.isOf(Items.GLOW_LICHEN) ||
      stack.isOf(Items.BROWN_MUSHROOM) ||
      stack.isOf(Items.RED_MUSHROOM)
