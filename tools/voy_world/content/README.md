# 内容包（给写作者）

这个目录是《旧日变奏曲》的**内容层**。写档案、写归因的人只需要动这里，不需要看懂 Elixir，也不需要改 `lib/` 下任何代码。

```
content/
  claims.tsv            归因卡：谁说这是怎么回事（一行一张）
  documents/            档案卡：玩家真正会读到的文件（一文件一份）
    gazetteer/          旧州府残卷
    official/           向上汇报的官样文章
    tech/               技术员手簿
    council/            场部—议事会规约
```

## 三件必须知道的事

1. **不要自己写年份**。正文里的日期一律用占位符：`{{date.era2a_zheng}}`、`{{date.era2a_stele}}`、`{{date.era2b}}`、`{{date.era2b_short}}`。渲染时会变成「光绪二十年」「79」这类写法。检查器会把写死的年份当作错误，因为改朝换代时它们会全部错掉。
2. **不要自己写地名**。用 `{{place.up}}` / `{{place.pass}}` / `{{place.down}}`，具体名字由节点决定（同一个模板可以长在山谷的不同支沟）。
3. **不要改 `lib/`**。如果某个字段不够用，那是规则层的事，提 issue 或找技术维护者。
4. **引用了哪一案，就写哪个 `gazetteer_case`**。手簿里一旦提到「拆钟旧案 / 毁堰旧案 / 立碑敷衍」，头部就写对应的 `gazetteer_case`。不写也能用（通配任何先例），但写了之后，这段历史换到别的先例时你的卡不会被选中——这是唯一能让「官方通报、技术手簿、旧州府残卷讲的是同一件事」被机器校验的办法（写错了那段历史会直接编不出来）。
5. **结局与先例的对应关系由可达性决定**。同一个结局可以出现在坝区，也可以出现在半山台地；决定它的是「官进不进得去」，不是地形本身。所以不要假设「坝区一定是同化」「台地一定是幸存」。

## 加一份档案

1. 在对应目录下新建 `.md` 文件（文件名就是它的 id，例如 `tech/blasting_zheng.md` → `doc.tech.blasting_zheng`）。
2. 头部写字段，`---` 收尾，下面是正文：

```
---
slot: tech_note
lens: tech
fate: Fate_Assimilated_NewZhai
relic: ancestral_bronze_zheng
gazetteer_case: case_1894_zheng_return_calmed_slide
claim: claim.c0_removed_and_vulnerable_window_blasting
---

对照清丈残卷所载拆钟旧案，{{date.era2b_short}}年收缴铜钲再度断绝阻尼（c0=0）；……
```

3. 跑检查、生成空模板或预览玩家实际读到的档案（也可等 CI 在 PR 里报错）：

```sh
mix voy.content lint
mix voy.content preview baishui          # 也可用 heishui / xuanguan / nuoliang / mahao / tingshan / all
mix voy.content template tech_note
```

## 字段表

| 字段 | 用在哪 | 含义 |
| :--- | :--- | :--- |
| `slot` | 全部 | 这份档案占哪个位置：`gazetteer` / `official_report` / `tech_note` / `council_document` |
| `lens` | 官方通报、手簿 | 口径：`official` / `tech`（残卷与规约不用写） |
| `fate` | 除残卷外 | 适用结局，可写多个（逗号分隔）；见下方词表 |
| `relic` | 手簿（可选） | 只对某件镇山古器成立：`ancestral_bronze_zheng` / `carved_diversion_stele`；不写就是两件都适用 |
| `gazetteer_case` | 官方通报、手簿（可选） | 本件引用的先例，见下方词表；写了就只在该先例下适用，不写则通配 |
| `gazetteer_case` | 残卷 | 对应的清丈旧案，决定残卷讲哪一段 |
| `claim` | 官方通报、手簿 | 引用的归因卡，决定 `claimed_cause` / `true_cause` |
| `governance` | 规约 | 政体代号，见下 |
| `title` | 残卷、规约 | 文书标题 |

结局与政体代号是固定词表，写错检查器会列出可用值：

- 结局：`Fate_Assimilated_NewZhai` / `Fate_Annihilated_BurnoutZone` / `Fate_Ascended_SpatialRift` / `Fate_Ancestral_ControlGroup` / `Fate_Deferred_ChronicSeep`（慢性渗漏：阻尼已毁但扰动不够点火） / `Fate_Tolerated_FalseAlarm`（反复虚警：c0 完好，每炮都被吸收）
- 政体：`brigade_elder_joint_council`（场部—议事会）/ `depopulated_concrete_sealed_zone`（封山死寂区）/ `rift_guarding_cliff_compact`（守山孤寨）/ `restored_ancestral_compact`（山官岁祭）/ `deferred_evacuation_watchpost`（待撤观测站）/ `cliff_observation_hermitage`（绝壁观测孤寨）
- 先例：`case_1894_zheng_return_calmed_slide`（铜钲案）/ `case_1902_stele_restored_stopped_flood`（石堰案）/ `case_mountain_too_steep_for_tithing`（立碑案）

## 加一张归因卡

`claims.tsv` 一行：

| 列 | 含义 |
| :--- | :--- |
| `tag` | `claim.` 开头的英文 id（小写字母、数字、下划线） |
| `allowed_for` | 哪些口径可以引用（`official` / `tech` / `folk`） |
| `forbidden_for` | 哪些口径不能引用——**两套账本靠这一列成立** |
| `label` | 给编辑看的中文短名，不进游戏 |

## 检查器会抓什么

- 未知字段、必填字段缺失、字段值不在词表里；
- 引用了不存在的归因卡，或引用了自己的口径被禁用的卡；
- 两份档案的适用条件重叠（同一段历史会同时选中它们）；
- 某个结局 / 古器组合缺少某个位置的档案；
- 正文里出现写死的年份，或无法解析的占位符；
- Windows 换行、BOM、注释行、空行都受支持，不必手动转换。

## 提交方式

Git + PR。CI 会跑检查并在 PR 里贴出错误（中文、带文件名和行号）。作者不需要在本机装 Elixir；当然装了会更快。
