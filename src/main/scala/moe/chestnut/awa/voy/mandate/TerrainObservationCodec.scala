package moe.chestnut.awa.voy.mandate

import com.mojang.serialization.{Codec, DataResult}
import net.minecraft.nbt.NbtCompound
import moe.chestnut.awa.voy.inner.veins.TerrainObservation
import scala.util.control.NonFatal

/** Versioned codec independent of attachment registration, for round-trip tests. */
object TerrainObservationCodec:
  val codec: Codec[TerrainObservation] = NbtCompound.CODEC.flatXmap(
    nbt =>
      try
        require(nbt.getInt("version") == 1, "Unsupported terrain sample version")
        DataResult.success(TerrainObservation(
          nbt.getString("stage"), nbt.getString("generator"),
          nbt.getInt("bottomY"), nbt.getInt("topY"),
          nbt.getIntArray("surface").toVector, nbt.getIntArray("ground").toVector,
          nbt.getIntArray("fluidDepth").toVector
        ))
      catch case NonFatal(e) => DataResult.error(() => s"Invalid terrain sample: ${e.getMessage}"),
    sample =>
      val nbt = new NbtCompound()
      nbt.putInt("version", 1)
      nbt.putString("stage", sample.stage)
      nbt.putString("generator", sample.generator)
      nbt.putInt("bottomY", sample.bottomY)
      nbt.putInt("topY", sample.topY)
      nbt.putIntArray("surface", sample.surface.toArray)
      nbt.putIntArray("ground", sample.ground.toArray)
      nbt.putIntArray("fluidDepth", sample.fluidDepth.toArray)
      DataResult.success(nbt)
  )
