# AGENTS.md — Variations of Yesterday（旧日变奏曲）

## 项目概览

这是一个 **Minecraft 1.21.2 Fabric 模组**，方向为 **中式民俗恐怖 + ARG 碎片化叙事 + 可激发介质生态模拟**。世界由核心「Mandate Engine」（FHN 快慢变量 + 滞后效应）驱动；玩家通过联合动力学读数与关系因果推演，在既定历史和动力学约束内辨认可达未来并顺势而为。背景设定为虚构的 1980s 山区水文地质工程与本土祭祀逻辑碰撞（全部地理/民族/仪式/事件均为虚构，不指涉真实存在）。

- 模组 ID：`variations-of-yesterday`
- 包名：`moe.chestnut.awa.voy`
- 版本与坐标：`gradle.properties`（`mod_version=0.1.0`，`maven_group=moe.chestnut.awa.voy`）
- 许可证：代码 MPL-2.0（`LICENSE`）；叙事与美术资源 CC BY-NC-SA 4.0（`LICENSE-CONTENT`，范围含 `docs/`、lang 文案、贴图与图标；Elixir 侧内容已随外迁移出本仓库）。Mojang EULA 规定模组不得售卖，故本协议只约束社区二创，不涉及商业分发。

## 技术栈

- **语言与平台**：Scala 3（3.6.3，显著缩进风格）+ Java 21，Minecraft 1.21.2 + Yarn + Fabric Loader 0.16.10 + Fabric API 0.106.1+1.21.2，通过 `kotori_scala` 加载。
- **并发与调度**：自建单线程 actor 内核（`inner/actor/`），便于直接发版。`MandateRuntime` 随服务器生命周期启停；`END_SERVER_TICK` 投递固定步长 `TickArrived(dt = 配置 tickDt，默认 0.05, tickRate)`，与通道事件在 `MandateAgent` 信箱内全序处理。游戏线程只投递事件、读取 `@volatile` 快照并在下一 tick 消费动作队列。
- **关系图谱与时代认知沙盘**：已外迁（2026-11-16）。原 `tools/voy_world/`（独立 Elixir Mix 包，内置 microKanren 引擎 `Voy.Kanren` 与 `Plug + Bandit` 本地卡片接线沙盘）与模组运行时零耦合，现作为独立工程在仓库外开发；存档位置与取回方式见「代码与目录组织」末尾。

## 构建与测试

- 构建与测试（JVM）：`./gradlew build`、`./gradlew test`、`./gradlew runClient`
- 关系沙盘与 WebUI（Elixir）：已于 2026-11-16 外迁，不再属于本仓库构建；`mix test` / `mix voy.web` 在外部工程内运行（见文末存档记录）。
- 本机环境注意：JDK 21 路径等本机配置见 `ref/本地环境.md`（`ref/` 被 git 忽略）。

## 代码与目录组织

