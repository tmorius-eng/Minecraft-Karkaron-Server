#!/usr/bin/env python3
"""Validate the Kharkhorum master plan + gameplay points (assets/world/kharkhorum/*.json).

Critical checks (exit 1): ids unique, everything inside the city bounds, districts do not overlap
(hidden underground districts excepted), gates on the wall outline, every point inside its district,
every required gameplay point type present, landmarks owned by a district, roads inside bounds.
"""
from __future__ import annotations

import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIR = os.path.join(ROOT, "assets", "world", "kharkhorum")
REQUIRED_POINT_TYPES = {"spawn", "service", "quest_npc", "merchant", "fast_travel", "dungeon", "event", "secret", "exit"}
REQUIRED_POINTS = {"spawn", "class_selection", "quest.first_hunt", "merchant.general", "blacksmith", "clan_hall",
                   "relic_hall", "achievements", "dungeon.entrance.khasar", "fast_travel.plaza", "exit.first_adventure"}


def parts(d):
    if "parts" in d:
        return [("rect", p) for p in d["parts"]]
    if "circle" in d.get("shape", {}):
        return [("circle", d["shape"]["circle"])]
    return []


def contains(d, x, z, plan):
    if d["id"] == "district.outer_wall":
        b = plan["bounds"]
        edge = min(x - b["x1"], b["x2"] - x, z - b["z1"], b["z2"] - z)
        return edge <= 24 or not (b["x1"] <= x <= b["x2"] and b["z1"] <= z <= b["z2"])
    for kind, p in parts(d):
        if kind == "rect" and p["x1"] <= x <= p["x2"] and p["z1"] <= z <= p["z2"]:
            return True
        if kind == "circle" and math.hypot(x - p["x"], z - p["z"]) <= p["r"]:
            return True
    return False


def rect_of(kind, p):
    if kind == "rect":
        return p["x1"], p["z1"], p["x2"], p["z2"]
    return p["x"] - p["r"], p["z"] - p["r"], p["x"] + p["r"], p["z"] + p["r"]


def overlap(a, b):
    return not (a[2] < b[0] or b[2] < a[0] or a[3] < b[1] or b[3] < a[1])


def main() -> int:
    errors, warnings = [], []
    plan = json.load(open(os.path.join(DIR, "master-plan.json"), encoding="utf-8"))
    pts = json.load(open(os.path.join(DIR, "points.json"), encoding="utf-8"))
    b = plan["bounds"]
    inside = lambda x, z: b["x1"] <= x <= b["x2"] and b["z1"] <= z <= b["z2"]

    ids = [d["id"] for d in plan["districts"]] + [g["id"] for g in plan["gates"]] + [r["id"] for r in plan["roads"]] \
        + [l["id"] for l in plan["landmarks"]] + [p["id"] for p in pts["points"]]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        errors.append(f"duplicate ids: {sorted(dup)}")

    districts = {d["id"]: d for d in plan["districts"]}
    surface = [d for d in plan["districts"] if not d.get("hidden") and d["id"] != "district.outer_wall"]
    for d in surface:
        for kind, p in parts(d):
            r = rect_of(kind, p)
            if not (inside(r[0], r[1]) and inside(r[2], r[3])):
                errors.append(f"{d['id']} extends outside the city bounds: {r}")
    for i, a in enumerate(surface):
        for c in surface[i + 1:]:
            for ka, pa in parts(a):
                for kc, pc in parts(c):
                    if overlap(rect_of(ka, pa), rect_of(kc, pc)):
                        errors.append(f"districts overlap: {a['id']} {rect_of(ka, pa)} x {c['id']} {rect_of(kc, pc)}")

    xs = [pt[0] for pt in plan["walls"]["outline"]]
    zs = [pt[1] for pt in plan["walls"]["outline"]]
    for g in plan["gates"]:
        on_wall = g["x"] in (min(xs), max(xs)) or g["z"] in (min(zs), max(zs))
        if not on_wall:
            errors.append(f"{g['id']} is not on the wall outline")

    for r in plan["roads"]:
        for x, z in r["points"]:
            if not inside(x, z):
                errors.append(f"{r['id']} point {x},{z} outside the city")

    for l in plan["landmarks"]:
        owners = [d["id"] for d in plan["districts"] if contains(d, l["x"], l["z"], plan)]
        if not owners:
            errors.append(f"{l['id']} at {l['x']},{l['z']} is in no district")
        listed = [d["id"] for d in plan["districts"] if l["id"] in d.get("landmarks", [])]
        if not listed:
            warnings.append(f"{l['id']} not listed in any district's landmarks")

    types = {p["type"] for p in pts["points"]}
    for t in REQUIRED_POINT_TYPES - types:
        errors.append(f"no gameplay point of type '{t}'")
    have = {p["id"] for p in pts["points"]}
    for need in REQUIRED_POINTS - have:
        errors.append(f"required point missing: {need}")
    for p in pts["points"]:
        d = districts.get(p["district"])
        if d is None:
            errors.append(f"point {p['id']}: unknown district {p['district']}")
        elif not contains(d, p["x"], p["z"], plan):
            errors.append(f"point {p['id']} ({p['x']},{p['z']}) is outside {p['district']}")
    for step in pts.get("player_flow", []):
        if step not in have and not step.startswith("district."):
            errors.append(f"player_flow step '{step}' has no point")

    for w in warnings:
        print("  warning:", w)
    if errors:
        print("WORLD PLAN VALIDATION FAILED:")
        for e in errors:
            print("  -", e)
        return 1
    print(f"world plan OK: {len(plan['districts'])} districts, {len(plan['gates'])} gates, {len(plan['roads'])} roads, "
          f"{len(plan['landmarks'])} landmarks, {len(pts['points'])} points")
    return 0


if __name__ == "__main__":
    sys.exit(main())
