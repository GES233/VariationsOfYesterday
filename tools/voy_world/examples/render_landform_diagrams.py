from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib import font_manager
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch, Polygon


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "diagrams"
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

INK = "#24313A"
MUTED = "#60717B"
PAPER = "#F7F4ED"
PANEL = "#FFFDF8"
BLUE = "#DCEBF1"
GREEN = "#E2EDDF"
OCHRE = "#F2E5C9"
RED = "#F2DEDA"
PURPLE = "#E9E1EF"
GRAY = "#E9ECEB"


def canvas(title, subtitle, figsize=(18, 11)):
    fig, ax = plt.subplots(figsize=figsize, facecolor=PAPER)
    ax.set_facecolor(PAPER)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis("off")
    ax.text(2, 96.5, title, fontsize=23, fontweight="bold", color=INK, va="top")
    ax.text(2, 92.2, subtitle, fontsize=11.5, color=MUTED, va="top")
    return fig, ax


def panel(ax, x, y, w, h, title=None):
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.35,rounding_size=0.8",
        linewidth=1.1, edgecolor="#C9CEC9", facecolor=PANEL, zorder=0,
    )
    ax.add_patch(patch)
    if title:
        ax.text(x + 1.4, y + h - 1.8, title, fontsize=13, fontweight="bold", color=INK, va="top")
    return patch


def box(ax, x, y, w, h, text, fc=BLUE, ec="#9FB3BA", fontsize=10, bold=False, zorder=2):
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.25,rounding_size=0.65",
        linewidth=1.0, edgecolor=ec, facecolor=fc, zorder=zorder,
    )
    ax.add_patch(patch)
    ax.text(
        x + w / 2, y + h / 2, text,
        ha="center", va="center", fontsize=fontsize, color=INK,
        fontweight="bold" if bold else "normal", linespacing=1.35, zorder=zorder + 1,
    )
    return patch


def arrow(ax, start, end, color=MUTED, style="-|>", connectionstyle="arc3", lw=1.5, zorder=4):
    patch = FancyArrowPatch(
        start, end, arrowstyle=style, mutation_scale=13,
        linewidth=lw, color=color, connectionstyle=connectionstyle, zorder=zorder,
    )
    ax.add_patch(patch)
    return patch


def save(fig, name):
    path = OUT / name
    fig.savefig(path, dpi=170, facecolor=PAPER, bbox_inches="tight", pad_inches=0.22)
    plt.close(fig)
    print(path)


