# 文档索引

> **2026-11-13 文档重组**：将模组本体（活体生物电菌网、AMD 酸化与同步除颤）与独立 Elixir 关系式盘工具链（`tools/voy_world/`）拆清边界；关系世界模型与五跳卷宗规范迁入 [`tools/voy_world/docs/`](../tools/voy_world/docs/)，挂起构想与 0.1 冻结期快照分别归入 `incubator/` 与 `archive/`。

## 模组本体（动力学、生态与元叙事）

| 文档 | 主题 | 状态 |
| :--- | :--- | :--- |
| [设计札记 10](design-note-10-meta-trilogy-and-drift.md) | **META / NARRATIVE**：三代语义漂变复盘、去滇西南化与纯虚构发光菌重锚、三部曲元叙事（深山旧梦 → 民科星海 → 终末之诗）、定位裁定（§5：恐怖只活在氛围层与钩子层） | 元叙事锚点 |
| [设计草案 01](design-draft-01-living-world.md) | 总纲：动力学（FHN + M）、Node/格子场与休眠调度、聚落与沉滞心跳、Worldgen 路线、开放待办 | 0.1 结构冻结（ARG / 民俗线已与模组运行解耦） |
| [设计札记 06](design-note-06-mycelial-grounding.md) | 生态落地：神经化菌网/AMD、M 动力学、感官签名、维护设施工单 | 实施规格 |
| [设计札记 07](design-note-07-pulse-coupling.md) | 脉冲耦合链路与相位同步（自动化心跳 / 体外起搏涌现） | 事件层规范 |
| [设计札记 08](design-note-08-fast-variable-and-stagnation.md) | 心跳脉冲语义：快变量读数、三正交轴与 `PulseStamp` 极简脉冲短窗（同步除颤分诊） | 已定稿落地 |

## 孵化构想与历史归档

| 文档 | 主题 | 状态 |
| :--- | :--- | :--- |
| [设计札记 09](incubator/design-note-09-mountain-dial.md) | 制式验山盘：有限域约束弛豫仪（定针/游核、同相互耗、盘契约、FORCEDENTRY 保留条款） | **挂起（第一阶段不实现）**；待拆清「体外自适应起搏器」与「24 候选推理盘」归属 |
| [验证报告 01](archive/verification-report-01-mandate-slice.md) | 0.1 纵切片数值验证与溯因 | 历史快照（回归参考） |

## 独立工具链（已迁入 `tools/voy_world/docs/`）

| 文档 | 主题 | 说明 |
| :--- | :--- | :--- |
| [设计札记 02](../tools/voy_world/docs/design-note-02-kanren-worldview-engine.md) | 关系世界模型：Kanren 分层、四纪编译（式盘历史）、六结局与时域结算、地形组合 | 已迁入 Elixir 侧；与模组运行时零耦合 |
| [设计札记 05](../tools/voy_world/docs/design-note-05-medikanren-microchains.md) | 五跳卷宗与内容管线：微链图谱、种子物证、写作规则、Canon 不变量 | 已迁入 Elixir 侧 |

- 模组阅读顺序：10（创作母题）→ 01 → 06 → 07 → 08；孵化与历史按需查证。
- **合并说明**：原札记 03（内容流水线）已并入札记 05 §4–§5；原札记 04（可达性解耦与六结局）已并入札记 02 §3.1 与 §5。
