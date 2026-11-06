# 设计札记 02 — Kanren 关系世界模型：神经符号分层与四纪历史编译器

> 状态：**现行架构说明**（全局叙事已由五跳微链模型接管，见札记 03 / 05；地形与六结局矩阵见札记 04）。

## 1. 神经符号三层架构

模组将连续动力学模拟与离散符号推理严格分层，避免在 ODE 内硬编码叙事分支，也避免符号层脱离物理真值：

- **底层（连续数值 / Scala）**：`MandateState` 与 `MandateGrid` 执行四维 RK4 积分与算子分裂平流扩散，零叙事规则侵入。
- **中间层（语义读数与契约 / Scala ↔ JSON）**：`MandateReadout` 将连续状态 $(v, w, s, q)$ 投影为慢变量分级 `Regime`（`Calm` / `Strained` / `Oscillating` / `Burnout`）与快变量相位 `PhaseReading`；`SliceContract` 将精确 Hopf 点、占空比反转与逐整数 tick 实测脉冲响应固化为 `contracts/mandate-slice-v1.json`。
- **上层（关系与证据引擎 / Elixir `tools/voy_world`）**：以 microKanren（`Voy.Kanren`）为核心，提供：
  1. **开放属性表匹配（`propso/2`）**：实体表示为键值表，规则只约束相关属性子集，其余风味字段自动透传；
  2. **不等约束（`neq` / `=/=`）**：在合一状态中维护不等式防火墙（如行政通报禁忌机械/巫术归因，被迫合一到秋雨替罪羊）；
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
| **第三纪：撤场与六结局** | `era2b` | 由 `(c0_status, pulse)` 的 $2 \times 3$ 象限决定六种结局与终态 Regime（同化新寨、烧毁死寂区、空间裂隙、幸存对照、慢性渗漏、虚警耐受）。**既定先例节点（`horizon: :locked`）**在生成期直接落定 `era3`；**未决变奏节点（`horizon: :open`）**在生成期停于 `era2b` 悬置初态，待运行时由玩家干预决定 `era3` 走向 | `era3_catastropheo/5`<br>`compile_branch_sliceo/2` |

---

## 3. 历史时域分层、局部证据与运行时结算的分工

系统将「既定先例」「未决山谷」与「玩家的运行时干预」严格分账：

1. **双时域节点编译（`Chronicle` + `Projection` + `Node`）**：
   - **既定先例节点（`horizon: :locked`，1–2 个）**：入场前已定格于第三纪结局（如 `BurnoutZone` 或 `ControlGroup`），三处遗迹与全套五跳卷宗（Hop 1..5）固定，作为溯因反演的对照标本。
   - **未决变奏节点（`horizon: :open`，其余山谷）**：生成期停在 `era2b`/悬置初态（如 `c0_collapsed` 下的 `ChronicSeep` 或 `Oscillating`），仅投放前四跳（Hop 1..4）与 2 张同车间干扰卡。
2. **五跳局部卷宗（`Pathway` + `Witness` + `Content`）**：将因果链拆解为单跳卡片与同车间干扰卡，按世界种子绑定批号、工业载体和实测安全刻度 `safe_tick`（详见札记 03、05）。
3. **三方观测与溯因反演（`Slice` + `Observation` + `Ascribe`）**：模拟总指挥部（月度汇总+行政甩锅）、技术内层（铅锤削顶+滞后读表）、老寨（物候先后+击打计数）对同一物理轨迹的观测失真，由 `Ascribe.abduceo/4` 在 24 候选空间内反演隐藏解释。
4. **运行时干预与结局改写（`Facts` + `MandateOverworld`）**：在 `:open` 节点中，玩家通过 Hop 1→2→3 排除干扰寻回真载体，结合 Hop 4 读数在不应期推迟窗口（`safe_tick`）敲击重锚 $c_0$ 并配合上游降扰，驱动山谷在 $2 \times 3$ 状态图上跃迁、结算 `era3` 结局并生成第 5 跳条规；追加式记录 `saw / did / said / predicted / settled`。

---

## 4. 向 Scala 3（`inner/`）对接与迁移原则

- **契约先行**：Scala 侧 `SliceContract` 产出 `contracts/mandate-slice-v1.json`，Elixir 侧 `Export` 产出 `schema_version = 2` 的节点与卷宗 JSON bundle；两侧通过 `test/fixtures/world_cases.json` 锁定跨栈一致性。
- **低频离散触发**：关系求解仅在 Node 激活、跨 Regime/Phase 跃迁或玩家操作勘定台时调用，绝不进入每 tick 的 RK4 积分循环。
