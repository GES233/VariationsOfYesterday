package moe.chestnut.awa.voy

import io.netty.buffer.Unpooled
import net.minecraft.network.RegistryByteBuf
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.util.Identifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.inner.veins.*
import moe.chestnut.awa.voy.mandate.*

class MandateNetworkingSpec:
  private val Overworld = Identifier.of("minecraft", "overworld")

  private def sampleReadout(
      excitation: Double = 0.4,
      stress: Double = 0.50,
      activity: Double = 0.15,
      sustenance: Double = 0.72,
      baselineCalming: Double = 0.03,
      phase: PhaseReading = PhaseReading.Cycle(PhaseLabel.Excitation, 0.25),
      sheltered: Boolean = true
  ): CellReadout =
    CellReadout(
      current = MandateCurrent(excitation, 0.1, stress, activity),
      regime = if stress >= 0.95 then Regime.Burnout else if stress >= 0.45 then Regime.Strained else Regime.Calm,
      phase = phase,
      sustenance = sustenance,
      baselineCalming = baselineCalming,
      pulseDrive = 0.1,
      rhythm =
        if sheltered then RhythmVerdict.Beating(40.0, 0.08, RhythmSource.Intrinsic)
        else RhythmVerdict.Slow(Double.NaN),
      lastBellStrikeAt = 4.0,
      simTime = 12.0
    )

  @Test
  def payloadCodecRoundTripsPublicAndDebugEntries(): Unit =
    val r1 = sampleReadout(
      excitation = 2.0,
      stress = 0.52,
      phase = PhaseReading.Cycle(PhaseLabel.Excitation, 0.25),
      sheltered = true
    )
    val r2 = sampleReadout(
      excitation = 0.1,
      stress = 0.10,
      sustenance = 0.0,
      baselineCalming = 0.0,
      phase = PhaseReading.Undefined(UndefinedReason.SlowOffsetBelowKnee),
      sheltered = false
    )
    val payload = MandateChunkSyncPayload(
      replaceAll = true,
      cells = Vector(
        SyncedCellEntry(ClientCellView.fromReadout(-3, 7, r1), None),
        SyncedCellEntry(
          ClientCellView.fromReadout(4, -2, r2),
          Some(DebugCellTelemetry.fromReadout(r2))
        )
      )
    )

    val buf = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY)
    try
      MandateChunkSyncPayload.Codec.encode(buf, payload)
      val decoded = MandateChunkSyncPayload.Codec.decode(buf)
      assertEquals(payload, decoded)
      assertEquals(0, buf.readableBytes())
    finally buf.release()

  @Test
  def computeSyncBatchFiltersLiveChunksAndEmitsDeltas(): Unit =
    val isLive = (cx: Int, cz: Int) => cx >= 0 && cx <= 1 && cz >= 0 && cz <= 1
    var stress00 = 0.10
    val readoutAt = (cx: Int, cz: Int) =>
      if cx == 0 && cz == 0 then sampleReadout(stress = stress00)
      else sampleReadout(stress = 0.20)

    // 1. Initial sync -> replaceAll = true, only live chunks inside radius
    val (p1, s1) = MandateNetworking.computeSyncBatch(
      prev = None,
      dimId = Overworld,
      centerX = 0,
      centerZ = 0,
      radius = 2,
      includeDebug = false,
      isLive = isLive,
      readoutAt = readoutAt
    )
    assertTrue(p1.isDefined)
    assertTrue(p1.get.replaceAll)
    assertEquals(4, p1.get.cells.size)
    assertTrue(p1.get.cells.forall(_.debug.isEmpty))

    // 2. No change -> None
    val (p2, s2) = MandateNetworking.computeSyncBatch(
      prev = Some(s1),
      dimId = Overworld,
      centerX = 0,
      centerZ = 0,
      radius = 2,
      includeDebug = false,
      isLive = isLive,
      readoutAt = readoutAt
    )
    assertTrue(p2.isEmpty)

    // 3. Single cell regime changes -> replaceAll = false with 1 delta cell
    stress00 = 0.60
    val (p3, s3) = MandateNetworking.computeSyncBatch(
      prev = Some(s2),
      dimId = Overworld,
      centerX = 0,
      centerZ = 0,
      radius = 2,
      includeDebug = false,
      isLive = isLive,
      readoutAt = readoutAt
    )
    assertTrue(p3.isDefined)
    assertFalse(p3.get.replaceAll)
    assertEquals(1, p3.get.cells.size)
    assertEquals((0, 0), (p3.get.cells.head.view.chunkX, p3.get.cells.head.view.chunkZ))
    assertEquals(Regime.Strained, p3.get.cells.head.view.regime)

    // 4. Player moves to adjacent chunk -> replaceAll = true window refresh
    val (p4, _) = MandateNetworking.computeSyncBatch(
      prev = Some(s3),
      dimId = Overworld,
      centerX = 1,
      centerZ = 0,
      radius = 2,
      includeDebug = false,
      isLive = isLive,
      readoutAt = readoutAt
    )
    assertTrue(p4.isDefined)
    assertTrue(p4.get.replaceAll)
    assertEquals(4, p4.get.cells.size)
