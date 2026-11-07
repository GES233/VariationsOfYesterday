# 设计札记 02 — 关系世界模型：四纪编译、地形组合与六结局

> 状态：**现行架构说明**。原札记 04（可达性解耦与六结局）已并入本文 §3.1 与 §5；五跳局部卷宗见札记 05。

## 1. 神经符号三层架构

模组将连续动力学模拟与离散符号推理严格分层，避免在 ODE 内硬编码叙事分支，也避免符号层脱离物理真值：

- **底层（连续数值 / Scala）**：`MandateState` 与 `MandateGrid` 执行四维 RK4 积分与算子分裂平流扩散，零叙事规则侵入。
- **中间层（语义读数与契约 / Scala ↔ JSON）**：`MandateReadout` 将连续状态 $(v, w, s, q)$ 投影为慢变量分级 `Regime`（`Calm` / `Strained` / `Oscillating` / `Burnout`）与快变量相位 `PhaseReading`；`SliceContract` 将精确 Hopf 点、占空比反转与逐整数 tick 实测脉冲响应固化为 `contracts/mandate-slice-v1.json`。
- **上层（关系与证据引擎 / Elixir `tools/voy_world`）**：以 microKanren（`Voy.Kanren`）为核心，提供：
  1. **开放属性表匹配（`propso/2`）**：实体表示为键值表，规则只约束相关属性子集，其余风味字段自动透传；
  2. **不等约束（`neq/2`）**：在合一状态中维护不等式防火墙（如行政通报禁忌机械/巫术归因，被迫合一到秋雨替罪羊）；
  3. **双向求值**：同一份关系既可由地形正向编译历史，也可由结局约束反向求解合法地形候选。

### 创作纪律：藏骨于皮
关系模型借用了「静态底盘（地势）+ 动态游标（相位）+ 局部切面（器物/物候）+ 多跳传导」的结构同构，但**游戏内文本、UI 与代码命名绝不出现术数或修仙名词**，全部转译为 **1980 年代深山水文地质工程** 与 **虚构山地古谚**。

---

## 2. 四纪历史编译器（`Voy.World.Chronicle`）

针对种子动态地形下「历史链与遗迹落位必须严密自洽」的需求，`Chronicle` 将支沟历史编码为四段 Kanren 关系管线：

| 阶段 | 输入锚点 | 核心关系与物理/制度分支 | 规则谓词 |
| :--- | :--- | :--- | :--- |
| **地理骨相** | 5 项离散特征 | 上游地形（陡裂隙/落水洞）、隘口（峡谷/溶洞）、下游（平坝/台地）、地质（活动断层/深应力腔/微裂隙）、可达性（机耕道/绝壁） | `Schema.topology/1` |
| **第一纪：山官火塘** | `topo` | 隘口决定镇山古器：峡谷立**镇山铜钲**（声学阻尼），溶洞立**石雕分水堰**（虹吸泄压）；初始维持 `c0_intact` 与 `Calm` | `era1_headmano/2` |
| **第二纪上：流官清丈** | `topo + era1` | 按可达性分叉：<br>• **机耕道可达**：清丈局收铜钲或毁石堰 $\rightarrow$ 触发山崩掩署或暗河倒灌反噬 $\rightarrow$ 发还古器、退守「外甲内寨」；<br>• **绝壁隔绝**：胥吏止步崖下，仅立归化碑虚报具结 | `era2a_magistrateo/3` |
| **第二纪下：垦殖改组** | `topo + era1 + era2a` | • **可达性 $\rightarrow c_0$**：通车则改组为国营垦殖分队、古器按废料收缴（`c0_collapsed`）、山官后裔贬为保管员；绝壁则挂靠林场、古器留存原地（`c0_intact`）；<br>• **源头×地质 $\rightarrow$ 扰动**：陡裂隙+活动断层进驻 302 爆破队（`alpha_shock`）；落水洞+深应力腔进驻高压注浆处（`OverwhelmingNegativeSuppression`）；陡裂隙+微裂隙设林场水文哨（`heavy_kick`） | `era2b_reclamationo/4` |
| **第三纪：撤场与六结局** | `era2b` | 由 `(c0_status, pulse)` 的 $2 \times 3$ 象限决定六种结局与终态 Regime（同化新寨、烧毁死寂区、空间裂隙、幸存对照、慢性渗漏、虚警耐受）；时域分账与运行时结算见 §3。 | `era3_catastropheo/5`<br>`compile_branch_sliceo/2` |

