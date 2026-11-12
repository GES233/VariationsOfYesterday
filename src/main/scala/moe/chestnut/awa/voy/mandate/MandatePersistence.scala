package moe.chestnut.awa.voy.mandate

import net.minecraft.datafixer.DataFixTypes
import net.minecraft.nbt.{NbtCompound, NbtElement, NbtList}
import net.minecraft.registry.RegistryWrapper
import net.minecraft.world.PersistentState

import moe.chestnut.awa.voy.inner.veins.{
  CellCoord,
  CellSnapshot,
  LatticeEdge,
  MandateCurrent,
  MandateParam,
  MandateSnapshot,
  OverloadLock,
  PulseLedger,
  PulseLink,
  PulseTransmitter,
  ShelterWindow,
  StressParam,
  ToleranceBook
}

/** Dimension-scoped carrier for the mandate lattice snapshot, saved with
  * the world via `PersistentStateManager`. The manager writes dirty states on
  * world save (autosave and shutdown), so the flow is:
  *
  *  - the actor publishes a fresh `MandateSnapshot` on a slow cadence and
  *    marks this state dirty;
  *  - at save time `writeNbt` serializes the latest published snapshot;
  *  - on engine start the runtime restores lattice, links and `simTime`
  *    from `snapshot` — but only if `geometry` matches the runtime's
  *    lattice constants, so a prototype-geometry change discards a stale
  *    save loudly instead of silently misplacing cells.
  *
  * `schemaVersion` is the format version of the NBT payload itself.
  */
final class MandatePersistence extends PersistentState:
  /** `(originX, originZ, width, height)` of the lattice the snapshot was
    * captured under; loaded from NBT or set by the runtime before the
    * first save. Server-thread only (engine start and save), unlike
    * `snapshot`.
    */
  var geometry: (Int, Int, Int, Int) = (0, 0, 0, 0)

  /** Latest published snapshot; `None` before the engine first reports.
    * Written by the ENGINE ACTOR thread (`persistSink`) and read by the
    * server thread at save time, hence `@volatile`: the reference swap is
    * the entire handoff (the snapshot itself is immutable).
    */
  @volatile var snapshot: Option[MandateSnapshot] = None

  override def writeNbt(
      nbt: NbtCompound,
      registryLookup: RegistryWrapper.WrapperLookup
  ): NbtCompound =
    snapshot.foreach { snap =>
      MandatePersistence.writeSnapshot(nbt, geometry, snap)
    }
    nbt

