#!/usr/bin/env python3
"""Build the SÜLD UI assets of the resource pack and the matching Java glyph table.

Inputs:  assets/art/source/*.png (Gemini "Nano Banana" art, tools/art/gemini_image.py)
Outputs: resourcepack/assets/suld/font/ui.json            one font: spaces, logo, icons, badges, GUI backgrounds
         resourcepack/assets/suld/textures/font/**.png
         resourcepack/assets/suld/{items,models,textures}/.../blank   an invisible item model for GUI buttons
         suld-plugin/src/main/java/mn/suld/plugin/ui/Glyphs.java      generated: code points of every glyph

GUI backgrounds are drawn into the container title (the classic font-background technique): a 176 px wide
image with ascent 13 sits exactly on the chest's top area; the slots under each card are invisible buttons.
Deterministic: same inputs give byte-identical outputs.

    python3 tools/pack/gen_ui.py [--preview DIR]
"""
from __future__ import annotations

import argparse
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pixelfont as pf  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ART = os.path.join(ROOT, "assets", "art", "source")
RP = os.path.join(ROOT, "resourcepack", "assets", "suld")
FONT_TEX = os.path.join(RP, "textures", "font")
JAVA = os.path.join(ROOT, "suld-plugin", "src", "main", "java", "mn", "suld", "plugin", "ui", "Glyphs.java")

# palette (Mongolian: eternal-sky blue, gold, crimson, felt white)
NAVY = (16, 22, 44)
NAVY2 = (26, 36, 70)
GOLD = (232, 182, 64)
GOLD_D = (150, 104, 30)
WHITE = (255, 255, 255)
SHADOW = (40, 26, 8)

providers: list[dict] = []
java_consts: list[tuple[str, str, str]] = []   # (NAME, codepoint, comment)
_next = [0xE000]


def cp() -> str:
    c = chr(_next[0])
    _next[0] += 1
    return c


def add_bitmap(name: str, img: Image.Image, rel: str, height: int, ascent: int, comment: str) -> str:
    path = os.path.join(FONT_TEX, rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=False)
    ch = cp()
    providers.append({"type": "bitmap", "file": f"suld:font/{rel}.png", "height": height, "ascent": ascent, "chars": [ch]})
    java_consts.append((name, ch, comment))
    return ch


# ----------------------------------------------------------------------------------------- spaces

def spaces() -> None:
    adv = {}
    for n in (1, 2, 4, 8, 16, 32, 64, 128):
        neg, pos = cp(), cp()
        adv[neg] = -n
        adv[pos] = n
        java_consts.append((f"NEG_{n}", neg, f"advance -{n}"))
        java_consts.append((f"POS_{n}", pos, f"advance +{n}"))
    providers.insert(0, {"type": "space", "advances": adv})


# ----------------------------------------------------------------------------------------- logo

def logo(preview: str | None) -> None:
    src = Image.open(os.path.join(ART, "logo_suld.png")).convert("RGB")
    w, h = src.size
    rgba = Image.new("RGBA", (w, h))
    sp, dp = src.load(), rgba.load()
    for y in range(h):
        for x in range(w):
            r, g, b = sp[x, y]
            m = max(r, g, b)
            # black background -> transparent; the blue glow fades out softly
            a = 0 if m < 40 else 255 if m > 120 else int((m - 40) / 80 * 255)
            dp[x, y] = (r, g, b, a)
    bbox = rgba.getbbox()
    rgba = rgba.crop(bbox)
    target_h = 24
    target_w = round(rgba.width * target_h / rgba.height)
    small = rgba.resize((target_w, target_h), Image.LANCZOS)
    # snap alpha so the logo stays crisp
    px = small.load()
    for y in range(small.height):
        for x in range(small.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 0 if a < 70 else 255)
    add_bitmap("LOGO", small, "logo", target_h, 7, f"SÜLD logo {target_w}x{target_h} (3 chat lines tall)")
    if preview:
        small.resize((small.width * 8, small.height * 8), Image.NEAREST).save(os.path.join(preview, "logo.png"))


# ----------------------------------------------------------------------------------------- icons (8x8)

