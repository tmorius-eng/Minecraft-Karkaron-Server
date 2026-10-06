#!/usr/bin/env python3
"""Generate assets/world/kharkhorum/slices/slice-1.json (Kharkhorum slice 1) from the master plan.

    python3 tools/world/gen_slice1.py

Slice 1 = the ceremonial axis + the complete outer wall: four gates, corner towers, wall towers about
every 48 blocks, the U-shaped inner canal with its bridges, streets to every gate, the plaza and
monument, the palace terrace front, one market street, one ger block, the forge and the sky shrine.
Re-run after editing; the output is deterministic.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WORLD = os.path.join(ROOT, "assets", "world", "kharkhorum")
plan = json.load(open(os.path.join(WORLD, "master-plan.json"), encoding="utf-8"))
pts = json.load(open(os.path.join(WORLD, "points.json"), encoding="utf-8"))

W_X1, W_Z1, W_X2, W_Z2 = -176, -160, 175, 127          # wall centre lines (master plan outline)
GATES = {"south": 0, "west": 0, "east": 0, "north": 104}  # along-wall coordinate of each gate
P = []


def add(id, module, at, district=None, rotate=0, mirror=False, params=None, landmark=False, overlap=False):
    d = {"id": id, "module": module, "at": at}
    if rotate: d["rotate"] = rotate
    if mirror: d["mirror"] = True
    if district: d["district"] = district
    if params: d["params"] = params
    if landmark: d["landmark"] = True
    if overlap: d["allow_overlap"] = True
    P.append(d)


# ------------------------------------------------------------------ outer wall
def side(name, a0, a1, gate_at, gate_half, tower_every=48):
    """Obstacles (gate, wall towers, corner towers) along one side, walls in between.
    Returns [(kind, centre, half)] sorted, and wall gaps [(from, to)] inclusive."""
    obstacles = [("corner", a0, 7), ("corner", a1, 7), ("gate", gate_at, gate_half)]
    t = a0 + tower_every
    while t < a1 - 20:
        if abs(t - gate_at) > gate_half + 12:
            obstacles.append(("tower", t, 4))
        t += tower_every
    obstacles.sort(key=lambda o: o[1])
    walls = []
    for (k1, c1, h1), (k2, c2, h2) in zip(obstacles, obstacles[1:]):
        lo, hi = c1 + h1 + 1, c2 - h2 - 1
        if hi >= lo: walls.append((lo, hi))
    return obstacles, walls


# south (z = 127), outer face +z: no rotation, walls run +x; the Imperial Gate is 35 wide (half 17)
obs, walls = side("south", W_X1, W_X2, 0, 17)
for k, c, h in obs:
    if k == "tower": add(f"tower.south_{c}", "tower.watch", [c, 0, W_Z2], "district.outer_wall")
for lo, hi in walls:
    add(f"wall.south_{lo}", "wall.straight", [lo, 0, W_Z2], "district.outer_wall", params={"length": hi - lo + 1})
add("gate.south", "gate.imperial", [0, 0, W_Z2], "district.outer_wall", landmark=True)

# north (z = -160), outer face -z: rotate 180 (local +x -> -x), each wall starts at its east end
obs, walls = side("north", W_X1, W_X2, GATES["north"], 12)
for k, c, h in obs:
    if k == "tower": add(f"tower.north_{c}", "tower.watch", [c, 0, W_Z1], "district.outer_wall", rotate=180)
for lo, hi in walls:
    add(f"wall.north_{lo}", "wall.straight", [hi, 0, W_Z1], "district.outer_wall", rotate=180, params={"length": hi - lo + 1})
add("gate.north", "gate.side", [GATES["north"], 0, W_Z1], "district.outer_wall", rotate=180)

# west (x = -176), outer face -x: rotate 90 (local +x -> +z), each wall starts at its north end
obs, walls = side("west", W_Z1, W_Z2, GATES["west"], 12)
for k, c, h in obs:
    if k == "tower": add(f"tower.west_{c}", "tower.watch", [W_X1, 0, c], "district.outer_wall", rotate=90)
for lo, hi in walls:
    add(f"wall.west_{lo}", "wall.straight", [W_X1, 0, lo], "district.outer_wall", rotate=90, params={"length": hi - lo + 1})
add("gate.west", "gate.side", [W_X1, 0, GATES["west"]], "district.outer_wall", rotate=90)

# east (x = 175), outer face +x: rotate 270 (local +x -> -z), each wall starts at its south end
obs, walls = side("east", W_Z1, W_Z2, GATES["east"], 12)
for k, c, h in obs:
    if k == "tower": add(f"tower.east_{c}", "tower.watch", [W_X2, 0, c], "district.outer_wall", rotate=270)
for lo, hi in walls:
    add(f"wall.east_{lo}", "wall.straight", [W_X2, 0, hi], "district.outer_wall", rotate=270, params={"length": hi - lo + 1})
add("gate.east", "gate.side", [W_X2, 0, GATES["east"]], "district.outer_wall", rotate=270)

# corner towers: 13 x 13 x ~30, door toward the city
add("tower.corner_sw", "tower.watch", [W_X1, 0, W_Z2], "district.outer_wall", params={"half": 6, "door_x": 4})
add("tower.corner_se", "tower.watch", [W_X2, 0, W_Z2], "district.outer_wall", params={"half": 6, "door_x": -4})
add("tower.corner_nw", "tower.watch", [W_X1, 0, W_Z1], "district.outer_wall", rotate=180, params={"half": 6, "door_x": -4})
add("tower.corner_ne", "tower.watch", [W_X2, 0, W_Z1], "district.outer_wall", rotate=180, params={"half": 6, "door_x": 4})

# ------------------------------------------------------------------ streets
avoid = [[p["x"], p["z"]] for p in pts["points"] if p["district"] == "district.central_plaza"]
add("road.avenue_south_gate", "road.paved", [0, 0, 0], "district.outer_wall", params={"points": [[0, 118], [0, 102]], "width": 9, "class": "main", "lanterns": 8})
add("road.avenue_south", "road.paved", [0, 0, 0], "district.central_plaza", params={"points": [[0, 86], [0, 29]], "width": 9, "class": "main", "lanterns": 8})
add("road.gate_south_out", "road.paved", [0, 0, 0], "district.outer_wall", params={"points": [[0, 133], [0, 136]], "width": 9, "class": "main"})
add("road.avenue_north", "road.paved", [0, 0, 0], "district.central_plaza", params={"points": [[0, -29], [0, -40]], "width": 9, "class": "main", "lanterns": 8})
add("road.market_street", "road.paved", [0, 0, 0], "district.market", params={"points": [[29, 0], [104, 0]], "width": 7, "class": "cross"})
add("road.cross_east", "road.paved", [0, 0, 0], "district.market", params={"points": [[105, 0], [146, 0]], "width": 7, "class": "cross"})
add("road.craft_lane_2", "road.paved", [0, 0, 0], "district.crafting", params={"points": [[5, 76], [140, 76]], "width": 3, "class": "lane"})
add("road.cross_east_gate", "road.paved", [0, 0, 0], "district.outer_wall", params={"points": [[156, 0], [170, 0]], "width": 7, "class": "cross"})
add("road.gate_east_out", "road.paved", [0, 0, 0], "district.outer_wall", params={"points": [[181, 0], [188, 0]], "width": 7, "class": "cross"})
add("road.cross_west", "road.paved", [0, 0, 0], "district.residential", params={"points": [[-30, 0], [-147, 0]], "width": 7, "class": "cross", "lanterns": 10})
add("road.cross_west_gate", "road.paved", [0, 0, 0], "district.outer_wall", params={"points": [[-157, 0], [-171, 0]], "width": 7, "class": "cross"})
add("road.gate_west_out", "road.paved", [0, 0, 0], "district.outer_wall", params={"points": [[-182, 0], [-189, 0]], "width": 7, "class": "cross"})
add("road.ne_diagonal", "road.paved", [0, 0, 0], "district.military", params={"points": [[20, -24], [60, -44], [104, -44], [104, -155]], "width": 5, "class": "secondary", "lanterns": 12})
add("road.gate_north_out", "road.paved", [0, 0, 0], "district.outer_wall", params={"points": [[104, -166], [104, -173]], "width": 5, "class": "secondary"})
add("road.yurt_lane", "road.paved", [0, 0, 0], "district.residential", params={"points": [[-60, 4], [-60, 52], [-96, 52]], "width": 3, "class": "lane"})
add("road.shrine_way", "road.paved", [0, 0, 0], "district.spiritual", params={"points": [[-96, -4], [-96, -62]], "width": 3, "class": "lane"})
add("road.craft_lane", "road.paved", [0, 0, 0], "district.crafting", params={"points": [[5, 60], [39, 60]], "width": 3, "class": "lane"})
add("road.palace_axis", "road.paved", [0, 0, 0], "district.palace", params={"points": [[0, -69], [0, -72]], "width": 9, "class": "main"})
add("road.fountain_w", "road.paved", [0, 0, 0], "district.palace", params={"points": [[-8, -69], [-8, -86]], "width": 3, "class": "secondary"})
add("road.fountain_e", "road.paved", [0, 0, 0], "district.palace", params={"points": [[8, -69], [8, -86]], "width": 3, "class": "secondary"})

# ------------------------------------------------------------------ inner canal (U) and bridges
add("canal.south", "canal.segment", [-152, 0, 94], "district.gardens",
    params={"length": 304, "cap_start": True, "cap_end": True,
            "rail_gaps": [[146, 158], [65, 79], [225, 239]]})       # grand bridge x 0, garden bridges x -80/80
for x, name in [(-152, "west"), (151, "east")]:
    add(f"canal.{name}", "canal.segment", [x, 0, -36], "district.gardens", rotate=90, overlap=True,
        params={"length": 126, "cap_start": True, "rail_gaps": [[30, 42]]})   # bridge at z 0
add("bridge.grand", "bridge.arched", [0, 0, 94], "district.gardens", landmark=True)
add("bridge.garden_w", "bridge.arched", [-80, 0, 94], "district.gardens", params={"half_width": 2})
add("bridge.garden_e", "bridge.arched", [80, 0, 94], "district.gardens", params={"half_width": 2})
add("bridge.west", "bridge.arched", [-152, 0, 0], "district.residential", rotate=90, params={"half_width": 3})
add("bridge.east", "bridge.arched", [151, 0, 0], "district.market", rotate=90, params={"half_width": 3})

# ------------------------------------------------------------------ plaza, monument, props
add("plaza.central", "plaza.ceremonial", [0, 1, 0], "district.central_plaza", params={"radius": 28, "avoid": avoid})
add("monument.khan", "monument.equestrian", [0, 1, 0], "district.central_plaza", landmark=True)
add("prop.class_stones", "prop.class_stones", [-14, 1, 8], "district.central_plaza")
add("prop.relay_plaza", "prop.relay_post", [-18, 1, -12], "district.central_plaza")
add("prop.relay_gate", "prop.relay_post", [10, 0, 112], "district.outer_wall")
add("prop.relay_west", "prop.relay_post", [-163, 0, 8], "district.outer_wall")
add("prop.relay_east", "prop.relay_post", [162, 0, 8], "district.outer_wall", rotate=180)
add("prop.relay_north", "prop.relay_post", [112, 0, -149], "district.outer_wall")

# ------------------------------------------------------------------ palace terrace front
add("stair.grand", "stair.grand", [0, 0, -40], "district.palace", params={"rise": 8})
add("terrace.front", "wall.retaining", [-72, 0, -57], "district.palace", params={"length": 144, "height": 8, "gaps": [[66, 78]]})
add("terrace.west", "wall.retaining", [-72, 0, -149], "district.palace", rotate=90, params={"length": 92, "height": 8})
add("terrace.east", "wall.retaining", [71, 0, -58], "district.palace", rotate=270, params={"length": 92, "height": 8})
add("terrace.north", "wall.retaining", [71, 0, -150], "district.palace", rotate=180, params={"length": 144, "height": 8})
add("palace.gate", "palace.gate", [0, 8, -62], "district.palace", landmark=True, params={"wall_length": 24})
add("palace.hall", "palace.hall", [0, 8, -112], "district.palace", landmark=True)
add("fountain.silver_tree", "fountain.silver_tree", [0, 8, -77], "district.palace", landmark=True)

# ------------------------------------------------------------------ market, gers, forge, shrine
add("market.street_east", "market.street", [28, 0, 0], "district.market", params={"length": 72})
add("market.street_grain", "market.street", [105, 0, 0], "district.market", params={"length": 40})

# ------------------------------------------------------------------ crafting: workshop street (lane 2, z 76)
for i, x in enumerate([56, 70, 84, 98, 112, 126]):
    add(f"workshop.n{x}", "shop.house", [x, 0, 73], "district.crafting", params={"roof": "hip" if i % 2 else "flat"})
    if abs(x - 80) > 8:   # keep clear of the garden bridge at x 80
        add(f"workshop.s{x}", "shop.house", [x, 0, 79], "district.crafting", rotate=180, params={"roof": "flat" if i % 2 else "hip"})

# ------------------------------------------------------------------ residential: khashaa yards (one ger each)
import random
rng = random.Random(1220)
kinds = ["firepit", "cart", "hay", "woodpile", "drying", "hitching"]
for x in [-136, -120, -104]:
    for z in [24, 40, 56, 72]:
        size = "medium" if rng.random() < 0.55 else "small"
        add(f"khashaa.{x}_{z}", "yard.fence", [x, 0, z], "district.residential", params={"x1": -6, "z1": -6, "x2": 6, "z2": 6, "gates": [[0, 6]]})
        add(f"ger.k{x}_{z}", "yurt", [x - 1, 0, z - 1], "district.residential", params={"size": size})
        add(f"camp.k{x}_{z}", "prop.camp", [x + 4, 0, z + 3], "district.residential", params={"kind": kinds[rng.randrange(len(kinds))]})
for x in [-80, -42]:
    add(f"khashaa.{x}_72", "yard.fence", [x, 0, 72], "district.residential", params={"x1": -7, "z1": -6, "x2": 7, "z2": 6, "gates": [[0, 6]]})
    add(f"ger.k{x}_72", "yurt", [x - 2, 0, 71], "district.residential", params={"size": "medium"})
    add(f"camp.k{x}_72", "prop.camp", [x + 4, 0, 75], "district.residential", params={"kind": "woodpile"})

# ------------------------------------------------------------------ clan district: a walled compound with the clan hall ger
add("clan.compound", "yard.fence", [-116, 0, -24], "district.clan", params={"x1": -13, "z1": -13, "x2": 13, "z2": 15, "gates": [[0, 15]]})
add("clan.hall", "yurt", [-116, 0, -26], "district.clan", params={"size": "large"})
add("clan.ger_w", "yurt", [-124, 0, -14], "district.clan", rotate=270, params={"size": "small"})
add("clan.ger_e", "yurt", [-108, 0, -14], "district.clan", rotate=90, params={"size": "small"})
add("clan.cairn", "prop.cairn", [-127, 0, -34], "district.clan")

# ------------------------------------------------------------------ military and stables (north-east)
for i, z in enumerate([-66, -88, -110, -132]):
    add(f"barracks.w{z}", "shop.house", [96, 0, z], "district.military", rotate=270, params={"roof": "hip"})
add("military.command", "yurt", [118, 0, -78], "district.military", rotate=90, params={"size": "large"})
add("military.yard", "yard.fence", [118, 0, -112], "district.military", params={"x1": -9, "z1": -14, "x2": 9, "z2": 14, "gates": [[-9, 0]]})
add("military.hitch_1", "prop.camp", [118, 0, -118], "district.military", params={"kind": "hitching"})
add("military.hitch_2", "prop.camp", [118, 0, -104], "district.military", params={"kind": "hitching"})
add("military.tower", "tower.watch", [118, 0, -138], "district.military", rotate=180)
add("stables.yard", "yard.fence", [148, 0, -128], "district.stables", params={"x1": -16, "z1": -18, "x2": 16, "z2": 18, "gates": [[-16, 0], [0, -18]]})
for j, (x, z) in enumerate([(140, -140), (156, -140), (140, -116), (156, -116)]):
    add(f"stables.hitch_{j}", "prop.camp", [x, 0, z], "district.stables", params={"kind": "hitching"})
add("stables.hay_1", "prop.camp", [148, 0, -128], "district.stables", params={"kind": "hay"})
add("stables.hay_2", "prop.camp", [136, 0, -128], "district.stables", params={"kind": "hay"})
add("stables.cart", "prop.camp", [160, 0, -128], "district.stables", params={"kind": "cart"})
for id, size, x, z, rot in [("ger.a", "medium", -74, 25, 0), ("ger.b", "small", -83, 22, 270), ("ger.c", "large", -44, 25, 90),
                            ("ger.d", "small", -40, 35, 90), ("ger.e", "medium", -86, 43, 0), ("ger.f", "small", -74, 44, 0),
                            ("ger.g", "medium", -46, 50, 90)]:
    add(id, "yurt", [x, 1, z], "district.residential", rotate=rot, params={"size": size})
add("yard.1", "yard.fence", [-76, 1, 24], "district.residential", params={"x1": -11, "z1": -9, "x2": 10, "z2": 9, "gates": [[10, 0], [0, 9]]})
add("yard.2", "yard.fence", [-43, 1, 28], "district.residential", params={"x1": -10, "z1": -14, "x2": 10, "z2": 14, "gates": [[-10, 0]]})
add("yard.3", "yard.fence", [-80, 1, 43], "district.residential", params={"x1": -13, "z1": -6, "x2": 13, "z2": 7, "gates": [[0, 7]]})
for id, kind, x, z in [("camp.fire_1", "firepit", -70, 18), ("camp.cart_1", "cart", -82, 29), ("camp.hay_1", "hay", -68, 31), ("camp.wood_1", "woodpile", -84, 31),
                       ("camp.fire_2", "firepit", -37, 17), ("camp.hitch_2", "hitching", -48, 39), ("camp.dry_2", "drying", -37, 40),
                       ("camp.fire_3", "firepit", -80, 40), ("camp.cart_4", "cart", -38, 52), ("camp.hay_4", "hay", -38, 46), ("camp.hitch_4", "hitching", -52, 57)]:
    add(id, "prop.camp", [x, 1, z], "district.residential", rotate=90 if id == "camp.cart_1" else 0, params={"kind": kind})
add("blacksmith.forge", "blacksmith.forge", [44, 0, 64], "district.crafting", rotate=90)
add("shrine.sky", "shrine.sky", [-96, 4, -72], "district.spiritual", landmark=True)
add("cairn.1", "prop.cairn", [-93, 0, -20], "district.spiritual")
add("cairn.2", "prop.cairn", [-99, 0, -40], "district.spiritual")

# ------------------------------------------------------------------ landscaping
G = lambda id, d, **p: add(id, "landscape.grove", [0, 0, 0], d, params=p)
G("grove.garden_east", "district.gardens", rect=[10, 103, 140, 120], count=20, spacing=6, species=["cherry", "cherry", "elm"], flowers=5,
  avoid=[[6, 108, 14, 116], [-6, 100, 6, 124], [74, 100, 86, 124]])
G("grove.garden_west", "district.gardens", rect=[-140, 103, -10, 120], count=20, spacing=6, species=["cherry", "cherry", "elm"], flowers=5,
  avoid=[[-6, 100, 6, 124], [-86, 100, -74, 124], [-63, 118, -51, 124]])
G("grove.avenue_east", "district.central_plaza", rect=[9, 33, 10, 86], count=7, spacing=7, species=["elm", "cherry"], flowers=2, avoid=[[5, 58, 40, 62]])
G("grove.avenue_west", "district.central_plaza", rect=[-10, 33, -9, 86], count=7, spacing=7, species=["elm", "cherry"], flowers=2)
G("grove.residential", "district.residential", rect=[-92, 57, -34, 62], count=5, spacing=8, species=["birch", "elm"], flowers=3,
  avoid=[[-96, 51, -60, 53], [-51, 45, -41, 55], [-55, 54, -49, 60], [-88, 65, -34, 80], [-111, 49, -97, 63]])
G("grove.crafting", "district.crafting", rect=[14, 64, 34, 72], count=3, spacing=8, species=["elm"], flowers=2, avoid=[[5, 58, 40, 62], [38, 56, 50, 72]])
G("grove.spiritual", "district.spiritual", rect=[-150, -150, -82, -46], count=18, spacing=8, species=["larch", "larch", "birch"], flowers=2,
  avoid=[[-107, -83, -85, -61], [-98, -64, -94, -4], [-100, -42, -90, -18], [-160, -41, -144, -30]])
G("grove.palace_east", "district.palace", rect=[30, -145, 62, -70], count=10, spacing=8, species=["elm", "cherry"], flowers=3)
G("grove.palace_west", "district.palace", rect=[-62, -145, -30, -70], count=10, spacing=8, species=["elm", "cherry"], flowers=3)
G("grove.east_wilds", "district.military", rect=[126, -100, 165, -50], count=6, spacing=10, species=["larch", "birch"], flowers=2,
  avoid=[[100, -160, 108, -40], [146, -41, 156, -30]])

slice_ = {
    "city": "kharkhorum", "slice": "slice.1", "version": 2, "seed": 1220,
    "title": "Slice 1 — ceremonial axis and the complete outer wall",
    "bounds": {"x1": -190, "z1": -174, "x2": 189, "z2": 136},
    "terrain": {"feather": 12, "clearance": 32, "surface": "minecraft:grass_block", "zones": [
        {"id": "zone.plaza", "circle": [0, 0, 28], "elevation": 1, "falloff": 0},
        {"id": "zone.palace_terrace", "rect": [-72, -150, 71, -57], "elevation": 8, "falloff": 0},
        {"id": "zone.residential", "rect": [-92, 14, -32, 60], "elevation": 1, "falloff": 2},
        {"id": "zone.west_street", "rect": [-147, -3, -30, 3], "elevation": 1, "falloff": 2},
        {"id": "zone.spiritual_hill", "circle": [-96, -72, 14], "elevation": 4, "falloff": 14}]},
    "placements": P}
out = os.path.join(WORLD, "slices", "slice-1.json")
with open(out, "w", encoding="utf-8") as fh:
    fh.write(json.dumps(slice_, ensure_ascii=False, indent=1) + "\n")
print(f"{len(P)} placements -> {out}")
