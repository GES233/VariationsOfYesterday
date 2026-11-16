# 札记 12（草案）：语义宪章——一物一名、钟即起搏、内核只发原子事实

> **状态**：草案；**S1（敲钟 v2a）已于 2026-11-16 落地**——三态判定、`SpikeSwallowed` 删除、相位文案/音画分支清除、裁决 ⑯ 记入札记 11，169 例全绿。3 处待拍板仍未定（§8）。
> **缘起**：语义漂移已经影响到创作判断本身——同一个量在文档/代码/玩家文案里有 3–10 个名字，"敲钟"这一条行为上就压着两代语义（札记 08 的相位窗 vs 札记 11 的节律）。最新想法「敲钟不那么看精确相位」若落地，会把现有裁决表撕开一道口子，**必须先定语义，再动代码**。
> **前置**：[札记 11](design-note-11-heartbeat-shelter.md)（现行庇护语义，15 条裁决）、[草案 01](design-draft-01-living-world.md)（方程与通道）。
> **取代**：札记 11 §1.5 判定表**第 3 行**（`SpikeSwallowed`）；§7 追加 **裁决 ⑯**。
> **本文档不写的**：方程、数值标定、实现细节。本文只定**名字、边界、谁判什么**。

---

## 0. 一页摘要

| 项 | 决定 |
|---|---|
| 命名 | **一概念一技术名、一技术名一概念**；名字的"种类"由后缀编码（量 / 态 / 事件 / 词）；`inner/` 内部去掉 `Mandate*` 前缀 |
| 敲钟 | **钟是起搏器，不是相位狙击手**。门控只看两条**状态**判据（生态死亡 / 过驱动），相位门控删除；「校拍/静默/失锚」降级为**事后回声**，不再影响任何判定 |
| 内核输出 | 只发**原子事实**（L0 采样 / L1 变更 / 命令回执），**永不发视图**。字符串、颜色、粒子、HUD 刻度、阈值一律不进边界 |
| 判定的归属 | 谁拥有状态，谁判定。`BellRitual` 现在从边界偷读引擎私有状态（`lastBellStrikeAt`/`simTime`）——改为**命令 → 回执** |
| 多人 | 现在只冻结**边界形状**（8 条，含粒度），不实现传输层；传输、兴趣管理、预测回滚以后再说 |
| 代价窗口 | 0.1 无外部用户 → 断存档/断协议的代价 ≈ 0。**这是清漂移最便宜的时刻** |

---

## 1. 为什么现在定，而不是以后

**漂移已经到达"会影响决策"的级别**，三个例子：

1. **同一个"锚定成功"有两种说法**：`c0 ≥ 0.01` 的振荡格在 `Excitation` 相位敲钟，裁决是 `AnchoredOrMaintained`（**已锚定**，`BellRitual.scala:75-76`），但文案走 `MandateWording.scala:165-166` 打印「叩钟失锚…c0 未重锚」。同一结果，代码说锚了，玩家被告知没锚。
2. **同一条行为压着两代语义**：札记 11 §2.2 的起搏器实测表（8 s 阻滞 / 12–20 s 2:1 / 30–60 s 1:1 / 90 s 过缓）**全部按间隔判定、与相位无关**，裁决①也明说「带内 → `Beating(Paced)`，不另设判据」；但 `BellRitual.scala:67-74` 仍在按 `PhaseLabel.Excitation` 门控。**代码落后于自己的裁决。**
3. **一个词三个意思**：`庇护` 同时是 `CellReadout.isSheltered`（节律庇护）与 `c0Sheltered`（`c0 ≥ 0.01`），两者在同一个 S2C payload 上同行（`MandateSyncProtocol.scala:22,25`）。

**为什么是现在**：0.1 阶段单人、无外部存档、无发布协议 → 「断档/断协议」的成本是 0，只需丢一次本机存档。一旦有玩家，同样的改名要付迁移成本，会**永远做不成**。

**为什么和"原子化"是同一件事**：漂移的根因不是命名随意，而是**边界上流通的是"视图"而不是"事实"**。视图天生要把多层语义焊在一起（`CellReadout` 11 个字段横跨物理量、判定、表现抓手、阈值），焊死之后每加一个消费端就要多一个名字。原子化是**命名纪律的前提**。

---

## 2. 命名法则

### 2.1 四条法则

| # | 法则 | 反例（现状） |
| :--: | :--- | :--- |
| N1 | **一概念一技术名**：同一物理量在代码里只有一个名字，别名只允许出现在注释（且必须括注正式名） | `sustenance` / `M` / `mycelium` / `MycelialHealth` / `菌网` 指同一个量 |
| N2 | **一技术名一概念**：一个名字不得兼任两种"种类" | `Readout` 同时是分类器、分岔数学、DTO 家族前缀 |
| N3 | **种类由后缀编码**（见 §2.2） | `Heartbeat` / `BeatStats` / `beatPeriod` / `heartbeatOrdinal` 混用 |
| N4 | **层由包编码，不用前缀**：`inner/` 内不用 `Mandate*` | `inner/veins` 里 8 个 `Mandate*`，前缀零区分度 |

### 2.2 四种"种类"与后缀

| 种类 | 含义 | 后缀 / 形态 | 例 |
| :--- | :--- | :--- | :--- |
| **量** | 连续标量（Double），物理或库存量 | 普通名词，无后缀 | `stress` `sustenance` `calming` `baselineCalming` `excitation` `recovery` `activity` `pulseDrive` |
| **态** | 由量派生出的离散判定 | `*Tier`（分档）/ `*Regime` / `*Verdict` / `*Label` / `*Outcome` / `*Reason` | `SustenanceTier` `Regime` `RhythmVerdict` `PhaseLabel` `BellOutcome` `ShelterReason` |
| **事件** | 发生了一次的事实 | `*Sampled` / `*Changed` / `*Began` / `*Ended` / `*Struck` / `*Echoed` / `*Accepted` / `*Rejected` | `RegimeChanged` `SpikeBegan` `BellEchoed` |
| **词** | 玩家可见文本 | **只能是 `MandateWording` 中的方法或 lang 键**，不得成为 `inner/` 的符号 | `菌网完好` |

