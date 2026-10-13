#!/usr/bin/env python3
"""«Сүлдийн Атлас» art and previews (docs/WORLD_MAP.md).

The atlas frame is drawn here in the server's Mongolian style: a dark wooden board, a deep-blue band with the gold
square-wave «алхан хээ» border, red-and-gold «өлзий» knots in the corners, a title plaque, pennant tabs on both sides
(one per kind of place on the left, the tools on the right) and a coordinate cartouche below. The same pieces become
the resource pack's atlas textures; this script also composes previews from a real map export of the server
(``/suld map export``) and its real places (``/suld map pois``), so a preview shows the world exactly as it is.

Usage::

    python3 -I tools/map/atlas_art.py world <map.png> <pois.json> <out.png> [--player X Z YAW] [--title T]
    python3 -I tools/map/atlas_art.py local <map.png> <pois.json> <out.png> --origin X0 Z0 --scale S [--player X Z YAW]
        [--pin NAME X Z] [--death X Z]
    python3 -I tools/map/atlas_art.py minimap <map.png> <out.png> --origin X0 Z0 --scale S --player X Z YAW [--info L1 L2]
    python3 -I tools/map/atlas_art.py pack <repo root>      # the resource pack's atlas textures, models, item definitions

``world``: the whole world (an export at zoom 4); ``local``: a part of it (``--origin`` is the export's north-west block,
``--scale`` blocks per pixel). ``--pin`` / ``--death`` add an example player pin / latest-death marker (marked as
such in the preview's caption). Deterministic: no clock, the torn paper edge uses a fixed seed.
"""
from __future__ import annotations

import argparse
import json
import math
import random

from PIL import Image, ImageDraw, ImageFont

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_COND = "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"

C = {
    "wood_dk": (46, 28, 17), "wood": (84, 54, 32), "wood_lt": (122, 84, 50), "wood_hl": (150, 108, 66),
    "blue_dk": (14, 28, 64), "blue": (28, 54, 112), "blue_lt": (52, 90, 160),
    "gold": (224, 176, 72), "gold_dk": (150, 108, 36), "gold_lt": (252, 222, 140),
    "red": (172, 38, 44), "red_dk": (104, 20, 26), "red_lt": (214, 72, 70),
    "parch": (234, 218, 180), "parch_dk": (210, 188, 142), "parch_edge": (176, 148, 100),
    "ink": (60, 40, 24), "white": (252, 248, 238), "outline": (24, 16, 10),
}