def landform_figure():
    fig, ax = canvas(
        "从地貌到结局：Chronicle 实际计算的是什么",
        "核心不是占卜，而是：把支沟抽成离散特征，再用同一份关系规则编译历史；未知字段可以由求解器反填。",
    )

    panel(ax, 2, 49, 34, 38, "1  先抽象一条支沟，而不是读取方块")
    mountains = Polygon(
        [(4, 61), (8, 75), (12, 82), (16, 68), (21, 79), (25, 65), (30, 74), (34, 61)],
        closed=True, facecolor="#E4E7DE", edgecolor="#A8B1A4", linewidth=1.2,
    )
    ax.add_patch(mountains)
    ax.plot([12, 15, 19, 23, 28], [78, 70, 65, 61, 56], color="#6E9EAE", lw=2.2)
    arrow(ax, (22, 62), (28, 56), color="#6E9EAE", lw=2.0)
    for x, y, label in [(12, 78, "UP"), (19, 65, "PASS"), (28, 56, "DOWN")]:
        ax.scatter([x], [y], s=65, color=INK, zorder=5)
        ax.text(x + 0.8, y + 1.0, label, fontsize=8.5, color=INK, fontweight="bold")
    ax.text(5, 83.5, "上游源头", fontsize=10.5, fontweight="bold", color=INK)
    ax.text(5, 80.8, "up_terrain + up_geology", fontsize=9.2, color=MUTED)
    ax.text(20.5, 70.5, "隘口", fontsize=10.5, fontweight="bold", color=INK)
    ax.text(20.5, 67.9, "pass_terrain", fontsize=9.2, color=MUTED)
    ax.text(23.5, 53.0, "下游聚落", fontsize=10.5, fontweight="bold", color=INK)
    ax.text(23.5, 50.5, "down_terrain + accessibility", fontsize=8.8, color=MUTED)
    ax.text(
        4, 58.5,
        "5 个类别字段\n2 种上游 × 2 种隘口 × 2 种下游\n× 3 种地质 × 2 种可达性 = 48 种组合",
        fontsize=10.2, color=INK, va="top", linespacing=1.45,
        bbox=dict(boxstyle="round,pad=0.55", fc=PANEL, ec="none", alpha=0.93),
    )

    panel(ax, 39, 49, 59, 38, "2  Chronicle 是四段关系管线")
    xs = [41, 52.4, 63.8, 75.2, 86.6]
    texts = [
        "topo\n地貌特征\n（可部分未知）",
        "era1\n隘口决定\n镇山古器\n与阻尼方式",
        "era2a\n下游地形决定\n流官是否入寨\n及旧案先例",
        "era2b\n下游×道路 → c0\n上游×地质 →\n工程队与脉冲",
        "era3\nc0 × 山官身份\n× 脉冲\n→ Fate + Regime",
    ]
    colors = [GRAY, GREEN, OCHRE, BLUE, PURPLE]
    for i, (x, text, color) in enumerate(zip(xs, texts, colors)):
        box(ax, x, 64, 9.5, 15.5, text, fc=color, fontsize=9.0, bold=(i == 0))
        if i < 4:
            arrow(ax, (x + 9.6, 71.8), (xs[i + 1] - 0.2, 71.8))
    ax.text(
        41.5, 60.2,
        "era2b：① 平坝+机耕道 → c0_collapsed；台地+绝壁 → c0_intact\n　　　② 陡裂隙+活动断裂 → 302 爆破；落水洞+深应力腔 → 注浆；陡裂隙+小裂隙 → 林场小震",
        fontsize=9.1, color=INK, va="top", linespacing=1.45,
    )
    box(
        ax, 43, 50.2, 51, 4.2,
        "正向：绑定 topo，求 history    ｜    反向：绑定 fate，保留 topo 为逻辑变量",
        fc=GRAY, fontsize=9.9, bold=True,
    )

    panel(ax, 2, 5, 96, 40, "3  一个实际例子：白水坝为什么得到“同化新寨”")
    box(
        ax, 4, 25.5, 27, 14,
        "已知地貌\n\nup = steep_fissure_headwater\npass = canyon_pass\ndown = alluvial_flat\ngeo = active_fault_vein\naccess = tractor_road_accessible",
        fc=GRAY, fontsize=9.4,
    )
    arrow(ax, (31.5, 32.5), (35, 32.5))
    box(
        ax, 35.5, 25.5, 27, 14,
        "历史中间状态\n\nera1：镇山铜钲，c0_intact\nera2a：外甲内寨，1894 铜钲旧案\nera2b：垦殖分队收缴铜钲\n　　　c0_collapsed + 302 队 + alpha_shock",
        fc=OCHRE, fontsize=9.4,
    )
    arrow(ax, (63, 32.5), (66.5, 32.5))
    box(
        ax, 67, 25.5, 29, 14,
        "第三纪结果\n\nFate_Assimilated_NewZhai\nfinal_regime = Oscillating\n\n随后 Projection 才派生：\n遗迹位置、方志、官方通报、技术员批注",
        fc=PURPLE, fontsize=9.4,
    )
    ax.text(
        4, 21.5,
        "这里的 c0、脉冲和 Regime 是规则使用的离散词汇；Elixir 原型没有执行 Scala MandateState 的 RK4 数值积分。",
        fontsize=10.5, color=MUTED, va="top",
    )
    ax.text(
        4, 17.3,
        "地貌 → 历史的代码：chronicle.ex:8 / 44 / 87 / 181；地貌字段：schema.ex:17；遗迹与档案：projection.ex:6 / 107",
        fontsize=9.7, color=MUTED, va="top",
    )
    box(
        ax, 4, 7, 92, 6.5,
        "因此 Chronicle 更像“带可逆查询的历史规则数据库”：正向生成设定，反向检查哪些地貌与历史满足设定约束。",
        fc=BLUE, fontsize=11.5, bold=True,
    )
    save(fig, "01_landform_to_chronicle.png")


