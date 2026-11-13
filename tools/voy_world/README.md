# Voy World

《旧日变奏曲》的零外部依赖 Elixir 关系世界模型，用于设计验证、内容检查与 JSON 导出（不作为模组运行时依赖）。

## 模块职责

- `Voy.Kanren`：microKanren 内核、开放属性匹配（`propso/2`）、不等约束（`neq/2`）与 DSL。
- `Voy.World.Chronicle` + `Projection`：5 项离散地形特征 $\leftrightarrow$ 四纪历史、六种结局与三处遗址结构的正反向编译。
- `Voy.World.Pathway`：mediKanren 五跳局部证据关系；仅凭 `from/to` 桥接元组合一拼链，删去任一跳即断链。
- `Voy.World.Witness`：按 `(seed, branch_id, relic, fate)` 绑定批号、工业载体和 `Contract` 实测 `Delayed` 安全刻度（`safe_ticks`）。
- `Voy.World.Content`：加载并校验 `content/documents/**/*.md`（32 张主链卡 + 4 张干扰卡）。
- `Voy.World.Slice / Observation / Ascribe / Contract`：读取 Scala 实测契约，模拟三方制度观测变换并在 24 候选空间内按证据与分辨率给出唯一/多解/缺证/矛盾四型反演。
- `Voy.World.Profile / Schema / Projection / Timeline / Texts / Dynamics`：跨栈 Regime 阈值镜像、地形 schema 与特征校验、结构投影、年代时间线、文本校验、相位/Regime 词汇表。
- `Voy.World.Fields`：keyword / atom-string map 的统一字段访问（`fetch` / `get` / `has?` / `fetch_opt`）。
- `Voy.World.Facts`：观测、行动、预测与结算的追加式运行时事实日志。
- `Voy.World.Node / Naming / Export`：稳定节点身份、种子卷宗物化与确定性 JSON bundle（`schema_version = 2`）。
- `Voy.World.Canon`：时间线、结构、地形覆盖及五跳成链/消融/干扰排斥/单卡认知局部性静态检查。
- `Voy.World`：JSON 边界门面（`compile_node` / `compile_branch` / `settle_open_dossier`）；Mix 任务 `mix voy.content` 见下。

## 设计文档

- [设计札记 02：关系世界模型（四纪编译、地形组合与六结局）](docs/design-note-02-kanren-worldview-engine.md)
- [设计札记 05：五跳卷宗与内容管线（微链、种子物证与写作规范）](docs/design-note-05-medikanren-microchains.md)
- [五跳内容包写作指南](content/README.md)
- [地形与结局图集](docs/diagrams/)

## 常用命令

```sh
mix test
mix voy.content lint
mix voy.content preview [baishui|heishui|xuanguan|nuoliang|mahao|tingshan|all] [--seed N]
mix voy.content template <hop1|hop2|hop3|hop4|hop5|decoy>
mix run examples/sandbox.exs
mix run examples/contracts_demo.exs
```

## 图表生成

```sh
mix run examples/export_topography_cases.exs
python examples/render_landform_diagrams.py
python examples/render_topography_atlas.py
python examples/render_feature_glossary.py
```
