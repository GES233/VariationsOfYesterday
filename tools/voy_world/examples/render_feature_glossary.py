"""Draw the feature glossary: what each abstract `voy_world` terrain atom means.

Figure 06a is the landform half of the vocabulary (source, pass, settlement);
figure 06b is the subsurface and access half (geology, accessibility).
Figure 07 takes one real branch and marks all five fields on a single map, so
the "one place = five field values" relation is visible.

These are diagrams, not worldgen: nothing here is read by the compiler. The
atoms, and the branch that figure 07 dissects, do come from
`docs/diagrams/topography_cases.json` (see `export_topography_cases.exs`) so
the labels cannot drift from the rules.
"""

import json
import textwrap
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib import font_manager
from matplotlib.patches import Ellipse, FancyBboxPatch, Polygon, Rectangle

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "diagrams"
DATA = OUT / "topography_cases.json"


def configure_font():
    families = [
        "DengXian",
        "Microsoft YaHei",
        "Noto Sans CJK SC",
        "Source Han Sans SC",
        "WenQuanYi Zen Hei",
        "PingFang SC",
        "SimHei",
        "Arial Unicode MS",
    ]
    for family in families:
        try:
            font_path = font_manager.findfont(family, fallback_to_default=False)
        except ValueError:
            continue
        font_manager.fontManager.addfont(font_path)
        plt.rcParams["font.family"] = family
        break
    else:
        raise RuntimeError("A CJK font is required to render these diagrams")
    # Atoms and enum values are pure ASCII; they read better in a fixed width.
    plt.rcParams["font.monospace"] = [
        "Consolas",
        "Courier New",
        "Noto Sans Mono",
        "DejaVu Sans Mono",
    ]


configure_font()
plt.rcParams["axes.unicode_minus"] = False

# One warm paper, one deep ink, one muted ink; group accents only where they
# mean something (group chip, accent bars, sketch tint).
PAPER = "#F4EFE2"
PANEL = "#FDFBF4"
INK = "#2E2B23"
MUTED = "#7C7462"
FAINT = "#A99F8A"
HAIR = "#DCD4BF"
SHADOW = "#3A3423"
WATER = "#8FB9C6"
WATER_DARK = "#5F8E9C"
ROCK = "#E4DDCA"
ROCK_DARK = "#B9AF97"
CONTOUR = "#CFC5AD"
LANDING = "#54665C"

# group -> (accent, light tint)
GROUP_STYLE = {
    "源头地貌": ("#7E7768", "#EAE5D7"),
    "隘口地貌": ("#B08A3F", "#F1E4C6"),
    "聚落地貌": ("#7C9864", "#E4EBD6"),
    "源头地质": ("#8D7AA8", "#E9E3F0"),
    "可达性": ("#6E93A6", "#DFEAF0"),
}

# The canvas is 100 x 100 data units on a wide figure, so one data unit is
# shorter vertically than horizontally. Anything that must look round is drawn
# as an ellipse with the vertical radius multiplied by this factor.
ASPECT = 1.0


def set_aspect(fig_w, fig_h):
    global ASPECT
    ASPECT = fig_w / fig_h


# Sketch stroke weights, shared by all 11 glyphs so they read as one set.
SK_CONTOUR = 0.7
SK_EDGE = 1.0
SK_WATER = 2.2
SK_INK = 1.6

LANDFORM_FEATURES = [
    {
        "key": "steep_fissure_headwater",
        "group": "源头地貌",
        "cn": "陡裂隙源头",
        "shape": "两壁近乎垂直的窄沟，沟底一线急流，源头是断层崖下的涌泉。沟宽 8–15 格，落差集中在 30–60 格内。",
        "landing": "深板岩/安山岩裸崖 + 苔石；沟底涌泉与叠瀑；崖顶留一块窄台地放钻探台。",
        "sketch": "fissure",
    },
    {
        "key": "karst_sinkhole_headwater",
        "group": "源头地貌",
        "cn": "落水洞源头",
        "shape": "漏斗状洼地，底部竖井把地表水转入地下；四周是溶蚀残丘，地表没有出水口。",
        "landing": "方解石/滴水石锥 + 石灰岩；中心竖井直通溶洞；环境音是滴水与暗河。",
        "sketch": "sinkhole",
    },
    {
        "key": "canyon_pass",
        "group": "隘口地貌",
        "cn": "峡谷隘口",
        "shape": "两壁夹峙的窄口，宽 5–10 格，是全沟唯一的咽喉；等高线在鞍部被掐断。",
        "landing": "垂直崖壁 + 木架/铁架 + 红绸东方红气缸；验山垂架挂在崖侧。",
        "sketch": "canyon",
    },
    {
        "key": "karst_gorge",
        "group": "隘口地貌",
        "cn": "溶洞峡口",
        "shape": "圆缓的 U 形谷，谷壁有溶洞入口；暗河从洞中进出，河在此消失又出现。",
        "landing": "溶洞 + 暗河 + 圆石矮堰（分水石堰残座）；谷底可行但无车路。",
        "sketch": "gorge",
    },
    {
        "key": "alluvial_flat",
        "group": "聚落地貌",
        "cn": "冲积平坝",
        "shape": "河漫滩，坡度极小、土层深厚，可机耕；等高线稀疏近平行。",
        "landing": "泥土/砂 + 河曲；稻田阶块、砖混宿舍（farm_dorm_unit_*）、晒场。",
        "sketch": "flat",
    },
    {
        "key": "mid_terrace",
        "group": "聚落地貌",
        "cn": "半山台地",
        "shape": "河上方 40–80 格的侵蚀阶地，前缘是断崖、台面平缓；孤寨悬在台上。",
        "landing": "台面 + 前缘崖 + 栈道溜索；悬空木寮与火塘，没有车路。",
        "sketch": "terrace",
    },
]

