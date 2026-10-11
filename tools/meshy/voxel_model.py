#!/usr/bin/env python3
"""Second stage of the prop voxeliser: coloured surface voxels -> a Minecraft box model.

    python3 tools/meshy/voxel_model.py build/props/<id>.voxels.json --id <id> [--colors 40]

1. The inside of the shell is filled (whatever the outside cannot reach), so boxes can be large.
2. Surface colours are reduced to a small palette (median cut) and smoothed once (each voxel takes the most common
   colour of its surface neighbours), so neighbouring voxels share a colour and merge.
3. Voxels of one colour are merged greedily into boxes (inside voxels take the nearest surface colour).
4. A face is left out when every voxel in front of it is filled (it can never be seen).

Writes resourcepack/assets/suld/{models,items}/prop/<id>.json and textures/prop/<id>.png (one pixel per palette
colour), adds textures/prop/ to the block and item atlases (a texture outside every atlas renders as the magenta and
black "missing" checkerboard), and writes the prop's collision cells (the world blocks its solid voxels fill, at the
height content/props.json gives it) to suld-plugin/src/main/resources/content/prop_shapes.json. Deterministic.
"""
from __future__ import annotations

import argparse
import json
import os
from collections import deque

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RP = os.path.join(ROOT, "resourcepack", "assets", "suld")
ATLASES = os.path.join(ROOT, "resourcepack", "assets", "minecraft", "atlases")
CONTENT = os.path.join(ROOT, "suld-plugin", "src", "main", "resources", "content")
FILL = 0.2  # a world block is solid when this share of it is filled by the prop's voxels
AO_STRENGTH = 0.3
RELIEF_WINDOW = 5   # voxels: the neighbourhood the relief is measured against
RELIEF_GAIN = 0.0   # brightness per cell of relief (off: on Meshy meshes it mostly adds voxel-step noise)
FACES = {  # face -> (axis, side) for occlusion
    "east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}


def load(path):
    d = json.load(open(path))
    nx, ny, nz = d["dims"]
    filled = np.zeros((nx, ny, nz), bool)
    rgb = np.zeros((nx, ny, nz, 3), np.float32)
    depth = np.zeros((3, nx, ny, nz), np.float32)
    has = np.zeros((3, nx, ny, nz), np.float32)
    for v in d["voxels"]:
        x, y, z, r, g, b = v[:6]
        ao = v[6] if len(v) > 6 else 0.0
        filled[x, y, z] = True
        # ambient occlusion from the sampler: carved and folded parts darken
        k = 1.0 - AO_STRENGTH * ao
        rgb[x, y, z] = (r * k, g * k, b * k)
        if len(v) > 8:
            depth[v[7], x, y, z] = v[8]
            has[v[7], x, y, z] = 1
    base = rgb.copy()
    # relief: how far the true surface lies in front of (or behind) the mean of its neighbours on the same face;
    # carvings go darker, raised parts lighter
    for axis in range(3):
        mean = _box(depth[axis] * has[axis], RELIEF_WINDOW) / np.maximum(_box(has[axis], RELIEF_WINDOW), 1e-6)
        rel = (depth[axis] - mean) * has[axis]
        k = np.clip(1.0 + RELIEF_GAIN * rel, 0.55, 1.3)
        sel = has[axis] > 0
        rgb[sel] *= k[sel][:, None]
    # soften the texture's speckle (granite grain is noise at this size): mean colour over the surface neighbours
    w = filled.astype(np.float32)
    num = np.stack([_box(rgb[..., i] * w, 3) for i in range(3)], axis=-1)
    den = _box(w, 3)[..., None]
    rgb = np.where(filled[..., None], num / np.maximum(den, 1e-6), rgb)
    # keep the texture's overall brightness: the shading only moves light around
    before = np.median(base[filled].mean(axis=1)) if filled.any() else 1.0
    after = np.median(rgb[filled].mean(axis=1)) if filled.any() else 1.0
    if after > 1e-6:
        rgb *= before / after
    return filled, np.clip(rgb, 0, 1)


def _box(a, w):
    """Mean-free box sum of a 3D array over a w-wide cube (integral image)."""
    r = w // 2
    p = np.pad(a, r + 1)
    c = p.cumsum(0).cumsum(1).cumsum(2)
    X, Y, Z = a.shape
    i0, i1 = np.arange(X), np.arange(X) + w
    j0, j1 = np.arange(Y), np.arange(Y) + w
    k0, k1 = np.arange(Z), np.arange(Z) + w
    def s(i, j, k):
        return c[np.ix_(i, j, k)]
    return (s(i1, j1, k1) - s(i0, j1, k1) - s(i1, j0, k1) - s(i1, j1, k0)
            + s(i0, j0, k1) + s(i0, j1, k0) + s(i1, j0, k0) - s(i0, j0, k0))


def exterior(filled):
    """Empty cells the outside reaches (6-connected flood from a one-cell border)."""
    p = np.pad(filled, 1)
    out = np.zeros_like(p)
    q = deque([(0, 0, 0)])
    out[0, 0, 0] = True
    X, Y, Z = p.shape
    while q:
        x, y, z = q.popleft()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            a, b, c = x + dx, y + dy, z + dz
            if 0 <= a < X and 0 <= b < Y and 0 <= c < Z and not out[a, b, c] and not p[a, b, c]:
                out[a, b, c] = True
                q.append((a, b, c))
    return out[1:-1, 1:-1, 1:-1]


def palette(colors: np.ndarray, n: int):
    """Median-cut palette of the surface colours: (index per colour, palette rgb 0..255)."""
    px = np.clip(colors * 255 + 0.5, 0, 255).astype(np.uint8)
    img = Image.fromarray(px.reshape(1, -1, 3), "RGB")
    q = img.quantize(colors=n, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    idx = np.asarray(q).reshape(-1)
    pal = np.array(q.getpalette()[: 3 * n], np.uint8).reshape(-1, 3)
    return idx, pal


def smooth(label, surface):
    """One mode-filter pass over the surface labels (3x3x3 neighbourhood, surface voxels only)."""
    out = label.copy()
    X, Y, Z = label.shape
    for x, y, z in zip(*np.nonzero(surface)):
        sl = (slice(max(0, x - 1), x + 2), slice(max(0, y - 1), y + 2), slice(max(0, z - 1), z + 2))
        vals = label[sl][surface[sl]]
        counts = np.bincount(vals)
        best = int(np.argmax(counts))
        if counts[best] > counts[label[x, y, z]]:
            out[x, y, z] = best
    return out


def spread(label, known, solid):
    """Give every solid voxel without a label the label of a nearest labelled neighbour (repeated dilation)."""
    label = label.copy()
    known = known.copy()
    while True:
        todo = solid & ~known
        if not todo.any():
            return label
        grew = False
        for axis in range(3):
            for step in (1, -1):
                src_known = np.roll(known, step, axis)
                src_label = np.roll(label, step, axis)
                # no wrap-around
                edge = [slice(None)] * 3
                edge[axis] = slice(0, 1) if step == 1 else slice(-1, None)
                src_known[tuple(edge)] = False
                take = todo & src_known & ~known
                if take.any():
                    label[take] = src_label[take]
                    known |= take
                    grew = True
                todo = solid & ~known
        if not grew:
            return label


def greedy(label, solid):
    """Boxes of one label: grow along x, then y, then z. Returns [(x0, y0, z0, x1, y1, z1, label)] (exclusive)."""
    used = np.zeros_like(solid)
    X, Y, Z = solid.shape
    boxes = []
    for z in range(Z):
        for y in range(Y):
            for x in range(X):
                if not solid[x, y, z] or used[x, y, z]:
                    continue
                c = label[x, y, z]
                x1 = x + 1
                while x1 < X and solid[x1, y, z] and not used[x1, y, z] and label[x1, y, z] == c:
                    x1 += 1
                y1 = y + 1
                while y1 < Y and all(solid[i, y1, z] and not used[i, y1, z] and label[i, y1, z] == c for i in range(x, x1)):
                    y1 += 1
                z1 = z + 1
                while z1 < Z:
                    blk = (slice(x, x1), slice(y, y1), z1)
                    if solid[blk].all() and not used[blk].any() and (label[blk] == c).all():
                        z1 += 1
                    else:
                        break
                used[x:x1, y:y1, z:z1] = True
                boxes.append((x, y, z, x1, y1, z1, int(c)))
    return boxes


def hidden(box, face, solid):
    """True when every voxel just outside this face of the box is solid."""
    x0, y0, z0, x1, y1, z1, _ = box
    axis, side = FACES[face]
    lo, hi = [x0, y0, z0], [x1, y1, z1]
    pos = hi[axis] if side > 0 else lo[axis] - 1
    if pos < 0 or pos >= solid.shape[axis]:
        return False
    sl = [slice(lo[i], hi[i]) for i in range(3)]
    sl[axis] = slice(pos, pos + 1)
    return bool(solid[tuple(sl)].all())


def preview(label, solid, pal, path, k=6):
    """An isometric preview of the voxels (painter's order, three-tone shading), for review."""
    X, Y, Z = solid.shape
    W, H = (X + Z) * k * 2 + 8, (X + Z) * k + Y * k * 2 + 8
    img = Image.new("RGBA", (W, H), (32, 32, 36, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    cx, cy = Z * k * 2 + 4, Y * k * 2 + 4
    order = sorted(zip(*np.nonzero(solid)), key=lambda v: (v[0] + v[2], v[1]))
    for x, y, z in order:
        col = np.array(pal[label[x, y, z]], float)
        px = cx + (x - z) * k * 2
        py = cy + (x + z) * k - y * k * 2
        top = [(px, py), (px + 2 * k, py + k), (px, py + 2 * k), (px - 2 * k, py + k)]
        left = [(px - 2 * k, py + k), (px, py + 2 * k), (px, py + 4 * k), (px - 2 * k, py + 3 * k)]
        right = [(px, py + 2 * k), (px + 2 * k, py + k), (px + 2 * k, py + 3 * k), (px, py + 4 * k)]
        for poly, f in ((left, 0.78), (right, 0.6), (top, 1.0)):
            d.polygon(poly, fill=tuple(int(min(255, c * f)) for c in col) + (255,))
    img.save(path)


def register_atlas() -> None:
    """textures/prop/ joins the block and item atlases (any namespace: suld:prop/<id>)."""
    src = {"type": "directory", "source": "prop", "prefix": "prop/"}
    for name in ("blocks", "items"):
        path = os.path.join(ATLASES, name + ".json")
        data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {"sources": []}
        if src not in data["sources"]:
            data["sources"].append(src)
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(data, fh, indent=1)
                fh.write("\n")


def collision_cells(solid: np.ndarray, height: float) -> list[list[int]]:
    """World blocks the prop fills, relative to the block it stands on top of (x/z centre, y = first block above
    the ground), unrotated. A voxel is height / ny blocks on a side; a block counts when FILL of it is covered."""
    nx, ny, nz = solid.shape
    c = height / ny
    per = {}
    for i, j, k in zip(*np.nonzero(solid)):
        cell = (int(np.floor(0.5 + (i + 0.5 - nx / 2) * c)), int(np.floor((j + 0.5) * c)),
                int(np.floor(0.5 + (k + 0.5 - nz / 2) * c)))
        per[cell] = per.get(cell, 0) + 1
    need = FILL / (c ** 3)
    return sorted([list(cell) for cell, n in per.items() if n >= need])


def write_shape(pid: str, cells: list[list[int]]) -> None:
    path = os.path.join(CONTENT, "prop_shapes.json")
    data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    data["about"] = ("Generated by tools/meshy/voxel_model.py: the world blocks each prop fills (relative to the block "
                     "it stands on, yaw 0), filled with barrier blocks so players cannot walk through it.")
    shapes = data.setdefault("shapes", {})
    shapes[pid] = cells
    data["shapes"] = dict(sorted(shapes.items()))
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1, ensure_ascii=False)
        fh.write("\n")


def prop_height(pid: str) -> float | None:
    path = os.path.join(CONTENT, "props.json")
    for p in json.load(open(path, encoding="utf-8")).get("props", []):
        if p.get("id") == pid:
            return float(p["height"])
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("voxels")
    ap.add_argument("--id", required=True)
    ap.add_argument("--colors", type=int, default=40)
    ap.add_argument("--preview", default=None)
    ap.add_argument("--unit", type=float, default=1.0, help="model units per voxel (0.5 = twice the detail)")
    a = ap.parse_args()
    surface, rgb = load(a.voxels)
    nx, ny, nz = surface.shape
    u = a.unit
    if max(nx, ny, nz) * u > 48:
        raise SystemExit("a box model fits 48 units (-16..32): lower --res or --unit")
    solid = surface | ~exterior(surface)
    idx, pal = palette(rgb[surface], a.colors)
    label = np.zeros(surface.shape, np.int32)
    label[surface] = idx
    label = smooth(label, surface)
    label = spread(label, surface, solid)
    boxes = greedy(label, solid)
    # model space: centred on x/z at 8, standing on y = -16
    ox, oy, oz = 8 - nx * u / 2, -16, 8 - nz * u / 2
    elements, faces_out = [], 0
    for b in boxes:
        x0, y0, z0, x1, y1, z1, c = b
        tu, tv = c % 16, c // 16  # the palette pixel
        uv = [tu + 0.25, tv + 0.25, tu + 0.75, tv + 0.75]
        faces = {}
        for f in FACES:
            if not hidden(b, f, solid):
                faces[f] = {"uv": uv, "texture": "#0"}
        if not faces:
            continue
        faces_out += len(faces)
        elements.append({"from": [round(ox + x0 * u, 3), round(oy + y0 * u, 3), round(oz + z0 * u, 3)],
                         "to": [round(ox + x1 * u, 3), round(oy + y1 * u, 3), round(oz + z1 * u, 3)], "faces": faces})
    tex = np.zeros((16, 16, 4), np.uint8)
    for i, col in enumerate(pal):
        tex[i // 16, i % 16] = (*col, 255)
    os.makedirs(os.path.join(RP, "textures", "prop"), exist_ok=True)
    os.makedirs(os.path.join(RP, "models", "prop"), exist_ok=True)
    os.makedirs(os.path.join(RP, "items", "prop"), exist_ok=True)
    Image.fromarray(tex, "RGBA").save(os.path.join(RP, "textures", "prop", a.id + ".png"))
    model = {"credit": f"SÜLD prop {a.id}: Meshy text-to-3D, voxelised by tools/meshy (voxel_sample.py, voxel_model.py)",
             "texture_size": [16, 16], "textures": {"0": f"suld:prop/{a.id}", "particle": f"suld:prop/{a.id}"},
             "elements": elements}
    with open(os.path.join(RP, "models", "prop", a.id + ".json"), "w") as fh:
        json.dump(model, fh, separators=(",", ":"))
    with open(os.path.join(RP, "items", "prop", a.id + ".json"), "w") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": f"suld:prop/{a.id}"}}, fh)
    register_atlas()
    height = prop_height(a.id)
    cells = collision_cells(solid, height) if height else []
    if height:
        write_shape(a.id, cells)
    report = {"id": a.id, "collision_blocks": len(cells), "grid": [nx, ny, nz], "surface_voxels": int(surface.sum()), "solid_voxels": int(solid.sum()),
              "colors": int(len(pal)), "elements": len(elements), "faces": faces_out, "height_units": ny * u}
    print(json.dumps(report))
    if a.preview:
        preview(label, solid, pal, a.preview)
    with open(os.path.join(ROOT, "build", "props", a.id + ".report.json"), "w") as fh:
        json.dump(report, fh, indent=1)


if __name__ == "__main__":
    main()
