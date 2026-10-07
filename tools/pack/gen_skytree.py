#!/usr/bin/env python3
"""Build the assets of the full-screen skill tree (Тэнгэрийн мод, docs/SKILL_SKY.md).

Inputs:  assets/art/source/skilltree_bg.png   night-sky backdrop (gemini-2.5-flash-image, tools/art/skilltree_prompts.json)
Outputs: resourcepack/assets/suld/textures/entity/skytree/{bg,frame,line,glow}.png
         resourcepack/assets/suld/models/entity/skytree/{bg,frame,line,glow}.json   flat 1x1-block quads (pivot 8,8,8)
         resourcepack/assets/suld/items/entity/skytree/*.json                       item definitions (tint = custom_model_data colour)
         resourcepack/assets/minecraft/atlases/{blocks,items}.json                  + directory source entity/skytree

The quads are 16x16 px, centred on the display origin, with both faces, so an ItemDisplay scaled (w, h, 1) is a
w x h block rectangle facing the viewer whichever way the renderer turns it. Deterministic.

    python3 tools/pack/gen_skytree.py
"""
from __future__ import annotations

import json
import os

from PIL import Image, ImageEnhance

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "assets", "art", "source", "skilltree_bg.png")
RP = os.path.join(ROOT, "resourcepack", "assets")
TEX = os.path.join(RP, "suld", "textures", "entity", "skytree")
MOD = os.path.join(RP, "suld", "models", "entity", "skytree")
ITM = os.path.join(RP, "suld", "items", "entity", "skytree")


def save_png(img: Image.Image, path: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def backdrop() -> Image.Image:
    im = Image.open(SRC).convert("RGB")
    im = im.resize((512, 288), Image.LANCZOS)
    # quantise to a small palette: crisp pixel art, small file
    im = ImageEnhance.Brightness(im).enhance(0.92)
    return im.quantize(colors=48, method=Image.MEDIANCUT, dither=Image.NONE).convert("RGB")


def frame() -> Image.Image:
    """The node slot: a light bevelled border (tinted by state) around a dark interior (tinted darker)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            corner = (x in (0, 15)) and (y in (0, 15))
            if corner:
                continue  # rounded corners
            if edge == 0:
                px[x, y] = (255, 255, 255, 255)
            elif edge == 1:
                px[x, y] = (150, 150, 150, 255) if (x + y) % 2 == 0 or x < 2 or y < 2 else (120, 120, 120, 255)
            else:
                v = 46 if y < 8 else 38  # a faint top-to-bottom shade inside
                px[x, y] = (v, v, v + 4, 255)
    return im


def line() -> Image.Image:
    im = Image.new("RGBA", (16, 16), (255, 255, 255, 255))
    px = im.load()
    for x in range(16):  # a soft edge across the thickness
        px[x, 0] = px[x, 15] = (200, 200, 200, 255)
    return im


def glow() -> Image.Image:
    """Hover halo: a ring that fades outwards."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if 5.5 <= d <= 7.5:
                a = int(255 * (1 - (d - 5.5) / 2.2))
                px[x, y] = (255, 255, 255, max(0, a))
    return im


def quad_model(texture: str, tint: bool) -> dict:
    face = {"uv": [0, 0, 16, 16], "texture": "#t"}
    if tint:
        face["tintindex"] = 0
    return {
        "textures": {"t": "suld:entity/skytree/" + texture, "particle": "suld:entity/skytree/" + texture},
        "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False,
                      "faces": {"north": dict(face), "south": dict(face)}}],
    }


def item_def(name: str, tint: bool) -> dict:
    m = {"type": "minecraft:model", "model": "suld:entity/skytree/" + name}
    if tint:
        m["tints"] = [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]
    return {"model": m}


def atlas(path: str) -> None:
    data = json.load(open(path)) if os.path.exists(path) else {"sources": []}
    src = {"type": "directory", "source": "entity/skytree", "prefix": "entity/skytree/"}
    if src not in data["sources"]:
        data["sources"].append(src)
    with open(path, "w") as fh:
        json.dump(data, fh, indent=1)
        fh.write("\n")


def main() -> None:
    save_png(backdrop(), os.path.join(TEX, "bg.png"))
    save_png(frame(), os.path.join(TEX, "frame.png"))
    save_png(line(), os.path.join(TEX, "line.png"))
    save_png(glow(), os.path.join(TEX, "glow.png"))
    os.makedirs(MOD, exist_ok=True)
    os.makedirs(ITM, exist_ok=True)
    for name, tint in (("bg", False), ("frame", True), ("line", True), ("glow", True)):
        with open(os.path.join(MOD, name + ".json"), "w") as fh:
            json.dump(quad_model(name, tint), fh, separators=(",", ":"))
        with open(os.path.join(ITM, name + ".json"), "w") as fh:
            json.dump(item_def(name, tint), fh, separators=(",", ":"))
    for a in ("blocks", "items"):
        atlas(os.path.join(RP, "minecraft", "atlases", a + ".json"))
    print("skytree assets:", sorted(os.listdir(TEX)))


if __name__ == "__main__":
    main()
