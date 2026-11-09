package moe.chestnut.awa.voy.blocks

import net.minecraft.block.{AbstractBlock, Block, BlockState, Blocks, PillarBlock}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.{ItemPlacementContext, ItemStack, Items}
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.{Registries, RegistryKeys}
import net.minecraft.registry.tag.{BlockTags, TagKey}
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.state.StateManager
import net.minecraft.state.property.IntProperty
import net.minecraft.text.Text
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.util.math.random.Random
import net.minecraft.util.{ActionResult, Hand, Identifier}
import net.minecraft.world.World

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict
import moe.chestnut.awa.voy.mandate.{MandateEconomy, MandatePhenomena, MandateRuntime, MandateWording}

/** `菌塘` (mycelial bed, design note 06 §4.3 & WO-3).
  *
  * The tuber the bed holds has neither roots nor green leaves; it grows only
  * while fed by an intact underground glowing mycelial network (`M`, the
  * fictional `荧丝`). This block holds no dynamics of its own — every tick and
  * inspection is a stateless projection of the chunk's
  * [[moe.chestnut.awa.voy.inner.veins.CellReadout]]
  * via [[MandatePhenomena.evaluateGastrodiaBed]]:
  *
  *  - `condition = 0` (`Growing`): `s < 0.45` and `M >= 0.60`; advances `age`
  *    from `0` to `3` (`MaxAge`), faster and with higher yield when a
  *    companion `菌材` (`inoculated_timber`) block touches the bed;
  *  - `condition = 1` (`Stalled`): `s >= 0.45` or `M < 0.60`; threads dry out
  *    and growth pauses;
  *  - `condition = 2` (`Withered`): `BurnoutNoCycle` or `M < floor`; symbiotic
  *    cords rot black and stay withered until refurbished with `菌材` or
  *    fungal inoculum after the ground recovers.
  */
