package moe.chestnut.awa.voy

import java.nio.file.{Files, Path}

import com.mojang.datafixers.{DSL, DataFixer}

import moe.chestnut.awa.voy.mandate.MandatePersistence
import com.mojang.datafixers.schemas.Schema
import com.mojang.serialization.Dynamic
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import net.minecraft.SharedConstants
import net.minecraft.world.PersistentStateManager

import moe.chestnut.awa.voy.inner.veins.{
  CellCoord,
  CellSnapshot,
  LatticeEdge,
  MandateCurrent,
  MandateParam,
  MandateSnapshot,
  PulseLink,
  PulseToleranceState,
  PulseTransmitter,
  StressParam
}

/** NBT round-trip of the world-persistence codec: a snapshot serialized
  * into a `NbtCompound` and read back through the registered
  * `PersistentState.Type` deserializer must come back exactly, and a
  * payload from an unknown schema version must be discarded whole.
  */
class MandatePersistenceSpec:
  private val geometry = (-16, -16, 32, 32)

  private val sample = MandateSnapshot(
    simTime = 54321.25,
    cells = Seq(
      CellSnapshot(
        CellCoord(0, 0),
        MandateCurrent(-1.5, -0.7, 0.42, 0.03),
        sustenance = 0.25,
        disturbance = 0.08,
        calming = 0.01,
        baselineCalming = 0.03,
        impulse = -0.2,
        fastParam = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
        stressParam =
          StressParam(sustenanceGrowth = 1e-3, sustenanceBurn = 4e-3),
        pulseState = PulseToleranceState(
          lastPulseTime = 54300.0,
          suppressedUntil = 54336.0,
          overloadUntil = Double.NegativeInfinity,
          tolerance = 0.25
        )
      ),
      CellSnapshot(
        CellCoord(31, 31),
        MandateCurrent(0.9, 0.4, 0.0, 0.0),
        sustenance = 0.0, // a dead patch the world must remember
        disturbance = 0.0,
        calming = 0.0,
        baselineCalming = 0.0,
        impulse = 0.0,
        fastParam = MandateParam(alpha = 0.65, beta = 0.75, epsilon = 0.1),
        stressParam = StressParam(coupling = 1.4, stressMax = 0.9)
      )
    ),
    edges = Seq(
      LatticeEdge(
        CellCoord(0, 0),
        CellCoord(1, 0),
        diffusion = 0.002,
        advection = 0.001
      )
    ),
    links = Seq(
      PulseLink(CellCoord(0, 0), CellCoord(31, 31)),
      PulseLink(
        CellCoord(31, 31),
        CellCoord(0, 0),
        area = 0.6,
        transmitter = PulseTransmitter.Bell,
        delay = 2.5
      )
    )
  )

  @Test def `snapshot survives the NBT round trip exactly`(): Unit =
    val nbt = MandatePersistence.toNbt(geometry, sample)
    val loaded = MandatePersistence.StateType.deserializer.apply(nbt, null)

    assertEquals(geometry, loaded.geometry)
    assertTrue(loaded.snapshot.isDefined, "a valid payload must deserialize")
    assertEquals(sample, loaded.snapshot.get)

  @Test def `an unknown schema version is discarded whole`(): Unit =
    val nbt = MandatePersistence.toNbt(geometry, sample)
    nbt.putInt("schemaVersion", MandatePersistence.SchemaVersion + 1)
    val loaded = MandatePersistence.StateType.deserializer.apply(nbt, null)

    assertTrue(
      loaded.snapshot.isEmpty,
      "an incompatible payload must fall back to a fresh lattice"
    )

  // ==== The real load path (regression: the carrier's DataFixTypes) ==== //
  //
  // The two tests above call the deserializer directly, which never touches
  // `PersistentStateManager` — and that is exactly where a null
  // `DataFixTypes` used to kill every reload: `readNbt` dereferences it
  // unconditionally, the NPE is caught and logged, the manager returns a
  // FRESH state, and the next save overwrites the old file.

  /** No-op data fixer: our payload has no vanilla schema, so the level fixer
    * must hand it back untouched; the test is about the manager's plumbing.
    */
  private val noFixer: DataFixer = new DataFixer:
    def update[T](
        `type`: DSL.TypeReference,
        input: Dynamic[T],
        version: Int,
        newVersion: Int
    ): Dynamic[T] = input
    def getSchema(version: Int): Schema = null

  private def newManager(dir: Path): PersistentStateManager =
    new PersistentStateManager(dir, noFixer, null)

  @Test def `a second manager loads the saved lattice back`(): Unit =
    // `readNbt` stamps the game's save version into the payload.
    SharedConstants.createGameVersion()
    val dir = Files.createTempDirectory(Path.of("build"), "persist-")

    val first = newManager(dir)
    val state = first.getOrCreate(MandatePersistence.StateType, MandatePersistence.Id)
    state.geometry = geometry
    state.snapshot = Some(sample)
    state.markDirty()
    first.save()

    // A fresh manager is what a server restart looks like.
    val second = newManager(dir)
    val loaded =
      second.getOrCreate(MandatePersistence.StateType, MandatePersistence.Id)

    assertEquals(geometry, loaded.geometry)
    assertEquals(Some(sample), loaded.snapshot)