# --------------------------------------------------------------------------------------------- pixel icons (16 px)
# '.' clear, 'd' outline; other letters index ICON_COLOURS.
ICON_COLOURS = {
    "W": (246, 240, 226), "R": (190, 40, 44), "O": (232, 132, 44), "S": (168, 166, 160), "s": (112, 110, 106),
    "K": (20, 16, 18), "E": (255, 70, 40), "P": (132, 92, 52), "B": (58, 120, 220), "l": (96, 94, 90),
    "T": (104, 126, 96), "H": (232, 222, 196), "G": (232, 184, 66), "g": (60, 160, 70), "L": (190, 245, 160),
    "C": (40, 200, 90), "Y": (250, 230, 120), "w": (210, 220, 236),
}
ICONS = {
    "city": [  # a ger
        "................",
        "......dddd......",
        "....ddWWWWdd....",
        "...dWWWWWWWWd...",
        "..dWWWWWWWWWWd..",
        ".dWWWWWWWWWWWWd.",
        ".dRRRRRRRRRRRRd.",
        ".dWWWWWWWWWWWWd.",
        ".dWWWWWOOWWWWWd.",
        ".dWWWWWOOWWWWWd.",
        ".dWWWWWOOWWWWWd.",
        ".dddddddddddddd.",
    ],
    "dungeon": [  # a cave mouth in rock
        "................",
        ".....dddddd.....",
        "...ddSSSSSSdd...",
        "..dSSSsSSSsSSd..",
        ".dSsSdddddSSSSd.",
        ".dSSdKKKKKdSsSd.",
        "dSSdKKKKKKKdSSSd",
        "dSsdKKEKEKKdSSsd",
        "dSSdKKKKKKKdSSSd",
        "dSSdKKKKKKKdSsSd",
        "dddddddddddddddd",
    ],
    "ovoo": [  # a cairn with its pole and a blue khadag
        "......d.........",
        ".....dPd........",
        ".....dPdBB......",
        ".....dPdBBB.....",
        "....dSSSdBB.....",
        "...dSsSSSd......",
        "..dSSSSsSSd.....",
        ".dSsSSSSSsSd....",
        "dSSSSsSSSSSSd...",
        "ddddddddddddd...",
    ],
    "site": [  # a stele on a stone turtle
        ".....dddd.......",
        "....dSSSSd......",
        "....dSllSd......",
        "....dSSSSd......",
        "....dSllSd......",
        "....dSSSSd......",
        "..ddSSSSSSdd....",
        ".dTTTTTTTTTTd...",
        ".dTTdTTTTdTTd...",
        "..dd..dd..dd....",
    ],
    "boss": [  # a horned skull
        "d..............d",
        "Hd............dH",
        "HHd..dddddd..dHH",
        ".HHddWWWWWWddHH.",
        "..dWWWWWWWWWWd..",
        "..dWKKWWWWKKWd..",
        "..dWKEWWWWEKWd..",
        "..dWWWWddWWWWd..",
        "...dWWWWWWWWd...",
        "....dWdWdWdd....",
        ".....dddddd.....",
    ],
    "relay": [  # a banner on a pole
        "....dddddd......",
        "....dRRRRRd.....",
        "....dRGRRRRd....",
        "....dRRRRRd.....",
        "....dPddddd.....",
        "....dPd.........",
        "....dPd.........",
        "....dPd.........",
        "...ddPdd........",
        "..dddddd........",
    ],
    "training": [  # crossed swords
        "d............d",
        "dwd........dwd",
        ".dwd......dwd.",
        "..dwd....dwd..",
        "...dwd..dwd...",
        "....dwddwd....",
        ".....dwwd.....",
        "....dwddwd....",
        "...dGd..dGd...",
        "..dGGd..dGGd..",
        ".dd........dd.",
    ],
    "pin": [  # a diamond pin
        "......dd......",
        ".....dLLd.....",
        "....dLCCLd....",
        "...dLCCCCLd...",
        "..dLCCWWCCLd..",
        "...dLCCCCLd...",
        "....dLCCLd....",
        ".....dLLd.....",
        "......dd......",
    ],
    "death": [  # a white skull
        "....dddddd....",
        "...dWWWWWWd...",
        "..dWWWWWWWWd..",
        "..dWKKWWKKWd..",
        "..dWKKWWKKWd..",
        "..dWWWddWWWd..",
        "...dWWWWWWd...",
        "....dWdWdWd...",
        ".....ddddd....",
    ],
    "all": [  # a compass star
        ".......d.......",
        "......dRd......",
        "......dRd......",
        ".....dRRRd.....",
        "....dRRRRRd....",
        "ddddddWdWdddddd",
        "dWWWWWdGdWWWWWd",
        "ddddddWdWdddddd",
        "....dWWWWWd....",
        ".....dWWWd.....",
        "......dWd......",
        "......dWd......",
        ".......d.......",
    ],
    "world": [  # a folded map
        "dddddddddddddd",
        "dHHBBHHHggHHHd",
        "dHBBBHHggggHHd",
        "dHHBHHHHggHHHd",
        "dHHHHHBBHHHHHd",
        "dHHggHBBBHHHHd",
        "dHHggHHBHHRHHd",
        "dHHHHHHHHRRRHd",
        "dddddddddddddd",
    ],
    "me": [  # a target
        "....ddddd....",
        "..ddWWWWWdd..",
        ".dWWdddddWWd.",
        ".dWd.....dWd.",
        "dWd...R...dWd",
        "dWd..RRR..dWd",
        "dWd...R...dWd",
        ".dWd.....dWd.",
        ".dWWdddddWWd.",
        "..ddWWWWWdd..",
        "....ddddd....",
    ],
    "plus": [
        "....dddd....",
        "....dWWd....",
        "....dWWd....",
        "ddddddWWdddd",
        "dWWWWWWWWWWd",
        "dWWWWWWWWWWd",
        "ddddddWWdddd",
        "....dWWd....",
        "....dWWd....",
        "....dddd....",
    ],
    "minus": [
        "............",
        "............",
        "............",
        "dddddddddddd",
        "dWWWWWWWWWWd",
        "dWWWWWWWWWWd",
        "dddddddddddd",
        "............",
    ],
    "pin_add": [
        "......dd...ddd.",
        ".....dLLd..dWd.",
        "....dLCCLdddWdd",
        "...dLCCCCLdWWWd",
        "..dLCCWWCCLddWd",
        "...dLCCCCLd.dWd",
        "....dLCCLd..ddd",
        ".....dLLd......",
        "......dd.......",
    ],
}


def icon(name: str, scale: int = 2) -> Image.Image:
    rows = ICONS[name]
    w, h = max(len(r) for r in rows), len(rows)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            im.putpixel((x, y), C["outline"] + (255,) if ch == "d" else ICON_COLOURS[ch] + (255,))
    return im.resize((w * scale, h * scale), Image.NEAREST)


# --------------------------------------------------------------------------------------------- text

def font(size: int, condensed: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_COND if condensed else FONT_BOLD, size)


def outlined(dr: ImageDraw.ImageDraw, xy, text: str, f, fill, outline=C["outline"], width: int = 2, anchor: str = "mm"):
    dr.text(xy, text, font=f, fill=fill, anchor=anchor, stroke_width=width, stroke_fill=outline)


# --------------------------------------------------------------------------------------------- ornaments

def ulzii(scale: int = 2) -> Image.Image:
    """An «өлзий» (endless knot) on a red rhombus, gold, 17 art px."""
    n = 17
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    c = n // 2
    for y in range(n):
        for x in range(n):
            d = abs(x - c) + abs(y - c)
            if d > c:
                continue
            if d == c:
                im.putpixel((x, y), C["outline"] + (255,))
            elif d == c - 1:
                im.putpixel((x, y), C["gold_dk"] + (255,))
            else:
                im.putpixel((x, y), C["red"] + (255,))
    # the woven knot: a diagonal lattice with gaps where the strands pass under each other
    for y in range(3, n - 3):
        for x in range(3, n - 3):
            if abs(x - c) + abs(y - c) > c - 3:
                continue
            u, v = (x + y) % 4, (x - y) % 4
            if u == 0 or v == 0:
                im.putpixel((x, y), C["gold_lt"] + (255,) if (x + y) % 8 < 4 else C["gold"] + (255,))
    im.putpixel((c, c), C["gold_lt"] + (255,))
    return im.resize((n * scale, n * scale), Image.NEAREST)


