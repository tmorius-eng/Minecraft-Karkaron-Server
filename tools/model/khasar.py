#!/usr/bin/env python3
"""Хасар, Агуйн Эзэн: the authored cuboid rig (docs/bosses/KHASAR.md, docs/BOSS_VISUAL_SPEC.md §5).

This file is the SOURCE of the boss model. It is plain Python data, read by tools/model/export_rig.py, which
packs the UVs, paints the atlases (tools/model/khasar_paint.py) and writes the resource-pack bone models, the item
definitions, rig.json/clips.json and a Blockbench file. Nothing here comes from a mesh: the Meshy preview
(task 01a1166a-4c56-7173-b025-56bfa0ff1317) was only measured with tools/blender/ortho_views.py for leg and body
proportions.

Units and frames
----------------
* Bone geometry is in model pixels (1/16 block at bone scale 1.0) **relative to the bone pivot**, in the bone's
  own rest frame: +Y up, +Z the creature's forward, +X its left (docs/MODEL_RENDERER.md §2). The exporter adds
  (8, 8, 8) so the pivot lands on the item-model point (8, 8, 8) and keeps every element inside −16…32.
* ``at`` is ``pivot − parentPivot`` in pixels, expressed in the parent's frame: exactly the translation of
  ``L = T(pivot − parentPivot + pos) · R(rest ⊕ rot) · S``. rig.json's ``pivot`` is the running sum of ``at``
  (the "unrotated" Blockbench-style pivot); with a parent rest rotation the child really sits at
  ``parentPivot + R(parent rest) · at``, which is what the Java Sampler and preview_rig.py compute.
* ``rest`` is Euler degrees [x, y, z] composed q = qz · qy · qx. Positive x pitches a forward-pointing part down.

Cube fields: ``f``/``t`` from/to (px), ``mat`` the paint material of every face, ``mats`` per-face overrides,
``skip`` faces never seen (not exported, no texels), ``rot`` an item-model element rotation
(axis, angle in 22.5° steps, origin px), ``deco`` per-face decal tags for the painter.
Faces are named as in Java models: north −Z (back), south +Z (front), west −X (right), east +X (left), up, down.
"""
from __future__ import annotations

import copy

RIG_ID = "khasar"
NAME_MN = "Хасар — Агуйн Эзэн"

ATLASES = {
    # density = texels per model pixel. Body at 1 (16 texels per block), head at 2 (32 per block): the head is
    # seen up close (BOSS_VISUAL_SPEC §5.2).
    # pad = texels of bleed around every face rect. The body packs like a vanilla entity texture (no padding; its
    # faces are 1:1 and the sprite is not filtered); the 2× head keeps a 1-texel bleed.
    "body": {"file": "khasar_body", "size": 128, "density": 1.0, "pad": 0},
    "head": {"file": "khasar_head", "size": 128, "density": 2.0, "pad": 1},
}


def c(f, t, mat="fur", mats=None, skip=(), rot=None, deco=None):
    """One cuboid. f/t are (x, y, z) px relative to the bone pivot."""
    lo = tuple(min(a, b) for a, b in zip(f, t))
    hi = tuple(max(a, b) for a, b in zip(f, t))
    return {"f": lo, "t": hi, "mat": mat, "mats": dict(mats or {}), "skip": set(skip),
            "rot": rot, "deco": dict(deco or {})}


_EW = {"east": "west", "west": "east"}