- `src/main/scala/moe/chestnut/awa/voy/`
  - `VariationsOfYesterday.scala` — 模组主入口，注册生命周期、tick 与主世界监听
  - `VoyConfig.scala` — 外部运行时配置（`config/variations-of-yesterday.json`：格子几何、`tickDt`、FHN/慢变量参数，默认值即原硬编码值；只注入接线层，不触契约锚）
  - `mandate/` — Mandate 游戏侧接线层：
    - `MandateRuntime.scala` — Minecraft ↔ Mandate 桥接（引擎 actor 生命周期、tick gate、快照发布、动作回包队列）
    - `MandateOverworld.scala` — 主世界接线门面（只注册两个监听器并转发 server tick / 动作流；具体行为在 `mandate/overworld/`，裁决与文案外置）
    - `overworld/` — 主世界接线模块（每个通道一个文件，均无状态、可独立阅读）：`BlockBreakListener`（采掘注应力 + 拆钟注销安抚源）、`UseBlockRouter`（右键有序分发）、`Inspection`（验山读数）、`FieldInoculation`（段木田间接菌 / 土壤再接种）、`BellStrike`（敲钟剂量 + 四裁决 VFX + 过驱动反噬）、`StagnationRunner`（20 tick 沉滞施加 + 60 s c0 重平衡）、`PhenomenaEmitter`（感官签名发射 + 黄水渗）、`ActionFeedback`（尖峰 / 相位迁移反馈）、`OverworldVocabulary`（剂量与方块/物品词表）、`VoyDebug`（`/voy debug` 门控的原始数值）
    - `BellRitual.scala` — 敲钟/共振裁决（`BellStrikeOutcome` + `evaluateBellStrike`，四裁决：无应/过密/失锚/起搏·校拍；无 Minecraft 依赖、可单测）
    - `MandateCalming.scala` — c0 安抚源注册表与重平衡接线（`Chunk ↔ List[Source(kind,pos)]`、世界扫描、60 s 重平衡广播、敲钟剂量；库存纯函数在 `inner/veins/CalmingStock`）
    - `MandateWording.scala` — 全部玩家可见文案（验山读数 / 叩钟四裁决 / 接种 / Regime 示警 / 相位盘，纯函数；颜色语言与札记 11 §1.8 一致）
    - `MandatePhenomena.scala` — 感官签名、经济压力档与再接种的纯映射层（黄水三档 / 夜光 / 静默枯败 / `classifyEconomy` / `evaluateReInoculation`，无 Minecraft 依赖、可单测）
    - `MandateEconomy.scala` — 沉滞经济档的 mixin 桥接（交易加价 / 繁殖 / 铁傀儡 / 作物 keep 概率，概率判定集中在此）
    - `MandateEffects.scala` — 自定义状态效果（`voy:stagnation` 基础档=无属性标记 / `voy:stagnation_spike` 急性档 ×0.09 / `voy:pulse_saturation` 过驱动反噬；色彩与心跳配色一致）
    - `MandatePersistence.scala` + `inner/veins/MandateSnapshot.scala` — 维度级持久化（`PersistentState`，整格快照含 `M`、通道、参数、边、链路、`BeatStats` 节奏记忆与安抚源列表，`SchemaVersion = 3`；状态与逐格参数来自存档，配置只作用于新注册格子、核心默认兜底）
  - `blocks/` — 菌网共生与设施方块层（`VoyBlocks` 注册表与创造栏、`GastrodiaBedBlock` 菌塘及荧丝菌材/鲜伏根/煨伏根）：`BedCompat`（农夫乐事沃土与小刀零依赖软兼容 hook + 材料词表，`GastrodiaBedBlock` 选择性 `export`）、`BedFx`（粒子/音效具名封装）、`BedActions`（右键 ①②③④ 四个动作正文）；块本体只留生命周期与点击判定
  - `inner/veins/` — 核心模拟（「地脉」= 山的身体，`mandate/` = 人间制度的授命）：`MandateState`、`MandateGrid`、`MandateAgent`、`ReadoutVocab`（`Regime` / `MycelialHealth` / `PhaseLabel` / `UndefinedReason` / `PhaseReading` / `ReadoutParam` / `Readout` 读数词表）、`CellReadout`（发布给游戏侧的单格语义读数，含 `isSheltered`）、`PhaseTracker`（Poincaré 截面相位 + `BeatStats` 节律统计）、`Heartbeat`（节律轴：Warming/Beating/Fast/Slow/Arrhythmic + 退出滞回）、`CalmingStock`（c0 源驱动库存纯函数）、`PhaseResponse`、`PulseCoupling`、`MandateSnapshot`
  - `inner/actor/Actor.scala` — 单线程 actor 内核（`ActorRef` 信箱，`tell` / `!` / `stop`）
  - `inner/event/EventMsg.scala` — 入向通道与拓扑协议 `EventDTO`（通道电平/脉冲 + 格子、输运边、链路的增删；`FirePulse.transmitter` 区分钟击与晶链，钟击在引擎内戳 `lastBellStrikeAt` 过驱动守卫）
  - `inner/action/ActionMsg.scala` — 出向动作协议 `MandateActionProtocol`
  - `inner/plant/PlantDTO.scala` — 植物与菌塘共生纯值词表（`GastrodiaBedVerdict`、`PlantDTO`）
  - `helpers/ODESolver.scala` — RK4 / Euler 求解器
