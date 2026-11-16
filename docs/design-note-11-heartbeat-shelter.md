# 札记 11（已定稿）：心跳即庇护——节律轴、c0 库存与 U 形余脉弧

> 状态：**已定稿落地**（2026-11-16；Step 0–4 全流程完成）。
> 取代：[札记 08（脉冲语义）](design-note-08-fast-variable-and-stagnation.md)。
> 前置阅读：[札记 06（菌网与 AMD）](design-note-06-mycelial-grounding.md)、[草案 01](design-draft-01-living-world.md)、[札记 07（脉冲耦合）](design-note-07-pulse-coupling.md)。
> 裁定来源：2026-11-16「心跳是震荡，窗不再具备庇护；心跳带来庇护」。工作稿存档（含 14 条裁决的完整讨论与代码核实补丁）见 [docs/reference/heartbeat-shelter-migration-plan-2026-11-16.md](reference/heartbeat-shelter-migration-plan-2026-11-16.md)。

---

## 0. 一页摘要

| 项 | 内容 |
|---|---|
| 变更性质 | **语义层重构 + 净删子系统 + c0 库存化解耦**；不动四维 RK4 方程、不动 `contracts/mandate-slice-v1.json`、不动 actor 内核、不动经济 mixin 与方块层 |
| 核心动作 | 删除 `PulseStamp` / `PulseLedger` / `PulseBookkeeping`（庇护窗 + 记账），新增 `Heartbeat` 节律轴；`c0` 从「瞬时锚定」改为「源驱动的泄漏库存」 |
| 存档 | `MandatePersistence.SchemaVersion` 2 → 3：每格携带 `BeatStats`（节奏记忆），另新增可选字段「安抚源注册表」 |
| 现状 | 全部落地；测试全绿；分步记录见 §4，三幕弧标定记录见附录 C |

---

## 1. 新语义

### 1.1 第三条读数轴：`Heartbeat`

`inner/veins/Heartbeat.scala`（纯函数、无 Minecraft 依赖、可单测）：

```scala
enum Heartbeat:
  case Warming                                   // 已可判定之前的宽限期（事件驱动，无时长常数）
  case Beating(period, duty, source: BeatSource) // 健康节拍：周期在带内、连续 minCycles 稳定、duty 不超限
  case Fast(period)                              // 心动过速：period < periodMin
  case Slow(period)                              // 心动过缓：period > periodMax，或长期无穿越
  case Arrhythmic(lastPeriod, duty)              // 失律：周期抖动超差，或单周期占空比超限

enum BeatSource:
  case Intrinsic                                 // 格子处于振荡带内，自持
  case Paced                                     // 由外部节律（钟、激励源、晶链）牵着跳
```

`BeatSource` 只记录来源、**不参与判定**；`Paced` 与 `Intrinsic` 同权庇护（否则早期玩家无法生存）。

### 1.2 判定表（v2 锚规则）

| 条件（按序） | 判定 |
|---|---|
| 周期数 < `minCycles`，且仍有生命迹象（`t − max(lastCross, bornAt) ≤ staleCycles × nominalPeriod`） | `Warming` |
| 同上但已判死（锚点过期） | `Slow(NaN)` |
| `period < periodMin` | `Fast` |
| `period > periodMax` | `Slow` |
| 最近 `minCycles` 个周期的变异系数 > `regularityTol` | `Arrhythmic` |
| 上一周期 `duty > dutyMax` | `Arrhythmic` |
| 否则 | `Beating` |

**锚规则的意义**：40 s 健康周期凑够 `minCycles = 3` 个间隔需要 ~120 s，而 `staleCycles × nominalPeriod ≈ 99 s`；若以出生时刻为锚，健康新格会在 99–120 s 间出现判定空窗。改为以**最近一次穿越**为锚（无穿越时退回出生时刻），语义不变、空窗消除。

