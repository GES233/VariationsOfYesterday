package moe.chestnut.awa.voy.blocks

import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.util.math.BlockPos

/** Named particle / sound cues for `菌塘` (mycelial bed).
  *
  * Each method is a verbatim lift of one inline `spawnParticles` /
  * `playSound` call: particle type, block-local position, count, offset,
  * speed and the sound volume / pitch are unchanged, so splitting the block
  * file cannot move any feel of the interactions.
  */
object BedFx:
  /** 荧丝尘: the bed is re-seeded (refurbish succeeded). */
  def myceliumDustRestore(world: ServerWorld, pos: BlockPos): Unit =
    world.spawnParticles(
      ParticleTypes.MYCELIUM,
      pos.getX + 0.5,
      pos.getY + 1.05,
      pos.getZ + 0.5,
      14,
      0.35,
      0.15,
      0.35,
      0.01
    )

  /** 白灰: the bed refuses the fresh timber (refurbish failed). */
  def whiteAshRefuse(world: ServerWorld, pos: BlockPos): Unit =
    world.spawnParticles(
      ParticleTypes.WHITE_ASH,
      pos.getX + 0.5,
      pos.getY + 1.05,
      pos.getZ + 0.5,
      8,
      0.3,
      0.15,
      0.3,
      0.01
    )

  /** 白灰: the cords rot black and the bed latches into `Withered`. */
  def whiteAshWither(world: ServerWorld, pos: BlockPos): Unit =
    world.spawnParticles(
      ParticleTypes.WHITE_ASH,
      pos.getX + 0.5,
      pos.getY + 1.05,
      pos.getZ + 0.5,
      6,
      0.35,
      0.15,
      0.35,
      0.0
    )

  /** 孢子: the tuber advances one age stage. */
  def sporeBloom(world: ServerWorld, pos: BlockPos): Unit =
    world.spawnParticles(
      ParticleTypes.SPORE_BLOSSOM_AIR,
      pos.getX + 0.5,
      pos.getY + 1.05,
      pos.getZ + 0.5,
      6,
      0.3,
      0.15,
      0.3,
      0.01
    )

  /** 荧丝尘: tubers are dug out and the bed is re-seeded. */
  def myceliumDustHarvest(world: ServerWorld, pos: BlockPos): Unit =
    world.spawnParticles(
      ParticleTypes.MYCELIUM,
      pos.getX + 0.5,
      pos.getY + 1.05,
      pos.getZ + 0.5,
      12,
      0.35,
      0.2,
      0.35,
      0.01
    )

  /** 骨粉音: fungal nutrient accepted, the growth step started. */
  def playBoneMealUse(world: ServerWorld, pos: BlockPos): Unit =
    world.playSound(
      null,
      pos,
      SoundEvents.ITEM_BONE_MEAL_USE,
      SoundCategory.BLOCKS,
      0.7f,
      1.15f
    )

  /** 生根土音: fresh timber is laid into a withered bed. */
  def playRootedDirtPlace(world: ServerWorld, pos: BlockPos): Unit =
    world.playSound(
      null,
      pos,
      SoundEvents.BLOCK_ROOTED_DIRT_PLACE,
      SoundCategory.BLOCKS,
      0.9f,
      1.0f
    )

  /** 生根土音: the rhizomorph mat under a harvest is broken open. */
  def playRootedDirtBreak(world: ServerWorld, pos: BlockPos): Unit =
    world.playSound(
      null,
      pos,
      SoundEvents.BLOCK_ROOTED_DIRT_BREAK,
      SoundCategory.BLOCKS,
      0.9f,
      1.05f
    )
