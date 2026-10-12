#!/usr/bin/env python3
"""Render a Minecraft TextDisplay board preview with the game's own font (client 1.21.11 bitmap fonts + unifont).

    python3 tools/pack/render_textdisplay.py <fontdir> <boards.json> <outdir>

<fontdir> holds the vanilla font assets, which are Mojang's and are never committed: jar/ = the client jar unpacked
(assets/minecraft/font, assets/minecraft/textures/font; client URL from piston-meta.mojang.com version manifest) and
unifont.zip (asset index object minecraft/font/unifont.zip). <boards.json> maps a preview name to a list of MiniMessage
strings (one board each), e.g. the output of PlazaBoards.campaignText/ballotText/hallText.

Mimics TextDisplay rendering: default font, line height 9, line width wrap, centred alignment, shadow, bold = glyph
drawn twice 1 px apart with +1 advance, background box 1 px around the text. Output is scaled up for viewing and
placed on a dusk sky backdrop like the plaza.
"""
import io, json, re, sys, zipfile
from PIL import Image, ImageDraw

ROOT = sys.argv[1]  # mcfont dir
FONT_DIR = ROOT + "/jar/assets/minecraft"

COLORS = {"white": (255, 255, 255), "gray": (170, 170, 170), "dark_gray": (85, 85, 85), "gold": (255, 170, 0),
          "yellow": (255, 255, 85), "aqua": (85, 255, 255), "green": (85, 255, 85), "red": (255, 85, 85)}


