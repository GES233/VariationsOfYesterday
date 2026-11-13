package moe.chestnut.awa.voy

import java.nio.file.{Files, Path}

import com.google.gson.{GsonBuilder, JsonObject, JsonParser}
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.veins.{MandateAgent, MandateParam, PulseParam, StressParam}

/** Geometry and tuning of the prototype lattice. Chunk-grained: the lattice
  * spans chunks `[originX, originX + width)` x `[originZ, originZ + height)`.
  */
final case class LatticeConfig(
    originX: Int = -16,
    originZ: Int = -16,
    width: Int = 32,
    height: Int = 32,
    diffusion: Double = 0.002,
    hotspotRadius: Double = 2.0,
    hotspotDisturbance: Double = 0.08,
    ambientDisturbance: Double = 0.002,
    calmingRadius: Double = 5.0,
    baselineCalming: Double = 0.03,
    initialStressPeak: Double = 0.18,
    initialStressFalloff: Double = 0.004
)

/** External runtime configuration (`config/variations-of-yesterday.json`).
  *
  * Injected at the wiring layer (`MandateRuntime`), never at the simulation
  * core. Restore precedence on world load: save > config > core default.
  */
final case class VoyConfig(
    tickDt: Double = 0.05,
    lattice: LatticeConfig = LatticeConfig(),
    fastParam: MandateParam = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
    stressParam: StressParam =
      StressParam(sustenanceGrowth = 1e-3, sustenanceBurn = 4e-3),
    pulseParam: PulseParam = PulseParam()
)

