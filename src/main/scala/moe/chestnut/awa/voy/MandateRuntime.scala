package moe.chestnut.awa.voy

import moe.chestnut.awa.voy.inner.mandate.*

/** PoC 运行时：持有当前 [[PrototypeScenario]]，负责 init/reset 生命周期、
  * 逐 tick 推进、世界区块 → Cell 的快照映射与阶段阈值的一次性通知。
  *
  * 本对象刻意不引用 Minecraft 类（游戏桥接在 [[PocGame]]），以便
  * init/reset/路由等纯逻辑可以直接单测。未 init 时不推进任何模拟。
  */
object MandateRuntime:
  /** 下游首次跨过阶段阈值的一次性通知（由游戏侧投递声音/文本）。 */
  enum PocNotice:
    case WarningCrossed(anchor: CellCoord)
    case CatastropheCrossed(anchor: CellCoord)

  private val MinTimescale = 0.1
  private val MaxTimescale = 20.0

  @volatile private var scenario: Option[PrototypeScenario] = None
  @volatile private var snapshot: Map[CellCoord, MandateCurrent] = Map.empty
  @volatile private var timescale: Double = 1.0
  /** 累计仿真时间（时间单位）：每 tick 前进 BaseDt × timescale。 */
  @volatile private var simTime: Double = 0.0
  private var warningNotified = false
  private var catastropheNotified = false

  def isInitialized: Boolean = scenario.isDefined

  def anchorChunk: Option[CellCoord] = scenario.map(_.anchorChunk)

  def upstreamChunk: Option[CellCoord] = scenario.map(_.upstreamChunk)

  /** 当前链条长度（未初始化为 0）。 */
  def chainLength: Int = scenario.map(_.length).getOrElse(0)

  def getTimescale: Double = timescale

  /** 累计仿真时间（时间单位），供 HUD 等按仿真时刻对齐采样。 */
  def getSimTime: Double = simTime

  /** 设置时间倍率（截断到 [0.1, 20]）。高速下相位窗口漂移属预期
    * （设计草案 §1.4：加速流逝 = 风化），不设防。 */
  def setTimescale(factor: Double): Unit =
    timescale = Math.min(MaxTimescale, Math.max(MinTimescale, factor))

  /** 以世界区块坐标为下游锚点初始化；已初始化则拒绝并返回 false。 */
  def init(anchor: CellCoord): Boolean = synchronized {
    if scenario.isDefined then false
    else
      val fresh = PrototypeScenario(anchor)
      scenario = Some(fresh)
      snapshot = fresh.snapshot
      simTime = 0.0
      warningNotified = false
      catastropheNotified = false
      true
  }

  /** 清空场景与全部运行时状态；未初始化时为空操作。 */
  def reset(): Unit = synchronized {
    scenario = None
    snapshot = Map.empty
    timescale = 1.0
    simTime = 0.0
    warningNotified = false
    catastropheNotified = false
  }

  /** 推进一个 server tick（基础步长 × 时间倍率），并返回本次跨过
    * 阶段阈值产生的一次性通知。未初始化时不做任何事。 */
  def tick(): Seq[PocNotice] =
    scenario match
      case None => Seq.empty
      case Some(sc) =>
        val dt = PrototypeScenario.BaseDt * timescale
        sc.step(dt)
        simTime += dt
        snapshot = sc.snapshot
        val downstream = sc.downstream
        val stage = PrototypeScenario.stageOf(downstream)
        val notices = Seq.newBuilder[PocNotice]
        // 灾变隐含已越过前兆，两个通知按顺序各自只发一次。
        if !warningNotified &&
          (stage == PocStage.Warning || stage == PocStage.Catastrophe)
        then
          warningNotified = true
          notices += PocNotice.WarningCrossed(sc.anchorChunk)
        val catastrophe = stage == PocStage.Catastrophe ||
          Math.abs(downstream.dissonance) >= 1.8 // 快变量尖峰抽样
        if !catastropheNotified && catastrophe then
          catastropheNotified = true
          notices += PocNotice.CatastropheCrossed(sc.anchorChunk)
        notices.result()

  /** HUD / 外部读取：世界区块坐标 → 状态，未初始化或活区外返回零状态。 */
  def stateAt(chunkX: Int, chunkZ: Int): MandateCurrent =
    snapshot.getOrElse(
      CellCoord(chunkX, chunkZ),
      MandateCurrent(0.0, 0.0, 0.0, 0.0)
    )

  // —— 命令与交互的纯逻辑入口（均要求已初始化） ——

  def setUpstreamDisturbance(amount: Double): Boolean =
    scenario.exists { sc =>
      sc.setUpstreamDisturbance(amount)
      true
    }

  def pulse(index: Int, area: Double): Boolean =
    scenario.exists(_.pulse(index, area))

  def pulseAtChunk(chunk: CellCoord): Boolean =
    scenario.exists(_.pulseAtChunk(chunk))

  def injectStressAtChunk(chunk: CellCoord): Boolean =
    scenario.exists(_.injectStressAtChunk(chunk))

  // —— 展示 ——

  /** /voy poc status 的多行中文摘要。 */
  def statusLines: Seq[String] =
    scenario match
      case None =>
        Seq("【VOY·PoC】未初始化 —— 用 /voy poc init 在当前位置建立下游锚点。")
      case Some(sc) =>
        val up = sc.upstream
        val down = sc.downstream
        Seq(
          f"【VOY·PoC】锚点 (${sc.anchorChunk.x}, ${sc.anchorChunk.z})，链长 ${sc.length}，时间倍率 ×${timescale}%.2f",
          f"上游 (${sc.upstreamChunk.x}, ${sc.upstreamChunk.z})：stress=${up.stress}%.3f activity=${up.activity}%.3f 阶段=${PrototypeScenario.stageOf(up).label}",
          f"下游 (${sc.downstreamChunk.x}, ${sc.downstreamChunk.z})：stress=${down.stress}%.3f activity=${down.activity}%.3f 阶段=${PrototypeScenario.stageOf(down).label}"
        )

  /** HUD 附加行（未初始化为 None）。 */
  def hudLine: Option[String] =
    scenario.map { sc =>
      val down = sc.downstream
      f"PoC 下游阶段=${PrototypeScenario.stageOf(down).label} stress=${down.stress}%.3f ×${timescale}%.1f"
    }
