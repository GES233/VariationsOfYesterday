package moe.chestnut.awa.voy.blocks

import net.minecraft.block.{AbstractBlock, Block, BlockState}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.{ItemPlacementContext, ItemStack, Items}
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.IntProperty
import net.minecraft.util.{ActionResult, Hand}
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import net.minecraft.world.World

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict
import moe.chestnut.awa.voy.mandate.{MandatePhenomena, MandateRuntime}

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
  *  - `condition = 1` (`Stalled`): `s >= 0.45`, `M < 0.60`, or the slow recovery
  *    offset collapsed below the knee (`isSlowOffsetBelowKnee`); threads dry out
  *    and growth pauses, then resumes once the cause clears;
  *  - `condition = 2` (`Withered`): ONLY when the mycelial network falls below
  *    the Allee floor (`SustenanceTier.Dead`); symbiotic cords rot black and stay
  *    withered until refurbished with `菌材` or fungal inoculum once `M` is live
  *    enough and the soil is sub-toxic. Neither an overdrive guard nor an
  *    unreadable phase latches a bed (2026-11-12).
  *
  * The right-click bodies live in [[BedActions]], the soft-compat vocabularies
  * in [[BedCompat]] and the particle/sound cues in [[BedFx]]; this file keeps
  * only the block lifecycle and the click-dispatch decision.
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
    GastrodiaBedBlock.advanceEcologicalTick(state, world, pos, random, playerBoosted = false)

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
        (currentCond != ConditionWithered && currentAge < MaxAge &&
          BedCompat.isNutrientMaterial(stack)) ||
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
      return BedActions.refurbishWitheredBed(state, serverWorld, pos, player, stack, readout)

    // 2. Mature non-withered bed: harvest Fresh Glowroot tubers and re-seed.
    //    Sneaking always means "don't disturb it" (inspect only), matching the
    //    vanilla convention players use when they want to build around a block.
    if currentCond != ConditionWithered && currentAge >= MaxAge && !player.isSneaking then
      return BedActions.harvestMatureBed(state, serverWorld, pos, player, stack, readout)

    // 3. Feeding a live bed with fungal nutrients (mushrooms / glow lichen /
    //    bone meal) attempts an ecological growth step that still obeys the
    //    chunk's s < 0.45 and M >= 0.60 symbiosis gate. `PASS` means that gate
    //    refused the boost, so the click falls through to inspection below.
    if currentCond != ConditionWithered && currentAge < MaxAge && BedCompat.isNutrientMaterial(stack) then
      val fed = BedActions.feedNutrient(state, serverWorld, pos, player, stack, readout)
      if fed != ActionResult.PASS then return fed

    // 4. Default interaction: sync visual condition and inspect the bed.
    BedActions.inspectAndSyncBed(state, serverWorld, pos, player, stack, readout)

object GastrodiaBedBlock:
  val MaxAge: Int = 3
  val ConditionHealthy: Int = 0
  val ConditionStalled: Int = 1
  val ConditionWithered: Int = 2

  val AGE: IntProperty = IntProperty.of("age", 0, MaxAge)
  val CONDITION: IntProperty = IntProperty.of("condition", 0, 2)

  // The companion's public vocabulary lives in BedCompat; keep it reachable as
  // `GastrodiaBedBlock.xxx` because `OverworldVocabulary` and `MandateOverworld`
  // call `GastrodiaBedBlock.isCompatRichSoil(state)` from outside this package.
  export BedCompat.verdictToCondition
  export BedCompat.hasCompanionTimber
  export BedCompat.isCompatRichSoil
  export BedCompat.isHarvestKnife
  export BedCompat.isRefurbishMaterial

  /** Advance one ecological tick of the bed (`randomTick`, or the nutrient
    * boost from ③). The block holds no state of its own, so this is a pure
    * projection of the chunk readout onto the `AGE` / `CONDITION` properties.
    */
  private[blocks] def advanceEcologicalTick(
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
          BedFx.whiteAshWither(world, pos)

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
          BedFx.sporeBloom(world, pos)
          if playerBoosted then BedFx.playBoneMealUse(world, pos)
