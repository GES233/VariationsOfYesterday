# Voy World

《旧日变奏曲》的独立 Elixir **关系图谱 + 时代认知模型 + 卡片接线沙盘**（不进模组 jar）。

它不负责模拟当前的物理 ODE（那是 Scala `MandateEngine` 的事），只负责回答：**一段跨越不同时代的经历、档案与认知视角，在各自的建模粒度与规则下是否逻辑自洽。**

## 极简架构（仅 4 个核心世界模块）

- `Voy.Kanren`（`lib/voy/kanren/`）：microKanren 关系内核（`eq/2`、`neq/2`、`propso/2`、`conde`、`fresh`）。
- `Voy.World.Graph`（`lib/voy/world/graph.ex`）：mediKanren 风格开放属性图 DSL（`nodeo/5`、`edgeo/6`、`patho/5`、`bridgeo/4`）。
- `Voy.World.Epistemic`（`lib/voy/world/epistemic.ex`）：时代认知模型层（概念允许/禁止词表、机构 `neq/2` 认知防火墙、年代顺序与接线校验）。
- `Voy.World.Store` + `Voy.World`（`lib/voy/world/store.ex`、`lib/voy/world.ex`）：读取和保存人类可直接编辑的 `world/eras.json` 与 `world/graph.json`。
- `Voy.World.Web.*` + `Mix.Tasks.Voy.Web`：`Plug + Bandit` 本地服务器与桌面卡片接线画布。

## 可直接编辑的数据

- `world/eras.json`：定义各个时代的顺序、建模粒度说明、允许/禁止概念词表、以及观察者 `neq` 认知防火墙。
- `world/graph.json`：定义节点（`nodes`）与档案卡片边（`edges`）。既可直接手改 JSON，也可以在 WebUI 里拖拽、接线、修改并一键保存回磁盘。

## 常用命令

```sh
mix test
mix voy.web --port 4040 --open
```

## 历史文档参考

- `docs/` 下保留了此前演进阶段的设计札记（02 / 05）与图表，作为背景参考；当前代码已精简为上述 Graph + Epistemic 沙盘架构。
