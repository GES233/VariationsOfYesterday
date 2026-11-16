# 迁移计划：心跳即庇护（Heartbeat-as-Shelter）

> **本仓库内副本**：原稿是 2026-11-16 的本机工作稿（`ref/`，不入库、其它设备不可见），于同日迁入 `docs/reference/` 以便跨设备查阅；路径已改指仓库内文件，正文其余部分按原稿冻结保留，不随札记 11 后续措辞同步。
>
> 状态：**已定稿并升格（2026-11-16，Step 0–4 全部完成）**。现行文档：[札记 11](../design-note-11-heartbeat-shelter.md)；本文件保留为工作稿（含 14 条裁决的讨论过程与代码核实补丁），不再更新。
> 定位：工作稿 → 已升格为 `docs/design-note-11-heartbeat-shelter.md`。
> 前置阅读：[札记 08（脉冲语义）](../design-note-08-fast-variable-and-stagnation.md)、[札记 06（菌网与 AMD）](../design-note-06-mycelial-grounding.md)、讨论纪要 11-15（本机工作稿，未入库）。
> 裁定来源：2026-11-16「心跳是震荡，窗不再具备庇护；心跳带来庇护」。

---

## 0. 一页摘要

| 项 | 内容 |
|---|---|
| 变更性质 | **语义层重构 + 净删子系统**。不动四维 RK4 方程、不动 `contracts/mandate-slice-v1.json`、不动 actor 内核、不动经济 mixin 与方块层 |
| 核心动作 | 删除 `PulseStamp` / `PulseLedger`（庇护窗 + 冷却时间戳），新增 `Heartbeat` 轴（节律判定），`isSheltered` 改挂节律 |
| 代码规模 | 约 **−260 行**（`PulseLedger` 89 + spec 51 + 快照/同步/读数/裁决/配置散点）/ **+180 行**（`Heartbeat` + spec + 判定接线），净约 **−80 行** |
| 影响面 | 20 个文件（§3），其中 6 个是测试 |
| 存档 | `MandatePersistence.SchemaVersion` 2 → 3；旧档按现有逻辑丢弃并重生成 |
| 分步 | Step 0 观测 → Step 1 加轴 + A/B 开关 → Step 2 切默认 + 删窗 → **Step 2.5 c0 库存化（v2 新增）** → Step 3 校准 → Step 4 文档 |
| 最大风险 | 不是代码，是**开局全山停滞**（静息格敲钟无法起搏）与**判定需要 2–3 个周期才成立**（新格有 2–3 分钟空窗） |

---

## 1. 新语义

### 1.1 第三条读数轴：`Heartbeat`

替换 `CellReadout.pulseStamp`（庇护轴）。建议放在 `inner/veins/Heartbeat.scala`，纯函数、无 Minecraft 依赖、可单测。

```scala
enum Heartbeat:
  /** 已可判定之前的宽限期（新格、刚再接种、刚加载）。事件驱动，无时长常数。 */
  case Warming

  /** 健康节拍：周期在带内、连续 N 个周期稳定、占空比不超限。 */
  case Beating(period: Double, duty: Double, source: BeatSource)

  /** 心动过速：周期 < periodMin（含 8–30 s 的强起搏区）。 */
  case Fast(period: Double)

  /** 心动过缓：周期 > periodMax，或长期无穿越。 */
  case Slow(period: Double)

  /** 失律：周期抖动超差，或单周期占空比超限（爆发型）。 */
  case Arrhythmic(lastPeriod: Double, duty: Double)

enum BeatSource:
  case Intrinsic   // 格子处于振荡带内，自持
  case Paced       // 由外部节律（钟、激励源、晶链）牵着跳
```

**为什么保留 `source`**：故事上「心境/大山的自持心跳」和「外量替它起搏」是两件事，未来奖励/文案要能区分；机制上两者暂时同权（否则早期玩家无法生存）。

### 1.2 判定表

| 条件（按序） | 判定 |
|---|---|
| 周期数 < `minCycles`，且仍有生命迹象（`t − max(lastCross, bornAt) ≤ staleCycles × nominalPeriod`） | `Warming` |
| 从未起跳或太久没跳（`t − max(lastCross, bornAt) > staleCycles × nominalPeriod`） | `Slow` |

> **v2 修正**：v1 第一行的宽限期是 `t − bornAt < staleCycles × nominalPeriod ≈ 99 s`，但在 40 s 健康周期下凑够 `minCycles = 3` 个间隔需要 4 次穿越 ≈ 120 s —— 健康新格会在 99–120 s 之间掉出 `Warming` 又不够周期判定，形成空窗。修正为「仍有生命迹象」判据：以最近一次穿越（无穿越时为出生时刻）为锚，只要锚点距今不超过 `staleCycles × nominalPeriod` 就仍在 `Warming`；判死条件对称改为同一锚点。语义不变（能判定即结束、99 s 无任何穿越即判死），但消除了空窗。
| `period < periodMin` | `Fast` |
| `period > periodMax` | `Slow` |
| 最近 `minCycles` 个周期的变异系数 > `regularityTol` | `Arrhythmic` |
| 上一周期 `duty > dutyMax` | `Arrhythmic` |
| 否则 | `Beating` |