ICONS = {
    "HEART": ({"r": (220, 40, 50), "R": (140, 16, 24), "w": (255, 200, 200)},
              ["........", ".RR..RR.", "RrwRRrrR", "RwrrrrrR", "RrrrrrrR", ".RrrrrR.", "..RrrR..", "...RR..."]),
    "COIN": ({"g": GOLD, "G": GOLD_D, "y": (255, 236, 150)},
             ["..GGGG..", ".GyygGG.", "GygGGgGG", "GyGggGgG", "GyGggGgG", "GggGGgGG", ".GgggGG.", "..GGGG.."]),
    "STAR": ({"y": (255, 220, 80), "Y": (190, 130, 20)},
             ["...YY...", "...yY...", "YYyyyyYY", ".Yyyyyy.", "..YyyY..", ".YyYYyY.", ".YY..YY.", "........"]),
    "EXP": ({"a": (120, 255, 120), "A": (40, 160, 40), "w": (230, 255, 230)},
            ["..AAAA..", ".AaawaA.", "AaaaawaA", "AaaaaaaA", "AaaaaaaA", "AaaaaaaA", ".AaaaaA.", "..AAAA.."]),
    "SCROLL": ({"p": (236, 214, 160), "P": (150, 110, 60), "r": (190, 40, 40)},
               [".PPPPPP.", "PppppppP", ".PpPPpP.", ".PppppP.", ".PpPPpP.", ".PppprP.", "PppppprP", ".PPPPPP."]),
    "SHIELD": ({"b": (60, 120, 220), "B": (24, 52, 120), "g": GOLD},
               ["BBBBBBBB", "BbbggbbB", "BbggggbB", "BbbggbbB", "BbbggbbB", ".BbbbbB.", "..BbbB..", "...BB..."]),
    "FLAG": ({"r": (200, 40, 40), "R": (120, 18, 18), "w": (120, 80, 40)},
             ["w.......", "wRRRRR..", "wrrrrrR.", "wrrrrrrR", "wrrrrrR.", "wRRRRR..", "w.......", "w......."]),
    "PIN": ({"r": (230, 60, 60), "R": (140, 20, 20), "w": WHITE},
            ["..RRRR..", ".RrrrrR.", "RrrwwrrR", "RrrwwrrR", ".RrrrrR.", "..RrrR..", "...RR...", "...RR..."]),
    "PERSON": ({"s": (230, 190, 150), "S": (150, 110, 80), "b": (60, 110, 220), "B": (30, 60, 140)},
               ["...SS...", "..SssS..", "..SssS..", "...SS...", ".BbbbbB.", "BbbbbbbB", "BbbbbbbB", ".BB..BB."]),
    "SWORD": ({"s": (220, 228, 240), "S": (120, 130, 150), "g": GOLD, "l": (110, 70, 40)},
              [".......S", "......Ss", ".....Ss.", "g...Ss..", ".gSss...", "..gS....", ".l.g....", "l......."]),
    "ULZII": ({"g": GOLD, "G": GOLD_D},
              [".GG..GG.", "GggGGggG", "GgGggGgG", ".GgggG..", "..GgggG.", "GgGggGgG", "GggGGggG", ".GG..GG."]),
    "SKULL": ({"w": (236, 236, 236), "W": (140, 140, 140), "k": (30, 30, 30)},
              ["..WWWW..", ".WwwwwW.", "WwwwwwwW", "WkkwwkkW", "WkkwwkkW", ".WwwwwW.", "..WkkW..", "..WWWW.."]),
    "CLOCK": ({"w": (240, 240, 240), "W": (120, 120, 140), "k": (40, 40, 60)},
              ["..WWWW..", ".WwwkwW.", "WwwwkwwW", "WwwwkwwW", "WwwwwkkW", "WwwwwwwW", ".WwwwwW.", "..WWWW.."]),
    "SIGNAL": ({"g": (90, 220, 90), "G": (40, 120, 40)},
               ["........", "......gG", "......gG", "....gGgG", "....gGgG", "..gGgGgG", "..gGgGgG", "gGgGgGgG"]),
}


def icons(preview: str | None) -> None:
    sheet = []
    for name, (pal, rows) in ICONS.items():
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if c != ".":
                    img.putpixel((x, y), tuple(pal[c]) + (255,))
        add_bitmap(f"ICON_{name}", img, f"icon/{name.lower()}", 8, 7, f"8x8 icon {name.lower()}")
        sheet.append(img)
    if preview:
        s = Image.new("RGBA", (len(sheet) * 10, 10), NAVY + (255,))
        for i, im in enumerate(sheet):
            s.paste(im, (i * 10 + 1, 1), im)
        s.resize((s.width * 8, s.height * 8), Image.NEAREST).save(os.path.join(preview, "icons.png"))


# ----------------------------------------------------------------------------------------- badges

def _hex(h: str) -> tuple:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def _dark(c: tuple, k: float = 0.55) -> tuple:
    return tuple(int(v * k) for v in c)


def _light(c: tuple) -> tuple:
    return tuple(min(255, int(v + (255 - v) * 0.45)) for v in c)


# Must match mn.suld.api.style.Rank / StaffBadge (label, colour) — checked by tools/validation.
RANKS = {"ARD": ("АРД", "#B8C0CC"), "TSEREG": ("ЦЭРЭГ", "#6BD06B"), "ARAVT": ("АРАВТ", "#4FD2D2"),
         "ZUUT": ("ЗУУТ", "#4F8BFF"), "MYANGAT": ("МЯНГАТ", "#B06BFF"), "TUMEN": ("ТҮМЭН", "#FF9A3C"),
         "NOYON": ("НОЁН", "#FF5A5A"), "KHAAN": ("ХААН", "#FFD24A")}
STAFF = {"OWNER": ("ЭЗЭН", "#FFC83C"), "ADMIN": ("АДМИН", "#FF4A4A"), "DEVELOPER": ("DEV", "#4AFFB4"),
         "MOD": ("МОД", "#4F8BFF"), "HELPER": ("ТУСЛАГЧ", "#5AD2FF"), "STREAMER": ("СТРИМЕР", "#B06BFF"),
         "SPONSOR": ("ДЭМЖИГЧ", "#FF6BB0")}
CLASSES = {"BAATAR": ("БААТАР", "#B02828"), "MERGEN": ("МЭРГЭН", "#288C3C"), "BOO": ("БӨӨ", "#6E32AA"),
           "DARKHAN": ("ДАРХАН", "#C86414"), "KHULEGCHIN": ("ХҮЛЭГЧИН", "#1E64BE")}
MISC = {"CLAN": ("ОВОГ", "#1E7878"), "SAFE": ("АЮУЛГҮЙ", "#288C3C"), "DANGER": ("АЮУЛТАЙ", "#B02828")}


