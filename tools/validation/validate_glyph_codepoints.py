#!/usr/bin/env python3
"""Glyph code points are append-only (AGENTS.md): an existing code point never gets a new meaning.

assets/registry/glyph_codepoints.json records, for every bitmap and space glyph of the pack's fonts, what it is: the
bitmap file and its cell in the sheet, or a space's advance. This check fails when a recorded code point now means
something else, is gone without being retired, or a retired one is used again. A glyph that is not recorded yet fails
too, with the hint to record it:

    python3 tools/validation/validate_glyph_codepoints.py --update   # appends the new glyphs, never edits one
    python3 tools/validation/validate_glyph_codepoints.py --retire suld/font/ui.json E045 "why"

tools/pack/glyph_tiles.py reads the same file: a tile keeps the code point its file was given, and a new tile never
takes one that was ever used.
"""
from __future__ import annotations

import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "resourcepack", "assets")
LOCK = os.path.join(ROOT, "assets", "registry", "glyph_codepoints.json")


def identity(file: str, height: int, ascent: int) -> str:
    """What a bitmap glyph is: one sheet can back several glyphs drawn at other heights (the hub's card glow)."""
    return f"{file} h{height} a{ascent}"


def font_glyphs(path: str) -> dict[str, str]:
    """{"E018": what it is} for one font file (ttf and reference providers have no per-glyph list)."""
    out: dict[str, str] = {}
    for p in json.load(open(path, encoding="utf-8"))["providers"]:
        if p.get("type") == "bitmap":
            for r, row in enumerate(p.get("chars") or []):
                for c, ch in enumerate(row):
                    if ch == "\u0000":
                        continue
                    cell = "" if len(p["chars"]) == 1 and len(row) == 1 else f" [{r},{c}]"
                    out[f"{ord(ch):04X}"] = identity(p["file"], p.get("height", 8), p.get("ascent", 7)) + cell
        elif p.get("type") == "space":
            for ch, adv in (p.get("advances") or {}).items():
                out[f"{ord(ch):04X}"] = f"space {adv}"
    return out


def current() -> dict[str, dict[str, str]]:
    fonts = {}
    for f in sorted(glob.glob(os.path.join(ASSETS, "*", "font", "*.json"))):
        rel = os.path.relpath(f, ASSETS).replace(os.sep, "/")
        g = font_glyphs(f)
        if g:
            fonts[rel] = g
    return fonts


def load() -> dict:
    if not os.path.exists(LOCK):
        return {"fonts": {}, "retired": {}}
    d = json.load(open(LOCK, encoding="utf-8"))
    d.setdefault("fonts", {})
    d.setdefault("retired", {})
    return d


def save(d: dict) -> None:
    for k in ("fonts", "retired"):
        d[k] = {f: dict(sorted(v.items(), key=lambda kv: int(kv[0], 16))) for f, v in sorted(d[k].items())}
    with open(LOCK, "w", encoding="utf-8") as fh:
        json.dump(d, fh, ensure_ascii=False, indent=1)
        fh.write("\n")


def problems(lock: dict, now: dict) -> list[str]:
    out = []
    for font, glyphs in lock["fonts"].items():
        have = now.get(font, {})
        retired = lock["retired"].get(font, {})
        for cp, what in glyphs.items():
            if cp in retired:
                continue
            if cp not in have:
                out.append(f"{font} U+{cp} ({what}) is gone: retire it (--retire), never reuse it")
            elif have[cp] != what:
                out.append(f"{font} U+{cp} changed meaning: was {what}, now {have[cp]} (append a new code point instead)")
    for font, glyphs in now.items():
        locked = lock["fonts"].get(font, {})
        retired = lock["retired"].get(font, {})
        for cp, what in glyphs.items():
            if cp in retired:
                out.append(f"{font} U+{cp} was retired ({retired[cp]}) and is used again for {what}")
            elif cp not in locked:
                out.append(f"{font} U+{cp} ({what}) is not recorded: run with --update to append it")
    return out


def main(argv: list[str]) -> int:
    lock, now = load(), current()
    if argv[:1] == ["--retire"] and len(argv) == 4:
        font, cp, why = argv[1], argv[2].upper().removeprefix("U+"), argv[3]
        if cp not in lock["fonts"].get(font, {}):
            print(f"{font} U+{cp} is not recorded")
            return 1
        lock["retired"].setdefault(font, {})[cp] = why
        save(lock)
        print(f"retired {font} U+{cp}")
        return 0
    if argv[:1] == ["--update"]:
        bad = [p for p in problems(lock, now) if "is not recorded" not in p]
        if bad:
            print("not updated: these are not new glyphs\n  " + "\n  ".join(bad))
            return 1
        added = 0
        for font, glyphs in now.items():
            locked = lock["fonts"].setdefault(font, {})
            for cp, what in glyphs.items():
                if cp not in locked:
                    locked[cp] = what
                    added += 1
        save(lock)
        print(f"recorded {added} new glyph(s)")
        return 0
    bad = problems(lock, now)
    for p in bad:
        print("FAIL " + p)
    total = sum(len(g) for g in now.values())
    print(f"{total} glyph code points in {len(now)} fonts: " + ("all as recorded" if not bad else f"{len(bad)} problem(s)"))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
