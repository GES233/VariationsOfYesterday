# AGENTS.md — Variations of Yesterday（旧日变奏曲）

## 项目概览

Minecraft 1.21.2 Fabric 模组：**中式民俗恐怖 + ARG 风格碎片化叙事 + 可激发介质生态模拟**。核心是「Mandate Engine」（FHN 快慢变量 + 滞后效应）——玩家通过联合动力学读数辨认可达未来并顺势而为。背景为虚构的 1980s 山区水文地质工程与本土祭祀逻辑的碰撞；全部地理、民族、仪式、事件均为虚构，不指涉真实存在。

- 模组 ID `variations-of-yesterday`，包名 `moe.chestnut.awa.voy`，版本在 `gradle.properties`（0.1.0）。
- 许可：代码 MPL-2.0（`LICENSE`）；叙事与美术资源 CC BY-NC-SA 4.0（`LICENSE-CONTENT`）。

## 技术栈

- Scala 3.6.3（显著缩进风格）+ Java 21；Minecraft 1.21.2 + Yarn + Fabric Loader 0.16.10 + Fabric API 0.106.1，经 `kotori_scala` 加载。
- 模拟走自建单线程 actor 内核（`inner/actor`）。`MandateRuntime` 随服务器生命周期启停；`END_SERVER_TICK` 投递固定步长 `TickArrived`（`tickDt` 默认 0.05），与通道/拓扑事件在 `MandateAgent` 信箱内全序处理。游戏线程只投事件、读 `@volatile` 快照、下一 tick 消费动作队列。

## 一页图（改代码前先看这个）

数据流只有两条半：世界 → 引擎（事件）、引擎 → 世界（动作 + 每 tick 的 readout 快照）。

```
Minecraft 世界                         引擎（单线程 actor）                 回到世界
─────────────                         ────────────────────                 ────────
破坏方块 ──StressInject──┐
右键手势 ──SetCalming───┤  ┌──▶ MandateAgent 信箱（全序）
         ──SetSustenance┤  │      └─ 格阵 step（RK4 + M + c0）
         ──FirePulse────┘  │      └─ 分类（regime / 节律 / 尖峰）
60 s 重平衡 ─SetBaselineCalming┘      └─ 每 tick 发布 readout（@volatile）
                                                         │
                        ┌────────────────────────────────┘
                        ├─▶ ActionFeedback（地鸣 / 示警，服务端线程）
                        └─▶ StagnationRunner（效果 / 感官 / 60 s 重平衡）

游戏线程：只投事件、只读 readout 快照、下一 tick 消费动作队列。不阻塞、不反向写。
```

**三条规则**
1. 世界 → 引擎只发**事件**（`inner/event/EventDTO`），不写引擎状态。
2. 引擎 → 世界只发**动作**（`inner/action/MandateActionProtocol`）+ readout 快照，不直接改世界。
3. 一个行为 = 路由 / 裁决 / 投递 / 文案 / 音画 五段，每段最多一个文件，**整条链 ≤ 3 个文件**（敲钟已做到：`UseBlockRouter` → `BellStrike` → `BellRitual`/`MandateWording`）。

**改什么，开哪几个文件**

| 你要改的 | 打开这些 | 注意 |
| :--- | :--- | :--- |
| 敲钟怎么判、说什么 | `mandate/BellRitual.scala` → `overworld/BellStrike.scala` → `mandate/MandateWording.scala` | 相位不参与判定（裁决⑯） |
| 一个数值 / 阈值 | `VoyConfig.scala`（42 个 knob） | 被契约钉住的见「红线」 |
| 菌塘 / 接种判定 | `mandate/MandatePhenomena.scala`（纯函数） | 调用方 `blocks/BedActions.scala`、`overworld/FieldInoculation.scala` |
| 感官现象（黄水/菌光/死寂） | `overworld/StagnationRunner.scala`（`emitPhenomena`） | 判定在 `MandatePhenomena`，这里只管呈现 |
| 玩家看到的一句话 | `mandate/MandateWording.scala` + `lang/*.json` | 长文案只写在这两处，别散进内核 |
| 经济 / 身体惩罚 | `mandate/MandatePhenomena.scala` → `MandateEconomy.scala` + `mixin/` | — |
| 同步给客户端什么 | `mandate/MandateSyncProtocol.scala` + `MandateNetworking.scala` | payload id 与字段序不可改 |
| 存档里存什么 | `mandate/MandatePersistence.scala` + `inner/veins/MandateSnapshot.scala` | schema 3 不匹配即丢档 |

