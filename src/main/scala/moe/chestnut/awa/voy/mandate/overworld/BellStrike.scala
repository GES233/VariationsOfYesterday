package moe.chestnut.awa.voy.mandate.overworld

import java.util.UUID

import net.minecraft.block.{BlockState, Blocks}
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.{DustParticleEffect, ParticleTypes}
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos

import moe.chestnut.awa.voy.VoyConfig
import moe.chestnut.awa.voy.inner.veins.*
import moe.chestnut.awa.voy.mandate.*

/** Bell strike: the one ritual gesture that both *paces* the rhythm and
  * *doses* the chunk's calming stock (design note 11 §1.5, ruling 11; note 12 §4,
  * ruling 16).
  *
  * World -> engine sends `StrikeBell` (`handle`); engine adjudicates
  * `NoAnswer` / `Overdriven` / `Struck` against its private guard and doses
  * `c0` atomically, then emits `BellStruck` back to `onBellStruck` for world
  * source registration, particles, sound, striker recoil, and chat wording.
  */
object BellStrike:
  /** Warm amber ring: the strike landed in the rhythm (bell-palette copper). */
  private val StrikeAmber = new DustParticleEffect(0xe6b432, 1.0f)

  /** Rust-red burst: overdrive backlash (matches the overload message hue). */
  private val StrikeRust = new DustParticleEffect(0x8a3b32, 1.1f)

  def handle(
      player: PlayerEntity,
      world: ServerWorld,
      pos: BlockPos,
      state: BlockState,
      chunkX: Int,
      chunkZ: Int
  ): Option[ActionResult] =
    if state.isOf(Blocks.BELL) && MandateRuntime.isLiveChunk(chunkX, chunkZ) then
      MandateRuntime.strikeBellAt(chunkX, chunkZ, pos.getX, pos.getY, pos.getZ, player.getUuid)
      Some(ActionResult.PASS)
    else None

  /** Apply the engine's `BellStruck` receipt on the server thread. */
  def onBellStruck(
      world: ServerWorld,
      pos: BlockPos,
      chunkX: Int,
      chunkZ: Int,
      outcome: BellOutcome,
      strikerId: Option[UUID]
  ): Unit =
    if outcome.anchorsC0 then
      // The engine already dosed `c0` atomically in the mailbox; register the
      // physical bell so its 45-min trickle stays backed by the world truth.
      MandateCalming.register(
        MandateSource(MandateSourceKind.Bell, pos.getX, pos.getY, pos.getZ)
      )

    feedback(world, pos, outcome)

    val striker = strikerId.flatMap(id => Option(world.getPlayerByUuid(id)))
    if outcome.triggersOverloadPenalty then
      striker.foreach { player =>
        player.addStatusEffect(
          new StatusEffectInstance(
            MandateEffects.PulseSaturationEntry,
            VoyConfig.current.rhythmParam.overloadPenaltyTicks,
            0,
            false,
            true,
            true
          )
        )
      }
      world.playSound(
        null,
        pos,
        SoundEvents.BLOCK_BELL_RESONATE,
        SoundCategory.BLOCKS,
        1.0f,
        0.3f
      )
      world.spawnParticles(
        ParticleTypes.ASH,
        pos.getX + 0.5,
        pos.getY + 0.8,
        pos.getZ + 0.5,
        10,
        0.4,
        0.4,
        0.4,
        0.02
      )

    striker.foreach { player =>
      player.sendMessage(
        Text.literal(MandateWording.formatBellPulse(chunkX, chunkZ, outcome)),
        true
      )
    }

  /** Per-outcome bell feedback (design note 12 §4.2). The vanilla bell keeps its
    * own sound (the interaction stays PASS), and these layers make the three
    * outcomes readable at a glance.
    */
  private def feedback(
      world: ServerWorld,
      pos: BlockPos,
      outcome: BellOutcome
  ): Unit =
    val x = pos.getX + 0.5
    val y = pos.getY + 0.8
    val z = pos.getZ + 0.5
    outcome match
      case BellOutcome.Struck =>
        world.spawnParticles(StrikeAmber, x, y, z, 10, 0.35, 0.25, 0.35, 0.01)
        world.spawnParticles(ParticleTypes.NOTE, x, y + 0.4, z, 6, 0.4, 0.3, 0.4, 0.05)

      case BellOutcome.NoAnswer =>
        // Dead meat gives no echo — but "no echo" must still be *legible* as a
        // distinct outcome, not as a missed click.
        world.playSound(
          null,
          pos,
          SoundEvents.BLOCK_CANDLE_EXTINGUISH,
          SoundCategory.BLOCKS,
          0.8f,
          0.7f
        )
        world.spawnParticles(ParticleTypes.ASH, x, y, z, 8, 0.35, 0.25, 0.35, 0.01)
        world.spawnParticles(ParticleTypes.ASH, x, y - 0.3, z, 4, 0.4, 0.15, 0.4, 0.005)

      case BellOutcome.Overdriven =>
        world.spawnParticles(StrikeRust, x, y, z, 14, 0.5, 0.4, 0.5, 0.02)
end BellStrike