> 一句话记忆：**量是名词、态是带后缀的名词、事件是过去式、词只活在文案层**。

### 2.3 改名成本三档（决定"什么时候改"）

| 档 | 范围 | 代价 | 何时做 |
| :-- | :--- | :--- | :--- |
| **A 免费** | 注释、文档、`mandate/`+`overworld/`+`client/` 内部符号、测试局部名、`inner/` 中不进 digest/存档/同步的符号 | 0 | **现在**（P0） |
| **B 数值快照** | `PhaseDetection` 常量与 `HeartbeatParam` 来源值（改数字 → 快照变化）；**`MandateState` / `ODESolver` 已不在此档**（2026-11-16 移除源码 digest，见 §9.2） | 需 `-Dvoy.contract.write=true` 重生成 + 逐行评审 | 搭札记 11「后续工作 2」的契约窗口 |
| **C 断档/断协议** | NBT key、`MandateCurrent` 字段、S2C 形状、enum case 名（`valueOf`） | schema 4 / payload v2，旧档丢弃 | **一次做完**（0.1 窗口内，代价≈0） |

---

## 3. 核心词表（canonical）

> 列义：**技术名**=代码唯一名（目标）；**现在叫什么**=禁止再用的写法；**玩家词**=只能出现在 `MandateWording`/lang。

### 3.1 量（L0）

| 概念 | 技术名 | 现在叫什么（禁） | 玩家词 |
| :--- | :--- | :--- | :--- |
| 快变量 v | `excitation` | `dissonance` `dis` `v` `快变量` | — |
| 慢变量 w | `recovery` | `entrench` `en` `w` `慢变量` | — |
| 应力 s | `stress` | `s` `应力` `landStress` | 地脉（→由 `Regime` 说） |
| 活动示踪 q | `activity` | `q` `tracer` | — |
| 菌网完整度 M | `sustenance` | `M` `维持度` `mycelium`（作量名时） | 菌网 |
| 主动安抚 c | `calming` | `c` | 安抚 |
| 基线安抚 c0 | `baselineCalming` | `c0` `c0Intact`（作阈值时改名）`BellBaselineCalming`（作剂量时改名） | 安抚源 / 涓流 |
| 脉冲驱动 p | `pulseDrive` | `p` `synapseP`（仅核内部） | — |
| 过驱动守卫 | `overdriveGuard` | `minInterval` `cooldown` `8s 窗` | 过密 |
| 酸蚀阈值 | `sustenanceBurnThreshold` | `酸蚀线` `toxicityThreshold` | 酸毒 |
| Allee 阈值 | `sustenanceFloor` | `floor` `枯绝线` | 枯绝 |

### 3.2 态（L1）

| 概念 | 技术名 | 现在叫什么（禁） | 玩家词 |
| :--- | :--- | :--- | :--- |
| 地脉态 | `Regime {Calm,Strained,Oscillating,Burnout}` | `regime 轴` `态` | 平静/绷紧/起振/饱和失能 |
| 相位读数 | `PhaseReading`（带理由）/ `PhaseLabel`（标签） | `相` `window` `刻度` | 刻度（20 格） |
| 节律判定 | `RhythmVerdict` | `Heartbeat` `heartbeat` `beat` `节拍` | 预热/起搏/过速/过缓/失律 |
| 节律来源 | `RhythmSource {Intrinsic,Paced}` | `BeatSource` `intrinsic/paced` | — |
| 节律证据 | `RhythmEvidence` | `BeatStats` `periods/duty` | — |
| 菌网档 | `SustenanceTier {Intact,Damaged,Dead}` | `MycelialHealth` `mycelium` `health` | 完好/受损/枯绝 |
| 庇护 | `isSheltered` + `ShelterReason` | `庇护`(作字段) `c0Sheltered` | 庇护 / 沉滞 |
| 生态死亡 | `isEcologicallyDead` | `isEcologicalDeath` `ecoDead` `burnout` `dead` | 不可逆 / 无应 |
| 静默枯萎 | `hasSilentBlight` | `silent blight` `isBurnoutPhase` | 死寂 |
| 黄水档 | `YellowSeepTier` | `seep` `铁黄渗水` | 黄水 |
| 经济档 | `SettlementTier {Maintained,Stagnant,Spiking}` | `EconomyStagnant` `经济档` | 维持/沉滞/急性 |
| 敲钟裁决 | `BellOutcome {NoAnswer,Overdriven,Struck}` | `BellStrikeOutcome` 四态 | 无应/过密/叩钟 |
| 敲钟回声 | `PulseOutcome {Paced,Delayed,Absorbed,Unresolved}` | 离线 `PulseOutcome`（含 `Premature`）与 `校拍/静默/失锚` 混用 | 校拍/静息/失锚/无应 |
| 尖峰 | `SpikeBegan` / `SpikeEnded`（事件） | `isSpiking` `地鸣` `尖峰` | 地鸣 |

> 注：运行时用 `Paced`（"这一拍打出了新的兴奋"），离线实验保留 `Premature`（相位响应术语，需要"本该何时到"的基线）。`PhaseResponse` 移出 main（P1-1）后两者不再共处一层，不构成 N1 冲突。

### 3.3 阈值（统一进 `SemanticsProfile`）

