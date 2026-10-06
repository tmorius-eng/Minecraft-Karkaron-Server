"""Render a SÜLD blueprint (BlueprintExport text) as a voxel preview, headless.

    blender -b -P tools/blender/render_blueprint.py -- --input bp.txt --output preview.png \
        [--samples 24] [--res 1600x1000]

Builds ONE mesh with only exposed faces (hidden faces culled) and a material per block family,
then renders an isometric orthographic view with Cycles on CPU (no GPU required).
"""
import argparse
import math
import sys

import bpy
import bmesh

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument("--input", required=True)
ap.add_argument("--output", required=True)
ap.add_argument("--samples", type=int, default=24)
ap.add_argument("--res", default="1600x1000")
args = ap.parse_args(argv)

# Approximate block colours (linear-ish sRGB) by family.
PALETTE = [
    ("white_wool", (0.92, 0.92, 0.90)), ("light_blue_wool", (0.35, 0.65, 0.95)), ("orange_wool", (0.95, 0.5, 0.12)),
    ("blue_banner", (0.15, 0.3, 0.85)), ("red_carpet", (0.7, 0.12, 0.12)), ("orange_carpet", (0.95, 0.5, 0.12)),
    ("acacia_door", (0.75, 0.4, 0.2)), ("spruce", (0.42, 0.3, 0.2)), ("smoker", (0.35, 0.33, 0.32)),
    ("grass_block", (0.36, 0.6, 0.25)), ("dirt_path", (0.6, 0.5, 0.3)), ("polished_andesite", (0.55, 0.56, 0.56)),
    ("chiseled_stone_bricks", (0.5, 0.5, 0.5)), ("stone_brick", (0.47, 0.47, 0.47)), ("mossy_cobblestone", (0.4, 0.48, 0.35)),
    ("cobblestone", (0.45, 0.45, 0.45)), ("andesite", (0.52, 0.52, 0.52)), ("stone", (0.5, 0.5, 0.5)),
    ("smooth_quartz", (0.93, 0.92, 0.88)), ("quartz", (0.95, 0.94, 0.9)), ("white_concrete", (0.96, 0.96, 0.96)),
    ("gold_block", (1.0, 0.78, 0.15)), ("lantern", (1.0, 0.7, 0.3)), ("lightning_rod", (0.8, 0.5, 0.3)),
]


def family(block):
    name = block.split("[", 1)[0].removeprefix("minecraft:")
    for i, (key, _) in enumerate(PALETTE):
        if name == key or name.startswith(key):
            return i
    return len(PALETTE)


blocks = {}
with open(args.input, encoding="utf-8") as fh:
    for line in fh:
        if line.startswith("#") or not line.strip():
            continue
        x, y, z, b = line.split(maxsplit=3)
        name = b.strip()
        if "carpet" in name or "banner" in name or "lightning_rod" in name:
            continue  # thin decorations: skip in the voxel preview
        blocks[(int(x), int(y), int(z))] = family(name)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
mats = []
for key, rgb in PALETTE + [("other", (0.8, 0.0, 0.8))]:
    m = bpy.data.materials.new(key)
    m.use_nodes = True
    bsdf = m.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
    bsdf.inputs["Roughness"].default_value = 0.9
    if key == "lantern":
        bsdf.inputs["Emission Color"].default_value = (1.0, 0.7, 0.3, 1.0)
        bsdf.inputs["Emission Strength"].default_value = 4.0
    mats.append(m)

mesh = bpy.data.meshes.new("blueprint")
bm = bmesh.new()
FACES = [((1, 0, 0), [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)]), ((-1, 0, 0), [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)]),
         ((0, 1, 0), [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)]), ((0, -1, 0), [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]),
         ((0, 0, 1), [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]), ((0, 0, -1), [(0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)])]
vcache = {}


def vert(p):
    v = vcache.get(p)
    if v is None:
        # Minecraft (x, y up, z south) -> Blender (x, -z, y up)
        v = bm.verts.new((p[0], -p[2], p[1]))
        vcache[p] = v
    return v


faces = 0
for (x, y, z), fam in blocks.items():
    for (nx, ny, nz), corners in FACES:
        if (x + nx, y + ny, z + nz) in blocks:
            continue
        f = bm.faces.new([vert((x + cx, y + cy, z + cz)) for cx, cy, cz in corners])
        f.material_index = fam
        faces += 1
bm.to_mesh(mesh)
bm.free()
obj = bpy.data.objects.new("blueprint", mesh)
for m in mats:
    obj.data.materials.append(m)
scene.collection.objects.link(obj)

xs = [p[0] for p in blocks]; zs = [p[2] for p in blocks]
span = max(max(xs) - min(xs), max(zs) - min(zs))
cam_data = bpy.data.cameras.new("cam")
cam_data.type = "ORTHO"
cam_data.ortho_scale = span * 1.25
cam_data.clip_start = 0.1
cam_data.clip_end = 5000
cam = bpy.data.objects.new("cam", cam_data)
cam.rotation_euler = (math.radians(55), 0, math.radians(45))
# Aim at the bounding-box centre: view direction of this rotation is (-sin45*sin55, cos45*sin55, -cos55).
d = (-math.sin(math.radians(45)) * math.sin(math.radians(55)), math.cos(math.radians(45)) * math.sin(math.radians(55)),
     -math.cos(math.radians(55)))
cx_b, cy_b = (max(xs) + min(xs)) / 2, -(max(zs) + min(zs)) / 2
dist = span * 2
cam.location = (cx_b - d[0] * dist, cy_b - d[1] * dist, 4 - d[2] * dist)
scene.collection.objects.link(cam)
scene.camera = cam

sun_data = bpy.data.lights.new("sun", "SUN")
sun_data.energy = 3.2
sun_data.angle = math.radians(2)
sun = bpy.data.objects.new("sun", sun_data)
sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(30))
scene.collection.objects.link(sun)
world = bpy.data.worlds.new("w")
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.55, 0.7, 0.95, 1.0)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.6
scene.world = world

w, h = (int(v) for v in args.res.split("x"))
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = args.samples
scene.cycles.use_denoising = False
scene.view_layers[0].cycles.use_denoising = False
scene.render.resolution_x, scene.render.resolution_y = w, h
scene.render.filepath = args.output
scene.view_settings.view_transform = "Standard"
bpy.ops.render.render(write_still=True)
print(f"rendered {len(blocks)} blocks / {faces} exposed faces -> {args.output}")
