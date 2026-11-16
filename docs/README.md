# 文档索引

> **2026-11-16 文档收口**：为降低维护与接手成本，现行有效文档已收束为根目录两份简明文档；所有带编号的历史设计札记与工作稿已统一迁入 `docs/archive/`，仅供追溯历史演进，不再作为当前实现或文案的依据。

## 现行文档（先看这两份）

| 文档 | 面向读者 | 内容 |
| :--- | :--- | :--- |
| [当前架构与工程边界](CURRENT_ARCHITECTURE.md) | 工程师 | 数据流、三轴语义、敲钟现行规则、双默认陷阱、冻结边界与未实施项清单 |
| [创作圣经](CREATIVE_BIBLE.md) | 策划 / 美工 / 剧情写手 | 世界观定稿、三条红线、玩家侧物候因果、标准词表与禁写清单、色板与资产台账 |
| [发版节奏与世界模式规划](ROADMAP_AND_MODES.md) | 制作人 / 策划 / 工程 | 0.1 引流期收尾清单、后续「生态漫游（一般模式）」建档二选一方案与预留接缝 |

## 历史归档（`docs/archive/`，仅供考古）

| 文档 | 原定位与说明 |
| :--- | :--- |
| [设计札记 12](archive/design-note-12-canonical-semantics.md) | 语义宪章与重构路线（S1 敲钟去相位、S2b 改名已落地；§5 原子事实流与 S3/S4/S5 尚未实施） |
| [设计札记 11](archive/design-note-11-heartbeat-shelter.md) | 「心跳即庇护」迁移定稿与 16 条裁决记录 |
| [心跳即庇护迁移计划](archive/heartbeat-shelter-migration-plan-2026-11-16.md) | 札记 11 的原始工作稿（内含 S2b 改名前的旧符号名） |
| [设计札记 10](archive/design-note-10-meta-trilogy-and-drift.md) | 「三重梦境」三部曲元叙事构想与三代语义漂变复盘 |
| [设计札记 08](archive/design-note-08-fast-variable-and-stagnation.md) | 旧「脉冲庇护窗」方案（已被札记 11 取代） |
| [设计札记 07](archive/design-note-07-pulse-coupling.md) | 水晶脉冲耦合链路与 E1/E2/E4b 实验回执 |
| [设计札记 06](archive/design-note-06-mycelial-grounding.md) | 菌网生物学底盘、感官签名、菌塘及未实施的 WO-2 设施工单 |
| [设计草案 01](archive/design-draft-01-living-world.md) | 0.1 初版总纲（含尚未实现的 Node 休眠调度与已外迁的 ARG 卷宗线） |
| [设计札记 09](archive/design-note-09-mountain-dial.md) | 挂起构想：制式验山盘（第一阶段不实现） |
| [验证报告 01](archive/verification-report-01-mandate-slice.md) | 0.1 纵切片数值验证历史快照 |

## 已外迁：独立工具链（2026-11-16）

Elixir 关系世界模型与五跳卷宗规范（原设计札记 02 / 05）连同图集，原在 `tools/voy_world/`；该包与模组运行时零耦合，已整体移出仓库：

- 存档：`D:\CodeRepo\MinecraftDev\variations-of-yesterday-archive\voy_world-2026-11-16.zip`
- 取回：`git checkout ffd0a70 -- tools/voy_world`，或解压存档后 `mix deps.get && mix test`
