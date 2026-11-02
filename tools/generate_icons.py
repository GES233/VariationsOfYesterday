import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = "."


def fmt(val):
    return f"{val:.2f}"


# ==============================================================================
# CONCEPT A: 《镇脉示波》 (The Oscilloscope Talisman)
# 1980s CRT Oscilloscope Bezel + Woodcut Layered Mountains + FHN Action Potential
# + Cinnabar Talisman Seal pinning the critical peak
# ==============================================================================
def generate_concept_a():
    svg = []
    svg.append('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">')
    svg.append('''
    <defs>
      <clipPath id="screenClipA">
        <rect x="44" y="44" width="424" height="424" rx="56" ry="56" />
      </clipPath>
      <linearGradient id="bgGradA" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#191614"/>
        <stop offset="65%" stop-color="#1F1A17"/>
        <stop offset="100%" stop-color="#120F0D"/>
      </linearGradient>
      <radialGradient id="crtGlowA" cx="50%" cy="48%" r="55%">
        <stop offset="0%" stop-color="#3A2818" stop-opacity="0.55"/>
        <stop offset="70%" stop-color="#1A1512" stop-opacity="0.1"/>
        <stop offset="100%" stop-color="#0D0B0A" stop-opacity="0.7"/>
      </radialGradient>
      <filter id="amberGlow" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="5" result="blur" />
        <feMerge>
          <feMergeNode in="blur" />
          <feMergeNode in="SourceGraphic" />
        </feMerge>
      </filter>
    </defs>
    ''')

    # Outer 1980s instrument casing / archive badge border
    svg.append('<rect x="12" y="12" width="488" height="488" rx="76" ry="76" fill="#2A2420" stroke="#EAE0C8" stroke-width="8"/>')
    svg.append('<rect x="26" y="26" width="460" height="460" rx="64" ry="64" fill="#161311" stroke="#6E6254" stroke-width="3"/>')

    # Corner instrument screws / rivets
    for cx, cy in [(48, 48), (464, 48), (48, 464), (464, 464)]:
        svg.append(f'<circle cx="{cx}" cy="{cy}" r="10" fill="#3A322C" stroke="#8C7D6B" stroke-width="2.5"/>')
        svg.append(f'<line x1="{cx-6}" y1="{cy-6}" x2="{cx+6}" y2="{cy+6}" stroke="#161311" stroke-width="2.5"/>')

    # Main CRT Screen / Badge interior
    svg.append('<g clip-path="url(#screenClipA)">')
    svg.append('<rect x="44" y="44" width="424" height="424" fill="url(#bgGradA)"/>')
    svg.append('<rect x="44" y="44" width="424" height="424" fill="url(#crtGlowA)"/>')

    # Oscilloscope Graticule Grid
    for pos in range(96, 460, 52):
        svg.append(f'<line x1="{pos}" y1="44" x2="{pos}" y2="468" stroke="#EAE0C8" stroke-opacity="0.10" stroke-width="1.5"/>')
        svg.append(f'<line x1="44" y1="{pos}" x2="468" y2="{pos}" stroke="#EAE0C8" stroke-opacity="0.10" stroke-width="1.5"/>')

    # Center crosshair axes with subtle ticks
    svg.append('<line x1="44" y1="280" x2="468" y2="280" stroke="#EAE0C8" stroke-opacity="0.24" stroke-width="2"/>')
    svg.append('<line x1="256" y1="44" x2="256" y2="468" stroke="#EAE0C8" stroke-opacity="0.20" stroke-width="2"/>')
    for x in range(60, 460, 16):
        svg.append(f'<line x1="{x}" y1="276" x2="{x}" y2="284" stroke="#EAE0C8" stroke-opacity="0.28" stroke-width="1.5"/>')

    # Critical Threshold Line (Upper Excitation Boundary)
    svg.append('<line x1="44" y1="148" x2="468" y2="148" stroke="#C8372D" stroke-width="3" stroke-dasharray="10,8" stroke-opacity="0.85"/>')

    # Woodcut Southwest Karst Mountains & Terraced Contour Lines at the bottom
    # Back ridge (parchment contour lines)
    svg.append('<path d="M 36 400 L 105 315 L 160 355 L 235 268 L 308 345 L 385 282 L 476 390 L 476 476 L 36 476 Z" fill="#26201B" stroke="#8C7B65" stroke-width="3"/>')
    # Topographic contour echoes inside mountains
    for offset, op in [(18, 0.55), (36, 0.45), (54, 0.35), (72, 0.25)]:
        svg.append(f'<path d="M 36 {400+offset} L 105 {315+offset*0.9} L 160 {355+offset*0.8} L 235 {268+offset*1.05} L 308 {345+offset*0.85} L 385 {282+offset*0.95} L 476 {390+offset}" fill="none" stroke="#EAE0C8" stroke-opacity="{op}" stroke-width="2.5"/>')

    # Foreground dark woodcut mountain silhouette
    svg.append('<path d="M 36 435 L 92 368 L 148 402 L 210 340 L 278 395 L 352 332 L 415 378 L 476 350 L 476 476 L 36 476 Z" fill="#120F0D" stroke="#EAE0C8" stroke-width="3.5" stroke-linejoin="round"/>')
    # Woodcut vertical hatch strokes on the foreground mountain
    hatch_x = [75, 88, 100, 185, 198, 210, 222, 330, 342, 354, 366, 440, 454]
    for hx in hatch_x:
        svg.append(f'<line x1="{hx}" y1="365" x2="{hx-12}" y2="455" stroke="#EAE0C8" stroke-opacity="0.22" stroke-width="2.5" stroke-linecap="round"/>')

    # Secondary Ghost Waveform (Delayed / Modulated Phase Response in Cinnabar Red)
    pts_ghost = []
    for i in range(44, 470, 3):
        t = (i - 44) / 424.0
        # Shifted damped wave
        y = 280 - 95 * math.exp(-((t - 0.56) / 0.11) ** 2) + 38 * math.exp(-((t - 0.72) / 0.09) ** 2)
        pts_ghost.append(f"{fmt(i)},{fmt(y)}")
    svg.append(f'<polyline points="{" ".join(pts_ghost)}" fill="none" stroke="#C8372D" stroke-width="5" stroke-opacity="0.75" stroke-dasharray="6,6"/>')

    # Primary FHN Action Potential Waveform (Glowing Amber-Gold)
    # Rest -> Sharp Excitation Spike at x=252 -> Refractory Recovery Trough -> Return to Rest
    pts_main = []
    for i in range(40, 472, 2):
        t = (i - 40) / 432.0
        # FHN-like fast spike peaking right at y=162 (just below the seal base) + slow recovery undershoot
        tremor = 8 * math.sin(t * 65) * math.exp(-((t - 0.28) / 0.08) ** 2)
        spike = -118 * math.exp(-((t - 0.49) / 0.062) ** 2)
        undershoot = 64 * math.exp(-((t - 0.66) / 0.11) ** 2)
        y = 280 + tremor + spike + undershoot
        pts_main.append(f"{fmt(i)},{fmt(y)}")

    svg.append(f'<polyline points="{" ".join(pts_main)}" fill="none" stroke="#F4B942" stroke-width="12" stroke-linecap="round" stroke-linejoin="round" filter="url(#amberGlow)" stroke-opacity="0.45"/>')
    svg.append(f'<polyline points="{" ".join(pts_main)}" fill="none" stroke="#FFD166" stroke-width="7.5" stroke-linecap="round" stroke-linejoin="round"/>')
    svg.append(f'<polyline points="{" ".join(pts_main)}" fill="none" stroke="#FFF6D6" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>')

    # Cinnabar Folk Talisman / Seal ("镇脉" Seal) stamping down on the spike apex
    seal_cx, seal_cy = 252, 104
    # Vertical suppression vector from seal to baseline
    svg.append(f'<line x1="{seal_cx}" y1="44" x2="{seal_cx}" y2="280" stroke="#C8372D" stroke-width="3" stroke-opacity="0.55"/>')

    # Octagonal / Square Talisman Seal Stamp in High-Contrast Cinnabar Red & Parchment
    svg.append(f'<g transform="translate({seal_cx}, {seal_cy}) rotate(-5)">')
    # Outer stamp shadow for depth against waveform
    svg.append('<rect x="-52" y="-50" width="104" height="100" rx="10" fill="#120F0D" opacity="0.88"/>')
    # Stamp body
    svg.append('<rect x="-48" y="-46" width="96" height="92" rx="8" fill="#C8372D" stroke="#EAE0C8" stroke-width="4"/>')
    svg.append('<rect x="-40" y="-38" width="80" height="76" rx="4" fill="none" stroke="#161311" stroke-width="2.5"/>')
    # Folk Talisman / Seal Glyph inside ("山" + "镇" geometric seal-script motif)
    svg.append('''
      <path d="M 0 -28 L 0 26
               M -24 -10 L 24 -10
               M -18 8 L 18 8
               M -26 24 L 26 24
               M -22 -10 L -22 -24
               M 22 -10 L 22 -24"
            stroke="#EAE0C8" stroke-width="6" stroke-linecap="square" fill="none"/>
    ''')
    svg.append('</g>')

    # Glowing node right at the clamped waveform peak (below the stamp's lower edge)
    svg.append(f'<circle cx="{seal_cx}" cy="162" r="9.5" fill="#FFF6D6" stroke="#C8372D" stroke-width="4.5"/>')

    # Inner screen frame bevel
    svg.append('<rect x="44" y="44" width="424" height="424" rx="56" ry="56" fill="none" stroke="#EAE0C8" stroke-width="5"/>')
    svg.append('</g>')

    svg.append('</svg>')
    path = os.path.join(OUT_DIR, "concept_a.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    return path


# ==============================================================================
# CONCEPT B: 《堪舆相图》 (Geomatic Phase-Portrait Emblem)
# 1980s Geological Survey Compass / Fengshui Bagua Ring + FHN Cubic Nullcline
# & Limit Cycle Trajectory + Woodcut Mountain Negative Space
# ==============================================================================
def generate_concept_b():
    svg = []
    svg.append('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">')
    svg.append('''
    <defs>
      <clipPath id="innerCircleB">
        <circle cx="256" cy="256" r="168"/>
      </clipPath>
    </defs>
    ''')

    # Aged 1980s Dossier Parchment Background (Rounded Square Tile so it works both full-bleed and in launchers)
    svg.append('<rect x="10" y="10" width="492" height="492" rx="68" ry="68" fill="#EFE5CF" stroke="#1E1B18" stroke-width="10"/>')
    svg.append('<rect x="24" y="24" width="464" height="464" rx="56" ry="56" fill="none" stroke="#C8372D" stroke-width="3.5"/>')

    # Corner registration marks (1980s surveying blueprint / archive style)
    for cx, cy, sx, sy in [(46, 46, 1, 1), (466, 46, -1, 1), (46, 466, 1, -1), (466, 466, -1, -1)]:
        svg.append(f'<path d="M {cx} {cy+24*sy} L {cx} {cy} L {cx+24*sx} {cy}" fill="none" stroke="#1E1B18" stroke-width="4.5" stroke-linecap="square"/>')
        svg.append(f'<circle cx="{cx+10*sx}" cy="{cy+10*sy}" r="3.5" fill="#C8372D"/>')

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

    # Compass Ring (Luopan / Theodolite Degree Dial)
    svg.append('<circle cx="256" cy="256" r="198" fill="#1E1B18"/>')
    svg.append('<circle cx="256" cy="256" r="170" fill="#EFE5CF"/>')

    # Degree ticks and 8 Trigram / Survey Sector Dividers in the dark ring
    for deg in range(0, 360, 5):
        rad = math.radians(deg)
        if deg % 45 == 0:
            r1, r2, w, col = 170, 198, 4.0, "#EFE5CF"
        elif deg % 15 == 0:
            r1, r2, w, col = 174, 194, 2.5, "#C8372D"
        else:
            r1, r2, w, col = 180, 190, 1.5, "#EFE5CF"
        x1, y1 = 256 + r1 * math.cos(rad), 256 + r1 * math.sin(rad)
        x2, y2 = 256 + r2 * math.cos(rad), 256 + r2 * math.sin(rad)
        svg.append(f'<line x1="{fmt(x1)}" y1="{fmt(y1)}" x2="{fmt(x2)}" y2="{fmt(y2)}" stroke="{col}" stroke-width="{w}"/>')

    # Inside the central circular viewport:
    svg.append('<g clip-path="url(#innerCircleB)">')
    # Upper Sky: Warm Parchment with subtle woodcut concentric sun/field arcs
    svg.append('<rect x="80" y="80" width="352" height="352" fill="#EFE5CF"/>')
    for r_arc in range(40, 220, 24):
        svg.append(f'<circle cx="256" cy="256" r="{r_arc}" fill="none" stroke="#1E1B18" stroke-opacity="0.12" stroke-width="1.5" stroke-dasharray="6,6"/>')

    # Lower Earth / Mountain Silhouette carved into the lower half of the phase plane
    svg.append('<path d="M 80 310 L 138 242 L 188 278 L 256 196 L 324 274 L 372 232 L 432 305 L 432 432 L 80 432 Z" fill="#1E1B18"/>')
    # White woodcut contour strata inside the dark mountain base
    for dy in [24, 48, 72, 96, 120]:
        svg.append(f'<path d="M 80 {310+dy} L 138 {242+dy*0.85} L 188 {278+dy*0.9} L 256 {196+dy*1.05} L 324 {274+dy*0.9} L 372 {232+dy*0.85} L 432 {305+dy}" fill="none" stroke="#EFE5CF" stroke-opacity="0.32" stroke-width="2.5"/>')

    # Phase Plane Axes
    svg.append('<line x1="88" y1="256" x2="424" y2="256" stroke="#1E1B18" stroke-width="2" stroke-opacity="0.35"/>')
    svg.append('<line x1="256" y1="88" x2="256" y2="424" stroke="#1E1B18" stroke-width="2" stroke-opacity="0.35"/>')

    # Linear Recovery Nullcline (w = (v + a)/b) - dashed engineering line
    svg.append('<line x1="135" y1="365" x2="355" y2="115" stroke="#C8372D" stroke-width="4" stroke-dasharray="10,7"/>')

    # FHN Limit Cycle Closed Orbit (Hysteresis Loop wrapping around the phase plane)
    # Drawn as a bold cinnabar red & parchment double-inked loop with directional arrows
    orbit_pts = []
    for k in range(120):
        theta = 2 * math.pi * k / 120.0
        # Relaxation oscillator orbit shape (fast horizontal jumps, slow along cubic branches)
        vx = 122 * math.cos(theta) + 22 * math.sin(2 * theta)
        wy = 92 * math.sin(theta) - 28 * math.cos(2 * theta)
        px = 256 + vx
        py = 256 - wy
        orbit_pts.append(f"{fmt(px)},{fmt(py)}")
    svg.append(f'<polygon points="{" ".join(orbit_pts)}" fill="none" stroke="#EFE5CF" stroke-width="14" stroke-linejoin="round"/>')
    svg.append(f'<polygon points="{" ".join(orbit_pts)}" fill="none" stroke="#C8372D" stroke-width="8" stroke-linejoin="round"/>')

    # FHN Cubic Nullcline: w = v - v^3 / 3 (The iconic S/N-shaped curve)
    cubic_pts = []
    for i in range(-100, 101, 2):
        v = i / 48.0  # [-2.08, 2.08]
        w = v - (v ** 3) / 3.0
        px = 256 + v * 74.0
        py = 256 - w * 95.0
        cubic_pts.append(f"{fmt(px)},{fmt(py)}")
    # Halo so it stands out over both dark mountain and light sky
    svg.append(f'<polyline points="{" ".join(cubic_pts)}" fill="none" stroke="#EFE5CF" stroke-width="15" stroke-linecap="round" stroke-linejoin="round"/>')
    svg.append(f'<polyline points="{" ".join(cubic_pts)}" fill="none" stroke="#1E1B18" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>')

    # Equilibrium / Excitation Threshold Target Node (Brass Coin / Survey Benchmark at intersection)
    eq_x, eq_y = 212, 278
    svg.append(f'<circle cx="{eq_x}" cy="{eq_y}" r="20" fill="#C8372D" stroke="#EFE5CF" stroke-width="4"/>')
    svg.append(f'<rect x="{eq_x-7}" y="{eq_y-7}" width="14" height="14" fill="#EFE5CF" stroke="#1E1B18" stroke-width="2.5"/>')

    svg.append('</g>')

    # Crisp inner border ring
    svg.append('<circle cx="256" cy="256" r="168" fill="none" stroke="#C8372D" stroke-width="4"/>')

    # Cardinal Folk/Survey Crown Marks (Top, Bottom, Left, Right)
    svg.append('<polygon points="256,52 242,82 270,82" fill="#C8372D" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<polygon points="256,460 242,430 270,430" fill="#C8372D" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<polygon points="52,256 82,242 82,270" fill="#C8372D" stroke="#EFE5CF" stroke-width="2.5"/>')
    svg.append('<polygon points="460,256 430,242 430,270" fill="#C8372D" stroke="#EFE5CF" stroke-width="2.5"/>')

    svg.append('</svg>')
    path = os.path.join(OUT_DIR, "concept_b.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    return path


# ==============================================================================
# CONCEPT C: 《界碑档案·红章》 (Boundary Stele & Stamped Dossier)
# High-Contrast Woodcut + Topographic Stress Contours + Geodesic Boundary Stele
# + Overprinted 1980s Red Archive Seal & Seismograph Wave
# ==============================================================================
def generate_concept_c():
    svg = []
    svg.append('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">')
    svg.append('''
    <defs>
      <clipPath id="cardClipC">
        <rect x="16" y="16" width="480" height="480" rx="64" ry="64"/>
      </clipPath>
    </defs>
    ''')

    svg.append('<g clip-path="url(#cardClipC)">')
    # Deep Charcoal Ink Base
    svg.append('<rect x="16" y="16" width="480" height="480" fill="#171513"/>')

    # Topographic Contour Vortex (Excitable Medium Spiral Wave / Mountain Contours)
    for k in range(1, 14):
        rx = k * 34
        ry = k * 28
        rot = k * 7 - 20
        col = "#EAE0C8" if k % 3 != 0 else "#C8372D"
        op = 0.22 if k % 3 != 0 else 0.45
        w = 2.5 if k % 2 == 0 else 1.8
        svg.append(f'<ellipse cx="256" cy="230" rx="{rx}" ry="{ry}" transform="rotate({rot} 256 230)" fill="none" stroke="{col}" stroke-opacity="{op}" stroke-width="{w}"/>')

    # Background Cinnabar Sun / Bureau Stamp Halo behind the Stele
    svg.append('<circle cx="256" cy="210" r="138" fill="#C8372D"/>')
    svg.append('<circle cx="256" cy="210" r="126" fill="none" stroke="#EAE0C8" stroke-width="3.5" stroke-dasharray="16,8"/>')
    # Inner star/compass geometry inside the red seal
    for deg in range(0, 360, 45):
        rad = math.radians(deg)
        x2, y2 = 256 + 118 * math.cos(rad), 210 + 118 * math.sin(rad)
        svg.append(f'<line x1="256" y1="210" x2="{fmt(x2)}" y2="{fmt(y2)}" stroke="#171513" stroke-width="3" stroke-opacity="0.35"/>')

    # Woodcut Terraced Hills at Base
    svg.append('<path d="M 16 390 Q 120 320 256 365 T 496 375 L 496 496 L 16 496 Z" fill="#171513" stroke="#EAE0C8" stroke-width="4"/>')
    svg.append('<path d="M 16 425 Q 140 365 256 405 T 496 415" fill="none" stroke="#C8372D" stroke-width="3.5"/>')
    svg.append('<path d="M 16 455 Q 150 405 256 440 T 496 450" fill="none" stroke="#EAE0C8" stroke-width="2.5" stroke-opacity="0.6"/>')

    # Central Boundary Stele / Ritual Monolith (镇山界碑 × 测绘水准标尺)
    # Horned folk altar crown + obelisk body
    svg.append('''
      <path d="M 204 420
               L 214 145
               L 192 122
               L 222 86
               L 256 108
               L 290 86
               L 320 122
               L 298 145
               L 308 420 Z"
            fill="#171513" stroke="#EAE0C8" stroke-width="6" stroke-linejoin="miter"/>
    ''')

    # 1980s Geodesic Leveling Staff Alternating Stadia Marks on the Stele Spine
    for idx, y_top in enumerate(range(155, 365, 26)):
        fill_col = "#EAE0C8" if idx % 2 == 0 else "#C8372D"
        svg.append(f'<rect x="244" y="{y_top}" width="24" height="26" fill="{fill_col}" stroke="#171513" stroke-width="2"/>')

    # Trig Station / Folk Eye Emblem near the top of the Stele
    svg.append('<polygon points="256,112 232,152 280,152" fill="#EAE0C8" stroke="#171513" stroke-width="3"/>')
    svg.append('<circle cx="256" cy="138" r="7" fill="#C8372D"/>')

    # Horizontal Seismograph / Phase-Modulation Wave cutting across the Stele
    wave_pts = []
    for x in range(16, 498, 3):
        t = (x - 256) / 240.0
        env = math.exp(-(t * 2.4) ** 2)
        y = 268 - 96 * env * math.cos(t * 14.0) + 28 * env * math.sin(t * 7.0)
        wave_pts.append(f"{fmt(x)},{fmt(y)}")
    svg.append(f'<polyline points="{" ".join(wave_pts)}" fill="none" stroke="#171513" stroke-width="16" stroke-linecap="round" stroke-linejoin="round"/>')
    svg.append(f'<polyline points="{" ".join(wave_pts)}" fill="none" stroke="#EAE0C8" stroke-width="7.5" stroke-linecap="round" stroke-linejoin="round"/>')
    svg.append(f'<polyline points="{" ".join(wave_pts)}" fill="none" stroke="#C8372D" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>')

    # Outer Dossier Stamp Frame
    svg.append('<rect x="16" y="16" width="480" height="480" rx="64" ry="64" fill="none" stroke="#EAE0C8" stroke-width="10"/>')
    svg.append('<rect x="32" y="32" width="448" height="448" rx="50" ry="50" fill="none" stroke="#C8372D" stroke-width="4"/>')
    svg.append('</g>')

    svg.append('</svg>')
    path = os.path.join(OUT_DIR, "concept_c.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    return path


# ==============================================================================
# CONCEPT D: 《山岳回响》 (Mountain Resonance Seal)
# Cinnabar Folk Seal Frame + Woodcut Three Peaks ("山" Seal Script Positive Space)
# + Green P1-Phosphor Oscilloscope Waveform Reflection ("旧日变奏" Dual-Frequency)
# ==============================================================================
def generate_concept_d():
    svg = []
    svg.append('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">')
    svg.append('''
    <defs>
      <filter id="greenGlowD" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="4.5" result="blur" />
        <feMerge>
          <feMergeNode in="blur" />
          <feMergeNode in="SourceGraphic" />
        </feMerge>
      </filter>
    </defs>
    ''')

    # Dark Pine-Soot & Phosphor Slate Base
    svg.append('<rect x="12" y="12" width="488" height="488" rx="64" ry="64" fill="#12191B" stroke="#C93B2B" stroke-width="12"/>')
    # Inner carved seal border with corner notches
    svg.append('''
      <path d="M 56 36 L 456 36 L 476 56 L 476 456 L 456 476 L 56 476 L 36 456 L 36 56 Z"
            fill="none" stroke="#EAE0C8" stroke-width="4"/>
    ''')

    # Subtle ruler calibration ticks along left and right borders
    for y in range(76, 446, 20):
        w_tick = 14 if (y - 76) % 60 == 0 else 8
        svg.append(f'<line x1="36" y1="{y}" x2="{36+w_tick}" y2="{y}" stroke="#EAE0C8" stroke-opacity="0.45" stroke-width="2"/>')
        svg.append(f'<line x1="476" y1="{y}" x2="{476-w_tick}" y2="{y}" stroke="#EAE0C8" stroke-opacity="0.45" stroke-width="2"/>')

    # Cinnabar Blood Moon / Critical Indicator Disc behind the central peak
    svg.append('<circle cx="256" cy="142" r="56" fill="#C93B2B" stroke="#EAE0C8" stroke-width="3.5"/>')
    # Concentric folk sun/moon ring
    svg.append('<circle cx="256" cy="142" r="72" fill="none" stroke="#C93B2B" stroke-width="2.5" stroke-dasharray="8,6"/>')

    # Upper Half: Bold Woodcut Three Peaks (forming both Southwest Karst Peaks & Seal-Script "山")
    svg.append('''
      <path d="M 64 268
               L 128 148
               L 184 224
               L 256 88
               L 328 224
               L 384 148
               L 448 268 Z"
            fill="#EAE0C8" stroke="#12191B" stroke-width="5" stroke-linejoin="round"/>
    ''')
    # Shadow facets on the right side of each peak (woodcut chiaroscuro)
    svg.append('<polygon points="256,88 328,224 256,268" fill="#C93B2B"/>')
    svg.append('<polygon points="128,148 184,224 128,268" fill="#263336"/>')
    svg.append('<polygon points="384,148 448,268 384,268" fill="#263336"/>')

    # Horizontal Raster Scanlines slicing across the mountains
    for y_scan in range(110, 268, 14):
        svg.append(f'<line x1="60" y1="{y_scan}" x2="452" y2="{y_scan}" stroke="#12191B" stroke-width="3.5" stroke-opacity="0.65"/>')

    # Horizon Datum Axis (Separating "Yesterday's Mountains" above from "Variations Waveform" below)
    svg.append('<line x1="48" y1="268" x2="464" y2="268" stroke="#EAE0C8" stroke-width="5"/>')

    # Lower Half: P1 Phosphor Green Oscilloscope Reflection (Fast-Slow Coupled Bursting + Slow Stress)
    # Slow stress variable envelope (Cinnabar Red curve)
    slow_pts = []
    for x in range(64, 452, 4):
        t = (x - 64) / 384.0
        y_slow = 290 + 115 * math.sin(t * math.pi) ** 1.6
        slow_pts.append(f"{fmt(x)},{fmt(y_slow)}")
    svg.append(f'<polyline points="{" ".join(slow_pts)}" fill="none" stroke="#C93B2B" stroke-width="5" stroke-dasharray="10,6"/>')

    # Fast variable oscillation (Inverted mirror of the 3 peaks transforming into FHN waveform)
    fast_pts = []
    for x in range(60, 454, 2):
        t = (x - 256) / 196.0  # [-1, 1]
        # Triple-peaked resonance packet echoing the three mountain peaks above
        env = math.exp(-(t * 1.65) ** 2)
        carrier = math.cos(t * 5.0 * math.pi)
        y_fast = 268 + 80 * env + 92 * env * carrier
        fast_pts.append(f"{fmt(x)},{fmt(y_fast)}")

    svg.append(f'<polyline points="{" ".join(fast_pts)}" fill="none" stroke="#48CA7E" stroke-width="11" stroke-linecap="round" stroke-linejoin="round" filter="url(#greenGlowD)" stroke-opacity="0.45"/>')
    svg.append(f'<polyline points="{" ".join(fast_pts)}" fill="none" stroke="#5CE696" stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round"/>')
    svg.append(f'<polyline points="{" ".join(fast_pts)}" fill="none" stroke="#E2FFEC" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>')

    # Corner Cinnabar Seal Marks
    svg.append('<rect x="56" y="56" width="24" height="24" fill="#C93B2B"/>')
    svg.append('<rect x="432" y="56" width="24" height="24" fill="#C93B2B"/>')
    svg.append('<rect x="56" y="432" width="24" height="24" fill="#C93B2B"/>')
    svg.append('<rect x="432" y="432" width="24" height="24" fill="#C93B2B"/>')

    svg.append('</svg>')
    path = os.path.join(OUT_DIR, "concept_d.svg")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(svg))
    return path


def build_comparison_board():
    # Create a 2x2 grid comparison board with 512px icon + 64px & 32px Mod Menu previews
    W, H = 1360, 1420
    board = Image.new("RGBA", (W, H), (20, 18, 16, 255))
    draw = ImageDraw.Draw(board)

    # Try loading a Chinese-capable system font (Microsoft YaHei / SimHei)
    font_title = None
    font_sub = None
    for fp in [
        "C:/Windows/Fonts/msyhbd.ttc",
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simhei.ttf",
    ]:
        if os.path.exists(fp):
            font_title = ImageFont.truetype(fp, 26)
            font_sub = ImageFont.truetype(fp, 17)
            break
    if font_title is None:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()

    items = [
        ("concept_a.png", "方案 A：《镇脉示波》", "80s 示波器视窗 × 琥珀金 FHN 脉冲 × 朱砂镇山符印"),
        ("concept_b.png", "方案 B：《堪舆相图》", "地质罗盘徽章 × FHN 三次零倾线/极限环 × 负形群山"),
        ("concept_c.png", "方案 C：《界碑档案·红章》", "木刻等高线应力场 × 测绘镇山界碑 × 科考局红章"),
        ("concept_d.png", "方案 D：《山岳回响》", "朱砂篆刻山纹印 × 绿色荧光示波倒影 × 快慢双频变奏"),
    ]

    positions = [(40, 35), (700, 35), (40, 720), (700, 720)]
    for (fname, title, subtitle), (px, py) in zip(items, positions):
        # Card background
        draw.rounded_rectangle([px, py, px + 620, py + 650], radius=20, fill=(32, 29, 26, 255), outline=(88, 78, 66, 255), width=2)
        draw.text((px + 24, py + 18), title, fill=(238, 228, 206, 255), font=font_title)
        draw.text((px + 24, py + 54), subtitle, fill=(182, 168, 148, 255), font=font_sub)

        img_path = os.path.join(OUT_DIR, fname)
        if os.path.exists(img_path):
            icon = Image.open(img_path).convert("RGBA")
            # Main 460x460 preview
            icon_large = icon.resize((460, 460), Image.Resampling.LANCZOS)
            board.paste(icon_large, (px + 24, py + 96), icon_large)

            # 64x64 preview (Mod Menu standard size)
            icon_64 = icon.resize((64, 64), Image.Resampling.LANCZOS)
            draw.text((px + 508, py + 120), "64×64", fill=(182, 168, 148, 255), font=font_sub)
            draw.rounded_rectangle([px + 506, py + 148, px + 574, py + 216], radius=6, fill=(18, 16, 15, 255), outline=(70, 62, 54, 255), width=1)
            board.paste(icon_64, (px + 508, py + 150), icon_64)

            # 32x32 preview (Small list icon size)
            icon_32 = icon.resize((32, 32), Image.Resampling.LANCZOS)
            draw.text((px + 508, py + 246), "32×32", fill=(182, 168, 148, 255), font=font_sub)
            draw.rounded_rectangle([px + 506, py + 274, px + 542, py + 310], radius=4, fill=(18, 16, 15, 255), outline=(70, 62, 54, 255), width=1)
            board.paste(icon_32, (px + 508, py + 276), icon_32)

    out_board = os.path.join(OUT_DIR, "concept_comparison.png")
    board.save(out_board)
    print("Saved comparison board:", out_board)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "board":
        build_comparison_board()
    else:
        a = generate_concept_a()
        b = generate_concept_b()
        c = generate_concept_c()
        d = generate_concept_d()
        print("Generated SVGs:", a, b, c, d)
