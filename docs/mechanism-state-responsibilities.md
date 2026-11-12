# 机制职责与状态转移（阶段 A 产出）

> 更新：2026-11-12。状态：**职责表初稿（阶段 A）**，配合第一批结构解耦（阶段 B）与第二批消费者事实分账（阶段 C-1）。
> 用途：把刺激、庇护、耐受、过载、生态损伤／菌网存活的触发、持续、失效、恢复、可观察证据与状态所有权写清，供后续玩法讨论与阶段 C 施工直接引用。
> 本文**不是玩法定稿**。文中参数（40 s / 8 s / 0.10 / 0.35 / 45 s / 120 tick 等）全部是现行实现的默认值，本轮没有重新确认（见[备忘 §3.2](work-in-progress-mechanism-decoupling.md)）；凡未由用户拍板的后果一律标【待拍板】。
> 第二批（2026-11-12，C-1）只把消费者共用的粗判拆成**具名事实**并保持每个消费者的结论不变；新增事实表见 §1.3，消费者依赖表见 §7.1，玩家可见后果仍全部未定。相关：[临时施工备忘 §2.2、§3.1–§3.7、§6、§10](work-in-progress-mechanism-decoupling.md)；[札记 08 心跳规范](design-note-08-fast-variable-and-stagnation.md)；[札记 06 菌网与菌塘](design-note-06-mycelial-grounding.md)。

---

## 0. 标注与阅读约定

三条纪律（引自[备忘 §1](work-in-progress-mechanism-decoupling.md)）：

- 用户认可过某个点子或批准过一次实现，不等于批准它成为全局玩法支柱；本文不把任何候选方案自动定稿。
- 本文只描述**现在代码里成立的机制**与**本轮已显式化的结构**。庇护约 40 s、安全间隔 8 s、耐受步进与衰减等属于现行实现，不是本轮重新确认的设计常量。
- 标注三类：
  - 【用户确认】只有用户明确拍板的方向／结论；
  - 【本轮显式化，结果不变】第一批结构改动把隐含职责写成显式状态或转移，运行结果与改前等价；
  - 【待拍板】玩法、参数或消费者后果尚未决定；本文只列问题与候选，不定案。
- 第一、二批的所有结构改动都标【本轮显式化，结果不变】：第二批只是把已有粗判拆成具名事实（§1.3），每个消费者的结论保持与改前相同，不新增玩法后果。
- 本文引用的【用户确认】仅限两条方向：「事件 → 机制（1..n）→ 效果」与转义层职责（[备忘 §2.1](work-in-progress-mechanism-decoupling.md)）；主动显式化状态与转移（[备忘 §2.2](work-in-progress-mechanism-decoupling.md)）；以及本轮无旧档兼容要求（[备忘 §1](work-in-progress-mechanism-decoupling.md)）。**它们都不构成任何具体玩法参数或后果的批准。**

阅读顺序：§1 三类信息 → §2 五机制职责 → §3 转移表 → §4 同事件多机制顺序 → §5 跨机制耦合 → §6 组合场景 → §7 新旧名对照 → §8 待定项。

---

## 1. 三类信息：保存 / 可推导 / 一次性

这是[备忘 §2.2](work-in-progress-mechanism-decoupling.md) 提出的切分，本批已落到代码；后续施工先判断一条信息属于哪一类，再决定它放哪里。

| 类别 | 判别规则 | 本项目的成员 | 保存 / 推导规则 |
| :--- | :--- | :--- | :--- |
| **必须保存的机制记忆** | 不能由「当前事实 + 时间」闭式重建，且会影响下一次转移 | `PulseLedger` 的四个 double（庇护期限、耐受量、末次脉冲时刻、锁闭期限）；每格 `M`；`simTime`；菌塘 `condition=2`（由方块状态保存） | 随快照／存档保存。缺省读取按零态处理（无窗、无负荷、无锁闭），不把缺失当异常 |
| **由事实可推导的分类** | 只凭①的已保存事实 + 当前事件／时间即可闭式求值 | 脉冲：`shelteredAt`／`overloadedAt`／`remainingShelterAt`／`toleranceAt`／`effectiveWindowAt`；相位：`isOffsetBelowKnee`（读数规则）／`isOverloadLocked`（账本）／`isBurnoutPhase`（二者并集，各事实互不遮蔽）；菌网：`isMyceliumViable`（`M ≥ floor`）／`isMyceliumAbsent`（`M ≤ 0`）；动力学：`isSlowOffsetBelowKnee`（`a_slow` 越膝）；共用守卫：`isEcologicalDeath`；尖峰：`hasActivePositiveSpike`；消费者分类：`isMaintained`／`hasSilentBlight`／`isGastrodiaBedDeepDead`／`isEcologicalBurnout`／`evaluateGastrodiaBed` 的活塘判定／`classifyEconomy`／`Regime`／`PhaseReading` | **不另存第二份可变真值**。读数可因测量条件失真，但不得让同一字段在不同模块代表不同事实；也不得从 `phase` 这个单槽展示反推某个事实（§1.1），也不得从一个合成分类反推它由哪些事实组成（§1.3） |
| **转移产生的一次性效果** | 只在转移发生的那一次执行，之后不参与查询 | 玩家 `voy:pulse_saturation`、失谐钟声／灰烬、庇护翻转 actionbar、白灰 silentBlight 粒子、钟分派文案、再接种种子写入、菌塘被写成 `Withered` 的锁存 | 由执行者负责（游戏线程轮询、方块状态、玩家状态效果）；其中「菌塘锁存」是最容易被误当成可推导标签的一项，见 §2.5 与 C1 |

两个边界说明：

- **窗口关闭**在第一批里被并入状态转移（结算时 `shelter.closed`），不是一次性效果；这样查询不必再额外掩蔽。
- **菌塘枯死**是混合体：判定 `evaluateGastrodiaBed` 可推导，但「锁存为死塘」是一次性效果，写入方块状态后不再随环境自动恢复。这正是[备忘 §3.4](work-in-progress-mechanism-decoupling.md) 指出的争议点，本批未改结论。
- 备忘 §2.2 的施工提醒：若将来「到期」必须触发一次性效果，需要显式记录处理进度或安排到期转移；当前实现尚无任何到期钩子（§4.3）。

### 1.1 事实查询 vs 展示：不得从互斥结果反推

`phase` 只有一个槽位，两个 no-cycle 原因同时成立时只能显示一个（显示优先级：机制规则先于读数阈值规则）。因此：

- `isOffsetBelowKnee` 与 `isOverloadLocked` **各自来自自己的判据**，不看 `phase`：前者由该格自己的 `effectiveA <= burnoutA` 求值，后者由 `pulseLedger.overloadedAt(simTime)` 求值；
- 两者可以**同时为真**，`isBurnoutPhase` 是二者的并集（这正是 `PhaseTracker` 发布两个原因时的条件集，因此消费者行为不变）；
- 禁止「`phase` 显示 A，所以 B 不成立」这一类反推；需要某个事实就调对应查询，需要展示才读 `phase`。

### 1.2 读数阈值 ≠ 已证实的动力学结论

相位门槛用的 `effectiveA` 是**读数侧**的量：它在 `slowA` 之外叠了一个「叠脉冲」修正，而**支配方程并不含这一项**——ODE 积分的是 `a = alpha − depth·(1−M) − coupling·s`，脉冲以 `dv/dt` 里的显式 `impulse + synapseP` 进入。因此：