`SectionLevel=1.2`、`PeakLevel=1.8`、`ProbeLevel=1.9`（**仅离线**）、`sustenanceFloor`、`sustenanceBurnThreshold`、`C0Anchor=0.03`、`C0ShelterLevel=0.01`、`strained/burnout/oscillatingQ`。
**规则**：阈值**只能**出现在 `SemanticsProfile` 里；任何事实**不得携带阈值**（现在 `CellReadout` 带了两个，见 §5.3）。

### 3.4 禁止写法清单（改名时 grep 这些）

> **S2b 已执行（2026-11-16）**：`dissonance`→`excitation`、`entrench`→`recovery`、`Heartbeat`→`RhythmVerdict`、`HeartbeatParam`→`RhythmParam`、`BeatStats`→`RhythmEvidence`、`BeatSource`→`RhythmSource`、`MycelialHealth`→`SustenanceTier`、`mycelialHealth()`→`sustenanceTier()`、`isEcologicalDeath`→`isEcologicallyDead`、`isBurnoutPhase`→`hasSilentBlight`、`c0Sheltered`→`hasCalmingStock`、`MandateParam`→`FastParam`；文件 `inner/veins/Heartbeat.scala`→`Rhythm.scala`，两个 spec 同步改名；34+29 个文件、±433 行、169 例全绿、**契约文件零变化**。
>
> **刻意未改（C 档，改了会断存档/断配置/断遥测）**：NBT key `dissonance` / `entrench` / `beatPeriods` / `beatDuty`，config key `heartbeatParam`，以及 wire name `"Beating"`。规则：**wire/存档里的名字是冻结边界，代码名与它不同是正常的**（就像协议 id）。

`dissonance` `entrench` `BeatStats` `BeatSource` `MycelialHealth` `BellStrikeOutcome` `SpikeSwallowed` `isEcologicalDeath` `isBurnoutPhase` `c0Sheltered` `c0Intact`（作阈值时）`BellBaselineCalming`（作剂量时）`MandateParam`（作快变量参数时 → `FastParam`）`section`（作阈值时）

---

## 4. 敲钟语义 v2：钟即起搏器

### 4.1 现状：六处读相位，其中一处是门控

| # | 位置 | 读了什么 | 作用 | 处置 |
| :--: | :--- | :--- | :--- | :--- |
| 1 | `BellRitual.scala:70-72` | `phase == Cycle(Excitation,_)` | **唯一真相位门**：不锚 `c0`（`SpikeSwallowed`） | **删除** |
| 2 | `BellRitual.scala:68` | `regime == Oscillating` | 门的使能条件（本身不是相位，但与 #1 合起来构成"相位窗"） | 随 #1 删除 |
| 3 | `BellRitual.scala:63` | `readout.isSlowOffsetBelowKnee` | 生态死亡判据之一——**间接读相位**：`CellReadout.isSlowOffsetBelowKnee` 的第一析取项是 `phase == Undefined(SlowOffsetBelowKnee)`（`CellReadout.scala:34-36`） | 改为只读量（见 §5.3 注） |
| 4 | `MandateWording.scala:156-168` | `PhaseLabel` 四分支 + `dialTick` | 文案（校拍/静息/失锚/起搏） | **改写**为回声文案 |
| 5 | `BellStrike.scala:116-129` | 是否不应期 | 音画（紫水晶清音 vs 3 音符） | 合并为 2 态（响 / 闷） |
| 6 | `MandateWording.scala:165-166` | —（`anchorsC0 == true` 时仍可达） | **语义冲突**：已锚定却打印"c0 未重锚" | 随 #1 一起消失 |

> 说明 #3：`isSlowOffsetBelowKnee` 有两套来源（`phase` 的 Undefined 理由 vs `Readout.isSlowOffsetBelowKnee` 用**默认参数**重算）。后者用的是 `MandateAgent.DefaultFastParam` 与默认 `coupling/depth`，与格子的实际配置可能不一致——**同一个谓词两个真相源**。原子化时只留"量 + profile"一条路。

**关键证据**：札记 11 §2.2 起搏器实测表 —— 8 s→阻滞、12/20 s→2:1/3:1、30–60 s→1:1、90 s→过缓。**全部按间隔判定，零相位依赖**；裁决①「带内 → `Beating(Paced)`；不另设判据」。→ **相位门控是札记 08 时代的遗留，与裁决①自相矛盾。**

### 4.2 v2a（必做，半天）：三态门控

```
BellOutcome:
  NoAnswer    ← isEcologicallyDead          （脉冲进方程；不锚 c0；不惩罚）
  Overdriven  ← 守卫内重复敲                （脉冲进方程；不锚 c0；吃 pulse_saturation）
  Struck      ← 其余一切                    （脉冲进方程；**无条件** c0 剂量 + 注册源）
```

- 删 `SpikeSwallowed`（唯一 case）；`evaluateBellStrike` 不再需要 `phase`。
- 判定依据只剩**引擎自有状态**（`sustenance`/越膝/`lastBellStrikeAt`）。
- 玩家词：无应 / 过密 / 叩钟。**「校拍」不再是敲钟的结果，而是节律的状态**（`RhythmVerdict.Beating` 且 `source == Paced`）。
- 音画三态（`BellStrike.feedback`）：叩钟＝琥珀尘 + 音符；过密＝锈红爆散 + 低音共鸣；无应＝**蜡烛闷熄 + 死灰**。最后一条是实测教训：4→3 合并时 `NoAnswer` 一度只剩 3 颗灰、没有声音，玩家无法把它与"点空了"区分——"无回音"必须是**可辨认的沉默**，不是真的静音。

### 4.3 v2b（推荐，1–2 天）：加一层「回声」，让词活下去

钟砸下去之后开一个 ≤10 s 的观测窗（脉冲峰在 t = τ = 5 s，与 `SpikeConfirm.WithinWindow(5.0)` 同量级），事后报一条**事实**：

