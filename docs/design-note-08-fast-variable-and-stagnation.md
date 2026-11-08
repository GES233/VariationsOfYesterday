# 札记 08（已定稿）：心跳脉冲语义——快变量读数与「沉滞」来源重构

> 状态：**已拍板并落地**（2026-11-08，分支 `refactor/fast-var-stagnation`）。
> 本文前身为同日的对齐备忘（罗列代码现状与 G1–G15 缺口清单、Q1–Q6 待拍板项）；
> 经 2026-11-07–08 两轮「心跳语义」讨论，分叉已裁定，机制已实施。
> 本文记录**定案后的机制口径与落地事实**；重构前的旧行为仅以对照方式保留。

---

## 0. 一句话

地脉是活的：**一次脉冲（敲钟）是一拍心跳**——来一拍，`沉滞` 短期退去；
太久没有脉冲，沉滞重新漫上来；**脉冲太密会把地脉顶进急性烧毁**
（`BurnoutNoCycle`，去极化阻滞），即刻反噬玩家并加深耐受。
`c0` 与慢变量通道维持不变，「烧毁」意象不变，但拆成**慢性（菌竭）**与**急性（过载）**两条路径。

---

## 1. 裁定结果（对应旧备忘的 Q1–Q6）

| 旧问题 | 裁定 |
|---|---|
| Q1 快变量重写幅度 | **(a) 不动方程**：四维 RK4 与契约哈希锚（`MandateState.scala` / `ODESolver.scala`）零改动；只重写读数与玩家侧语义。契约 `mandate-slice-v1.json` 未重生成，`SliceContractSpec` 全程通过。 |
| Q2 `v` 的叙事身份 | **(c) 分层**：`v` 是菌网生物电（电生理量），`Regime`/`PhaseReading` 是叙事读数。尖峰（spike）= **正向**除极暴发（`v ≥ +1.8`）。 |
| Q3 沉滞主来源 | **脉冲保鲜窗（心跳）**：沉滞的基础来源从「无 `c0`」改为「**太久没有脉冲**」。一次有效脉冲开启抵消窗 `W_eff(T)`，窗过期沉滞回归；`c0` 退居慢变量安抚通道与感官签名的庇护条件。 |
| Q4 `c0` 的角色 | **(c) 沉滞与 `c0` 解耦**：`c0` 只进慢变量方程（`-ρc0` 项）与菌光/前兆签名（见 §5）；不再提供沉滞免疫。 |
| Q5 两个 `Burnout` | **(a) 语义分层，不改枚举名**（不破跨栈词汇）：`Regime.Burnout`（s ≥ 0.95）= 饱和失能，仍可振荡、仍可被脉冲短期压住沉滞；`BurnoutNoCycle`（`a_eff ≤ a_burnout`）= 去极化阻滞，分**慢性（菌竭）**与**急性（过载）**两条路径，共用同一膝点判据。 |
| Q6 兼容与重放 | 存档 `schemaVersion` 不变（=1）：新增字段（脉冲耐受四元组）带向后兼容读取（缺省即零态）。不改 `Regime`/`PhaseReading` 枚举名，Elixir 侧 `Voy.World.Profile` 不动。 |

旧备忘的 G5/G10（`|v|` 尖峰判据）随本次**一并修复**：`Excitation` 标签、`CellSpikeTriggered`
事件、尖峰档沉滞与 HUD 着色统一改为**正向**判据 `v ≥ spikeLevel`；负向复极过冲
（实测 `v_min ≈ −1.9`）不再被误读为尖峰。黄水渗色（`MandatePhenomena.classifySeepTier`）
保留 `|v|` 判据，因为它对应 `dq/dt` 的酸负荷方程（同样按 `|v|` 计）。

---

## 2. 核心机制：心跳三律

### 2.1 脉冲抵消窗（保鲜）

- 一次**正面积脉冲**（`FirePulse(area > 0)`：敲钟、水晶链路）到达活细胞时，开启
  沉滞抵消窗：`suppressedUntil = t + W_eff(T)`，其中
  `W_eff(T) = W_0 · max(0, 1 − T/T_invert)`；
- 窗内 `沉滞`（`voy:stagnation`）**主动移除**（不再等 55 tick 自然衰减）；窗外每 20 tick
  重新施加。`isMaintained` 的新定义（[MandateReadout.scala](../src/main/scala/moe/chestnut/awa/voy/inner/mandate/MandateReadout.scala)）：

  ```
  isMaintained = pulseSuppressed && sustenance ≥ floor && phase ≠ BurnoutNoCycle && v < spikeLevel
  ```

### 2.2 太快 = 过载烧毁（急性 BurnoutNoCycle）