- `effectiveA <= burnoutA` 只能读作「当前读数规则判定此格无周期相位」，**不能**声称「实际轨迹不存在极限环」；札记 08 实测过仍会尖峰、却被标为 burnout 的格；
- `a_slow <= burnoutA`（`slowA`，ODE 自己的偏移）才是接近动力学的陈述，且它也只说明该偏移下无极限环，不等于菌网已死；
- 写作时把「规则判据」与「物理结论」分开表述；改方程或改 `pulseBurnoutGain` 都会改变前者与后者的关系。

### 1.3 具名事实清单（第二批新增）

第二批把消费者共用的粗判拆成下列事实。它们都是**派生**的：不另存；原始事实各自只按自己的判据求值，合成事实按定义由原始事实并集／合取（表内注明），且合成的值与改前逐组合等价。显示侧（`PhaseTracker.reading`）另算一遍同一判据，不调用 `isOffsetBelowKnee` / `isOverloadLocked` 查询——今天等价，改判据时必须同步。

| 事实 | 判据 | 位置 | 消费者 |
| :--- | :--- | :--- | :--- |
| `isOffsetBelowKnee` | 本格 `effectiveA <= burnoutA`（读数规则；含叠脉冲修正，ODE 不含） | `Readout.isOffsetBelowKnee` / `cell.isOffsetBelowKnee` | 相位展示（规则原因；显示侧同判据另算，不调用此查询）；`isBurnoutPhase`；菌塘／静默（经并集） |
| `isSlowOffsetBelowKnee` | `a_slow <= burnoutA`（ODE 自己积分的偏移；无脉冲修正） | `Readout.isSlowOffsetBelowKnee` | `isEcologicalDeath`；「介质存活／工作区间／相位可读」三分时的动力学侧事实（§8.4） |
| `isOverloadLocked` | `pulseLedger.overloadedAt(simTime)`（机制规则） | `cell.isOverloadLocked` | 相位展示（机制原因优先；显示侧另算，不调用此查询）；`isBurnoutPhase` |
| `isBurnoutPhase` | 上两条的并集 | `cell.isBurnoutPhase` | 沿用旧后果的所有消费者（庇护、经济、菌塘、静默、钟分派） |
| `isMyceliumViable` | `M >= floor`（本格 floor） | `cell.isMyceliumViable` | `isMaintained` |
| `isMyceliumAbsent` | `M <= 0`（吸收态，比 floor 严） | `cell.isMyceliumAbsent` | `hasSilentBlight` |
| `isEcologicalDeath` | `!isMyceliumViable \|\| isSlowOffsetBelowKnee` | `Readout.isEcologicalDeath`（数值重载 + 快照重载） | `MandateAgent.recordPulse` 的「死菌不记账」守卫；`isEcologicalBurnout` 的前两子句 |
| `isEcologicalBurnout` | `isEcologicalDeath \|\| (isOffsetBelowKnee && pulseDrive <= singlePulsePeak)` | `Readout.isEcologicalBurnout` | 钟分派 `DeadMyceliumBurnout` |
| `hasActivePositiveSpike` | `v >= spikeLevel`（只算正向；负向回摆不算） | `cell.hasActivePositiveSpike` | `isMaintained`；`classifyEconomy` 的尖峰档 |
| `isMaintained` | `pulseSuppressed && isMyceliumViable && !isBurnoutPhase && !hasActivePositiveSpike` | `Readout.isMaintained` | 玩家沉滞效果、验山读数、开发 HUD、经济档（C8/C9/C13 未拆） |
| `hasSilentBlight` | `isBurnoutPhase \|\| isMyceliumAbsent` | `MandatePhenomena.hasSilentBlight` | 静默签名 |
| `isGastrodiaBedDeepDead` | `isBurnoutPhase \|\| M < max(floor, 1e-9)` | `MandatePhenomena.isGastrodiaBedDeepDead` | 菌塘 `Withered` 守卫 |

三条边界，防止把「具名化」误当「已分账」：

- **两套 `M` 阈值**：`isMyceliumViable` 用 `M >= floor`，`isMyceliumAbsent` 用 `M <= 0`，`isGastrodiaBedDeepDead` 用 `M < floor`。三者服务不同语义，不得合并成一个「M 死」。
- **两套死亡**：`isEcologicalDeath`（含 `a_slow` 越膝）是「地脉失能」守卫，`isGastrodiaBedDeepDead`（含 `isBurnoutPhase`）是菌塘锁存规则；钟分派用 `isEcologicalBurnout`（再叠读数规则）。它们不互相蕴含。
- **合成事实仍是消费者唯一入口**：`isMaintained` 等四个合成的结论与改前相同，拆读是阶段 C 的决定（§7.1、§8.5），不是本批成果。

---

## 2. 五个机制的职责

### 2.1 物理脉冲输入（刺激）

`FirePulse` 是通道事件，**永远先进物理方程**（钟永远发声）；`bookkeeping` 轴只决定同一到达是否再进玩法账本。来源包括敲钟、水晶链路转发（当前无游戏侧 `AddLink` 发射者，见[札记 07 §6](design-note-07-pulse-coupling.md)），以及 `area <= 0` 的负脉冲（照常进方程，不进账本）。内生尖峰不走 `FirePulse`，见[备忘 §2.1](work-in-progress-mechanism-decoupling.md)。

> **边界（防误读）**：`Counted / PhysicsOnly` 只是把原来的布尔开关显式命名成「进不进账本」，仍是二选一；`onCountedPulse` 也仍在一次结算里同时更新庇护／耐受／过载三项。**第一、二批都不是转义层**：备忘 §2.1 所说的「事件 → 机制（1..n）→ 效果」分派仍未实现，接口与数据结构都未定（见 §8.6）。

| 职责项 | 内容 |
| :--- | :--- |
| 触发条件 | 收到 `FirePulse(cell, area, transmitter, bookkeeping)` 且格在活跃晶格；`area > 0` 才可能进入账本 |
| 持续 | alpha 核按发射器 `tau` 瞬时注入（Bell 5.0 s／CrystalLink 2.0 s），数秒内衰减；没有持续态 |
| 失效 / 恢复 | 核自然衰减；不涉及恢复 |
| 可观察证据 | 钟声／链路音效；开发 HUD 的 `p=`；物理尖峰触发的震动与烟柱；验山读数中 `v / s / q` 的变化。**没有「已记账」的直接回执**——是否记账只能从后续庇护、耐受、过载表现反推 |
| 状态拥有者 | 协议 `EventDTO` → `MandateAgent` 信箱；物理态在 `MandateState.impulse` / synapse 核 |
| 是否必须保存 | **否**。突触核、未投递链路脉冲刻意不存（[MandateSnapshot](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateSnapshot.scala)）；`simTime` 必须保存 |
| 代码落点 | [EventMsg.scala](../src/main/scala/moe/chestnut/awa/voy/inner/event/EventMsg.scala) `FirePulse`／`PulseBookkeeping`；[MandateAgent.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateAgent.scala) `applyEvent`／`recordPulse` |

### 2.2 庇护（ShelterWindow）

