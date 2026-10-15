package moe.chestnut.awa.voy.block

import com.mojang.serialization.MapCodec
import net.minecraft.block.{AbstractBlock, Block, BlockState, HorizontalFacingBlock, ShapeContext}
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.{ItemPlacementContext, ItemStack, Items}
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.{SoundCategory, SoundEvents}
import net.minecraft.state.StateManager
import net.minecraft.state.property.{BooleanProperty, EnumProperty, Properties}
import net.minecraft.text.Text
import net.minecraft.util.{ActionResult, BlockMirror, BlockRotation, Formatting, Hand, StringIdentifiable}
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.{BlockPos, ChunkSectionPos, Direction}
import net.minecraft.util.math.random.Random
import net.minecraft.util.shape.{VoxelShape, VoxelShapes}
import net.minecraft.world.{BlockView, World}
import net.minecraft.world.block.WireOrientation

import moe.chestnut.awa.voy.MandateRuntime
import moe.chestnut.awa.voy.inner.mandate.{ExcitablePhase, MandateReadout, StressRegime}

/** Discrete blockstate mirror of [[ExcitablePhase]] for driving the 3D block
  * model pose (plumb bob deflection, reed vibration, and indicator lamp).
  */
enum PlumbPhase(val id: String) extends Enum[PlumbPhase] with StringIdentifiable:
  case Resting extends PlumbPhase("resting")
  case Refractory extends PlumbPhase("refractory")
  case Vulnerable extends PlumbPhase("vulnerable")
  case Excitation extends PlumbPhase("excitation")

  override def asString(): String = id

object PlumbPhase:
  def fromExcitable(phase: ExcitablePhase): PlumbPhase = phase match
    case ExcitablePhase.Resting            => PlumbPhase.Resting
    case ExcitablePhase.AbsoluteRefractory => PlumbPhase.Refractory
    case ExcitablePhase.RelativeRefractory => PlumbPhase.Vulnerable
    case ExcitablePhase.Excitation         => PlumbPhase.Excitation

/** **Telluric Plumb Frame（地脉验山仪 / 验山垂架）** — 1980s field-survey instrument
  * combining a timber A-frame plumb bob, a bronze resonance reed + calibration
  * gong, and an olive-drab acoustic monitor box (design draft §1.3, §2.2).
  *
  * Gameplay & testing functions:
  *   - **Live phase pose**: polls `MandateReadout.classify` every 4 ticks and
  *     switches its `phase` blockstate (`resting` / `refractory` / `vulnerable`
  *     / `excitation`), changing the plumb bob angle, indicator lamp, and
  *     ambient particles.
  *   - **Right-click (empty hand)**: inspect the local chunk's phase & stress
  *     via a 1980s survey log readout in the action bar.
  *   - **Sneak + Right-click (or Redstone pulse)**: strike the bronze
  *     calibration gong, firing an alpha synaptic pulse (`FirePulse`) into the
  *     chunk's fast variable.
  *   - **Right-click holding Gunpowder or TNT**: inject a simulated blasting
  *     disturbance (`StressInject(+0.28)`) into the chunk for rapid in-game
  *     testing.
  *   - **Comparator output**: emits 1–3 (`Resting`), 7 (`AbsoluteRefractory`
  *     delay window), 11 (`RelativeRefractory` vulnerable window), or 15
  *     (`Excitation` spike), allowing redstone circuits to automate phase-locked
  *     bell strikes (§3.2 "scientific replication of witchcraft").
  */
