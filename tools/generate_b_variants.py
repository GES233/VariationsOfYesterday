import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = "./assets"


def fmt(val):
    return f"{val:.2f}"


def base_b_frame_open(clip_id):
    """Shared Concept B outer parchment tile, registration marks, octagonal Bagua frame, and compass dial."""
    svg = []
    svg.append('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">')
    svg.append(f'''
    <defs>
      <clipPath id="{clip_id}">
        <circle cx="256" cy="256" r="168"/>
      </clipPath>
    </defs>
    ''')

    # Aged 1980s Dossier Parchment Background
    svg.append('<rect x="10" y="10" width="492" height="492" rx="68" ry="68" fill="#EFE5CF" stroke="#1E1B18" stroke-width="10"/>')
    svg.append('<rect x="24" y="24" width="464" height="464" rx="56" ry="56" fill="none" stroke="#C8372D" stroke-width="3.5"/>')

    # Corner registration marks + tiny voxel square accents
    for cx, cy, sx, sy in [(46, 46, 1, 1), (466, 46, -1, 1), (46, 466, 1, -1), (466, 466, -1, -1)]:
        svg.append(f'<path d="M {cx} {cy+26*sy} L {cx} {cy} L {cx+26*sx} {cy}" fill="none" stroke="#1E1B18" stroke-width="4.5" stroke-linecap="square"/>')
        svg.append(f'<rect x="{cx+8*sx - 4}" y="{cy+8*sy - 4}" width="8" height="8" fill="#C8372D"/>')

    # Outer Octagonal Bagua / Geomatic Frame
    oct_pts = []
    for k in range(8):
        ang = math.radians(22.5 + k * 45)
        r = 228
        oct_pts.append(f"{fmt(256 + r * math.cos(ang))},{fmt(256 + r * math.sin(ang))}")
    svg.append(f'<polygon points="{" ".join(oct_pts)}" fill="#1E1B18" stroke="#1E1B18" stroke-width="6" stroke-linejoin="round"/>')

    oct_pts_in = []
    for k in range(8):
        ang = math.radians(22.5 + k * 45)
        r = 216
        oct_pts_in.append(f"{fmt(256 + r * math.cos(ang))},{fmt(256 + r * math.sin(ang))}")
    svg.append(f'<polygon points="{" ".join(oct_pts_in)}" fill="#EFE5CF" stroke="#C8372D" stroke-width="3" stroke-linejoin="round"/>')

    # Compass Ring (Luopan / Clock / Musical Measure Dial)
    svg.append('<circle cx="256" cy="256" r="198" fill="#1E1B18"/>')
    svg.append('<circle cx="256" cy="256" r="170" fill="#EFE5CF"/>')

    # Degree / Chrono ticks (60-minute / 24-mountain divisions)
    for deg in range(0, 360, 6):
        rad = math.radians(deg)
        if deg % 30 == 0:
            r1, r2, w, col = 171, 197, 3.8, "#EFE5CF"
        elif deg % 18 == 0:
            r1, r2, w, col = 175, 194, 2.5, "#C8372D"
        else:
            r1, r2, w, col = 180, 190, 1.5, "#EFE5CF"
        x1, y1 = 256 + r1 * math.cos(rad), 256 + r1 * math.sin(rad)
        x2, y2 = 256 + r2 * math.cos(rad), 256 + r2 * math.sin(rad)
        svg.append(f'<line x1="{fmt(x1)}" y1="{fmt(y1)}" x2="{fmt(x2)}" y2="{fmt(y2)}" stroke="{col}" stroke-width="{w}"/>')

    svg.append(f'<g clip-path="url(#{clip_id})">')
    svg.append('<rect x="80" y="80" width="352" height="352" fill="#EFE5CF"/>')
    return svg


