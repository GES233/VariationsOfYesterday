package moe.chestnut.awa.voy

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.BellBlock
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.{RegistryKey, RegistryKeys}
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.text.Text
import net.minecraft.util.{ActionResult, Identifier}
import net.minecraft.util.math.BlockPos

import moe.chestnut.awa.voy.MandateRuntime.PocNotice
import moe.chestnut.awa.voy.inner.mandate.CellCoord

/** PoC 的 Minecraft 桥接层：server tick 门控、阶段反馈投递、敲钟与
  * TNT 爆炸两条交互通道。所有反馈只做声音/粒子/文本，不破坏方块、
  * 不伤害实体。
  */
object PocGame:

  def register(): Unit =
    // 模拟节拍：MandateRuntime 未 init 时 tick 为空操作；无玩家在线时
    // 暂停推进（设计草案 §1.4，首次灾变按游玩时长计）。TNT 爆炸队列
    // 不受玩家在线门控影响，照常消费（注入与否由活区判断决定）。
    ServerTickEvents.END_SERVER_TICK.register { server =>
      drainTntQueue(server)
      if !server.getPlayerManager.getPlayerList.isEmpty then
        MandateRuntime.tick().foreach(deliverNotice(server, _))
    }

    // 敲钟 = 相位调制脉冲：活区内的钟被使用时向对应 Cell 发放 alpha
    // 内核脉冲，返回 PASS 让 vanilla 敲钟流程照常进行。
    UseBlockCallback.EVENT.register { (_, world, _, hitResult) =>
      if !world.isClient then
        val pos = hitResult.getBlockPos
        if world.getBlockState(pos).getBlock.isInstanceOf[BellBlock] then
          val chunk = CellCoord(pos.getX >> 4, pos.getZ >> 4)
          if MandateRuntime.pulseAtChunk(chunk) then
            world match
              case sw: ServerWorld =>
                sw.spawnParticles(
                  ParticleTypes.SCULK_SOUL,
                  pos.getX + 0.5, pos.getY + 0.5, pos.getZ + 0.5,
                  6, 0.3, 0.3, 0.3, 0.01
                )
              case _ => ()
      ActionResult.PASS
    }

  /** 消费 Mixin 侧压入的 TNT 爆炸队列。 */
  private def drainTntQueue(server: MinecraftServer): Unit =
    var entry = TntExplosionQueue.poll()
    while entry != null do
      val key =
        RegistryKey.of(RegistryKeys.WORLD, Identifier.of(entry.worldId()))
      val world = server.getWorld(key)
      if world != null then onTntExploded(world, entry.x(), entry.y(), entry.z())
      entry = TntExplosionQueue.poll()

  /** 阶段阈值的一次性反馈：中文文本 + 锚点处声景。 */
  private def deliverNotice(
      server: MinecraftServer,
      notice: PocNotice
  ): Unit =
    val overworld = server.getOverworld
    val anchorPos = notice match
      case PocNotice.WarningCrossed(anchor)     => anchor
      case PocNotice.CatastropheCrossed(anchor) => anchor
    val x = anchorPos.x * 16 + 8.0
    val z = anchorPos.z * 16 + 8.0
    notice match
      case PocNotice.WarningCrossed(_) =>
        server.getPlayerManager.broadcast(
          Text.literal("【VOY·PoC】下游读数越过前兆阈值：土地开始示警，注意钟与河水的异样。"),
          false
        )
        overworld.playSound(
          null, x, 64.0, z,
          SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, 1.0f, 0.8f
        )
      case PocNotice.CatastropheCrossed(_) =>
        server.getPlayerManager.broadcast(
          Text.literal("【VOY·PoC】下游发生灾变点火：迟到的回响已经抵达村庄。"),
          false
        )
        overworld.playSound(
          null, x, 64.0, z,
          SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 0.8f, 0.6f
        )

  /** TNT 爆炸的一次性扰动注入（经 TntExplosionQueue 由 tick 轮询调用）：
    * 活区内生效，附即时、无害的阈下反馈（闷响 + 烟）。 */
  def onTntExploded(world: ServerWorld, x: Double, y: Double, z: Double): Unit =
    val chunk =
      CellCoord(Math.floor(x).toInt >> 4, Math.floor(z).toInt >> 4)
    if MandateRuntime.injectStressAtChunk(chunk) then
      world.spawnParticles(
        ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.6, 0.6, 0.6, 0.02
      )
      world.playSound(
        null, x, y, z,
        SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.9f, 0.5f
      )
