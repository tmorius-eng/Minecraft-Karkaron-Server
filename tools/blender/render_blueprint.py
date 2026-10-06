"""Render SÜLD voxel dumps (compiled modules, slices, or blocks read back from a real world), headless.

    blender -b -P tools/blender/render_blueprint.py -- --input dump.txt --output out.png [options]
    blender -b -P tools/blender/render_blueprint.py -- --input dump.txt --views views.json --outdir renders/

Input: lines "x y z minecraft:block[state]" (Minecraft axes: +x east, +y up, +z south); "#" lines are ignored.

Options:
  --samples N        Cycles samples (default 32)
  --res WxH          resolution (default 1600x1000)
  --views FILE       JSON list of views: {"name", "eye": [x,y,z], "target": [x,y,z], "lens": 35, "night": false}
                     or {"name", "iso": true} for the default isometric overview
  --crop x1,z1,x2,z2 only render blocks inside this column range (faster close-ups)

Geometry: one mesh, with block shapes approximated (slabs, stairs, fences, walls, panes, carpets,
lanterns, campfires, doors, trapdoors, ladders, plants). Hidden faces between full cubes are
culled. Colours are approximate per-block averages, not textures. Light-emitting blocks are
emissive, so night views show the real lighting layout.
"""
import argparse
import json
import math
import os
import sys

import bmesh
import bpy

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument("--input", required=True)
ap.add_argument("--output")
ap.add_argument("--views")
ap.add_argument("--outdir", default=".")
ap.add_argument("--samples", type=int, default=32)
ap.add_argument("--res", default="1600x1000")
ap.add_argument("--crop")
args = ap.parse_args(argv)