class TelluricPlumbBlock(settings: AbstractBlock.Settings) extends Block(settings):
  import TelluricPlumbBlock.*

  setDefaultState(
    getStateManager.getDefaultState
      .`with`(FACING, Direction.NORTH)
      .`with`(POWERED, java.lang.Boolean.FALSE)
      .`with`(PHASE, PlumbPhase.Resting)
  )

  override protected def getCodec: MapCodec[? <: Block] = CODEC

  override protected def appendProperties(
      builder: StateManager.Builder[Block, BlockState]
  ): Unit =
    builder.add(FACING, POWERED, PHASE)

  override def getPlacementState(ctx: ItemPlacementContext): BlockState =
    getDefaultState.`with`(FACING, ctx.getHorizontalPlayerFacing.getOpposite)

  override protected def getOutlineShape(
      state: BlockState,
      world: BlockView,
      pos: BlockPos,
      context: ShapeContext
  ): VoxelShape = FrameShape

  override protected def onBlockAdded(
      state: BlockState,
      world: World,
      pos: BlockPos,
      oldState: BlockState,
      notify: Boolean
  ): Unit =
    if !world.isClient then
      world.scheduleBlockTick(pos, this, PollIntervalTicks)

  override def onPlaced(
      world: World,
      pos: BlockPos,
      state: BlockState,
      placer: LivingEntity,
      itemStack: ItemStack
  ): Unit =
    if !world.isClient then
      syncPhaseFromMandate(world, pos, state)
      world.scheduleBlockTick(pos, this, PollIntervalTicks)

  override protected def scheduledTick(
      state: BlockState,
      world: ServerWorld,
      pos: BlockPos,
      random: Random
  ): Unit =
    syncPhaseFromMandate(world, pos, state)
    world.scheduleBlockTick(pos, this, PollIntervalTicks)

  override protected def neighborUpdate(
      state: BlockState,
      world: World,
      pos: BlockPos,
      sourceBlock: Block,
      wireOrientation: WireOrientation,
      notify: Boolean
  ): Unit =
    if world.isClient then return
    val receiving = world.isReceivingRedstonePower(pos)
    val wasPowered = state.get(POWERED).booleanValue()
    if receiving != wasPowered then
      val nextState = state.`with`(POWERED, java.lang.Boolean.valueOf(receiving))
      world.setBlockState(pos, nextState, Block.NOTIFY_LISTENERS)
      if receiving then
        strikeCalibrationGong(world, pos, None)

  override protected def onUseWithItem(
      stack: ItemStack,
      state: BlockState,
      world: World,
      pos: BlockPos,
      player: PlayerEntity,
      hand: Hand,
      hit: BlockHitResult
  ): ActionResult =
    if stack.isOf(Items.GUNPOWDER) || stack.isOf(Items.TNT) then
      if !world.isClient then
        val chunkX = ChunkSectionPos.getSectionCoord(pos.getX)
        val chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ)
        MandateRuntime.injectStressAt(chunkX, chunkZ, TestStressInjection)
        world.playSound(
          null,
          pos,
          SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
          SoundCategory.BLOCKS,
          0.45f,
          0.55f
        )
        val updated = syncPhaseFromMandate(world, pos, state)
        val current = MandateRuntime.stateAt(chunkX, chunkZ)
        player.sendMessage(
          Text
            .translatable(
              "message.variations-of-yesterday.telluric_plumb.blast_injected",
              f"${current.stress}%.2f"
            )
            .formatted(Formatting.GOLD),
          true
        )
      ActionResult.SUCCESS
    else
      ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION

  override protected def onUse(
      state: BlockState,
      world: World,
      pos: BlockPos,
      player: PlayerEntity,
      hit: BlockHitResult
  ): ActionResult =
    if world.isClient then return ActionResult.SUCCESS
    if player.isSneaking then
      strikeCalibrationGong(world, pos, Some(player))
    else
      val updated = syncPhaseFromMandate(world, pos, state)
      inspectReadout(world, pos, updated, player)
    ActionResult.SUCCESS

  override def randomDisplayTick(
      state: BlockState,
      world: World,
      pos: BlockPos,
      random: Random
  ): Unit =
    val cx = pos.getX + 0.5
    val cy = pos.getY + 0.55
    val cz = pos.getZ + 0.5
    state.get(PHASE) match
      case PlumbPhase.Resting =>
        if random.nextInt(5) == 0 then
          world.addParticle(
            ParticleTypes.MYCELIUM,
            cx + (random.nextDouble() - 0.5) * 0.25,
            cy,
            cz + (random.nextDouble() - 0.5) * 0.25,
            0.0,
            0.0,
            0.0
          )
      case PlumbPhase.Refractory =>
        // Inward-sinking mist ("死寂回吸 / 退潮期")
        for _ <- 0 until 2 do
          val ox = (random.nextDouble() - 0.5) * 0.7
          val oz = (random.nextDouble() - 0.5) * 0.7
          world.addParticle(
            ParticleTypes.WHITE_ASH,
            cx + ox,
            pos.getY + 0.25 + random.nextDouble() * 0.5,
            cz + oz,
            -ox * 0.08,
            -0.02,
            -oz * 0.08
          )
      case PlumbPhase.Vulnerable =>
        // High-frequency standing-wave sparks ("蓄势躁动 / 易损窗口")
        world.addParticle(
          ParticleTypes.ELECTRIC_SPARK,
          cx + (random.nextDouble() - 0.5) * 0.2,
          cy + (random.nextDouble() - 0.5) * 0.2,
          cz + (random.nextDouble() - 0.5) * 0.2,
          0.0,
          0.02,
          0.0
        )
        if random.nextInt(4) == 0 then
          world.playSound(
            cx,
            cy,
            cz,
            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
            SoundCategory.BLOCKS,
            0.25f,
            1.75f,
            false
          )
      case PlumbPhase.Excitation =>
        // Active surge tremor ("尖峰爆发")
        for _ <- 0 until 3 do
          world.addParticle(
            ParticleTypes.SMOKE,
            cx + (random.nextDouble() - 0.5) * 0.5,
            cy + (random.nextDouble() - 0.5) * 0.3,
            cz + (random.nextDouble() - 0.5) * 0.5,
            (random.nextDouble() - 0.5) * 0.05,
            0.04,
            (random.nextDouble() - 0.5) * 0.05
          )
        world.addParticle(ParticleTypes.LAVA, cx, cy, cz, 0.0, 0.05, 0.0)

  override protected def hasComparatorOutput(state: BlockState): Boolean = true

  override protected def getComparatorOutput(
      state: BlockState,
      world: World,
      pos: BlockPos
  ): Int =
    val chunkX = ChunkSectionPos.getSectionCoord(pos.getX)
    val chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ)
    val current = MandateRuntime.stateAt(chunkX, chunkZ)
    val readout = MandateReadout.classify(current)
    readout.phase match
      case ExcitablePhase.Excitation         => 15
      case ExcitablePhase.RelativeRefractory => 11
      case ExcitablePhase.AbsoluteRefractory => 7
      case ExcitablePhase.Resting =>
        Math.min(3, Math.max(1, Math.round(current.stress * 6.0).toInt + 1))

  override protected def rotate(
      state: BlockState,
      rotation: BlockRotation
  ): BlockState =
    state.`with`(FACING, rotation.rotate(state.get(FACING)))

  override protected def mirror(
      state: BlockState,
      mirror: BlockMirror
  ): BlockState =
    state.rotate(mirror.getRotation(state.get(FACING)))

  private def syncPhaseFromMandate(
      world: World,
      pos: BlockPos,
      state: BlockState
  ): BlockState =
    val chunkX = ChunkSectionPos.getSectionCoord(pos.getX)
    val chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ)
    val current = MandateRuntime.stateAt(chunkX, chunkZ)
    val readout = MandateReadout.classify(current)
    val targetPhase = PlumbPhase.fromExcitable(readout.phase)
    val nextState =
      if state.get(PHASE) != targetPhase then
        val updated = state.`with`(PHASE, targetPhase)
        world.setBlockState(pos, updated, Block.NOTIFY_ALL)
        updated
      else state
    world.updateComparators(pos, this)
    nextState

  private def strikeCalibrationGong(
      world: World,
      pos: BlockPos,
      player: Option[PlayerEntity]
  ): Unit =
    val chunkX = ChunkSectionPos.getSectionCoord(pos.getX)
    val chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ)
    val before = MandateReadout.classify(MandateRuntime.stateAt(chunkX, chunkZ))
    MandateRuntime.firePulseAt(chunkX, chunkZ, GongPulseArea)

    val pitch = before.phase match
      case ExcitablePhase.AbsoluteRefractory => 0.85f // deep resonant delay tone
      case ExcitablePhase.RelativeRefractory => 1.55f // sharp dissonant warning tone
      case ExcitablePhase.Excitation         => 0.60f
      case ExcitablePhase.Resting            => 1.15f

    world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 0.9f, pitch)
    world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.7f, pitch)

    world match
      case sw: ServerWorld =>
        sw.spawnParticles(
          ParticleTypes.NOTE,
          pos.getX + 0.5,
          pos.getY + 0.95,
          pos.getZ + 0.5,
          6,
          0.25,
          0.15,
          0.25,
          0.5
        )
      case _ => ()

    player.foreach { p =>
      val hintKey = before.phase match
        case ExcitablePhase.AbsoluteRefractory =>
          "message.variations-of-yesterday.telluric_plumb.gong_refractory"
        case ExcitablePhase.RelativeRefractory =>
          "message.variations-of-yesterday.telluric_plumb.gong_vulnerable"
        case ExcitablePhase.Excitation =>
          "message.variations-of-yesterday.telluric_plumb.gong_excitation"
        case ExcitablePhase.Resting =>
          "message.variations-of-yesterday.telluric_plumb.gong_resting"
      val color = before.phase match
        case ExcitablePhase.AbsoluteRefractory => Formatting.AQUA
        case ExcitablePhase.RelativeRefractory => Formatting.RED
        case ExcitablePhase.Excitation         => Formatting.LIGHT_PURPLE
        case ExcitablePhase.Resting            => Formatting.GREEN
      p.sendMessage(Text.translatable(hintKey).formatted(color), true)
    }

  private def inspectReadout(
      world: World,
      pos: BlockPos,
      state: BlockState,
      player: PlayerEntity
  ): Unit =
    val chunkX = ChunkSectionPos.getSectionCoord(pos.getX)
    val chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ)
    val current = MandateRuntime.stateAt(chunkX, chunkZ)
    val readout = MandateReadout.classify(current)

    val (pitch, msgKey, color) = readout.phase match
      case ExcitablePhase.Resting =>
        (1.0f, "message.variations-of-yesterday.telluric_plumb.read_resting", Formatting.GRAY)
      case ExcitablePhase.AbsoluteRefractory =>
        (0.75f, "message.variations-of-yesterday.telluric_plumb.read_refractory", Formatting.AQUA)
      case ExcitablePhase.RelativeRefractory =>
        (1.65f, "message.variations-of-yesterday.telluric_plumb.read_vulnerable", Formatting.GOLD)
      case ExcitablePhase.Excitation =>
        (0.55f, "message.variations-of-yesterday.telluric_plumb.read_excitation", Formatting.RED)

    world.playSound(
      null,
      pos,
      SoundEvents.BLOCK_COPPER_BULB_TURN_ON,
      SoundCategory.BLOCKS,
      0.8f,
      pitch
    )
    player.sendMessage(
      Text
        .translatable(
          msgKey,
          f"${current.stress}%.2f",
          f"${current.activity}%.2f",
          readout.regime.toString
        )
        .formatted(color),
      true
    )

object TelluricPlumbBlock:
  val CODEC: MapCodec[TelluricPlumbBlock] =
    AbstractBlock.createCodec(new TelluricPlumbBlock(_))

  val FACING: EnumProperty[Direction] = Properties.HORIZONTAL_FACING
  val POWERED: BooleanProperty = Properties.POWERED
  val PHASE: EnumProperty[PlumbPhase] = EnumProperty.of("phase", classOf[PlumbPhase])

  private val PollIntervalTicks = 4
  private val GongPulseArea = Math.E * 5.0
  private val TestStressInjection = 0.28

  private val BaseShape: VoxelShape =
    Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 3.0, 15.0)
  private val PillarsAndBoxShape: VoxelShape =
    Block.createCuboidShape(2.0, 3.0, 2.0, 14.0, 16.0, 14.0)
  val FrameShape: VoxelShape = VoxelShapes.union(BaseShape, PillarsAndBoxShape)
