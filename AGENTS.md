# AGENTS.md — Variations of Yesterday（旧日变奏曲）

## 项目概览

这是一个 **Minecraft 1.21.2 Fabric 模组**，方向为 **中式民俗恐怖 + ARG 碎片化叙事 + 可激发介质生态模拟**。世界由核心「Mandate Engine」（FHN 快慢变量 + 滞后效应）驱动；玩家通过联合动力学读数与关系因果推演，在既定历史和动力学约束内辨认可达未来并顺势而为。背景设定为虚构的 1980s 山区水文地质工程与本土祭祀逻辑碰撞（全部地理/民族/仪式/事件均为虚构，不指涉真实存在）。

- 模组 ID：`variations-of-yesterday`
- 包名：`moe.chestnut.awa.voy`
- 版本与坐标：`gradle.properties`（`mod_version=0.1.0`，`maven_group=moe.chestnut.awa.voy`）
- 许可证：代码 MPL-2.0（`LICENSE`）；叙事与美术资源 CC BY-NC-SA 4.0（`LICENSE-CONTENT`，范围含 `docs/`、`tools/voy_world/docs/`、`tools/voy_world/world/`、lang 文案、贴图与图标）。Mojang EULA 规定模组不得售卖，故本协议只约束社区二创，不涉及商业分发。

## 技术栈

- **语言与平台**：Scala 3（3.6.3，显著缩进风格）+ Java 21，Minecraft 1.21.2 + Yarn + Fabric Loader 0.16.10 + Fabric API 0.106.1+1.21.2，通过 `kotori_scala` 加载。
- **并发与调度**：自建单线程 actor 内核（`inner/actor/`），便于直接发版。`MandateRuntime` 随服务器生命周期启停；`END_SERVER_TICK` 投递固定步长 `TickArrived(dt = 配置 tickDt，默认 0.05, tickRate)`，与通道事件在 `MandateAgent` 信箱内全序处理。游戏线程只投递事件、读取 `@volatile` 快照并在下一 tick 消费动作队列。
- **关系图谱与时代认知沙盘**：`tools/voy_world/`（独立 Elixir Mix 包，不进模组 jar），内置 microKanren 引擎（`Voy.Kanren`）与 `Plug + Bandit` 本地桌面卡片接线沙盘。

## 构建与测试

- 构建与测试（JVM）：`./gradlew build`、`./gradlew test`、`./gradlew runClient`
- 关系沙盘与 WebUI（Elixir）：在 `tools/voy_world/` 下运行 `mix test`、`mix voy.web [--port 4040 --open]`
- 本机环境注意：JDK 21 路径等本机配置见 `ref/本地环境.md`（`ref/` 被 git 忽略）。

## 代码与目录组织

