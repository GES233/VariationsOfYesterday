# 设计札记 05 — 五跳卷宗与内容管线（微链、种子物证与写作规范）

> 状态：**已迁入 `tools/voy_world/docs/`（Elixir 内容管线规范）**。原札记 03（内容流水线）已并入本文 §4–§5。实现见 `lib/voy/world/pathway.ex`、`witness.ex`、`content.ex` 与 `canon.ex`；作者内容在 `content/documents/`；关系引擎背景见 [札记 02](design-note-02-kanren-worldview-engine.md)。

## 1. 设计目标：局部证据的多跳合一

借鉴 mediKanren 的关系拼链思想，每份档案单据只记录一条**封闭在自身专业口径内的单跳有向边（$A \xrightarrow{r} B$）**，不写全局因果摘要。只有当玩家（或求解器）将 5 份单据中的共享桥接实体（批号、工业载体、阻尼常数、实测推迟刻度）首尾合一时，完整故事脉络才浮现：

- **拓扑多态**：按古器类型分两条互不交叉的物理主链（铜钲声学冶金链 vs 石堰喀斯特水工链）；
- **单跳守恒锁**：`Pathway.connect_edges/1` 仅靠 `:from` 与 `:to` 元组合一拼链，不读顶层结局标签；删去任一跳即断链；
- **种子物证绑定（Seeded Skolemization）**：由 `(seed, branch_id, relic, fate)` 确定性派生批号、备件名与实测推迟刻度 `safe_tick`，并混入 2 张断链干扰单据（规则见 §4.3）；
- **双时域投放**：`:locked` 生成期全量物化 5 张主链卡 + 2 张干扰卡；`:open` 生成期按 `baseline_fate` 物化 Hop 1..4、结算补 Hop 5（`history.era3` 保持基线，时域分账详见札记 02 §3.1）。

---

## 2. 五跳微链图谱（`Voy.World.Pathway`）

每条主链由 5 条有向边（Hop 1..5）首尾咬合而成（`:locked` 节点生成期全量物化；`:open` 节点生成期按 `baseline_fate` 物化 Hop 1..4，Hop 5 随结算落定）：

### 2.1 主链 A：声学冶金微链（`relic == :ancestral_bronze_zheng`）

| 跳数 | 单据类型 (`doc_kind`) | 起点 (`from`) | 终点 (`to`) | 局部证据内容 |
| :--- | :--- | :--- | :--- | :--- |
| **Hop 1** | `:gazetteer_excerpt` | `{:relic, :ancestral_bronze_zheng, :acoustic_damping}` | `{:precedent_omen, :fish_scale_ripple_and_slide, {:tau_breaths, 3}}` | 清丈局收铜钲致山崩掩署；发还铜钲叩击（三息余音）乃止 |
| **Hop 2** | `:requisition_slip` | `{:precedent_omen, ..., {:tau_breaths, 3}}` | `{:scrap_batch, batch, :high_tin_bronze, c0_status, {:tau_breaths, 3}}` | 垦殖改组收缴（`c0_collapsed`）或绝壁就地建档取副耳试片（`c0_intact`），编入批号 `batch` |
| **Hop 3** | `:workshop_card` | `{:scrap_batch, batch, :high_tin_bronze, c0_status, {:tau_breaths, 3}}` | `{:industrial_vessel, vessel, :bronze_alloy_casting, c0_status, {:tau_breaths, 3}}` | 机修车间将 `batch` 高锡青铜铸入备件 `vessel`，探伤实测保留三息（1.5s）谐振余音 |
| **Hop 4** | `:field_notebook` | `{:industrial_vessel, vessel, :bronze_alloy_casting, c0_status, {:tau_breaths, 3}}` | `{:modulation_protocol, vessel, :measured_delay_window, fate}` | 记录起振区 `28.1%` 与饱和区 `60.6%` 占空比反转、中点点炮 `+15.62` 平台阻滞，以及在第 `safe_tick` 格激发谐振可推迟尖峰 |
| **Hop 5** | `:council_ordinance` | `{:modulation_protocol, vessel, :measured_delay_window, fate}` | `{:emergent_ritual, fate, vessel, :spike_delayed_or_stabilized}` | 现行守山条规：盯验山匣读数至第 `safe_tick` 格，操作挂红绸之 `vessel` |

### 2.2 主链 B：喀斯特水工微链（`relic == :carved_diversion_stele`）

| 跳数 | 单据类型 (`doc_kind`) | 起点 (`from`) | 终点 (`to`) | 局部证据内容 |
| :--- | :--- | :--- | :--- | :--- |
| **Hop 1** | `:gazetteer_excerpt` | `{:relic, :carved_diversion_stele, :karst_siphon_relief}` | `{:precedent_omen, :sinkhole_backflow_and_flood, {:siphon_head_m, 12}}` | 清丈局毁分水石堰致暗河倒灌；复立石堰按十二米水头古槽分洪乃止 |
| **Hop 2** | `:requisition_slip` | `{:precedent_omen, ..., {:siphon_head_m, 12}}` | `{:scrap_batch, batch, :grooved_limestone_ashlar, c0_status, {:siphon_head_m, 12}}` | 刻槽条石收缴（`c0_collapsed`）或原址留存取副坎试石（`c0_intact`），编入批号 `batch` |
| **Hop 3** | `:workshop_card` | `{:scrap_batch, batch, :grooved_limestone_ashlar, c0_status, {:siphon_head_m, 12}}` | `{:industrial_vessel, vessel, :siphon_weir_lining, c0_status, {:siphon_head_m, 12}}` | 基建队用 `batch` 弧槽条石砌筑水工设施 `vessel`，复刻十二米水头虹吸曲率 |
| **Hop 4** | `:field_notebook` | `{:industrial_vessel, vessel, :siphon_weir_lining, c0_status, {:siphon_head_m, 12}}` | `{:modulation_protocol, vessel, :measured_delay_window, fate}` | 记录占空比反转（`28.1%` vs `60.6%`）及在第 `safe_tick` 格开启 `vessel` 虹吸可泄压推迟尖峰 |
| **Hop 5** | `:council_ordinance` | `{:modulation_protocol, vessel, :measured_delay_window, fate}` | `{:emergent_ritual, fate, vessel, :spike_delayed_or_stabilized}` | 现行守山条规：在验山匣第 `safe_tick` 格拉起 `vessel` 闸板放回头水 |