SUBSURFACE_FEATURES = [
    {
        "key": "active_fault_vein",
        "group": "源头地质",
        "cn": "活动断层脉",
        "shape": "地表可见的断层线：直线状谷、断层崖、成串涌泉；岩体破碎，爆破效率最高。",
        "landing": "直线碎裂带（安山岩/深板岩碎块）；302 钻探台就打在断层上。",
        "sketch": "fault",
    },
    {
        "key": "deep_stress_cavity",
        "group": "源头地质",
        "cn": "深层应力空腔",
        "shape": "地下数十格以下的巨型空腔，顶板薄；地表是塌陷洼地与地鸣，可打注浆孔。",
        "landing": "大型溶洞 + 薄顶板；井口后来被水泥封盖（烧毁线的物证）。",
        "sketch": "cavity",
    },
    {
        "key": "minor_fracture",
        "group": "源头地质",
        "cn": "微裂隙",
        "shape": "岩体只发育细裂隙，渗水慢、扰动小；所以这里进驻的只是林场水文哨。",
        "landing": "细裂隙 + 渗水痕 + 苔藓；小木屋、林场界牌、水文标尺。",
        "sketch": "fracture",
    },
    {
        "key": "tractor_road_accessible",
        "group": "可达性",
        "cn": "机耕道可达",
        "shape": "一条勉强能过拖拉机的土路，从坝区盘上隘口；宽 3–4 格，有回头弯与木桥。",
        "landing": "泥土小径 + 木桥 + 里程碑。这是「官能进来动手拆器」的物质条件。",
        "sketch": "road",
    },
    {
        "key": "cliff_isolated",
        "group": "可达性",
        "cn": "绝壁不通车",
        "shape": "只有栈道、藤梯或溜索，没有车路；丈量胥吏到此为止，只立一块归化碑。",
        "landing": "脚手架/梯子/藤蔓 + 断头栈道 + 石砖归化碑；立碑案的物质条件。",
        "sketch": "cliff",
    },
]


def canvas(title, subtitle, figsize, title_y=97.6, subtitle_y=94.2):
    fig = plt.figure(figsize=figsize, facecolor=PAPER)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_facecolor(PAPER)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis("off")
    ax.text(2.2, title_y, title, fontsize=19, fontweight="bold", color=INK, va="top")
    ax.text(2.2, subtitle_y, subtitle, fontsize=10, color=MUTED, va="top")
    return fig, ax


def rbox(ax, x, y, w, h, face, edge="none", lw=0.0, radius=1.0, z=2, alpha=1.0):
    ax.add_patch(
        FancyBboxPatch(
            (x, y),
            w,
            h,
            boxstyle=f"round,pad=0,rounding_size={radius}",
            facecolor=face,
            edgecolor=edge,
            linewidth=lw,
            zorder=z,
            alpha=alpha,
        )
    )


def card(ax, x, y, w, h, face=PANEL, edge=HAIR, lw=1.0, radius=1.0, shadow=True):
    if shadow:
        rbox(ax, x + 0.32, y - 0.42, w, h, SHADOW, radius=radius, z=1, alpha=0.08)
    rbox(ax, x, y, w, h, face, edge, lw, radius, z=2)


def panel_title(ax, x, y, text, accent, fontsize=9.5):
    """Small section heading: accent tick + bold ink label."""
    ax.add_patch(Rectangle((x, y - 1.05), 0.45, 1.5, facecolor=accent, edgecolor="none", zorder=4))
    ax.text(x + 1.1, y, text, fontsize=fontsize, fontweight="bold", color=INK,
            ha="left", va="center", zorder=4)


