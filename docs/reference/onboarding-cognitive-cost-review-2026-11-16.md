# 新人接手认知成本评估与简化方案（2026-11-16）

> **评估对象**：`master @ 51da5a3` 工作区，0.1.0。**方法**：三位分析员分别逐文件通读 `inner/`、`mandate/+blocks/+client/`、`docs/+resources/`，全部断言带 `file:line`；Lead 独立复核核心链路并抽检 6 条关键断言（契约 digest、死通道、`0.03` 三处定义、refactor 提交、lang 键集测试、`ref/` 被忽略）。
> **证据等级**：标 `[验证]` = 命令/读文件直接确认；`[估算]` = 有依据的区间，误差约 ±50%。本文不是代码审查报告，而是**接手成本报告**：结论针对"明天入职、能力强、零背景"的人，不针对原作者。
> **一句话**：这是**一个建得很好、但路标很差的项目**。认知成本不来自代码量（生产代码只有 6.8k 行、测试 169 例 35 秒全绿），而来自**同一概念有 3–10 个名字、一个行为横跨 4–7 个文件、以及"文档—代码—契约"三份真相互相打架**。

---

## 0. 结论摘要

| 问题 | 答案 |
| :--- | :--- |
| 一个强工程师**能编译、跑测试、画出数据流图** | **12–24 h**（≈1.5–3 人日）`[估算]` |
| **敢改一处游戏侧行为并自证没坏** | 累计 **3–6 个工作日** `[估算]` |
| **敢动方程/契约锚**（改 `MandateState`、`PhaseDetection`、`HeartbeatParam` 来源值） | 再 **+2–4 人日**，累计 **1–2 周** `[估算]` |
| **能独立负责一条纵切**（如心跳轴全链路，含数值后果判断） | **2–3 周** `[估算]` |
| 成本构成 | 读代码 40%、**辨词义 25%**、辨"哪份文档是真的" 20%、环境与验证 15% |
| 美工（懂 PS、不懂 MC 管线）参与成本 | **8.5–16 h**，现在处于"半能干活"状态 `[估算]` |
| 剧情撰写者参与成本 | **9–16 h**，现在只能改 **8 条** lang 键 `[估算]` |
| 认知成本能压到多少 | 做完 P0+P1（约 8–12 人日）→ **降 30–40%**；最便宜的 5 件事（**< 1 人日**）→ 降 10–15% |

**成本最高的三个单点**（都不是"代码写得差"）：

1. **动词/名词的命名漂移**：一条"山的心跳"在代码里有 ≥7 套名字（`heartbeat`/`beat`/`pulse`/`spike`/`地鸣`/`地脉`/`节律`）；`c0` 有 10 个名字；"菌网"一个量有 5 个名字；反过来 `spike` 一个词有 5 种互不相同的判据、`burnout` 有 5 种判据、`phase` 有 4 种含义。
2. **拆分是按行数、不是按概念**：`ffd0a70` 一次提交把 `MandateOverworld` 751 行拆成 55 行门面 + 10 个文件、把 `GastrodiaBedBlock` 405 行拆成 4 件（提交信息自称 "pure structural split / verbatim lift"）。结果：**敲钟一个行为跨 7 个文件**、`overworld/` 里 4/10 不是通道。
3. **契约锚是隐性雷**：`contracts/mandate-slice-v1.json` 的 `numeric_source_digest` 对 `MandateState.scala` 与 `ODESolver.scala` **全文**做 SHA-256 `[验证]`（`SliceContract.scala:498-514`），这两个文件改一个空格测试就红，而代码里没有就近提示——这也是死代码一直不敢删的真因。

---

## 1. 评估口径：为什么不是"代码太烂"

先说清楚，否则下面的问题清单会被误读。

**该表扬的四点**（这是判断"简化该做到什么程度"的基准）：

- **纯度守住了**：`inner/` 全包零 Minecraft import，可回放、可单测；`MandateWording` 保持零 MC 依赖以便客户端 HUD 复用。这在 Fabric 模组里很少见。
- **注释带出处**：`measured:` / `derived:` 前缀强制标provenance，`HeartbeatParam.rationale`、`VoyConfig` 的每个数值都写了实测来源，甚至写了"为什么不是另一个值"（`Heartbeat.scala:134-160` 解释退出规则改了两版的原因）。
- **契约 oracle 是科研级诚实**：`SliceContract` 把设计稿主张与实测结论并列，允许标 `refuted`（`SliceContract.scala:89-133`），并在注释里写明"契约是为了携带分歧，不是隐藏分歧"。
- **反馈环快**：22 suites / 169 tests / **35.3 s** 全绿，`build/libs` 今日 19:29 刚产出 `[验证]`（`build/test-results`）。

**所以问题不是"烂"，是"未做面向读者的收口"**：所有知识都在作者脑内和注释里，没有一层"给读者的索引"。三个受众（工程师、美工、撰写者）卡在同一个地方——**代码与文档之间没有中间层**。