**退出规则**（风险 3 对策，`Heartbeat.smooth`；2026-11-16 晚修订）：只有**失律**（`Arrhythmic`）需要抖动保护——它是唯一能被单个周期翻掉的判定（单周期占空比超限，或周期窗里一个离群值）。离开 `Beating` 需连续 `exitBadCycles = 2` 个**本格自己的周期**（`2 × periodOf(raw)`，而非 `2 × nominalPeriod`：旧口径对 `Fast` 是 4 个坏拍、对 `Slow` 还不到 1 拍，从不是「两个坏拍」）。`Fast`（过速）与 `Slow`（静默）**即时发布**：前者已过 `minCycles` 均值，后者已等满 `staleCycles`，再加一段滞回就是把同一段等待算两遍（旧口径下令停跳的格子继续报 `Beating` 约 178 s = 99 s 陈旧 + 79 s 滞回）。进入 `Beating` 与一切非 `Beating` 之间的转移仍然即时。

### 1.3 庇护定义

```scala
def isSheltered: Boolean =
  (heartbeat.isBeating || heartbeat.isWarming) &&
  mycelium.isViable &&
  !isSlowOffsetBelowKnee
```

- `Warming` 事件驱动、无时长常数：出现在「新注册格 / 再接种后 / 存档加载后」，结束于「已可判定」或「`staleCycles × nominalPeriod` 无穿越判死」。
- **故意不排除 `isSpiking`**：心跳的高相位本来就该被庇护。旧规则「瞬时 `v ≥ 1.8` 排除庇护」会让健康心跳每 10 s 里约 1 s 被判受罚（健康节拍 duty 实测 4%–13%），必须废除。

### 1.4 经济 / 肉体映射

| 判定 | 经济档 | 玩家肉体 |
|---|---|---|
| `Beating` / `Warming` | `Maintained` | 无 |
| `Fast` / `Arrhythmic` | `Spiking` | 急性档（`voy:stagnation_spike` + 缓慢；敲钟过驱动另吃 `voy:pulse_saturation`） |
| `Slow` | `Stagnant` | 无（沉滞只压经济） |

生态死亡（菌枯/越膝）在经济轴上恒为 `Stagnant`，与节律判定无关。

### 1.5 敲钟：从「开窗」到「起搏 / 校拍」

| # | 条件 | 裁决 | 效果 |
|---|---|---|---|
| 1 | 菌枯或越膝 | `DeadMyceliumBurnout` | 脉冲进方程；不锚 c0；无惩罚 |
| 2 | 过驱动守卫内（`simTime − lastBellStrikeAt < overdriveGuard` = 8 s） | `PulseOverloadBurnout` | 脉冲进方程；`voy:pulse_saturation`；不锚 c0 |
| 3 | 未锚振荡（`c0 < C0ShelterLevel`）+ `Excitation` | `SpikeSwallowed` | 脉冲进方程；不锚 c0 |
| 4 | 其余 | `AnchoredOrMaintained` | 脉冲进方程；c0 剂量注入 + 注册钟源 |

- **饱和但菌活的格子可以锚 c0**（旧 `SaturatedNoAnchor` 删除）——重锚是失能救赎链的一环（札记 06 §7 成本路径）。
- 钟的视觉/听觉反馈四态可分：起搏＝琥珀铜尘 + 音符；校拍＝紫水晶清音 + 音符；失锚＝蜡烛闷熄 + 灰；过密＝锈红爆散 + 低音共鸣；无应＝几缕死灰。

### 1.6 世界观公理：「镇死 = 沉滞」

`c0` 是山的**镇静剂**：它把应力压出自然带下沿 → 停跳 → `Slow` → 沉滞。老寨的「镇山」范式（安抚到静止）是待超越的旧范式，新范式是「养心跳」（起搏 / 校拍 / 晶链）。`c0` 的抗酸蚀救命角色与菌光/前兆签名角色不变；契约锚值 0.03 一字不动。

### 1.7 `c0` 库存化：源注册表

`c0` 不是任何单个方块的属性，而是**带源汇的慢库存**（钾离子式）：

