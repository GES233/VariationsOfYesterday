package moe.chestnut.awa.voy

import scala.jdk.CollectionConverters.*

import net.fabricmc.fabric.api.event.player.{PlayerBlockBreakEvents, UseBlockCallback}
import net.minecraft.block.{BlockState, Blocks}
import net.minecraft.entity.effect.{StatusEffectInstance, StatusEffects}
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.text.Text
import net.minecraft.util.math.Box
import net.minecraft.util.{ActionResult, Hand}
import net.minecraft.world.World

import moe.chestnut.awa.voy.inner.action.{ActionMsgPool, MandateActionProtocol}
import moe.chestnut.awa.voy.inner.mandate.*

/** Minimal Overworld reference wiring for the Mandate Engine (design draft
  * §1.3, §7.8, §8):
  *
  *  - **World -> Mandate (1:N channel mapping)**:
  *    - Breaking stone / ore blocks injects land stress (`StressInject`)
  *      with immediate sub-threshold feedback inside the active lattice;
  *    - Ringing a `Blocks.BELL` establishes baseline calming (`c0 = 0.03`)
  *      and fires an alpha-synapse pulse (`FirePulse(area = 12.0)`);
  *      breaking a Bell collapses `c0` in that chunk (`c0 = 0.0`).
  *  - **Diegetic Readout**:
  *    - Right-clicking a block while holding a Clock or Compass reads out
  *      the chunk's `Regime`, `PhaseReading` (with 20-division dial tick),
  *      and `c0` shelter status.
  *  - **Mandate -> Overworld (`沉滞` Stagnation & Spike actions)**:
  *    - Chunks without an active, non-spiking `c0` maintenance source apply
  *      the Overworld Stagnation effect (`MINING_FATIGUE` on players,
  *      `SLOWNESS` + ash particles on nearby villagers);
  *    - Outbound `CellSpikeTriggered` and `RegimeTransitioned` actions produce
  *      audible tremors and ash plumes on the server thread.
  */