final class GastrodiaBedBlock(settings: AbstractBlock.Settings) extends Block(settings):
  import GastrodiaBedBlock.*

  this.setDefaultState(
    this.stateManager.getDefaultState
      .`with`(AGE, Integer.valueOf(0))
      .`with`(CONDITION, Integer.valueOf(ConditionHealthy))
  )

  override protected def appendProperties(builder: StateManager.Builder[Block, BlockState]): Unit =
    builder.add(AGE, CONDITION)

  override def getPlacementState(ctx: ItemPlacementContext): BlockState =
    val world = ctx.getWorld
    val pos = ctx.getBlockPos
    val cond =
      if !world.isClient && world.getRegistryKey == World.OVERWORLD then
        val readout = MandateRuntime.readoutAt(pos.getX >> 4, pos.getZ >> 4)
        verdictToCondition(MandatePhenomena.evaluateGastrodiaBed(readout))
      else ConditionHealthy
    this.getDefaultState
      .`with`(AGE, Integer.valueOf(0))
      .`with`(CONDITION, Integer.valueOf(cond))

  override protected def hasRandomTicks(state: BlockState): Boolean = true

  override protected def randomTick(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      random: Random
  ): Unit =
    if world.getRegistryKey != World.OVERWORLD then return
    advanceEcologicalTick(state, world, pos, random, playerBoosted = false)

  override protected def onUseWithItem(
      stack: ItemStack,
      state: BlockState,
      world: World,
      pos: BlockPos,
      player: PlayerEntity,
      hand: Hand,
      hit: BlockHitResult
  ): ActionResult =
    // Let Clock / Compass pass through to MandateOverworld's diegetic readout.
    if stack.isOf(Items.CLOCK) || stack.isOf(Items.COMPASS) then
      return ActionResult.PASS

    val currentCond = state.get(CONDITION).intValue()
    val currentAge = state.get(AGE).intValue()
    val emptyHand = stack.isEmpty

    // Only take over the clicks this bed actually answers. Everything else
    // keeps vanilla behaviour, so a player can still build against (or on top
    // of) the bed and a stray right-click with a block never harvests a ripe
    // bed by accident.
    val tendsBed =
      (currentCond == ConditionWithered && isRefurbishMaterial(stack)) ||
        (currentCond != ConditionWithered && currentAge < MaxAge && isNutrientMaterial(stack)) ||
        (currentCond != ConditionWithered && currentAge >= MaxAge &&
          (emptyHand || isHarvestKnife(stack))) ||
        emptyHand ||
        player.isSneaking
    if !tendsBed then return ActionResult.PASS

    if world.isClient then return ActionResult.SUCCESS
    if world.getRegistryKey != World.OVERWORLD then return ActionResult.PASS

    val serverWorld = world.asInstanceOf[ServerWorld]
    val chunkX = pos.getX >> 4
    val chunkZ = pos.getZ >> 4
    val readout = MandateRuntime.readoutAt(chunkX, chunkZ)

    // 1. Withered bed: refurbish with Inoculated Timber or fungal inoculum.
    if currentCond == ConditionWithered && isRefurbishMaterial(stack) then
      if MandatePhenomena.canRefurbishGastrodiaBed(readout) then
        val liveVerdict = MandatePhenomena.evaluateGastrodiaBed(readout)
        val restoredCond = verdictToCondition(liveVerdict)
        serverWorld.setBlockState(
          pos,
          state
            .`with`(AGE, Integer.valueOf(0))
            .`with`(CONDITION, Integer.valueOf(restoredCond)),
          Block.NOTIFY_LISTENERS
        )
        if !player.isCreative then stack.decrement(1)
        serverWorld.spawnParticles(
          ParticleTypes.MYCELIUM,
          pos.getX + 0.5,
          pos.getY + 1.05,
          pos.getZ + 0.5,
          14,
          0.35,
          0.15,
          0.35,
          0.01
        )
        serverWorld.playSound(
          null,
          pos,
          SoundEvents.BLOCK_ROOTED_DIRT_PLACE,
          SoundCategory.BLOCKS,
          0.9f,
          1.0f
        )
        player.sendMessage(
          Text.literal(MandateWording.formatGastrodiaRefurbish(success = true, readout)),
          true
        )
        return ActionResult.SUCCESS
      else
        serverWorld.spawnParticles(
          ParticleTypes.WHITE_ASH,
          pos.getX + 0.5,
          pos.getY + 1.05,
          pos.getZ + 0.5,
          8,
          0.3,
          0.15,
          0.3,
          0.01
        )
        player.sendMessage(
          Text.literal(MandateWording.formatGastrodiaRefurbish(success = false, readout)),
          true
        )
        return ActionResult.SUCCESS

    // 2. Mature non-withered bed: harvest Fresh Glowroot tubers and re-seed.
    //    Sneaking always means "don't disturb it" (inspect only), matching the
    //    vanilla convention players use when they want to build around a block.
    if currentCond != ConditionWithered && currentAge >= MaxAge && !player.isSneaking then
      harvestMatureBed(state, serverWorld, pos, player, stack, readout)
      return ActionResult.SUCCESS

    // 3. Feeding a live bed with fungal nutrients (mushrooms / glow lichen /
    //    bone meal) attempts an ecological growth step that still obeys the
    //    chunk's s < 0.45 and M >= 0.60 symbiosis gate.
    if currentCond != ConditionWithered && currentAge < MaxAge && isNutrientMaterial(stack) then
      val verdict = MandatePhenomena.evaluateGastrodiaBed(readout)
      if verdict == GastrodiaBedVerdict.Growing then
        if !player.isCreative then stack.decrement(1)
        advanceEcologicalTick(state, serverWorld, pos, serverWorld.random, playerBoosted = true)
        val updatedAge = serverWorld.getBlockState(pos).get(AGE).intValue()
        val companion = hasCompanionTimber(serverWorld, pos)
        player.sendMessage(
          Text.literal(
            MandateWording.formatGastrodiaInspection(
              updatedAge,
              MaxAge,
              verdict,
              companion,
              readout
            )
          ),
          true
        )
        return ActionResult.SUCCESS

    // 4. Default interaction: sync visual condition and inspect the bed.
    inspectAndSyncBed(state, serverWorld, pos, player, readout)
    ActionResult.SUCCESS

  private def advanceEcologicalTick(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      random: Random,
      playerBoosted: Boolean
  ): Unit =
    val chunkX = pos.getX >> 4
    val chunkZ = pos.getZ >> 4
    val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
    val verdict = MandatePhenomena.evaluateGastrodiaBed(readout)
    val currentCond = state.get(CONDITION).intValue()
    val currentAge = state.get(AGE).intValue()

    verdict match
      case GastrodiaBedVerdict.Withered =>
        if currentCond != ConditionWithered then
          world.setBlockState(
            pos,
            state.`with`(CONDITION, Integer.valueOf(ConditionWithered)),
            Block.NOTIFY_LISTENERS
          )
          world.spawnParticles(
            ParticleTypes.WHITE_ASH,
            pos.getX + 0.5,
            pos.getY + 1.05,
            pos.getZ + 0.5,
            6,
            0.35,
            0.15,
            0.35,
            0.0
          )

      case GastrodiaBedVerdict.Stalled =>
        // Once withered, a bed stays withered until manually refurbished.
        if currentCond != ConditionWithered && currentCond != ConditionStalled then
          world.setBlockState(
            pos,
            state.`with`(CONDITION, Integer.valueOf(ConditionStalled)),
            Block.NOTIFY_LISTENERS
          )

      case GastrodiaBedVerdict.Growing =>
        if currentCond == ConditionWithered then return
        if !playerBoosted && MandateEconomy.suppressCropGrowth(chunkX, chunkZ) then
          if currentCond != ConditionHealthy then
            world.setBlockState(
              pos,
              state.`with`(CONDITION, Integer.valueOf(ConditionHealthy)),
              Block.NOTIFY_LISTENERS
            )
          return

        val companion = hasCompanionTimber(world, pos)
        val shouldAdvance =
          currentAge < MaxAge &&
            (playerBoosted || (if companion then random.nextInt(2) == 0 else random.nextInt(4) == 0))

        val nextAge = if shouldAdvance then Math.min(MaxAge, currentAge + 1) else currentAge
        if nextAge != currentAge || currentCond != ConditionHealthy then
          world.setBlockState(
            pos,
            state
              .`with`(AGE, Integer.valueOf(nextAge))
              .`with`(CONDITION, Integer.valueOf(ConditionHealthy)),
            Block.NOTIFY_LISTENERS
          )
        if shouldAdvance then
          world.spawnParticles(
            ParticleTypes.SPORE_BLOSSOM_AIR,
            pos.getX + 0.5,
            pos.getY + 1.05,
            pos.getZ + 0.5,
            6,
            0.3,
            0.15,
            0.3,
            0.01
          )
          if playerBoosted then
            world.playSound(
              null,
              pos,
              SoundEvents.ITEM_BONE_MEAL_USE,
              SoundCategory.BLOCKS,
              0.7f,
              1.15f
            )

  private def harvestMatureBed(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      player: PlayerEntity,
      heldStack: ItemStack,
      readout: moe.chestnut.awa.voy.inner.veins.CellReadout
  ): Unit =
    val companion = hasCompanionTimber(world, pos)
    val knifeBonus = if isHarvestKnife(heldStack) then 1 else 0
    val count =
      (if companion then 3 + world.random.nextInt(2)
       else 1 + world.random.nextInt(2)) + knifeBonus
    Block.dropStack(world, pos.up(), new ItemStack(VoyBlocks.FreshGastrodiaItem, count))

    val nextCond = verdictToCondition(MandatePhenomena.evaluateGastrodiaBed(readout))
    world.setBlockState(
      pos,
      state
        .`with`(AGE, Integer.valueOf(0))
        .`with`(CONDITION, Integer.valueOf(nextCond)),
      Block.NOTIFY_LISTENERS
    )
    world.spawnParticles(
      ParticleTypes.MYCELIUM,
      pos.getX + 0.5,
      pos.getY + 1.05,
      pos.getZ + 0.5,
      12,
      0.35,
      0.2,
      0.35,
      0.01
    )
    world.playSound(
      null,
      pos,
      SoundEvents.BLOCK_ROOTED_DIRT_BREAK,
      SoundCategory.BLOCKS,
      0.9f,
      1.05f
    )
    player.sendMessage(
      Text.literal(MandateWording.formatGastrodiaHarvest(count, companion)),
      true
    )

  private def inspectAndSyncBed(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      player: PlayerEntity,
      readout: moe.chestnut.awa.voy.inner.veins.CellReadout
  ): Unit =
    val currentCond = state.get(CONDITION).intValue()
    val currentAge = state.get(AGE).intValue()
    val liveVerdict =
      if currentCond == ConditionWithered then GastrodiaBedVerdict.Withered
      else MandatePhenomena.evaluateGastrodiaBed(readout)
    val syncedCond = verdictToCondition(liveVerdict)
    if syncedCond != currentCond then
      world.setBlockState(
        pos,
        state.`with`(CONDITION, Integer.valueOf(syncedCond)),
        Block.NOTIFY_LISTENERS
      )
    val companion = hasCompanionTimber(world, pos)
    player.sendMessage(
      Text.literal(
        MandateWording.formatGastrodiaInspection(
          currentAge,
          MaxAge,
          liveVerdict,
          companion,
          readout
        )
      ),
      true
    )