# ---------------------------------------------------------------------------------------------
# colours (sRGB 0..255, approximate texture averages)
C = {
    "stone_bricks": (122, 121, 122), "cracked_stone_bricks": (114, 113, 114), "mossy_stone_bricks": (112, 120, 100),
    "chiseled_stone_bricks": (118, 117, 118), "stone": (125, 125, 125), "smooth_stone": (159, 159, 159),
    "cobblestone": (127, 127, 127), "mossy_cobblestone": (110, 118, 95),
    "tuff_bricks": (98, 102, 95), "polished_tuff": (97, 104, 99), "tuff": (108, 109, 102), "chiseled_tuff_bricks": (98, 102, 95),
    "mud_bricks": (137, 103, 79), "packed_mud": (142, 106, 79), "mud": (60, 57, 60),
    "polished_andesite": (132, 135, 133), "andesite": (136, 136, 136), "polished_diorite": (192, 193, 194),
    "polished_granite": (154, 106, 89), "granite": (149, 103, 85),
    "polished_deepslate": (72, 72, 73), "deepslate_tiles": (54, 54, 55), "deepslate_bricks": (70, 70, 71),
    "cobbled_deepslate": (77, 77, 80), "chiseled_deepslate": (54, 54, 54), "deepslate": (80, 80, 82),
    "gold_block": (246, 208, 61), "raw_gold_block": (221, 169, 46),
    "cut_copper": (191, 106, 80), "waxed_cut_copper": (191, 106, 80), "copper_block": (192, 107, 79),
    "waxed_copper_block": (192, 107, 79),
    "stripped_mangrove_log": (119, 54, 47), "mangrove_planks": (117, 54, 48), "mangrove_log": (84, 66, 41),
    "stripped_dark_oak_log": (72, 56, 36), "dark_oak_log": (60, 46, 26), "dark_oak_planks": (66, 43, 20),
    "dark_oak_wood": (60, 46, 26), "stripped_dark_oak_wood": (72, 56, 36),
    "spruce_planks": (114, 84, 48), "spruce_log": (58, 37, 16), "stripped_spruce_log": (115, 89, 52),
    "spruce_wood": (58, 37, 16),
    "oak_planks": (162, 130, 78), "oak_log": (109, 85, 50), "stripped_oak_log": (177, 144, 86),
    "birch_planks": (192, 175, 121), "birch_log": (216, 215, 210),
    "acacia_planks": (168, 90, 50), "acacia_log": (103, 96, 86), "stripped_acacia_log": (174, 92, 59),
    "cherry_planks": (226, 178, 172), "cherry_log": (54, 33, 44), "stripped_cherry_log": (215, 145, 148),
    "jungle_planks": (160, 115, 80),
    "white_wool": (233, 236, 236), "light_blue_wool": (58, 175, 217), "orange_wool": (240, 118, 19),
    "red_wool": (160, 39, 34), "yellow_wool": (248, 197, 39), "black_wool": (20, 21, 25),
    "blue_wool": (53, 57, 157), "green_wool": (84, 109, 27), "lime_wool": (112, 185, 25),
    "brown_wool": (114, 71, 40), "gray_wool": (62, 68, 71), "light_gray_wool": (142, 142, 134),
    "cyan_wool": (21, 137, 145), "purple_wool": (121, 42, 172), "pink_wool": (237, 141, 172),
    "magenta_wool": (189, 68, 179),
    "white_terracotta": (209, 178, 161), "terracotta": (152, 94, 67), "red_terracotta": (143, 61, 46),
    "orange_terracotta": (161, 83, 37), "yellow_terracotta": (186, 133, 35), "light_gray_terracotta": (135, 106, 97),
    "brown_terracotta": (77, 51, 35), "cyan_terracotta": (86, 91, 91), "blue_terracotta": (74, 59, 91),
    "black_terracotta": (37, 23, 16), "gray_terracotta": (57, 42, 35), "light_blue_terracotta": (113, 108, 137),
    "white_concrete": (207, 213, 214), "red_concrete": (142, 32, 32), "light_blue_concrete": (35, 137, 198),
    "grass_block": (98, 146, 62), "dirt": (134, 96, 67), "dirt_path": (148, 122, 65), "coarse_dirt": (119, 85, 59),
    "rooted_dirt": (144, 103, 76), "podzol": (91, 63, 24), "gravel": (131, 127, 126), "sand": (219, 207, 163),
    "moss_block": (89, 109, 45), "clay": (160, 166, 179),
    "water": (40, 85, 190), "lava": (207, 92, 20), "ice": (145, 183, 253),
    "cherry_leaves": (229, 172, 194), "spruce_leaves": (48, 82, 50), "birch_leaves": (110, 150, 70),
    "oak_leaves": (64, 122, 42), "azalea_leaves": (90, 115, 44), "flowering_azalea_leaves": (110, 120, 70),
    "dark_oak_leaves": (50, 100, 30),
    "polished_blackstone": (53, 48, 56), "blackstone": (42, 36, 41), "gilded_blackstone": (86, 66, 42),
    "polished_blackstone_bricks": (48, 42, 49), "chiseled_polished_blackstone": (53, 48, 56),
    "calcite": (223, 224, 220), "smooth_quartz": (235, 229, 222), "quartz_block": (235, 229, 222),
    "quartz_pillar": (235, 230, 224), "bone_block": (229, 225, 207), "bricks": (150, 97, 83),
    "iron_bars": (110, 110, 110), "iron_block": (220, 220, 220), "chain": (55, 58, 68), "anvil": (68, 68, 68),
    "chipped_anvil": (68, 68, 68), "barrel": (130, 95, 55), "chest": (160, 110, 40), "trapped_chest": (160, 110, 40),
    "hay_block": (166, 139, 12), "crafting_table": (120, 78, 45), "smithing_table": (56, 58, 70),
    "blast_furnace": (100, 100, 100), "smoker": (95, 85, 75), "furnace": (110, 110, 110), "grindstone": (140, 140, 140),
    "cauldron": (73, 72, 74), "water_cauldron": (73, 72, 74), "lava_cauldron": (180, 80, 20), "composter": (110, 75, 40),
    "bookshelf": (117, 94, 59), "decorated_pot": (140, 80, 60), "flower_pot": (124, 68, 53), "loom": (140, 115, 90),
    "cartography_table": (90, 70, 50), "fletching_table": (180, 160, 110), "lectern": (150, 110, 60),
    "stonecutter": (120, 115, 112), "bell": (230, 190, 70), "target": (225, 170, 160),
    "brown_stained_glass": (102, 76, 51), "white_stained_glass": (240, 240, 240), "glass": (200, 220, 230),
    "ladder": (125, 98, 57), "lily_pad": (32, 128, 48), "pink_petals": (240, 170, 200), "short_grass": (90, 140, 55),
    "tall_grass": (90, 140, 55), "fern": (80, 130, 50), "poppy": (200, 40, 30), "dandelion": (240, 220, 40),
    "cornflower": (70, 100, 200), "azure_bluet": (200, 220, 220), "oxeye_daisy": (220, 220, 200),
    "blue_orchid": (40, 160, 220), "allium": (170, 100, 220), "lily_of_the_valley": (230, 230, 230),
    "sweet_berry_bush": (60, 100, 40), "dead_bush": (130, 100, 60), "azalea": (90, 115, 44),
    "lantern": (220, 160, 80), "soul_lantern": (120, 200, 220), "campfire": (110, 80, 50), "soul_campfire": (80, 110, 120),
    "end_rod": (235, 230, 220), "torch": (230, 190, 90), "wall_torch": (230, 190, 90), "glowstone": (230, 190, 110),
    "sea_lantern": (190, 210, 205), "shroomlight": (240, 150, 80), "jack_o_lantern": (220, 140, 30),
    "lightning_rod": (190, 110, 80), "candle": (220, 200, 160), "redstone_lamp": (180, 120, 80),
    "melon": (110, 145, 30), "pumpkin": (200, 120, 25), "carved_pumpkin": (200, 120, 25),
    "white_banner": (230, 230, 230), "red_banner": (160, 39, 34),
}
EMIT = {"lantern": 14, "soul_lantern": 8, "campfire": 15, "soul_campfire": 8, "end_rod": 12, "torch": 12,
        "wall_torch": 12, "glowstone": 14, "sea_lantern": 14, "shroomlight": 14, "jack_o_lantern": 14, "lava": 14,
        "lava_cauldron": 14, "redstone_lamp": 0, "candle": 6}
