# 五跳内容包写作指南

本目录下的 `content/documents/` 是唯一的叙事散文来源。每份卡片只记录一条局部单据，完整因果由 `Voy.World.Pathway` 按桥接元组合一拼出，批号、载体与勘定格由 `Voy.World.Witness` 按世界种子注入。

## 目录与卡片头部规则

| 路径 | 角色与跳数 | 必填头部字段（`---` 包围） |
| :--- | :--- | :--- |
| `main/hop1/*.md` | 旧志先例（Hop 1） | `hop: 1`, `role: main`, `relic`, `title` |
| `main/hop2/*.md` | 收缴/代管单（Hop 2） | `hop: 2`, `role: main`, `relic`, `c0_status`, `title` |
| `main/hop3/*.md` | 车间配料卡（Hop 3） | `hop: 3`, `role: main`, `relic`, `title` |
| `main/hop4/*.md` | 野外观测手簿（Hop 4） | `hop: 4`, `role: main`, `relic`, `fate`, `title` |
| `main/hop5/*.md` | 现行守山条规（Hop 5） | `hop: 5`, `role: main`, `relic`, `fate`, `title` |
| `decoy/*.md` | 断链干扰卡 | `role: decoy`, `relic`, `decoy_class`, `title` |

- `relic`：`ancestral_bronze_zheng` | `carved_diversion_stele`
- `c0_status`：`c0_collapsed` | `c0_intact`
- `decoy_class`：`material_mismatch` | `destination_dead_end`

## 写作纪律与可用占位符

1. **单卡只知一跳**：严禁在一张卡内同时写出古器原名、流转批号 `{{batch_id}}` 与载体图号 `{{vessel_code}}`；`{{batch_id}}` 仅限 Hop 2/3，`{{vessel_code}}` 仅限 Hop 3/5，占空比 `{{onset_percent}}`（`28.1%`）与 `{{settled_percent}}`（`60.6%`）仅限 Hop 4。
2. **禁止写死地名、物证与数字年份**：使用以下占位符：
   - 地点：`{{place.up}}`、`{{place.pass}}`、`{{place.down}}`
   - 真链物证：`{{batch_id}}`、`{{vessel_name}}`、`{{vessel_code}}`、`{{safe_tick}}`、`{{safe_phase}}`
   - 契约实测值：`{{onset_period}}`、`{{onset_percent}}`、`{{onset_ticks}}`、`{{settled_period}}`、`{{settled_percent}}`、`{{settled_ticks}}`、`{{settled_delay}}`
   - 干扰物证：`{{decoy_a_batch}}`、`{{decoy_a_vessel_name}}`、`{{decoy_a_vessel_code}}`、`{{decoy_b_batch}}`

## 校验与预览

完整语法与可用分支见[札记 05 §5](../../../docs/design-note-05-medikanren-microchains.md#5-常用命令)；日常用 `mix voy.content lint` 与 `mix voy.content preview all --seed 101`。