---

## 2. 认知成本模型与三档门槛

### 2.1 六类成本

| # | 成本类 | 内容 | 小时 | 说明 |
| :--: | :--- | :--- | ---: | :--- |
| C1 | 环境 | JDK 21（本机默认 zulu25，Gradle 配置期直接崩）、loom 拉 MC 1.21.2/Yarn、`GRADLE_USER_HOME` 在仓外 | 1.5–4 | 说明写在 `ref/本地环境.md`，而 `ref/` 被 `.gitignore` 忽略 `[验证]` → **clone 者拿不到** |
| C2 | 文档考古 | 必读链 982 行 / 约 1.1 万字；含"哪份是现行"的判断 | 1.5–3 | 官方顺序会把新人先带进**已作废的札记 08**（`docs/README.md:37` 只写"08 仅作历史参考"） |
| C3 | 代码 | 生产 52 文件 6805 行；其中接线层 34 文件 4222 行（占 62%，**比仿真核心还大**） | 6–12 | 文件头注释与代码约 1:1，实际阅读量 ≈ 2× 行数 |
| C4 | 词义 | ~40 个核心概念、17 个 enum/56 个 case、42 个可调参数 | 4–8 | 这是**唯一不能靠"聪明"跳过**的一段：名字不指向唯一物理量 |
| C5 | 数据流与并发 | 单线程 actor → `@volatile` 快照 → 动作队列 → 下一 tick 回放；契约 oracle 回放 | 3–5 | 关键知识分散在 `MandateAgent` 注释、`AGENTS.md`、`SliceReplay` 三处 |
| C6 | 验证 | 游戏侧仅 4 spec/21 例/0.79 s 覆盖；`Wording / StagnationRunner / blocks / mixin / HUD` **零覆盖** | 2–4 | 改行为只能 `runClient` 手工复现（15–30 min/次）——**这条最杀第一次改动** |

### 2.2 三档门槛

| 门槛 | 定义 | 累计 | 卡点 |
| :--- | :--- | ---: | :--- |
| **Gate A「能跑」** | `./gradlew test` 全绿；能画 事件→actor→grid→readout→action 数据流；能改文案/加日志 | **12–24 h** | C1 + C3 前半 |
| **Gate B「敢改」** | 改一条游戏侧行为（接线层/读数投影/心跳判定）并自证不破契约 | **3–6 工作日** | C4 + C6：**无法证伪**是真障碍 |
| **Gate C「敢动核心」** | 改方程、`PhaseDetection` 常量、`HeartbeatParam` 来源值 | **+2–4 人日** | 要重跑 5 个 Python 探针、重生成契约、回改 6 份文档的实测表 |

> 关键判据：Gate B 的门槛不是"读完 6805 行"，而是**能回答"改这个数会让哪些测试红、哪些客户端错色、哪份文档要跟着改"**。目前这三个答案分别在 `SliceContractSpec`、`MandateSyncProtocol`、`docs/` 三处，且都没有从代码处链接过去。

---

## 3. 量化基线（分子在哪里）

| 维度 | 数字 | 来源 |
| :--- | :--- | :--- |
| 生产代码 | 52 文件 / **6805 行** | `src/` 共 76 文件 11322 行，测试 24 文件 4517 行 |
| 接线层 vs 核心 | 4222 行 vs 2850 行 | `mandate/`(1696) + `overworld/`(945) + `blocks/`(777) + `client/`(406) + `mixin/`(84) + 入口 314 |
| 测试 | 22 suites / **169 例 / 35.3 s**，其中游戏侧仅 21 例 0.79 s | `build/test-results` 实测 |
| 文档 | 10 篇 / 1645 行 / **31,998 汉字**；必读链 982 行 | `docs/`；另有 `ref/`（34 MB，被 gitignore） |
| 契约 | 1038 行机器生成 JSON，禁手改 | `contracts/mandate-slice-v1.json` |
| 可调参数 | **42 个**（LatticeConfig 14 + fast 3 + Stress 14 + Heartbeat 7 + Calming 4） | `VoyConfig.scala:73-81` |
| 参数类 | 6 个 case class，**每个都有完整默认值** → 任何 `StressParam()` 都是合法代码 | 见 §4.3-1 |
| 概念词 | 17 enum / 56 case；玩家可见判定 19 个 | `ReadoutVocab/Heartbeat/BellRitual/PlantDTO/MandatePhenomena` |
| 代码内文档引用 | **211 行**带 `§`/`design note`/`ruling` 的注释 | 全 `src/`，`MandateWording` 27、`CellChangeTracker` 14、`Heartbeat` 10 |
| 悬空外部引用 | 3 处指向**已外迁**的 `Voy.World.Profile`(Elixir) | `ReadoutVocab.scala:76`、`PhaseResponse.scala:17`、`SliceContract.scala:8` |
| 玩家可见文案 | **95 条**中文字面量（63 主 + 32 客户端），lang 仅 16 键且 **8 个是死键** | 详见 §6 |

