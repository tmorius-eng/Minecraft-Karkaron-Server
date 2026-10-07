#!/usr/bin/env python3
"""SÜLD's mob and boss rigs beyond Хасар: authored cuboid rigs built from four body plans (docs/models/MOB_RIGS.md).

Every rig here is ORIGINAL work, drawn as cuboids from scratch in this file (no meshes, no third-party models), in
the exact asset contract of tools/model/khasar.py (bones in model pixels relative to the parent pivot, +Y up, +Z the
creature's forward, +X its left). tools/model/export_rig.py exports any rig named in ``RIGS``:

    python3 tools/model/export_rig.py chono_govi        # one rig
    python3 tools/model/export_fauna.py                 # every rig in RIGS

Body plans (bones are kept few on purpose: a region mob is on screen by the dozen; the renderer's LOD then updates
them every 2 ticks near a player, every 4 ticks further out and not at all beyond 48 blocks):

* ``canine``  – root, body, head, jaw, 4 legs, tail                                  8 displays (wolves)
* ``ursine``  – root, body, head, jaw, 4 legs                                        7 displays (bears)
* ``humanoid``– root, hips, torso, head, 2 arms, 2 legs (or a robe), weapon          8–9 displays (people, spirits,
                khans, the giant)
* ``arachnid``– root, body, 2 claws, 2 leg banks, 3 tail segments                    8 displays (the scorpion)

Materials are named here and painted by tools/model/fauna_paint.py from each rig's palette.
"""
from __future__ import annotations

import copy

from khasar import bone, c, mirror  # the shared cuboid helpers of the asset contract


def _atlases(size=64, density=1.0):
    return {"body": {"file": "body", "size": size, "density": density, "pad": 0}}


def _rig(rid, name, scale, host_height, bones, atlas=64, parts=(), variants=None, palette=None, plan="", note=""):
    return {"id": rid, "name_mn": name, "scale": scale, "hostHeight": host_height, "atlases": _atlases(atlas),
            "bones": bones, "parts": list(parts), "variants": variants or {}, "palette": palette or {}, "plan": plan,
            "note": note}


# ============================================================================================ canine

def canine(rid, name, palette, scale=1.0, ruff=True, lean=0.0):
    """A steppe wolf: long body, deep chest, narrow muzzle, bushy tail. ``lean`` thins the body (desert wolves)."""
    w = 4 - lean
    body = [
        c((-w, -4, -8), (w, 4, 7), mat="fur", mats={"down": "belly"}),
        c((-w + 0.5, 3.5, -6), (w - 0.5, 5, 3), mat="back", skip={"down"}),             # dark saddle along the spine
    ]
    if ruff:
        body.append(c((-w - 0.75, -3.5, 3), (w + 0.75, 5, 8.5), mat="ruff", skip={"north"}))  # chest and neck ruff
    head = [
        c((-3, -3, -1), (3, 3, 5), mat="head"),
        c((-1.75, -2.75, 5), (1.75, 0.25, 9.5), mat="muzzle", mats={"down": "jawline"}, skip={"north"}),
        c((-1, -0.75, 9.5), (1, 0.75, 10.25), mat="nose", skip={"north"}),
        c((1.1, 0.5, 4.75), (2.6, 1.6, 5.15), mat="eye", skip={"north"}),
        c((1.0, 3, 0.5), (3.0, 6, 2.0), mat="ear"),
    ]
    head = head + mirror([head[3], head[4]])
    jaw = [c((-1.5, -1.5, 0), (1.5, 0, 4.25), mat="jaw", mats={"up": "mouth"}, skip={"north"})]
    leg = [c((-1.1, -9, -1.1), (1.1, 0, 1.1), mat="leg"), c((-1.4, -9.25, -1.3), (1.4, -8, 1.9), mat="paw")]
    tail = [c((-1.25, -1.25, -8.5), (1.25, 1.25, 0), mat="tail"), c((-1.0, -1.0, -10), (1.0, 1.0, -8.5), mat="tail_tip")]
    bones = [
        bone("root", None, (0, 0, 0), model=False),
        bone("body", "root", (0, 12.5, 0), cubes=body),
        bone("head", "body", (0, 3, 7.5), (-8, 0, 0), cubes=head),
        bone("jaw", "head", (0, -2.75, 5), cubes=jaw),
        bone("leg_fl", "body", (2.4, -3.5, 5), cubes=leg),
        bone("leg_fr", "body", (-2.4, -3.5, 5), cubes=mirror(leg)),
        bone("leg_hl", "body", (2.4, -3.5, -6), cubes=leg),
        bone("leg_hr", "body", (-2.4, -3.5, -6), cubes=mirror(leg)),
        bone("tail", "body", (0, 3, -8), (-38, 0, 0), cubes=tail),
    ]
    return _rig(rid, name, scale, 0.85 * scale, bones, palette=palette, plan="canine")


# ============================================================================================ ursine