def mirror(cubes, share=True):
    """Mirror cubes across the bone's YZ plane (x → −x). Mirrored cubes share the original's texels (u reversed)."""
    out = []
    for cb in cubes:
        m = copy.deepcopy(cb)
        (x0, y0, z0), (x1, y1, z1) = cb["f"], cb["t"]
        m["f"], m["t"] = (-x1, y0, z0), (-x0, y1, z1)
        m["mats"] = {_EW.get(k, k): v for k, v in cb["mats"].items()}
        m["skip"] = {_EW.get(k, k) for k in cb["skip"]}
        m["deco"] = {_EW.get(k, k): v for k, v in cb["deco"].items()}
        if cb["rot"]:
            axis, ang, (ox, oy, oz) = cb["rot"]
            m["rot"] = (axis, ang if axis == "x" else -ang, (-ox, oy, oz))
        m.pop("mirror_src", None)
        if share:
            m["mirror_src"] = cb  # the exporter reuses this cube's texels, east/west swapped and u reversed
        out.append(m)
    return out


def bone(id, parent, at, rest=(0, 0, 0), atlas="body", cubes=(), scale=1.0, model=True, note=""):
    return {"id": id, "parent": parent, "at": tuple(at), "rest": tuple(rest), "atlas": atlas, "cubes": list(cubes),
            "scale": scale, "model": model and bool(cubes), "note": note}


# --------------------------------------------------------------------------------------------------- torso

BODY_FRONT = [
    # chest barrel: deep and wide, the forequarters carry the creature's mass
    c((-9, -8, -2), (9, 6, 13), mats={"down": "belly"}, deco={"east": "flank_scar"}),
    # withers hump over the shoulders (under the mane)
    c((-8, 6, 0), (8, 8.5, 11), skip={"down"}),
    # brisket keel and the shaggy chest bib hanging below it
    c((-6, -11, 5.5), (6, -8, 14), mat="belly_fur", skip={"up"}),
    c((-5, -12.75, 8.5), (5, -11, 13.5), mat="bib", skip={"up"}),
    # fore-chest under the throat
    c((-7.5, -7, 13), (7.5, 4, 15), mat="bib", skip={"north"}),
    # rib taper towards the waist
    c((-8, -6.5, -6), (8, 5.5, -2), mats={"down": "belly"}, skip={"south"}),
    # spine ridge behind the withers
    c((-5, 5.5, -7), (5, 7.5, 0), skip={"down"}),
]

BODY_REAR = [
    # loin: narrower and lower than the shoulders (the back slopes down to the croup)
    c((-7.5, -6, -8), (7.5, 4, 1), mats={"down": "belly"}, skip={"south"}),
    # belly tuck
    c((-6, -8, -7), (6, -6, 0), mat="belly", skip={"up"}),
    # croup / hips
    c((-7, -5, -13), (7, 3.5, -8), skip={"south"}),
    # buttocks
    c((-6.5, -8.5, -14.5), (6.5, 1, -11.5), skip={"south", "up"}),
    # tail root
    c((-3, 0, -15.5), (3, 4.5, -12.5), skip={"south"}),
    # spine ridge along the loin
    c((-4, 4, -9), (4, 5.5, 1), skip={"down"}),
]

# The broken bone collar sits on the neck just behind the head: half of its plates are gone and the cord on the
# right side ends in a frayed stub with one bone still dangling from it.
NECK = [
    c((-6, -6, -3), (6, 6, 5.5), mats={"down": "throat"}, skip={"north"}),
    c((-5, -9, -1), (5, -6, 3.5), mat="throat", skip={"up"}),
    # cord (rawhide, cracked): left side, underside, right stub, dangling end
    c((6, -6.4, 2.75), (6.8, 5, 4.25), mat="cord"),
    c((-6, -6.8, 2.75), (6, -6, 4.25), mat="cord"),
    c((-6.8, -6.4, 2.75), (-6, -2, 4.25), mat="cord"),
    c((-7.4, -10.5, 3.05), (-6.6, -6.4, 4.05), mat="cord"),
    # bone plates (yellowed, cracked)
    c((-1.5, -10, 2.5), (1.5, -6.8, 4.5), mat="bone"),
    c((2.4, -9.4, 2.5), (5, -6.8, 4.5), mat="bone"),
    c((6.8, -5.6, 2.25), (7.8, -2.2, 4.75), mat="bone"),
    c((6.8, -1.4, 2.25), (7.8, 2.2, 4.75), mat="bone"),
    c((-4.6, -8.4, 2.5), (-2.4, -6.8, 4.5), mat="bone", deco={"south": "crack"}),
    c((-7.7, -13, 2.85), (-6.3, -10.5, 4.25), mat="bone"),
]

