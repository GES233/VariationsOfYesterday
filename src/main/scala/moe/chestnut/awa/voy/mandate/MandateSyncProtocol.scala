package moe.chestnut.awa.voy.mandate

import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

import moe.chestnut.awa.voy.inner.veins.*

/** Qualitative, spoiler-safe view of a live mandate cell sent to clients
  * within render/sync distance.
  *
  * Raw continuous state (`v`, `s`, `q`, `M`) is intentionally excluded here so
  * production clients cannot read exact dynamical numbers out of memory
  * (design note 06 §5 WO-1).
  */
final case class ClientCellView(
    chunkX: Int,
    chunkZ: Int,
    regime: Regime,
    mycelium: MycelialHealth,
    isSheltered: Boolean,
    isSpiking: Boolean,
    isBurnoutPhase: Boolean,
    c0Sheltered: Boolean,
    seepTier: MandatePhenomena.YellowSeepTier,
    dialTick: Option[Int],
    phaseLabel: Option[PhaseLabel],
    /** Heartbeat category wire ordinal (`Heartbeat.wireOrdinal`), packed into
      * the flags byte (bits 4-6). Available to every player, not just debug.
      */
    heartbeatOrdinal: Int = 0
)

object ClientCellView:
  def fromReadout(
      chunkX: Int,
      chunkZ: Int,
      readout: CellReadout
  ): ClientCellView =
    val phaseLabel = readout.phase match
      case PhaseReading.Cycle(lbl, _) => Some(lbl)
      case PhaseReading.Basin(lbl)    => Some(lbl)
      case PhaseReading.Undefined(_)  => None
    ClientCellView(
      chunkX = chunkX,
      chunkZ = chunkZ,
      regime = readout.regime,
      mycelium = readout.mycelium,
      isSheltered = readout.isSheltered,
      isSpiking = readout.isSpiking,
      isBurnoutPhase = MandatePhenomena.hasSilentBlight(readout),
      c0Sheltered = readout.baselineCalming >= MandatePhenomena.C0ShelterLevel,
      seepTier = MandatePhenomena.classifySeepTier(readout),
      dialTick = readout.dialTick,
      phaseLabel = phaseLabel,
      heartbeatOrdinal = Heartbeat.wireOrdinal(readout.heartbeat)
    )

/** Full numerical telemetry attached only for dev environments or OP players. */
final case class DebugCellTelemetry(
    dissonance: Double,
    stress: Double,
    activity: Double,
    sustenance: Double,
    baselineCalming: Double,
    pulseDrive: Double,
    simTime: Double,
    phaseKind: Byte,
    phaseFraction: Double,
    undefinedReason: Option[UndefinedReason],
    // Heartbeat axis observability (design note 11): precise period/duty live
    // here; the category rides on ClientCellView's flags byte for everyone.
    // Options, not NaN sentinels: IEEE NaN != NaN would break payload equality.
    beatPeriod: Option[Double] = None,
    beatDuty: Option[Double] = None
):
  def phaseReading(label: Option[PhaseLabel]): PhaseReading =
    phaseKind match
      case 0 => PhaseReading.Cycle(label.getOrElse(PhaseLabel.Resting), phaseFraction)
      case 1 => PhaseReading.Basin(label.getOrElse(PhaseLabel.Resting))
      case _ => PhaseReading.Undefined(undefinedReason.getOrElse(UndefinedReason.NoCycleReference))

object DebugCellTelemetry:
  def fromReadout(readout: CellReadout): DebugCellTelemetry =
    val (kind, frac, reason) = readout.phase match
      case PhaseReading.Cycle(_, f)  => (0.toByte, f, None)
      case PhaseReading.Basin(_)     => (1.toByte, 0.0, None)
      case PhaseReading.Undefined(r) => (2.toByte, 0.0, Some(r))
    DebugCellTelemetry(
      dissonance = readout.current.dissonance,
      stress = readout.current.stress,
      activity = readout.current.activity,
      sustenance = readout.sustenance,
      baselineCalming = readout.baselineCalming,
      pulseDrive = readout.pulseDrive,
      simTime = readout.simTime,
      phaseKind = kind,
      phaseFraction = frac,
      undefinedReason = reason,
      beatPeriod = Heartbeat.periodOf(readout.heartbeat),
      beatDuty = Heartbeat.dutyOf(readout.heartbeat)
    )

final case class SyncedCellEntry(
    view: ClientCellView,
    debug: Option[DebugCellTelemetry]
)

/** S2C payload synchronizing live mandate cells in a player's render/sync
  * radius.
  */
final case class MandateChunkSyncPayload(
    replaceAll: Boolean,
    cells: Vector[SyncedCellEntry]
) extends CustomPayload:
  override def getId: CustomPayload.Id[? <: CustomPayload] = MandateChunkSyncPayload.Id