def ursine(rid, name, palette, scale=1.0, moss=False, crown=False):
    """A Khangai bear: heavy forequarters with a shoulder hump, short neck, round ears, thick legs and long claws.
    ``moss`` grows a mossy back with spruce twigs (Ойн Эзэн); ``crown`` adds a ring of antler-like branches."""
    body = [
        c((-7, -7, -1), (7, 6, 12), mat="fur", mats={"down": "belly"}),
        c((-6.5, -6, -13), (6.5, 5, -1), mat="fur", mats={"down": "belly"}, skip={"south"}),
        c((-5.5, 6, 1), (5.5, 8.5, 9), mat="back", skip={"down"}),                      # shoulder hump
        c((-1.5, 0, -14.5), (1.5, 3, -13), mat="fur", skip={"south"}),                  # stub tail
    ]
    if moss:
        body += [c((-5, 5, -11), (5, 7, 0), mat="moss", skip={"down"}),
                 c((-4, 8.5, 2), (4, 9.5, 8), mat="moss", skip={"down"}),
                 c((2, 7, -6), (3, 12, -5), mat="twig"), c((-3, 9.5, 4), (-2, 14, 5), mat="twig"),
                 c((-1, 7, -9), (0, 11, -8), mat="twig")]
    head = [
        c((-4, -4, -1), (4, 4, 6), mat="head"),
        c((-2.5, -3.5, 6), (2.5, 0.5, 10.5), mat="muzzle", mats={"down": "jawline"}, skip={"north"}),
        c((-1.25, -1, 10.5), (1.25, 0.75, 11.25), mat="nose", skip={"north"}),
        c((1.4, 0.75, 5.75), (3.0, 1.9, 6.15), mat="eye", skip={"north"}),
        c((2.5, 3.5, 0.5), (5, 6, 2.5), mat="ear"),
    ]
    head = head + mirror([head[3], head[4]])
    if crown:
        head += [c((3, 4, 1), (4, 9, 2), mat="twig"), c((-4, 4, 1), (-3, 9, 2), mat="twig"),
                 c((4, 8, 1), (6, 9, 2), mat="twig"), c((-6, 8, 1), (-4, 9, 2), mat="twig")]
    jaw = [c((-2, -1.75, 0), (2, 0, 4.25), mat="jaw", mats={"up": "mouth"}, skip={"north"})]
    leg = [c((-2.5, -9, -2.5), (2.5, 0, 2.5), mat="leg"),
           c((-2.75, -9.5, -2.75), (2.75, -8, 3.25), mat="paw"),
           c((-2.25, -9.5, 3.25), (-1.25, -8.75, 4.5), mat="claw"), c((-0.5, -9.5, 3.25), (0.5, -8.75, 4.75), mat="claw"),
           c((1.25, -9.5, 3.25), (2.25, -8.75, 4.5), mat="claw")]
    bones = [
        bone("root", None, (0, 0, 0), model=False),
        bone("body", "root", (0, 14, 0), cubes=body),
        bone("head", "body", (0, 2, 12), (-6, 0, 0), cubes=head),
        bone("jaw", "head", (0, -3.5, 6), cubes=jaw),
        bone("leg_fl", "body", (4.5, -5, 7.5), cubes=leg),
        bone("leg_fr", "body", (-4.5, -5, 7.5), cubes=mirror(leg)),
        bone("leg_hl", "body", (4.5, -5, -9), cubes=leg),
        bone("leg_hr", "body", (-4.5, -5, -9), cubes=mirror(leg)),
    ]
    return _rig(rid, name, scale, 1.4 * scale, bones, atlas=128 if (moss or crown) else 64, palette=palette, plan="ursine")


# ============================================================================================ humanoid