def badges(preview: str | None) -> None:
    out = []
    groups = [("RANK", RANKS, True), ("STAFF", STAFF, True), ("CLASS", CLASSES, False), ("", MISC, False)]
    for prefix, table, bright in groups:
        for name, (label, colour) in table.items():
            c = _hex(colour)
            # bright ranks: saturated fill with a light rim; class badges: their (darker) colour
            fill = _dark(c, 0.78) if bright else c
            edge = _light(c)
            img = pf.badge(label, fill, edge, WHITE, _dark(c, 0.35))
            key = f"BADGE_{prefix}_{name}" if prefix else f"BADGE_{name}"
            add_bitmap(key, img, f"badge/{(prefix + '_' if prefix else '') + name}".lower(), 9, 8, f"badge «{label}»")
            out.append(img)
    if preview:
        wmax = max(i.width for i in out)
        s = Image.new("RGBA", (wmax + 4, len(out) * 11 + 2), NAVY + (255,))
        for i, im in enumerate(out):
            s.paste(im, (2, 2 + i * 11), im)
        s.resize((s.width * 6, s.height * 6), Image.NEAREST).save(os.path.join(preview, "badges.png"))


# ----------------------------------------------------------------------------------------- GUI backgrounds

def pixel_art(path: str, size: tuple[int, int]) -> Image.Image:
    """Downscale a large illustration to a crisp pixel-art thumbnail (centre crop to the aspect, quantised)."""
    im = Image.open(path).convert("RGB")
    tw, th = size
    want = tw / th
    w, h = im.size
    if w / h > want:
        nw = int(h * want)
        im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = int(w / want)
        im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    im = im.filter(ImageFilter.UnsharpMask(radius=2, percent=120, threshold=2))
    im = im.resize((tw, th), Image.LANCZOS)
    im = im.quantize(colors=40, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    return im


def ornament_border(d: ImageDraw.ImageDraw, x0: int, y0: int, x1: int, y1: int) -> None:
    d.rectangle([x0, y0, x1, y1], outline=GOLD_D)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], outline=GOLD)


def card(img: Image.Image, art: str, label: str, x: int, y: int, w: int, h: int, banner) -> None:
    """A menu card: gold frame, coloured banner with the label, pixel-art illustration."""
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=NAVY)
    bh = 11
    d.rectangle([x + 1, y + 1, x + w - 2, y + bh], fill=banner)
    d.line([x + 1, y + bh + 1, x + w - 2, y + bh + 1], fill=tuple(max(0, c - 60) for c in banner))
    # a real word always beats an abbreviation: bold when it fits the banner, regular weight when it only fits that way
    bold = True
    tw = pf.text_width(label, bold=True)
    if tw > w - 4:
        bold = False
        tw = pf.text_width(label, bold=False)
    if tw > w - 4:
        raise SystemExit(f"card label «{label}» is {tw}px even in regular weight, wider than the {w - 4}px banner")
    pf.draw_text(img, x + (w - tw) // 2, y + 3, label, WHITE, tuple(max(0, c - 90) for c in banner), bold=bold)
    pic = pixel_art(os.path.join(ART, art + ".png"), (w - 2, h - bh - 3))
    img.paste(pic, (x + 1, y + bh + 2))
    ornament_border(d, x - 1, y - 1, x + w, y + h)


def header(img: Image.Image, title_width_hint: int = 0) -> None:
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 175, 16], fill=NAVY2)
    d.line([0, 16, 175, 16], fill=GOLD)
    # small ulzii-style knots in the header corners
    for ox in (3, 164):
        for (dx, dy) in ((1, 0), (2, 0), (0, 1), (3, 1), (1, 2), (2, 2), (0, 3), (3, 3), (1, 4), (2, 4)):
            img.putpixel((ox + dx * 2, 4 + dy * 2), GOLD + (255,))


def gui_menu(name: str, cards: list[tuple[str, str, tuple]], preview: str | None, rows: int = 6) -> None:
    """A rows-row chest background with 3x(rows/3) cards; card i covers 3x3 slots."""
    height = 17 + rows * 18 + 1
    img = Image.new("RGBA", (176, height), NAVY + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 175, height - 1], outline=GOLD_D)
    header(img)
    for i, (art, label, banner) in enumerate(cards):
        col, row = i % 3, i // 3
        x = 8 + col * 54
        y = 18 + row * 54
        card(img, art, label, x + 1, y + 1, 52, 52, banner)
    add_bitmap(f"GUI_{name.upper()}", img, f"gui/{name}", height, 13, f"chest background {name} 176x{height}")
    if preview:
        img.resize((176 * 4, height * 4), Image.NEAREST).save(os.path.join(preview, f"gui_{name}.png"))


def gui_grid(name: str, cards: list[tuple[str, str, tuple]], preview: str | None) -> None:
    """A 6-row chest with 4x2 portrait cards, each 2 slots wide and 3 tall; the 9th slot column holds buttons."""
    rows = 6
    height = 17 + rows * 18 + 1
    img = Image.new("RGBA", (176, height), NAVY + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 175, height - 1], outline=GOLD_D)
    header(img)
    for i, (art, label, banner) in enumerate(cards):
        col, row = i % 4, i // 4
        card(img, art, label, 8 + col * 36 + 1, 18 + row * 54 + 1, 34, 52, banner)
    # the ninth column: two button wells, drawn as empty slots
    for r in range(rows):
        x, y = 8 + 8 * 18, 18 + r * 18
        d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=NAVY2, outline=GOLD_D)
    add_bitmap(f"GUI_{name.upper()}", img, f"gui/{name}", height, 13, f"chest background {name} 176x{height} (4x2 cards)")
    if preview:
        img.resize((176 * 4, height * 4), Image.NEAREST).save(os.path.join(preview, f"gui_{name}.png"))


