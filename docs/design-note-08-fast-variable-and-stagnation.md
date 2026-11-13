# 札记 08（已定稿）：心跳脉冲语义——快变量读数、三正交轴与极简脉冲短窗

> 状态：**已定稿落地**（2026-11-08 初版落地；**2026-11-13 重构精简为三正交读数轴 + `PulseStamp` 极简短窗/冷却**）。
>
> **2026-11-13 机制精简说明**：
> - 移除早期的脉冲耐受度衰减账本（`ToleranceBook`）、世界侧急性锁闭窗（`OverloadLock`）与读数层叠加脉冲伪偏移（`Readout.effectiveA`），精简为双时间戳 `PulseStamp(shelterUntil, cooldownUntil)` 与三字段 `PulseParam(shelterWindow = 40.0, minInterval = 8.0, overloadPenaltyTicks = 120)`；存档 `schemaVersion` 升至 `2`。
> - 读数层（`CellReadout`）解耦为三条正交轴：**动力学轴**（`regime`、`phase`、`isSpiking`、`isSlowOffsetBelowKnee`）、**菌网健康轴**（`mycelium: MycelialHealth ∈ {Intact, Damaged, Dead}`、`isToxicSoil`）与**庇护轴**（`isSheltered`、`isBaselineAnchored`）。
> - 无周期原因 `UndefinedReason` 回归纯动力学：`SlowOffsetBelowKnee`（`a(s, M) <= burnoutA`）、`TransientNoCycle`、`NoCycleReference`。

---

## 0. 一句话

地脉是活的：**一次脉冲（敲钟）是一拍心跳**——来一拍，开启固定时长（默认 40 s）的脉动庇护窗，`沉滞` 短期退去；太久没有脉冲，沉滞重新漫上来；**脉冲间隔小于安全冷却窗（默认 8 s）会被拒绝并即刻反噬敲钟者**（施加 `voy:pulse_saturation`，不刷新庇护窗）。`c0` 与慢变量通道维持不变，只负责慢变量安抚（`-ρc₀`）与感官签名庇护。

---

## 1. 核心裁定

| 维度 | 现行口径 |
|---|---|
| 快变量与方程 | **不动四维 RK4 方程与契约数值 oracle**：尖峰（spike）统一采用**正向**除极暴发判据（`v ≥ +1.8`）；黄水渗色（`MandatePhenomena.classifySeepTier`）保留 `|v|` 判据，对应 `dq/dt` 的酸负荷方程。 |
| 三条正交读数轴 | 1. **动力学轴**：`regime`（`Calm / Strained / Oscillating / Burnout`）、`phase`（`Cycle / Basin / Undefined`）、`isSpiking`、`isSlowOffsetBelowKnee`（`a(s, M) <= -0.4667`）；<br>2. **菌网轴**：`MycelialHealth` 三档（`Intact`：`M ≥ 0.60`；`Damaged`：`floor ≤ M < 0.60`；`Dead`：`M < floor`）与 `isToxicSoil`（`s ≥ 0.60`）；<br>3. **庇护轴**：`isSheltered = isPulseSheltered && mycelium.isViable && !isSlowOffsetBelowKnee && !isSpiking`。 |
| 沉滞主来源 | **脉动庇护窗（`PulseStamp`）**：一次有效正脉冲开启 `[t, t + shelterWindow]`（默认 40.0 s）庇护窗与 `[t, t + minInterval]`（默认 8.0 s）冷却窗；窗外失去庇护，原版经济受沉滞压制。 |
| `c0` 的角色 | **沉滞与 `c0` 解耦**：`c0` 只进慢变量方程（`-ρc0` 项）与菌光/前兆签名（`isBaselineAnchored`）；不再提供沉滞免疫。 |
| 存档兼容 | `MandatePersistence.SchemaVersion = 2`：每格仅持久化 `shelterUntil` 与 `cooldownUntil` 两个 `Double`（旧 v1 存档加载时打印 `WARN` 日志并重置为初始态）。 |

---

## 2. 核心机制：极简脉冲印记（`PulseStamp`）

### 2.1 脉冲庇护窗与冷却窗

- 一次**正面积记账脉冲**（`FirePulse(area > 0, bookkeeping = Counted)`：敲钟、水晶链路）到达活细胞（`mycelium.isViable && !isSlowOffsetBelowKnee`）时，由 `PulseLedger.settle` 结算：
  - **正常结算（`PulseSettlement.Sheltered`）**：当 `simTime >= cooldownUntil` 时，更新 `PulseStamp(shelterUntil = t + shelterWindow, cooldownUntil = t + minInterval)`；
  - **冷却拒绝（`PulseSettlement.CooldownRefused`）**：当 `simTime < cooldownUntil` 时，**保持原 `PulseStamp` 不变**（不延长、也不抹除已有庇护窗），敲钟者即刻承受 `voy:pulse_saturation`（默认 120 tick：挖掘 ×0.75、移速 ×0.85）+ 低闷失谐钟声 + 灰烬粒子。
