package moe.chestnut.awa.voy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}

import moe.chestnut.awa.voy.inner.mandate.CellCoord

/** PoC 运行时生命周期：init/reset 门控、时间倍率与快照映射。
  * 全部为纯逻辑路径，不触碰 Minecraft 类。
  */
class MandateRuntimeSpec:
  @BeforeEach def setup(): Unit = MandateRuntime.reset()
  @AfterEach def teardown(): Unit = MandateRuntime.reset()

  @Test def `uninitialized runtime stays inert`(): Unit =
    assertTrue(!MandateRuntime.isInitialized)
    assertTrue(MandateRuntime.tick().isEmpty, "未 init 不得推进或通知")
    assertTrue(!MandateRuntime.pulse(0, 1.0))
    assertTrue(!MandateRuntime.pulseAtChunk(CellCoord(0, 0)))
    assertTrue(!MandateRuntime.injectStressAtChunk(CellCoord(0, 0)))
    assertTrue(!MandateRuntime.setUpstreamDisturbance(0.5))
    assertEquals(
      0.0,
      MandateRuntime.stateAt(3, 4).stress,
      1e-12,
      "未 init 时快照为零状态"
    )

  @Test def `init anchors the chain and refuses a second init`(): Unit =
    assertTrue(MandateRuntime.init(CellCoord(10, 20)))
    assertTrue(MandateRuntime.isInitialized)
    assertTrue(!MandateRuntime.init(CellCoord(30, 40)), "重复 init 应被拒绝")
    assertEquals(Some(CellCoord(10, 20)), MandateRuntime.anchorChunk)
    assertEquals(
      Some(CellCoord(5, 20)),
      MandateRuntime.upstreamChunk,
      "上游应位于锚点 -x 方向"
    )

  @Test def `tick advances the scenario and maps chunks to cells`(): Unit =
    MandateRuntime.init(CellCoord(10, 20))
    val notices = MandateRuntime.tick()
    assertTrue(notices.isEmpty, "首个 tick 不应越过任何阈值")
    // 上游有持续扰动，一个 tick 后应力应大于零；链外区块保持零。
    val upstream = MandateRuntime.stateAt(5, 20)
    assertTrue(upstream.stress > 0.0, s"上游应积累应力，got ${upstream.stress}")
    assertEquals(0.0, MandateRuntime.stateAt(999, 999).stress, 1e-12)

  @Test def `timescale is clamped to the supported range`(): Unit =
    MandateRuntime.setTimescale(100.0)
    assertEquals(20.0, MandateRuntime.getTimescale, 1e-12)
    MandateRuntime.setTimescale(0.01)
    assertEquals(0.1, MandateRuntime.getTimescale, 1e-12)
    MandateRuntime.setTimescale(4.0)
    assertEquals(4.0, MandateRuntime.getTimescale, 1e-12)

  @Test def `timescale scales the per-tick integration step`(): Unit =
    MandateRuntime.init(CellCoord(10, 20))
    MandateRuntime.tick()
    val slow = MandateRuntime.stateAt(5, 20).stress
    MandateRuntime.reset()
    MandateRuntime.init(CellCoord(10, 20))
    MandateRuntime.setTimescale(10.0)
    MandateRuntime.tick()
    val fast = MandateRuntime.stateAt(5, 20).stress
    assertTrue(
      fast > slow * 5.0,
      s"×10 倍率的单 tick 增量应显著大于 ×1（$fast vs $slow）"
    )

  @Test def `reset clears the scenario and the snapshot`(): Unit =
    MandateRuntime.init(CellCoord(10, 20))
    MandateRuntime.tick()
    MandateRuntime.reset()
    assertTrue(!MandateRuntime.isInitialized)
    assertEquals(None, MandateRuntime.anchorChunk)
    assertEquals(1.0, MandateRuntime.getTimescale, 1e-12)
    assertEquals(0.0, MandateRuntime.stateAt(5, 20).stress, 1e-12)