| 职责项 | 内容 |
| :--- | :--- |
| 触发条件 | 一个 **Counted** 正脉冲被账本**接受**（`PulseSettlement.Sheltered`）→ `ShelterWindow(t + W_eff)`；`W_eff = 40 · max(0, 1 − T/1.0)`，`T` 取脉冲到达**前**的衰减耐受 |
| 持续 | 到 `shelter.until`；闭式查询。`shelteredAt(t) = !overloadedAt(t) && shelter.holdsAt(t)`——锁闭期间即使旧窗未到期也一律不庇护 |
| 失效 / 恢复 | 到期；或被下一次**被拒绝**的计数脉冲一次性置为 `ShelterWindow.closed`。恢复：新的一次被接受的计数脉冲 |
| 可观察证据 | 开发 HUD 顶栏「脉冲庇护」／「沉滞(STAGNANT)」；庇护翻转时的 actionbar（「一记脉动荡开…」／「太久没有脉动…」）；沉滞效果被主动移除（窗外每 20 tick 重施，效果 55 tick）；验山读数「脉动庇护(免除沉滞)」；经济档；村民身上的沉滞标记 |
| 状态拥有者 | `PulseLedger.shelter`（每格，actor 内）；`CellReadout.pulseLedger` 原样发布；`pulseSuppressed` 是派生便利值，恒等于 `pulseLedger.shelteredAt(simTime)` |
| 是否必须保存 | **是**（NBT 键 `suppressedUntil` = `shelter.until`）；否则重启会丢失或重开庇护窗口 |
| 代码落点 | [PulseLedger.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/PulseLedger.scala) `ShelterWindow`；[MandateReadout.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateReadout.scala) `isMaintained`；[MandateOverworld.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/MandateOverworld.scala) `applyChunkMandateEffects` |

### 2.3 耐受（ToleranceBook）

| 职责项 | 内容 |
| :--- | :--- |
| 触发条件 | 每次被账本处理的计数脉冲都 `chargedAt`：接受 +`normalToleranceStep`（0.10），拒绝 +`overpulseToleranceStep`（0.35），都先按 `t` 衰减旧值再累加并 cap 1.5 |
| 持续 | 从 `lastPulseTime` 起指数衰减，`toleranceDecayTau = 45 s`；闭式求值，不需要逐 tick 写 |
| 失效 / 恢复 | 没有「失效」；停手后衰减到 0。`T ≥ invertTolerance`（1.0）时任何计数脉冲都必被拒，`W_eff` 归零；但 recorded cause 只有在间隔已过 `minSafeInterval` 时才记为 `ToleranceSaturated`，8 s 内会先被 `PulseTooSoon` 截获（守卫顺序见 §3.4） |
| 可观察证据 | 开发 HUD 的 `tol=`；钟分派文案「沉滞退去约 N 息」的窗口随耐受缩短；连续维护时「窗口越来越短」的手感。没有独立图标或条 |
| 状态拥有者 | `PulseLedger.tolerance`（`amount` + `lastPulseTime`） |
| 是否必须保存 | **是**（NBT 键 `pulseTolerance` / `lastPulseTime`） |
| 代码落点 | [PulseLedger.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/PulseLedger.scala) `ToleranceBook`；[MandateWording.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/MandateWording.scala) 窗口文案 |

### 2.4 过载（OverloadLock）

| 职责项 | 内容 |
| :--- | :--- |
| 触发条件 | `onCountedPulse` 的守卫按 **①脉冲太快 → ②已在锁闭 → ③耐受饱和** 顺序择一；命中即 `PulseSettlement.Overloaded(cause, 8 s, toleranceAfter)` |
| 持续 | `OverloadLock(t + 8 s)`；锁内 `overloadedAt = true`、`shelteredAt = false`，相位发布 `UndefinedReason.OverloadLocked`（机制原因优先于读数规则原因） |
| 失效 / 恢复 | 闭式到期。**到期没有一次性效果**（不通知、不复位其他状态）。锁内再敲**不是无效**：守卫命中后会重新上锁到 `t + 8 s`，即期限被刷新；只要到达间隔 < `minSafeInterval` 8 s 就能一直续期（**没有总上限**），停止补脉冲后最多 8 s 解除。默认参数下这次命中的 cause 总记为 `PulseTooSoon`（见 §3.4 注）。注意链路脉冲恒 `Counted` 且由源格尖峰持续转发，所以**有持续链路流量的格可以长期保持锁闭**；这是当前机制的直接后果，不是 bug（是否允许见 §8.3） |
| 可观察证据 | 钟分派「脉冲过载」文案；玩家获得 `voy:pulse_saturation`（120 tick，挖掘 ×0.75、移速 ×0.85）；低闷失谐钟声 + 灰烬；开发 HUD 相位行显示 `Undef(OverloadLocked)`（与读数规则原因的 `Undef(OffsetBelowKnee)` 在代码与开发 HUD 里已可区分；验山读数／告示走 `formatPhaseDial`，玩家面向仍是同词「静默·深度失能」，内容层未拆，见 C2）；白灰 `silentBlight` 粒子；`tol` 跳升 |
| 状态拥有者 | `PulseLedger.overload`；`OverloadCause` 是这次结算的一次性解释，**不保存** |
| 是否必须保存 | **是**（NBT 键 `overloadUntil`）。cause 不保存 |
| 代码落点 | [PulseLedger.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/PulseLedger.scala) `OverloadLock`／`OverloadCause`；[BellRitual.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/BellRitual.scala)；[MandateEffects.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/MandateEffects.scala)；[MandateOverworld.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/MandateOverworld.scala) 过载反噬 |

### 2.5 生态损伤 / 菌网存活

| 职责项 | 内容 |
| :--- | :--- |
| 触发条件 | 慢系统动力学（`M` 的 Fisher-KPP：logistic 生长、酸蚀、沿输运边的扩散）+ 叙事 `SetSustenance`；**不是**由单次脉冲直接扣 `M`。当前**没有额外的「账本负荷 → 损伤」路径**（即没有把脉冲计数直接兑换成 `M` 损失的地方）；但物理刺激仍会**间接**影响菌网：脉冲进 `dv/dt` → `v` 尖峰抬 `q`（`λ·(|v|−v_spike)_+`）→ `q` 经 `η` 抬 `s` → `s` 超过 0.6 的酸蚀扣 `M`（`p → v → q → s → M`）。游戏侧该链是开的（`sustenanceBurn = 4e-3`、阈值 0.6），冻结契约切片里 `sustenanceBurn = 0` 故 dormant。后续若新增显式负荷伤害，须先核对该链是否已覆盖同一后果，避免重复累计（备忘 §3.7） |
| 持续 | `M` 持久；`M = 0` 是吸收态（无自生） |
| 失效 / 恢复 | 再接种抬高地板（野生菌种 0.05／荧丝菌材 0.12，写入取 `max(当前 M, 种子)`，已有活丝不被冲掉）；邻格扩散回填小死斑；大死区实质永久。`a_slow <= burnoutA` 是轨迹失能，不等于 `M` 已死；泄压后若 `M` 仍活，相位可回升。菌塘枯死锁存需人工翻修（非 burnout 且 `s < 0.6`） |
| 可观察证据 | 菌塘 `condition`（0 长／1 滞／2 枯）、验山读数「菌网:完好/受损/枯绝」、夜间菌光与前兆孢子、静默枯败的灰、黄水三档、开发 HUD 的 `M=`、经济档 |
| 状态拥有者 | `MandateState.sustenance`（核心，每格）；菌塘锁存在 `GastrodiaBedBlock.CONDITION`（消费侧方块状态） |
| 是否必须保存 | **是**（`M` 必须随快照保存，`M=0` 必须仍是 0）；菌塘 `condition` 随区块保存；读数分类不保存 |
| 代码落点 | [MandateState.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateState.scala) `sustenance`；[MandateReadout.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateReadout.scala) `isMyceliumViable`／`isMyceliumAbsent`／`isSlowOffsetBelowKnee`／`isEcologicalDeath`；[MandateAgent.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateAgent.scala) `recordPulse` 的 `ecoDead` 守卫；[MandatePhenomena.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/MandatePhenomena.scala) `hasSilentBlight`／`isGastrodiaBedDeepDead`；[GastrodiaBedBlock.scala](../src/main/scala/moe/chestnut/awa/voy/blocks/GastrodiaBedBlock.scala) |

