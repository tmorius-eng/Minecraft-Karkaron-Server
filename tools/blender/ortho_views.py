"""Headless Blender: orthographic front / side / top reference views of a mesh, plus its bounding box and a
height profile along its length. Used to take proportions off a Meshy reference mesh before a cuboid rig is
authored (docs/ASSET_PRODUCTION_PIPELINE.md stage 6). The mesh is only measured and rendered; nothing is exported.

Run:
  blender -b -P tools/blender/ortho_views.py -- \
      --input assets/sources/<task>.glb --out-dir <dir> --stem khasar_meshy \
      [--length 3.4] [--px-per-block 64] [--samples 8] [--bins 17] [--forward auto|+x|-x|+y|-y]

Conventions (Blender space, Z up): the creature's length axis is the longer horizontal axis (or --forward). The
mesh is re-oriented so that its forward is Blender −Y (the "front" camera looks at its face from −Y), recentred
on the floor (min Z = 0, centred in X/Y) and, with --length, uniformly scaled so that its length is that many
blocks. Every view has a 1-block grid drawn into the image (block lines dark, the origin lines red), so the
renders can be read off directly.

Printed lines (parse-friendly):
  SULD_BBOX raw_dims=(x,y,z) dims_blocks=(width,length,height) scale=s
  SULD_PROFILE i z_from z_to top bottom width   (one per length bin, front → back, in blocks)
  SULD_VIEW <path> px_per_block=N origin_px=(u,v)
"""
from __future__ import annotations

import math
import os
import sys


def _args(argv):
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--stem", default="ortho")
    ap.add_argument("--length", type=float, default=None, help="normalise the length to this many blocks")
    ap.add_argument("--px-per-block", type=int, default=64)
    ap.add_argument("--samples", type=int, default=8)
    ap.add_argument("--bins", type=int, default=17)
    ap.add_argument("--forward", default="auto", help="mesh forward axis in Blender space: auto, +x, -x, +y, -y")
    return ap.parse_args(argv)


def _import(path):
    import bpy
    ext = os.path.splitext(path)[1].lower()
    if ext in (".glb", ".gltf"):
        bpy.ops.import_scene.gltf(filepath=path)
    elif ext == ".obj":
        bpy.ops.wm.obj_import(filepath=path)
    elif ext == ".fbx":
        bpy.ops.import_scene.fbx(filepath=path)
    else:
        raise SystemExit(f"unsupported input: {ext}")


def _grid(path, ppb, origin_uv):
    """Draw a 1-block grid into a rendered PNG (Blender's bundled numpy; image rows are bottom-up)."""
    import bpy
    import numpy as np
    img = bpy.data.images.load(path)
    w, h = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)
    ou, ov = origin_uv  # top-left based pixel coords of the world origin
    for u in range(w):
        d = (u - ou) / ppb
        if abs(d - round(d)) * ppb < 0.5:
            col = (0.75, 0.1, 0.1, 1) if round(d) == 0 else (0.15, 0.15, 0.18, 1)
            mask = px[:, u, 3] < 0.5
            px[mask, u] = col
    for v in range(h):
        d = (v - ov) / ppb
        if abs(d - round(d)) * ppb < 0.5:
            col = (0.75, 0.1, 0.1, 1) if round(d) == 0 else (0.15, 0.15, 0.18, 1)
            row = h - 1 - v
            mask = px[row, :, 3] < 0.5
            px[row, mask] = col
    # neutral background behind the transparent film
    bg = px[:, :, 3] < 0.5
    px[bg] = (0.55, 0.55, 0.57, 1)
    px[:, :, 3] = 1
    img.pixels[:] = px.ravel()
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()


