# AGENTS.md — Variations of Yesterday（旧日变奏曲）

## 项目概览

这是一个 **Minecraft Fabric 模组**，处于非常早期的开发阶段。当前创作方向（讨论中，尚未定型）：**中式民俗恐怖 + ARG 碎片化叙事的「活着的世界」生态模拟模组**。世界本身是一个由核心「Mandate Engine」驱动的动力学系统（可激发介质 / 快慢变量 + 滞后效应），会因过度扰动跨越临界点而发生生态级灾变；玩家的核心目标是「调制与维稳」——实事求是地监测并安抚这片土地，而非征服它。叙事层面以虚构的近现代（1980s）滇西南风格山区为背景，通过档案、报表等 ARG 碎片呈现现代工程与本土祭祀逻辑的碰撞（全部地理/民族/仪式/事件均为虚构，不指涉真实存在）。

注意：早期概念稿已废弃删除；旧设定中「残缺蓝图 / 崩坏与修复循环 / 调试者」的表述不再适用，但 Mandate Engine 的技术骨架（FHN 型 ODE）沿用于新方向。设计文档目录为 `docs/`。

- 模组 ID：`variations-of-yesterday`
- 包名：`moe.chestnut.awa.voy`
- 版本与坐标：`gradle.properties`（`mod_version=0.1.0`，`maven_group=moe.chestnut.awa.voy`）
- 许可证：CC0-1.0
- 仓库：https://github.com/GES233/VariationsOfYesterday

## 技术栈

- **语言**：以 **Scala 3**（3.6.3，缩进语法 / 显著缩进风格）为主，Mixin 用 Java 编写。
- **平台**：Minecraft 1.21.2 + Yarn mappings + Fabric Loader 0.16.10 + Fabric API 0.106.1+1.21.2，Java 21。
- **Scala 支持**：通过 `com.kotori316:scalable-cats-force-fabric`（Kotori Scala，`kotori_scala`）加载，`fabric.mod.json` 中的入口点使用 `"adapter": "kotori_scala"`。
- **并发模型**：**Apache Pekko**（typed actor），`pekko-actor-typed` + `pekko-slf4j`，测试用 `pekko-actor-testkit-typed`。目前 ActorSystem 尚未接线（`VariationsOfYesterday.scala` 中有 TODO）。
- **函数式库**：cats-core / cats-kernel（kotori 定制版本，来自 kotori316 的 Maven 仓库）。

## 构建与运行

使用 Gradle Wrapper（Windows 下用 `gradlew.bat`，Unix 下用 `./gradlew`）：

- 构建：`./gradlew build`（产物在 `build/libs/`，包含 remap 后的模组 jar）
- 清理：`./gradlew clean`
- 测试：`./gradlew test`（JUnit Platform）
- 运行客户端：`./gradlew runClient`（运行目录为 `run/`）
- 运行服务端：`./gradlew runServer`
- 数据生成：`./gradlew runDatagen`（Fabric datagen 已配置为 client 侧运行，输出到 `src/main/generated`）

CI：GitHub Actions（`.github/workflows/build.yml`），在 push / PR 时于 Ubuntu + JDK 21 (Microsoft 发行版) 上执行 `./gradlew build` 并上传 `build/libs/` 构件。

## 代码组织

Loom 配置了 **splitEnvironmentSourceSets**（`main` / `client` 分离），每个 source set 同时包含 `java` 和 `scala` 目录：

- `src/main/scala/moe/chestnut/awa/voy/`
  - `VariationsOfYesterday.scala` — 模组主入口（`ModInitializer`），目前仅打印日志
  - `inner/` — 模组核心内部逻辑（不依赖 Minecraft API 的部分尽量放这里）：
    - `mandate/` — Mandate Engine：`MandateState`（FitzHugh–Nagumo 快子系统 + 土地应力慢子系统：状态 `dissonance/entrench/stress/activity`，参数 `MandateParam`/`StressParam`，外部输入通道 `disturbance/calming/baselineCalming`）、`MandateAgent`（固定步长累积器的模拟循环）
    - `event/` — 事件协议：`EventDTO` sealed trait 及事件消息（`TickArrived` 等），`EventMapper`（占位）
    - `action/` — 动作协议：`MandateActionProtocol` sealed trait 及动作消息
    - `plant/` — 植物 DTO（占位）
  - `helpers/ODESolver.scala` — 通用 ODE 求解器（Euler / RK4），配合 `NumericTuple` trait 使用