---

## 3. 转移表

列含义：**当前条件** = 转移前已保存或已推导的事实组合；**事件／时间推进** = 触发；**守卫** = 还需额外成立的条件；**状态更新** = 写入保存事实；**一次性效果** = 只在这次发生、之后不参与查询的执行；**恢复条件** = 回到可再次转移所需的路径。所有时间都以 `simTime` 为准；未注「保存」的状态更新只活在本次裁决内。

### 3.1 物理脉冲输入

| 当前条件 | 事件 / 时间推进 | 守卫 | 状态更新 | 一次性效果 | 恢复条件 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 任意活跃格 | `FirePulse(area > 0, Counted)` | 无（物理总是施加）；记账另受 §3.2–§3.4 守卫 | 先物理注入；再把到达交给 `onCountedPulse`（同一次裁决） | 无（声音／尖峰由物理或下游产生） | — |
| 任意活跃格 | `FirePulse(area > 0, PhysicsOnly)` | 无 | 只物理注入；账本不动 | 无 | — |
| 任意活跃格 | `FirePulse(area ≤ 0)` | 无 | 只物理注入；即使 `Counted` 也不记账（`recordPulse` 的 `area > 0` 守卫） | 无 | — |
| 格不在活跃晶格 | 任意通道／拓扑事件 | 晶格存在 | 丢弃（确定性，不随加载顺序变化） | 无 | `RegisterCell` |
| 任意格 | `TickArrived(dt)` | — | 积分四维状态与 `M`、衰减 synapse 核，推进 `simTime` | 动力学越过尖峰门限时发 `CellSpikeTriggered`（一次） | — |
| 任意格 | `RegisterCell` / `RemoveCell` | — | 注册／移除格；账本随格清空或丢弃 | 移除时旧账本报废 | 重新注册（账本零态） |

### 3.2 庇护

| 当前条件 | 事件 / 时间推进 | 守卫 | 状态更新 | 一次性效果 | 恢复条件 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 无窗／窗已过期 + Counted 脉冲 | 事件 | 账本守卫全过（非太快、未锁、未饱和） | `shelter.until = t + W_eff`；`tolerance += 0.10`（cap 1.5，`lastPulseTime = t`）；`overload.closed` | 无（沉滞移除是消费侧 20 tick 轮询） | 窗到期 |
| 有窗 + 时间推进 | tick | — | 无（闭式到期） | 无（无到期钩子） | 新 Counted 脉冲 |
| 有窗 + Counted 被拒 | 事件 | 任一 `OverloadCause` | `shelter.closed`（一次性关闭） | 过载惩罚（钟分派路径；链路脉冲无玩家惩罚） | 锁到期 + 新脉冲被接受 |
| 有窗 + PhysicsOnly 脉冲 | 事件 | — | 不刷新、不续期 | 无 | 旧窗自然到期 |
| 锁闭中 | 查询 | `overloadedAt(t)` | — | — | 锁到期 |
| 生态死亡（`M < floor` 或 `a_slow ≤ burnoutA`） | 事件 | `recordPulse` 的 `ecoDead` 守卫 | 账本完全不动（既不重置也不新设） | 钟分派「叩钟无应」（PhysicsOnly） | 再接种 + 泄压 |

### 3.3 耐受

| 当前条件 | 事件 / 时间推进 | 守卫 | 状态更新 | 一次性效果 | 恢复条件 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Counted + 被接受 | 事件 | 非太快、未锁、未饱和 | `amount = min(1.5, decay_at(t) + 0.10)`，`lastPulseTime = t` | 无 | 衰减 |
| Counted + 被拒绝 | 事件 | 任一 `OverloadCause` | `amount = min(1.5, decay_at(t) + 0.35)`，`lastPulseTime = t` | 无 | 衰减 |
| 任意 | tick | — | 无写入（闭式衰减） | 无 | 停手，`τ = 45 s` |
| `decay_at(t) ≥ 1.0` | 查询 | `saturatedAt(t)` | — | 下一枚计数脉冲必被拒（`ToleranceSaturated`），`W_eff = 0` | 衰减到 `< 1.0` |
| 生态死亡 | 事件 | `recordPulse` 的 `ecoDead` | 不记账 | 无 | 再接种 |
| `RegisterCell` | 拓扑 | — | 账本零态（含耐受清零） | — | — |

### 3.4 过载

| 当前条件 | 事件 / 时间推进 | 守卫 | 状态更新 | 一次性效果 | 恢复条件 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Counted + `elapsed < 8 s` | 事件 | 守卫①`PulseTooSoon` | `overload.until = t + 8 s`；`shelter.closed`；`tolerance += 0.35` | 钟分派路径：`voy:pulse_saturation` + 失谐钟声 + 灰烬；链路路径：无玩家惩罚 | 8 s 到期 |
| Counted + `elapsed ≥ 8 s` 且锁仍在（仅锁时长 > 安全间隔的配置） | 事件 | 守卫②`AlreadyLocked` | 同上（重新上锁） | 同上 | 到期 |
| Counted + 耐受 ≥ 1.0（前两守卫不成立） | 事件 | 守卫③`ToleranceSaturated` | 同上 | 同上 | 耐受衰减到 `< 1.0` 后到期 |
| 任意 | tick | — | 无（闭式到期） | 无（无到期钩子） | — |
| 锁到期 | 查询 | `overloadedAt = false` | 相位不再发布 `OverloadLocked` | — | 新 Counted 脉冲（庇护仍需重新打开） |

> **注（默认参数下的可达性）**：锁闭时长 8 s 恰等于最小安全间隔 8 s，而守卫顺序是「太快 → 锁闭 → 饱和」。因此默认参数下 `AlreadyLocked` **不可达**：`elapsed < 8` 已被 `PulseTooSoon` 截获，`elapsed ≥ 8` 时锁已过期。只有把 `overloadBurnoutDuration` 配得比 `minSafeInterval` 长，才会出现该分支。一次结算只记录一个 `cause`，它是解释而非状态（不保存）。

### 3.5 生态损伤 / 菌网存活