**关键点：不能再用瞬时 `v ≥ 1.8` 排除庇护。** 实测健康节拍中 `v ≥ 1.8` 的占空比是 6%–13%，旧规则会让健康心跳每十秒里约一秒被判“尖峰受罚”。新判定必须用周期统计量。

#### 1.2.1 参数：全部由模型量导出（不引入自由常数）

| 参数 | 取值 | 来源 |
|---|---|---|
| `nominalPeriod` | `39.47` | **测量**：契约 `contracts/mandate-slice-v1.json` 的 `settled_period`（s=1.0 饱和工作点）；自然带两端 43.7 / 36.4 的几何均值 39.9 与此一致 |
| `periodMin` | `0.75 × nominalPeriod ≈ 29.6` | 自然带最快周期是 36.4 s（s=0.7）；比它再快 25% 就只能是过驱动“硬顶”出来的节奏，不是自然节律 → 系数 0.75 |
| `periodMax` | `2.0 × nominalPeriod ≈ 78.9` | “最多漏一拍”的语义：隔两个自然周期才响一次已经不是节律，是偶发事件 → 系数 2.0 |
| `dutyMax` | `0.5 × plateauFraction(sat) ≈ 0.30` | 饱和工作点的高台期占周期 **60.6%**（草案 01 §1.1 实测）；健康节拍实测 6%–13%；阈值取饱和高台期的一半 |
| `minCycles` | `3` | 与现有 `isRegular` 同源：判“规律”本来就要 2 个间隔，再加当前这一拍 |
| `regularityTol` | `0.20` | 沿用 `ReadoutParam.regularityTol`，不新造 |
| `staleCycles` | `2.5` | 沿用 `ReadoutParam.staleCycles` |
| `overdriveGuard` | `1.6 × τ_bell = 8.0 s` | 札记 08 既有推导（1.6τ_bell）；且实测 8 s 节奏确实落入去极化阻滞（§2.2），两条独立证据一致 |
| `overloadPenaltyTicks` | `120`（6 s） | **唯一一个纯手感常数**，标 `TODO(calibrate)`，Step 3 在 60–200 tick 区间标定 |

> **纪律**：`Heartbeat.scala` 里不允许出现没有出处的字面量。能写成表达式的写成表达式（`0.75 * nominalPeriod`）；不能的必须三选一：`// derived: …` 注释、`measured:` 指向探针脚本、`TODO(calibrate)` + 试验区间。Step 0 顺带加一个 `HeartbeatParamSpec`，断言这些关系式（`periodMin == 0.75 * nominalPeriod` 等），防止将来改 `nominalPeriod` 时阈值不跟着动。

### 1.3 庇护定义

```scala
def isSheltered: Boolean =
  (heartbeat.isBeating || heartbeat.isWarming) &&
  mycelium.isViable &&
  !isSlowOffsetBelowKnee
```

- `Warming` 是**事件驱动、没有时长常数**：出现在「新注册格 / 再接种后 / 存档加载后」，结束于“已可判定”（凑够 `minCycles` 个周期）或“已可判死”（超过 `staleCycles × nominalPeriod ≈ 99 s` 无穿越）。它替代旧系统里“第一次敲钟就有窗”的即时反馈，避免开局/新格即受罚。
- 故意**不**排除 `isSpiking`：心跳的高相位本来就该被庇护。

### 1.4 经济 / 沉滞 / 肉体惩罚映射

| 新判定 | 经济档 | 玩家肉体 |
|---|---|---|
| `Beating` / `Warming` | `Maintained` | 无 |
| `Fast` / `Arrhythmic` | `Spiking` | `voy:pulse_saturation`（沿用 120 tick） |
| `Slow` | `Stagnant` | 无（沉滞只压经济） |

`MandateOverworld` 的 20 tick 循环与 `classifyEconomy` 的调用点不变，只换判定来源。

### 1.5 敲钟的新角色：从「开窗」变成「起搏 / 校拍」

| # | 条件 | Outcome（沿用枚举名） | 新效果 |
|---|---|---|---|
| 1 | 菌枯或越膝 | `DeadMyceliumBurnout` | 脉冲进方程；不锚 c0；无惩罚 |
| 2 | 冷却内（`minInterval` 保留 8 s 防连点） | `PulseOverloadBurnout` | 脉冲进方程；`voy:pulse_saturation`；不锚 c0 |
| 3 | 未锚振荡 + `Excitation` | `SpikeSwallowed` | 脉冲进方程；不锚 c0（语义仍成立：被自己的尖峰吞掉） |
| 4 | 其余 | `AnchoredOrMaintained` | 脉冲进方程；锚 c0 = 0.03 |

- **删除 `PulseBookkeeping` 的记账分支**：没有窗可记，`FirePulse.bookkeeping` 整个枚举可删（或降级为 `transmitter` 已足够）。
- 「40 s」从代码常数降级为文案/指引常数（`HeartbeatParam.nominalPeriod = 40.0` 仅用于提示语）。
- `SpikeSwallowed` 保留，因为它同时承担「相位不对时敲钟不锚 c0」的叙事信息。

---

## 2. 数值依据（本机实测，探针可复现）

### 2.1 自然心跳带（I = 0）

