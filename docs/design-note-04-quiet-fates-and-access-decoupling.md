# 设计札记 04 — 可达性解耦与六种历史结局

> 状态：**现行规范**。实现见 `tools/voy_world/lib/voy/world/chronicle.ex`。

## 1. 地形组合空间（24 / 48 可生成）

支沟地形由 5 个离散特征描述：$2 \text{ 上游地形} \times 2 \text{ 隘口} \times 2 \text{ 下游地形} \times 3 \text{ 源头地质} \times 2 \text{ 可达性} = 48$ 种组合：

- **24 种可生成世界**：源头地形与源头地质落在三条合法物理配对内（`steep_fissure + active_fault`、`karst_sinkhole + deep_stress_cavity`、`steep_fissure + minor_fracture`），每个配对跨越 2 种隘口 × 2 种可达性 × 2 种下游地形，全部编译出唯一历史切片。
- **24 种不支持地形（`:unsupported_topography`）**：地质与源头地形未配对的 3 组组合，留待后续扩展独立扰动通道。

## 2. 可达性解耦：道路决定 $c_0$ 存亡

古器（铜钲/分水石堰）是否被收缴拆毁，取决于**外部行政力量能否通车进入**（`accessibility`），而非下游聚落是平坝还是台地：

- `tractor_road_accessible`（机耕道可达）：第二纪上清丈局入寨收器触发反噬，退守「外甲内寨」；第二纪下改组为国营垦殖分队，古器按废料收缴（`c0_collapsed`），山官后裔贬为仓库保管员。
- `cliff_isolated`（绝壁隔绝）：第二纪上胥吏止步崖下立归化碑；第二纪下仅挂靠林场联营，主器留存原地未拆（`c0_intact`），山官保留祭祀权。

由此，「平坝但断路」与「台地但通车」均成为合法世界，同一结局可在两种下游地形上生成。

## 3. 六种历史结局（$c_0 \times \text{Pulse}$ 完整象限）

第三纪结局由 `(c0_status, pulse)` 的 $2 \times 3$ 笛卡尔积完整覆盖：

| $c_0$ 状态 \ 源头脉冲 | `alpha_shock`<br>（302 队浅层点炮） | `OverwhelmingNegativeSuppression`<br>（总指挥部高压反向注浆） | `heavy_kick`<br>（林场水文哨低强度采伐） |
| :--- | :--- | :--- | :--- |
| **`c0_collapsed`**<br>（通车，主器收缴） | **同化新寨**<br>`Fate_Assimilated_NewZhai`<br>终态：`Oscillating`<br>政体：`brigade_elder_joint_council` | **烧毁死寂区**<br>`Fate_Annihilated_BurnoutZone`<br>终态：`Burnout`<br>政体：`depopulated_concrete_sealed_zone` | **慢性渗漏**<br>`Fate_Deferred_ChronicSeep`<br>终态：`Strained`<br>政体：`deferred_evacuation_watchpost` |
| **`c0_intact`**<br>（绝壁，主器留存） | **虚警耐受**<br>`Fate_Tolerated_FalseAlarm`<br>终态：`Calm`<br>政体：`cliff_observation_hermitage` | **空间裂隙**<br>`Fate_Ascended_SpatialRift`<br>终态：`Strained`<br>政体：`rift_guarding_cliff_compact` | **幸存对照**<br>`Fate_Ancestral_ControlGroup`<br>终态：`Calm`<br>政体：`restored_ancestral_compact` |

其中两类「未爆发」结局补齐了叙事对照：
- **慢性渗漏（`Fate_Deferred_ChronicSeep`）**：维持源已毁，但林场低强度扰动不足以点火；山鸣不断、累积应力逐年抬高，两套账本并行、撤离方案待批，是给玩家留出充裕观察期的开局形态。
- **虚警耐受（`Fate_Tolerated_FalseAlarm`）**：$c_0$ 完好，302 队四十七次周期点炮全被吸收而未成灾，作为同化新寨线的平行对照组。