def hairline(ax, x0, x1, y, color=HAIR, lw=0.7, z=3):
    ax.plot([x0, x1], [y, y], color=color, linewidth=lw, zorder=z)


def mono(ax, x, y, text, fontsize, color=INK, ha="left", va="baseline", z=4, bold=False):
    ax.text(x, y, text, fontsize=fontsize, family="monospace", color=color, ha=ha, va=va,
            zorder=z, fontweight="bold" if bold else "normal")


def wrap(text, width):
    return textwrap.wrap(text, width) or [""]


# ----------------------------------------------------------------- sketches


def sketch(ax, kind, bx, by, bw, bh):
    """Draw one glyph inside (bx, by, bw, bh); no text, shared stroke weights."""

    def u(fx):
        return bx + fx * bw

    def v(fy):
        return by + fy * bh

    def poly(points, face, edge="none", lw=0.0, z=5):
        ax.add_patch(
            Polygon(
                [(u(fx), v(fy)) for fx, fy in points],
                closed=True,
                facecolor=face,
                edgecolor=edge,
                linewidth=lw,
                zorder=z,
            )
        )

    def line(points, colour=CONTOUR, lw=SK_CONTOUR, z=6, style="-"):
        ax.plot([u(fx) for fx, _ in points], [v(fy) for _, fy in points], color=colour,
                linewidth=lw, zorder=z, linestyle=style, solid_capstyle="round")

    def dot(fx, fy, r, colour, z=8):
        ax.add_patch(
            Ellipse(
                (u(fx), v(fy)),
                2 * r * bw,
                2 * r * bw * ASPECT,
                facecolor=colour,
                edgecolor="none",
                zorder=z,
            )
        )

    def box(fx, fy, fw, fh, face=PANEL, edge=INK, lw=0.8, z=9):
        ax.add_patch(Rectangle((u(fx), v(fy)), fw * bw, fh * bh, facecolor=face, edgecolor=edge,
                               linewidth=lw, zorder=z))

    def contours(n, y0=0.10, step=0.20, x1=1.0):
        for i in range(n):
            line([(0.0, y0 + step * i), (x1, y0 + step * i)], z=3)

    if kind == "fissure":
        # two converging walls, a thread of water, contours hugging each wall
        poly([(0.02, 0.02), (0.28, 0.02), (0.455, 0.96), (0.19, 0.96)], ROCK, ROCK_DARK, SK_EDGE, 4)
        poly([(0.98, 0.02), (0.72, 0.02), (0.545, 0.96), (0.81, 0.96)], ROCK, ROCK_DARK, SK_EDGE, 4)
        for offset in [0.05, 0.10, 0.15]:
            line([(0.02 + offset, 0.04), (0.20 + offset, 0.94)], z=5)
            line([(0.98 - offset, 0.04), (0.80 - offset, 0.94)], z=5)
        poly([(0.28, 0.02), (0.72, 0.02), (0.545, 0.96), (0.455, 0.96)], WATER, "none", 0.0, 6)
        line([(0.50, 0.02), (0.50, 0.96)], WATER_DARK, 0.9, z=7, style=(0, (2, 3)))
        dot(0.50, 0.94, 0.026, WATER_DARK)

    elif kind == "sinkhole":
        for r in [0.46, 0.35, 0.25, 0.16]:
            ax.add_patch(Ellipse((u(0.5), v(0.52)), 2 * r * bw, 1.45 * r * bh, facecolor="none",
                                 edgecolor=ROCK_DARK, linewidth=SK_EDGE, zorder=4))
        line([(0.5, 0.99), (0.5, 0.68)], WATER, SK_WATER)
        ax.add_patch(Ellipse((u(0.5), v(0.52)), 0.11 * bw, 0.11 * bw * ASPECT, facecolor=INK,
                             edgecolor="none", zorder=8))
        line([(0.5, 0.32), (0.5, 0.03)], WATER_DARK, 1.2, z=6, style=(0, (2, 3)))

    elif kind == "canyon":
        poly([(0.02, 0.02), (0.33, 0.02), (0.40, 0.98), (0.02, 0.98)], ROCK, ROCK_DARK, SK_EDGE, 4)
        poly([(0.98, 0.02), (0.67, 0.02), (0.60, 0.98), (0.98, 0.98)], ROCK, ROCK_DARK, SK_EDGE, 4)
        for fy in [0.16, 0.34, 0.52, 0.70, 0.88]:
            line([(0.40, fy), (0.60, fy)], PAPER, SK_CONTOUR, z=5)
        poly([(0.40, 0.02), (0.60, 0.02), (0.555, 0.98), (0.445, 0.98)], WATER, "none", 0.0, 6)
        box(0.448, 0.42, 0.104, 0.15, "#E7D3A8", INK, 0.9)

    elif kind == "gorge":
        for i in range(4):
            fy = 0.10 + 0.22 * i
            line([(0.0, fy), (0.26, fy + 0.06), (0.5, fy + 0.08), (0.74, fy + 0.06), (1.0, fy)],
                 z=3)
        poly([(0.0, 0.0), (0.0, 0.66), (0.10, 0.60), (0.26, 0.44), (0.33, 0.24), (0.30, 0.0)],
             ROCK, ROCK_DARK, SK_EDGE, 4)
        poly([(1.0, 0.0), (1.0, 0.66), (0.90, 0.60), (0.74, 0.44), (0.67, 0.24), (0.70, 0.0)],
             ROCK, ROCK_DARK, SK_EDGE, 4)
        line([(0.5, 0.99), (0.5, 0.62)], WATER, SK_WATER, z=6)
        line([(0.5, 0.42), (0.5, 0.03)], WATER, SK_WATER, z=6, style=(0, (3, 3)))
        ax.add_patch(Ellipse((u(0.5), v(0.52)), 0.13 * bw, 0.13 * bw * ASPECT, facecolor=INK,
                             edgecolor="none", zorder=8))

    elif kind == "flat":
        contours(6, y0=0.08, step=0.16)
        poly([(0.0, 0.40), (0.28, 0.46), (0.58, 0.40), (1.0, 0.48), (1.0, 0.60), (0.58, 0.52),
              (0.28, 0.58), (0.0, 0.52)], WATER, "none", 0.0, 6)
        for fx, fy in [(0.12, 0.14), (0.34, 0.18), (0.62, 0.12), (0.24, 0.72), (0.50, 0.76),
                       (0.76, 0.70)]:
            box(fx, fy, 0.11, 0.10)

    elif kind == "terrace":
        line([(0.0, 0.12), (1.0, 0.12)], WATER, 2.8, z=6)
        poly([(0.0, 0.12), (1.0, 0.12), (1.0, 0.42), (0.0, 0.42)], ROCK_DARK, "none", 0.0, 4)
        for fx in [i / 26 for i in range(26)]:
            line([(fx, 0.12), (fx, 0.42)], PAPER, 0.5, z=5)
        poly([(0.04, 0.42), (0.96, 0.42), (0.96, 0.64), (0.04, 0.64)], ROCK, ROCK_DARK, SK_EDGE, 6)
        for fx in [0.10, 0.30, 0.50, 0.70]:
            box(fx, 0.68, 0.12, 0.11)
        line([(0.5, 0.42), (0.5, 0.14)], MUTED, 1.0, z=7, style=(0, (2, 3)))

    elif kind == "fault":
        line([(0.04, 0.94), (0.96, 0.10)], INK, SK_INK, z=7)
        for fx, fy in [(0.18, 0.79), (0.36, 0.64), (0.54, 0.49), (0.72, 0.34)]:
            dot(fx, fy, 0.030, WATER_DARK)
        for i in range(6):
            t = 0.14 + 0.13 * i
            line([(t - 0.03, 0.94 - t * 0.86), (t + 0.06, 0.94 - t * 0.86 - 0.07)], z=6)
        box(0.60, 0.66, 0.20, 0.16, "#CFC3DC", INK, 0.9)

    elif kind == "cavity":
        contours(4, y0=0.12, step=0.20)
        ax.add_patch(Ellipse((u(0.5), v(0.48)), 0.62 * bw, 0.44 * bh, facecolor="#DCD2E6",
                             edgecolor=INK, linewidth=SK_EDGE, linestyle=(0, (3, 2)), zorder=6))
        for fx in [0.34, 0.5, 0.66]:
            line([(fx, 0.94), (fx, 0.72)], INK, 1.1, z=7)
            box(fx - 0.035, 0.90, 0.07, 0.05, PANEL, INK, 0.7)

    elif kind == "fracture":
        contours(4, y0=0.12, step=0.20)
        for fx, fy, dx in [(0.18, 0.20, 0.18), (0.44, 0.30, -0.15), (0.60, 0.62, 0.20),
                           (0.32, 0.74, -0.14)]:
            line([(fx, fy), (fx + dx, fy + 0.14)], MUTED, 0.9, z=7)
            line([(fx + dx, fy + 0.14), (fx + dx * 1.5, fy + 0.22)], MUTED, SK_CONTOUR, z=7)
        for fx, fy in [(0.24, 0.36), (0.54, 0.50), (0.74, 0.26)]:
            dot(fx, fy, 0.032, WATER)
        box(0.68, 0.72, 0.22, 0.18)

    elif kind == "road":
        contours(5, y0=0.10, step=0.20)
        road = [(0.12, 0.02), (0.30, 0.20), (0.22, 0.40), (0.44, 0.54), (0.64, 0.68),
                (0.56, 0.94)]
        line(road, "#C9A876", 4.4, z=7)
        line(road, INK, SK_CONTOUR, z=8, style=(0, (4, 3)))
        for fx, fy in [(0.30, 0.20), (0.44, 0.54), (0.64, 0.68)]:
            box(fx - 0.025, fy - 0.012, 0.06, 0.045, "#E7D3A8", INK, 0.6, z=9)

    elif kind == "cliff":
        contours(5, y0=0.10, step=0.18, x1=0.64)
        poly([(0.64, 0.0), (1.0, 0.0), (1.0, 0.98), (0.64, 0.98)], ROCK, ROCK_DARK, SK_EDGE, 5)
        for fy in [0.18, 0.38, 0.58, 0.78]:
            line([(0.64, fy), (0.92, fy)], PAPER, SK_CONTOUR, z=6)
        line([(0.58, 0.02), (0.62, 0.32)], MUTED, 1.1, z=7, style=(0, (2, 2)))
        line([(0.62, 0.32), (0.58, 0.58)], MUTED, 1.1, z=7, style=(0, (2, 2)))
        box(0.40, 0.04, 0.10, 0.15, ROCK_DARK, INK, 0.8, z=9)


