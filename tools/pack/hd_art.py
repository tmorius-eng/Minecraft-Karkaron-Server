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
from PIL import Image

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