- 方程：`c0' = c0 + (平衡 − c0)·(1 − e^(−Δt/τ))`，平衡 = `0.03 × 源权重`，`τ = 45 min`，封顶 `0.04`；剂量：有效敲击 +0.008。
- 源模型：世界是 `Chunk ↔ List[Source(kind, pos)]` 注册表（`MandateSourceKind` 目前只有 `Bell`；AMD/古堰等后续加权重即可）；注册表随存档持久化，每 60 s 重平衡时对**已加载区块**按世界真值重建（顺带自愈爆炸/活塞破坏），未加载区块沿用持久化副本。
- 拆钟 = 移除一个源，**涓流减半不归零**；worldgen 安抚环没有源方块，按模型 ~45 min 漏失——「旧安魂曲无源则散」。
- 引擎零改动：重平衡事件走信箱全序，回放确定。

### 1.8 颜色语言（HUD / 聊天 / 效果图标统一）

| 语义 | 色 | 用途 |
|---|---|---|
| 起搏 `Beating` | 绿 `#38B86D` | 方块填充 / §a 庇护在线 |
| 预热 `Warming` | 青绿 `#66A98F` | 方块填充 / 宽限 |
| 过缓 `Slow` | 蓝灰 `#526275` | 方块填充 / 沉滞播报 §c / `stagnation` 效果色 |
| 过速 `Fast` | 橙 `#EB6828` | 方块填充 / §e 预警 |
| 失律 `Arrhythmic` | 红 `#FF2E2E` | 方块填充 / §e 预警 / `stagnation_spike` 效果色 |
| 生态死亡 | 暗红 `#7D2948` | 方块填充 / §4 不可逆 |
| 校拍/起搏成功 | 青 §b | 消息 |
| 失锚（可恢复失误） | 黄 §e | 消息 |
| 调试数值 | 灰 §7/§8 | 消息 |

尖峰不闪红：高相位属于心跳本身（「近期变化」里以信息色 §b 显示为「地鸣尖峰」）。

---

## 2. 数值依据（探针可复现）

### 2.1 自然心跳带（I = 0）

| s | 周期 | 峰值 v | 判真尖峰（≥1.8） |
|---|---:|---:|---|
| 0.26 | — | 0.728(谷) | 静息 |
| 0.27 | 47.2 s | 1.776 | 否（差 0.024） |
| 0.30 | 43.7 s | 1.807 | 是 |
| 0.40 | **39.5 s** | 1.852 | 是 |
| 0.50 | 37.6 s | 1.880 | 是 |
| 0.60 | 36.7 s | 1.903 | 是（`s ≥ 0.60` 开始酸蚀菌网） |

→ 自然心跳带 = `s ∈ [0.265, 0.60)`，上限与 `sustenanceBurnThreshold` 重合、余量为零——叙事「靠伤维持清醒」的数学形态。

### 2.2 敲钟作为起搏器（静息格 a = 0.7，α 核 area 12 / τ 5）

| 节奏 | 结果 | 判定 |
|---|---|---|
| 8 s | 去极化阻滞（`v_end = 0.88`，无周期） | 过驱动守卫下限的物理依据 |
| 12 / 20 s | 2:1 / 3:1 牵入，周期 36–40 s | 带内 → `Beating(Paced)`；不另设判据（裁决 1） |
| 30–60 s | 1:1 起搏，周期 = 敲击间隔 | `Beating(Paced)` |
| 90 s | 1:1，周期 90 s | 超出 `periodMax ≈ 79` → `Slow` |

### 2.3 占空比

健康节拍 `duty(v ≥ 1.8)` 实测 4.2% / 4.4% / 5.8%；1:1 起搏 5.8%–13.0%；饱和工作点高台期 60.6%。→ `dutyMax = 0.5 × 0.606 ≈ 0.30`。

### 2.4 参数溯源（纪律：心跳模块无无出处字面量）