- `src/main/scala/moe/chestnut/awa/voy/`
  - `VariationsOfYesterday.scala` — 模组主入口，注册生命周期、tick 与主世界监听
  - `VoyConfig.scala` — 外部运行时配置（`config/variations-of-yesterday.json`：格子几何、`tickDt`、FHN/慢变量参数，默认值即原硬编码值；只注入接线层，不触契约锚）
  - `mandate/` — Mandate 游戏侧接线层：
    - `MandateRuntime.scala` — Minecraft ↔ Mandate 桥接（引擎 actor 生命周期、tick gate、快照发布、动作回包队列）
    - `MandateOverworld.scala` — 主世界事件接线（采掘注入应力、敲钟/拆钟、验山读数、沉滞施加、感官签名发射、再接种；纯接线，裁决与文案外置）
    - `BellRitual.scala` — 敲钟/共振裁决（`BellStrikeOutcome` + `evaluateBellStrike`，无 Minecraft 依赖、可单测）
    - `MandateWording.scala` — 全部玩家可见文案（验山读数 / 叩钟六裁决 / 接种 / Regime 示警 / 相位盘，纯函数）
    - `MandatePhenomena.scala` — 感官签名、经济压力档与再接种的纯映射层（黄水三档 / 夜光 / 静默枯败 / `classifyEconomy` / `evaluateReInoculation`，无 Minecraft 依赖、可单测）
    - `MandateEconomy.scala` — 沉滞经济档的 mixin 桥接（交易加价 / 繁殖 / 铁傀儡 / 作物 keep 概率，概率判定集中在此）
    - `MandateEffects.scala` — 自定义状态效果（`voy:stagnation` 基础档=无属性标记 / `voy:stagnation_spike` 尖峰档 ×0.09 / `voy:pulse_saturation` 脉冲过载）
    - `MandatePersistence.scala` + `inner/veins/MandateSnapshot.scala` — 维度级持久化（`PersistentState`，整格快照含 `M`、通道、参数、边、链路与 `simTime`；状态与逐格参数来自存档，配置只作用于新注册格子、核心默认兜底）
  - `blocks/` — 菌网共生与设施方块层（`VoyBlocks` 注册表与创造栏、`GastrodiaBedBlock` 菌塘及荧丝菌材/鲜伏根/煨伏根，含农夫乐事沃土与小刀零依赖软兼容钩子）
  - `inner/veins/` — 核心模拟（「地脉」= 山的身体，`mandate/` = 人间制度的授命）：`MandateState`、`MandateGrid`、`MandateAgent`、`MandateReadout`、`PhaseResponse`、`PulseCoupling`、`PulseLedger`（`PulseStamp` 极简庇护短窗与冷却间隔）、`MandateSnapshot`
  - `inner/actor/Actor.scala` — 单线程 actor 内核（`ActorRef` 信箱，`tell` / `!` / `stop`）
  - `inner/event/EventMsg.scala` — 入向通道与拓扑协议 `EventDTO`（通道电平/脉冲 + 格子、输运边、链路的增删；`PulseBookkeeping` 区分物理脉冲与玩法记账）
  - `inner/action/ActionMsg.scala` — 出向动作协议 `MandateActionProtocol`
  - `inner/plant/PlantDTO.scala` — 植物与菌塘共生纯值词表（`GastrodiaBedVerdict`、`PlantDTO`）
  - `helpers/ODESolver.scala` — RK4 / Euler 求解器
- `src/client/scala/moe/chestnut/awa/voy/` — 客户端入口与开发 HUD（`hud/MandateHud.scala`，`/voy hud [on|off]`）
- `src/main/java/moe/chestnut/awa/voy/mixin/` — mixin 层（`VillagerEntityMixin` 交易/繁殖/铁傀儡、`CropBlockMixin` 作物生长，均委托 `MandateEconomy`；Java 源与 Scala 联合编译，见 `build.gradle` 的 sourceSets 配置）
- `src/test/scala/moe/chestnut/awa/voy/` — 19 个 JUnit 5 测试规范（`GridBenchmarkSpec` 默认跳过）；`inner/veins/` 下 15 个，`inner/actor/ActorSpec` 覆盖 actor 内核，顶层另有 `VoyConfigSpec` / `MandatePersistenceSpec` / `MandatePhenomenaSpec`；契约 oracle `SliceContract` / `SliceReplay` 也在此源集（不再进模组 jar）
- `contracts/mandate-slice-v1.json` — Scala 实测生成的跨栈数值契约（含逐 tick 实测 `safe_ticks`）
- `tools/voy_world/` — 独立 Elixir 关系图谱、时代认知模型与卡片接线沙盘（含 `docs/` 内的历史设计札记 02 / 05 与图集）：
  - `lib/voy/kanren/` — microKanren 内核与 DSL
  - `lib/voy/world/` — `Graph`（mediKanren 风格开放属性图 `nodeo` / `edgeo` / `patho` / `bridgeo`）、`Epistemic`（时代粒度、概念越界、机构 `neq/2` 防火墙与接线校验）、`Store`（读写 `world/*.json`）、`Web`（`Plug + Bandit` 卡片接线画布服务）；门面 `Voy.World`、Mix 任务 `Mix.Tasks.Voy.Web`
  - `world/` — 人类可直接编辑的 `eras.json`（时代认知模型）与 `graph.json`（节点与档案卡片边）
  - `docs/` — 此前阶段的设计札记（02 / 05）及生成图集（`diagrams/`）
- `docs/` — 模组本体中文设计文档（索引与边界重划见 `docs/README.md`）：草案 01、札记 06/07/08/10；`incubator/` 存放挂起构想（札记 09）；`archive/` 存放历史快照（验证报告 01）。原札记 03/04 已分别并入 05/02。

## 开发约定

- Scala 3 采用显著缩进风格；注释使用英文，设计文档使用中文。
- 模拟核心（`inner/`）不直接引用 Minecraft 类，保持纯函数/actor 可回放与可单测。
- 依赖版本统一维护在 `gradle.properties`。