def gui_frame(name: str, preview: str | None) -> None:
    """A 6-row chest with a framed 9x5 item area and a separate bottom row for navigation."""
    rows = 6
    height = 17 + rows * 18 + 1
    img = Image.new("RGBA", (176, height), NAVY + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 175, height - 1], outline=GOLD_D)
    header(img)
    for r in range(rows):
        for c in range(9):
            x, y = 8 + c * 18, 18 + r * 18
            d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=NAVY2 if r < 5 else NAVY, outline=GOLD_D if r < 5 else (60, 46, 24))
    d.line([2, 18 + 5 * 18 - 2, 173, 18 + 5 * 18 - 2], fill=GOLD)
    add_bitmap(f"GUI_{name.upper()}", img, f"gui/{name}", height, 13, f"chest background {name} 176x{height} (framed slots)")
    if preview:
        img.resize((176 * 4, height * 4), Image.NEAREST).save(os.path.join(preview, f"gui_{name}.png"))


# ----------------------------------------------------------------------------------------- invisible item

def blank_item() -> None:
    tex = os.path.join(RP, "textures", "item", "gui_blank.png")
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(tex)
    os.makedirs(os.path.join(RP, "models", "item"), exist_ok=True)
    with open(os.path.join(RP, "models", "item", "gui_blank.json"), "w", encoding="utf-8") as fh:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "suld:item/gui_blank"}}, fh, indent=2)
        fh.write("\n")
    os.makedirs(os.path.join(RP, "items"), exist_ok=True)
    with open(os.path.join(RP, "items", "gui_blank.json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": "suld:item/gui_blank"}}, fh, indent=2)
        fh.write("\n")


# ----------------------------------------------------------------------------------------- skill map

PARCH = (212, 188, 138)
PARCH_D = (172, 146, 98)
BRONZE = (96, 64, 30)
BRONZE_L = (150, 104, 44)
LEATHER = (52, 36, 22)
TURQ = (42, 196, 180)


def gui_skillmap(preview: str | None) -> None:
    """The skill-map chest: aged parchment with contour lines and steppe motifs, a bronze frame with turquoise
    corner studs, and a dark leather toolbar row. Procedural and deterministic (no external art)."""
    import random
    rows = 6
    height = 17 + rows * 18 + 1
    img = Image.new("RGBA", (176, height), LEATHER + (255,))
    px = img.load()
    rnd = random.Random(20240607)
    # parchment area (the five map rows)
    y0, y1 = 17, 17 + 5 * 18
    for y in range(y0, y1):
        for x in range(1, 175):
            n = rnd.randint(-7, 7)
            edge = min(x - 1, 174 - x, y - y0, y1 - 1 - y)
            shade = 0 if edge > 9 else (9 - edge) * 3
            c = tuple(max(0, min(255, PARCH[i] + n - shade)) for i in range(3))
            px[x, y] = c + (255,)
    d = ImageDraw.Draw(img)
    # contour lines (low hills) and little grass ticks: the steppe
    for k in range(7):
        base = y0 + 8 + k * 11
        phase = rnd.random() * 6.28
        import math
        prev = None
        for x in range(4, 172):
            y = int(base + 3 * math.sin(x / 11.0 + phase) + 2 * math.sin(x / 5.0 + phase * 2))
            if y0 + 3 <= y < y1 - 3:
                px[x, y] = tuple(max(0, c - 22) for c in PARCH_D) + (255,)
    for _ in range(46):
        x, y = rnd.randint(8, 166), rnd.randint(y0 + 6, y1 - 8)
        col = tuple(max(0, c - 34) for c in PARCH_D) + (255,)
        px[x, y] = col
        px[x - 1, y - 1] = col
        px[x + 1, y - 1] = col
    # a compass rose in the lower-left of the map, a sun in the upper-right (decor, behind the icons)
    cx, cy = 14, y1 - 14
    for i in range(-5, 6):
        px[cx + i, cy] = BRONZE + (255,)
        px[cx, cy + i] = BRONZE + (255,)
    px[cx, cy - 6] = (176, 40, 40, 255)
    # bronze frame around the map and a gold inner line
    d.rectangle([0, y0 - 1, 175, y1], outline=BRONZE)
    d.rectangle([1, y0, 174, y1 - 1], outline=BRONZE_L)
    # header
    d.rectangle([0, 0, 175, 16], fill=LEATHER)
    d.line([0, 16, 175, 16], fill=GOLD)
    for ox in (3, 164):
        for (dx, dy) in ((1, 0), (2, 0), (0, 1), (3, 1), (1, 2), (2, 2), (0, 3), (3, 3), (1, 4), (2, 4)):
            img.putpixel((ox + dx * 2, 4 + dy * 2), GOLD + (255,))
    # toolbar row: nine bronze-rimmed wells for the buttons
    d.line([0, y1, 175, y1], fill=GOLD)
    for c in range(9):
        x, y = 8 + c * 18, 18 + 5 * 18
        d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=(34, 24, 14), outline=BRONZE_L)
    # turquoise studs in the frame corners
    for (x, y) in ((2, y0 + 1), (173, y0 + 1), (2, y1 - 2), (173, y1 - 2)):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            img.putpixel((x + dx - (1 if x > 100 else 0), y + dy - (1 if y > 100 else 0)), TURQ + (255,))
    d.rectangle([0, 0, 175, height - 1], outline=BRONZE)
    add_bitmap("GUI_SKILLMAP", img, "gui/skillmap", height, 13, f"chest background skillmap 176x{height} (parchment map, toolbar row)")
    if preview:
        img.resize((176 * 4, height * 4), Image.NEAREST).save(os.path.join(preview, "gui_skillmap.png"))


