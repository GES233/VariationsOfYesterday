# AGENTS.md — Variations of Yesterday（旧日变奏曲）

## 项目概览

这是一个 **Minecraft 1.21.2 Fabric 模组**，方向为 **中式民俗恐怖 + ARG 碎片化叙事 + 可激发介质生态模拟**。世界由核心「Mandate Engine」（FHN 快慢变量 + 滞后效应）驱动；玩家通过联合动力学读数与关系因果推演，在既定历史和动力学约束内辨认可达未来并顺势而为。背景设定为虚构的 1980s 山区水文地质工程与本土祭祀逻辑碰撞（全部地理/民族/仪式/事件均为虚构，不指涉真实存在）。

- 模组 ID：`variations-of-yesterday`
- 包名：`moe.chestnut.awa.voy`
- 版本与坐标：`gradle.properties`（`mod_version=0.1.0`，`maven_group=moe.chestnut.awa.voy`）
- 许可证：All Rights Reserved（ARR）

## 技术栈

- **语言与平台**：Scala 3（3.6.3，显著缩进风格）+ Java 21，Minecraft 1.21.2 + Yarn + Fabric Loader 0.16.10 + Fabric API 0.106.1+1.21.2，通过 `kotori_scala` 加载。
- **并发与调度**：自建单线程 actor 内核（`inner/actor/`），便于直接发版。`MandateRuntime` 随服务器生命周期启停；`END_SERVER_TICK` 投递固定步长 `TickArrived(0.05, tickRate)`，与通道事件在 `MandateAgent` 信箱内全序处理。游戏线程只投递事件、读取 `@volatile` 快照并在下一 tick 消费动作队列。
- **关系推理与内容沙盘**：`tools/voy_world/`（独立 Elixir Mix 包，不进模组 jar），内置零依赖 microKanren 引擎（`Voy.Kanren`）。

## 构建与测试

- 构建与测试（JVM）：`./gradlew build`、`./gradlew test`、`./gradlew runClient`
- 关系沙盘与内容检查（Elixir）：在 `tools/voy_world/` 下运行 `mix test`、`mix voy.content lint`、`mix run examples/sandbox.exs`
- 本机环境注意：JDK 21 路径等本机配置见 `ref/本地环境.md`（`ref/` 被 git 忽略）。

## 代码与目录组织

- `src/main/scala/moe/chestnut/awa/voy/`
  - `VariationsOfYesterday.scala` — 模组主入口，注册生命周期、tick 与主世界监听
  - `MandateRuntime.scala` — Minecraft ↔ Mandate 桥接（引擎 actor 生命周期、tick gate、快照发布、动作回包队列）
  - `MandateOverworld.scala` — 主世界交互接线（采掘注入应力、敲钟/拆钟控制 $c_0$ 与脉冲、钟表/指南针验山读数、原版「沉滞」debuff）
  - `inner/mandate/` — 核心模拟：`MandateState`、`MandateGrid`、`MandateAgent`、`MandateReadout`、`PhaseResponse`、`SliceReplay`、`SliceContract`
  - `inner/actor/Actor.scala` — 单线程 actor 内核（`ActorRef` 信箱，`tell` / `!` / `stop`）
  - `inner/event/EventMsg.scala` — 入向通道与拓扑协议 `EventDTO`（通道电平/脉冲 + 格子、输运边、链路的增删）
  - `inner/action/ActionMsg.scala` — 出向动作协议 `MandateActionProtocol`
  - `helpers/ODESolver.scala` — RK4 / Euler 求解器
  - `VoyConfig.scala` — 外部运行时配置（`config/variations-of-yesterday.json`：格子几何、`tickDt`、FHN/慢变量参数，默认值即原硬编码值；只注入接线层，不触契约锚）
  - `MandatePersistence.scala` + `inner/mandate/MandateSnapshot.scala` — 维度级持久化（`PersistentState`，整格快照含 `M`、通道、参数、边、链路与 `simTime`；状态来自存档、参数来自配置）
- `src/client/scala/moe/chestnut/awa/voy/` — 客户端入口与开发 HUD（`hud/MandateHud.scala`，`/voy hud [on|off]`）
- `src/test/scala/moe/chestnut/awa/voy/inner/mandate/` — 10 个 JUnit 5 测试规范；`inner/actor/ActorSpec` 覆盖 actor 内核
- `contracts/mandate-slice-v1.json` — Scala 实测生成的跨栈数值契约（含逐 tick 实测 `safe_ticks`）
- `tools/voy_world/` — Elixir 关系世界模型：
  - `lib/voy/kanren/` — microKanren 内核与 DSL
  - `lib/voy/world/` — `Chronicle`（地形与四纪六结局）、`Pathway + Witness + Content`（五跳局部证据与种子物化）、`Observation + Ascribe + Slice + Contract`（三方观测与 24 候选反演）、`Facts`（运行时事实日志）、`Node + Naming + Export + Canon`（节点编译、schema v2 导出与静态检查）
  - `content/documents/` — 唯一叙事散文来源（32 张主链卡 + 4 张干扰卡）
- `docs/` — 中文设计草案、札记 02–05 与验证报告 01

## 开发约定

- Scala 3 采用显著缩进风格；注释使用英文，设计文档使用中文。
- 模拟核心（`inner/`）不直接引用 Minecraft 类，保持纯函数/actor 可回放与可单测。
- 依赖版本统一维护在 `gradle.properties`。
