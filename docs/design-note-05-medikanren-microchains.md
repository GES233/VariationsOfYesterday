# 设计札记 05 — mediKanren 局部微链与种子物证绑定

> 状态：**五跳已替换全局叙事**（2026-10-30）。`Pathway + Witness + Content` 是唯一玩家叙事管线；旧 `Causality`、四份全知档案与 `archives` 导出已退役，`Chronicle` 仅保留地形与历史骨架。Export 当前为 schema v2。
> 硬边界：**更深入，而不是更大**——不扩充 24 种可生成地形与 6 种结局的边界。安全刻度改为读取 Scala 逐 tick 实测的 `Delayed` 结果，不再从声明相位窗猜测。

---

## 0. 背景与四项专家级深度要求

早期 `tools/voy_world/content/` 中的 24 份档案卡采用了「自顶向下分拣 + 全知摘要」写法（例如单张 `blasting_zheng.md` 同时写出清丈旧案、79 年收缴铜钲、`c0=0`、302 队点炮与全谷自持振荡）。这违背了引入 Kanren 的初衷——借鉴 **mediKanren**：**每份研究/单据只记录一条局部的、封闭在自身专业口径内的逻辑微链（Single-hop Edge $A \xrightarrow{r} B$），通过共享中间桥接实体（Bridge Terms）的多跳合一（Multi-hop Unification），涌现出完整故事脉络。**

同时，桥接实体必须与 Minecraft 世界生成的随机性结合：
- **第一层（拓扑与路径多态）**：由 D8 地形与古器类型决定走哪条物理微链（峡谷铜钲走「废铜回收 → 农机翻砂混铸 → 声学谐振代钟」；溶洞石堰走「条石征用 → 水泵房溢流槽砌筑 → 虹吸泄压代堰」）；
- **第二层（Kanren 守恒锁）**：单据之间不靠结局标签匹配，而靠共享逻辑变量（`?batch`、`?vessel`、`?tau`、`?window`）首尾合一；
- **第三层（种子物证具象化 / Seeded Skolemization）**：由 `(world_seed, node_id)` 确定性派生具体批号、工业备件名与相位格数，并生成 **断链干扰项（Decoy Records）**，防 Wiki 剧透。

---

## 1. 交付物清单（仅限 `tools/voy_world/`）

请在 `tools/voy_world/` 下新增（或最小侵入式扩展）以下文件，保持既有 API 100% 兼容：

1. `tools/voy_world/lib/voy/world/pathway.ex`（新增）—— mediKanren 风格局部微链关系引擎；
2. `tools/voy_world/lib/voy/world/witness.ex`（新增）—— 种子确定性物证绑定器（Seeded Skolemizer）、干扰单据生成与文本插值渲染；
3. `tools/voy_world/test/voy/world/pathway_test.exs`（新增）—— 覆盖多跳合一、单跳消融断链定理、种子物证隔离、干扰链排除、单卡反剧透检查（Anti-Omniscience Linter）。

> **红线约束**：
> - 严禁破坏现有 `Chronicle`、`Projection`、`Content`、`Ascribe`、`Observation`、`Contract` 测试；
> - 在 `Voy.Kanren` 中提取结果时，必须使用 `Voy.Kanren.Core.run_all(...) |> Voy.Kanren.Core.terms()`（并在自定义 goal 中使用 `walk_star/2` 而非浅层 `walk/2`）；
> - 所有枚举顺序与列表输出必须确定性保序，严禁依赖无序 `Map.values/1`。

---

## 2. 模块一：`Voy.World.Pathway`（mediKanren 局部微链图谱）

### 2.1 微链的 5 跳结构（5-Hop Causal Pathway）

每一条完整故事脉络由 **5 条局部有向边（Hops）** 首尾咬合而成。每条边具有结构：

```elixir
%{
  hop: 1..5,
  doc_kind: atom(),          # :gazetteer_excerpt | :requisition_slip | :workshop_card | :field_notebook | :council_ordinance
  lens: atom(),              # :gazetteer | :official_logistics | :workshop_tech | :hydro_tech | :council_folk
  from: term(),              # 输入锚点（与上一跳的 :to 合一）
  relation: atom(),          # 局部机制谓词
  to: term(),                # 输出锚点（与下一跳的 :from 合一）
  attrs: keyword()           # 该跳独有的局部证据属性
}
```

