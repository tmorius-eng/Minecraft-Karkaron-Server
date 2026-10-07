#!/usr/bin/env python3
"""Export an authored cuboid rig (tools/model/<rig>.py) to SÜLD's model-renderer asset contract
(docs/MODEL_RENDERER.md §3):

  resourcepack/assets/suld/models/entity/<rig>/<bone>.json     Java item models (cuboids, tintindex 0)
  resourcepack/assets/suld/items/entity/<rig>/<bone>.json      item definitions with the custom_model_data tint
  resourcepack/assets/suld/textures/entity/<rig>/<atlas>.png   painted atlases (tools/model/<rig>_paint.py)
  resourcepack/assets/minecraft/atlases/{blocks,items}.json    a directory source so item models can use them
  suld-plugin/src/main/resources/models/<rig>/rig.json         skeleton
  suld-plugin/src/main/resources/models/<rig>/clips.json       clips (tools/model/<rig>_clips.py)
  assets/models/blockbench/boss/<rig>.bbmodel                  Blockbench "free" model for artists (rest pose, groups, UVs)
  assets/reports/boss.<rig>.json                               per-asset report (ASSET_PRODUCTION_PIPELINE §4)

    python3 tools/model/export_rig.py khasar [--no-display-flip] [--check]

Display flip: vanilla's ItemDisplay renderer turns the item 180° about Y before drawing it
(DisplayRenderer.ItemDisplayRenderer: ``poseStack.mulPose(Axis.YP.rotation(π))``). The exporter therefore writes
every bone model pre-turned by 180° about the pivot (x → 16 − x, z → 16 − z, faces north↔south and east↔west,
up/down UVs turned), so that in game the bone's +Z is the creature's forward as the rig assumes. preview_rig.py
applies the same client turn. ``--no-display-flip`` writes the models unturned (if the client check shows the turn
is not there). MANUAL_QA_REQUIRED either way.

``--check`` builds everything in memory and prints the report without writing.
"""
from __future__ import annotations

import argparse
import base64
import importlib
import io
import json
import math
import os
import shutil
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)

import rigmath  # noqa: E402

FACES = ("north", "south", "west", "east", "up", "down")
SWAP_EW = {"east": "west", "west": "east"}
FLIP_FACE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "up", "down": "down"}
ALLOWED_ANGLES = (-45, -22.5, 0, 22.5, 45)