def humanoid(rid, name, palette, scale=1.0, kind="warrior", weapon="saber", headwear="fur_hat", spirit=False):
    """A person of the steppe or a spirit wearing one's shape. ``kind`` picks the body: ``warrior`` (deel robe to the
    knees, boots), ``wraith`` (no legs: a long ragged robe that floats), ``brute`` (bare arms, fur loincloth, big
    hands). ``weapon``: saber, spear, staff, club, none. ``headwear``: fur_hat (лоовууз), crown, ice_crown, hood, none."""
    legs = kind != "wraith"
    torso = [
        c((-4, 0, -2.25), (4, 11, 2.25), mat="robe", mats={"south": "robe_front"}),
        c((-4.25, 0, -2.5), (4.25, 1.5, 2.5), mat="belt"),
        c((-4.5, 9, -2.75), (4.5, 12, 2.75), mat="shoulder", skip={"down"}),
    ]
    hips = []
    if kind == "warrior":
        hips = [c((-4.5, -6.5, -2.75), (4.5, 0, 2.75), mat="robe", mats={"south": "robe_front"}, skip={"up"})]
    elif kind == "wraith":
        hips = [c((-4.5, -7, -2.75), (4.5, 0, 2.75), mat="robe", skip={"up"}),
                c((-4, -12, -2.5), (4, -7, 2.5), mat="rag", skip={"up"}),
                c((-3, -15, -2), (3, -12, 2), mat="rag", skip={"up"})]
    elif kind == "brute":
        hips = [c((-4.5, -4, -2.75), (4.5, 0, 2.75), mat="loincloth", skip={"up"})]
    head = [c((-4, 0, -4), (4, 8, 4), mat="skin", mats={"south": "face"})]
    if headwear == "fur_hat":
        head += [c((-4.5, 5.5, -4.5), (4.5, 8.5, 4.5), mat="hat_fur", skip={"down"}),
                 c((-3, 8.5, -3), (3, 11, 3), mat="hat_top", skip={"down"}),
                 c((-0.5, 11, -0.5), (0.5, 12.5, 0.5), mat="hat_knot")]
    elif headwear == "crown":
        head += [c((-4.5, 7, -4.5), (4.5, 9, 4.5), mat="gold", skip={"down"}),
                 c((-1, 9, 3.5), (1, 12, 4.5), mat="gold"), c((-4.5, 9, -1), (-3.5, 11, 1), mat="gold"),
                 c((3.5, 9, -1), (4.5, 11, 1), mat="gold"), c((-0.5, 10, 4.5), (0.5, 11, 4.75), mat="gem")]
    elif headwear == "ice_crown":
        head += [c((-4.5, 7, -4.5), (4.5, 8.5, 4.5), mat="ice", skip={"down"}),
                 c((-0.75, 8.5, 3.5), (0.75, 14, 4.5), mat="ice"), c((-4.5, 8.5, 2), (-3.5, 12, 3), mat="ice"),
                 c((3.5, 8.5, 2), (4.5, 12, 3), mat="ice"), c((-2.5, 8.5, -4.5), (-1.5, 11, -3.5), mat="ice"),
                 c((1.5, 8.5, -4.5), (2.5, 11, -3.5), mat="ice")]
    elif headwear == "hood":
        head += [c((-4.5, 1, -4.75), (4.5, 8.75, 4.5), mat="rag", mats={"south": "hood_open"}, skip={"down"})]
    if spirit:
        head.append(c((-3, 3.25, 4.01), (3, 4.75, 4.2), mat="glow_eyes", skip={"north"}))
    arm_w = 2.5 if kind == "brute" else 2
    arm = [c((-arm_w, -11, -arm_w), (arm_w, 1, arm_w), mat="sleeve" if kind != "brute" else "skin"),
           c((-arm_w + 0.25, -12.5, -arm_w + 0.25), (arm_w - 0.25, -11, arm_w - 0.25), mat="hand")]
    if kind == "wraith":
        arm = [c((-2, -10, -2), (2, 1, 2), mat="robe"), c((-2.25, -12, -2.25), (2.25, -10, 2.25), mat="rag"),
               c((-1.25, -13, -1.25), (1.25, -12, 1.25), mat="hand")]
    leg = [c((-2, -12, -2), (2, 0, 2), mat="trousers"), c((-2.25, -12, -2.5), (2.25, -7, 2.75), mat="boot")]
    weapons = {
        "saber": [c((-0.6, -1.25, -1), (0.6, 1.25, 1), mat="hilt"), c((-1.5, -0.5, 1), (1.5, 0.5, 1.6), mat="gold"),
                  c((-0.35, -0.9, 1.6), (0.35, 0.9, 15), mat="steel")],
        "spear": [c((-0.5, -0.5, -10), (0.5, 0.5, 14), mat="shaft"), c((-0.9, -0.9, 14), (0.9, 0.9, 19), mat="ice"),
                  c((-1.2, -0.3, 13.2), (1.2, 0.3, 14), mat="tassel")],
        "staff": [c((-0.6, -0.6, -8), (0.6, 0.6, 12), mat="shaft"), c((-1.6, -1.6, 12), (1.6, 1.6, 15), mat="gold"),
                  c((-0.9, -0.9, 15), (0.9, 0.9, 17), mat="gem")],
        "club": [c((-1.2, -1.2, -2), (1.2, 1.2, 8), mat="shaft"), c((-2.5, -2.5, 8), (2.5, 2.5, 16), mat="club_head")],
    }
    bones = [bone("root", None, (0, 0, 0), model=False)]
    hip_y = 12 if legs else 15
    bones.append(bone("hips", "root", (0, hip_y, 0), cubes=hips, model=bool(hips)))
    bones.append(bone("torso", "hips", (0, 0, 0), cubes=torso))
    bones.append(bone("head", "torso", (0, 12, 0), cubes=head))
    bones.append(bone("arm_l", "torso", (4 + arm_w, 10, 0), cubes=arm))
    bones.append(bone("arm_r", "torso", (-4 - arm_w, 10, 0), cubes=mirror(arm)))
    if legs:
        bones.append(bone("leg_l", "hips", (2, 0, 0), cubes=leg))
        bones.append(bone("leg_r", "hips", (-2, 0, 0), cubes=mirror(leg)))
    if weapon in weapons:
        bones.append(bone("weapon", "arm_r", (0, -11.75, 0), (-80, 0, 0), cubes=weapons[weapon]))
    hh = 1.95 if legs else 1.95
    return _rig(rid, name, scale, hh * scale, bones, atlas=128 if headwear in ("crown", "ice_crown") or kind != "warrior" else 64,
                palette=palette, plan="humanoid")


# ============================================================================================ arachnid

def arachnid(rid, name, palette, scale=1.0):
    """The Gobi scorpion: a flat segmented carapace, two heavy pincers held forward, four legs a side and a tail of
    three segments curling over the back to the stinger."""
    body = [
        c((-4, -1.5, -6), (4, 2, 4), mat="shell", mats={"down": "belly"}),
        c((-3, -1.25, 4), (3, 1.75, 7.5), mat="shell", mats={"down": "belly"}, skip={"north"}),
        c((-1.6, 0.6, 7.5), (1.6, 1.4, 7.9), mat="eye", skip={"north"}),
        c((-3.5, 2, -5), (3.5, 2.75, 3), mat="plate", skip={"down"}),
    ]
    claw = [c((-1, -1, 0), (1, 1, 5), mat="shell"), c((-2, -1.5, 5), (2, 1.5, 8.5), mat="claw"),
            c((-2, -1.25, 8.5), (-0.4, 1.25, 11), mat="claw"), c((0.4, -1.25, 8.5), (2, 1.25, 10.5), mat="claw_tip")]
    bank = []
    for k, z in enumerate((3, 0.5, -2, -4.5)):
        bank.append(c((0, -0.6, z - 0.6), (6.5 - k * 0.3, 0.6, z + 0.6), mat="leg", rot=("z", -22.5, (0, 0, z))))
        bank.append(c((5.5 - k * 0.3, -4.5, z - 0.5), (6.6 - k * 0.3, -0.5, z + 0.5), mat="leg_tip"))
    tail1 = [c((-1.75, -1.5, -6), (1.75, 1.5, 0), mat="shell")]
    tail2 = [c((-1.5, -1.25, -6), (1.5, 1.25, 0), mat="shell")]
    tail3 = [c((-1.25, -1.25, -4), (1.25, 1.25, 0), mat="sting_bulb"), c((-0.5, -0.5, -6.5), (0.5, 0.5, -4), mat="sting")]
    bones = [
        bone("root", None, (0, 0, 0), model=False),
        bone("body", "root", (0, 4, 0), cubes=body),
        bone("claw_l", "body", (3, 0, 6), (0, -18, 0), cubes=claw),
        bone("claw_r", "body", (-3, 0, 6), (0, 18, 0), cubes=mirror(claw)),
        bone("legs_l", "body", (3.5, 0, 0), cubes=bank),
        bone("legs_r", "body", (-3.5, 0, 0), cubes=mirror(bank)),
        bone("tail_1", "body", (0, 1, -6), (55, 0, 0), cubes=tail1),
        bone("tail_2", "tail_1", (0, 0, -6), (45, 0, 0), cubes=tail2),
        bone("tail_3", "tail_2", (0, 0, -6), (50, 0, 0), cubes=tail3),
    ]
    return _rig(rid, name, scale, 0.6 * scale, bones, palette=palette, plan="arachnid")


