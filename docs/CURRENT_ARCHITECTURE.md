# 当前架构与工程边界（Current Architecture）

> **本文档是当前代码真实状态的唯一工程说明。** 历史设计札记已归档至 `docs/archive/`，不再作为开发依据。

## 1. 运行时数据流

模拟运行在自建单线程 Actor（`inner/actor/Actor.scala` + `MandateAgent.scala`）内，世界线程不直接修改模拟状态：

1. **输入（世界 → 引擎）**：`MandateRuntime` 在 `END_SERVER_TICK` 投递固定步长 `TickArrived(dt = 0.05)`，与玩家操作产生的 `EventDTO`（`StressInject`、`SetCalming`、`SetBaselineCalming`、`SetSustenance`、`StrikeBell`、`FirePulse` 等）在同一信箱全序执行。
2. **内核步进（`inner/veins/`）**：
   - **四维 RK4 核**（`MandateState.scala`）：快变量 FHN（`excitation` $v$、`recovery` $w$）+ 慢变量（`stress` $s \in [0, 1]$、`activity` $q$）+ 闭式衰减 $\alpha$ 突触脉冲核（`Bell` $\tau=5$ / `CrystalLink` $\tau=2$）。
   - **第五状态 `sustenance`（$M \in [0, 1]$）**：每步 RK4 后显式推进（logistic 再生 − 高应力酸蚀 + 格间扩散），$M=0$ 为 Allee 吸收态。
   - **空间与生态 Adapter**（`MandateGrid` + `MandateField` + `EcologyKind`）：默认通过 `PrototypeValleyField` 注入固定 `32×32` 区块初始场，并按群系映射四种慢变量生态 Adapter（`Mountain` 山地酸水、`Swamp` 湿地泥炭暗火、`Permafrost` 冻土热喀斯特、`Desert` 生物土壤结皮 BSC），各自挂载独立的 `StressParam` 预设与物候词表（`OverworldVocabulary.scala`）。
3. **输出（引擎 → 世界）**：
   - 每 tick 发布 `@volatile readoutSnapshot: Map[(Int, Int), CellReadout]`（游戏线程只读，不含 `lastBellStrikeAt` / `simTime` 等私有状态）；
   - 离散动作队列 `MandateActionProtocol`（`CellSpikeTriggered`、`RegimeTransitioned`、`BellStruck`）在下一服务端 tick 由 `ActionFeedback` 消费。

## 2. 三条正交读数轴与现行裁决

- **动力学轴（`ReadoutVocab.scala`）**：`Regime`（`Calm / Strained / Oscillating / Burnout`）与 `PhaseReading`（`Cycle / Basin / Undefined`）。截面阈值 `SectionLevel = 1.2`，真尖峰阈值 `SpikeLevel = 1.8`。
- **菌网轴（`SustenanceTier`）**：`Intact`（$M \ge 0.60$）、`Damaged`（`floor ≤ M < 0.60`）、`Dead`（$M < \text{floor}$）。
- **节律庇护轴（`Rhythm.scala`）**：`RhythmVerdict`（`Warming / Beating / Fast / Slow / Arrhythmic`）。仅当 `Beating` 或 `Warming`、菌网存活且恢复偏移未越膝（`!isSlowOffsetBelowKnee`）时提供庇护；`Arrhythmic` 退出 `Beating` 有 `2 × 本格周期` 滞回，`Fast`/`Slow` 即时发布。
- **敲钟规则（`BellRitual.scala` + `BellStrike.scala`）**：钟是起搏器，**相位不参与任何判定**；走「`StrikeBell` 命令 → 引擎裁决并原子注入 $c_0$ → `BellStruck` 回执」闭环，按状态三选一：
  - `NoAnswer`：生态死亡（菌枯或越膝）→ 脉冲进方程、不锚 $c_0$、无惩罚、蜡烛闷熄 + 灰烬；
  - `Overdriven`：8 s 守卫内连敲（守卫记在格、惩罚给玩家）→ 脉冲进方程、不锚 $c_0$、玩家获 `voy:pulse_saturation`；
  - `Struck`：其余情况 → 脉冲进方程 + **无条件**注入 $c_0$ 剂量并注册钟源。