| `PulseOutcome` | 含义 | 玩家词（复活） | 原始硬件 |
| :--- | :--- | :--- | :--- |
| `Paced` | 这一拍引发了新的兴奋 | **校拍** | 本拍内出现新 `SpikeBegan` |
| `Delayed` | 把下一拍推后了 | **静息** | 下一次上穿晚于基线 |
| `Absorbed` | 被既有兴奋吞没 | **失锚** | 窗内无新兴奋且窗外已有在跑 |
| `Unresolved` | 窗内无反应 | **无应** | 无上穿也无既有兴奋 |

三条约束，缺一不可：

1. **回声不参与任何判定**（不改 `c0`、不改节律、不改经济）。它只是叙事与图鉴的原料。
2. **允许晚到**：3–10 s 后一条聊天反馈完全贴合「滞后效应」主题，并与「钟声在地脉里回荡了半拍」的写法一致。
3. **零新机制**：复用 `SpikeDetector` 的确认窗 + `PulseLinkRunner` 的 per-cell 观测模式（晶链已经这么做了）。

> **为什么这比"精确相位"好**：相位门控要求玩家读 20 格刻度盘并卡在几十毫秒的窗口里——而本项目的美学纲领是「世界即仪表盘」（札记 06 §3），世界本身并不显示精确相位。**一个必须看 HUD 才能玩对的机制，与自己的美学纲领冲突。** v2a+v2b 把技巧从"卡相位"移到"持节奏 + 判状态"，两者都能从世界本身读出来（钟声、菌光、颜色语言）。

### 4.4 与既有裁决的关系

- **记裁决 ⑯**：敲钟不再以相位为门控；`SpikeSwallowed` 删除；`校拍/静息/失锚/无应` 改为事后回声。依据：札记 11 §2.2 实测表 + 裁决①。
- **§1.5 判定表第 3 行作废**（本文档取代）。
- **裁决 ⑧「regime 轴不动，以心跳为准」保持不变**：v2 让敲钟更依赖心跳轴，方向一致。
- **c0 剂量与源注册不变**（裁决 ⑪）：`Struck` 仍然 `+0.008` + 注册 Bell 源。

### 4.5 迁移表（代码层）

| 现在 | 之后 | 位置 |
| :--- | :--- | :--- |
| `BellStrikeOutcome.DeadMyceliumBurnout` | `BellOutcome.NoAnswer` | `BellRitual.scala:31` |
| `BellStrikeOutcome.PulseOverloadBurnout` | `BellOutcome.Overdriven` | `:39` |
| `BellStrikeOutcome.SpikeSwallowed` | **删** | `:47` |
| `BellStrikeOutcome.AnchoredOrMaintained` | `BellOutcome.Struck` | `:55` |
| `outcome.anchorsC0` / `baselineCalming` / `pulseArea` / `triggersOverloadPenalty` | 收敛为 `BellOutcome` 自带的 3 个能力位（`anchorsC0` 恒为 `true` 时可直接去掉该位） | `:21-26` |
| `readout.phase` 分支 | 删 | `MandateWording:156-168`、`BellStrike:116-129` |
| —（新增） | `PulseOutcome` 回声事实 | `inner/` 新增，复用 `SpikeDetector` |

> **S1 已按本表落地**（2026-11-16；上表"位置"列的行号是**改动前**的）。两处超出原 S1 描述、但在此一并做了：
>
> 1. **enum 与 case 改名提前到 S1**：`BellStrikeOutcome` → `BellOutcome`、`AnchoredOrMaintained` → `Struck`、`DeadMyceliumBurnout` → `NoAnswer`、`PulseOverloadBurnout` → `Overdriven`。理由：判定规则变了之后旧名字全部名不副实，留着就是本文档要消灭的那种漂移；改动只涉及 4 个文件 + 1 个测试，编译器全程护航。
> 2. **`BellBaselineCalming` 删除**（原计划 S3 收进 `C0Anchor`）：它是"假值"的唯一符号载体，随 `outcome.baselineCalming` 字段一起消失；锚值暂只在 `CalmingParam.tricklePerWeight` 与契约 `c0Intact` 两处，S3 再收成单点。
>
> **测试**：`MandateReadoutSpec` 的 excitation 段与低 c0 未锚段已重写为三态，并新增「c0 库存不影响裁决」回归守卫（若有人把 c0 或相位门加回来，该用例立刻红）；`MandateAgentSpec` 的守卫戳/晶链不戳/死格不戳用例保留（测的是引擎行为，不是相位）；`MandateHud:198-200` 的刻度条注释已同步。
>
> **尚未做**：进游戏的三态音画手感验证的**终检**（`runClient` 敲活格/过密/无应三态）。为免"死格要等 30 分钟"，已加开发命令 **`/voy decay`**（把自己所在区块的 `M` 置 0，吸收态）——仅开发环境注册，发布包不含。

---

## 5. 内核输出的原子化边界

> **状态：目标形态，当前未实现。** 现在边界上真实流通的仍是 §5.3 描述的那个 11 字段 `CellReadout` 视图；本节写的是它要变成的样子，落地见 §7 的 **S3**。读到这里不要以为事实流已经存在（2026-11-16 补注：这是本文档唯一一处"描述未来"的章节）。

### 5.1 "原子"的六条判据

一条输出算"原子事实"，当且仅当：