---

## 3. 历史时域分层、局部证据与运行时结算

系统将「既定先例」「未决山谷」与「玩家的运行时干预」严格分账：

1. **双时域节点编译（`Chronicle` + `Projection` + `Node`）**：`horizon: :locked` 先例节点在生成期落定 `era3` 并投放全套五跳；`horizon: :open` 变奏节点在生成期按无干预基线编译 `era3`（`baseline_fate`，仍写入 `history`），只投放 Hop 1..4 + 2 张干扰卡，卷宗 `settled: false` 并携带 `reachable_transitions`（细节见 §3.1）。
2. **五跳局部卷宗（`Pathway` + `Witness` + `Content`）**：将因果链拆解为单跳卡片与同车间干扰卡，按世界种子绑定批号、工业载体和实测安全刻度 `safe_tick`（详见札记 05）。
3. **三方观测与溯因反演（`Slice` + `Observation` + `Ascribe`）**：模拟总指挥部（月度汇总+行政甩锅）、技术内层（铅锤削顶+滞后读表）、老寨（物候先后+击打计数）对同一物理轨迹的观测失真，由 `Ascribe.abduceo/4` 在 24 候选空间内反演隐藏解释。
4. **运行时干预与结局改写（`Facts` + `MandateOverworld`）**：在 `:open` 节点中，玩家通过 Hop 1→2→3 排除干扰寻回真载体，结合 Hop 4 读数在不应期推迟窗口（`safe_tick`）敲击重锚 $c_0$ 并配合上游降扰，把卷宗结算到目标结局并生成第 5 跳条规；生成期 `history.era3` 保持基线不改写，干预与结算追加式记录 `saw / did / said / predicted / settled`（`Facts` / `settle_open_dossier` 已在 Elixir 原型实现；Minecraft 侧接线见草案 01 §7 待完成项 4）。

### 3.1 六结局矩阵：先例分类表与未决山谷状态转移

第三纪结局由 `(c0_status, pulse)` 的 $2 \times 3$ 笛卡尔积完整覆盖。这一矩阵同时承担双重职责：

1. **既定先例节点（`horizon: :locked`，设计目标 1–2 个）的尸检分类表**：入场前已定格的结局（如 `BurnoutZone` 死寂标本与 `ControlGroup` 完好对照），提供完整的 Hop 1..5 卷宗供玩家反向前推（abduction）出 `$c_0$ 状态 × 源头脉冲 $\rightarrow$ 终态 Regime` 的因果律。
2. **未决变奏节点（`horizon: :open`，其余山谷）的运行时状态转移相图**：生成期按无干预基线编译 `era3`（`baseline_fate`，如 `c0_collapsed` 行的 `ChronicSeep` 或 `Oscillating`，仍写入 `history`），卷宗 `settled: false` 并携带 `reachable_transitions`。玩家通过寻回真工业载体（Hop 1→2→3）、在不应期推迟窗口（Hop 4 `safe_tick`）敲击重锚 $c_0$（纵向跨行：`c0_collapsed` $\rightarrow$ `c0_intact`），或关停/阻尼上游工业扰动（横向跨列），在运行时把卷宗结算到目标结局并生成第 5 跳条规（生成期 `history.era3` 保持基线不改写；游戏侧接线见草案 01 §7 待完成项 4）。