---

## 4. 五类病灶（按"推高成本"排序）

### 4.1 命名：一物多名 / 一名多物

这是**唯一一个数量级领先**的成本项。它不是风格问题：读代码时必须先在脑内做映射表，才看得懂一条赋值。

**(a) 一物多名**（同一物理量，多个名字）

| 概念 | 名字数 | 名字们（示例） |
| :--- | --: | :--- |
| 山的心跳 | **≥7** | `heartbeat` / `beat` / `pulse` / `spike` / `地鸣` / `地脉` / `节律(节拍)` |
| `c0`（基线安抚） | **10** | `baselineCalming` / `c0` / `CalmingStock` / `CalmingParam` / `MandateCalming` / `MandateSourceKind.Bell` / `C0ShelterLevel` / `BellBaselineCalming` / `c0Intact` / `trickle/dose/cap` |
| 菌网 `M` | **5** | `sustenance` / `M` / `mycelium` / `MycelialHealth` / `菌网`（另有叙事名"荧丝"） |
| 安抚源/涓流 | 5 | `MandateSource` / `安抚源` / `维持源` / `涓流` / `镇静剂`（札记 11 的新定义） |

两个具体坑：

- `C0ShelterLevel = 0.01`（"是否已锚定"的阈值，`MandatePhenomena.scala:51`）与 `c0Intact = 0.03`（正常工作点）都叫"c0 水平"，**差 3 倍**，用途完全不同。
- `0.03` 这个契约锚在 **3 处各自定义** `[验证]`：`BellRitual.scala:15`、`VoyConfig.scala:41`、`CalmingStock.scala:38`（写成 `0.03/2700`）。札记 11 要求"0.03 一字不动"，但它没有唯一符号，改参数必漏一处。

**(b) 一名多物**（同一个词，多个判据）——比 (a) 更危险，因为**静默错**

| 词 | 判据数 | 分别是什么 |
| :--- | --: | :--- |
| `spike`/尖峰 | **5** | ①`v>=1.8` 正向运行时 ②上穿1.2+峰≥1.8+回落穿0 契约 ③上穿1.2+5s内峰≥1.8 晶链 ④`\|v\|>1.9` 离线链探针 ⑤`\|v\|>vSpike` 活动示踪 q |
| `burnout` | **5** | `Regime.Burnout`(s≥0.95) / `burnoutA`(右膝) / `SlowOffsetBelowKnee` / `DeadMyceliumBurnout` / `PulseOverloadBurnout` |
| `phase` | **4** | 0–1 小数 fraction / `PhaseLabel` 枚举 / 子系统名(`PhaseTracker`…) / 已删除的"庇护窗" |
| `readout` | 5 | `object Readout`(分类器+分岔数学) / `CellReadout`(DTO) / `ReadoutParam` / `readoutSink` / `MandateSnapshot` |
| `shelter`/庇护 | **2** | `CellReadout.isSheltered`(心跳庇护) vs `c0Sheltered`(c0≥0.01) 同时在一个 payload 上 |

最贵的一处：`ReadoutVocab.scala:76` 的"regime 分区"注释说它"mirroring `Voy.World.Profile.regime/3`"——那个 Elixir 模块 2026-11-16 已整体迁出仓库。**定义词表的文件，引用了一个不存在的东西**。同病还有 `PhaseResponse.scala:17`、`SliceContract.scala:8/27/558`。

**(c) 前缀零区分度**：`inner/` 里 8 个 `Mandate*`、`mandate/` 里 10 个 `Mandate*`，唯一区分在后缀。最误导的是 `MandateParam`（只装 α/β/ε 三个**快变量**参数）vs `StressParam`（装 14 个**慢变量+M** 参数）——名字与内容完全不对称。

> **为什么这条最贵**：它把"理解"变成了"背诵"。能力强的人能背，但要花 4–8 h，而且**背错不会被编译器发现**——只会让推导出错误的因果。

---

### 4.2 过度抽象：把"实验室"和"生产线"放在同一层

