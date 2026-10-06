"""Headless Blender "hero" render for a textured source mesh: imports the mesh
(materials preserved), frames it diagonally, lights it with a key/rim/fill rig
plus a soft world light, and renders a showcase still. Pure `blender -b`.

Run:
  blender -b -P tools/blender/hero_render.py -- --input X.glb --out Y.png
"""
from __future__ import annotations
import sys, os, math


def _args(argv):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--res", type=int, default=768)
    ap.add_argument("--samples", type=int, default=96)
    return ap.parse_args(argv)


def main():
    import bpy
    from mathutils import Vector
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    a = _args(argv)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=a.input)
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes:
        o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    bpy.ops.object.origin_set(type="ORIGIN_GEOMETRY", center="BOUNDS")
    obj.location = (0, 0, 0)
    longest = max(obj.dimensions) or 1.0
    s = 2.0 / longest
    obj.scale = (s, s, s)
    bpy.ops.object.transform_apply(scale=True)
    # Tilt the blade off-axis so it reads as a 3D object, not an edge-on line.
    obj.rotation_euler = (math.radians(18), math.radians(12), math.radians(28))

    # Soft world fill so dark-blue steel is not crushed to black.
    world = bpy.data.worlds.new("W"); bpy.context.scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.05, 0.06, 0.09, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.6

    def area(name, loc, energy, size):
        d = bpy.data.lights.new(name, type="AREA"); d.energy = energy; d.size = size
        o = bpy.data.objects.new(name, d); o.location = loc
        c = o.constraints.new("TRACK_TO"); c.target = obj
        bpy.context.scene.collection.objects.link(o)
    area("Key", (3, -3, 4), 900, 4)     # warm key
    area("Rim", (-3, 2, 3), 600, 3)     # cool rim
    area("Fill", (0, -4, 1), 300, 5)    # front fill

    cam_d = bpy.data.cameras.new("Cam"); cam = bpy.data.objects.new("Cam", cam_d)
    bpy.context.scene.collection.objects.link(cam)
    cam.location = (3.2, -3.2, 1.2); bpy.context.scene.camera = cam
    tc = cam.constraints.new("TRACK_TO"); tc.target = obj

    sc = bpy.context.scene
    sc.render.engine = "CYCLES"; sc.cycles.device = "CPU"; sc.cycles.samples = a.samples
    sc.cycles.use_denoising = False; sc.view_layers[0].cycles.use_denoising = False
    sc.render.resolution_x = a.res; sc.render.resolution_y = a.res
    sc.render.film_transparent = True
    os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
    sc.render.filepath = a.out
    bpy.ops.render.render(write_still=True)
    print(f"SULD_HERO {a.out}")


main()
