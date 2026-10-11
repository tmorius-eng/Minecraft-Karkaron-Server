#!/usr/bin/env python3
"""Generate the static props of tools/meshy/props.json with Meshy: a text-to-3D preview, then the textured refine,
then the GLB into assets/sources/props/<id>.glb (git-ignored). Tasks are cached (assets/.meshy_cache), so a re-run
spends nothing for props already made. The credential is injected by the environment's proxy (meshy_client.py).

    python3 tools/meshy/generate_props.py [--only id,id]
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meshy_client as mc  # noqa: E402

OUT = os.path.join("assets", "sources", "props")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    props = json.load(open(os.path.join(os.path.dirname(__file__), "props.json"), encoding="utf-8"))["props"]
    only = {s for s in args.only.split(",") if s}
    todo = [p for p in props if not only or p["id"] in only]
    os.makedirs(OUT, exist_ok=True)
    previews = {p["id"]: mc.preview_task(p["prompt"]) for p in todo if not os.path.exists(os.path.join(OUT, p["id"] + ".glb"))}
    for pid, task in previews.items():
        print(f"{pid}: preview {task}", flush=True)
    refines = {}
    for p in todo:
        pid = p["id"]
        if pid not in previews:
            print(f"{pid}: already downloaded", flush=True)
            continue
        info = mc.wait(previews[pid])
        if info.get("status") != "SUCCEEDED":
            print(f"{pid}: preview {info.get('status')} {info.get('task_error')}", flush=True)
            continue
        refines[pid] = mc.refine_task(previews[pid], p.get("texture"))
        print(f"{pid}: refine {refines[pid]}", flush=True)
    for pid, task in refines.items():
        info = mc.wait(task)
        url = (info.get("model_urls") or {}).get("glb")
        if info.get("status") != "SUCCEEDED" or not url:
            print(f"{pid}: refine {info.get('status')} {info.get('task_error')}", flush=True)
            continue
        dest = os.path.join(OUT, pid + ".glb")
        urllib.request.urlretrieve(url, dest)
        if info.get("thumbnail_url"):
            try:
                urllib.request.urlretrieve(info["thumbnail_url"], os.path.join(OUT, pid + "_thumb.png"))
            except Exception:
                pass
        print(f"{pid}: saved {dest} ({os.path.getsize(dest)} bytes)", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
