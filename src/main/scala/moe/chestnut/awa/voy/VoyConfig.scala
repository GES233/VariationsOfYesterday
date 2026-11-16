package moe.chestnut.awa.voy

import java.nio.file.{Files, Path}

import com.google.gson.{GsonBuilder, JsonObject, JsonParser}
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.{Logger, LoggerFactory}

import moe.chestnut.awa.voy.inner.veins.{
  CalmingParam,
  RhythmParam,
  MandateAgent,
  FastParam,
  Readout,
  StressParam
}

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
    /** measured (tools/lab/fhn_slow.py): the core reaches the acid line in
      * ~33 min, and an anchored 0.04 calming stock can walk it back down
      * (u_core <= 0.038 is the rescue window, ruling 13).
      */
    hotspotDisturbance: Double = 0.036,
    /** measured: three-tier field — the 8x8 remnant ring keeps beating while
      * sagging toward the onset edge (bottom ~0.28) and reaches the acid line
      * in ~47 min (U-shaped arc, ruling 12).
      */
    remnantRadius: Double = 4.0,
    remnantDisturbance: Double = 0.034,
    ambientDisturbance: Double = 0.002,
    calmingRadius: Double = 5.0,
    baselineCalming: Double = 0.03,
    initialStressPeak: Double = LatticeConfig.DefaultInitialStressPeak,
    initialStressFalloff: Double = LatticeConfig.DefaultInitialStressFalloff
)

object LatticeConfig:
  /** derived: oscillation onset `s_onset = α − a_c` (Hopf point, closed form;
    * M = 1, k = 1).
    */
  val OscillationOnsetStress: Double =
    val fast = MandateAgent.DefaultFastParam
    fast.alpha - Readout.hopfPoint(fast)._2

  /** derived: `s_onset + 0.25 × (s_acid − s_onset)` ≈ 0.349 — the opening
    * keeps a small "old-era remnant pulse" inside the natural rhythm band
    * `[s_onset, s_acid)` (2026-11-16 ruling, risk 1 of design note 11).
    */
  val DefaultInitialStressPeak: Double =
    OscillationOnsetStress +
      0.25 * (StressParam.BareCore.sustenanceBurnThreshold - OscillationOnsetStress)

  /** derived: `(peak − s_onset) / 4` — the remnant zone is an 8×8-chunk
    * inscribed circle (in-band radius 4 cells) around the lattice centre.
    */
  val DefaultInitialStressFalloff: Double =
    (DefaultInitialStressPeak - OscillationOnsetStress) / 4.0

/** External runtime configuration (`config/variations-of-yesterday.json`).
  *
  * Injected at the wiring layer (`MandateRuntime`), never at the simulation
  * core. Restore precedence on world load: save > config > core default.
  */
final case class VoyConfig(
    tickDt: Double = 0.05,
    lattice: LatticeConfig = LatticeConfig(),
    fastParam: FastParam = FastParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
    stressParam: StressParam = StressParam.OverworldDefault,
    rhythmParam: RhythmParam = RhythmParam(),
    calmingParam: CalmingParam = CalmingParam()
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
        rhythmParam = rhythmFrom(obj(root, "heartbeatParam")),
        calmingParam = calmingFrom(obj(root, "calmingParam"))
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
      remnantRadius = dbl(o, "remnantRadius", d.remnantRadius),
      remnantDisturbance = dbl(o, "remnantDisturbance", d.remnantDisturbance),
      ambientDisturbance = dbl(o, "ambientDisturbance", d.ambientDisturbance),
      calmingRadius = dbl(o, "calmingRadius", d.calmingRadius),
      baselineCalming = dbl(o, "baselineCalming", d.baselineCalming),
      initialStressPeak = dbl(o, "initialStressPeak", d.initialStressPeak),
      initialStressFalloff = dbl(o, "initialStressFalloff", d.initialStressFalloff)
    )

  private def fastFrom(o: JsonObject): FastParam =
    val d = MandateAgent.DefaultFastParam
    FastParam(
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

  private def calmingFrom(o: JsonObject): CalmingParam =
    val d = CalmingParam()
    CalmingParam(
      tau = dbl(o, "tau", d.tau),
      tricklePerWeight = dbl(o, "tricklePerWeight", d.tricklePerWeight),
      dose = dbl(o, "dose", d.dose),
      cap = dbl(o, "cap", d.cap)
    )

  private def rhythmFrom(o: JsonObject): RhythmParam =
    val d = RhythmParam()
    RhythmParam(
      nominalPeriod = dbl(o, "nominalPeriod", d.nominalPeriod),
      minCycles = int(o, "minCycles", d.minCycles),
      regularityTol = dbl(o, "regularityTol", d.regularityTol),
      staleCycles = dbl(o, "staleCycles", d.staleCycles),
      overdriveGuard = dbl(o, "overdriveGuard", d.overdriveGuard),
      overloadPenaltyTicks = int(o, "overloadPenaltyTicks", d.overloadPenaltyTicks),
      exitBadCycles = int(o, "exitBadCycles", d.exitBadCycles)
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
    l.addProperty("remnantRadius", cfg.lattice.remnantRadius)
    l.addProperty("remnantDisturbance", cfg.lattice.remnantDisturbance)
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

    val hp = cfg.rhythmParam
    val h = JsonObject()
    h.addProperty("nominalPeriod", hp.nominalPeriod)
    h.addProperty("minCycles", hp.minCycles)
    h.addProperty("regularityTol", hp.regularityTol)
    h.addProperty("staleCycles", hp.staleCycles)
    h.addProperty("overdriveGuard", hp.overdriveGuard)
    h.addProperty("overloadPenaltyTicks", hp.overloadPenaltyTicks)
    h.addProperty("exitBadCycles", hp.exitBadCycles)
    root.add("heartbeatParam", h)

    val cp = cfg.calmingParam
    val c = JsonObject()
    c.addProperty("tau", cp.tau)
    c.addProperty("tricklePerWeight", cp.tricklePerWeight)
    c.addProperty("dose", cp.dose)
    c.addProperty("cap", cp.cap)
    root.add("calmingParam", c)

    GsonBuilder().setPrettyPrinting().create().toJson(root)
