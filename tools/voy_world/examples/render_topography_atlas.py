"""Render the topography atlas from the compiler's own output.

Nothing here decides what a world is: `export_topography_cases.exs` asks the
rule layer about all 48 feature combinations and records the answer, and this
script only lays that answer out. Run the exporter first:

    mix run examples/export_topography_cases.exs
    python examples/render_topography_atlas.py
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
OUT.mkdir(parents=True, exist_ok=True)


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
        return
    raise RuntimeError("A CJK font is required to render these diagrams")


configure_font()
plt.rcParams["axes.unicode_minus"] = False
# Figure 05 prints fate atoms in fixed width; figure 04 does not use this.
plt.rcParams["font.monospace"] = [
    "Consolas",
    "Courier New",
    "Noto Sans Mono",
    "DejaVu Sans Mono",
]

INK = "#24313A"
MUTED = "#60717B"
FAINT = "#9AA9B1"
PAPER = "#F7F4ED"
PANEL = "#FFFDF8"
BLUE = "#DCEBF1"
GREEN = "#E2EDDF"
OCHRE = "#F2E5C9"
RED = "#F2DEDA"
PURPLE = "#E9E1EF"
GRAY = "#E9ECEB"
ROCK = "#D8D2C4"
WATER = "#9FC4CE"
DARK = "#4A5A64"

# Figure 05 shares the warmer palette of the glossary figures (06/07); figure
# 04 keeps the original constants above untouched.
P5_PAPER = "#F4EFE2"
P5_PANEL = "#FDFBF4"
P5_INK = "#2E2B23"
P5_MUTED = "#7C7462"
P5_FAINT = "#A99F8A"
P5_HAIR = "#DCD4BF"
P5_SHADOW = "#3A3423"
P5_ROCK = "#E4DDCA"
P5_ROCK_DARK = "#B9AF97"
P5_WATER = "#8FB9C6"
P5_OFFICIAL = "#8A5B4A"
P5_TECH = "#54665C"

FATE_STYLE = {
    "Fate_Assimilated_NewZhai": ("同化", "退化新寨", OCHRE, "#CBB27A"),
    "Fate_Annihilated_BurnoutZone": ("湮灭", "烧毁死寂区", GRAY, "#B4BBB9"),
    "Fate_Ascended_SpatialRift": ("飞升", "空间裂隙", PURPLE, "#B49FC4"),
    "Fate_Ancestral_ControlGroup": ("幸存", "山官岁祭", GREEN, "#A8BE9F"),
    "Fate_Deferred_ChronicSeep": ("渗漏", "慢性未爆", BLUE, "#9BB8C4"),
    "Fate_Tolerated_FalseAlarm": ("虚警", "反复吸收", RED, "#CBA79F"),
}

FATE_ORDER = [
    "Fate_Assimilated_NewZhai",
    "Fate_Annihilated_BurnoutZone",
    "Fate_Deferred_ChronicSeep",
    "Fate_Ascended_SpatialRift",
    "Fate_Tolerated_FalseAlarm",
    "Fate_Ancestral_ControlGroup",
]

TERRAIN = {
    "steep_fissure_headwater": "陡裂隙源头",
    "karst_sinkhole_headwater": "落水洞源头",
    "canyon_pass": "峡谷隘口",
    "karst_gorge": "溶洞峡口",
    "alluvial_flat": "冲积平坝",
    "mid_terrace": "半山台地",
    "active_fault_vein": "活动断层脉",
    "deep_stress_cavity": "深层应力空腔",
    "minor_fracture": "微裂隙",
    "tractor_road_accessible": "机耕道可达",
    "cliff_isolated": "绝壁不通车",
}

TEAM = {
    "survey_blasting_team_302": "302爆破队",
    "hq_high_pressure_grouting_corps": "直属高压注浆处",
    "forestry_hydro_post": "林场水文哨",
}

DISTURBANCE = {
    "periodic_shallow_blasting": "周期浅层爆破",
    "overwhelming_reverse_suppression": "超高压反向封井",
    "low_level_logging_shock": "低强度采伐震动",
}

RELIC = {
    "ancestral_bronze_zheng": "镇山铜钲",
    "carved_diversion_stele": "石雕分水堰",
}

GOVERNANCE = {
    "brigade_elder_joint_council": "场部—议事会",
    "depopulated_concrete_sealed_zone": "封山死寂区",
    "rift_guarding_cliff_compact": "守山孤寨",
    "restored_ancestral_compact": "山官岁祭",
    "deferred_evacuation_watchpost": "待撤观测站",
    "cliff_observation_hermitage": "绝壁观测孤寨",
}

REGIME = {
    "Calm": "静息 Calm",
    "Strained": "临界慢化 Strained",
    "Oscillating": "自持振荡 Oscillating",
    "Burnout": "烧毁 Burnout",
}

PAIRS = [
    ("steep_fissure_headwater", "active_fault_vein"),
    ("karst_sinkhole_headwater", "deep_stress_cavity"),
    ("steep_fissure_headwater", "minor_fracture"),
]

COLUMNS = [
    ("canyon_pass", "tractor_road_accessible"),
    ("canyon_pass", "cliff_isolated"),
    ("karst_gorge", "tractor_road_accessible"),
    ("karst_gorge", "cliff_isolated"),
]


def load_cases():
    payload = json.loads(DATA.read_text(encoding="utf-8"))
    compiled = {}
    for case in payload["cases"]:
        if case["status"] != "compiled":
            continue
        f = case["features"]
        key = (f["up_terrain"], f["up_geology"], f["pass_terrain"], f["accessibility"])
        compiled.setdefault(key, {})[f["down_terrain"]] = case
    return payload, compiled


def audit(compiled):
    """The matrix is only legal if the downstream terrain changes nothing."""
    for tiers in compiled.values():
        answers = {
            (case["fate"], case["precedent"], case["relic"], case["regime"])
            for case in tiers.values()
        }
        if len(answers) != 1:
            return False
    return True


def canvas(title, subtitle, figsize, title_y=97.0, subtitle_y=93.2, paper=PAPER):
    fig = plt.figure(figsize=figsize, facecolor=paper)
    ax = fig.add_axes([0, 0, 1, 1])
    ax.set_facecolor(paper)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis("off")
    ax.text(2.2, title_y, title, fontsize=21, fontweight="bold", color=INK, va="top")
    ax.text(2.2, subtitle_y, subtitle, fontsize=10.4, color=MUTED, va="top")
    return fig, ax


def card(ax, x, y, w, h, face, edge, lw=1.2, radius=1.2, alpha=1.0):
    ax.add_patch(
        FancyBboxPatch(
            (x, y),
            w,
            h,
            boxstyle=f"round,pad=0,rounding_size={radius}",
            facecolor=face,
            edgecolor=edge,
            linewidth=lw,
            alpha=alpha,
            zorder=2,
        )
    )


def wrap(text, width):
    return textwrap.wrap(text, width) or [""]


# ---------------------------------------------------------------- figure 04


def render_matrix(payload, compiled):
    fig, ax = canvas(
        "地形组合 → 结局：一张可证的因果矩阵",
        "每一格都由 Voy.World.compile_branch/1 现算（examples/export_topography_cases.exs）："
        f"{payload['counts']['compiled']} 个可生成世界 + {payload['counts']['unsupported_topography']} 个地质未配对 = 48",
        (19.6, 12.6),
    )

    left, col_w, col_gap = 20.5, 12.4, 1.5
    row_h = 15.0
    header_y = 77.5

    for index, (pass_terrain, access) in enumerate(COLUMNS):
        x = left + index * (col_w + col_gap)
        face = OCHRE if access == "tractor_road_accessible" else BLUE
        card(ax, x, header_y, col_w, 7.6, face, "#C9C2B2", lw=1.0)
        ax.text(
            x + col_w / 2,
            header_y + 5.0,
            TERRAIN[pass_terrain],
            fontsize=11.0,
            fontweight="bold",
            color=INK,
            ha="center",
        )
        ax.text(
            x + col_w / 2,
            header_y + 1.9,
            "通车 → c0 被拆" if access == "tractor_road_accessible" else "断路 → c0 完好",
            fontsize=8.8,
            color=MUTED,
            ha="center",
        )

    for row_index, (up, geology) in enumerate(PAIRS):
        y = header_y - 3.0 - (row_index + 1) * row_h - row_index * 4.2
        sample = compiled[(up, geology, "canyon_pass", "tractor_road_accessible")][
            "alluvial_flat"
        ]

        card(ax, 2.0, y, left - 3.6, row_h, PANEL, "#D9D2C4", lw=1.0)
        ax.text(3.3, y + row_h - 3.4, TERRAIN[up], fontsize=12.0, fontweight="bold", color=INK)
        ax.text(3.3, y + row_h - 7.6, f"+ {TERRAIN[geology]}", fontsize=10.0, color=DARK)
        ax.text(
            3.3, y + row_h - 11.2, f"进驻：{TEAM[sample['stationed_team']]}", fontsize=9.4, color=MUTED
        )
        ax.text(
            3.3, y + 1.6, DISTURBANCE[sample["disturbance"]], fontsize=9.2, color=FAINT
        )

        for col_index, (pass_terrain, access) in enumerate(COLUMNS):
            x = left + col_index * (col_w + col_gap)
            case = compiled[(up, geology, pass_terrain, access)]["alluvial_flat"]
            short, sub, face, edge = FATE_STYLE[case["fate"]]
            card(ax, x, y, col_w, row_h, face, edge, lw=1.3)
            ax.text(
                x + col_w / 2, y + row_h - 4.4, short, fontsize=15.0, fontweight="bold",
                color=INK, ha="center",
            )
            ax.text(x + col_w / 2, y + row_h - 8.2, sub, fontsize=9.2, color=DARK, ha="center")
            ax.text(
                x + col_w / 2, y + row_h - 11.6, REGIME[case["regime"]], fontsize=8.6,
                color=MUTED, ha="center",
            )
            ax.text(
                x + col_w / 2, y + 1.6, f"{RELIC[case['relic']]} · 2 种地形", fontsize=8.2,
                color=FAINT, ha="center",
            )

    # right panel
    panel_x, panel_w = 77.6, 20.4
    panel_top, panel_bottom = header_y + 7.6, 22.0
    card(ax, panel_x, panel_bottom, panel_w, panel_top - panel_bottom, PANEL, "#D9D2C4", lw=1.0)
    ax.text(
        panel_x + 1.5, panel_top - 3.2, "扰动表未配对的地质", fontsize=11.4,
        fontweight="bold", color=INK,
    )
    ax.text(
        panel_x + 1.5,
        panel_top - 7.4,
        "这 24 格没有历史。源头地质\n与地表形态从未被写成同一\n条通道，所以不是「还没写」，\n而是「规则不允许」。",
        fontsize=8.8,
        color=MUTED,
        va="top",
        linespacing=1.7,
    )

    unpaired = [
        ("陡裂隙源头", "深层应力空腔", "空腔塌陷：无人的灾变"),
        ("落水洞源头", "活动断层脉", "断层微震：山自己敲"),
        ("落水洞源头", "微裂隙", "溶蚀慢渗：只抬基线"),
    ]
    for index, (up_label, geo_label, hint) in enumerate(unpaired):
        y = panel_top - 26.0 - index * 12.4
        card(ax, panel_x + 1.4, y, panel_w - 2.8, 10.8, GRAY, "#CFD4D2", lw=1.0)
        ax.text(panel_x + 2.6, y + 7.4, up_label, fontsize=9.6, color=INK)
        ax.text(panel_x + 2.6, y + 4.4, f"+ {geo_label}", fontsize=8.8, color=DARK)
        ax.text(panel_x + 2.6, y + 1.5, hint, fontsize=8.2, color=MUTED)

    ax.text(
        panel_x + 1.5,
        panel_bottom + 3.0,
        "留给 v2：要改 Dynamics 的\n扰动通道语义，不能只写结局。",
        fontsize=8.6,
        color=MUTED,
        va="bottom",
        linespacing=1.6,
    )

    # legend
    legend_title_y = 19.4
    ax.text(2.2, legend_title_y, "六种结局", fontsize=11.0, fontweight="bold", color=INK)
    for index, fate in enumerate(FATE_ORDER):
        short, sub, face, edge = FATE_STYLE[fate]
        x = 2.2 + index * 12.5
        card(ax, x, legend_title_y - 5.8, 11.6, 4.4, face, edge, lw=1.0)
        ax.text(x + 5.8, legend_title_y - 3.6, f"{short} · {sub}", fontsize=8.4, color=INK, ha="center")

    notes = [
        "结局 ← (可达性 → c0) × (源头地质配对 → 脉冲)：2 × 3 的叉积里被写了 6 个象限。",
        "古器 ← 隘口地形：峡谷隘口 → 镇山铜钲，溶洞峡口 → 石雕分水堰。",
        "先例 ← 可达性 + 古器：通车 → 铜钲案 / 石堰案，断路 → 立碑案。",
        "聚落地形（冲积平坝 / 半山台地）目前只作为记录字段：坝区与台地给出完全相同的结局、古器与先例。",
        "※ 每格实际对应 2 个世界（坝区 + 台地）：3 配对 × 2 隘口 × 2 可达性 × 2 地形 = 24。",
    ]
    for index, line in enumerate(notes):
        ax.text(2.2, 10.6 - index * 2.15, line, fontsize=9.2, color=INK if index < 3 else MUTED)

    path = OUT / "04_topography_matrix.png"
    fig.savefig(path, dpi=170, facecolor=PAPER, bbox_inches="tight")
    plt.close(fig)
    print(f"wrote {path}")


# ---------------------------------------------------------------- figure 05


def profile(ax, case, bx, by, bw, bh):
    """A schematic cross-section: source -> pass -> settlement.

    Drawn directly in figure coordinates inside (bx, by, bw, bh): the section
    sits on top, the three zone captions and the pulse/c0 note below it.
    """
    features = case["features"]
    up, pass_terrain, down = (
        features["up_terrain"],
        features["pass_terrain"],
        features["down_terrain"],
    )

    label_h = 3.1  # reserved at the bottom for zone captions + note

    def X(fx):
        return bx + 0.6 + fx * (bw - 1.2)

    def Y(fy):
        return by + label_h + fy / 0.84 * (bh - label_h - 0.5)

    def poly(points, face, edge=P5_ROCK_DARK, lw=1.0, zorder=3):
        ax.add_patch(
            Polygon(
                [(X(fx), Y(fy)) for fx, fy in points],
                closed=True,
                facecolor=face,
                edgecolor=edge,
                linewidth=lw,
                zorder=zorder,
            )
        )

    def circle(fx, fy, r, face):
        ax.add_patch(
            Ellipse(
                (X(fx), Y(fy)), 2 * r * (bw - 1.2), 2 * r / 0.84 * (bh - label_h - 0.5),
                facecolor=face, edgecolor="none", zorder=4,
            )
        )

    if up == "steep_fissure_headwater":
        poly([(0.00, 0.02), (0.00, 0.72), (0.09, 0.26), (0.15, 0.02)], P5_ROCK)
        poly([(0.34, 0.02), (0.34, 0.72), (0.25, 0.26), (0.19, 0.02)], P5_ROCK)
        poly([(0.12, 0.02), (0.15, 0.14), (0.19, 0.14), (0.22, 0.02)], P5_WATER, "none", lw=0)
    else:
        poly(
            [
                (0.00, 0.02), (0.00, 0.72), (0.08, 0.30), (0.14, 0.14), (0.20, 0.14),
                (0.26, 0.30), (0.34, 0.72), (0.34, 0.02),
            ],
            P5_ROCK,
        )
        circle(0.17, 0.06, 0.032, P5_INK)

    if pass_terrain == "canyon_pass":
        poly([(0.35, 0.02), (0.35, 0.78), (0.45, 0.78), (0.47, 0.02)], P5_ROCK)
        poly([(0.53, 0.02), (0.55, 0.78), (0.65, 0.78), (0.65, 0.02)], P5_ROCK)
    else:
        poly(
            [(0.35, 0.02), (0.35, 0.50), (0.38, 0.66), (0.43, 0.62), (0.47, 0.28), (0.47, 0.02)],
            P5_ROCK,
        )
        poly(
            [(0.53, 0.02), (0.53, 0.28), (0.57, 0.62), (0.62, 0.66), (0.65, 0.50), (0.65, 0.02)],
            P5_ROCK,
        )
        circle(0.404, 0.22, 0.024, P5_INK)
        poly([(0.47, 0.02), (0.485, 0.16), (0.515, 0.16), (0.53, 0.02)], P5_WATER, "none", lw=0)

    if down == "alluvial_flat":
        poly([(0.66, 0.02), (0.99, 0.02), (0.99, 0.16), (0.66, 0.16)], P5_ROCK)
        xs = 0.72
        for _ in range(3):
            ax.add_patch(
                Rectangle((X(xs), Y(0.16)), 0.050 * (bw - 1.2), Y(0.29) - Y(0.16),
                          facecolor=P5_PANEL, edgecolor=P5_INK, linewidth=0.8, zorder=5)
            )
            xs += 0.082
    else:
        poly(
            [(0.66, 0.02), (0.99, 0.02), (0.99, 0.56), (0.78, 0.56), (0.78, 0.30),
             (0.66, 0.30)],
            P5_ROCK,
        )
        xs = 0.81
        for _ in range(3):
            ax.add_patch(
                Rectangle((X(xs), Y(0.56)), 0.048 * (bw - 1.2), Y(0.69) - Y(0.56),
                          facecolor=P5_PANEL, edgecolor=P5_INK, linewidth=0.8, zorder=5)
            )
            xs += 0.060

    # markers
    ax.add_patch(
        Rectangle((X(0.042), Y(0.46)), 0.062 * (bw - 1.2), Y(0.59) - Y(0.46),
                  facecolor="#F3D7C4", edgecolor="#B4795A", linewidth=0.9, zorder=6)
    )
    ax.text(X(0.073), Y(0.525), "队", fontsize=6.4, color="#7C3F2A", ha="center", va="center",
            zorder=7)
    ax.text(X(0.073), Y(0.40), TEAM[case["stationed_team"]], fontsize=6.2, color="#7C3F2A",
            ha="center", va="top", zorder=7)

    ax.add_patch(
        Rectangle(
            (X(0.485), Y(0.16)),
            0.030 * (bw - 1.2),
            Y(0.38) - Y(0.16),
            facecolor="#EFE3C2" if "zheng" in case["relic"] else "#DCE6DA",
            edgecolor=P5_INK,
            linewidth=0.9,
            zorder=6,
        )
    )

    ax.plot([X(0.0), X(1.0)], [Y(0.02), Y(0.02)], color=P5_FAINT, linewidth=0.7, zorder=1)
    ax.text(X(0.17), by + 1.95, f"源头·{TERRAIN[up]}", fontsize=6.4, color=P5_MUTED, ha="center")
    ax.text(
        X(0.50), by + 1.95, f"隘口·{TERRAIN[pass_terrain]}（{RELIC[case['relic']]}）",
        fontsize=6.4, color=P5_MUTED, ha="center",
    )
    ax.text(X(0.82), by + 1.95, f"聚落·{TERRAIN[down]}", fontsize=6.4, color=P5_MUTED,
            ha="center")
    ax_note = "上游在做什么 → 脉冲　｜　古器在不在 → c0"
    ax.text(X(0.50), by + 0.75, ax_note, fontsize=6.2, color=P5_FAINT, ha="center")


def render_profiles(compiled):
    fig, ax = canvas(
        "六种结局的同一副骨相：源头 → 隘口 → 聚落",
        "骨相相同，差别只在两处：古器还在不在（c0），上游在做什么（脉冲）。图上每条描述都是改后编译产物。",
        (19.6, 16.4),
        title_y=98.0,
        subtitle_y=94.6,
        paper=P5_PAPER,
    )

    # (up_terrain, up_geology, access, down_terrain, pass_terrain)
    picks = {
        "Fate_Assimilated_NewZhai": (
            "steep_fissure_headwater", "active_fault_vein", "tractor_road_accessible",
            "alluvial_flat", "canyon_pass",
        ),
        "Fate_Annihilated_BurnoutZone": (
            "karst_sinkhole_headwater", "deep_stress_cavity", "tractor_road_accessible",
            "alluvial_flat", "karst_gorge",
        ),
        "Fate_Deferred_ChronicSeep": (
            "steep_fissure_headwater", "minor_fracture", "tractor_road_accessible",
            "alluvial_flat", "canyon_pass",
        ),
        "Fate_Ascended_SpatialRift": (
            "karst_sinkhole_headwater", "deep_stress_cavity", "cliff_isolated", "mid_terrace",
            "karst_gorge",
        ),
        "Fate_Tolerated_FalseAlarm": (
            "steep_fissure_headwater", "active_fault_vein", "cliff_isolated", "mid_terrace",
            "canyon_pass",
        ),
        "Fate_Ancestral_ControlGroup": (
            "steep_fissure_headwater", "minor_fracture", "cliff_isolated", "mid_terrace",
            "canyon_pass",
        ),
    }

    card_w, card_h = 30.8, 42.0
    positions = []
    for row in range(2):
        for col in range(3):
            positions.append((2.0 + col * 32.4, 4.2 + (1 - row) * 45.6, card_w, card_h))

    for fate, (x, y, w, h) in zip(FATE_ORDER, positions):
        up, geology, access, down, pass_terrain = picks[fate]
        case = compiled[(up, geology, pass_terrain, access)][down]
        short, sub, face, edge = FATE_STYLE[case["fate"]]
        top, pad = y + h, 1.6

        # soft shadow + panel, fate colour only on the edge and the accent tick
        card(ax, x + 0.32, y - 0.42, w, h, P5_SHADOW, "none", lw=0, radius=1.0, alpha=0.08)
        card(ax, x, y, w, h, P5_PANEL, edge, lw=1.2, radius=1.0)

        ax.add_patch(
            Rectangle((x + pad, top - 4.35), 0.45, 1.7, facecolor=edge, edgecolor="none",
                      zorder=4)
        )
        ax.text(x + pad + 1.1, top - 3.55, f"{short} · {sub}", fontsize=13.5,
                fontweight="bold", color=P5_INK)
        ax.text(x + pad + 1.1, top - 6.15, case["fate"], fontsize=6.8, family="monospace",
                color=P5_MUTED)
        ax.text(
            x + pad + 1.1, top - 7.85,
            f"{REGIME[case['regime']]}　｜　政体 {GOVERNANCE[case['polity']]}",
            fontsize=6.8, color=P5_MUTED,
        )

        # cross-section on a fate-tinted panel
        px, py, pw, ph = x + pad, top - 22.0, w - 2 * pad, 13.2
        card(ax, px, py, pw, ph, face, P5_HAIR, lw=0.7, radius=0.7)
        profile(ax, case, px + 0.4, py + 0.4, pw - 0.8, ph - 0.8)

        text_y = top - 23.8
        for label, body in [
            ("源头", case["structures"]["upstream_site"][1]),
            ("隘口", case["structures"]["pass_site"][1]),
            ("聚落", case["structures"]["downstream_site"][1]),
        ]:
            block = wrap(f"{label}：{body}", 34)[:2]
            ax.text(
                x + pad, text_y, "\n".join(block), fontsize=7.5, color=P5_INK, va="top",
                linespacing=1.55,
            )
            text_y -= 1.85 * len(block) + 0.85

        ax.plot([x + pad, x + w - pad], [y + 4.35, y + 4.35], color=P5_HAIR, linewidth=0.7,
                zorder=3)
        ax.text(
            x + pad, y + 3.05, wrap(f"官方口径：{case['archives']['official_label']}", 34)[0],
            fontsize=6.9, color=P5_OFFICIAL,
        )
        ax.text(
            x + pad, y + 1.65, wrap(f"技术真因：{case['archives']['tech_label']}", 34)[0],
            fontsize=6.9, color=P5_TECH,
        )

    path = OUT / "05_fate_profiles.png"
    fig.savefig(path, dpi=170, facecolor=P5_PAPER, bbox_inches="tight")
    plt.close(fig)
    print(f"wrote {path}")


def main():
    payload, compiled = load_cases()
    if len(compiled) != 12:
        raise SystemExit(
            f"expected 12 distinct (geology pair, pass, access) worlds, got {len(compiled)}; "
            "re-run the exporter"
        )
    if not audit(compiled):
        raise SystemExit(
            "the downstream terrain changed something for some world: the matrix figure "
            "would be lying, fix the renderer before publishing it"
        )
    render_matrix(payload, compiled)
    render_profiles(compiled)


if __name__ == "__main__":
    main()
