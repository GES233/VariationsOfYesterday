package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** PoC 场景纯逻辑：有向河流传播、脉冲路由与一次性扰动注入。
  *
  * 断言幅度由复刻 RK4 的离线脚本预演（t=150 时间单位）：上游注入 0.5
  * 后沿链向下游递减扩散，反向则完全无回流。
  */
class PrototypeScenarioSpec:
  private val dt = PrototypeScenario.BaseDt

  private def newScenario(): PrototypeScenario =
    val sc = PrototypeScenario(CellCoord(100, 100), length = 6)
    sc.setUpstreamDisturbance(0.0) // 关闭持续扰动，隔离传输行为
    sc

  private def step(sc: PrototypeScenario, tEnd: Double): Unit =
    var t = 0.0
    while t < tEnd do
      sc.step(dt)
      t += dt

  @Test def `chain maps world chunks upstream of the anchor`(): Unit =
    val sc = newScenario()
    // 下游锚点 = init 区块；上游沿 -x 延伸。
    assertEquals(CellCoord(100, 100), sc.downstreamChunk)
    assertEquals(CellCoord(95, 100), sc.upstreamChunk)
    assertTrue(sc.contains(CellCoord(97, 100)), "链上区块应在活区内")
    assertTrue(!sc.contains(CellCoord(101, 100)), "链外区块不在活区内")
    assertEquals(Some(4), sc.cellIndexAt(CellCoord(99, 100)))
    assertEquals(None, sc.cellIndexAt(CellCoord(99, 101)))

  @Test def `stress propagates downstream along the river`(): Unit =
    val sc = newScenario()
    assertTrue(sc.injectStressAtChunk(sc.upstreamChunk, 0.5))
    step(sc, 150.0)
    val stresses = sc.cells.map(_.getState.stress)
    assertTrue(
      stresses(1) > 0.05,
      s"紧邻下游的 Cell 应承接上游来水，got ${stresses(1)}"
    )
    assertTrue(
      stresses(0) < 0.2,
      s"上游应被河水排走大半，got ${stresses(0)}"
    )
    // 有向性：各 Cell 应力沿下游方向总体递减。
    assertTrue(stresses(0) > stresses(2), s"应力应向下游递减：$stresses")

  @Test def `no backflow from downstream to upstream`(): Unit =
    val sc = newScenario()
    assertTrue(sc.injectStressAtChunk(sc.downstreamChunk, 0.5))
    step(sc, 150.0)
    for i <- 0 until sc.length - 1 do
      assertEquals(
        0.0,
        sc.cells(i).getState.stress,
        1e-12,
        s"河水不得向上游回流（Cell $i）"
      )
    assertTrue(
      sc.downstream.stress > 0.2,
      s"下游应力应大体滞留，got ${sc.downstream.stress}"
    )

  @Test def `pulse routes only to the addressed cell`(): Unit =
    val sc = newScenario()
    assertTrue(sc.pulse(2, area = 5.0))
    step(sc, dt)
    assertTrue(
      sc.cells(2).synapseOutput > 0.0,
      "目标 Cell 应收到 alpha 内核输出"
    )
    for i <- 0 until sc.length if i != 2 do
      assertEquals(
        0.0,
        sc.cells(i).synapseOutput,
        1e-12,
        s"Cell $i 不应收到脉冲"
      )
    // 越界序号被拒绝。
    assertTrue(!sc.pulse(-1, 1.0))
    assertTrue(!sc.pulse(sc.length, 1.0))

  @Test def `pulse routes by world chunk and rejects outside chunks`(): Unit =
    val sc = newScenario()
    val target = 4
    assertTrue(sc.pulseAtChunk(sc.chunkAt(target), area = 5.0))
    step(sc, dt)
    assertTrue(sc.cells(target).synapseOutput > 0.0)
    assertEquals(0.0, sc.cells(0).synapseOutput, 1e-12)
    // 活区外的钟不触发。
    assertTrue(!sc.pulseAtChunk(CellCoord(999, 999)))

  @Test def `stress injection respects the stress ceiling`(): Unit =
    val sc = newScenario()
    assertTrue(sc.injectStressAtChunk(sc.downstreamChunk, 5.0))
    assertEquals(
      PrototypeScenario.PocStressParam.stressMax,
      sc.downstream.stress,
      1e-12,
      "注入必须截断到 stressMax"
    )
    // 活区外注入被拒绝。
    assertTrue(!sc.injectStressAtChunk(CellCoord(0, 0), 0.1))