| 当前条件 | 事件 / 时间推进 | 守卫 | 状态更新 | 一次性效果 | 恢复条件 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 持续酸蚀（`s > 0.6` 且 `M > 0`） | tick 积分 | Fisher-KPP | `M` 下降；穿 `floor` 且局部趋势 ≤ 0 → 0（吸收） | 消费侧检查时刻可能把菌塘写成 `Withered` | 泄压 + 再接种 |
| `M > 0` + 低应力 | tick | — | logistic 回升 + 扩散 | 无 | — |
| `effectiveA ≤ burnoutA`（读数阈值规则；含叠脉冲修正，**ODE 不含此项**） | 查询／显示 | `overloaded` 为假时占据相位槽 | 不写状态；相位发布 `OffsetBelowKnee` | 静默枯败、钟「无应」、菌塘可能锁存（经 `hasSilentBlight`／`isGastrodiaBedDeepDead` 的并集判据） | `effectiveA` 回升（降应力／抬 `M`／撤掉叠脉冲） |
| `a_slow ≤ burnoutA`（ODE 自己的偏移，接近动力学结论） | tick／查询 | 不涉及相位槽 | 不写状态；`isSlowOffsetBelowKnee` 为真 | 由 `isEcologicalDeath`／`isEcologicalBurnout` 合并判定（消费者结果不变） | 应力缓解或 `M` 回升使 `a_slow` 回升 |
| `M < floor` | tick／查询 | — | 不写状态；`isMyceliumViable = false`，分类「枯绝」 | 同上；菌塘 `isGastrodiaBedDeepDead`；静默仍用 `isMyceliumAbsent`（`M ≤ 0`） | 再接种（大死区实质永久） |
| 菌塘随机刻／交互被判 `Withered` | 下游检查 | `evaluateGastrodiaBed`（内部 `isGastrodiaBedDeepDead`） | `CONDITION = 2` 锁存 | 白灰、枯死外观 | 人工翻修（`canRefurbishGastrodiaBed`：非 burnout 且 `s < 0.6`） |
| 再接种 | `SetSustenance`／方块交互 | 接线层 `evaluateReInoculation`（`M < 0.2` 且 `s < 0.6`） | `M = max(M, seed)`（0.05／0.12） | 种子写入、文案 | 扩散 / logistic |

---

## 4. 同一事件命中多机制：顺序与冲突

### 4.1 一次 Counted 脉冲的原子结算

- **信箱全序**：同一个 `FirePulse` 在 `MandateAgent.handle` 内**先 `applyEvent`（物理）→ 后 `recordPulse`（账本）**，中间不插入其他事件；因此物理与账本属于同一次明确裁决（符合[备忘 §6 阶段 B](work-in-progress-mechanism-decoupling.md) 的「避免半完成状态」）。
- **账本内**：`onCountedPulse` 先调用 `overloadCauseAt`（太快 → 锁闭 → 饱和），再二选一，一次性更新三个子事实；不会出现「窗口开了但耐受没加」或「锁上了但窗口还开着」。
- **死菌双守卫**：`recordPulse` 内部用 `Readout.isEcologicalDeath`（`!isMyceliumViable || isSlowOffsetBelowKnee`）跳过记账；钟分派用 `isEcologicalBurnout`。**两者同用本格参数时**（生产调用方都是如此；函数签名仍可传入任意参数），后者多出的第三项 `isOffsetBelowKnee && pulseDrive ≤ 单钟峰值` 在逻辑上恒被前两子句蕴含（`pulseDrive ≤ 峰值 ⇒ a_eff == a_slow ⇒ isOffsetBelowKnee ⟺ isSlowOffsetBelowKnee`；`pulseDrive > 峰值` 时该项为假），所以 `isEcologicalBurnout ≡ isEcologicalDeath`；保留第三项只是把「读数规则也参与」写成显式意图。死格不会因敲钟叠耐受。

### 4.2 钟分派与账本的双重裁决（本批未改）

钟分派在游戏线程读**上一 tick 的 `CellReadout` 快照**判 outcome，再投递 `FirePulse`；真正落到账本的是 actor 内的 `onCountedPulse`。两个守卫集不完全相同：

- 分派过载分支 = `pulseLedger.wouldOverloadAt(...) || isBurnoutPhase`；账本只认自己的实时闭式状态。
- 因此存在**短暂不一致**（本批保持既有行为，不据此判定 bug）：
  - 分派判 `PulseOverloadBurnout`（因相位 `OffsetBelowKnee`，而账本守卫未命中）→ 不锚 `c0`、玩家吃过载惩罚、`bookkeeping = Counted` 仍送进账本，账本可能接受并开窗；
  - 分派判 `AnchoredOrMaintained` 而账本拒绝（快照落后／边界）→ 文案宣称已锚、已庇护，账本实际锁闭。
- 是否改为「账本结算为唯一真相、分派只做提示／文案」，或让分派与账本共用同一函数【待拍板】（备忘 §2.2 的目标）。
- 钟一次动作还同时涉及 `c0` 重锚（`anchorsC0`）、物理脉冲、账本、玩家惩罚；本批只拆账本内部，**没有拆钟**（[备忘 §3.1](work-in-progress-mechanism-decoupling.md)，属阶段 C）【待拍板】。

### 4.3 时间到期语义

- 庇护、耐受、锁闭都使用闭式查询，不需要每 tick 写状态；**到期当前没有任何一次性效果**，因此也不需要记录处理进度。
- 沉滞的施加／移除由游戏层每 20 tick 轮询 `isMaintained` 完成；它是消费侧效果，不是账本到期转移。
- 备忘 §2.2 的要求：**若将来「到期」必须触发一次性效果**，需要显式记录处理进度或安排到期转移。当前实现不满足这一点，列为阶段 C 的施工注意项。

---

## 5. 跨机制关系清单（本批后仍存在的耦合）

| 编号 | 关系（当前事实） | 代码依据 | 标注 | 待讨论 |
| :--- | :--- | :--- | :--- | :--- |
| C1 | 过载期间菌塘判 `Withered` | `evaluateGastrodiaBed` 的 `isGastrodiaBedDeepDead = isBurnoutPhase \|\| M < max(floor, 1e-9)`；随机刻／交互时写入锁存 | 【本轮显式化，结果不变】 | 短时过载应「暂停生长」还是允许「枯死锁存」（[备忘 §3.7](work-in-progress-mechanism-decoupling.md)）【待拍板】 |
| C2 | 静默枯败同时覆盖两种 no-cycle 原因 | `hasSilentBlight = isBurnoutPhase \|\| isMyceliumAbsent`；玩家文案两原因同词 | 【本轮显式化，结果不变】 | 短时锁闭是否发静默签名；文案是否拆出「过载·暂锁」【待拍板】 |
| C3 | 耐受缩短庇护窗口 | `effectiveWindowAt = 40 · (1 − T/1.0)` | 【本轮显式化，结果不变】 | 数值与「维护必加耐受」【待拍板】 |
| C4 | 过载关闭庇护窗口 | 结算内 `shelter.closed`；查询再以 `overloadedAt` 掩蔽 | 【本轮显式化，结果不变】 | 是否保留「一次过载即清窗」【待拍板】 |
| C5 | 死菌不记账 | `recordPulse` 的 `ecoDead`（`M < floor` 或 `a_slow ≤ burnoutA`） | 【本轮显式化，结果不变】 | 死亡边界是否应只看 `M` 而非 `a_slow`【待拍板】 |
| C6 | 机制锁闭进入相位读数 | `PhaseTracker.reading` 先看 `overloaded()` → `OverloadLocked`；读数阈值原因其次（**仅展示优先级**）。两个事实查询各自独立，同时成立时都返回真（§1.1） | 【本轮显式化，结果不变】 | 读数是否应反映「规则锁闭」（[备忘 §3.3](work-in-progress-mechanism-decoupling.md)）；两原因同时成立时 `PhaseReading` 只能显示一个，是否需要并列展示【待拍板】 |
| C7 | 慢偏移越膝仍被下游当「烧毁／枯绝」 | `isEcologicalBurnout` 的 `isSlowOffsetBelowKnee` 与钟 `DeadMyceliumBurnout`；`isBurnoutPhase` 供菌塘／静默 | 【本轮显式化，结果不变】 | 备忘 §3.7 要求的「介质存活 / 工作区间 / 相位可读」三分【待拍板】 |
| C8 | `isMaintained` 仍是庇护／经济／玩家效果的统一入口 | `pulseSuppressed && isMyceliumViable && !isBurnoutPhase && !hasActivePositiveSpike`（四个具名事实的合成） | 【本轮显式化，结果不变】（消费者仍未拆） | 消费者分账（[备忘 §3.5](work-in-progress-mechanism-decoupling.md)）；已具名化，拍板后改读对应事实即可 |
| C9 | 经济档统一由 `isMaintained` 决定 | `classifyEconomy`：维持态读 `isMaintained`，尖峰档读 `Regime.Burnout` 或 `hasActivePositiveSpike` | 【本轮显式化，结果不变】 | 是否按长期土地健康／临时危险／设施服务分账 |
| C10 | 钟一次动作多职责 | `BellStrikeOutcome`：`c0` + 脉冲 + 账本 + 惩罚 | 【待拍板】（本批未拆） | 备忘 §3.1、阶段 C |
| C11 | 正常维护必加耐受 | 每次接受都 `+0.10` | 【待拍板】（本批未拆） | 「维护必加耐受」是否需要 |
| C12 | 链路脉冲恒 `Counted`、无分派 | 链路无分派 → 恒 `Counted`（来源字段 `transmitter` 保留） | 【待拍板】（本批未拆） | 内生／链路／外部是否共享同一套耐受／过载／庇护（[备忘 §2.1](work-in-progress-mechanism-decoupling.md)） |
| C13 | 沉滞玩家效果由轮询驱动 | 每 20 tick 轮询 `isMaintained`；效果 55 tick | 【待拍板】（本批未拆） | 是否改为状态转移钩子（备忘 §2.2 到期语义） |
| C14 | 菌塘枯死不可自动恢复 | `CONDITION = 2` 锁存；翻修要求非 burnout 且 `s < 0.6` | 【待拍板】（本批未拆） | 短时事件是否允许留下这种锁存 |