def warp_to_slots(src: Image.Image, xs_map: list, xs_bar: list, ys: list, bar_top: int, height: int) -> Image.Image:
    """Resample painted art onto the 176 px chest so that its wells land exactly on the slots: a separable
    piecewise-linear map through control points (source px -> chest px) per axis, with its own x map for the toolbar
    band (y >= bar_top), each chest pixel the average of the source area it covers. Deterministic."""
    import numpy as np
    a = np.asarray(src.convert("RGBA")).astype(np.float64)
    H, W = a.shape[:2]
    ii = np.zeros((H + 1, W + 1, 4))
    ii[1:, 1:] = a.cumsum(0).cumsum(1)

    def inv(points, v):  # chest coordinate -> source coordinate
        dst = [d for _, d in points]
        srcs = [s for s, _ in points]
        return float(np.interp(v, dst, srcs))

    out = Image.new("RGBA", (176, height))
    px = out.load()
    for y in range(height):
        y0, y1 = inv(ys, y), inv(ys, y + 1)
        for x in range(176):
            xm = xs_bar if y >= bar_top else xs_map
            x0, x1 = inv(xm, x), inv(xm, x + 1)
            r0, r1 = int(max(0, min(H - 1, round(y0)))), int(max(1, min(H, round(y1))))
            c0, c1 = int(max(0, min(W - 1, round(x0)))), int(max(1, min(W, round(x1))))
            if r1 <= r0:
                r1 = r0 + 1
            if c1 <= c0:
                c1 = c0 + 1
            tot = ii[r1, c1] - ii[r0, c1] - ii[r1, c0] + ii[r0, c0]
            v = tot / ((r1 - r0) * (c1 - c0))
            px[x, y] = tuple(int(round(t)) for t in v[:3]) + (255,)
    return out


# ----------------------------------------------------------------------------------------- dungeon ladder

# (row, col) of each ladder dungeon's slot, bottom to top: the stair climbs from the steppe to the sky palace.
# Mirrored in suld-plugin gui/DungeonMenu.SLOTS.
DUNGEON_SLOTS = [(4, 1), (4, 3), (4, 5), (4, 7), (2, 7), (2, 5), (2, 3), (2, 1), (0, 2), (0, 5)]
STONE = (118, 110, 98)
STONE_D = (70, 64, 58)
STONE_L = (168, 158, 140)