| # | 判据 | 反面（现状） |
| :--: | :--- | :--- |
| F1 | 只讲一件事（单一语义） | `CellReadout` 11 个字段横跨四层 |
| F2 | 自足：带 `CellRef` + `simTime` + `semanticsVersion`，离开上下文可解释 | 消费端要靠"上一帧"才能解释 |
| F3 | 幂等：带 `seq`/`strikeId`，重复投递无害 | 事件无 id，重放会产生重复反馈 |
| F4 | 无表现：不含字符串、颜色、粒子、UI 布局、玩家 | `dialTick`（20 格刻度）在 DTO 里 |
| F5 | 无聚合：不跨格、不跨时间、不跨玩家合成 | 每 tick 全格 `Map` 快照（1024 条） |
| F6 | **可重算**：不依赖引擎私有历史。依赖去抖/滞回/观测窗的判定（`Regime`、`RhythmVerdict`、`SpikeBegan`、`BellEchoed`）**必须发布**——下游算不出来；`f(量, profile)` 的纯派生（`SustenanceTier`、`PhaseLabel`、`dialTick`、`isToxicSoil`）**不发布** | 纯派生的 `PhaseLabel`/`dialTick` 被当事实发出去；阈值随 DTO 传输，两端可能不一致 |

### 5.2 三类事实 + 三条通道（不同频）

```
┌ 遥测通道（限频 1–5 Hz，按订阅，可丢弃）── 只给调试/HUD/数值报表，禁止用于玩法判定
│   CellSampled(cell, simTime, seq, excitation, recovery, stress, activity,
│               sustenance, calming, baselineCalming, phaseFraction: Option[Double],
│               phaseUndefined: Option[UndefinedReason])
│
├ 变更通道（只在变化时发，不可丢）──────────── 玩法唯一通道
│   RegimeChanged(cell, simTime, seq, to)                    ← 含 regimeDwell 去抖，不可重算
│   RhythmChanged(cell, simTime, seq, to, evidence)          ← 含 smooth 滞回，不可重算
│   SpikeBegan / SpikeEnded(cell, simTime, seq)              ← 边沿检测，不可重算
│   BellStruck(cell, simTime, seq, strikeId, outcome: BellOutcome)
│   BellEchoed(cell, simTime, seq, strikeId, outcome: PulseOutcome)  ← 观测窗，不可重算
│
└ 回执通道（点对点）────────────────────────── 命令的答复
    CommandAccepted(cell, simTime, seq, commandId)
    CommandRejected(cell, simTime, seq, commandId, reason)
```

**这样事实集合比第一版小了近一半**：`SustenanceTier`、`isSheltered`、`PhaseLabel`、`isToxicSoil`、`dialTick`、各种 `word` 全部**不再发布**——它们是 `f(已发布的量或事实, SemanticsProfile)`，由每个消费端用**同一份共享纯函数**算。少一条事实 = 少一处真相、少一个版本兼容点。

**规则**：

- **只发"变成什么"，不发"从什么变成什么"**。晚加入的客户端错过 `from` 也能收敛（现状 `RegimeTransitioned(cell, from, to, phase)` 同时带了 `from` 与没人用的 `phase`，后者在 `ActionFeedback:62` 被 `_` 丢弃）。
- **阈值不在事实里**，在 `SemanticsProfile`（版本化）。事实带 `semanticsVersion`；版本不符的客户端**降级**（关 HUD / 只显示文字），而不是花屏。
- **遥测通道不是契约**：它的字段可以随调试需要增删，不算破坏兼容；变更通道才是契约。
- **两通道频率分开**才有带宽可言：现状是每 tick 重建全格 `CellReadout` 映射（1024 条 × 20 Hz），多人下这不可能，即使单人也是纯浪费。

### 5.3 `CellReadout` 的去向（逐字段）

| 字段 | 层 | 现状消费端（示例） | 去向 |
| :--- | :-- | :--- | :--- |
| `current: MandateCurrent` | L0 量包 | Phenomena / Wording / HUD / ActionFeedback | 拆进 `CellSampled` 的 4 个量；`entrench` 出向无人读，只留遥测 |
| `regime` | L1 态 | Wording / HUD / Phenomena / ActionFeedback | `RegimeChanged`（**保留**：有去抖，不可重算） |
| `phase` | L1 态 | Wording / BellStrike / HUD / SliceReplay | **下沉**：只发 `phaseFraction + phaseUndefined`；`PhaseLabel` 与 `dialTick` 归表现层按 profile 算 |
| `sustenance` | L0 量 | Phenomena / blocks / `mycelium` | `CellSampled.sustenance`（**保留**） |
| `baselineCalming` | L0 量 | BellRitual 门控 / MandateCalming / blocks / HUD | `CellSampled.baselineCalming`（**保留**） |
| `pulseDrive` | L0 派生量 | 零玩法消费，纯调试 | **删**（要留只留遥测） |
| `heartbeat` | L1 态 | Wording / HUD / Phenomena / blocks / S2C | `RhythmChanged`（**保留**：有滞回，不可重算） |
| `lastBellStrikeAt` | **引擎私有状态** | 唯一消费点是 `BellRitual:78-80` 的"减时钟比 8 s" | **下沉为派生量**并**移出边界**：守卫由引擎判，输出 `BellOutcome.Overdriven`；不再把时间戳发给跨界 DTO |
| `simTime` | 元数据 | `BellRitual` 算守卫 | **删**（per-world 时钟不该在 per-cell 里复制 1024 份）；事实自带 `simTime` |
| `sustenanceFloor` | 阈值 | `mycelium` 派生 | **删** → `SemanticsProfile` |
| `toxicityThreshold` | 阈值 | `isToxicSoil` | **删** → `SemanticsProfile` |
| `dialTick()` | **L2 表现** | HUD / Inspection / Wording | **下沉到消费端** |
| `mycelium()` | L1 派生 | 多处 | 不发布；消费端用 `f(CellSampled.sustenance, profile)` |
| `isSpiking()` | L1 派生 | Phenomena / ActionFeedback | `SpikeBegan/Ended`（**升格为事实**：边沿不可重算） |
| `isSheltered()` | L1 派生 | StagnationRunner / Economy / HUD | 不发布；由 `RhythmChanged` + 量 + profile 算（若实测重复实现 ≥3 处，再升格） |
| `isToxicSoil()` | L1 派生 | Phenomena / blocks | 不发布；`f(stress, profile)` |
| `isSlowOffsetBelowKnee()` | L1 派生 | BellRitual / Phenomena / blocks | **合并为一条**：只读量与 profile，不再从 `phase` 的 Undefined 理由反推（消除 §4.1 #3 的双真相源） |

