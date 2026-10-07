#!/usr/bin/env python3
"""Render what the client draws for a SÜLD HUD line, offline: the action-bar panel (and optionally a boss-bar target
frame) laid out with the pack's own font file and textures, the way the client lays out text (advances, ascents,
centring, tint), over a mock screen with the re-skinned hotbar. For reviewing layouts before a client test; the
input is the JSON dumped by HudComposerTest (build/hud-samples/*.json: [{"text", "color"}]).

    python3 tools/pack/hud_render.py OUT.png PANEL.json [TARGET.json] [--scale 3] [--width 427] [--height 240]
"""
from __future__ import annotations

import argparse
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "resourcepack", "assets")


def load_font(name: str = "suld:hud") -> dict:
    ns, path = name.split(":")
    spec = json.load(open(os.path.join(ASSETS, ns, "font", path + ".json"), encoding="utf-8"))
    table = {}
    for p in spec["providers"]:
        if p["type"] == "space":
            for ch, adv in p["advances"].items():
                table[ch] = ("space", adv)
        elif p["type"] == "bitmap":
            fns, fpath = p["file"].split(":")
            img = Image.open(os.path.join(ASSETS, fns, "textures", fpath)).convert("RGBA")
            rows = p["chars"]
            cw, chh = img.width // len(rows[0]), img.height // len(rows)
            scale = p["height"] / chh
            for r, row in enumerate(rows):
                for i, ch in enumerate(row):
                    cell = img.crop((i * cw, r * chh, (i + 1) * cw, (r + 1) * chh))
                    width = 0
                    for x in range(cw - 1, -1, -1):
                        if any(cell.getpixel((x, y))[3] for y in range(chh)):
                            width = x + 1
                            break
                    adv = int(0.5 + width * scale) + 1
                    table[ch] = ("glyph", adv, cell, scale, p["ascent"])
    return table


def tint(cell: Image.Image, color: str) -> Image.Image:
    r, g, b = (int(color[i:i + 2], 16) for i in (1, 3, 5))
    out = cell.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            pr, pg, pb, pa = px[x, y]
            px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out


def draw_line(screen: Image.Image, font: dict, runs: list[dict], centre_x: float, text_top: float, S: int) -> None:
    total = 0
    for run in runs:
        for ch in run["text"]:
            total += font[ch][1]
    x = centre_x - total / 2
    for run in runs:
        for ch in run["text"]:
            g = font[ch]
            if g[0] == "glyph":
                _, adv, cell, scale, ascent = g
                img = tint(cell, run.get("color", "#FFFFFF"))
                w, h = round(img.width * scale * S), round(img.height * scale * S)
                img = img.resize((max(1, w), max(1, h)), Image.NEAREST)
                screen.alpha_composite(img, (round(x * S), round((text_top + 7 - ascent) * S)))
            x += g[1]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("panel")
    ap.add_argument("target", nargs="?")
    ap.add_argument("--scale", type=int, default=3)
    ap.add_argument("--width", type=int, default=427)
    ap.add_argument("--height", type=int, default=240)
    a = ap.parse_args()
    S, W, H = a.scale, a.width, a.height
    screen = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 255))
    px = screen.load()
    for y in range(H * S):  # sky to stone floor
        t = y / (H * S)
        c = (int(150 - 80 * t), int(170 - 90 * t), int(200 - 120 * t)) if t < 0.55 else (int(96 - 20 * t), int(92 - 20 * t), int(88 - 20 * t))
        for x in range(W * S):
            px[x, y] = c + (255,)
    hud = os.path.join(ASSETS, "minecraft", "textures", "gui", "sprites", "hud")
    hb = Image.open(os.path.join(hud, "hotbar.png")).convert("RGBA").resize((182 * S, 22 * S), Image.NEAREST)
    screen.alpha_composite(hb, ((W // 2 - 91) * S, (H - 22) * S))
    sel = Image.open(os.path.join(hud, "hotbar_selection.png")).convert("RGBA").resize((24 * S, 23 * S), Image.NEAREST)
    screen.alpha_composite(sel, ((W // 2 - 91 - 1 + 2 * 20) * S, (H - 22 - 1) * S))
    font = load_font()
    draw_line(screen, font, json.load(open(a.panel, encoding="utf-8")), W / 2, H - 72, S)
    if a.target:
        draw_line(screen, font, json.load(open(a.target, encoding="utf-8")), W / 2, 3, S)
    screen.convert("RGB").save(a.out)


if __name__ == "__main__":
    main()