| s | 周期 | 峰值 v | 游戏判真尖峰（≥1.8） |
|---|---:|---:|---|
| 0.26 | — | 0.728(谷) | 静息 |
| 0.27 | 47.2 s | 1.776 | **否**（差 0.024） |
| 0.30 | 43.7 s | 1.807 | 是 |
| 0.40 | **39.5 s** | 1.852 | 是 |
| 0.50 | 37.6 s | 1.880 | 是 |
| 0.60 | 36.7 s | 1.903 | 是（但 `s ≥ 0.60` 开始酸蚀菌网） |

→ 自然心跳带 = **s ∈ [0.30, 0.60)**，上限与 `sustenanceBurnThreshold` 重合，**余量为零**。这就是叙事里「靠伤维持清醒」的数学形态。

### 2.2 敲钟作为起搏器（静息格 a = 0.7，α 核 area 12 / τ 5）

| 敲钟节奏 | 结果 | 判定含义 |
|---|---|---|
| 8 s | 去极化阻滞：`v_end = 0.88`，无周期 | `Fast`（危险区，正是现行冷却下限） |
| 12 s / 20 s | 2:1 / 3:1 牵入，周期 36–40 s | ⚠️ 见下注：v1 标 `Fast`，与 §1.2 判定表冲突，待 Step 3 复核 |
| 30 / 40 / 60 / 90 s | 1:1 起搏，周期 = 敲击间隔 | 30–60 s → `Beating`（`Paced`）；**90 s 超出 `periodMax≈79` → `Slow`** |

> **v2 注（计划内部不一致）**：12 s/20 s 牵入的观测周期是 36–40 s，在带内（> `periodMin ≈ 29.6`），且裁决 1 已定 `BeatSource` 不参与判定 —— 按 §1.2 判定表它们应判 `Beating(Paced)`，除非实测 CV 或 duty 越限落入 `Arrhythmic`。Step 3 用固化后的 pacemaker stats 探针复核这两种节奏的 CV/duty，再决定是否需要为「牵入比 > 1:1」增设判据。施工期 `HeartbeatSpec` 以 §1.2 判定表为准。

### 2.3 健康节拍的占空比

`s = 0.40`、`I = 0.5`、`s = 0.50` 三种健康节拍实测 `duty(v ≥ 1.8) = 4.2% / 4.4% / 5.8%`；1:1 起搏在 30–90 s 区间为 5.8%–13.0%。→ `dutyMax = 0.5 × 0.606 ≈ 0.30`：介于“健康最大值 13%”与“饱和高台期 60.6%”之间，取后者的一半。

### 2.4 既有常数溯源（顺带审计）

| 常数 | 值 | 出处 | 类别 |
|---|---|---|---|
| Hopf 点 `a_c` | 0.435 | `β·(v_c − v_c³/3) − v_c`，`v_c = −√(1−εβ)` | **推导（闭式）** |
| 膝 `burnoutA` | −0.4667 | `β·(2/3) − 1`（右膝 `v = +1`） | **推导（闭式）** |
| 静息→振荡起点 | `s = 0.265` | `s_onset = α − a_c`（M=1，k=1） | **推导** |
| 自然心跳带下界 | `s = 0.30` | 峰值需达 1.8 → 实测 onset 处峰值只有 1.776，（`s=0.27` 差 0.024） | **实测** |
| 自然心跳带上界 | `s = 0.60` | `sustenanceBurnThreshold`（札记 06 §2.2：AMD 酸蚀线） | 叙事/工程口径 |
| `sustenanceDepth` Δ | 1.2 | 令 `M = 0` 落在膝下：`α − Δ ≤ burnoutA → Δ ≥ 1.1667` | **推导（反解）** |
| `shelterWindow` | 40.0 s | 札记 08：“锚定一个自然振荡周期 ≈ 39.5” | **实测（取整）** |
| `minInterval` | 8.0 s | 札记 08：“≈ 1.6 τ_bell”；实测 8 s 节奏即阻滞 | **推导 + 实测互证** |
| `overloadPenaltyTicks` | 120 | 无出处 | **手感**（本次标 `TODO`） |
| `strained` / `burnout` | 0.45 / 0.95 | 契约工作点（stress 0.45 → Strained；0.95 → Burnout） | **契约钉死**（改则需重出契约） |
| `baselineCalming` c₀ | 0.03 | 契约 `c0Intact` | **实测（契约）** |
| `regularityTol` | 0.20 | 读数层原型，无推导 | **手感**（被测试覆盖） |
| `basinTol` / `minPeriod` / `staleCycles` | 0.25 / 1.0 / 2.5 | 读数层原型 | **手感**（被测试覆盖） |
| `initialStressPeak` | 0.18 | 场地初始化 | **手感**（且导致开局不在自然带 → 建议改成 `s_onset + 0.25·(0.60 − s_onset) ≈ 0.35`） |
| `hotspotDisturbance` / `ambientDisturbance` | 0.08 / 0.002 | 场地 | **手感**（40× 比例无解释） |
| `stressDoseForBlock` | 0.01 / 0.04 / 0.06 | 玩法 | **手感** |

结论：**真正缺出处的只有少数几个**——`overloadPenaltyTicks`、`regularityTol`、`basinTol`、场地扰动与初始应力。本次重构顺手把“心跳”相关的全部改成表达式；场地那几个留到 Step 3 一起标定（它们不属于心跳语义，属于开局布景）。

