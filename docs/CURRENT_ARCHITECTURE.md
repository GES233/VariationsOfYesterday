# 当前架构与工程边界（Current Architecture）

> **本文档是当前代码真实状态的唯一工程说明。** 历史设计札记已全部归档至 `docs/archive/`，仅供考古，不再作为开发依据。

## 1. 运行时数据流

模拟运行在自建单线程 Actor（`inner/actor/Actor.scala` + `MandateAgent.scala`）内，世界线程不直接修改模拟状态：

1. **输入（世界 → 引擎）**：`MandateRuntime` 在 `END_SERVER_TICK` 投递固定步长 `TickArrived(dt = 0.05)`，与玩家操作转换来的 `EventDTO`（`StressInject`、`SetCalming`、`SetBaselineCalming`、`SetSustenance`、`StrikeBell`、`FirePulse` 等）在同一信箱全序执行。
2. **内核步进（`inner/veins/`）**：
   - **四维 RK4 核**（`MandateState.scala`）：快变量 FHN（`excitation` $v$、`recovery` $w$）+ 慢变量（`stress` $s \in [0, 1]$、`activity` $q$）+ step 间闭式衰减的 $\alpha$ 突触脉冲核（`Bell` $\tau=5$ / `CrystalLink` $\tau=2$）。
   - **第五状态 `sustenance`（$M \in [0, 1]$）**：每步 RK4 之后显式推进（logistic 再生 − 高应力酸蚀 + 格间扩散），$M=0$ 为 Allee 吸收态。
   - **空间耦合**（`MandateGrid.scala` + `MandateField.scala`）：当前通过 `MandateField` 接口（默认实现 `PrototypeValleyField`）注入固定 `32×32` 区块初始场与坐标映射，后续动态生态/一般模式可直接替换该策略。
3. **输出（引擎 → 世界）**：
   - 每 tick 发布 `@volatile readoutSnapshot: Map[(Int, Int), CellReadout]`（游戏线程只读，不含 `lastBellStrikeAt` / `simTime` 等引擎私有状态）；
   - 离散动作队列 `MandateActionProtocol`（`CellSpikeTriggered`、`RegimeTransitioned`、`BellStruck`）在下一服务端 tick 由 `ActionFeedback` 消费。

## 2. 三条正交读数轴与现行裁决

- **动力学轴（`ReadoutVocab.scala`）**：`Regime`（`Calm / Strained / Oscillating / Burnout`）与 `PhaseReading`（`Cycle / Basin / Undefined`）。`SectionLevel = 1.2`，真尖峰阈值 `SpikeLevel = 1.8`。
- **菌网轴（`SustenanceTier`）**：`Intact`（$M \ge 0.60$）、`Damaged`（`floor ≤ M < 0.60`）、`Dead`（$M < \text{floor}$）。
- **节律庇护轴（`Rhythm.scala`）**：`RhythmVerdict`（`Warming / Beating / Fast / Slow / Arrhythmic`）。只有 `Beating` 或 `Warming` 且菌网存活、恢复偏移未越膝（`!isSlowOffsetBelowKnee`）才获得庇护；`Arrhythmic` 离开 `Beating` 有 `2 × 本格周期` 的退出滞回，`Fast`/`Slow` 即时发布。
- **敲钟现行规则（`inner/veins/BellRitual.scala` + `BellStrike.scala`）**：钟是起搏器，**相位不参与任何判定**；采用「`StrikeBell` 命令 → 引擎内裁决并原子注入 $c_0$ 剂量 → `BellStruck` 回执」闭环。只按状态三选一：
  - `NoAnswer`：生态死亡（菌枯或越膝）→ 脉冲进方程、不锚 $c_0$、无惩罚、蜡烛闷熄 + 灰烬；
  - `Overdriven`：8 s 过驱动守卫内连敲（守卫记在格、惩罚给玩家）→ 脉冲进方程、不锚 $c_0$、玩家吃 `voy:pulse_saturation`；
  - `Struck`：其余一切 → 脉冲进方程 + **无条件**注入 $c_0$ 剂量并注册钟源。
- **$c_0$ 库存（`CalmingStock.scala` + `MandateCalming.scala`）**：$c_0$ 是由 `Chunk ↔ List[MandateSource]` 源注册表维持的泄漏库存（$\tau = 45\text{ min}$，单钟平衡值 `0.03`，封顶 `0.04`），每 60 s 对已加载区块扫描重平衡。
- **未模拟区块（格阵外）**：`MandateRuntime.readoutAt` 返回 `CellReadout.zero` → `Maintained`，一般区域安全、不施压沉滞。

## 3. 工程避坑指南（边界、双默认与刻意保留的旧 Key）

1. **预设与双默认参数**：
   - `StressParam.BareCore`（`StressParam()` 默认，`sustenanceGrowth = 0.0, sustenanceBurn = 0.0`，菌网休眠）：专供裸核测试与数值回归快照；
   - `StressParam.OverworldDefault`（`1e-3` / `4e-3`）：`VoyConfig` 主世界运行时默认预设；接线代码切勿直接传 `BareCore` 当生产默认。