def fate_matrix_figure():
    fig, ax = canvas(
        "从结局反推地貌：当前规则的全部完整解",
        "四种 Fate 各有 2 个候选；差别只在隘口及其镇山器。另有 4 个组合能编译到 era2b，却没有任何 era3 规则可接续。",
        figsize=(19, 11),
    )
    columns = [
        (2, 15, "结局 / Regime"),
        (17, 16, "上游地形"),
        (33, 16, "上游地质"),
        (49, 19, "下游地形 + 可达性"),
        (68, 15, "隘口：每行 2 解"),
        (83, 15, "era2b → era3 关键条件"),
    ]
    for x, w, title in columns:
        box(ax, x, 82, w, 6, title, fc=GRAY, fontsize=10.2, bold=True)

    rows = [
        (
            67, BLUE,
            "同化 · Oscillating\nFate_Assimilated_NewZhai",
            "陡裂隙源头\nsteep_fissure_headwater",
            "活动断裂脉\nactive_fault_vein",
            "冲积平坝 + 机耕道可达\nalluvial_flat\ntractor_road_accessible",
            "峡谷隘口 → 铜钲\n或\n溶洞峡口 → 分水碑",
            "c0_collapsed\n+ alpha_shock\n302 浅层爆破",
        ),
        (
            52, RED,
            "湮灭 · Burnout\nFate_Annihilated_BurnoutZone",
            "喀斯特落水洞\nkarst_sinkhole_headwater",
            "深层应力腔\ndeep_stress_cavity",
            "冲积平坝 + 机耕道可达\nalluvial_flat\ntractor_road_accessible",
            "峡谷隘口 → 铜钲\n或\n溶洞峡口 → 分水碑",
            "c0_collapsed\n+ 强负向抑制\n高压反向注浆",
        ),
        (
            37, PURPLE,
            "飞升 · Strained\nFate_Ascended_SpatialRift",
            "喀斯特落水洞\nkarst_sinkhole_headwater",
            "深层应力腔\ndeep_stress_cavity",
            "半山台地 + 绝壁隔绝\nmid_terrace\ncliff_isolated",
            "峡谷隘口 → 铜钲\n或\n溶洞峡口 → 分水碑",
            "c0_intact + 山官保留\n+ 强负向抑制\n形成空间裂隙",
        ),
        (
            22, GREEN,
            "幸存对照 · Calm\nFate_Ancestral_ControlGroup",
            "陡裂隙源头\nsteep_fissure_headwater",
            "小型裂隙\nminor_fracture",
            "半山台地 + 绝壁隔绝\nmid_terrace\ncliff_isolated",
            "峡谷隘口 → 铜钲\n或\n溶洞峡口 → 分水碑",
            "c0_intact + 山官保留\n+ heavy_kick\n林场低强度扰动",
        ),
    ]
    for y, color, *texts in rows:
        for (x, w, _), text in zip(columns, texts):
            box(ax, x, y, w, 14, text, fc=color, fontsize=8.8)

    ax.text(2, 19.5, "能推进到 era2b、但 era3 无规则：unresolved_history", fontsize=12, fontweight="bold", color=INK)
    box(
        ax, 2, 5, 47, 12.5,
        "陡裂隙 + 小裂隙 + 冲积平坝 + 机耕道\n→ c0_collapsed + heavy_kick\n没有“古器已毁但仅受低强度扰动”的结局规则\n隘口 2 种，因此占 2 个组合",
        fc=OCHRE, fontsize=9.6,
    )
    box(
        ax, 51, 5, 47, 12.5,
        "陡裂隙 + 活动断裂 + 半山台地 + 绝壁\n→ c0_intact + 山官保留 + alpha_shock\n没有“古器完好但遭遇爆破”的结局规则\n隘口 2 种，因此占 2 个组合",
        fc=OCHRE, fontsize=9.6,
    )
    ax.text(
        2, 2.2,
        "总计 48：8 个完整解 + 4 个 unresolved_history + 36 个 unsupported_topography。反向查询返回候选集，不保证只有唯一地貌。",
        fontsize=10.7, color=MUTED,
    )
    save(fig, "02_fate_to_landform_matrix.png")


