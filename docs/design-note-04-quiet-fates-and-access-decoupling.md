# 设计札记 04 — 可达性解耦与两种「未爆发的结局」

> 状态：**已落地**（2026-10-27）。配套实现见 `tools/voy_world/`：`Chronicle.era2a_magistrateo`（改为按可达性分叉）、`Chronicle.era2b_reclamationo`（可达性 ⟂ 下游地形）、`Chronicle.era3_catastropheo`（+2 分支）、`Voy.World.Content` 的 `gazetteer_case` 锚定、`Canon.check_archives/1`。
> 定位：把 48 种地形组合里「未决」的 16 格补成可生成世界，并让「手簿引用的先例 = 残卷记载的先例」从作者的自觉变成编译约束。

---

## 0. 起因

设计札记 02 的五阶管线里，`era3` 只有四条手写分支（同化 / 湮灭 / 飞升 / 幸存对照），其余组合返回 `:unsupported_topography` 或 `:unresolved_history`。48 种地形组合的实测分布是：

| 数量 | 原因 |
| :--- | :--- |
| 24 | `(up_terrain, up_geology)` 不在扰动表的三个配对里 |
| 12 | `accessibility` 与 `down_terrain` 被强制耦合 |
| 4 | `era3` 缺两个 `(c0, pulse)` 象限 |
| 8 | 已支持 |

12 + 4 这 16 格不是「设定还没写」，而是「规则把合法组合排除了」。本札记处理的就是这 16 格。

## 1. 可达性解耦：能进与拆器分家

原实现把四件事绑成一条链：

```
down_terrain = alluvial_flat  ⟺  tractor_road_accessible  ⟺  建制化垦殖场  ⟺  c0 被拆
down_terrain = mid_terrace    ⟺  cliff_isolated           ⟺  挂靠林场孤寨  ⟺  c0 完好
```

于是「坝区」必然被拆、「台地」必然保全，两种地形各锁死一半结局。物理上真正决定后果的是**官能不能进得去**，地形只是它的相关物。改动：

- `era2a_magistrateo` 改按 `accessibility` 分叉：能通车 → 清丈局收器/毁堰 → 对应先例；断路 → 胥吏只立归化碑 → 立碑案。
- `era2b_reclamationo` 改按 `accessibility` 分叉：能通车 → 建制化 + 收缴 + `c0_collapsed`；断路 → 挂靠林场 + 保全 + `c0_intact`。

收益：**坝区但断路**（组织进不去，铜钲留在原地）与**台地但通车**（拖拉机道直上，被制度化拆走）成为合法世界。同一结局现在可以长在两种地形上，而内容成本为零——档案按 `(fate, relic)` 索引，本就不看地形。

## 2. 两个缺失象限：未爆发与未成灾

`era3` 的结局由 `(c0 状态, pulse 类型)` 决定。原有四条分支覆盖 4 个象限，剩下两个补上：

| | `alpha_shock`（302 队点炮） | `Overwhelming`（高压注浆） | `heavy_kick`（林场低强度采伐） |
| :--- | :--- | :--- | :--- |
| `c0_collapsed` | 同化 `Oscillating` | 湮灭 `Burnout` | **慢性渗漏 `Strained`** |
| `c0_intact` | **虚警耐受 `Calm`** | 飞升 `Strained` | 幸存对照 `Calm` |

- **`Fate_Deferred_ChronicSeep`（慢性渗漏）**：维持源已毁，但扰动永远不足以点火。世界不振荡也不烧毁，只是一直漏——山鸣年年不断、应力缓慢累积、水位线逐年下降。这是六种世界里**唯一给玩家留时间**的一种，也是 0.1 纵切片最合适的默认开局（先学会读趋势，再去看那四个事后世界）。政体 `deferred_evacuation_watchpost`：两套账本并行，撤离方案待批。
- **`Fate_Tolerated_FalseAlarm`（虚警耐受）**：c0 完好 + 302 队周期点炮撞在易损窗，每炮都被吸收、每次都在临界擦过。它是**对照组**：玩家在别处见过这条时间线死人，这里是同一条时间线没死人的版本。手簿可以写「虚警四十七次，一次也没成灾」——读者自己会想到，若哪天真把 c0 收走，这四十七次全都会变成真的。政体 `cliff_observation_hermitage`。

