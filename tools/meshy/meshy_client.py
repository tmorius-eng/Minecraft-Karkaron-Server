#!/usr/bin/env python3
"""SÜLD Meshy client — text/image-to-3D generation for the asset pipeline.

Credential handling
--------------------
This client NEVER reads, prints, logs, or writes a Meshy API key. In the SÜLD
Claude Cloud environment the egress proxy injects the configured Meshy
credential for requests to api.meshy.ai, so requests go out unauthenticated
from this process and the proxy adds the Authorization header. We only ensure
requests use the environment's HTTPS proxy + CA bundle (urllib reads HTTPS_PROXY
automatically; the CA bundle is already in the system trust store).

If you run this outside that environment you must front api.meshy.ai with an
equivalent authenticating proxy; do not add a key here.

Task metadata is cached under assets/.meshy_cache/ so repeated runs do not spend
credits re-creating identical tasks.

Usage:
  meshy_client.py balance
  meshy_client.py text3d --prompt "..." [--art-style realistic] [--no-remesh]
  meshy_client.py refine <preview_task_id> [--texture-prompt "..."]
  meshy_client.py poll <task_id> [--download-dir DIR] [--timeout 600]
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.request

API = "https://api.meshy.ai/openapi"
CACHE = os.path.join("assets", ".meshy_cache")


def _request(method: str, path: str, body: dict | None = None) -> dict:
    url = API + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    # No Authorization header: the Cloud egress proxy injects the credential.
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode())


def balance() -> int:
    print(json.dumps(_request("GET", "/v1/balance")))
    return 0


def text3d(prompt: str, art_style: str, remesh: bool) -> int:
    os.makedirs(CACHE, exist_ok=True)
    key = hashlib.sha1(f"{prompt}|{art_style}|{remesh}".encode()).hexdigest()[:16]
    cache_file = os.path.join(CACHE, f"text3d_{key}.json")
    if os.path.exists(cache_file):
        cached = json.load(open(cache_file))
        print(json.dumps({"cached": True, **cached}))
        return 0
    result = _request("POST", "/v2/text-to-3d", {
        "mode": "preview", "prompt": prompt, "art_style": art_style, "should_remesh": remesh,
    })
    task_id = result.get("result")
    record = {"task_id": task_id, "prompt": prompt, "art_style": art_style}
    json.dump(record, open(cache_file, "w"), indent=2, ensure_ascii=False)
    print(json.dumps({"cached": False, **record}))
    return 0


def refine_task(preview_task_id: str, texture_prompt: str | None = None) -> str:
    """Start the texturing (refine) of a finished preview; cached like the previews. Returns the refine task id."""
    os.makedirs(CACHE, exist_ok=True)
    key = hashlib.sha1(f"refine|{preview_task_id}|{texture_prompt}".encode()).hexdigest()[:16]
    cache_file = os.path.join(CACHE, f"refine_{key}.json")
    if os.path.exists(cache_file):
        return json.load(open(cache_file))["task_id"]
    body = {"mode": "refine", "preview_task_id": preview_task_id, "enable_pbr": False, "ai_model": "meshy-7.1"}
    if texture_prompt:
        body["texture_prompt"] = texture_prompt
    task_id = _request("POST", "/v2/text-to-3d", body).get("result")
    json.dump({"task_id": task_id, "preview_task_id": preview_task_id, "texture_prompt": texture_prompt},
              open(cache_file, "w"), indent=2, ensure_ascii=False)
    return task_id


def preview_task(prompt: str, art_style: str = "realistic", remesh: bool = True) -> str:
    """Start (or reuse from the cache) a text-to-3D preview. Returns its task id."""
    os.makedirs(CACHE, exist_ok=True)
    key = hashlib.sha1(f"{prompt}|{art_style}|{remesh}".encode()).hexdigest()[:16]
    cache_file = os.path.join(CACHE, f"text3d_{key}.json")
    if os.path.exists(cache_file):
        return json.load(open(cache_file))["task_id"]
    task_id = _request("POST", "/v2/text-to-3d", {
        "mode": "preview", "prompt": prompt, "art_style": art_style, "should_remesh": remesh,
    }).get("result")
    json.dump({"task_id": task_id, "prompt": prompt, "art_style": art_style}, open(cache_file, "w"), indent=2, ensure_ascii=False)
    return task_id


def wait(task_id: str, timeout: float = 900.0) -> dict:
    """Poll a text-to-3D task until it ends; returns its final record."""
    deadline = time.time() + timeout
    info: dict = {}
    while time.time() < deadline:
        info = _request("GET", f"/v2/text-to-3d/{task_id}")
        if info.get("status") in ("SUCCEEDED", "FAILED", "CANCELED", "EXPIRED"):
            return info
        time.sleep(10)
    return info


def poll(task_id: str, download_dir: str | None, timeout: float) -> int:
    deadline = time.time() + timeout
    status = None
    info: dict = {}
    while time.time() < deadline:
        info = _request("GET", f"/v2/text-to-3d/{task_id}")
        status = info.get("status")
        prog = info.get("progress", 0)
        print(f"status={status} progress={prog}", file=sys.stderr)
        if status in ("SUCCEEDED", "FAILED", "CANCELED"):
            break
        time.sleep(8)
    if status != "SUCCEEDED":
        print(json.dumps({"task_id": task_id, "status": status}))
        return 1
    urls = info.get("model_urls", {})
    saved = {}
    if download_dir:
        os.makedirs(download_dir, exist_ok=True)
        for fmt in ("glb", "obj", "fbx"):
            if urls.get(fmt):
                dest = os.path.join(download_dir, f"{task_id}.{fmt}")
                urllib.request.urlretrieve(urls[fmt], dest)
                saved[fmt] = dest
        # thumbnail
        if info.get("thumbnail_url"):
            dest = os.path.join(download_dir, f"{task_id}_thumb.png")
            try:
                urllib.request.urlretrieve(info["thumbnail_url"], dest)
                saved["thumbnail"] = dest
            except Exception:
                pass
    print(json.dumps({"task_id": task_id, "status": status, "saved": saved,
                      "polycount": info.get("polycount")}, ensure_ascii=False))
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("balance")
    t = sub.add_parser("text3d")
    t.add_argument("--prompt", required=True)
    t.add_argument("--art-style", default="realistic")
    t.add_argument("--no-remesh", action="store_true")
    r = sub.add_parser("refine")
    r.add_argument("preview_task_id")
    r.add_argument("--texture-prompt", default=None)
    p = sub.add_parser("poll")
    p.add_argument("task_id")
    p.add_argument("--download-dir", default=None)
    p.add_argument("--timeout", type=float, default=600.0)
    args = ap.parse_args()
    if args.cmd == "balance":
        return balance()
    if args.cmd == "text3d":
        return text3d(args.prompt, args.art_style, not args.no_remesh)
    if args.cmd == "refine":
        print(json.dumps({"task_id": refine_task(args.preview_task_id, args.texture_prompt)}))
        return 0
    if args.cmd == "poll":
        return poll(args.task_id, args.download_dir, args.timeout)
    return 2


if __name__ == "__main__":
    sys.exit(main())