SUFFIXES = ["_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_door", "_trapdoor", "_pane", "_carpet",
            "_button", "_pressure_plate", "_wall_sign", "_hanging_sign", "_sign", "_wall_banner", "_banner"]


def base_material(name):
    if name in C:
        return name
    for suf in SUFFIXES:
        if name.endswith(suf):
            b = name[: -len(suf)]
            for cand in (b, b + "s", b + "_planks", b + "_block", b + "_wool" if suf == "_carpet" else b):
                if cand in C:
                    return cand
            if suf == "_carpet" and b + "_wool" in C:
                return b + "_wool"
    if name.startswith("potted_"):
        return "flower_pot"
    if name.startswith("waxed_"):
        return base_material(name[6:])
    if name.endswith("_wool") or name.endswith("_carpet"):
        return "white_wool"
    return None


def parse(block):
    name = block.split("[", 1)[0].removeprefix("minecraft:")
    props = {}
    if "[" in block:
        for kv in block[block.index("[") + 1:-1].split(","):
            if "=" in kv:
                k, v = kv.split("=", 1)
                props[k] = v
    return name, props


# ---------------------------------------------------------------------------------------------
# shapes → list of boxes (x0,y0,z0,x1,y1,z1) in block-local coordinates (z south)
Q = {"NW": (0, 0, .5, .5), "NE": (.5, 0, 1, .5), "SW": (0, .5, .5, 1), "SE": (.5, .5, 1, 1)}
ROT = {"north": 0, "east": 1, "south": 2, "west": 3}
CW = {"NW": "NE", "NE": "SE", "SE": "SW", "SW": "NW"}


def stair_boxes(p):
    f = p.get("facing", "north")
    shape = p.get("shape", "straight")
    quads = {"straight": ["NW", "NE"], "outer_left": ["NW"], "outer_right": ["NE"],
             "inner_left": ["NW", "NE", "SW"], "inner_right": ["NW", "NE", "SE"]}.get(shape, ["NW", "NE"])
    for _ in range(ROT.get(f, 0)):
        quads = [CW[q] for q in quads]
    top = p.get("half") == "top"
    boxes = [(0, .5, 0, 1, 1, 1) if top else (0, 0, 0, 1, .5, 1)]
    for q in quads:
        x0, z0, x1, z1 = Q[q]
        boxes.append((x0, 0, z0, x1, .5, z1) if top else (x0, .5, z0, x1, 1, z1))
    return boxes


