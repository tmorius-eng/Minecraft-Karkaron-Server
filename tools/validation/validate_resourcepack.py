#!/usr/bin/env python3
"""Validate the SÜLD resource pack for the server's Minecraft version (1.21.11):

* pack.mcmeta declares a format range that covers TARGET_PACK_FORMAT (modern
  min_format/max_format, or a legacy integer pack_format);
* no model uses the legacy "overrides" list (ignored by clients since 1.21.4);
* every item model definition (assets/<ns>/items/*.json) resolves its suld: models,
  and range_dispatch thresholds are unique;
* every model's suld: texture references resolve to a PNG on disk, and that PNG is stitched into the block and
  item atlases (under block/ or item/, or a directory source in assets/minecraft/atlases/{blocks,items}.json):
  a texture outside every atlas renders as the magenta/black "missing" checkerboard in game;
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


def model_problems(ref: str, seen: set | None = None) -> list[str]:
    """Why a suld: model referenced by an item definition would not render: the model file, each of its suld:
    textures, and its suld: parent chain (followed through), all on disk. Vanilla refs are the client's own."""
    seen = set() if seen is None else seen
    if not ref.startswith("suld:") or ref in seen:
        return []
    seen.add(ref)
    fp = resolve(ref, "model")
    if not os.path.exists(fp):
        return [f"model '{ref}' missing"]
    try:
        model = json.load(open(fp, encoding="utf-8"))
    except Exception as ex:  # noqa: BLE001
        return [f"model '{ref}' is not valid JSON: {ex}"]
    out = []
    for tex in (model.get("textures") or {}).values():
        if isinstance(tex, str) and tex.startswith("suld:") and not os.path.exists(resolve(tex, "texture")):
            out.append(f"model '{ref}' texture '{tex}' missing")
    parent = model.get("parent")
    if isinstance(parent, str):
        out.extend(model_problems(parent, seen))
    return out


# Item definitions whose using_item branches are draw animations (pulling_0/1/2).
DRAW_ITEMS = ("bow",)


def draw_stage_problems(definition: dict) -> list[str]:
    """A bow/crossbow-style definition: every minecraft:using_item condition must have an idle model on on_false and
    three distinct draw stages on on_true (a use_duration range_dispatch: fallback pulling_0 + two entries)."""
    out: list[str] = []

    def visit(node, where: str):
        if isinstance(node, dict):
            if node.get("type") in ("minecraft:condition", "condition") \
                    and node.get("property") in ("minecraft:using_item", "using_item"):
                idle: list = []
                walk_item_models(node.get("on_false"), idle, [])
                if not idle:
                    out.append(f"{where}: using_item condition has no idle (on_false) model")
                stages: list = []
                walk_item_models(node.get("on_true"), stages, [])
                if len(set(stages)) < 3:
                    out.append(f"{where}: using_item on_true has {len(set(stages))} draw stage(s), expected 3 "
                               f"(pulling_0/1/2): {stages}")
            for k, v in node.items():
                visit(v, f"{where}.{k}")
        elif isinstance(node, list):
            for i, v in enumerate(node):
                visit(v, f"{where}[{i}]")

    visit(definition, "model")
    return out


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


# Vanilla's own directory sources of the block/item atlases (any namespace).
VANILLA_ATLAS_DIRS = ("block/", "item/")


def atlas_dirs(name: str) -> tuple[str, ...]:
    """Texture path prefixes the pack adds to minecraft:<name> (directory sources), plus vanilla's own."""
    path = os.path.join(RP, "assets", "minecraft", "atlases", name + ".json")
    dirs = list(VANILLA_ATLAS_DIRS)
    if os.path.exists(path):
        for src in json.load(open(path, encoding="utf-8")).get("sources", []):
            if src.get("type") in ("directory", "minecraft:directory") and src.get("source"):
                dirs.append(src["source"].rstrip("/") + "/")
    return tuple(dirs)


def main() -> int:
    errors: list[str] = []
    atlases = {name: atlas_dirs(name) for name in ("blocks", "items")}

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
                # every branch: range_dispatch entries/fallback and condition on_true/on_false (bow draw stages)
                walk_item_models(model, refs, thresholds)
                for ref in dict.fromkeys(refs):
                    for problem in model_problems(ref):
                        errors.append(f"{os.path.relpath(fp, ROOT)}: item {problem}")
                if os.path.splitext(fn)[0] in DRAW_ITEMS:  # a shield's using_item has one (blocking) model, on purpose
                    for problem in draw_stage_problems(model):
                        errors.append(f"{os.path.relpath(fp, ROOT)}: {problem}")
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
                tex_path = ref.split(":", 1)[1]
                for name, dirs in atlases.items():
                    if not tex_path.startswith(dirs):
                        errors.append(f"{os.path.relpath(fp, ROOT)}: texture '{ref}' is in no directory source of the "
                                      f"{name} atlas (assets/minecraft/atlases/{name}.json): it renders as missing")

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