> 另：`Regime.Burnout`（`s ≥ 0.95` 的应力读数）与 `isBurnoutPhase`（无周期相位）是两种读数，札记 06 §3 的不变量要求静默签名只绑后者——这条不涉及取舍，不在上表编号内。

---

## 6. 组合场景检查表

五个场景来自[备忘 §3.7](work-in-progress-mechanism-decoupling.md)。「方向」列是备忘里的**候选方向，不是定稿**；「当前实现真实结论」按 2026-11-12 源码与默认参数读出，供后续对照。

| 场景 | 备忘 §3.7 的方向（候选） | 当前实现真实结论 | 判定 | 待拍板 |
| :--- | :--- | :--- | :--- | :--- |
| **A. 活菌 + 短时过载** | 临时过载可暂缓生长，解除后按生态条件重判；不自动等于永久死亡 | 活菌 `M ≥ floor`；8 s 锁闭内 `phase = OverloadLocked`、`isBurnoutPhase = true` → `silentBlight = true`；菌塘**若**在锁闭期被随机刻／交互评估，会被写成 `Withered` 并锁存（之后需翻修）；`isMaintained = false` → 经济至少沉滞档（若 `v` 尖峰或 `Regime.Burnout` 则为尖峰档）；玩家若经钟触发则吃 `pulse_saturation`；`M` 与轨迹不受损；8 s 后相位恢复，但庇护窗已在结算时关闭，须新脉冲；耐受已 `+0.35` | 功能限制可恢复；生态后果取决于检查时机，可能被放大为永久锁存，**与候选方向不一致** | 是否改为「过载期间暂停生长」；是否允许锁存 |
| **B. 低菌量 + 无过载** | 低菌量只影响生长／生态，不应误触发过载与死亡 | 前提：`M ∈ [floor, 0.60)` **且 `a_slow > burnoutA`（未越膝）**——此时菌塘 `Stalled`；`silentBlight = false`；账本无锁闭；若相位有定义且窗新鲜，`isMaintained` 可为 true → 庇护与经济照常（`isMaintained` 只要求 `M ≥ floor`，不要求 `M ≥ 0.60`）。注意菌网受损本身不决定相位：默认 `coupling = 1.0 / depth = 1.2` 下，`M = 0.59` 只要 `s ≥ 0.54` 就已 `a_slow ≤ burnoutA`，转入场景 C 的混合（`Withered`／静默／非维持）；`M` 越膝主要靠 **s 高**，不是靠 M 低。`M < floor` 才是「枯绝」 | 基本符合（不误触发过载、不自动判死）；「菌网受损仍算 maintained」是待定耦合 | 庇护／经济是否应随 `M` 分级（C8／C9） |
| **C. 慢系统失能但菌网未死** | `a_slow` 越膝 ≠ 菌网死亡；介质存活、动力学工作区间、相位可读性应分开 | **(i)** `a_slow ≤ burnoutA` 且 `M ≥ floor`：`phase = OffsetBelowKnee`；`isEcologicalBurnout = true` → 钟判 `DeadMyceliumBurnout`（PhysicsOnly）；菌塘 `Withered`；`silentBlight`；经济非维持；但 `M` 未死，泄压后 `a_slow` 可回升、相位可恢复，菌塘需翻修。**(ii)** `Regime.Burnout`（`s ≥ 0.95`）且 `M` 活、`a_slow` 未越膝：相位可为 `Cycle`；`silentBlight` 必须 false；菌塘 `Stalled`（`s ≥ 0.45`）；钟 `SaturatedNoAnchor`（不锚 `c0` 但 `Counted`、开窗）；窗新鲜且无尖峰时 `isMaintained` 可为 true → 经济可维持 | (ii) 已区分；(i) 仍把轨迹失能当生态烧毁，**未满足** | 是否为 (i) 增加独立的「介质存活／损伤」事实；钟在 (i) 下是否仍应毫无回音；菌塘是否仍判死 |
| **D. 菌网死亡 + 过载并存** | 两状态可并存、互不取消；过载解除不能复活死菌网 | `M < floor` 后 `recordPulse` 跳过记账，不再新设锁；死亡前已设的锁可自然到期。两者同时成立时 `phase` 发布 `OverloadLocked`（机制原因优先），不是 `OffsetBelowKnee`；`isBurnoutPhase` 同判，下游结论不变（菌塘 `Withered`、`silentBlight`、钟优先 `DeadMyceliumBurnout`）。锁到期不改变 `M`；`M = 0` 吸收 | 满足「不互相取消」；读数无法并列展示两个原因 | 是否需并列／分别查询（`CellReadout` 已有两个布尔，但 `phase` 单槽）；是否需要额外的账本负荷损伤（注意 `p → v → q → s → M` 已覆盖一条间接路径） |
| **E. 仅缺周期参照** | 无周期参照是观测不足，不应自动致枯死 | `phase = NoCycleReference`；不属于 `isBurnoutPhase`；`silentBlight = false`；菌塘按 `s` / `M` 判 `Growing` / `Stalled`；钟走默认 `AnchoredOrMaintained`（锚 `c0` + `Counted`）；窗新鲜时 `isMaintained` 可为 true | 符合 | 无（内容层如何解释「看不见相位」可另定） |

