package moe.chestnut.awa.voy.mandate.overworld

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.{ItemStack, Items}
import net.minecraft.text.Text
import net.minecraft.util.ActionResult

import moe.chestnut.awa.voy.mandate.{MandateRuntime, MandateWording}

/** Diegetic readout: right-clicking a block while holding a Clock or a
  * Compass reads out the chunk's `Regime`, its `PhaseReading` (with the
  * 20-division mountain dial tick) and shelter status. The held item is the
  * instrument; nothing is consumed and the world is not touched.
  */
object Inspection:
  def handle(
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
end Inspection