- `src/client/scala/moe/chestnut/awa/voy/` — 客户端入口与开发 HUD（`hud/MandateHud.scala` 心跳配色与两行 telemetry、`hud/CellChangeTracker.scala` 近期变化历史，`/voy hud [on|off]`）
- `src/main/java/moe/chestnut/awa/voy/mixin/` — mixin 层（`VillagerEntityMixin` 交易/繁殖/铁傀儡、`CropBlockMixin` 作物生长，均委托 `MandateEconomy`；Java 源与 Scala 联合编译，见 `build.gradle` 的 sourceSets 配置）
- `src/test/scala/moe/chestnut/awa/voy/` — 22 个 JUnit 5 测试规范（`GridBenchmarkSpec` 默认跳过）；`inner/veins/` 下 18 个（含 `HeartbeatSpec` / `HeartbeatParamSpec` / `CalmingStockSpec`），`inner/actor/ActorSpec` 覆盖 actor 内核，顶层另有 `VoyConfigSpec` / `MandatePersistenceSpec` / `MandatePhenomenaSpec` / `MandateNetworkingSpec`；契约 oracle `SliceContract` / `SliceReplay` 也在此源集（不再进模组 jar）
- `tools/lab/` — FHN 数值探针（心跳阈值与余脉弧的实测出处，纯 Python；`fhn_slow.py` 含慢系统 + c0 漏失仿真）
- `contracts/mandate-slice-v1.json` — Scala 实测生成的跨栈数值契约（含逐 tick 实测 `safe_ticks`）
- **已外迁（2026-11-16）：`tools/voy_world/`** — 原独立 Elixir 关系图谱、时代认知模型与卡片接线沙盘（microKanren 内核与 DSL、`Graph`（mediKanren 风格开放属性图）/`Epistemic`（时代粒度、概念越界、`neq/2` 防火墙）/`Store`/`Web`（`Plug + Bandit` 画布）、人类可编辑的 `world/eras.json` 与 `world/graph.json`、历史设计札记 02 / 05 与图集）。为在跑通最小循环后作为独立工程继续开发，该包与其 CI（`.github/workflows/voy_world.yml`）已移出本仓库：
  - 存档：`D:\CodeRepo\MinecraftDev\variations-of-yesterday-archive\voy_world-2026-11-16.zip`（仅含 git 跟踪的 39 个文件 / 4.06 MB，SHA-256 `61A65F6394994C1362F7B0FE07216793FA8C53D13B30B195A1B24D68999003C0`，压缩包内附 `ARCHIVE-NOTE.md`）；
  - 取回：`git checkout ffd0a70 -- tools/voy_world`（保留全部提交历史），或解压存档后 `mix deps.get && mix test`；
  - 依赖方向不变：契约仍由 Scala 实测产出（`contracts/mandate-slice-v1.json`），Elixir 侧只读，双方不在运行时通信。
- `docs/` — 模组本体中文设计文档（索引与边界重划见 `docs/README.md`）：草案 01、札记 06/07/10、**札记 11（现行庇护语义，取代 08）**；札记 08 为历史存档；`incubator/` 存放挂起构想（札记 09）；`archive/` 存放历史快照（验证报告 01）。原札记 03/04 已分别并入 05/02。

## 开发约定

- Scala 3 采用显著缩进风格；注释使用英文，设计文档使用中文。
- 模拟核心（`inner/`）不直接引用 Minecraft 类，保持纯函数/actor 可回放与可单测。
- 依赖版本统一维护在 `gradle.properties`。
