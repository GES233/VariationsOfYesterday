package moe.chestnut.awa.voy

import java.nio.file.{Files, Path}

import com.google.gson.{GsonBuilder, JsonObject, JsonParser}
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.mandate.{MandateParam, StressParam}

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
  * Restore semantics on world load: STATE comes from the save, PARAMETERS
  * come from this file. Tuning a knob therefore applies to existing
  * worlds immediately, without wiping their history.
  */
final case class VoyConfig(
    tickDt: Double = 0.05,
    lattice: LatticeConfig = LatticeConfig(),
    fastParam: MandateParam = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
    stressParam: StressParam =
      StressParam(sustenanceGrowth = 1e-3, sustenanceBurn = 4e-3)
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
        stressParam = stressFrom(obj(root, "stressParam"))
      )
    catch
      case e: Exception =>
        logger.warn(s"Unreadable config at $path ($e); using defaults.")
        VoyConfig()

  // ==== JSON codec (manual, so every field documents itself in the file) ====

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

  private def latticeFrom(o: JsonObject): LatticeConfig = LatticeConfig(
    originX = int(o, "originX", -16),
    originZ = int(o, "originZ", -16),
    width = int(o, "width", 32),
    height = int(o, "height", 32),
    diffusion = dbl(o, "diffusion", 0.002),
    hotspotRadius = dbl(o, "hotspotRadius", 2.0),
    hotspotDisturbance = dbl(o, "hotspotDisturbance", 0.08),
    ambientDisturbance = dbl(o, "ambientDisturbance", 0.002),
    calmingRadius = dbl(o, "calmingRadius", 5.0),
    baselineCalming = dbl(o, "baselineCalming", 0.03),
    initialStressPeak = dbl(o, "initialStressPeak", 0.18),
    initialStressFalloff = dbl(o, "initialStressFalloff", 0.004)
  )

  private def fastFrom(o: JsonObject): MandateParam = MandateParam(
    alpha = dbl(o, "alpha", 0.7),
    beta = dbl(o, "beta", 0.8),
    epsilon = dbl(o, "epsilon", 0.08)
  )

  private def stressFrom(o: JsonObject): StressParam = StressParam(
    gamma = dbl(o, "gamma", 0.01),
    delta0 = dbl(o, "delta0", 0.3),
    sHalf = dbl(o, "sHalf", 0.15),
    rho = dbl(o, "rho", 1.0),
    eta = dbl(o, "eta", 1.0),
    lambda = dbl(o, "lambda", 0.2),
    vSpike = dbl(o, "vSpike", 1.8),
    coupling = dbl(o, "coupling", 1.0),
    stressMax = dbl(o, "stressMax", 1.0),
    sustenanceDepth = dbl(o, "sustenanceDepth", 1.2),
    sustenanceGrowth = dbl(o, "sustenanceGrowth", 1e-3),
    sustenanceBurn = dbl(o, "sustenanceBurn", 4e-3),
    sustenanceBurnThreshold = dbl(o, "sustenanceBurnThreshold", 0.6),
    sustenanceFloor = dbl(o, "sustenanceFloor", 1e-4)
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

    GsonBuilder().setPrettyPrinting().create().toJson(root)
