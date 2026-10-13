#!/usr/bin/env python3
"""The world map's natural colours (docs/WORLD_MAP.md): Minecraft's own biome tints and tinted-block textures, read
from a 1.21.11 client jar and written to ``suld-plugin/src/main/resources/map/colors.json``.

A vanilla map paints every grass block the same green and all water the same blue. The atlas paints them the way the
game shows them: grass, leaves and water take the colour of the biome they really stand in (read from the chunk), so a
savanna is olive, a taiga blue-green, a warm ocean turquoise. This tool only collects Minecraft's data for that:

* per biome (``data/minecraft/worldgen/biome/*.json``): the grass colour (its ``grass_color``, or the
  ``colormap/grass.png`` pixel at its temperature and downfall, with the ``dark_forest`` / ``swamp`` modifiers the
  client applies), the foliage colour (``foliage_color`` or ``colormap/foliage.png``) and the water colour;
* per tinted block: which tint it takes in the client (grass, foliage, water or a fixed colour, as the client's block
  colour table does) and the average brightness of its texture (alpha-weighted, first animation frame).

The plugin multiplies the two and picks the nearest map colour; nothing here is guessed. Usage:
``python3 -I tools/map/gen_map_colors.py <client.jar> [out.json]``. Deterministic (sorted keys, no timestamps).
"""
from __future__ import annotations

import io
import json
import os
import sys
import zipfile

from PIL import Image

# The client's block colour table (BlockColors.createDefault, 1.21.11) for the blocks a map can show from above.
# "grass"/"foliage"/"water" take the biome's colour; "#rrggbb" is a fixed colour; the texture gives the brightness.
TINTED = {
    "grass_block": ("grass", "grass_block_top"),
    "short_grass": ("grass", "short_grass"),
    "tall_grass": ("grass", "tall_grass_top"),
    "fern": ("grass", "fern"),
    "large_fern": ("grass", "large_fern_top"),
    "bush": ("grass", "bush"),
    "oak_leaves": ("foliage", "oak_leaves"),
    "jungle_leaves": ("foliage", "jungle_leaves"),
    "acacia_leaves": ("foliage", "acacia_leaves"),
    "dark_oak_leaves": ("foliage", "dark_oak_leaves"),
    "mangrove_leaves": ("foliage", "mangrove_leaves"),
    "vine": ("foliage", "vine"),
    "spruce_leaves": ("#619961", "spruce_leaves"),
    "birch_leaves": ("#80a755", "birch_leaves"),
    "lily_pad": ("#208030", "lily_pad"),
    "water": ("water", "water_still"),
}

BIOME_DIR = "data/minecraft/worldgen/biome/"


def _hex(s: str) -> tuple[int, int, int]:
    s = s.lstrip("#")
    return int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16)


def _colormap(jar: zipfile.ZipFile, name: str) -> Image.Image:
    return Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/colormap/{name}.png"))).convert("RGB")


def _lookup(cm: Image.Image, temperature: float, downfall: float) -> tuple[int, int, int]:
    # GrassColor.get / FoliageColor.get: downfall is scaled by the temperature, both clamped to 0..1
    t = min(1.0, max(0.0, temperature))
    d = min(1.0, max(0.0, downfall)) * t
    return cm.getpixel((int((1.0 - t) * 255.0), int((1.0 - d) * 255.0)))


def _dark_forest(c: tuple[int, int, int]) -> tuple[int, int, int]:
    v = (c[0] << 16) | (c[1] << 8) | c[2]
    v = ((v & 0xFEFEFE) + 0x28340A) >> 1
    return (v >> 16) & 255, (v >> 8) & 255, v & 255


def _brightness(jar: zipfile.ZipFile, texture: str) -> int:
    im = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/block/{texture}.png"))).convert("RGBA")
    w, _ = im.size
    im = im.crop((0, 0, w, w))  # the first frame of an animation strip
    total = weight = 0
    pixels = im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata()
    for r, g, b, a in pixels:
        if a == 0:
            continue
        total += (r + g + b) / 3 * a
        weight += a
    return round(total / weight)


def build(jar_path: str) -> dict:
    jar = zipfile.ZipFile(jar_path)
    grass, foliage = _colormap(jar, "grass"), _colormap(jar, "foliage")
    biomes = {}
    for name in sorted(n for n in jar.namelist() if n.startswith(BIOME_DIR) and n.endswith(".json")):
        b = json.loads(jar.read(name))
        e = b.get("effects", {})
        t, d = b.get("temperature", 0.5), b.get("downfall", 0.5)
        g = _hex(e["grass_color"]) if "grass_color" in e else _lookup(grass, t, d)
        modifier = e.get("grass_color_modifier", "none")
        if modifier == "dark_forest":
            g = _dark_forest(g)
        elif modifier == "swamp":
            g = _hex("#6a7039")  # the warmer of the client's two swamp greens, which covers most of a swamp
        f = _hex(e["foliage_color"]) if "foliage_color" in e else _lookup(foliage, t, d)
        w = _hex(e.get("water_color", "#3f76e4"))
        key = "minecraft:" + name[len(BIOME_DIR):-5]
        biomes[key] = ["%02x%02x%02x" % g, "%02x%02x%02x" % f, "%02x%02x%02x" % w]
    tinted = {}
    for block, (tint, texture) in sorted(TINTED.items()):
        tinted["minecraft:" + block] = [tint, _brightness(jar, texture)]
    return {
        "format": 1,
        "source": "Minecraft 1.21.11 client jar: worldgen/biome/*.json, colormap/grass.png and foliage.png, block textures",
        "biomes": biomes,
        "tinted": tinted,
    }


def _dumps(data: dict) -> str:
    """One entry per line, so a Minecraft update shows as a readable diff."""
    lines = ["{"]
    lines.append(f' "format": {data["format"]},')
    lines.append(f' "source": {json.dumps(data["source"])},')
    for section in ("biomes", "tinted"):
        lines.append(f' "{section}": {{')
        items = sorted(data[section].items())
        for i, (k, v) in enumerate(items):
            lines.append(f'  {json.dumps(k)}: {json.dumps(v)}' + ("," if i < len(items) - 1 else ""))
        lines.append(" }" + ("," if section == "biomes" else ""))
    lines.append("}")
    return "\n".join(lines) + "\n"


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("usage: gen_map_colors.py <client.jar> [out.json]")
    here = os.path.dirname(os.path.abspath(__file__))
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "..", "..", "suld-plugin", "src", "main", "resources", "map", "colors.json")
    data = build(sys.argv[1])
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        fh.write(_dumps(data))
    print(f"{len(data['biomes'])} biomes, {len(data['tinted'])} tinted blocks -> {os.path.relpath(out)}")


if __name__ == "__main__":
    main()