# ------------------------------------------------------------------ glossary


def render_glossary(features, title, subtitle, path, note_lines):
    set_aspect(19.6, 14.6)
    fig, ax = canvas(title, subtitle, (19.6, 14.6))

    card_w, card_h, pad = 30.4, 40.0, 1.7
    xs = [2.0, 34.8, 67.6]
    ys = [46.4, 3.4]
    slots = [(row, col) for row in range(2) for col in range(3)]

    for feature, (row, col) in zip(features, slots):
        x, y = xs[col], ys[row]
        accent, tint = GROUP_STYLE[feature["group"]]
        top = y + card_h
        card(ax, x, y, card_w, card_h)

        # group chip
        chip_w = 5.2
        rbox(ax, x + pad, top - 3.4, chip_w, 2.0, accent, radius=0.55, z=4)
        ax.text(x + pad + chip_w / 2, top - 2.42, feature["group"], fontsize=7.4,
                color=PANEL, ha="center", va="center", zorder=5, fontweight="bold")

        # sketch panel on the group tint
        sx, sy = x + pad, top - 19.0
        sw, sh = card_w - 2 * pad, 14.6
        rbox(ax, sx, sy, sw, sh, tint, HAIR, 0.7, radius=0.7, z=3)
        sketch(ax, feature["sketch"], sx + 1.0, sy + 0.9, sw - 2.0, sh - 1.8)

        # name / atom / description hierarchy
        ax.text(x + pad, top - 22.6, feature["cn"], fontsize=14.5, fontweight="bold", color=INK)
        mono(ax, x + pad, top - 25.2, feature["key"], 7.2, MUTED)
        hairline(ax, x + pad, x + card_w - pad, top - 26.4)

        body_y = top - 27.6
        for line in wrap(feature["shape"], 30)[:3]:
            ax.text(x + pad, body_y, line, fontsize=7.8, color=INK, va="top")
            body_y -= 1.85

        land_lines = wrap("落地：" + feature["landing"], 30)[:3]
        land_top = body_y - 0.7
        ax.add_patch(Rectangle((x + pad, land_top - 1.55 * len(land_lines) - 0.1), 0.28,
                               1.55 * len(land_lines) + 0.6, facecolor=accent, edgecolor="none",
                               zorder=4))
        for i, line in enumerate(land_lines):
            ax.text(x + pad + 0.9, land_top - 1.55 * i, line, fontsize=7.1, color=LANDING,
                    va="top")

    if note_lines:
        x, y = xs[2], ys[1]
        card(ax, x, y, card_w, card_h, "#F1EBDC", "#D5CCB4", lw=1.0)
        panel_title(ax, x + pad, y + card_h - 2.6, note_lines[0], "#B08A3F", fontsize=11)
        hairline(ax, x + pad, x + card_w - pad, y + card_h - 5.2)
        ny = y + card_h - 7.4
        for line in note_lines[1:]:
            if line:
                colour = INK if line.startswith("·") else MUTED
                ax.text(x + pad, ny, line, fontsize=8.0, color=colour, va="top")
                ny -= 2.1
            else:
                ny -= 1.1

    fig.savefig(path, dpi=170, facecolor=PAPER, bbox_inches="tight")
    plt.close(fig)
    print(f"wrote {path}")