| 参数 | 值 | 出处 |
|---|---|---|
| `nominalPeriod` | 39.47 | **测量**：契约 `settled_period`（s=1.0 工作点） |
| `periodMin` | `0.75 × nominalPeriod ≈ 29.6` | **推导**：自然带最快 36.4 s 再快 25% 只能是过驱动 |
| `periodMax` | `2.0 × nominalPeriod ≈ 78.9` | **推导**：「最多漏一拍」 |
| `dutyMax` | `0.5 × 0.606 ≈ 0.303` | **实测**：饱和高台期的一半 |
| `minCycles` | 3 | **推导**：2 个间隔 + 当前一拍 |
| `regularityTol` / `staleCycles` | 0.20 / 2.5 | 沿用 `ReadoutParam` |
| `overdriveGuard` | `1.6 × τ_bell = 8.0 s` | **推导 + 实测互证**（8 s 阻滞） |
| `exitBadCycles` | 2 | 风险 3 滞回裁定：仅 `Arrhythmic`，单位＝**本格周期** |
| `overloadPenaltyTicks` | 120 | `TODO(calibrate)`：唯一纯手感常数 |
| `τ_c0` / 漏失 | 45 min | **推导**：局时尺度记忆 |
| `tricklePerWeight` | `0.03 / 2700` | **推导**：1 权重平衡 = 契约 `c0Intact` |
| 剂量 / 封顶 | 0.008 / 0.04 | **推导**：封顶在契约锚之上一点 |
| 初始应力峰值 | `s_onset + 0.25·(s_acid − s_onset) ≈ 0.349` | **推导**：8×8 余脉区（带内半径 4 格） |
| `hotspotDisturbance` | 0.036 | **实测**（`fhn_slow.py`）：核 ~33 min 到酸蚀线且可救 |
| `remnantDisturbance` | 0.034 | **实测**：环带 ~47 min 到酸蚀线、节拍全程不灭 |

### 2.5 复现方式

`tools/lab/`（纯 Python，标准库）：`fhn_probe.py`（自然带）、`fhn_duty.py`（占空比）、`fhn_pacemaker.py` / `fhn_pacemaker_stats.py`（敲钟起搏）、`fhn_slow.py`（慢系统 + c0 漏失 + 救赎窗）。参数溯源见 `README.md`。

---

## 3. 实现落点

| 文件 | 动作 |
|---|---|
| `inner/veins/Heartbeat.scala` | **新增**：`Heartbeat`/`BeatSource`/`BeatStats`/`HeartbeatParam` + 纯分类器 + 退出滞回 |
| `inner/veins/CalmingStock.scala` | **新增**：`MandateSourceKind`/`MandateSource`/`CalmingParam` + 库存纯函数（`step`/`dose`） |
| `inner/veins/{ReadoutVocab,CellReadout,PhaseTracker}.scala` | 原单文件 `MandateReadout.scala`，按类型拆为三份：`CellReadout.heartbeat` + `lastBellStrikeAt`；`isSheltered` 改节律；`PhaseTracker` 周期环 + duty 累计 + `beatStats` + `restoreBeatStats` |
| `inner/veins/MandateAgent.scala` | 删 `recordPulse`/`pulseStamp`；Bell 脉冲记 `lastBellStrikeAt`；分类 + 滞回；快照带 `BeatStats` |
| `inner/veins/MandateSnapshot.scala` | `pulseStamps` → `beatStats`（周期环/lastDuty/lastCross/bornAt） |
| `inner/veins/PulseLedger.scala` | **删除** |
| `mandate/MandateCalming.scala` | **新增**：源注册表、世界扫描、60 s 重平衡广播、剂量 |
| `mandate/BellRitual.scala` | 四裁决；守卫用 `lastBellStrikeAt`；未锚判据 `c0 < C0ShelterLevel` |
| `mandate/MandatePersistence.scala` | `SchemaVersion = 3`；`BeatStats` + 源注册表读写 |
| `mandate/MandateSyncProtocol.scala` | `ClientCellView` 心跳类别打包 flags 位（4–6 位）；debug telemetry 删 `remainingShelter/remainingCooldown`，保留 period/duty |
| `mandate/MandatePhenomena.scala` | 经济/肉体按心跳映射；生态死亡优先 |
| `mandate/MandateOverworld.scala` + `mandate/overworld/` | 三层扰动场、敲钟四态 VFX、拆钟源移除 + 文案、20-tick 循环读节律、60 s 重平衡钩子（后按通道拆为 `overworld/` 模块，门面只留注册与转发） |
| `mandate/MandateWording.scala` | 全量文案换心跳语义 + 颜色语言 |
| `VoyConfig.scala` | `heartbeatParam` + `calmingParam`；`remnantRadius`/`remnantDisturbance`；`initialStressPeak`/`Falloff` 导出式 |
| `client/hud/MandateHud.scala` | 心跳配色主轴、两行 telemetry、自适应面板、图例 |
| `client/hud/CellChangeTracker.scala` | **新增**：「近期变化」历史（心跳/地脉/菌网/庇护跳变 + 尖峰） |
| `AGENTS.md` / `docs/README.md` | 机制描述同步 |
| `contracts/mandate-slice-v1.json`、`SliceContract`、`SliceReplay`、`PhaseResponse` | **不动** |

