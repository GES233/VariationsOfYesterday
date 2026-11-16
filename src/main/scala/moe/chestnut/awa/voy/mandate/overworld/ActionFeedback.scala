package moe.chestnut.awa.voy.mandate.overworld

import scala.jdk.CollectionConverters.*

import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

import moe.chestnut.awa.voy.inner.action.{ActionMsgPool, MandateActionProtocol}
import moe.chestnut.awa.voy.inner.veins.Regime
import moe.chestnut.awa.voy.mandate.{MandateRuntime, MandateWording, VoyDebug}

/** Mandate -> Overworld, action replay channel (design draft §3.2): outbound
  * `CellSpikeTriggered` and `RegimeTransitioned` actions become audible
  * tremors and ash plumes on the server thread.
  *
  * The tremor itself is the player-facing signal. The raw readout used to be
  * printed twice per oscillation period and became the mod's loudest, least
  * useful surface, so it now lives behind [[VoyDebug]].
  */
object ActionFeedback:
  def handle(server: MinecraftServer, action: MandateActionProtocol): Unit =
    val overworld = server.getOverworld
    if overworld == null then return

    action match
      case ActionMsgPool.CellSpikeTriggered(cell, cur, regime) =>
        val (chunkX, chunkZ) = MandateRuntime.gridToWorld(cell)
        overworld.getPlayers.asScala.foreach { player =>
          val dx = Math.abs(player.getChunkPos.x - chunkX)
          val dz = Math.abs(player.getChunkPos.z - chunkZ)
          if dx <= 4 && dz <= 4 then
            overworld.playSound(
              null,
              player.getBlockPos,
              SoundEvents.AMBIENT_CAVE.value(),
              SoundCategory.AMBIENT,
              0.9f,
              0.6f
            )
            overworld.spawnParticles(
              ParticleTypes.CAMPFIRE_COSY_SMOKE,
              player.getX,
              player.getY + 0.2,
              player.getZ,
              10,
              1.2,
              0.2,
              1.2,
              0.01
            )
            if VoyDebug.numbers then
              player.sendMessage(
                Text.literal(
                  f"§6[地脉尖峰] 区块 [$chunkX%d, $chunkZ%d] 激发地鸣 (v=${cur.excitation}%.2f, s=${cur.stress}%.2f, $regime)"
                ),
                true
              )
        }

      case ActionMsgPool.RegimeTransitioned(cell, from, to) =>
        val (chunkX, chunkZ) = MandateRuntime.gridToWorld(cell)
        if to != Regime.Calm then
          overworld.getPlayers.asScala.foreach { player =>
            val dx = Math.abs(player.getChunkPos.x - chunkX)
            val dz = Math.abs(player.getChunkPos.z - chunkZ)
            if dx <= 3 && dz <= 3 then
              player.sendMessage(Text.literal(s"§e[验山示警] ${MandateWording.regimeHerald(to)}"), true)
              if VoyDebug.numbers then
                player.sendMessage(
                  Text.literal(f"§8[$chunkX%d, $chunkZ%d] $from -> $to"),
                  true
                )
          }

      case ActionMsgPool.BellStruck(cell, outcome, bx, by, bz, strikerId) =>
        val (chunkX, chunkZ) = MandateRuntime.gridToWorld(cell)
        BellStrike.onBellStruck(overworld, new BlockPos(bx, by, bz), chunkX, chunkZ, outcome, strikerId)
end ActionFeedback