- 庇护窗内 `沉滞`（`voy:stagnation`）主动移除；窗外每 20 tick 重新评估。`CellReadout.isSheltered` 的定义（[MandateReadout.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateReadout.scala)）：

  ```scala
  def isSheltered: Boolean =
    isPulseSheltered && mycelium.isViable && !isSlowOffsetBelowKnee && !isSpiking
  ```

### 2.2 边界与克制

- **负脉冲不记账**：`area ≤ 0`（如 `OverwhelmingNegativeSuppression`）不参与脉冲印记；
- **死菌或越膝不记账**：`!mycelium.isViable` 或 `isSlowOffsetBelowKnee` 的细胞跳过脉冲印记；
- **尖峰吞没不记账**：`SpikeSwallowed` 的一击照常把脉冲送进方程（`v/q/s` 仍记账），但置 `FirePulse.bookkeeping = PhysicsOnly`，不更新 `PulseStamp`；
- **通道物理不变**：钟永远发声、山永远记账（`FirePulse` 照常进方程，连敲脉冲同样进入突触核与 `q`/`s` 积分）。

---

## 3. 敲钟分派表（`evaluateBellStrike`，定稿）

按序判定，先到先得（[BellRitual.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/BellRitual.scala)）：

| # | 条件 | Outcome | 效果 |
|---|---|---|---|
| 1 | 慢性菌竭或越膝（`!readout.mycelium.isViable \|\| readout.isSlowOffsetBelowKnee`） | `DeadMyceliumBurnout` | 不锚 `c0`、不记账、不罚——死肉无回音；指路泄压+接种 |
| 2 | 冷却窗内过密敲击（`readout.pulseStamp.isInCooldownAt(readout.simTime)`） | `PulseOverloadBurnout` | 不锚 `c0`、不刷新印记、玩家吃 `voy:pulse_saturation` |
| 3 | `Regime.Burnout`（`s ≥ 0.95` 但菌网尚存） | `SaturatedNoAnchor` | 不锚 `c0`，**但正常开启庇护窗**（敲了能缓口气但治不好饱和应力） |
| 4 | 未锚振荡 + `Cycle(Excitation)` | `SpikeSwallowed` | 尖峰吞没：不锚 `c0`、`bookkeeping = PhysicsOnly`（脉冲进方程，不更新 `PulseStamp`） |
| 5 | 未锚振荡 + `Cycle(Absolute/RelativeRefractory)` | `RefractoryReanchored` | 锚 `c0` + 开启庇护窗 + 推迟下一次尖峰 |
| 6 | 其余（Calm/Strained/静息域/瞬态/无周期参照） | `AnchoredOrMaintained` | 锚 `c0` + 开启庇护窗 |

---

## 4. 配置（`config/variations-of-yesterday.json` 的 `pulseParam` 段）

| 键 | 默认 | 含义 |
|---|---|---|
| `shelterWindow` | 40.0 | 单次有效脉冲开启的庇护窗时长（模拟秒，锚定一个自然振荡周期 ≈39.5 s） |
| `minInterval` | 8.0 | 脉冲最小冷却间隔（模拟秒，≈1.6τ_bell） |
| `overloadPenaltyTicks` | 120 | 冷却期内强行敲钟施加 `voy:pulse_saturation` 的持续游戏刻数 |

---

## 5. 代码落点

- [PulseLedger.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/PulseLedger.scala) —— `PulseParam`、`PulseStamp(shelterUntil, cooldownUntil)`、`PulseSettlement` 与 `PulseLedger.settle`；
- [MandateReadout.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateReadout.scala) —— `MycelialHealth`（`Intact / Damaged / Dead`）、`UndefinedReason`（`SlowOffsetBelowKnee / TransientNoCycle / NoCycleReference`）、三正交轴 `CellReadout` 与纯数学 `PhaseTracker`；
- [MandateAgent.scala](../src/main/scala/moe/chestnut/awa/voy/inner/veins/MandateAgent.scala) —— 单槽位 `CellSlot` 统一管理每格运行时簿记；
- [MandatePhenomena.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/MandatePhenomena.scala) & [BellRitual.scala](../src/main/scala/moe/chestnut/awa/voy/mandate/BellRitual.scala) —— 感官签名、经济压力档、菌塘生长与敲钟六裁决全部映射在三条正交轴上。
