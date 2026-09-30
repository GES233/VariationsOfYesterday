package moe.chestnut.awa.voy.inner.mandate

/** PoC 阶段划分（依据下游单元格状态，用于 status 展示与一次性反馈）。
  *
  * 注意：阶段只看慢变量（stress / activity），快变量尖峰由运行时的
  * 灾变判定单独抽样，避免 status 随振荡闪烁。
  */
enum PocStage(val label: String):
  case Calm extends PocStage("平稳")
  case Watch extends PocStage("扰动")
  case Warning extends PocStage("前兆")
  case Catastrophe extends PocStage("灾变")

object PrototypeScenario:
  // —— 链条几何 ——
  /** 链条长度：index 0 = 最上游，length-1 = 下游锚点。 */
  val Length = 6

  // —— 节奏参数 ——
  // 数值由复刻 RK4 的离线脚本标定（timescale=1，1 时间单位 = 1 秒现实时间）：
  // 下游约 26 分钟越过前兆阈值、约 28 分钟首次灾变点火，落在 PoC 目标
  // 「首次灾变 20–40 分钟」区间内。正式节奏（首次灾变 3–5 小时）留待
  // 设计草案 §7.5 的时间尺度映射统一校准，此处仅为 PoC 服务。
  val Advection = 0.008
  val UpstreamDisturbance = 0.12
  val DownstreamBaselineCalming = 0.02
  val PocStressParam = StressParam(gamma = 0.03)

  /** 每 tick 基础步长：20 tick/s × 0.05 = 1 时间单位/秒。 */
  val BaseDt = 0.05

  // —— 阶段阈值（下游单元格） ——
  val WatchStress = 0.15
  val WarningStress = 0.30
  val CatastropheStress = 0.65
  val CatastropheActivity = 0.08

  // —— 交互强度 ——
  /** 敲钟相位脉冲的 alpha 内核面积（峰值 ≈ area / (e·tau) ≈ 0.74）。 */
  val BellPulseArea = 10.0

  /** TNT 爆炸的一次性应力注入量。 */
  val TntStressKick = 0.12

  /** 由下游单元格慢变量判定当前阶段（不含瞬时尖峰）。 */
  def stageOf(state: MandateCurrent): PocStage =
    if state.stress >= CatastropheStress || state.activity >= CatastropheActivity
    then PocStage.Catastrophe
    else if state.stress >= WarningStress then PocStage.Warning
    else if state.stress >= WatchStress then PocStage.Watch
    else PocStage.Calm

/** 最小 PoC 场景：一条手工的上游→下游有向河流 Cell 链。
  *
  * 链条沿区块 x 轴向 -x 方向从下游锚点（玩家 init 时所在区块，临时借用
  * vanilla 村庄/钟语义，不影响正式村/寨定义）向上游延伸，Cell 之间只接
  * advection 有向边（河水只向下游搬运应力），不做 D8 提取或 worldgen。
  *
  * 纯逻辑类，不依赖 Minecraft API，可直接单测。
  */
final class PrototypeScenario(
    val anchorChunk: CellCoord,
    val length: Int = PrototypeScenario.Length
):
  import PrototypeScenario.*

  require(length >= 2, "PoC 链条至少需要上游与下游两个单元格")

  // 网格内部坐标只承担「第几节」的角色，世界区块坐标由 chunks 另行映射。
  private val indexCoords: Vector[CellCoord] =
    Vector.tabulate(length)(i => CellCoord(i, 0))

  /** 每个 Cell 对应的世界区块坐标（上游 → 下游）。 */
  val chunks: Vector[CellCoord] = Vector.tabulate(length)(i =>
    CellCoord(anchorChunk.x - (length - 1 - i), anchorChunk.z)
  )

  private val indexByChunk: Map[CellCoord, Int] = chunks.zipWithIndex.toMap

  val cells: Vector[MandateState] = indexCoords.map { _ =>
    MandateState(
      state = MandateCurrent(0.0, 0.0, 0.0, 0.0),
      param = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
      stressParam = PocStressParam,
      time_step = BaseDt
    )
  }
  // 上游工程遗迹：持续扰动源；下游锚点：残余仪式（钟）的基线安抚。
  cells(0).disturbance = UpstreamDisturbance
  cells(length - 1).baselineCalming = DownstreamBaselineCalming

  val grid: MandateGrid = MandateGrid(
    indexCoords.map(coord => coord -> cells(coord.x)).toMap,
    (0 until length - 1).map(i =>
      LatticeEdge(indexCoords(i), indexCoords(i + 1), advection = Advection)
    )
  )

  def step(dt: Double): Unit = grid.step(dt)

  // —— 查询 ——

  def cellIndexAt(chunk: CellCoord): Option[Int] = indexByChunk.get(chunk)
  def contains(chunk: CellCoord): Boolean = indexByChunk.contains(chunk)
  def chunkAt(index: Int): CellCoord = chunks(index)
  def upstreamChunk: CellCoord = chunks(0)
  def downstreamChunk: CellCoord = chunks(length - 1)
  def upstream: MandateCurrent = cells(0).getState
  def downstream: MandateCurrent = cells(length - 1).getState

  /** 世界区块坐标 → 当前状态（供 HUD / status 读取）。 */
  def snapshot: Map[CellCoord, MandateCurrent] =
    chunks.indices.map(i => chunks(i) -> cells(i).getState).toMap

  // —— 外部输入 ——

  /** 设置上游持续扰动强度（/voy poc disturb）。 */
  def setUpstreamDisturbance(amount: Double): Unit =
    cells(0).disturbance = Math.max(0.0, amount)

  /** 相位脉冲路由：向指定序号的 Cell 发放 alpha 内核脉冲。 */
  def pulse(index: Int, area: Double = BellPulseArea): Boolean =
    if index < 0 || index >= length then false
    else
      cells(index).firePulse(area)
      true

  /** 按世界区块路由脉冲（敲钟）；不在活区内返回 false。 */
  def pulseAtChunk(chunk: CellCoord, area: Double = BellPulseArea): Boolean =
    indexByChunk.get(chunk).exists(pulse(_, area))

  /** 一次性扰动注入（TNT 爆炸）：直接抬高所在 Cell 应力并截断到上界。 */
  def injectStressAtChunk(
      chunk: CellCoord,
      amount: Double = TntStressKick
  ): Boolean =
    indexByChunk.get(chunk).exists { i =>
      val cell = cells(i)
      val raised =
        Math.min(cell.getState.stress + amount, cell.stressParam.stressMax)
      cell.state = cell.getState.copy(stress = raised)
      true
    }