- **$c_0$ 库存（`CalmingStock.scala` + `MandateCalming.scala`）**：由 `Chunk ↔ List[MandateSource]` 维护的泄漏库存（$\tau = 45\text{ min}$，单钟平衡值 `0.03`，封顶 `0.04`），每 60 s 扫描已加载区块重平衡。
- **未模拟区块（格阵外）**：`MandateRuntime.readoutAt` 返回 `CellReadout.zero` → `Maintained`（安全、无沉滞）。

## 3. 工程避坑指南（双默认与冻结边界）

1. **`StressParam` 双默认**：
   - `BareCore`（`StressParam()` 默认，生长/燃烧率均为 `0.0`，菌网休眠）：**仅供裸核测试与数值回归快照**，生产接线严禁直接使用；
   - 生产预设由 `StressParam.forEcology` 按群系注入：`OverworldDefault`（山地 `1e-3 / 4e-3`）、`SwampPeatDefault`、`PermafrostDefault`、`DesertBscDefault`。
2. **冻结的序列化与协议 Key（代码已改名，wire/save key 刻意保留）**：
   - **存档（`MandatePersistence.scala`）**：`SchemaVersion = 3`（版本或尺寸不匹配即整档丢弃）；NBT key 仍为 `"dissonance"` / `"entrench"` / `"beatPeriods"` / `"beatDuty"`。
   - **配置（`VoyConfig.scala`）**：JSON 键名仍为 `"heartbeatParam"`。
   - **S2C 同步（`MandateSyncProtocol.scala`）**：payload id `variations-of-yesterday:mandate_chunk_sync` 与字段顺序不可改，仅允许尾部追加或升版本号。
3. **数值回归快照（`contracts/mandate-slice-v1.json`）**：
   - 仅锁积分数值（无源码 SHA-256 锁，改注释/重构不报错）。非刻意调方程勿手改；重生成：`./gradlew test -Dvoy.contract.write=true`。
4. **测试集工具**：`PhaseResponse.scala`（离线 PRC 台架）位于 `src/test`；`GridBenchmarkSpec` 需设置 `VOY_BENCH=1` 才运行。

## 4. 尚未实施的规划项（勿将历史归档当成现状）

### 4.1 开局状态模块（`ArrivalCore.scala`）

- **现状**：位于 `mandate/overworld/ArrivalCore.scala`，尚未接入游戏交互。在服务端维护 `Dormant → Spent → Nourished`（终态）及按玩家 UUID 的首次触发记录，持久化于单份 `voy_arrival_core`（`PersistentStateManager`），不进 Agent、不改地脉存档。现有 4 项单测仅覆盖三态流转与 NBT 往返。
- **2026-11-17 待调整边界**（创作规则见 `CREATIVE_BIBLE.md` §6）：
  1. **全局历史与局部物理核解耦**：世界可有多枚核/残核，仅一处承载开局历史。需拆分「世界开局/个人引导完成记录」（跨维度统一存取、毁核或换核不重置）与「单枚物理核的存续/活动状态」（核被敲掉后原址不再产生核演出，`Nourished` 不代表物理核不可破坏）。
  2. **新玩家引导兜底**：玩家引入后独立存在，核与菌网存亡不影响登录与重生；当原核已毁或已进入 `Nourished`（当前 `Stable` 分支尚不记录新玩家）时，后来者仍需通过替代载体获得引导。
  3. **不新增核网络引擎**：局部状态与演出留在 Minecraft 侧，仅真实影响地脉的行为经既有事件接口进 Agent。结构生成、物理方块、粒子演出与模式选择均待接入。

### 4.2 其他未实施项

- **事实流与回声**：`SemanticsProfile` 阈值集中、三通道原子事实流与 S2C 重做、`PulseOutcome` 事后回声；
- **生态与世界扩展**：石灰石沟 / 人工湿地方块（WO-2）、Node 动态休眠调度与 D8 地势平流、结构生成与开局 3 分钟引导；
- **关系叙事端**：Elixir `tools/voy_world` 关系沙盘与五跳卷宗已外迁（见 `docs/README.md`），与当前 JVM 模组零耦合。

## 5. 开发与调试入口

- 构建与测试：`./gradlew build`、`./gradlew test`、`./gradlew runClient`
- 开发期命令（仅开发环境注册）：
  - `/voy debug [on|off]`：开关聊天栏原始数值读数；
  - `/voy decay`：将脚下区块 `M` 置 `0`（测试「叩钟无应」）；
  - `/voy hud [on|off]`：开关客户端 7×7 区块节律 HUD（`src/client/.../hud/MandateHud.scala`）。