object GastrodiaBedBlock:
  val MaxAge: Int = 3
  val ConditionHealthy: Int = 0
  val ConditionStalled: Int = 1
  val ConditionWithered: Int = 2

  val AGE: IntProperty = IntProperty.of("age", 0, MaxAge)
  val CONDITION: IntProperty = IntProperty.of("condition", 0, 2)

  private val CompanionDirections: Array[Direction] = Array(
    Direction.NORTH,
    Direction.EAST,
    Direction.SOUTH,
    Direction.WEST,
    Direction.DOWN
  )

  def verdictToCondition(verdict: GastrodiaBedVerdict): Int = verdict match
    case GastrodiaBedVerdict.Growing => ConditionHealthy
    case GastrodiaBedVerdict.Stalled => ConditionStalled
    case GastrodiaBedVerdict.Withered => ConditionWithered

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
  private val CommonKnifeTag: TagKey[net.minecraft.item.Item] =
    TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "tools/knife"))
  private val CommonKnivesTag: TagKey[net.minecraft.item.Item] =
    TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "tools/knives"))
  private val FdKnivesTag: TagKey[net.minecraft.item.Item] =
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

  private def isNutrientMaterial(stack: ItemStack): Boolean =
    stack.isOf(Items.BONE_MEAL) ||
      stack.isOf(Items.GLOW_LICHEN) ||
      stack.isOf(Items.BROWN_MUSHROOM) ||
      stack.isOf(Items.RED_MUSHROOM)
