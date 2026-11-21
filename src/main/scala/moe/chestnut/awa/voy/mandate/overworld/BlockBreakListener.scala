package moe.chestnut.awa.voy.mandate.overworld

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.block.Blocks
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

import moe.chestnut.awa.voy.inner.veins.Regime
import moe.chestnut.awa.voy.mandate.{MandateCalming, MandateRuntime}

/** World -> Mandate, mining channel (design draft §1.1, §1.2): breaking stone
  * or ore injects land stress into the struck chunk, with immediate
  * sub-threshold feedback inside the active lattice.
  *
  * A broken bell is not an injection but a *removal*: it unregisters one
  * calming source and the chunk's `c0` stock then leaks away with its time
  * constant instead of snapping to zero (design note 11 ruling 11).
  */
object BlockBreakListener:
  def register(): Unit =
    PlayerBlockBreakEvents.AFTER.register { (world, player, pos, state, _) =>
      if !world.isClient && world.getRegistryKey == World.OVERWORLD then
        val chunkX = pos.getX >> 4
        val chunkZ = pos.getZ >> 4
        val serverWorld = world.asInstanceOf[ServerWorld]

        if state.isOf(Blocks.BELL) then
          onBellBroken(serverWorld, player, pos, chunkX, chunkZ)
        else
          val ecology = OverworldVocabulary.ecologyAt(serverWorld, pos)
          val dose = OverworldVocabulary.stressDoseForBlock(state, ecology)
          if dose > 0.0 && MandateRuntime.isLiveChunk(chunkX, chunkZ) then
            MandateRuntime.injectStressAt(chunkX, chunkZ, dose)
            val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
            // Immediate sub-threshold feedback (design draft §1.2, dual-channel feedback)
            if dose >= 0.04 || readout.regime != Regime.Calm then
              serverWorld.spawnParticles(
                ParticleTypes.ASH,
                pos.getX + 0.5,
                pos.getY + 0.5,
                pos.getZ + 0.5,
                6,
                0.25,
                0.25,
                0.25,
                0.01
              )
              serverWorld.playSound(
                null,
                pos,
                SoundEvents.BLOCK_DEEPSLATE_STEP,
                SoundCategory.AMBIENT,
                0.6f,
                0.5f
              )
    }

  /** One calming source disappears; the stock it fed keeps leaking (note 11
    * ruling 11), so the wording reports what remains rather than a verdict.
    */
  private def onBellBroken(
      serverWorld: ServerWorld,
      player: PlayerEntity,
      pos: BlockPos,
      chunkX: Int,
      chunkZ: Int
  ): Unit =
    MandateCalming.unregister(pos.getX, pos.getY, pos.getZ)
    MandateRuntime.injectStressAt(chunkX, chunkZ, 0.06)
    val remaining = MandateCalming.weightAt(chunkX, chunkZ)
    serverWorld.playSound(
      null,
      pos,
      SoundEvents.BLOCK_BELL_RESONATE,
      SoundCategory.BLOCKS,
      1.0f,
      0.5f
    )
    player.sendMessage(
      Text.literal(
        if remaining > 0.0 then
          f"§e[旧日变奏] 镇山钟损毁：区块 [$chunkX%d, $chunkZ%d] 尚有安抚源在维持涓流（余 ${remaining}%.0f 份），c0 将缓慢回落"
        else
          f"§c[旧日变奏] 镇山钟损毁：区块 [$chunkX%d, $chunkZ%d] 最后一座安抚源消失，c0 开始缓慢漏失"
      ),
      true
    )
end BlockBreakListener
