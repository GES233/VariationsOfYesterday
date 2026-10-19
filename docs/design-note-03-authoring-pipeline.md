# 设计札记 03 — 内容流水线：让人写设定，让机器把设定编成因果

> 状态：**已定方案 + 部分落地**（2026-11）。配套实现见 `tools/voy_world/`：`Voy.World.Content`、`content/`、`mix voy.content lint`、`.github/workflows/voy_world.yml`。
> 定位：把「谁写什么」定下来，使没有技术背景的作者也能参与世界观生产，同时保证关系引擎（`design-note-02`）的语义不被内容编辑破坏。

---

## 0. 为什么需要这一层

原型阶段内容与规则是长在一起的：文档正文写在 `Projection` 的 `conde` 分支里，归因卡写在同一个函数里，年份写死在句子中。这对一个人维护是高效的，但有两种成本会在交接时兑现：

1. **作者写不了**：改一句话要懂 Elixir、要懂合一、要知道 `nil` 与 `:any` 的区别；
2. **一致性靠人盯**：同一段历史被两处文本描述，年份、地名、口径很容易漂移，而这类漂移正是「归因清晰」这一验收标准的天敌。

因此把内容从代码里整体搬出，形成**内容包**：作者写卡片与档案，机器把卡片编译成关系。

## 1. 三层分工

| 层 | 谁 | 碰什么 | 变更 |
| :--- | :--- | :--- | :--- |
| 内容 | 作者 | `content/documents/**/*.md`、`content/claims.tsv` | 加文件 / 加一行 |
| 结构 | 技术维护者 | 年代锚点、纪元链、结局表、地貌词表、规则层 | 改代码，走评审 |
| 校验 | 机器 | 检查器 + CI | 自动 |

边界的一句话：**作者可以增删内容，不能改变结构**。新增一个结局、一个纪元、一种地貌属于结构变更，必须有人改代码——这是有意的约束，因为结构一变，所有历史与档案的适用条件都要重新验证。

## 2. 内容包格式

选型原则：**零新语法、零依赖、diff 干净、Excel 可编辑**。

- 表格用 **TSV**（`claims.tsv`）：制表符分隔，单元格里可以自由出现逗号与《》，不需要引号规则；
- 档案用 **Markdown**：`---` 头部平铺 `key: value`，下面是散文正文；
- 两者都是纯文本，Git 可评审、可回滚。

一份档案：

```
---
slot: tech_note
lens: tech
fate: Fate_Assimilated_NewZhai
relic: ancestral_bronze_zheng
claim: claim.c0_removed_and_vulnerable_window_blasting
---

对照清丈残卷所载拆钟旧案，{{date.era2b_short}}年收缴铜钲再度断绝阻尼（c0=0）；……
```

一张归因卡（`claims.tsv` 一行）：

| tag | allowed_for | forbidden_for | label |
| :--- | :--- | :--- | :--- |
| `claim.seasonal_rain_mudslide` | `official` | `tech,folk` | 秋雨滑坡（官方口径） |

`forbidden_for` 就是原来的 `neq` 禁忌，`allowed_for` 就是「谁有资格引用」。两套账本不再由代码约束，而由这张表成立：官方通报引用官方卡，技术手簿引用技术卡，二者必然不同。

## 3. 机器侧要做的三件事

1. **选择器**：给定一段已编译的历史（`fate`、`relic`、`gazetteer_case`），为每个 `slot` 选出**恰好一份**适用文档。条件留空的维度即通配。
2. **渲染器**：把 `{{place.down}}`、`{{date.era2a_zheng}}` 换成节点名与时间线写法；日期写法只有三种（纪年、公历、两位 fieldwork 写法），都在 `Timeline` 里。
3. **检查器**：把所有作者能犯的错变成中文报错，带文件与行号。

## 4. 检查清单（当前实现）

| 检查 | 错误示例 |
| :--- | :--- |
| 字段合法性 | `未知字段 "govenance"；可用字段：slot / lens / fate / relic / gazetteer_case / claim / governance / title` |
| 必填字段 | `tech_note 缺少必填字段：claim` |
| 词表 | `fate 里的 "Fate_Nope" 不合法；可用：Fate_Assimilated_NewZhai, …` |
| 引用完整性 | `档案卡 doc.tech.x 引用了不存在的归因卡 "claim.nope"；现有：…` |
| 口径禁忌 | `tech 口径不能引用 claim.seasonal_rain_mudslide（该卡的 forbidden_for 包含 tech）` |
| 唯一适用 | `档案卡 doc.tech.a 与 doc.tech.b 的适用条件重叠：同一段历史会同时选中它们` |
| 覆盖 | `fate=Fate_Assimilated_NewZhai + relic=carved_diversion_stele 没有任何 tech_note 文档适用` |
| 日期归属 | `无法解析的占位符：{{date.era9}}`；正文里写死的年份由 `Canon` 的日期检查兜底 |
| 编码宽容 | CRLF、BOM、注释行、空行都接受 |

`mix voy.content lint` 打印这些错误；`mix test` 把它们当作断言。

## 5. 工作流

- **Git + PR**。作者只改 `content/`；CI（`.github/workflows/voy_world.yml`）跑格式、测试、内容检查，错误贴在 PR 里，作者在本机不需要装 Elixir。
- **评审分工**：技术维护者看结构与词表；编辑看文风、口径、时代质感；检查器看引用与唯一性。三者互不替代。
- **未来可加**：`mix voy.content preview <node>` 把玩家会读到的档案按顺序打印出来（作者不跑游戏就能校文风）；编辑器片段模板；错误信息里的「最接近的合法值」建议。

## 6. 与既有结构的关系

- `design-draft-01` §1 的动力学结构不变；内容包只影响 §4 叙事层的产出。
- `design-note-02` 的关系引擎不变：内容包提供**事实与文本**，不参与合一。作者写的是「适用条件」，机器翻译成关系约束。
- 内容包的生产物最终进入 `Voy.World.Export` 的 bundle（`schema_version`），JVM 侧只读导出物，不读 TSV/MD。

## 7. 迁移状态

| 步骤 | 内容 | 状态 |
| :--- | :--- | :--- |
| 1 | 内容包格式 + 加载器 + 检查器 | ✅ 完成 |
| 2 | 现有 4 类档案 + 8 张归因卡无损迁入，**与规则层逐字节一致**（`content_test.exs` 对 8 种可编译地形断言相等） | ✅ 完成 |
| 3 | 规则层改为读内容包（`Projection` 变通用选择器，`Texts` 中的档案条目退役） | ⬜ 下一步 |
| 4 | `Canon` 增加包级检查（覆盖、唯一、禁忌已由 `Content.lint` 承担；日期与名字仍由 `Canon` 校验切片） | 🟡 部分 |
| 5 | 体验层：`preview`、错误建议、模板 | ⬜ 未做 |

## 8. 已知债务

1. **结构描述仍在规则里**：`upstream_site` / `pass_site` 的两段描述因为挂在位置上而不是文书上，暂留 `Projection`；
2. **透镜叙述仍在 `Texts`**：运行时三口径解释（`Causality`）尚未迁入内容包，占位符语法也还分两套（`%{}` 与 `{{}}`）；
3. **日期仍是代码**：`Timeline` 的锚点未做成表格。理由：年代属于结构，作者不应随手增删；若将来需要按种子移动年代，再迁；
4. **归因卡 tag 仍是原子**：`claim.*` 经 `String.to_atom` 转成原子进入档案字段。当前包受评审、规模有限，可接受；迁 JVM 时统一为字符串 id；
5. **没有预览命令**：作者只能读 Markdown，看不到渲染后的档案。
