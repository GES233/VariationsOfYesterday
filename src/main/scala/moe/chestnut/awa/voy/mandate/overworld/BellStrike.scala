package moe.chestnut.awa.voy.mandate.overworld

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
  * The three outcomes come from the pure [[BellRitual]] adjudicator; this module
  * only spends their consequences and renders them. Every outcome is
  * distinguishable without reading chat: amber + notes = the strike landed, rust
  * burst = overdrive, dead ash = no answer. No outcome depends on the cycle's
  * phase any more — the bell is a pacemaker, not a phase sniper.
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
    if state.isOf(Blocks.BELL) then
      val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
      val outcome = BellRitual.evaluateBellStrike(readout, VoyConfig.current.rhythmParam)
      if outcome.anchorsC0 then
        // The strike is a DOSE into the chunk's calming stock, and the
        // bell itself is registered as a source so its trickle starts
        // immediately (design note 11 ruling 11).
        MandateCalming.register(
          MandateSource(MandateSourceKind.Bell, pos.getX, pos.getY, pos.getZ)
        )
        MandateRuntime.setBaselineCalmingAt(
          chunkX,
          chunkZ,
          MandateCalming.dose(readout.baselineCalming)
        )
      // Physics always applies (the bell always sounds); the guard stamp
      // is recorded engine-side from the Bell transmitter (note 11 §1.5).
      MandateRuntime.firePulseAt(chunkX, chunkZ, BellRitual.BellPulseArea)
      // Three outcomes must be distinguishable without reading chat
      // (design note 12 §4.2): particles + layered sounds per outcome.
      feedback(world, pos, outcome)
      // Acute overdrive backlash (design note 11 §1.5 row 2): striking
      // inside the guard recoils on the striker immediately.
      if outcome.triggersOverloadPenalty then
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
      player.sendMessage(
        Text.literal(MandateWording.formatBellPulse(chunkX, chunkZ, outcome)),
        true
      )
      Some(ActionResult.PASS)
    else None

  /** Per-outcome bell feedback (design note 12 §4.2). The vanilla bell keeps its
    * own sound (the interaction stays PASS), and these layers make the three
    * outcomes readable at a glance.
    *
    * The old "phase-corrected" variant (amethyst chime) is gone with the phase
    * gate: the bell no longer claims to know where the cycle was.
    */
  private def feedback(
      world: ServerWorld,
      pos: BlockPos,
      outcome: BellRitual.BellOutcome
  ): Unit =
    val x = pos.getX + 0.5
    val y = pos.getY + 0.8
    val z = pos.getZ + 0.5
    outcome match
      case BellRitual.BellOutcome.Struck =>
        world.spawnParticles(StrikeAmber, x, y, z, 10, 0.35, 0.25, 0.35, 0.01)
        world.spawnParticles(ParticleTypes.NOTE, x, y + 0.4, z, 6, 0.4, 0.3, 0.4, 0.05)

      case BellRitual.BellOutcome.NoAnswer =>
        // Dead meat gives no echo — but "no echo" must still be *legible* as a
        // distinct outcome, not as a missed click. A muffled candle-out (the cue
        // freed by the collapsed `SpikeSwallowed` state) plus a thin drift of
        // ash: the bell did ring, and died instantly.
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

      case BellRitual.BellOutcome.Overdriven =>
        world.spawnParticles(StrikeRust, x, y, z, 14, 0.5, 0.4, 0.5, 0.02)
end BellStrike