| # | 位置 | 问题 | 代价 |
| :--: | :--- | :--- | :--- |
| 1 | `PhaseResponse.scala` **479 行**（最大文件） | 离线 PRC 测量台住在 **main 源集**；生产代码只用 `hopfPoint` + 2 个常量 | 读完 479 行才知道"为什么生产代码需要它"；相邻的 `PulseKind`（实验剂量）与 `PulseTransmitter`（运行时核）是两套同名近义目录 |
| 2 | `PulseCoupling.scala` | `runCoupled/lagSeries/CoupledTrace`（约 90 行）仅测试使用 | 同上，加剧"哪段能改"的判断成本 |
| 3 | `ODESolver.scala:17-18` | `method: Symbol` 与 `extra: Option[Map[String,Any]]` **从无调用者**；`equ` 里参数叫 `_extra`；Euler 分支是死代码 | 新人必须花时间想"extra 是干嘛的"，答案是"没干嘛"；且这两个文件是契约 digest 目标 → **删不掉**（§4.4） |
| 4 | `MandatePhenomena.scala` | 自称"orthogonal consumer projections ... no cross-consumer coupling"，实际是 4 类互不相关策略 + 4 个共享常量 + 1 个空转发；`BellRitual`、`SyncProtocol` 都依赖它的常量 | "纯映射"同时是常量所有者 → 改动半径不可预测 |
| 5 | `ReadoutVocab.scala:69-164` | `object Readout` 把**分岔数学**（`burnoutA/slowA/oscillating/restPoint`）和**表现层投影**（`label/dialTick`）塞在一起 | 读"读数"要同时理解分岔理论；`restPoint` 用 64 步二分求精度 ~1e-18 的值，而调用方只用它做容差 0.25 的布尔判定 |
| 6 | `MandateAgent.apply(...)` | **13 个参数**，调用点混用 4 个位置参数 + 7 个具名参数 | 每次读调用点都要数参数 |
| 7 | `VoyConfig.scala` | 一个文件三种职责：纯物理导出常量 + JSON 解析 + JSON 写出 + Fabric 路径 | 加一个 knob 要改 4 处；且"默认值"在核心与配置两层含义不同（核心 `MandateState` 默认 M 休眠=0，`VoyConfig` 默认启用 M 的 r/c_burn） |

### 4.3 职责不清

| # | 位置 | 问题 |
| :--: | :--- | :--- |
| 1 | `MandateState.scala:86-140` | **可变大杂烩**：4 维状态 + 3 组参数 + 5 个输入通道 + α 核 + 2 个 flux + solver。且"哪些量在 RK4 向量里、哪些在向量外"**只写在注释里**——改字段顺序不会编译报错，只会静默错位 |
| 2 | `MandateAgent.scala:148-243` | `integrate` 一个闭包 **96 行做 9 件事**（脉冲投递/积分/相位观测/regime 去抖/心跳滞回/readout 组装/spike 边沿/发布/持久化节拍）。改任何一件都要先装下 9 件 |
| 3 | `MandateOverworld.scala` | 它是**目录，不是门面**：55 行里 **32 行是文件头注释**（`:9-40`），行为只有 3 行。讽刺的是——**这 32 行是全仓库最好的一份架构文档**，却锁在 Scala 文件里 |
| 4 | `overworld/` 10 文件 | 4/10 不是"通道"：`Inspection`（30 行、一个 `if`）、`PhenomenaEmitter`（89 行、唯一调用者 `StagnationRunner:155`）、`OverworldVocabulary`（纯词表）、`VoyDebug`（开发命令，包内唯一可变全局） |
| 5 | `blocks/` 四分 | 切口是"行数均分"而非耦合面：`BedCompat` 用 `GastrodiaBedBlock` 的常量、`GastrodiaBedBlock` 又 `export BedCompat.*` → **包内循环依赖**；`blocks ↔ mandate` 双向依赖使 `blocks/` 无法脱离引擎编译/测试 |
| 6 | `StagnationRunner.scala:44-57` | 20-tick 效果通道里夹了 1200-tick 的 c0 重平衡，两个频率挤在一个 `tick` |
| 7 | `MandateRuntime.scala:32/156/245-248` | **整条发布通道是死的** `[验证]`：每 tick 把 1024 条 entry 重映射进 `@volatile snapshot`，**grep 无任何读点**（~20k 分配/秒白烧）。它还教给新人一个假模型："有两个 volatile 发布通道"，实际只有一个 |
| 8 | `BellRitual.scala:22-26,55-56` | `BellStrikeOutcome.baselineCalming` 生产从不读，但 `MandateWording.scala:168` 把它当 `c0=0.03` **打印给玩家**，而同方法 `:154,162,164` 还直接插值英文枚举（`Excitation`/`AbsoluteRefractory`）→ 中文句子里夹英文 |
| 9 | 测试放错包 | `MandateReadoutSpec.scala:166-268` 的 4 个敲钟测试（mandate 语义）住在 `inner/veins` 包 → 改 `BellRitual` 会红 inner 测试，并制造"inner 依赖游戏层"的错觉 |

### 4.4 需要大量上下文才能理解的代码

这是"文档—代码—契约"三方打架的具体形态，也是**最容易好心办坏事**的一类。

