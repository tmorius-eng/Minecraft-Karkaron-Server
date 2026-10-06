#!/usr/bin/env python3
"""SÜLD resource-pack budget + hygiene validator.

FAILS the build when:
  - a raw source mesh (GLB/FBX/OBJ/…) is inside resourcepack/
  - a texture exceeds its configured pixel budget (ordinary vs hero)
  - duplicate texture files exist (identical content)
  - any single file exceeds max_file_kb
  - the pack total exceeds total_budget_mb

Always reports: uncompressed size, final ZIP size, largest files, texture/model/
sound counts, and estimated download time at 5/10/20/50 Mbps.
"""
from __future__ import annotations

import hashlib
import io
import json
import os
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RP = os.path.join(ROOT, "resourcepack")
BUDGET = os.path.join(ROOT, "assets", "registry", "pack_budget.json")


def png_dims(path: str) -> tuple[int, int] | None:
    try:
        with open(path, "rb") as f:
            head = f.read(24)
        if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
            return None
        return struct.unpack(">II", head[16:24])
    except Exception:
        return None


def hero_texture_paths(cfg: dict) -> set[str]:
    out = set()
    for ref in cfg.get("hero_textures", []):
        ns, _, path = ref.partition(":")
        if not path:
            ns, path = "minecraft", ref
        out.add(os.path.join(RP, "assets", ns, "textures", path + ".png"))
    return out


def human(n: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f} {unit}" if unit != "B" else f"{n} B"
        n /= 1024
    return f"{n:.1f} GB"


def main() -> int:
    cfg = json.load(open(BUDGET))
    errors: list[str] = []
    forbidden = tuple(cfg["forbidden_extensions"])
    heroes = hero_texture_paths(cfg)

    files: list[tuple[str, int]] = []
    hashes: dict[str, str] = {}
    tex_count = model_count = sound_bytes = 0

    for dirpath, _d, names in os.walk(RP):
        for fn in names:
            fp = os.path.join(dirpath, fn)
            rel = os.path.relpath(fp, ROOT)
            size = os.path.getsize(fp)
            files.append((rel, size))
            ext = os.path.splitext(fn)[1].lower()

            if ext in forbidden:
                errors.append(f"FORBIDDEN raw source in resource pack: {rel} (move to assets/source/)")
            if size > cfg["max_file_kb"] * 1024:
                errors.append(f"file too large: {rel} = {human(size)} (> {cfg['max_file_kb']} KB)")

            if ext == ".png":
                tex_count += 1
                dims = png_dims(fp)
                if dims:
                    limit = cfg["max_texture_px_hero"] if fp in heroes else cfg["max_texture_px_ordinary"]
                    just = cfg.get("justified_large", {})
                    rel_norm = rel.replace(os.sep, "/")
                    if max(dims) > limit and rel_norm not in just:
                        tier = "hero" if fp in heroes else "ordinary"
                        errors.append(f"texture over budget: {rel} = {dims[0]}x{dims[1]} (> {limit}px {tier})")
                digest = hashlib.sha1(open(fp, "rb").read()).hexdigest()
                if digest in hashes:
                    errors.append(f"duplicate texture: {rel} == {hashes[digest]}")
                else:
                    hashes[digest] = rel
            elif ext == ".json":
                model_count += 1
            elif ext in (".ogg", ".wav", ".mp3"):
                sound_bytes += size

    total = sum(s for _, s in files)

    # Build an in-memory ZIP to measure the real delivered size.
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for rel, _s in files:
            z.write(os.path.join(ROOT, rel), rel)
    zip_size = buf.tell()

    if total > cfg["total_budget_mb"] * 1024 * 1024:
        errors.append(f"pack total {human(total)} exceeds budget {cfg['total_budget_mb']} MB")

    print("=== SÜLD resource-pack budget report ===")
    print(f"files: {len(files)}  | textures: {tex_count}  models: {model_count}  sounds: {human(sound_bytes)}")
    print(f"uncompressed: {human(total)}  | final ZIP: {human(zip_size)}")
    print("largest files:")
    for rel, s in sorted(files, key=lambda x: -x[1])[:5]:
        print(f"    {human(s):>10}  {rel}")
    print("estimated download (ZIP) at:")
    for mbps in (5, 10, 20, 50):
        secs = (zip_size * 8) / (mbps * 1_000_000)
        print(f"    {mbps:>2} Mbps : {secs:.2f} s")

    if errors:
        print("\nPACK BUDGET VALIDATION FAILED:")
        for e in errors:
            print("  -", e)
        return 1
    print("\npack budget OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
