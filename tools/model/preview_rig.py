#!/usr/bin/env python3
"""Preview (and verify) an exported SÜLD rig without a GPU or a client: a small software rasterizer.

It reads ONLY the shipped files (rig.json, clips.json, the bone item models and their textures), poses them with
the maths of docs/MODEL_RENDERER.md §2 (tools/model/rigmath.py, the twin of the Java Sampler) and draws what an
ItemDisplay with transform NONE draws: the model's (8, 8, 8) px at the display origin, 16 px per block, the vanilla
180° item-display turn about Y (``--no-display-flip`` to leave it out), then translation · leftRotation · scale.
Faces are textured with nearest sampling and lit like an entity (two fixed lights + ambient).

    python3 tools/model/preview_rig.py khasar --out <dir> [--views side,front,threequarter,left,top]
        [--clip idle --tick 0] [--sheet] [--frames 6] [--ppb 64] [--silhouette]

Outputs <rig>_<view>.png per view, <rig>_sheet.png (every clip × frames, side view) and <rig>_silhouette.png.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import rigmath  # noqa: E402

FACE_ORDER = ("north", "south", "west", "east", "up", "down")
L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])
BG = (118, 120, 124)
GRID = (96, 98, 103)
GROUND = (150, 70, 60)


def face_quad(name, f, t):
    """Corners in uv order (u1v1, u2v1, u2v2, u1v2) — the same convention as export_rig.face_frame."""
    (x0, y0, z0), (x1, y1, z1) = f, t
    q = {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[name]
    return np.array(q, dtype=float)


def rot_elem(P, rot):
    if not rot:
        return P
    o = np.array(rot["origin"], dtype=float)
    a = math.radians(rot["angle"])
    c, s = math.cos(a), math.sin(a)
    R = {"x": [[1, 0, 0], [0, c, -s], [0, s, c]], "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]],
         "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[rot["axis"]]
    return (P - o) @ np.array(R).T + o


class Rig:
    def __init__(self, rid, flip=True):
        base = os.path.join(ROOT, "suld-plugin", "src", "main", "resources", "models", rid)
        self.rig = json.load(open(os.path.join(base, "rig.json"), encoding="utf-8"))
        self.clips = json.load(open(os.path.join(base, "clips.json"), encoding="utf-8"))["clips"]
        self.flip = flip
        self.tex = {}
        self.geo = {}   # bone -> list of (quad item-px (4,3), uv (4,2), texture key)
        mdir = os.path.join(ROOT, "resourcepack", "assets", "suld", "models", "entity", rid)
        for b in self.rig["bones"]:
            if b.get("model"):
                self.geo[b["id"]] = self.load_model(os.path.join(mdir, b["id"] + ".json"))
        self.variants = {}
        for v in self.rig.get("variants", []):
            self.variants[v["id"]] = (v["bone"], self.load_model(os.path.join(mdir, v["id"] + ".json")))

    def texture(self, ref):
        if ref not in self.tex:
            ns, _, path = ref.partition(":")
            im = Image.open(os.path.join(ROOT, "resourcepack", "assets", ns, "textures", path + ".png")).convert("RGBA")
            self.tex[ref] = np.asarray(im, dtype=np.float32)
        return ref

    def load_model(self, path):
        m = json.load(open(path, encoding="utf-8"))
        texs = m["textures"]
        out = []
        for e in m["elements"]:
            for fn, face in e["faces"].items():
                ref = texs[face["texture"].lstrip("#")]
                self.texture(ref)
                P = rot_elem(face_quad(fn, e["from"], e["to"]), e.get("rotation"))
                u1, v1, u2, v2 = face["uv"]
                uv = np.array([(u1, v1), (u2, v1), (u2, v2), (u1, v2)], dtype=float)
                out.append((P, uv, ref))
        return out

    def posed_quads(self, clip=None, tick=0.0, yaw=0.0, swaps=None):
        bones = self.rig["bones"]
        c = self.clips.get(clip) if clip else None
        M = rigmath.pose(bones, c, tick)
        D = rigmath.display(bones, M, yaw, self.rig.get("scale", 1.0))
        out = []
        for b in bones:
            if not b.get("model"):
                continue
            geo = self.geo[b["id"]]
            if swaps and b["id"] in swaps:
                geo = self.variants[swaps[b["id"]]][1]
            x = D[b["id"]]
            if x.s <= 1e-6:
                continue
            q = x.q
            R = quat_matrix(q)
            for P, uv, ref in geo:
                p = (P - 8.0) / 16.0
                if self.flip:
                    p = p * np.array([-1.0, 1.0, -1.0])
                W = (p * x.s) @ R.T + np.array(x.t)
                out.append((W, uv, ref))
        return out


def quat_matrix(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def view_basis(az, el):
    a, e = math.radians(az), math.radians(el)
    d = np.array([math.sin(a) * math.cos(e), math.sin(e), math.cos(a) * math.cos(e)])  # towards the viewer
    up = np.array([0.0, 1.0, 0.0])
    right = np.cross(up, d)
    if np.linalg.norm(right) < 1e-6:
        right = np.array([1.0, 0, 0])
    right /= np.linalg.norm(right)
    cup = np.cross(d, right)
    return right, cup, d


VIEWS = {"side": (-90, 0), "left": (90, 0), "front": (0, 0), "back": (180, 0), "threequarter": (40, 20),
         "threequarter_back": (-140, 22), "top": (0, 89.9), "low": (30, 6)}


def render(rig, quads, view, ppb=64, extent=None, grid=True, silhouette=False, textures=None):
    az, el = VIEWS[view] if isinstance(view, str) else view
    right, up, d = view_basis(az, el)
    if extent is None:
        extent = (-3.0, 3.0, -0.6, 3.0)  # horizontal and vertical range in view units (blocks)
    h0, h1, v0, v1 = extent
    W, H = int((h1 - h0) * ppb), int((v1 - v0) * ppb)
    img = np.zeros((H, W, 3), dtype=np.float32)
    img[:] = BG
    zbuf = np.full((H, W), -1e9, dtype=np.float32)
    if grid and el < 45:
        for k in range(math.floor(h0), math.ceil(h1) + 1):
            x = int((k - h0) * ppb)
            if 0 <= x < W:
                img[:, x] = GRID
        for k in range(math.floor(v0), math.ceil(v1) + 1):
            y = int((v1 - k) * ppb)
            if 0 <= y < H:
                img[y, :] = GROUND if k == 0 else GRID
    hit = np.zeros((H, W), dtype=bool)
    for Wq, uv, ref in quads:
        tex = rig.tex[ref]
        th, tw = tex.shape[:2]
        sx = (Wq @ right - h0) * ppb
        sy = (v1 - Wq @ up) * ppb
        sz = Wq @ d
        n = -np.cross(Wq[1] - Wq[0], Wq[3] - Wq[0])  # corners run clockwise seen from outside
        nl = np.linalg.norm(n)
        if nl < 1e-12:
            continue
        n /= nl
        # item models are drawn without back-face culling only for translucent sprites; cuboids cull back faces
        if n @ d <= 1e-6:
            continue
        shade = min(1.0, 0.4 + 0.6 * max(0.0, n @ L0) + 0.6 * max(0.0, n @ L1))
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster_tri(img, zbuf, hit, sx[list(tri)], sy[list(tri)], sz[list(tri)], uv[list(tri)], tex, tw, th, shade,
                       silhouette)
    out = np.clip(img, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGB"), hit


def raster_tri(img, zbuf, hit, xs, ys, zs, uvs, tex, tw, th, shade, silhouette):
    H, W = zbuf.shape
    x0, x1 = max(0, int(math.floor(xs.min()))), min(W - 1, int(math.ceil(xs.max())))
    y0, y1 = max(0, int(math.floor(ys.min()))), min(H - 1, int(math.ceil(ys.max())))
    if x0 > x1 or y0 > y1:
        return
    px, py = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    (ax, bx, cx), (ay, by, cy) = xs, ys
    den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(den) < 1e-9:
        return
    w0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
    w1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
    w2 = 1 - w0 - w1
    eps = -1e-6
    inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
    if not inside.any():
        return
    z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
    u = w0 * uvs[0, 0] + w1 * uvs[1, 0] + w2 * uvs[2, 0]
    v = w0 * uvs[0, 1] + w1 * uvs[1, 1] + w2 * uvs[2, 1]
    tx = np.clip(np.floor(u / 16.0 * tw).astype(int), 0, tw - 1)
    ty = np.clip(np.floor(v / 16.0 * th).astype(int), 0, th - 1)
    col = tex[ty, tx]
    zb = zbuf[y0:y1 + 1, x0:x1 + 1]
    m = inside & (z > zb) & (col[..., 3] > 127)
    if not m.any():
        return
    zb[m] = z[m]
    sub = img[y0:y1 + 1, x0:x1 + 1]
    if silhouette:
        sub[m] = (20, 20, 20)
    else:
        sub[m] = col[..., :3][m] * shade
    hit[y0:y1 + 1, x0:x1 + 1] |= m


# ------------------------------------------------------------------------------------------------- outputs

def label(im, text, xy=(4, 2), fill=(235, 235, 235)):
    ImageDraw.Draw(im).text(xy, text, fill=fill)
    return im


def _frame(args):
    rid, flip, clip, tick, view, ppb, extent, swaps = args
    rig = Rig(rid, flip)
    im, _ = render(rig, rig.posed_quads(clip, tick, swaps=swaps), view, ppb, extent)
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("rig")
    ap.add_argument("--out", required=True)
    ap.add_argument("--views", default="side,front,threequarter")
    ap.add_argument("--clip", default=None)
    ap.add_argument("--tick", type=float, default=0)
    ap.add_argument("--ppb", type=int, default=64)
    ap.add_argument("--sheet", action="store_true")
    ap.add_argument("--clips", default=None, help="comma list for the sheet (default: all)")
    ap.add_argument("--frames", type=int, default=6)
    ap.add_argument("--sheet-view", default="side")
    ap.add_argument("--sheet-ppb", type=int, default=28)
    ap.add_argument("--silhouette", action="store_true")
    ap.add_argument("--swap", default=None, help="bone=variant[,…] (e.g. head=head_rage)")
    ap.add_argument("--tag", default="")
    ap.add_argument("--extent", default=None, help="h0,h1,v0,v1 in blocks (close-ups)")
    ap.add_argument("--no-display-flip", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    flip = not a.no_display_flip
    rig = Rig(a.rig, flip)
    swaps = dict(kv.split("=") for kv in a.swap.split(",")) if a.swap else None
    tag = ("_" + a.tag) if a.tag else ""
    for view in [v for v in a.views.split(",") if v]:
        quads = rig.posed_quads(a.clip, a.tick, swaps=swaps)
        ext = (-3.0, 3.0, -0.5, 3.0) if view not in ("front", "back") else (-1.75, 1.75, -0.5, 3.0)
        if view == "top":
            ext = (-1.75, 1.75, -2.75, 2.5)
        if a.extent:
            ext = tuple(float(v) for v in a.extent.split(","))
        im, hit = render(rig, quads, view, a.ppb, ext)
        name = f"{a.rig}_{view}{tag}.png"
        label(im, f"{a.rig} {view} {a.clip or 'rest'}@{a.tick:g}").save(os.path.join(a.out, name))
        print("wrote", os.path.join(a.out, name))
    if a.silhouette:
        quads = rig.posed_quads(a.clip, a.tick)
        im, hit = render(rig, quads, "side", 32, (-3.0, 3.0, -0.25, 2.75), grid=False, silhouette=True)
        # the spec's 64 px greyscale silhouette test: scale the creature's side view to 64 px high
        ys, xs = np.nonzero(hit)
        crop = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)).convert("L")
        s = 64 / crop.height
        small = crop.resize((max(1, int(crop.width * s)), 64), Image.NEAREST)
        big = small.resize((small.width * 4, 256), Image.NEAREST)
        big.save(os.path.join(a.out, f"{a.rig}_silhouette{tag}.png"))
        print("wrote", os.path.join(a.out, f"{a.rig}_silhouette{tag}.png"))
    if a.sheet:
        names = a.clips.split(",") if a.clips else list(rig.clips.keys())
        ppb = a.sheet_ppb
        ext = (-3.0, 3.0, -1.75, 3.25) if a.sheet_view != "front" else (-2.0, 2.0, -1.75, 3.25)
        if a.extent:
            ext = tuple(float(v) for v in a.extent.split(","))
        jobs = []
        for cn in names:
            c = rig.clips[cn]
            L = c["length"]
            n = a.frames
            ticks = [round(L * k / (n if c.get("loop") else max(1, n - 1)), 1) for k in range(n)]
            for t in ticks:
                jobs.append((a.rig, flip, cn, t, a.sheet_view, ppb, ext, swaps))
        with ProcessPoolExecutor(max_workers=os.cpu_count() or 2) as pool:
            frames = list(pool.map(_frame, jobs))
        fw, fh = frames[0].size
        sheet = Image.new("RGB", (fw * a.frames + 90, fh * len(names)), (40, 40, 44))
        k = 0
        for r, cn in enumerate(names):
            c = rig.clips[cn]
            ev = " ".join(f"{e[1]}@{e[0]}" for e in c.get("events", []))
            ImageDraw.Draw(sheet).text((4, r * fh + 4), f"{cn}\n{c['length']}t{' loop' if c.get('loop') else ''}\n{ev}",
                                       fill=(235, 235, 235))
            for col in range(a.frames):
                im = frames[k]
                t = jobs[k][3]
                label(im, f"t{t:g}", (3, fh - 12))
                sheet.paste(im, (90 + col * fw, r * fh))
                k += 1
        out = os.path.join(a.out, f"{a.rig}_sheet{tag}.png")
        sheet.save(out)
        print("wrote", out)


if __name__ == "__main__":
    main()