def solver_figure():
    fig, ax = canvas(
        "同一份关系为什么能双向执行：microKanren 求解过程",
        "以 reverse_node(fate = Fate_Ascended_SpatialRift) 为例。正向与反向不是两套算法，区别只是哪些变量预先有值。",
        figsize=(18, 11),
    )

    panel(ax, 2, 60, 96, 27, "1  查询就是一串 Goal；每个 Goal 接收 State，返回可能状态的 Stream")
    steps = [
        (4, 13, "fresh\n创建逻辑变量\n?up ?pass ?down\n?geo ?access …", GRAY),
        (19, 15, "eq + propso\n建立封闭 topo 骨架\n并绑定已知特征", BLUE),
        (36, 16, "history_through_era3o\n依次合取四段历史关系\nconde 产生候选分支", OCHRE),
        (54, 14, "fate_constraintgo\n只保留 era3.fate\n等于目标结局", PURPLE),
        (70, 12, "run_all\n拉取搜索流\n枚举全部解", GREEN),
        (84, 12, "reify + to_public\n把内部 Var/atom\n变成 JSON-safe map", GRAY),
    ]
    for i, (x, w, text, color) in enumerate(steps):
        box(ax, x, 66, w, 14, text, fc=color, fontsize=9.3)
        if i < len(steps) - 1:
            next_x = steps[i + 1][0]
            arrow(ax, (x + w + 0.2, 73), (next_x - 0.2, 73))
    ax.text(
        4, 62.5,
        "State = subst（变量绑定）+ counter（新变量编号）+ neq（不等约束）；unify 失败则该分支成为空流，成功则扩展 subst。",
        fontsize=10.5, color=MUTED,
    )

    panel(ax, 2, 28, 46, 28, "2  反向求解时，地貌不是输入答案，而是待求变量")
    box(
        ax, 4, 32.5, 42, 18.5,
        "查询前已知：\nfate = Fate_Ascended_SpatialRift\n\n求解后各成功分支都会绑定：\n?up     → karst_sinkhole_headwater\n?down → mid_terrace\n?geo   → deep_stress_cavity\n?access → cliff_isolated\nc0       → c0_intact\npulse  → OverwhelmingNegativeSuppression",
        fc=BLUE, fontsize=9.5,
    )
    ax.text(
        4, 30.2,
        "正向 compile_node 只是先把这五项地貌绑定，再跑同一组关系。",
        fontsize=9.9, color=MUTED,
    )

    panel(ax, 51, 28, 47, 28, "3  为什么返回 2 个候选，而不是 1 个")
    box(
        ax, 53, 43, 43, 8.5,
        "共同约束已经锁定上游、地质、下游、道路、c0 与脉冲\n但 era3 没有约束 pass_terrain，所以 era1 的两条隘口分支都保留",
        fc=PURPLE, fontsize=10.0,
    )
    box(
        ax, 53, 33.5, 20, 7.5,
        "分支 A\n?pass → canyon_pass\n?relic → ancestral_bronze_zheng",
        fc=GREEN, fontsize=9.1,
    )
    box(
        ax, 76, 33.5, 20, 7.5,
        "分支 B\n?pass → karst_gorge\n?relic → carved_diversion_stele",
        fc=GREEN, fontsize=9.1,
    )
    ax.text(53, 30.2, "这就是“反向推理”：搜索满足同一约束网络的变量绑定。", fontsize=9.9, color=MUTED)

    panel(ax, 2, 4, 96, 20, "4  这套原型能做什么、不能做什么")
    box(
        ax, 4, 7, 29, 12,
        "能做\n有限类别特征的正向编译与反向候选求解\n部分已知地貌下的约束补全\n档案/遗迹与共同历史保持一致",
        fc=GREEN, fontsize=9.8,
    )
    box(
        ax, 35.5, 7, 29, 12,
        "不做\n不从 Minecraft 高度图自动提取这些类别\n不反向放置方块或生成 D8 汇流树\n不运行连续动力学数值积分",
        fc=RED, fontsize=9.8,
    )
    box(
        ax, 67, 7, 29, 12,
        "实现落点\nunify / bind / mplus：kanren/core.ex\nfresh / conde：kanren/dsl.ex\n节点查询与失败分类：world/node.ex",
        fc=GRAY, fontsize=9.8,
    )
    save(fig, "03_microkanren_bidirectional_solver.png")


if __name__ == "__main__":
    landform_figure()
    fate_matrix_figure()
    solver_figure()
