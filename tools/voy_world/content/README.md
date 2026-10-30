# 五跳内容包（给写作者）

这个目录是《旧日变奏曲》的**唯一叙事内容层**。每份文件只写一条局部证据，不写全知摘要；完整故事由 `Pathway` 通过桥接元组拼出，批号、器物和勘定格由 `Witness` 按世界种子绑定。

```
content/documents/
  main/hop1/       旧志先例
  main/hop2/       收缴或代管单
  main/hop3/       车间配料卡
  main/hop4/       野外观测手簿
  main/hop5/       现行条规
  decoy/           两类无法走通全程的干扰单据
```

## 写作纪律

1. **一卡只知一跳**：不要在一张卡里补全上下游全部真相。
2. **不要写地名和种子物证**：用 `{{place.up}}`、`{{place.pass}}`、`{{place.down}}`、`{{batch_id}}`、`{{vessel_name}}`、`{{vessel_code}}`、`{{safe_tick}}` 等占位符。
3. **不要写死年份**：年代属于结构层。正文中的四位或两位数字年份会被检查器拒绝。
4. **不要改 `lib/`**：新增 hop、古器、结局或干扰类型属于结构变更，需要技术维护者同步修改关系规则。

## 主链卡

```markdown
---
hop: 2
role: main
relic: ancestral_bronze_zheng
c0_status: c0_collapsed
title: 物资回收与代管过磅单
---

{{place.up}}旧铜器编入批号 {{batch_id}}……
```

- Hop 1、3：只写 `hop / role / relic / title`。
- Hop 2：另写 `c0_status: c0_collapsed` 或 `c0_intact`。
- Hop 4、5：另写 `fate`，使用六种既有结局之一。

## 干扰卡

```markdown
---
role: decoy
relic: ancestral_bronze_zheng
decoy_class: material_mismatch
title: 机修车间翻砂配料卡（另册）
---

普通生铁浇铸{{decoy_a_vessel_name}}……
```

`decoy_class` 目前只有 `material_mismatch`（材质不合，Hop 3→4 断链）和 `destination_dead_end`（材料外运，Hop 2→3 断链）。

## 工具

```sh
mix voy.content lint
mix voy.content preview baishui --seed 101
mix voy.content template hop2
mix voy.content template decoy
```

检查器覆盖全部 12 个 `(relic, fate)` 组合，要求每个组合恰好选出五张主链卡和两张干扰卡，并检查未知字段、条件重叠、缺卡、非法词表、未绑定占位符和写死年份。Windows 换行、BOM、注释行与空行均可接受。
