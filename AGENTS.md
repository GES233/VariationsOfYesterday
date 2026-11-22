# AGENTS.md — Variations of Yesterday（旧日变奏曲）

## 项目概览

Minecraft 1.21.2 Fabric 模组：**中式民俗恐怖 + ARG 碎片化叙事 + 可激发介质生态模拟**。
核心是「Mandate Engine」（FHN 快慢变量 + 滞后效应）——玩家通过联合动力学读数辨认可达未来并顺势而为。背景为虚构的 1980s 山区自然工程与架空祭祀逻辑的碰撞；全部地理、民族、仪式、事件均为虚构。

- 模组 ID `variations-of-yesterday`，包名 `moe.chestnut.awa.voy`，版本与依赖见 `gradle.properties`。
- 许可：代码 MPL-2.0（`LICENSE`）；叙事与美术资源 CC BY-NC-SA 4.0（`LICENSE-CONTENT`）。

## 技术栈

- Scala 3.6.3（显著缩进）+ Java 21；Minecraft 1.21.2 + Yarn + Fabric Loader 0.16.10 + Fabric API 0.106.1（经 `kotori_scala` 加载）。
- 模拟运行在自建单线程 Actor（`inner/actor`）。`MandateRuntime` 随服务端生命周期启停；`END_SERVER_TICK` 投递固定步长 `TickArrived(dt = 0.05)`，与世界事件在 `MandateAgent` 信箱内全序处理。游戏线程只投事件、只读 `@volatile` 快照、下一 tick 消费动作队列。

## 一页图（改代码前先看这个）

```
Minecraft 世界                         引擎（单线程 actor）                 回到世界
─────────────                         ────────────────────                 ────────
破坏方块 ──StressInject──┐
右键手势 ──SetCalming────┤     ┌──▶ MandateAgent 信箱（全序）
         ──SetSustenance─┤     │      └─ 格阵 step（RK4 + M + c0）
         ──StrikeBell────┘     │      └─ 分类（regime / 节律 / 尖峰 / 敲钟回执）
60 s 重平衡 ─SetBaselineCalming┘      └─ 每 tick 发布 readout（@volatile）
                                                         │
                        ┌────────────────────────────────┘
                        ├─▶ ActionFeedback（地鸣 / 示警 / BellStruck，服务端线程）
                        └─▶ StagnationRunner（效果 / 感官 / 60 s 重平衡）

游戏线程：只投事件、只读 readout 快照、下一 tick 消费动作队列。不阻塞、不反向写。
```

**三条规则**
1. 世界 → 引擎只发**事件**（`inner/event/EventDTO`），不写引擎状态。
2. 引擎 → 世界只发**动作**（`inner/action/MandateActionProtocol`）+ readout 快照，不直接改世界。
3. 一个行为 = 路由 / 裁决 / 投递 / 文案 / 音画 五段，每段最多一个文件，**整条链 ≤ 3 个文件**（如敲钟：`UseBlockRouter` → `BellStrike` → `BellRitual`/`MandateWording`）。

**改什么，开哪几个文件**

| 你要改的 | 打开这些 | 注意 |
| :--- | :--- | :--- |
| 敲钟怎么判、说什么 | `inner/veins/BellRitual.scala` → `overworld/BellStrike.scala` → `mandate/MandateWording.scala` | 相位不参与判定；命令 `StrikeBell` → 回执 `BellStruck` |
| 一个数值 / 阈值 | `VoyConfig.scala`（42 个 knob） | 被契约钉住的见「红线」 |
| 菌塘 / 接种判定 | `mandate/MandatePhenomena.scala`（纯函数） | 调用方 `blocks/BedActions.scala`、`overworld/FieldInoculation.scala` |
| 感官现象（黄水/菌光/死寂） | `overworld/StagnationRunner.scala`（`emitPhenomena`） | 判定在 `MandatePhenomena`，这里只管呈现 |
| 玩家看到的一句话 | `mandate/MandateWording.scala` + `lang/*.json` | 长文案只写在这两处，别散进内核 |
| 经济 / 身体惩罚 | `mandate/MandatePhenomena.scala` → `MandateEconomy.scala` + `mixin/` | — |
| 同步给客户端什么 | `mandate/MandateSyncProtocol.scala` + `MandateNetworking.scala` | payload id 与字段序不可改 |
| 存档里存什么 | `mandate/MandatePersistence.scala` + `inner/veins/MandateSnapshot.scala` | schema 3 不匹配即丢档 |