| # | 形态 | 证据 |
| :--: | :--- | :--- |
| 1 | **契约锚隐雷** | `SliceContract.scala:498-514` 对 `MandateState.scala` + `ODESolver.scala` **全文**做 SHA-256。改一个空格 → 测试红；而注释级改动**所有数值不变**，diff 里只有 `numeric_source_digest` 一行 → 极易被误判为环境问题。**这是死代码不敢删的真因** |
| 2 | **代码引用文档章节** | 211 行注释带 `§`/`design note N`/`ruling N`（inner 内平均每 30 行一条）→ 读代码必须 5 份文档同时开着 |
| 3 | **文档与实测矛盾，且代码是对的** | 草案 01:148 说"场外按 `CellReadout.zero` 判沉滞"；实际 `zero.isSheltered == true`，且 `MandatePhenomenaSpec:132-136` **反向断言** `classifyEconomy(zero) == Maintained` |
| 4 | **文档与契约矛盾** | 草案 01:62-64 说"40–60% 相位脉冲触发提前尖峰"；契约里这条恰恰是 `refuted`（`SliceContract.scala:101-112`）。新人会去"修代码让它符合文档"，正确答案是**改文档** |
| 5 | **旧作废文档仍在可搜范围** | 札记 08 整篇作废（自称"请勿按本文实现"），但 `lang:3,7` 仍用旧词"脉冲"、`BellRitual.scala:11`/`Heartbeat.scala:232` 仍在引用 08 |
| 6 | **外部上下文缺失** | `ref/`（34 MB、含环境说明与讨论纪要）被 gitignore；Elixir 关系沙盘已迁出仓库但代码仍在引用；同一份《心跳即庇护迁移计划》存在**两个版本**（tracked 34012 B vs untracked 33759 B），新人无法判断哪个权威 |

### 4.5 隐式契约与静默失效（会让新人"改对了却崩了"）

| # | 位置 | 失效方式 |
| :--: | :--- | :--- |
| 1 | `ActionFeedback.scala:27` + `ActionMsg.scala:5` + `build.gradle:84-88` | sealed 动作 + **无兜底 match + 无 `-Werror`**：新增一个动作 → 编译只给 warning → 运行时在**服务端 tick 线程**抛 `MatchError` 崩服 |
| 2 | `MandateSyncProtocol.scala:124-128` 等 5 处 `.ordinal` | 枚举在 `inner/` 重排 → **客户端静默错色/错字**，无异常；payload 也无版本号 |
| 3 | `MandatePersistence.scala:91,257` `valueOf` | 用 **enum 名字**反序列化 NBT：重命名一个 case → 世界加载抛异常 → **存档打不开**（而不是优雅丢弃） |
| 4 | `UseBlockRouter.scala:48-52` | `orElse` 链的顺序**就是语义**（注释自己承认：`PASS` 也算"认领"）；插错一行 = 敲钟静默失效。无测试、无类型约束 |
| 5 | `VoyConfig.scala` 对称性 | 加 knob 要改 3–4 处；而 `VoyConfigSpec` 的"写默认值再读回"测试**抓不到漏写**（writer 漏 key → reader 落默认 → 仍然相等）→ 测试给了假安全感 |

---

## 5. 简化路线图

> 原则：**只在接线层做减法与收口，不碰契约红线**。每条给工作量 / 风险 / 判据。工时按"熟悉本仓的人"计。
> 判据里的"169 例全绿"是必要条件；带 ★ 的可被现有测试**完全验证**，不需要进游戏。

### P0 — 半天到一天，纯减法（先做，立竿见影）

| # | 动作 | 工作量 | 判据 |
| :--: | :--- | ---: | :--- |
| P0-1 ★ | **删死通道与假字段**：`MandateRuntime.snapshot/publishSnapshot`（传 `_ => ()`，不动 `MandateAgent` 签名）、`BellStrikeOutcome.baselineCalming`、`MandatePhenomena.sustenanceWord`、`CellChangeTracker.reset` | 0.5 天 | 169 例绿；`grep -c snapshotSink` 只剩 inner 一处；玩家不再看到 `c0=0.03` |
| P0-2 | **文案收口**：`BlockBreakListener:84-90`、`StagnationRunner:164-166`、`ActionFeedback:56,69` 的 6 条消息搬进 `MandateWording`；删掉玩家文本里的英文枚举与 `c0=` 数字 | 0.5 天 | 写文案只需开 1 个文件 |
| P0-3 | **`0.03` 单一符号**：收进 `MandateConstants.C0Anchor`，`BellRitual:15`/`VoyConfig:41`/`CalmingStock:38` 改引用 | 2 h | 169 例绿 |
| P0-4 | **兜底 match**：`ActionFeedback` 加 `case other => warn`，或 `build.gradle` 对 `ActionFeedback` 开 `-Werror` | 1 h | 故意加一个 dummy 动作应编译失败/运行告警而非崩服 |
| P0-5 | **`VoyConfig` 对称性测试**：用 `productElementNames` 断言 `defaultsJson` 的 key 集与 case class 字段集相等 | 2–4 h | 故意删 writer 一行，测试必须红 |

### P1 — 2–4 天，把"文件跨度"压下来（认知成本主因）

