#!/usr/bin/env python3
"""Generate source art with Google's Gemini image models ("Nano Banana") for the SÜLD resource pack.

    python3 tools/art/gemini_image.py --prompts tools/art/prompts.json --out assets/art/source [--only id,id] [--force]

The API key is never read or printed by this script: it is either in GEMINI_API_KEY (sent as the
x-goog-api-key header) or injected by the environment's egress proxy. Outputs are the raw model images;
tools/art/pixelize.py turns them into pack textures. Existing outputs are kept unless --force.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
UA = "tmorius-eng/Minecraft-Karkaron-Server/1.0"


def generate(model: str, prompt: str, aspect: str, size: str | None = None) -> bytes:
    image_config = {"aspectRatio": aspect}
    if size:
        image_config["imageSize"] = size  # "1K" / "2K" (Gemini 3 image models)
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"responseModalities": ["IMAGE"], "imageConfig": image_config},
    }
    headers = {"Content-Type": "application/json", "User-Agent": UA}
    key = os.environ.get("GEMINI_API_KEY")
    if key:
        headers["x-goog-api-key"] = key
    req = urllib.request.Request(API.format(model=model), data=json.dumps(body).encode(), headers=headers, method="POST")
    for attempt in range(1, 6):
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                data = json.load(r)
            break
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 503) and attempt < 5:
                time.sleep(min(60, 5 * attempt))
                continue
            raise RuntimeError(f"HTTP {e.code}: {e.read()[:300]!r}") from None
    for cand in data.get("candidates", []) if isinstance(data, dict) else []:
        for part in (cand.get("content") or {}).get("parts", []):
            inline = part.get("inlineData") or part.get("inline_data")
            if isinstance(inline, dict) and inline.get("data"):
                return base64.b64decode(inline["data"])
    raise RuntimeError(f"no image in response: {json.dumps(data)[:300]}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prompts", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--only")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()
    spec = json.load(open(args.prompts, encoding="utf-8"))
    style = spec.get("style", "")
    only = set(args.only.split(",")) if args.only else None
    os.makedirs(args.out, exist_ok=True)
    failed = 0
    for item in spec["images"]:
        if only and item["id"] not in only:
            continue
        path = os.path.join(args.out, item["id"] + ".png")
        if os.path.exists(path) and not args.force:
            print(f"  = {item['id']} (exists)")
            continue
        prompt = item["prompt"] + (" " + style if item.get("style", True) else "")
        try:
            png = generate(item.get("model", spec.get("model", "gemini-2.5-flash-image")), prompt, item.get("aspect", "1:1"), item.get("size", spec.get("size")))
        except Exception as e:  # report and continue
            print(f"  !! {item['id']}: {e}")
            failed += 1
            continue
        with open(path, "wb") as fh:
            fh.write(png)
        print(f"  ok {item['id']} ({len(png) // 1024} KiB)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
