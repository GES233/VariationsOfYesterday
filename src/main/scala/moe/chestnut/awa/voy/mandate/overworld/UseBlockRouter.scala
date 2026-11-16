package moe.chestnut.awa.voy.mandate.overworld

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.{ItemStack, Items}
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{ActionResult, Hand}
import net.minecraft.world.World

import moe.chestnut.awa.voy.mandate.{MandateRuntime, MandateWording, VoyDebug}

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
    inspect(player, held, chunkX, chunkZ)
      .orElse(FieldInoculation.timber(player, world, pos, state, held, chunkX, chunkZ))
      .orElse(FieldInoculation.soil(player, world, pos, state, held, chunkX, chunkZ))
      .orElse(BellStrike.handle(player, world, pos, state, chunkX, chunkZ))
      .getOrElse(ActionResult.PASS)

  /** Diegetic readout: right-clicking a block while holding a Clock or a
    * Compass reads out the chunk's `Regime`, its `PhaseReading` (with the
    * 20-division mountain dial tick) and shelter status. The held item is the
    * instrument; nothing is consumed and the world is not touched.
    *
    * Folded in from the former `Inspection` object in the S2 subtraction pass:
    * it was one `if` that existed only as a branch of this chain.
    */
  private def inspect(
      player: PlayerEntity,
      held: ItemStack,
      chunkX: Int,
      chunkZ: Int
  ): Option[ActionResult] =
    if held.isOf(Items.CLOCK) || held.isOf(Items.COMPASS) then
      val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
      player.sendMessage(
        Text.literal(MandateWording.formatInspection(chunkX, chunkZ, readout, VoyDebug.numbers)),
        true
      )
      Some(ActionResult.PASS)
    else None
end UseBlockRouter
