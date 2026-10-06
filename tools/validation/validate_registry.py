#!/usr/bin/env python3
"""Validate assets/registry/assets.json: unique IDs, ID naming, required fields,
enum values, and that referenced files exist on disk. Exit non-zero on failure.
"""
from __future__ import annotations

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REGISTRY = os.path.join(ROOT, "assets", "registry", "assets.json")

ID_RE = re.compile(r"^[a-z0-9_]+\.[a-z0-9_]+$")
STATUSES = {"concept", "generated", "processed", "needs_review", "approved", "integrated"}
RARITIES = {"common", "uncommon", "rare", "epic", "legendary", "ancient", "mythic", "unique"}
FILE_KEYS = ("bbmodel", "minecraft_model", "texture_file", "preview_render")


def main() -> int:
    data = json.load(open(REGISTRY))
    errors: list[str] = []
    seen: set[str] = set()
    cmd_seen: dict[int, str] = {}

    for a in data.get("assets", []):
        aid = a.get("id", "<missing>")
        if not ID_RE.match(aid):
            errors.append(f"{aid}: id must match <type>.<name> (lowercase/underscore)")
        if aid in seen:
            errors.append(f"{aid}: duplicate asset id")
        seen.add(aid)
        if a.get("status") not in STATUSES:
            errors.append(f"{aid}: invalid status {a.get('status')!r}")
        if "rarity" in a and a["rarity"] not in RARITIES:
            errors.append(f"{aid}: invalid rarity {a['rarity']!r}")
        cmd = a.get("custom_model_data")
        if isinstance(cmd, int):
            if cmd in cmd_seen:
                errors.append(f"{aid}: custom_model_data {cmd} already used by {cmd_seen[cmd]}")
            cmd_seen[cmd] = aid
        for key in FILE_KEYS:
            rel = a.get(key)
            if rel and not os.path.exists(os.path.join(ROOT, rel)):
                errors.append(f"{aid}: {key} missing on disk: {rel}")

    if errors:
        print("REGISTRY VALIDATION FAILED:")
        for e in errors:
            print("  -", e)
        return 1
    print(f"registry OK: {len(seen)} asset(s), {len(cmd_seen)} custom_model_data id(s), all files present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
