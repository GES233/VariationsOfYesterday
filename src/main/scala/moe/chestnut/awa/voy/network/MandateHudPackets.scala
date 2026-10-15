package moe.chestnut.awa.voy.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import moe.chestnut.awa.voy.inner.mandate.MandateCurrent

/** Network payloads for synchronising the development Mandate HUD between
  * dedicated/integrated servers and clients.
  */
object MandateHudPackets:
  val DefaultRadius = 2

  /** C2S: client notifies server whether `/voy hud` is enabled. */
  final case class HudSubscribePayload(enabled: Boolean) extends CustomPayload:
    override def getId: CustomPayload.Id[? <: CustomPayload] =
      HudSubscribePayload.Id

  object HudSubscribePayload:
    val Id: CustomPayload.Id[HudSubscribePayload] =
      new CustomPayload.Id(Identifier.of("variations-of-yesterday", "hud_subscribe"))

    val Codec: PacketCodec[RegistryByteBuf, HudSubscribePayload] =
      CustomPayload.codecOf(
        (payload: HudSubscribePayload, buf: RegistryByteBuf) =>
          buf.writeBoolean(payload.enabled),
        (buf: RegistryByteBuf) =>
          HudSubscribePayload(buf.readBoolean())
      )

  /** S2C: server streams nearby chunk mandate states + simulation clock to
    * subscribed HUD clients.
    */
  final case class HudSnapshotPayload(
      simTime: Double,
      centerChunkX: Int,
      centerChunkZ: Int,
      radius: Int,
      cells: Vector[MandateCurrent]
  ) extends CustomPayload:
    override def getId: CustomPayload.Id[? <: CustomPayload] =
      HudSnapshotPayload.Id

  object HudSnapshotPayload:
    val Id: CustomPayload.Id[HudSnapshotPayload] =
      new CustomPayload.Id(Identifier.of("variations-of-yesterday", "hud_snapshot"))

    val Codec: PacketCodec[RegistryByteBuf, HudSnapshotPayload] =
      CustomPayload.codecOf(
        (payload: HudSnapshotPayload, buf: RegistryByteBuf) =>
          buf.writeDouble(payload.simTime)
          buf.writeVarInt(payload.centerChunkX)
          buf.writeVarInt(payload.centerChunkZ)
          buf.writeVarInt(payload.radius)
          buf.writeVarInt(payload.cells.size)
          payload.cells.foreach { c =>
            buf.writeDouble(c.dissonance)
            buf.writeDouble(c.entrench)
            buf.writeDouble(c.stress)
            buf.writeDouble(c.activity)
          }
        ,
        (buf: RegistryByteBuf) =>
          val simTime = buf.readDouble()
          val cx = buf.readVarInt()
          val cz = buf.readVarInt()
          val radius = buf.readVarInt()
          val n = buf.readVarInt()
          val builder = Vector.newBuilder[MandateCurrent]
          builder.sizeHint(n)
          var i = 0
          while i < n do
            builder += MandateCurrent(
              dissonance = buf.readDouble(),
              entrench = buf.readDouble(),
              stress = buf.readDouble(),
              activity = buf.readDouble()
            )
            i += 1
          HudSnapshotPayload(simTime, cx, cz, radius, builder.result())
      )

  private var registered = false

  /** Register payload types on both sides (idempotent). */
  def registerCommon(): Unit = synchronized {
    if !registered then
      PayloadTypeRegistry.playC2S().register(HudSubscribePayload.Id, HudSubscribePayload.Codec)
      PayloadTypeRegistry.playS2C().register(HudSnapshotPayload.Id, HudSnapshotPayload.Codec)
      registered = true
  }
