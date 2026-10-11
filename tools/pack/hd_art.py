"""HD textures from the owner's art (assets/art/source), for gen_ui.py.

The client draws a bitmap font glyph at its provider's ``height`` in GUI pixels whatever the texture's size, so a
texture SCALE times larger than the glyph shows SCALE times the detail: at GUI scale 4 every texture pixel is one
screen pixel. Each function returns images SCALE times the size the font entry declares; gen_ui keeps the entry's
height and ascent.

* ``gui_questbook.webp`` (1483x1061): the quest book window, Акт I active. Warped so its wells sit on the chest slots;
  the other two tab states are made from it (tab backgrounds and labels swapped).
* ``badges.webp`` (667x2000): 23 pills, rank, staff, class and misc badges top to bottom.
* ``tooltip_badges.webp`` (1708x920): the tooltip frame, six quest pills and the mouse icon.

Deterministic: the same sources give byte-identical PNGs.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageFilter

SCALE = 4


def _interp(points, v):
    dst = [d for _, d in points]
    src = [s for s, _ in points]
    return np.interp(v, dst, src)


def warp(src: Image.Image, xs: list, xs_bar: list, ys: list, bar_top: float, width: int, height: int,
         scale: int = SCALE) -> Image.Image:
    """Resample ``src`` onto a width x height chest (times ``scale``) through per-axis control points (source px ->
    chest px); rows at or below ``bar_top`` (chest px) use ``xs_bar``. Each output pixel averages the source area it
    covers (box filter through an integral image)."""
    a = np.asarray(src.convert("RGBA")).astype(np.float64)
    H, W = a.shape[:2]
    ii = np.zeros((H + 1, W + 1, 4))
    ii[1:, 1:] = a.cumsum(0).cumsum(1)
    ow, oh = width * scale, height * scale
    ty = np.arange(oh + 1) / scale
    tx = np.arange(ow + 1) / scale
    yb = np.clip(np.rint(_interp(ys, ty)).astype(int), 0, H)
    out = np.zeros((oh, ow, 4))
    for xm, rows in ((xs, ty[:-1] < bar_top), (xs_bar, ty[:-1] >= bar_top)):
        if not rows.any():
            continue
        xb = np.clip(np.rint(_interp(xm, tx)).astype(int), 0, W)
        r0 = yb[:-1][rows]
        r1 = np.maximum(yb[1:][rows], r0 + 1).clip(max=H)
        r0 = np.minimum(r0, r1 - 1)
        c0 = xb[:-1]
        c1 = np.maximum(xb[1:], c0 + 1).clip(max=W)
        c0 = np.minimum(c0, c1 - 1)
        R0, C0 = np.meshgrid(r0, c0, indexing="ij")
        R1, C1 = np.meshgrid(r1, c1, indexing="ij")
        tot = ii[R1, C1] - ii[R0, C1] - ii[R1, C0] + ii[R0, C0]
        out[rows] = tot / ((R1 - R0) * (C1 - C0))[..., None]
    out[..., 3] = 255
    return Image.fromarray(np.rint(out).clip(0, 255).astype(np.uint8), "RGBA")


def shrink(img: Image.Image, height: int) -> Image.Image:
    """Box-filtered downscale to ``height`` px, keeping the aspect."""
    w = max(1, round(img.width * height / img.height))
    return img.resize((w, height), Image.BOX)


# ------------------------------------------------------------------------------------------------ quest book

# source boxes of the three tabs (x0, y0, x1, y1), of the text inside each, and a clean strip of the tab's own fill
QB_TABS = [(44, 124, 506, 247), (521, 126, 975, 245), (992, 126, 1451, 245)]
QB_TEXT = [(150, 150, 420, 222), (620, 150, 880, 222), (1060, 150, 1390, 222)]
QB_CLEAN = [(100, 152), (575, 618), (1045, 1068)]  # x ranges between the corner frets and the label
# well centres in the source and the chest slot centres they map to
QB_COLS = [124, 279, 433.5, 590.5, 744, 897.5, 1052.5, 1207.5, 1359.5]
QB_BAR = [152 + 146 * c for c in range(9)]
QB_ROWS = [(185, 26), (328, 44), (475, 62), (622, 80), (768, 98), (949, 116)]


def _blank(a: np.ndarray, tab: int) -> np.ndarray:
    """The tab's background with its label painted over by repeating a clean vertical strip of its own fill."""
    x0, y0, x1, y1 = QB_TEXT[tab]
    c0, c1 = QB_CLEAN[tab]
    b = a.copy()
    strip = a[y0:y1, c0:c1]
    for x in range(x0, x1):
        b[y0:y1, x] = strip[:, (x - x0) % (c1 - c0)]
    tx0, ty0, tx1, ty1 = QB_TABS[tab]
    return b[ty0:ty1, tx0:tx1]


def _label_mask(a: np.ndarray, tab: int, dark: bool) -> np.ndarray:
    """Alpha (0..1) of the tab's label: dark letters on the gold tab, light letters on the blue ones."""
    x0, y0, x1, y1 = QB_TEXT[tab]
    lum = a[y0:y1, x0:x1, :3].mean(axis=2)
    m = (90 - lum) / 50 if dark else (lum - 120) / 60
    return m.clip(0, 1)