### 2.5 复现方式

探针在 `tmp/` 下，共 **4 个**：`fhn_probe.py`、`fhn_duty.py`、`fhn_pacemaker.py`、`fhn_pacemaker_stats.py`（v1 漏列了 `fhn_duty.py`——它是 §2.3 dutyMax 测量的出处，不能丢）。`tmp/` 被 gitignore；Step 0 把它们固化为仓库内 `tools/lab/` 下的小工具。

---

## 3. 改动清单（按文件）

| 文件 | 动作 | 步 | 备注/测试 |
|---|---|---|---|
| `inner/veins/Heartbeat.scala` | **新增**：enum + `HeartbeatParam` + 纯分类器 | 1 | 配 `HeartbeatSpec`（≥10 例） |
| `inner/veins/MandateReadout.scala` | `CellReadout` 增 `heartbeat` 字段；`isSheltered` 改实现；`PhaseTracker` 增周期统计（环形缓冲 + duty 累计）并导出不可变 `BeatStats`（见附录 C.2.2）；旧 `pulseStamp`/`isPulseSheltered` 暂留（Step 1）后删（Step 2）。**约束：`PhaseTracker` 公开 API 只加不改**（`SliceReplay`/`PhaseResponseSpec`/`MandateReadoutSpec` 直接构造它） | 1/2 | `MandateReadoutSpec` 10 处 |
| `inner/veins/PulseLedger.scala` | **删除**（`PulseParam` 里只保留 `minInterval` / `overloadPenaltyTicks`，移入 `HeartbeatParam`） | 2 | 删 `PulseLedgerSpec` |
| `inner/veins/MandateAgent.scala` | 删 `recordPulse` 与 `CellSlot.pulseStamp`；`FirePulse` 仍进方程；`pulseParam` 参数改名 | 2 | `MandateAgentSpec` 12 处 |
| `inner/veins/MandateSnapshot.scala` | 删 `pulseStamps` / `restorePulseStamps`；**v2 追加：快照携带每格 `BeatStats`（周期环 / lastDuty / lastCross / bornAt），消除读档后全场 `Warming` 悬崖（风险 2）** | 2 | 快照结构变化 |
| `mandate/MandatePersistence.scala` | 删两字段读写；`SchemaVersion = 3` | 2 | `MandatePersistenceSpec` 3 处 |
| `mandate/MandateSyncProtocol.scala` | `ClientCellView` 增 heartbeat 类别（手写位打包需 3 bit）+ 量化周期；`DebugCellTelemetry` 删 `remainingShelter/remainingCooldown`、换成 period/duty/heartbeat（Step 0/1 先只增不删，Step 2 删旧字段） | 0/2 | `MandateNetworkingSpec` 1 处 |
| `client/mandate/ClientMandateCache.scala`（**在 `src/client/` 源集**，v1 路径误写为主源集） | 跟随载荷字段 | 2 | — |
| `mandate/BellRitual.scala` | 四裁决；删 `PulseBookkeeping` 记账语义。**冷却判定改用每格 `lastStrikeAt: Double`**（存 `CellSlot`，不进快照，见附录 C.2.1） | 2 | 现有 `BellRitual` 测试改名 |
| `mandate/MandateWording.scala` + `lang/{zh_cn,en_us}.json` | 文案：「脉动庇护」→「心跳/节律」；删「win=」「cd=」类措辞 | 2 | 键集一致性测试 |
| `mandate/MandatePhenomena.scala` | `classifyEconomy` 改按 heartbeat | 1（开关旁路）/2 | `MandatePhenomenaSpec` 5 处 |
| `mandate/MandateOverworld.scala` | 沉滞/急性循环不变（读 `isSheltered`）；敲钟接线去掉记账参数 | 2 | — |
| `client/hud/MandateHud.scala` | 三行 telemetry 改成 heartbeat 类别 + period + duty；格子配色按新轴 | 2 | 手工验证 |
| `VoyConfig.scala` | `pulseParam` → `heartbeatParam`（`overdriveGuard` / `overloadPenaltyTicks` + `shelterModel` A/B 开关，Step 2 后删开关）；**另：`initialStressPeak`/`initialStressFalloff` 改导出式**（附录 C.1.2，独立 commit） | 1/2 | `VoyConfigSpec` **2 处**（`minInterval` 与 `shelterWindow` 两条断言，v1 写 1 处） |
| `docs/design-note-08-*.md` | 重写/替换为札记 11 | 4 | — |
| `AGENTS.md` / `docs/README.md` / `README*.md` | 机制描述同步 | 4 | — |
| `contracts/mandate-slice-v1.json` | **不动** | — | 契约只覆盖相位响应 |
| `SliceContract` / `SliceReplay` / `PhaseResponse` | **不动** | — | 已确认不引用庇护 |

**估算**：Step 1 约 +120 行（`Heartbeat` 80 + spec 120 + 接线，扣除部分占位）；Step 2 约 −300 行、+60 行样板。

---

## 4. 分步实施

### Step 0 · 观测层验证（0.5 场，不改语义）