# --------------------------------------------------------------------------------------------------- head

HEAD = [
    # skull: broad, the cheek ruff widens it further
    c((-5, -4, -2), (5, 4.75, 5.5), mat="head", deco={"east": "ear_scar"}),
    # heavy brow overhanging the deep-set eyes
    c((-4.5, 3, 4.5), (4.5, 5, 6.5), mat="brow"),
    # cheek ruff
    c((5, -5, -2.5), (7, 2, 4), mat="ruff"),
    # muzzle: long, a wolf's wedge, narrowing to the nose; scarred on top and on the left
    c((-2.75, -3.5, 5.5), (2.75, 1.25, 11.5), mat="muzzle", mats={"down": "palate"},
      deco={"up": "muzzle_scar", "east": "muzzle_scar_side"}, skip={"north"}),
    c((-2.25, -3.25, 11.5), (2.25, 0.75, 13), mat="muzzle", mats={"down": "palate"}, skip={"north"}),
    c((-2.25, 1.25, 5.5), (2.25, 2.5, 9.5), mat="muzzle", deco={"up": "muzzle_scar_top"}, skip={"north", "down"}),
    c((-1.75, -0.25, 13), (1.75, 1.5, 14), mat="nose", skip={"north"}),
    # upper lip line
    c((-3, -4.5, 6.5), (3, -3.5, 12.5), mat="lip", mats={"down": "palate"}, skip={"up"}),
    # upper canines
    c((1.4, -6.5, 11.25), (2.3, -4.5, 12.15), mat="tooth", skip={"up"}),
    # deep-set ember eyes
    c((2.9, 1.1, 5.1), (4.6, 2.75, 5.9), mat="eye", skip={"north"}),
    # crown fur (the mane begins here)
    c((-4, 3.75, -3), (4, 5.5, 2), mat="crown", skip={"down"}),
]
HEAD = HEAD + mirror([HEAD[2]]) + mirror([HEAD[8]]) + mirror([HEAD[9]])

JAW = [
    c((-2.5, -2.25, 0), (2.5, 0, 10), mat="jaw", mats={"up": "gum"}, skip={"north"}),
    c((-3.75, -3.5, -4), (3.75, 0, 2), mat="jaw_fur", skip={"up"}),
    c((-1.6, -0.5, 1), (1.6, 0.3, 8.75), mat="tongue", skip={"down"}),
    c((1.35, 0, 8.4), (2.2, 1.75, 9.25), mat="tooth", skip={"down"}),
]
JAW = JAW + mirror([JAW[3]])

# the left ear is torn: its outer tip is gone and a notch is bitten out of the rim
EAR_L = [
    c((-2, 0, -1), (2, 4, 1), mat="ear", deco={"south": "ear_notch"}),
    c((-2, 4, -0.75), (0.25, 6.25, 0.75), mat="ear"),
]
EAR_R = [
    c((-2, 0, -1), (2, 4, 1), mat="ear"),
    c((-1.5, 4, -0.75), (1.5, 6, 0.75), mat="ear"),
    c((-0.75, 6, -0.5), (0.75, 7.75, 0.5), mat="ear"),
]

# --------------------------------------------------------------------------------------------------- mane

