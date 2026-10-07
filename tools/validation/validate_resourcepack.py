#!/usr/bin/env python3
"""Validate the SÜLD resource pack for the server's Minecraft version (1.21.11):

* pack.mcmeta declares a format range that covers TARGET_PACK_FORMAT (modern
  min_format/max_format, or a legacy integer pack_format);
* no model uses the legacy "overrides" list (ignored by clients since 1.21.4);
* every item model definition (assets/<ns>/items/*.json) resolves its suld: models,
  and range_dispatch thresholds are unique;
* every model's suld: texture references resolve to a PNG on disk;
* every equipment asset (assets/<ns>/equipment/*.json) resolves each layer's suld:
  texture to textures/entity/equipment/<layer type>/<path>.png;
* every registry asset with custom_model_data + base_item is actually mapped to its
  minecraft_model by the base item's definition.
Exit non-zero on failure.
"""
from __future__ import annotations

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RP = os.path.join(ROOT, "resourcepack")
REGISTRY = os.path.join(ROOT, "assets", "registry", "assets.json")
# Resource pack format of Minecraft 1.21.11 (vanilla version.json: pack_version.resource_major).
TARGET_PACK_FORMAT = 75


def _major(v):
    return v[0] if isinstance(v, list) else v


def check_format(pack: dict) -> str | None:
    if "min_format" in pack or "max_format" in pack:
        lo, hi = _major(pack.get("min_format")), _major(pack.get("max_format"))
        if not isinstance(lo, int) or not isinstance(hi, int) or lo > hi:
            return f"min_format/max_format malformed: {pack.get('min_format')!r}..{pack.get('max_format')!r}"
        if not lo <= TARGET_PACK_FORMAT <= hi:
            return f"format range {lo}..{hi} does not include {TARGET_PACK_FORMAT} (Minecraft 1.21.11)"
        return None
    pf = pack.get("pack_format")
    if not isinstance(pf, int):
        return "pack.mcmeta needs min_format/max_format (or pack_format)"
    if pf != TARGET_PACK_FORMAT:
        return f"pack_format {pf} != {TARGET_PACK_FORMAT} (Minecraft 1.21.11) — clients would flag the pack incompatible"
    return None


def walk_item_models(node, out: list, thresholds: list):
    """Collect model refs and range_dispatch thresholds from an item model definition tree."""
    if isinstance(node, dict):
        if node.get("type") in ("minecraft:model", "model") and isinstance(node.get("model"), str):
            out.append(node["model"])
        if node.get("type") in ("minecraft:range_dispatch", "range_dispatch"):
            thresholds.append([e.get("threshold") for e in node.get("entries", [])])
        for v in node.values():
            walk_item_models(v, out, thresholds)
    elif isinstance(node, list):
        for v in node:
            walk_item_models(v, out, thresholds)


def cmd_map(definition: dict) -> dict:
    """threshold -> model for a top-level custom_model_data range_dispatch definition."""
    m = definition.get("model", {})
    if m.get("property") not in ("minecraft:custom_model_data", "custom_model_data"):
        return {}
    out = {}
    for e in m.get("entries", []):
        node = e.get("model") or {}
        if node.get("type") in ("minecraft:model", "model"):
            out[e.get("threshold")] = node.get("model")
        else:  # e.g. a bow's using_item condition: the idle (on_false) model names the skin
            refs: list = []
            walk_item_models(node.get("on_false", node), refs, [])
            out[e.get("threshold")] = refs[0] if refs else None
    return out


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
        meta = json.load(open(mcmeta, encoding="utf-8"))
        problem = check_format(meta["pack"])
        if problem:
            errors.append(f"pack.mcmeta: {problem}")
    except Exception as ex:  # noqa: BLE001
        errors.append(f"pack.mcmeta invalid: {ex}")

    models_dir = os.path.join(RP, "assets")
    model_count = tex_refs = equipment_count = 0
    item_defs: dict = {}
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
            rel = os.path.relpath(fp, RP).replace(os.sep, "/")
            if "/items/" in "/" + rel:
                refs, thresholds = [], []
                walk_item_models(model, refs, thresholds)
                for ref in refs:
                    if ref.startswith("suld:") and not os.path.exists(resolve(ref, "model")):
                        errors.append(f"{os.path.relpath(fp, ROOT)}: item model '{ref}' missing")
                for ts in thresholds:
                    if len(ts) != len(set(ts)):
                        errors.append(f"{os.path.relpath(fp, ROOT)}: duplicate range_dispatch thresholds {ts}")
                item_defs[os.path.splitext(fn)[0]] = model
                continue
            if "/equipment/" in "/" + rel:
                equipment_count += 1
                for layer, entries in (model.get("layers") or {}).items():
                    for entry in entries if isinstance(entries, list) else []:
                        ref = entry.get("texture", "") if isinstance(entry, dict) else ""
                        if not ref.startswith("suld:"):
                            continue
                        ns, _, path = ref.partition(":")
                        tp = os.path.join(RP, "assets", ns, "textures", "entity", "equipment", layer, path + ".png")
                        tex_refs += 1
                        if not os.path.exists(tp):
                            errors.append(f"{os.path.relpath(fp, ROOT)}: {layer} texture '{ref}' -> missing "
                                          f"{os.path.relpath(tp, ROOT)}")
                continue
            model_count += 1
            if "overrides" in model:
                errors.append(f"{os.path.relpath(fp, ROOT)}: legacy 'overrides' are ignored since 1.21.4 — "
                              f"use assets/<ns>/items/<item>.json")
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

    # Registry <-> pack: every asset that claims a custom model must really be wired up.
    mapped = 0
    if os.path.exists(REGISTRY):
        for a in json.load(open(REGISTRY, encoding="utf-8")).get("assets", []):
            cmd, base, mpath = a.get("custom_model_data"), a.get("base_item"), a.get("minecraft_model")
            if not (cmd and base and mpath):
                continue
            item = base.split(":", 1)[-1]
            expected = "suld:" + mpath.split("/models/", 1)[-1].removesuffix(".json")
            got = cmd_map(item_defs.get(item, {})).get(cmd)
            if got != expected:
                errors.append(f"registry {a.get('id')}: custom_model_data {cmd} on {base} maps to {got!r}, "
                              f"expected {expected!r} (assets/minecraft/items/{item}.json)")
            else:
                mapped += 1

    if errors:
        print("RESOURCE PACK VALIDATION FAILED:")
        for e in errors:
            print("  -", e)
        return 1
    print(f"resource pack OK: format covers {TARGET_PACK_FORMAT}, {model_count} model(s), "
          f"{len(item_defs)} item definition(s), {equipment_count} equipment asset(s), {tex_refs} texture ref(s), "
          f"{mapped} registry model mapping(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