def main():
    import bpy
    from mathutils import Matrix, Vector

    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    a = _args(argv)
    os.makedirs(a.out_dir, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    _import(a.input)
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    if not meshes:
        raise SystemExit("no mesh objects")
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes:
        o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    for o in list(bpy.context.scene.objects):
        if o is not obj and o.type != "MESH":
            bpy.data.objects.remove(o, do_unlink=True)
    obj.parent = None
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    vs = [v.co.copy() for v in obj.data.vertices]
    mn = Vector((min(v.x for v in vs), min(v.y for v in vs), min(v.z for v in vs)))
    mx = Vector((max(v.x for v in vs), max(v.y for v in vs), max(v.z for v in vs)))
    raw = mx - mn
    fwd = a.forward
    if fwd == "auto":
        fwd = "-y" if raw.y >= raw.x else "+x"
    # rotate so that the forward axis becomes −Y
    rot = {"-y": 0.0, "+y": math.pi, "+x": -math.pi / 2, "-x": math.pi / 2}[fwd]
    obj.data.transform(Matrix.Rotation(rot, 4, "Z"))
    vs = [v.co.copy() for v in obj.data.vertices]
    mn = Vector((min(v.x for v in vs), min(v.y for v in vs), min(v.z for v in vs)))
    mx = Vector((max(v.x for v in vs), max(v.y for v in vs), max(v.z for v in vs)))
    centre = Vector(((mn.x + mx.x) / 2, (mn.y + mx.y) / 2, mn.z))
    length = mx.y - mn.y
    s = (a.length / length) if a.length else 1.0
    obj.data.transform(Matrix.Scale(s, 4) @ Matrix.Translation(-centre))
    obj.data.update()
    vs = [v.co.copy() for v in obj.data.vertices]
    mn = Vector((min(v.x for v in vs), min(v.y for v in vs), min(v.z for v in vs)))
    mx = Vector((max(v.x for v in vs), max(v.y for v in vs), max(v.z for v in vs)))
    dims = mx - mn
    tris = sum(len(p.vertices) - 2 for p in obj.data.polygons)
    print(f"SULD_BBOX raw_dims=({raw.x:.4f},{raw.y:.4f},{raw.z:.4f}) forward={fwd} "
          f"dims_blocks=({dims.x:.3f},{dims.y:.3f},{dims.z:.3f}) scale={s:.5f} tris={tris} "
          f"min=({mn.x:.3f},{mn.y:.3f},{mn.z:.3f}) max=({mx.x:.3f},{mx.y:.3f},{mx.z:.3f})")

    # height / width profile along the length (front = −Y first)
    n = max(1, a.bins)
    step = dims.y / n
    for i in range(n):
        y0 = mn.y + i * step
        y1 = y0 + step
        sel = [v for v in vs if y0 <= v.y <= y1]
        if not sel:
            print(f"SULD_PROFILE {i} {y0:.3f} {y1:.3f} - - -")
            continue
        top = max(v.z for v in sel)
        bot = min(v.z for v in sel)
        wid = max(v.x for v in sel) - min(v.x for v in sel)
        print(f"SULD_PROFILE {i} {y0 - mn.y:.3f} {y1 - mn.y:.3f} top={top:.3f} bottom={bot:.3f} width={wid:.3f}")

    if not obj.data.materials:
        mat = bpy.data.materials.new("Fallback")
        mat.use_nodes = True
        mat.node_tree.nodes.get("Principled BSDF").inputs["Base Color"].default_value = (0.5, 0.5, 0.55, 1)
        obj.data.materials.append(mat)

    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.device = "CPU"
    sc.cycles.samples = a.samples
    sc.cycles.use_denoising = False
    sc.view_layers[0].cycles.use_denoising = False
    sc.render.film_transparent = True
    world = bpy.data.worlds.new("W")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.2
    sc.world = world
    sun_d = bpy.data.lights.new("Sun", type="SUN")
    sun_d.energy = 3.0
    sun = bpy.data.objects.new("Sun", sun_d)
    sun.rotation_euler = (0.7, 0.3, -0.6)
    sc.collection.objects.link(sun)

    cam_d = bpy.data.cameras.new("Cam")
    cam_d.type = "ORTHO"
    cam = bpy.data.objects.new("Cam", cam_d)
    sc.collection.objects.link(cam)
    sc.camera = cam
    ppb = a.px_per_block
    pad = 0.25
    far = 20.0
    views = {
        # name: (camera location, rotation, horizontal world extent (lo, hi) left→right, vertical (lo, hi))
        "front": ((0, -far, 0), (math.pi / 2, 0, 0), (mn.x - pad, mx.x + pad), (mn.z - pad, mx.z + pad), "x", "z"),
        "side": ((far, 0, 0), (math.pi / 2, 0, math.pi / 2), (mn.y - pad, mx.y + pad), (mn.z - pad, mx.z + pad), "y", "z"),
        "top": ((0, 0, far), (0, 0, 0), (mn.x - pad, mx.x + pad), (mn.y - pad, mx.y + pad), "x", "y"),
    }
    for name, (loc, r, (h0, h1), (v0, v1), ha, va) in views.items():
        # snap extents to whole blocks so the grid lands on pixel lines
        h0, h1, v0, v1 = math.floor(h0), math.ceil(h1), math.floor(v0), math.ceil(v1)
        wpx, hpx = int((h1 - h0) * ppb), int((v1 - v0) * ppb)
        sc.render.resolution_x, sc.render.resolution_y = wpx, hpx
        cam_d.ortho_scale = max(h1 - h0, v1 - v0)
        cx, cy = (h0 + h1) / 2, (v0 + v1) / 2
        if name == "front":
            cam.location = (cx, -far, cy)
            origin = (int((0 - h0) * ppb), int((v1 - 0) * ppb))
        elif name == "side":
            # camera at +X looking −X: screen right = +Y (the creature's back), left = front
            cam.location = (far, cx, cy)
            origin = (int((0 - h0) * ppb), int((v1 - 0) * ppb))
        else:
            cam.location = (cx, cy, far)
            origin = (int((0 - h0) * ppb), int((v1 - 0) * ppb))
        cam.rotation_euler = r
        cam_d.clip_end = 100
        path = os.path.abspath(os.path.join(a.out_dir, f"{a.stem}_{name}.png"))
        sc.render.filepath = path
        bpy.ops.render.render(write_still=True)
        _grid(path, ppb, origin)
        print(f"SULD_VIEW {path} px_per_block={ppb} origin_px={origin}")


if __name__ == "__main__":
    main()
