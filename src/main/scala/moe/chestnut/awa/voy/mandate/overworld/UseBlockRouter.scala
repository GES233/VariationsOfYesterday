package moe.chestnut.awa.voy.mandate.overworld

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{ActionResult, Hand}
import net.minecraft.world.World

/** Ordered dispatch for right-click gestures (design draft §1.2, §3.2).
  *
  * The order is the semantics: an instrument readout answers first, then the
  * two inoculation gestures, then the bell. Each handler declines with `None`
  * when the gesture is not its business — including the handlers that answer
  * with `PASS`, which still claim the click so that a later branch never
  * steals it (the inoculum branches deliberately defer to vanilla placement
  * without letting the bell branch see the event).
  */
object UseBlockRouter:
  def register(): Unit =
    UseBlockCallback.EVENT.register { (player, world, hand, hitResult) =>
      if world.isClient || hand != Hand.MAIN_HAND || world.getRegistryKey != World.OVERWORLD then
        ActionResult.PASS
      else
        val pos = hitResult.getBlockPos
        route(
          player,
          world.asInstanceOf[ServerWorld],
          pos,
          world.getBlockState(pos),
          player.getStackInHand(hand),
          pos.getX >> 4,
          pos.getZ >> 4
        )
    }

  private def route(
      player: PlayerEntity,
      world: ServerWorld,
      pos: BlockPos,
      state: BlockState,
      held: ItemStack,
      chunkX: Int,
      chunkZ: Int
  ): ActionResult =
    Inspection.handle(player, held, chunkX, chunkZ)
      .orElse(FieldInoculation.timber(player, world, pos, state, held, chunkX, chunkZ))
      .orElse(FieldInoculation.soil(player, world, pos, state, held, chunkX, chunkZ))
      .orElse(BellStrike.handle(player, world, pos, state, chunkX, chunkZ))
      .getOrElse(ActionResult.PASS)
end UseBlockRouter