### 2.3 两类断链干扰卡（Decoy Edges）

每个卷宗固定附带 2 张同车间干扰卡，由 Kanren 合一自动剪枝淘汰：

1. **材质不符（`:material_mismatch`，断于 Hop 3 $\rightarrow$ 4）**：车间使用普通生铁（`{:tau_breaths, 0}`）或毛石（`{:siphon_head_m, 0}`）铸造备件，无阻尼/水头属性，无法与观测手簿合一。
2. **去向断链（`:destination_dead_end`，断于 Hop 2 $\rightarrow$ 3）**：回收批号去向为外运外单位（铜钲线为省局冶炼厂、石堰线为省水利工程处），无法与本场车间配料卡的 `:scrap_batch` 输入合一。

---

## 3. 单卡认知局部性与不变量（`Canon.check_pathways/1`）

`Canon.check_pathways/1` 对全部 12 组 `(relic, fate)` 检查定理 1/2/3/5；`pathway_test.exs` 补定理 4（跨 seed 隔离）与 6（$c_0$ 防火墙）：

1. **多跳合一唯一性**：5 张真卡唯一连通起点 `{:relic, ...}` 与终点 `{:emergent_ritual, ...}`。
2. **单跳消融断链**：删去 Hop 1..5 中任意 1 跳，剩余 4 张卡 `connect_edges/1` 必返回 `[]`。
3. **干扰链排斥**：混入 2 张干扰卡并打乱顺序后，仍只合出唯一真载体。
4. **种子隔离与跨卡咬合**：不同 `seed` 派生不同 `(batch_id, vessel_name, safe_tick)`；同一 `seed` 内跨卡引用的批号、载体图号与 `safe_tick` 逐字相等，且 `safe_tick` 必来自契约实测 `Delayed` 列表。
5. **单卡反剧透（Epistemic Locality）**：`batch_id` 仅出现在 Hop 2 与 3；`vessel_code` 仅出现在 Hop 3 与 5；占空比数值 `28.1%` / `60.6%` 仅出现在 Hop 4；无任何单卡同时出现古器原名、流转批号与载体图号。
6. **$c_0$ 防火墙与六结局分叉**：Hop 2/3 元组显式携带 `c0_status`，防止拆器单据与留存单据串接。

---

## 4. 内容包与写作规则

### 4.1 三层职责分离

| 层级 | 维护者 | 负责内容 | 变更方式 |
| :--- | :--- | :--- | :--- |
| **内容层** | 叙事作者 | `content/documents/**/*.md`（32 张主链卡 + 4 张干扰卡） | 编辑 Markdown，运行 `mix voy.content lint` |
| **结构层** | 技术维护者 | 五跳拓扑（`Pathway`）、种子物证池（`Witness`）、四纪历史（`Chronicle`）、数值契约（`Contract`） | 修改代码与测试 |
| **校验层** | 机器检查 | 卡片覆盖、唯一选择、占位符闭合、禁写死年份、五跳消融与认知局部性（`Canon`） | `mix test` / CI 自动执行 |

作者只负责单据文风与局部事实；新增 hop、古器、结局、干扰类型或安全刻度属于结构变更。

### 4.2 卡片格式与选择规则

每份档案是带平铺头部（`---`）的 Markdown 文件：

- **Hop 1（旧志先例）、Hop 3（车间配料卡）**：声明 `hop / role / relic / title`；
- **Hop 2（收缴或代管单）**：另声明 `c0_status`（`c0_collapsed` 或 `c0_intact`）；
- **Hop 4（野外观测手簿）、Hop 5（现行条规）**：另声明 `fate`（六种结局之一）；
- **干扰卡（Decoy）**：`role: decoy`，声明 `relic / decoy_class / title`。

对于全部 $2 \times 6 = 12$ 个 `(relic, fate)` 组合，`Content.card_for/2` 为每条边唯一选出一张卡片（5 张主链 + 2 张干扰），缺卡或撞卡均直接报错。具体写作语法与占位符清单见 [content/README.md](../content/README.md)。

### 4.3 种子物化与实测安全刻度

1. **实测安全刻度（`safe_ticks`）**：Scala `PhaseResponse` 对每个整数 tick 实测 `alpha_metal_resonance` 的尖峰响应，仅将结果为 `Delayed` 的刻度写入 `contracts/mandate-slice-v1.json`。
2. **种子绑定（`Witness.bind/5`）**：按 `(seed, branch_id, relic, fate)` 确定性哈希选出互不冲突的真链批号、工业载体与 `safe_tick`，以及两套同车间干扰批号（材质不符卡另附干扰载体），渲染占位符后填入 `Export`（schema v2）的 `dossier` 字段。

---

## 5. 常用命令

```sh
mix voy.content lint
mix voy.content preview [baishui|heishui|xuanguan|nuoliang|mahao|tingshan|all] [--seed N]
mix voy.content template <hop1|hop2|hop3|hop4|hop5|decoy>
```

（`preview` 默认 `all`，默认 seed 101；`template` 输出空白主链/干扰卡。）
