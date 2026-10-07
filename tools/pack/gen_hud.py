#!/usr/bin/env python3
"""Build the SÜLD in-game HUD: the font ``suld:hud`` (every element of the bottom panel and the target frame as a
glyph whose ascent puts it on its row), its textures, transparent replacements for the vanilla survival HUD sprites
the panel replaces, and the generated Java glyph table.

The panel is drawn into the action bar (and the target frame into a white boss bar) by HudService. A bitmap glyph's
top edge lands at ``H - 65 - ascent`` GUI pixels for action-bar text (H = screen height), so an element whose top is
Y px above the bottom of the screen uses ``ascent = Y - 65``. Advances are computed exactly as the client does
(trimmed width × scale, rounded, + 1), so the server can position every piece to the pixel.

Inputs:  assets/art/source/hud_diamond.png (Gemini, tools/art/hud_prompts.json); everything else is drawn here.
Outputs: resourcepack/assets/suld/font/hud.json
         resourcepack/assets/suld/textures/font/hud/*.png
         resourcepack/assets/minecraft/textures/gui/sprites/{hud,boss_bar}/... (transparent)
         suld-plugin/src/main/java/mn/suld/plugin/hud/HudGlyphs.java
Deterministic: same inputs, byte-identical outputs.

    python3 tools/pack/gen_hud.py [--preview DIR] [--map FILE]
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pixelfont as pf  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ART = os.path.join(ROOT, "assets", "art", "source")
RP = os.path.join(ROOT, "resourcepack", "assets")
TEX = os.path.join(RP, "suld", "textures", "font", "hud")
FONT = os.path.join(RP, "suld", "font", "hud.json")
SPRITES = os.path.join(RP, "minecraft", "textures", "gui", "sprites")
JAVA = os.path.join(ROOT, "suld-plugin", "src", "main", "java", "mn", "suld", "plugin", "hud", "HudGlyphs.java")

# ---------------------------------------------------------------------------------------------- palette
BRONZE_D = (74, 44, 18)
BRONZE = (168, 112, 52)
BRONZE_L = (232, 186, 104)
BRONZE_S = (112, 70, 30)
GOLD = (255, 214, 92)
GOLD_D = (176, 120, 30)
SILVER = (196, 204, 214)
SILVER_D = (98, 106, 120)
LEATHER = (30, 19, 12)
LEATHER_L = (46, 30, 19)
INK = (255, 255, 255)
INK_SHADOW = (26, 16, 8)
TURQ = (64, 214, 196)
TURQ_D = (20, 118, 112)

FILLS = {  # top highlight, middle, bottom (vertical gradient; columns identical so pieces tile seamlessly)
    "hp": ((255, 132, 132), (214, 38, 58), (138, 16, 30)),
    "hp_chip": ((255, 246, 226), (255, 224, 196), (220, 176, 150)),
    "absorb": ((255, 238, 140), (246, 196, 52), (176, 122, 18)),
    "res_baatar": ((255, 170, 110), (255, 100, 44), (170, 50, 14)),
    "res_mergen": ((190, 255, 170), (110, 220, 110), (40, 130, 50)),
    "res_boo": ((222, 180, 255), (170, 100, 255), (92, 40, 170)),
    "res_darkhan": ((255, 214, 140), (255, 152, 52), (176, 84, 14)),
    "res_khulegchin": ((176, 220, 255), (84, 170, 255), (30, 90, 190)),
    "food": ((255, 214, 140), (232, 160, 64), (150, 92, 26)),
    "air": ((210, 244, 255), (130, 210, 255), (50, 130, 210)),
    "mount": ((236, 196, 140), (190, 138, 82), (110, 72, 36)),
    "exp": ((170, 255, 236), (64, 214, 196), (16, 112, 104)),
    "tgt": ((255, 132, 132), (214, 38, 58), (138, 16, 30)),
    "tgt_chip": ((255, 246, 226), (255, 224, 196), (220, 176, 150)),
}

# ---------------------------------------------------------------------------------------------- layout (GUI px)
# Y = distance of an element's TOP edge from the bottom of the screen; ascent = Y - 65 (action bar).
# Ares-HUD-style layout: two rows of long bars either side of a central diamond, directly above the hotbar
# (the vanilla jump/locator bar strip just above the hotbar, Y 29..24, stays usable).
ROW_S_Y = 63        # spell slots / buff icons row (12 px)
ROW_A_Y = 48        # HP | resource bars (8 px, inner 6)
ROW_B_Y = 38        # food/mount/air | EXP bars (8 px, inner 6)
BAR_H = 8
DIAMOND_Y = 54      # level diamond (30 px), centred between the rows (centre Y = 39)
DIAMOND_W = 30
PLAQUE_Y = 31       # cartouche under the diamond (14 x 7); with the diamond it covers the vanilla level number
PLAQUE_W = 14
BAR_W = 86          # bar frame width; bars run from |x| = 4 (under the diamond) to |x| = 90
SIDE_X = 4
SLOT_W = 12
TARGET_W = 182      # target frame (boss bar); inner = 180 x 5
PIECES = [1, 2, 4, 8, 16, 32, 64]
TARGET_PIECES = PIECES + [128]


def asc(y_top: int) -> int:
    return y_top - 65


# boss-bar title: glyph top = T + 7 - ascent, T = title text top; the vanilla bar would be at T + 9 (5 px)
TGT_FRAME_ASC = -1      # frame top at T+8 (7 px tall: T+8..T+15)
TGT_INNER_ASC = -2      # fill/digits top at T+9

# ---------------------------------------------------------------------------------------------- font building
providers: list[dict] = []
glyphs: dict[str, tuple[str, int]] = {}    # NAME -> (char, advance)
charsets: dict[str, dict] = {}             # NAME -> {"symbols": str, "chars": str, "adv": [int]}
pieces: dict[str, list[tuple[str, int]]] = {}
_next = [0xE000]


def cp() -> str:
    c = chr(_next[0])
    _next[0] += 1
    return c


def save(img: Image.Image, rel: str) -> str:
    path = os.path.join(TEX, rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=False)
    return f"suld:font/hud/{rel}.png"


def trimmed_width(img: Image.Image, x0: int, w: int, h: int) -> int:
    px = img.load()
    for x in range(x0 + w - 1, x0 - 1, -1):
        for y in range(h):
            if px[x, y][3] != 0:
                return x - x0 + 1
    return 0


def advance_of(trim: int, scale: float) -> int:
    return int(0.5 + trim * scale) + 1


def single(name: str, img: Image.Image, height: int, ascent: int) -> None:
    """One glyph from one image; ``height`` is the displayed height (the texture may be 2x)."""
    file = save(img, name.lower())
    ch = cp()
    providers.append({"type": "bitmap", "file": file, "height": height, "ascent": ascent, "chars": [ch]})
    glyphs[name] = (ch, advance_of(trimmed_width(img, 0, img.width, img.height), height / img.height))


def strip(name: str, cells: list[Image.Image], cell_w: int, height: int, ascent: int) -> list[tuple[str, int]]:
    """Several glyphs side by side in one texture (one provider row); returns [(char, advance)]."""
    h = cells[0].height
    sheet = Image.new("RGBA", (cell_w * len(cells), h), (0, 0, 0, 0))
    for i, c in enumerate(cells):
        sheet.paste(c, (i * cell_w, 0))  # no mask: cells never overlap, and the space's alpha-1 width pixel must survive
    file = save(sheet, name.lower())
    chars = "".join(cp() for _ in cells)
    providers.append({"type": "bitmap", "file": file, "height": height, "ascent": ascent, "chars": [chars]})
    scale = height / h
    return [(chars[i], advance_of(trimmed_width(sheet, i * cell_w, cell_w, h), scale)) for i in range(len(cells))]


def space_glyphs() -> None:
    adv = {}
    for k in range(8):
        n = 1 << k
        for sign, tag in ((-1, "NEG"), (1, "POS")):
            ch = cp()
            adv[ch] = sign * n
            glyphs[f"{tag}_{n}"] = (ch, sign * n)
    providers.append({"type": "space", "advances": adv})


# ---------------------------------------------------------------------------------------------- drawing helpers
def grad(c3, rows: int) -> list[tuple]:
    """Vertical gradient rows: a 1-texel highlight, then top→mid→bottom."""
    top, mid, bot = c3
    out = []
    for r in range(rows):
        if r == 0:
            out.append(top)
            continue
        t = (r - 1) / max(1, rows - 2)
        if t < 0.45:
            u = t / 0.45
            out.append(tuple(round(top[i] * (1 - u) * 0.35 + mid[i] * (1 - (1 - u) * 0.35)) for i in range(3)))
        else:
            u = (t - 0.45) / 0.55
            out.append(tuple(round(mid[i] * (1 - u) + bot[i] * u) for i in range(3)))
    return out


def fill_piece(kind: str, w: int, h: int) -> Image.Image:
    rows = grad(FILLS[kind], h * 2)
    img = Image.new("RGBA", (w * 2, h * 2), (0, 0, 0, 0))
    px = img.load()
    for y in range(h * 2):
        for x in range(w * 2):
            px[x, y] = rows[y] + (255,)
    return img


def frame(w: int, h: int, metal=(BRONZE_D, BRONZE, BRONZE_L, BRONZE_S), chamfer_left=False, chamfer_right=False,
          inner=LEATHER, cap: str | None = None) -> Image.Image:
    """A 2x bar frame: dark outline, bevelled metal rim (light top, shade bottom), dark felt inside; ``cap`` = "L"/"R"
    puts a gold khee-hook end cap on that (outer) end."""
    W, H = w * 2, h * 2
    dark, mid, light, shade = metal
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            edge = min(x, y, W - 1 - x, H - 1 - y)
            if (chamfer_left and x + y < 3) or (chamfer_left and x + (H - 1 - y) < 3) or \
               (chamfer_right and (W - 1 - x) + y < 3) or (chamfer_right and (W - 1 - x) + (H - 1 - y) < 3):
                continue
            if edge == 0:
                px[x, y] = dark + (255,)
            elif edge == 1:
                px[x, y] = (light if y <= 1 or x <= 1 else shade if y >= H - 2 else mid) + (255,)
            else:
                n = ((x * 7 + y * 13) % 11) == 0  # felt speckle
                px[x, y] = (LEATHER_L if n else inner) + (255,)
    if cap:
        # khee hook: a gold spiral end cap 6 texels wide (3 px)
        hook = ["######",
                "#....#",
                "#.##.#",
                "#.#..#",
                "#.####",
                "#.....",
                "######"]
        for y in range(H):
            row = hook[min(len(hook) - 1, int(y * len(hook) / H))]
            for i in range(6):
                x = i if cap == "L" else W - 1 - i
                c = row[i]
                px[x, y] = ((GOLD if y < H // 2 else GOLD_D) if c == "#" else BRONZE_D) + (255,)
    return img


def khee_band(w: int) -> Image.Image:
    """A 2x Mongolian khee (meander) band, 3 px tall, repeating every 6 px."""
    W, H = w * 2, 6
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    motif = ["############",
             "#....#......",
             "#.##.#.####.",
             "#..#.#....#.",
             "####.######.",
             "............"]
    for y in range(H):
        for x in range(W):
            c = motif[y][x % 12]
            if c == "#":
                px[x, y] = (BRONZE_L if y == 0 else BRONZE) + (255,)
            elif y < 5:
                px[x, y] = BRONZE_D + (255,)
    return img


def diamond() -> Image.Image:
    src = Image.open(os.path.join(ART, "hud_diamond.png")).convert("RGB")
    s = DIAMOND_W * 2
    img = src.resize((s, s), Image.LANCZOS).convert("RGBA")
    px = img.load()
    c = (s - 1) / 2
    for y in range(s):
        for x in range(s):
            d = abs(x - c) + abs(y - c)          # rhombus distance
            r, g, b, _ = px[x, y]
            if d > s / 2 - 0.5:
                px[x, y] = (0, 0, 0, 0)
            elif d > s / 2 - 1.6:
                px[x, y] = BRONZE_D + (255,)
            else:
                px[x, y] = (r, g, b, 255)
    return img


def plaque() -> Image.Image:
    """A small bronze cartouche under the diamond (2x, 14 x 7) with a gold ulzii dot."""
    W, H = PLAQUE_W * 2, 14
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            cut = max(0, 4 - y)          # tapered top meeting the diamond's lower point
            if x < cut or x > W - 1 - cut:
                continue
            edge = min(x - cut, y, W - 1 - cut - x, H - 1 - y)
            px[x, y] = (BRONZE_D if edge == 0 else BRONZE_L if y <= 1 else BRONZE if edge == 1 else LEATHER) + (255,)
    for (x, y) in ((13, 6), (14, 6), (13, 7), (14, 7), (12, 7), (15, 7), (13, 8), (14, 8)):
        px[x, y] = GOLD + (255,)
    return img


def ink_glyph(rows: list[str], shadow=True, color=INK) -> Image.Image:
    w, h = len(rows[0]), len(rows)
    img = Image.new("RGBA", (w + (1 if shadow else 0), h + (1 if shadow else 0)), (0, 0, 0, 0))
    px = img.load()
    if shadow:
        for y, r in enumerate(rows):
            for x, c in enumerate(r):
                if c == "#":
                    px[x + 1, y + 1] = INK_SHADOW + (255,)
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c == "#":
                px[x, y] = color + (255,)
    return img


DIGITS3 = {
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"], "3": ["###", "..#", ".##", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "###"],
    "/": ["..#", "..#", ".#.", "#..", "#.."], "%": ["#.#", "..#", ".#.", "#..", "#.#"],
    ".": ["#"], "+": ["...", ".#.", "###", ".#.", "..."], "-": ["...", "...", "###", "...", "..."],
    "k": ["#..", "#.#", "##.", "#.#", "#.#"], "s": [".##", "#..", ".#.", "..#", "##."],
    "x": ["...", "#.#", ".#.", "#.#", "..."], "M": ["#.#", "###", "###", "#.#", "#.#"],
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "X": ["#.#", "#.#", ".#.", "#.#", "#.#"],
}
DIGITS3["."] = [".", ".", ".", ".", "#"]
DIGIT_SYMBOLS = "0123456789/%.+-ksxMAX"


def digit_set(name: str, ascent: int) -> None:
    syms = "".join(dict.fromkeys(DIGIT_SYMBOLS))
    cells = [ink_glyph(DIGITS3[s]) for s in syms]
    out = strip("digits_" + name.lower(), cells, 4, 6, ascent)
    charsets[name] = {"symbols": syms, "chars": "".join(c for c, _ in out), "adv": [a for _, a in out]}


def text_set(name: str, ascent: int, symbols: str) -> None:
    cells = []
    for s in symbols:
        g = ink_glyph(pf._G[s])
        if s == " ":
            g.putpixel((pf.SPACE - 1, g.height - 1), (0, 0, 0, 1))  # advance SPACE + 1 like the label renderer
        cells.append(g)
    out = strip("text_" + name.lower(), cells, 6, 8, ascent)
    charsets[name] = {"symbols": symbols, "chars": "".join(c for c, _ in out), "adv": [a for _, a in out]}


# ---------------------------------------------------------------------------------------------- icons (9x9, 1x)
ICONS = {
    "SPEED": ("#9FE8FF", [".........", "..####...", ".......#.", "#######..", ".......#.", "..#####..", ".........", "...###...", "........."]),
    "SLOW": ("#8C9BB0", [".........", "...###...", "..#...#..", ".#..#..#.", ".#.#.#.#.", ".#..#..#.", "..#...#..", "..#####..", "........."]),
    "STRENGTH": ("#FF6B5A", ["......##.", ".....###.", "....###..", "#..###...", ".####....", "..##.....", ".#.##....", "#...#....", "........."]),
    "WEAKNESS": ("#8C8C8C", ["......##.", ".....#.#.", "....#.#..", "#..#.#...", ".##.#....", "..#......", ".#.#.....", "#...#....", "........."]),
    "POISON": ("#6BD45A", ["....#....", "...###...", "..#####..", ".#######.", ".##.####.", ".###.###.", "..#####..", "...###...", "........."]),
    "REGEN": ("#FF8AC8", [".........", ".##...##.", "####.####", "#########", "####.####", ".##...##.", "..#...#..", "...#.#...", "....#...."]),
    "FIRE": ("#FF9A3C", ["....#....", "...##....", "...###.#.", "..#####..", ".###.###.", ".##...##.", ".##...##.", "..#####..", "........."]),
    "WITHER": ("#5A5060", [".........", "..#####..", ".#######.", ".#..#..#.", ".#######.", "..##.##..", "..#.#.#..", ".........", "........."]),
    "SHIELD": ("#FFD24A", [".........", ".#######.", ".#######.", ".###.###.", ".###.###.", "..#####..", "...###...", "....#....", "........."]),
    "EMPOWER": ("#FFE08A", ["......###", ".......##", "......#.#", ".....#...", "....#....", "#..#.....", ".##......", ".##......", "#..#....."]),
    "SOUL": ("#B8E4FF", ["...###...", "..#####..", ".##.#.##.", ".#######.", ".#######.", ".#######.", ".##.#.##.", ".#..#..#.", "........."]),
    "DANGER": ("#FF5A5A", ["#.......#", ".#.....#.", "..#...#..", "...#.#...", "....#....", "...#.#...", "..#...#..", ".#.....#.", "#.......#"]),
    "SAFE": ("#7CE07C", [".........", ".#######.", ".##...##.", ".#.#.#.#.", ".#..#..#.", "..#...#..", "...#.#...", "....#....", "........."]),
    "BROKEN": ("#FF6B6B", [".........", ".#######.", ".#..#..#.", ".#.#..##.", ".##..#.#.", ".#..#..#.", ".#######.", ".........", "........."]),
    # the death wound on the class gear (docs/DEATH_AND_RECOVERY.md): a cracked shield-heart
    "WOUND": ("#D0463C", [".##...##.", "####.####", "###.#####", "####.####", ".##.####.", "..##.##..", "...#.#...", "....#....", "........."]),
    "RELIC": ("#40D6C4", ["....#....", "...###...", "..#####..", ".#######.", "#########", ".#######.", "..#####..", "...###...", "....#...."]),
    "RESIST": ("#C4CCD6", [".........", ".#######.", ".#.....#.", ".#.###.#.", ".#.###.#.", "..#...#..", "...#.#...", "....#....", "........."]),
    "HUNGER": ("#C9A04A", [".........", "....###..", "...#####.", "...#####.", "..######.", ".###.##..", "#.#......", ".#.......", "........."]),
    "JUMP": ("#9CF07C", ["....#....", "...###...", "..#####..", ".#..#..#.", "....#....", "....#....", "...###...", "..#####..", "........."]),
    "NIGHT": ("#8C7CFF", [".........", "..#####..", ".#.....#.", "#..###..#", "#..###..#", ".#.....#.", "..#####..", ".........", "........."]),
    "HASTE": ("#FFD24A", [".........", ".#####...", "#.....#..", ".....#.#.", "....#...#", "...#.....", "..#......", ".#.......", "........."]),
}


def icon(name: str) -> Image.Image:
    color, rows = ICONS[name]
    rgb = tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = img.load()
    filled = {(x, y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == "#"}
    for (x, y) in filled:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 9 and 0 <= ny < 9 and (nx, ny) not in filled:
                px[nx, ny] = INK_SHADOW + (255,)
    for (x, y) in filled:
        px[x, y] = rgb + (255,)
    return img


def slot(state: str) -> Image.Image:
    """12x12 spell slot (2x): bronze rim; inner colour by state."""
    inner = {"READY": (18, 88, 84), "CD": (22, 16, 12), "LOCKED": (40, 40, 44), "NORES": (88, 22, 22), "ULT": (96, 70, 14)}[state]
    W = SLOT_W * 2
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    px = img.load()
    for y in range(W):
        for x in range(W):
            edge = min(x, y, W - 1 - x, W - 1 - y)
            if (x in (0, W - 1)) and (y in (0, W - 1)):
                continue
            if edge == 0:
                px[x, y] = BRONZE_D + (255,)
            elif edge == 1:
                px[x, y] = (BRONZE_L if y <= 1 or x <= 1 else BRONZE_S) + (255,)
            elif edge == 2 and state in ("READY", "ULT"):
                px[x, y] = (TURQ if state == "READY" else GOLD) + (255,)
            else:
                k = 1.0 - 0.25 * (y / W)
                px[x, y] = tuple(int(c * k) for c in inner) + (255,)
    return img


NUMERALS = {
    "I": ["#", "#", "#", "#", "#"],
    "II": ["#.#", "#.#", "#.#", "#.#", "#.#"],
    "III": ["#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#"],
    "IV": ["#.#.#", "#.#.#", "#.#.#", "#..#.", "#..#."],
    "ULT": ["..#..", ".###.", "#####", ".###.", "..#.."],
    "LOCK": [".###.", "#...#", "#####", "##.##", "#####"],
}


BAR_ICONS = {  # 5x5 icons drawn inside the bars before the numbers (Ares-style "♥ 20/20")
    "HEART": ("#FF8A8A", [".#.#.", "#####", "#####", ".###.", "..#.."]),
    "RES": ("#FFE08A", ["..##.", ".##..", "#####", "..##.", ".##.."]),
    "FOOD": ("#FFC070", ["..###", ".####", ".###.", "##...", "#...."]),
    "HORSE": ("#E0B47A", ["...##", "#####", "####.", "#..#.", "#..#."]),
    "AIR": ("#C8F0FF", [".###.", "#...#", "#.#.#", "#...#", ".###."]),
    "EXP": ("#A0FFE8", ["..#..", ".###.", "#####", ".###.", "..#.."]),
}


def bar_icon(name: str) -> Image.Image:
    color, rows = BAR_ICONS[name]
    rgb = tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))
    return ink_glyph(rows, shadow=True, color=rgb)


def hotbar() -> Image.Image:
    """The hotbar (2x, 182 x 22): nine dark felt slots in bronze frames, a khee band along the top."""
    W, H = 364, 44
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            edge = min(x, y, W - 1 - x, H - 1 - y)
            if edge < 2:
                px[x, y] = BRONZE_D + (255,)
            elif edge < 4:
                px[x, y] = (BRONZE_L if y < 4 else BRONZE_S if y >= H - 4 else BRONZE) + (255,)
            else:
                sx = (x - 2) % 40
                if sx < 2 or sx >= 38:
                    px[x, y] = BRONZE_S + (255,)          # slot dividers
                else:
                    n = ((x * 7 + y * 13) % 11) == 0
                    px[x, y] = (LEATHER_L if n else LEATHER) + (225,)
    band = khee_band(W // 2 - 8)
    for y in range(band.height):
        for x in range(band.width):
            p = band.getpixel((x, y))
            if p[3] and y < 3:
                px[8 + x, y] = p
    return img


def hotbar_selection() -> Image.Image:
    """Selected slot (2x, 24 x 23): gold rim with a turquoise inner line."""
    W, H = 48, 46
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            edge = min(x, y, W - 1 - x, H - 1 - y)
            if edge < 2:
                px[x, y] = GOLD_D + (255,)
            elif edge < 4:
                px[x, y] = GOLD + (255,)
            elif edge < 5:
                px[x, y] = TURQ + (255,)
    for (x, y) in ((23, 0), (24, 0), (23, 1), (24, 1)):
        px[x, y] = TURQ + (255,)
    return img


def offhand_slot() -> Image.Image:
    """Off-hand slot (2x, 29 x 24)."""
    W, H = 58, 48
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(2, H):
        for x in range(W):
            if x >= 44:
                continue
            edge = min(x, y - 2, 43 - x, H - 1 - y)
            if edge < 2:
                px[x, y] = BRONZE_D + (255,)
            elif edge < 4:
                px[x, y] = BRONZE + (255,)
            else:
                px[x, y] = LEATHER + (225,)
    return img


# ---------------------------------------------------------------------------------------------- vanilla sprites
BLANK_SPRITES = {
    "hud/heart": (9, 9, ["container", "container_blinking", "container_hardcore", "container_hardcore_blinking",
                         "full", "full_blinking", "half", "half_blinking", "hardcore_full", "hardcore_full_blinking",
                         "hardcore_half", "hardcore_half_blinking", "absorbing_full", "absorbing_full_blinking",
                         "absorbing_half", "absorbing_half_blinking", "absorbing_hardcore_full",
                         "absorbing_hardcore_full_blinking", "absorbing_hardcore_half", "absorbing_hardcore_half_blinking",
                         "poisoned_full", "poisoned_full_blinking", "poisoned_half", "poisoned_half_blinking",
                         "poisoned_hardcore_full", "poisoned_hardcore_full_blinking", "poisoned_hardcore_half",
                         "poisoned_hardcore_half_blinking", "withered_full", "withered_full_blinking", "withered_half",
                         "withered_half_blinking", "withered_hardcore_full", "withered_hardcore_full_blinking",
                         "withered_hardcore_half", "withered_hardcore_half_blinking", "frozen_full", "frozen_full_blinking",
                         "frozen_half", "frozen_half_blinking", "frozen_hardcore_full", "frozen_hardcore_full_blinking",
                         "frozen_hardcore_half", "frozen_hardcore_half_blinking", "vehicle_container", "vehicle_full",
                         "vehicle_half"]),
    "hud": (9, 9, ["food_empty", "food_empty_hunger", "food_full", "food_full_hunger", "food_half", "food_half_hunger",
                   "armor_empty", "armor_full", "armor_half", "air", "air_bursting", "air_empty"]),
}


def blank_sprites() -> int:
    n = 0
    for folder, (w, h, names) in BLANK_SPRITES.items():
        for nm in names:
            path = os.path.join(SPRITES, folder, nm + ".png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            Image.new("RGBA", (w, h), (0, 0, 0, 0)).save(path, optimize=False)
            n += 1
    for nm in ("experience_bar_background", "experience_bar_progress"):
        path = os.path.join(SPRITES, "hud", nm + ".png")
        Image.new("RGBA", (182, 5), (0, 0, 0, 0)).save(path, optimize=False)
        n += 1
    for nm, im in (("hotbar", hotbar()), ("hotbar_selection", hotbar_selection())):
        path = os.path.join(SPRITES, "hud", nm + ".png")
        im.save(path, optimize=False)
        n += 1
    left = offhand_slot()
    left.save(os.path.join(SPRITES, "hud", "hotbar_offhand_left.png"), optimize=False)
    left.transpose(Image.FLIP_LEFT_RIGHT).save(os.path.join(SPRITES, "hud", "hotbar_offhand_right.png"), optimize=False)
    n += 2
    for nm in ("white_background", "white_progress"):  # the target frame's boss bar draws its own bar
        path = os.path.join(SPRITES, "boss_bar", nm + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        Image.new("RGBA", (182, 5), (0, 0, 0, 0)).save(path, optimize=False)
        n += 1
    return n


# ---------------------------------------------------------------------------------------------- build
def build() -> None:
    space_glyphs()
    for row, y in (("A", ROW_A_Y), ("B", ROW_B_Y)):
        for side in ("L", "R"):
            single(f"FRAME_{row}_{side}", frame(BAR_W, BAR_H, cap=side), BAR_H, asc(y))
    for side in ("L", "R"):
        single(f"FRAME_A_{side}_LOW", frame(BAR_W, BAR_H, metal=((120, 10, 10), (200, 40, 40), (255, 120, 110), (140, 20, 20)),
                                            cap=side), BAR_H, asc(ROW_A_Y))
    single("DIAMOND", diamond(), DIAMOND_W, asc(DIAMOND_Y))
    single("PLAQUE", plaque(), 7, asc(PLAQUE_Y))
    single("TGT_FRAME", frame(TARGET_W, 7, chamfer_left=True, chamfer_right=True), 7, TGT_FRAME_ASC)
    single("TGT_FRAME_ELITE", frame(TARGET_W, 7, metal=(SILVER_D, (150, 158, 170), SILVER, (110, 118, 130)), chamfer_left=True,
                                    chamfer_right=True), 7, TGT_FRAME_ASC)
    single("TGT_FRAME_BOSS", frame(TARGET_W, 7, metal=(GOLD_D, (226, 170, 60), GOLD, (150, 100, 20)), chamfer_left=True,
                                   chamfer_right=True), 7, TGT_FRAME_ASC)
    # bar fills: power-of-two pieces per kind (columns identical, so pieces butt together seamlessly)
    row_a = {"hp", "hp_chip", "absorb"}
    for kind in FILLS:
        if kind.startswith("tgt"):
            cells = [fill_piece(kind, w, 5) for w in TARGET_PIECES]
            pieces[kind] = strip(f"fill_{kind}", cells, 256, 5, TGT_INNER_ASC)
        else:
            y = ROW_A_Y - 1 if kind in row_a or kind.startswith("res_") else ROW_B_Y - 1
            cells = [fill_piece(kind, w, BAR_H - 2) for w in PIECES]
            pieces[kind] = strip(f"fill_{kind}", cells, 128, BAR_H - 2, asc(y))
    for nm in BAR_ICONS:
        for row, y in (("A", ROW_A_Y), ("B", ROW_B_Y)):
            single(f"BI_{row}_{nm}", bar_icon(nm), 6, asc(y - 1))
    for st in ("READY", "CD", "LOCKED", "NORES", "ULT"):
        single(f"SLOT_{st}", slot(st), SLOT_W, asc(ROW_S_Y))
    for nm, rows in NUMERALS.items():
        single(f"NUM_{nm}", ink_glyph(rows), 6, asc(ROW_S_Y - 3))
    for nm in ICONS:
        single(f"ICON_{nm}", icon(nm), 9, asc(ROW_S_Y - 1))
    # digit sets (3x5 + shadow) and text sets (5x7 + shadow)
    digit_set("ROW_A", asc(ROW_A_Y - 1))
    digit_set("ROW_B", asc(ROW_B_Y - 1))
    digit_set("SLOT", asc(ROW_S_Y - 4))
    digit_set("BUFF", asc(ROW_S_Y - 3))
    digit_set("TARGET", TGT_INNER_ASC)
    text_symbols = " " + "".join(k for k in pf._G.keys())
    pf._G[" "] = ["..."] * 7
    text_set("LINE", 7, text_symbols)
    del pf._G[" "]
    text_set("LEVEL", asc(43), "0123456789")     # 7 px digits centred on the diamond (Y 43..36)


def write_font() -> None:
    os.makedirs(os.path.dirname(FONT), exist_ok=True)
    with open(FONT, "w", encoding="utf-8") as fh:
        json.dump({"providers": providers}, fh, ensure_ascii=True, indent=1)
        fh.write("\n")


def jstr(s: str) -> str:
    """A Java string literal. Quote and backslash get backslash escapes: as \\u escapes javac would read them as
    real quote/backslash characters and end the literal."""
    out = []
    for c in s:
        if c == '"':
            out.append('\\"')
        elif c == "\\":
            out.append("\\\\")
        elif 32 <= ord(c) < 127:
            out.append(c)
        else:
            out.append("\\u%04X" % ord(c))
    return '"' + "".join(out) + '"'


def write_java() -> None:
    L = []
    L.append("package mn.suld.plugin.hud;\n")
    L.append("import net.kyori.adventure.key.Key;\n")
    L.append("/**")
    L.append(" * GENERATED by tools/pack/gen_hud.py — do not edit. Glyphs of the resource-pack font {@code suld:hud}: the HUD panel,")
    L.append(" * the target frame, bar fill pieces, digit and text sets. Advances are exact (computed like the client does).")
    L.append(" */")
    L.append("public final class HudGlyphs {\n")
    L.append("    public static final Key FONT = Key.key(\"suld\", \"hud\");\n")
    L.append("    /** A glyph and its advance in GUI pixels. */")
    L.append("    public record G(String ch, int advance) {\n    }\n")
    L.append("    /** Symbols drawable in one set, the glyph for each and its advance. */")
    L.append("    public record CharSet(String symbols, String chars, int[] advances) {")
    L.append("        public int indexOf(char symbol) {\n            return symbols.indexOf(symbol);\n        }\n    }\n")
    L.append("    private HudGlyphs() {\n    }\n")
    L.append("    // layout (GUI px, x relative to the screen centre)")
    for k, v in (("BAR_W", BAR_W), ("BAR_H", BAR_H), ("SIDE_X", SIDE_X), ("DIAMOND_W", DIAMOND_W), ("PLAQUE_W", PLAQUE_W),
                 ("SLOT_W", SLOT_W), ("TARGET_W", TARGET_W)):
        L.append(f"    public static final int {k} = {v};")
    L.append("")
    for name, (ch, adv) in glyphs.items():
        L.append(f"    public static final G {name} = new G({jstr(ch)}, {adv});")
    L.append("")
    for kind, lst in pieces.items():
        widths = TARGET_PIECES if kind.startswith("tgt") else PIECES
        L.append(f"    /** fill pieces of widths {widths} */")
        L.append(f"    public static final G[] FILL_{kind.upper()} = {{" + ", ".join(f"new G({jstr(c)}, {a})" for c, a in lst) + "};")
    L.append("")
    for name, cs in charsets.items():
        adv = ", ".join(str(a) for a in cs["adv"])
        L.append(f"    public static final CharSet {name} = new CharSet({jstr(cs['symbols'])}, {jstr(cs['chars'])}, new int[]{{{adv}}});")
    L.append("}")
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write("\n".join(L) + "\n")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="write a 4x mock-up of the panel and the target frame here")
    ap.add_argument("--map", help="also write the glyph table as JSON (for test bots)")
    args = ap.parse_args()
    if os.path.isdir(TEX):
        for f in sorted(os.listdir(TEX)):
            os.remove(os.path.join(TEX, f))
    build()
    write_font()
    n = blank_sprites()
    write_java()
    if args.map:
        with open(args.map, "w", encoding="utf-8") as fh:
            json.dump({"glyphs": glyphs, "pieces": pieces, "charsets": charsets}, fh, ensure_ascii=False, indent=1)
    if args.preview:
        preview(args.preview)
    print(f"{len(providers)} providers, {len(glyphs)} glyphs, {sum(len(p) for p in pieces.values())} fill pieces, "
          f"{len(charsets)} char sets, {n} vanilla sprites blanked")


def preview(out: str) -> None:
    """Static mock-up of the frames over a grey background (4x); the live composition is rendered by hud_render.py."""
    os.makedirs(out, exist_ok=True)
    W, H = 220, 70
    img = Image.new("RGBA", (W * 2, H * 2), (96, 104, 92, 255))
    def put(rel, x, ytop, scale_h, folder=TEX):
        g = Image.open(os.path.join(folder, rel + ".png"))
        s = scale_h * 2 / g.height
        g = g.resize((round(g.width * s), round(g.height * s)), Image.NEAREST)
        img.alpha_composite(g, (round((W / 2 + x) * 2), round((H - ytop) * 2)))
    put("hotbar", -91, 22, 22, os.path.join(SPRITES, "hud"))
    put("frame_a_l", -SIDE_X - BAR_W, ROW_A_Y, BAR_H)
    put("frame_a_r", SIDE_X, ROW_A_Y, BAR_H)
    put("frame_b_l", -SIDE_X - BAR_W, ROW_B_Y, BAR_H)
    put("frame_b_r", SIDE_X, ROW_B_Y, BAR_H)
    put("plaque", -PLAQUE_W / 2, PLAQUE_Y, 7)
    put("diamond", -DIAMOND_W / 2, DIAMOND_Y, DIAMOND_W)
    img = img.resize((W * 4, H * 4), Image.NEAREST)
    img.save(os.path.join(out, "hud_panel_static.png"))


if __name__ == "__main__":
    main()
