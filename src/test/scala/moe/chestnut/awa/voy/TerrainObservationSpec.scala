package moe.chestnut.awa.voy

import moe.chestnut.awa.voy.inner.veins.TerrainObservation
import moe.chestnut.awa.voy.mandate.TerrainObservationCodec
import net.minecraft.nbt.{NbtCompound, NbtOps}
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TerrainObservationSpec:
  private def sample = TerrainObservation("surface_stage", "example.CustomGenerator", -64, 319,
    Vector.fill(256)(63), Vector.fill(256)(50), Vector.fill(256)(13))

  @Test def preservesEveryColumnAndProvenanceThroughNbt(): Unit =
    val codec = TerrainObservationCodec.codec
    val encoded = codec.encodeStart(NbtOps.INSTANCE, sample).getOrThrow()
    assertEquals(sample, codec.parse(NbtOps.INSTANCE, encoded).getOrThrow())

  @Test def malformedAndFutureSamplesAreRejected(): Unit =
    val codec = TerrainObservationCodec.codec
    val encoded = codec.encodeStart(NbtOps.INSTANCE, sample).getOrThrow().asInstanceOf[NbtCompound]
    encoded.putInt("version", 2)
    assertTrue(codec.parse(NbtOps.INSTANCE, encoded).error().isPresent)
    encoded.putInt("version", 1)
    encoded.putIntArray("ground", Array(1, 2))
    assertTrue(codec.parse(NbtOps.INSTANCE, encoded).error().isPresent)

  @Test def voidColumnsDoNotMasqueradeAsLowGround(): Unit =
    val void = sample.copy(surface = Vector.fill(256)(-65), ground = Vector.fill(256)(-65), fluidDepth = Vector.fill(256)(0))
    assertTrue(void.medianGround.isEmpty)
    val mixed = void.copy(surface = void.surface.updated(0, 60), ground = void.ground.updated(0, 60))
    assertEquals(Some(60.0), mixed.medianGround)

  @Test def comparisonCountsColumnsAndRejectsDifferentVerticalDomains(): Unit =
    val observed = sample.copy(stage = "first_full_observation", ground = sample.ground.updated(3, 51), fluidDepth = sample.fluidDepth.updated(3, 12))
    assertEquals(Some(1), sample.changedColumns(observed))
    assertEquals(None, sample.changedColumns(observed.copy(topY = 320)))

  @Test def dimensionPolicyIsExplicitAndDisableWins(): Unit =
    assertTrue(TerrainCaptureConfig().allows("minecraft:overworld"))
    assertFalse(TerrainCaptureConfig().allows("minecraft:the_nether"))
    assertTrue(TerrainCaptureConfig(dimensions = Set("example:valley")).allows("example:valley"))
    assertFalse(TerrainCaptureConfig(enabled = false).allows("minecraft:overworld"))

  @Test def columnScanSeparatesFluidSurfaceFromBedAndSkipsIgnoredCover(): Unit =
    val column = TerrainObservation.scanColumn(-64, 80) { y =>
      if y > 63 then (false, false) // Air, leaves and ignored modded cover.
      else if y > 50 then (false, true) // Any fluid, not just vanilla water.
      else (true, false)
    }
    assertEquals((63, 50, 13), column)

  @Test def waterloggedGroundIsNotMistakenForAnOpenWaterColumn(): Unit =
    assertEquals((60, 60, 0), TerrainObservation.scanColumn(-64, 60)(_ => (true, true)))
    assertEquals((-65, -65, 0), TerrainObservation.scanColumn(-64, -65)(_ => fail("Void must not be read")))