> 汇总：**保留 5（`current`/`sustenance`/`baselineCalming`/`regime`/`heartbeat`）、下沉 2（`phase`/`lastBellStrikeAt`）、删除 4（`pulseDrive`/`simTime`/两个阈值）、派生成员按 F6 分流**。

> **最重要的一条**：`lastBellStrikeAt` 与 `simTime` 出现在跨界 DTO 里，是因为**敲钟的裁决被放在了边界之外**——纯函数 `BellRitual` 需要读引擎私有状态。改成**命令 → 回执**（玩家敲钟 = 发 `StrikeBell` 命令；引擎持有状态、自己判、回 `BellOutcome` 事实）之后，这两个字段从边界消失，判定回到状态的所有者手里。这也是多人下唯一正确的形状（客户端不判权威状态）。
>
> **两条粒度规则**（务必写进代码注释，否则一定会再混）：
> 1. **过驱动守卫是「格」的属性，不是「玩家」的属性**：地脉 8 s 内吃不下第二记钟，谁敲都一样；而**麻手（`pulse_saturation`）是「玩家」的**，只给敲钟的那只手。现状是"记在格、罚在玩家"（`BellStrike.scala:63-73`）——**规则本身是对的，但没被命名**，所以看起来像 bug。命名：`BellOutcome.Overdriven`（格级）+ `PlayerRecoil(player)`（玩家级），由玩家层消费端投影出来。
> 2. **per-player 的东西不得进 per-cell 事实**：现状反例有 5 处，最典型的是 `includeDebug`——**同一个格的 wire 形状取决于接收者的权限**（`MandateNetworking:82-83`），多人下同一格对不同玩家必须有不同视图，这种差异只能由"事实 × 观察者"投影产生，不能烘焙进事实。

### 5.4 视图去哪了

| 视图 | 现在在哪 | 之后 |
| :--- | :--- | :--- |
| 玩家文案 | `MandateWording`（wiring 层，OK） | 不变，但只消费 L1 事实 |
| 粒子/音效 | `BellStrike` / `PhenomenaEmitter` / `BedFx` | 不变（消费端） |
| S2C payload | `MandateSyncProtocol` 手写 12+12 字段 + 5 处 `.ordinal` | 改为**转发事实流 + 快照**，不再自定义视图结构；`.ordinal` 换成显式 tag + 版本号 |
| HUD | `MandateHud` / `CellChangeTracker`（客户端自有中文字典 2 套） | 合并为 1 套，消费 L1 事实 |

---

## 6. 为多人预留：现在冻结 8 条，以后再说 5 条

### 6.1 现在必须冻结（都很便宜，晚了很贵）

| # | 约束 | 现在状态 | 代价 |
| :--: | :--- | :--- | :--- |
| M1 | **格子标识带世界**：`CellRef(worldKey, x, z)`，不用裸 `(Int,Int)` + 隐式主世界 | 裸坐标 + `MandateRuntime` 硬取 `getOverworld` | A 档 |
| M2 | 事实自足 + 带 `seq` + 带 `semanticsVersion` | 无 id、无版本 | A 档 |
| M3 | 事实只用 `simTime`，**不得混入 MC tick** | `overloadPenaltyTicks` 是 MC tick 混在仿真语义里 | A 档 |
| M4 | 引擎**永不引用玩家**；per-player 数据只在消费端 | 已满足（`StagnationRunner` 投影是消费端） | 0 |
| M5 | 世界 → 引擎只发**命令**，不发状态写；每条命令有**回执** | 只有 `EventDTO`，无回执 | A 档 |
| M6 | 判定只依赖（事实 + profile），不依赖"客户端看到了什么" | `BellRitual` 靠 DTO 偷状态，违反 | B 档（§5.3） |
| M7 | 权威单写；读侧无锁 | 已满足（`@volatile` 单写多读） | 0 |
| M8 | **每条数据标粒度**：`cell` / `player` / `world` 三者不得混。`simTime` 属 world（不要复制进每格）；守卫属 cell；药水与权限属 player | 反例 5 处，最典型 `includeDebug`（格级 wire 形状取决于玩家权限）与 `lastBellStrikeAt`（cell 记时、player 受罚，规则对但未命名） | A 档 |

### 6.2 明确推迟（不要现在做）

传输协议（TCP/QUIC/自定义包）、兴趣管理/视野裁剪、客户端预测与回滚、权威迁移/主机迁移、**per-player 知识门控（ARG 的核心机制之一）**。
理由：这五件事全部可以在"事实流 + 快照"之上实现；**边界形状错了它们都要重写，边界形状对了它们都只是加一层**。

### 6.3 一个具体的收益

S2C 现在每格最多 31 次 `buf.write`、12+12 字段手写对称、5 处 `.ordinal` 上线、无版本号。改成"事实流 + 快照"后：加一个字段 = 加一个事实类型（自动可序列化）；版本不符 = 降级；`.ordinal` = 显式 tag。**多人和单人共用同一套事实，不再有两份真相。**

---

## 7. 实施分批

