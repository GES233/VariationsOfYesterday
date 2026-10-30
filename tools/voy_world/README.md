# Voy World

《旧日变奏曲》的零外部依赖 Elixir 关系世界模型，用于设计验证与导出，不是 Minecraft 模组的运行时依赖。

## 当前架构

- `Voy.Kanren`：microKanren 内核、开放属性、不等约束与 DSL。
- `Voy.World.Chronicle`：保留地形、四纪历史、六种结局与结构的正反向编译。
- `Voy.World.Pathway`：五跳局部证据关系；每张卡只记录一条边，完整故事靠桥接元组合一。
- `Voy.World.Witness`：按 `(seed, branch_id, relic, fate)` 绑定批号、工业载体和实测推迟刻度。
- `Voy.World.Content`：五跳 Markdown 内容包的唯一散文来源；旧四份全知档案与 `claims.tsv` 已退役。
- `Voy.World.Ascribe / Observation / Contract`：数值轨迹、三方观测、证据反演与 Scala 实测契约。
- `Voy.World.Facts`：观测、行动、预测、结算的追加式运行时事实日志。
- `Voy.World.Node / Naming`：稳定节点身份、语义求解、名称与五跳卷宗物化。
- `Voy.World.Export`：确定性 JSON bundle；当前 `schema_version = 2`，节点以 `dossier` 取代旧 `archives`。
- `Voy.World.Canon`：历史、结构、五跳成链、断链、干扰排除、认知局部性和日期检查。

旧 `Voy.World.Causality` 全局三段因果、全局透镜文本与旧相位转移表已删除，不再与五跳模型并存。

## 五跳叙事

每个 `(relic, fate)` 组合固定生成五张主链卡和两张干扰卡：

```text
旧志先例 → 收缴/代管单 → 车间配料卡 → 野外观测手簿 → 现行条规
```

`Pathway.connect_edges/1` 只按 `from/to` 元组拼接，不读结局标签。删去任一跳都会断链；材质不符与材料外运两类干扰无法补链。

安全操作刻度不是从声明相位窗猜出的。Scala `PhaseResponse` 对每个整数 tick 实测脉冲响应，只有结果为 `Delayed` 的刻度才进入 `Witness` 的种子候选集。

## 作者内容包

作者只编辑 `content/`：

```text
content/documents/main/hop1..5/   五类主链卡
content/documents/decoy/          干扰卡
content/README.md                 中文写作说明
```

`Content` 检查全部 12 个 `(relic, fate)` 组合的卡片覆盖与唯一选择，并检查字段、词表、占位符、写死年份和编码。`Witness` 直接渲染这些 Markdown，作者不需要修改 Elixir。

```sh
mix voy.content lint
mix voy.content preview [baishui|heishui|xuanguan|nuoliang|mahao|tingshan|all] [--seed N]
mix voy.content template <hop1|hop2|hop3|hop4|hop5|decoy>
```

## Node 与 Export

语义求解可以不传 seed；玩家向编译、物化和导出必须传整数 seed：

```elixir
spec = [
  node_id: "node_rift_07",
  branch_id: "branch_rift",
  rule_set: :default_branch_v1,
  fate_constraint: {:require, :Fate_Ascended_SpatialRift},
  seed: 101,
  names: [node: "黑水支沟", up: "鬼哭漏斗", pass: "断碑垭口", down: "悬棺寨"]
]

{:ok, candidates} = Voy.World.reverse_node(spec)
{:ok, record} = Voy.World.materialize_node(spec, hd(candidates))
```

`Export.export_branch(topo, seed: 101)` 输出 v2 bundle，包含历史、结构、Profile 和完整 `dossier`。bundle 校验 seed、relic/fate 一致性、五张主链卡、两张干扰卡及 JSON round-trip。

## 验证与演示

```sh
mix test
mix voy.content lint
mix run examples/sandbox.exs
mix run examples/contracts_demo.exs
```

测试覆盖 48 种地形组合（24 可生成、24 不支持）、五跳正反向合一、单跳消融、干扰排除、种子隔离、实测安全刻度、内容检查、Export v2 和事实日志。`test/fixtures/world_cases.json` 同时保存历史语义与种子物证 golden cases。

地貌图仍可用以下工具再生成；图 01–03 是早期概念图，04–07 才反映当前地形编译结果：

```sh
python examples/render_landform_diagrams.py
mix run examples/export_topography_cases.exs
python examples/render_topography_atlas.py
python examples/render_feature_glossary.py
```