def side_panel(f, t):
    # panel of thickness t on the side the block faces AWAY from (doors/ladders sit against the far side)
    return {"north": (0, 0, 1 - t, 1, 1, 1), "south": (0, 0, 0, 1, 1, t),
            "east": (0, 0, 0, t, 1, 1), "west": (1 - t, 0, 0, 1, 1, 1)}.get(f, (0, 0, 0, 1, 1, t))


def connections(p, post, arm, h):
    a0, a1 = arm
    boxes = [post]
    if p.get("north") not in (None, "false", "none"):
        boxes.append((a0, 0, 0, a1, h, .5))
    if p.get("south") not in (None, "false", "none"):
        boxes.append((a0, 0, .5, a1, h, 1))
    if p.get("west") not in (None, "false", "none"):
        boxes.append((0, 0, a0, .5, h, a1))
    if p.get("east") not in (None, "false", "none"):
        boxes.append((.5, 0, a0, 1, h, a1))
    return boxes


def shape(name, p):
    """None = full cube (culled); else list of boxes."""
    if name.endswith("_stairs"):
        return stair_boxes(p)
    if name.endswith("_slab"):
        t = p.get("type", "bottom")
        return None if t == "double" else [(0, .5, 0, 1, 1, 1)] if t == "top" else [(0, 0, 0, 1, .5, 1)]
    if name.endswith("_fence"):
        return connections(p, (.375, 0, .375, .625, 1, .625), (.4375, .5625), .9)
    if name.endswith("_wall") and "sign" not in name and "banner" not in name and "torch" not in name:
        post = (.25, 0, .25, .75, 1, .75) if p.get("up", "true") == "true" else (.3125, 0, .3125, .6875, .875, .6875)
        return connections(p, post, (.3125, .6875), .875)
    if name.endswith("_pane") or name == "iron_bars":
        return connections(p, (.4375, 0, .4375, .5625, 1, .5625), (.4375, .5625), 1)
    if name.endswith("_carpet") or name in ("pink_petals", "lily_pad"):
        return [(0, 0, 0, 1, .0625, 1)]
    if name in ("lantern", "soul_lantern"):
        return [(.3125, .0625, .3125, .6875, .625, .6875)] if p.get("hanging") == "true" else [(.3125, 0, .3125, .6875, .5625, .6875)]
    if name.endswith("campfire"):
        return [(0, 0, 0, 1, .4375, 1), (.3, .4375, .3, .7, .95, .7)]
    if name in ("end_rod", "lightning_rod", "chain"):
        return [(.4375, 0, .4375, .5625, 1, .5625)]
    if name in ("torch",):
        return [(.4375, 0, .4375, .5625, .625, .5625)]
    if name.endswith("_door"):
        return [side_panel(p.get("facing", "north"), .1875)]
    if name.endswith("_trapdoor"):
        if p.get("open") == "true":
            return [side_panel(p.get("facing", "north"), .1875)]
        return [(0, .8125, 0, 1, 1, 1)] if p.get("half") == "top" else [(0, 0, 0, 1, .1875, 1)]
    if name == "ladder":
        return [side_panel(p.get("facing", "north"), .0625)]
    if name.endswith("_fence_gate"):
        return [(0, .3, .4375, 1, 1, .5625)] if p.get("facing") in ("north", "south") else [(.4375, .3, 0, .5625, 1, 1)]
    if name in ("short_grass", "fern", "dead_bush", "sweet_berry_bush") or name.endswith("_tulip") or name in (
            "poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "blue_orchid", "allium", "lily_of_the_valley",
            "azalea"):
        return [(.25, 0, .25, .75, .6, .75)]
    if name in ("tall_grass", "large_fern"):
        return [(.2, 0, .2, .8, 1, .8)]
    if name in ("chest", "trapped_chest"):
        return [(.0625, 0, .0625, .9375, .875, .9375)]
    if name in ("flower_pot",) or name.startswith("potted_"):
        return [(.3125, 0, .3125, .6875, .375, .6875), (.4, .375, .4, .6, .8, .6)]
    if name in ("anvil", "chipped_anvil", "damaged_anvil"):
        return [(.125, 0, .125, .875, .25, .875), (.3, .25, .2, .7, .6, .8), (0, .6, .19, 1, 1, .81)]
    if name in ("grindstone",):
        return [(.25, 0, .125, .75, .9, .875)]
    if name in ("cauldron", "water_cauldron", "lava_cauldron", "composter"):
        return [(0, 0, 0, 1, 1, 1)]
    if name in ("bell",):
        return [(.25, .25, .25, .75, .9, .75)]
    if name.endswith("_banner") or name.endswith("_sign") or name.endswith("_button"):
        return []
    if name in ("water",):
        return "water"
    return None