| $c_0$ 状态 \ 源头脉冲 | `alpha_shock`<br>（302 队浅层点炮） | `OverwhelmingNegativeSuppression`<br>（总指挥部高压反向注浆） | `heavy_kick`<br>（林场水文哨低强度采伐） |
| :--- | :--- | :--- | :--- |
| **`c0_collapsed`**<br>（通车，主器收缴） | **同化新寨**<br>`Fate_Assimilated_NewZhai`<br>终态：`Oscillating`<br>政体：`brigade_elder_joint_council` | **烧毁死寂区**<br>`Fate_Annihilated_BurnoutZone`<br>终态：`Burnout`<br>政体：`depopulated_concrete_sealed_zone` | **慢性渗漏**<br>`Fate_Deferred_ChronicSeep`<br>终态：`Strained`<br>政体：`deferred_evacuation_watchpost` |
| **`c0_intact`**<br>（绝壁留存 / 运行时重锚） | **虚警耐受**<br>`Fate_Tolerated_FalseAlarm`<br>终态：`Calm`<br>政体：`cliff_observation_hermitage` | **空间裂隙**<br>`Fate_Ascended_SpatialRift`<br>终态：`Strained`<br>政体：`rift_guarding_cliff_compact` | **幸存对照**<br>`Fate_Ancestral_ControlGroup`<br>终态：`Calm`<br>政体：`restored_ancestral_compact` |

其中两类「未爆发」状态构成了关键对照与开局入口：

- **慢性渗漏（`Fate_Deferred_ChronicSeep`）**：维持源已毁（`c0_collapsed`），但林场低强度扰动尚未击穿分岔；山鸣不断、累积应力逐年抬高，两套账本并行、撤离方案待批，是 `:open` 变奏山谷最典型的悬置开局形态。
- **虚警耐受（`Fate_Tolerated_FalseAlarm`）**：$c_0$ 完好（或由玩家在相位推迟窗口成功重锚），302 队周期点炮被维持源吸收而免于震荡成灾，既是同化新寨线的平行对照组，也是玩家在 `:open` 节点重锚 $c_0$ 后的可达稳态。

---

## 4. 向 Scala 3（`inner/`）对接与迁移原则

- **契约先行**：Scala 侧 `SliceContract` 产出 `contracts/mandate-slice-v1.json`，Elixir 侧 `Export` 产出 `schema_version = 2` 的节点与卷宗 JSON bundle；Elixir 侧用 `test/fixtures/world_cases.json` 钉住 golden（其中种子物证值来自 Scala 契约），跨栈数值锚仍是 `contracts/mandate-slice-v1.json`。
- **低频离散触发**：关系求解仅在 Node 激活、跨 Regime/Phase 跃迁或玩家操作勘定台时调用，绝不进入每 tick 的 RK4 积分循环。

---

## 5. 地形组合空间与可达性解耦

### 5.1 地形组合空间（24 / 48 可生成）

支沟地形由 5 个离散特征描述：$2 \text{ 上游地形} \times 2 \text{ 隘口} \times 2 \text{ 下游地形} \times 3 \text{ 源头地质} \times 2 \text{ 可达性} = 48$ 种组合：

- **24 种可生成世界**：源头地形与源头地质落在三条合法物理配对内（`steep_fissure + active_fault`、`karst_sinkhole + deep_stress_cavity`、`steep_fissure + minor_fracture`），每个配对跨越 2 种隘口 × 2 种可达性 × 2 种下游地形，全部编译出唯一历史切片。
- **24 种不支持地形（`:unsupported_topography`）**：地质与源头地形未配对的 3 组组合，留待后续扩展独立扰动通道。

### 5.2 可达性解耦：道路决定 $c_0$ 存亡

古器（铜钲/分水石堰）是否被收缴拆毁，取决于**外部行政力量能否通车进入**（`accessibility`），而非下游聚落是平坝还是台地：

- `tractor_road_accessible`（机耕道可达）：第二纪上清丈局入寨收器触发反噬，退守「外甲内寨」；第二纪下改组为国营垦殖分队，古器按废料收缴（`c0_collapsed`），山官后裔贬为仓库保管员。
- `cliff_isolated`（绝壁隔绝）：第二纪上胥吏止步崖下立归化碑；第二纪下仅挂靠林场联营，主器留存原地未拆（`c0_intact`），山官保留祭祀权。

由此，「平坝但断路」与「台地但通车」均成为合法世界，同一结局可在两种下游地形上生成。
