package moe.chestnut.awa.voy.mandate.overworld

import scala.jdk.CollectionConverters.*

import net.minecraft.entity.effect.{StatusEffectInstance, StatusEffects}
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.Box

import moe.chestnut.awa.voy.mandate.{MandateCalming, MandateEffects, MandatePhenomena, MandateRuntime}

/** Mandate -> Overworld, `沉滞` (Stagnation) channel (design draft §3.2;
  * heartbeat-shelter model, design note 11).
  *
  * Chunks without a living heartbeat (`Slow` / `Fast` / `Arrhythmic`) are
  * stagnated: the vanilla *economy* sags (trade prices, breeding, golem
  * summons, crop growth — see `MandateEconomy` and the mixins) and nearby
  * villagers visibly wear `voy:stagnation`. The baseline tier never touches
  * the player's body; while the rhythm runs acute or the ground sits in
  * saturated burnout the pressure turns bodily.
  *
  * This module is also the 20-tick heartbeat of the world-side wiring: it
  * drives the per-player effect pass and owns the 60 s calming-stock
  * rebalance hook.
  */
object StagnationRunner:
  /** Stagnation bookkeeping cadence and effect duration in server ticks
    * (design draft §3.2: reapply outside the pulse window, short enough that
    * a fresh window clears it immediately).
    */
  private val StagnationReapplyTicks = 20
  private val StagnationEffectTicks = 55

  /** Last shelter verdict per player, so a *change* can be announced once
    * instead of the same line every second. Server thread only.
    */
  private val lastShelter = new java.util.HashMap[java.util.UUID, java.lang.Boolean]()

  /** Periodic Overworld check (runs on server thread). */
  def tick(server: MinecraftServer): Unit =
    if server.getTicks % StagnationReapplyTicks != 0 then return
    val overworld = server.getOverworld
    if overworld == null then return

    overworld.getPlayers.asScala.foreach { player =>
      applyTo(overworld, player)
    }

    // Baseline-calming stock rebalance (design note 11 ruling 11): scan the
    // world truth for calming sources, then step every cell's c0 stock toward
    // its source-driven equilibrium / leak.
    if server.getTicks % MandateCalming.RebalanceTicks == 0 then
      MandateCalming.rebalance(overworld)

  private def applyTo(world: ServerWorld, player: ServerPlayerEntity): Unit =
    val chunkX = player.getChunkPos.x
    val chunkZ = player.getChunkPos.z
    val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
    val sheltered = readout.isSheltered

    announceShelterChange(player, sheltered)

    if sheltered then
      player.removeStatusEffect(MandateEffects.StagnationEntry)
      player.removeStatusEffect(MandateEffects.StagnationSpikeEntry)
    else
      val acuteBodyStrain = MandatePhenomena.shouldApplyAcuteBodyStrain(readout)
      if acuteBodyStrain then
        player.addStatusEffect(
          new StatusEffectInstance(
            MandateEffects.StagnationSpikeEntry,
            StagnationEffectTicks,
            0,
            false,
            true,
            true
          )
        )
        player.addStatusEffect(
          new StatusEffectInstance(
            StatusEffects.SLOWNESS,
            StagnationEffectTicks,
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
            MandateEffects.StagnationEntry,
            StagnationEffectTicks,
            0,
            true,
            true,
            false
          )
        )
        if acuteBodyStrain then
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

    // Sensory phenomenon layer (design note 06 §3): the world itself is the
    // instrument panel. The profile binds silence to the burnout-phase reasons only —
    // never to an oscillating `Regime.Burnout`.
    val profile = MandatePhenomena.classifyPhenomena(readout, world.isNight)
    if profile.precursorSpore then
      // Precursor signal while c0 still holds (§1.2 critical slowing-down)
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
    PhenomenaEmitter.emit(world, player, profile)

  /** Speak only when the shelter verdict *flips*: entering stagnation and
    * coming back out are the two moments a player can actually act on.
    * Repeating the state every second is what turned this system into noise.
    */
  private def announceShelterChange(player: ServerPlayerEntity, maintained: Boolean): Unit =
    val previous = lastShelter.put(player.getUuid, java.lang.Boolean.valueOf(maintained))
    if previous == null || previous.booleanValue == maintained then return
    val line =
      if maintained then "§a地鸣的心跳续上了：节律在线，沉滞退去，寨里的营生又活络了"
      else "§c心跳断了或乱了：沉滞漫上来，寨里的营生蔫了下去"
    player.sendMessage(Text.literal(line), true)
end StagnationRunner