# ============================================================================================ palettes (hex ramps, dark → light)

P = {
    "govi_wolf": {"fur": ["#5E4630", "#7A5C3E", "#96754F", "#B08F64", "#C8A97C"], "back": ["#3F2E20", "#56402C", "#6E5238"],
                  "belly": ["#A08A6A", "#BBA582", "#D2BE9A"], "ruff": ["#8C7150", "#A88A64", "#C2A47A", "#D6BC92"],
                  "eye": ["#2A1606", "#A8641E", "#E0A040"]},
    "orkhon_wolf": {"fur": ["#3C3A38", "#55524E", "#6E6A64", "#87827A", "#A09A90"], "back": ["#24221F", "#33302C", "#45413B"],
                    "belly": ["#8A8478", "#A39C8E", "#BAB3A4"], "ruff": ["#5C5852", "#75706A", "#8E8880", "#A8A296"],
                    "eye": ["#20140A", "#8A5A1E", "#D49A3A"]},
    "grey_wolf": {"fur": ["#4E5258", "#6A6F76", "#868C93", "#A3A8AE", "#BEC2C7"], "back": ["#2F3236", "#3F4348", "#52575D"],
                  "belly": ["#B7B9BB", "#CDCFD0", "#E1E2E2"], "ruff": ["#9EA2A6", "#B6B9BC", "#CDD0D2", "#E0E2E3"],
                  "eye": ["#1A1A0E", "#7E8A2A", "#C8D25A"]},
    "brown_bear": {"fur": ["#3A2416", "#4E3220", "#64422B", "#7A5437", "#906845"], "back": ["#2C1B10", "#3A2416", "#4E3220"],
                   "belly": ["#5A4030", "#6E5240", "#82644E"], "eye": ["#120A06", "#4A2A12", "#7A4A22"]},
    "forest_lord": {"fur": ["#141210", "#1E1B18", "#2A2622", "#37322C", "#453F37"], "back": ["#0E0C0A", "#16130F", "#201C17"],
                    "belly": ["#2E2A25", "#3B3630", "#48423A"], "moss": ["#2E4A1E", "#3E6026", "#4F7830", "#62903C"],
                    "twig": ["#3A2A1C", "#4E3A26", "#634A32"], "eye": ["#0A1A06", "#3A8A1E", "#8AE05A"]},
    "bandit": {"robe": ["#2A3A52", "#36496A", "#445A80", "#536B94"], "belt": ["#5A3A1E", "#74502A", "#8E6638"],
               "shoulder": ["#3A2A1C", "#4E3A26", "#634A32"], "skin": ["#7A5038", "#956448", "#AE7A58", "#C49070"],
               "trousers": ["#2E2620", "#3E342C", "#4E4238"], "boot": ["#1E1612", "#2C201A", "#3C2C22"],
               "hat_fur": ["#3A2A1C", "#54402C", "#6E563C", "#886C4C"], "hat_top": ["#7A1E1E", "#962A26", "#B03A30"],
               "hat_knot": ["#C8A040", "#E0BC58"], "sleeve": ["#2A3A52", "#36496A", "#445A80"], "hand": ["#7A5038", "#956448", "#AE7A58"],
               "hilt": ["#2A1A10", "#3E2818", "#523620"], "gold": ["#8A6A1E", "#B08A2A", "#D4AC40", "#ECCB66"],
               "steel": ["#5E6268", "#80858C", "#A4A9AF", "#C8CCD0", "#E6E8EA"]},
    "sand_spirit": {"robe": ["#7A6440", "#967C54", "#B09468", "#C8AC7E"], "rag": ["#5E4A2E", "#7A6240", "#967C54"],
                    "belt": ["#4E3A22", "#644C2E"], "shoulder": ["#8A7250", "#A48A62", "#BCA276"],
                    "skin": ["#3E3226", "#54442E", "#6A5636"], "hand": ["#3E3226", "#54442E", "#6A5636"],
                    "glow_eyes": ["#FFB040", "#FFD070", "#FFF0B0"]},
    "ice_spirit": {"robe": ["#6A8AA8", "#86A6C2", "#A4C2DA", "#C4DCEE"], "rag": ["#4E6E8E", "#6A8AA8", "#86A6C2"],
                   "belt": ["#3A5A7A", "#4E6E8E"], "shoulder": ["#A4C2DA", "#C4DCEE", "#E2F0FA"],
                   "skin": ["#8AA4BC", "#A4BED4", "#C0D6E8"], "hand": ["#A4BED4", "#C0D6E8", "#DCEAF4"],
                   "glow_eyes": ["#60E0FF", "#A0F0FF", "#E8FCFF"], "ice": ["#7ABCE0", "#9ED2EE", "#C4E6F8", "#E8F6FE"]},
    "sand_khan": {"robe": ["#5A2A1A", "#743622", "#8E442C", "#A85436"], "robe_front": ["#8A6A1E", "#B08A2A", "#D4AC40"],
                  "rag": ["#8A7458", "#A48E6E", "#BCA684", "#D2BE9C"], "belt": ["#8A6A1E", "#B08A2A", "#D4AC40"],
                  "shoulder": ["#8A6A1E", "#B08A2A", "#D4AC40", "#ECCB66"], "skin": ["#9A8664", "#B29E7A", "#C8B690", "#DCCCA6"],
                  "hand": ["#9A8664", "#B29E7A", "#C8B690"], "trousers": ["#5A2A1A", "#743622", "#8E442C"],
                  "boot": ["#3A2416", "#4E3220"], "gold": ["#8A6A1E", "#B08A2A", "#D4AC40", "#ECCB66", "#FFE28A"],
                  "gem": ["#1E6A8A", "#2A8AB0", "#5AB8DA"], "shaft": ["#3A2416", "#4E3220", "#64422B"],
                  "glow_eyes": ["#FF7A20", "#FFB050", "#FFE0A0"], "sleeve": ["#5A2A1A", "#743622", "#8E442C"]},
    "ice_khan": {"robe": ["#2A4A6A", "#365C82", "#446E9A", "#5482B0"], "robe_front": ["#86A6C2", "#A4C2DA", "#C4DCEE"],
                 "belt": ["#C0C8D0", "#DCE2E8"], "shoulder": ["#7ABCE0", "#9ED2EE", "#C4E6F8", "#E8F6FE"],
                 "skin": ["#7A90A4", "#94AABE", "#AEC2D4"], "hand": ["#94AABE", "#AEC2D4", "#C8D8E6"],
                 "trousers": ["#1E3048", "#2A4060", "#365078"], "boot": ["#9ED2EE", "#C4E6F8", "#E8F6FE"],
                 "sleeve": ["#2A4A6A", "#365C82", "#446E9A"], "ice": ["#7ABCE0", "#9ED2EE", "#C4E6F8", "#E8F6FE"],
                 "shaft": ["#C0C8D0", "#DCE2E8", "#F0F4F8"], "tassel": ["#1E3048", "#2A4060"],
                 "glow_eyes": ["#40C8FF", "#90E8FF", "#E0FAFF"]},
    "giant": {"skin": ["#5A6A70", "#6E7E84", "#829298", "#96A6AC"], "face": ["#5A6A70", "#6E7E84", "#829298"],
              "robe": ["#4A3A2A", "#5E4A36", "#725A42"], "belt": ["#2E2014", "#3E2C1C"], "shoulder": ["#E0E4E8", "#F0F2F4", "#FFFFFF"],
              "loincloth": ["#5A4630", "#745A3E", "#8E6E4C", "#A8845C"], "hand": ["#5A6A70", "#6E7E84", "#829298"],
              "trousers": ["#5A6A70", "#6E7E84", "#829298"], "boot": ["#3A3028", "#4E4236", "#625444"],
              "shaft": ["#3A2A1C", "#4E3A26", "#634A32"], "club_head": ["#4A4E52", "#62666A", "#7A7E82", "#92969A"],
              "glow_eyes": ["#C0E8FF", "#E0F4FF", "#FFFFFF"]},
    "scorpion": {"shell": ["#4A2E14", "#62401E", "#7C5428", "#966A34"], "plate": ["#2E1C0C", "#3E2812", "#52361A"],
                 "belly": ["#A08050", "#B89868", "#CCAE80"], "claw": ["#5A3A18", "#764E22", "#92642E", "#AE7C3C"],
                 "claw_tip": ["#1E140A", "#2E1E10"], "leg": ["#62401E", "#7C5428", "#966A34"], "leg_tip": ["#2E1C0C", "#3E2812"],
                 "sting_bulb": ["#7A2A10", "#963818", "#B04A22"], "sting": ["#1A0E06", "#2A180C"],
                 "eye": ["#0A0604", "#3A1A0A", "#7A3A14"]},
}