### 2.2 两条物理主链（按古器 `relic` 分叉，绝不混串）

#### 主链 A：声学冶金微链（`relic == :ancestral_bronze_zheng`，峡谷隘口）

| 跳数 | `doc_kind` | `from` 节点 | `relation` | `to` 节点 | 局部物理/制度含义 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Hop 1** | `:gazetteer_excerpt` | `{:relic, :ancestral_bronze_zheng, :acoustic_damping}` | `:removal_provokes_omen` | `{:precedent_omen, :fish_scale_ripple_and_slide, {:tau_ breaths, 3}}` | 清丈局收铜钲 → 涧水起鱼鳞碎纹、山崩掩署；发还铜钲叩击（三息余音）乃止 |
| **Hop 2** | `:requisition_slip` | `{:precedent_omen, :fish_scale_ripple_and_slide, {:tau_breaths, 3}}` | `:requisitioned_as_scrap` | `{:scrap_batch, batch_tag, :high_tin_bronze, {:tau_breaths, 3}}` | 79年垦殖改组，无视旧志征兆，将寨门旧铜器按废有色金属过磅收缴，编入批号 `batch_tag` |
| **Hop 3** | `:workshop_card` | `{:scrap_batch, batch_tag, :high_tin_bronze, {:tau_breaths, 3}}` | `:cast_into_industrial_vessel` | `{:industrial_vessel, vessel_tag, :bronze_alloy_casting, {:tau_breaths, 3}}` | 机修车间将 `batch_tag` 高锡青铜掺入生铁翻砂，铸成农机备件 `vessel_tag`，探伤实测保留三息（1.5s）谐振余音 |
| **Hop 4** | `:field_notebook` | `{:industrial_vessel, vessel_tag, :bronze_alloy_casting, {:tau_breaths, 3}}` | `:duty_cycle_inversion_diagnosed` | `{:modulation_protocol, vessel_tag, :measured_delay_window, fate}` | 拆器后高台闷雷期从起振区 `28.1%` 膨胀至饱和区 `60.6%`（占空比反转）；中段（`φ=0.50`）点炮反致平台阻滞 `+15.62`；在契约实测为推迟的第 N 格激发谐振可推迟尖峰 |
| **Hop 5** | `:council_ordinance` | `{:modulation_protocol, vessel_tag, :measured_delay_window, fate}` | `:enacted_as_survival_ritual` | `{:emergent_ritual, fate, vessel_tag, :spike_delayed_or_stabilized}` | 留守议事会立死规：穿劳保服盯簧片落定格，持锤叩击红绸悬挂之 `vessel_tag` 三下，维系残存山谷 |

#### 主链 B：喀斯特水工微链（`relic == :carved_diversion_stele`，溶洞隘口）

| 跳数 | `doc_kind` | `from` 节点 | `relation` | `to` 节点 | 局部物理/制度含义 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Hop 1** | `:gazetteer_excerpt` | `{:relic, :carved_diversion_stele, :karst_siphon_relief}` | `:removal_provokes_omen` | `{:precedent_omen, :sinkhole_backflow_and_flood, {:siphon_head_m, 12}}` | 清丈局毁分水石堰辟官田 → 暗河倒灌吞没仓廒；复立石堰按古槽分洪乃止 |
| **Hop 2** | `:requisition_slip` | `{:precedent_omen, :sinkhole_backflow_and_flood, {:siphon_head_m, 12}}` | `:requisitioned_as_scrap` | `{:scrap_batch, batch_tag, :grooved_limestone_ashlar, {:siphon_head_m, 12}}` | 79年改组，将刻槽石堰条石按基建石料征用，编入石料调拨批号 `batch_tag` |
| **Hop 3** | `:workshop_card` | `{:scrap_batch, batch_tag, :grooved_limestone_ashlar, {:siphon_head_m, 12}}` | `:cast_into_industrial_vessel` | `{:industrial_vessel, vessel_tag, :siphon_weir_lining, {:siphon_head_m, 12}}` | 基建队贪图省工，直接用 `batch_tag` 带弧槽条石砌筑水泵房溢流设施 `vessel_tag`，无意间复刻了 12 米水头虹吸曲率 |
| **Hop 4** | `:field_notebook` | `{:industrial_vessel, vessel_tag, :siphon_weir_lining, {:siphon_head_m, 12}}` | `:duty_cycle_inversion_diagnosed` | `{:modulation_protocol, vessel_tag, :measured_delay_window, fate}` | 毁堰后溶洞承压高台期由 `28.1%` 膨胀至 `60.6%`；中段爆破反致暗河憋压阻滞 `+15.62`；在契约实测为推迟的第 N 格开启 `vessel_tag` 虹吸闸门可泄压推迟尖峰 |
| **Hop 5** | `:council_ordinance` | `{:modulation_protocol, vessel_tag, :measured_delay_window, fate}` | `:enacted_as_survival_ritual` | `{:emergent_ritual, fate, vessel_tag, :spike_delayed_or_stabilized}` | 留守议事会立死规：按交接班点名，在验山匣读数格拉起挂红绸之 `vessel_tag` 铸铁闸板九寸，放暗河回头水 |

