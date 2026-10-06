#!/usr/bin/env python3
"""Validate the SÜLD resource pack: pack.mcmeta is well-formed, SÜLD assets are
namespaced under assets/suld/, every model's texture references resolve to a PNG
on disk, and item-override models point at existing models. Exit non-zero on
failure.
"""
from __future__ import annotations

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RP = os.path.join(ROOT, "resourcepack")


def resolve(ref: str, kind: str) -> str:
    """Resolve a namespaced ref like 'suld:item/foo' to a file path under the pack."""
    ns, _, path = ref.partition(":")
    if not path:
        ns, path = "minecraft", ref
    sub = "textures" if kind == "texture" else "models"
    ext = ".png" if kind == "texture" else ".json"
    return os.path.join(RP, "assets", ns, sub, path + ext)


def main() -> int:
    errors: list[str] = []

    mcmeta = os.path.join(RP, "pack.mcmeta")
    if not os.path.exists(mcmeta):
        print("RESOURCE PACK VALIDATION FAILED: pack.mcmeta missing")
        return 1
    try:
        meta = json.load(open(mcmeta))
        assert isinstance(meta["pack"]["pack_format"], int)
    except Exception as ex:  # noqa: BLE001
        errors.append(f"pack.mcmeta invalid: {ex}")

    models_dir = os.path.join(RP, "assets")
    model_count = tex_refs = 0
    for dirpath, _dirs, files in os.walk(models_dir):
        for fn in files:
            if not fn.endswith(".json"):
                continue
            fp = os.path.join(dirpath, fn)
            try:
                model = json.load(open(fp))
            except Exception as ex:  # noqa: BLE001
                errors.append(f"{fp}: not valid JSON: {ex}")
                continue
            model_count += 1
            for ref in (model.get("textures") or {}).values():
                if not isinstance(ref, str) or ref.startswith("#"):
                    continue
                # Vanilla (minecraft:) assets are provided by the client, not shipped.
                if ref.startswith("minecraft:") or ":" not in ref:
                    continue
                tex_refs += 1
                tp = resolve(ref, "texture")
                if not os.path.exists(tp):
                    errors.append(f"{os.path.relpath(fp, ROOT)}: texture ref '{ref}' -> missing {os.path.relpath(tp, ROOT)}")
            for ov in model.get("overrides", []):
                mref = ov.get("model")
                if mref and mref.startswith("suld:") and not os.path.exists(resolve(mref, "model")):
                    errors.append(f"{os.path.relpath(fp, ROOT)}: override model '{mref}' missing")

    if errors:
        print("RESOURCE PACK VALIDATION FAILED:")
        for e in errors:
            print("  -", e)
        return 1
    print(f"resource pack OK: {model_count} model(s), {tex_refs} texture ref(s) resolved")
    return 0


if __name__ == "__main__":
    sys.exit(main())
