#!/usr/bin/env python3
"""Rebuild the prop box models from the Meshy GLBs (assets/sources/props/<id>.glb, made by generate_props.py):
Blender samples the voxels (voxel_sample.py), voxel_model.py writes the model, item and palette texture into the
resource pack. Settings per prop: tools/meshy/props.json "voxel". Deterministic.

    python3 tools/meshy/build_props.py [--only id,id] [--preview DIR]
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    ap.add_argument("--preview", default=None)
    a = ap.parse_args()
    only = {s for s in a.only.split(",") if s}
    props = json.load(open(os.path.join(HERE, "props.json"), encoding="utf-8"))["props"]
    os.makedirs(os.path.join(ROOT, "build", "props"), exist_ok=True)
    for p in props:
        if only and p["id"] not in only:
            continue
        glb = os.path.join(ROOT, "assets", "sources", "props", p["id"] + ".glb")
        if not os.path.exists(glb):
            print(f"{p['id']}: no {glb} (run generate_props.py)", file=sys.stderr)
            continue
        v = p["voxel"]
        vox = os.path.join(ROOT, "build", "props", p["id"] + ".voxels.json")
        subprocess.run(["blender", "-b", "-P", os.path.join(HERE, "voxel_sample.py"), "--", "--input", glb,
                        "--res", str(v["res"]), "--out", vox], check=True, stdout=subprocess.DEVNULL)
        cmd = [sys.executable, os.path.join(HERE, "voxel_model.py"), vox, "--id", p["id"], "--unit", str(v["unit"]),
               "--colors", str(v["colors"])]
        if a.preview:
            os.makedirs(a.preview, exist_ok=True)
            cmd += ["--preview", os.path.join(a.preview, p["id"] + ".png")]
        subprocess.run(cmd, check=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