**红线（改了就测试红，或旧档作废）**
1. `contracts/mandate-slice-v1.json` 机器生成，改代码后重生成（`-Dvoy.contract.write=true`），**别手改**。
2. `contracts/mandate-slice-v1.json` 锁的是**数值**，不是源码字节：改 `MandateState` / `ODESolver` 的**行为**会让快照变化、测试报错；改注释、改名、重构结构不会（2026-11-16 降级，札记 12 §9.2）。
3. 钉住的数值：`SectionLevel=1.2`、`SpikeLevel=1.8`、`nominalPeriod=39.47`、`strained/burnout/oscillatingQ`、`c0` 锚 0.03。
4. 存档 `SchemaVersion=3` 不匹配即整体丢弃；S2C payload id 不可改，只能尾部加字段。

## 构建与测试

- `./gradlew build`、`./gradlew test`、`./gradlew runClient`。
- 本机 JDK 21 路径等环境注意事项见 `ref/本地环境.md`（`ref/` 被 git 忽略）。
- 测试：22 个 JUnit 5 spec（`inner/veins/` 17 个），`GridBenchmarkSpec` 需 `VOY_BENCH=1` 才运行；数值快照 oracle `SliceContract` / `SliceReplay` 也在 test 源集（不进模组 jar；跨栈角色已于 2026-11-16 退役）。
- 2026-11-16 起 Elixir 关系沙盘不在本仓库构建内（`mix test` 不适用）；存档与取回见 `docs/README.md`「已外迁」。

## 代码与目录组织

`src/main/scala/moe/chestnut/awa/voy/`：

- `VariationsOfYesterday.scala` — 模组入口（生命周期、tick、主世界监听）；`VoyConfig.scala` — 外部运行时配置（`config/variations-of-yesterday.json`，只注入接线层，不触契约锚）。
- `mandate/` — Mandate 游戏侧接线层：`MandateRuntime`（Minecraft ↔ Mandate 桥接）、`MandateOverworld`（主世界门面，委托 `overworld/` 下按通道拆分的无状态模块）、`BellRitual`（敲钟裁决纯函数）、`MandateCalming`（c0 安抚源注册表）、`MandateWording`（玩家可见文案）、`MandatePhenomena`（感官签名/经济档/再接种纯映射）、`MandateEconomy`（mixin 桥接）、`MandateEffects`（自定义状态效果）、`MandateSyncProtocol` + `MandateNetworking`（S2C 同步）、`MandatePersistence`（维度级持久化）、`VoyDebug`（`/voy` 开发命令门，不是通道，故不在 `overworld/`）。
- `mandate/overworld/` — 7 个世界侧模块：`BlockBreakListener`、`UseBlockRouter`（含钟/罗盘读数分支）、`FieldInoculation`、`BellStrike`、`StagnationRunner`（含感官层）、`ActionFeedback`、`OverworldVocabulary`。
- `blocks/` — 菌塘与设施方块（`VoyBlocks` 注册、`GastrodiaBedBlock` 及 `BedCompat` / `BedFx` / `BedActions`）。
- `inner/veins/` — 纯模拟核心（「地脉」）：状态与网格、`MandateAgent`、读数词表、心跳节律与相位、c0 库存、快照、尖峰/相位检测；无 Minecraft 依赖，可单测与回放。
- `inner/{actor,event,action,plant}/` — actor 内核、入向 `EventDTO` / 出向 `MandateActionProtocol`、植物纯值词表；`helpers/ODESolver` — RK4 / Euler。

其余：

- `src/client/scala/.../` — 客户端入口、`mandate/ClientMandateCache` 与开发 HUD（`hud/`，`/voy hud [on|off]`）。
- `src/main/java/.../mixin/` — `VillagerEntityMixin` / `CropBlockMixin`，均委托 `MandateEconomy`；Java 与 Scala 联合编译（见 `build.gradle` 的 sourceSets）。
- `src/test/scala/.../` — 上述 spec 与契约 oracle。
- `tools/lab/` — FHN 数值探针（纯 Python，`RhythmVerdict` 阈值与余脉弧的实测出处）。
- `contracts/mandate-slice-v1.json` — Scala 实测生成的**内部数值快照**（跨栈角色已退役），勿手改；须重生成时走 `build.gradle` 的 `voy.contract.write` 流程并评审 diff。
- `docs/` — 中文设计文档与参考稿，索引见 `docs/README.md`；现行庇护语义看札记 11，**现行命名与边界语义看札记 12**（札记 08 仅供历史参考），其来源工作稿在 `docs/reference/`。

## 开发约定

- Scala 3 采用显著缩进；代码注释用英文，设计文档用中文。
- `inner/` 不直接引用 Minecraft 类，保持纯函数/actor 可回放与可单测。
- 依赖版本统一维护在 `gradle.properties`。
