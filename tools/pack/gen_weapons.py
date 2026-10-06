#!/usr/bin/env python3
"""Generate the SÜLD class weapons: 5 classes x 4 tiers, as 3D voxel item models.

Each weapon is a 16x16 role sprite (B blade, G guard, H handle, P gem, A accent...) painted with a tier palette:
  T1 iron & leather · T2 steel & bronze · T3 gold & crimson · T4 the celestial blue of Тэнгэр.
Tiers 3-4 use an ornate variant of the shape. The model is built from the sprite as real 3D geometry — one box
per run of pixels, thicker for guards, gems and grips than for the blade — so the weapon has depth in the hand.
Bows get the three pulling frames. Output: textures + models under resourcepack/assets/suld/, and the item
definitions' entries are written by tools/pack/gen_items.py (custom_model_data 871000 + class*10 + tier).

    python3 tools/pack/gen_weapons.py [--preview DIR]
"""
from __future__ import annotations

import argparse
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "resourcepack", "assets", "suld", "textures", "item", "weapon")
MODELS = os.path.join(ROOT, "resourcepack", "assets", "suld", "models", "item", "weapon")
OUT_JSON = os.path.join(ROOT, "resourcepack", "assets", "suld", "weapons.json")

# --- tier palettes: role -> RGB ----------------------------------------------------------------
PALETTES = {
    1: {"W": (235, 240, 248), "B": (200, 206, 216), "b": (150, 158, 172), "k": (96, 104, 118), "G": (128, 90, 54),
        "g": (84, 56, 32), "H": (100, 64, 38), "h": (66, 42, 24), "P": (160, 160, 172), "A": (176, 44, 44), "S": (232, 228, 214)},
    2: {"W": (244, 250, 255), "B": (176, 204, 236), "b": (118, 150, 196), "k": (58, 80, 126), "G": (218, 154, 72),
        "g": (150, 96, 40), "H": (58, 46, 40), "h": (36, 28, 24), "P": (236, 176, 84), "A": (206, 40, 40), "S": (240, 236, 222)},
    3: {"W": (255, 252, 232), "B": (240, 230, 204), "b": (204, 184, 142), "k": (142, 112, 72), "G": (255, 212, 76),
        "g": (178, 122, 30), "H": (124, 22, 32), "h": (82, 12, 20), "P": (226, 34, 64), "A": (255, 96, 60), "S": (255, 240, 200)},
    4: {"W": (226, 255, 255), "B": (152, 232, 255), "b": (82, 162, 242), "k": (36, 82, 182), "G": (255, 226, 122),
        "g": (202, 152, 42), "H": (30, 42, 92), "h": (16, 22, 58), "P": (170, 255, 255), "A": (122, 255, 232), "S": (200, 255, 255)},
}
OUTLINE = {1: (24, 22, 24), 2: (22, 20, 26), 3: (40, 22, 12), 4: (10, 24, 60)}
# thickness (model units, out of 16) per role: the blade is thin, guards/gems/grips stand out
DEPTH = {"W": 1.0, "B": 1.0, "b": 1.0, "k": 1.0, "S": 0.5, "G": 3.0, "g": 3.0, "P": 3.0, "A": 2.0, "H": 2.0, "h": 2.0, "#": 1.0}