def r4(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


# ------------------------------------------------------------------------------------------- cube geometry

def face_frame(name, f, t):
    """Corner at uv (u1, v1), u and v edge vectors, the outward normal, and the face size (du, dv) in px."""
    (x0, y0, z0), (x1, y1, z1) = f, t
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    return {
        "north": ((x1, y1, z0), (-dx, 0, 0), (0, -dy, 0), (0, 0, -1), (dx, dy)),
        "south": ((x0, y1, z1), (dx, 0, 0), (0, -dy, 0), (0, 0, 1), (dx, dy)),
        "west": ((x0, y1, z0), (0, 0, dz), (0, -dy, 0), (-1, 0, 0), (dz, dy)),
        "east": ((x1, y1, z1), (0, 0, -dz), (0, -dy, 0), (1, 0, 0), (dz, dy)),
        "up": ((x0, y1, z0), (dx, 0, 0), (0, 0, dz), (0, 1, 0), (dx, dz)),
        "down": ((x0, y0, z1), (dx, 0, 0), (0, 0, -dz), (0, -1, 0), (dx, dz)),
    }[name]


def rot_point(p, rot):
    """Item-model element rotation (right-handed about the axis through the origin)."""
    if not rot:
        return p
    axis, ang, o = rot
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    if axis == "x":
        y, z = y * ca - z * sa, y * sa + z * ca
    elif axis == "y":
        x, z = x * ca + z * sa, -x * sa + z * ca
    else:
        x, y = x * ca - y * sa, x * sa + y * ca
    return (x + o[0], y + o[1], z + o[2])


def rot_vec(v, rot):
    if not rot:
        return v
    axis, ang, _ = rot
    return rot_point(v, (axis, ang, (0, 0, 0)))


# ------------------------------------------------------------------------------------------- packing

class Shelf:
    def __init__(self, size, pad=1):
        self.size, self.pad = size, pad
        self.x = self.y = self.row_h = 0

    def place(self, w, h):
        cw, ch = w + 2 * self.pad, h + 2 * self.pad
        if self.x + cw > self.size:
            self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        if self.y + ch > self.size or cw > self.size:
            return None
        at = (self.x + self.pad, self.y + self.pad)
        self.x += cw
        self.row_h = max(self.row_h, ch)
        return at


# ------------------------------------------------------------------------------------------- export

class Exporter:
    def __init__(self, rig_name, display_flip=True):
        self.name = rig_name
        try:
            self.mod = importlib.import_module(rig_name)
            self.paint = importlib.import_module(rig_name + "_paint")
            self.clips_mod = importlib.import_module(rig_name + "_clips")
            self.R = self.mod.RIG
        except ModuleNotFoundError:
            # the body-plan rigs of tools/model/fauna.py share one painter and one set of clips
            import types
            import fauna
            import fauna_clips
            import fauna_paint
            if rig_name not in fauna.RIGS:
                raise SystemExit(f"no rig {rig_name!r} (neither tools/model/{rig_name}.py nor fauna.RIGS)")
            self.R = fauna.rig(rig_name)
            self.mod = types.SimpleNamespace(RIG=self.R)
            self.paint = fauna_paint.painter(self.R)
            R = self.R
            self.clips_mod = types.SimpleNamespace(build=lambda rigj: fauna_clips.clips_for(R))
        self.flip = display_flip
        self.bones = self.R["bones"]
        self.by_id = {b["id"]: b for b in self.bones}
        self.warnings: list[str] = []

    # ---- skeleton
    def rig_json(self):
        piv = {}
        out = []
        for b in self.bones:
            pp = piv[b["parent"]] if b["parent"] else (0.0, 0.0, 0.0)
            p = tuple(pp[k] + b["at"][k] / 16.0 for k in range(3))
            piv[b["id"]] = p
            e = {"id": b["id"], "parent": b["parent"], "pivot": [r4(v) for v in p], "rest": [r4(v) for v in b["rest"]],
                 "model": bool(b["model"])}
            if b["model"]:
                e["scale"] = r4(b["scale"])
            out.append(e)
        rig = {"id": self.R["id"], "scale": self.R["scale"], "hostHeight": self.R["hostHeight"], "bones": out,
               "parts": self.R.get("parts", [])}
        if self.R.get("variants"):
            rig["variants"] = [{"id": k, "bone": v["of"]} for k, v in self.R["variants"].items()]
        return rig

    # ---- faces
    def collect(self):
        """Every exported face, with its texel rect request (or a reference to a shared one)."""
        self.faces = []    # dicts: bone, cube, name, mat, key, ref, mirror, variant
        variants = self.R.get("variants", {})
        for b in self.bones:
            if not b["model"]:
                continue
            dens = self.R["atlases"][b["atlas"]]["density"]
            for ci, cb in enumerate(b["cubes"]):
                for fn in FACES:
                    if fn in cb["skip"]:
                        continue
                    mat = cb["mats"].get(fn, cb["mat"])
                    src = cb.get("mirror_src")
                    if src is not None:
                        ref = (id(src), SWAP_EW.get(fn, fn), None)
                        self.faces.append({"bone": b["id"], "cube": cb, "ci": ci, "name": fn, "mat": mat, "ref": ref,
                                           "mirror": True, "atlas": b["atlas"]})
                        continue
                    key = (id(cb), fn, None)
                    *_, (du, dv) = face_frame(fn, cb["f"], cb["t"])
                    w, h = max(1, round(abs(du) * dens)), max(1, round(abs(dv) * dens))
                    self.faces.append({"bone": b["id"], "cube": cb, "ci": ci, "name": fn, "mat": mat, "ref": key,
                                       "mirror": False, "own": True, "w": w, "h": h, "atlas": b["atlas"],
                                       "deco": cb["deco"].get(fn)})
                    for vid, v in variants.items():
                        if v["of"] == b["id"] and mat in v["remap"]:
                            self.faces.append({"bone": b["id"], "cube": cb, "ci": ci, "name": fn, "mat": v["remap"][mat],
                                               "ref": (id(cb), fn, vid), "mirror": False, "own": True, "w": w, "h": h,
                                               "atlas": b["atlas"], "deco": cb["deco"].get(fn), "variant": vid})
        # mirrored cubes may also need variant rects
        for f in list(self.faces):
            if f["mirror"]:
                for vid, v in variants.items():
                    if v["of"] == f["bone"] and f["mat"] in v["remap"]:
                        self.faces.append(dict(f, ref=(f["ref"][0], f["ref"][1], vid), variant=vid))

    def pack(self):
        self.rects = {}
        usage = {}
        for an, a in self.R["atlases"].items():
            own = [f for f in self.faces if f.get("own") and f["atlas"] == an]
            own.sort(key=lambda f: (-f["h"], f["mat"], -f["w"]))
            shelf = Shelf(a["size"], a.get("pad", 1))
            area = 0
            for f in own:
                at = shelf.place(f["w"], f["h"])
                if at is None:
                    pd = 2 * a.get("pad", 1)
                    need = sum((g["w"] + pd) * (g["h"] + pd) for g in own)
                    raise SystemExit(f"atlas {an} overflows {a['size']}²: needs ≈{need} texels incl. padding")
                self.rects[f["ref"]] = (at[0], at[1], f["w"], f["h"])
                area += f["w"] * f["h"]
            usage[an] = (area, shelf.y + shelf.row_h)
        self.usage = usage

    # ---- painting
    def rest_pose(self):
        self.rigj = self.rig_json()
        self.M = rigmath.pose(self.rigj["bones"])

    def world_of(self, bone_id, p_px):
        b = self.by_id[bone_id]
        m = self.M[bone_id]
        s = b["scale"]
        return m.apply(tuple(v * s / 16.0 for v in p_px))

    def paint_all(self):
        from PIL import Image
        self.images = {an: Image.new("RGBA", (a["size"], a["size"]), (0, 0, 0, 0)) for an, a in self.R["atlases"].items()}
        px = {an: im.load() for an, im in self.images.items()}
        for f in self.faces:
            if not f.get("own"):
                continue
            cb = f["cube"]
            corner, uvec, vvec, nrm, _ = face_frame(f["name"], cb["f"], cb["t"])
            x, y, w, h = self.rects[f["ref"]]
            normal_local = rot_vec(nrm, cb["rot"])
            m = self.M[f["bone"]]
            nw = rigmath.qrot(m.q, normal_local)

            def texel_pos(i, j, corner=corner, uvec=uvec, vvec=vvec, w=w, h=h, cb=cb, bone=f["bone"]):
                fu, fv = (i + 0.5) / w, (j + 0.5) / h
                p = tuple(corner[k] + fu * uvec[k] + fv * vvec[k] for k in range(3))
                return self.world_of(bone, rot_point(p, cb["rot"]))

            face = {"name": f["name"], "mat": f["mat"], "deco": f.get("deco"), "w": w, "h": h, "normal": nw,
                    "key": (f["bone"], f["ci"], f["name"]), "bone": f["bone"]}
            rows = self.paint.paint_face(face, texel_pos)
            P = px[f["atlas"]]
            for j in range(h):
                for i in range(w):
                    P[x + i, y + j] = rows[j][i]
            # bleed the edge into the 1-texel padding (mipmaps and filtering never pick up a neighbour)
            if not self.R["atlases"][f["atlas"]].get("pad", 1):
                continue
            for i in range(-1, w + 1):
                for j in (-1, h):
                    P[x + i, y + j] = P[x + min(max(i, 0), w - 1), y + min(max(j, 0), h - 1)]
            for j in range(0, h):
                for i in (-1, w):
                    P[x + i, y + j] = P[x + min(max(i, 0), w - 1), y + j]

    def png_bytes(self, im):
        """Indexed PNG with the exact swatches (smaller than RGBA; the client expands it)."""
        data = list(im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata())
        cols = sorted(set(data))
        if len(cols) > 256:
            b = io.BytesIO()
            im.save(b, "PNG", optimize=True)
            return b.getvalue()
        from PIL import Image
        cols = sorted(cols, key=lambda c: c[3])  # transparent first
        index = {c: i for i, c in enumerate(cols)}
        pim = Image.new("P", im.size)
        flat = []
        for c in cols:
            flat += c[:3]
        pim.putpalette(flat + [0] * (768 - len(flat)))
        pim.putdata([index[c] for c in data])
        trns = bytes(c[3] for c in cols)
        b = io.BytesIO()
        pim.save(b, "PNG", optimize=True, transparency=trns)
        return b.getvalue()

    # ---- models
    def uv_of(self, f, size):
        ref = f["ref"]
        x, y, w, h = self.rects[ref]
        k = 16.0 / size
        u1, v1, u2, v2 = x * k, y * k, (x + w) * k, (y + h) * k
        if f["mirror"]:
            u1, u2 = u2, u1
        return [u1, v1, u2, v2]

    def element(self, cb, faces, atlas_size):
        (x0, y0, z0), (x1, y1, z1) = cb["f"], cb["t"]
        fr = [x0 + 8, y0 + 8, z0 + 8]
        to = [x1 + 8, y1 + 8, z1 + 8]
        rot = None
        if cb["rot"]:
            axis, ang, o = cb["rot"]
            if ang not in ALLOWED_ANGLES:
                raise SystemExit(f"element rotation {ang} not allowed")
            rot = {"origin": [o[0] + 8, o[1] + 8, o[2] + 8], "axis": axis, "angle": ang}
        out_faces = {}
        for f in faces:
            uv = self.uv_of(f, atlas_size)
            name = f["name"]
            if self.flip:
                name = FLIP_FACE[name]
                if name in ("up", "down"):
                    uv = [uv[2], uv[3], uv[0], uv[1]]
            out_faces[name] = {"uv": [r4(v) for v in uv], "texture": "#0", "tintindex": 0}
        if self.flip:
            fr, to = [16 - to[0], fr[1], 16 - to[2]], [16 - fr[0], to[1], 16 - fr[2]]
            if rot:
                o = rot["origin"]
                rot = {"origin": [16 - o[0], o[1], 16 - o[2]], "axis": rot["axis"],
                       "angle": rot["angle"] if rot["axis"] == "y" else -rot["angle"]}
        for v in fr + to:
            if v < -16 or v > 32:
                raise SystemExit(f"element outside −16…32: {fr} {to}")
        e = {"from": [r4(v) for v in fr], "to": [r4(v) for v in to]}
        if rot:
            e["rotation"] = {"origin": [r4(v) for v in rot["origin"]], "axis": rot["axis"], "angle": rot["angle"]}
        e["faces"] = {k: out_faces[k] for k in FACES if k in out_faces}
        return e

    def models(self):
        out = {}
        variants = self.R.get("variants", {})
        for b in self.bones:
            if not b["model"]:
                continue
            a = self.R["atlases"][b["atlas"]]
            tex = f"suld:entity/{self.R['id']}/{a['file']}"
            for vid in [None] + [k for k, v in variants.items() if v["of"] == b["id"]]:
                els = []
                for ci, cb in enumerate(b["cubes"]):
                    fs = []
                    for f in self.faces:
                        if f["bone"] != b["id"] or f["ci"] != ci:
                            continue
                        if f.get("variant") not in (None, vid):
                            continue
                        fs.append(f)
                    # a variant face replaces the base face of the same name
                    chosen = {}
                    for f in fs:
                        if f["name"] not in chosen or f.get("variant") == vid:
                            chosen[f["name"]] = f
                    els.append(self.element(cb, list(chosen.values()), a["size"]))
                name = vid or b["id"]
                out[name] = {"credit": f"SÜLD {self.R['id']} rig — generated by tools/model/export_rig.py",
                             "texture_size": [a["size"], a["size"]],
                             "textures": {"0": tex, "particle": tex}, "elements": els}
        return out

    # ---- blockbench
    def bbmodel(self, pngs):
        tex_ids = {an: i for i, an in enumerate(self.R["atlases"])}
        textures = []
        for an, a in self.R["atlases"].items():
            textures.append({"path": "", "name": a["file"] + ".png", "folder": "", "namespace": "", "id": str(tex_ids[an]),
                             "width": a["size"], "height": a["size"], "uv_width": a["size"], "uv_height": a["size"],
                             "particle": False, "render_mode": "default", "visible": True, "mode": "bitmap",
                             "saved": True, "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"suld/{self.name}/tex/{an}")),
                             "source": "data:image/png;base64," + base64.b64encode(pngs[an]).decode()})
        piv_px = {b["id"]: [v * 16 for v in rb["pivot"]] for b, rb in zip(self.bones, self.rigj["bones"])}
        elements, groups = [], {}
        for b in self.bones:
            gid = str(uuid.uuid5(uuid.NAMESPACE_URL, f"suld/{self.name}/bone/{b['id']}"))
            groups[b["id"]] = {"name": b["id"], "origin": [r4(v) for v in piv_px[b["id"]]],
                               "rotation": [r4(v) for v in b["rest"]], "uuid": gid, "export": True,
                               "isOpen": False, "visibility": True, "children": []}
            for ci, cb in enumerate(b["cubes"]):
                P = piv_px[b["id"]]
                faces = {}
                for f in self.faces:
                    if f["bone"] == b["id"] and f["ci"] == ci and not f.get("variant"):
                        x, y, w, h = self.rects[f["ref"]]
                        uv = [x, y, x + w, y + h] if not f["mirror"] else [x + w, y, x, y + h]
                        faces[f["name"]] = {"uv": [r4(v) for v in uv], "texture": tex_ids[b["atlas"]]}
                eu = str(uuid.uuid5(uuid.NAMESPACE_URL, f"suld/{self.name}/el/{b['id']}/{ci}"))
                rot = [0, 0, 0]
                origin = P
                if cb["rot"]:
                    axis, ang, o = cb["rot"]
                    rot = [ang if axis == "x" else 0, ang if axis == "y" else 0, ang if axis == "z" else 0]
                    origin = [P[k] + o[k] for k in range(3)]
                elements.append({"name": f"{b['id']}_{ci}_{cb['mat']}", "box_uv": False, "rescale": False,
                                 "locked": False, "from": [r4(P[k] + cb["f"][k]) for k in range(3)],
                                 "to": [r4(P[k] + cb["t"][k]) for k in range(3)], "autouv": 0, "color": ci % 8,
                                 "origin": [r4(v) for v in origin], "rotation": rot, "faces": faces, "type": "cube",
                                 "uuid": eu})
                groups[b["id"]]["children"].append(eu)
        roots = []
        for b in self.bones:
            if b["parent"]:
                groups[b["parent"]]["children"].append(groups[b["id"]])
            else:
                roots.append(groups[b["id"]])
        # Clips are not embedded: their source is tools/model/<rig>_clips.py (sampled keyframes would make the file
        # ~0.6 MB). Event ticks are listed in the note for animators.
        return {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
                "name": self.R["id"], "model_identifier": self.R["id"],
                "suld_note": ("Generated from tools/model/khasar.py by tools/model/export_rig.py; edit the Python source "
                              "and re-export (this file is not read back). Rest pose: group origin = rig.json pivot × 16, "
                              "group rotation = rest (Euler XYZ, q = qz·qy·qx). Blockbench's rotation sign conventions "
                              "for this format are not verified."),
                "resolution": {"width": 128, "height": 128}, "elements": elements, "outliner": roots,
                "textures": textures, "animations": [],
                "suld_clips": {k: {"length": c["length"], "loop": bool(c.get("loop")), "events": c.get("events", [])}
                               for k, c in self.clips.items()}}

    # ---- run
    def build(self):
        self.rest_pose()
        self.collect()
        self.pack()
        self.paint_all()
        self.clips = self.clips_mod.build(self.rigj)
        self.model_json = self.models()
        self.pngs = {an: self.png_bytes(im) for an, im in self.images.items()}

    def stats(self):
        cubes = {b["id"]: len(b["cubes"]) for b in self.bones if b["model"]}
        lo = [1e9] * 3
        hi = [-1e9] * 3
        lowest = {}
        for b in self.bones:
            for cb in b["cubes"]:
                for xi in (0, 1):
                    for yi in (0, 1):
                        for zi in (0, 1):
                            p = (cb["f"][0] if not xi else cb["t"][0], cb["f"][1] if not yi else cb["t"][1],
                                 cb["f"][2] if not zi else cb["t"][2])
                            w = self.world_of(b["id"], rot_point(p, cb["rot"]))
                            for k in range(3):
                                lo[k], hi[k] = min(lo[k], w[k]), max(hi[k], w[k])
                            if b["id"].endswith("_paw"):
                                lowest[b["id"]] = min(lowest.get(b["id"], 1e9), w[1])
        return cubes, lo, hi, lowest

    def write(self, check=False):
        rid = self.R["id"]
        rp = os.path.join(ROOT, "resourcepack", "assets")
        mdir = os.path.join(rp, "suld", "models", "entity", rid)
        idir = os.path.join(rp, "suld", "items", "entity", rid)
        tdir = os.path.join(rp, "suld", "textures", "entity", rid)
        sdir = os.path.join(ROOT, "suld-plugin", "src", "main", "resources", "models", rid)
        files = {}
        for name, m in self.model_json.items():
            files[os.path.join(mdir, name + ".json")] = json.dumps(m, ensure_ascii=False, separators=(",", ":")).encode()
            item = {"model": {"type": "minecraft:model", "model": f"suld:entity/{rid}/{name}",
                              "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]}}
            files[os.path.join(idir, name + ".json")] = json.dumps(item, separators=(",", ":")).encode()
        for an, data in self.pngs.items():
            files[os.path.join(tdir, self.R["atlases"][an]["file"] + ".png")] = data
        files[os.path.join(sdir, "rig.json")] = (json.dumps(self.rigj, ensure_ascii=False, indent=1) + "\n").encode()
        files[os.path.join(sdir, "clips.json")] = (json.dumps({"clips": self.clips}, separators=(",", ":")) + "\n").encode()
        bb = os.path.join(ROOT, "assets", "models", "blockbench", "boss", rid + ".bbmodel")
        files[bb] = json.dumps(self.bbmodel(self.pngs), ensure_ascii=False, separators=(",", ":")).encode()

        cubes, lo, hi, lowest = self.stats()
        pack_bytes = sum(len(v) for k, v in files.items() if k.startswith(os.path.join(ROOT, "resourcepack")))
        tex_bytes = sum(len(v) for v in self.pngs.values())
        print(f"rig {rid}: {len(self.bones)} bones, {sum(1 for b in self.bones if b['model'])} displays, "
              f"{sum(cubes.values())} cuboids, {len(self.model_json)} models ({len(self.model_json) - len(cubes)} variants)")
        for b, n in cubes.items():
            print(f"    {b:<14} {n:>3}")
        for an, (area, used_h) in self.usage.items():
            a = self.R["atlases"][an]
            print(f"atlas {an}: {area} texels painted ({100 * area / a['size'] ** 2:.0f} %), rows used {used_h}/{a['size']}, "
                  f"{len(self.pngs[an])} B")
        print(f"rest bbox (blocks): x {lo[0]:.2f}..{hi[0]:.2f}  y {lo[1]:.2f}..{hi[1]:.2f}  z {lo[2]:.2f}..{hi[2]:.2f}  "
              f"→ {hi[0] - lo[0]:.2f} wide × {hi[1] - lo[1]:.2f} tall × {hi[2] - lo[2]:.2f} long")
        print("paw soles (y, blocks): " + ", ".join(f"{k}={v:.3f}" for k, v in lowest.items()))
        print(f"clips: " + ", ".join(f"{k}({c['length']}{'L' if c.get('loop') else ''}{''.join(' ' + e[1] + '@' + str(e[0]) for e in c.get('events', []))})"
                                     for k, c in self.clips.items()))
        print(f"pack bytes (models + item defs + textures): {pack_bytes} B; textures {tex_bytes} B")
        self.report = {"pack_bytes": pack_bytes, "tex_bytes": tex_bytes, "cubes": cubes, "bbox": [lo, hi]}
        if check:
            return
        for d in (mdir, idir, tdir):
            if os.path.isdir(d):
                shutil.rmtree(d)
        for path, data in files.items():
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as fh:
                fh.write(data)
        self.atlas_sources(rid)
        self.write_report(rid, cubes, pack_bytes, tex_bytes, lo, hi)
        print(f"wrote {len(files)} files")

    def atlas_sources(self, rid):
        """Item models draw from the block (and, on newer clients, item) atlas: add a directory source for the rig."""
        src = {"type": "directory", "source": f"entity/{rid}", "prefix": f"entity/{rid}/"}
        for atlas in ("blocks", "items"):
            p = os.path.join(ROOT, "resourcepack", "assets", "minecraft", "atlases", atlas + ".json")
            data = {"sources": []}
            if os.path.exists(p):
                data = json.load(open(p, encoding="utf-8"))
            if src not in data.get("sources", []):
                data.setdefault("sources", []).append(src)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "w", encoding="utf-8") as fh:
                json.dump(data, fh, indent=1)
                fh.write("\n")

    def write_report(self, rid, cubes, pack_bytes, tex_bytes, lo, hi):
        p = os.path.join(ROOT, "assets", "reports", f"boss.{rid}.json")
        old = json.load(open(p, encoding="utf-8")) if os.path.exists(p) else {}
        old.setdefault("id", f"boss.{rid}")
        old["minecraft"] = {
            "rig": f"suld-plugin/src/main/resources/models/{rid}/rig.json",
            "displays": len(cubes), "elements": sum(cubes.values()), "tri_upper_bound": sum(cubes.values()) * 12,
            "elements_per_bone": cubes, "textures": {a["file"]: f"{a['size']}x{a['size']}" for a in self.R["atlases"].values()},
            "texture_bytes": tex_bytes, "pack_bytes": pack_bytes, "models": len(self.model_json),
            "clips": {k: {"length": c["length"], "loop": bool(c.get("loop")), "events": c.get("events", [])}
                      for k, c in self.clips.items()},
            "rest_bbox_blocks": {"min": [round(v, 3) for v in lo], "max": [round(v, 3) for v in hi]},
            "display_flip": self.flip,
        }
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as fh:
            json.dump(old, fh, ensure_ascii=False, indent=1)
            fh.write("\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("rig")
    ap.add_argument("--no-display-flip", action="store_true")
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    ex = Exporter(a.rig, display_flip=not a.no_display_flip)
    ex.build()
    ex.write(check=a.check)


if __name__ == "__main__":
    main()