# ---------------------------------------------------------------------------------------------
blocks = {}
crop = [int(v) for v in args.crop.split(",")] if args.crop else None
with open(args.input, encoding="utf-8") as fh:
    for line in fh:
        if line.startswith("#") or not line.strip():
            continue
        x, y, z, b = line.split(maxsplit=3)
        x, y, z = int(x), int(y), int(z)
        if crop and not (crop[0] <= x <= crop[2] and crop[1] <= z <= crop[3]):
            continue
        name, props = parse(b.strip())
        if name in ("air", "cave_air", "void_air", "light", "structure_void", "barrier"):
            continue
        blocks[(x, y, z)] = (name, props)

mat_index = {}
mat_list = []


def material_for(name):
    key = base_material(name) or "other"
    if name in EMIT:
        key = name
    if key not in mat_index:
        mat_index[key] = len(mat_list)
        mat_list.append(key)
    return mat_index[key]


bpy.ops.wm.read_factory_settings(use_empty=True)
full = {}
for pos, (name, props) in blocks.items():
    sh = shape(name, props)
    full[pos] = sh  # None = full cube; "water"; list of boxes

bm = bmesh.new()
vcache = {}


def vert(p):
    v = vcache.get(p)
    if v is None:
        v = bm.verts.new((p[0], -p[2], p[1]))  # Minecraft (x, y up, z south) -> Blender (x, -z, y up)
        vcache[p] = v
    return v


CUBE = [((1, 0, 0), [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)]), ((-1, 0, 0), [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)]),
        ((0, 1, 0), [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)]), ((0, -1, 0), [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]),
        ((0, 0, 1), [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]), ((0, 0, -1), [(0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)])]
faces = 0
for (x, y, z), sh in full.items():
    name, _ = blocks[(x, y, z)]
    mi = material_for(name)
    if sh is None or sh == "water":
        top = .875 if sh == "water" else 1
        for (nx, ny, nz), corners in CUBE:
            nb = full.get((x + nx, y + ny, z + nz), 0)
            if sh is None and nb is None:
                continue
            if sh == "water" and (nb == "water" or (nb is None and ny != 1)):
                continue
            try:
                f = bm.faces.new([vert((x + cx, y + (cy * top), z + cz)) for cx, cy, cz in corners])
            except ValueError:
                continue
            f.material_index = mi
            faces += 1
        continue
    for (x0, y0, z0, x1, y1, z1) in sh:
        for (nx, ny, nz), corners in CUBE:
            pts = [(x + (x1 if cx else x0), y + (y1 if cy else y0), z + (z1 if cz else z0)) for cx, cy, cz in corners]
            try:
                f = bm.faces.new([vert(p) for p in pts])
            except ValueError:
                continue
            f.material_index = mi
            faces += 1

mesh = bpy.data.meshes.new("voxels")
bm.to_mesh(mesh)
bm.free()

scene = bpy.context.scene
obj = bpy.data.objects.new("voxels", mesh)
scene.collection.objects.link(obj)

materials = {}
for key in mat_list:
    m = bpy.data.materials.new(key)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    rgb = C.get(key, (200, 0, 200))
    lin = tuple((c / 255.0) ** 2.2 for c in rgb)
    bsdf.inputs["Base Color"].default_value = (*lin, 1.0)
    bsdf.inputs["Roughness"].default_value = 0.85
    if key in ("gold_block", "iron_block", "bell") or "copper" in key:
        bsdf.inputs["Metallic"].default_value = 0.8
        bsdf.inputs["Roughness"].default_value = 0.35
    if key == "water":
        bsdf.inputs["Roughness"].default_value = 0.05
        bsdf.inputs["Alpha"].default_value = 0.8
    materials[key] = m
    obj.data.materials.append(m)


