"""Headless Blender: import a source mesh, normalise it, render a promo image,
and re-export clean glTF/OBJ. Pure `blender -b` — no GUI, no socket, no display.

Run:
  blender -b -P tools/blender/process_and_render.py -- \
      --input assets/sources/foo.glb \
      --render assets/previews/foo.png \
      --export-glb assets/sources/foo_clean.glb \
      --size 2.0 --samples 24 --res 512

Normalisation: joins mesh objects, recentres the origin to geometry, moves to
the world origin, and uniformly scales so the longest axis equals --size. This
gives every source asset a consistent scale/orientation for downstream work.
"""
from __future__ import annotations

import sys
import os


def _args(argv):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--render", default=None)
    ap.add_argument("--export-glb", default=None)
    ap.add_argument("--export-obj", default=None)
    ap.add_argument("--size", type=float, default=2.0)
    ap.add_argument("--samples", type=int, default=24)
    ap.add_argument("--res", type=int, default=512)
    return ap.parse_args(argv)


def main():
    import bpy
    from mathutils import Vector

    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    a = _args(argv)

    bpy.ops.wm.read_factory_settings(use_empty=True)

    ext = os.path.splitext(a.input)[1].lower()
    if ext == ".glb" or ext == ".gltf":
        bpy.ops.import_scene.gltf(filepath=a.input)
    elif ext == ".obj":
        bpy.ops.wm.obj_import(filepath=a.input)
    elif ext == ".fbx":
        bpy.ops.import_scene.fbx(filepath=a.input)
    else:
        raise SystemExit(f"Unsupported input format: {ext}")

    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    if not meshes:
        raise SystemExit("No mesh objects imported")

    # Join into one object.
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes:
        o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    obj.name = "SuldAsset"

    # Recentre origin to geometry, move to world origin.
    bpy.ops.object.origin_set(type="ORIGIN_GEOMETRY", center="BOUNDS")
    obj.location = (0, 0, 0)

    # Uniform scale so the longest dimension == size.
    dims = obj.dimensions
    longest = max(dims.x, dims.y, dims.z) or 1.0
    scale = a.size / longest
    obj.scale = (scale, scale, scale)
    bpy.ops.object.transform_apply(scale=True)

    tris = sum(len(p.vertices) - 2 for p in obj.data.polygons)
    print(f"SULD_QC verts={len(obj.data.vertices)} polys={len(obj.data.polygons)} "
          f"tris={tris} materials={len(obj.data.materials)} "
          f"dims=({obj.dimensions.x:.3f},{obj.dimensions.y:.3f},{obj.dimensions.z:.3f})")

    if a.render:
        os.makedirs(os.path.dirname(a.render) or ".", exist_ok=True)
        # Fallback material if the import had none, so the render is not black.
        if not obj.data.materials:
            mat = bpy.data.materials.new("SuldFallback")
            mat.use_nodes = True
            mat.node_tree.nodes.get("Principled BSDF").inputs["Base Color"].default_value = (0.5, 0.5, 0.55, 1)
            obj.data.materials.append(mat)

        cam_data = bpy.data.cameras.new("Cam")
        cam = bpy.data.objects.new("Cam", cam_data)
        bpy.context.scene.collection.objects.link(cam)
        cam.location = (a.size * 1.8, -a.size * 1.8, a.size * 1.3)
        cam.rotation_euler = (1.09, 0, 0.785)
        bpy.context.scene.camera = cam

        sun_data = bpy.data.lights.new("Sun", type="SUN")
        sun_data.energy = 4.0
        sun = bpy.data.objects.new("Sun", sun_data)
        sun.location = (a.size * 2, -a.size * 2, a.size * 4)
        sun.rotation_euler = (0.6, 0.2, 0.3)
        bpy.context.scene.collection.objects.link(sun)

        sc = bpy.context.scene
        sc.render.engine = "CYCLES"
        sc.cycles.device = "CPU"
        sc.cycles.samples = a.samples
        sc.cycles.use_denoising = False  # apt Blender has no OpenImageDenoise
        sc.view_layers[0].cycles.use_denoising = False
        sc.render.resolution_x = a.res
        sc.render.resolution_y = a.res
        sc.render.film_transparent = True
        sc.render.filepath = a.render
        bpy.ops.render.render(write_still=True)
        print(f"SULD_RENDER {a.render}")

    if a.export_glb:
        os.makedirs(os.path.dirname(a.export_glb) or ".", exist_ok=True)
        bpy.ops.export_scene.gltf(filepath=a.export_glb, use_selection=False)
        print(f"SULD_EXPORT_GLB {a.export_glb}")
    if a.export_obj:
        os.makedirs(os.path.dirname(a.export_obj) or ".", exist_ok=True)
        bpy.ops.wm.obj_export(filepath=a.export_obj)
        print(f"SULD_EXPORT_OBJ {a.export_obj}")

    print("SULD_BLENDER_DONE")


main()
