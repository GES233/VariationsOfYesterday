package moe.chestnut.awa.voy.mandate.overworld

import net.minecraft.block.Blocks
import net.minecraft.particle.{DustParticleEffect, ParticleTypes}
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld

import moe.chestnut.awa.voy.mandate.MandatePhenomena

/** Mandate -> Overworld, sensory layer (design note 06 §3): the world itself
  * is the instrument panel — 3-tier iron-yellow water seep, night mycelial
  * glow on healthy sheltered ground, and silent blight on deep-incapacitated
  * chunks, all projected from the pure [[MandatePhenomena]] profile.
  *
  * Runs every 20 ticks per player and stays cheap by construction: the seep
  * is a bounded random surface scan, never a world sweep.
  */
object PhenomenaEmitter:
  /** Iron-ochre seep colour (the "yellow boy" precipitate of note 06 §3). */
  private val SeepDust = new DustParticleEffect(0xc77e1f, 1.1f)

  /** Server-side emission of the chunk's phenomenon profile around a player
    * (design note 06 §3): silent blight, night mycelial glow, and the 3-tier
    * iron-yellow water seep.
    */
  def emit(
      world: ServerWorld,
      player: ServerPlayerEntity,
      profile: MandatePhenomena.PhenomenonProfile
  ): Unit =
    if profile.silentBlight then
      // Deep incapacitation: no spores, no glow — a thin dead ash only.
      world.spawnParticles(
        ParticleTypes.WHITE_ASH,
        player.getX,
        player.getY + 0.8,
        player.getZ,
        2,
        0.8,
        0.4,
        0.8,
        0.0
      )
    else if profile.mycelialGlow then
      // Healthy sheltered night: faint mycelial bioluminescence. Density is
      // capped low on purpose (note 06 §7.3: the glow must never become a
      // free numeric readout of M).
      world.spawnParticles(
        ParticleTypes.GLOW,
        player.getX,
        player.getY + 0.2,
        player.getZ,
        2,
        1.2,
        0.4,
        1.2,
        0.0
      )

    if profile.seepTier != MandatePhenomena.YellowSeepTier.None then
      emitYellowSeep(world, player, profile.seepTier.particleCount)

  /** Sample a few nearby surface water blocks and tint them with iron-ochre
    * dust ("yellow boy", note 06 §3). Bounded scan — at most 24 attempts and
    * 2 emission sites per player per emission tick — keeps the per-tick cost
    * fixed regardless of world size.
    */
  private def emitYellowSeep(world: ServerWorld, player: ServerPlayerEntity, count: Int): Unit =
    val base = player.getBlockPos
    val rng = world.random
    var emitted = 0
    var attempts = 0
    while emitted < 2 && attempts < 24 do
      attempts += 1
      val pos = base.add(rng.nextInt(17) - 8, rng.nextInt(9) - 4, rng.nextInt(17) - 8)
      if world.getBlockState(pos).isOf(Blocks.WATER) then
        world.spawnParticles(
          SeepDust,
          pos.getX + 0.5,
          pos.getY + 0.95,
          pos.getZ + 0.5,
          count,
          0.35,
          0.05,
          0.35,
          0.0
        )
        emitted += 1
end PhenomenaEmitter
