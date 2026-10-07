#!/usr/bin/env python3
"""Procedural pixel-art painter for Хасар's two atlases (docs/bosses/KHASAR.md §4, ASSET_STYLE_GUIDE §4).

Called by tools/model/export_rig.py once per packed face. Deterministic: every random choice is a hash of the
bone, the face and the texel, so the same rig always paints the same PNG.

Rules it follows:
* every texel is a swatch from a fixed ramp (3–7 shades per material, no gradients, no dithering noise);
* fur is painted as staggered 2-texel locks (root shadow, body, occasional light tip), with large soft tone
  patches taken from 3-D value noise on the rest-pose position, so neighbouring faces agree;
* value plan: black mane and saddle, soot-grey flanks, grizzled pale bib/throat and dusty underbelly,
  dark legs/paws; one warm accent (the dim ember eyes) and two adornment materials (bone, cave stone);
* light from the top left (bevelled stone and bone, lighter fur tips on top rows); bright pixels are rare.
"""
from __future__ import annotations

import math
import zlib

# ------------------------------------------------------------------------------------------------ palette

FUR = ["#1B1A1F", "#2B2A2E", "#3A393F", "#4A4A50", "#5E5D63", "#6E6D73", "#807F85", "#97969A"]
MANE = ["#111014", "#1B1A1F", "#26252A", "#34333A", "#5E5D63"]
BIB = ["#3A393F", "#4A4A50", "#5E5D63", "#77767C", "#8C857A", "#A0978A"]
BELLY = ["#4E463C", "#6B5F50", "#8A7B68", "#A39279"]
STONE = ["#2A2C30", "#45484D", "#5F6368", "#7E8287", "#A3A7AB"]
BONE = ["#5E5646", "#857B65", "#ACA187", "#D0C6AC"]
CORD = ["#2E1E14", "#4A2E1E", "#6E4A2F"]
CLAW = ["#121115", "#24211F", "#3D3833", "#5E5650"]
PAD = ["#141317", "#221E20", "#302A2B"]
NOSE = ["#0E0D10", "#18171B", "#2B2A2E", "#4A4A50"]
EYE = ["#120B0A", "#5A2414", "#9A4420", "#C8642A", "#E08A44"]
EYE_RAGE = ["#1A0A08", "#8A2A12", "#D2501E", "#F58A2A", "#FFC46A"]
GUM = ["#3A0E10", "#5A1418", "#7A1E1E", "#94302C"]
TONGUE = ["#5A1418", "#7A1E1E", "#A3302A", "#B8402F"]
TEETH = ["#6E6552", "#9A8E74", "#BFB396", "#D8CFB8", "#E8E4DA"]
SCAR = ["#5A5254", "#7A7272", "#958C88", "#AAA29A"]
EAR_IN = ["#231C1E", "#33292B", "#4A3C3C", "#5E4E4C"]


def hexrgb(h: str) -> tuple[int, int, int, int]:
    return int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255


# ------------------------------------------------------------------------------------------------ noise

def hsh(*parts) -> float:
    """Deterministic hash of anything printable → [0, 1)."""
    return (zlib.crc32(repr(parts).encode()) & 0xFFFFFFFF) / 4294967296.0


def _lattice(ix, iy, iz, seed):
    n = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    n &= 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(p, freq, seed=0) -> float:
    """Smooth 3-D value noise in [0, 1] (trilinear, smoothstep weights)."""
    x, y, z = p[0] * freq, p[1] * freq, p[2] * freq
    x0, y0, z0 = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - x0, y - y0, z - z0
    fx, fy, fz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                acc += w * _lattice(x0 + dx, y0 + dy, z0 + dz, seed)
    return acc


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def pick(ramp, idx):
    return ramp[max(0, min(len(ramp) - 1, int(round(idx))))]


# ------------------------------------------------------------------------------------------------ fur locks

