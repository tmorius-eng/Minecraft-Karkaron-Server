#!/usr/bin/env python3
"""Generate the SÜLD item textures (16x16 pixel art) and their item models.

Each sprite is drawn as a 16x16 character grid; a dark outline is added automatically around every
filled pixel (the vanilla item look). Output goes to resourcepack/assets/suld/{textures,models}/item/.
Deterministic: re-running produces byte-identical PNGs.

    python3 tools/pack/gen_item_textures.py [--preview DIR]   # --preview writes 16x upscaled PNGs
"""
from __future__ import annotations

import argparse
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "resourcepack", "assets", "suld", "textures", "item")
MODELS = os.path.join(ROOT, "resourcepack", "assets", "suld", "models", "item")

PALETTE = {
    # steel
    "W": (240, 244, 250), "s": (206, 213, 224), "m": (150, 160, 176), "d": (96, 104, 120),
    # gold / bronze
    "Y": (255, 222, 120), "g": (226, 172, 58), "G": (150, 102, 30),
    # leather / wood
    "L": (132, 82, 46), "l": (88, 52, 30), "w": (160, 112, 64), "v": (110, 74, 40),
    # red (tassels, cloth)
    "r": (196, 44, 44), "R": (118, 22, 26),
    # sky blue (Tenger)
    "B": (150, 220, 255), "b": (70, 150, 230), "n": (34, 82, 170), "c": (220, 248, 255),
    # bone / fang
    "o": (238, 230, 206), "O": (196, 184, 152), "q": (140, 128, 100),
    # fur
    "f": (168, 164, 158), "F": (118, 114, 110), "u": (150, 108, 70), "U": (100, 70, 44),
    # sand (Gobi)
    "a": (232, 204, 140), "A": (186, 150, 84),
    # string
    "t": (230, 226, 210),
    # jade (хаш)
    "j": (128, 214, 150), "J": (52, 136, 84),
}
OUTLINE = (26, 22, 22, 255)

# --- sprites: 16 rows x 16 cols, "." = transparent --------------------------------------------

SPRITES: dict[str, list[str]] = {
    # Талын Илд — curved Mongol steppe saber: bright edge, gold guard, red horsehair tassel.
    "talyn_ild": [
        "................",
        "..............W.",
        ".............Ws.",
        "............Wsm.",
        "...........Wsm..",
        "..........Wsm...",
        ".........Wsm....",
        "........Wsm.....",
        ".......Wsm......",
        "...g..Wsm.......",
        "....gYsm........",
        ".....gG.........",
        "....lLGg........",
        "...lLl..........",
        "..rl............",
        ".rR.............",
    ],
    # Сургамжийн Илд — plain training saber: iron blade, wooden guard, leather wrap.
    "surgamj_ild": [
        "................",
        "................",
        ".............s..",
        "............sm..",
        "...........sm...",
        "..........sm....",
        ".........sm.....",
        "........sm......",
        ".......sm.......",
        "...w..sm........",
        "....wsd.........",
        ".....wv.........",
        "....lLvw........",
        "...lL...........",
        "..vl............",
        "................",
    ],
    # Хасарын Соёо — a blade carved from the great wolf's fang, grip wrapped in grey fur.
    "khasar_soyo": [
        "................",
        "..............o.",
        ".............oO.",
        "............ooO.",
        "...........ooO..",
        "..........ooOq..",
        ".........ooOq...",
        "........ooOq....",
        ".......ooOq.....",
        "......ooOq......",
        "...F.oOq........",
        "....FfO.........",
        "....fFF.........",
        "...fFf..........",
        "..fF............",
        "..F.............",
    ],
    # Говийн Хутга — short Gobi knife, sand-gold hilt with a red bead.
    "govi_khutga": [
        "................",
        "................",
        "................",
        "................",
        "...........W....",
        "..........Ws....",
        ".........Wsm....",
        "........Wsm.....",
        ".......Wsm......",
        "......Wsm.......",
        "....aAsm........",
        ".....aA.........",
        "....AaAa........",
        "...aA...........",
        "..rA............",
        "................",
    ],
    # Хангайн Сүх — mountain axe: steel head with a blue ulzii inlay, ash haft.
    "khangai_sukh": [
        "................",
        "........dmm.....",
        ".......dmssW....",
        "......dmsbsW....",
        "......msbnbW....",
        ".......mmbsW....",
        "........wmsW....",
        ".......wv.dd....",
        "......wv........",
        ".....wv.........",
        "....wv..........",
        "...wv...........",
        "..wv............",
        ".lL.............",
        ".l..............",
        "................",
    ],
    # Сүлд Илд — Тэнгэр: the sky-blessed legendary blade, glowing blue edge, gold ulzii guard.
    "suld_ild_tenger": [
        "..............c.",
        ".............cB.",
        "............cBb.",
        "...........cBbn.",
        "..........cBbn..",
        ".........cBbn...",
        "........cBbn....",
        ".......cBbn.....",
        "......cBbn......",
        "..Y..cBbn.......",
        "...YgBbn........",
        "....gYg.........",
        "...nlgGY........",
        "..nln.G.........",
        ".Yn.............",
        "YY..............",
    ],
    # Сургамжийн Нум — Mongol composite recurve bow (standby): limbs arc right, string on the left.
    "surgamj_num": [
        "................",
        ".....Gwv........",
        ".....t..wv......",
        ".....t....v.....",
        ".....t.....w....",
        ".....t......w...",
        ".....t......w...",
        ".....t......G...",
        ".....t......lr..",
        ".....t......G...",
        ".....t......w...",
        ".....t......w...",
        ".....t.....w....",
        ".....t....v.....",
        ".....t..wv......",
        ".....Gwv........",
    ],
}