# ---- dungeons 5–10 (docs/world/DUNGEON_LADDER.md)
P.update({
    "lus": {"robe": ["#0E3A44", "#145060", "#1C687A", "#268294"], "rag": ["#0A2C34", "#0E3A44", "#145060"],
            "belt": ["#0A2C34", "#0E3A44"], "shoulder": ["#1C687A", "#268294", "#3AA0B0"], "skin": ["#2E6A70", "#3E8288", "#529CA0"],
            "hand": ["#3E8288", "#529CA0", "#68B4B6"], "glow_eyes": ["#40FFE0", "#90FFF0", "#E0FFFA"]},
    "deep_wolf": {"fur": ["#1E2A36", "#2A3A4A", "#384C5E", "#486072", "#5A7486"], "back": ["#121A22", "#1A242E", "#24303C"],
                  "belly": ["#5A7486", "#6E8A9A", "#84A0AE"], "ruff": ["#3A5062", "#4C6476", "#5E788A", "#728C9C"],
                  "eye": ["#062020", "#1E8A8A", "#60E0E0"]},
    "tangut": {"robe": ["#4A4440", "#5E5852", "#726C64", "#888078"], "robe_front": ["#6A2A22", "#843830", "#9C4A3E"],
               "rag": ["#3A3430", "#4A4440", "#5E5852"], "belt": ["#6A2A22", "#843830"], "shoulder": ["#5A5048", "#6E6258", "#827468"],
               "skin": ["#8A8478", "#A09A8C", "#B6B0A2"], "hand": ["#8A8478", "#A09A8C"], "hilt": ["#2A2420", "#3A3430"],
               "gold": ["#6A5A3A", "#84724A", "#9E8A5C"], "steel": ["#4E5052", "#686A6C", "#828486", "#9C9EA0"],
               "glow_eyes": ["#FF5A3A", "#FF9A70", "#FFD0B8"], "trousers": ["#3A3430", "#4A4440"], "boot": ["#2A2420", "#3A3430"]},
    "ruin_scorpion": {"shell": ["#3A140E", "#521E14", "#6A2A1C", "#843826"], "plate": ["#200A06", "#2E100A", "#3E160E"],
                      "belly": ["#8A5A40", "#A06E52", "#B68464"], "claw": ["#4A1A10", "#622416", "#7C301E", "#963E28"],
                      "claw_tip": ["#140604", "#200A06"], "leg": ["#521E14", "#6A2A1C", "#843826"], "leg_tip": ["#200A06", "#2E100A"],
                      "sting_bulb": ["#C8A040", "#E0BC58", "#F4D878"], "sting": ["#140604", "#200A06"], "eye": ["#0A0402", "#5A1A0A", "#C84A1E"]},
    "raider": {"robe": ["#6A1E1A", "#842822", "#9E342C", "#B84438"], "belt": ["#2E2014", "#3E2C1C", "#503A26"],
               "shoulder": ["#3A2A1C", "#4E3A26", "#634A32"], "skin": ["#7A5038", "#956448", "#AE7A58"], "hand": ["#7A5038", "#956448"],
               "trousers": ["#2E2620", "#3E342C"], "boot": ["#1E1612", "#2C201A"], "hat_fur": ["#2A2018", "#3E3024", "#544232"],
               "hat_top": ["#1E1612", "#2C201A"], "hat_knot": ["#8A8A8A", "#B0B0B0"], "shaft": ["#3A2A1C", "#4E3A26", "#634A32"],
               "ice": ["#6E7276", "#8E9296", "#B0B4B8", "#D2D6DA"], "tassel": ["#9E342C", "#B84438"], "sleeve": ["#6A1E1A", "#842822"]},
    "war_wolf": {"fur": ["#1A1614", "#2A2420", "#3A322C", "#4A4038", "#5A4E44"], "back": ["#100C0A", "#1A1614", "#241E1A"],
                 "belly": ["#4A4038", "#5A4E44", "#6A5C50"], "ruff": ["#6A1E1A", "#842822", "#9E342C", "#B84438"],
                 "eye": ["#200604", "#A0301A", "#F06A3A"]},
    "savdag": {"robe": ["#3A4A30", "#4A5E3C", "#5C724A", "#708A5A"], "rag": ["#2E3A26", "#3A4A30", "#4A5E3C"],
               "belt": ["#5A5A5A", "#787878"], "shoulder": ["#787878", "#929292", "#ACACAC"], "skin": ["#6E6E6A", "#868682", "#9E9E98"],
               "hand": ["#6E6E6A", "#868682"], "glow_eyes": ["#7AFF6A", "#B8FFAE", "#ECFFE8"]},
    "cave_bear": {"fur": ["#2A2A2C", "#3A3A3E", "#4C4C50", "#5E5E62", "#727276"], "back": ["#1E1E20", "#2A2A2C", "#3A3A3E"],
                  "belly": ["#5E5E62", "#727276", "#86868A"], "eye": ["#0A0A0A", "#3A3A2A", "#8A8A5A"]},
    "sky_soldier": {"robe": ["#C8D8E8", "#DCE8F4", "#EEF4FA", "#FFFFFF"], "robe_front": ["#4A7AB0", "#5A90C8", "#6EA6DC"],
                    "belt": ["#C8A040", "#E0BC58"], "shoulder": ["#C0C8D0", "#DCE2E8", "#F0F4F8"], "skin": ["#A8B4C0", "#BCC6D0", "#D0D8E0"],
                    "hand": ["#BCC6D0", "#D0D8E0"], "trousers": ["#4A7AB0", "#5A90C8"], "boot": ["#C0C8D0", "#DCE2E8"],
                    "gold": ["#B0B8C0", "#C8D0D8", "#E0E6EC", "#F4F8FC"], "gem": ["#4A90D0", "#6AB0F0"], "shaft": ["#C8A040", "#E0BC58"],
                    "ice": ["#9EC8F0", "#C0DCF6", "#E2F0FC"], "tassel": ["#4A7AB0", "#5A90C8"], "sleeve": ["#C8D8E8", "#DCE8F4"],
                    "glow_eyes": ["#80C0FF", "#B8DCFF", "#F0F8FF"]},
    "sky_wolf": {"fur": ["#B8C8D8", "#CCD8E4", "#DEE8F0", "#EEF4F8", "#FFFFFF"], "back": ["#8AA4C0", "#A0B8D0", "#B8CCE0"],
                 "belly": ["#EEF4F8", "#F8FBFD", "#FFFFFF"], "ruff": ["#DEE8F0", "#EEF4F8", "#F8FBFD", "#FFFFFF"],
                 "eye": ["#0A2040", "#3A80D0", "#9ED0FF"]},
    "palace_guard": {"robe": ["#14286A", "#1C3486", "#2442A2", "#3254BC"], "robe_front": ["#B08A2A", "#D4AC40", "#ECCB66"],
                     "belt": ["#B08A2A", "#D4AC40"], "shoulder": ["#B08A2A", "#D4AC40", "#ECCB66", "#FFE28A"],
                     "skin": ["#956448", "#AE7A58", "#C49070"], "hand": ["#956448", "#AE7A58"], "trousers": ["#0E1C4E", "#14286A"],
                     "boot": ["#2A1A10", "#3E2818"], "gold": ["#8A6A1E", "#B08A2A", "#D4AC40", "#ECCB66", "#FFE28A"],
                     "gem": ["#1E4ABA", "#3A6AE0"], "shaft": ["#3A2416", "#4E3220"], "ice": ["#B0B4B8", "#D2D6DA", "#F0F2F4"],
                     "tassel": ["#2442A2", "#3254BC"], "sleeve": ["#14286A", "#1C3486"]},
    "black_general": {"robe": ["#141414", "#1E1E1E", "#2A2A2A", "#363636"], "robe_front": ["#5A1414", "#741C1C", "#8E2626"],
                      "belt": ["#5A1414", "#741C1C"], "shoulder": ["#2A2A2E", "#3A3A40", "#4C4C54", "#60606A"],
                      "skin": ["#4A4440", "#5E5852", "#726C64"], "hand": ["#2A2A2E", "#3A3A40"], "trousers": ["#141414", "#1E1E1E"],
                      "boot": ["#0A0A0A", "#141414"], "gold": ["#3A3A40", "#4C4C54", "#60606A", "#7A7A86"], "gem": ["#A01414", "#E02020"],
                      "hilt": ["#0A0A0A", "#1E1E1E"], "steel": ["#3A3E44", "#52565C", "#6C7076", "#8A8E94", "#B0B4BA"],
                      "glow_eyes": ["#FF2A1A", "#FF6A4A", "#FFB0A0"], "sleeve": ["#141414", "#1E1E1E"]},
    "red_lord": {"robe": ["#8E1E16", "#A82A20", "#C2382C", "#D84A3C"], "robe_front": ["#C8A040", "#E0BC58", "#F4D878"],
                 "belt": ["#2E2014", "#3E2C1C"], "shoulder": ["#5A3A1E", "#74502A", "#8E6638"], "skin": ["#8A5A40", "#A06E52", "#B68464"],
                 "hand": ["#8A5A40", "#A06E52"], "trousers": ["#2E2620", "#3E342C"], "boot": ["#1E1612", "#2C201A"],
                 "hat_fur": ["#4A3020", "#644430", "#7E5A40", "#987052"], "hat_top": ["#C8A040", "#E0BC58"], "hat_knot": ["#E02020", "#FF4040"],
                 "hilt": ["#2A1A10", "#3E2818"], "gold": ["#8A6A1E", "#B08A2A", "#D4AC40", "#ECCB66"],
                 "steel": ["#5E6268", "#80858C", "#A4A9AF", "#C8CCD0", "#E6E8EA"], "sleeve": ["#8E1E16", "#A82A20"]},
    "mountain_lord": {"skin": ["#4E5248", "#62665A", "#767A6C", "#8A8E80"], "face": ["#4E5248", "#62665A", "#767A6C"],
                      "robe": ["#3A4A30", "#4A5E3C", "#5C724A"], "belt": ["#2E2014", "#3E2C1C"], "shoulder": ["#3E6026", "#4F7830", "#62903C"],
                      "loincloth": ["#3A4A30", "#4A5E3C", "#5C724A", "#708A5A"], "hand": ["#4E5248", "#62665A"],
                      "trousers": ["#4E5248", "#62665A"], "boot": ["#3A3E34", "#4E5248"], "shaft": ["#3A2A1C", "#4E3A26"],
                      "club_head": ["#5A5E62", "#72767A", "#8A8E92", "#A2A6AA"], "glow_eyes": ["#7AFF6A", "#B8FFAE", "#ECFFE8"]},
})