# The hackle mane: coarse black fur from the crown to the middle of the back, with shards of cave stone grown into
# it (the den's dripstone has calcified into the matted fur over centuries; ORIGINAL FICTION).
MANE_1 = [
    c((-4, 2, -10), (4, 6, 8.5), mat="mane", skip={"down"}),
    # stepped hackle tufts: the highest over the withers, a lower one behind
    c((-3, 6, 0), (3, 7.75, 7.5), mat="mane", skip={"down"}),
    c((-2.5, 6, -8), (2.5, 7, -2.5), mat="mane", skip={"down"}),
    c((4, -2, -8), (8, 5, 8), mat="mane", skip={"west"}),
    c((7.5, -7.5, -2), (10.5, 1, 9), mat="mane", skip={"west"}),
    c((-3, 2, -15), (3, 5, -10), mat="mane", skip={"down", "south"}),
    c((-4.5, -3, 8.5), (4.5, 2.5, 10.5), mat="mane", skip={"north"}),
    # stone shards
    c((2, 4, -4), (4.25, 9.5, -1.75), mat="stone", rot=("z", -22.5, (3, 4, -3))),
    c((-1, 5, -9), (1, 8, -7), mat="stone", rot=("x", -22.5, (0, 5, -8))),
]
MANE_1 = MANE_1 + mirror([MANE_1[3], MANE_1[4]]) + [
    c((-4.25, 4, 2), (-2, 8.75, 4.25), mat="stone", rot=("z", 22.5, (-3, 4, 3))),
]

MANE_2 = [
    c((-4.5, -1, -3), (4.5, 3, 5.5), mat="mane", skip={"down"}),
    c((4.5, -5, -2), (7, 1.5, 5), mat="mane", skip={"west"}),
]
MANE_2 = MANE_2 + mirror([MANE_2[1]])

# Cave stone crusted into old wounds on the left shoulder and the right ribs. Hidden in phase 3 (they shatter off).
SHARDS = [
    c((0, -3, -4), (1.25, 2, 2), mat="stone_crust", skip={"west"}),
    c((0.5, 0, -3), (2.5, 5, -1), mat="stone", rot=("z", -22.5, (0.5, 0, -2))),
    c((0.5, -2, 0), (2, 2, 1.5), mat="stone", rot=("z", -45, (0.5, 0, 0.75))),
    c((0.5, -4, -1.5), (1.75, -1.5, 0), mat="stone"),
    c((-19.25, -2, -9), (-18, 2, -4), mat="stone_crust", skip={"east"}),
    c((-20.5, -1, -8), (-18.5, 3.5, -6), mat="stone", rot=("z", 22.5, (-18.5, -1, -7))),
]

# --------------------------------------------------------------------------------------------------- legs

FRONT_UPPER = [
    # shoulder and upper arm: the heavy forequarter mass
    c((-3.25, -6, -4.5), (3.5, 4, 4.5), mat="fur", skip={"up"}),
    # forearm
    c((-2.5, -13, -2.5), (2.5, -6, 2.5), mat="leg", skip={"up"}),
    # elbow point and the long feathering behind the forearm
    c((-2, -8.5, -4.25), (2, -5.5, -2.5), mat="leg", skip={"south"}),
    c((-1.75, -12, -3.5), (1.75, -8.5, -2.5), mat="leg_fringe", skip={"south", "up"}),
]
FRONT_LOWER = [
    c((-2.75, -2, -2.5), (2.75, 0.5, 2.75), mat="leg"),
    c((-2.25, -5.5, -2), (2.25, -2, 2.25), mat="leg", skip={"up", "down"}),
    c((-1.25, -4.5, -3), (1.25, -2.5, -2), mat="leg_fringe", skip={"south"}),
]
FRONT_PAW = [
    c((-3.25, -1.5, -2.5), (3.25, 1, 3.5), mat="paw", mats={"down": "pad"}),
    c((-3.25, -1.5, 3.5), (-1.7, 0.25, 5), mat="toe", mats={"down": "pad"}, skip={"north"}),
    c((-1.6, -1.5, 3.5), (-0.05, 0.4, 5.25), mat="toe", mats={"down": "pad"}, skip={"north"}),
    c((0.05, -1.5, 3.5), (1.6, 0.4, 5.25), mat="toe", mats={"down": "pad"}, skip={"north"}),
    c((1.7, -1.5, 3.5), (3.25, 0.25, 5), mat="toe", mats={"down": "pad"}, skip={"north"}),
    # claws: dark, worn, long on the forepaws (they rake in the swipe)
    c((-2.85, -1.5, 5), (-2.1, -0.4, 6.25), mat="claw", skip={"north"}),
    c((-1.2, -1.5, 5.25), (-0.45, -0.4, 6.6), mat="claw", skip={"north"}),
    c((0.45, -1.5, 5.25), (1.2, -0.4, 6.6), mat="claw", skip={"north"}),
    c((2.1, -1.5, 5), (2.85, -0.4, 6.25), mat="claw", skip={"north"}),
    c((-1.5, -1.5, -3.25), (1.5, 0, -2.5), mat="pad", skip={"south"}),
]

