#!/usr/bin/env python3
"""Build the combat decals (docs/COMBAT_FEEL.md, docs/SKILL_VFX_SPEC.md §6): drawn from scratch, deterministic.

Outputs: resourcepack/assets/suld/textures/entity/vfx/{slash,thrust,ring,reticle}.png   32x32, white with alpha
         resourcepack/assets/suld/models/entity/vfx/*.json                                 flat quads, both faces
         resourcepack/assets/suld/items/entity/vfx/*.json                                  tint = custom_model_data colour
         resourcepack/assets/minecraft/atlases/{blocks,items}.json                         + directory source entity/vfx

Models (1 block across, centred on the display origin so scale and rotation pivot in the middle):
  slash_h  the sweep arc lying flat (horizontal sword / axe sweep)     slash_v  the same arc standing up (overhead chop)
  thrust   a straight streak lying flat, pointing +Z (spear thrust)    ring     a shock ring lying flat (stomp, landing)
  reticle  the lock-on marker, upright (billboarded by the display)

    python3 tools/pack/gen_vfx.py
"""
from __future__ import annotations

import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RP = os.path.join(ROOT, "resourcepack", "assets")
TEX = os.path.join(RP, "suld", "textures", "entity", "vfx")
MOD = os.path.join(RP, "suld", "models", "entity", "vfx")
ITM = os.path.join(RP, "suld", "items", "entity", "vfx")
N = 32


def px_alpha(fn) -> Image.Image:
    """A white 32x32 image whose alpha is fn(u, v) in [0, 1] (u, v in [-1, 1], v up)."""
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    p = im.load()
    for y in range(N):
        for x in range(N):
            u = (x + 0.5) / N * 2 - 1
            v = 1 - (y + 0.5) / N * 2
            a = max(0.0, min(1.0, fn(u, v)))
            if a > 0.02:
                # hot white core, the tint colours the rest: keep RGB white, alpha carries the shape
                p[x, y] = (255, 255, 255, int(round(a * 255 / 17)) * 17)  # 16 alpha steps: crisp pixel look
    return im


def slash(u: float, v: float) -> float:
    """A crescent: the outer edge sharp, fading inwards; thick in the middle of the sweep, thin at both tips."""
    r = math.hypot(u, v + 0.6)
    ang = math.atan2(v + 0.6, u)  # 0..pi over the upper half
    if not (0.08 < ang < math.pi - 0.08):
        return 0.0
    along = math.sin(ang)  # 1 in the middle, 0 at the tips
    outer, width = 1.12, 0.12 + 0.34 * along
    if r > outer or r < outer - width:
        return 0.0
    depth = (outer - r) / width  # 0 at the edge .. 1 inside
    return (1.0 - depth) ** 0.8 * (0.35 + 0.65 * along)


def thrust(u: float, v: float) -> float:
    """A streak along +v: sharp tip, widening and fading towards the tail."""
    if v < -0.95 or v > 0.95:
        return 0.0
    t = (v + 0.95) / 1.9  # 0 tail .. 1 tip
    half = 0.07 + 0.55 * (1 - t) * t * 2.0
    if abs(u) > half:
        return 0.0
    return (1 - abs(u) / half) ** 0.6 * (0.25 + 0.75 * t)


def ring(u: float, v: float) -> float:
    r = math.hypot(u, v)
    if r > 0.96 or r < 0.70:
        return 0.0
    return 1 - abs(r - 0.86) / 0.13


def reticle(u: float, v: float) -> float:
    """Four corner chevrons around a hollow diamond: readable at any distance, nothing in the middle."""
    d = abs(u) + abs(v)
    diamond = 1.0 if 0.30 < d < 0.40 else 0.0
    m = max(abs(u), abs(v))
    corner = 1.0 if (0.78 < m < 0.92 and min(abs(u), abs(v)) > 0.45) else 0.0
    return max(diamond, corner)


def quad(texture: str, flat: bool) -> dict:
    face = {"uv": [0, 0, 16, 16], "texture": "#t", "tintindex": 0}
    if flat:  # lying in the XZ plane at the origin; texture "up" points to +Z (forward of the display)
        el = {"from": [0, 8, 0], "to": [16, 8, 16], "shade": False,
              "faces": {"up": dict(face, rotation=180), "down": dict(face)}}
    else:
        el = {"from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "faces": {"north": dict(face), "south": dict(face)}}
    tex = "suld:entity/vfx/" + texture
    return {"textures": {"t": tex, "particle": tex}, "elements": [el]}


def item_def(name: str) -> dict:
    return {"model": {"type": "minecraft:model", "model": "suld:entity/vfx/" + name,
                      "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]}}


def atlas(path: str) -> None:
    data = json.load(open(path)) if os.path.exists(path) else {"sources": []}
    src = {"type": "directory", "source": "entity/vfx", "prefix": "entity/vfx/"}
    if src not in data["sources"]:
        data["sources"].append(src)
    with open(path, "w") as fh:
        json.dump(data, fh, indent=1)
        fh.write("\n")


def main() -> None:
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MOD, exist_ok=True)
    os.makedirs(ITM, exist_ok=True)
    for name, fn in (("slash", slash), ("thrust", thrust), ("ring", ring), ("reticle", reticle)):
        px_alpha(fn).save(os.path.join(TEX, name + ".png"), optimize=True)
    models = {"slash_h": ("slash", True), "slash_v": ("slash", False), "thrust": ("thrust", True),
              "ring": ("ring", True), "reticle": ("reticle", False)}
    for name, (tex, flat) in models.items():
        with open(os.path.join(MOD, name + ".json"), "w") as fh:
            json.dump(quad(tex, flat), fh, separators=(",", ":"))
        with open(os.path.join(ITM, name + ".json"), "w") as fh:
            json.dump(item_def(name), fh, separators=(",", ":"))
    for a in ("blocks", "items"):
        atlas(os.path.join(RP, "minecraft", "atlases", a + ".json"))
    print("vfx assets:", sorted(os.listdir(TEX)), sorted(models))


if __name__ == "__main__":
    main()