| # | 动作 | 工作量 | 风险 |
| :--: | :--- | ---: | :--- |
| P1-1 ★ | **离线实验移出 main**：`PhaseResponse` 的 12 个离线函数 + `PulseKind/PulseShape/PulseOutcome/Trial/Spike/DutyCycle` + `PulseCoupling` 的 `runCoupled/lagSeries/CoupledTrace` 移到 `src/test`（同包）；`hopfPoint` 留在 main（15 行 `Bifurcation.scala`）；`PulseCoupling.scala` 改名 `PulseLinkRunner.scala`。**main −~570 行** | 2–4 h | 中低：不碰 digest 两文件；`SliceContract/SliceReplay` 同包，编译过即验证 |
| P1-2 | **`overworld/` 10 → 6**：`PhenomenaEmitter` 并回 `StagnationRunner`；`Inspection` 并回 `UseBlockRouter`；`VoyDebug` 移出 `overworld/`。**保留** `BellStrike`/`FieldInoculation`/`ActionFeedback`/`BlockBreakListener`/`OverworldVocabulary` | 1–1.5 天 | 中低：**不动 `register()` 的事件注册顺序** |
| P1-3 | **`blocks/` 四分 → 三件**：`BedActions` 并回 `GastrodiaBedBlock`；删 5 个 `export`（改 `OverworldVocabulary:66` 直接 import）→ 打断包内循环；保留 `BedCompat`（软兼容词表）与 `BedFx`（音画表） | 1 天 | 低：6 个 `MandatePhenomenaSpec` 用例 + 进游戏点一次菌塘 |
| P1-4 ★ | **拆 `MandatePhenomena`**：4 组策略移到 `mandate/policy/{Economy,BodyStrain,Sensory,BedPolicy}`，4 个常量进 `MandateConstants` | 0.5–1 天 | 低：**唯一能被现有测试完全验证**的一条 |
| P1-5 | **别名清零 + spike 词汇 5→2**：`PhaseResponse.section/spikeLevel` → `PhaseDetection.*`；`SpikeConfirmWindow` → `PhaseDetection.ConfirmWindow`；`ChainProbeAmplitude=1.9` → `PhaseDetection.BiDirectionalProbeLevel`；`StressParam.vSpike` 改名（**JSON key 保留**） | 2–3 h | 低：数值零变化，约 16 处引用 |
| P1-6 | **文件/类型改名对齐**：`ActionMsg.scala`→`MandateActionProtocol.scala`、`PlantDTO.scala`→`GastrodiaBedVerdict.scala`、`EventMsg.scala`→`EventDTO.scala`、`ReadoutVocab` 拆出 `Readout.scala`；4 个敲钟测试迁到 `mandate/BellRitualSpec` | 2–4 h | 低（同包改名） |
| P1-7 | **写三份"路标"文档**（收益最大的一步，见 §5.3） | 1 天 | 低 |

### P2 — 1–2 周，结构性改造（第二个迭代做）

| # | 动作 | 工作量 | 风险 |
| :--: | :--- | ---: | :--- |
| P2-1 | `MandateAgent` 13 参 → `AgentWiring` 对象；96 行 `integrate` → `stepOnce/projectCells/publish/maybePersist`；提取 `CellPulseState`（regime/心跳/spike 三个状态机） | 6–10 h | 中：先用 `SliceReplay` 做前后轨迹比对，再跑全量 |
| P2-2 | 打断 `blocks ↔ mandate` 循环：`(chunkX,chunkZ) => CellReadout` 函数注入（注册早于 `start()`，所以必须传函数） | 2–3 天 | 中：收益是 `blocks/` 可独立测试 |
| P2-3 | S2C 加协议版本号 + 去掉 5 处 `.ordinal`（文件内显式映射），合并 4 份心跳序数映射 | 1–1.5 天 | 低：payload id 与字段序不能动 |
| P2-4 | `VoyConfig` 三职责分家：`LatticeDefaults`（纯物理，去 Fabric 依赖）/ 配置 I/O / `VoyConfig` 门面 | 2–3 天 | 中 |

**做完 P0+P1 的预期**：一个行为跨文件 **7 → 3–4**；`overworld/` 10 → 6；main 少 ~570 行；可调参数同一锚点 3 → 1；**认知成本降 30–40%**，Gate B 从 3–6 工作日压到 2–3 天。

### 5.1 明确不该动的（硬约束）

1. `contracts/mandate-slice-v1.json` 禁手改；`MandateState.scala` + `ODESolver.scala` **全文**是 digest 目标——要动必须走 `-Dvoy.contract.write=true` 重生成 + 评审 diff。
2. `PhaseDetection.SectionLevel=1.2` / `SpikeLevel=1.8`；`HeartbeatParam.nominalPeriod=39.47`；`ReadoutParam.strained=0.45/burnout=0.95/oscillatingQ=0.40` —— 都被契约样本钉住。
3. 回放边界：`EventDTO` 不带时间戳/维度、actor 信箱全序、`SliceReplay` 不读时钟。
4. `@volatile` 单写多读快照（`MandateGrid.cellsV` / `MandateRuntime`）；不可换锁。
5. 存档 schema v3 与 NBT key 名；`voy_mandate` 身份；S2C payload id 与字段序。
6. mixin 注入点（Yarn 1.21.2 精确签名）与 `build.gradle` 的 Java/Scala 联合编译三行（mixin 能 import Scala object 的前提）。
7. `MandateWording` 必须保持零 Minecraft import（客户端 HUD 复用）。