脉冲间隔 `< I_min`（默认 8.0 模拟秒，≈1.6× 钟核 τ=5.0）或耐受饱和（`T ≥ T_invert`）时：
1. **世界侧**：该细胞进入急性 `BurnoutNoCycle` 锁闭 `overloadBurnoutDuration`（默认 8 s），
   抵消窗作废；
2. **玩家侧即刻小惩罚**：敲钟者获得 `voy:pulse_saturation`（脉冲过载，默认 120 tick：
   挖掘 ×0.75、移速 ×0.85）+ 低闷失谐钟声 + 灰烬粒子；
3. **后续更耐受**：`T ← T + ΔT_over`（默认 +0.35，远大于正常敲钟的 +0.10），
   此后每次脉冲的抵消窗按 `1 − T/T_invert` 缩短，直到停手等 `T` 指数衰减
   （`τ_tol` = 45 s，锚定弛豫周期尺度）。

### 2.3 两条烧毁路径共用同一膝点

读数层把突触核的脉冲驱动纳入有效恢复偏移（[MandateReadout.scala](../src/main/scala/moe/chestnut/awa/voy/inner/mandate/MandateReadout.scala) `Readout.effectiveA`）：

```
a_slow = α − Δ(1−M) − k·s                      （慢路径：菌竭烧毁）
a_eff  = a_slow − gain · max(p − p_single, 0)   （快路径：过载烧毁，p = impulse + synapseP）
BurnoutNoCycle ⟺ a_eff ≤ a_burnout = −0.4667  或急性锁闭窗口内
```

- **慢性菌竭**：高应力酸蚀耗尽 `M`（~0.6 h 越膝、~4.4 h 灭绝，`M = 0` 吸收态）——
  大死区实质永久，恢复只能泄压 + 再接种；敲钟**毫无回音**（`DeadMyceliumBurnout`）。
- **急性过载**：连发脉冲把残余核 `p` 叠过单钟峰值（`p_single = 12/(e·5) ≈ 0.883`），
  把活地脉瞬时顶穿膝点——土地越差（`M` 低、`s` 高，`a_slow` 越小），越经不起快敲。
- **联动**：健康沃土（`a_slow = 0.70`）连敲两三下也顶不穿；受损地（`a_slow` 已逼近膝点）
  稍快即过载——**土地状态决定敲钟的安全节奏**。

### 2.4 边界与克制

- **负脉冲不记账**：`area ≤ 0`（如 `OverwhelmingNegativeSuppression`）不参与心跳簿记；
- **死菌不吃脉冲**：`M ≤ floor` 或 `a_slow ≤ a_burnout` 的细胞跳过耐受簿记
  （死亡不是玩家的错，不叠耐受）；
- **水晶链路也心跳**：链路转发的正脉冲同样开启抵消窗（周期 ~39.5 s 的锁相链路
  恰好能自动维持一个区块的庇护——自动化心跳是涌现玩法）；
- **通道物理不变**：钟永远发声、山永远记账（`FirePulse` 照常进方程，
  过载脉冲同样喂养 `q`/`s` 的压力账）；
- **账本随存档**：脉冲耐受四元组（`lastPulseTime / suppressedUntil / overloadUntil /
  tolerance`）随 `MandateSnapshot` 整格落盘，以 `simTime` 闭式求值，重登/重启连续。

---

## 3. 敲钟分派表（`evaluateBellStrike`，定稿）

按序判定，先到先得（[MandateOverworld.scala](../src/main/scala/moe/chestnut/awa/voy/MandateOverworld.scala)）：

| # | 条件 | Outcome | 效果 |
|---|---|---|---|
| 1 | 慢性菌竭（`M` 死透或 `a_slow ≤ a_burnout`） | `DeadMyceliumBurnout` | 不锚 `c0`、不抵消、不罚——死肉无回音；指路泄压+接种 |
| 2 | 脉冲过密 / 耐受饱和 / 已在急性锁闭 / 相读 `BurnoutNoCycle` | `PulseOverloadBurnout` | 不锚 `c0`、抵消窗作废、`T+ΔT_over`、玩家吃 `voy:pulse_saturation` |
| 3 | `Regime.Burnout`（s ≥ 0.95 但菌网活着） | `SaturatedNoAnchor` | 不锚 `c0`，**但正常开启抵消窗**（敲了有用但治不好） |
| 4 | 未锚振荡 + `Cycle(Excitation)` | `SpikeSwallowed` | 尖峰吞没：不锚 `c0`、不抵消 |
| 5 | 未锚振荡 + `Cycle(Absolute/RelativeRefractory)` | `RefractoryReanchored` | 锚 `c0` + 抵消窗 + 推迟下一次尖峰 |
| 6 | 其余（Calm/Strained/静息域/瞬态/无周期参照） | `AnchoredOrMaintained` | 锚 `c0` + 抵消窗（宽容：起振初期不设防） |