---

## 4. 分步实施记录

Step 0 探针与统计（`tools/lab/`、`PhaseTracker`）→ Step 1 加轴 + A/B（commit `3302f25`；实机验证暴露读档悬崖与余脉枯竭，引出第二轮讨论）→ Step 2 切默认删窗 + schema 3（`8d4167b`）→ Step 2.5 c0 库存化与源注册表（`13f0603`）→ Step 3 慢系统标定（附录 C）→ Step 4 文档收口（札记 08 标注被取代、目录同步）。各步细节已并入 §1–§3 与 §7。

---

## 5. 测试

- **新增**：`HeartbeatSpec`（分类表 + 真实轨迹积分 + 退出规则共 21 例）、`HeartbeatParamSpec`（8 条关系式断言）、`CalmingStockSpec`（6 例，含「拆两钟之一不归零」回归）。
- **改写**：`MandateReadoutSpec`（庇护/四裁决/守卫/阈值）、`MandateAgentSpec`（守卫戳/晶链不戳/死格不戳/ Warming）、`MandatePhenomenaSpec`（心跳经济/肉体/菌塘）、`MandatePersistenceSpec`（BeatStats + 源注册表 + schema）、`MandateSnapshotSpec`（BeatStats 往返）、`MandateNetworkingSpec`、`VoyConfigSpec`。
- **删除**：`PulseLedgerSpec`。
- **不动**：`SliceContractSpec`、`SliceReplaySpec`、`PhaseResponseSpec`、`PulseCouplingSpec`。

---

## 6. 风险与回退

| # | 风险 | 状态 |
|---|---|---|
| 1 | 开局全山停滞 | 已解：余脉初始场（导出式）+ `Warming` 宽限 |
| 2 | 读档后全场 Warming 悬崖 | 已解：schema 3 快照携带 `BeatStats` |
| 3 | 心跳抖动导致沉滞闪烁 | 已解：失律专用退出滞回（2 × 本格周期）；`Fast`/`Slow` 即时发布（2026-11-16 晚修订） |
| 4 | 玩家不知道窗没了该做什么 | 已解：HUD 拍子可视化 + 近期变化 + 四态钟反馈；开局教学见「后续工作 4」 |
| 5 | 旧档不可迁移 | `SchemaVersion = 3`，按既有逻辑丢弃重生成（0.1 阶段无外部用户） |
| 6 | 删代码不可开关回退 | Step 1 已在真存档验证；Step 2 独立 commit |

---

## 7. 裁决（2026-11-16，14 条 + 晚间 1 条修订）

**基础五条**：① `Paced` 算庇护、`BeatSource` 只记录；② 8 s 改名 `overdriveGuard`（1.6 τ_bell）；③ `Warming` 不设时长常数；④ 旧档直接丢弃（schema 3）；⑤ `PulseBookkeeping` 整个删除。

**三项后续**：⑥ 心跳模块无出处字面量纪律 + `HeartbeatParamSpec` 关系式断言；⑦ 初始应力峰值/衰减改导出式（8×8 余脉区、带内半径 4 格）；⑧ `regime` 轴不动（HUD/文案以心跳为准）。

