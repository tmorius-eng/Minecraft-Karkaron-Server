#!/usr/bin/env python3
"""The «Командын Өргөө» window kit (docs/UI_HUB.md): every piece of the owner's HD mock-up
assets/art/source/gui_hub.png (1586 x 992, a 9 x 6 chest screen at GUI scale ~4) cut out and rebuilt on the chest
grid, at hd_art.SCALE (4 texture px per GUI px), so the window is as sharp as the picture.

A hub window is three glyphs of the suld:ui font stacked in the container title (negative-space shifts between
them, see gui/HubLayout.java):

  HUB_FRAME   the frame: side banners, crest, panel cloth, the 8 tab tiles with their icons and labels, the
              «✕ Хаах» button, and an opaque band under the window that hides the player's bag
  PAGE_<X>    one per window: the title and subtitle on the header, the section title and pill, the 8 command
              cards (icon well, cyan title, grey subtitle, chevron)
  HUB_TAB_ON  the cyan glow tile over the active tab (drawn at tab i by a shift of 18 i)
  HUB_BACK    the «« Буцах» footer button (drawn only when the window has a parent)
  HUB_PREV / HUB_NEXT   paging arrows in the footer

Grid (chest GUI px; the chest's top-left is (0, 0)): tabs = row 0 (slots 0-7; slot 8 is an ornament), cards =
rows 1-4, two per row (columns 0-3 and 5-8; column 4 is the gutter), footer = row 5 (back 0-1, close 3-5,
paging 7-8). The canvas is X0..X0+W wide and reaches 22 px above the chest (the header): at GUI scale 4 on a
1080p screen the chest's top is 24 px under the screen edge, so nothing may rise higher.

Deterministic: same inputs give byte-identical outputs. Used by gen_ui.py (``build``), which appends the glyphs
after every older one so the older code points never move.
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass, field

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import hd_art
from hd_art import SCALE as S

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ART = os.path.join(ROOT, "assets", "art", "source")
SRC = os.path.join(ART, "gui_hub.png")
FONT_DIRS = ["/usr/share/fonts/opentype/inter", "/usr/share/fonts/truetype/inter", "/usr/share/fonts/truetype/dejavu"]

# ------------------------------------------------------------------------------------------- the grid (GUI px)

X0, Y0 = -18, -22            # the canvas's top-left in chest coordinates
W, H = 212, 244              # the canvas: x -18..194, y -22..222 (the chest is 176 x 222)
PANEL = (-2, -22, 178, 134)  # the framed window
SLOT_X, SLOT_Y, ROW = 8, 18, 18
TAB_W, TAB_H = 17, 16        # a tab tile inside its 18 px slot: x slot..slot+17, y 19..35
SECTION_Y = (36, 41)         # the section title strip, above the first card row
CARDS = 8
CARD_H = 12                  # a card tile, at the bottom of its 18 px row (row top + 5 .. + 17)
CARD_COLS = ((0, 4), (5, 9))  # slot columns of the left and right card
FOOTER_Y = 108
CLOSE = (62, 110, 116, 124)  # the «✕ Хаах» button over slots 3-5
BACK = (8, 110, 46, 124)     # the «« Буцах» button over slots 0-1
ARROW = ((134, 110, 150, 124), (154, 110, 170, 124))   # prev (slot 7), next (slot 8)
BAND_Y = 128                 # from here down the canvas hides the player's bag (opaque; the panel covers its top)

# ------------------------------------------------------------------------------------------- the mock-up (source px)

M_PANEL = (288, 148, 1300, 886)        # the panel's outer rim
M_PANEL_BORDER = 36                    # its ornate frame, kept unstretched on all four sides
M_CLOTH = (705, 432, 955, 476)         # plain panel cloth (between the tabs and the cards; row 429 is a gold rail)
M_CREST = (560, 28, 1030, 172)         # the trident crest with its horns and medallion, over the sky
M_TITLE_ORN = ((300, 150, 560, 236), (1030, 150, 1290, 236))   # the gold lines beside the title
M_TAB_LEFT = [300, 420, 543, 669, 795, 921, 1050, 1176]        # the 8 tab tiles' left edges
M_TAB_Y = (283, 415)
M_TAB_WIDTH = 113
M_TAB_BORDER = 12
M_TAB_ICON = (14, 8, 100, 93)          # an icon's box inside its tile
M_CARD_LEFT = ((318, 782), (802, 1268))
M_CARD_ROWS = ((480, 556), (565, 641), (650, 726), (735, 811))
M_CARD_BORDER = 10
M_CARD_CLEAN = (655, 486, 700, 550)    # clean card cloth between the subtitle and the silhouette
M_WELL = (17, 6, 102, 70)              # the icon well inside a card (rounded dark square with the icon)
M_CHEVRON = (422, 20, 452, 56)         # the chevron, from the card's left edge
M_SECTION_ICON = (318, 438, 364, 474)
M_CLOSE = (668, 826, 922, 882)
M_CLOSE_BORDER = 14
M_CLOSE_CLEAN = (16, 14, 44, 42)       # red cloth left of the «✕», relative to the button
M_BANNERS = ((212, 84, 304, 930), (1282, 84, 1374, 930))   # the hanging side banners, over the world

TAB_ICONS = ["helmet", "scroll", "swords", "chest", "chart", "map", "gear", "book"]
CARD_ICONS = ["compass", "chest", "scroll", "chart", "swords", "map", "arrows", "book"]   # the mock-up's 8 wells

# colours (from the mock-up)
GOLD = (236, 190, 76)
GOLD_LIGHT = (255, 228, 140)
GOLD_DARK = (160, 108, 30)
INK = (46, 28, 8)
CYAN = (86, 226, 236)
CYAN_DARK = (16, 70, 84)
WHITE = (242, 242, 246)
GREY = (164, 176, 200)
DIM = (150, 160, 182)
NAVY = (12, 18, 40)
BAND = (6, 8, 16)
RED = (170, 38, 36)


# ------------------------------------------------------------------------------------------- helpers

def _hd(v: float) -> int:
    return int(round(v * S))


def cx(x: float) -> int:
    """Canvas HD x of a chest GUI x."""
    return _hd(x - X0)


def cy(y: float) -> int:
    return _hd(y - Y0)


def _np(img: Image.Image) -> np.ndarray:
    return np.asarray(img.convert("RGBA")).astype(np.float64)


def _img(a: np.ndarray) -> Image.Image:
    return Image.fromarray(np.clip(np.rint(a), 0, 255).astype(np.uint8), "RGBA")


def _font(weight: str, px: int) -> ImageFont.FreeTypeFont:
    names = {"display": ["InterDisplay-Bold.otf", "Inter-Bold.otf", "DejaVuSans-Bold.ttf"],
             "bold": ["Inter-Bold.otf", "DejaVuSans-Bold.ttf"],
             "semi": ["Inter-SemiBold.otf", "Inter-Medium.otf", "DejaVuSans.ttf"]}[weight]
    for d in FONT_DIRS:
        for n in names:
            p = os.path.join(d, n)
            if os.path.exists(p):
                return ImageFont.truetype(p, px)
    return ImageFont.load_default(px)


def letter(text: str, px: int, weight: str = "bold", colour=WHITE, outline=None, shadow=None, pixel: int = 0,
           gradient=None) -> Image.Image:
    """``text`` as an RGBA image, ``px`` the font size in HD px. ``pixel`` = k draws it blocky like the mock-up's
    lettering: rendered k times smaller without smoothing, enlarged k times. ``outline`` adds a 1-unit dark edge,
    ``shadow`` a drop shadow one unit down-right; ``gradient`` = (top, bottom) colours the fill top to bottom.
    The image is as tall as the font's line (ascent + descent), so pieces of one size share a baseline
    (``img.info["baseline"]`` = the baseline's y in the image)."""
    k = max(1, pixel)
    font = _font(weight, max(4, px // k))
    asc, desc = font.getmetrics()
    bbox = font.getbbox(text)
    tw = bbox[2]
    pad = 2
    mask = Image.new("L", (tw + 2 * pad, asc + desc + 2 * pad), 0)
    ImageDraw.Draw(mask).text((pad, pad), text, font=font, fill=255, anchor="la")
    m = np.asarray(mask).astype(np.float64) / 255.0
    if k > 1:
        m = (m >= 0.5).astype(np.float64)
    h, w = m.shape
    layer = np.zeros((h + 2, w + 2, 4))

    def paint(mm, col, dx, dy, alpha=1.0):
        sub = layer[1 + dy:1 + dy + h, 1 + dx:1 + dx + w]
        a = mm * alpha
        for c in range(3):
            sub[..., c] = sub[..., c] * (1 - a) + col[c] * a
        sub[..., 3] = np.maximum(sub[..., 3], a * 255)

    if shadow is not None:
        paint(m, shadow, 1, 1, 0.85)
    if outline is not None:
        o = np.zeros_like(m)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            o = np.maximum(o, np.roll(np.roll(m, dy, axis=0), dx, axis=1))
        paint(o, outline, 0, 0)
    if gradient is not None:
        top, bot = gradient
        # the gradient runs over the letters' own height (cap top to baseline), light above
        y_top, y_base = pad + max(0, bbox[1]), pad + asc
        t = np.clip((np.arange(h)[:, None] - y_top) / max(1, y_base - y_top), 0, 1)
        fill = np.zeros((h, w, 3))
        for c in range(3):
            fill[..., c] = top[c] * (1 - t) + bot[c] * t
        sub = layer[1:1 + h, 1:1 + w]
        for c in range(3):
            sub[..., c] = sub[..., c] * (1 - m) + fill[..., c] * m
        sub[..., 3] = np.maximum(sub[..., 3], m * 255)
    else:
        paint(m, colour, 0, 0)
    out = _img(layer)
    base = (1 + pad + asc) * k
    if k > 1:
        out = out.resize((out.width * k, out.height * k), Image.NEAREST)
    out.info["baseline"] = base
    return out


def letter_run(parts, px: int, weight: str = "display", pixel: int = 0, gap: int = 0) -> Image.Image:
    """Several lettered pieces side by side on one baseline: ``parts`` = (text, px_scale, kwargs) tuples."""
    imgs = [letter(t, round(px * sc), weight, pixel=pixel, **kw) for t, sc, kw in parts]
    base = max(i.info["baseline"] for i in imgs)
    below = max(i.height - i.info["baseline"] for i in imgs)
    out = Image.new("RGBA", (sum(i.width for i in imgs) + gap * (len(imgs) - 1), base + below), (0, 0, 0, 0))
    x = 0
    for i in imgs:
        out.alpha_composite(i, (x, base - i.info["baseline"]))
        x += i.width + gap
    out.info["baseline"] = base
    return out


def diamond(size: int, colour, edge=INK) -> Image.Image:
    """A small gold diamond (the mock-up's «◆» ornament), ``size`` HD px."""
    img = Image.new("RGBA", (size + 2, size + 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = (size + 1) / 2
    r = size / 2
    d.polygon([(c, c - r), (c + r, c), (c, c + r), (c - r, c)], fill=colour + (255,), outline=edge + (255,))
    return img


def cross(size: int, colour, edge=INK) -> Image.Image:
    """The «✕» of the close button."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w = max(2, size // 5)
    m = size // 6
    for col, off in ((edge, 2), (colour, 0)):
        d.line([(m, m), (size - m, size - m)], fill=col + (255,), width=w + off)
        d.line([(size - m, m), (m, size - m)], fill=col + (255,), width=w + off)
    return img


def _paste_centre(dst: Image.Image, img: Image.Image, box, dy: int = 0) -> None:
    """alpha-composite ``img`` centred in ``box`` (HD px)."""
    x0, y0, x1, y1 = box
    dst.alpha_composite(img, (round((x0 + x1 - img.width) / 2), round((y0 + y1 - img.height) / 2) + dy))


def _paste_left(dst: Image.Image, img: Image.Image, x: int, box_y) -> None:
    y0, y1 = box_y
    dst.alpha_composite(img, (x, round((y0 + y1 - img.height) / 2)))


def _tile(patch: Image.Image, size: tuple[int, int]) -> Image.Image:
    """``patch`` tiled (mirrored every other copy, so no seam shows) to ``size``."""
    w, h = size
    out = Image.new("RGBA", size)
    pw, ph = patch.size
    flipped = patch.transpose(Image.FLIP_LEFT_RIGHT)
    vflip = patch.transpose(Image.FLIP_TOP_BOTTOM)
    both = flipped.transpose(Image.FLIP_TOP_BOTTOM)
    for j, y in enumerate(range(0, h, ph)):
        for i, x in enumerate(range(0, w, pw)):
            p = (patch, flipped, vflip, both)[(i % 2) + 2 * (j % 2)]
            out.paste(p, (x, y))
    return out


def _keyed(img: Image.Image, sat_lo: float = 0.16, sat_hi: float = 0.34, feather: int = 2) -> Image.Image:
    """A piece painted over the (blurred, grey) world: saturated pixels (the blue cloth, the gold) stay, the
    desaturated backdrop goes transparent; the gold tassels' dark fur keeps a soft edge."""
    a = _np(img)
    rgb = a[..., :3]
    mx, mn = rgb.max(axis=2), rgb.min(axis=2)
    sat = (mx - mn) / np.maximum(mx, 1.0)
    alpha = np.clip((sat - sat_lo) / (sat_hi - sat_lo), 0, 1)
    # warm, mid-bright pixels (gold, fur) keep even when their saturation is low
    warm = (rgb[..., 0] > rgb[..., 2] + 25) & (mx > 90)
    alpha = np.where(warm, np.maximum(alpha, np.clip((mx - 90) / 60, 0, 1)), alpha)
    if feather:
        al = Image.fromarray((alpha * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(feather))
        alpha = np.asarray(al).astype(np.float64) / 255.0
    a[..., 3] = a[..., 3] * alpha
    return _img(a)


def _clean_nine(src: Image.Image, box, border: int, clean_box, size: tuple[int, int], k: float) -> Image.Image:
    """A framed tile of the mock-up (``box``), its inside painted over with the tile's own clean cloth
    (``clean_box``, tiled), nine-sliced to ``size`` HD px with the frame ``border`` scaled by ``k``."""
    tile = src.crop(box).convert("RGBA")
    patch = src.crop(clean_box).convert("RGBA")
    inner = (border, border, tile.width - border, tile.height - border)
    cloth = _tile(patch, (inner[2] - inner[0], inner[3] - inner[1]))
    tile.paste(cloth, (inner[0], inner[1]))
    return hd_art._nine(tile, (border, border, border, border), size, k)


def _icon_cut(src: Image.Image, box, size: tuple[int, int]) -> Image.Image:
    """An icon lifted off its dark tile: the brightness above the tile's cloth becomes alpha, colours kept."""
    im = src.crop(box).convert("RGB").resize(size, Image.LANCZOS)
    a = np.asarray(im).astype(np.float64)
    lum = a.mean(axis=2)
    bg = np.percentile(lum, 25)
    alpha = np.clip((lum - bg - 10) / 40.0, 0, 1)
    sat = (a.max(axis=2) - a.min(axis=2)) / np.maximum(a.max(axis=2), 1.0)
    alpha = np.maximum(alpha, np.clip((sat - 0.3) / 0.3, 0, 1) * (lum > bg + 6))
    return _img(np.dstack([a, alpha * 255]))


# ------------------------------------------------------------------------------------------- the pieces

class Kit:
    """Every piece of the mock-up at HD size, cut once."""

    def __init__(self, path: str = SRC):
        self.src = Image.open(path).convert("RGBA")
        src = self.src
        # source px per GUI px of the mock-up: its panel is the chest's 180 px (PANEL) wide
        self.k = (PANEL[2] - PANEL[0]) * S / (M_PANEL[2] - M_PANEL[0])
        k = self.k
        self.cloth = src.crop(M_CLOTH)
        # tab tiles: the inactive template (tab 2) and the active one (tab 1, cyan)
        tb = M_TAB_BORDER
        y0, y1 = M_TAB_Y
        size = (TAB_W * S, TAB_H * S)
        off_box = (M_TAB_LEFT[1], y0, M_TAB_LEFT[1] + M_TAB_WIDTH, y1)
        off_clean = (M_TAB_LEFT[1] + 16, y0 + 16, M_TAB_LEFT[1] + M_TAB_WIDTH - 16, y0 + 40)
        self.tab_off = _clean_nine(src, off_box, tb, off_clean, size, k)
        on_box = (M_TAB_LEFT[0] - 4, y0 - 4, M_TAB_LEFT[0] + M_TAB_WIDTH + 4, y1 + 4)
        on_clean = (M_TAB_LEFT[0] + 16, y0 + 16, M_TAB_LEFT[0] + M_TAB_WIDTH - 16, y0 + 40)
        on = _clean_nine(src, on_box, tb + 4, on_clean, ((TAB_W + 2) * S, (TAB_H + 2) * S), k)
        self.tab_on = self._hollow(on, inset=_hd(2.2), keep_alpha=70)
        self.tab_icons = {}
        for name, left in zip(TAB_ICONS, M_TAB_LEFT):
            bx = (left + M_TAB_ICON[0], y0 + M_TAB_ICON[1], left + M_TAB_ICON[2], y0 + M_TAB_ICON[3])
            self.tab_icons[name] = _icon_cut(src, bx, (10 * S, 10 * S))
        # cards: the template and the 8 icon wells, chevron
        (lx0, lx1), (rx0, rx1) = M_CARD_LEFT
        (ry0, ry1) = M_CARD_ROWS[0]
        card_w = (CARD_COLS[0][1] - CARD_COLS[0][0]) * ROW
        self.card = _clean_nine(src, (lx0, ry0, lx1, ry1), M_CARD_BORDER, M_CARD_CLEAN, (card_w * S, CARD_H * S), k)
        self.wells = {}
        for i, name in enumerate(CARD_ICONS):
            col, row = i % 2, i // 2
            x0 = M_CARD_LEFT[col][0]
            y0c = M_CARD_ROWS[row][0]
            bx = (x0 + M_WELL[0], y0c + M_WELL[1], x0 + M_WELL[2], y0c + M_WELL[3])
            well = src.crop(bx).convert("RGBA")
            self.wells[name] = well.resize(((CARD_H - 2) * S * well.width // well.height, (CARD_H - 2) * S), Image.LANCZOS)
        self.chevron = _icon_cut(src, (lx0 + M_CHEVRON[0], ry0 + M_CHEVRON[1], lx0 + M_CHEVRON[2], ry0 + M_CHEVRON[3]), (4 * S, 6 * S))
        # section icon
        self.section_icon = _icon_cut(src, M_SECTION_ICON, (round(46 * k), round(36 * k)))
        # the close button and its frame for other buttons
        self.close_src = src.crop(M_CLOSE).convert("RGBA")
        # crest, banners, title ornaments
        crest = _keyed(src.crop(M_CREST), 0.3, 0.5, 1)
        a = _np(crest)
        navy = (a[..., 2] > a[..., 0] + 10) & (a[..., :3].max(axis=2) < 100)   # the panel cloth behind the medallion
        a[..., 3] = np.where(navy, 0, a[..., 3])
        self.crest = _img(a)
        self.banners = [_keyed(src.crop(b), 0.14, 0.3, 2) for b in M_BANNERS]
        self.title_orn = [src.crop(b).convert("RGBA") for b in M_TITLE_ORN]

    @staticmethod
    def _hollow(tile: Image.Image, inset: int, keep_alpha: int) -> Image.Image:
        """The tile with its inside made (nearly) transparent: only the frame stays, a faint tint inside."""
        a = _np(tile)
        a[inset:-inset, inset:-inset, 3] = np.minimum(a[inset:-inset, inset:-inset, 3], keep_alpha)
        return _img(a)

    # ----- composites

    def panel(self) -> Image.Image:
        """The framed window PANEL (HD px), its inside the mock-up's cloth."""
        w = (PANEL[2] - PANEL[0]) * S
        h = (PANEL[3] - PANEL[1]) * S
        src = self.src.copy()
        # the mock-up's close button reaches into the bottom border: painted over with cloth first
        bx = (M_CLOSE[0] - 6, M_CLOSE[1] - 6, M_CLOSE[2] + 6, M_PANEL[3] - 8)
        src.paste(_tile(self.cloth, (bx[2] - bx[0], bx[3] - bx[1])), (bx[0], bx[1]))
        return _clean_nine(src, M_PANEL, M_PANEL_BORDER, M_CLOTH, (w, h), self.k)

    def button(self, text: str, colour=None, size=(54, 14), font_px: int = 22, mark: str = "cross") -> Image.Image:
        """A footer button in the «✕ Хаах» frame, re-lettered; ``colour`` recolours its cloth; ``mark`` = cross
        (a drawn «✕») or chevrons («) before the text."""
        b = self.close_src
        patch = b.crop(M_CLOSE_CLEAN)
        tile = b.copy()
        inner = (M_CLOSE_BORDER, M_CLOSE_BORDER, b.width - M_CLOSE_BORDER, b.height - M_CLOSE_BORDER)
        tile.paste(_tile(patch, (inner[2] - inner[0], inner[3] - inner[1])), (inner[0], inner[1]))
        img = hd_art._nine(tile, (M_CLOSE_BORDER,) * 4, (size[0] * S, size[1] * S), self.k)
        if colour:
            img = hd_art._recolour(img, colour)
        lab = letter(text, font_px, "bold", WHITE, outline=INK, pixel=2)
        if mark == "cross":
            m = cross(font_px, (255, 96, 86))
        else:
            m = letter("«", font_px, "bold", GOLD, outline=INK, pixel=2)
        gap = _hd(1.5)
        tw = m.width + gap + lab.width
        x = round((img.width - tw) / 2)
        _paste_left(img, m, x, (0, img.height))
        _paste_left(img, lab, x + m.width + gap, (0, img.height))
        return img

    def pill(self, text: str, width_px: int, height_px: int) -> Image.Image:
        """The section's right-hand pill: a dark rounded bar with a thin gold edge and dim lettering."""
        img = Image.new("RGBA", (width_px, height_px), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        r = height_px // 2
        d.rounded_rectangle([0, 0, width_px - 1, height_px - 1], radius=r, fill=(14, 20, 42, 220), outline=GOLD_DARK + (255,), width=2)
        lab = letter(text, round(height_px * 0.66), "semi", DIM)
        _paste_centre(img, lab, (0, 0, width_px, height_px))
        return img

    def arrow(self, right: bool) -> Image.Image:
        """A paging arrow tile (16 x 14 GUI px): the tab frame with the cards' chevron."""
        img = hd_art._nine(self.tab_off, (_hd(3),) * 4, (16 * S, 14 * S), 1.0)
        ch = self.chevron.resize((5 * S, 8 * S), Image.LANCZOS)
        ch = ch if right else ch.transpose(Image.FLIP_LEFT_RIGHT)
        _paste_centre(img, ch, (0, 0, img.width, img.height))
        return img


# ------------------------------------------------------------------------------------------- windows

@dataclass
class Card:
    icon: object          # a kit icon name, a PIL image for the well, or None: a hole where the slot's real item shows
    title: str
    subtitle: str


@dataclass
class Spec:
    name: str                         # glyph suffix: PAGE_<NAME>, file gui/hub_page_<name>.png
    title: str                        # the header's title (after «SÜLD ◆»)
    subtitle: str | None              # the header's second line; None leaves the row to the dynamic title text
    section: str
    pill: str
    cards: list[Card] = field(default_factory=list)


TABS = ["Тоглогч", "Даалгавар", "Ур чадвар", "Дэлгүүр", "Rankup", "Аялал", "Тохиргоо", "Тусламж"]

HUB = Spec("hub", "КОМАНДЫН ӨРГӨӨ", "Серверийн үндсэн командууд", "Тоглогчийн командууд", "Үндсэн тоглолтын командууд", [
    Card("compass", "/menu", "Үндсэн цэсийг нээнэ."),
    Card("chest", "/shop", "Дэлгүүрийн цэс."),
    Card("scroll", "/quest", "Даалгаврын цэс."),
    Card("chart", "/rankup", "Ранк ахих нөхцөл."),
    Card("swords", "/class", "Анги сонгох, харах."),
    Card("map", "/travel", "Аялал, бүсүүд рүү."),
    Card("arrows", "/skills", "Ур чадварын цэс."),
    Card("book", "/help", "Бүх командын жагсаалт."),
])

PROFILE = Spec("profile", "ТОГЛОГЧИЙН ПРОФАЙЛ", None, "Миний баатар", "Түвшин · зоос · цол", [
    Card("chest", "Түвшний шагнал", "Түвшин бүрийн шагнал авах."),
    Card("chart", "Цол", "Ранк ахих, одоогийн цол."),
    Card("compass", "Гоёл", "Гоёл чимэглэл, цол, нэр."),
    Card("scroll", "Эрэл", "Түүхийн бүлгүүд, явц."),
    Card("arrows", "Ур чадвар", "Чадварын мод, build."),
    Card("swords", "Овог", "Овгийн мэдээлэл, гишүүд."),
    Card("book", "Өдрийн шагнал", "Өдөр бүрийн шагнал, цуваа."),
    Card("map", "Даалгавар", "Өдрийн даалгаврууд."),
])

HELP = Spec("help", "ТУСЛАМЖ", "Заавар · командууд · дүрэм", "Тусламжийн сэдвүүд", "Дарж уншина", [
    Card("compass", "Эхлэх", "Шинэ тоглогчийн алхмууд."),
    Card("swords", "Анги", "Таван анги, /class /profile /exp."),
    Card("map", "Хархорум", "Хотын үйлчилгээ, аюулгүй бүс."),
    Card("chest", "Агуй ба бүлэг", "/party /dungeon."),
    Card("arrows", "Овог", "/clan /cc."),
    Card("chart", "Реликс", "/relic list · hint · history."),
    Card("scroll", "Командууд", "Бүх команд нэг дор."),
    Card("book", "Дүрэм", "Серверийн дүрэм."),
])

# mn.suld.api.style.Rank, in ladder order (the badge pills come from the owner's sheet, like the TAB badges)
RANKS = [("Ард", 1, 0), ("Цэрэг", 5, 150), ("Аравтын дарга", 10, 400), ("Зуутын дарга", 18, 1000),
         ("Мянгатын ноён", 26, 2500), ("Түмэний ноён", 35, 6000), ("Их ноён", 45, 14000), ("Хаан", 60, 35000)]


def _rank_icons() -> list:
    sheet = os.path.join(ART, "badges.webp")
    if not os.path.exists(sheet):
        return ["chart"] * 8
    pills = hd_art.rank_badges(sheet)[:8]
    return [hd_art.shrink(p, 9 * S) for p in pills]


RANKUP = Spec("rankup", "ЦОЛ · RANKUP", None, "Цолын шат", "Ард → Хаан", [
    Card(icon, name, f"Түвшин {lvl}" + (f" · {cost:,} зоос".replace(",", " ") if cost else " · эхлэлийн цол"))
    for icon, (name, lvl, cost) in zip(_rank_icons(), RANKS)])

# content/shops.json shop.general, the same order: the slot under each card holds the real item, so the well is a hole
# (the painted name is the short form: «Шарсан …» would run into the card's chevron; the tooltip has the full name)
SUPPLIES = [("Боов x8", 20), ("Хонины мах x8", 40), ("Үхрийн мах x8", 45), ("Бамбар x16", 12),
            ("Бамбай", 120), ("Алтан лууван x4", 90), ("Алтан алим", 300), ("Эмээл", 250)]
SHOP = Spec("shop", "ДЭЛГҮҮР", None, "Хангамж", "Зоосоор", [Card(None, n, f"{c} зоос · дарж авна") for n, c in SUPPLIES])
SHOP2 = Spec("shop2", "ДЭЛГҮҮР · ЗАХ", None, "Зах · үйлчилгээ", "2-р хуудас", [
    Card(None, "Чөдөр x2", "40 зоос · дарж авна"),
    Card("chest", "Олз зарах", "Цүнхний бүх олзыг зарна."),
    Card("scroll", "Зарах цонх", "Сонгож зарна, үнэтэйгээ."),
    Card("compass", "Гоёл", "Цол, нэр, чатын өнгө."),
    Card("chart", "Кредит дэлгүүр", "Сүлд Кредит — зөвхөн гоёлд."),
    Card("book", "Жорын ном", "Дархны 43 жор, урлах."),
    Card("swords", "Захиалгын самбар", "Бусдаар урлуулах, хөлс."),
    Card("map", "Хэтэвч", "Зоос, кредит."),
])

TRAVEL = Spec("travel", "АЯЛАЛ", None, "Өртөө ба газрууд", "Өртөөнд зогсоод аял", [
    Card("map", "Төв талбай", "Хархорумын зүрх."),
    Card("map", "Өмнөд хаалга", "Говь руу."),
    Card("map", "Баруун хаалга", "Баруун тал руу."),
    Card("map", "Зүүн хаалга", "Зүүн тал руу."),
    Card("map", "Хойд хаалга", "Хойд ой, уул руу."),
    Card("scroll", "Түүхэн газрууд", "Олсон газрууд, зам."),
    Card("compass", "Овоо", "Хамгийн ойрын овоо."),
    Card("chest", "Хархорум руу", "/spawn — хот руу буцах."),
])

SETTINGS = Spec("settings", "ТОХИРГОО", None, "Тоглолтын тохиргоо", "Гэрэлтсэн = асаалттай", [
    Card("scroll", "Эрлийн самбар", "Дэлгэцийн дээд талын эрлийн зурвас."),
    Card("chart", "Хажуугийн самбар", "Баруун талын SÜLD самбар."),
    Card("book", "Зөвлөмж", "Үе үе гарах зөвлөмжийн мөрүүд."),
    Card("compass", "HUD мэдэгдэл", "Үйлдлийн мөрөн дээрх мэдэгдлүүд."),
    Card("arrows", "Цэсний дуу", "Товч дарахад гарах дуу."),
    Card("swords", "Чатын толгой", "Чат дахь тоглогчийн толгой."),
    Card("map", "Багийн урилга", "Бусдаас багийн урилга авах."),
    Card("chest", "Тулааны урилга", "Бусдаас тулааны урилга авах."),
])

PAGES = [HUB, PROFILE]          # the first glyphs; new pages go to EXTRA_PAGES (code points only ever append)
EXTRA_PAGES = [HELP, RANKUP, SHOP, SHOP2, TRAVEL]
LATER_PAGES = [SETTINGS]        # after the glow glyphs, which came first


def _canvas() -> Image.Image:
    return Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))


def tab_box(i: int) -> tuple[int, int, int, int]:
    """Tab i's tile, HD canvas px."""
    x = SLOT_X + i * ROW
    return cx(x), cy(SLOT_Y + 1), cx(x + TAB_W), cy(SLOT_Y + 1 + TAB_H)


def hole_box(i: int) -> tuple[int, int, int, int]:
    """The slot under card i's well (HD canvas px): a hole in the frame where that slot's real item shows."""
    col, row = i % 2, i // 2
    x = SLOT_X + CARD_COLS[col][0] * ROW
    y = SLOT_Y + (row + 1) * ROW
    return cx(x), cy(y), cx(x + ROW), cy(y + ROW)


def card_box(i: int) -> tuple[int, int, int, int]:
    col, row = i % 2, i // 2
    c0, c1 = CARD_COLS[col]
    y = SLOT_Y + (row + 1) * ROW
    return cx(SLOT_X + c0 * ROW), cy(y + ROW - 1 - CARD_H), cx(SLOT_X + c1 * ROW), cy(y + ROW - 1)


def frame(kit: Kit) -> Image.Image:
    """HUB_FRAME."""
    img = _canvas()
    d = ImageDraw.Draw(img)
    # the band that hides the bag, with a shadow under the window
    d.rectangle([0, cy(BAND_Y), img.width, img.height], fill=BAND + (255,))
    for i in range(_hd(8)):
        t = i / _hd(8)
        c = tuple(int(v * t) for v in BAND)
        d.line([0, cy(PANEL[3]) + i, img.width, cy(PANEL[3]) + i], fill=c + (255,))
    # side banners (over the band, behind the panel)
    for side, b in enumerate(kit.banners):
        bh = cy(142)
        bw = round(b.width * bh / b.height)
        b2 = b.resize((bw, bh), Image.LANCZOS)
        x = cx(PANEL[0] + 3) - bw if side == 0 else cx(PANEL[2] - 3)
        img.alpha_composite(b2, (x, 0))
    # the panel
    img.alpha_composite(kit.panel(), (cx(PANEL[0]), cy(PANEL[1])))
    # the crest, centred on the top rail
    ch = cy(SLOT_Y - 20) - cy(PANEL[1])  # from the top edge to a little above the title
    crest = kit.crest
    cw = round(crest.width * ch / crest.height)
    crest = crest.resize((cw, ch), Image.LANCZOS)
    img.alpha_composite(crest, (round((img.width - cw) / 2), cy(PANEL[1])))
    # the gold lines beside the title
    for side, orn in enumerate(kit.title_orn):
        oh = _hd(8)
        ow = round(orn.width * oh / orn.height)
        o = orn.resize((ow, oh), Image.LANCZOS)
        x = cx(PANEL[0] + 6) if side == 0 else cx(PANEL[2] - 6) - ow
        img.alpha_composite(_keyed(o, 0.3, 0.5, 0), (x, cy(-2)))
    # the tabs: 8 tiles with icon and label, a ninth ornament tile
    for i in range(9):
        bx = tab_box(i)
        img.alpha_composite(kit.tab_off, (bx[0], bx[1]))
        if i < 8:
            icon = kit.tab_icons[TAB_ICONS[i]].resize((9 * S, 9 * S), Image.LANCZOS)
            _paste_centre(img, icon, (bx[0], bx[1] + _hd(1), bx[2], bx[1] + _hd(10)))
            room = (bx[2] - bx[0]) - _hd(1)
            for px, weight in ((12, "bold"), (11, "bold"), (11, "semi"), (10, "semi")):
                lab = letter(TABS[i], px, weight, WHITE, shadow=(0, 0, 0))
                if lab.width <= room:
                    break
            if lab.width > room:
                lab = lab.resize((room, lab.height), Image.LANCZOS)
            _paste_centre(img, lab, (bx[0], bx[1] + _hd(10), bx[2], bx[3] - _hd(0.5)))
        else:
            _paste_centre(img, diamond(_hd(5), GOLD), bx)
            _paste_centre(img, diamond(_hd(2), (190, 40, 40), GOLD_DARK), bx)
    # the close button
    img.alpha_composite(kit.button("Хаах"), (cx(CLOSE[0]), cy(CLOSE[1])))
    # a hole under every card's well: pages with item cards let the slot's item show through
    a = _np(img)
    for i in range(CARDS):
        x0, y0, x1, y1 = hole_box(i)
        a[y0:y1, x0:x1, 3] = 0
    return _img(a)


def card_on(kit: Kit) -> Image.Image:
    """The glow frame over a card (owned rank, chosen setting): the active tab's frame stretched to a card."""
    return hd_art._nine(kit.tab_on, (_hd(3),) * 4, ((CARD_COLS[0][1] - CARD_COLS[0][0]) * ROW * S + 2 * S, (CARD_H + 2) * S), 1.0)


def page(kit: Kit, spec: Spec) -> Image.Image:
    """PAGE_<spec.name>: header text, section strip and the 8 cards; transparent elsewhere."""
    img = _canvas()
    # header: «SÜLD ◆ TITLE», one baseline, SÜLD a little larger like the mock-up
    title_px = _hd(9)
    gold = dict(gradient=(GOLD_LIGHT, GOLD), outline=INK, shadow=(0, 0, 0))
    run = letter_run([("SÜLD", 1.25, gold), ("◆", 0.55, dict(colour=CYAN, outline=CYAN_DARK)), (spec.title, 1.0, gold)],
                     title_px, "display", pixel=3, gap=_hd(3))
    inner_w = (PANEL[2] - PANEL[0] - 20) * S
    if run.width > inner_w:
        run = run.resize((inner_w, round(run.height * inner_w / run.width)), Image.LANCZOS)
    _paste_centre(img, run, (0, cy(-5), img.width, cy(6)))
    if spec.subtitle:
        sub = letter(spec.subtitle, _hd(4.2), "semi", GREY, shadow=(0, 0, 0))
        _paste_centre(img, sub, (0, cy(7.5), img.width, cy(16.5)))
    # the frame has a hole under every card's well: painted cards cover it with cloth, item cards keep it open
    cloth = kit.cloth.resize((ROW * S, ROW * S), Image.LANCZOS)
    for i in range(CARDS):
        hx0, hy0, _, _ = hole_box(i)
        img.alpha_composite(cloth, (hx0, hy0))
    item_page = any(c.icon is None for c in spec.cards)
    # section strip (on item pages it starts right of the first well's hole and the pill stops before the second)
    sy0, sy1 = cy(SECTION_Y[0]) + _hd(0.5), cy(SECTION_Y[1])
    sx = SLOT_X + (ROW + 1 if item_page else 0)
    icon = kit.section_icon.resize((_hd(5), round(kit.section_icon.height * _hd(5) / kit.section_icon.width)), Image.LANCZOS)
    _paste_left(img, icon, cx(sx), (sy0, sy1))
    sec = letter(spec.section, _hd(4.4), "bold", gradient=(GOLD_LIGHT, GOLD), outline=INK)
    _paste_left(img, sec, cx(sx + 6), (sy0, sy1))
    _paste_left(img, diamond(_hd(2), GOLD, GOLD_DARK), cx(sx + 6) + sec.width + _hd(1.5), (sy0, sy1))
    pill = kit.pill(spec.pill, _hd(46 if item_page else 64), _hd(4.5))
    img.alpha_composite(pill, (cx(SLOT_X + 9 * ROW) - pill.width, round((sy0 + sy1 - pill.height) / 2)))
    holes = []
    for i, c in enumerate(spec.cards[:8]):
        bx = card_box(i)
        img.alpha_composite(kit.card, (bx[0], bx[1]))
        if c.icon is None:
            holes.append(i)
            tx = bx[0] + _hd(ROW + 1)
        else:
            well = kit.wells[c.icon] if isinstance(c.icon, str) else c.icon
            if not isinstance(c.icon, str):
                well = well.resize((round(well.width * 6 * S / well.height), 6 * S), Image.LANCZOS)
            _paste_left(img, well, bx[0] + _hd(2), (bx[1], bx[3]))
            tx = bx[0] + _hd(2) + well.width + _hd(2)
        t = letter(c.title, _hd(4.6), "bold", CYAN, shadow=(0, 0, 0), pixel=2)
        img.alpha_composite(t, (tx, bx[1] + _hd(0.6)))
        s = letter(c.subtitle, _hd(3.2), "semi", GREY, shadow=(0, 0, 0))
        maxw = bx[2] - _hd(10) - tx
        if s.width > maxw:
            s = s.resize((maxw, s.height), Image.LANCZOS)
        img.alpha_composite(s, (tx, bx[1] + _hd(6.6)))
        _paste_centre(img, kit.chevron, (bx[2] - _hd(7), bx[1], bx[2] - _hd(2), bx[3]))
    if holes:
        a = _np(img)
        for i in holes:
            x0, y0, x1, y1 = hole_box(i)
            a[y0:y1, x0:x1, 3] = 0
        img = _img(a)
    return img


# ------------------------------------------------------------------------------------------- sprites

def button_sprites(mc_dir: str) -> None:
    """The vanilla widget buttons (dialogs, the Esc menu) in the window's look: navy cloth, gold rim; highlighted =
    cyan rim; disabled = grey. Nine-slice 200 x 20 GUI px, border 3, drawn at SCALE."""
    root = os.path.join(mc_dir, "textures", "gui", "sprites", "widget")
    os.makedirs(root, exist_ok=True)
    w, h, b = 200 * S, 20 * S, 3 * S
    for name, rim, fill_top, fill_bot in (("button", GOLD, (30, 40, 78), (14, 20, 44)),
                                          ("button_highlighted", CYAN, (36, 54, 96), (18, 28, 56)),
                                          ("button_disabled", (96, 98, 110), (28, 30, 40), (18, 20, 28))):
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        for y in range(h):
            t = y / (h - 1)
            c = tuple(int(fill_top[k] * (1 - t) + fill_bot[k] * t) for k in range(3))
            d.line([0, y, w, y], fill=c + (255,))
        d.rectangle([0, 0, w - 1, h - 1], outline=INK + (255,), width=S)
        d.rectangle([S, S, w - 1 - S, h - 1 - S], outline=rim + (255,), width=S)
        d.rectangle([2 * S, 2 * S, w - 1 - 2 * S, h - 1 - 2 * S], outline=tuple(int(v * 0.55) for v in rim) + (255,), width=1)
        for (px, py) in ((b, b), (w - b, b), (b, h - b), (w - b, h - b)):
            d.polygon([(px, py - S), (px + S, py), (px, py + S), (px - S, py)], fill=rim + (255,))
        img.save(os.path.join(root, name + ".png"), optimize=False)
        with open(os.path.join(root, name + ".png.mcmeta"), "w", encoding="utf-8") as fh:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": 200, "height": 20, "border": 3}}}, fh, indent=1)
            fh.write("\n")


def slot_highlight(mc_dir: str) -> None:
    """The hovered slot's highlight (24 x 24 GUI px around the 18 px slot): a soft gold glow behind the item and a
    thin gold rim in front, instead of the vanilla white square."""
    root = os.path.join(mc_dir, "textures", "gui", "sprites", "container")
    os.makedirs(root, exist_ok=True)
    n = 24 * S
    yy, xx = np.mgrid[0:n, 0:n]
    c = (n - 1) / 2
    r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / (n / 2)
    glow = np.clip(1 - r, 0, 1) ** 1.6 * 90
    back = np.dstack([np.full((n, n), 255.0), np.full((n, n), 214.0), np.full((n, n), 120.0), glow])
    _img(back).save(os.path.join(root, "slot_highlight_back.png"), optimize=False)
    front = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(front)
    m = 3 * S
    d.rectangle([m, m, n - 1 - m, n - 1 - m], outline=GOLD + (150,), width=S)
    front.save(os.path.join(root, "slot_highlight_front.png"), optimize=False)


# ------------------------------------------------------------------------------------------- build

def preview_compose(frame_img: Image.Image, page_img: Image.Image, tab_on: Image.Image | None, active: int,
                    chest_png: str | None) -> Image.Image:
    """What the client shows: the chest texture under the glyphs (where they are transparent)."""
    out = Image.new("RGBA", frame_img.size, (28, 30, 40, 255))
    if chest_png and os.path.exists(chest_png):
        chest = Image.open(chest_png).convert("RGBA").crop((0, 0, 176 * S, 222 * S))
        out.alpha_composite(chest, (cx(0), cy(0)))
    out.alpha_composite(frame_img)
    out.alpha_composite(page_img)
    if tab_on is not None and active >= 0:
        bx = tab_box(active)
        out.alpha_composite(tab_on, (bx[0] - S, bx[1] - S))
    return out


def build(add_bitmap, preview: str | None, mc_dir: str) -> None:
    """Write every hub glyph (appended after the older ones), the widget/slot sprites, and the previews."""
    if not os.path.exists(SRC):
        return
    kit = Kit(SRC)
    ascent = 13 - Y0                 # glyph top = title y (6) + 7 - ascent = Y0
    fr = frame(kit)
    add_bitmap("HUB_FRAME", fr, "gui/hub_frame", H, ascent, f"Командын Өргөө frame {W}x{H} at x{X0} (HD x4): banners, tabs, close")
    pages = {}
    for spec in PAGES:
        pg = page(kit, spec)
        pages[spec.name] = pg
        add_bitmap(f"PAGE_{spec.name.upper()}", pg, f"gui/hub_page_{spec.name}", H, ascent, f"hub page {spec.name} {W}x{H} (HD x4)")
    tab_on = kit.tab_on
    add_bitmap("HUB_TAB_ON", tab_on, "gui/hub_tab_on", TAB_H + 2, 13 - SLOT_Y, f"active tab tile {TAB_W + 2}x{TAB_H + 2} at the tab's slot (HD x4)")
    back = kit.button("Буцах", (28, 44, 92), (BACK[2] - BACK[0], BACK[3] - BACK[1]), 20, mark="chevrons")
    add_bitmap("HUB_BACK", back, "gui/hub_back", BACK[3] - BACK[1], 13 - BACK[1], f"footer «Буцах» button {BACK[2] - BACK[0]}x{BACK[3] - BACK[1]} at slot 0 (HD x4)")
    prev_, next_ = kit.arrow(False), kit.arrow(True)
    add_bitmap("HUB_PREV", prev_, "gui/hub_prev", 14, 13 - ARROW[0][1], "footer paging arrow « 16x14 at slot 7 (HD x4)")
    add_bitmap("HUB_NEXT", next_, "gui/hub_next", 14, 13 - ARROW[1][1], "footer paging arrow » 16x14 at slot 8 (HD x4)")
    for spec in EXTRA_PAGES:
        pg = page(kit, spec)
        pages[spec.name] = pg
        add_bitmap(f"PAGE_{spec.name.upper()}", pg, f"gui/hub_page_{spec.name}", H, ascent, f"hub page {spec.name} {W}x{H} (HD x4)")
    glow = card_on(kit)
    for row in range(4):
        top = SLOT_Y + (row + 1) * ROW + ROW - 1 - CARD_H - 1       # one px above the card tile
        # one texture, four providers (only the ascent differs per row)
        add_bitmap(f"HUB_CARD_ON_R{row + 1}", glow, "gui/hub_card_on", CARD_H + 2, 13 - top,
                   f"glow frame over a card of row {row + 1} (owned rank, chosen setting), 74x14 (HD x4)")
    for spec in LATER_PAGES:
        pg = page(kit, spec)
        pages[spec.name] = pg
        add_bitmap(f"PAGE_{spec.name.upper()}", pg, f"gui/hub_page_{spec.name}", H, ascent, f"hub page {spec.name} {W}x{H} (HD x4)")
    button_sprites(mc_dir)
    slot_highlight(mc_dir)
    if preview:
        chest = os.path.join(mc_dir, "textures", "gui", "container", "generic_54.png")
        for spec, active in ((HUB, -1), (PROFILE, 0), (HELP, 7), (RANKUP, 4), (SHOP, 3), (TRAVEL, 5), (SETTINGS, 6)):
            comp = preview_compose(fr, pages[spec.name], tab_on, active, chest)
            if spec is RANKUP:
                for i in range(3):
                    bx = card_box(i)
                    comp.alpha_composite(glow, (bx[0] - S, bx[1] - S))
            if spec is PROFILE:
                comp.alpha_composite(back, (cx(BACK[0]), cy(BACK[1])))
                comp.alpha_composite(prev_, (cx(ARROW[0][0]), cy(ARROW[0][1])))
                comp.alpha_composite(next_, (cx(ARROW[1][0]), cy(ARROW[1][1])))
            comp.save(os.path.join(preview, f"hub_{spec.name}.png"))
            comp.resize((W * 2, H * 2), Image.BOX).save(os.path.join(preview, f"hub_{spec.name}_x2.png"))


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", required=True)
    args = ap.parse_args()
    os.makedirs(args.preview, exist_ok=True)
    tmp = os.path.join(args.preview, "rp")

    def _add(name, img, rel, height, ascent, comment):
        p = os.path.join(tmp, rel + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        img.save(p)
        print(name, img.size, height, ascent)

    build(_add, args.preview, os.path.join(tmp, "mc"))
