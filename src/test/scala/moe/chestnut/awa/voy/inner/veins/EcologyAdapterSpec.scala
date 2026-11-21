package moe.chestnut.awa.voy.inner.veins

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.VoyConfig
import moe.chestnut.awa.voy.mandate.{MandateField, MandatePhenomena, PrototypeValleyField}

/** Verification of the four ecological slow-subsystem adapters (`Mountain`,
  * `Swamp`, `Permafrost`, `Desert`):
  *
  *  - Biome path classification (`EcologyKind.fromBiomePath`);
  *  - Dynamical separation under the shared 4D RK4 + Fisher-KPP equations;
  *  - Spatial coupling across adjacent ecological cells in `MandateGrid`;
  *  - Multi-parameter persistence round-trip in `MandateSnapshot`.
  */
class EcologyAdapterSpec:
  private val fast = MandateAgent.DefaultFastParam
  private val dt = 0.05

  @Test def `biome path classifier maps vanilla and modded biome ids to the four adapters`(): Unit =
    assertEquals(EcologyKind.Swamp, EcologyKind.fromBiomePath("swamp"))
    assertEquals(EcologyKind.Swamp, EcologyKind.fromBiomePath("mangrove_swamp"))
    assertEquals(EcologyKind.Swamp, EcologyKind.fromBiomePath("peat_bog"))

    assertEquals(EcologyKind.Permafrost, EcologyKind.fromBiomePath("snowy_plains"))
    assertEquals(EcologyKind.Permafrost, EcologyKind.fromBiomePath("ice_spikes"))
    assertEquals(EcologyKind.Permafrost, EcologyKind.fromBiomePath("frozen_peaks"))
    assertEquals(EcologyKind.Permafrost, EcologyKind.fromBiomePath("grove"))

    assertEquals(EcologyKind.Desert, EcologyKind.fromBiomePath("desert"))
    assertEquals(EcologyKind.Desert, EcologyKind.fromBiomePath("eroded_badlands"))
    assertEquals(EcologyKind.Desert, EcologyKind.fromBiomePath("arid_dunes"))

    assertEquals(EcologyKind.Mountain, EcologyKind.fromBiomePath("stony_peaks"))
    assertEquals(EcologyKind.Mountain, EcologyKind.fromBiomePath("Meadow"))
    assertEquals(EcologyKind.Mountain, EcologyKind.fromBiomePath("cherry_grove"))

  @Test def `swamp wetland buffers a neighbouring stressed mountain cell better than mountain alone`(): Unit =
    // Pair A: Stressed mountain (s = 0.35) coupled to a healthy mountain neighbour (s = 0.10)
    val mtnStressedA = MandateState(
      MandateCurrent(-1.2, -0.6, 0.35, 0.0),
      fast,
      StressParam.forEcology(EcologyKind.Mountain)
    )
    val mtnBuffer = MandateState(
      MandateCurrent(-1.2, -0.6, 0.10, 0.0),
      fast,
      StressParam.forEcology(EcologyKind.Mountain)
    )
    val gridMtn = MandateGrid(
      Map(CellCoord(0, 0) -> mtnStressedA, CellCoord(1, 0) -> mtnBuffer),
      Seq(LatticeEdge(CellCoord(0, 0), CellCoord(1, 0), diffusion = 0.005))
    )

    // Pair B: Stressed mountain (s = 0.35) coupled to a healthy swamp wetland neighbour (s = 0.10)
    val mtnStressedB = MandateState(
      MandateCurrent(-1.2, -0.6, 0.35, 0.0),
      fast,
      StressParam.forEcology(EcologyKind.Mountain)
    )
    val swampBuffer = MandateState(
      MandateCurrent(-1.2, -0.6, 0.10, 0.0),
      fast,
      StressParam.forEcology(EcologyKind.Swamp)
    )
    val gridSwamp = MandateGrid(
      Map(CellCoord(0, 0) -> mtnStressedB, CellCoord(1, 0) -> swampBuffer),
      Seq(LatticeEdge(CellCoord(0, 0), CellCoord(1, 0), diffusion = 0.005))
    )

    var i = 0
    while i < 4000 do // 200 sim seconds
      gridMtn.step(dt)
      gridSwamp.step(dt)
      i += 1

    assertTrue(
      swampBuffer.getState.stress < mtnBuffer.getState.stress,
      s"swamp buffer should digest diffused stress faster (${swampBuffer.getState.stress} vs ${mtnBuffer.getState.stress})"
    )
    assertTrue(
      mtnStressedB.getState.stress < mtnStressedA.getState.stress,
      s"mountain cell next to a healthy swamp should experience stronger relief (${mtnStressedB.getState.stress} vs ${mtnStressedA.getState.stress})"
    )

  @Test def `swamp peat smoldering escalates stress faster and burns sustenance faster once ignited`(): Unit =
    val mtn = MandateState(
      MandateCurrent(2.0, -0.2, 0.75, 0.25),
      fast,
      StressParam.forEcology(EcologyKind.Mountain)
    )
    val swamp = MandateState(
      MandateCurrent(2.0, -0.2, 0.75, 0.25),
      fast,
      StressParam.forEcology(EcologyKind.Swamp)
    )
    var i = 0
    while i < 1200 do // 60 sim seconds of unforced smoldering (u = 0)
      mtn.step(dt)
      swamp.step(dt)
      i += 1

    assertTrue(
      swamp.getState.stress > mtn.getState.stress,
      s"hydrophobic peat smoldering should escalate stress faster (${swamp.getState.stress} vs ${mtn.getState.stress})"
    )
    assertTrue(
      swamp.sustenance < mtn.sustenance,
      s"peat smoldering above threshold should consume the moss mat faster (${swamp.sustenance} vs ${mtn.sustenance})"
    )

  @Test def `permafrost exhibits thermal inertia under early disturbance and deeper collapse at M=0`(): Unit =
    val mtn = MandateState(
      MandateCurrent(-1.2, -0.6, 0.0, 0.0),
      fast,
      StressParam.forEcology(EcologyKind.Mountain)
    )
    val permafrost = MandateState(
      MandateCurrent(-1.2, -0.6, 0.0, 0.0),
      fast,
      StressParam.forEcology(EcologyKind.Permafrost)
    )
    mtn.disturbance = 0.04
    permafrost.disturbance = 0.04

    var i = 0
    while i < 600 do // 30 sim seconds
      mtn.step(dt)
      permafrost.step(dt)
      i += 1

    assertTrue(
      permafrost.getState.stress < mtn.getState.stress * 0.65,
      s"permafrost thermal inertia should slow initial stress build-up (${permafrost.getState.stress} vs ${mtn.getState.stress})"
    )

    // At M = 0, permafrost thermokarst depth (1.35) pushes slowA deeper below the knee than mountain (1.20)
    val aMtnDead = Readout.slowA(fast, stress = 0.0, sustenance = 0.0, depth = StressParam.OverworldDefault.sustenanceDepth)
    val aPermaDead = Readout.slowA(fast, stress = 0.0, sustenance = 0.0, depth = StressParam.PermafrostDefault.sustenanceDepth)
    assertTrue(aPermaDead < aMtnDead)
    assertTrue(aPermaDead <= Readout.burnoutA(fast))

  @Test def `desert biological soil crust degrades at lower stress and blocks premature re-inoculation`(): Unit =
    // At s = 0.50 (Strained), mountain mycelium (s_T = 0.60) does not burn,
    // whereas desert BSC (s_T = 0.45) already suffers wind-erosion abrasion.
    val mtn = MandateState(
      MandateCurrent(-1.2, -0.6, 0.50, 0.0),
      fast,
      StressParam.forEcology(EcologyKind.Mountain)
    )
    val desert = MandateState(
      MandateCurrent(-1.2, -0.6, 0.50, 0.0),
      fast,
      StressParam.forEcology(EcologyKind.Desert)
    )
    // Freeze stress at 0.50 to isolate the sustenance equation
    mtn.stressParam = mtn.stressParam.copy(gamma = 0.0)
    desert.stressParam = desert.stressParam.copy(gamma = 0.0)

    var i = 0
    while i < 2000 do
      mtn.step(dt)
      desert.step(dt)
      i += 1

    assertEquals(1.0, mtn.sustenance, 1e-9, "mountain mycelium is unharmed at s = 0.50 < 0.60")
    assertTrue(desert.sustenance < 1.0, s"desert BSC must erode at s = 0.50 > 0.45, got ${desert.sustenance}")

    // Re-inoculation check via CellReadout toxicityThreshold:
    val desertDepletedAt050 = CellReadout.zero.copy(
      current = MandateCurrent(-1.2, -0.6, 0.50, 0.0),
      sustenance = 0.0,
      sustenanceFloor = StressParam.DesertBscDefault.sustenanceFloor,
      toxicityThreshold = StressParam.DesertBscDefault.sustenanceBurnThreshold,
      sustenanceDepth = StressParam.DesertBscDefault.sustenanceDepth
    )
    assertEquals(
      MandatePhenomena.ReInoculationOutcome.AcidTooToxic,
      MandatePhenomena.evaluateReInoculation(desertDepletedAt050),
      "desert crust cannot be re-inoculated while wind-shear stress s = 0.50 >= 0.45"
    )

  @Test def `mixed-ecology lattice preserves per-cell adapter parameters across snapshot round-trip`(): Unit =
    val cfg = VoyConfig()
    val grid = PrototypeValleyField.buildInitialGrid(
      cfg,
      (cx, cz) =>
        (Math.floorMod(cx, 2), Math.floorMod(cz, 2)) match
          case (0, 0) => EcologyKind.Mountain
          case (1, 0) => EcologyKind.Swamp
          case (0, 1) => EcologyKind.Permafrost
          case _      => EcologyKind.Desert
    )
    val snap = MandateSnapshot.capture(grid, Nil, simTime = 12.5)
    val sets = MandateSnapshot.parameterSets(snap)
    assertEquals(4, sets.size, "all four ecological StressParam presets should be present in the snapshot")

    val restored = MandateSnapshot.restoreGrid(snap)
    for (coord, origCell) <- grid.cells do
      assertEquals(origCell.stressParam, restored.cells(coord).stressParam)
