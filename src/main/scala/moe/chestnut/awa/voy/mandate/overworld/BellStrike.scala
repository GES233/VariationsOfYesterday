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

/** Bell strike: the one ritual gesture that both *paces* the heartbeat and
  * *doses* the chunk's calming stock (design note 11 §1.5, ruling 11).
  *
  * The four verdicts come from the pure [[BellRitual]] adjudicator; this
  * module only spends their consequences and renders them. Every verdict is
  * distinguishable without reading chat: amber + notes = paced, a muffled
  * puff = swallowed, rust burst = overdrive, dead ash = no answer.
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
      val outcome = BellRitual.evaluateBellStrike(readout, VoyConfig.current.heartbeatParam)
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
      MandateRuntime.firePulseAt(chunkX, chunkZ, outcome.pulseArea)
      // Four verdicts must be distinguishable without reading chat
      // (design note 11 §1.5): particles + layered sounds per outcome.
      feedback(world, pos, outcome, readout)
      // Acute overdrive backlash (design note 11 §1.5 row 2): striking
      // inside the guard recoils on the striker immediately.
      if outcome.triggersOverloadPenalty then
        player.addStatusEffect(
          new StatusEffectInstance(
            MandateEffects.PulseSaturationEntry,
            VoyConfig.current.heartbeatParam.overloadPenaltyTicks,
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
        Text.literal(MandateWording.formatBellPulse(chunkX, chunkZ, readout, outcome)),
        true
      )
      Some(ActionResult.PASS)
    else None

  /** Per-verdict bell feedback (design note 11 §1.5). The vanilla bell keeps
    * its own sound (the interaction stays PASS), and these layers make the
    * four verdicts readable at a glance.
    */
  private def feedback(
      world: ServerWorld,
      pos: BlockPos,
      outcome: BellRitual.BellStrikeOutcome,
      readout: CellReadout
  ): Unit =
    val x = pos.getX + 0.5
    val y = pos.getY + 0.8
    val z = pos.getZ + 0.5
    outcome match
      case BellRitual.BellStrikeOutcome.AnchoredOrMaintained =>
        world.spawnParticles(StrikeAmber, x, y, z, 10, 0.35, 0.25, 0.35, 0.01)
        readout.phase match
          case PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory | PhaseLabel.RelativeRefractory, _) =>
            // Phase-corrected: a clear chime and rising notes.
            world.playSound(
              null,
              pos,
              SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
              SoundCategory.BLOCKS,
              0.9f,
              1.4f
            )
            world.spawnParticles(ParticleTypes.NOTE, x, y + 0.4, z, 6, 0.4, 0.3, 0.4, 0.05)
          case _ =>
            world.spawnParticles(ParticleTypes.NOTE, x, y + 0.3, z, 3, 0.3, 0.2, 0.3, 0.02)

      case BellRitual.BellStrikeOutcome.SpikeSwallowed =>
        world.playSound(
          null,
          pos,
          SoundEvents.BLOCK_CANDLE_EXTINGUISH,
          SoundCategory.BLOCKS,
          0.8f,
          0.7f
        )
        world.spawnParticles(ParticleTypes.ASH, x, y, z, 6, 0.3, 0.3, 0.3, 0.01)

      case BellRitual.BellStrikeOutcome.DeadMyceliumBurnout =>
        world.spawnParticles(ParticleTypes.ASH, x, y - 0.2, z, 3, 0.4, 0.2, 0.4, 0.005)

      case BellRitual.BellStrikeOutcome.PulseOverloadBurnout =>
        world.spawnParticles(StrikeRust, x, y, z, 14, 0.5, 0.4, 0.5, 0.02)
end BellStrike
