package moe.chestnut.awa.voy.mandate

import java.util.concurrent.ThreadLocalRandom

/** Server-thread bridge between the stagnation economy tiers
  * ([[MandatePhenomena.classifyEconomy]]) and the vanilla-economy mixins
  * (`moe.chestnut.awa.voy.mixin`): trade prices, villager breeding, iron
  * golem summons and crop random-tick growth (design draft §3.2, 2026-11-09
  * revision).
  *
  * Keep-chance rolls live here (not in the mixins) so the mixins stay
  * trivial and the whole decision path is exercised from the test source set.
  * The rolls are gameplay-side pressure, not engine state — thread-local
  * randomness is fine and never touches the replay-deterministic lattice.
  */
object MandateEconomy:

  private def profile(chunkX: Int, chunkZ: Int): MandatePhenomena.EconomyProfile =
    MandatePhenomena.classifyEconomy(MandateRuntime.readoutAt(chunkX, chunkZ))

  private def rollsAgainst(keepChance: Double): Boolean =
    keepChance < 1.0 && ThreadLocalRandom.current().nextDouble() >= keepChance

  /** Special-price bonus a stagnated villager adds to every offer when the
    * trade screen opens (cleared with the session, so it never accumulates).
    */
  def tradePriceBonus(chunkX: Int, chunkZ: Int): Int =
    profile(chunkX, chunkZ).tradePriceBonus

  /** Whether one villager breeding attempt is suppressed this check. */
  def suppressBreeding(chunkX: Int, chunkZ: Int): Boolean =
    rollsAgainst(profile(chunkX, chunkZ).breedingKeepChance)

  /** Whether one iron-golem summon is suppressed this attempt. */
  def suppressGolemSpawn(chunkX: Int, chunkZ: Int): Boolean =
    rollsAgainst(profile(chunkX, chunkZ).golemKeepChance)

  /** Whether one crop random-tick growth is suppressed this tick. */
  def suppressCropGrowth(chunkX: Int, chunkZ: Int): Boolean =
    rollsAgainst(profile(chunkX, chunkZ).cropKeepChance)