# ------------------------------------------------------------------ figure 07


def marker(ax, x, y, mark, r=1.35, fontsize=7.6, z=13):
    ax.add_patch(
        Ellipse((x, y), 2 * r, 2 * r * ASPECT, facecolor=INK, edgecolor=PANEL,
                linewidth=1.2, zorder=z)
    )
    ax.text(x, y, mark, fontsize=fontsize, color=PANEL, ha="center", va="center", zorder=z + 1)


def render_anatomy(case):
    set_aspect(19.6, 13.4)
    fig, ax = canvas(
        "一条支沟怎么变成五个字段：以白水坝（baishui）为例",
        "同一条沟在规则层里是 5 个 atom 的取值；compile_branch/1 再把它变成结局、遗迹落位与四份档案。",
        (19.6, 13.4),
    )

    # --- left: bird's-eye of the branch ------------------------------------
    mx, my, mw, mh = 3.0, 26.0, 52.0, 66.0
    card(ax, mx, my, mw, mh, "#F1ECDD", HAIR, lw=1.0, radius=1.2)
    panel_title(ax, mx + 1.8, my + mh - 2.6, "鸟瞰（上＝源头，下＝聚落）", "#7E7768")

    def U(fx):
        return mx + 2.8 + fx * (mw - 5.6)

    def V(fy):
        return my + 3.2 + fy * (mh - 7.4)

    # The valley as a plan: a centre line that pinches at the pass, flanked by
    # rock ridges. `half` is the half-width of the valley floor at each station.
    centre = [(0.50, 0.97), (0.49, 0.82), (0.52, 0.66), (0.50, 0.50), (0.48, 0.34),
              (0.45, 0.16), (0.43, 0.03)]
    half = [0.15, 0.13, 0.07, 0.06, 0.11, 0.17, 0.20]

    for t in [1.0, 0.66, 0.33]:
        for side in (-1, 1):
            inner = [(x + side * w, y) for (x, y), w in zip(centre, half)]
            edge = [(x + side * (w + (0.34 - w) * t), y) for (x, y), w in zip(centre, half)]
            ax.add_patch(
                Polygon(
                    [(U(fx), V(fy)) for fx, fy in inner + list(reversed(edge))],
                    closed=True,
                    facecolor=ROCK if t == 1.0 else "none",
                    edgecolor=ROCK_DARK if t == 1.0 else CONTOUR,
                    linewidth=SK_EDGE if t == 1.0 else SK_CONTOUR,
                    zorder=4 if t == 1.0 else 5,
                )
            )

    # the river follows the valley floor
    ax.plot([U(fx) for fx, _ in centre], [V(fy) for _, fy in centre], color=WATER,
            linewidth=5.5, zorder=6, solid_capstyle="round")
    ax.plot([U(fx) for fx, _ in centre], [V(fy) for _, fy in centre], color=WATER_DARK,
            linewidth=0.9, zorder=7, linestyle=(0, (3, 3)))

    # fault line + spring chain at the source
    ax.plot([U(0.16), U(0.66)], [V(0.92), V(0.845)], color=INK, linewidth=SK_INK, zorder=8)
    for fx, fy in [(0.24, 0.909), (0.36, 0.89), (0.48, 0.872), (0.60, 0.853)]:
        ax.add_patch(
            Ellipse((U(fx), V(fy)), 1.0, 1.0 * ASPECT, facecolor=WATER_DARK, edgecolor="none",
                    zorder=9)
        )
    ax.text(U(0.14), V(0.965), "断层线 + 涌泉", fontsize=6.6, color=MUTED, ha="left",
            va="center", zorder=10)

    # drill pad sits on the fault
    ax.add_patch(Rectangle((U(0.66), V(0.885)), 0.13 * (mw - 5.6), 0.05 * (mh - 7.4),
                           facecolor="#CFC3DC", edgecolor=INK, linewidth=0.9, zorder=8))
    ax.text(U(0.805), V(0.907), "302 钻探台", fontsize=6.6, color=INK, ha="left", va="center",
            zorder=9)

    # tractor road: settlement -> pass -> drill pad
    road = [(0.30, 0.04), (0.35, 0.16), (0.30, 0.30), (0.38, 0.42), (0.44, 0.56),
            (0.42, 0.74), (0.40, 0.92)]
    ax.plot([U(fx) for fx, _ in road], [V(fy) for _, fy in road], color="#C9A876", linewidth=3.2,
            zorder=8, solid_capstyle="round")
    ax.plot([U(fx) for fx, _ in road], [V(fy) for _, fy in road], color=INK, linewidth=0.7,
            zorder=9, linestyle=(0, (4, 3)))

    # pass relic frame in the pinch
    ax.add_patch(Rectangle((U(0.487), V(0.475)), 0.026 * (mw - 5.6), 0.05 * (mh - 7.4),
                           facecolor="#E7D3A8", edgecolor=INK, linewidth=0.9, zorder=10))
    ax.text(U(0.53), V(0.60), "隘口木架", fontsize=6.6, color=INK, ha="left", va="center",
            zorder=11)

    # settlement on the flood plain
    for fx in [0.30, 0.39, 0.48]:
        ax.add_patch(Rectangle((U(fx), V(0.085)), 0.045 * (mw - 5.6), 0.045 * (mh - 7.4),
                               facecolor=PANEL, edgecolor=INK, linewidth=0.8, zorder=10))
    ax.text(U(0.565), V(0.105), "场部宿舍\n稻田", fontsize=6.4, color=INK, ha="left", va="center",
            zorder=11, linespacing=1.4)

    callouts = [
        (0.50, 0.955, "①", "up_terrain = steep_fissure_headwater", "陡裂隙源头：窄沟 + 断层崖涌泉"),
        (0.30, 0.885, "②", "up_geology = active_fault_vein", "活动断层脉：直线碎裂带，钻探台打在上面"),
        (0.50, 0.575, "③", "pass_terrain = canyon_pass", "峡谷隘口：全沟唯一咽喉，古器架在此"),
        (0.36, 0.035, "④", "down_terrain = alluvial_flat", "冲积平坝：可机耕的河漫滩，聚落所在"),
        (0.33, 0.245, "⑤", "accessibility = tractor_road_accessible", "机耕道可达：官能进来，所以 c0 被拆"),
    ]
    for fx, fy, mark, _atom, _hint in callouts:
        marker(ax, U(fx), V(fy), mark, r=1.2, fontsize=7.2)

    # --- right top: the five field values ----------------------------------
    px, pw = 57.4, 39.6
    pt, pb = 92.0, 50.0
    card(ax, px, pb, pw, pt - pb)
    panel_title(ax, px + 1.6, pt - 2.6, "这条沟在规则层里的取值", "#6E93A6")
    hairline(ax, px + 1.6, px + pw - 1.6, pt - 4.8)

    ty = pt - 7.6
    for i, (_fx, _fy, mark, atom, hint) in enumerate(callouts):
        marker(ax, px + 2.6, ty + 0.15, mark, r=0.85, fontsize=6.0, z=6)
        mono(ax, px + 4.3, ty, atom, 7.0, INK, bold=True)
        ty -= 2.0
        for line in wrap(hint, 32)[:2]:
            ax.text(px + 4.3, ty + 0.55, line, fontsize=6.6, color=MUTED, va="top")
            ty -= 1.5
        ty -= 1.8
        if i < len(callouts) - 1:
            hairline(ax, px + 1.6, px + pw - 1.6, ty + 0.85, color="#EAE3D0", lw=0.6)

    for line in wrap("聚落地形（坝区 / 台地）目前不影响结局，只是记录字段；其余四个 atom 已经把这段历史钉死。",
                     32)[:3]:
        ax.text(px + 1.6, ty + 0.2, line, fontsize=6.4, color=FAINT, va="top")
        ty -= 1.6

    # --- right bottom: what the compiler made of it -------------------------
    cb = 26.0
    card(ax, px, cb, pw, 20.5)
    panel_title(ax, px + 1.6, cb + 17.9, "编译结果", "#B08A3F")
    hairline(ax, px + 1.6, px + pw - 1.6, cb + 15.9)
    outcome = [
        ("结局", case["fate"]),
        ("慢变量分区", case["regime"]),
        ("政体", case["polity"]),
        ("镇山古器", case["relic"]),
        ("先例", case["precedent"]),
        ("官方口径", case["archives"]["official_label"]),
        ("技术真因", case["archives"]["tech_label"]),
    ]
    oy = cb + 14.5
    for label, value in outcome:
        value = str(value)
        ax.text(px + 1.6, oy, label, fontsize=6.9, color=MUTED)
        if value.isascii():
            mono(ax, px + 9.0, oy, value, 6.9, INK)
        else:
            ax.text(px + 9.0, oy, value, fontsize=6.9, color=INK)
        hairline(ax, px + 1.6, px + pw - 1.6, oy - 0.95, color="#EFE9D8", lw=0.5)
        oy -= 2.05

    # --- bottom: cross section of the same branch ---------------------------
    sx, sy, sw, sh = 3.0, 3.0, 94.4, 20.5
    card(ax, sx, sy, sw, sh, PANEL, HAIR, lw=1.0, radius=1.2)
    panel_title(ax, sx + 1.8, sy + sh - 2.5, "同一条沟的横剖面（位置与上图对应）", "#7C9864")

    def X(fx):
        return sx + 3.0 + fx * (sw - 6.0)

    def Y(fy):
        return sy + 2.6 + fy * (sh - 7.6)

    # zone bands tie the section back to the map markers above
    zones = [
        (0.02, 0.315, "#EAE5D7", "源头·陡裂隙 ①", 0.16),
        (0.315, 0.63, "#F1E4C6", "隘口·峡谷 ③", 0.47),
        (0.63, 0.98, "#E4EBD6", "聚落·平坝 ④", 0.80),
    ]
    for fx0, fx1, tint, label, label_fx in zones:
        ax.add_patch(Rectangle((X(fx0), Y(0.02)), X(fx1) - X(fx0), Y(1.0) - Y(0.02),
                               facecolor=tint, edgecolor="none", zorder=2, alpha=0.85))
        ax.text(X(label_fx), Y(0.97), label, fontsize=7.2, color=MUTED, ha="center", va="top",
                zorder=8)
    for fx in (0.315, 0.63):
        ax.plot([X(fx), X(fx)], [Y(0.02), Y(0.86)], color=HAIR, linewidth=0.8,
                linestyle=(0, (4, 3)), zorder=3)

    profile = [
        (0.02, 0.08), (0.10, 0.78), (0.17, 0.12), (0.30, 0.10), (0.33, 0.80),
        (0.39, 0.14), (0.45, 0.14), (0.55, 0.78), (0.61, 0.12), (0.74, 0.10),
        (0.86, 0.09), (0.98, 0.08),
    ]
    ax.add_patch(
        Polygon(
            [(X(fx), Y(fy)) for fx, fy in profile] + [(X(0.98), Y(0.02)), (X(0.02), Y(0.02))],
            closed=True, facecolor=ROCK, edgecolor=ROCK_DARK, linewidth=SK_EDGE, zorder=4,
        )
    )
    ax.plot([X(0.02), X(0.98)], [Y(0.08), Y(0.08)], color=WATER, linewidth=3.0, zorder=5)
    ax.text(X(0.135), Y(0.66), "断层崖 + 涌泉", fontsize=6.4, color=INK, ha="center", zorder=8)
    ax.text(X(0.45), Y(0.36), "古器架", fontsize=6.4, color=INK, ha="center", zorder=8)
    ax.text(X(0.80), Y(0.32), "宿舍 / 稻田", fontsize=6.4, color=INK, ha="center", zorder=8)
    for fx in [0.13, 0.36, 0.66]:
        ax.plot([X(fx), X(fx)], [Y(0.08), Y(0.02)], color=FAINT, linewidth=0.5, zorder=3)

    path = OUT / "07_branch_anatomy.png"
    fig.savefig(path, dpi=170, facecolor=PAPER, bbox_inches="tight")
    plt.close(fig)
    print(f"wrote {path}")


