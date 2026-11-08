# 验证报告 01 — 0.1 纵切片的最短可验证闭环

> 状态：**历史快照**（0.1 冻结期实测，未含 M / 持久化 / 外部配置）；数值仍与当期契约逐条一致，后续机制以草案 01 为准。
>
> 对象：单节点 `branch_01_baishui`（上游 `up` / 隘口 `pass` / 下游 `down`）、一个 `p` 通道脉冲事件。
> 复现命令：
> ```sh
> ./gradlew test                   # Scala 侧契约与动力学测试
> cd tools/voy_world && mix test   # Elixir 侧关系、溯因与内容包测试
> ```

---

## A. 核心命题与候选空间

**核心命题**：在纵切片单个 Node 上，由 `(c0_state, pulse_kind, safe_phase_window)` 决定的隐藏解释，会在受控脉冲实验测得的相位响应表上留下可区分的数值指纹；三方机构（总指挥部、技术内层、老寨）经各自的观测、量化与行政规则变换后保留的记录，足以在 24 候选空间内唯一反演该解释，并由 Mandate 数值层结算验证。

| 项目 | 定义 |
| :--- | :--- |
| **参数与初值** | `alpha=0.7, beta=0.8, epsilon=0.08`；`StressParam()` 默认；初值 `(v, w, s, q) = (-1.2, -0.6, 0, 0)` |
| **候选空间 $H$** | `c0_state ∈ {c0_intact, c0_collapsed}` × `pulse_kind ∈ {alpha_shock, heavy_kick, alpha_metal_resonance, OverwhelmingNegativeSuppression}` × `safe_phase_window ∈ {absolute[0.20,0.30], relative[0.40,0.60], late[0.75,1.00]}` = **24 个离散候选** |
| **证据集合 $E$** | `official_hq{water_level, surge_count, rain_total, material, quantity, cause_tag}`、`tech_inner{impact_count, amplitude, surge_lag, surge_direction, rain_total, material}`、`old_zhai{sign_order, sign_sequence, magnitude, surge_lag, count_of_blows}` |
| **真实尖峰判据（相位传感层）** | $v$ 上穿截面 $1.2$ **且**峰值 $\ge 1.8$ 后回落穿过 $0$（排除仅把 $v$ 顶过 $1.2$ 而未激发动作电位的过阈伪影；读数层尖峰为正向 $v \ge +1.8$，见札记 08） |

### 相位定义域（`MandateReadout` 五态区分）

| 状态 | 判据 | 返回值 |
| :--- | :--- | :--- |
| **周期轨道** | $a_{\text{burnout}} < a(s) < a_c$，且已观测 $\ge 3$ 次过阈、相邻间隔偏差 $\le 20\%$ | `PhaseReading.Cycle(label, fraction)` |
| **静息吸引域** | $a(s) \ge a_c$ 且 $|v-v^*|\le 0.25, |w-w^*|\le 0.25$、且未记录过截面穿越 | `PhaseReading.Basin(Resting)`（无周期分数） |
| **强瞬态** | 单次激发或未稳定成周期 | `PhaseReading.Undefined(TransientNoCycle)` |
| **无周期参照** | 尚未积累到周期参照（刚注册 / 刚唤醒等） | `PhaseReading.Undefined(NoCycleReference)` |
| **方程级烧毁** | $a(s) \le a_{\text{burnout}} = -0.46667$ | `PhaseReading.Undefined(BurnoutNoCycle)` |

> 表内 $a(s)$ 为当期口径；现行代码为 $a(s, M)$（`sustenance`，$M=1$ 时与上表等价）。

---

## B. 竞争假设与区分实验

针对「点炮后下游出现强烈地鸣与水体翻涌」的五种竞争假设检验：

| # | 假设 | 实验结果 |
| :--- | :--- | :--- |
| **C1** | 饱和工作点（$s=1.0, a=-0.30$）下 40%–60% 窗内 `alpha_shock` 当场激发提前尖峰 | **证伪**。在 $a=-0.30$ 下 `alpha_shock` 全域 0 次点火；$\varphi=0.50$ 处实为**强推迟**（`+15.62`） |
| **C2** | 事件落点坐标系差异导致表观提前 | 排除。不同参考系下的真实下一次尖峰时刻一致 |
| **C3** | **过阈伪影**：矩形/脉冲把 $v$ 顶过 $1.2$ 后回落，被裸截面检测器误计为尖峰 | **证实**。这是早期测试假阳性的成因（过阈 2 次，真尖峰仅 1 次） |
| **C4** | 积分步长伪影 | 排除。`dt ∈ {0.02, 0.01, 0.005}` 下周期为 `39.47 / 39.4725 / 39.47375`（相对散布 `9.5e-5`） |
| **C5** | 参考快照采样点偏移 | 排除。周期偏差 `< 0.03%` |

---

## C. 相位响应（PRC）实测与占空比反转

### C.1 受控脉冲响应表（饱和工作点 $a = -0.30, s = 1.0, T = 39.47$）