object MandatePersistence:
  /** Saved-data id: `data/voy_mandate.dat` inside the dimension folder. */
  val Id = "voy_mandate"

  /** Format version of the NBT payload; bump on incompatible changes. */
  val SchemaVersion = 1

  val StateType: PersistentState.Type[MandatePersistence] =
    new PersistentState.Type[MandatePersistence](
      () => new MandatePersistence,
      (nbt, _) => fromNbt(nbt),
      // This slot is the data-fix CARRIER, not a schema claim about our
      // payload: the compound is ours, no vanilla fixer knows its keys, so
      // the level fixer leaves it untouched. It must NOT be null —
      // `PersistentStateManager.readNbt` dereferences it unconditionally on
      // load, and a null here makes every reload throw (caught, logged as
      // "Error loading saved data"), silently reset the lattice and then
      // overwrite the stale save. `MandatePersistenceSpec` round-trips
      // through the real manager to keep this honest.
      DataFixTypes.LEVEL
    )

  // ==== NBT codec (kept pure so it round-trips in unit tests) ====

  /** Serialize a snapshot under a fresh root compound. */
  def toNbt(
      geometry: (Int, Int, Int, Int),
      snapshot: MandateSnapshot
  ): NbtCompound =
    writeSnapshot(NbtCompound(), geometry, snapshot)

  private def writeSnapshot(
      nbt: NbtCompound,
      geometry: (Int, Int, Int, Int),
      snapshot: MandateSnapshot
  ): NbtCompound =
    nbt.putInt("schemaVersion", SchemaVersion)
    nbt.putInt("originX", geometry._1)
    nbt.putInt("originZ", geometry._2)
    nbt.putInt("width", geometry._3)
    nbt.putInt("height", geometry._4)
    nbt.putDouble("simTime", snapshot.simTime)

    val cells = NbtList()
    snapshot.cells.foreach { cs =>
      val c = NbtCompound()
      c.putInt("x", cs.coord.x)
      c.putInt("z", cs.coord.z)
      c.putDouble("dissonance", cs.current.dissonance)
      c.putDouble("entrench", cs.current.entrench)
      c.putDouble("stress", cs.current.stress)
      c.putDouble("activity", cs.current.activity)
      c.putDouble("sustenance", cs.sustenance)
      c.putDouble("disturbance", cs.disturbance)
      c.putDouble("calming", cs.calming)
      c.putDouble("baselineCalming", cs.baselineCalming)
      c.putDouble("impulse", cs.impulse)
      c.putDouble("alpha", cs.fastParam.alpha)
      c.putDouble("beta", cs.fastParam.beta)
      c.putDouble("epsilon", cs.fastParam.epsilon)
      val sp = cs.stressParam
      c.putDouble("gamma", sp.gamma)
      c.putDouble("delta0", sp.delta0)
      c.putDouble("sHalf", sp.sHalf)
      c.putDouble("rho", sp.rho)
      c.putDouble("eta", sp.eta)
      c.putDouble("lambda", sp.lambda)
      c.putDouble("vSpike", sp.vSpike)
      c.putDouble("coupling", sp.coupling)
      c.putDouble("stressMax", sp.stressMax)
      c.putDouble("sustenanceDepth", sp.sustenanceDepth)
      c.putDouble("sustenanceGrowth", sp.sustenanceGrowth)
      c.putDouble("sustenanceBurn", sp.sustenanceBurn)
      c.putDouble("sustenanceBurnThreshold", sp.sustenanceBurnThreshold)
      c.putDouble("sustenanceFloor", sp.sustenanceFloor)
      // Pulse ledger: the three mechanisms keep their own saved fact. The
      // NBT key names are the ones the former fused state used — the payload
      // shape did not change, only which owner each number belongs to.
      val pl = cs.pulseLedger
      c.putDouble("lastPulseTime", pl.tolerance.lastPulseTime)
      c.putDouble("suppressedUntil", pl.shelter.until)
      c.putDouble("overloadUntil", pl.overload.until)
      c.putDouble("pulseTolerance", pl.tolerance.amount)
      cells.add(c)
    }
    nbt.put("cells", cells)

    val edges = NbtList()
    snapshot.edges.foreach { e =>
      val c = NbtCompound()
      c.putInt("fromX", e.from.x)
      c.putInt("fromZ", e.from.z)
      c.putInt("toX", e.to.x)
      c.putInt("toZ", e.to.z)
      c.putDouble("diffusion", e.diffusion)
      c.putDouble("advection", e.advection)
      edges.add(c)
    }
    nbt.put("edges", edges)

    val links = NbtList()
    snapshot.links.foreach { l =>
      val c = NbtCompound()
      c.putInt("srcX", l.src.x)
      c.putInt("srcZ", l.src.z)
      c.putInt("dstX", l.dst.x)
      c.putInt("dstZ", l.dst.z)
      c.putDouble("area", l.area)
      c.putDouble("delay", l.delay)
      c.putString("transmitter", l.transmitter.toString)
      links.add(c)
    }
    nbt.put("links", links)
    nbt

  private def fromNbt(nbt: NbtCompound): MandatePersistence =
    val state = MandatePersistence()
    if nbt.isEmpty then return state
    // A payload from an incompatible format version is discarded: the
    // runtime falls back to a fresh lattice, never to a half-parsed one.
    if nbt.getInt("schemaVersion") != SchemaVersion then return state
    state.geometry =
      (nbt.getInt("originX"), nbt.getInt("originZ"), nbt.getInt("width"), nbt.getInt("height"))

    val cells = nbt.getList("cells", NbtElement.COMPOUND_TYPE).toArray.map {
      case c: NbtCompound =>
        CellSnapshot(
          CellCoord(c.getInt("x"), c.getInt("z")),
          MandateCurrent(
            c.getDouble("dissonance"),
            c.getDouble("entrench"),
            c.getDouble("stress"),
            c.getDouble("activity")
          ),
          sustenance = c.getDouble("sustenance"),
          disturbance = c.getDouble("disturbance"),
          calming = c.getDouble("calming"),
          baselineCalming = c.getDouble("baselineCalming"),
          impulse = c.getDouble("impulse"),
          fastParam = MandateParam(
            c.getDouble("alpha"),
            c.getDouble("beta"),
            c.getDouble("epsilon")
          ),
          stressParam = StressParam(
            gamma = c.getDouble("gamma"),
            delta0 = c.getDouble("delta0"),
            sHalf = c.getDouble("sHalf"),
            rho = c.getDouble("rho"),
            eta = c.getDouble("eta"),
            lambda = c.getDouble("lambda"),
            vSpike = c.getDouble("vSpike"),
            coupling = c.getDouble("coupling"),
            stressMax = c.getDouble("stressMax"),
            sustenanceDepth = c.getDouble("sustenanceDepth"),
            sustenanceGrowth = c.getDouble("sustenanceGrowth"),
            sustenanceBurn = c.getDouble("sustenanceBurn"),
            sustenanceBurnThreshold = c.getDouble("sustenanceBurnThreshold"),
            sustenanceFloor = c.getDouble("sustenanceFloor")
          ),
          // Absent keys mean "never pulsed on this cell": the ledger reads as
          // inert (no window, no load, no lockout). No migration path is
          // written here — this project has no old saves to carry over.
          pulseLedger = PulseLedger(
            shelter = ShelterWindow(
              if c.contains("suppressedUntil") then c.getDouble("suppressedUntil")
              else Double.NegativeInfinity
            ),
            tolerance = ToleranceBook(
              amount =
                if c.contains("pulseTolerance") then c.getDouble("pulseTolerance")
                else 0.0,
              lastPulseTime =
                if c.contains("lastPulseTime") then c.getDouble("lastPulseTime")
                else Double.NegativeInfinity
            ),
            overload = OverloadLock(
              if c.contains("overloadUntil") then c.getDouble("overloadUntil")
              else Double.NegativeInfinity
            )
          )
        )
    }.toSeq

    val edges = nbt.getList("edges", NbtElement.COMPOUND_TYPE).toArray.map {
      case e: NbtCompound =>
        LatticeEdge(
          CellCoord(e.getInt("fromX"), e.getInt("fromZ")),
          CellCoord(e.getInt("toX"), e.getInt("toZ")),
          diffusion = e.getDouble("diffusion"),
          advection = e.getDouble("advection")
        )
    }.toSeq

    val links = nbt.getList("links", NbtElement.COMPOUND_TYPE).toArray.map {
      case l: NbtCompound =>
        PulseLink(
          CellCoord(l.getInt("srcX"), l.getInt("srcZ")),
          CellCoord(l.getInt("dstX"), l.getInt("dstZ")),
          area = l.getDouble("area"),
          transmitter = PulseTransmitter.valueOf(l.getString("transmitter")),
          delay = l.getDouble("delay")
        )
    }.toSeq

    state.snapshot = Some(
      MandateSnapshot(nbt.getDouble("simTime"), cells, edges, links)
    )
    state
