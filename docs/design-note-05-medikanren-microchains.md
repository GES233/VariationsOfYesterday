# 设计札记 05 — mediKanren 五跳局部微链与种子物证绑定

> 状态：**现行规范**。实现见 `tools/voy_world/lib/voy/world/pathway.ex`、`witness.ex` 与 `canon.ex`。

## 1. 设计目标：局部证据的多跳合一

借鉴 mediKanren 的关系拼链思想，每份档案单据只记录一条**封闭在自身专业口径内的单跳有向边（$A \xrightarrow{r} B$）**，不写全局因果摘要。只有当玩家（或求解器）将 5 份单据中的共享桥接实体（批号、工业载体、阻尼常数、实测推迟刻度）首尾合一时，完整故事脉络才浮现：

- **拓扑多态**：按古器类型分两条互不交叉的物理主链（铜钲声学冶金链 vs 石堰喀斯特水工链）；
- **单跳守恒锁**：`Pathway.connect_edges/1` 仅靠 `:from` 与 `:to` 元组合一拼链，不读顶层结局标签；删去任一跳即断链；
- **种子物证绑定（Seeded Skolemization）**：由 `(seed, branch_id, relic, fate)` 确定性派生批号、备件名与实测推迟刻度 `safe_tick`，并同卷宗混入 2 张断链干扰单据。

---

## 2. 五跳微链图谱（`Voy.World.Pathway`）

每条主链由 5 条有向边（Hop 1..5）首尾咬合而成：

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
2. **去向断链（`:destination_dead_end`，断于 Hop 2 $\rightarrow$ 3）**：回收批号去向为外运省局冶炼厂（`:scrap_shipment`），无法与本场车间配料卡的 `:scrap_batch` 输入合一。

---

## 3. 单卡认知局部性与不变量（`Canon.check_pathways/1`）

静态检查器与测试套件（`pathway_test.exs`）对全部 12 组 `(relic, fate)` 强制执行六条定理：
1. **多跳合一唯一性**：5 张真卡唯一连通起点 `{:relic, ...}` 与终点 `{:emergent_ritual, ...}`。
2. **单跳消融断链**：删去 Hop 1..5 中任意 1 跳，剩余 4 张卡 `connect_edges/1` 必返回 `[]`。
3. **干扰链排斥**：混入 2 张干扰卡并打乱顺序后，仍只合出唯一真载体。
4. **种子隔离与跨卡咬合**：不同 `seed` 派生不同 `(batch_id, vessel_name, safe_tick)`；同一 `seed` 内跨卡引用的批号、载体图号与 `safe_tick` 逐字相等，且 `safe_tick` 必来自契约实测 `Delayed` 列表。
5. **单卡反剧透（Epistemic Locality）**：`batch_id` 仅出现在 Hop 2 与 3；`vessel_code` 仅出现在 Hop 3 与 5；占空比数值 `28.1%` / `60.6%` 仅出现在 Hop 4；无任何单卡同时出现古器原名、流转批号与载体图号。
6. **$c_0$ 防火墙与六结局分叉**：Hop 2/3 元组显式携带 `c0_status`，防止拆器单据与留存单据串接。
