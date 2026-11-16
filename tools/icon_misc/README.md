# tools/icon_misc — 模组图标的源文件

- `assets/concept_e_v3.svg` — 现行图标（「活山」版，512×512）的矢量母版。
  `src/main/resources/assets/variations-of-yesterday/icon.png` 是它的 128px 位图成品，随模组资源入库；其余尺寸需要时从该 SVG 重新导出。

> 2026-11-16 减法：早期 A / B / B1–B4 / C / D 概念稿、E 系列早期迭代、旧图标 `icon_legacy.png`，
> 以及三个生成脚本（`icon_gen.py`、`generate_icons.py`、`generate_b_variants.py`）已删除。
> 这些脚本生成的都是被否的图标方案，当前图标并非由它们产出；完整历史仍可在 git（`5e09f50` 及更早提交）中查到。