def lock_term(i, j, w, h, key, length=4, width=2, tip=0.45, shadow=1.0, vary=0.6):
    """Tone offset of a fur-lock texel. Locks are vertical strands ``width`` (2–3) texels wide and ``length`` long,
    staggered per column: lit left edge, shadowed right edge (light from the top left), a dark root where the lock
    above overlaps it, and an occasional light tip. Lock widths vary between 2 and 3 along the face."""
    # variable-width columns: walk the face left to right with 2- or 3-texel strands
    x, col = 0, 0
    while True:
        cw = width + (1 if hsh(key, "w", col) < 0.45 else 0)
        if i < x + cw:
            break
        x += cw
        col += 1
    cx = i - x
    off = int(hsh(key, "off", col) * length)
    jj = j + off
    t = jj % length
    lock = jj // length
    v = (hsh(key, "v", col, lock) - 0.5) * 2 * vary
    tone = v
    if cx == 0:
        tone += 0.4
    elif cx == cw - 1:
        tone -= 0.45
    if t == 0 and cx != 0:
        tone -= 0.8 * shadow
    elif t == length - 1 and cx < cw - 1 and hsh(key, "tip", col, lock) < tip:
        tone += 0.9
    return tone


def line_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy or 1e-9
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / L2))
    qx, qy = ax + t * dx, ay + t * dy
    return math.hypot(px - qx, py - qy), t


# decal strokes in face-normalised coordinates (u, v in 0..1): list of (u0, v0, u1, v1, width_in_texels)
SCARS = {
    # three parallel claw rakes across the left flank of the chest (an old fight)
    "flank_scar": [(0.30, 0.18, 0.55, 0.62, 0.9), (0.40, 0.14, 0.65, 0.58, 0.9), (0.50, 0.12, 0.74, 0.52, 0.8)],
    # a bald seam on the left haunch
    "haunch_scar": [(0.20, 0.35, 0.80, 0.55, 1.0)],
    # a long scar from under the torn left ear down towards the eye
    "ear_scar": [(0.25, 0.10, 0.85, 0.62, 1.1)],
    # pale cuts across the muzzle
    "muzzle_scar": [(0.10, 0.30, 0.90, 0.62, 1.1), (0.25, 0.80, 0.80, 0.95, 0.9)],
    "muzzle_scar_top": [(0.15, 0.20, 0.85, 0.70, 1.1)],
    "muzzle_scar_side": [(0.15, 0.35, 0.75, 0.15, 1.0)],
}


def scar_overlay(face, i, j, w, h):
    """None, or a colour for a scar texel (pale centre, dark lower edge)."""
    tag = face["deco"]
    if tag not in SCARS:
        return None
    u, v = (i + 0.5) / w, (j + 0.5) / h
    for k, (u0, v0, u1, v1, wid) in enumerate(SCARS[tag]):
        d, t = line_dist(u * w, v * h, u0 * w, v0 * h, u1 * w, v1 * h)
        if d < wid * 0.55:
            return pick(SCAR, 2 + (1 if hsh(tag, k, i, j) < 0.25 else 0) - (1 if t < 0.08 or t > 0.92 else 0))
        # 1-texel dark lip below the scar (the healed edge)
        d2, _ = line_dist(u * w, v * h - 1.0, u0 * w, v0 * h, u1 * w, v1 * h)
        if d2 < wid * 0.5:
            return FUR[0]
    return None


# ------------------------------------------------------------------------------------------------ materials

