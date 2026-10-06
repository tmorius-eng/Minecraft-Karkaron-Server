#!/usr/bin/env python3
"""Render the Kharkhorum master plan as a top-down map (PNG) for design review.

    python3 tools/world/render_plan_map.py [out.png] [--scale 3]

North is up. Shows the 16-block chunk grid (64-block labels), districts, walls, gates, roads,
canal/ponds/bridges, landmarks, gameplay points and the slice boundaries.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIR = os.path.join(ROOT, "assets", "world", "kharkhorum")
out = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else os.path.join(DIR, "master-plan-map.png")
S = int(sys.argv[sys.argv.index("--scale") + 1]) if "--scale" in sys.argv else 3

plan = json.load(open(os.path.join(DIR, "master-plan.json"), encoding="utf-8"))
pts = json.load(open(os.path.join(DIR, "points.json"), encoding="utf-8"))
b = plan["bounds"]
M = 40  # margin px
W, H = (b["x2"] - b["x1"] + 1) * S + 2 * M, (b["z2"] - b["z1"] + 1) * S + 2 * M + 120
img = Image.new("RGB", (W, H), (236, 230, 214))
dr = ImageDraw.Draw(img, "RGBA")
try:
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 15)
except OSError:
    font = big = ImageFont.load_default()


def px(x, z):
    return M + (x - b["x1"]) * S, M + (z - b["z1"]) * S


COLORS = {"central_plaza": (210, 190, 150), "palace": (200, 60, 50), "spiritual": (240, 240, 250), "military": (90, 90, 100),
          "stables": (170, 130, 80), "residential": (245, 245, 235), "clan": (150, 110, 160), "relic": (60, 90, 170),
          "market": (230, 150, 60), "crafting": (110, 80, 60), "gardens": (120, 180, 100), "undercity": (40, 40, 40)}

# ground
dr.rectangle([px(b["x1"], b["z1"]), px(b["x2"] + 1, b["z2"] + 1)], fill=(196, 206, 160))
for d in plan["districts"]:
    key = d["id"].split(".", 1)[1]
    col = COLORS.get(key)
    if col is None:
        continue
    alpha = 60 if d.get("hidden") else 150
    for p in d.get("parts", []):
        dr.rectangle([px(p["x1"], p["z1"]), px(p["x2"] + 1, p["z2"] + 1)], fill=col + (alpha,),
                     outline=(40, 40, 40, 200) if not d.get("hidden") else (40, 40, 40, 120), width=1)
    if "circle" in d.get("shape", {}):
        c = d["shape"]["circle"]
        dr.ellipse([px(c["x"] - c["r"], c["z"] - c["r"]), px(c["x"] + c["r"] + 1, c["z"] + c["r"] + 1)], fill=col + (230,),
                   outline=(80, 60, 40), width=2)

# canal + ponds + bridges
can = plan["water"]["canal"]
cw = can["width"] * S
for (x1, z1), (x2, z2) in zip(can["points"], can["points"][1:]):
    dr.line([px(x1, z1), px(x2, z2)], fill=(70, 130, 200), width=cw)
for e in can["ends"]:
    dr.ellipse([px(e["x"] - e["radius"], e["z"] - e["radius"]), px(e["x"] + e["radius"], e["z"] + e["radius"])], fill=(70, 130, 200))
# roads
widths = {"main": 9, "cross": 7, "secondary": 5, "lane": 3}
for r in plan["roads"]:
    for (x1, z1), (x2, z2) in zip(r["points"], r["points"][1:]):
        dr.line([px(x1, z1), px(x2, z2)], fill=(150, 120, 80), width=widths[r["class"]] * S)
for br in plan["water"]["bridges"]:
    x, z, w = br["x"], br["z"], br["width"]
    vertical = abs(z) > 80
    if vertical:
        dr.rectangle([px(x - w // 2, z - br["span"] // 2), px(x + w // 2 + 1, z + br["span"] // 2 + 1)], fill=(220, 220, 220), outline=(60, 60, 60))
    else:
        dr.rectangle([px(x - br["span"] // 2, z - w // 2), px(x + br["span"] // 2 + 1, z + w // 2 + 1)], fill=(220, 220, 220), outline=(60, 60, 60))

# walls + towers + gates
wt = plan["walls"]["thickness"]
o = plan["walls"]["outline"]
dr.rectangle([px(o[0][0], o[0][1]), px(o[2][0] + 1, o[2][1] + 1)], outline=(90, 90, 90), width=wt * S)
for x, z in plan["walls"]["corner_towers"]:
    dr.rectangle([px(x - 6, z - 6), px(x + 7, z + 7)], fill=(110, 110, 110), outline=(30, 30, 30))
for g in plan["gates"]:
    half = g["width_total"] // 2
    if g["z"] in (b["z1"], b["z2"]):
        dr.rectangle([px(g["x"] - half, g["z"] - 6), px(g["x"] + half, g["z"] + 6)], fill=(170, 40, 40), outline=(20, 20, 20))
    else:
        dr.rectangle([px(g["x"] - 6, g["z"] - half), px(g["x"] + 6, g["z"] + half)], fill=(170, 40, 40), outline=(20, 20, 20))
    dr.text(px(g["x"] + 8, g["z"] - 8), g["name_en"], fill=(120, 0, 0), font=font)

# chunk grid
for x in range(b["x1"], b["x2"] + 2, 16):
    dr.line([px(x, b["z1"]), px(x, b["z2"] + 1)], fill=(0, 0, 0, 22 if x % 64 else 60), width=1)
    if x % 64 == 0:
        dr.text((px(x, b["z1"])[0] + 2, M - 14), str(x), fill=(60, 60, 60), font=font)
for z in range(b["z1"], b["z2"] + 2, 16):
    dr.line([px(b["x1"], z), px(b["x2"] + 1, z)], fill=(0, 0, 0, 22 if z % 64 else 60), width=1)
    if z % 64 == 0:
        dr.text((4, px(b["x1"], z)[1] - 6), str(z), fill=(60, 60, 60), font=font)

# slice boundary
for s in plan["slices"]:
    r = s["bounds"]
    dr.rectangle([px(r["x1"], r["z1"]), px(r["x2"] + 1, r["z2"] + 1)], outline=(220, 0, 160), width=2)
    dr.text(px(r["x1"] + 2, r["z1"] + 2), s["id"], fill=(220, 0, 160), font=font)

# landmarks + points
for l in plan["landmarks"]:
    x, y = px(l["x"], l["z"])
    dr.polygon([(x, y - 7), (x + 7, y), (x, y + 7), (x - 7, y)], fill=(250, 200, 40), outline=(60, 40, 0))
TYPE_COL = {"spawn": (0, 160, 255), "service": (0, 150, 0), "npc": (0, 120, 60), "quest_npc": (255, 120, 0), "merchant": (200, 120, 0),
            "fast_travel": (0, 200, 200), "event": (180, 0, 180), "secret": (80, 80, 80), "exit": (0, 0, 0), "dungeon": (120, 0, 0),
            "home": (120, 120, 255)}
for p in pts["points"]:
    x, y = px(p["x"], p["z"])
    dr.ellipse([x - 3, y - 3, x + 3, y + 3], fill=TYPE_COL.get(p["type"], (0, 0, 0)), outline=(255, 255, 255))

# district labels
for d in plan["districts"]:
    if d["id"] in ("district.outer_wall",):
        continue
    if "parts" in d:
        p0 = d["parts"][0]
        cx, cz = (p0["x1"] + p0["x2"]) // 2, (p0["z1"] + p0["z2"]) // 2
    else:
        c = d["shape"]["circle"]
        cx, cz = c["x"], c["z"] + c["r"] // 2
    dr.text(px(cx - 20, cz), d["name_en"], fill=(20, 20, 20), font=font)

dr.text((M, H - 105), plan["title"] + "  —  master plan v" + str(plan["version"]) + "  (north up, 1 px = 1/" + str(S) + " block)",
        fill=(30, 30, 30), font=big)
legend = "◆ landmark   ● points: blue spawn · green service · orange quest · gold merchant · cyan fast-travel · purple event · grey secret · dark-red dungeon   magenta = slice"
dr.text((M, H - 80), legend, fill=(40, 40, 40), font=font)
dr.text((M, H - 60), f"City {b['x2'] - b['x1'] + 1} x {b['z2'] - b['z1'] + 1} blocks · grid 16 (= chunks) · labels every 64 · origin = plaza centre",
        fill=(40, 40, 40), font=font)
img.save(out)
print("wrote", out, img.size)
