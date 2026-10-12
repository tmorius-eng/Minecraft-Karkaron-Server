#!/usr/bin/env python3
"""The pack's HD text font ``suld:hd`` (docs/UI_HUB.md): a TrueType provider for the rows under a hub title, the
menu tooltips and the dialog pages, sharp at every GUI scale where the 8 px bitmap font is blocky.

DejaVu Sans Bold (Bitstream Vera licence, permissive; the licence file is written beside the font) subset to the
scripts SÜLD writes in: Latin, Latin-1, Cyrillic (Ө, Ү included), general punctuation and the few symbols the UI
uses. Characters the subset lacks fall through to minecraft:default (a "reference" provider). Deterministic:
timestamps are not recalculated. Needs fontTools; without it the previously written files stay and a note is
printed."""
from __future__ import annotations

import json
import os
import shutil

SOURCE = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
LICENCE = "/usr/share/doc/fonts-dejavu-core/copyright"
SYMBOLS = "◆✕✦«»·★☆✔✘✓→←┃━▎▶⟳✉…–—“”’‘₮°№§•"
RANGES = [(0x20, 0x7E), (0xA0, 0xFF), (0x400, 0x4FF), (0x2000, 0x206F)]
SIZE = 8.0
OVERSAMPLE = 4.0


def build(rp_suld_dir: str) -> bool:
    """Writes assets/suld/font/hd.ttf, hd.json and hd-LICENSE.txt under ``rp_suld_dir`` (the pack's suld namespace)."""
    font_dir = os.path.join(rp_suld_dir, "font")
    os.makedirs(font_dir, exist_ok=True)
    out = os.path.join(font_dir, "hd.ttf")
    try:
        from fontTools import subset
    except ImportError:
        print("hd_font: fontTools missing, keeping the committed hd.ttf" if os.path.exists(out) else "hd_font: fontTools missing, no hd.ttf")
        return os.path.exists(out)
    if not os.path.exists(SOURCE):
        print("hd_font: " + SOURCE + " missing")
        return os.path.exists(out)
    unicodes = set()
    for lo, hi in RANGES:
        unicodes.update(range(lo, hi + 1))
    unicodes.update(ord(c) for c in SYMBOLS)
    opts = subset.Options()
    opts.recalc_timestamp = False
    opts.recalc_bounds = False
    opts.layout_features = ["kern", "liga"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    font = subset.load_font(SOURCE, opts)
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=sorted(unicodes))
    sub.subset(font)
    subset.save_font(font, out, opts)
    with open(os.path.join(font_dir, "hd.json"), "w", encoding="utf-8") as fh:
        json.dump({"providers": [
            {"type": "ttf", "file": "suld:hd.ttf", "size": SIZE, "oversample": OVERSAMPLE, "shift": [0, 0], "skip": ""},
            {"type": "reference", "id": "minecraft:default"},
        ]}, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    if os.path.exists(LICENCE):
        shutil.copyfile(LICENCE, os.path.join(font_dir, "hd-LICENSE.txt"))
    return True


if __name__ == "__main__":
    import sys
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ok = build(sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, "resourcepack", "assets", "suld"))
    print("hd font", "written" if ok else "NOT written")