RIGS = {
    # wolves of the three regions (region mobs and Хасар's pack)
    "chono_govi": canine("chono_govi", "Говийн Чоно", P["govi_wolf"], scale=0.95, lean=0.5),
    "chono_orkhon": canine("chono_orkhon", "Орхоны Чоно", P["orkhon_wolf"], scale=1.05),
    "chono_saaral": canine("chono_saaral", "Хангайн Саарал Чоно", P["grey_wolf"], scale=1.1),
    # bears
    "baavgai": ursine("baavgai", "Хангайн Баавгай", P["brown_bear"], scale=1.0),
    "oin_ezen": ursine("oin_ezen", "Хар Баавгай — Ойн Эзэн", P["forest_lord"], scale=1.55, moss=True, crown=True),
    # people and spirits
    "deeremchin": humanoid("deeremchin", "Дээрэмчин", P["bandit"], kind="warrior", weapon="saber", headwear="fur_hat"),
    "elsnii_suns": humanoid("elsnii_suns", "Элсний Сүнс", P["sand_spirit"], kind="wraith", weapon="none", headwear="hood", spirit=True),
    "mosun_suns": humanoid("mosun_suns", "Алтайн Мөсөн Сүнс", P["ice_spirit"], kind="wraith", weapon="none", headwear="ice_crown", spirit=True),
    "elsnii_khaan": humanoid("elsnii_khaan", "Элсний Хаан — Булшны Эзэн", P["sand_khan"], scale=1.35, kind="warrior", weapon="staff",
                             headwear="crown", spirit=True),
    "mosun_khaan": humanoid("mosun_khaan", "Мөсөн Хаан — Оргилын Сахиул", P["ice_khan"], scale=1.45, kind="warrior", weapon="spear",
                            headwear="ice_crown", spirit=True),
    "altai_avarga": humanoid("altai_avarga", "Алтайн Аварга", P["giant"], scale=2.1, kind="brute", weapon="club", headwear="none", spirit=True),
    # the scorpion
    "khilents": arachnid("khilents", "Говийн Хилэнц", P["scorpion"], scale=1.0),
    # dungeons 5–10
    "usny_lus": humanoid("usny_lus", "Усны Лус", P["lus"], kind="wraith", weapon="none", headwear="hood", spirit=True),
    "dalain_chono": canine("dalain_chono", "Далайн Чоно", P["deep_wolf"], scale=1.15),
    "lusyn_khaan": humanoid("lusyn_khaan", "Лусын Хаан — Далайн Эзэн", dict(P["lus"], gold=P["lus"]["shoulder"], gem=["#40FFE0", "#90FFF0"],
                            shaft=P["lus"]["rag"]), scale=1.5, kind="wraith", weapon="staff", headwear="crown", spirit=True),
    "tangud_suns": humanoid("tangud_suns", "Тангудын Сүнс", P["tangut"], kind="warrior", weapon="saber", headwear="hood", spirit=True),
    "balgasny_khilents": arachnid("balgasny_khilents", "Балгасны Аварга Хилэнц", P["ruin_scorpion"], scale=1.6),
    "khar_janjin": humanoid("khar_janjin", "Хар Жанжин — Балгасны Эзэн", P["black_general"], scale=1.5, kind="warrior", weapon="saber",
                            headwear="crown", spirit=True),
    "khureenii_kharuul": humanoid("khureenii_kharuul", "Хүрээний Харуул", P["raider"], kind="warrior", weapon="spear", headwear="fur_hat"),
    "dainy_chono": canine("dainy_chono", "Дайны Чоно", P["war_wolf"], scale=1.25),
    "ulaan_khadny_noyon": humanoid("ulaan_khadny_noyon", "Улаан Хадны Ноён", P["red_lord"], scale=1.45, kind="warrior", weapon="saber",
                                   headwear="fur_hat"),
    "uulyn_savdag": humanoid("uulyn_savdag", "Уулын Савдаг", P["savdag"], kind="wraith", weapon="none", headwear="hood", spirit=True),
    "aguin_baavgai": ursine("aguin_baavgai", "Агуйн Баавгай", P["cave_bear"], scale=1.15),
    "khangai_savdag": humanoid("khangai_savdag", "Хангай Савдаг — Уулын Эзэн", P["mountain_lord"], scale=2.3, kind="brute", weapon="club",
                               headwear="none", spirit=True),
    "tengeriin_tsereg": humanoid("tengeriin_tsereg", "Тэнгэрийн Цэрэг", P["sky_soldier"], kind="warrior", weapon="spear", headwear="crown"),
    "tengeriin_chono": canine("tengeriin_chono", "Тэнгэрийн Чоно", P["sky_wolf"], scale=1.2),
    "khukh_tengeriin_elch": humanoid("khukh_tengeriin_elch", "Хөх Тэнгэрийн Элч", P["sky_soldier"], scale=1.5, kind="warrior", weapon="staff",
                                     headwear="ice_crown", spirit=True),
    "ordny_sakhiul": humanoid("ordny_sakhiul", "Ордны Сахиул", P["palace_guard"], kind="warrior", weapon="spear", headwear="crown"),
    "khukh_suldiin_sakhiul": humanoid("khukh_suldiin_sakhiul", "Хөх Сүлдийн Сахиул", P["palace_guard"], scale=1.7, kind="warrior",
                                      weapon="spear", headwear="crown", spirit=True),
}