def m_fur(face, i, j, w, h, p, n):
    """Body fur: black saddle, soot-grey flanks, grizzled lower sides."""
    base = 4.6
    base -= 2.6 * smooth(1.45, 1.85, p[1])          # dark saddle along the back
    base += 1.2 * (1 - smooth(0.95, 1.4, p[1]))      # paler, grizzled lower flanks
    if face["bone"].startswith("leg"):
        base -= 1.4                                  # legs stay dark and heavy
    if n[1] > 0.6:
        base -= 0.8
    if n[1] < -0.6:
        base += 0.4
    base += (vnoise(p, 3.0, 1) - 0.5) * 1.6          # large soft tone patches
    base += 0.3 if j < max(1, h // 5) and abs(n[1]) < 0.5 else 0.0   # light catches the top edge (top-left light)
    key = (face["key"], "fur")
    return pick(FUR, base + lock_term(i, j, w, h, key, length=4))


def m_leg(face, i, j, w, h, p, n):
    base = 3.0 + 0.6 * (1 - smooth(0.2, 0.9, p[1])) + (vnoise(p, 4.0, 2) - 0.5) * 1.0
    # the dark stripe down the front of the forelegs (a wolf marking, darkened)
    if face["name"] == "south" and abs((i + 0.5) / w - 0.5) < 0.22 and p[1] > 0.12:
        base = 1.6
    if n[1] > 0.6:
        base -= 0.6
    return pick(FUR, base + lock_term(i, j, w, h, (face["key"], "leg"), length=3, vary=0.5))


def m_leg_fringe(face, i, j, w, h, p, n):
    return pick(FUR, 2.2 + lock_term(i, j, w, h, (face["key"], "lf"), length=5, tip=0.3, vary=0.5))


def m_belly(face, i, j, w, h, p, n):
    base = 2.0 + (vnoise(p, 3.5, 3) - 0.5) * 1.2
    edge = min(i, w - 1 - i)
    if edge == 0:
        base -= 1
    return pick(BELLY, base + 0.6 * lock_term(i, j, w, h, (face["key"], "b"), length=3, tip=0.2, vary=0.4))


def m_belly_fur(face, i, j, w, h, p, n):
    if n[1] < -0.6:
        return m_belly(face, i, j, w, h, p, n)
    return pick(BIB, 2.4 + (vnoise(p, 3.0, 4) - 0.5) + lock_term(i, j, w, h, (face["key"], "bf"), length=4))


def m_bib(face, i, j, w, h, p, n):
    """The shaggy grizzled chest bib and throat: the palest fur, long locks with light tips."""
    base = 3.0 + (vnoise(p, 3.0, 5) - 0.5) * 1.4
    if n[1] > 0.6:
        base -= 1.0
    return pick(BIB, base + lock_term(i, j, w, h, (face["key"], "bib"), length=5, tip=0.55))


def m_mane(face, i, j, w, h, p, n):
    """Coarse black hackles: long locks with deep root shadows and rare grizzled tips."""
    base = 1.6 + (vnoise(p, 4.0, 6) - 0.5) * 1.2
    if n[1] > 0.6:
        base -= 0.3
    key = (face["key"], "mane")
    t = lock_term(i, j, w, h, key, length=6, tip=0.35, shadow=1.4, vary=0.7)
    if t > 0.9 and hsh(key, "grizzle", i // 2, j // 6) < 0.18:
        return MANE[4]
    return pick(MANE[:4], base + t)


def m_tail(face, i, j, w, h, p, n):
    base = 3.0 if n[1] < -0.5 else 2.0
    base += (vnoise(p, 4.0, 7) - 0.5)
    return pick(FUR, base + lock_term(i, j, w, h, (face["key"], "tail"), length=5, tip=0.4))


def m_tail_tip(face, i, j, w, h, p, n):
    return pick(MANE[:4], 1.4 + lock_term(i, j, w, h, (face["key"], "tt"), length=5, tip=0.3, shadow=1.2))


def bevel(i, j, w, h):
    """+1 on the top/left edge, −1 on the bottom/right edge (light from the top left)."""
    if i == 0 or j == 0:
        return 1
    if i == w - 1 or j == h - 1:
        return -1
    return 0


def m_stone(face, i, j, w, h, p, n):
    """Cave stone grown into the fur: flat facets, bevelled edges, dark cracks, a rare pale mineral fleck."""
    key = face["key"]
    base = 2.0 + (0.8 if n[1] > 0.6 else 0) - (0.6 if n[1] < -0.6 else 0) + bevel(i, j, w, h)
    # one or two cracks per face: a stepped line
    for k in range(1 + int(hsh(key, "nc") * 2)):
        ax = hsh(key, "ca", k) * w
        slope = (hsh(key, "cs", k) - 0.5) * 1.6
        if abs((i + 0.5) - (ax + slope * (j + 0.5))) < 0.5 and 0 < j < h - 1:
            return STONE[0]
    if hsh(key, "fleck", i, j) < 0.03:
        return STONE[4]
    facet = 0.5 if hsh(key, "facet", i // 2, j // 3) < 0.35 else 0
    return pick(STONE, base + facet)


def m_stone_crust(face, i, j, w, h, p, n):
    edge = min(i, j, w - 1 - i, h - 1 - j)
    if edge == 0 and hsh(face["key"], "crust", i, j) < 0.6:
        return m_fur(face, i, j, w, h, p, n)
    return pick(STONE, 1.4 + 0.5 * bevel(i, j, w, h) + (hsh(face["key"], "c", i // 2, j // 2) - 0.5))


def m_bone(face, i, j, w, h, p, n):
    """Yellowed bone plates: bevelled, grimy at the bottom, a drilled cord hole, an optional crack."""
    key = face["key"]
    base = 2.0 + bevel(i, j, w, h) + (0.5 if n[1] > 0.6 else 0)
    if j >= h - max(1, h // 4):
        base -= 1                                    # grime
    if face["deco"] == "crack" and abs(i - (w * 0.35 + j * 0.4)) < 0.6:
        return BONE[0]
    if face["name"] in ("south", "north") and j == 1 and i == w // 2 and h >= 3:
        return CORD[0]                               # the hole the cord runs through
    if hsh(key, "pit", i, j) < 0.06:
        base -= 1
    return pick(BONE, base)


def m_cord(face, i, j, w, h, p, n):
    return CORD[1 if (i + j) % 3 else 0] if (i + j) % 3 != 2 else CORD[2]


def m_paw(face, i, j, w, h, p, n):
    if n[1] < -0.6:
        return m_pad(face, i, j, w, h, p, n)
    base = 2.0 + (vnoise(p, 6.0, 8) - 0.5) * 0.8 + (0.5 if n[1] > 0.6 else 0)
    return pick(FUR, base + 0.6 * lock_term(i, j, w, h, (face["key"], "paw"), length=3, vary=0.4))


def m_toe(face, i, j, w, h, p, n):
    if n[1] < -0.6:
        return m_pad(face, i, j, w, h, p, n)
    if face["name"] == "south" and j >= h - 1:
        return CLAW[1]                               # the claw root peeking out
    if i == 0 or i == w - 1:
        return FUR[0]                                # gap between toes
    return pick(FUR, 2.0 + (0.6 if n[1] > 0.6 else 0))


def m_claw(face, i, j, w, h, p, n):
    if n[1] > 0.6:
        return CLAW[3] if j == 0 else CLAW[2]        # worn horn catches the light at the tip
    return CLAW[1] if j == 0 else CLAW[0]


def m_pad(face, i, j, w, h, p, n):
    return PAD[1 if hsh(face["key"], "pad", i // 2, j // 2) < 0.6 else 0]


# --- head atlas

def m_head(face, i, j, w, h, p, n):
    """Head fur: a soot mask over the forehead, greyer on the cheeks."""
    base = 3.6 + (vnoise(p, 6.0, 9) - 0.5) * 1.2
    if n[1] > 0.6:
        base -= 1.4
    if face["name"] == "south":
        base -= 0.8
    s = scar_overlay(face, i, j, w, h)
    if s:
        return s
    return pick(FUR, base + lock_term(i, j, w, h, (face["key"], "head"), length=4, tip=0.35))


def m_brow(face, i, j, w, h, p, n):
    if n[1] < -0.6:
        return FUR[0]                                # the shadow over the eyes
    return pick(FUR, 1.2 + lock_term(i, j, w, h, (face["key"], "brow"), length=3, tip=0.3, vary=0.5))


def m_ruff(face, i, j, w, h, p, n):
    # the pale cheek ruff: the brightest fur on the head, a wolf's facial mask
    return pick(BIB, 3.4 + (vnoise(p, 6.0, 10) - 0.5) + lock_term(i, j, w, h, (face["key"], "ruff"), length=5, tip=0.5))


def m_crown(face, i, j, w, h, p, n):
    return m_mane(face, i, j, w, h, p, n)


def m_muzzle(face, i, j, w, h, p, n):
    """Dark bridge, greyer flanks; pale scar cuts on the top and the left side."""
    s = scar_overlay(face, i, j, w, h)
    if s:
        return s
    if n[1] > 0.6:
        base = 2.4
    elif face["name"] == "south":
        base = 3.0
    else:
        if j == h - 1:
            return FUR[0]                            # lip line
        # the muzzle flanks: pale and grizzled (the facial mask), darker along the bridge edge
        return pick(BIB, 2.6 + 2.0 * ((j + 0.5) / h) + 0.7 * lock_term(i, j, w, h, (face["key"], "mz"), length=3,
                                                                      tip=0.3, vary=0.4))
    return pick(FUR, base + 0.7 * lock_term(i, j, w, h, (face["key"], "mz"), length=3, tip=0.3, vary=0.4))


def m_nose(face, i, j, w, h, p, n):
    if face["name"] == "south":
        # two nostrils, a wet highlight on the top row
        if j == h // 2 and (i == max(1, w // 4) or i == min(w - 2, w - 1 - w // 4)):
            return NOSE[0]
        if j == 0 and hsh(face["key"], "wet", i) < 0.35:
            return NOSE[3]
        return NOSE[1]
    if n[1] > 0.6:
        return NOSE[2] if hsh(face["key"], "n", i, j) < 0.3 else NOSE[1]
    return NOSE[1]


def m_lip(face, i, j, w, h, p, n):
    if n[1] < -0.6:
        return m_palate(face, i, j, w, h, p, n)
    return FUR[0] if j == h - 1 else FUR[1]


def teeth_rim(face, i, j, w, h):
    """Teeth along the outer rim of a palate/gum face (sides and front), gaps every 3rd texel."""
    name = face["name"]
    front = (j == 0) if name == "down" else (j == h - 1)     # down face: v1 is the front; up face: v grows forward
    side = i == 0 or i == w - 1
    if front or side:
        k = (j if side else i) % 3
        return TEETH[1 if k == 2 else 2] if k != 1 else TEETH[3]
    return None


def m_palate(face, i, j, w, h, p, n):
    t = teeth_rim(face, i, j, w, h)
    if t:
        return t
    return GUM[2] if j % 2 else GUM[1]                       # palate ridges


def m_gum(face, i, j, w, h, p, n):
    return m_palate(face, i, j, w, h, p, n)


def m_tooth(face, i, j, w, h, p, n):
    if n[1] < -0.6 or j == h - 1:
        return TEETH[3] if (i == 0 and hsh(face["key"], "tip") < 0.5) else TEETH[2]
    if j == 0:
        return TEETH[0]                                       # stained root
    return TEETH[2] if i == 0 else TEETH[1]


def m_tongue(face, i, j, w, h, p, n):
    if n[1] > 0.6 and i == w // 2:
        return TONGUE[0]                                      # centre groove
    return TONGUE[2] if (j + i) % 4 else TONGUE[1]


def m_jaw(face, i, j, w, h, p, n):
    if n[1] > 0.6:
        return m_gum(face, i, j, w, h, p, n)
    if j == 0 and face["name"] in ("east", "west", "south"):
        return FUR[0]                                         # lower lip
    if n[1] < -0.6:
        return pick(BIB, 3.5 + lock_term(i, j, w, h, (face["key"], "chin"), length=3))
    return pick(BIB, 2.6 + 1.6 * ((j + 0.5) / h) + 0.6 * lock_term(i, j, w, h, (face["key"], "jw"), length=3))


def m_jaw_fur(face, i, j, w, h, p, n):
    return pick(BIB, 3.4 + lock_term(i, j, w, h, (face["key"], "jf"), length=5, tip=0.5))


def m_throat(face, i, j, w, h, p, n):
    return m_bib(face, i, j, w, h, p, n)


def _eye(ramp, face, i, j, w, h, n):
    if face["name"] == "south":
        # socket rim, ember iris, a slit pupil, one warm highlight top-left; dim: no glow, no bright halo
        if j == 0:
            return FUR[0]
        if i == 0 or i == w - 1:
            return ramp[1]
        if i == w // 2 and j >= 1 and w >= 4:
            return ramp[0]
        if i == 1 and j == 1:
            return ramp[4]
        return ramp[3] if j <= h // 2 else ramp[2]
    if face["name"] in ("east", "west"):
        return ramp[2] if (j > 0 and j < h - 1) else ramp[1]
    return FUR[0]


def m_eye(face, i, j, w, h, p, n):
    return _eye(EYE, face, i, j, w, h, n)


def m_eye_rage(face, i, j, w, h, p, n):
    return _eye(EYE_RAGE, face, i, j, w, h, n)


def m_ear(face, i, j, w, h, p, n):
    if face["name"] == "south":
        # the inner ear: dark skin with pale tufts, a rim of fur
        if i == 0 or i == w - 1:
            return FUR[1]
        if face["deco"] == "ear_notch" and j <= 1 and i >= w - 3:
            return SCAR[1] if j == 1 else FUR[0]              # the bitten notch, healed pale
        if hsh(face["key"], "tuft", i, j) < 0.25:
            return BIB[3]
        return EAR_IN[1 + (j * 2) // max(1, h)]
    return pick(FUR, 2.6 + lock_term(i, j, w, h, (face["key"], "ear"), length=3, tip=0.25, vary=0.4))


MATERIALS = {
    "fur": m_fur, "leg": m_leg, "leg_fringe": m_leg_fringe, "belly": m_belly, "belly_fur": m_belly_fur,
    "bib": m_bib, "throat": m_throat, "mane": m_mane, "tail": m_tail, "tail_tip": m_tail_tip,
    "stone": m_stone, "stone_crust": m_stone_crust, "bone": m_bone, "cord": m_cord,
    "paw": m_paw, "toe": m_toe, "claw": m_claw, "pad": m_pad,
    "head": m_head, "brow": m_brow, "ruff": m_ruff, "crown": m_crown, "muzzle": m_muzzle, "nose": m_nose,
    "lip": m_lip, "palate": m_palate, "gum": m_gum, "tooth": m_tooth, "tongue": m_tongue, "jaw": m_jaw,
    "jaw_fur": m_jaw_fur, "eye": m_eye, "eye_rage": m_eye_rage, "ear": m_ear,
}


def paint_face(face, texel_pos):
    """Paint one face. ``face``: dict(name, mat, deco, key, w, h, normal); ``texel_pos(i, j)`` → rest position (blocks).
    Returns rows (h lists of w RGBA tuples)."""
    fn = MATERIALS[face["mat"]]
    w, h, n = face["w"], face["h"], face["normal"]
    rows = []
    for j in range(h):
        row = []
        for i in range(w):
            p = texel_pos(i, j)
            col = None
            if face["deco"] in SCARS and face["mat"] in ("fur", "leg"):
                col = scar_overlay(face, i, j, w, h)
            row.append(hexrgb(col or fn(face, i, j, w, h, p, n)))
        rows.append(row)
    return rows
