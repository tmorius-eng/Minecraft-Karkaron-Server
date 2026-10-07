#!/usr/bin/env python3
"""Add satellite ("minor") nodes to every class skill tree (docs/SKILL_SKY.md).

Each grid node of category DEFENSE / OFFENSE / SPELL gets 3 satellites: small stat bonuses that hang off it like the
leaf clusters of a constellation map. A satellite costs 1 point, has 1 rank, needs its hub learned (edge hub->sat)
and its hub's level, and gives about half to three quarters of a notable's stat per point, so the tree grows wider
(about 110 nodes per class) without making a point worth more. Ids are hub id + _s1.._s3, so saved builds stay
valid when this is re-run. Idempotent: existing satellites are replaced.

    python3 tools/content/gen_skill_minors.py
"""
from __future__ import annotations

import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SKILLS = os.path.join(ROOT, "suld-api", "src", "main", "resources", "skills")
CLASSES = ["baatar", "mergen", "boo", "darkhan", "khulegchin"]

# (stat key, value per point, name, icon)
DEF = [("HEALTH", 3, "Бат Бие", "APPLE"), ("ARMOR", 3, "Төмөр Арьс", "IRON_NUGGET"),
       ("DAMAGE_REDUCTION", 1.5, "Бамбай Сэтгэл", "TURTLE_SCUTE"), ("HEALTH_REGEN", 0.2, "Сэргэлт", "GLISTERING_MELON_SLICE"),
       ("KB_RESIST", 6, "Хөдлөшгүй", "COBBLESTONE"), ("DODGE_PCT", 1.5, "Салхин Алхам", "FEATHER")]
OFF = [("ATTACK_PCT", 2.5, "Хурц Ир", "FLINT"), ("CRIT_CHANCE", 2, "Шонхор Нүд", "SPYGLASS"),
       ("CRIT_DAMAGE", 7, "Үхлийн Цохилт", "BONE"), ("ATTACK_SPEED_PCT", 2.5, "Хурдан Гар", "SUGAR"),
       ("LIFESTEAL", 1, "Цус Ундаалагч", "FERMENTED_SPIDER_EYE")]
SPL = [("SPELL_DAMAGE", 2.5, "Тэнгэрийн Хүч", "BLAZE_POWDER"), ("RESOURCE_MAX", 7, "Гүн Сан", "LAPIS_LAZULI"),
       ("RESOURCE_REGEN", 0.3, "Урсгал", "PRISMARINE_CRYSTALS"), ("COST_REDUCTION", 1.5, "Хэмнэл", "AMETHYST_SHARD"),
       ("COOLDOWN_REDUCTION", 1.5, "Цагийн Хүрд", "CLOCK"), ("HEAL_POWER", 4, "Эмчлэх Гар", "GLOW_BERRIES")]
POOL = {"DEFENSE": DEF, "OFFENSE": OFF, "SPELL": SPL}
ROMAN = ["", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X", " XI", " XII"]


def node_line(n: dict) -> str:
    return "    " + json.dumps(n, ensure_ascii=False)


def main() -> None:
    for c in CLASSES:
        path = os.path.join(SKILLS, c + ".json")
        text = open(path, encoding="utf-8").read()
        # drop an earlier run (satellite lines are marked by their orbit tag / their edge triples)
        lines = [l for l in text.split("\n") if '"orbit:' not in l and "_s1\"]" not in l]
        text = re.sub(r",(\s*\n\s*\])", r"\1", "\n".join(lines))  # the comma the removed lines leave behind
        d = json.loads(text)
        hubs = [n for n in d["nodes"] if n["category"] in POOL and not n.get("keystone") and not n.get("capstone")]
        used: dict[str, int] = {}
        sats, edges = [], []
        for i, hub in enumerate(hubs):
            pool = POOL[hub["category"]]
            trio = []
            for k in range(3):
                key, val, name, icon = pool[(i + 2 * k) % len(pool)]
                n = used.get(name, 0)
                used[name] = n + 1
                sid = hub["id"] + "_s" + str(k + 1)
                sats.append({"id": sid, "name": name + ROMAN[min(n, len(ROMAN) - 1)],
                             "description": "«" + hub["name"] + "»-ын жижиг чадвар.", "category": hub["category"],
                             "icon": icon, "x": 15, "y": 15, "cost": 1, "level": hub.get("level", 1),
                             "tags": ["orbit:" + hub["id"], "minor"], "effects": [{"type": "stat", "key": key, "value": val}]})
                trio.append(json.dumps([hub["id"], sid], ensure_ascii=False))
            edges.append("    " + ", ".join(trio))
        # the hand-formatted file stays as it is: satellites go after the last node, their edges after the last edge
        ni = text.index('\n  "edges"')
        nodes_end = text.rindex("]", 0, ni)  # the "]" closing the nodes array
        text = text[:nodes_end].rstrip() + ",\n" + ",\n".join(node_line(n) for n in sats) + "\n  " + text[nodes_end:]
        ei = text.index('"edges"')
        edges_end = text.index("\n  ]", ei)
        text = text[:edges_end].rstrip() + ",\n" + ",\n".join(edges) + text[edges_end:]
        json.loads(text)  # still valid
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
        print(f"{c}: {len(hubs)} hubs, +{len(sats)} satellites, {len(d['nodes']) + len(sats)} nodes")


if __name__ == "__main__":
    main()