object MandateChunkSyncPayload:
  val PayloadId: Identifier = Identifier.of("variations-of-yesterday", "mandate_chunk_sync")
  val Id: CustomPayload.Id[MandateChunkSyncPayload] =
    new CustomPayload.Id[MandateChunkSyncPayload](PayloadId)

  private val FlagSheltered = 1 << 0
  private val FlagSpiking = 1 << 1
  private val FlagBurnoutPhase = 1 << 2
  private val FlagC0Sheltered = 1 << 3

  val Codec: PacketCodec[RegistryByteBuf, MandateChunkSyncPayload] =
    new PacketCodec[RegistryByteBuf, MandateChunkSyncPayload]:
      override def decode(buf: RegistryByteBuf): MandateChunkSyncPayload =
        val replaceAll = buf.readBoolean()
        val count = buf.readVarInt()
        val builder = Vector.newBuilder[SyncedCellEntry]
        builder.sizeHint(count)
        var i = 0
        while i < count do
          builder += decodeEntry(buf)
          i += 1
        MandateChunkSyncPayload(replaceAll, builder.result())

      override def encode(buf: RegistryByteBuf, value: MandateChunkSyncPayload): Unit =
        buf.writeBoolean(value.replaceAll)
        buf.writeVarInt(value.cells.size)
        value.cells.foreach(encodeEntry(buf, _))

  private def encodeEntry(buf: RegistryByteBuf, entry: SyncedCellEntry): Unit =
    val v = entry.view
    buf.writeInt(v.chunkX)
    buf.writeInt(v.chunkZ)
    buf.writeByte(v.regime.ordinal)
    buf.writeByte(v.mycelium.ordinal)
    val flags =
      (if v.isSheltered then FlagSheltered else 0) |
        (if v.isSpiking then FlagSpiking else 0) |
        (if v.isBurnoutPhase then FlagBurnoutPhase else 0) |
        (if v.c0Sheltered then FlagC0Sheltered else 0) |
        ((v.heartbeatOrdinal & 0x7) << 4)
    buf.writeByte(flags)
    buf.writeByte(v.seepTier.ordinal)
    buf.writeByte(v.dialTick.getOrElse(-1))
    buf.writeByte(v.phaseLabel.map(_.ordinal).getOrElse(-1))
    entry.debug match
      case None =>
        buf.writeBoolean(false)
      case Some(d) =>
        buf.writeBoolean(true)
        buf.writeDouble(d.dissonance)
        buf.writeDouble(d.stress)
        buf.writeDouble(d.activity)
        buf.writeDouble(d.sustenance)
        buf.writeDouble(d.baselineCalming)
        buf.writeDouble(d.pulseDrive)
        buf.writeDouble(d.simTime)
        buf.writeByte(d.phaseKind)
        buf.writeDouble(d.phaseFraction)
        buf.writeByte(d.undefinedReason.map(_.ordinal).getOrElse(-1))
        buf.writeBoolean(d.beatPeriod.isDefined)
        d.beatPeriod.foreach(buf.writeDouble)
        buf.writeBoolean(d.beatDuty.isDefined)
        d.beatDuty.foreach(buf.writeDouble)

  private def decodeEntry(buf: RegistryByteBuf): SyncedCellEntry =
    val chunkX = buf.readInt()
    val chunkZ = buf.readInt()
    val regime = Regime.fromOrdinal(buf.readByte() & 0xff)
    val mycelium = MycelialHealth.fromOrdinal(buf.readByte() & 0xff)
    val flags = buf.readByte() & 0xff
    val seepTier = MandatePhenomena.YellowSeepTier.fromOrdinal(buf.readByte() & 0xff)
    val rawDial = buf.readByte().toInt
    val dialTick = if rawDial < 0 then None else Some(rawDial)
    val rawLabel = buf.readByte().toInt
    val phaseLabel = if rawLabel < 0 then None else Some(PhaseLabel.fromOrdinal(rawLabel))
    val view = ClientCellView(
      chunkX = chunkX,
      chunkZ = chunkZ,
      regime = regime,
      mycelium = mycelium,
      isSheltered = (flags & FlagSheltered) != 0,
      isSpiking = (flags & FlagSpiking) != 0,
      isBurnoutPhase = (flags & FlagBurnoutPhase) != 0,
      c0Sheltered = (flags & FlagC0Sheltered) != 0,
      seepTier = seepTier,
      dialTick = dialTick,
      phaseLabel = phaseLabel,
      heartbeatOrdinal = (flags >> 4) & 0x7
    )
    val hasDebug = buf.readBoolean()
    val debug =
      if !hasDebug then None
      else
        val dissonance = buf.readDouble()
        val stress = buf.readDouble()
        val activity = buf.readDouble()
        val sustenance = buf.readDouble()
        val baselineCalming = buf.readDouble()
        val pulseDrive = buf.readDouble()
        val simTime = buf.readDouble()
        val phaseKind = buf.readByte()
        val phaseFraction = buf.readDouble()
        val rawReason = buf.readByte().toInt
        val undefinedReason =
          if rawReason < 0 then None else Some(UndefinedReason.fromOrdinal(rawReason))
        val beatPeriod = if buf.readBoolean() then Some(buf.readDouble()) else None
        val beatDuty = if buf.readBoolean() then Some(buf.readDouble()) else None
        Some(
          DebugCellTelemetry(
            dissonance = dissonance,
            stress = stress,
            activity = activity,
            sustenance = sustenance,
            baselineCalming = baselineCalming,
            pulseDrive = pulseDrive,
            simTime = simTime,
            phaseKind = phaseKind,
            phaseFraction = phaseFraction,
            undefinedReason = undefinedReason,
            beatPeriod = beatPeriod,
            beatDuty = beatDuty
          )
        )
    SyncedCellEntry(view, debug)
