#!/usr/bin/env python3
"""Keep every bitmap glyph of the pack's fonts within the client's glyph limit (256 texels on either axis).

Minecraft 1.21.11 stitches each font glyph into a 256 x 256 atlas page. A bitmap cell wider or taller than that
cannot be stitched, and the client draws its missing-glyph box (▯) in its place, with the box's own 6 px advance. A
4x HD chest background of 704 x 288 texels therefore became one ▯, and the title after it landed 171 px too far
left. This happened to the welcome screen in the live test of 2026-10-15: «Тавтай морил, <name>!▯» over an empty
chest.

This step cuts every such cell into tiles of at most 256 texels and gives each tile its own code point. It then
rewrites the glyph wherever it is used, in Glyphs.java and the lang files, as one string. The string holds the
tiles plus the space glyphs that put each tile where it was in the big image, and it ends at the art's full width + 1.
That is the advance the Java layout counts on (Menu: 176 + 1, HubLayout: W + 1), even where the art's right edge is
transparent (the client would trim it), so callers need no change. Fully transparent tiles are dropped, and identical tiles share one glyph.

gen_ui.py runs this after it writes the fonts. It is idempotent: a pack with no oversized cell is left as it is.

    python3 tools/pack/glyph_tiles.py
"""
from __future__ import annotations

import hashlib
import json
import os
import re

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "resourcepack", "assets")
GLYPHS_JAVA = os.path.join(ROOT, "suld-plugin", "src", "main", "java", "mn", "suld", "plugin", "ui", "Glyphs.java")
LANG = os.path.join(ASSETS, "minecraft", "lang")

# The client's limit (FontTexture is 256 x 256 in 1.21.11).
MAX_TEXELS = 256
# The font, where its tiles' code points start, and where shift glyphs go if the font has none.
FONTS = {
    "suld/font/ui.json": {"tiles": 0xE400, "spaces": None},
    "minecraft/font/default.json": {"tiles": 0xF800, "spaces": 0xF8E0},
}
POWERS = (128, 64, 32, 16, 8, 4, 2, 1)


def ink_width(img: Image.Image) -> int:
    """The client's glyph width: one past the last column with any alpha (0 for an empty cell)."""
    alpha = img.getchannel("A")
    box = alpha.getbbox()
    return 0 if box is None else box[2]


def advance(width: int, scale: float) -> int:
    """The client's advance of a bitmap glyph: ink width x scale, rounded, + 1."""
    return int(0.5 + width * scale) + 1


def as_int(v: float, what: str) -> int:
    if abs(v - round(v)) > 1e-9:
        raise SystemExit(f"glyph_tiles: {what} = {v} is not a whole GUI px; tiles must fall on whole px")
    return int(round(v))


class Shifts:
    """The ±2^n space glyphs of one font (found, or added to it)."""

    def __init__(self, providers: list, first_free: int | None):
        self.chars: dict[int, str] = {}
        for p in providers:
            if p.get("type") == "space":
                for ch, adv in p["advances"].items():
                    if adv in (*POWERS, *(-n for n in POWERS)) and adv not in self.chars:
                        self.chars[adv] = ch
        missing = [a for n in POWERS for a in (-n, n) if a not in self.chars]
        if missing:
            if first_free is None:
                raise SystemExit("glyph_tiles: the font has no ±2^n space glyphs and no range to add them")
            space = next((p for p in providers if p.get("type") == "space"), None)
            if space is None:
                space = {"type": "space", "advances": {}}
                providers.insert(0, space)
            nxt = first_free
            for a in missing:
                while chr(nxt) in space["advances"]:
                    nxt += 1
                ch = chr(nxt)
                space["advances"][ch] = a
                self.chars[a] = ch
                nxt += 1

    def shift(self, px: int) -> str:
        out, left = [], abs(px)
        for n in POWERS:
            while left >= n:
                out.append(self.chars[-n if px < 0 else n])
                left -= n
        return "".join(out)