def hammer_band(length: int, horizontal: bool, scale: int = 2) -> Image.Image:
    """The «алхан хээ» band: a gold square wave between two gold rails on deep blue, 16 art px thick."""
    t = 16
    w, h = (length // scale, t) if horizontal else (t, length // scale)
    im = Image.new("RGBA", (w, h), C["blue"] + (255,))
    px = im.load()

    def put(a, b, col):  # a along the band, b across it
        x, y = (a, b) if horizontal else (b, a)
        if 0 <= x < w and 0 <= y < h:
            px[x, y] = col + (255,)

    n = w if horizontal else h
    for a in range(n):
        put(a, 0, C["outline"])
        put(a, 1, C["gold_dk"])
        put(a, 2, C["blue_dk"])
        put(a, t - 3, C["blue_dk"])
        put(a, t - 2, C["gold_dk"])
        put(a, t - 1, C["outline"])
        # square wave, period 10, between rows 4 and 11
        ph = a % 10
        if ph in (0, 5):
            for b in range(4, 12):
                put(a, b, C["gold"])
        elif ph < 5:
            put(a, 4, C["gold"])
        else:
            put(a, 11, C["gold"])
        # red studs inside the wave's open cells
        if ph == 2 or ph == 3:
            put(a, 9, C["red_lt"])
        if ph == 7 or ph == 8:
            put(a, 6, C["red_lt"])
    return im.resize((w * scale, h * scale), Image.NEAREST)


def torn_edge(dr: ImageDraw.ImageDraw, box, seed: int = 7, depth: int = 9):
    """A torn parchment edge just inside ``box`` (left, top, right, bottom): the paper overlaps the map by 0..depth px."""
    rnd = random.Random(seed)
    l, t, r, b = box

    def jag(n):
        out, v = [], depth / 2
        for _ in range(n):
            v = max(1, min(depth, v + rnd.uniform(-2.2, 2.2)))
            out.append(v)
        return out

    step = 6
    top = jag((r - l) // step + 2)
    bottom = jag((r - l) // step + 2)
    left = jag((b - t) // step + 2)
    right = jag((b - t) // step + 2)
    for i in range(len(top) - 1):
        x0, x1 = l + i * step, l + (i + 1) * step
        dr.polygon([(x0, t - 1), (x1, t - 1), (x1, t + top[i + 1]), (x0, t + top[i])], fill=C["parch"])
        dr.line([(x0, t + top[i]), (x1, t + top[i + 1])], fill=C["parch_edge"], width=2)
        dr.polygon([(x0, b + 1), (x1, b + 1), (x1, b - bottom[i + 1]), (x0, b - bottom[i])], fill=C["parch"])
        dr.line([(x0, b - bottom[i]), (x1, b - bottom[i + 1])], fill=C["parch_edge"], width=2)
    for i in range(len(left) - 1):
        y0, y1 = t + i * step, t + (i + 1) * step
        dr.polygon([(l - 1, y0), (l - 1, y1), (l + left[i + 1], y1), (l + left[i], y0)], fill=C["parch"])
        dr.line([(l + left[i], y0), (l + left[i + 1], y1)], fill=C["parch_edge"], width=2)
        dr.polygon([(r + 1, y0), (r + 1, y1), (r - right[i + 1], y1), (r - right[i], y0)], fill=C["parch"])
        dr.line([(r - right[i], y0), (r - right[i + 1], y1)], fill=C["parch_edge"], width=2)


def paper(size, seed: int = 3) -> Image.Image:
    """Parchment with a faint fibre noise."""
    w, h = size
    im = Image.new("RGBA", size, C["parch"] + (255,))
    rnd = random.Random(seed)
    px = im.load()
    for _ in range(w * h // 9):
        x, y = rnd.randrange(w), rnd.randrange(h)
        k = rnd.choice((-10, -6, -4, 4, 6))
        r, g, b, a = px[x, y]
        px[x, y] = (max(0, min(255, r + k)), max(0, min(255, g + k)), max(0, min(255, b + k)), a)
    return im


def pennant(w: int, h: int, active: bool, left_side: bool) -> Image.Image:
    """A tab: a blue (active: red) banner cloth with a gold rim and a swallowtail notch on its outer end."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    notch = h // 3
    if left_side:
        pts = [(w - 1, 0), (0, 0), (notch, h // 2), (0, h - 1), (w - 1, h - 1)]
    else:
        pts = [(0, 0), (w - 1, 0), (w - 1 - notch, h // 2), (w - 1, h - 1), (0, h - 1)]
    body = C["red"] if active else C["blue"]
    dr.polygon(pts, fill=C["outline"])
    inner = [(x + (2 if x < w / 2 else -2), y + (2 if y < h / 2 else -2)) for x, y in pts]
    dr.polygon(inner, fill=C["gold_dk"])
    inner2 = [(x + (4 if x < w / 2 else -4), y + (4 if y < h / 2 else -4)) for x, y in pts]
    dr.polygon(inner2, fill=body)
    # a lighter fold line along the cloth
    fy = 8
    dr.line([(6 if not left_side else notch + 4, fy), (w - 7 if not left_side else w - 6, fy)],
            fill=C["red_lt"] if active else C["blue_lt"], width=2)
    return im


def plaque(text: str, w: int, h: int) -> Image.Image:
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    dr.rounded_rectangle([0, 0, w - 1, h - 1], radius=10, fill=C["outline"])
    dr.rounded_rectangle([3, 3, w - 4, h - 4], radius=8, fill=C["gold_dk"])
    dr.rounded_rectangle([6, 6, w - 7, h - 7], radius=6, fill=C["blue"])
    dr.rounded_rectangle([9, 9, w - 10, h - 10], radius=4, outline=C["gold"], width=2)
    if text:
        outlined(dr, (w // 2, h // 2 + 1), text, font(h // 2 - 2), C["gold_lt"], width=3)
    k = ulzii(1)
    im.alpha_composite(k, (14, (h - k.height) // 2))
    im.alpha_composite(k, (w - 14 - k.width, (h - k.height) // 2))
    return im


# --------------------------------------------------------------------------------------------- the atlas

LEFT_TABS = [("all", "Бүгд"), ("city", "Хот, өртөө"), ("dungeon", "Агуй"), ("ovoo", "Овоо"), ("site", "Түүхэн газар"),
             ("boss", "Аварга дайсан"), ("pin", "Миний тэмдэг")]
RIGHT_TABS = [("world", "Бүх ертөнц"), ("me", "Миний байр"), ("plus", "Ойртуулах"), ("minus", "Холдуулах"), ("pin_add", "Тэмдэг тавих")]


class Atlas:
    """The atlas page: a square map well of ``well`` px inside the frame."""

    WOOD, BAND, MARGIN = 18, 32, 20
    TAB_W, TAB_H, TAB_GAP = 64, 48, 10
    TOP, BOTTOM = 30, 40

    def __init__(self, well: int = 640):
        self.well = well
        self.frame = well + 2 * (self.MARGIN + self.BAND + self.WOOD)
        self.width = self.frame + 2 * (self.TAB_W - 14)
        self.height = self.TOP + self.frame + self.BOTTOM
        self.fx = (self.width - self.frame) // 2
        self.fy = self.TOP
        inset = self.WOOD + self.BAND + self.MARGIN
        self.map_box = (self.fx + inset, self.fy + inset, self.fx + inset + well, self.fy + inset + well)

    def base(self) -> Image.Image:
        """The page under the map: the parchment well."""
        im = Image.new("RGBA", (self.width, self.height), (0, 0, 0, 0))
        o = self.WOOD + self.BAND
        x0, y0 = self.fx + o, self.fy + o
        im.alpha_composite(paper((self.frame - 2 * o, self.frame - 2 * o)), (x0, y0))
        return im

    def frame_over(self, im: Image.Image, active_left: int = 0, title: str = "СҮЛДИЙН АТЛАС", tabs: bool = True):
        dr = ImageDraw.Draw(im)
        fx, fy, F = self.fx, self.fy, self.frame
        # torn paper over the map's edge
        torn_edge(dr, self.map_box)
        # wooden board
        o = self.WOOD
        dr.rectangle([fx, fy, fx + F - 1, fy + o - 1], fill=C["wood"])
        dr.rectangle([fx, fy + F - o, fx + F - 1, fy + F - 1], fill=C["wood"])
        dr.rectangle([fx, fy, fx + o - 1, fy + F - 1], fill=C["wood"])
        dr.rectangle([fx + F - o, fy, fx + F - 1, fy + F - 1], fill=C["wood"])
        dr.rectangle([fx, fy, fx + F - 1, fy + F - 1], outline=C["outline"], width=3)
        dr.rectangle([fx + 4, fy + 4, fx + F - 5, fy + F - 5], outline=C["wood_lt"], width=2)
        rnd = random.Random(11)
        for _ in range(140):  # wood grain
            side = rnd.randrange(4)
            if side < 2:
                x, y = fx + rnd.randrange(8, F - 30), fy + (rnd.randrange(7, o - 3) if side == 0 else F - o + rnd.randrange(3, o - 7))
                dr.line([(x, y), (x + rnd.randrange(8, 30), y)], fill=C["wood_dk"] if rnd.random() < 0.6 else C["wood_hl"], width=1)
            else:
                y, x = fy + rnd.randrange(8, F - 30), fx + (rnd.randrange(7, o - 3) if side == 2 else F - o + rnd.randrange(3, o - 7))
                dr.line([(x, y), (x, y + rnd.randrange(8, 30))], fill=C["wood_dk"] if rnd.random() < 0.6 else C["wood_hl"], width=1)
        # the blue «алхан хээ» band
        b0 = o
        length = F - 2 * b0
        top = hammer_band(length, True)
        im.alpha_composite(top, (fx + b0, fy + b0))
        im.alpha_composite(top.transpose(Image.FLIP_TOP_BOTTOM), (fx + b0, fy + F - b0 - self.BAND))
        side = hammer_band(length, False)
        im.alpha_composite(side, (fx + b0, fy + b0))
        im.alpha_composite(side.transpose(Image.FLIP_LEFT_RIGHT), (fx + F - b0 - self.BAND, fy + b0))
        # inner red line around the parchment
        p0 = o + self.BAND
        dr.rectangle([fx + p0, fy + p0, fx + F - p0 - 1, fy + F - p0 - 1], outline=C["red_dk"], width=2)
        # corner knots
        k = ulzii(2)
        for cx, cy in ((fx + b0 + self.BAND // 2, fy + b0 + self.BAND // 2), (fx + F - b0 - self.BAND // 2, fy + b0 + self.BAND // 2),
                       (fx + b0 + self.BAND // 2, fy + F - b0 - self.BAND // 2), (fx + F - b0 - self.BAND // 2, fy + F - b0 - self.BAND // 2)):
            im.alpha_composite(k, (cx - k.width // 2, cy - k.height // 2))
        if tabs:
            self._tabs(im, active_left)
        # title plaque
        pl = plaque(title, 360, 54)
        im.alpha_composite(pl, ((self.width - pl.width) // 2, fy - self.TOP + 2))

    def _tabs(self, im: Image.Image, active_left: int):
        fy, F, fx = self.fy, self.frame, self.fx
        n = len(LEFT_TABS)
        span = n * self.TAB_H + (n - 1) * self.TAB_GAP
        y = fy + (F - span) // 2
        for i, (ic, _) in enumerate(LEFT_TABS):
            p = pennant(self.TAB_W, self.TAB_H, i == active_left, True)
            x = fx - self.TAB_W + 16
            im.alpha_composite(p, (x, y))
            g = icon(ic)
            im.alpha_composite(g, (x + 18 + (self.TAB_W - 24 - g.width) // 2, y + (self.TAB_H - g.height) // 2))
            y += self.TAB_H + self.TAB_GAP
        n = len(RIGHT_TABS)
        span = n * self.TAB_H + (n - 1) * self.TAB_GAP
        y = fy + (F - span) // 2
        for ic, _ in RIGHT_TABS:
            p = pennant(self.TAB_W, self.TAB_H, False, False)
            x = fx + F - 16
            im.alpha_composite(p, (x, y))
            g = icon(ic)
            im.alpha_composite(g, (x + 6 + (self.TAB_W - 24 - g.width) // 2, y + (self.TAB_H - g.height) // 2))
            y += self.TAB_H + self.TAB_GAP

    def cartouche(self, im: Image.Image, text: str, scale_text: str = "", scale_px: int = 0):
        dr = ImageDraw.Draw(im)
        w, h = 400, 40
        x, y = (self.width - w) // 2, self.fy + self.frame - 22
        dr.rounded_rectangle([x, y, x + w, y + h], radius=8, fill=C["outline"])
        dr.rounded_rectangle([x + 3, y + 3, x + w - 3, y + h - 3], radius=6, fill=C["gold_dk"])
        dr.rounded_rectangle([x + 6, y + 6, x + w - 6, y + h - 6], radius=4, fill=C["blue_dk"])
        outlined(dr, (x + w // 2, y + h // 2 + 1), text, font(17), C["white"], width=2)
        if scale_px:
            # a scale bar on the parchment, bottom right inside the map
            mx1, my1 = self.map_box[2] - 16, self.map_box[3] - 22
            mx0 = mx1 - scale_px
            dr.rectangle([mx0 - 6, my1 - 18, mx1 + 6, my1 + 8], fill=C["parch"] + (220,))
            dr.line([(mx0, my1), (mx1, my1)], fill=C["ink"], width=3)
            dr.line([(mx0, my1 - 5), (mx0, my1 + 5)], fill=C["ink"], width=2)
            dr.line([(mx1, my1 - 5), (mx1, my1 + 5)], fill=C["ink"], width=2)
            dr.text(((mx0 + mx1) // 2, my1 - 9), scale_text, font=font(12), fill=C["ink"], anchor="mm")


# --------------------------------------------------------------------------------------------- map content

def player_arrow(dr: ImageDraw.ImageDraw, x: float, y: float, yaw: float, size: float = 11):
    """Minecraft yaw: 0 = south (+z), 90 = west, 180 = north; the arrow points where the player looks."""
    a = math.radians(yaw)
    fwd = (-math.sin(a), math.cos(a))  # +z is down on the map
    side = (-fwd[1], fwd[0])
    tip = (x + fwd[0] * size, y + fwd[1] * size)
    back = (x - fwd[0] * size * 0.6, y - fwd[1] * size * 0.6)
    l = (x - fwd[0] * size * 0.9 + side[0] * size * 0.75, y - fwd[1] * size * 0.9 + side[1] * size * 0.75)
    r = (x - fwd[0] * size * 0.9 - side[0] * size * 0.75, y - fwd[1] * size * 0.9 - side[1] * size * 0.75)
    dr.polygon([tip, l, back, r], fill=C["outline"])
    s = 0.72
    tip2 = (x + fwd[0] * size * s, y + fwd[1] * size * s)
    l2 = (x - fwd[0] * size * 0.6 + side[0] * size * 0.5, y - fwd[1] * size * 0.6 + side[1] * size * 0.5)
    r2 = (x - fwd[0] * size * 0.6 - side[0] * size * 0.5, y - fwd[1] * size * 0.6 - side[1] * size * 0.5)
    back2 = (x - fwd[0] * size * 0.35, y - fwd[1] * size * 0.35)
    dr.polygon([tip2, l2, back2, r2], fill=C["gold_lt"])


def compose(atlas: Atlas, map_png: str, origin, scale: float, pois, *, player=None, pins=(), death=None,
            show=("city", "dungeon", "ovoo", "site", "boss"), labels=("city", "dungeon", "boss"), regions=True,
            grid=1000, coords="", scale_text="", scale_px=0, icon_scale=2, title="СҮЛДИЙН АТЛАС") -> Image.Image:
    im = atlas.base()
    mx0, my0, mx1, my1 = atlas.map_box
    well = mx1 - mx0
    m = Image.open(map_png).convert("RGBA")
    if m.width > well or m.height > well:
        m = m.crop((max(0, (m.width - well) // 2), max(0, (m.height - well) // 2),
                    max(0, (m.width - well) // 2) + min(well, m.width), max(0, (m.height - well) // 2) + min(well, m.height)))
        ox = origin[0] + max(0, (Image.open(map_png).width - well) // 2) * scale
        oz = origin[1] + max(0, (Image.open(map_png).height - well) // 2) * scale
        origin = (ox, oz)
    offx, offy = mx0 + (well - m.width) // 2, my0 + (well - m.height) // 2
    im.alpha_composite(m, (offx, offy))

    def to_px(x, z):
        return offx + (x - origin[0]) / scale, offy + (z - origin[1]) / scale

    def inside(px, py, pad=0):
        return mx0 + pad <= px < mx1 - pad and my0 + pad <= py < my1 - pad

    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    dr = ImageDraw.Draw(layer)
    if grid:
        g0 = math.floor(origin[0] / grid) * grid
        for gx in range(int(g0), int(origin[0] + well * scale) + grid, grid):
            px, _ = to_px(gx, 0)
            if mx0 < px < mx1:
                dr.line([(px, my0), (px, my1)], fill=C["ink"] + (38,), width=1)
        g0 = math.floor(origin[1] / grid) * grid
        for gz in range(int(g0), int(origin[1] + well * scale) + grid, grid):
            _, py = to_px(0, gz)
            if my0 < py < my1:
                dr.line([(mx0, py), (mx1, py)], fill=C["ink"] + (38,), width=1)
    im.alpha_composite(layer)
    dr = ImageDraw.Draw(im)
    if regions:
        byreg = {}
        for p in pois:
            if p["kind"] == "ovoo" and p.get("region"):
                byreg.setdefault(p["region"], []).append((p["x"], p["z"]))
        rf = font(15)
        taken = []
        # the biggest regions first: they have the most room
        for name, pts in sorted(byreg.items(), key=lambda kv: -len(kv[1])):
            if name == "Хархорум":
                continue
            cx = sum(x for x, _ in pts) / len(pts)
            cz = sum(z for _, z in pts) / len(pts)
            px, py = to_px(cx, cz)
            spaced = "\u2009".join(name.upper())
            tw = dr.textlength(spaced, font=rf)
            r = (px - tw / 2, py - 10, px + tw / 2, py + 10)
            if r[0] < mx0 + 8 or r[2] > mx1 - 8 or r[1] < my0 + 8 or r[3] > my1 - 8:
                continue
            if any(r[0] < b[2] and b[0] < r[2] and r[1] < b[3] and b[1] < r[3] for b in taken):
                continue
            taken.append(r)
            dr.text((px, py), spaced, font=rf, fill=C["ink"] + (200,), anchor="mm", stroke_width=3, stroke_fill=C["parch"] + (160,))
    order = {"ovoo": 0, "site": 1, "relay": 2, "training": 3, "boss": 4, "dungeon": 5, "city": 6}
    boxes = []  # icons and labels already on the page: a label never covers one
    wanted = []
    for p in sorted(pois, key=lambda q: order.get(q["kind"], 0)):
        if p["kind"] not in show:
            continue
        px, py = to_px(p["x"], p["z"])
        if not inside(px, py, 6):
            continue
        sc = icon_scale if p["kind"] in ("city", "dungeon", "boss") else max(1, icon_scale - 1) if icon_scale > 1 else 1
        g = icon(p["kind"], sc)
        x0, y0 = int(px - g.width / 2), int(py - g.height / 2)
        im.alpha_composite(g, (x0, y0))
        boxes.append((x0, y0, x0 + g.width, y0 + g.height))
        if p["kind"] in labels:
            wanted.append((-order.get(p["kind"], 0), p["name"], px, py, g.height, p["kind"]))
    for name, x, z in pins:
        px, py = to_px(x, z)
        if inside(px, py, 6):
            g = icon("pin", 2)
            x0, y0 = int(px - g.width / 2), int(py - g.height / 2)
            im.alpha_composite(g, (x0, y0))
            boxes.append((x0, y0, x0 + g.width, y0 + g.height))
            wanted.append((-10, name, px, py, g.height, "pin"))
    if death:
        px, py = to_px(*death)
        if inside(px, py, 6):
            g = icon("death", 2)
            x0, y0 = int(px - g.width / 2), int(py - g.height / 2)
            im.alpha_composite(g, (x0, y0))
            boxes.append((x0, y0, x0 + g.width, y0 + g.height))
            wanted.append((-9, "Сүүлд унасан газар", px, py, g.height, "death"))
    for _, name, px, py, ih, kind in sorted(wanted, key=lambda w: w[0]):
        f = font(13 if kind in ("city", "pin") else 12)
        tw = dr.textlength(name, font=f) + 4
        th = 15
        for cx, cy in ((px, py + ih / 2 + 9), (px, py - ih / 2 - 9), (px + ih / 2 + 6 + tw / 2, py), (px - ih / 2 - 6 - tw / 2, py)):
            r = (cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2)
            if r[0] < mx0 + 2 or r[2] > mx1 - 2 or r[1] < my0 + 2 or r[3] > my1 - 2:
                continue
            if any(r[0] < b[2] and b[0] < r[2] and r[1] < b[3] and b[1] < r[3] for b in boxes):
                continue
            outlined(dr, (cx, cy), name, f, C["white"], width=2)
            boxes.append(r)
            break
    if player:
        px, py = to_px(player[0], player[1])
        if inside(px, py):
            player_arrow(dr, px, py, player[2], 12)
    atlas.frame_over(im, 0, title)
    atlas.cartouche(im, coords, scale_text, scale_px)
    return im


def minimap(map_png: str, origin, scale: float, player, info, out: str):
    """The corner minimap at its real size: 128 × 128 map pixels drawn 2× (GUI scale 2), framed, with its info lines."""
    m = Image.open(map_png).convert("RGBA")
    cx, cz = (player[0] - origin[0]) / scale, (player[1] - origin[1]) / scale
    half = 64
    crop = m.crop((int(cx - half), int(cz - half), int(cx + half), int(cz + half)))
    bg = paper((128, 128), 5)
    bg.alpha_composite(crop)
    s = 2
    view = bg.resize((128 * s, 128 * s), Image.NEAREST)
    pad = 10
    W = view.width + 2 * pad
    H = view.height + 2 * pad + 18 * len(info) + 10
    im = Image.new("RGBA", (W + 40, H + 40), (36, 44, 40, 255))
    dr = ImageDraw.Draw(im)
    x0, y0 = 20, 20
    dr.rectangle([x0, y0, x0 + W - 1, y0 + view.height + 2 * pad - 1], fill=C["outline"])
    dr.rectangle([x0 + 3, y0 + 3, x0 + W - 4, y0 + view.height + 2 * pad - 4], fill=C["gold_dk"])
    dr.rectangle([x0 + 6, y0 + 6, x0 + W - 7, y0 + view.height + 2 * pad - 7], fill=C["blue"])
    im.alpha_composite(view, (x0 + pad, y0 + pad))
    k = ulzii(1)
    for kx, ky in ((x0 + 1, y0 + 1), (x0 + W - k.width - 1, y0 + 1), (x0 + 1, y0 + view.height + 2 * pad - k.height - 1),
                   (x0 + W - k.width - 1, y0 + view.height + 2 * pad - k.height - 1)):
        im.alpha_composite(k, (kx, ky))
    f = font(12)
    for letter, (lx, ly) in (("Х", (x0 + W // 2, y0 + 4)), ("Ө", (x0 + W // 2, y0 + view.height + 2 * pad - 5)),
                             ("Б", (x0 + 5, y0 + view.height // 2 + pad)), ("З", (x0 + W - 5, y0 + view.height // 2 + pad))):
        outlined(dr, (lx, ly), letter, f, C["gold_lt"], width=2)
    player_arrow(dr, x0 + pad + view.width / 2, y0 + pad + view.height / 2, player[2], 9)
    ty = y0 + view.height + 2 * pad + 6
    for line in info:
        tw = dr.textlength(line, font=font(13))
        dr.rectangle([x0, ty - 1, x0 + tw + 10, ty + 16], fill=(0, 0, 0, 150))
        dr.text((x0 + 5, ty), line, font=font(13), fill=C["white"])
        ty += 18
    im.save(out)


# --------------------------------------------------------------------------------------------- pack assets

#: Pixels per block of every atlas texture: a map frame is 128 map pixels a block, so the art is drawn at the same
#: density and the frame, the tabs and the maps meet pixel for pixel.
PX_PER_BLOCK = 128
FRONT_W, FRONT_H = 784, 864           # multiples of 16, so the block atlas keeps its mipmap levels
FRAME_X, FRAME_Y = 2, 30               # the frame square inside front.png (the title plaque above, the cartouche below)
TAB_NAMES = [n for n, _ in LEFT_TABS] + [n for n, _ in RIGHT_TABS]


def front_texture() -> Image.Image:
    """The frame in front of the maps: wood, the «алхан хээ» band, the parchment margin, the torn edge over the maps'
    rim, the corner knots, the title plaque and the cartouche; transparent where the maps show."""
    a = Atlas(640)
    im = Image.new("RGBA", (FRONT_W, FRONT_H), (0, 0, 0, 0))
    # the frame is drawn at (fx, fy); move it to FRAME_X/Y on this texture
    a.fx, a.fy = FRAME_X, FRAME_Y
    inset = a.WOOD + a.BAND + a.MARGIN
    a.map_box = (a.fx + inset, a.fy + inset, a.fx + inset + 640, a.fy + inset + 640)
    o = a.WOOD + a.BAND
    margin = paper((a.frame - 2 * o, a.frame - 2 * o), 4)
    hole = Image.new("L", margin.size, 255)
    ImageDraw.Draw(hole).rectangle([a.MARGIN, a.MARGIN, a.MARGIN + 639, a.MARGIN + 639], fill=0)
    margin.putalpha(hole)
    im.alpha_composite(margin, (a.fx + o, a.fy + o))
    a.width = FRONT_W
    a.frame_over(im, -1, "", tabs=False)  # the plaque's text is a text display, so it can change
    ct = plaque_blank(400, 40)
    im.alpha_composite(ct, ((FRONT_W - ct.width) // 2, a.fy + a.frame - 22))
    return im


def plaque_blank(w: int, h: int) -> Image.Image:
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dr = ImageDraw.Draw(im)
    dr.rounded_rectangle([0, 0, w - 1, h - 1], radius=10, fill=C["outline"])
    dr.rounded_rectangle([3, 3, w - 4, h - 4], radius=8, fill=C["gold_dk"])
    dr.rounded_rectangle([6, 6, w - 7, h - 7], radius=6, fill=C["blue"])
    dr.rounded_rectangle([9, 9, w - 10, h - 10], radius=4, outline=C["gold"], width=2)
    k = ulzii(1)
    im.alpha_composite(k, (14, (h - k.height) // 2))
    im.alpha_composite(k, (w - 14 - k.width, (h - k.height) // 2))
    return im


def tab_texture(name: str, state: str, left_side: bool) -> Image.Image:
    """A 64 x 48 tab: blue (normal), lighter (hover) or red (on), with its icon."""
    p = pennant(Atlas.TAB_W, Atlas.TAB_H, state == "on", left_side)
    if state == "hover":
        px = p.load()
        for y in range(p.height):
            for x in range(p.width):
                r, g, b, al = px[x, y]
                if al and (r, g, b) == C["blue"]:
                    px[x, y] = C["blue_lt"] + (al,)
    g = icon(name)
    ox = 18 if left_side else 6
    p.alpha_composite(g, (ox + (Atlas.TAB_W - 24 - g.width) // 2, (Atlas.TAB_H - g.height) // 2))
    return p


MARKERS = ["city", "relay", "dungeon", "ovoo", "site", "boss", "training", "pin", "death", "player"]


def marker_texture(name: str) -> Image.Image:
    """A marker icon on a 32 x 32 canvas (16 art px at 2x), centred, so every marker quad centres the same way."""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    if name == "player":
        player_arrow(ImageDraw.Draw(im), 16, 16, 180, 13)  # pointing up (north); the display turns it
        return im
    g = icon(name, 2)
    if g.width > 32 or g.height > 32:
        g = icon(name, 1).resize((g.width // 2 * 2 // 2, g.height // 2), Image.NEAREST)
    im.alpha_composite(g, ((32 - g.width) // 2, (32 - g.height) // 2))
    return im


def pack(root: str) -> list[str]:
    """Writes the atlas's textures, models and item definitions into the resource pack at ``root``."""
    import os
    rp = os.path.join(root, "resourcepack", "assets")
    tex = os.path.join(rp, "suld", "textures", "entity", "atlas")
    mod = os.path.join(rp, "suld", "models", "entity", "atlas")
    itm = os.path.join(rp, "suld", "items", "entity", "atlas")
    for d in (tex, mod, itm):
        os.makedirs(d, exist_ok=True)
    quads = {"back": paper((640, 640), 3), "front": front_texture()}
    for name in MARKERS:
        quads["marker_" + name] = marker_texture(name)
    for name in TAB_NAMES:
        left = name in [n for n, _ in LEFT_TABS]
        for state in ("", "_hover", "_on"):
            quads[f"tab_{name}{state}"] = tab_texture(name, state.strip("_") or "normal", left)
    written = []
    for name, img in quads.items():
        img.save(os.path.join(tex, name + ".png"), optimize=True)
        model = {"textures": {"t": "suld:entity/atlas/" + name, "particle": "suld:entity/atlas/" + name},
                 "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False,
                               "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#t"},
                                         "south": {"uv": [0, 0, 16, 16], "texture": "#t"}}}]}
        with open(os.path.join(mod, name + ".json"), "w") as fh:
            json.dump(model, fh, separators=(",", ":"))
        with open(os.path.join(itm, name + ".json"), "w") as fh:
            json.dump({"model": {"type": "minecraft:model", "model": "suld:entity/atlas/" + name}}, fh, separators=(",", ":"))
        written.append(name)
    for a in ("blocks", "items"):
        path = os.path.join(rp, "minecraft", "atlases", a + ".json")
        data = json.load(open(path)) if os.path.exists(path) else {"sources": []}
        src = {"type": "directory", "source": "entity/atlas", "prefix": "entity/atlas/"}
        if src not in data["sources"]:
            data["sources"].append(src)
            with open(path, "w") as fh:
                json.dump(data, fh, indent=1)
                fh.write("\n")
    return written


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=("world", "local", "minimap", "pack"))
    ap.add_argument("map")
    ap.add_argument("rest", nargs="*")
    ap.add_argument("--origin", nargs=2, type=float)
    ap.add_argument("--scale", type=float, default=16)
    ap.add_argument("--player", nargs=3, type=float)
    ap.add_argument("--pin", nargs=3, action="append", default=[])
    ap.add_argument("--death", nargs=2, type=float)
    ap.add_argument("--coords", default="")
    ap.add_argument("--info", nargs="*", default=[])
    ap.add_argument("--title", default="СҮЛДИЙН АТЛАС")
    a = ap.parse_args()
    if a.mode == "pack":
        names = pack(a.map)
        print(f"atlas pack assets: {len(names)} quads ({len(MARKERS)} markers, {len(TAB_NAMES)} tabs x 3)")
        return
    if a.mode == "minimap":
        minimap(a.map, a.origin, a.scale, a.player, a.info, a.rest[0])
        return
    pois = json.load(open(a.rest[0], encoding="utf-8"))["pois"]
    out = a.rest[1]
    atlas = Atlas(640)
    pins = [(n, float(x), float(z)) for n, x, z in a.pin]
    if a.mode == "world":
        img = compose(atlas, a.map, a.origin, a.scale, pois, player=a.player, pins=pins, death=a.death,
                      coords=a.coords, scale_text="1000 блок", scale_px=int(1000 / a.scale), title=a.title)
    else:
        img = compose(atlas, a.map, a.origin, a.scale, pois, player=a.player, pins=pins, death=a.death,
                      show=("city", "relay", "dungeon", "ovoo", "site", "boss", "training"),
                      labels=("city", "relay", "dungeon", "boss", "training", "site", "ovoo"), regions=False, grid=0,
                      coords=a.coords, scale_text="200 блок", scale_px=int(200 / a.scale), title=a.title)
    img.save(out)


if __name__ == "__main__":
    main()