# --- shapes: class -> (base, ornate) ----------------------------------------------------------------
SHAPES = {
    "baatar": ([
        "................", "..............W.", ".............WB.", "............WBb.", "...........WBb..", "..........WBb...",
        ".........WBb....", "........WBb.....", ".......WBb......", "...G..WBb.......", "....GGBk........", ".....gG.........",
        "....hHgG........", "...hHh..........", "..Ah............", ".AA.............",
    ], [
        "..............W.", ".............WB.", "............WBb.", "...........WBkb.", "..........WBkb..", ".........WBkb...",
        "........WBkb....", ".......WBkb.....", "......WBkb......", "..GG.WBkb.......", "...GGGBk........", "..G.gGG.........",
        "....hHgGG.......", "...hHh..G.......", "..Ph............", ".APA............",
    ]),
    "mergen": ([
        "................", ".....GHh........", ".....S..Hh......", ".....S....h.....", ".....S.....H....", ".....S......H...",
        ".....S......H...", ".....S......G...", ".....S......hA..", ".....S......G...", ".....S......H...", ".....S......H...",
        ".....S.....H....", ".....S....h.....", ".....S..Hh......", ".....GHh........",
    ], [
        "....A...........", ".....GHh........", ".....S..Hh......", ".....S....hG....", ".....S.....H....", ".....S......H...",
        ".....S......G...", ".....S......P...", ".....S......hA..", ".....S......P...", ".....S......G...", ".....S......H...",
        ".....S.....H....", ".....S....hG....", ".....S..Hh......", "....AGHh........",
    ]),
    "boo": ([
        "............AAA.", "...........APPA.", "...........APPA.", "............AA..", "...........hH...", "..........hH....",
        ".........hH.....", "........hH......", ".......hH.......", "......hH.A......", ".....hH.A.......", "....hH..........",
        "...hH...........", "..hH............", ".hH.............", "................",
    ], [
        "...........GAAG.", "..........GAPPAG", "..........GAPPAG", "...........GAAG.", "..........AhH.A.", ".........A.hH.A.",
        ".........hH.....", "........hH......", ".......hG.......", "......hH.A......", ".....hH.A.......", "....hG.A........",
        "...hH...........", "..hH............", ".gH.............", "................",
    ]),
    "darkhan": ([
        "................", "......kbbbbk....", ".....kbBBBBbk...", ".....kbBWWBbk...", ".....kbBBBBbk...", "......kbGGbk....",
        "........hH......", ".......hH.......", "......hH........", ".....hH.........", "....hH..........", "...hH...........",
        "..hH............", ".PP.............", ".P..............", "................",
    ], [
        ".......G..G.....", "......kbbbbk....", ".....kbBAABbk...", "....GkbAWWAbkG..", ".....kbBAABbk...", "......kbGGbk....",
        "........hG......", ".......hH.......", "......hG........", ".....hH.........", "....hG..........", "...hH...........",
        "..hH............", ".PP.............", "PAP.............", "................",
    ]),
    "khulegchin": ([
        "..............W.", ".............WB.", "............WBb.", "...........kBb..", "..........AGk...", ".........AAh....",
        "........A.h.....", ".........h......", "........h.......", ".......h........", "......h.........", ".....h..........",
        "....h...........", "...h............", "..h.............", ".g..............",
    ], [
        ".............WW.", "............WBBW", "...........WBkbW", "..........WBkb..", ".........AGGk...", "........AAPh....",
        ".......A.AhG....", "........A.h.....", "........h.......", ".......G........", "......h.........", ".....h..........",
        "....G...........", "...h............", "..h.............", ".gg.............",
    ]),
}

CLASSES = ["baatar", "mergen", "boo", "darkhan", "khulegchin"]
BASE_ITEM = {"baatar": "iron_sword", "mergen": "bow", "boo": "blaze_rod", "darkhan": "iron_axe", "khulegchin": "iron_sword"}

HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
BOW_DISPLAY = dict(HANDHELD)
BOW_DISPLAY.update({
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
})


def tier_shape(cls: str, tier: int) -> list[str]:
    base, ornate = SHAPES[cls]
    rows = ornate if tier >= 3 else base
    bad = [(i, r) for i, r in enumerate(rows) if len(r) != 16]
    if len(rows) != 16 or bad:
        raise SystemExit(f"{cls} tier {tier}: sprite must be 16x16, bad rows {bad}")
    return rows


def bow_pull(rows: list[str], pull: int) -> list[str]:
    """Pulled string (a V towards the archer) and a nocked arrow."""
    g = [list(r) for r in rows]
    for y in range(16):
        for x in range(16):
            if g[y][x] == "S":
                g[y][x] = "."
    top, bottom, mid, sx = 2, 14, 8, 5
    nock = sx - pull
    for y in range(top, bottom + 1):
        k = (y - top) / (mid - top) if y <= mid else (bottom - y) / (bottom - mid)
        x = round(sx + (nock - sx) * k)
        if g[y][x] == ".":
            g[y][x] = "S"
    for x in range(nock, 15):
        if g[mid][x] == "." or x == nock:
            g[mid][x] = "A" if x <= nock + 1 else "H"
    g[mid][14] = "B"
    g[mid][15] = "W"
    return ["".join(r) for r in g]