| 脉冲类型 | 剂量 `(area, τ)` | 点火相位域 | 推迟相位域 | 三窗中点响应（绝对 `0.25` / 相对 `0.50` / 晚期 `0.875`） |
| :--- | :--- | :--- | :--- | :--- |
| `alpha_shock` | `12, 5` | 无 | `0.075–0.575` | `+6.62` / **`+15.62`** / `-2.40`（Absorbed） |
| `heavy_kick` | `5, 10` | 无 | `0.025–0.500` | `+7.58` / `+18.68` / `-0.72`（Absorbed） |
| `alpha_metal_resonance` | `2×3, 2` | `0.40, 0.55, 0.575` | `0.325–0.600` | `+0.20` / `+9.12` / `-2.40` |
| `OverwhelmingNegativeSuppression` | `-25, 5` | `0.025–0.175` | `0.550–0.975` | `-9.08` / `+0.86` / `+12.14` |

### C.2 定量结论
1. **占空比反转（Duty-Cycle Inversion）**：
   - 在**近临界起振区（$s = 0.30, a = 0.40, T = 43.715$）**，去极化高台期（$v > 0$）占周期的 **28.06%**（`12.27` 刻），$\varphi = 0.50$ 落在左支超极化恢复段（$v = -1.619$），`alpha_shock` 当场触发提前尖峰（`Premature, delta = -17.38`）。
   - 在**饱和过驱动区（$s = 1.0, a = -0.30, T = 39.47$）**，去极化高台期膨胀至 **60.57%**（`23.91` 刻），$\varphi = 0.50$ 仍处上支高台期（$v = +0.911$），正向脉冲产生平台期阻滞延长（`Delayed, delta = +15.62`），该点暴力点火面积阈值达 `14.84 > 12`。
2. **实测安全刻度**：`PhaseResponse.tickTrials` 对 `alpha_metal_resonance` 逐整数 tick 实测，仅将 `Delayed` 结果写入契约 `safe_ticks`，供 `Witness` 绑定。
3. **有向平流链不可简单线性叠相**：3 格链（`advection = 0.001`）上下游周期存在微小频差（`28.31` vs `28.92`），观测窗口内滞后漂移达 `22.57` 个周期，`ChainObservation.composable == false`。

---

## D. 溯因唯一性与极小充分证据集（`Voy.World.Ascribe`）

`Ascribe.explain/2` 基于 Kanren 关系目标 `abduceo/4` 返回四种互斥结果：`{:unique, h}`、`{:multiple, ...}`、`{:none, ...}`、`{:incomplete, ...}`。

1. **24 / 24 唯一反演**：在默认仪器读表分辨率 `0.25` 下，$H$ 中全部 24 个候选均唯一还原真值；当分辨率放宽至 `1.0` 时，绝对窗下 `alpha_shock` 与 `heavy_kick`（间隔仅差 `0.96`）诚实退化为 `:multiple`。
2. **单点不可替代核 vs 极小充分集**：
   - `Ascribe.minimal_chain/2` 求取所有极小充分集的交集 $\bigcap \mathcal{S}$（单删即失唯一性的证据项）；
   - `Ascribe.minimal_sufficient_sets/2` 求取所有自身足以唯一还原真值的极小子集 $\mathcal{S}$；例如在晚期窗 `alpha_shock` vs `alpha_metal_resonance` 对撞案例上，极小充分集横跨三方机构：`{official_hq/water_level, tech_inner/surge_lag, old_zhai/count_of_blows}`。
3. **替罪羊排除**：官方通报因 `official_tabooo/1`（`neq` 禁忌）强制归因于 `:seasonal_rain_mudslide`，但三方记录中的 `impact_count`、`sign_order`、`amplitude` 任一项均可将「秋雨自然侵蚀」证伪排除。

---

## E. 三方制度观测变换与现实考据

| 维度 | OfficialHQ（总指挥部） | TechInner（技术内层） | OldZhai（老寨） |
| :--- | :--- | :--- | :--- |
| **观测通道** | 月度水位桶、雨量汇总、火工品领料单 | 铅锤撞框计数、滞后读表（分辨率 `0.25`）、作业票 | 物候四相序列、山应与雨的先后、击打次数 |
| **信息失真** | 水位削顶分桶；成因栏按行政规则填秋雨滑坡 | 幅度撞框削顶（`:frame_slam`），丢失慢变量 $s$ | 无量值、无日期 |
| **保留的不可替代线索** | 慢变量粗分区（区分 $c_0$ 存亡的冗余见证） | **脉冲滞后量与方向**（定窗口的唯一通道） | **山应与雨的先后**（排除秋雨）、**敲击次数**（区分三叩共振） |

**现实记录结构参考（仅取仪器与台账结构，无现实指涉）**：
- 自记雨量计故障迹线结构：[降水迹线异常分析](http://qxkjjz.cn/cn/article/pdf/preview/10.3969/j.issn.2095-1973.2019.03.003.pdf)、[降水现象与自记降水量不一致](http://qxqk.nmc.cn/qx/ch/reader/create_pdf.aspx?file_no=19950114&st=alljournals#1#1#1)
- 火工品领退台账与管理规定：[火工品支领、清退制度](http://p.safehoo.com/Manage/System/Fire/201504/389489.shtml)、[爆破器材管理规定摘录](http://www.dcblast.com/UpLoadfiles/upLoadImages/200710221011019167.pdf#6#15#6)