### 2.3 干扰边（Decoy Edges）与 Kanren 过滤

`Pathway` 必须同时在关系表中声明至少 **2 类无法走通全程的干扰边（Decoy Edges）**：
1. **材质/阻尼断链（Material Mismatch Decoy）**：
   - 回收单 `batch_decoy_a` 回收的是普通生铁农具（`:plain_grey_iron`, `{:tau_breaths, 0}`），虽进了车间铸成备件 `vessel_decoy_a`，但因缺少 `{:tau_breaths, 3}`（或 `{:siphon_head_m, 12}`），在 **Hop 3 → Hop 4** 合一失败！
2. **去向断链（Destination Dead-end Decoy）**：
   - 回收单 `batch_decoy_b` 虽含铜/石，但处理去向是 `:shipped_out_to_provincial_smelter`（外运省局），在 **Hop 2 → Hop 3** 无车间配料卡可合一！

### 2.4 `Voy.World.Pathway` 必须提供的核心 API

```elixir
# 1. 单跳关系：给定或求解任意单跳边
Pathway.edgeo(relic, fate, edge)

# 2. 5 跳完整路径关系：强制 prev.to == next.from 首尾合一
Pathway.chaino(relic, fate, start_node, end_node, hops)

# 3. 确定性正向求解：返回唯一合法的 5 跳微链 + 2 条断链干扰项
Pathway.compile_chain(relic, fate) :: {:ok, %{chain: [map()], decoys: [map()]}}

# 4. 任意边子集拼接检验（供消融测试使用）：
#    给定一组散乱的 edges，仅凭节点首尾 Unification（禁止读取顶层 fate 作弊）
#    能否从 {:relic, relic, _} 一路连通至 {:emergent_ritual, fate, vessel_tag, _}？
Pathway.connect_edges(edges) :: [%{start: term(), finish: term(), vessel: term(), path: [map()]}]
```

> **关键设计约束**：`connect_edges/1` 拼接时，**严禁**靠比对顶层 `:fate` 字段跳步，必须严格验证 `hop1.to == hop2.from`、`hop2.to == hop3.from`、`hop3.to == hop4.from`、`hop4.to == hop5.from`。因此只要从 5 跳集合中删掉任意 1 跳（Path Ablation），`connect_edges/1` 必然返回 `[]`！

---

## 3. 模块二：`Voy.World.Witness`（种子物证绑定与原生态文本渲染）

### 3.1 种子确定性物证池（Seeded Skolemization）

给定 `(world_seed, branch_id, relic, fate)`，`Witness.bind(seed, branch_id, relic, fate)` 使用确定性哈希（如 `:erlang.phash2({seed, branch_id, salt})`）从年代考据池中选出互不冲突的具象凭证（**真链 1 套 + 干扰链 2 套，同一 seed 内真假批号/备件名绝不撞车**）：