def tile_font(rel: str, cfg: dict) -> dict[str, str]:
    """Tiles the oversized cells of one font; returns {old glyph: replacement string}."""
    path = os.path.join(ASSETS, *rel.split("/"))
    if not os.path.exists(path):
        return {}
    spec = json.load(open(path, encoding="utf-8"))
    providers = spec["providers"]
    used = {ch for p in providers for ch in (p.get("advances") or {})}
    used |= {ch for p in providers for row in (p.get("chars") or []) for ch in row}
    oversized = []
    for i, p in enumerate(providers):
        if p.get("type") != "bitmap":
            continue
        ns, _, fpath = p["file"].partition(":")
        with Image.open(os.path.join(ASSETS, ns, "textures", fpath)) as im:
            w, h = im.size
        rows = p["chars"]
        if w // len(rows[0]) > MAX_TEXELS or h // len(rows) > MAX_TEXELS:
            if len(rows) != 1 or len(rows[0]) != 1:
                raise SystemExit(f"glyph_tiles: {rel} {p['file']}: only single-glyph sheets can be tiled")
            oversized.append(i)
    if not oversized:
        return {}

    shifts = Shifts(providers, cfg["spaces"])
    used |= set(shifts.chars.values())
    next_cp = [cfg["tiles"]]

    def new_cp() -> str:
        while chr(next_cp[0]) in used:
            next_cp[0] += 1
        ch = chr(next_cp[0])
        used.add(ch)
        return ch

    shared: dict[tuple, str] = {}           # (tile bytes digest, height, ascent) -> its glyph
    written: dict[str, str] = {}            # tile bytes digest -> its file
    replacement: dict[str, str] = {}
    out_providers = []
    sources, cleaned = set(), set()
    for i, p in enumerate(providers):
        if i not in oversized:
            out_providers.append(p)
            continue
        ns, _, fpath = p["file"].partition(":")
        src = os.path.join(ASSETS, ns, "textures", fpath)
        sources.add(src)
        with Image.open(src) as im:
            img = im.convert("RGBA")
        scale = p["height"] / img.height
        ascent = p.get("ascent", 0)
        total = as_int(img.width * scale, f"{p['file']} width") + 1   # the layout's width + 1, untrimmed
        base = os.path.splitext(fpath)[0]                  # font/gui/welcome
        tile_dir = os.path.dirname(base) + "/tiles"
        stale = os.path.join(ASSETS, ns, "textures", *tile_dir.split("/"))
        if src not in cleaned and os.path.isdir(stale):     # tiles of an earlier run of this image (once per image:
            cleaned.add(src)                                # one sheet can back several glyphs)
            for fn in os.listdir(stale):
                if re.fullmatch(re.escape(os.path.basename(base)) + r"_\d+_\d+\.png", fn):
                    os.remove(os.path.join(stale, fn))
        pieces, pen = [], 0
        for ty in range(0, img.height, MAX_TEXELS):
            for tx in range(0, img.width, MAX_TEXELS):
                tile = img.crop((tx, ty, min(img.width, tx + MAX_TEXELS), min(img.height, ty + MAX_TEXELS)))
                width = ink_width(tile)
                if width == 0:
                    continue                                # nothing to draw: no glyph
                t_height = as_int(tile.height * scale, f"{p['file']} tile height")
                t_ascent = as_int(ascent - ty * scale, f"{p['file']} tile ascent")
                if t_ascent > t_height:
                    raise SystemExit(f"glyph_tiles: {p['file']} tile at {tx},{ty}: ascent {t_ascent} > height {t_height}")
                digest = hashlib.sha1(tile.tobytes() + repr(tile.size).encode()).hexdigest()
                key = (digest, t_height, t_ascent)
                if key not in shared:
                    if digest not in written:
                        name = f"{tile_dir}/{os.path.basename(base)}_{ty // MAX_TEXELS}_{tx // MAX_TEXELS}.png"
                        full = os.path.join(ASSETS, ns, "textures", *name.split("/"))
                        os.makedirs(os.path.dirname(full), exist_ok=True)
                        tile.save(full, optimize=False)
                        written[digest] = f"{ns}:{name}"
                    ch = new_cp()
                    shared[key] = ch
                    out_providers.append({"type": "bitmap", "file": written[digest], "height": t_height,
                                          "ascent": t_ascent, "chars": [ch]})
                x = as_int(tx * scale, f"{p['file']} tile x")
                pieces.append(shifts.shift(x - pen) + shared[key])
                pen = x + advance(width, scale)
        pieces.append(shifts.shift(total - pen))
        replacement[p["chars"][0]] = "".join(pieces)
    spec["providers"] = out_providers
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(spec, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    still = {os.path.join(ASSETS, q["file"].partition(":")[0], "textures", q["file"].partition(":")[2])
             for q in out_providers if q.get("type") == "bitmap"}
    for src in sources - still:
        os.remove(src)
    print(f"glyph_tiles: {rel}: {len(replacement)} oversized glyph(s) -> {len(shared)} tile glyph(s)")
    return replacement


def java_escape(s: str) -> str:
    return "".join(f"\\u{ord(c):04X}" for c in s)


def rewrite_glyphs_java(replacement: dict[str, str]) -> None:
    if not replacement or not os.path.exists(GLYPHS_JAVA):
        return
    src = open(GLYPHS_JAVA, encoding="utf-8", newline="").read()

    def sub(m: re.Match) -> str:
        ch = chr(int(m.group(2), 16))
        return m.group(1) + java_escape(replacement[ch]) + '";' if ch in replacement else m.group(0)

    src = re.sub(r'(public static final String \w+ = ")\\u([0-9A-F]{4})";', sub, src)
    with open(GLYPHS_JAVA, "w", encoding="utf-8", newline="") as fh:
        fh.write(src)


def rewrite_lang(replacement: dict[str, str]) -> None:
    if not replacement or not os.path.isdir(LANG):
        return
    for fn in sorted(os.listdir(LANG)):
        if not fn.endswith(".json"):
            continue
        path = os.path.join(LANG, fn)
        entries = json.load(open(path, encoding="utf-8"))
        changed = False
        for k, v in entries.items():
            new = "".join(replacement.get(c, c) for c in v)
            if new != v:
                entries[k] = new
                changed = True
        if changed:
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(entries, fh, ensure_ascii=False, indent=1)
                fh.write("\n")


def main() -> None:
    for rel, cfg in FONTS.items():
        replacement = tile_font(rel, cfg)
        if rel == "suld/font/ui.json":
            rewrite_glyphs_java(replacement)
        if rel == "minecraft/font/default.json":
            rewrite_lang(replacement)


if __name__ == "__main__":
    main()