def gui_dungeons(preview: str | None) -> None:
    """The dungeon ladder: a night sky over layered mountains, a gold-edged stone stair winding through ten slot
    plinths from the steppe (bottom) to the sky palace (top), and a toolbar row. Procedural and deterministic."""
    import math
    import random
    rows = 6
    height = 17 + rows * 18 + 1
    art = os.path.join(ART, "gui_dungeons.webp")
    if os.path.exists(art):
        # the owner's painted night-steppe ladder (assets/art/source/gui_dungeons.webp, 1672x941, its ten plinths in
        # this layout): warped so each plinth's dark well covers its slot exactly (measured well interiors below)
        def wells(lefts_rights, cols):
            pts = [(0, 0)]
            for (l, r), c in zip(lefts_rights, cols):
                pts += [(l, 8 + 18 * c), (r, 8 + 18 * c + 16)]
            return sorted(set(pts + [(1672, 176)]))
        xs_map = wells([(308, 430), (464, 588), (612, 734), (934, 1056), (1240, 1362)], [1, 2, 3, 5, 7])
        xs_bar = wells([(96, 234), (262, 400), (426, 570), (596, 736), (762, 902), (928, 1068), (1096, 1236),
                        (1264, 1406), (1432, 1572)], range(9))
        ys = [(0, 0), (112, 18), (230, 34), (344, 54), (458, 70), (614, 90), (728, 106), (774, 108), (904, 124), (941, height)]
        img = warp_to_slots(Image.open(art), xs_map, xs_bar, ys, 107, height)
        add_bitmap("GUI_DUNGEONS", img, "gui/dungeons", height, 13, f"chest background dungeons 176x{height} (dungeon ladder)")
        if preview:
            img.resize((176 * 4, height * 4), Image.NEAREST).save(os.path.join(preview, "gui_dungeons.png"))
        return
    img = Image.new("RGBA", (176, height), NAVY + (255,))
    px = img.load()
    rnd = random.Random(20261011)
    y0, y1 = 17, 17 + 5 * 18
    # sky: deep navy at the top fading to a dusk teal at the horizon
    for y in range(y0, y1):
        t = (y - y0) / (y1 - y0)
        c = (int(14 + 20 * t), int(20 + 40 * t), int(46 + 30 * t))
        for x in range(1, 175):
            px[x, y] = c + (255,)
    for _ in range(70):
        x, y = rnd.randint(3, 172), rnd.randint(y0 + 2, y0 + 50)
        b = rnd.randint(150, 255)
        px[x, y] = (b, b, min(255, b + 20), 255)
    # eternal-sky sun disc (top right)
    cx, cy = 156, y0 + 10
    for y in range(cy - 6, cy + 7):
        for x in range(cx - 6, cx + 7):
            if (x - cx) ** 2 + (y - cy) ** 2 <= 36:
                px[x, y] = (250, 214, 110, 255)
    # three mountain layers, darker towards the front
    for k, (base, amp, col) in enumerate(((y0 + 58, 14, (40, 56, 84)), (y0 + 70, 11, (30, 42, 64)), (y0 + 82, 7, (22, 30, 46)))):
        ph = rnd.random() * 6.28
        for x in range(1, 175):
            top = int(base - amp * abs(math.sin(x / (19.0 - 4 * k) + ph)) - 3 * math.sin(x / 7.0 + ph * 2))
            for y in range(max(y0, top), y1):
                px[x, y] = col + (255,)
            if k == 0 and top < y1 and top - 1 >= y0:
                px[x, top] = (210, 224, 236, 255)  # snow line on the far range
    d = ImageDraw.Draw(img)

    def centre(r: int, c: int) -> tuple[int, int]:
        return 8 + c * 18 + 7, 18 + r * 18 + 7

    # the stair: a 5 px stone path with a gold edge between consecutive plinths (horizontal, then vertical)
    for (r0, c0), (r1, c1) in zip(DUNGEON_SLOTS, DUNGEON_SLOTS[1:]):
        (xa, ya), (xb, yb) = centre(r0, c0), centre(r1, c1)
        pts = [(xa, ya), (xb, ya), (xb, yb)] if r0 == r1 else [(xa, ya), (xa, yb), (xb, yb)]
        for (p0, p1) in zip(pts, pts[1:]):
            d.line([p0, p1], fill=GOLD_D, width=7)
            d.line([p0, p1], fill=STONE, width=5)
        for (p0, p1) in zip(pts, pts[1:]):  # step marks every 4 px
            n = max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1]))
            for i in range(0, n, 4):
                x = p0[0] + (p1[0] - p0[0]) * i // max(1, n)
                y = p0[1] + (p1[1] - p0[1]) * i // max(1, n)
                px[x, y] = STONE_D + (255,)
    # plinths: a stone well with a gold frame under every dungeon slot
    for r, c in DUNGEON_SLOTS:
        x, y = 8 + c * 18, 18 + r * 18
        d.rectangle([x - 2, y - 2, x + 17, y + 17], fill=GOLD_D)
        d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=STONE_D, outline=GOLD)
    # header and toolbar row
    d.rectangle([0, 0, 175, 16], fill=NAVY2)
    d.line([0, 16, 175, 16], fill=GOLD)
    for ox in (3, 164):
        for (dx, dy) in ((1, 0), (2, 0), (0, 1), (3, 1), (1, 2), (2, 2), (0, 3), (3, 3), (1, 4), (2, 4)):
            img.putpixel((ox + dx * 2, 4 + dy * 2), GOLD + (255,))
    d.line([0, y1, 175, y1], fill=GOLD)
    d.rectangle([1, y1 + 1, 174, height - 2], fill=NAVY)
    for c in range(9):
        x, y = 8 + c * 18, 18 + 5 * 18
        d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=NAVY2, outline=GOLD_D)
    d.rectangle([0, 0, 175, height - 1], outline=GOLD_D)
    add_bitmap("GUI_DUNGEONS", img, "gui/dungeons", height, 13, f"chest background dungeons 176x{height} (dungeon ladder)")
    if preview:
        img.resize((176 * 4, height * 4), Image.NEAREST).save(os.path.join(preview, "gui_dungeons.png"))


# ----------------------------------------------------------------------------------------- story map (/quest)

# (row, col) of the 18 chapters, mirrored in suld-plugin gui/QuestMenu.SLOTS: a zigzag road along the bottom two
# rows (Хэрлэн, then Говь), up the right edge, and back along the top two rows (Хангай, then Алтай).
QUEST_SLOTS = [(4, 0), (3, 1), (4, 2), (3, 3), (4, 4), (3, 5), (4, 6), (3, 7), (4, 8),
               (1, 8), (0, 7), (1, 6), (0, 5), (1, 4), (0, 3), (1, 2), (0, 1), (1, 0)]
# chapter index -> region tint (Хэрлэн green steppe, Говь sand, Хангай forest, Алтай snow)
QUEST_BANDS = [(0, 5, (126, 158, 84)), (5, 9, (214, 184, 120)), (9, 14, (78, 120, 72)), (14, 18, (200, 214, 222))]