HIND_UPPER = [
    # the thigh: a big ham, angled forward
    c((-3.5, -8.5, -4.5), (3, 3, 4.5), mat="fur", skip={"up"}, deco={"east": "haunch_scar"}),
    c((-2.5, -10.5, -3), (2.5, -8.5, 3.5), mat="leg", skip={"up"}),
    c((-2, -8, -5.5), (2, -2, -4.5), mat="leg_fringe", skip={"south"}),
]
HIND_LOWER = [
    # gaskin, hock point and the metatarsus (tilted back to near-vertical with a −45° element rotation)
    c((-2, -7, -2), (2, 0.5, 2.5), mat="leg", skip={"up"}),
    c((-1.5, -8.25, -3), (1.5, -6, -1), mat="leg"),
    c((-1.75, -13.5, -1.75), (1.75, -6.5, 1.75), mat="leg", skip={"up"}, rot=("x", -45, (0, -7, 0))),
]
HIND_PAW = [
    c((-3, -1.5, -2.5), (3, 1, 3), mat="paw", mats={"down": "pad"}),
    c((-3, -1.5, 3), (-1.55, 0.2, 4.4), mat="toe", mats={"down": "pad"}, skip={"north"}),
    c((-1.45, -1.5, 3), (-0.05, 0.3, 4.6), mat="toe", mats={"down": "pad"}, skip={"north"}),
    c((0.05, -1.5, 3), (1.45, 0.3, 4.6), mat="toe", mats={"down": "pad"}, skip={"north"}),
    c((1.55, -1.5, 3), (3, 0.2, 4.4), mat="toe", mats={"down": "pad"}, skip={"north"}),
]

# --------------------------------------------------------------------------------------------------- tail

TAIL_1 = [
    c((-2.5, -2.5, -5.5), (2.5, 2.5, 0.5), mat="tail"),
    c((-2, 2.5, -4.5), (2, 3.5, -0.5), mat="tail", skip={"down"}),
]
TAIL_2 = [
    c((-3.25, -3.5, -6.5), (3.25, 3, 0.5), mat="tail"),
    c((-2.5, -4.75, -5.5), (2.5, -3.5, -1), mat="tail", skip={"up"}),
]
TAIL_3 = [
    c((-2.5, -2.75, -5), (2.5, 2.25, 0.5), mat="tail_tip"),
    c((-1.5, -1.75, -6.75), (1.5, 1.25, -5), mat="tail_tip", skip={"south"}),
]

# --------------------------------------------------------------------------------------------------- skeleton

# Leg joints, from the Meshy proportion pass (front legs at 0.30 of the length from the nose, hind legs at 0.76;
# chest bottom at 0.37 of the withers height) and pushed lower and heavier for Хасар.
FL = (8.5, -3, 8.25)       # shoulder joint in body_front
HL = (6.5, -0.75, -9.75)   # hip joint in body_rear
HIND_UPPER_REST = (-20, 0, 0)
HIND_LOWER_REST = (55, 0, 0)    # gaskin points down and back (world +35°)
HIND_PAW_REST = (-35, 0, 0)     # foot level again


