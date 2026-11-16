# 文档索引

> **文档重组与外迁**：2026-11-13 将模组本体与独立 Elixir 关系式盘工具链（`tools/voy_world/`）拆清边界，关系世界模型与五跳卷宗规范迁入 `tools/voy_world/docs/`，挂起构想与 0.1 冻结期快照分别归入 `incubator/` 与 `archive/`；2026-11-16 工具链连同其 `docs/` 下的**设计札记 02 / 05** 与图集整体移出仓库，存档与取回方式见下文「已外迁」小节。本索引只索引留在仓库内的模组本体文档。

## 模组本体（动力学、生态与元叙事）

| 文档 | 主题 | 状态 |
| :--- | :--- | :--- |
| [设计札记 10](design-note-10-meta-trilogy-and-drift.md) | **META / NARRATIVE**：三代语义漂变复盘、去滇西南化与纯虚构发光菌重锚、三部曲元叙事（深山旧梦 → 民科星海 → 终末之诗）、定位裁定（§5：恐怖只活在氛围层与钩子层） | 元叙事锚点 |
| [设计草案 01](design-draft-01-living-world.md) | 总纲：动力学（FHN + M）、Node/格子场与休眠调度、聚落与沉滞心跳、Worldgen 路线、开放待办 | 0.1 结构冻结（ARG-style / 民俗线已与模组运行解耦） |
| [设计札记 06](design-note-06-mycelial-grounding.md) | 生态落地：神经化菌网/AMD、M 动力学、感官签名、维护设施工单 | 实施规格 |
| [设计札记 07](design-note-07-pulse-coupling.md) | 脉冲耦合链路与相位同步（自动化心跳 / 体外起搏涌现） | 事件层规范 |
| [设计札记 08](design-note-08-fast-variable-and-stagnation.md) | 心跳脉冲语义：快变量读数、三正交轴与 `PulseStamp` 极简脉冲短窗（同步除颤分诊） | **已被 11 取代（历史存档）** |
| [设计札记 11](design-note-11-heartbeat-shelter.md) | **心跳即庇护**：节律轴 `RhythmVerdict`（Warming/Beating/Fast/Slow/Arrhythmic）、四裁决敲钟（起搏/校拍）、c0 源驱动库存与 U 形余脉弧、颜色语言 | **已定稿落地** |
| [设计札记 12](design-note-12-canonical-semantics.md) | **语义宪章**：一物一名四法则与核心词表、改名成本三档、敲钟 v2（钟即起搏器，去相位门控 + 事后回声）、内核只发原子事实（遥测/变更/回执三通道）与多人预留 8 条（含粒度） | **草案（S1 敲钟 v2a 已落地；3 处待拍板）** |

## 孵化构想与历史归档

| 文档 | 主题 | 状态 |
| :--- | :--- | :--- |
| [设计札记 09](incubator/design-note-09-mountain-dial.md) | 制式验山盘：有限域约束弛豫仪（定针/游核、同相互耗、盘契约、FORCEDENTRY 保留条款） | **挂起（第一阶段不实现）**；待拆清「体外自适应起搏器」与「24 候选推理盘」归属 |
| [验证报告 01](archive/verification-report-01-mandate-slice.md) | 0.1 纵切片数值验证与溯因 | 历史快照（回归参考） |

## 参考材料（来源稿与实测出处）

| 文档 | 主题 | 状态 |
| :--- | :--- | :--- |
| [心跳即庇护迁移计划](reference/heartbeat-shelter-migration-plan-2026-11-16.md) | 札记 11 的来源工作稿：14 条裁决的讨论过程、数值依据、按文件改动清单、分步实施与代码核实补丁 | 历史工作稿（正文冻结，不随札记 11 后续措辞同步） |

## 已外迁：独立工具链（2026-11-16）

Elixir 关系世界模型与五跳卷宗规范（**设计札记 02 / 05**）连同图集，原在 `tools/voy_world/docs/`；该包与模组运行时零耦合，已整体移出仓库，待最小循环跑通后作为独立工程继续开发。

- 存档：`D:\CodeRepo\MinecraftDev\variations-of-yesterday-archive\voy_world-2026-11-16.zip`
- 取回：`git checkout ffd0a70 -- tools/voy_world`，或解压存档后 `mix deps.get && mix test`
- 两篇札记与图集在压缩包的 `voy_world/docs/` 下（原札记 03 并入 05 §4–§5，原札记 04 并入 02 §3.1 与 §5）；包内 `ARCHIVE-NOTE.md` 有完整说明。

- 模组阅读顺序：10（创作母题）→ 01 → 06 → 07 → **11（现行庇护语义）**→ **12（现行命名与边界语义）**；08 仅作历史参考；孵化与历史按需查证。
- 写代码/写文案前先看 **12 的 §3 词表与 §3.4 禁止写法清单**；两者冲突时以 12 为准。
