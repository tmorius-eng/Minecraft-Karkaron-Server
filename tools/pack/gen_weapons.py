#!/usr/bin/env python3
"""Generate the SÜLD class weapons: 5 classes x 4 tiers as 32x32 sprites and 3D voxel item models.

Every weapon is drawn procedurally in a "weapon frame" laid over the usual item diagonal: u runs from the grip
(bottom-left) to the tip (top-right), v runs across it (v < 0 is the lit upper-left side). Roles per pixel:
  W lit edge · B body · b shade · k dark edge · G/g metal trim · H/h grip, wood or shaft · P gem · A accent · S white
painted with a tier palette:  T1 iron & leather · T2 steel & bronze · T3 gold & crimson · T4 the celestial blue of Тэнгэр.

  Баатар    curved sabre (хурц илд)           Мэргэн     recurve horn bow (нум)
  Бөө       shaman's staff with an ongon head  Дархан     smith's war hammer
  Хүлэгчин  horse-rider's spear with a horsehair tassel

The model is real geometry: one box per run of same-depth pixels (a pixel is half a model unit), thin bevelled edges
on blades, thick guards, gems and grips. The bow is drawn the way the game holds a bow: the belly (the curve) faces
up-left, away from the archer, and the string runs along the lower-right chord; pulling moves the nocked string and
arrow towards the archer (down-right) — the three pulling frames. Output: textures and models under
resourcepack/assets/suld/ plus weapons.json (read by tools/pack/gen_items.py; custom_model_data 871000 + class*10 + tier).

    python3 tools/pack/gen_weapons.py [--preview DIR]
"""
from __future__ import annotations

import argparse
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "resourcepack", "assets", "suld", "textures", "item", "weapon")
MODELS = os.path.join(ROOT, "resourcepack", "assets", "suld", "models", "item", "weapon")
OUT_JSON = os.path.join(ROOT, "resourcepack", "assets", "suld", "weapons.json")

N = 32  # sprite size

PALETTES = {
    1: {"W": (235, 240, 248), "B": (200, 206, 216), "b": (150, 158, 172), "k": (96, 104, 118), "G": (150, 108, 66),
        "g": (96, 66, 38), "H": (112, 72, 42), "h": (72, 46, 26), "P": (170, 170, 184), "A": (176, 44, 44), "S": (240, 236, 222)},
    2: {"W": (244, 250, 255), "B": (176, 204, 236), "b": (118, 150, 196), "k": (58, 80, 126), "G": (226, 160, 74),
        "g": (156, 100, 42), "H": (66, 52, 44), "h": (40, 31, 26), "P": (240, 180, 86), "A": (206, 40, 40), "S": (244, 240, 226)},
    3: {"W": (255, 252, 232), "B": (242, 232, 206), "b": (206, 186, 144), "k": (144, 114, 74), "G": (255, 214, 78),
        "g": (180, 124, 32), "H": (132, 24, 34), "h": (86, 14, 22), "P": (228, 34, 66), "A": (255, 96, 60), "S": (255, 242, 204)},
    4: {"W": (230, 255, 255), "B": (152, 232, 255), "b": (84, 164, 244), "k": (38, 84, 184), "G": (255, 228, 124),
        "g": (204, 154, 44), "H": (34, 48, 100), "h": (18, 26, 62), "P": (172, 255, 255), "A": (124, 255, 234), "S": (206, 255, 255)},
}
OUTLINE = {1: (24, 22, 24), 2: (22, 20, 26), 3: (44, 24, 12), 4: (10, 24, 62)}
# the shaman's ribbons are blue khadag silk until the gold and celestial tiers
CLASS_ACCENT = {("boo", 1): (60, 120, 220), ("boo", 2): (80, 150, 240), ("khulegchin", 1): (176, 44, 44)}

# model thickness (units out of 16) per role: blades are thin with a bevelled edge, guards, gems and grips stand out
DEPTH = {"W": 0.5, "B": 1.0, "b": 1.0, "k": 0.5, "S": 0.5, "G": 3.0, "g": 3.0, "P": 3.0, "A": 1.0, "H": 1.5, "h": 1.5}