2. **冻结的序列化与协议边界（代码变量已改名，wire/save key 刻意保留）**：
   - **存档（`MandatePersistence.scala`）**：`SchemaVersion = 3`（版本或几何尺寸不匹配即整体丢弃重来）；NBT key 仍沿用 `"dissonance"` / `"entrench"` / `"beatPeriods"` / `"beatDuty"`。
   - **配置（`VoyConfig.scala`）**：`config/variations-of-yesterday.json` 中的键名仍用 `"heartbeatParam"`。
   - **S2C 同步（`MandateSyncProtocol.scala`）**：payload id `variations-of-yesterday:mandate_chunk_sync` 与字段二进制顺序不可破坏，只能在尾部追加或重做版本号。
3. **数值回归快照（`contracts/mandate-slice-v1.json`）**：
   - 已降级为 Scala 内部数值快照（无源码 SHA-256 锁，改注释/重构不报错，改积分行为才报错）。非刻意调方程勿手改；重生成命令为 `./gradlew test -Dvoy.contract.write=true`。
4. **测试集里的实验室代码**：
   - 离线 PRC 实验台架 `PhaseResponse.scala` 已移入 `src/test`（`hopfPoint` 已收入 `Readout`）；`GridBenchmarkSpec` 需设置环境变量 `VOY_BENCH=1` 才运行。

## 4. 明确尚未实施的规划项（勿把历史文档当成现状）

### 4.1 开局状态模块：当前实现与多核修订边界

**当前实现**：`mandate/overworld/ArrivalCore.scala` 是尚未接入游戏交互的基础模块。服务端线程维护 `Dormant → Spent → Nourished`（也可直接滋养），玩家按 UUID 只触发一次；首次返回塌棚结果，后来者返回个人回执，加入本身不改变状态。`Nourished` 为终态。当前通过传入世界的 `PersistentStateManager` 保存单份 `voy_arrival_core`，把开局阶段、滋养状态与玩家记录放在一起；这不是多物理核的管理实现。模块不进入 Agent，也不改地脉存档。四项已有测试覆盖原三态与 NBT 往返，尚未验证物理核的破坏、多核或替代引导。

**2026-11-17 确定、尚待代码调整**（创作规则见 `CREATIVE_BIBLE.md` §6）：

- 世界可有多枚核／残核，仅一处承载开局历史。需要分离“世界开局是否触发、玩家是否完成个人引导”的持久化记录与“每枚核是否存在、是否活动”的局部状态。跨维度如何统一存取开局记录在接线时确定，不可误把每个维度都当成一次新开局。
- 世界开局与个人记录不依附可破坏方块；毁核不清除记录，替换、放置或激活其他核也不重置开局。局部核被移除后，原址不再返回核的演出反馈。
- 玩家引入后独立存在；核与菌网的存亡不控制登录、角色存续或重生。核已毁或已滋养时，后来者仍需获得必要引导，具体载体待定。当前 `Stable` 分支不会记录新玩家，不能直接当成完整的新玩家引导实现。
- `Nourished` 仅作为开局流程的终态理解，不能兼任物理核永久活跃／不可破坏的标志。局部活动状态与其解耦。
- 不因此新增核网络模拟或智能规划引擎。局部状态及演出留在 Minecraft 侧；只有真实影响地脉的行为通过已有事件接口输入 Agent。

结构生成、物理核、滋养／激活条件、线索发放、模式选择与粒子演出均尚待接入；调用方须在状态更新后消费结果。这里的更新是内存修改并标记待保存，不构成演出与磁盘存档的崩溃事务保证。

### 4.2 其他未实施项

下列内容在 `docs/archive/` 中有完整讨论，但**当前代码尚未实现**：
- **事实流与回声**：`SemanticsProfile` 阈值集中、三通道原子事实流与 S2C 协议重做、`PulseOutcome` 事后回声（敲钟本身的 `StrikeBell` → `BellStruck` 命令/回执已落地）；
- **生态与世界扩展**：石灰石沟 / 人工湿地方块（WO-2）、Node 动态休眠调度与 D8 地势平流边、结构生成与开局 3 分钟引导；
- **关系叙事端**：Elixir `tools/voy_world` 关系沙盘与五跳卷宗已整体外迁（见 `docs/README.md`），当前 JVM 模组与之零运行时耦合。

## 5. 开发与调试入口

- 构建与测试：`./gradlew build`、`./gradlew test`、`./gradlew runClient`
- 开发期命令（仅 `isDevelopmentEnvironment` 注册）：
  - `/voy debug [on|off]`：开启/关闭聊天栏原始数值读数；
  - `/voy decay`：将脚下区块 `M` 置 `0`（用于快速测试「叩钟无应」）；
  - `/voy hud [on|off]`：客户端 7×7 区块节律调试面板（`src/client/.../hud/MandateHud.scala`）。