### 5.2 明确不建议做

- 用通用 JSON 映射器替掉 `VoyConfig` 手写 parse/write：省 120 行但引入派生魔法，调试成本更高。
- 拆 `StressParam` 成两个 case class：它逐格进存档 → schema 变更 → 旧档全丢。只加分组注释。
- 给 `MandateWording` 做完整模板引擎：现在 95 条文案，模板化的收益不抵风险。

### 5.3 三份缺失的"路标"（最便宜的认知成本杠杆）

这是把 211 处"代码→文档"跳转、5 篇必读文档、42 个参数压成"一页纸"的产物：

| 文档 | 内容 | 工作量 | 预计省 |
| :--- | :--- | ---: | ---: |
| `docs/ARCHITECTURE.md` | ①三层数据流图（世界→actor→grid→readout→action→世界，标注每跳文件）②**契约锚清单**（哪两个文件全文哈希、哪些常量被钉住）③6 个参数类与 42 个 knob 的归属 ④"哪个文件是入口/哪个是实验台" | 4–6 h | Gate A 省 2–4 h |
| `docs/glossary.md` | 三列：中文概念 ↔ 代码符号 ↔ 玩家可见词 + "禁止出现在玩家文案"标注 + 旧→新对照（08→11） | 3–4 h | Gate B 省 3–5 h；美工/撰写同时受益 |
| `docs/README.md` 加"现行状态"列 | 每篇文档标 现行/作废/挂起/历史工作稿；把 08 移入 `archive/`，把 `Voy.World.Profile` 三处悬空引用改成"（已外迁，见 docs/README §已外迁）" | 1–2 h | 消灭"读错文档"的最坏情况 |

**这三份 + P0 全部，总量约 1.5–2 人日，预期砍掉 15–25% 的入门时间。**

---

## 6. 非技术协作者：美工与剧情撰写者

### 6.1 现状：同一个病，两个症状

| | 美工（懂 PS/像素画，不懂 MC 管线） | 剧情撰写者（懂民俗恐怖文本，不懂动力学） |
| :--- | :--- | :--- |
| **入门成本** | **8.5–16 h**（≈1–2 工作日） | **9–16 h**（≈1.5–2 工作日） `[估算]` |
| **现在能干活吗** | **半能**：能画，但不知道画什么、放哪、看不到效果、没验收标准 | **只能改 8 条**：lang 16 键里 8 个活、8 个死 |
| **最大卡点 1** | 零美术规范：尺寸/色板/命名/交接目录/验收清单全缺；唯一长文《素材渠道指南》在 git 外**且方向已被札记 10 作废** | 文本无容器：无 `advancement/`、无成书物品、无 `sounds.json`，卷宗/ARG 管线已外迁 |
| **最大卡点 2** | `textures/mob_effect/` **空目录** vs `MandateEffects.scala:40-42` 已注册 3 个效果 → 链条唯一断点 `[验证]` | **78% 玩家可见文案硬编码在 Scala**（95 条中文字面量 vs lang 8 条活键） |
| **要过的坑** | 12 个 blockstate variant ≠ 12 张贴图；16×16 不是 128×128；三面（top/side/bottom）；文件名即 id、错了静默紫黑 | `§a/§b/§c/§e/§7` 颜色码 168 处；`f"..."` 的 `%d/%.2f` 格式位；`lang` 双文件键集必须一致（有测试断言） |
| **"不查就写错"的术语** | 8 色语义色板（色彩即读数） | **73 条**术语，其中 8 条写错即自相矛盾：沉滞≠烧毁、镇死=沉滞、起搏≠校拍(5 态)、尖峰不受罚、Burnout≠静默、荧丝是虚构词、Slow 不碰身体、**臭是好兆头** |

**两者卡在同一处**：代码与文档之间没有中间层。美工缺"资源管线的书面规范与交接位"，撰写者缺"文案的数据落点"。

### 6.2 两类人的成本拆解

**美工 8.5–16 h**：读最小文档集 1.8–2.5 h（约 5000 汉字，不是全量）+ 术语与色板 0.5–1 h + 跑通资源管线 2–4 h（本机已就绪 0.5–1 h）+ 第一次独立产出 4–8 h。
> 关键开关：做一个 `run/resourcepacks/voy-art-dev/` 覆盖包后，"改图→看效果"从"重启客户端 + Gradle"降到"存盘 + F3+T"（0.5–1 h 投入）。**现在没有这个开关**。

