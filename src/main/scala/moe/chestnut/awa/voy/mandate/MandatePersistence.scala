package moe.chestnut.awa.voy.mandate

import net.minecraft.datafixer.DataFixTypes
import net.minecraft.nbt.{NbtCompound, NbtDouble, NbtElement, NbtList}
import net.minecraft.registry.RegistryWrapper
import net.minecraft.world.PersistentState
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.veins.{
  BeatStats,
  CellCoord,
  CellSnapshot,
  LatticeEdge,
  MandateCurrent,
  MandateParam,
  MandateSnapshot,
  MandateSource,
  MandateSourceKind,
  PulseLink,
  PulseTransmitter,
  StressParam
}

/** Dimension-scoped carrier for the mandate lattice snapshot, saved with
  * the world via `PersistentStateManager`.
  */
final class MandatePersistence extends PersistentState:
  var geometry: (Int, Int, Int, Int) = (0, 0, 0, 0)
  @volatile var snapshot: Option[MandateSnapshot] = None

  /** Registered calming sources (design note 11 ruling 11): the chunk ↔
    * source map that feeds the baseline-calming stock. Additive field: saves
    * without it simply start with an empty registry and re-scan the world on
    * the next rebalance.
    */
  var sources: List[MandateSource] = Nil

  override def writeNbt(
      nbt: NbtCompound,
      registryLookup: RegistryWrapper.WrapperLookup
  ): NbtCompound =
    snapshot.foreach { snap =>
      MandatePersistence.writeSnapshot(nbt, geometry, snap)
      MandatePersistence.writeSources(nbt, sources)
      MandatePersistence.logger.info(
        f"Saved mandate lattice (${snap.cells.size}%d cells, ${snap.links.size}%d links, simTime = ${snap.simTime}%.2f)."
      )
    }
    nbt

object MandatePersistence:
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  val Id = "voy_mandate"
  val SchemaVersion = 3

  val StateType: PersistentState.Type[MandatePersistence] =
    new PersistentState.Type[MandatePersistence](
      () => new MandatePersistence,
      (nbt, _) => fromNbt(nbt),
      DataFixTypes.LEVEL
    )

  def toNbt(
      geometry: (Int, Int, Int, Int),
      snapshot: MandateSnapshot,
      sources: List[MandateSource] = Nil
  ): NbtCompound =
    val nbt = writeSnapshot(NbtCompound(), geometry, snapshot)
    writeSources(nbt, sources)
    nbt

  /** Write the calming-source registry (additive, schema-3 compatible). */
  private def writeSources(nbt: NbtCompound, sources: List[MandateSource]): Unit =
    val list = NbtList()
    sources.foreach { s =>
      val c = NbtCompound()
      c.putString("kind", s.kind.toString)
      c.putInt("x", s.x)
      c.putInt("y", s.y)
      c.putInt("z", s.z)
      list.add(c)
    }
    nbt.put("sources", list)

  private def readSources(nbt: NbtCompound): List[MandateSource] =
    if !nbt.contains("sources") then Nil
    else
      nbt.getList("sources", NbtElement.COMPOUND_TYPE).toArray.toList.collect {
        case c: NbtCompound =>
          MandateSource(
            MandateSourceKind.valueOf(c.getString("kind")),
            c.getInt("x"),
            c.getInt("y"),
            c.getInt("z")
          )
      }

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
      // Heartbeat rhythm memory (schema 3, design note 11 risk 2). NaN
      // sentinels round-trip through NBT doubles unchanged.
      val periodList = NbtList()
      cs.beatStats.periods.foreach(per => periodList.add(NbtDouble.of(per)))
      c.put("beatPeriods", periodList)
      c.putDouble("beatDuty", cs.beatStats.lastDuty)
      c.putDouble("lastCross", cs.beatStats.lastCross)
      c.putDouble("bornAt", cs.beatStats.bornAt)
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
    val savedSchema = nbt.getInt("schemaVersion")
    if savedSchema != SchemaVersion then
      logger.warn(
        s"Discarding saved mandate lattice with schemaVersion=$savedSchema (expected $SchemaVersion)."
      )
      return state
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
          beatStats = BeatStats(
            periods = c.getList("beatPeriods", NbtElement.DOUBLE_TYPE).toArray.map {
              case d: NbtDouble => d.doubleValue()
            }.toVector,
            lastDuty = c.getDouble("beatDuty"),
            lastCross = c.getDouble("lastCross"),
            bornAt = c.getDouble("bornAt")
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
    state.sources = readSources(nbt)
    state