def set_emission(strength_scale):
    for key, m in materials.items():
        bsdf = m.node_tree.nodes["Principled BSDF"]
        level = EMIT.get(key, 0)
        if level:
            rgb = C.get(key, (255, 200, 120))
            lin = tuple((c / 255.0) ** 2.2 for c in rgb)
            bsdf.inputs["Emission Color"].default_value = (*lin, 1.0)
            bsdf.inputs["Emission Strength"].default_value = level / 15.0 * strength_scale
        else:
            bsdf.inputs["Emission Strength"].default_value = 0.0


xs = [p[0] for p in blocks]; ys = [p[1] for p in blocks]; zs = [p[2] for p in blocks]
bbox = (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs))

sun_data = bpy.data.lights.new("sun", "SUN")
sun_data.angle = math.radians(2)
sun = bpy.data.objects.new("sun", sun_data)
sun.rotation_euler = (math.radians(48), math.radians(12), math.radians(35))
scene.collection.objects.link(sun)
world = bpy.data.worlds.new("w")
world.use_nodes = True
scene.world = world
bg = world.node_tree.nodes["Background"]


def lighting(night):
    if night:
        sun_data.energy = 0.08
        sun_data.color = (0.6, 0.7, 1.0)
        bg.inputs["Color"].default_value = (0.012, 0.016, 0.04, 1)
        bg.inputs["Strength"].default_value = 0.5
        set_emission(60.0)
    else:
        sun_data.energy = 3.4
        sun_data.color = (1.0, 0.96, 0.9)
        bg.inputs["Color"].default_value = (0.55, 0.70, 0.95, 1)
        bg.inputs["Strength"].default_value = 0.65
        set_emission(4.0)


cam_data = bpy.data.cameras.new("cam")
cam_data.clip_start = 0.1
cam_data.clip_end = 5000
cam = bpy.data.objects.new("cam", cam_data)
scene.collection.objects.link(cam)
scene.camera = cam


def mc_to_bl(p):
    return (p[0], -p[2], p[1])


def aim(eye, target):
    from mathutils import Vector
    e = Vector(mc_to_bl(eye)); t = Vector(mc_to_bl(target))
    cam.location = e
    cam.rotation_euler = (t - e).to_track_quat("-Z", "Y").to_euler()


def iso():
    span = max(bbox[3] - bbox[0], bbox[5] - bbox[2], 1)
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = span * 1.15
    cx, cz = (bbox[0] + bbox[3]) / 2, (bbox[2] + bbox[5]) / 2
    d = span * 2
    # from the south-east, looking north-west and down: the reference image's "from the south" reading
    eye = (cx + d * 0.45, bbox[4] + d * 0.75, cz + d * 0.85)
    aim(eye, (cx, (bbox[1] + bbox[4]) / 3, cz))


w, h = (int(v) for v in args.res.split("x"))
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = args.samples
scene.render.resolution_x, scene.render.resolution_y = w, h
scene.view_settings.view_transform = "Standard"
try:
    scene.cycles.use_denoising = True
    scene.cycles.denoiser = "OPENIMAGEDENOISE"
except Exception:
    scene.cycles.use_denoising = False

views = []
if args.views:
    with open(args.views, encoding="utf-8") as fh:
        views = json.load(fh)
else:
    views = [{"name": os.path.splitext(os.path.basename(args.output or "render.png"))[0], "iso": True}]

os.makedirs(args.outdir, exist_ok=True)
for v in views:
    lighting(v.get("night", False))
    if v.get("iso"):
        iso()
    else:
        cam_data.type = "PERSP"
        cam_data.lens = v.get("lens", 30)
        aim(v["eye"], v["target"])
    out = args.output if (args.output and not args.views) else os.path.join(args.outdir, v["name"] + ".png")
    scene.render.filepath = out
    try:
        bpy.ops.render.render(write_still=True)
    except RuntimeError:
        scene.cycles.use_denoising = False
        bpy.ops.render.render(write_still=True)
    print(f"rendered {v['name']} -> {out}")
print(f"{len(blocks)} blocks / {faces} faces / {len(mat_list)} materials; unknown colours: "
      f"{sorted({blocks[p][0] for p in blocks if base_material(blocks[p][0]) is None and blocks[p][0] not in EMIT})[:40]}")
