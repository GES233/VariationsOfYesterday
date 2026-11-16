# AGENTS.md — Variations of Yesterday（旧日变奏曲）

## 项目概览

Minecraft 1.21.2 Fabric 模组：**中式民俗恐怖 + ARG 风格碎片化叙事 + 可激发介质生态模拟**。核心是「Mandate Engine」（FHN 快慢变量 + 滞后效应）——玩家通过联合动力学读数辨认可达未来并顺势而为。背景为虚构的 1980s 山区水文地质工程与本土祭祀逻辑的碰撞；全部地理、民族、仪式、事件均为虚构，不指涉真实存在。

- 模组 ID `variations-of-yesterday`，包名 `moe.chestnut.awa.voy`，版本在 `gradle.properties`（0.1.0）。
- 许可：代码 MPL-2.0（`LICENSE`）；叙事与美术资源 CC BY-NC-SA 4.0（`LICENSE-CONTENT`）。

## 技术栈

- Scala 3.6.3（显著缩进风格）+ Java 21；Minecraft 1.21.2 + Yarn + Fabric Loader 0.16.10 + Fabric API 0.106.1，经 `kotori_scala` 加载。
- 模拟走自建单线程 actor 内核（`inner/actor`）。`MandateRuntime` 随服务器生命周期启停；`END_SERVER_TICK` 投递固定步长 `TickArrived`（`tickDt` 默认 0.05），与通道/拓扑事件在 `MandateAgent` 信箱内全序处理。游戏线程只投事件、读 `@volatile` 快照、下一 tick 消费动作队列。

## 构建与测试

- `./gradlew build`、`./gradlew test`、`./gradlew runClient`。
- 本机 JDK 21 路径等环境注意事项见 `ref/本地环境.md`（`ref/` 被 git 忽略）。
- 测试：22 个 JUnit 5 spec（`inner/veins/` 17 个），`GridBenchmarkSpec` 需 `VOY_BENCH=1` 才运行；跨栈契约 oracle `SliceContract` / `SliceReplay` 也在 test 源集（不进模组 jar）。
- 2026-11-16 起 Elixir 关系沙盘不在本仓库构建内（`mix test` 不适用）；存档与取回见 `docs/README.md`「已外迁」。

## 代码与目录组织

`src/main/scala/moe/chestnut/awa/voy/`：

- `VariationsOfYesterday.scala` — 模组入口（生命周期、tick、主世界监听）；`VoyConfig.scala` — 外部运行时配置（`config/variations-of-yesterday.json`，只注入接线层，不触契约锚）。
- `mandate/` — Mandate 游戏侧接线层：`MandateRuntime`（Minecraft ↔ Mandate 桥接）、`MandateOverworld`（主世界门面，委托 `overworld/` 下按通道拆分的无状态模块）、`BellRitual`（敲钟裁决纯函数）、`MandateCalming`（c0 安抚源注册表）、`MandateWording`（玩家可见文案）、`MandatePhenomena`（感官签名/经济档/再接种纯映射）、`MandateEconomy`（mixin 桥接）、`MandateEffects`（自定义状态效果）、`MandateSyncProtocol` + `MandateNetworking`（S2C 同步）、`MandatePersistence`（维度级持久化）。
- `blocks/` — 菌塘与设施方块（`VoyBlocks` 注册、`GastrodiaBedBlock` 及 `BedCompat` / `BedFx` / `BedActions`）。
- `inner/veins/` — 纯模拟核心（「地脉」）：状态与网格、`MandateAgent`、读数词表、心跳节律与相位、c0 库存、快照、尖峰/相位检测；无 Minecraft 依赖，可单测与回放。
- `inner/{actor,event,action,plant}/` — actor 内核、入向 `EventDTO` / 出向 `MandateActionProtocol`、植物纯值词表；`helpers/ODESolver` — RK4 / Euler。

其余：

- `src/client/scala/.../` — 客户端入口、`mandate/ClientMandateCache` 与开发 HUD（`hud/`，`/voy hud [on|off]`）。
- `src/main/java/.../mixin/` — `VillagerEntityMixin` / `CropBlockMixin`，均委托 `MandateEconomy`；Java 与 Scala 联合编译（见 `build.gradle` 的 sourceSets）。
- `src/test/scala/.../` — 上述 spec 与契约 oracle。
- `tools/lab/` — FHN 数值探针（纯 Python，`Heartbeat` 阈值与余脉弧的实测出处）。
- `contracts/mandate-slice-v1.json` — Scala 实测生成的跨栈数值契约，勿手改；须重生成时按 `build.gradle` 的 `voy.contract.write` 流程。
- `docs/` — 中文设计文档与参考稿，索引见 `docs/README.md`；现行庇护语义看札记 11（札记 08 仅供历史参考），其来源工作稿在 `docs/reference/`。

## 开发约定

- Scala 3 采用显著缩进；代码注释用英文，设计文档用中文。
- `inner/` 不直接引用 Minecraft 类，保持纯函数/actor 可回放与可单测。
- 依赖版本统一维护在 `gradle.properties`。