CLASSES = ["baatar", "mergen", "boo", "darkhan", "khulegchin"]
BASE_ITEM = {"baatar": "iron_sword", "mergen": "bow", "boo": "blaze_rod", "darkhan": "iron_axe", "khulegchin": "iron_sword"}

HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
BOW_DISPLAY = dict(HANDHELD)
BOW_DISPLAY.update({
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
})

# --- the weapon frame ---------------------------------------------------------------------------------------
ORIGIN = (1.5, 30.5)
D = (math.sqrt(0.5), -math.sqrt(0.5))   # along the weapon, grip -> tip (up-right)
NRM = (math.sqrt(0.5), math.sqrt(0.5))  # across it (down-right); the lit side is v < 0


def uv(x: int, y: int) -> tuple[float, float]:
    dx, dy = x + 0.5 - ORIGIN[0], y + 0.5 - ORIGIN[1]
    return dx * D[0] + dy * D[1], dx * NRM[0] + dy * NRM[1]


def from_uv(u: float, v: float) -> tuple[float, float]:
    return ORIGIN[0] + u * D[0] + v * NRM[0], ORIGIN[1] + u * D[1] + v * NRM[1]


class Sprite:
    def __init__(self) -> None:
        self.g = [["."] * N for _ in range(N)]

    def put(self, x: int, y: int, ch: str) -> None:
        if 1 <= x < N - 1 and 1 <= y < N - 1:
            self.g[y][x] = ch

    def fill(self, pred, role) -> None:
        """Paint every pixel whose (u, v) satisfies pred; role is a char or a function (u, v) -> char | None."""
        for y in range(1, N - 1):
            for x in range(1, N - 1):
                u, v = uv(x, y)
                if pred(u, v):
                    ch = role(u, v) if callable(role) else role
                    if ch:
                        self.g[y][x] = ch

    def at(self, u: float, v: float, ch: str) -> None:
        x, y = from_uv(u, v)
        self.put(int(math.floor(x)), int(math.floor(y)), ch)

    def erase(self, pred) -> None:
        for y in range(1, N - 1):
            for x in range(1, N - 1):
                u, v = uv(x, y)
                if pred(u, v):
                    self.g[y][x] = "."

    def rows(self) -> list[str]:
        return ["".join(r) for r in self.g]


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def blade(s: Sprite, u0: float, u1: float, hw0: float, hw1: float, curve: float = 0.0, tip: float = 7.0,
          fuller: bool = False, edge_light: bool = True, inlay: str | None = None) -> None:
    """A tapering (and optionally curved) blade. Lit upper-left edge W, body B, shade b, dark spine k."""
    def region(u, v):
        if u < u0 or u > u1:
            return False
        t = (u - u0) / (u1 - u0)
        hw = lerp(hw0, hw1, t)
        rem = u1 - u
        if rem < tip:
            hw *= max(0.0, rem / tip) ** 0.85
        return abs(v + curve * t * t) <= hw

    def role(u, v):
        t = (u - u0) / (u1 - u0)
        hw = lerp(hw0, hw1, t)
        rem = u1 - u
        if rem < tip:
            hw *= max(0.0, rem / tip) ** 0.85
        vn = (v + curve * t * t) / max(hw, 0.01)
        if inlay and abs(vn - 0.15) < 0.16 and u0 + 3 < u < u1 - tip * 0.6:
            return inlay
        if fuller and abs(vn) < 0.2 and u0 + 2 < u < u1 - tip * 0.7:
            return "b" if vn < 0 else "k"
        if vn < -0.55 and edge_light:
            return "W"
        if vn < 0.1:
            return "B"
        if vn < 0.62:
            return "b"
        return "k"

    s.fill(region, role)


