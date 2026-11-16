package moe.chestnut.awa.voy.blocks

import net.minecraft.block.{Block, BlockState}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict
import moe.chestnut.awa.voy.inner.veins.CellReadout
import moe.chestnut.awa.voy.mandate.{MandatePhenomena, MandateWording}

import BedCompat.{hasCompanionTimber, isHarvestKnife, verdictToCondition}
import GastrodiaBedBlock.{AGE, CONDITION, ConditionWithered, MaxAge, advanceEcologicalTick}

/** The four right-click action bodies of `菌塘` (mycelial bed), lifted out of
  * `GastrodiaBedBlock.onUseWithItem` verbatim: thresholds, item spends, drops,
  * particles/sounds and player-facing wording are unchanged.
  *
  * `GastrodiaBedBlock.onUseWithItem` owns the `tendsBed` decision and the
  * server/overworld guards; each method here assumes it has already been
  * selected and returns the `ActionResult` the handler should return.
  */
object BedActions:
  /** ① Withered bed: refurbish with Inoculated Timber or fungal inoculum. */
  def refurbishWitheredBed(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      player: PlayerEntity,
      stack: ItemStack,
      readout: CellReadout
  ): ActionResult =
    if MandatePhenomena.canRefurbishGastrodiaBed(readout) then
      val liveVerdict = MandatePhenomena.evaluateGastrodiaBed(readout)
      val restoredCond = verdictToCondition(liveVerdict)
      world.setBlockState(
        pos,
        state
          .`with`(AGE, Integer.valueOf(0))
          .`with`(CONDITION, Integer.valueOf(restoredCond)),
        Block.NOTIFY_LISTENERS
      )
      if !player.isCreative then stack.decrement(1)
      BedFx.myceliumDustRestore(world, pos)
      BedFx.playRootedDirtPlace(world, pos)
      player.sendMessage(
        Text.literal(MandateWording.formatGastrodiaRefurbish(success = true, readout)),
        true
      )
      ActionResult.SUCCESS
    else
      BedFx.whiteAshRefuse(world, pos)
      player.sendMessage(
        Text.literal(MandateWording.formatGastrodiaRefurbish(success = false, readout)),
        true
      )
      ActionResult.SUCCESS

  /** ② Mature non-withered bed: harvest Fresh Glowroot tubers and re-seed. */
  def harvestMatureBed(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      player: PlayerEntity,
      stack: ItemStack,
      readout: CellReadout
  ): ActionResult =
    val companion = hasCompanionTimber(world, pos)
    val knifeBonus = if isHarvestKnife(stack) then 1 else 0
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
    BedFx.myceliumDustHarvest(world, pos)
    BedFx.playRootedDirtBreak(world, pos)
    player.sendMessage(
      Text.literal(MandateWording.formatGastrodiaHarvest(count, companion)),
      true
    )
    ActionResult.SUCCESS

  /** ③ Feeding a live bed with fungal nutrients (mushrooms / glow lichen /
    * bone meal) attempts an ecological growth step that still obeys the
    * chunk's `s < 0.45` and `M >= 0.60` symbiosis gate.
    *
    * `ActionResult.PASS` means the gate refused the boost: the caller falls
    * through to ④ exactly where the original inline branch did, without
    * consuming the item.
    */
  def feedNutrient(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      player: PlayerEntity,
      stack: ItemStack,
      readout: CellReadout
  ): ActionResult =
    val verdict = MandatePhenomena.evaluateGastrodiaBed(readout)
    if verdict == GastrodiaBedVerdict.Growing then
      if !player.isCreative then stack.decrement(1)
      advanceEcologicalTick(state, world, pos, world.random, playerBoosted = true)
      val updatedAge = world.getBlockState(pos).get(AGE).intValue()
      val companion = hasCompanionTimber(world, pos)
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
      ActionResult.SUCCESS
    else ActionResult.PASS

  /** ④ Default interaction: sync visual condition and inspect the bed.
    *
    * Inspection is item-agnostic; `stack` is accepted only to keep the four
    * action bodies on one call shape.
    */
  def inspectAndSyncBed(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      player: PlayerEntity,
      stack: ItemStack,
      readout: CellReadout
  ): ActionResult =
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
    ActionResult.SUCCESS