## 3. 副产品：先例锚定与「印证」检查转真

解耦之前，「(fate, relic) ⟹ 先例」这条定理成立纯属巧合：`c0` 由地形决定，地形又决定先例。解耦之后 `c0` 改由可达性决定，定理若靠运气就会破——某份手簿写着「对照拆钟旧案」，却被配到立碑案。

因此 `official_report` / `tech_note` 增加可选字段 `gazetteer_case`：**写了就只在该先例下适用**。现有 6 份技术手簿全部钉死（4 份按古器钉在铜钲案/石堰案，2 份钉在立碑案），官方通报保持通配。于是：

- 投影片 `Projection.compile_archiveso` 与装配器 `Content.archives_for` 在选择时都带上先例条件，钉错的手簿匹配不到任何历史 → 该地形直接编不出来；
- `Canon.check_attribution_split` 里原先恒真的「手簿必须印证残卷」分支**变成真的检查**，因为 `corroborated_gazetteer` 现在取自文档声明，而不是切片自己；
- 新增 `Canon.check_archives/1`：把「规则能配对但没有档案可用」与「地质根本不在扰动表里」两种失败分开报告——否则档案缺口会伪装成 `:unsupported_topography`。

覆盖检查（`count_matches`）仍只看槽位声明的维度，不把 `gazetteer_case` 纳入笛卡尔积；否则每加一个先例都要 4×2×3 份档案，内容成本立刻失控。

## 4. 结果与代价

| 指标 | 改前 | 改后 |
| :--- | :--- | :--- |
| 可生成世界（48 种组合中） | 8 | 24 |
| 结局种类 | 4 | 6 |
| 幽灵分类 `:unresolved_history` | 4 格 | 0 格（代码路径保留，当前不可达） |
| 档案卡 | 17 | 24（+7） |
| 归因卡 | 8 | 12（+4） |
| 结构文本（`Texts`） | 8 | 10 |

`:unresolved_history` 归零是这次改动的直接后果，不是遗漏：只要六个 `(c0, pulse)` 象限都有分支，就不存在「第二纪有了、第三纪没写」的地形。该分类保留在 `Voy.World.compile_branch/1` 与 `Node.classify_failure/2` 里，供将来扩象限时继续使用。

## 5. 有意不做的部分

**24 格地质错配仍然不可生成**（`steep_fissure + deep_stress_cavity`、`karst_sinkhole + active_fault_vein`、`karst_sinkhole + minor_fracture`）。这三种配对在故事上有价值——溶蚀慢渗可以喂「慢性渗漏」、断层微震可以喂「虚警」、空腔塌陷可以给你一个**没有人类、连账本都写不出归因**的结局——但它们要改 `Dynamics.disturbance_pulseo` 的语义（引入无脉冲与随机时机两类通道），属于 v2。理由与 JVM 迁移相关：迁移需要一份冻结的 v1 语义面，现在这 24 格是清晰的「不生成」，而不是待填的坑。

## 6. 对 JVM 迁移的影响

- `schema_version` 仍是 **1**，但它的**语义面变大了**：6 个结局、6 种政体、24 种可生成地形。迁移应以本次冻结的 24 格为准（`test/fixtures/world_cases.json` 的 reverse 段已扩为六结局 × 四地形）。
- 若在迁移**之后**再补地理三通道，应当升到 v2；若在此之前补，仍可留在 v1 里一次性对齐。
- 结局到 `Profile.regime/3` 的映射没有新增分区：慢性渗漏落 `Strained`，虚警耐受落 `Calm`。JVM 侧的 `MandateReadout` 只需复刻这四个分区，不必扩词表。