- 在 `PhaseTracker` 里加周期统计与 duty 累计，但**只输出**（`/voy debug` 打印 `period/duty/heartbeat`），`isSheltered` 不动。
- 验收：runClient 里能对上 §2 的三个现象（自然带 40 s、8 s 阻滞、1:1 起搏）。
- 回退：纯删除新增代码。

### Step 1 · 加轴 + A/B 开关（1–2 场）

- `CellReadout.heartbeat` 接入；`VoyConfig.shelterModel = "window" | "heartbeat"`；`isSheltered` 按开关分派。
- 旧 `PulseStamp` 全保留，行为默认仍是 `window`。
- 验收：同一存档可切换两种庇护模型；测试两套各自绿；旧行为零回归。
- 回退：删开关并回到旧路径（代码零删除）。

### Step 2 · 切默认 + 删窗（1–2 场）

- 默认 `heartbeat`；删除 `PulseLedger` / `PulseStamp` / spec / 快照字段 / 同步字段；`SchemaVersion = 3`。
- 验收：全测试绿；旧档加载打印 discard 日志并重生成；runClient 体验通过。
- 回退：只能 `git revert`（删代码不可开关回退），所以 Step 1 必须先在真存档里跑过。

### Step 2.5 · c0 库存化解耦（L2，1 场；v2 新增）**已实现 2026-11-16**

排在 Step 2 之后：`BellRitual` 的锚定语义刚在 Step 2 重写，同文件不撞车。实现要点见裁决 11（源注册表通用化 `Chunk ↔ List[Source(kind,pos)]`、60 s 重平衡、剂量 0.008/封顶 0.04/τ 45 min、`C0ShelterLevel` 未锚判据、`CalmingStockSpec` 回归「拆一钟不归零」）。

- 敲钟「锚定 c0 = 0.03」改为**剂量注入**（有效敲击 +dose，封顶 ~0.04）；裁决表里的「已锚/未锚」分支判据从 `c0 ≤ 0` 改为浓度阈值（沿用 `C0ShelterLevel = 0.01`）。
- 钟方块存在 = 涓流源（平衡浓度 0.03）；拆钟 = 停涓流 + 保留应力冲击 0.06，**不再瞬时归零**。区块级来源簿记随 `MandatePersistence` 持久化。
- 接线层每 ~60 s 按 simTime 广播重平衡事件：`c0 ← c0·exp(−Δt/τ) + trickle`，τ ≈ 45 min；引擎零改动，回放由信箱全序保证。
- 验收：`BellRitual`/持久化测试更新；实机验证「两个钟拆一个，c0 缓降不归零」。

### Step 3 · 校准（1 场）

- 用 §2 数据复核 `periodMin / periodMax / dutyMax` 三个导出系数，以及唯一的 `TODO` 常数 `overloadPenaltyTicks`；playtest 20 分钟；记录“出生是否突兀、心跳是否可读、失律是否可救”。
- **v2 追加（实机验证发现，已裁定方向）——余脉区应力平衡**：现状恢复驼峰峰值 0.0225 < 安抚环 c0=0.03，带内无稳定不动点，余脉 ~4 分钟即静默（玩家实测「有的周期自发沉滞」）。**方向已定：U 形三幕弧（裁决 12），寿命局时尺度（裁决 9）**。
- 具体标定：场地改**三层扰动场**（c0 数值不动）——热点核 `u_core`（半径 < 2，缓升）、余脉环 `remnantDisturbance ∈ (0.009, 0.04)`（半径 < 4，即 8×8 余脉圈）、外围 `ambientDisturbance`（保持 0.002，静息平衡 s≈0.03）。目标：第一幕（c0 压着）20–30 min、谷底不跌破 `s_onset`、第三幕（酸蚀冲刺）40–60 min；`u_core` 同时满足裁决 13 的救赎窗口（锚定后净项 < 0）。探针扩展：`tools/lab/` 加 slow-stock 仿真（stress + activity + c0 一阶漏失），扫 `(u_remnant, u_core, s0)` 空间并打印 s(t)/节拍弧线，手算可校验。
- 验收：一份 5 行调参记录进本文件附录。

**标定记录（2026-11-16，`tools/lab/fhn_slow.py` 慢系统探针；dt=0.05，4 h 视界，含 c0 漏失）**

1. `u_core` 0.08 → **0.036**：核心到酸蚀线 ~33 min（0.038→30 min、0.040→27 min）；锚定 c0=0.04 时可压回（0.036：60 min 后 s 0.60→0.48；0.038 勉强；0.040 压不动）→ **救赎窗口上界 ≈ 0.038**（裁决 13 落地）。
2. **三层扰动场**：core 0.036（r<2）/ remnant **0.034**（2≤r<4，新增 `remnantRadius=4.0`、`remnantDisturbance`）/ ambient 0.002（r≥4）；`MandateRuntime.buildGrid` 按距离分层赋值。
3. 余脉弧（r=3，s0=0.286）：**不跌破 onset**（谷底 0.277–0.280，节拍全程持续，周期 39.5 s），~47 min 到酸蚀线；r=2 约 41–53 min；核心 ~33 min **先死**——「中心先坏、环带随后」的时间序成立。
4. 安抚环 r=4.5（无余脉扰动）：余脉扰动之外保持静默下滑至 0；外围静息（平衡 s≈0.007）。
5. 已知限制：探针无噪声、无外部脉冲；实机中节拍一旦跌破 onset 不会自愈重启（需玩家敲钟重新起搏）——playtest 必须验证「失拍 → 敲钟救回」路径；旧存档保留逐格 disturbance（存档 > 配置），验证三幕弧需**新世界**。

