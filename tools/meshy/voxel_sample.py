"""Blender stage of the prop voxeliser: sample a (Meshy) GLB into coloured surface voxels.

    blender -b -P tools/meshy/voxel_sample.py -- --input assets/sources/props/stone_turtle.glb \
        --res 48 --out build/props/stone_turtle.voxels.json

Every voxel of a grid whose longest side is ``--res`` cells is kept when its centre lies within half a cell diagonal
of the surface; its colour is the base-colour texture at the nearest surface point, and its ``ao`` the share of
short rays from that point (a fixed hemisphere around the surface normal, two cells long) that hit the mesh again:
carvings, folds and grooves too shallow for a voxel still show as darker voxels. ``axis`` and ``depth`` (cells,
outwards positive) say where the true surface lies along its main normal, for the relief shading of voxel_model. Axes are written in Minecraft
model space (x east, y up, z south; glTF's +Y up becomes y). tools/meshy/voxel_model.py turns the result into a box
model.
"""
import argparse
import json
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from mathutils.interpolate import poly_3d_calc


def args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--res", type=int, default=48)
    ap.add_argument("--out", required=True)
    return ap.parse_args(argv)


def base_image(mat):
    """The image feeding the material's base colour (glTF import: Principled BSDF <- Image Texture)."""
    if mat is None or not mat.use_nodes:
        return None
    for n in mat.node_tree.nodes:
        if n.type == "BSDF_PRINCIPLED":
            link = n.inputs["Base Color"].links
            if link and link[0].from_node.type == "TEX_IMAGE":
                return link[0].from_node.image
    for n in mat.node_tree.nodes:
        if n.type == "TEX_IMAGE" and n.image is not None:
            return n.image
    return None


def hemisphere(n=16):
    """Fixed, evenly spread unit directions around +z (cosine-ish), deterministic."""
    out = []
    golden = np.pi * (3 - np.sqrt(5))
    for i in range(n):
        z = 1 - (i + 0.5) / n          # 1 .. 0
        r = np.sqrt(max(0.0, 1 - z * z))
        a = golden * i
        out.append(Vector((np.cos(a) * r, np.sin(a) * r, z)))
    return out


def ao_at(tree, loc, normal, length, dirs):
    """Share of the hemisphere rays from just above the surface that hit the mesh within ``length``."""
    n = normal.normalized()
    t = Vector((1, 0, 0)) if abs(n.x) < 0.9 else Vector((0, 1, 0))
    b1 = n.cross(t).normalized()
    b2 = n.cross(b1)
    start = loc + n * (length * 0.02)
    hits = 0
    for d in dirs:
        w = b1 * d.x + b2 * d.y + n * d.z
        if tree.ray_cast(start, w, length)[0] is not None:
            hits += 1
    return hits / len(dirs)


def main():
    a = args()
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=a.input)
    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    deps = bpy.context.evaluated_depsgraph_get()
    bm = bmesh.new()
    mats = []
    for o in meshes:
        me = o.evaluated_get(deps).to_mesh()
        me.transform(o.matrix_world)
        offset = len(mats)
        mats += [base_image(m) for m in o.data.materials] or [None]
        tmp = bmesh.new()
        tmp.from_mesh(me)
        for f in tmp.faces:
            f.material_index += offset
        tmp_me = bpy.data.meshes.new("tmp")
        tmp.to_mesh(tmp_me)
        bm.from_mesh(tmp_me)
        tmp.free()
        o.evaluated_get(deps).to_mesh_clear()
    bmesh.ops.triangulate(bm, faces=bm.faces[:])
    bm.faces.ensure_lookup_table()
    uv = bm.loops.layers.uv.active
    pixels = {}
    for i, img in enumerate(mats):
        if img is not None and i not in pixels:
            w, h = img.size
            buf = np.empty(w * h * 4, dtype=np.float32)
            img.pixels.foreach_get(buf)
            pixels[i] = (buf.reshape(h, w, 4), w, h)
    tree = BVHTree.FromBMesh(bm)
    xs = [v.co.x for v in bm.verts]
    ys = [v.co.y for v in bm.verts]
    zs = [v.co.z for v in bm.verts]
    lo = Vector((min(xs), min(ys), min(zs)))
    hi = Vector((max(xs), max(ys), max(zs)))
    size = hi - lo
    cell = max(size) / a.res
    n = [max(1, int(np.ceil(size[k] / cell))) for k in range(3)]
    reach = cell * 0.87
    dirs = hemisphere()
    vox = []
    for ix in range(n[0]):
        for iy in range(n[1]):
            for iz in range(n[2]):
                c = lo + Vector(((ix + 0.5) * cell, (iy + 0.5) * cell, (iz + 0.5) * cell))
                loc, normal, fi, dist = tree.find_nearest(c, reach)
                if loc is None:
                    continue
                f = bm.faces[fi]
                rgb = [0.6, 0.6, 0.6]
                px = pixels.get(f.material_index)
                if px is not None and uv is not None:
                    w3 = poly_3d_calc([l.vert.co for l in f.loops], loc)
                    u = sum(wt * l[uv].uv.x for wt, l in zip(w3, f.loops))
                    v = sum(wt * l[uv].uv.y for wt, l in zip(w3, f.loops))
                    arr, w, h = px
                    x = int((u % 1.0) * (w - 1))
                    y = int((v % 1.0) * (h - 1))
                    rgb = [float(t) for t in arr[y, x, :3]]
                ao = ao_at(tree, loc, normal, cell * 2.0, dirs)
                # Blender z up -> model y up; Blender -y (glTF forward) -> model z
                # where the true surface lies along the main normal axis, in cells from the voxel centre (model axes):
                # the relief of carvings too shallow for a voxel
                nm = (normal.x, normal.z, -normal.y)
                axis = max(range(3), key=lambda k: abs(nm[k]))
                off = (loc - c)
                offm = (off.x, off.z, -off.y)
                depth = offm[axis] / cell * (1 if nm[axis] > 0 else -1)
                vox.append([ix, iz, n[1] - 1 - iy, *[round(t, 4) for t in rgb], round(ao, 3),
                            axis, round(depth, 3)])
    json.dump({"dims": [n[0], n[2], n[1]], "cell": cell, "voxels": vox}, open(a.out, "w"))
    print(f"voxels: {len(vox)} in {n[0]}x{n[2]}x{n[1]}")


main()
