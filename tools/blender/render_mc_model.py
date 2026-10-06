"""Headless Blender preview of a Minecraft (Blockbench-exported) model JSON.

Rebuilds each `elements[]` cuboid (from/to in 0..16 model space, optional
rotation) as a Blender box, applies the referenced texture as a box-projected
material, and renders a Cycles CPU still with a transparent background. This
gives a faithful preview of the real in-game geometry without a GPU or the
Blockbench WebGPU renderer.

Run:
  blender -b -P tools/blender/render_mc_model.py -- \
      --model resourcepack/assets/suld/models/item/foo.json \
      --texture resourcepack/assets/suld/textures/item/foo.png \
      --out assets/previews/foo.png --res 512 --samples 48
"""
from __future__ import annotations

import json
import os
import sys


def _args(argv):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--texture", default=None)
    ap.add_argument("--out", required=True)
    ap.add_argument("--res", type=int, default=512)
    ap.add_argument("--samples", type=int, default=48)
    return ap.parse_args(argv)


def main():
    import bpy
    import math
    from mathutils import Vector

    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    a = _args(argv)
    model = json.load(open(a.model))

    bpy.ops.wm.read_factory_settings(use_empty=True)

    # Material from the texture (box projection), or a neutral steel fallback.
    mat = bpy.data.materials.new("SuldMat")
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Metallic"].default_value = 0.6
    bsdf.inputs["Roughness"].default_value = 0.45
    if a.texture and os.path.exists(a.texture):
        img = bpy.data.images.load(a.texture)
        img.colorspace_settings.name = "sRGB"
        tex = mat.node_tree.nodes.new("ShaderNodeTexImage")
        tex.image = img
        tex.projection = "BOX"
        tex.interpolation = "Closest"  # crisp pixel-art look
        coord = mat.node_tree.nodes.new("ShaderNodeTexCoord")
        mat.node_tree.links.new(tex.inputs["Vector"], coord.outputs["Generated"])
        mat.node_tree.links.new(bsdf.inputs["Base Color"], tex.outputs["Color"])
    else:
        bsdf.inputs["Base Color"].default_value = (0.6, 0.64, 0.7, 1)

    centre = Vector((8, 8, 8)) / 16.0
    for i, el in enumerate(model.get("elements", [])):
        f = [c / 16.0 for c in el["from"]]
        t = [c / 16.0 for c in el["to"]]
        size = [t[j] - f[j] for j in range(3)]
        loc = [(f[j] + t[j]) / 2.0 - centre[j] for j in range(3)]
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        cube = bpy.context.active_object
        cube.name = el.get("name", f"el{i}")
        cube.scale = (size[0], size[1], size[2])
        rot = el.get("rotation")
        if rot and rot.get("angle"):
            axis = {"x": 0, "y": 1, "z": 2}[rot.get("axis", "y")]
            pivot = [rot["origin"][j] / 16.0 - centre[j] for j in range(3)]
            bpy.context.scene.cursor.location = pivot
            bpy.ops.object.origin_set(type="ORIGIN_CURSOR")
            cube.rotation_euler[axis] = math.radians(rot["angle"])
        cube.data.materials.append(mat)

    # Camera + lights.
    cam_data = bpy.data.cameras.new("Cam")
    cam = bpy.data.objects.new("Cam", cam_data)
    bpy.context.scene.collection.objects.link(cam)
    cam.location = (2.2, -2.2, 1.6)
    cam.rotation_euler = (1.02, 0, 0.785)
    cam_data.ortho_scale = 2.4
    cam_data.type = "ORTHO"
    bpy.context.scene.camera = cam
    key = bpy.data.lights.new("Key", type="AREA"); key.energy = 400; key.size = 5
    ko = bpy.data.objects.new("Key", key); ko.location = (4, -4, 6); ko.rotation_euler = (0.5, 0.1, 0.5)
    bpy.context.scene.collection.objects.link(ko)

    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = a.samples
    sc.cycles.use_denoising = False
    sc.view_layers[0].cycles.use_denoising = False
    sc.render.resolution_x = a.res
    sc.render.resolution_y = a.res
    sc.render.film_transparent = True
    os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
    sc.render.filepath = a.out
    bpy.ops.render.render(write_still=True)
    print(f"SULD_RENDER {a.out} elements={len(model.get('elements', []))}")


main()
