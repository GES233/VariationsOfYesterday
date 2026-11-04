# 设计札记 03 — 内容流水线：局部单据与机器拼接

> 状态：**现行规范**。实现位于 `tools/voy_world/`（`Voy.World.Content`、`Voy.World.Pathway`、`Voy.World.Witness`、`content/` 与 `mix voy.content`）。

## 1. 三层职责分离

故事不存在于任何一张全知摘要卡中，而是由五张局部单据通过桥接元组首尾合一后涌现：

| 层级 | 维护者 | 负责内容 | 变更方式 |
| :--- | :--- | :--- | :--- |
| **内容层** | 叙事作者 | `content/documents/**/*.md`（32 张主链卡 + 4 张干扰卡） | 编辑 Markdown，运行 `mix voy.content lint` |
| **结构层** | 技术维护者 | 五跳拓扑（`Pathway`）、种子物证池（`Witness`）、四纪历史（`Chronicle`）、数值契约（`Contract`） | 修改代码与测试 |
| **校验层** | 机器检查 | 卡片覆盖、唯一选择、占位符闭合、禁写死年份、五跳消融与认知局部性（`Canon`） | `mix test` / CI 自动执行 |

作者只负责单据文风与局部事实；新增 hop、古器、结局、干扰类型或安全刻度属于结构变更。

## 2. 卡片格式与选择规则

每份档案是带平铺头部（`---`）的 Markdown 文件：

- **Hop 1（旧志先例）、Hop 3（车间配料卡）**：声明 `hop / role / relic / title`；
- **Hop 2（收缴或代管单）**：另声明 `c0_status`（`c0_collapsed` 或 `c0_intact`）；
- **Hop 4（野外观测手簿）、Hop 5（现行条规）**：另声明 `fate`（六种结局之一）；
- **干扰卡（Decoy）**：`role: decoy`，声明 `relic / decoy_class / title`。

对于全部 $2 \times 6 = 12$ 个 `(relic, fate)` 组合，`Content.card_for/2` 为每条边唯一选出一张卡片（5 张主链 + 2 张干扰），缺卡或撞卡均直接报错。具体写作语法与占位符清单见 [content/README.md](<../tools/voy_world/content/README.md>)。

## 3. 种子物化与实测安全刻度

1. **实测安全刻度（`safe_ticks`）**：Scala `PhaseResponse` 对每个整数 tick 实测 `alpha_metal_resonance` 的尖峰响应，仅将结果为 `Delayed` 的刻度写入 `contracts/mandate-slice-v1.json`。
2. **种子绑定（`Witness.bind/4`）**：按 `(seed, branch_id, relic, fate)` 确定性哈希选出互不冲突的真链批号、工业载体与 `safe_tick`，以及两套同车间干扰批号/载体，渲染占位符后填入 `Export`（schema v2）的 `dossier` 字段。

## 4. 常用命令

```sh
mix voy.content lint
mix voy.content preview [baishui|heishui|xuanguan|nuoliang|mahao|tingshan|all] [--seed N]
mix voy.content template <hop1|hop2|hop3|hop4|hop5|decoy>
```
