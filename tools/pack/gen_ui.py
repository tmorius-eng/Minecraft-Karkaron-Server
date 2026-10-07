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
    tw = pf.text_width(label, bold=True)
    if tw > w - 4:
        raise SystemExit(f"card label «{label}» is {tw}px, wider than the {w - 4}px banner")
    pf.draw_text(img, x + (w - tw) // 2, y + 3, label, WHITE, tuple(max(0, c - 90) for c in banner), bold=True)
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
    ("card_shop", "ЗАХ", (40, 140, 60)),
    ("card_help", "ЗААВАР", (30, 120, 120)),
]

COSMETICS = [
    ("card_cos_tag", "ЦОЛ", (200, 140, 30)),
    ("card_cos_name", "НЭР", (176, 40, 40)),
    ("card_cos_chat", "ЧАТ", (40, 90, 200)),
    ("card_cos_join", "ЗАР", (130, 60, 190)),
    ("card_cos_emoji", "ЭМО", (200, 90, 140)),
    ("card_cos_aura", "ЦОГ", (30, 120, 120)),
    ("card_cos_trail", "МӨР", (40, 140, 60)),
    ("card_cos_kill", "ЯЛА", (176, 70, 40)),
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
    blank_item()
    os.makedirs(os.path.join(RP, "font"), exist_ok=True)
    with open(os.path.join(RP, "font", "ui.json"), "w", encoding="utf-8") as fh:
        json.dump({"providers": providers}, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    write_java()
    print(f"ui: {len(java_consts)} glyphs, font suld:ui, Glyphs.java written")


if __name__ == "__main__":
    main()