### Step 4 · 文档收束（0.5 场）

- 本文件升格为 `docs/design-note-11-heartbeat-shelter.md`（定稿状态）；札记 08 标注“已被 11 取代”；更新目录与 AGENTS。

---

## 5. 存档 / 网络 / 文案迁移

| 面 | 处理 |
|---|---|
| 存档 | `SchemaVersion 2 → 3`；`fromNbt` 现有逻辑是「版本不符即丢弃并重生成」，沿用即可（0.1 阶段无外部用户） |
| 网络 | `ClientCellView` 换字段；Fabric payload codec 无版本协商，**要求客户端与服务端同版本**（本就如此，因为 `kotori_scala` 也在 `depends` 里）；`DebugCellTelemetry` 删字段即可 |
| 文案 | 全仓搜「庇护窗 / pulse / win= / cd=」，替换为「心跳 / 节律 / 周期 / 占空比」；lang 只增不改键（`term.*`） |
| HUD | 图例从「Calm + 脉冲(庇护)」改为「心跳(节律)」；新增「失律 / 过速 / 过缓」三色 |

---

## 6. 测试计划

**新增** `HeartbeatSpec`（≥10 例）：

1. 周期 40 s、稳定、duty 0.10 → `Beating`
2. 周期 20 s → `Fast`
3. 周期 120 s → `Slow`
4. 周期抖动 25% → `Arrhythmic`
5. 单周期 duty 0.45 → `Arrhythmic`
6. 无穿越 → `Slow`
7. 3 个周期前 → `Warming`，且 `isSheltered` 为真
8. `Warming` 期间菌网枯死 → `isSheltered` 为假（`mycelium`/`knee` 仍然优先）
9. 越膝：即便 `Beating` 也不庇护
10. A/B 开关两侧 `isSheltered` 的等价性基准（同一 readout，window 模型用旧逻辑）

**修改**：`MandateAgentSpec`、`MandateReadoutSpec`、`MandatePhenomenaSpec`、`MandatePersistenceSpec`、`MandateNetworkingSpec`、`VoyConfigSpec`。
**删除**：`PulseLedgerSpec`。
**不动**：`SliceContractSpec`、`SliceReplaySpec`、`PhaseResponseSpec`、`PulseCouplingSpec`（链路机制与庇护无关）。

**集成验收**（runClient，`/voy debug on`）：
- 自然带格：周期 36–47 s，HUD 显示 `Beating`
- 8 s 连敲：HUD 显示 `Fast` + 阻滞，玩家吃 `pulse_saturation`
- 12–20 s 连敲：周期 36–40 s（牵入）
- 40 s 起搏：1:1，`Paced`

---

## 7. 风险与回退

| # | 风险 | 对策 |
|---|---|---|
| 1 | **开局全山停滞**：初始 `initialStressPeak = 0.18` 在自然带（`[s_onset=0.265, 0.60)`）之外，且静息格敲钟不能起搏 | **已定（v2）**：`initialStressPeak = s_onset + 0.25·(s_acid − s_onset) ≈ 0.349`，余脉区定为 **8×8 区块**（带内半径 4 格），`initialStressFalloff = (peak − s_onset)/4 ≈ 0.02`；外加 `Warming` 宽限兜底 |
| 2 | **新格 2–3 分钟空窗**：判定需要 `minCycles × period` | `Warming` 状态 + 进入/退出滞回（连续 2 个不合格周期才退出 `Beating`）。**v2 补充（实机验证发现）**：读档后全场 tracker 归零 → 所有格子统一 `Warming` ~99 s，随后带外格子同时判死翻色，形成「全场庇护悬崖」；且读档重开 = 白嫖 99 s 宽限。对策：Step 2 快照（反正升 SchemaVersion 3）携带 `BeatStats`（周期环 / lastDuty / lastCross / bornAt 对齐 simTime），消除悬崖 |
| 3 | 心跳抖动导致沉滞闪烁 | **v2 已实现（Step 1）**：`Heartbeat.smooth` 退出滞回——离开 `Beating` 需**连续 2 个不合格周期**（按时间 2 × `nominalPeriod`，不是按 tick：一个坏判定会持续数百 tick）；进入 `Beating` 与非 `Beating` 间转换即时。`exitBadCycles = 2` 进 `HeartbeatParam` 与配置 |
| 4 | 玩家不知道自己该做什么（窗没了，钟声不再“上 buff”） | 文案与 HUD 必须把「拍子」可视化（周期数、稳定度）；前 3 分钟给一次强制示范（旧钟敲三下 → 菌光按节拍亮） |
| 5 | 8–30 s 区间与 `Fast` 惩罚重叠（数值上 8 s 阻滞、12–20 s 牵入） | 统一归 `Fast`；惩罚同 `pulse_saturation`，不额外加档 |
| 6 | 删代码后不可开关回退 | Step 1 必须在真实存档里跑过至少一次完整负载/存读；Step 2 单独一个 commit |

