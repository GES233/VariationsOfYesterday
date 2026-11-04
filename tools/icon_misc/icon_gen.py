# Generate the mod icon: a worn cinnabar seal, white-carved character "昨"
# (as in 旧日 / yesterday), on dark archival background.
# Usage: python tools/icon_gen.py
import random
from PIL import Image, ImageDraw

SIZE = 128
random.seed(42)

BG = (20, 15, 11)          # near-black warm charcoal
RED = (170, 55, 38)        # cinnabar / vermillion
RED_D = (140, 42, 30)      # darker red for mottling
PALE = (233, 223, 200)     # aged paper (carved strokes)

img = Image.new("RGB", (SIZE, SIZE), BG)
d = ImageDraw.Draw(img)

# --- background: clean dark field -------------------------------------------

# --- seal body -------------------------------------------------------------
S0, S1 = 18, 110           # seal square extents

def jrect(x0, y0, x1, y1, fill, j=1):
    """filled rect with slightly jittered edges"""
    d.rectangle([x0, y0, x1, y1], fill=fill)
    if j:
        for _ in range(j * 6):
            x = random.randint(x0 - 1, x1)
            y = random.randint(y0 - 1, y1)
            d.point((x, y), fill=fill)

# main block
d.rectangle([S0, S0, S1, S1], fill=RED)
# rough edges: a few irregular bites, not perforations
for _ in range(18):
    side = random.randrange(4)
    t = random.randint(S0 + 2, S1 - 4)
    n = random.randint(1, 2)
    ln = random.randint(2, 5)
    if side == 0:   d.rectangle([t, S0, t + ln, S0 + n], fill=BG)
    elif side == 1: d.rectangle([t, S1 - n, t + ln, S1], fill=BG)
    elif side == 2: d.rectangle([S0, t, S0 + n, t + ln], fill=BG)
    else:           d.rectangle([S1 - n, t, S1, t + ln], fill=BG)
# corner knock-offs
for cx, cy in [(S0, S0), (S1, S0), (S0, S1), (S1, S1)]:
    k = random.randint(2, 4)
    d.polygon([(cx, cy),
               (cx + (k if cx == S0 else -k), cy),
               (cx, cy + (k if cy == S0 else -k))], fill=BG)

# red mottling inside seal
for _ in range(500):
    x = random.randint(S0 + 1, S1 - 1)
    y = random.randint(S0 + 1, S1 - 1)
    if img.getpixel((x, y)) == RED and random.random() < 0.5:
        img.putpixel((x, y), RED_D)

# --- border frame (white-carved) ------------------------------------------
B0, B1 = 26, 102
W = 4
jrect(B0, B0, B1, B0 + W, PALE)
jrect(B0, B1 - W, B1, B1, PALE)
jrect(B0, B0, B0 + W, B1, PALE)
jrect(B1 - W, B0, B1, B1, PALE)

# --- character 昨, white-carved (白文) --------------------------------------
SW = 8                     # stroke width
# left radical 日
jrect(36, 36, 36 + SW, 92, PALE)          # left vertical
jrect(52, 36, 52 + SW, 92, PALE)          # right vertical
jrect(36, 36, 60, 36 + SW, PALE)          # top bar
jrect(36, 60, 60, 60 + SW, PALE)          # middle bar
jrect(36, 84, 60, 92, PALE)               # bottom bar
# right side 乍
# 撇: thick diagonal from upper-left into the first horizontal
for i in range(SW):
    d.line([(66 + i, 36), (76 + i, 48)], fill=PALE)
jrect(74, 46, 98, 46 + SW, PALE)          # 横1
jrect(74, 46, 74 + SW, 92, PALE)          # 竖
jrect(74, 66, 98, 66 + SW, PALE)          # 横2
jrect(74, 84, 98, 92, PALE)               # 横3

# --- wear: speckles where the carved strokes are eaten back -----------------
for _ in range(260):
    x = random.randint(B0, B1)
    y = random.randint(B0, B1)
    if img.getpixel((x, y)) == PALE and random.random() < 0.35:
        img.putpixel((x, y), RED)
# pale dust on red (ink lifted)
for _ in range(120):
    x = random.randint(S0, S1)
    y = random.randint(S0, S1)
    if img.getpixel((x, y)) in (RED, RED_D) and random.random() < 0.25:
        img.putpixel((x, y), PALE)

# cleanup: nothing may live outside the seal square
for y in range(SIZE):
    for x in range(SIZE):
        if x < S0 or x > S1 or y < S0 or y > S1:
            img.putpixel((x, y), BG)

out = "src/main/resources/assets/variations-of-yesterday/icon.png"
img.save(out)
print("wrote", out)