def shaft(s: Sprite, u0: float, u1: float, hw: float, wrap: bool = True) -> None:
    """A round shaft or grip: H body with an h lower edge, optional diagonal wrap bands."""
    def role(u, v):
        if wrap and int((u - u0) // 1.6) % 2 == 1:
            return "h" if v < 0 else "g"
        return "H" if v < 0.1 * hw else "h"

    s.fill(lambda u, v: u0 <= u <= u1 and abs(v) <= hw, role)


def disc(s: Sprite, uc: float, vc: float, ru: float, rv: float, role) -> None:
    s.fill(lambda u, v: ((u - uc) / ru) ** 2 + ((v - vc) / rv) ** 2 <= 1.0, role)


def ring(s: Sprite, uc: float, vc: float, r: float, thick: float, role) -> None:
    s.fill(lambda u, v: abs(math.hypot(u - uc, v - vc) - r) <= thick / 2, role)


def gem(s: Sprite, uc: float, vc: float, r: float, light: str = "S") -> None:
    disc(s, uc, vc, r, r, "P")
    s.at(uc - r * 0.4, vc - r * 0.4, light)


# --- Баатар: curved sabre ------------------------------------------------------------------------------------
def baatar(tier: int) -> Sprite:
    s = Sprite()
    shaft(s, 3.5, 11.0, 1.45)                      # wrapped leather grip
    disc(s, 2.2, 0, 2.1, 2.1, "G")                 # pommel cap
    disc(s, 2.2, 0, 1.1, 1.1, "g")
    hw = {1: 2.9, 2: 3.4, 3: 3.9, 4: 4.3}[tier]
    length = {1: 38.5, 2: 39.0, 3: 39.5, 4: 40.0}[tier]
    blade(s, 12.0, length, hw, hw * 0.74, curve=4.2 + tier * 0.5, fuller=tier >= 2, inlay="G" if tier == 3 else None, tip=8.5)
    if tier == 1:                                   # a plain round guard
        disc(s, 11.6, 0, 1.2, 3.7, "G")
        disc(s, 11.6, 0, 0.7, 2.6, "g")
    elif tier == 2:                                 # bronze guard with up-turned quillons
        disc(s, 11.6, 0, 1.4, 4.3, "G")
        for sgn in (-1, 1):
            for k in range(3):
                s.at(12.4 + k * 0.9, sgn * (4.2 + k * 0.3), "G")
        s.fill(lambda u, v: 3.4 <= u <= 4.3 and abs(v) <= 1.7, "g")
    elif tier == 3:                                 # gold winged guard, red gems, red tassel
        disc(s, 11.6, 0, 1.5, 4.9, "G")
        for sgn in (-1, 1):
            for k in range(5):
                s.at(12.2 + k * 0.9, sgn * (4.7 + k * 0.45), "G" if k % 2 == 0 else "g")
        gem(s, 11.6, 0, 1.4)
        gem(s, 2.2, 0, 1.15)
        for k in range(6):
            s.at(0.8, 2.8 + k * 0.9, "A")
        s.at(-0.2, 8.0, "A"); s.at(1.8, 8.0, "A"); s.at(0.8, 8.6, "A")
        for t in range(4):
            s.fill(lambda u, v, t=t: abs(u - (4.2 + t * 1.8)) <= 0.4 and abs(v) <= 1.5, "G")
    else:                                           # celestial: feathered wings, crystal pommel, cyan runes
        disc(s, 11.6, 0, 1.6, 5.2, "G")
        for sgn in (-1, 1):
            for k, ln in enumerate((4.4, 3.4, 2.4)):
                for j in range(int(ln * 2)):
                    s.at(12.0 + j * 0.5 + k * 0.5, sgn * (5.0 + k * 1.1), "G" if j % 3 else "S")
        gem(s, 11.6, 0, 1.6, "W")
        disc(s, 2.2, 0, 1.6, 1.6, "P")
        s.at(1.6, -0.5, "W")
        for k in range(6):
            s.at(0.8, 3.0 + k * 0.9, "A")
        s.at(-0.2, 8.4, "S"); s.at(1.8, 8.4, "S"); s.at(0.8, 9.0, "S")
        for t in range(7):
            s.at(15.0 + t * 3.0, -0.1 - 0.0, "A")
    return s


# --- Мэргэн: recurve horn bow ---------------------------------------------------------------------------------
BOW_CENTER_U = 20.0
BOW_HALF = 17.0


def bow(tier: int, pull: float = 0.0, arrow: bool = False) -> Sprite:
    s = Sprite()
    depth = {1: 8.0, 2: 8.5, 3: 8.8, 4: 9.2}[tier]
    flare = {1: 1.2, 2: 3.0, 3: 3.4, 4: 4.2}[tier]
    thick = {1: 1.25, 2: 1.6, 3: 1.8, 4: 1.9}[tier]

    def centre(u):
        sn = (u - BOW_CENTER_U) / BOW_HALF
        edge = max(0.0, (abs(sn) - 0.68) / 0.32)
        # the belly bulges towards -v (away from the archer); a recurve's tips flick forward again
        return -depth * (1 - sn * sn) - flare * edge * edge

    def region(u, v):
        if abs(u - BOW_CENTER_U) > BOW_HALF + 0.5:
            return False
        sn = abs(u - BOW_CENTER_U) / BOW_HALF
        t = thick * (1.0 - 0.45 * sn ** 2.2)
        return abs(v - centre(u)) <= max(t, 0.8)

    def role(u, v):
        sn = abs(u - BOW_CENTER_U) / BOW_HALF
        d = v - centre(u)
        if tier == 4:
            return "W" if d < -0.2 else ("B" if sn < 0.6 else "b")
        if tier >= 2 and sn > 0.8:
            return "S"                              # horn tips
        if tier == 3 and (abs(sn - 0.36) < 0.05 or abs(sn - 0.6) < 0.05):
            return "G"                              # gold bands
        if tier == 2 and abs(sn - 0.52) < 0.05:
            return "A"
        return "H" if d < 0.15 else "h"

    s.fill(region, role)
    # grip: wrapped in the middle of the belly
    s.fill(lambda u, v: abs(u - BOW_CENTER_U) <= 2.8 and abs(v - centre(u)) <= thick + 0.7,
           lambda u, v: ("A" if int(u) % 2 == 0 else "h") if tier <= 2 else (("G" if int(u) % 2 == 0 else "g") if tier == 3 else "S"))
    # the string: the chord between the tips (v = 0), drawn towards the archer (+v) at the nock
    lo, hi = BOW_CENTER_U - BOW_HALF, BOW_CENTER_U + BOW_HALF

    def string_v(u):
        return pull * (1 - abs(u - BOW_CENTER_U) / BOW_HALF)

    s.fill(lambda u, v: lo <= u <= hi and abs(v - string_v(u)) <= 0.5, "S" if tier < 4 else "W")
    if arrow:
        tip_v = centre(BOW_CENTER_U) - 2.0
        s.fill(lambda u, v: abs(u - BOW_CENTER_U) <= 0.55 and tip_v <= v <= pull, "H" if tier < 4 else "B")
        for k in range(4):                          # fletching at the nock
            s.at(BOW_CENTER_U - 1.1, pull - 0.6 - k * 0.9, "A")
            s.at(BOW_CENTER_U + 1.1, pull - 0.6 - k * 0.9, "A")
        for k in range(3):                          # the head
            s.at(BOW_CENTER_U, tip_v - 0.6 - k * 0.4, "W" if k == 2 else "B")
        s.at(BOW_CENTER_U - 0.9, tip_v + 0.5, "B")
        s.at(BOW_CENTER_U + 0.9, tip_v + 0.5, "B")
    if tier == 3:
        gem(s, BOW_CENTER_U, centre(BOW_CENTER_U) - 0.2, 1.1)
        for k in range(5):                          # a red tassel on the lower tip
            s.at(lo - 0.6, 1.0 + k * 0.9, "A")
        s.at(lo - 1.6, 5.8, "A"); s.at(lo + 0.4, 5.8, "A")
    if tier == 4:
        gem(s, BOW_CENTER_U, centre(BOW_CENTER_U), 1.2, "W")
        for k in range(5):                          # stars drifting off the limbs
            s.at(BOW_CENTER_U - 8 + k * 4.0, centre(BOW_CENTER_U - 8 + k * 4.0) - 2.4, "S")
    return s


# --- Бөө: staff with an ongon head -------------------------------------------------------------------------------
def boo(tier: int) -> Sprite:
    s = Sprite()
    shaft(s, 1.5, 30.0, 1.0 if tier < 3 else 1.2, wrap=False)
    if tier >= 2:                                   # bronze / gold rings along the staff
        for u in (5.0, 10.0, 15.0, 20.0):
            s.fill(lambda a, v, u=u: abs(a - u) <= 0.7 and abs(v) <= 1.8, "G" if tier >= 3 else "g")
    head_u = 33.0
    if tier == 1:
        # a bone ring with a fan of feathers, a rawhide tie and hanging beads
        ring(s, head_u, 0, 4.3, 1.6, "S")
        for sgn, ln in ((-1, 6), (0, 8), (1, 6)):
            for k in range(ln):
                s.at(head_u + 4.6 + k * 0.55, sgn * (1.0 + k * 0.5 + (0.6 if sgn else 0)), "A" if k % 2 == 0 else "S")
        s.fill(lambda u, v: abs(u - 29.4) <= 0.8 and abs(v) <= 1.9, "A")
        for k in range(6):
            s.at(28.4 - k * 0.55, 2.4 + (k % 2) * 0.7, "A")
        s.at(26.0, 5.3, "S"); s.at(25.0, 6.0, "S")
    elif tier == 2:
        # an ongon: a horn ring crowned with curved antlers, blue khadag ribbons
        ring(s, head_u - 1.5, 0, 3.4, 1.5, "S")
        for sgn in (-1, 1):
            for k in range(14):
                t = k / 13.0
                s.at(head_u - 0.5 + t * 7.0, sgn * (2.4 + 4.2 * t - 3.0 * t * t), "S")
            for tine in (0.35, 0.6, 0.85):
                for j in range(3):
                    s.at(head_u - 0.5 + tine * 7.0 + j * 0.5, sgn * (2.4 + 4.2 * tine - 3.0 * tine * tine + j * 0.9), "S")
        disc(s, head_u - 1.5, 0, 1.9, 1.9, "G")
        gem(s, head_u - 1.5, 0, 1.2)
        for k in range(9):
            s.at(head_u - 5.4 - k * 0.6, 1.8 + k * 0.45, "A")
            s.at(head_u - 5.4 - k * 0.7, -1.8 - k * 0.55, "A")
    elif tier == 3:
        # golden ring crowned with a red orb, a sunburst of rays, long ribbons
        ring(s, head_u, 0, 4.6, 1.6, "G")
        disc(s, head_u, 0, 2.9, 2.9, "P")
        s.at(head_u - 1.0, -1.1, "S"); s.at(head_u - 0.4, -1.5, "S")
        for ang in range(0, 360, 45):
            for r in (6.0, 6.8):
                s.at(head_u + r * math.cos(math.radians(ang)), r * math.sin(math.radians(ang)), "G")
        for k in range(9):
            s.at(head_u - 5.0 - k * 0.5, 3.0 + k * 0.6, "A")
            s.at(head_u - 5.4 - k * 0.5, -3.0 - k * 0.5, "A")
    else:
        # a floating celestial crystal inside an open golden crescent, with rays and silk
        ring(s, head_u - 0.5, 0, 5.2, 1.7, "G")
        s.erase(lambda u, v: u > head_u + 2.5 and abs(v) < 4.0)
        s.fill(lambda u, v: abs(u - head_u) + abs(v) <= 3.6, lambda u, v: "W" if (u - head_u) - v > 1.4 or v < -1.8 else "P")
        for ang in range(0, 360, 45):
            for r in (6.8, 7.6):
                s.at(head_u + r * math.cos(math.radians(ang)), r * math.sin(math.radians(ang)), "W")
        for k in range(10):
            s.at(head_u - 6.0 - k * 0.5, 3.0 + k * 0.6, "A")
            s.at(head_u - 6.0 - k * 0.5, -3.0 - k * 0.55, "S")
    return s


# --- Дархан: war hammer ---------------------------------------------------------------------------------------------
def darkhan(tier: int) -> Sprite:
    s = Sprite()
    shaft(s, 2.0, 29.0, 1.55)
    disc(s, 1.6, 0, 1.7, 1.7, "G")
    head_u = 31.5
    big = {1: 3.6, 2: 4.0, 3: 4.3, 4: 4.6}[tier]
    # striking head: a block across the shaft, lit face on the upper-left, a spike on the other side
    s.fill(lambda u, v: abs(u - head_u) <= big and -7.0 <= v <= 4.0,
           lambda u, v: "W" if v < -5.4 else ("B" if v < 0.2 else ("b" if v < 2.6 else "k")))
    s.fill(lambda u, v: 4.0 < v <= 9.4 and abs(u - head_u) <= (9.4 - v) * 0.55 + 0.2, lambda u, v: "b" if v < 6.4 else "k")
    s.fill(lambda u, v: abs(u - (head_u - big - 0.8)) <= 0.9 and abs(v + 1.5) <= 4.0, "G" if tier >= 2 else "g")   # collar
    if tier >= 2:
        s.fill(lambda u, v: abs(u - head_u) <= 0.5 and -6.2 <= v <= 3.4, "b")                                      # centre line
        for sgn in (-1, 1):                                                                                          # corner caps
            s.fill(lambda u, v, sgn=sgn: abs(u - (head_u + sgn * (big - 0.6))) <= 0.7 and -7.0 <= v <= 4.0, "G")
    if tier == 3:
        gem(s, head_u, -1.6, 1.5)
        for k in range(5):                           # a crest of flames on the top
            s.at(head_u - 3.2 + k * 1.6, 4.6 + (k % 2) * 1.0, "A")
    if tier == 4:
        gem(s, head_u, -1.6, 1.7, "W")
        for k in range(5):                           # blue-hot runes on the face
            s.at(head_u - 3.2 + k * 1.6, -5.4, "A")
        for k in range(6):
            s.at(head_u + 2.6 + k * 0.5, 5.0 + k * 0.7, "A")
        for k in range(7):                           # drifting sparks
            s.at(head_u - 7 + k * 2.2, 7.6 + (k % 3) * 0.8, "S")
    for k in range(5):                               # leather wrist loop
        s.at(0.8, 3.2 + k * 0.8, "A" if tier != 4 else "S")
    return s


# --- Хүлэгчин: spear with a horsehair tassel ----------------------------------------------------------------------
def khulegchin(tier: int) -> Sprite:
    s = Sprite()
    shaft(s, 1.5, 26.5, 1.0 if tier < 3 else 1.15, wrap=tier >= 2)
    disc(s, 1.0, 0, 1.4, 1.4, "G")
    s.fill(lambda u, v: 25.5 <= u <= 28.5 and abs(v) <= 1.9, "G" if tier >= 2 else "g")   # socket
    hw = {1: 3.5, 2: 3.9, 3: 4.3, 4: 4.7}[tier]
    blade(s, 27.5, 39.6, hw, hw * 0.5, curve=0.0, tip=9.5, fuller=tier >= 2, edge_light=True)
    if tier >= 3:
        s.fill(lambda u, v: 28.0 <= u <= 30.4 and abs(v) <= hw * 0.95, "G")
        gem(s, 29.2, 0, 1.1)
    # the horsehair tassel: strands fan out backwards from the socket
    spreads = {1: 4.8, 2: 5.2, 3: 6.0, 4: 6.4}[tier]
    for k in range(9):
        ang = math.radians(-66 + k * 132 / 8)
        ln = spreads * (0.65 + 0.35 * math.cos(ang))
        steps = int(ln * 2)
        for j in range(1, steps + 1):
            r = j * 0.5
            col = "A" if tier != 4 else ("S" if (k + j) % 3 == 0 else "A")
            if tier == 3 and j % 4 == 0:
                col = "G"
            s.at(25.0 - r * math.cos(ang) * 1.0, r * math.sin(ang) * 1.0, col)
    s.fill(lambda u, v: abs(u - 25.2) <= 0.8 and abs(v) <= 1.8, "G" if tier >= 2 else "g")  # the knot
    return s


BUILDERS = {"baatar": baatar, "boo": boo, "darkhan": darkhan, "khulegchin": khulegchin}


def frames_for(cls: str, tier: int) -> dict[str, Sprite]:
    name = f"{cls}_{tier}"
    if cls == "mergen":
        out = {name: bow(tier)}
        for i, pull in enumerate((2.5, 5.5, 8.5)):
            out[f"{name}_pulling_{i}"] = bow(tier, pull, arrow=True)
        return out
    return {name: BUILDERS[cls](tier)}


# --- output --------------------------------------------------------------------------------------------------------
def paint(rows: list[str], tier: int, cls: str) -> Image.Image:
    pal = dict(PALETTES[tier])
    if (cls, tier) in CLASS_ACCENT:
        pal["A"] = CLASS_ACCENT[(cls, tier)]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    filled = set()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), pal[ch] + (255,))
                filled.add((x, y))
    for (x, y) in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < N and 0 <= ny < N and (nx, ny) not in filled:
                img.putpixel((nx, ny), OUTLINE[tier] + (255,))
    return img


