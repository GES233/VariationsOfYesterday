# 旧日变奏曲（Variations of Yesterday）

[English](README.md)

这是一个基于 **Minecraft 1.21.2（Fabric / Scala 3）** 的生态模拟与碎片化叙事模组。世界由可激发介质快慢变量动力学系统（**Mandate Engine**）驱动；玩家作为「调制者」，结合动力学物候读数与五跳局部证据链，在既定历史与物理约束下安抚并维系山谷生态。

## 核心模块

- **动力学内核（`src/main/scala/.../inner/mandate/`）**：FHN 快子系统 + 土地应力慢子系统（滞后效应与宽容区）、有向平流扩散格子场（`MandateGrid`）、Pekko typed actor tick 事件驱动调度（`MandateAgent`）、语义化四相与慢变量分级读数（`MandateReadout`）及跨栈数值契约（`SliceContract`）。
- **游戏侧桥接（`MandateRuntime` / `MandateOverworld` / `MandateHud`）**：采集采掘、敲钟与验山读表交互，维护 $c_0$ 庇护与主世界「沉滞」状态，提供开发期 `/voy hud` 覆层。
- **关系世界模型与内容管线（`tools/voy_world/`）**：独立 Elixir + microKanren 包，负责 48 种地形分类与四纪六结局编译（`Chronicle`）、mediKanren 五跳局部证据链与种子物证绑定（`Pathway` / `Witness` / `Content`）、三方观测反演与运行时事实结算（`Observation` / `Ascribe` / `Facts`）。

## 设计文档（`docs/`）

- [设计草案 01 — 活着的世界](docs/design-draft-01-living-world.md)
- [设计札记 02 — Kanren 关系世界模型与四纪编译器](docs/design-note-02-kanren-worldview-engine.md)
- [设计札记 03 — 内容流水线：局部单据与机器拼接](docs/design-note-03-authoring-pipeline.md)
- [设计札记 04 — 可达性解耦与六种历史结局](docs/design-note-04-quiet-fates-and-access-decoupling.md)
- [设计札记 05 — mediKanren 五跳局部微链与种子物证绑定](docs/design-note-05-medikanren-microchains.md)
- [验证报告 01 — 0.1 纵切片的最短可验证闭环](docs/verification-report-01-mandate-slice.md)

## 常用命令

```sh
# 模组构建与测试
./gradlew test
./gradlew build
./gradlew runClient

# Elixir 关系沙盘与内容包检查
cd tools/voy_world
mix test
mix voy.content lint
mix voy.content preview baishui --seed 101
```

## 许可证

All Rights Reserved（ARR，保留所有权利）。