> 读法提醒：以上「真实结论」是**当前消费者选择**的结果，不是物理必然。C1–C14 中任何一条被用户拍板改掉后，本表对应行应同步重写。
>
> 表中的 `phase = OffsetBelowKnee` 指**展示结果**；它背后的事实是读数阈值判据 `effectiveA ≤ burnoutA`（含叠脉冲修正），与 ODE 自己积分的 `a_slow` 不是同一个量（§1.2）。`M` 仍活、而 `a_slow` 或 `a_eff` 越膝时，两者可以不一致：消费者按 `isBurnoutPhase` 走，原因查询按各自判据走（§1.1）。

---

## 7. 与第一、二批代码改动的对应关系

第一批只改结构与命名，第二批把消费者共用的粗判拆成具名事实；两批都不改数值与下游结论（行为等价清单见[备忘 §9、§10](work-in-progress-mechanism-decoupling.md)）。下表用于按新名检索旧资料。

| 现在（2026-11-12） | 原来 | 变化性质 | 行为 |
| :--- | :--- | :--- | :--- |
| `PulseLedger` | `PulseToleranceState` | 状态聚合改名，并拆为三个独立事实 | 不变 |
| `ShelterWindow` | `PulseToleranceState.suppressedUntil` | 字段提升为独立事实 | 不变 |
| `ToleranceBook` | `PulseToleranceState.tolerance` + `lastPulseTime` + `toleranceAt` | 同上 | 不变 |
| `OverloadLock` | `PulseToleranceState.overloadUntil` + `isOverloadedAt` | 同上 | 不变 |
| `PulseLedger.onCountedPulse` | `PulseToleranceState.onPulse` | 改名；只有 `Counted` 到达才调用 | 不变 |
| `PulseSettlement.Sheltered` | `PulseStrikeVerdict.Suppressed` | 改名 | 不变 |
| `PulseSettlement.Overloaded(cause, lockoutDuration, toleranceAfter)` | `PulseStrikeVerdict.Overloaded(nextTolerance, burnoutDuration)` | 增加 `cause`，字段语义显式化 | 不变 |
| `OverloadCause.PulseTooSoon / AlreadyLocked / ToleranceSaturated` | `wouldOverloadAt` 的三个 OR 条件 | 把守卫理由显式化 | 不变（`cause` 不保存） |
| `PulseBookkeeping.Counted / PhysicsOnly` | `FirePulse.heartbeat = true / false` | 轴改名 | 不变 |
| `FirePulse.bookkeeping` | `FirePulse.heartbeat` | 同上 | 不变 |
| `UndefinedReason.OffsetBelowKnee` | `UndefinedReason.BurnoutNoCycle`（读数规则部分） | 原因拆分 | 不变 |
| `UndefinedReason.OverloadLocked` | `UndefinedReason.BurnoutNoCycle`（锁闭部分） | 原因拆分 | 不变 |
| `CellReadout.isBurnoutPhase` / `isOffsetBelowKnee` / `isOverloadLocked` | 各消费点 `phase == BurnoutNoCycle` | 新增派生查询；两个事实各按自己的判据求值（不看 `phase`），`isBurnoutPhase` 为并集；消费点统一改读 `isBurnoutPhase` | 不变 |
| `CellReadout.pulseLedger` | `CellReadout.pulseState` | 原样发布账本 | 不变 |
| `pulseLedger.shelteredAt` / `remainingShelterAt` / `toleranceAt` / `effectiveWindowAt` | `pulseState.isSuppressedAt` / `remainingSuppressionAt` / `toleranceAt` / `effectiveWindowAt` | 改名 | 不变 |
| 快照／NBT 四键：`suppressedUntil` / `overloadUntil` / `lastPulseTime` / `pulseTolerance` | 同名四键 | NBT key 不变，归属改到三个子事实 | 不变（缺键即零态） |
| `PhaseTracker.reading` 分支顺序 | 旧 `aEff <= knee \|\| overloaded` | 先机制（`OverloadLocked`）后读数规则（`OffsetBelowKnee`） | 不变（两原因同时成立仍只发布一个） |
| `MandateWording.formatPhaseShort`（**仅开发 HUD** `/voy hud` 可见） | `Undef(BurnoutNoCycle)` | 文本随原因名变为 `Undef(OffsetBelowKnee)` / `Undef(OverloadLocked)` | 开发叠层文本变化；玩家面向文案不变 |
| `MandateWording.formatPhaseDial`（验山读数／告示，玩家面向） | 两原因同词 | 两原因仍同词「静默·深度失能」 | 不变（是否拆词属内容决定，见 C2） |
| 叩钟失锚文案「本次不计心跳」 | 原文 | 未改（内容层仍用旧词，代码标识不再用「心跳」） | 不变（待内容层统一，见 §8.8） |
| `Readout.isEcologicalBurnout` 三子句 | `M < floor \|\| slow <= burnoutA \|\| (isOffsetBelowKnee && drive <= peak)` | 前两子句收进共享 `isEcologicalDeath`，第三子句改为引用读数事实 | 不变 |
| `Readout.isMaintained` 四子句 | `pulseSuppressed && M >= floor && !isBurnoutPhase && v < spikeLevel` | 改用 `isMyceliumViable` / `!hasActivePositiveSpike`，语义具名 | 不变 |
| `MandateAgent.recordPulse` 的 `ecoDead` | 就地算 `M < floor \|\| slowA <= knee` | 改用共享 `Readout.isEcologicalDeath` | 不变 |
| `classifyPhenomena` 的 `silentBlight` | 就地算 `isBurnoutPhase \|\| M <= 0` | 具名 `hasSilentBlight` | 不变 |
| `evaluateGastrodiaBed` 的 `deepDead` | 就地算 `isBurnoutPhase \|\| M < max(floor, 1e-9)` | 具名 `isGastrodiaBedDeepDead` | 不变 |
| `classifyEconomy` 尖峰判据 | `dissonance >= spikeLevel` | `hasActivePositiveSpike` | 不变 |
| 新增查询 | 无 | `isMyceliumViable` / `isMyceliumAbsent` / `isSlowOffsetBelowKnee` / `isEcologicalDeath` / `hasActivePositiveSpike` / `hasSilentBlight` / `isGastrodiaBedDeepDead` | 纯新增，不改变任何现有值 |

**行为等价核对**：庇护窗口仍为 40 s 基准、耐受仍缩短窗口、过载仍关闭窗口并锁 8 s、菌塘在两种 no-cycle 原因下仍判 `Withered`、`silentBlight` 仍覆盖两者。第二批的等价性证据分三层：① `isMaintained` 有 96 组组合与改前字面表达式逐一比对；② `isEcologicalDeath` 与 `isEcologicalBurnout`（同用本格参数时）在「单钟峰值以下」40 组组合一致（此范围内两守卫恒等价，见 §4.1）；③ 其余合成项（`hasSilentBlight` / `isGastrodiaBedDeepDead` / `classifyEconomy`）用定点边界断言（两套 `M` 阈值、负向回摆）覆盖，不是全组合枚举。除 NaN 外（损坏存档的 double、或损坏配置导致的非有限值，属定义域外；取反写法在 NaN 下与改前不同，`recordPulse` 的记账守卫在 M=NaN 时也会从「照记账」变为「跳过」）有限输入上等价；单测见 `MandateReadoutSpec`／`MandatePhenomenaSpec`。