def voxel_model(rows: list[str], texture: str, display: dict) -> dict:
    """3D model: the outlined sprite as boxes (one per same-depth run of a row); a pixel is half a model unit."""
    depth = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                depth[(x, y)] = DEPTH.get(ch, 1.0)
    inner = dict(depth)
    for (x, y) in inner:  # the outline takes the depth of its thickest neighbour: a solid silhouette
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = (x + dx, y + dy)
            if 0 <= nb[0] < N and 0 <= nb[1] < N and nb not in inner:
                depth[nb] = max(depth.get(nb, 0.0), inner[(x, y)])
    half = 16.0 / N  # model units per pixel
    elements = []
    for y in range(N):
        x = 0
        while x < N:
            d = depth.get((x, y))
            if not d:
                x += 1
                continue
            x0 = x
            while x + 1 < N and depth.get((x + 1, y)) == d:
                x += 1
            x1 = x + 1
            z0, z1 = 8 - d / 2, 8 + d / 2
            ytop = (N - y) * half
            u0, u1, v0, v1 = x0 * half, x1 * half, y * half, (y + 1) * half
            elements.append({
                "from": [x0 * half, ytop - half, z0], "to": [x1 * half, ytop, z1],
                "faces": {
                    "south": {"uv": [u0, v0, u1, v1], "texture": "#0"},
                    "north": {"uv": [u1, v0, u0, v1], "texture": "#0"},
                    "up": {"uv": [u0, v0, u1, v1], "texture": "#0"},
                    "down": {"uv": [u0, v0, u1, v1], "texture": "#0"},
                    "west": {"uv": [u0, v0, u0 + half, v1], "texture": "#0"},
                    "east": {"uv": [u1 - half, v0, u1, v1], "texture": "#0"},
                },
            })
            x += 1
    return {"textures": {"0": texture, "particle": texture}, "gui_light": "front", "elements": elements, "display": display}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    table = {}
    previews = []
    pulls = []
    for ci, cls in enumerate(CLASSES):
        for tier in (1, 2, 3, 4):
            frames = frames_for(cls, tier)
            name = f"{cls}_{tier}"
            for fname, sprite in frames.items():
                rows = sprite.rows()
                img = paint(rows, tier, cls)
                img.save(os.path.join(TEX, fname + ".png"), optimize=False)
                model = voxel_model(rows, f"suld:item/weapon/{fname}", BOW_DISPLAY if cls == "mergen" else HANDHELD)
                with open(os.path.join(MODELS, fname + ".json"), "w", encoding="utf-8") as fh:
                    json.dump(model, fh, separators=(",", ":"))
                if fname == name:
                    previews.append(img)
                elif cls == "mergen" and tier in (1, 4) and fname.endswith("_2"):
                    pulls.append(img)
            table[name] = {"base_item": "minecraft:" + BASE_ITEM[cls], "custom_model_data": 871000 + ci * 10 + tier,
                           "model": f"suld:item/weapon/{name}", "bow": cls == "mergen"}
    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump(table, fh, indent=1)
        fh.write("\n")
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
        cell, scale = 4 * N + 8, 4
        sheet = Image.new("RGBA", (4 * cell + 2 * cell, 5 * cell), (44, 48, 60, 255))
        for i, im in enumerate(previews):
            big = im.resize((N * scale, N * scale), Image.NEAREST)
            sheet.paste(big, ((i % 4) * cell + 4, (i // 4) * cell + 4), big)
        for i, im in enumerate(pulls):
            big = im.resize((N * scale, N * scale), Image.NEAREST)
            sheet.paste(big, (4 * cell + i * cell + 4, 1 * cell + 4), big)
        sheet.save(os.path.join(args.preview, "weapons.png"))
    print(f"{len(table)} class weapons (+ bow frames) written")


if __name__ == "__main__":
    main()