object MandateOverworld:
  /** Standard c0 level provided by an intact village bell (§1.1.3, SliceContract.c0Intact). */
  val BellBaselineCalming: Double = 0.03

  /** Standard pulse area delivered when ringing a bell (matches SliceContract alpha_shock/resonance scale). */
  val BellPulseArea: Double = 12.0

  /** Compute the stress injection dose for breaking a block in the Overworld. */
  def stressDoseForBlock(state: BlockState): Double =
    if state.isOf(Blocks.TNT) || state.isOf(Blocks.ANCIENT_DEBRIS) || state.isOf(Blocks.BUDDING_AMETHYST) then
      0.06
    else if isOreBlock(state) then
      0.04
    else if state.isIn(BlockTags.BASE_STONE_OVERWORLD) then
      0.01
    else
      0.0

  private def isOreBlock(state: BlockState): Boolean =
    state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE) ||
      state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE) ||
      state.isOf(Blocks.COPPER_ORE) || state.isOf(Blocks.DEEPSLATE_COPPER_ORE) ||
      state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE) ||
      state.isOf(Blocks.REDSTONE_ORE) || state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE) ||
      state.isOf(Blocks.LAPIS_ORE) || state.isOf(Blocks.DEEPSLATE_LAPIS_ORE) ||
      state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE) ||
      state.isOf(Blocks.EMERALD_ORE) || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE)

  def register(): Unit =
    registerBlockBreakListener()
    registerUseBlockListener()

  private def registerBlockBreakListener(): Unit =
    PlayerBlockBreakEvents.AFTER.register { (world, player, pos, state, _) =>
      if !world.isClient && world.getRegistryKey == World.OVERWORLD then
        val chunkX = pos.getX >> 4
        val chunkZ = pos.getZ >> 4
        val serverWorld = world.asInstanceOf[ServerWorld]

        if state.isOf(Blocks.BELL) then
          MandateRuntime.setBaselineCalmingAt(chunkX, chunkZ, 0.0)
          MandateRuntime.injectStressAt(chunkX, chunkZ, 0.06)
          serverWorld.playSound(
            null,
            pos,
            SoundEvents.BLOCK_BELL_RESONATE,
            SoundCategory.BLOCKS,
            1.0f,
            0.5f
          )
          player.sendMessage(
            Text.literal(f"§c[旧日变奏] 镇山钟损毁：区块 [$chunkX%d, $chunkZ%d] 维持源 (c0) 坍塌，沉滞浮现"),
            true
          )
        else
          val dose = stressDoseForBlock(state)
          if dose > 0.0 && MandateRuntime.isLiveChunk(chunkX, chunkZ) then
            MandateRuntime.injectStressAt(chunkX, chunkZ, dose)
            val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
            // Immediate sub-threshold feedback (design draft §1.1.3 & §8.8)
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

  private def registerUseBlockListener(): Unit =
    UseBlockCallback.EVENT.register { (player, world, hand, hitResult) =>
      if world.isClient || hand != Hand.MAIN_HAND || world.getRegistryKey != World.OVERWORLD then
        ActionResult.PASS
      else
        val pos = hitResult.getBlockPos
        val chunkX = pos.getX >> 4
        val chunkZ = pos.getZ >> 4
        val state = world.getBlockState(pos)
        val held = player.getStackInHand(hand)

        if held.isOf(Items.CLOCK) || held.isOf(Items.COMPASS) then
          val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
          player.sendMessage(Text.literal(formatInspection(chunkX, chunkZ, readout)), true)
          ActionResult.PASS
        else if state.isOf(Blocks.BELL) then
          MandateRuntime.setBaselineCalmingAt(chunkX, chunkZ, BellBaselineCalming)
          MandateRuntime.firePulseAt(chunkX, chunkZ, BellPulseArea)
          val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
          player.sendMessage(Text.literal(formatBellPulse(chunkX, chunkZ, readout)), true)
          ActionResult.PASS
        else
          ActionResult.PASS
    }

  /** Periodic Overworld check (runs on server thread): applies or clears the
    * `沉滞` (Stagnation) debuff on players and nearby villagers depending on
    * whether their chunk is sheltered by an intact `c0` maintenance source.
    */
  def onServerTick(server: MinecraftServer): Unit =
    if server.getTicks % 20 != 0 then return
    val overworld = server.getOverworld
    if overworld == null then return

    overworld.getPlayers.asScala.foreach { player =>
      applyChunkMandateEffects(overworld, player)
    }

  private def applyChunkMandateEffects(world: ServerWorld, player: ServerPlayerEntity): Unit =
    val chunkX = player.getChunkPos.x
    val chunkZ = player.getChunkPos.z
    val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
    val maintained = readout.isMaintained

    if !maintained then
      val spikingOrBurnout =
        readout.regime == Regime.Burnout ||
          Math.abs(readout.current.dissonance) >= ReadoutParam().spikeLevel
      val amplifier = if spikingOrBurnout then 1 else 0

      // Apply Stagnation (`沉滞`) mining fatigue to the player
      player.addStatusEffect(
        new StatusEffectInstance(
          StatusEffects.MINING_FATIGUE,
          55,
          amplifier,
          true,
          false,
          true
        )
      )
      if spikingOrBurnout then
        player.addStatusEffect(
          new StatusEffectInstance(
            StatusEffects.SLOWNESS,
            55,
            0,
            true,
            false,
            true
          )
        )
        world.spawnParticles(
          ParticleTypes.ASH,
          player.getX,
          player.getY + 1.0,
          player.getZ,
          8,
          0.6,
          0.5,
          0.6,
          0.01
        )

      // Apply Stagnation to nearby vanilla villagers (§7.8)
      val box = new Box(
        player.getX - 24.0,
        player.getY - 12.0,
        player.getZ - 24.0,
        player.getX + 24.0,
        player.getY + 12.0,
        player.getZ + 24.0
      )
      world.getEntitiesByClass(classOf[VillagerEntity], box, _ => true).asScala.foreach { villager =>
        villager.addStatusEffect(
          new StatusEffectInstance(
            StatusEffects.SLOWNESS,
            55,
            0,
            true,
            false,
            false
          )
        )
        world.spawnParticles(
          ParticleTypes.ASH,
          villager.getX,
          villager.getY + 1.5,
          villager.getZ,
          2,
          0.2,
          0.2,
          0.2,
          0.0
        )
      }
    else if readout.regime == Regime.Strained || readout.regime == Regime.Oscillating then
      // Precursor signal while c0 still holds (§7.8 flickering dashboard)
      world.spawnParticles(
        ParticleTypes.MYCELIUM,
        player.getX,
        player.getY + 0.8,
        player.getZ,
        4,
        0.8,
        0.4,
        0.8,
        0.0
      )

  /** Handle outbound actions drained from the Mandate actor on the server thread. */
  def onAction(server: MinecraftServer, action: MandateActionProtocol): Unit =
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
            player.sendMessage(
              Text.literal(
                f"§6[地脉尖峰] 区块 [$chunkX%d, $chunkZ%d] 激发地鸣 (v=${cur.dissonance}%.2f, s=${cur.stress}%.2f, $regime)"
              ),
              true
            )
        }

      case ActionMsgPool.RegimeTransitioned(cell, from, to, _) =>
        val (chunkX, chunkZ) = MandateRuntime.gridToWorld(cell)
        if to != Regime.Calm then
          overworld.getPlayers.asScala.foreach { player =>
            val dx = Math.abs(player.getChunkPos.x - chunkX)
            val dz = Math.abs(player.getChunkPos.z - chunkZ)
            if dx <= 3 && dz <= 3 then
              player.sendMessage(
                Text.literal(f"§e[验山示警] 区块 [$chunkX%d, $chunkZ%d] 慢应力跃迁: $from -> $to"),
                true
              )
          }

      case ActionMsgPool.MandateCurrentStatus(_) => ()

  def formatInspection(chunkX: Int, chunkZ: Int, r: CellReadout): String =
    val shelter = if r.isMaintained then "§a庇护正常(免除沉滞)" else "§c沉滞生效(缺c0或尖峰失效)"
    val phaseText = formatPhaseShort(r.phase)
    f"§e[验山读数] [$chunkX%d,$chunkZ%d] 态:${r.regime} 相:$phaseText s=${r.current.stress}%.2f c0=${r.baselineCalming}%.2f | $shelter"

  def formatBellPulse(chunkX: Int, chunkZ: Int, r: CellReadout): String =
    r.phase match
      case PhaseReading.Cycle(label, _) =>
        val tick = r.dialTick.getOrElse(0)
        val effectHint = label match
          case PhaseLabel.AbsoluteRefractory | PhaseLabel.RelativeRefractory =>
            "§a命中推迟刻度，下一次地鸣尖峰延后"
          case PhaseLabel.Excitation =>
            "§c正处尖峰爆发期，钟声被地鸣吞没"
          case PhaseLabel.Resting =>
            "§7处于恢复晚期，脉冲被平缓吸收"
        f"§b[叩钟谐振] [$chunkX%d,$chunkZ%d] 刻度:第$tick%02d/20格 ($label) -> $effectHint"
      case _ =>
        f"§b[叩钟镇山] [$chunkX%d,$chunkZ%d] 维持源已锚定 (c0=$BellBaselineCalming%.2f)，周遭沉滞消退"

  def formatPhaseShort(phase: PhaseReading): String =
    phase match
      case PhaseReading.Cycle(label, frac) =>
        val tick = Readout.dialTick(phase).getOrElse(0)
        f"$label($frac%.2f/第$tick%02d格)"
      case PhaseReading.Basin(label) =>
        s"Basin($label)"
      case PhaseReading.Undefined(reason) =>
        s"Undef($reason)"