def gui_quests(preview: str | None) -> None:
    """The story map: the owner's painted map when present, else (procedural) an old parchment map with four tinted lands (steppe, desert, forest, snow peaks), the
    Kharkhorum camp in the middle row, and a red-dashed road through 18 framed chapter wells. Deterministic."""
    import math
    import random
    rows = 6
    height = 17 + rows * 18 + 1
    art = os.path.join(ART, "gui_quests.webp")
    if os.path.exists(art):
        # the owner's painted story map (assets/art/source/gui_quests.webp, drawn on this very layout): box-filtered
        # down to the chest's 176 px, its wells land on the chapter slots
        img = Image.open(art).convert("RGBA").resize((176, height), Image.BOX)
        add_bitmap("GUI_QUESTS", img, "gui/quests", height, 13, f"chest background quests 176x{height} (story map)")
        if preview:
            img.resize((176 * 4, height * 4), Image.NEAREST).save(os.path.join(preview, "gui_quests.png"))
        return
    img = Image.new("RGBA", (176, height), LEATHER + (255,))
    px = img.load()
    rnd = random.Random(20261012)
    y0, y1 = 17, 17 + 5 * 18

    def centre(r: int, c: int) -> tuple[int, int]:
        return 8 + c * 18 + 7, 18 + r * 18 + 7

    # parchment with land tints: each pixel takes the tint of the nearest chapter's region (soft Voronoi)
    pts = [(centre(r, c), i) for i, (r, c) in enumerate(QUEST_SLOTS)]
    def band(i: int) -> tuple:
        for a, b, col in QUEST_BANDS:
            if a <= i < b:
                return col
        return PARCH
    for y in range(y0, y1):
        for x in range(1, 175):
            best, bi = 1e9, 0
            for (cx, cy), i in pts:
                d = (cx - x) ** 2 + (cy - y) ** 2
                if d < best:
                    best, bi = d, i
            t = band(bi)
            n = rnd.randint(-6, 6)
            edge = min(x - 1, 174 - x, y - y0, y1 - 1 - y)
            shade = 0 if edge > 8 else (8 - edge) * 3
            c = tuple(max(0, min(255, int(PARCH[k] * 0.55 + t[k] * 0.45) + n - shade)) for k in range(3))
            px[x, y] = c + (255,)
    d = ImageDraw.Draw(img)
    # the middle row: the Orkhon valley and the Kharkhorum camp (a gold ger outline in the centre)
    my = 18 + 2 * 18
    for x in range(4, 172):
        yy = int(my + 8 + 2 * math.sin(x / 9.0))
        px[x, yy] = (84, 120, 168, 255)  # the river
    gx, gy = 88, my + 3
    d.ellipse([gx - 7, gy - 1, gx + 7, gy + 9], outline=GOLD_D, fill=(236, 220, 180))
    d.polygon([(gx - 8, gy + 2), (gx, gy - 6), (gx + 8, gy + 2)], outline=GOLD_D, fill=(214, 196, 150))
    d.rectangle([gx - 1, gy + 4, gx + 1, gy + 9], fill=(150, 60, 40))
    # the road: a red dashed line chapter to chapter
    for (r0, c0), (r1, c1) in zip(QUEST_SLOTS, QUEST_SLOTS[1:]):
        (xa, ya), (xb, yb) = centre(r0, c0), centre(r1, c1)
        n = max(abs(xb - xa), abs(yb - ya))
        for i in range(n):
            if (i // 2) % 2:
                continue
            x = xa + (xb - xa) * i // max(1, n)
            y = ya + (yb - ya) * i // max(1, n)
            for dx, dy in ((0, 0), (1, 0), (0, 1)):
                px[x + dx, y + dy] = (150, 36, 30, 255)
    # chapter wells: a bronze frame on darker parchment
    for r, c in QUEST_SLOTS:
        x, y = 8 + c * 18, 18 + r * 18
        d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=PARCH_D, outline=BRONZE)
        d.rectangle([x, y, x + 15, y + 15], outline=BRONZE_L)
    # compass rose (left of the middle row, clear of the road)
    cx, cy = 22, my + 4
    for i in range(-4, 5):
        px[cx + i, cy] = BRONZE + (255,)
        px[cx, cy + i] = BRONZE + (255,)
    px[cx, cy - 5] = (176, 40, 40, 255)
    # frame, header, toolbar
    d.rectangle([0, y0 - 1, 175, y1], outline=BRONZE)
    d.rectangle([1, y0, 174, y1 - 1], outline=BRONZE_L)
    d.rectangle([0, 0, 175, 16], fill=LEATHER)
    d.line([0, 16, 175, 16], fill=GOLD)
    for ox in (3, 164):
        for (dx, dy) in ((1, 0), (2, 0), (0, 1), (3, 1), (1, 2), (2, 2), (0, 3), (3, 3), (1, 4), (2, 4)):
            img.putpixel((ox + dx * 2, 4 + dy * 2), GOLD + (255,))
    d.line([0, y1, 175, y1], fill=GOLD)
    for c in range(9):
        x, y = 8 + c * 18, 18 + 5 * 18
        d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=(34, 24, 14), outline=BRONZE_L)
    d.rectangle([0, 0, 175, height - 1], outline=BRONZE)
    add_bitmap("GUI_QUESTS", img, "gui/quests", height, 13, f"chest background quests 176x{height} (story map)")
    if preview:
        img.resize((176 * 4, height * 4), Image.NEAREST).save(os.path.join(preview, "gui_quests.png"))


TREE_COLORS = {
    "off": ((84, 62, 36), (50, 36, 20)),
    "on": ((250, 196, 60), (150, 100, 20)),
    "next": ((60, 224, 204), (20, 120, 110)),
    "red": ((226, 64, 64), (120, 24, 24)),
}