---

## 8. 裁决状态

**已定（2026-11-16）**

1. `Paced` 算庇护；`BeatSource` 只记录来源，不参与判定。
2. 8 s 保留，改名 `overdriveGuard`：语义从“冷却”改为“过驱动保护”（`1.6 × τ_bell`，与实测阻滞互证）。
3. ~~`Warming` 宽限多长~~ —— 改为**不设时长常数**：事件驱动，能判定即结束，或 `staleCycles × nominalPeriod` 判死。
4. 旧档直接丢弃（`SchemaVersion = 3`）。
5. `PulseBookkeeping` 整个删除。

**三项后续裁决（2026-11-16 全部落定，原为“仍需点头”）**

6. ✅ 接受“心跳模块内不得出现无出处字面量”的纪律，并加 `HeartbeatParamSpec` 断言关系式（防止改 `nominalPeriod` 时阈值不跟着动）。
7. ✅ `initialStressPeak` 改导出量 `s_onset + 0.25 × (s_acid − s_onset) ≈ 0.349`；余脉区定为 **8×8 区块**（带内半径 4 格），`initialStressFalloff = (peak − s_onset)/4 ≈ 0.02` 同样写成导出式。已核实无任何测试断言初始应力（`VoyConfigSpec` 只断言 `pulseParam`），改动独立成 commit。
8. ✅ `regime` 轴这版不动；HUD/文案以 `heartbeat` 为准，`regime` 退化为辅助信息（未来再议是否改挂 `a′ = a − βI`）。

**余脉与 c0 讨论（2026-11-16 第二轮，全部落定）**

9. ✅ **余脉寿命 = 局时尺度 30–60 min**，不追求永久稳态。数学依据：自然带全在恢复驼峰下降支（平衡点不稳定）且自激 `ηq` 是正反馈，带内稳定不动点在冻结方程下不存在——「永久」需要动契约方程，不成比例。
10. ✅ **「镇死 = 沉滞」是世界观公理**：c0 = 山的镇静剂（把应力压出带下沿 → 停跳 → Slow → 沉滞）。老寨「镇山」是待超越的旧范式，玩家学会「养心跳」。c0 的抗酸蚀与菌光/前兆签名角色不变；契约锚值 0.03 一字不动。文案必须在 Step 4 把这个反转讲清楚。
11. ✅ **c0 解耦 = L2 库存化**（不动引擎、不碰契约）：敲钟从「瞬时锚定 0.03」改为**剂量注入**（有效敲击 +dose=0.008，封顶 0.04）；源方块存在提供涓流（平衡 = 0.03 × 权重 = 契约「完好村钟」工作点）；c0 按 **τ=45 min** 一阶漏失；拆钟 = 停涓流 + 保留应力冲击 0.06，**不归零**（修复「两个钟拆一个就没 c0」）。
    - **源模型通用化（2026-11-16 追加裁定）**：c0 不专属钟——世界是 `Chunk ↔ List[Source(kind, pos)]` 注册表（`MandateSourceKind` 目前只有 `Bell`，AMD/古堰等后续加权重即可）；注册表随存档持久化，重平衡时对**已加载区块**按世界真值重建（顺带自愈爆炸/活塞破坏），未加载区块沿用持久化副本。
    - 实现：`CalmingStock`（纯函数：指数逼近源平衡 + 剂量）与 `MandateCalming`（接线：注册表、扫描、60 s 重平衡广播），引擎零改动、回放由信箱全序保证；「未锚】判据从 `c0 ≤ 0` 改为 `c0 < C0ShelterLevel(0.01)`。L3（`dc0/dt` 方程化）排入下一个契约重生成窗口，与札记 07 突触解耦搭车。
    - **推论**：worldgen 的安抚环 c0=0.03 没有实际源方块，按库存模型会在 ~45 min 内漏失——「旧安魂曲无源则散」；而这恰好是 U 形弧第二幕的机制之一（c0 消散 → 净应力回升 → 余脉回稳），Step 3 慢系统探针必须把 c0 漏失纳入仿真。
12. ✅ **余脉弧线 = U 形三幕**（L2 的自然涌现，只要 `u_remnant ∈ (0.009, 0.04)`）：第一幕残余安魂曲压着（c0≈0.03，心跳放缓）；第二幕 c0 漏失后谷底回稳（「曲终后山的脉搏又清晰了」）；第三幕 c0 耗尽后冲向酸蚀线（看得见的 deadline）。
13. ✅ **热点核必须可救**：`u_core` 封顶在「锚定 c0 + 自然恢复可压制」的水平（即锚定后净项 < 0；参考线 `u_core < ρ·0.03 + recovery(s) ≈ 0.0525`）。保住「重锚 → 泄压 → 接种」救赎链（札记 06 §7 裁定的成本路径）。现状 `u=0.08` 下锚定后净项仍 > 0，烧掉的核在当前版本是永久死地——必须随 Step 3 标定修正。出生区可离开性已核实：无任何定身效果，基线沉滞不碰玩家身体，急性档最重 Slowness 0 + 挖掘 ×0.09，步行永远可行。
14. ✅ **空间分布结论 + AMD 泄压设施立项**：不可救死地在现参数下**只存在于热点核**（u=0.08 > 全部汇 0.0525）；其它山谷 ambient=0.002、汇恒占优、**永远自愈**（s 0.6→0.45 无 c0 约 29 min，锚定 c0 后约 6 min；采掘一次性注入同样被吸收），裁决 13 修复唯一结构性死地。AMD 治理设施（石灰石沟/人工湿地/古堰）是札记 06 §4 已设计的 c 通道内容，**单独立项、排在心跳迁移之后**：接线 `SetCalming`，沿用「c ≳ 2u 才拉得下、单组只能减速、叠多组才能灭火」的实测约束；教学文案主题为「镇（泄压）/养（起搏）」双手套。设施落地后热点核救赎升级为工程解法（c 叠加 > u_core），裁决 13 的封顶为无设施玩家保留底线。