**余脉与 c0（第二轮）**：⑨ 余脉寿命 = 局时尺度 30–60 min，不追求永久稳态（自然带全在恢复驼峰下降支、自激为正反馈，稳定不动点不存在于冻结方程）；⑩ 「镇死 = 沉滞」为世界观公理；⑪ c0 解耦 L2 库存化（源注册表通用化 `Chunk ↔ List[Source(kind,pos)]`，L3 方程化排入契约窗口）；⑫ 余脉弧线 = U 形三幕（压着 → 曲终回稳 → 冲向酸蚀）；⑬ 热点核必须可救（`u_core` 封顶在锚定可压制水平）；⑭ 空间分布结论：不可救死地只在热点核，山谷永远自愈；AMD 泄压设施单独立项（排在迁移之后，教学主题「镇（泄压）/养（起搏）」双手套）。

**晚间修订（滞回语义）**：⑮ 退出滞回只保护 `Arrhythmic`，单位改为**本格周期**（`2 × periodOf(raw)`）；`Fast`/`Slow` 即时发布。依据：旧口径 `2 × nominalPeriod` 对所有非 `Beating` 生效，使过速延迟 ~79 s（叠加 3 间隔均值后 ~154 s）、停跳延迟 ~178 s（99 s 陈旧 + 79 s 滞回），既是「同一段等待算两遍」，也与「急性」语义相反。

---

## 附录 A：旧概念 → 新概念

| 旧（札记 08 / 反模式） | 新（本札记） |
|---|---|
| 脉冲庇护窗 `PulseStamp` | 心跳 `Heartbeat` |
| 敲钟开窗 | 敲钟起搏 / 校拍 |
| 40 s 窗时长 | 40 s 健康周期参考（文案） |
| `minInterval` 8 s 冷却 | `overdriveGuard` 8 s（过驱动保护） |
| 沉滞 = 窗外 | 沉滞 = 无心跳 / 过缓 / 失律 |
| 经济档按窗 | 经济档按节律 |
| c0 瞬时锚定/坍塌 | c0 剂量注入 + 源涓流 + 45 min 漏失 |
| 「安抚越满越安全」 | 「镇死 = 沉滞」：安抚是镇静剂 |
| `PulseSettlement` / `PulseBookkeeping` | 删除 |

## 附录 B：与札记 08 的冲突（已消解）

札记 08 §2.1 曾写「冷却拒绝时保持原 `PulseStamp` 不变」，代码断言「清空」——两者随子系统删除一并消失。

## 附录 C：三幕弧标定记录（`tools/lab/fhn_slow.py`）

1. `u_core` 0.08 → **0.036**：到酸蚀线 ~33 min；锚定 c0=0.04 可压回（0.036：60 min 后 s 0.60→0.48；0.038 勉强；**0.040 压不动**）→ 救赎窗上界 ≈ 0.038。
2. 三层扰动场：core 0.036（r<2）/ remnant **0.034**（2≤r<4，新增 `remnantRadius=4.0`）/ ambient 0.002（r≥4）。
3. 余脉弧（r=3，s0=0.286）：不跌破 onset（谷底 0.277–0.280，周期 39.5 s 全程持续），~47 min 到酸蚀线；r=2 约 41–53 min；核心 ~33 min 先死——「中心先坏、环带随后」成立。
4. 安抚环 r=4.5：余脉扰动外静默下滑至 0；外围静息（平衡 s≈0.007）。
5. 已知限制：探针无噪声、无外部脉冲；实机中节拍跌破 onset 后不会自愈重启（需玩家敲钟）——「失拍 → 敲钟救回」路径需实机验证；旧存档保留逐格 disturbance（存档 > 配置），三幕弧验证需**新世界**。

## 后续工作

1. **AMD 泄压设施**（札记 06 §4 的 c 通道实施）：石灰石沟/湿地/古堰接线 `SetCalming`，约束「c ≳ 2u 才拉得下、单组只能减速」；热点核救赎升级为工程解法。
2. **L3 契约窗口**：`dc0/dt` 进 `MandateState`，与札记 07 突触解耦搭车重生成契约金样。
3. **regime 轴重审**：未来可改挂 `a′ = a − βI`，消除「s=0、Calm、却在跳」的轴间不一致。
4. **开局教学**：前 3 分钟「旧钟敲三下 → 菌光按节拍亮」的强制示范（风险 4 残留）。