def base_b_frame_close(svg):
    svg.append('</g>')
    # Crisp inner border ring
    svg.append('<circle cx="256" cy="256" r="168" fill="none" stroke="#C8372D" stroke-width="4"/>')

    # Cardinal Folk/Survey Crown Marks (Top, Bottom, Left, Right)
    svg.append('<polygon points="256,52 242,82 270,82" fill="#C8372D" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<polygon points="256,460 242,430 270,430" fill="#C8372D" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<polygon points="52,256 82,242 82,270" fill="#C8372D" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<polygon points="460,256 430,242 430,270" fill="#C8372D" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('</svg>')


def draw_voxel_columns(svg, col_heights, start_x=80, col_w=22, base_y=432, cap_col="#C8372D"):
    """Draw clean Minecraft stepped block columns with grass/terrace top caps and stone brick grid."""
    # Outline polygon for the whole stepped mountain mass
    pts = [f"M {start_x} {base_y}"]
    for i, top_y in enumerate(col_heights):
        xL = start_x + i * col_w
        xR = xL + col_w
        pts.append(f"L {xL} {top_y} L {xR} {top_y}")
    pts.append(f"L {start_x + len(col_heights)*col_w} {base_y} Z")
    svg.append(f'<path d="{" ".join(pts)}" fill="#1E1B18" stroke="#EFE5CF" stroke-width="3" stroke-linejoin="miter"/>')

    # Draw individual block strata & top grass/terrace caps without messy overlaps
    for i, top_y in enumerate(col_heights):
        xL = start_x + i * col_w
        # Top grass/terrace block cap
        svg.append(f'<rect x="{xL+1.5}" y="{top_y+1.5}" width="{col_w-3}" height="8" fill="{cap_col}"/>')
        # Sub-surface voxel blocks
        for by in range(top_y + 22, base_y, 22):
            svg.append(f'<rect x="{xL+2}" y="{by}" width="{col_w-4}" height="18" fill="none" stroke="#EFE5CF" stroke-opacity="0.22" stroke-width="1.8"/>')


def draw_iso_cube(svg, cx, cy, size=24, top_col="#EFE5CF", left_col="#C8372D", right_col="#1E1B18", stroke_col="#1E1B18"):
    """Draw a single 2.5D isometric Minecraft block centered at top-ridge (cx, cy)."""
    dx = size * 0.866
    dy = size * 0.5
    # Top diamond
    top_pts = f"{fmt(cx)},{fmt(cy - dy)} {fmt(cx + dx)},{fmt(cy)} {fmt(cx)},{fmt(cy + dy)} {fmt(cx - dx)},{fmt(cy)}"
    # Left face
    left_pts = f"{fmt(cx - dx)},{fmt(cy)} {fmt(cx)},{fmt(cy + dy)} {fmt(cx)},{fmt(cy + dy + size)} {fmt(cx - dx)},{fmt(cy + size)}"
    # Right face
    right_pts = f"{fmt(cx)},{fmt(cy + dy)} {fmt(cx + dx)},{fmt(cy)} {fmt(cx + dx)},{fmt(cy + size)} {fmt(cx)},{fmt(cy + dy + size)}"
    svg.append(f'<polygon points="{left_pts}" fill="{left_col}" stroke="{stroke_col}" stroke-width="2" stroke-linejoin="round"/>')
    svg.append(f'<polygon points="{right_pts}" fill="{right_col}" stroke="{stroke_col}" stroke-width="2" stroke-linejoin="round"/>')
    svg.append(f'<polygon points="{top_pts}" fill="{top_col}" stroke="{stroke_col}" stroke-width="2" stroke-linejoin="round"/>')


# ==============================================================================
# VARIANT B1: 《山神吞口·年轮逆钟》 (Folk Nuo Mask, Tree-Ring Clock & Stepped Voxel Peaks)
# - Authentic woodcut Southwest Nuo Mask (双角山神傩面: upturned fierce eyes,
#   cloud-scroll brows, curved ritual fangs)
# - Forehead & Crown form a V-O-Y Sundial / Clock with Tree-Ring grooves behind
# - Lower half: Clean Minecraft Stepped Voxel Mountains with cinnabar grass caps
# ==============================================================================
def generate_b1():
    svg = base_b_frame_open("clipB1")

    # 1. Concentric Tree-Ring & Phonograph Grooves in Sky
    for r_ring in range(32, 185, 18):
        dash = "28,6,10,6" if (r_ring // 18) % 2 == 0 else "44,8"
        col = "#1E1B18" if (r_ring // 18) % 3 != 0 else "#C8372D"
        op = "0.20" if col == "#1E1B18" else "0.32"
        svg.append(f'<circle cx="256" cy="200" r="{r_ring}" fill="none" stroke="{col}" stroke-opacity="{op}" stroke-width="2.2" stroke-dasharray="{dash}"/>')

    # Clock Hands behind the Nuo Mask pointing to 10:10 ("V" of VOY)
    svg.append('<line x1="256" y1="200" x2="152" y2="118" stroke="#1E1B18" stroke-width="6" stroke-linecap="round"/>')
    svg.append('<line x1="256" y1="200" x2="360" y2="118" stroke="#C8372D" stroke-width="6" stroke-linecap="round"/>')

    # 2. Clean Minecraft Stepped Blocky Mountains at the bottom (16 columns * 22px = 352px)
    heights_b1 = [348, 326, 304, 282, 304, 326, 292, 270, 270, 292, 326, 304, 282, 304, 326, 348]
    draw_voxel_columns(svg, heights_b1, start_x=80, col_w=22, base_y=432, cap_col="#C8372D")

    # Highlighted voxel ore/shrine blocks inside the mountain base
    for bx, by in [(148, 340), (247, 316), (247, 360), (346, 340)]:
        svg.append(f'<rect x="{bx}" y="{by}" width="18" height="14" fill="#C8372D" stroke="#EFE5CF" stroke-width="1.5"/>')

    # 3. Authentic Woodcut Folk Nuo Mask (滇西南木刻山神傩面)
    # Curved Ox/Pheasant Ritual Horns forming a dramatic crescent "V" crown
    svg.append('''
      <path d="M 205 148
               C 165 138, 138 112, 134 86
               C 158 92, 185 110, 222 136 Z"
            fill="#C8372D" stroke="#1E1B18" stroke-width="4.5" stroke-linejoin="round"/>
      <path d="M 307 148
               C 347 138, 374 112, 378 86
               C 354 92, 327 110, 290 136 Z"
            fill="#C8372D" stroke="#1E1B18" stroke-width="4.5" stroke-linejoin="round"/>
    ''')

    # Nuo Mask Shield-shaped Carved Wood Faceplate
    svg.append('''
      <path d="M 176 142
               Q 256 126 336 142
               L 342 214
               Q 340 264 256 278
               Q 172 264 170 214 Z"
            fill="#1E1B18" stroke="#EFE5CF" stroke-width="5" stroke-linejoin="round"/>
    ''')

    # Cinnabar Forehead Flame / Sun-Wheel & Cloud-Scroll Brows (云雷纹眉)
    svg.append('''
      <path d="M 184 176 Q 218 154 246 178
               M 328 176 Q 294 154 266 178"
            fill="none" stroke="#C8372D" stroke-width="7" stroke-linecap="round"/>
    ''')

    # Fierce Upturned Folk Mask Eyes (Slanted Almond / Copper-Coin Eyes)
    svg.append('''
      <path d="M 186 196 Q 214 176 240 198 Q 214 214 186 196 Z"
            fill="#EFE5CF" stroke="#1E1B18" stroke-width="2.5"/>
      <path d="M 326 196 Q 298 176 272 198 Q 298 214 326 196 Z"
            fill="#EFE5CF" stroke="#1E1B18" stroke-width="2.5"/>
    ''')
    svg.append('<circle cx="214" cy="196" r="8" fill="#C8372D"/><circle cx="214" cy="196" r="3" fill="#1E1B18"/>')
    svg.append('<circle cx="298" cy="196" r="8" fill="#C8372D"/><circle cx="298" cy="196" r="3" fill="#1E1B18"/>')

    # Central Forehead "V-O-Y" Tuning Fork / Third Eye Emblem
    svg.append('<path d="M 236 122 L 256 154 L 276 122 M 256 154 L 256 222" fill="none" stroke="#EFE5CF" stroke-width="5" stroke-linecap="round"/>')
    svg.append('<circle cx="256" cy="156" r="12" fill="#C8372D" stroke="#EFE5CF" stroke-width="3.5"/>')

    # Nuo Mask Grinning Ritual Mouth & Curved Tusks (吞口獠牙)
    svg.append('<path d="M 198 236 Q 256 254 314 236 Q 256 268 198 236 Z" fill="#C8372D" stroke="#EFE5CF" stroke-width="3"/>')
    # Upper and lower carved fangs
    svg.append('<polygon points="206,242 216,216 226,244" fill="#EFE5CF" stroke="#1E1B18" stroke-width="2"/>')
    svg.append('<polygon points="306,242 296,216 286,244" fill="#EFE5CF" stroke="#1E1B18" stroke-width="2"/>')
    svg.append('<polygon points="234,242 242,260 250,244" fill="#EFE5CF"/>')
    svg.append('<polygon points="278,242 270,260 262,244" fill="#EFE5CF"/>')

    base_b_frame_close(svg)
    path = os.path.join(OUT_DIR, "concept_b1.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    return path


# ==============================================================================
# VARIANT B2: 《VOY 祭祀音叉·等轴方块山》 (VOY Tuning-Fork Sundial & Isometric Voxel Mountain)
# - Upper Half: Tree-Ring Sun Disc + 12 Bronze-Drum Sun Rays + 5 Musical Stave Strings
# - Centerpiece: Monumental "V · O · Y" Folk Ritual Tuning-Fork & Clock-Hand Staff
# - Lower Half: True 2.5D Isometric Minecraft Block Mountain Pyramid (3-tone woodcut cubes!)
# ==============================================================================
def generate_b2():
    svg = base_b_frame_open("clipB2")

    # 1. Tree-Ring Sun / Chrono Disc in the upper center
    svg.append('<circle cx="256" cy="196" r="88" fill="#C8372D"/>')
    for r_g, dash in [(76, "32,8,12,8"), (62, "20,6,40,6"), (48, "16,6")]:
        svg.append(f'<circle cx="256" cy="196" r="{r_g}" fill="none" stroke="#EFE5CF" stroke-opacity="0.6" stroke-width="2.5" stroke-dasharray="{dash}"/>')

    # 12 Bronze Drum Sun Rays (铜鼓十二芒 × 时钟十二刻度)
    for k in range(12):
        ang = math.radians(k * 30)
        x1, y1 = 256 + 90 * math.cos(ang), 196 + 90 * math.sin(ang)
        x2, y2 = 256 + 110 * math.cos(ang), 196 + 110 * math.sin(ang)
        svg.append(f'<line x1="{fmt(x1)}" y1="{fmt(y1)}" x2="{fmt(x2)}" y2="{fmt(y2)}" stroke="#1E1B18" stroke-width="4" stroke-linecap="round"/>')

    # 2. Five Musical Stave Strings (五线谱音律弦) with Diamond Chime Notes
    for idx in range(5):
        y_stave = 160 + idx * 15
        svg.append(f'<path d="M 88 {y_stave+14} Q 256 {y_stave-16} 424 {y_stave+14}" fill="none" stroke="#1E1B18" stroke-width="2.2" stroke-opacity="0.65"/>')
    for nx, ny in [(132, 178), (158, 162), (354, 168), (380, 186)]:
        svg.append(f'<rect x="{nx-6}" y="{ny-6}" width="12" height="12" transform="rotate(45 {nx} {ny})" fill="#C8372D" stroke="#EFE5CF" stroke-width="2"/>')

    # 3. 2.5D Isometric Minecraft Block Mountain in the Lower Half!
    # Painter's order: back rows to front rows, bottom layers to top peak
    S = 24
    dx = S * 0.866
    dy = S * 0.5
    # Build a stepped voxel mountain massif centered at x=256
    # Layer 4 (Base wide row)
    for k in range(-5, 6):
        draw_iso_cube(svg, 256 + k * dx, 368 + abs(k) * dy * 0.6, S)
    # Layer 3
    for k in range(-4, 5):
        draw_iso_cube(svg, 256 + k * dx, 340 + abs(k) * dy * 0.6, S)
    # Layer 2 (Twin side peaks + central ridge)
    for k in [-3, -2, -1, 0, 1, 2, 3]:
        top_c = "#EFE5CF" if abs(k) != 2 else "#C8372D"
        draw_iso_cube(svg, 256 + k * dx, 312 + abs(k) * dy * 0.5, S, top_col=top_c)
    # Layer 1 (Upper central spire)
    for k in [-1, 0, 1]:
        draw_iso_cube(svg, 256 + k * dx, 284 + abs(k) * dy, S)
    # Summit Altar Block
    draw_iso_cube(svg, 256, 258, S, top_col="#C8372D", left_col="#EFE5CF", right_col="#1E1B18")

    # 4. Monumental "V · O · Y" Ritual Tuning-Fork & Clock-Hand Totem rising from the summit
    svg.append('''
      <path d="M 196 114 L 256 196 L 316 114
               M 256 196 L 256 264"
            fill="none" stroke="#EFE5CF" stroke-width="18" stroke-linecap="square" stroke-linejoin="miter"/>
      <path d="M 196 114 L 256 196 L 316 114
               M 256 196 L 256 264"
            fill="none" stroke="#1E1B18" stroke-width="10" stroke-linecap="square" stroke-linejoin="miter"/>
    ''')
    # Clock hand arrowheads on the V arms
    svg.append('<polygon points="188,104 216,118 196,136" fill="#1E1B18" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<polygon points="324,104 296,118 316,136" fill="#1E1B18" stroke="#EFE5CF" stroke-width="2.5"/>')

    # Central "O" — Ritual Bronze Coin / Nuo Eye at (256, 196)
    svg.append('<circle cx="256" cy="196" r="26" fill="#EFE5CF" stroke="#1E1B18" stroke-width="5.5"/>')
    svg.append('<circle cx="256" cy="196" r="17" fill="#C8372D"/>')
    svg.append('<rect x="248" y="188" width="16" height="16" fill="#EFE5CF" stroke="#1E1B18" stroke-width="2.5"/>')

    base_b_frame_close(svg)
    path = os.path.join(OUT_DIR, "concept_b2.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    return path


# ==============================================================================
# VARIANT B3: 《双生傩晷·旧日变奏》 (Split Nuo Sun-Wheel, Musical Bars & Voxel Peaks)
# - Left Half of Totem Disc: Ancient Folk Nuo Mask / Bronze Drum Totem ("旧日 Yesterday")
# - Right Half of Totem Disc: Tree-Ring Clockwork & Acoustic Spectrum Bars ("变奏 Variations")
# - Divided by retro-angled Clock Hands forming V-O-Y, over Stepped Minecraft Mountains
# ==============================================================================
def generate_b3():
    svg = base_b_frame_open("clipB3")

    # 1. Background Concentric Tree-Ring / Soundwave Halo
    for r_bg in [146, 128, 110]:
        svg.append(f'<circle cx="256" cy="212" r="{r_bg}" fill="none" stroke="#1E1B18" stroke-opacity="0.20" stroke-width="2" stroke-dasharray="28,8,8,8"/>')

    # 2. Central Split Totem Medallion at (256, 206), radius 88
    # Left semicircle: Cinnabar & Charcoal Ancient Nuo Mask ("旧日")
    svg.append('<path d="M 256 118 A 88 88 0 0 0 256 294 Z" fill="#C8372D" stroke="#1E1B18" stroke-width="5"/>')
    # Right semicircle: Dark Charcoal Phonograph / Musical Clock Wheel ("变奏")
    svg.append('<path d="M 256 118 A 88 88 0 0 1 256 294 Z" fill="#1E1B18" stroke="#1E1B18" stroke-width="5"/>')

    # Left Half Details (Carved Folk Nuo Mask Half-Face: Horn, Cloud Brow, Fierce Eye, Fang)
    svg.append('<path d="M 218 128 L 182 94 L 204 136 Z" fill="#1E1B18" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<path d="M 186 174 Q 216 154 244 176" fill="none" stroke="#EFE5CF" stroke-width="5.5" stroke-linecap="round"/>')
    svg.append('<circle cx="212" cy="196" r="18" fill="#EFE5CF" stroke="#1E1B18" stroke-width="4"/>')
    svg.append('<rect x="205" y="189" width="14" height="14" fill="#1E1B18"/>')
    # Half grinning mouth + tusk on left
    svg.append('<path d="M 192 238 Q 224 256 256 256 L 256 236 Z" fill="#1E1B18" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<polygon points="202,240 212,218 222,244" fill="#EFE5CF" stroke="#1E1B18" stroke-width="2"/>')

    # Right Half Details (Tree-Ring Grooves + Musical Pitch Bars + Clock Roman/Folk Ticks)
    for r_rh in [72, 56, 40]:
        svg.append(f'<path d="M 256 {206-r_rh} A {r_rh} {r_rh} 0 0 1 256 {206+r_rh}" fill="none" stroke="#EFE5CF" stroke-opacity="0.45" stroke-width="2.5" stroke-dasharray="14,6"/>')
    # Horizontal musical equalizer / tuning fork bars on the right half
    for by, bw, bcol in [(156, 46, "#C8372D"), (174, 64, "#EFE5CF"), (192, 52, "#C8372D"), (210, 70, "#EFE5CF"), (228, 44, "#C8372D"), (246, 32, "#EFE5CF")]:
        svg.append(f'<rect x="260" y="{by}" width="{bw}" height="9" fill="{bcol}"/>')

    # Central Vertical Divider + V-O-Y Clock Hands
    svg.append('<line x1="256" y1="96" x2="256" y2="306" stroke="#EFE5CF" stroke-width="6"/>')
    svg.append('<line x1="256" y1="206" x2="322" y2="140" stroke="#C8372D" stroke-width="7" stroke-linecap="round"/>')
    svg.append('<line x1="256" y1="206" x2="190" y2="140" stroke="#1E1B18" stroke-width="7" stroke-linecap="round"/>')
    svg.append('<circle cx="256" cy="206" r="14" fill="#EFE5CF" stroke="#1E1B18" stroke-width="4"/>')
    svg.append('<circle cx="256" cy="206" r="5" fill="#C8372D"/>')

    # 3. Clean Minecraft Stepped Voxel Mountains at the Bottom
    heights_b3 = [356, 334, 308, 284, 308, 334, 312, 290, 290, 312, 334, 308, 284, 308, 334, 356]
    draw_voxel_columns(svg, heights_b3, start_x=80, col_w=22, base_y=432, cap_col="#C8372D")

    base_b_frame_close(svg)
    path = os.path.join(OUT_DIR, "concept_b3.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    return path


# ==============================================================================
# VARIANT B4: 《留声年轮·万山祭坛》 (Tree-Ring Phonograph, Clock Stylus & Isometric Shrine)
# - Upper Center: Giant Tree-Ring / Phonograph Record Disc with Folk Sun-Totem Hub
# - Sweeping Retrograde Clock Hands / Gramophone Stylus forming "V / Y" over "O"
# - Bottom Half: Isometric Minecraft Voxel Canyon framing a Cinnabar Ritual Shrine Gate
# ==============================================================================
def generate_b4():
    svg = base_b_frame_open("clipB4")

    # 1. Giant Tree-Ring / Phonograph Record Wheel at (256, 204)
    svg.append('<circle cx="256" cy="204" r="102" fill="#1E1B18"/>')
    svg.append('<circle cx="256" cy="204" r="94" fill="#C8372D"/>')
    for r_v, col, w, dash in [
        (82, "#1E1B18", 4, "48,6,16,6"),
        (70, "#EFE5CF", 2.5, "32,8,12,8"),
        (58, "#1E1B18", 3.5, "24,6,36,6"),
        (46, "#EFE5CF", 2.5, "18,6"),
        (34, "#1E1B18", 3, "28,6"),
    ]:
        svg.append(f'<circle cx="256" cy="204" r="{r_v}" fill="none" stroke="{col}" stroke-width="{w}" stroke-dasharray="{dash}"/>')

    # Center Folk Sun / Nuo Totem Eye ("O")
    svg.append('<circle cx="256" cy="204" r="22" fill="#EFE5CF" stroke="#1E1B18" stroke-width="4"/>')
    svg.append('<polygon points="256,186 274,204 256,222 238,204" fill="#1E1B18"/>')
    svg.append('<circle cx="256" cy="204" r="6" fill="#C8372D"/>')

    # 2. Retrograde Clock Hands / Gramophone Stylus Needles ("V" & "Y")
    svg.append('<line x1="358" y1="114" x2="256" y2="204" stroke="#EFE5CF" stroke-width="11" stroke-linecap="round"/>')
    svg.append('<line x1="358" y1="114" x2="256" y2="204" stroke="#1E1B18" stroke-width="5.5" stroke-linecap="round"/>')
    svg.append('<line x1="164" y1="136" x2="256" y2="204" stroke="#EFE5CF" stroke-width="9" stroke-linecap="round"/>')
    svg.append('<line x1="164" y1="136" x2="256" y2="204" stroke="#C8372D" stroke-width="4.5" stroke-linecap="round"/>')

    # 3. Isometric Minecraft Voxel Canyon Peaks on Left & Right
    S = 22
    dx = S * 0.866
    dy = S * 0.5
    # Left voxel cliff
    for row_y, cols in [(300, [-6, -5, -4]), (326, [-6, -5, -4, -3]), (352, [-6, -5, -4, -3, -2]), (378, [-6, -5, -4, -3, -2, -1])]:
        for k in cols:
            draw_iso_cube(svg, 256 + k * dx, row_y + abs(k + 4) * dy * 0.4, S)
    # Right voxel cliff
    for row_y, cols in [(300, [4, 5, 6]), (326, [3, 4, 5, 6]), (352, [2, 3, 4, 5, 6]), (378, [1, 2, 3, 4, 5, 6])]:
        for k in cols:
            draw_iso_cube(svg, 256 + k * dx, row_y + abs(k - 4) * dy * 0.4, S)
    # Canyon floor blocks
    for k in [-2, -1, 0, 1, 2]:
        draw_iso_cube(svg, 256 + k * dx, 396, S, top_col="#C8372D")

    # 4. Central Folk Ritual Torii / Mountain Shrine Gate (镇山祭祀牌坊/图腾柱)
    svg.append('''
      <path d="M 196 280 L 208 290 H 304 L 316 280
               M 210 308 H 302
               M 224 286 V 382
               M 288 286 V 382
               M 256 264 V 350"
            fill="none" stroke="#EFE5CF" stroke-width="9" stroke-linecap="square" stroke-linejoin="miter"/>
      <path d="M 196 280 L 208 290 H 304 L 316 280
               M 210 308 H 302
               M 224 286 V 382
               M 288 286 V 382"
            fill="none" stroke="#C8372D" stroke-width="4.5" stroke-linecap="square" stroke-linejoin="miter"/>
    ''')
    # Hanging ritual bell / talisman plaque in the center of the shrine gate
    svg.append('<rect x="244" y="290" width="24" height="32" fill="#C8372D" stroke="#EFE5CF" stroke-width="3"/>')
    svg.append('<circle cx="256" cy="306" r="5" fill="#EFE5CF"/>')

    base_b_frame_close(svg)
    path = os.path.join(OUT_DIR, "concept_b4.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    return path


def build_b_variants_board():
    W, H = 1360, 1420
    board = Image.new("RGBA", (W, H), (20, 18, 16, 255))
    draw = ImageDraw.Draw(board)

    font_title = None
    font_sub = None
    for fp in [
        "C:/Windows/Fonts/msyhbd.ttc",
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simhei.ttf",
    ]:
        if os.path.exists(fp):
            font_title = ImageFont.truetype(fp, 25)
            font_sub = ImageFont.truetype(fp, 16)
            break
    if font_title is None:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()

    items = [
        ("concept_b1.png", "方案 B1：《山神吞口·年轮逆钟》", "滇南木刻双角傩面 × 年轮时钟/VOY 眉心印 × 2D 阶梯方块山"),
        ("concept_b2.png", "方案 B2：《VOY 音叉日晷·等轴方块山》", "V·O·Y 祭祀音叉/日晷针 × 五线谱铜鼓日轮 × 2.5D 等轴方块山"),
        ("concept_b3.png", "方案 B3：《双生傩晷·旧日变奏》", "左半古傩面(旧日) + 右半音律年轮(变奏) × VOY 指针 × 阶梯方块山"),
        ("concept_b4.png", "方案 B4：《留声年轮·万山祭坛》", "年轮黑胶唱盘 × 逆时留声唱针 × 2.5D 等轴方块峡谷与镇山牌坊"),
    ]

    positions = [(40, 35), (700, 35), (40, 720), (700, 720)]
    for (fname, title, subtitle), (px, py) in zip(items, positions):
        draw.rounded_rectangle([px, py, px + 620, py + 650], radius=20, fill=(32, 29, 26, 255), outline=(88, 78, 66, 255), width=2)
        draw.text((px + 24, py + 18), title, fill=(238, 228, 206, 255), font=font_title)
        draw.text((px + 24, py + 54), subtitle, fill=(182, 168, 148, 255), font=font_sub)

        img_path = os.path.join(OUT_DIR, fname)
        if os.path.exists(img_path):
            icon = Image.open(img_path).convert("RGBA")
            icon_large = icon.resize((460, 460), Image.Resampling.LANCZOS)
            board.paste(icon_large, (px + 24, py + 96), icon_large)

            icon_64 = icon.resize((64, 64), Image.Resampling.LANCZOS)
            draw.text((px + 508, py + 120), "64×64", fill=(182, 168, 148, 255), font=font_sub)
            draw.rounded_rectangle([px + 506, py + 148, px + 574, py + 216], radius=6, fill=(18, 16, 15, 255), outline=(70, 62, 54, 255), width=1)
            board.paste(icon_64, (px + 508, py + 150), icon_64)

            icon_32 = icon.resize((32, 32), Image.Resampling.LANCZOS)
            draw.text((px + 508, py + 246), "32×32", fill=(182, 168, 148, 255), font=font_sub)
            draw.rounded_rectangle([px + 506, py + 274, px + 542, py + 310], radius=4, fill=(18, 16, 15, 255), outline=(70, 62, 54, 255), width=1)
            board.paste(icon_32, (px + 508, py + 276), icon_32)

    out_board = os.path.join(OUT_DIR, "concept_b_variants_board.png")
    board.save(out_board)
    print("Saved B variants board:", out_board)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "board":
        build_b_variants_board()
    else:
        b1 = generate_b1()
        b2 = generate_b2()
        b3 = generate_b3()
        b4 = generate_b4()
        print("Generated B variant SVGs:", b1, b2, b3, b4)