- **铜钲分支候选池**：
  - 批号池（`batch_id`）：`"滇垦废字（79）第04号"`、`"云农调字（79）第11号"`、`"垦机收字（80）第07号"`、`"边森回字（79）第19号"`
  - 工业载体池（`vessel_name` / `vessel_code`）：
    1. `{"东方红-54型叁号气缸盖", "翻-79-09-丙"}`
    2. `{"丰收-35型副变速箱壳体", "铸-79-11-甲"}`
    3. `{"红旗-100型高压水泵蜗壳", "修-80-02-乙"}`
    4. `{"195型柴油机平衡飞轮盘", "翻-79-12-丁"}`
- **石堰分支候选池**：
  - 批号池（`batch_id`）：`"滇垦基字（79）第03号"`、`"云水调字（79）第08号"`、`"林工石字（80）第12号"`、`"垦建征字（79）第15号"`
  - 工业载体池（`vessel_name` / `vessel_code`）：
    1. `{"叁号冷却水塔副溢流槽", "砌-79-10-甲"}`
    2. `{"机修房尾水消力池隔墙", "水-79-12-丙"}`
    3. `{"二号水轮泵站回水弯道", "建-80-03-乙"}`
    4. `{"选矿场沉砂池虹吸底坎", "砌-79-11-丁"}`
- **动力学实测参数锚定（直接读取 `Voy.World.Contract` 真值 + 种子微扰接口）**：
  - 默认锚定 `contracts/mandate-slice-v1.json` 真值：
    - 起振区周期 `onset_period = 43.7`、高台期占比 `28.1%`（约 `12.3` 刻）、中点响应 `-17.38`（提前引爆）；
    - 饱和区周期 `settled_period = 39.5`、高台期占比 `60.6%`（约 `23.9` 刻）、中点响应 `+15.62`（平台阻滞）；
    - 实测推迟刻度：Scala 对每个整数 tick 测量 `alpha_metal_resonance`，契约只把结果为 `Delayed` 的条目写入 `safe_ticks`；`Witness` 再按种子从中选取 `safe_tick`。不再预设 `[8, 9, 10, 11]` 或把绝对窗标签当作有效性证明。

### 3.2 原生态单跳档案渲染（严守「单卡只知一跳」铁律）

`Witness.render_dossier(seed, branch_id, relic, fate, names)` 输出包含 **5 份主链原生态单据 + 2 份同车间干扰单据** 的完整调查卷宗。

**单卡反剧透硬约束（Anti-Omniscience Rule）**：
- **Hop 1（清丈残卷）**：只允许出现古器与晚清异象，**严禁**出现 `batch_id`、`vessel_name`、302 队或现代年份；
- **Hop 2（物资回收过磅单）**：只允许出现古器收缴描述与 `batch_id`，**严禁**出现最终铸成的 `vessel_name`、水文相位或议事会祭祀；
- **Hop 3（车间翻砂/砌筑配料卡）**：只允许出现输入 `batch_id`、产出 `vessel_name`（`vessel_code`）及探伤/试水物理余音属性，**严禁**提及古器原本是镇山法器，也**严禁**提及点炮或祭祀；
- **Hop 4（302 队野外观测手簿夹页）**：只允许记录「起振区 28.1% vs 饱和区 60.6% 占空比反转」「中点点炮反致 `+15.6` 刻平台阻滞」及「唯在静默段第 `safe_tick` 格激发谐振可推迟尖峰」，**严禁**直接点名 `batch_id`；
- **Hop 5（留守议事会交接班死规）**：只允许规定在「第 `safe_tick` 格」操作挂红绸之 `vessel_name`（`vessel_code`），**严禁**解释它为何有效，也**严禁**出现 `batch_id` 或百年前的清丈局。

---

## 4. 验收测试规格（`tools/voy_world/test/voy/world/pathway_test.exs`）

小模型完成实现后，运行 `mix test` 必须额外通过以下 5 项硬核测试：

1. **多跳合一完备性（Multi-hop Synthesis across all 12 `(relic, fate)` pairs）**：
   遍历 2 种 `relic` × 6 种 `fate`，`Pathway.compile_chain/2` 均唯一合出 5 跳微链，`Pathway.connect_edges(chain)` 唯一还原出从 `{:relic, relic, _}` 到 `{:emergent_ritual, fate, vessel_tag, _}` 的完整路径。