**撰写者 9–16 h**：读必读文档 4.2–6.3 h（约 1.1 万字，按官方顺序会先撞上已作废的 08）+ 术语记忆 3–5 h（73 条，43 条需理解边界）+ 工具链 0.5–2 h + 第一次产出 1.5–3 h。
> 关键开关：**把 49 条 `MandateWording` 字面量外置到 lang**（6–10 h），撰写者才获得零代码路径。现在写进游戏的文本**必须改 Scala**，且要保护颜色码与格式位。

### 6.3 脚手架（19–29.5 h，约 2.5–4 人日）

| # | 脚手架 | 工作量 | 收益 | 回本点 |
| :--: | :--- | ---: | :--- | :--- |
| S1 | **术语对照表 `docs/glossary.md`** | 3–4 h | 两类人共用，消灭 8 条高危错法 | 第 1 个协作者 |
| S2 | **美术三件套**：`docs/art-guide.md`（16×16/面映射/12 variant 对照表/8 色板/禁区）+ `art/{inbox,briefs,accepted}/` + `docs/art-checklist.md` | 5–8 h | 美工 8.5–16 h → **4–6 h**，且第一次即可独立交付 | 第 1–2 个 |
| S3 | **文案外置**：49 条落 lang + 死键 lint + `textures/mob_effect/` 补 3 张图标 | 7–12 h | 撰写者获得零代码路径，省 5–8 h/人；修掉唯一美术链条断点 | 第 2–3 个 |
| S4 | 资源包预览骨架 + `preview.html` | 0.5–1 h | "改图→看效果" ≤ 1 分钟 | 立刻 |
| S5 | 开场 10 条文案 brief（札记 11 的"后续工作 4"目前**零文案**） | 2–3 h | 给撰写者第一周就能交、且真进游戏的任务队列 | 立刻 |

> 只做 **S1+S2+S3**（约 10–15.5 h，1.5–2 人日）即可把两类人的入门成本各压掉一半以上。

---

## 7. 如果只能做五件事（总计 < 1.5 人日）

1. **`docs/glossary.md`**（3–4 h）——一份文件同时救工程师、美工、撰写者三条线。
2. **删死通道与假字段**（P0-1，0.5 天）——消除"双发布通道"的假模型和"玩家被打印假数值"。
3. **`ARCHITECTURE.md` + 契约锚清单**（4–6 h）——把 211 处跳转压成一页，附"改什么会红"的清单。
4. **文案收口 + `0.03` 单一符号**（P0-2/P0-3，0.5 天）——为撰写者与后续所有改动降低半径。
5. **离线实验移出 main**（P1-1，2–4 h）——main 少 570 行，"哪段是生产路径"不再需要猜。

做完这五件，Gate A 预计从 12–24 h 降到 **8–16 h**，Gate B 从 3–6 工作日降到 **2–4 天**。

---

## 8. 附录：证据与未验证项

**三份逐文件证据**（各 435–548 行，全部带 `file:line`）：

| 文件 | 范围 | 关键产出 |
| :--- | :--- | :--- |
| `.dsh-scratch/onboarding-review/findings-inner.md` | `inner/` 17 文件 + `helpers/` + `VoyConfig` + 20 测试文件 + 契约 + 5 个 Python 探针 | 18 条问题、9 条简化、9 条硬约束 |
| `.dsh-scratch/onboarding-review/findings-game.md` | `mandate/`+`overworld/`+`blocks/`+`client/`+`mixin/` 34 文件 4222 行 | 53 条问题（S1–S4）、8 条简化、6 条边界 |
| `.dsh-scratch/onboarding-review/findings-content.md` | `docs/` 全量 + `resources/` 38 文件 + lang + 许可证 | 美术/撰写两套成本模型、16 条缺口、7 条脚手架 |

**Lead 抽检确认的 6 条关键断言**：契约 digest 全文哈希两文件（`SliceContract.scala:498-514`）；`MandateRuntime.snapshot` 零读点；`0.03` 三处定义；`ffd0a70` 的 "pure structural split / verbatim lift" 提交信息；`MandatePhenomenaSpec:216-225` 的 lang 键集断言；`ref/` 在 `.gitignore` 末行。

**未验证项**（结论对其不敏感）：`pack_format` 具体值；`F3+T` 是否重读 `build/resources/main`；`runClient` 在受限沙箱下能否启动（`GRADLE_USER_HOME` 在仓外，本次两次尝试均被沙箱拒绝，故 169 例耗时取自 19:29 的真实运行记录）；16×16 贴图是程序生成还是手绘占位。

**估算误差说明**：所有小时数是"阅读速度 6000–10000 汉字/h、术语 15–25 条/h、像素画 15–40 min/张"的经验区间，真人间可达 ±50%。但"两类非技术协作者各需 1–2 个工作日、卡点分别是『无规范』与『无容器』"这个定性结论对波动不敏感。