# mob id → rig id, read by the plugin (SuldContent.MODELS) through suld-plugin/src/main/resources/models/index.json
MOBS = {
    "mob.goviin_chono": "chono_govi", "mob.orkhon_chono": "chono_orkhon", "mob.saaral_chono": "chono_saaral",
    "mob.khangai_baavgai": "baavgai", "mob.oin_ezen": "oin_ezen",
    "mob.deeremchin": "deeremchin", "mob.elsnii_suns": "elsnii_suns", "mob.altai_mosun_suns": "mosun_suns",
    "mob.elsnii_khaan": "elsnii_khaan", "mob.mosun_khaan": "mosun_khaan", "mob.altai_avarga": "altai_avarga",
    "mob.goviin_khilents": "khilents",
    "mob.usny_lus": "usny_lus", "mob.dalain_chono": "dalain_chono", "mob.lusyn_khaan": "lusyn_khaan",
    "mob.tangud_suns": "tangud_suns", "mob.balgasny_khilents": "balgasny_khilents", "mob.khar_janjin": "khar_janjin",
    "mob.khureenii_kharuul": "khureenii_kharuul", "mob.dainy_chono": "dainy_chono", "mob.ulaan_khadny_noyon": "ulaan_khadny_noyon",
    "mob.uulyn_savdag": "uulyn_savdag", "mob.aguin_baavgai": "aguin_baavgai", "mob.khangai_savdag": "khangai_savdag",
    "mob.tengeriin_tsereg": "tengeriin_tsereg", "mob.tengeriin_chono": "tengeriin_chono", "mob.khukh_tengeriin_elch": "khukh_tengeriin_elch",
    "mob.ordny_sakhiul": "ordny_sakhiul", "mob.khukh_suldiin_sakhiul": "khukh_suldiin_sakhiul",
}


def rig(name):
    r = copy.deepcopy(RIGS[name])
    return r