2. **无捷径消融定理（No-Shortcut / Path Ablation Theorem）**：
   对任意一条 5 跳微链，枚举删去 Hop 1..5 中的任意 1 跳（剩余 4 跳），调用 `Pathway.connect_edges/1` 均无法连通起点与终点（返回 `[]`）。证明没有任何一张卡片可以跳步剧透。
3. **干扰链排斥定理（Decoy Rejection Theorem）**：
   将 2 条干扰边混入 5 条真链边（共 7 张打乱顺序的单据），`Pathway.connect_edges/1` 依然**只**合出唯一那条真 `vessel_tag`，生铁无阻尼件与外运废料批号被 Kanren 自动剪枝淘汰。
4. **种子随机隔离与内部一致性（Seed Isolation & Internal Coherence）**：
   - 同一 `(relic, fate)` 在 `seed = 101` 与 `seed = 202` 下，`Witness.bind/4` 产出的 `(batch_id, vessel_name, safe_tick)` 组合不同；
   - 在任意给定 seed 内，Hop 2 单据正文中的 `batch_id` 与 Hop 3 单据正文中的 `batch_id` 100% 逐字相等；Hop 3 正文中的 `vessel_name` / `vessel_code` 与 Hop 5 正文中的 `vessel_name` / `vessel_code` 100% 逐字相等；Hop 4 与 Hop 5 正文中的 `safe_tick` 100% 逐字相等，且 `safe_tick / 39.474324` 严格落在 `[0.20, 0.30]` 绝对不应期内。
5. **单卡认知局部性静态检查（Epistemic Locality Linter）**：
   扫描渲染出的 5 份单据正文：
   - `batch_id` **仅**出现在 Hop 2 与 Hop 3（出现次数恰好为 2）；
   - `vessel_code` **仅**出现在 Hop 3 与 Hop 5（出现次数恰好为 2）；
   - `"28.1%"` 与 `"60.6%"`（占空比反转实测值）**仅**出现在 Hop 4；
   - 没有任何单份文档同时包含古器原名（`"铜钲"` / `"分水石堰"`）、流转批号 `batch_id` 与现世载体 `vessel_code`。
6. **`c0_status` 合一防火墙与六结局物理分叉定理（`c0_status` Unification Firewall & 6-Fate Regime Differentiation）**：
   - Hop 2 与 Hop 3 节点元组显式携带 `c0_status`（`:c0_collapsed` vs `:c0_intact`），使得 `c0_collapsed` 的「全数拆解回炉」单据在 Kanren 中**无法**与 `c0_intact` 的观测手簿/守山条规合一；
   - `c0_intact` 三种结局（幸存对照、虚警耐受、空间裂隙）在 Hop 2 中严格保持「绝壁栈道断绝，主器留存原地未拆」，仅取副耳/副坎试片登记 `batch_id` 配制耦合副件；
   - 全部 6 种结局在 Hop 4（302 队手簿）与 Hop 5（现行条规）中呈现完全互异的物理相区与制度文本（自持振荡代钟、烧毁钙化水泥封井、慢性渗漏双账本、宽容区幸存对照、四十七次虚警核销、极值黑影裂隙封山铁誓）。

---

## 5. 阶段二（路线 1 + 2 联合规格）：管线合流与交互式「盲拼调查台」

> 用途：供 `deepseek-account/deepseek-flash` 子代理一次性完成「内容管线合流（路线 1）」与「交互式档案盲拼调查台 HTML（路线 2）」。
> 硬性纪律：
> 1. 执行任何 `mix` 或 `gradlew` 命令前，先读 `ref/本地环境.md`；
> 2. **严禁执行 `git add` 或 `git commit`**；
> 3. **严禁破坏现有的 146 条 Elixir 测试（尤其是 `golden_test.exs`、`node_test.exs`、`content_test.exs`）**。

### 5.1 路线 1：`Node` / `Naming` / `Content.preview` / `Canon` 合流

