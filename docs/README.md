# 文档索引

> **2026-11-16 文档收口**：现行有效文档已收束为下方三份；带编号的历史设计札记与工作稿均已归档至 `docs/archive/`，仅供追溯演进，不再作为实现或文案依据。

## 现行文档（先看这三份）

| 文档 | 面向读者 | 内容 |
| :--- | :--- | :--- |
| [当前架构与工程边界](CURRENT_ARCHITECTURE.md) | 工程师 | 数据流、三轴语义、敲钟规则、预设与序列化红线、未实施项 |
| [创作圣经](CREATIVE_BIBLE.md) | 策划 / 美工 / 剧情 | 世界观与三条红线、物候因果表、标准词表、色板与资产台账、核与开局边界 |
| [发版节奏与世界模式规划](ROADMAP_AND_MODES.md) | 制作人 / 策划 / 工程 | 0.1 收尾清单、「旧日谷地 / 生态漫游」双模式方案与预留接缝 |

## 历史归档（`docs/archive/`，仅供考古）

| 文档 | 原定位与说明 |
| :--- | :--- |
| [设计札记 12](archive/design-note-12-canonical-semantics.md) | 语义宪章与重构路线（S1 敲钟去相位、S2b 改名已落地；§5 原子事实流与 S3–S5 未实施） |
| [设计札记 11](archive/design-note-11-heartbeat-shelter.md) | 「心跳即庇护」迁移定稿与 16 条裁决记录 |
| [心跳即庇护迁移计划](archive/heartbeat-shelter-migration-plan-2026-11-16.md) | 札记 11 原始工作稿（含 S2b 改名前的旧符号名） |
| [设计札记 10](archive/design-note-10-meta-trilogy-and-drift.md) | 「三重梦境」三部曲元叙事构想与三代语义漂变复盘 |
| [设计札记 08](archive/design-note-08-fast-variable-and-stagnation.md) | 旧「脉冲庇护窗」方案（已被札记 11 取代） |
| [设计札记 07](archive/design-note-07-pulse-coupling.md) | 水晶脉冲耦合链路与 E1/E2/E4b 实验回执 |
| [设计札记 06](archive/design-note-06-mycelial-grounding.md) | 菌网生物学底盘、感官签名、菌塘及未实施的 WO-2 设施工单 |
| [设计草案 01](archive/design-draft-01-living-world.md) | 0.1 初版总纲（含未实现的 Node 休眠调度与已外迁的 ARG 卷宗线） |
| [设计札记 09](archive/design-note-09-mountain-dial.md) | 挂起构想：制式验山盘（第一阶段不实现） |
| [验证报告 01](archive/verification-report-01-mandate-slice.md) | 0.1 纵切片数值验证历史快照 |

## 已外迁：独立工具链（2026-11-16）

Elixir 关系世界模型与五跳卷宗规范（原设计札记 02 / 05）原在 `tools/voy_world/`，与模组运行时零耦合，已移出仓库。取回方式：`git checkout ffd0a70 -- tools/voy_world`，或解压备份归档后执行 `mix deps.get && mix test`。
