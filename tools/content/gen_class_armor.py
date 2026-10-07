#!/usr/bin/env python3
"""Generate the class armour definitions (docs/CLASS_ARMOR_SYSTEM.md) into suld-api/.../items/class_armor.json.

Stat budget rule (CLASS_ARMOR_SYSTEM "Stats"): at every armour level a class piece has the stat budget of the generic
armour the simulator used as its proxy at that item level and rarity (Engine.proxy: the highest-requirement generic
piece of the slot that can roll the tier's rarity). Per tier the budget is fitted linearly between the tier's entry
armour level and the next tier's entry (same rarity), so armour grows with the armour level inside a tier:
    mean(stat, AL) = base + perLevel * (AL - 1)
The data range is base * [0.88, 1.12], corrected for the rarity's roll floor (fixed-rarity items still roll their
base stats in the upper part of the range). The 4-piece sets per tier carry the class bonus (Баатар: health / damage
reduction), counted as about x1.08 power in the simulation.

    python3 tools/content/gen_class_armor.py          # writes class_armor.json and the sets into sets.json
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ITEMS = os.path.join(ROOT, "suld-api", "src", "main", "resources", "items")

R = ["common", "uncommon", "rare", "epic", "legendary", "ancient", "mythic"]
MULT = {"common": 1.0, "uncommon": 1.1, "rare": 1.25, "epic": 1.4, "legendary": 1.6, "ancient": 1.8, "mythic": 2.0}
FLOOR = {"common": 0, "uncommon": 0, "rare": .1, "epic": .25, "legendary": .4, "ancient": .55, "mythic": .7}
PIECES = ["helmet", "chestplate", "leggings", "boots"]
TIER_AL = [1, 12, 24, 36, 48, 60]
TIER_RARITY = ["uncommon", "rare", "epic", "legendary", "ancient", "mythic"]
TIER_NAME = ["Анхан", "Бэхжсэн", "Сонгомол", "Эзэнт", "Домогт", "Тэнгэрлэг"]
TIER_MATERIAL = ["leather", "chainmail", "iron", "iron", "diamond", "netherite"]
PIECE_NAME = {"helmet": "Дуулга", "chestplate": "Хуяг", "leggings": "Өмд", "boots": "Гутал"}

CLASSES = {
    "baatar": {
        "name": "Баатрын",
        "lore": "Баатрын ангийн хуяг — хуягийн түвшин ахих тусам хамт ахина.",
        # 2 pieces: health, 4 pieces: damage reduction (ARMOR_PROGRESSION "Armour and the class")
        "set2": lambda t: {"stats": {"max_health": [4, 8, 14, 20, 26, 32][t]}},
        "set4": lambda t: {"effects": [{"type": "stat", "key": "DAMAGE_REDUCTION", "value": [3, 4, 5, 6, 7, 8][t]}]},
        "setName": "Баатрын Хуяг",
    },
}

generic = json.load(open(os.path.join(ITEMS, "armor.json"), encoding="utf-8"))["items"]


def proxy(piece, al, want):
    best = None
    for d in generic:
        if d["type"] != piece.upper() or d["level"] > al:
            continue
        lo, hi = R.index(d["rarity"]), R.index(d.get("maxRarity", d["rarity"]))
        score = (1000 if lo <= R.index(want) <= hi else 0) + d["level"]
        if best is None or score > best[0]:
            best = (score, d)
    return best[1]


def mean(d, al, want):
    lo, hi = R.index(d["rarity"]), R.index(d.get("maxRarity", d["rarity"]))
    r = R[min(max(R.index(want), lo), hi)]
    q = (1 + FLOOR[r]) / 2
    return {k: (mn + q * (mx - mn)) * MULT[r] / MULT[d["rarity"]] + d.get("perLevel", {}).get(k, 0) * (al - 1)
            for k, (mn, mx) in d["stats"].items()}


def budget(piece, t):
    """(base at AL 1, per level) per stat for tier index t."""
    a0, rar = TIER_AL[t], TIER_RARITY[t]
    m0 = mean(proxy(piece, a0, rar), a0, rar)
    if t + 1 < len(TIER_AL):
        a1 = TIER_AL[t + 1]
        m1 = mean(proxy(piece, a1, rar), a1, rar)
        slope = {k: max(0.0, (m1.get(k, v) - v) / (a1 - a0)) for k, v in m0.items()}
    else:
        slope = budget(piece, t - 1)[1]
        slope = {k: slope.get(k, 0.0) for k in m0}
    base = {k: max(0.1, v - slope[k] * (a0 - 1)) for k, v in m0.items()}
    return base, slope


def main():
    items, sets = [], []
    for cid, c in CLASSES.items():
        for t in range(6):
            set_id = f"set.class.{cid}_t{t + 1}"
            ids = []
            for piece in PIECES:
                base, slope = budget(piece, t)
                if piece == "boots" and "move_speed" not in base:  # keep the boots' speed when the proxy set has none
                    prev = budget(piece, t - 1)[0] if t > 0 else {}
                    base["move_speed"] = prev.get("move_speed", 1.5)
                f = FLOOR[TIER_RARITY[t]]
                k = 0.88 + 0.24 * (1 + f) / 2  # mean of [0.88c, 1.12c] at this rarity's roll floor
                stats, per = {}, {}
                for stat, b in base.items():
                    centre = b / k
                    stats[stat] = [round(centre * 0.88, 2), round(centre * 1.12, 2)]
                    if slope.get(stat, 0) > 0.0005:
                        per[stat] = round(slope[stat], 3)
                did = f"armor.class.{cid}.{piece}.t{t + 1}"
                ids.append(did)
                items.append({
                    "id": did,
                    "name": f"{c['name']} {TIER_NAME[t]} {PIECE_NAME[piece]}",
                    "material": f"minecraft:{TIER_MATERIAL[t]}_{piece}",
                    "type": piece.upper(),
                    "rarity": TIER_RARITY[t],
                    "maxRarity": TIER_RARITY[t],
                    "level": 1,
                    "stats": stats,
                    "perLevel": per,
                    "durability": 0,
                    "sell": 0,
                    "lore": c["lore"],
                    "classes": [cid],
                    "binding": "SOULBOUND",
                    "set": set_id,
                    "lootable": False,
                })
            sets.append({"id": set_id, "name": f"{c['setName']} · {TIER_NAME[t]}", "pieces": ids,
                         "bonuses": {"2": c["set2"](t), "4": c["set4"](t)}})
    with open(os.path.join(ITEMS, "class_armor.json"), "w", encoding="utf-8") as fh:
        json.dump({"items": items}, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    sp = os.path.join(ITEMS, "sets.json")
    doc = json.load(open(sp, encoding="utf-8"))
    doc["sets"] = [s for s in doc["sets"] if not s["id"].startswith("set.class.")] + sets
    with open(sp, "w", encoding="utf-8") as fh:
        json.dump(doc, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    idx = os.path.join(ITEMS, "index.json")
    index = json.load(open(idx, encoding="utf-8"))
    if "class_armor.json" not in index["items"]:
        index["items"].insert(index["items"].index("armor.json") + 1, "class_armor.json")
        with open(idx, "w", encoding="utf-8") as fh:
            json.dump(index, fh, ensure_ascii=False, indent=1)
            fh.write("\n")
    print(f"{len(items)} class armour definitions, {len(sets)} sets")


if __name__ == "__main__":
    main()