1. **`Voy.World.Node` 与 `Voy.World.Naming` 可选种子卷宗挂载（保持无种子调用 100% 向后兼容）**：
   - 在 `Voy.World.Node.normalize_spec/1` 中支持可选字段 `:seed`（`integer() | nil`，默认为 `nil`）。当调用方未传 `:seed` 时，保持现有返回结构**完全不变**（确保 `golden_test.exs` 与 `world_cases.json` 零回归）！
   - 当 `spec.seed` 为整数时，`Naming.materialize/2` 在生成的 keyword record 尾部追加 `dossier: Witness.render_dossier(spec.seed, spec.branch_id, relic, fate, spec.names)`（其中 `relic = era1[:guardian_relic]`，`fate = era3[:fate]`），并在 `Voy.World.Export`（若转换 keyword record 为 JSON map）或 `Voy.World.compile_node_dossier(spec_fields, feature_fields, seed)` 中提供便捷入口：
     ```elixir
     Voy.World.compile_node_dossier(spec_fields, feature_fields, seed) :: {:ok, [map()]} | {:error, term()}
     ```
2. **`Voy.World.Content.preview` 与 `mix voy.content preview` 扩展**：
   - `Content.preview(target \\ "all", opts \\ [])`：支持可选 `seed: integer()`（默认 `seed: 101`）。在原有预设节点预览文本（地貌、四纪、三处遗迹、四份表层档案）之后，追加一段 **`【五跳微链卷宗（seed=101，含2份同车间干扰单据）】`**，按顺序列出 `Witness.render_dossier(seed, branch_id, relic, fate, %{up: up_name, pass: pass_name, down: down_name})` 的 5 份主链单据与 2 份干扰单据。
   - **注意**：必须保留原有 `Content.preview/1` 输出中的全部既有段落与标题，确保现有的 `content_test.exs` 100% 通过！
   - 在 `Mix.Tasks.Voy.Content` 中支持 `mix voy.content preview [target] [--seed N]`。
3. **`Voy.World.Canon.check_pathways/1` 静态检查器**：
   - 在 `Voy.World.Canon` 中新增 `check_pathways(seeds \\ [101, 202]) :: :ok | {:error, [term()]}`：
     - 遍历全部 12 个 `(relic, fate)` 组合与给定的 `seeds`；
     - 验证 5 跳微链合一唯一性、单跳消融断链（删任意 1 跳 `connect_edges` 为 `[]`）、干扰边排斥、以及单卡认知局部性（`batch_id` 仅在 Hop 2/3、`vessel_code` 仅在 Hop 3/5、`"28.1%"` 与 `"60.6%"` 仅在 Hop 4、无单卡同时含古器名+批号+图号）；
     - 全部通过返回 `:ok`。
4. **新增测试**：
   - 在 `tools/voy_world/test/voy/world/pathway_test.exs`（或对应测试文件）中新增针对 `Canon.check_pathways/1`、`Voy.World.compile_node_dossier/3` 与 `Content.preview/2` 微链段落的断言测试。

### 5.2 路线 2：交互式「mediKanren 档案盲拼调查台」（`ref/show-me-dossier-desk.html`）

请生成自包含、零外部依赖的单文件 HTML：`ref/show-me-dossier-desk.html`（视觉风格严格对齐 `ref/mandate-cell-lab.html` 与 `ref/show-me-phase-duty-inversion.html`：深灰黑底色 `#111516`、卡片底色 `#181d1f`、暖铜金主色 `#e0b56a`、青绿通过色 `#6bc4a6`、砖红断链告警色 `#d96b6b`、衬线与等宽混排）。

页面内嵌由 Elixir 真实导出的 **6 个预设支沟（`baishui` 同化新寨、`heishui` 烧毁封禁、`xuanguan` 空间裂隙、`nuoliang` 幸存对照、`mahao` 慢性渗漏、`tingshan` 虚警耐受）× 4 个种子（`101, 202, 303, 404`）** 的完整 `Witness.render_dossier` + `Pathway` 节点元组数据（可在生成 HTML 前用 `mix run` 小脚本直接导出真实 JSON 嵌进 HTML，严禁手敲假文本！）。

**必须具备的四大交互功能**：
1. **顶部控制台（Top Bar）**：
   - 支沟/案件切换（6 个预设支沟 + 一个「🎲 随机盲测案件（隐藏结局名）」按钮）；
   - 世界种子切换（`Seed 101` / `202` / `303` / `404`）；
   - 模式切换：**「🕵️ 盲拼断案模式（乱序隐去跳数与真伪标签）」** vs **「🔬 Kanren 图谱透视模式（显示 Hop 1..5、`:from`/`:to` 元组与干扰断点）」**。