**红线（改了就测试红，或旧档作废）**
1. `contracts/mandate-slice-v1.json` 机器生成，改积分行为后需重生成（`./gradlew test -Dvoy.contract.write=true`），**勿手改**。
2. 钉住的锚点：`SectionLevel=1.2`、`SpikeLevel=1.8`、`nominalPeriod=39.47`、`strained/burnout/oscillatingQ`、`c0` 锚 `0.03`；`StressParam.BareCore` 专供裸核测试，生产接线用 `StressParam.forEcology`。
3. 存档 `SchemaVersion=3` 不匹配即整档丢弃；S2C payload id 与字段顺序不可改，只能尾部追加。

## 构建与测试

- 命令：`./gradlew build`、`./gradlew test`、`./gradlew runClient`（本机 JDK 21 注意事项见 `ref/本地环境.md`）。
- 测试：`src/test/scala/.../` 下 JUnit 5 spec + 数值回归快照 `SliceContract` / `SliceReplay`；`GridBenchmarkSpec` 需 `VOY_BENCH=1` 才运行。
- **唯一依据**：现状以代码与 `docs/CURRENT_ARCHITECTURE.md`（工程）、`docs/CREATIVE_BIBLE.md`（创作）、`docs/ROADMAP_AND_MODES.md`（规划）为准；`docs/archive/` 仅供考古。

## 代码与目录组织

`src/main/scala/moe/chestnut/awa/voy/`：
- `VariationsOfYesterday.scala`（模组入口）、`VoyConfig.scala`（外部配置 `config/variations-of-yesterday.json`）。
- `mandate/` — 游戏侧接线：`MandateRuntime`、`MandateOverworld`、`MandateField`、`MandateCalming`、`MandateWording`、`MandatePhenomena`、`MandateEconomy`、`MandateEffects`、`MandateSyncProtocol` + `MandateNetworking`、`MandatePersistence`、`VoyDebug`（`/voy` 命令）。
- `mandate/overworld/` — 世界侧模块：`BlockBreakListener`、`UseBlockRouter`、`FieldInoculation`、`BellStrike`、`StagnationRunner`、`ActionFeedback`、`OverworldVocabulary`、`ArrivalCore`（开局状态基础，待接线）。
- `blocks/` — 菌塘与设施方块（`VoyBlocks`、`GastrodiaBedBlock` 及 `BedCompat` / `BedFx` / `BedActions`）。
- `inner/veins/` — 纯模拟核心（「地脉」，无 MC 依赖）：状态与网格、`MandateAgent`、`BellRitual`、读数与节律（`Readout` / `Rhythm`）、$c_0$ 库存、生态类型（`EcologyKind`）、快照。
- `inner/{actor,event,action,plant}/` & `helpers/ODESolver` — Actor 内核、事件/动作协议、植物词表、RK4/Euler 求解器。

其余目录：
- `src/client/scala/.../` — 客户端入口、`ClientMandateCache` 与调试 HUD（`/voy hud [on|off]`）。
- `src/main/java/.../mixin/` — `VillagerEntityMixin` / `CropBlockMixin`（委托 `MandateEconomy`）。
- `tools/lab/` — Python FHN 数值探针。

## 开发约定

- Scala 3 显著缩进；代码注释用英文，设计文档用中文。
- `inner/` 严禁引用 Minecraft 类，保持纯函数/actor 可回放与可单测。

<!-- aoci:begin -->
## AOCI 仓库认知

`aoci.txt` 是面向模型的仓库级结构化认知索引（每条受管对象独立 Entry，含标签与 F/R/A/S 语义），由 Host 模型基于真实源码证据生成与读取，不得用正则/AST/模板拼凑。

### 核心工具入口
- `aoci_rules`：获取当前会话运行合同。
- `aoci_overview`：建立或恢复完整仓库认知（若返回 `continuation_required=true`，逐块原样传 `next_cursor` 直至 `completed=true`，最后提交一次 Attestation）。
- `aoci_maintain`：受管对象在任务收尾达到最终稳定态后调用一次（纯只读任务不调，中间修改不逐次调）。
- `aoci_update_entry`：按 `aoci_maintain` 返回的候选批次，原样保留 `source_sha256` / `candidate_id` 提交完整语义更新。
- `aoci_report`：证据不足时登记待办，不猜写。

### 运行与收尾守则
1. **建立与恢复**：新 Run 若缺少可靠完整认知，先调 `aoci_rules` 再调 `aoci_overview`。发生上下文压缩（`context_compaction`）后，此前认知视为不可靠，须带 `refresh_reasons=["context_compaction"]` 重新拉取完整 `aoci_overview`。
2. **源码证据优先**：AOCI 提供系统级上下文导航，不替代修改与验证所需的直接源码、测试和配置读取。
3. **写边界与异常处理**：遵循工具返回的 `repair_required` / `stopped` 状态与安全停点；若用户明确要求严格零写入或禁止修改 `aoci.txt` / `.aoci`，以用户限制为准并如实报告。
<!-- aoci:end -->
