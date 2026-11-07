package moe.chestnut.awa.voy

import scala.jdk.CollectionConverters.*

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.player.{PlayerBlockBreakEvents, UseBlockCallback}
import net.minecraft.block.{BlockState, Blocks}
import net.minecraft.entity.effect.{StatusEffectInstance, StatusEffects}
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.item.Items
import net.minecraft.particle.{DustParticleEffect, ParticleTypes}
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.MinecraftServer
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.{CommandManager, ServerCommandSource}
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
  * §1.1, §1.4, §3.2):
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
  *      our own `voy:stagnation` effect (`×0.30` block break speed; the spike
  *      tier `voy:stagnation_spike` is `×0.09`), plus `SLOWNESS` and ash
  *      particles on nearby villagers;
  *    - Outbound `CellSpikeTriggered` and `RegimeTransitioned` actions produce
  *      audible tremors and ash plumes on the server thread.
  *  - **Sensory phenomena (design note 06 §3)**: the world itself is the
  *    instrument panel — 3-tier iron-yellow water seep, night mycelial glow
  *    on healthy sheltered ground, and silent blight on deep-incapacitated
  *    (`BurnoutNoCycle`) chunks, all driven by [[MandatePhenomena]].
  *  - **Mycelial re-inoculation (note 06 §7.1)**: applying fungal inoculum
  *    to depleted, sub-toxic soil seeds `M` via `EventMsg.SetSustenance`.
  */