2. **左侧桌面：7 张散落单据（5 真 + 2 干扰）与桥接词高亮**：
   - 展示当前案件的 7 张单据卡片；
   - **桥接词联动高亮**：将单据正文中的关键桥接词（`batch_id`、`vessel_name`、`vessel_code`、`三息`/`十二米水头`、`28.1%`/`60.6%`、`第N格`）渲染为可点击/悬停的胶囊标签（badge），点击任一桥接词时，桌面上所有包含该桥接词的单据同步高亮，直观呈现「单卡只知一跳，桥接词跨卡咬合」！
3. **右侧上栏：5 槽位 Kanren 合一拼图板（Unification Chain Board）+ 消融开关**：
   - 提供 5 个有向槽位：`[槽1: 旧志先例] ──(==)──> [槽2: 收缴/代管单] ──(==)──> [槽3: 车间配料卡] ──(==)──> [槽4: 302队手簿] ──(==)──> [槽5: 现行守山条规]`；
   - 玩家点击桌面卡片可填入槽位（或一键「自动按 Kanren 求解排布」）；
   - **实时 Unification 校验器**：每两个相邻槽位之间显示合一状态：
     - 若 `slot[i].to` 与 `slot[i+1].from` 严格相等，显示绿色 `✓ 合一成功` 及共享的中间元组（如 `{:scrap_batch, "滇垦废字（79）第04号", :high_tin_bronze, :c0_collapsed, {:tau_breaths, 3}}`）；
     - 若玩家把**材质干扰卡（`:material_mismatch`）**放入槽 3，槽 3↔4 之间立即亮起红色告警：`✗ 合一失败: {:tau_breaths, 0} =/= {:tau_breaths, 3}（生铁无阻尼，无法与手簿谐振要求合一）`；
     - 若玩家把**外运干扰卡（`:destination_dead_end`）**放入槽 2，槽 2↔3 之间立即亮起红色告警：`✗ 合一失败: :scrap_shipment =/= :scrap_batch（废料已外运省局，未进本场车间）`；
   - **单跳消融开关（Ablation Toggle）**：每个槽位旁有一个「✂️ 抽掉此跳」按钮，点击后实时模拟 `Pathway.connect_edges(remaining)`，顶部立即显示 `路径断裂 (Connected Path: []) —— 证明无任何单卡可跨跳剧透`。
4. **右侧下栏：车间实物指认与相位拨盘验证（The Obra Dinn Verdict）**：
   - 展示车间/山口里的 **3 件候选实物**（1 件真载体 `vessel_name (vessel_code)`、1 件生铁/毛石干扰载体 `decoy_a_vessel_name (decoy_a_vessel_code)`、1 堆外运废料回单 `decoy_b_batch`）+ **1 个周期刻度拨盘（Tick 1..39，标出起振区 `28.1%` 旧黄历误区与饱和区 `60.6%` 占空比反转陷阱 `φ=0.50`）**；
   - 玩家选定「目标实物」与「操作刻度第 N 格」后点击 **【执行勘定 / 叩击验证】**，系统核对是否命中当前 seed 的 `(binding.vessel_code, binding.safe_tick)` 并给出动力学反馈说明。

---

## 6. 替换完成后的现行边界（2026-10-30）

§5 是阶段性合流规格，现已进一步收敛为完整替换：

- `Causality`、旧四份全知档案、`claims.tsv`、旧相位转移表与 `archives` 导出全部退役，不再作为兼容层维护；
- `Chronicle` 继续输出地形、四纪历史、结局和结构，不输出叙事档案；`Node` 的语义求解可无 seed，玩家向编译与导出必须有 seed；
- 五跳散文全部迁入 `content/documents/`，`Witness` 不再保存重复模板；每个 `(relic, fate)` 由内容包提供五张主链卡与两张干扰卡；
- `Export` 升级为 schema v2，以必需的 `dossier` 取代 `archives`，并校验 seed、relic/fate、卡片数量与 hop/role；
- `Ascribe / Observation / Facts` 保留，分别承担数值证据反演与运行时结算，不属于旧全局叙事。