| 批 | 内容 | 工作量 | 判据 |
| :-- | :--- | ---: | :--- |
| **S0 定名**（本文档） | 词表 + 法则 + 禁止写法清单 | 本文档 | 团队认可（§8 三问拍板） |
| **S1 敲钟 v2a** ✅ | 三态门控、删 `SpikeSwallowed`、删相位文案/音画分支、记裁决⑯ | 0.5 天 | ✅ 已落地：`./gradlew test` 22 套 169 例全绿、客户端编译通过；进游戏三态音画验证**待做** |
| **S2 改名 A 档** ✅ | grep 禁止写法清单，改 `mandate/`+`overworld/`+`client/`+测试；`inner/` 中非存档字段的符号 + 三个文件改名 | 1–2 天 | ✅ 已落地 2026-11-16（见 §3.4），169 例全绿、契约未动 |
| **S3 原子化（第一阶段）** | ① 敲钟改命令+回执（删 `lastBellStrikeAt`/`simTime` 出边界）② 阈值进 `SemanticsProfile` ③ `dialTick`/`PhaseLabel` 下沉 ④ `CellReadout` 降级为**引擎内部/调试遥测**，wiring 逐步改消费事实 ⑤ 每条通道标粒度（cell/player/world） | 3–5 天 | `BellRitual` 不再接收 `CellReadout`；S2C 不含阈值与 `dialTick`；169 例绿 |
| **S4 改名 B/C 档** | 搭札记 11「后续工作 2」的契约窗口：`PhaseDetection` / `HeartbeatParam` 数值变更 + schema 4 + payload v2（**`MandateState` / `ODESolver` 改名已随 digest 移除归入 A 档**，S4 成本下降） | 2–4 天 | 契约重生成并逐行评审；旧档按声明丢弃 |
| **S5 v2b 回声** | `PulseOutcome` 观测窗（复用 `SpikeDetector`） | 1–2 天 | 敲钟后 3–10 s 收到 4 类回声之一；不影响任何判定 |

**顺序理由**：S1 让"敲钟"先自洽（用户新想法的直接落点）；S2 是纯改名、零行为变化、先把词稳住；S3 才动边界（有 S2 的词做地基）；S4 借已有的契约窗口，不额外开闸；S5 是叙事增强，可随时砍。

### 7.1 S3 施工单（开工照此执行）

**目标**：敲钟不再由边界外的纯函数读全局视图判定，改成 **世界发命令 → 引擎（状态所有者）判定 → 回一条事实**。现状是"全局单例供给 + 纯函数外表"：`BellStrike` 调 `MandateRuntime.readoutAt` 偷视图，而判定需要的 `lastBellStrikeAt`/`simTime` 因此被塞进跨界 DTO。

| # | 文件 | 动作 |
| :--: | :--- | :--- |
| 1 | `inner/action/ActionMsg.scala` | 新增出向事实 `BellStruck(cell, strikeId, outcome)` |
| 2 | `inner/event/EventMsg.scala` | 新增入向命令 `StrikeBell(cell)`（与 tick 同信箱全序） |
| 3 | `inner/veins/MandateAgent.scala` | 信箱内处理：判定 → 发射脉冲 → 记 c0 剂量 → 发 `BellStruck` |
| 4 | `mandate/BellRitual.scala` | 判定逻辑改由引擎调用，输入从 `CellReadout` 换成引擎侧状态 |
| 5 | `mandate/overworld/BellStrike.scala` | 改成"发命令 + 等回执"；粒子/音效/麻手/文案全部由回执驱动 |

连带：`MandateWording.formatBellPulse` 改签名；`MandateReadoutSpec` 的敲钟 4 例迁到新 spec。
**不在范围**：c0 库存机制、`CellReadout` 其余字段（S3b）、S2C 广播、存档 schema、`MandateAgent` 的 13 参签名（P2-1）、v2b 回声（S5）。

**不变量（破坏即失败）**：① 三态语义不变；② 脉冲无条件进方程、`Struck` 无条件锚 c0 + 注册源；③ 过密守卫是**格**的属性、麻手是**玩家**的属性；④ 命令与 tick 同信箱全序，同事件流 → 同轨迹；⑤ 169 例全绿 + `contracts/` 逐字节不变；⑥ **敲钟行为链 ≤ 3 文件**。

**Done**：`BellRitual` 不再接 `CellReadout`（`grep` 证明）；判定路径不再读 `lastBellStrikeAt`/`simTime`；新增"命令→回执（活/死/过密）+ 同一 `strikeId` 幂等"用例；`./gradlew test` + `runClient` 三态音画可辨。

**已知坑**：① `handle` 要同步返回 `ActionResult`，命令是异步的 → **返回 `PASS` 认领点击，反馈走回执，别同步等待**；② 复用现成的 `actionSink`，别扩 13 参签名；③ 敲钟用例住在 `inner/veins` 包里（本就错位），迁移注意 class 级 fixture；④ **c0 剂量必须在引擎做**（c0 是引擎状态），源注册登记留在 wiring——界线 = 是否需要引擎私有状态；⑤ 回执晚 1 tick（50 ms），可接受。

**开工前问用户的 2 件事**：① 回执粒度：只给敲钟者，还是广播给附近玩家？② 文案：保留三条，还是借机合并？

**提交切法**：① `feat(inner)` 接收命令并在 actor 内裁决 → ② `feat(mandate)` 回执驱动反馈 → ③ `test(inner)` 命令路径 + 幂等 + 三态 → ④ `docs` 更新本表。

---

## 8. 需要你拍板的 3 点（已按推荐值写成默认）

| # | 问题 | 推荐值 | 备选 |
| :--: | :--- | :--- | :--- |
| ① | 敲钟是否**无条件**锚定 `c0`（只留死亡/过密两拒）？ | **是**（v2a） | 保留"尖峰期不锚"，但改为**按状态**（如 `SpikeBegan` 后 2 s 内不锚），而非按相位 |
| ② | 「校拍/静息/失锚」是否保留为**事后回声**？ | **保留**（v2b），且不参与判定 | 彻底删掉这 3 个词，只留"叩钟/过密/无应" |
| ③ | 改名是否借 0.1 窗口**一次断档**（schema 4 + payload v2）？ | **是**（现在成本≈0） | 只做 A 档免费改名，B/C 推迟 |

