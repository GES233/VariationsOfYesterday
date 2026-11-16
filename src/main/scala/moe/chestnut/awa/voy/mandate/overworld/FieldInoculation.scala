package moe.chestnut.awa.voy.mandate.overworld

import net.minecraft.block.{BlockState, PillarBlock}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.math.{BlockPos, Direction}

import moe.chestnut.awa.voy.blocks.VoyBlocks
import moe.chestnut.awa.voy.mandate.{MandatePhenomena, MandateRuntime, MandateWording}

/** Player -> Mandate, mycelial field inoculation channel (design note 06
  * §7.1): two gestures that both spend an item to seed mycelium in the field.
  *
  *  - **Timber colonization**: wild inoculum on stripped timber, when the
  *    chunk's network is live enough, turns the log in place into `菌材`;
  *  - **Soil re-inoculation**: fungal inoculum into depleted sub-toxic soil
  *    seeds `M` via `EventMsg.SetSustenance`. Seeding is a floor, never a
  *    downgrade, and prepared `菌材` needs a deliberate sneak-right-click so
  *    that placement stays the default gesture.
  */
object FieldInoculation:
  /** Gesture 1: colonize stripped timber into `菌材` in the field. */
  def timber(
      player: PlayerEntity,
      world: ServerWorld,
      pos: BlockPos,
      state: BlockState,
      held: ItemStack,
      chunkX: Int,
      chunkZ: Int
  ): Option[ActionResult] =
    if !player.isSneaking && OverworldVocabulary.isWildInoculum(held) &&
      OverworldVocabulary.isStrippedTimber(state) &&
      MandateRuntime.isLiveChunk(chunkX, chunkZ)
    then
      val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
      if MandatePhenomena.canInoculateTimberInField(readout) then
        val axis =
          if state.contains(PillarBlock.AXIS) then state.get(PillarBlock.AXIS)
          else Direction.Axis.Y
        world.setBlockState(
          pos,
          VoyBlocks.InoculatedTimber.getDefaultState.`with`(PillarBlock.AXIS, axis)
        )
        if !player.isCreative then held.decrement(1)
        world.spawnParticles(
          ParticleTypes.MYCELIUM,
          pos.getX + 0.5,
          pos.getY + 0.8,
          pos.getZ + 0.5,
          14,
          0.4,
          0.4,
          0.4,
          0.01
        )
        world.spawnParticles(
          ParticleTypes.SPORE_BLOSSOM_AIR,
          pos.getX + 0.5,
          pos.getY + 0.8,
          pos.getZ + 0.5,
          10,
          0.4,
          0.4,
          0.4,
          0.02
        )
        world.playSound(
          null,
          pos,
          SoundEvents.BLOCK_MOSS_PLACE,
          SoundCategory.BLOCKS,
          0.9f,
          0.95f
        )
        player.sendMessage(
          Text.literal(MandateWording.formatTimberFieldInoculation(success = true, readout)),
          true
        )
      else
        player.sendMessage(
          Text.literal(MandateWording.formatTimberFieldInoculation(success = false, readout)),
          true
        )
      Some(ActionResult.SUCCESS)
    else None

  /** Gesture 2 (scatter): seed mycelial sustenance into the soil. Prepared
    * `菌材` requires sneaking because placement stays the default gesture for
    * a building material; wild inoculum keeps the original scatter gesture.
    */
  def soil(
      player: PlayerEntity,
      world: ServerWorld,
      pos: BlockPos,
      state: BlockState,
      held: ItemStack,
      chunkX: Int,
      chunkZ: Int
  ): Option[ActionResult] =
    if OverworldVocabulary.isInoculum(held) && OverworldVocabulary.isInoculableSoil(state) &&
      MandateRuntime.isLiveChunk(chunkX, chunkZ) &&
      (if held.isOf(VoyBlocks.InoculatedTimberItem) then player.isSneaking
       else !player.isSneaking)
    then
      val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
      val outcome = MandatePhenomena.evaluateReInoculation(readout)
      val richTimber = held.isOf(VoyBlocks.InoculatedTimberItem)
      outcome match
        case MandatePhenomena.ReInoculationOutcome.AlreadyViable =>
          // Living threads remain: let vanilla placement proceed, no spam.
          Some(ActionResult.PASS)
        case MandatePhenomena.ReInoculationOutcome.AcidTooToxic =>
          // The inoculum dies in acid soil: say so, then let vanilla
          // placement consume the item as an honest cost.
          player.sendMessage(
            Text.literal(MandateWording.formatReInoculation(chunkX, chunkZ, readout, outcome, richTimber)),
            true
          )
          Some(ActionResult.PASS)
        case MandatePhenomena.ReInoculationOutcome.Seeded =>
          // Seeding is a floor, never a downgrade: a bed that already holds
          // more living thread than the seed must not lose sustenance.
          val targetSeed =
            Math.max(
              readout.sustenance,
              if richTimber then MandatePhenomena.RichReInoculationSeed
              else outcome.targetLevel
            )
          MandateRuntime.setSustenanceAt(chunkX, chunkZ, targetSeed)
          if !player.isCreative then held.decrement(1)
          world.spawnParticles(
            ParticleTypes.SPORE_BLOSSOM_AIR,
            pos.getX + 0.5,
            pos.getY + 1.0,
            pos.getZ + 0.5,
            12,
            0.4,
            0.3,
            0.4,
            0.02
          )
          world.spawnParticles(
            ParticleTypes.MYCELIUM,
            pos.getX + 0.5,
            pos.getY + 1.0,
            pos.getZ + 0.5,
            10,
            0.4,
            0.2,
            0.4,
            0.0
          )
          world.playSound(
            null,
            pos,
            SoundEvents.ITEM_BONE_MEAL_USE,
            SoundCategory.BLOCKS,
            0.8f,
            1.1f
          )
          player.sendMessage(
            Text.literal(MandateWording.formatReInoculation(chunkX, chunkZ, readout, outcome, richTimber)),
            true
          )
          Some(ActionResult.SUCCESS)
    else None
end FieldInoculation