def pelt(fill: str, shade: str) -> list[str]:
    """A stretched hide (head, four legs, tail), mirrored left/right."""
    half = ["........", "......SS", ".....SFF", "..S..SFF", "..FSSFFF", "...FFFFF", "...FFSFF", "...FFFFF",
            "...FFFFF", "...FFSFF", "..FSFFFF", "..S..FFF", "......FF", ".......F", ".......S", "........"]
    rows = [h + h[::-1] for h in half]
    return [r.replace("F", fill).replace("S", shade) for r in rows]


SPRITES["chonon_arisan"] = pelt("f", "F")      # Чонын арьс — grey wolf pelt
SPRITES["baavgain_arisan"] = pelt("u", "U")    # Баавгайн арьс — brown bear pelt


def ring(band: str, light: str, shade: str, gem: list[str]) -> list[str]:
    """A finger ring seen from the front: a round band (lit top-left, shaded bottom-right) and a stone on top."""
    rows = [list("." * 16) for _ in range(16)]
    cx, cy = 7.5, 9.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 3.6 <= d <= 5.4:
                lit = (x - cx) + (y - cy) < -2
                dark = (x - cx) + (y - cy) > 3
                rows[y][x] = light if lit else shade if dark else band
    for dy, line in enumerate(gem):          # the stone sits on the top of the band
        for dx, ch in enumerate(line):
            if ch != ".":
                rows[1 + dy][6 + dx] = ch
    return ["".join(r) for r in rows]


SPRITES["mungun_bugj"] = ring("s", "W", "m", [".WW.", "WsmW", ".md."])      # Мөнгөн Бөгж — plain silver ring
SPRITES["khash_bugj"] = ring("s", "W", "m", [".jj.", "jjJj", "jJJJ", ".JJ."])  # Хаш Бөгж — silver set with jade
SPRITES["altan_bugj"] = ring("g", "Y", "G", [".BB.", "BcbB", "bbnb", ".nn."])  # Алтан Бөгж — gold set with turquoise

# Bow pulling frames: the string is drawn back toward the archer (left) and an arrow appears.
BOW_PULL = {
    "surgamj_num_pulling_0": 1,
    "surgamj_num_pulling_1": 2,
    "surgamj_num_pulling_2": 4,
}


def bow_frame(pull: int) -> list[str]:
    rows = [list(r) for r in SPRITES["surgamj_num"]]
    for y in range(16):
        for x in range(16):
            if rows[y][x] == "t":
                rows[y][x] = "."
    top, bottom, mid, sx = 2, 14, 8, 5
    nock = sx - pull
    for y in range(top, bottom + 1):
        k = (y - top) / (mid - top) if y <= mid else (bottom - y) / (bottom - mid)
        x = round(sx + (nock - sx) * k)
        if rows[y][x] == ".":
            rows[y][x] = "t"
    for x in range(nock, 15):          # arrow: fletching at the nock, shaft, steel head
        if rows[mid][x] == "." or x == nock:
            rows[mid][x] = "r" if x <= nock + 1 else "w"
    rows[mid][14] = "s"
    rows[mid][15] = "W"
    return ["".join(r) for r in rows]


def render(grid: list[str]) -> Image.Image:
    assert len(grid) == 16 and all(len(r) == 16 for r in grid), "sprites are 16x16"
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    filled = set()
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            img.putpixel((x, y), PALETTE[ch] + (255,))
            filled.add((x, y))
    for (x, y) in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in filled:
                img.putpixel((nx, ny), OUTLINE)
    return img


def model(name: str, parent: str = "minecraft:item/handheld") -> dict:
    return {"parent": parent, "textures": {"layer0": f"suld:item/{name}"}}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="also write 16x upscaled previews here")
    args = ap.parse_args()
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    sprites = dict(SPRITES)
    for name, pull in BOW_PULL.items():
        sprites[name] = bow_frame(pull)
    for name, grid in sprites.items():
        img = render(grid)
        img.save(os.path.join(TEX, f"{name}.png"), optimize=False)
        if args.preview:
            os.makedirs(args.preview, exist_ok=True)
            img.resize((256, 256), Image.NEAREST).save(os.path.join(args.preview, f"{name}.png"))
        if name.startswith("surgamj_num"):
            parent = "minecraft:item/bow"
        elif name in ("chonon_arisan", "baavgain_arisan", "mungun_bugj", "khash_bugj", "altan_bugj"):
            parent = "minecraft:item/generated"
        else:
            parent = "minecraft:item/handheld"
        with open(os.path.join(MODELS, f"{name}.json"), "w", encoding="utf-8") as fh:
            json.dump(model(name, parent), fh, indent=2)
            fh.write("\n")
    print(f"{len(sprites)} textures + models written")


if __name__ == "__main__":
    main()