def _paste(dst: np.ndarray, img: np.ndarray, box) -> None:
    x0, y0, x1, y1 = box
    im = Image.fromarray(img.astype(np.uint8)).resize((x1 - x0, y1 - y0), Image.BICUBIC)
    dst[y0:y1, x0:x1] = np.asarray(im).astype(np.float64)


def questbook(path: str, height: int) -> list[Image.Image]:
    """The three tab states of the quest book background, each 176 x height chest px times SCALE."""
    src = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
    gold_blank, navy_blank = _blank(src, 0), _blank(src, 1)
    labels = [_label_mask(src, 0, True), _label_mask(src, 1, False), _label_mask(src, 2, False)]
    ink_dark, ink_light = np.array([34, 22, 8.0]), np.array([242, 226, 182.0])
    xs = [(0, 0)] + [(s, 16 + 18 * c) for c, s in enumerate(QB_COLS)] + [(src.shape[1], 176)]
    xs_bar = [(0, 0)] + [(s, 16 + 18 * c) for c, s in enumerate(QB_BAR)] + [(src.shape[1], 176)]
    ys = [(0, 0), (112, 17)] + list(QB_ROWS) + [(src.shape[0], height)]
    out = []
    for active in range(3):
        a = src.copy()
        for t in range(3):
            on = t == active
            _paste(a, gold_blank if on else navy_blank, QB_TABS[t])
            x0, y0, x1, y1 = QB_TABS[t]
            tx0, ty0, tx1, ty1 = QB_TEXT[t]
            m = labels[t]
            # centre the label in its tab (the source labels are already centred in their own tab)
            w, h = m.shape[1], m.shape[0]
            cx, cy = (x0 + x1) // 2 - w // 2, ty0
            ink = ink_dark if on else ink_light
            region = a[cy:cy + h, cx:cx + w]
            region[:] = region * (1 - m[..., None]) + ink * m[..., None]
        out.append(warp(Image.fromarray(a.astype(np.uint8)), xs, xs_bar, ys, 107, 176, height))
    return out


# ------------------------------------------------------------------------------------------------ badges

def _rows_of(mask: np.ndarray, min_len: int) -> list[tuple[int, int]]:
    rows = mask.any(axis=1)
    out, start = [], None
    for i, v in enumerate(list(rows) + [False]):
        if v and start is None:
            start = i
        if not v and start is not None:
            if i - start >= min_len:
                out.append((start, i))
            start = None
    return out


def _crop_pill(a: np.ndarray, y0: int, y1: int, bg: np.ndarray, x_from: int = 0, x_to: int | None = None) -> Image.Image:
    """One pill out of a sheet: its bounding box on the sheet's background colour, background made transparent."""
    sub = a[y0:y1, x_from:x_to]
    diff = np.abs(sub[..., :3] - bg).sum(axis=2)
    cols = np.where((diff > 60).any(axis=0))[0]
    c0, c1 = cols[0], cols[-1] + 1
    piece = sub[:, c0:c1]
    d = np.abs(piece[..., :3] - bg).sum(axis=2)
    alpha = ((d - 30) / 50).clip(0, 1) * 255
    rgba = np.dstack([piece[..., :3], alpha])
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


def rank_badges(path: str) -> list[Image.Image]:
    """The 23 pills of the badge sheet, top to bottom, at full source size."""
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
    bg = np.median(a[:, :40].reshape(-1, 3), axis=0)
    mask = np.abs(a - bg).sum(axis=2) > 60
    return [_crop_pill(a, y0, y1, bg) for y0, y1 in _rows_of(mask, 20)]


def tooltip_sheet(path: str) -> dict:
    """The frame (with its blue inside), the six quest pills and the mouse icon of the tooltip sheet."""
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
    bg = np.median(a[400:500, 900:940].reshape(-1, 3), axis=0)
    pills_mask = np.abs(a[:, 940:1500] - bg).sum(axis=2) > 80
    pills = [_crop_pill(a, y0, y1, bg, 940, 1500) for y0, y1 in _rows_of(pills_mask, 40)]
    mouse = _crop_pill(a, 120, 230, bg, 1530, 1640)
    return {"frame": Image.fromarray(a[60:830, 70:890].astype(np.uint8)), "pills": pills, "mouse": mouse}