def main():
    payload = json.loads(DATA.read_text(encoding="utf-8"))
    cases = [case for case in payload["cases"] if case["status"] == "compiled"]

    render_glossary(
        LANDFORM_FEATURES,
        "地貌词表（上半）：源头、隘口、聚落",
        "这 6 个 atom 决定「沟长什么样」。规则层只认这些取值；世界生成接线后，每一种都必须能在地图上被认出来。",
        OUT / "06a_landform_glossary.png",
        None,
    )

    render_glossary(
        SUBSURFACE_FEATURES,
        "地貌词表（下半）：源头地质与可达性",
        "这 5 个 atom 不改变「长什么样」，只改变「地下是什么」和「人能不能进来」——它们决定脉冲强度与 c0 存亡。",
        OUT / "06b_subsurface_glossary.png",
        [
            "怎么读这两张表",
            "",
            "atom 是接口而不是描写：",
            "世界生成必须能在地图上",
            "把每个 atom 认出来，否则",
            "规则层算出来的历史无处",
            "落地。",
            "",
            "· 源头 2 × 地质 3：只允许",
            "  3 组配对，其余 24 格无历史",
            "· 隘口 2 → 决定镇山古器",
            "· 可达性 2 → 决定 c0 存亡",
            "· 聚落 2 → 目前只是记录字段",
            "",
            "图 07 把 5 个 atom 标在同一",
            "条沟上，看它们怎么组成一个",
            "「地点」。",
        ],
    )

    wanted = {
        "up_terrain": "steep_fissure_headwater",
        "up_geology": "active_fault_vein",
        "pass_terrain": "canyon_pass",
        "down_terrain": "alluvial_flat",
        "accessibility": "tractor_road_accessible",
    }
    match = [case for case in cases if all(case["features"][k] == v for k, v in wanted.items())]
    if len(match) != 1:
        raise SystemExit(
            "the baishui branch (steep fissure + active fault + canyon pass + tractor road + "
            "alluvial flat) no longer compiles to exactly one history; re-run the exporter"
        )

    render_anatomy(match[0])


if __name__ == "__main__":
    main()
