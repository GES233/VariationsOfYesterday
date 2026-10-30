# 设计札记 03 — 内容流水线：让人写局部证据，让机器拼出因果

> 状态：**五跳内容包已落地**（2026-10-30 替换旧四卡全局摘要）。实现见 `tools/voy_world/`：`Voy.World.Content`、`Voy.World.Pathway`、`Voy.World.Witness`、`content/` 与 `mix voy.content`。
> 定位：作者只写局部单据与散文；关系层负责桥接元组，种子层负责物证，检查器负责覆盖与一致性。

## 0. 为什么替换旧内容包

旧内容包把清丈旧案、现代收缴、点炮、相位与结局写进同一张「全知摘要」，作者看似只改散文，实际上仍在维护全局因果结论。这与五跳模型的目标冲突：故事不应存在于任何一张卡中，而应由局部证据首尾合一后涌现。

因此旧 `claims.tsv`、四类全局档案、`Projection.compile_archiveso` 与 `Causality` 均已退役。`Chronicle` 继续负责地形、四纪历史、结局与结构；`Pathway + Witness + Content` 接管全部玩家叙事。

## 1. 三层分工

| 层 | 谁 | 碰什么 | 变更 |
| :--- | :--- | :--- | :--- |
| 内容 | 作者 | `content/documents/**/*.md` | 新增或修改局部卡片 |
| 结构 | 技术维护者 | 五跳边、桥接元组、地貌、纪元、结局、物证池、数值契约 | 改代码并评审 |
| 校验 | 机器 | 内容检查、五跳检查、测试与 CI | 自动 |

作者可以改文风与局部事实，不能自行增加 hop、古器、结局、干扰类型或安全窗口。这些是结构变更。

## 2. 卡片格式

每份档案是一张 Markdown 卡：`---` 头部平铺 `key: value`，下面是正文。

主链 Hop 2 示例：

```
---
hop: 2
role: main
relic: ancestral_bronze_zheng
c0_status: c0_collapsed
title: 物资回收与代管过磅单
---

{{place.up}}旧铜器编入批号 {{batch_id}}……
```

干扰卡示例：

```
---
role: decoy
relic: ancestral_bronze_zheng
decoy_class: material_mismatch
title: 机修车间翻砂配料卡（另册）
---

普通生铁浇铸{{decoy_a_vessel_name}}……
```

条件规则：

- Hop 1、3：`hop / role / relic / title`；
- Hop 2：另写 `c0_status`；
- Hop 4、5：另写 `fate`；
- 干扰卡：`role: decoy`，写 `relic / decoy_class / title`，不写 hop 或结局。

当前每个 `(relic, fate)` 都选择五张主链卡与两张干扰卡，共 12 组组合、36 份作者卡片。

## 3. 机器侧职责

1. **关系拼接**：`Pathway` 只用 `from/to` 桥接元组拼链；删任一跳即断，材质不符和外运单据不能补链。
2. **唯一选择**：`Content.card_for/2` 按 hop、角色、古器、结局、c0 与干扰类型选择恰好一张卡；缺卡和多卡都是错误。
3. **种子物化**：`Witness` 绑定批号、器物和实测推迟刻度，再渲染卡片占位符。
4. **内容检查**：未知字段、必填缺失、非法词表、未绑定占位符、写死年份、重复适用和覆盖缺口均报中文 `file:line`。
5. **叙事检查**：`Canon.check_pathways/1` 验证唯一成链、单跳消融、干扰排斥、单卡认知局部性与日期归属。

## 4. 实测安全刻度

安全操作不再由「绝对不应期」标签直接推出。Scala 在 `PhaseResponse` 中逐整数 tick 测量金属共振的真实尖峰响应，写入 `contracts/mandate-slice-v1.json` 的 `safe_ticks`。只有 `Delayed` 结果可进入 `Witness` 候选；`Premature`、`Absorbed` 与其他结果均不可称为安全操作。

因此档案中的第 N 格同时受世界种子与数值契约约束，而不是作者手写的一条口诀。

## 5. 工作流

```sh
mix voy.content lint
mix voy.content preview [baishui|heishui|xuanguan|nuoliang|mahao|tingshan|all] [--seed N]
mix voy.content template <hop1|hop2|hop3|hop4|hop5|decoy>
```

作者只改 `content/`；CI 运行格式、测试与内容检查。技术维护者评审结构，编辑评审文风与单卡认知边界，机器检查关系与覆盖，三者互不替代。

## 6. 与导出及 JVM 的关系

`Voy.World.Export` 当前为 **schema v2**。节点以 `dossier` 取代旧 `archives`，携带 seed、binding、五张主链卡和两张干扰卡，并校验其与历史中的 relic/fate 一致。JVM 只读导出物，不直接读取 Markdown。

`Ascribe / Observation / Facts` 仍负责数值证据反演与运行时预测结算，不属于被退役的全局叙事模型。