def reletter(pill: Image.Image, text: str, height: int = 9 * SCALE) -> Image.Image:
    """A pill from the sheet with its label replaced (the sheet misspells some labels): the old label is painted
    over with the pill's own fill (each row the median colour of a clean strip by the left end cap), then ``text`` is
    drawn in the pixel font, white with a dark drop shadow, at the sheet's letter size. Returns the pill at
    ``height`` px."""
    import pixelfont as pf
    a = np.asarray(pill.convert("RGBA")).astype(np.float64)
    h, w = a.shape[:2]
    c0, c1 = int(w * 0.05), int(w * 0.09)
    x0, x1 = c1, int(w * 0.95)
    y0, y1 = int(h * 0.18), int(h * 0.82)
    row = np.median(a[y0:y1, c0:c1], axis=1)
    a[y0:y1, x0:x1] = row[:, None, :]
    k = max(1, round(h * 0.52 / 7))  # the sheet's letters are about half the pill's height
    fill = row[len(row) // 2, :3]
    shadow = tuple(int(v * 0.3) for v in fill)
    ink = Image.new("RGBA", ((pf.text_width(text) + 1) * k + k, 8 * k), (0, 0, 0, 0))
    for colour, off in ((shadow, k // 2 + 1), ((250, 250, 250), 0)):
        tmp = Image.new("RGBA", (pf.text_width(text) + 1, 8), (0, 0, 0, 0))
        pf.draw_text(tmp, 0, 0, text, colour)
        big = tmp.resize((tmp.width * k, tmp.height * k), Image.NEAREST)
        # heavier stems like the sheet's letters, without the bold font's doubled columns (which close up Ю, Ж)
        big = Image.fromarray(np.maximum(np.asarray(big), np.roll(np.asarray(big), k // 3, axis=1)), "RGBA")
        ink.alpha_composite(big, (off, off))
    img = Image.fromarray(a.astype(np.uint8), "RGBA")
    img.alpha_composite(ink, ((w - ink.width) // 2 + k // 2, (h - 7 * k) // 2))
    return shrink(img, height)


# the tooltip frame on the tooltip sheet: its outer box (gems included) and the gold line's box
TT_BOX = (78, 74, 882, 816)
TT_LINE = (104, 101, 856, 788)
TT_SIZE = (40, 37)  # GUI px of the nine-slice sprite (texture SCALE times larger)
TT_BORDER = 12      # the client draws the sprite 12 px (9 margin + 3 padding) around the text


def tooltip_frame(path: str) -> Image.Image:
    """The tooltip background sprite: the frame with its blue inside, the sheet's backdrop made transparent outside
    the gold line (the corner gems stay). TT_SIZE times SCALE px."""
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
    bg = np.median(a[400:500, 900:940].reshape(-1, 3), axis=0)
    x0, y0, x1, y1 = TT_BOX
    sub = a[y0:y1, x0:x1]
    d = np.abs(sub - bg).sum(axis=2)
    alpha = ((d - 40) / 60).clip(0, 1)
    lx0, ly0, lx1, ly1 = TT_LINE
    alpha[ly0 - y0:ly1 - y0, lx0 - x0:lx1 - x0] = 1.0
    rgba = np.dstack([sub, alpha * 255]).astype(np.uint8)
    w, h = TT_SIZE
    return Image.fromarray(rgba, "RGBA").resize((w * SCALE, h * SCALE), Image.BOX)


# ------------------------------------------------------------------------------------------------ inventory (E)

# gui_inventory_keyed.png (2048 px square, tools/art/inventory_prompts.json): the owner's inventory mock-up with the
# scene keyed to magenta and the player taken out of the portrait. Source px -> vanilla inventory px (176 x 166).
INV_PANEL = (205, 300, 1845, 1850)                     # the window's outer rim
INV_COL_START = [278, 445, 612, 780, 948, 1119, 1287, 1457, 1625]   # wells of the 9 inventory columns
INV_COL_END = [423, 590, 759, 927, 1098, 1265, 1435, 1603, 1770]
INV_YS = [(300, 0), (385, 5), (1075, 81), (1103, 84), (1247, 100), (1268, 102), (1413, 118), (1434, 120),
          (1581, 136), (1657, 142), (1805, 158), (1850, 166)]
INV_RIM_XS = [(205, 0), (240, 3), (1815, 173), (1845, 176)]
INV_CELL = (278, 1103, 423, 1247)                      # a clean well (row 1, column 1), reused for armour/off-hand
INV_ARMOUR = [(281, 440, 415, 574), (281, 599, 415, 731), (281, 756, 414, 891), (281, 917, 415, 1049)]
INV_SHIELD = (1023, 930, 1153, 1057)
INV_PORTRAIT = (443, 410, 988, 1062)
INV_CRAFT = (1072, 540, 1445, 900, [(1072, 96), (1096, 98), (1248, 114), (1273, 116), (1426, 132), (1445, 134)],
             [(540, 16), (559, 18), (706, 34), (728, 36), (875, 52), (900, 54)])
INV_RESULT = (1608, 622, 1798, 830, [(1608, 153), (1623, 154), (1782, 170), (1798, 171)],
              [(622, 27), (637, 28), (814, 44), (830, 45)])
INV_ARROW = ((1455, 655, 1600, 795), (134, 29, 151, 43))
# ornaments moved into the free parts of the vanilla layout: (source box, gui box)
INV_ORNAMENTS = [((1175, 905, 1805, 1050), (96, 60, 172, 80)),
                 ((1590, 440, 1805, 610), (137, 6, 172, 26)),
                 ((1030, 450, 1160, 505), (100, 8, 114, 14))]
INV_NAVY_PATCH = (1180, 400, 1440, 520)               # plain panel cloth, tiled behind the top half
# the decor glyph: the whole keyed sheet mapped like the rim; inside the window only these boxes (the corner
# knots, the crest, the bottom tassels) may draw, and only in the 7 px margin around the slots
INV_DECOR_BOXES = [(110, 210, 320, 420), (1620, 210, 1830, 420), (860, 0, 1090, 410),
                   (130, 1790, 340, 2048), (1700, 1790, 1920, 2048), (840, 1820, 1090, 2048)]


def _gui_of(sx: float, sy: float) -> tuple[float, float]:
    x0, y0, x1, y1 = INV_PANEL
    return (sx - x0) * 176 / (x1 - x0), (sy - y0) * 166 / (y1 - y0)


def _keyed_alpha(a: np.ndarray) -> np.ndarray:
    """Alpha of the keyed sheet: magenta (and the magenta haze Gemini left at its edges) is transparent, and the
    bit of grass left under the bottom tassels is dropped by colour (only gold, blue cloth and dark outline stay)."""
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    m = np.minimum(r, b) - g                         # how magenta a pixel is
    alpha = np.clip((110 - m) / 70.0, 0, 1)
    h = a.shape[0]
    low = np.zeros(r.shape, bool)
    low[int(h * 1835 / 2048):] = True                # below the window's bottom rim
    gold = (r > g + 12) & (r > b + 28) & (r > 70)
    blue = (b > r + 25) & (b > g + 8)
    dark = (r + g + b) < 120
    keep = gold | blue | dark
    alpha[low & ~keep] = 0
    return alpha


def _box(img: Image.Image, src_box, size) -> Image.Image:
    return img.crop(src_box).resize(size, Image.LANCZOS)


def _warp_box(src: Image.Image, box, xs, ys, scale: int = SCALE) -> tuple[Image.Image, tuple[int, int]]:
    """warp() of one element: source box -> its gui rectangle given by the control points."""
    x0, y0, x1, y1 = box
    gx0, gy0 = xs[0][1], ys[0][1]
    w, h = xs[-1][1] - gx0, ys[-1][1] - gy0
    crop = src.crop(box)
    xs_ = [(s - x0, g - gx0) for s, g in xs]
    ys_ = [(s - y0, g - gy0) for s, g in ys]
    return warp(crop, xs_, xs_, ys_, h + 1, w, h, scale), (gx0, gy0)


def _cell(src: Image.Image, scale: int = SCALE) -> Image.Image:
    """One 18 x 18 slot: a clean well with one gui pixel of its frame around."""
    x0, y0, x1, y1 = INV_CELL
    px = (x1 - x0) / 16.0
    box = (round(x0 - px), round(y0 - px), round(x1 + px), round(y1 + px))
    return _box(src, box, (18 * scale, 18 * scale))


def inventory_cell(path: str, scale: int = SCALE) -> Image.Image:
    """One gold-framed slot well (18 x 18 chest px times SCALE) from the keyed inventory art, for other menus."""
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
    return _cell(Image.fromarray(a.astype(np.uint8), "RGB"), scale).convert("RGBA")


def _icon(src: Image.Image, box, scale: int = SCALE) -> Image.Image:
    """An empty-slot placeholder (helmet, chestplate, leggings, boots, shield) lifted off its well: the brightness
    above the well's own cloth becomes alpha, tinted with the icon's colour."""
    a = np.asarray(src.crop(box).resize((16 * scale, 16 * scale), Image.LANCZOS).convert("RGB")).astype(np.float64)
    lum = a.mean(axis=2)
    bg = np.percentile(lum, 30)
    top = np.percentile(lum, 98)
    alpha = np.clip((lum - bg - 6) / max(1.0, top - bg - 6), 0, 1)
    edge = 2 * scale                                  # the well's frame is not part of the icon
    alpha[:edge, :] = alpha[-edge:, :] = 0
    alpha[:, :edge] = alpha[:, -edge:] = 0
    tint = np.array([150, 162, 196.0])
    rgb = np.clip(a * 0.35 + tint * 0.65, 0, 255)
    out = np.dstack([rgb, alpha * 150])              # faint, like the mock-up's empty-slot marks
    return Image.fromarray(np.rint(out).astype(np.uint8), "RGBA")


def inventory(path: str) -> dict:
    """The survival inventory in SÜLD's style.

    Returns ``texture`` (176 x 166 times SCALE, for textures/gui/container/inventory.png; every slot frame sits
    exactly on the vanilla slot), ``decor`` (the spires, crest, banners and tassels around the window, a font glyph
    drawn through the screen's title), ``decor_box`` (its gui rectangle relative to the window) and ``icons``
    (empty armour / off-hand slot placeholders, 16 x 16 times SCALE)."""
    sheet = Image.open(path).convert("RGB")
    a = np.asarray(sheet).astype(np.float64)
    alpha = _keyed_alpha(a)
    # magenta haze off the edges: pull the colour towards the pixel's own non-magenta part
    spill = np.clip(np.minimum(a[..., 0], a[..., 2]) - a[..., 1] - 40, 0, None)[..., None] / 1.6
    clean = np.clip(a - np.concatenate([spill, np.zeros_like(spill), spill], axis=2), 0, 255)
    src = Image.fromarray(clean.astype(np.uint8), "RGB")
    S = SCALE

    # 1. the window: rim everywhere, the inventory and hot bar (one warp, column by column)
    grid_xs = [(205, 0), (240, 3)]
    for c in range(9):
        grid_xs += [(INV_COL_START[c], 8 + 18 * c), (INV_COL_END[c], 24 + 18 * c)]
    grid_xs += [(1815, 173), (1845, 176)]
    win = np.asarray(warp(src, INV_RIM_XS, grid_xs, INV_YS, 81, 176, 166)).astype(np.float64)
    # the very top row samples the keyed magenta just above the rim: take the row under it instead
    win[0] = win[1]

    # 2. the top half: plain cloth, then every element on its vanilla rectangle
    patch = np.asarray(src.crop(INV_NAVY_PATCH).resize(((INV_NAVY_PATCH[2] - INV_NAVY_PATCH[0]) * S * 18 // 160,
                                                        (INV_NAVY_PATCH[3] - INV_NAVY_PATCH[1]) * S * 18 // 160),
                                                       Image.LANCZOS).convert("RGBA")).astype(np.float64)
    soft = np.asarray(Image.fromarray(patch.astype(np.uint8), "RGBA").filter(ImageFilter.GaussianBlur(3 * S))).astype(np.float64)
    patch = patch * 0.45 + soft * 0.55                # the cloth's weave, quieter: it fills a large area
    tile = np.concatenate([patch, patch[:, ::-1]], axis=1)
    tile = np.concatenate([tile, tile[::-1]], axis=0)
    x0, y0, x1, y1 = 3 * S, 5 * S, 173 * S, 81 * S
    reps = (-(-(y1 - y0) // tile.shape[0]), -(-(x1 - x0) // tile.shape[1]), 1)
    win[y0:y1, x0:x1] = np.tile(tile, reps)[:y1 - y0, :x1 - x0]
    big = (-(-166 * S // tile.shape[0]), -(-176 * S // tile.shape[1]), 1)
    cloth = Image.fromarray(np.rint(np.tile(tile, big)[:166 * S, :176 * S]).clip(0, 255).astype(np.uint8), "RGBA")
    out = Image.fromarray(np.rint(win).clip(0, 255).astype(np.uint8), "RGBA")

    def put(img: Image.Image, gx: float, gy: float) -> None:
        out.alpha_composite(img.convert("RGBA"), (round(gx * S), round(gy * S)))

    for (sb, gb) in INV_ORNAMENTS:
        # the ornament with the cloth it was painted on, faded in over 3 gui px so no edge shows
        piece = np.asarray(_box(src, sb, ((gb[2] - gb[0]) * S, (gb[3] - gb[1]) * S)).convert("RGBA")).astype(np.float64)
        ph, pw = piece.shape[:2]
        fy = np.clip(np.minimum(np.arange(ph), np.arange(ph)[::-1]) / (3.0 * S), 0, 1)
        fx = np.clip(np.minimum(np.arange(pw), np.arange(pw)[::-1]) / (3.0 * S), 0, 1)
        piece[..., 3] = 255 * np.outer(fy, fx)
        put(Image.fromarray(np.rint(piece).astype(np.uint8), "RGBA"), gb[0], gb[1])
    put(_box(src, INV_PORTRAIT, (51 * S, 72 * S)), 25, 7)
    cell = _cell(src)
    for i in range(4):
        put(cell, 7, 7 + 18 * i)                     # armour (icons are the slot sprites)
    put(cell, 76, 61)                                # off hand
    craft, at = _warp_box(src, INV_CRAFT[:4], INV_CRAFT[4], INV_CRAFT[5])
    put(craft, *at)
    res, at = _warp_box(src, INV_RESULT[:4], INV_RESULT[4], INV_RESULT[5])
    put(res, *at)
    (sb, gb) = INV_ARROW
    arrow = _box(src, sb, ((gb[2] - gb[0]) * S, (gb[3] - gb[1]) * S)).convert("RGBA")
    aa = np.asarray(arrow).astype(np.float64)
    aa[..., 3] = np.clip(((aa[..., 0] - aa[..., 2]) - 30) / 50.0, 0, 1) * 255
    put(Image.fromarray(aa.astype(np.uint8), "RGBA"), gb[0], gb[1])
    texture = out

    # 3. the decor glyph: the whole sheet, mapped like the rim (one scale), transparent over the window
    gx0, gy0 = _gui_of(0, 0)
    gx1, gy1 = _gui_of(a.shape[1], a.shape[0])
    left, top = int(np.floor(gx0)), int(np.floor(gy0))
    w, h = int(np.ceil(gx1)) - left, int(np.ceil(gy1)) - top
    px0, py0, px1, py1 = INV_PANEL
    sx = lambda g: px0 + g * (px1 - px0) / 176
    sy = lambda g: py0 + g * (py1 - py0) / 166
    rgba = np.dstack([clean, alpha * 255]).astype(np.uint8)
    full = Image.fromarray(rgba, "RGBA")
    crop_box = (sx(left), sy(top), sx(left + w), sy(top + h))
    decor = full.transform((w * S, h * S), Image.EXTENT, crop_box, Image.BICUBIC)
    d = np.asarray(decor).astype(np.float64)
    gy, gx = np.mgrid[0:h * S, 0:w * S] / S
    gx = gx + left
    gy = gy + top
    inside = (gx >= 0) & (gx < 176) & (gy >= 0) & (gy < 166)
    margin = (gx < 7) | (gx >= 169) | (gy < 7) | (gy >= 159)
    allowed = np.zeros(inside.shape, bool)
    for bx0, by0, bx1, by1 in INV_DECOR_BOXES:
        ax0, ay0 = _gui_of(bx0, by0)
        ax1, ay1 = _gui_of(bx1, by1)
        allowed |= (gx >= ax0) & (gx < ax1) & (gy >= ay0) & (gy < ay1)
    d[..., 3][inside & ~(allowed & margin)] = 0
    decor = Image.fromarray(np.rint(d).clip(0, 255).astype(np.uint8), "RGBA")

    names = ["helmet", "chestplate", "leggings", "boots"]
    icons = {n: _icon(src, INV_ARMOUR[i]) for i, n in enumerate(names)}
    icons["shield"] = _icon(src, INV_SHIELD)
    return {"texture": texture, "decor": decor, "decor_box": (left, top, w, h), "icons": icons, "cloth": cloth}


def chest(path: str) -> Image.Image:
    """minecraft:textures/gui/container/generic_54.png in the inventory's style (176 x 222 times SCALE): the chest
    rows (y 0..125) with a gold well on every slot, the player's inventory and hot bar (y 126..222) taken from the
    survival inventory, whose rows sit exactly 56 px higher (84 -> 140, 142 -> 198)."""
    r = inventory(path)
    S = SCALE
    inv, cloth = r["texture"], r["cloth"]
    out = Image.new("RGBA", (176 * S, 222 * S), (0, 0, 0, 0))
    out.paste(cloth, (0, 0))
    out.paste(cloth.crop((0, 0, 176 * S, 56 * S)), (0, 166 * S))
    # the rim: the survival inventory's top band (its corners, and a plain stretch of it between the corner and the
    # crest stretched across) and a plain stretch of its side strips (beside the inventory rows) over the chest rows
    band = inv.crop((0, 0, 176 * S, 5 * S))
    out.alpha_composite(band.crop((0, 0, 7 * S, 5 * S)), (0, 0))
    out.alpha_composite(band.crop((30 * S, 0, 60 * S, 5 * S)).resize((162 * S, 5 * S), Image.LANCZOS), (7 * S, 0))
    out.alpha_composite(band.crop((169 * S, 0, 176 * S, 5 * S)), (169 * S, 0))
    for x0 in (0, 169):
        side = inv.crop((x0 * S, 100 * S, (x0 + 7) * S, 130 * S)).resize((7 * S, 132 * S), Image.LANCZOS)
        out.alpha_composite(side, (x0 * S, 5 * S))
    # player inventory, hot bar and bottom rim: the survival inventory's lower part, 56 px down
    out.alpha_composite(inv.crop((0, 81 * S, 176 * S, 166 * S)), (0, 137 * S))
    cell = inventory_cell(path, S)
    for row in range(6):
        for c in range(9):
            out.alpha_composite(cell, ((7 + 18 * c) * S, (17 + 18 * row) * S))
    rule = Image.new("RGBA", (162 * S, max(1, S // 2)), (232, 182, 64, 255))
    out.alpha_composite(rule, (7 * S, 136 * S))      # under the "Inventory" label
    return out


# ------------------------------------------------------------------------------------------------ main menu

# gui_main.webp (1492x1054): the owner's Сүлд Цэс: painted title, six cards with painted labels, a bar at the bottom.
# Source px -> chest px of a 6-row chest; the bar (below the cards) lands on the screen's "Inventory" label row.
MM_XS = [(20, 0), (38, 7), (504, 61), (996, 115), (1452, 169), (1472, 176)]
MM_YS = [(12, 0), (135, 16), (151, 18), (557, 72), (958, 126), (1045, 138)]
MM_HEIGHT = 138
MM_RELETTER = (1120, 176, 1365, 242)      # card 3's painted word (ЭРДЭН): painted over with the banner's cloth
MM_CLEAN = (1074, 176, 1116, 242)         # a clean strip of that banner between its left knot and the word
MM_BANNER3 = (1010, 168, 1450, 252)
MM_BAR = (972, 1027)                      # the bottom bar's cloth rows (between its gold lines)
MM_BAR_CLEAN = (600, 760)                 # a plain stretch of it
MM_BAR_KNOTS = [(24, 140), (1350, 1468)]  # its corner knots: under the screen's "Inventory" label, so cleared


def main_menu(path: str) -> tuple[Image.Image, tuple[float, float, float, float]]:
    """The owner's main menu on a 6-row chest (176 x 138 chest px times SCALE) and card 3's banner in chest px, where
    the label is re-lettered (the card opens quests and dungeons: ЭРЭЛ, not the painted ЭРДЭН)."""
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
    x0, y0, x1, y1 = MM_RELETTER
    cx0, cy0, cx1, cy1 = MM_CLEAN
    strip = a[cy0:cy1, cx0:cx1]
    reps = -(-(x1 - x0) // strip.shape[1])
    tiles = np.concatenate([strip if i % 2 == 0 else strip[:, ::-1] for i in range(reps)], axis=1)
    a[y0:y1, x0:x1] = tiles[:, :x1 - x0]
    by0, by1 = MM_BAR
    plain = a[by0:by1, MM_BAR_CLEAN[0]:MM_BAR_CLEAN[1]]
    for kx0, kx1 in MM_BAR_KNOTS:
        a[by0:by1, kx0:kx1] = np.concatenate([plain] * (-(-(kx1 - kx0) // plain.shape[1])), axis=1)[:, :kx1 - kx0]
    src = Image.fromarray(a.astype(np.uint8), "RGB")
    img = warp(src, MM_XS, MM_XS, MM_YS, MM_HEIGHT + 1, 176, MM_HEIGHT)

    def gx(v):
        return float(np.interp(v, [p for p, _ in MM_XS], [g for _, g in MM_XS]))

    def gy(v):
        return float(np.interp(v, [p for p, _ in MM_YS], [g for _, g in MM_YS]))
    bx0, by0, bx1, by1 = MM_BANNER3
    return img, (gx(bx0), gy(by0), gx(bx1), gy(by1))


# ------------------------------------------------------------------------------------------------ card menus

# gui_welcome.webp (1961x802): the owner's welcome window: a header bar and three framed cards, each a coloured
# banner over a painting. Source px -> chest px of a 3-row chest (176 x 72): the cards land on their 3x3 slot blocks.
WC_XS = [(14, 0), (40, 7), (648, 61), (1304, 115), (1918, 169), (1944, 176)]
WC_YS = [(14, 0), (128, 16), (150, 18), (256, 28), (762, 70.5), (787, 72)]
WC_CARD = (18, 138, 668, 778)          # card 1 with the gems on its corners: the 9-slice template of every card
WC_CARD_BORDER = (30, 26, 34, 24)      # its frame (left, top, right, bottom) in source px
WC_BANNER = (46, 158, 630, 257)        # card 1's banner, knots at both ends: 3-slice template
WC_BANNER_CAP = 90                     # source px of each banner end (the knot) kept unstretched
WC_PICTURE_TOP = 258                   # where the painting starts under the banner
WC_HEADER = (14, 14, 1944, 136)        # the header bar with its corner knots (above the cards' gems)


def welcome(path: str) -> Image.Image:
    """The owner's welcome window on a 3-row chest (176 x 72 chest px times SCALE)."""
    src = Image.open(path).convert("RGB")
    return warp(src, WC_XS, WC_XS, WC_YS, 73, 176, 72)


def welcome_banners() -> list[tuple[float, float, float, float]]:
    """The three banners of welcome() in chest px (x0, y0, x1, y1), for the labels."""
    def gx(s):
        return float(np.interp(s, [p for p, _ in WC_XS], [g for _, g in WC_XS]))

    def gy(s):
        return float(np.interp(s, [p for p, _ in WC_YS], [g for _, g in WC_YS]))
    lefts = [WC_BANNER[0], WC_BANNER[0] + (666 - 40), WC_BANNER[0] + (1322 - 40)]
    width = WC_BANNER[2] - WC_BANNER[0]
    return [(gx(l), gy(WC_BANNER[1]), gx(l + width), gy(WC_BANNER[3])) for l in lefts]


def _nine(img: Image.Image, border: tuple[int, int, int, int], size: tuple[int, int], scale_border: float) -> Image.Image:
    """9-slice resize: the corners keep their look (scaled by ``scale_border``), the edges and the centre stretch."""
    l, t, r, b = border
    W, H = img.size
    w, h = size
    L, T, R, B = (max(1, round(v * scale_border)) for v in (l, t, r, b))
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    xs_src = [0, l, W - r, W]
    ys_src = [0, t, H - b, H]
    xs_dst = [0, L, w - R, w]
    ys_dst = [0, T, h - B, h]
    for i in range(3):
        for j in range(3):
            box = (xs_src[i], ys_src[j], xs_src[i + 1], ys_src[j + 1])
            dw, dh = xs_dst[i + 1] - xs_dst[i], ys_dst[j + 1] - ys_dst[j]
            if dw <= 0 or dh <= 0 or box[2] <= box[0] or box[3] <= box[1]:
                continue
            out.paste(img.crop(box).resize((dw, dh), Image.LANCZOS), (xs_dst[i], ys_dst[j]))
    return out


def _cover(path: str, size: tuple[int, int]) -> Image.Image:
    """A painting cropped to fill ``size`` (centre crop to the aspect), downscaled smoothly."""
    im = Image.open(path).convert("RGB")
    tw, th = size
    w, h = im.size
    if w / h > tw / th:
        nw = int(h * tw / th)
        im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = int(w * th / tw)
        im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    return im.resize(size, Image.LANCZOS).convert("RGBA")


def _recolour(img: Image.Image, colour: tuple[int, int, int]) -> Image.Image:
    """The banner in another colour: its cloth takes ``colour`` (shading kept), its gold knots and trim stay."""
    a = np.asarray(img.convert("RGBA")).astype(np.float64)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    gold = (r > 120) & (r > b + 50) & (g > 80)
    lum = (0.3 * r + 0.59 * g + 0.11 * b)
    cloth = ~gold
    m = lum[cloth].mean() if cloth.any() else 1.0
    k = np.clip(lum / max(1.0, m), 0.35, 1.8)[..., None]
    tinted = np.clip(np.array(colour, float)[None, None, :] * k, 0, 255)
    a[..., :3] = np.where(cloth[..., None], tinted, a[..., :3])
    return Image.fromarray(np.rint(a).astype(np.uint8), "RGBA")


class CardKit:
    """Pieces of the owner's welcome window to build any card menu in the same style, at SCALE."""

    def __init__(self, path: str, scale: int = SCALE):
        self.src = Image.open(path).convert("RGBA")
        self.S = scale
        # source px per chest px of the welcome cards (~11.3): the frame keeps that thickness on every card
        self.k = scale * 54 / (648 - 40)
        self.card = self.src.crop(WC_CARD)
        self.banner = self.src.crop(WC_BANNER)

    def header(self, width: int = 176) -> Image.Image:
        """The header bar (width x 17 chest px): its corner knots kept, the bar between them stretched."""
        h = self.src.crop(WC_HEADER)
        cap = 200
        return _nine(h, (cap, 4, cap, 4), (width * self.S, 17 * self.S), self.k)

    def frame(self, w: int, h: int) -> Image.Image:
        """A card frame w x h chest px, the inside transparent (the painting goes under it)."""
        f = _nine(self.card, WC_CARD_BORDER, (w * self.S, h * self.S), self.k)
        a = np.asarray(f).astype(np.float64)
        l, t, r, b = (round(v * self.k) for v in WC_CARD_BORDER)
        a[t:a.shape[0] - b, l:a.shape[1] - r, 3] = 0
        return Image.fromarray(np.rint(a).astype(np.uint8), "RGBA")

    def inner(self, w: int, h: int) -> tuple[int, int, int, int]:
        """The inside of a frame of w x h chest px, in HD px (x0, y0, x1, y1)."""
        l, t, r, b = (round(v * self.k) for v in WC_CARD_BORDER)
        return l, t, w * self.S - r, h * self.S - b

    def banner_strip(self, width_px: int, colour=None, knots: bool = True) -> Image.Image:
        """A banner width_px HD px wide (knots at both ends unless ``knots`` is False), optionally recoloured."""
        bh = round((WC_BANNER[3] - WC_BANNER[1]) * self.k)
        cap = WC_BANNER_CAP
        if knots:
            img = _nine(self.banner, (cap, 0, cap, 0), (width_px, bh), self.k)
        else:
            # the plain middle of the banner with its own trim ends (12 source px), no knot
            mid = self.banner.crop((cap, 0, self.banner.width - cap, self.banner.height))
            ends = Image.new("RGBA", (mid.width + 24, mid.height))
            ends.paste(self.banner.crop((0, 0, 12, self.banner.height)), (0, 0))
            ends.paste(mid, (12, 0))
            ends.paste(self.banner.crop((self.banner.width - 12, 0, self.banner.width, self.banner.height)), (12 + mid.width, 0))
            img = _nine(ends, (12, 0, 12, 0), (width_px, bh), self.k)
        return _recolour(img, colour) if colour else img

    def label_room(self, w: int, knots: bool = True) -> float:
        """Chest px free for a label on the banner of a w px card."""
        x0, _, x1, _ = self.inner(w, 54)
        free = (x1 - x0) / self.S
        return free - (2 * 80 * self.k / self.S if knots else 2.0)

    def card_image(self, w: int, h: int, art: str, colour, knots: bool = True) -> Image.Image:
        """A whole card: frame, banner and the painting, w x h chest px times SCALE."""
        out = Image.new("RGBA", (w * self.S, h * self.S), (24, 18, 12, 255))
        x0, y0, x1, y1 = self.inner(w, h)
        strip = self.banner_strip(x1 - x0, colour, knots)
        out.alpha_composite(strip, (x0, y0))
        top = y0 + strip.height
        out.alpha_composite(_cover(art, (x1 - x0, y1 - top)), (x0, top))
        out.alpha_composite(self.frame(w, h), (0, 0))
        return out

    def banner_box(self, w: int, h: int) -> tuple[float, float, float, float]:
        """Where the label goes on card_image(w, h), in chest px relative to the card."""
        x0, y0, x1, y1 = self.inner(w, h)
        bh = round((WC_BANNER[3] - WC_BANNER[1]) * self.k)
        return x0 / self.S, y0 / self.S, x1 / self.S, (y0 + bh) / self.S