object MandateOverworld:
  /** Standard c0 level provided by an intact village bell (§1.2, SliceContract.c0Intact). */
  val BellBaselineCalming: Double = 0.03

  /** Standard pulse area delivered when ringing a bell (matches SliceContract alpha_shock/resonance scale). */
  val BellPulseArea: Double = 12.0

  /** Phase-gated outcome of striking a bell / resonance vessel on a cell. */
  enum BellStrikeOutcome(
      val anchorsC0: Boolean,
      val baselineCalming: Double,
      val pulseArea: Double
  ):
    /** Saturated incapacitation (`Regime.Burnout`, `s >= 0.95`): the fast
      * system is still inside its oscillatory window, but the cell is
      * functionally incapacitated and the bell cannot re-anchor `c0` while
      * the stress stays saturated. NOT a dead zone: there is no latch, so
      * once sustained calming pulls the stress back below the threshold
      * (c ≳ 2u, design note 07 E4b), ordinary strikes anchor again. Recovery
      * is meant to be expensive, not impossible. */
    case SaturatedNoAnchor extends BellStrikeOutcome(false, 0.0, BellPulseArea)

    /** Unanchored oscillator (`c0 <= 0`, `Regime.Oscillating`) struck during `PhaseLabel.Excitation`:
      * pulse is swallowed by the active spike and `c0` fails to re-anchor.
      */
    case SpikeSwallowed extends BellStrikeOutcome(false, 0.0, BellPulseArea)

    /** Unanchored oscillator (`c0 <= 0`, `Regime.Oscillating`) struck inside the refractory delay
      * window (`AbsoluteRefractory` | `RelativeRefractory`): re-anchors `c0` and delays the spike.
      */
    case RefractoryReanchored extends BellStrikeOutcome(true, BellBaselineCalming, BellPulseArea)

    /** Calm / Strained / Resting or already-anchored cell: anchors/maintains `c0` and fires pulse. */
    case AnchoredOrMaintained extends BellStrikeOutcome(true, BellBaselineCalming, BellPulseArea)

  /** Pure, phase-gated rule for re-anchoring `c0` and firing a resonance pulse
    * when a player strikes a bell / industrial vessel in a chunk:
    *
    *  - `Regime.Burnout`: saturated incapacitation (`失能`), not death. The
    *    cell keeps oscillating; ringing cannot re-anchor `c0` while the stress
    *    stays saturated (`baselineCalming` stays `0.0`), but nothing is
    *    latched — relieving the stress re-enables anchoring.
    *  - Unanchored oscillator (`baselineCalming <= 0.0` and `Regime.Oscillating`):
    *    - `Cycle(AbsoluteRefractory | RelativeRefractory, _)`: hits the refractory
    *      delay window (`safe_tick`), re-anchoring `c0 = BellBaselineCalming` and firing the pulse.
    *    - `Cycle(Excitation, _)`: struck during the active spike; fails to re-anchor `c0`
    *      while still firing the pulse (swallowed by the spike).
    *  - `Calm` / `Strained` / `Resting` or already-intact `c0`: anchors/maintains
    *    `c0 = BellBaselineCalming` and fires the pulse.
    */
  def evaluateBellStrike(readout: CellReadout): BellStrikeOutcome =
    if readout.regime == Regime.Burnout then
      BellStrikeOutcome.SaturatedNoAnchor
    else if readout.baselineCalming <= 0.0 && readout.regime == Regime.Oscillating then
      readout.phase match
        case PhaseReading.Cycle(PhaseLabel.AbsoluteRefractory | PhaseLabel.RelativeRefractory, _) =>
          BellStrikeOutcome.RefractoryReanchored
        case PhaseReading.Cycle(PhaseLabel.Excitation, _) =>
          BellStrikeOutcome.SpikeSwallowed
        case _ =>
          BellStrikeOutcome.AnchoredOrMaintained
    else
      BellStrikeOutcome.AnchoredOrMaintained

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

  /** Fungal inoculum items that can seed mycelial sustenance (`M`) into a
    * depleted chunk (design note 06 §7.1, narrative "re-inoculation").
    */
  private def isInoculum(stack: net.minecraft.item.ItemStack): Boolean =
    stack.isOf(Items.GLOW_LICHEN) || stack.isOf(Items.BROWN_MUSHROOM) ||
      stack.isOf(Items.RED_MUSHROOM) || stack.isOf(Items.MYCELIUM)

  /** Ground blocks that accept inoculum (the soil/wetland continuity the
    * mycelium lives in, note 06 §2.1).
    */
  private def isInoculableSoil(state: BlockState): Boolean =
    state.isIn(BlockTags.DIRT) || state.isOf(Blocks.FARMLAND) ||
      state.isOf(Blocks.MUD) || state.isOf(Blocks.MOSS_BLOCK)

  def register(): Unit =
    registerBlockBreakListener()
    registerUseBlockListener()
    registerDebugCommand()

  /** `/voy debug [on|off]` — the raw `v`/`s`/`c0` numbers are diagnostics and
    * live behind this toggle instead of in the player's chat bar.
    */
  private def registerDebugCommand(): Unit =
    CommandRegistrationCallback.EVENT.register { (dispatcher, _, _) =>
      def report(ctx: CommandContext[ServerCommandSource], text: String): Int =
        ctx.getSource.sendFeedback(() => Text.literal(text), false)
        1
      val debug = CommandManager.literal("debug")
        .executes { ctx =>
          debugNumbers = !debugNumbers
          report(ctx, s"§7[旧日变奏] 数值调试：${if debugNumbers then "开" else "关"}")
        }
        .`then`(
          CommandManager.literal("on").executes { ctx =>
            debugNumbers = true
            report(ctx, "§7[旧日变奏] 数值调试：开")
          }
        )
        .`then`(
          CommandManager.literal("off").executes { ctx =>
            debugNumbers = false
            report(ctx, "§7[旧日变奏] 数值调试：关")
          }
        )
      dispatcher.register(CommandManager.literal("voy").`then`(debug))
    }

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
            // Immediate sub-threshold feedback (design draft §1.2, dual-channel feedback)
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
        else if isInoculum(held) && isInoculableSoil(state) &&
          MandateRuntime.isLiveChunk(chunkX, chunkZ)
        then
          val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
          val outcome = MandatePhenomena.evaluateReInoculation(readout)
          outcome match
            case MandatePhenomena.ReInoculationOutcome.AlreadyViable =>
              // Living threads remain: let vanilla placement proceed, no spam.
              ActionResult.PASS
            case MandatePhenomena.ReInoculationOutcome.AcidTooToxic =>
              // The inoculum dies in acid soil: say so, then let vanilla
              // placement consume the item as an honest cost.
              player.sendMessage(
                Text.literal(formatReInoculation(chunkX, chunkZ, readout, outcome)),
                true
              )
              ActionResult.PASS
            case MandatePhenomena.ReInoculationOutcome.Seeded =>
              val serverWorld = world.asInstanceOf[ServerWorld]
              MandateRuntime.setSustenanceAt(chunkX, chunkZ, outcome.targetLevel)
              if !player.isCreative then held.decrement(1)
              serverWorld.spawnParticles(
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
              serverWorld.spawnParticles(
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
              serverWorld.playSound(
                null,
                pos,
                SoundEvents.ITEM_BONE_MEAL_USE,
                SoundCategory.BLOCKS,
                0.8f,
                1.1f
              )
              player.sendMessage(
                Text.literal(formatReInoculation(chunkX, chunkZ, readout, outcome)),
                true
              )
              ActionResult.SUCCESS
        else if state.isOf(Blocks.BELL) then
          val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
          val outcome = evaluateBellStrike(readout)
          if outcome.anchorsC0 then
            MandateRuntime.setBaselineCalmingAt(chunkX, chunkZ, outcome.baselineCalming)
          MandateRuntime.firePulseAt(chunkX, chunkZ, outcome.pulseArea)
          player.sendMessage(
            Text.literal(formatBellPulse(chunkX, chunkZ, readout, outcome)),
            true
          )
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

  /** Raw `v` / `s` / `c0` numbers are diagnostics, not player-facing text.
    * Off by default; `/voy debug on` in-game turns them back on.
    */
  @volatile var debugNumbers: Boolean = false

  /** Last shelter verdict per player, so a *change* can be announced once
    * instead of the same line every second. Server thread only.
    */
  private val lastShelter = new java.util.HashMap[java.util.UUID, java.lang.Boolean]()

  private def applyChunkMandateEffects(world: ServerWorld, player: ServerPlayerEntity): Unit =
    val chunkX = player.getChunkPos.x
    val chunkZ = player.getChunkPos.z
    val readout = MandateRuntime.readoutAt(chunkX, chunkZ)
    val maintained = readout.isMaintained

    announceShelterChange(player, maintained)

    if !maintained then
      val spikingOrBurnout =
        readout.regime == Regime.Burnout ||
          Math.abs(readout.current.dissonance) >= ReadoutParam().spikeLevel

      // Apply `沉滞` (Stagnation) — our own effect, so the slowdown is
      // attributable to the lattice instead of to vanilla mining fatigue.
      // Particles are shown only during a spike: the baseline state is a
      // permanent condition and must not become visual noise.
      player.addStatusEffect(
        new StatusEffectInstance(
          if spikingOrBurnout then MandateEffects.StagnationSpikeEntry
          else MandateEffects.StagnationEntry,
          55,
          0,
          false,
          spikingOrBurnout,
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

      // Apply Stagnation to nearby vanilla villagers (§3.2)
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

    // Sensory phenomenon layer (design note 06 §3): the world itself is the
    // instrument panel. The profile binds silence to `BurnoutNoCycle` only —
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
    emitChunkPhenomena(world, player, profile)

  /** Iron-ochre seep colour (the "yellow boy" precipitate of note 06 §3). */
  private val SeepDust = new DustParticleEffect(0xc77e1f, 1.1f)

  /** Server-side emission of the chunk's phenomenon profile around a player
    * (design note 06 §3): silent blight, night mycelial glow, and the 3-tier
    * iron-yellow water seep. Runs every 20 ticks per player and stays cheap
    * by construction (a bounded random surface scan for the seep).
    */
  private def emitChunkPhenomena(
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

  /** Speak only when the shelter verdict *flips*: entering stagnation and
    * coming back out are the two moments a player can actually act on.
    * Repeating the state every second is what turned this system into noise.
    */
  private def announceShelterChange(player: ServerPlayerEntity, maintained: Boolean): Unit =
    val previous = lastShelter.put(player.getUuid, java.lang.Boolean.valueOf(maintained))
    if previous == null || previous.booleanValue == maintained then return
    val line =
      if maintained then "§a地脉归位，沉滞退去，手上又利索了"
      else "§7地脉松了：沉滞缠上来，挖掘开始发涩"
    player.sendMessage(Text.literal(line), true)

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
            // The tremor itself (sound + ash) is the player-facing signal; the
            // raw readout used to be printed twice per oscillation period and
            // became the mod's loudest, least useful surface.
            if debugNumbers then
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
              player.sendMessage(Text.literal(s"§e[验山示警] ${regimeHerald(to)}"), true)
              if debugNumbers then
                player.sendMessage(
                  Text.literal(f"§8[$chunkX%d, $chunkZ%d] $from -> $to"),
                  true
                )
          }

      case ActionMsgPool.MandateCurrentStatus(_) => ()

  /** Player-facing wording for a mycelial re-inoculation attempt (note 06
    * §7.1): what the soil did with the inoculum, never raw `M`/`s` numbers
    * (note 06 §1 constraint 5).
    */
  def formatReInoculation(
      chunkX: Int,
      chunkZ: Int,
      r: CellReadout,
      outcome: MandatePhenomena.ReInoculationOutcome
  ): String =
    outcome match
      case MandatePhenomena.ReInoculationOutcome.Seeded =>
        f"§a[接种成菌] [$chunkX%d,$chunkZ%d] 菌种落入土层，菌丝开始重新结网（菌网:${MandatePhenomena.sustenanceWord(r.sustenance)}，养复需时）"
      case MandatePhenomena.ReInoculationOutcome.AcidTooToxic =>
        f"§c[接种失败] [$chunkX%d,$chunkZ%d] 土层酸毒未退，菌种落土即死——须先泄压降酸再接种（菌网:${MandatePhenomena.sustenanceWord(r.sustenance)}）"
      case MandatePhenomena.ReInoculationOutcome.AlreadyViable =>
        f"§7[接种无益] [$chunkX%d,$chunkZ%d] 此区块菌网尚存活丝（${MandatePhenomena.sustenanceWord(r.sustenance)}），可自行养复"

  /** Player-facing wording for a slow-variable regime change: what the land is
    * doing, not which enum member it moved to.
    */
  def regimeHerald(regime: Regime): String = regime match
    case Regime.Burnout => "这一带的地脉饱和失能了，得花大力气泄压才能缓回来"
    case Regime.Oscillating => "这一带的地脉开始跳，压不住了"
    case Regime.Strained => "这一带的地脉绷紧了，像要出事"
    case Regime.Calm => "这一带的地脉松下来了"

  /** Clock/compass readout (验山): the institutional dial plus the 3-tier
    * mycelial integrity word, never raw continuous numbers (design note 06
    * §1 constraint 5). Raw values ride the `debugNumbers` gate only. */
  def formatInspection(chunkX: Int, chunkZ: Int, r: CellReadout): String =
    val shelter = if r.isMaintained then "§a庇护正常(免除沉滞)" else "§c沉滞生效(缺c0或尖峰失效)"
    val base =
      f"§e[验山读数] [$chunkX%d,$chunkZ%d] 态:${regimeWord(r.regime)} 相:${formatPhaseDial(r.phase)} 菌网:${MandatePhenomena.sustenanceWord(r.sustenance)} | $shelter"
    if debugNumbers then
      val fracText = r.phase match
        case PhaseReading.Cycle(_, frac) => f" frac=$frac%.3f"
        case _ => ""
      base +
        f" §8[s=${r.current.stress}%.3f c0=${r.baselineCalming}%.3f q=${r.current.activity}%.3f M=${r.sustenance}%.2f v=${r.current.dissonance}%.2f$fracText]"
    else base

  /** Qualitative regime word for player-facing surfaces; enum names are
    * debug vocabulary. */
  def regimeWord(regime: Regime): String = regime match
    case Regime.Calm => "平静"
    case Regime.Strained => "绷紧"
    case Regime.Oscillating => "起振"
    case Regime.Burnout => "饱和失能"

  /** The institutional dial: 20 ticks per cycle, resolution-limited by
    * design (note 06 §1 constraint 5). Enum labels and fractions stay in
    * `formatPhaseShort` for the dev HUD. */
  def formatPhaseDial(phase: PhaseReading): String =
    phase match
      case PhaseReading.Cycle(label, _) =>
        val tick = Readout.dialTick(phase).getOrElse(0)
        val word = label match
          case PhaseLabel.Excitation => "激发"
          case PhaseLabel.AbsoluteRefractory => "绝对不应"
          case PhaseLabel.RelativeRefractory => "相对不应"
          case PhaseLabel.Resting => "静息"
        f"${word}·第$tick%02d/20格"
      case PhaseReading.Basin(_) => "静息域"
      case PhaseReading.Undefined(reason) =>
        reason match
          case UndefinedReason.BurnoutNoCycle => "静默·深度失能"
          case UndefinedReason.TransientNoCycle => "瞬态未定"
          case UndefinedReason.NoCycleReference => "无周期参照"

  def formatBellPulse(chunkX: Int, chunkZ: Int, r: CellReadout): String =
    formatBellPulse(chunkX, chunkZ, r, evaluateBellStrike(r))

  def formatBellPulse(
      chunkX: Int,
      chunkZ: Int,
      r: CellReadout,
      outcome: BellStrikeOutcome
  ): String =
    outcome match
      case BellStrikeOutcome.SaturatedNoAnchor =>
        f"§4[叩钟无应] [$chunkX%d,$chunkZ%d] 地脉饱和失能 (Burnout)，钟声无应——需先泄压方可重锚 (c0=0.00)"

      case BellStrikeOutcome.SpikeSwallowed =>
        val tick = r.dialTick.getOrElse(0)
        f"§c[叩钟失锚] [$chunkX%d,$chunkZ%d] 刻度:第$tick%02d/20格 (Excitation) -> 正处尖峰爆发期，钟声被吞没，c0重锚失败(需候不应期推迟窗口)"

      case BellStrikeOutcome.RefractoryReanchored =>
        val tick = r.dialTick.getOrElse(0)
        val label = r.phase match
          case PhaseReading.Cycle(l, _) => l.toString
          case _ => "Refractory"
        f"§a[叩钟重锚] [$chunkX%d,$chunkZ%d] 刻度:第$tick%02d/20格 ($label) -> 命中推迟刻度，c0已重锚 ($BellBaselineCalming%.2f) 且下一次尖峰延后"

      case BellStrikeOutcome.AnchoredOrMaintained =>
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

  /** Dev rendering (HUD, `debugNumbers`): enum labels and the raw fraction.
    * Player-facing surfaces use `formatPhaseDial` instead. */
  def formatPhaseShort(phase: PhaseReading): String =
    phase match
      case PhaseReading.Cycle(label, frac) =>
        val tick = Readout.dialTick(phase).getOrElse(0)
        f"$label($frac%.2f/第$tick%02d格)"
      case PhaseReading.Basin(label) =>
        s"Basin($label)"
      case PhaseReading.Undefined(reason) =>
        s"Undef($reason)"