def tree_items() -> None:
    """Connector lines between skill nodes: horizontal, vertical and both diagonals in four states (unlearned,
    learned path, next step, exclusive choice). Each is an item model scaled to fill an 18 px slot so neighbours join."""
    os.makedirs(os.path.join(RP, "textures", "item"), exist_ok=True)
    os.makedirs(os.path.join(RP, "models", "item"), exist_ok=True)
    os.makedirs(os.path.join(RP, "items"), exist_ok=True)
    for color, (main, dark) in TREE_COLORS.items():
        for kind in ("h", "v", "d1", "d2"):
            img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            d = ImageDraw.Draw(img)
            if kind == "h":
                d.rectangle([0, 7, 15, 8], fill=main + (255,))
                d.line([0, 9, 15, 9], fill=dark + (255,))
            elif kind == "v":
                d.rectangle([7, 0, 8, 15], fill=main + (255,))
                d.line([9, 0, 9, 15], fill=dark + (255,))
            else:
                for i in range(16):
                    x = i if kind == "d1" else 15 - i
                    for dx in (0, 1):
                        xx = min(15, max(0, x + dx - (1 if kind == "d2" else 0)))
                        img.putpixel((xx, i), main + (255,))
                    xs = min(15, max(0, x + 2 - (1 if kind == "d2" else 0)))
                    if img.getpixel((xs, i))[3] == 0:
                        img.putpixel((xs, i), dark + (255,))
            name = f"tree_{kind}_{color}"
            img.save(os.path.join(RP, "textures", "item", name + ".png"))
            with open(os.path.join(RP, "models", "item", name + ".json"), "w", encoding="utf-8") as fh:
                json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"suld:item/{name}"},
                           "display": {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.125, 1.125, 1.125]}}}, fh, indent=2)
                fh.write("\n")
            with open(os.path.join(RP, "items", name + ".json"), "w", encoding="utf-8") as fh:
                json.dump({"model": {"type": "minecraft:model", "model": f"suld:item/{name}"}}, fh, indent=2)
                fh.write("\n")


def orb_item() -> None:
    """Orb of Oblivion: a violet sphere with a bright core (resets the skill tree)."""
    import math
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            if r <= 7.2:
                t = r / 7.2
                lx, ly = x - 5.5, y - 5.0
                hi = max(0.0, 1 - math.hypot(lx, ly) / 5.5)
                base = (int(70 + 120 * (1 - t) + 80 * hi), int(18 + 40 * (1 - t) + 120 * hi), int(120 + 100 * (1 - t) + 100 * hi))
                if 0.55 < t < 0.75 and (x + y) % 3 == 0:
                    base = (200, 90, 230)
                img.putpixel((x, y), tuple(min(255, c) for c in base) + (255,))
    for (x, y) in ((8, 8), (7, 7), (8, 7), (7, 8)):
        img.putpixel((x, y), (250, 220, 255, 255))
    img.putpixel((3, 6), (255, 255, 255, 255))
    img.putpixel((12, 3), (230, 180, 255, 255))
    name = "orb_oblivion"
    img.save(os.path.join(RP, "textures", "item", name + ".png"))
    with open(os.path.join(RP, "models", "item", name + ".json"), "w", encoding="utf-8") as fh:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"suld:item/{name}"}}, fh, indent=2)
        fh.write("\n")
    with open(os.path.join(RP, "items", name + ".json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": f"suld:item/{name}"}}, fh, indent=2)
        fh.write("\n")


# ----------------------------------------------------------------------------------------- java

def write_java() -> None:
    lines = [
        "package mn.suld.plugin.ui;",
        "",
        "import net.kyori.adventure.key.Key;",
        "",
        "/**",
        " * GENERATED by tools/pack/gen_ui.py — do not edit. Code points of the glyphs in the resource-pack font",
        " * {@code suld:ui} (logo, icons, rank badges, GUI backgrounds, space shifts).",
        " */",
        "public final class Glyphs {",
        "",
        "    public static final Key FONT = Key.key(\"suld\", \"ui\");",
        "",
    ]
    for name, ch, comment in java_consts:
        lines.append(f"    /** {comment} */")
        lines.append(f"    public static final String {name} = \"\\u{ord(ch):04X}\";")
    lines += ["", "    private Glyphs() {", "    }", "}", ""]
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))


MAIN_MENU = [
    ("card_profile", "ДҮР", (40, 90, 200)),
    ("card_class", "АНГИ", (176, 40, 40)),
    ("card_quests", "ЭРЭЛ", (200, 120, 20)),
    ("card_rank", "ЦОЛ", (130, 60, 190)),
    ("card_shop", "ДЭЛГҮҮР", (40, 140, 60)),
    ("card_help", "ЗААВАР", (30, 120, 120)),
]

COSMETICS = [
    ("card_cos_tag", "ЦОЛ", (200, 140, 30)),
    ("card_cos_name", "НЭР", (176, 40, 40)),
    ("card_cos_chat", "ЧАТ", (40, 90, 200)),
    ("card_cos_join", "ОРОЛТ", (130, 60, 190)),
    ("card_cos_emoji", "ЭМОЖИ", (200, 90, 140)),
    ("card_cos_aura", "ГЭРЭЛ", (30, 120, 120)),
    ("card_cos_trail", "МӨР", (40, 140, 60)),
    ("card_cos_kill", "ЯЛАЛТ", (176, 70, 40)),
]

WELCOME = [
    ("card_help", "ЗААВАР", (40, 140, 60)),
    ("card_welcome", "ЦЭС", (200, 140, 30)),
    ("card_travel", "ТОГЛОХ", (40, 90, 200)),
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
    spaces()
    logo(args.preview)
    icons(args.preview)
    badges(args.preview)
    gui_menu("main", MAIN_MENU, args.preview)
    gui_menu("welcome", WELCOME, args.preview, rows=3)
    gui_grid("cosmetics", COSMETICS, args.preview)
    gui_frame("frame", args.preview)
    gui_skillmap(args.preview)
    blank_item()
    tree_items()
    orb_item()
    gui_dungeons(args.preview)  # appended last: earlier glyphs keep their code points
    gui_quests(args.preview)
    os.makedirs(os.path.join(RP, "font"), exist_ok=True)
    with open(os.path.join(RP, "font", "ui.json"), "w", encoding="utf-8") as fh:
        json.dump({"providers": providers}, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    write_java()
    print(f"ui: {len(java_consts)} glyphs, font suld:ui, Glyphs.java written")


if __name__ == "__main__":
    main()