---

## 9. 测试也是语义：三类锚与出处纪律

测试是事实上的规格。审计（2026-11-16，484 条断言）发现的问题是**锚错位**，不是断言写错：**只有 36 行（7%）标了出处**，所以红色无法区分"撞了回归"还是"撞了计划内变更"。

### 9.1 三类锚

| 锚 | 断言的是 | 改主意时该做什么 | 处置 |
| :--- | :--- | :--- | :--- |
| **意图锚** | 一条设计决定（能指到裁决编号 / 文档章节 / 公理） | 先改裁决，再改测试 | **必须标注裁决号**，如 `// ruling 16` |
| **实测锚** | 一次测量的数值（`nominalPeriod=39.47`、`settled_period`、duty 界） | 重新测量 → 重生成契约 | 标注 `measured: 出处` |
| **偶然锚** | 实现碰巧的样子（字段顺序、浮点末位、中间结构、某个本可自由重构的数值） | **应当可以自由改** | 能删就删；留则写明"为什么必须钉住" |

现状：意图 106 / 实测 24 / 主偶然 2，另有约 25 处夹带偶然成分。**心跳轴（`HeartbeatSpec` / `HeartbeatParamSpec`）是唯一做到"断言带出处 + 断言关系式而非字面量"的模块，应作为模板。**

### 9.2 已标记的"计划内矛盾"（改这些时测试红是**正常**的）

| 位置 | 锁住的东西 | 为什么是计划内的 |
| :--- | :--- | :--- |
| `MandateReadoutSpec:72/92` | `Regime.Burnout`(s≥0.95) 与相位域**不一致**（一格同时"s=1 饱和"和"在跳"） | 札记 11 §7 后续工作 3「regime 轴重审」尚未做 |
| `SliceContract` 全家（1016 行 = 测试代码 25%） | 数值快照（**已降级 2026-11-16**） | 源码 digest 移除 → `MandateState`/`ODESolver` 解锁，归入 A 档改名；角色改为内部回归快照。PRC sweep 是否移出 test 源集留作可选项：省 13.6 s / 减 25% 测试代码，但会失去 6 条机器可读的负知识，需先搬进文档 |
| ~~`PrototypeHotspotSpec`~~ | ~~"热点永不尖峰"~~ | **已修（2026-11-16）**：原来用旧标定（0.08 驱动 / 0.18 种子）反向守护；现重写为守住现行两端——开场的核**必须**在跳（0.349 > onset），外场**必须**静默 |

### 9.3 两个设计问题——已裁定（2026-11-16）

1. **格阵外（未模拟区块）= 一般区域 = 安全**。裁定原话：「取决于游戏规则；我的想法是：剧情沉滞 / 一般安全。」
   所以：沉滞只属于**剧情区域**（活跃 Node / 谷地，即格阵内），格阵外不施压。落地：
   - **代码不改**：`CellReadout.zero` 判 `Maintained` 本来就符合；
   - **改文档**：草案 01:148 的"场外判沉滞"是旧说法，已改为"一般区域安全、沉滞只属剧情区域"；
   - **改测试理由**：`MandatePhenomenaSpec:132-136` 的断言保留，但把理由从"Warming 宽限"（实现细节）改成"场外是普通区域，本就不施压"（设计裁定）。这条注释原本把实现巧合当成了设计依据——正是本次审计要找的那类漂移。
2. **`inner/` 允不允许中文：不规定**。裁定原话：「其实我并不怎么 care 这个。」
   原 §10 那条"任何时候 `inner/` 都不出现中文字符串"**撤回**——它已被 `MycelialHealth.word` 违反，且没有收益。保留的唯一相关约束是可查的那条："玩家长文案只写在 `MandateWording` / lang，不散进 `inner/`"。**被自己违反的规则比没有规则更糟：它训练所有人忽略文档。**

### 9.4 修法（约 1.5 人日，P0+P1 即可止漂）

| # | 动作 | 工作量 |
| :--: | :--- | ---: |
| P0-1 ✅ | 按现行场重写 `PrototypeHotspotSpec`（核必须跳 / 外场必须静默） | 已完成 |
| P0-2 ✅ | 9.3-1 已裁定：改文档 + 改测试理由，哨兵语义不变（`zero` → `Maintained` 保留） | 已完成 |
| P1-1 | 每个 `@Test` 加 `@Tag`（`intent`/`measured`/`incidental`）+ spec 头一行出处 | 0.5 天 |
| P1-2 | 给 9.2 的三类打 `@Tag("deferred")`，并在 `AGENTS.md` 一页图里加一句"`grep deferred` = 这些红是计划内的" | 2 h |
| P1-3 | `VoyConfigSpec` 补"非默认值往返 + key 集断言"（现测试抓不到漏写 writer） | 0.5 天 |
| P2 ✅ | 契约降级：移除源码 digest 锁、角色改为内部数值快照（重生成 1 行 diff） | 已完成（2026-11-16） |

---

## 10. 明确不做

- **不做通用事实总线 / ECS**：三类事实足够，别把边界做成框架。
- **不做协议版本协商握手**：只在事实里带 `semanticsVersion`，不符则降级。
- **不给 `PulseOutcome` 做持久化**：回声只活在会话内，不进存档（避免又一个 schema 字段）。
- **不规定 `inner/` 能不能出现中文**（2026-11-16 裁定）：不设 lint、不搬词表；唯一保留的是"玩家长文案只写在 `MandateWording` / lang"。
- **不动**：`contracts/`、`MandateState`/`ODESolver`（S4 之前）、`PhaseDetection` 常量值、回放边界、`@volatile` 单写多读。