---

## 附录 A：旧概念 → 新概念对照

| 旧 | 新 |
|---|---|
| 脉冲庇护窗 `PulseStamp` | 心跳 `Heartbeat` |
| 敲钟开窗 | 敲钟起搏 / 校拍 |
| 40 s 窗时长 | 40 s 健康周期参考值（文案用） |
| `minInterval` 8 s 冷却 | `overdriveGuard` 8 s（`1.6 × τ_bell`，过驱动保护） |
| 沉滞 = 窗外 | 沉滞 = 无心跳 / 心跳过缓 |
| 经济档按窗 | 经济档按节律 |
| `SpikeSwallowed`（不记账） | `SpikeSwallowed`（不锚 c0） |
| `PulseSettlement` | 删除 |

## 附录 B：与既有文档的两处冲突（本计划会顺带解决）

1. **札记 08 §2.1 vs 代码**：文档说冷却拒绝时“保持原 `PulseStamp` 不变”，代码与 `PulseLedgerSpec` 断言“清空”。Step 2 删除该子系统后冲突自然消失；**若 Step 2 延期，须先按文档修代码与测试**。
2. **`regime` 仍按 `s` 分档**：`I` 起搏时会与心跳轴不一致（`s=0`、`Calm`、却在跳）。本计划不改 `regime`，但 HUD/文案必须以 `heartbeat` 轴为准，`regime` 退化为辅助信息（未来再议是否改挂 `a' = a − βI`）。

## 附录 C：v2（2026-11-16）裁决落实与代码核实补丁

### C.1 三项裁决

见 §8 第 6–8 条。其中裁决 7 的「中心」有明确几何含义：地脉格网 32×32（`originX/Z = −16`）的几何中心即区块 (0,0) 出生区；初始应力是以该中心为峰值的圆锥场 `s = max(0, peak − d × falloff)`，`d` 以格（区块）计。8×8 余脉区 = 带内半径 4 格（圆场内切 8×8 方区，四角略出带属正常），与 `hotspotRadius = 2`、`calmingRadius = 5` 构成同心三层（扰动核 / 余脉心跳 / 安抚环）。

### C.2 代码核实补丁（施工前必读）

1. **`lastStrikeAt` 替代冷却谓词**：`BellRitual` 裁决 #2 与 `MandateAgent` 共享 `pulseStamp.isInCooldownAt`；删除 `PulseStamp` 后，过驱动保护判定改用每格 `lastStrikeAt: Double`（存 `CellSlot`，**不进快照**——8 s 冷却跨存档无意义，加载后视为已过冷却，快照反而更简）。
2. **`BeatStats` 不可变快照层**：`Heartbeat.classify` 是纯函数，而周期统计活在 actor 内的 `PhaseTracker` 里；定义 `BeatStats(periods, lastDuty, lastCross, bornAt)` 由 tracker 每 tick 导出、随 `CellReadout.heartbeat` 构造，保持分类器纯函数可单测。
3. **`PhaseTracker` 公开 API 只加不改**：`SliceReplay`/`PhaseResponseSpec`/`MandateReadoutSpec` 直接构造它；`crossTimes` 无界 List 顺带改环形缓冲，但保留 `crossings` 接口。判定表用**变异系数**（最近 `minCycles` 个周期），与现有 `isRegular` 的两点判据 `|i1−i2| ≤ tol·mean` 并存，不复用。
4. **网络编码**：`ClientCellView.isSheltered` 是手写位打包（`FlagSheltered`）；heartbeat 类别需 3 bit + 量化 period；duty 与精确 period 只进 `DebugCellTelemetry`（替换 `remainingShelter/remainingCooldown`），避免每格载荷膨胀。
5. **探针是 4 个**：见 §2.5（`fhn_duty.py` 是 dutyMax 出处）。
6. **`pulse_saturation` 保持事件触发**：现状是敲钟过驱动那一敲施加（`MandateOverworld` 用 `overloadPenaltyTicks`）；Step 1/2 不得改成 20-tick 循环按 `Fast` 补刀，否则玩家在 `Fast` 期间被反复上药。
7. 路径更正：`ClientMandateCache.scala` 在 `src/client/` 源集；`VoyConfigSpec` 有 2 处断言受 `pulseParam → heartbeatParam` 改名影响。

### C.3 计划内部不一致（已标注，Step 3 复核）

§2.2 的 12 s/20 s 牵入行与 §1.2 判定表冲突，详见 §2.2 表下注解。