object VoyConfig:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val FileName = "variations-of-yesterday.json"

  @volatile private var currentV: VoyConfig = VoyConfig()
  def current: VoyConfig = currentV

  def reload(): VoyConfig =
    val path = FabricLoader.getInstance().getConfigDir.resolve(FileName)
    currentV = loadFrom(path)
    logger.info(s"Mandate config: $currentV")
    currentV

  def loadFrom(path: Path): VoyConfig =
    if !Files.exists(path) then
      val defaults = VoyConfig()
      try
        Files.writeString(path, defaultsJson(defaults))
      catch
        case e: Exception =>
          logger.warn(s"Could not write default config to $path: $e")
      return defaults

    try
      val root = JsonParser.parseString(Files.readString(path)).getAsJsonObject
      VoyConfig(
        tickDt = dbl(root, "tickDt", 0.05),
        lattice = latticeFrom(obj(root, "lattice")),
        fastParam = fastFrom(obj(root, "fastParam")),
        stressParam = stressFrom(obj(root, "stressParam")),
        pulseParam = pulseFrom(obj(root, "pulseParam"))
      )
    catch
      case e: Exception =>
        logger.warn(s"Unreadable config at $path ($e); using defaults.")
        VoyConfig()

  private def dbl(obj: JsonObject, key: String, fallback: Double): Double =
    if obj != null && obj.has(key) && obj.get(key).isJsonPrimitive then
      obj.get(key).getAsDouble
    else fallback

  private def int(obj: JsonObject, key: String, fallback: Int): Int =
    if obj != null && obj.has(key) && obj.get(key).isJsonPrimitive then
      obj.get(key).getAsInt
    else fallback

  private def obj(root: JsonObject, key: String): JsonObject =
    if root.has(key) && root.get(key).isJsonObject then root.getAsJsonObject(key)
    else null

  private def latticeFrom(o: JsonObject): LatticeConfig =
    val d = LatticeConfig()
    LatticeConfig(
      originX = int(o, "originX", d.originX),
      originZ = int(o, "originZ", d.originZ),
      width = int(o, "width", d.width),
      height = int(o, "height", d.height),
      diffusion = dbl(o, "diffusion", d.diffusion),
      hotspotRadius = dbl(o, "hotspotRadius", d.hotspotRadius),
      hotspotDisturbance = dbl(o, "hotspotDisturbance", d.hotspotDisturbance),
      ambientDisturbance = dbl(o, "ambientDisturbance", d.ambientDisturbance),
      calmingRadius = dbl(o, "calmingRadius", d.calmingRadius),
      baselineCalming = dbl(o, "baselineCalming", d.baselineCalming),
      initialStressPeak = dbl(o, "initialStressPeak", d.initialStressPeak),
      initialStressFalloff = dbl(o, "initialStressFalloff", d.initialStressFalloff)
    )

  private def fastFrom(o: JsonObject): MandateParam =
    val d = MandateAgent.DefaultFastParam
    MandateParam(
      alpha = dbl(o, "alpha", d.alpha),
      beta = dbl(o, "beta", d.beta),
      epsilon = dbl(o, "epsilon", d.epsilon)
    )

  private def stressFrom(o: JsonObject): StressParam =
    val d = VoyConfig().stressParam
    StressParam(
      gamma = dbl(o, "gamma", d.gamma),
      delta0 = dbl(o, "delta0", d.delta0),
      sHalf = dbl(o, "sHalf", d.sHalf),
      rho = dbl(o, "rho", d.rho),
      eta = dbl(o, "eta", d.eta),
      lambda = dbl(o, "lambda", d.lambda),
      vSpike = dbl(o, "vSpike", d.vSpike),
      coupling = dbl(o, "coupling", d.coupling),
      stressMax = dbl(o, "stressMax", d.stressMax),
      sustenanceDepth = dbl(o, "sustenanceDepth", d.sustenanceDepth),
      sustenanceGrowth = dbl(o, "sustenanceGrowth", d.sustenanceGrowth),
      sustenanceBurn = dbl(o, "sustenanceBurn", d.sustenanceBurn),
      sustenanceBurnThreshold = dbl(o, "sustenanceBurnThreshold", d.sustenanceBurnThreshold),
      sustenanceFloor = dbl(o, "sustenanceFloor", d.sustenanceFloor)
    )

  private def pulseFrom(o: JsonObject): PulseParam =
    val d = PulseParam()
    PulseParam(
      shelterWindow = dbl(o, "shelterWindow", d.shelterWindow),
      minInterval = dbl(o, "minInterval", d.minInterval),
      overloadPenaltyTicks = int(o, "overloadPenaltyTicks", d.overloadPenaltyTicks)
    )

  private def defaultsJson(cfg: VoyConfig): String =
    val root = JsonObject()
    root.addProperty("tickDt", cfg.tickDt)

    val l = JsonObject()
    l.addProperty("originX", cfg.lattice.originX)
    l.addProperty("originZ", cfg.lattice.originZ)
    l.addProperty("width", cfg.lattice.width)
    l.addProperty("height", cfg.lattice.height)
    l.addProperty("diffusion", cfg.lattice.diffusion)
    l.addProperty("hotspotRadius", cfg.lattice.hotspotRadius)
    l.addProperty("hotspotDisturbance", cfg.lattice.hotspotDisturbance)
    l.addProperty("ambientDisturbance", cfg.lattice.ambientDisturbance)
    l.addProperty("calmingRadius", cfg.lattice.calmingRadius)
    l.addProperty("baselineCalming", cfg.lattice.baselineCalming)
    l.addProperty("initialStressPeak", cfg.lattice.initialStressPeak)
    l.addProperty("initialStressFalloff", cfg.lattice.initialStressFalloff)
    root.add("lattice", l)

    val f = JsonObject()
    f.addProperty("alpha", cfg.fastParam.alpha)
    f.addProperty("beta", cfg.fastParam.beta)
    f.addProperty("epsilon", cfg.fastParam.epsilon)
    root.add("fastParam", f)

    val sp = cfg.stressParam
    val s = JsonObject()
    s.addProperty("gamma", sp.gamma)
    s.addProperty("delta0", sp.delta0)
    s.addProperty("sHalf", sp.sHalf)
    s.addProperty("rho", sp.rho)
    s.addProperty("eta", sp.eta)
    s.addProperty("lambda", sp.lambda)
    s.addProperty("vSpike", sp.vSpike)
    s.addProperty("coupling", sp.coupling)
    s.addProperty("stressMax", sp.stressMax)
    s.addProperty("sustenanceDepth", sp.sustenanceDepth)
    s.addProperty("sustenanceGrowth", sp.sustenanceGrowth)
    s.addProperty("sustenanceBurn", sp.sustenanceBurn)
    s.addProperty("sustenanceBurnThreshold", sp.sustenanceBurnThreshold)
    s.addProperty("sustenanceFloor", sp.sustenanceFloor)
    root.add("stressParam", s)

    val pp = cfg.pulseParam
    val p = JsonObject()
    p.addProperty("shelterWindow", pp.shelterWindow)
    p.addProperty("minInterval", pp.minInterval)
    p.addProperty("overloadPenaltyTicks", pp.overloadPenaltyTicks)
    root.add("pulseParam", p)

    GsonBuilder().setPrettyPrinting().create().toJson(root)
