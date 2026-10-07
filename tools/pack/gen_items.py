#!/usr/bin/env python3
"""Write the vanilla item definitions (resourcepack/assets/minecraft/items/*.json) that map SÜLD
custom_model_data ids to SÜLD models — the single source of truth for every item skin.

Each definition is a range_dispatch on custom_model_data; every SÜLD id gets its own entry followed by a
"cap" entry back to the vanilla model, so an unknown id never shows a wrong skin. Bows keep the vanilla
using_item / use_duration pulling logic. Run after gen_weapons.py (reads resourcepack/assets/suld/weapons.json).

    python3 tools/pack/gen_items.py
"""
from __future__ import annotations

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ITEMS = os.path.join(ROOT, "resourcepack", "assets", "minecraft", "items")
WEAPONS = os.path.join(ROOT, "resourcepack", "assets", "suld", "weapons.json")

# legacy SÜLD item skins (custom_model_data -> model), kept as they are
LEGACY = {
    "iron_sword": {870010: "suld:item/talyn_ild", 870011: "suld:item/surgamj_ild", 870012: "suld:item/khasar_soyo",
                   870013: "suld:item/govi_khutga"},
    "netherite_sword": {870001: "suld:item/suld_ild_tenger"},
    "iron_axe": {870030: "suld:item/khangai_sukh"},
    "leather": {870040: "suld:item/chonon_arisan"},
    "rabbit_hide": {870041: "suld:item/baavgain_arisan"},
    "nether_star": {870100: "suld:item/khukh_suld", 870101: "suld:item/altan_gerege"},
    "bow": {870020: "suld:item/surgamj_num"},
    # jewellery
    "iron_nugget": {872001: "suld:item/mungun_bugj"},
    "emerald": {872002: "suld:item/khash_bugj"},
    "gold_nugget": {872003: "suld:item/altan_bugj"},
}
BOWS = {"bow"}


def m(ref: str) -> dict:
    return {"type": "minecraft:model", "model": ref}


def bow_tree(prefix: str) -> dict:
    return {"type": "minecraft:condition", "property": "minecraft:using_item", "on_false": m(prefix),
            "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                        "entries": [{"threshold": 0.65, "model": m(prefix + "_pulling_1")},
                                    {"threshold": 0.9, "model": m(prefix + "_pulling_2")}],
                        "fallback": m(prefix + "_pulling_0")}}


def main() -> None:
    table = {k: dict(v) for k, v in LEGACY.items()}
    for name, w in json.load(open(WEAPONS, encoding="utf-8")).items():
        item = w["base_item"].split(":", 1)[1]
        table.setdefault(item, {})[w["custom_model_data"]] = w["model"]
    os.makedirs(ITEMS, exist_ok=True)
    for item, ids in sorted(table.items()):
        vanilla = f"minecraft:item/{item}"
        node = (lambda ref: bow_tree(ref)) if item in BOWS else m
        entries = []
        for cmd in sorted(ids):
            entries.append({"threshold": cmd, "model": node(ids[cmd])})
            if cmd + 1 not in ids:
                entries.append({"threshold": cmd + 1, "model": node(vanilla)})
        definition = {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                                "entries": entries, "fallback": node(vanilla)}}
        with open(os.path.join(ITEMS, item + ".json"), "w", encoding="utf-8") as fh:
            json.dump(definition, fh, indent=2)
            fh.write("\n")
    print(f"{len(table)} item definitions written: {', '.join(sorted(table))}")


if __name__ == "__main__":
    main()