# per-class accents: the shaman's ribbons are blue khadag silk until the gold and celestial tiers
CLASS_ACCENT = {("boo", 1): (60, 120, 220), ("boo", 2): (80, 150, 240)}


def paint(rows: list[str], tier: int, cls: str = "") -> Image.Image:
    pal = dict(PALETTES[tier])
    if (cls, tier) in CLASS_ACCENT:
        pal["A"] = CLASS_ACCENT[(cls, tier)]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    filled = set()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), pal[ch] + (255,))
                filled.add((x, y))
    for (x, y) in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in filled:
                img.putpixel((nx, ny), OUTLINE[tier] + (255,))
    return img


def voxel_model(rows: list[str], texture: str, display: dict) -> dict:
    """3D model: the outlined sprite as boxes (one per same-depth run of a row); depth by role."""
    # the outline pixels (added in paint) take the depth of their thickest neighbour: a solid silhouette
    depth = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                depth[(x, y)] = DEPTH.get(ch, 1.0)
    inner = dict(depth)
    for (x, y) in inner:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if 0 <= n[0] < 16 and 0 <= n[1] < 16 and n not in inner:
                depth[n] = max(depth.get(n, 0.0), inner[(x, y)])
    elements = []
    for y in range(16):
        x = 0
        while x < 16:
            d = depth.get((x, y))
            if not d:
                x += 1
                continue
            x0 = x
            while x + 1 < 16 and depth.get((x + 1, y)) == d:
                x += 1
            x1 = x + 1
            z0, z1 = 8 - d / 2, 8 + d / 2
            ytop = 16 - y
            uv_row = [x0, y, x1, y + 1]
            elements.append({
                "from": [x0, ytop - 1, z0], "to": [x1, ytop, z1],
                "faces": {
                    "south": {"uv": uv_row, "texture": "#0"},
                    "north": {"uv": [x1, y, x0, y + 1], "texture": "#0"},
                    "up": {"uv": uv_row, "texture": "#0"},
                    "down": {"uv": uv_row, "texture": "#0"},
                    "west": {"uv": [x0, y, x0 + 1, y + 1], "texture": "#0"},
                    "east": {"uv": [x1 - 1, y, x1, y + 1], "texture": "#0"},
                },
            })
            x += 1
    return {"textures": {"0": texture, "particle": texture}, "gui_light": "front", "elements": elements, "display": display}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    table = {}
    previews = []
    for ci, cls in enumerate(CLASSES):
        for tier in (1, 2, 3, 4):
            rows = tier_shape(cls, tier)
            name = f"{cls}_{tier}"
            frames = {name: rows}
            if cls == "mergen":
                for i, pull in enumerate((1, 2, 4)):
                    frames[f"{name}_pulling_{i}"] = bow_pull(rows, pull)
            for fname, frows in frames.items():
                img = paint(frows, tier, cls)
                img.save(os.path.join(TEX, fname + ".png"), optimize=False)
                model = voxel_model(frows, f"suld:item/weapon/{fname}", BOW_DISPLAY if cls == "mergen" else HANDHELD)
                with open(os.path.join(MODELS, fname + ".json"), "w", encoding="utf-8") as fh:
                    json.dump(model, fh, separators=(",", ":"))
                if fname == name:
                    previews.append(img)
            table[name] = {"base_item": "minecraft:" + BASE_ITEM[cls], "custom_model_data": 871000 + ci * 10 + tier,
                           "model": f"suld:item/weapon/{name}", "bow": cls == "mergen"}
    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump(table, fh, indent=1)
        fh.write("\n")
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
        sheet = Image.new("RGBA", (4 * 140, 5 * 140), (40, 44, 56, 255))
        for i, im in enumerate(previews):
            sheet.paste(im.resize((128, 128), Image.NEAREST), ((i % 4) * 140 + 6, (i // 4) * 140 + 6), im.resize((128, 128), Image.NEAREST))
        sheet.save(os.path.join(args.preview, "weapons.png"))
    print(f"{len(table)} class weapons (+ bow frames) written")


if __name__ == "__main__":
    main()