---

## 4. 配置（`config/variations-of-yesterday.json` 新增 `pulseParam` 段）

| 键 | 默认 | 含义 |
|---|---|---|
| `suppressionWindow` | 40.0 | `W_0`，锚定一个自然振荡周期（≈39.5 s） |
| `minSafeInterval` | 8.0 | `I_min`，脉冲最小安全间隔（≈1.6τ_bell） |
| `toleranceDecayTau` | 45.0 | 耐受指数衰减常数（模拟秒） |
| `normalToleranceStep` | 0.10 | 正常脉冲的耐受增量 |
| `overpulseToleranceStep` | 0.35 | 过载脉冲的耐受增量 |
| `invertTolerance` | 1.0 | 耐受反转阈值（抵消效力归零） |
| `maxTolerance` | 1.5 | 耐受硬上限（保证衰减有界） |
| `overloadBurnoutDuration` | 8.0 | 急性 `BurnoutNoCycle` 锁闭时长 |
| `overloadPenaltyTicks` | 120 | `voy:pulse_saturation` 持续 tick 数 |
| `singlePulsePeak` | 0.8829 | 单钟核峰值 `12/(e·5)`，过载增益的起扣点 |
| `pulseBurnoutGain` | 1.6 | 超出部分的 `a_eff` 下压增益 |

---

## 5. 落地修改面（2026-11-08 实施事实）

- **改（机制核，读数层）**：[MandateReadout.scala](../src/main/scala/moe/chestnut/awa/voy/inner/mandate/MandateReadout.scala)
  —— `Readout.slowA` / `effectiveA` / `isEcologicalBurnout` 新增；`label` 与 `isMaintained`
  改正向尖峰判据；`CellReadout` 增加 `pulseDrive / pulseSuppressed / pulseTolerance /
  pulseState / simTime`；`PhaseTracker` 接受 `pulseDrive` 与 `overloaded` 探针。
  [MandateAgent.scala](../src/main/scala/moe/chestnut/awa/voy/inner/mandate/MandateAgent.scala)
  —— 正脉冲簿记（`recordPulse`）、尖峰事件改正向判据、读数投影携带心跳状态。
  [MandateSnapshot.scala](../src/main/scala/moe/chestnut/awa/voy/inner/mandate/MandateSnapshot.scala)
  / [MandatePersistence.scala](../src/main/scala/moe/chestnut/awa/voy/MandatePersistence.scala)
  —— 耐受四元组随格落盘（缺省读取，schemaVersion 不变）。
- **改（游戏接线）**：[MandateOverworld.scala](../src/main/scala/moe/chestnut/awa/voy/MandateOverworld.scala)
  —— 六分派 `evaluateBellStrike`、过载即刻反噬（效果 + 失谐钟声 + 灰烬）、
  窗内主动移除沉滞、文案与验山读数改「脉动庇护」口径；
  [MandateEffects.scala](../src/main/scala/moe/chestnut/awa/voy/MandateEffects.scala)
  —— 新增 `voy:pulse_saturation`；lang 双语同步（键集一致性由
  `MandatePhenomenaSpec` 断言）。
- **增**：[PulseTolerance.scala](../src/main/scala/moe/chestnut/awa/voy/inner/mandate/PulseTolerance.scala)
  （纯函数耐受簿记）+ `PulseToleranceSpec`；`MandateReadoutSpec` / `MandateAgentSpec`
  增加心跳/过载/死菌三组行为测试。
- **感官签名口径微调**：[MandatePhenomena.scala](../src/main/scala/moe/chestnut/awa/voy/MandatePhenomena.scala)
  的菌光与前兆孢子改绑 **`c0` 庇护**（`baselineCalming ≥ 0.01`）而非 `isMaintained`——
  心跳管玩家的身体，签名管土地自己的健康；札记 06 §3 签名表因此不破。
- **不动**：`MandateState.scala`、`ODESolver.scala`（契约哈希锚）、
  `contracts/mandate-slice-v1.json`、`tools/voy_world` 全部、`Regime`/`PhaseReading` 枚举线级名。

---

## 6. 仍开放（后续排期）

1. **心跳投影层**（音频节奏 / 菌丝地面亮度脉搏波）未实施，属表现层另排；
2. 耐受数值（`W_0`、`I_min`、步进与衰减）为首批手感值，待 playtest 校准；
3. 「敲钟近处心跳重一拍」的世界侧代价反馈（近处重拍音效）待做；
4. 水晶链路自动心跳与 `minSafeInterval` 的交互（锁相周期 ≈ 39.5 s > `I_min` = 8 s，
   现参数下单链路安全；多链路汇聚到同格的过密风险是涌现特性，保留观察）。