- `src/client/scala/...` — `VariationsOfYesterdayClient`（`ClientModInitializer`）、`VariationsOfYesterdayDataGenerator`（datagen 入口）
- `src/main/java` / `src/client/java` — Mixin（目前是模板自带的 `ExampleMixin` / `ExampleClientMixin`）
- `src/main/resources/fabric.mod.json` — 模组清单（入口点、mixin 配置、依赖）；版本号由 `processResources` 注入
- `src/main/resources/variations-of-yesterday.mixins.json` 与 `src/client/resources/variations-of-yesterday.client.mixins.json` — Mixin 配置
- `src/test/scala/` — 测试代码目录（当前仅有空目录骨架 `core/calendar`，尚无测试）
- `docs/` — 中文设计文档；当前有效的是 `docs/design-draft-01-living-world.md`（新方向草案：动力学/河流/聚落/ARG），旧概念稿已废弃清空
- `run/` — 本地开发运行的游戏目录（存档、日志、配置），不要提交修改

## 开发约定

- **Scala 风格**：Scala 3 显著缩进（无大括号）；顶层 `object` 作为 Fabric 入口点；消息协议用 `sealed trait` + 伴生对象中的 `final case class`（见 `EventMsg`、`ActionMsgPool`）。
- **入口点**必须保持 `object Xxx extends ModInitializer/ClientModInitializer/DataGeneratorEntrypoint` 形式，并与 `fabric.mod.json` 中注册的全限定名一致（adapter 为 `kotori_scala`）。
- 事件与动作用 DTO 隔离：`EventDTO`（入）与 `MandateActionProtocol`（出）分层，模拟核心不直接触碰游戏上下文。
- Mixin 用 Java 编写，放在 `mixin` / `mixin.client` 包下，并在对应 mixin json 中注册。
- 新增版本号、依赖版本统一改 `gradle.properties`，不要在 `build.gradle` 中硬编码。
- 项目注释使用英文；设计文档与 README 使用中文（README.md 为英文，README-zh.md 为中文）。

## 测试

- 测试框架：JUnit Platform（`useJUnitPlatform()`，JUnit Jupiter 依赖已声明）+ Pekko typed actor testkit，测试代码放在 `src/test/scala`。
- 已有测试：`inner/mandate/MandateStateSpec`（复现设计草案 §1.1 的数值结论：阈值点火、自持、滞后熄灭、宽容区，以及 `simulationLoop` 子步进回归）。核心模拟逻辑（`inner/`、`helpers/`）不依赖 Minecraft，可直接纯单测覆盖。
- **本机环境注意**：系统默认 `java` 是 JDK 25，Gradle 8.12.1 在其上会崩溃（`Type T not present`）；运行 Gradle 需指定 JDK 21，例如 `JAVA_HOME='D:\Q\Scoop\apps\zulu21-jdk\current' ./gradlew test`（CI 用 JDK 21，不受影响）。
- Fabric/Minecraft 集成无法简单单测，验证集成行为请使用 `./gradlew runClient` 手动运行游戏。

## 注意事项

- 构建需要访问 Fabric Maven（`maven.fabricmc.net`）与 Kotori316 Maven（`maven.kotori316.com`）；离线或网络受限环境可能构建失败。
- 项目处于早期阶段，很多文件是模板遗留（如 ExampleMixin、`fabric.mod.json` 中占位 description、suggests 里的 `another-mod`）或 TODO 占位，不要当成已完成的功能。
- `build/`、`.gradle/`、`run/` 等为生成产物，已在 `.gitignore` 中排除。