def _legs():
    out = []
    for side, sx in (("l", 1), ("r", -1)):
        # the right legs mirror the left ones and share their texels
        mir = mirror if sx < 0 else list
        out += [
            bone(f"leg_f{side}_upper", "body_front", (FL[0] * sx, FL[1], FL[2]), cubes=mir(FRONT_UPPER)),
            bone(f"leg_f{side}_lower", f"leg_f{side}_upper", (0, -13, 0.25), cubes=mir(FRONT_LOWER)),
            bone(f"leg_f{side}_paw", f"leg_f{side}_lower", (0, -5.5, 0.5), cubes=mir(FRONT_PAW)),
        ]
    for side, sx in (("l", 1), ("r", -1)):
        mir = mirror if sx < 0 else list
        out += [
            bone(f"leg_h{side}_upper", "body_rear", (HL[0] * sx, HL[1], HL[2]), HIND_UPPER_REST, cubes=mir(HIND_UPPER)),
            bone(f"leg_h{side}_lower", f"leg_h{side}_upper", (0, -9.75, 1.5), HIND_LOWER_REST, cubes=mir(HIND_LOWER)),
            bone(f"leg_h{side}_paw", f"leg_h{side}_lower", (0, -11.6, 4.6), HIND_PAW_REST, cubes=mir(HIND_PAW)),
        ]
    return out


BONES = [
    bone("root", None, (0, 0, 0), model=False, note="follows the host; yaw and position"),
    bone("body_front", "root", (0, 23, -3), cubes=BODY_FRONT, note="shoulders, chest, bib (merged)"),
    bone("body_rear", "body_front", (0, 0, -2), cubes=BODY_REAR, note="loin, hips, tail root (merged)"),
    bone("neck", "body_front", (0, 1, 12), (40, 0, 0), cubes=NECK, note="neck + broken bone collar (merged)"),
    bone("head", "neck", (0, 0, 5), (-30, 0, 0), atlas="head", cubes=HEAD),
    bone("jaw", "head", (0, -4, 2.5), atlas="head", cubes=JAW),
    bone("ear_l", "head", (3.25, 5.5, 0.5), (8, 0, -16), atlas="head", cubes=EAR_L, note="torn ear"),
    bone("ear_r", "head", (-3.25, 5.5, 0.5), (8, 0, 16), atlas="head", cubes=EAR_R),
    bone("mane_1", "body_front", (0, 3.5, 7), cubes=MANE_1, note="hackles + stone shards; ×1.35 in phase 2"),
    bone("mane_2", "neck", (0, 6, 1), cubes=MANE_2, note="nape hackles; ×1.35 in phase 2"),
    bone("shards_flank", "body_front", (9, -1, 4), cubes=SHARDS, note="hidden from phase 3 (stone falls off)"),
    *_legs(),
    bone("tail_1", "body_rear", (0, 2, -14), (-50, 0, 0), cubes=TAIL_1),
    bone("tail_2", "tail_1", (0, 0, -5.5), (-12, 0, 0), cubes=TAIL_2),
    bone("tail_3", "tail_2", (0, 0, -6.5), (-8, 0, 0), cubes=TAIL_3),
]

# Extra Interaction hitbox: the head sticks out of the RAVAGER host box (BOSS_VISUAL_SPEC §5.1).
PARTS = [{"bone": "head", "width": 1.0, "height": 0.9}]

# Model variants: same geometry as the bone, with some materials swapped (an item_model swap, not a display).
# head_rage: brighter ember eyes for phases 2–3 (Уурласан, Галзуурсан).
VARIANTS = {"head_rage": {"of": "head", "remap": {"eye": "eye_rage"}}}

RIG = {
    "id": RIG_ID,
    "name_mn": NAME_MN,
    "scale": 1.0,
    "hostHeight": 2.2,
    "atlases": ATLASES,
    "bones": BONES,
    "parts": PARTS,
    "variants": VARIANTS,
}