class Font:
    def __init__(self):
        self.glyphs = {}  # char -> (image RGBA mask, advance, y_offset)
        prov = json.load(open(FONT_DIR + "/font/include/default.json"))["providers"]
        for p in prov:
            img = Image.open(FONT_DIR + "/textures/font/" + p["file"].split(":")[1].replace("font/", "")).convert("RGBA")
            rows = p["chars"]
            cols = max(len(r) for r in rows)
            cw, ch = img.width // cols, img.height // len(rows)
            height = p.get("height", 8)
            ascent = p["ascent"]
            scale = height / ch
            for ry, row in enumerate(rows):
                for cx, c in enumerate(row):
                    if c in self.glyphs or c == "\u0000":
                        continue
                    cell = img.crop((cx * cw, ry * ch, (cx + 1) * cw, (ry + 1) * ch))
                    alpha = cell.split()[3]
                    bbox = alpha.getbbox()
                    width = 0 if bbox is None else bbox[2]
                    if scale != 1:
                        cell = cell.resize((max(1, round(cw * scale)), max(1, round(ch * scale))), Image.NEAREST)
                        width = round(width * scale)
                    self.glyphs[c] = (cell, width + 1, 7 - ascent)
        self.uni = {}
        with zipfile.ZipFile(ROOT + "/unifont.zip") as z:
            name = [n for n in z.namelist() if n.endswith(".hex")][0]
            for line in z.read(name).decode().splitlines():
                cp, bits = line.split(":")
                self.uni[int(cp, 16)] = bits

    def glyph(self, c):
        if c == " ":
            return None, 4, 0
        if c in self.glyphs:
            return self.glyphs[c]
        bits = self.uni.get(ord(c))
        if bits is None:
            return None, 4, 0
        w = len(bits) // 4  # 32 hex digits = 8 px wide, 64 = 16 px, always 16 rows
        img = Image.new("RGBA", (w, 16), (0, 0, 0, 0))
        px = img.load()
        rowlen = len(bits) // 16
        left, right = w, 0
        for y in range(16):
            v = int(bits[y * rowlen:(y + 1) * rowlen], 16)
            for x in range(w):
                if v >> (w - 1 - x) & 1:
                    px[x, y] = (255, 255, 255, 255)
                    left, right = min(left, x), max(right, x)
        if right < left:
            return None, 4, 0
        img = img.crop((left, 0, right + 1, 16)).resize(((right - left + 2) // 2, 8), Image.NEAREST)
        g = (img, img.width + 1, -1)
        self.glyphs[c] = g
        return g


def parse(mini):
    """MiniMessage subset -> list of lines, each a list of (text, color, bold)."""
    out, line = [], []
    stack_color, bold = ["white"], 0
    for tok in re.split(r"(<[^<>]+>)", mini):
        if not tok:
            continue
        m = re.fullmatch(r"<(/?)([a-z_]+)(?::[^>]*)?>", tok)
        if m and (m.group(2) in COLORS or m.group(2) in ("bold", "b")):
            closing, name = m.group(1) == "/", m.group(2)
            if name in ("bold", "b"):
                bold += -1 if closing else 1
            elif closing:
                if len(stack_color) > 1: stack_color.pop()
            else:
                stack_color.append(name)
            continue
        tok = tok.replace("\\<", "<")
        parts = tok.split("\n")
        for i, part in enumerate(parts):
            if i > 0:
                out.append(line); line = []
            if part:
                line.append((part, stack_color[-1], bold > 0))
    out.append(line)
    return out


def measure(font, runs):
    return sum(font.glyph(c)[1] + (1 if b else 0) for t, _, b in runs for c in t)


def wrap(font, runs, width):
    """Split a line's runs into display lines no wider than width, at spaces."""
    words, cur = [], []
    for t, col, b in runs:
        for piece in re.split(r"( )", t):
            if piece:
                words.append((piece, col, b))
    lines, cur, w = [], [], 0
    for wd in words:
        ww = measure(font, [wd])
        if cur and w + ww > width and wd[0] != " ":
            while cur and cur[-1][0] == " ":
                w -= measure(font, [cur.pop()])
            lines.append(cur); cur, w = [], 0
        if not cur and wd[0] == " ":
            continue
        cur.append(wd); w += ww
    lines.append(cur)
    return lines


def render(font, mini, line_width=260, bg=(20, 12, 4, 160)):
    lines = []
    for l in parse(mini):
        lines.extend(wrap(font, l, line_width) if l else [[]])
    widths = [measure(font, l) for l in lines]
    W = max(widths) if widths else 0
    H = len(lines) * 9
    TOP = 3  # accented capitals (Й, Ё) rise above the line box, as in the game
    img = Image.new("RGBA", (W + 2, H + 1 + TOP), (0, 0, 0, 0))
    ImageDraw.Draw(img).rectangle([0, TOP, W + 1, H + TOP], fill=bg)
    for i, l in enumerate(lines):
        x = 1 + (W - widths[i]) // 2
        y = i * 9 + TOP
        for t, col, b in l:
            rgb = COLORS[col]
            shadow = tuple(v // 4 for v in rgb)
            for c in t:
                g, adv, yo = font.glyph(c)
                if g is not None:
                    for dx, dy, colr in ((1, 1, shadow), (0, 0, rgb)):
                        tint = Image.new("RGBA", g.size, colr + (255,))
                        mask = g.split()[3]
                        for bx in ((0, 1) if b else (0,)):
                            img.paste(tint, (x + dx + bx, y + dy + yo + 1), mask)
                x += adv + (1 if b else 0)
    return img


def scene(boards, out, scale=4):
    pad = 24
    w = sum(b.width for b in boards) * scale + pad * (len(boards) + 1)
    h = max(b.height for b in boards) * scale + pad * 2
    bgimg = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(bgimg)
    for y in range(h):  # dusk sky over the plaza
        t = y / h
        d.line([(0, y), (w, y)], fill=(int(70 + 90 * t), int(110 + 60 * t), int(170 - 40 * t), 255))
    x = pad
    for b in boards:
        big = b.resize((b.width * scale, b.height * scale), Image.NEAREST)
        bgimg.alpha_composite(big, (x, pad))
        x += big.width + pad
    bgimg.convert("RGB").save(out)


if __name__ == "__main__":
    font = Font()
    spec = json.load(open(sys.argv[2]))
    for name, texts in spec.items():
        scene([render(font, t) for t in texts], sys.argv[3] + "/" + name + ".png")
        print("wrote", name)