**测试标记约定**：为「行为不变」而**刻意钉住旧后果**的断言带批次注释——第一批是 `[BATCH-1 REGRESSION LOCK]`，第二批在具名事实处补 `[BATCH-2 REGRESSION LOCK]`（短时锁闭仍判菌塘 `Withered`、仍发静默签名）。它们的含义是「当前行为被锁定」，**不是**「解耦目标已达成」；`grep -rn "BATCH-1 REGRESSION LOCK\|BATCH-2 REGRESSION LOCK" src/` 可一次找全，等 §8 对应项拍板后集中改。

### 7.1 消费者现在读什么（第二批后）

「现在读什么」是代码事实；「语义上可能需要」是 §8.5 的候选，**不是决定**，拍板前不要据此改代码或文案。

| 消费者 | 现在读的事实（结论不变） | 语义上可能需要（候选【待拍板】） | 耦合 |
| :--- | :--- | :--- | :--- |
| 玩家沉滞效果（`MandateOverworld.applyChunkMandateEffects`） | `isMaintained`；离开维持态后还内联 `Regime.Burnout \|\| dissonance >= ReadoutParam().spikeLevel` 判尖峰档 | 庇护窗口（`shelteredAt`）＋ 实际暴露（尖峰／锁闭），不必牵连菌网健康 | C8、C13 |
| 经济档（`MandateEconomy` → `classifyEconomy`） | `isMaintained`、`Regime.Burnout`、`hasActivePositiveSpike` | 长期土地健康（`isMyceliumViable`／`isSlowOffsetBelowKnee`）与临时危险（尖峰／锁闭）分账 | C9 |
| 菌塘（`GastrodiaBedBlock` → `evaluateGastrodiaBed`） | `isGastrodiaBedDeepDead`；`Stalled` 分支还读 `current.stress >= strained` 与 `M < GastrodiaHealthySustenance` | 生态死亡（`M`／`isEcologicalDeath`）与短时锁闭分开；锁闭期是否只停长 | C1、C14 |
| 静默签名（`classifyPhenomena` → `hasSilentBlight`） | `hasSilentBlight` | 是否只绑真正死亡（`isMyceliumAbsent`／`isSlowOffsetBelowKnee`），锁闭另设征兆 | C2、C7 |
| 钟分派（`BellRitual.evaluateBellStrike`） | `isEcologicalBurnout`、`pulseLedger.wouldOverloadAt`、`isBurnoutPhase`、`Regime`、`baselineCalming`、`phase`（`Cycle` 的标签分支） | 账本结算是否为唯一真相；分派是否只做提示／文案 | C10、C6 |
| 验山读数／告示（`MandateWording`） | `isMaintained`、`regime`、`sustenanceWord`（`M`／floor）、`pulseLedger.effectiveWindowAt`、`phase`（`formatPhaseDial`） | 两个 no-cycle 原因是否拆词 | C2 |
| 开发 HUD（`MandateHud`） | `isMaintained`、`regime`、`phase`；格子尖峰判色（`tileColor`）内联 `dissonance >= spikeLevel` | 已可直接读两个原因，无需再改 | — |
| 死菌守卫（`MandateAgent.recordPulse`） | `isEcologicalDeath` | 死亡边界是否只看 `M` 而非 `a_slow` | C5 |

> 尖峰判据尚未收敛：`MandateAgent`（尖峰事件）、`MandateOverworld`（尖峰档）、`MandateHud`（格子尖峰判色）仍内联 `dissonance >= spikeLevel`，与 `hasActivePositiveSpike` 取值相同。若日后改尖峰定义（例如改为上穿确认），这三处要一起改；§7.1 不表示它们已经只依赖具名事实。

---

## 8. 待定项（待用户拍板）

以下每一条都会改变 §2／§5／§6 中的某行；未拍板前不要在施工或文案里写成既成事实。

### 8.1 庇护

- 庇护的来源、寿命、范围；是否所有 Counted 正脉冲都开窗，还是只由钟／设施／世界条件维持。
- `W0 = 40 s` 是否保留；短时锁闭是否应撤销庇护（当前 C4 是）。

### 8.2 耐受

- 耐受适用于什么对象：土地、局部介质、设施，还是某条刺激通道。
- 是否「正常维护必加耐受」（当前 C11 是）；步进 0.10／0.35、`τ = 45 s`、上限 1.5、反转阈值 1.0 是否保留。

### 8.3 过载

- `I_min = 8 s` 与锁闭 8 s 是否保留；默认参数下 `AlreadyLocked` 不可达，是否需要修参数或删分支。
- 锁内再敲**当前会让锁闭不断刷新（无总上限）**：是否保留、是否封顶、是否改成「锁内再敲只延长一次／不延长」；持续链路流量导致的长期锁闭是否可接受（见 §2.4）。
- 过载造成什么损伤：当前只有「物理间接」（`p → v → q → s → M`），若再加显式的**账本负荷 → 损伤 → 存活**路径，须先核对该链，避免同一后果重复累计（备忘 §3.7）。

### 8.4 生态后果

- 短时过载：暂停生长、不影响菌塘，还是允许枯死锁存。
- 静默签名是否覆盖锁闭；读数规则越膝（`OffsetBelowKnee`）是否仍判菌塘枯死；钟在 `OffsetBelowKnee` 下是否仍「无应」。
- 若要定义「刺激负荷造成生态损伤」的玩法规则，需明确它落在**物理间接链**（现成）之上还是另立账本损伤（当前没有），并给出不与酸蚀重复累计的依据。

### 8.5 消费者分账

- `isMaintained` / `classifyEconomy` / 玩家效果 / 菌塘是否拆开；庇护是否应随 `M` 分级；沉滞是否只依赖庇护而不牵连经济与菌塘。
- 第二批已把候选事实具名化（§1.3）并列出每个消费者当前的读法（§7.1）。多数情况拍板后只需改读取对象；但若方向涉及「短时过载期菌塘只停长」或「静默只绑真正死亡」，还需要新增／调整判据（例如给 `evaluateGastrodiaBed` 增加 `isOverloadLocked → Stalled` 分支，或把 `hasSilentBlight` 改为只读生态死亡事实），不是纯替换。

### 8.6 钟与转义层

- 转义层接口、数据结构与执行方式（【用户确认】方向「事件 → 机制（1..n）→ 效果」与转义层职责，实现未定，见[备忘 §2.1](work-in-progress-mechanism-decoupling.md)）。
- 钟的多职责（`c0` 重锚、脉冲、账本、惩罚）如何拆；拆钟应改变什么。

### 8.7 来源与账本

- 链路脉冲、内生尖峰、外部敲击是否共享同一套耐受／过载／庇护规则；是否用「发生事实」而不是重复注入物理刺激。

### 8.8 到期与观测

- 到期是否需要一次性效果或通知；读数是否需要并列显示两个 no-cycle 原因；玩家文案是否拆分「过载·暂锁」。
- 玩家看到的征兆与真实状态的对应关系（[备忘 §3.3–§3.5、§8](work-in-progress-mechanism-decoupling.md) 的观测问题）。

### 8.9 保存与参数

- 无旧档兼容要求【用户确认，见[备忘 §1](work-in-progress-mechanism-decoupling.md)】；将来可改 NBT key／`schemaVersion`，但新机制自身的保存与恢复必须正确。
- 所有数值为首批手感值，待 playtest 校准（[札记 08 §6](design-note-08-fast-variable-and-stagnation.md)）。

> 后续每形成一条结论，应回到 §5／§6 的对应行更新，并把【待拍板】改成【用户确认】或【本轮显式化，结果不变】；结论未形成前保留问题，避免在上下文接续时把推测当成既定要求。
