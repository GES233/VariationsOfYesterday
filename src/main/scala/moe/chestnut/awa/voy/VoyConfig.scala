package moe.chestnut.awa.voy

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import com.google.gson.{GsonBuilder, JsonObject, JsonParser}
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.veins.{MandateAgent, MandateParam, PulseToleranceParam, StressParam}

/** Geometry and tuning of the prototype lattice (formerly hardcoded in
  * `MandateRuntime`). Chunk-grained: the lattice spans chunks
  * `[originX, originX + width)` x `[originZ, originZ + height)`.
  *
  * Changing the geometry changes the persistence fingerprint: a saved
  * lattice captured under different geometry is discarded loudly on load
  * (see `MandateRuntime.start`).
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
  * Discipline: these values are injected at the WIRING layer
  * (`MandateRuntime`), never at the simulation core. The defaults here are
  * exactly the previously hardcoded gameplay values, so a missing config
  * file reproduces the old behaviour bit for bit — and the frozen
  * contract slice, which instantiates `StressParam()` / `MandateParam`
  * defaults directly, is unaffected by anything in this file. That is
  * what makes the gamma time-scale calibration (design draft 01 §7.3) a
  * runtime tuning knob instead of a contract regeneration.
  *
  * Restore semantics on world load (save > config > core default): the
  * save wins for every value it carries, including per-cell parameters;
  * the config applies to a fresh world and to newly registered cells,
  * with the core defaults as the last resort. Tuning a knob therefore
  * does NOT retroactively change the physics of an existing world's cells.
  */
final case class TerrainCaptureConfig(
    enabled: Boolean = true,
    earlyCapture: Boolean = true,
    dimensions: Set[String] = Set("minecraft:overworld")
):
  def allows(dimension: String): Boolean = enabled && dimensions.contains(dimension)

final case class VoyConfig(
    tickDt: Double = 0.05,
    lattice: LatticeConfig = LatticeConfig(),
    fastParam: MandateParam = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
    stressParam: StressParam =
      StressParam(sustenanceGrowth = 1e-3, sustenanceBurn = 4e-3),
    pulseParam: PulseToleranceParam = PulseToleranceParam(),
    terrain: TerrainCaptureConfig = TerrainCaptureConfig()
)

object VoyConfig:
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /** Config file name inside the Fabric config directory. */
  val FileName = "variations-of-yesterday.json"

  /** The active configuration; replaced atomically by [[reload]]. */
  @volatile private var currentV: VoyConfig = VoyConfig()
  def current: VoyConfig = currentV

  /** Load (or create) the config file in the Fabric config directory and
    * make it active. Called once during mod init, before the engine
    * starts; mid-run reload is not supported (lattice geometry is read
    * live by the game thread).
    */
  def reload(): VoyConfig =
    val path = FabricLoader.getInstance().getConfigDir.resolve(FileName)
    currentV = loadFrom(path)
    logger.info(s"Mandate config: $currentV")
    currentV

  /** Load from an explicit path: defaults for a missing or unreadable
    * file (a missing file is additionally written out with every default
    * value, so all knobs are discoverable), per-field overrides from a
    * present one. Kept Fabric-free so it round-trips in unit tests.
    */
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
        pulseParam = pulseFrom(obj(root, "pulseParam")),
        terrain = terrainFrom(obj(root, "terrain"))
      )
    catch
      case e: Exception =>
        logger.warn(s"Unreadable config at $path ($e); using defaults.")
        VoyConfig()

  // ==== JSON codec (manual, so every field documents itself in the file) ====

  private def terrainFrom(o: JsonObject): TerrainCaptureConfig =
    val d = TerrainCaptureConfig()
    if o == null then d
    else TerrainCaptureConfig(
      enabled = if o.has("enabled") then o.get("enabled").getAsBoolean else d.enabled,
      earlyCapture = if o.has("earlyCapture") then o.get("earlyCapture").getAsBoolean else d.earlyCapture,
      dimensions = if o.has("dimensions") then
        o.getAsJsonArray("dimensions").asScala.map(_.getAsString).toSet
      else d.dimensions
    )

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
    // Fallbacks must be the *gameplay* defaults (M acid-burn enabled), not the
    // dormant core `StressParam()` used by the frozen contract slice.
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

  private def pulseFrom(o: JsonObject): PulseToleranceParam =
    val d = PulseToleranceParam()
    PulseToleranceParam(
      suppressionWindow = dbl(o, "suppressionWindow", d.suppressionWindow),
      minSafeInterval = dbl(o, "minSafeInterval", d.minSafeInterval),
      toleranceDecayTau = dbl(o, "toleranceDecayTau", d.toleranceDecayTau),
      normalToleranceStep = dbl(o, "normalToleranceStep", d.normalToleranceStep),
      overpulseToleranceStep = dbl(o, "overpulseToleranceStep", d.overpulseToleranceStep),
      invertTolerance = dbl(o, "invertTolerance", d.invertTolerance),
      maxTolerance = dbl(o, "maxTolerance", d.maxTolerance),
      overloadBurnoutDuration = dbl(o, "overloadBurnoutDuration", d.overloadBurnoutDuration),
      overloadPenaltyTicks = int(o, "overloadPenaltyTicks", d.overloadPenaltyTicks),
      singlePulsePeak = dbl(o, "singlePulsePeak", d.singlePulsePeak),
      pulseBurnoutGain = dbl(o, "pulseBurnoutGain", d.pulseBurnoutGain)
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
    p.addProperty("suppressionWindow", pp.suppressionWindow)
    p.addProperty("minSafeInterval", pp.minSafeInterval)
    p.addProperty("toleranceDecayTau", pp.toleranceDecayTau)
    p.addProperty("normalToleranceStep", pp.normalToleranceStep)
    p.addProperty("overpulseToleranceStep", pp.overpulseToleranceStep)
    p.addProperty("invertTolerance", pp.invertTolerance)
    p.addProperty("maxTolerance", pp.maxTolerance)
    p.addProperty("overloadBurnoutDuration", pp.overloadBurnoutDuration)
    p.addProperty("overloadPenaltyTicks", pp.overloadPenaltyTicks)
    p.addProperty("singlePulsePeak", pp.singlePulsePeak)
    p.addProperty("pulseBurnoutGain", pp.pulseBurnoutGain)
    root.add("pulseParam", p)

    val terrain = JsonObject()
    terrain.addProperty("enabled", cfg.terrain.enabled)
    terrain.addProperty("earlyCapture", cfg.terrain.earlyCapture)
    val dimensions = new com.google.gson.JsonArray()
    cfg.terrain.dimensions.toVector.sorted.foreach(dimensions.add)
    terrain.add("dimensions", dimensions)
    root.add("terrain", terrain)
    GsonBuilder().setPrettyPrinting().create().toJson(root)
