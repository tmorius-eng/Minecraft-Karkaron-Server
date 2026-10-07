#!/usr/bin/env python3
"""Generate the SÜLD class weapons: 5 classes x 6 tiers as 32x32 sprites and 3D voxel item models.

Every weapon is drawn procedurally in a "weapon frame" laid over the usual item diagonal: u runs from the grip
(bottom-left) to the tip (top-right), v runs across it (v < 0 is the lit upper-left side). The frame fits inside the
sprite while u - v <= ~40.5 and u + v <= ~40.3. Roles per pixel (colour from the tier palette, depth from DEPTH):
  W lit edge · B body · b shade · k dark edge · K raised back spine (W colour) · G/g trim metal · H/h grip, wood,
  limb or shaft · L/l wrap, binding or second material · P/p gem · A/a accent (tassel, ribbon, fletching)
  S horsehair, bone or string · T inlay or engraving · E/e glow (sky light, or the smith's ember)

A tier is never a recolour: each step changes the silhouette (length, guard or head form, added parts), the material
palette and the ornament. Tiers follow the armour tiers of docs/ARMOR_PROGRESSION_VISUAL_SPEC.md:
  T1 Эхлэл   iron, leather, rawhide             T2 Сайжруулсан  steel, bronze, red lacquer cord
  T3 Элчин polished steel, gilt, red lacquer, white horsehair
  T4 Хааны   engraved steel, silver, dark iron, black lacquer, deep red silk, a little gold
  T5 Тэнгэрлэг  blued steel, dark bronze, turquoise, bone
  T6 Дээдэс night-sky steel, silver-white edges, sky-blue glow, star rivets

  Баатар    curved sabre (хурц илд)       plain blade -> fuller and quillons -> winged guard, gems, tassel ->
            long double-curve blade with spine and engraving -> wolf-head pommel, turquoise inlay, dark bronze ->
            star-steel blade with a forked tip, silver-white edges and sky glow
  Мэргэн    recurve horn bow (нум)         horn and sinew -> stiff bone siyahs -> ornate curled ears ->
            war bow with bone plates -> eagle-wing limbs -> celestial bow with forked ears and a glowing string
  Бөө       shaman staff                   forked wood -> ringed staff and jingle rings -> ongon head ->
            spirit-mirror head -> bone-and-bell crown -> sky-gate head
  Дархан    war hammer                     iron block -> banded head with peen -> spiked -> forge face with an
            ember core -> wolf-maw head -> star-anvil head
  Хүлэгчин  rider's spear                  leaf blade -> horsehair tassel -> winged blade -> hooked blade ->
            horse-skull socket -> comet blade

All motifs (wolf, eagle, horse skull, sky gate, star rivets) are ORIGINAL FICTION drawn from scratch: no Soyombo, no
tamga, no seal or script text.

The model is real geometry: one box per run of same-depth pixels (a pixel is half a model unit), thin bevelled edges
on blades, thick guards, gems and grips. The bow is drawn the way the game holds a bow: the belly (the curve) faces
up-left, away from the archer, and the string runs along the lower-right chord; pulling moves the nocked string and
arrow towards the archer (down-right) — the three pulling frames. Output: textures and models under
resourcepack/assets/suld/ plus weapons.json (read by tools/pack/gen_items.py; custom_model_data 871000 + class*10 +
tier, tiers 1..6). Output is deterministic.

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
TIERS = (1, 2, 3, 4, 5, 6)


def hexc(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


# Palettes per tier. Hex targets come from docs/ASSET_STYLE_GUIDE.md §2 and the armour tier palettes.
PALETTES = {
    1: {  # Эхлэл: aged iron, leather, rawhide
        "W": hexc("C9CDD3"), "B": hexc("8D9199"), "b": hexc("6F737B"), "k": hexc("4A4D54"),
        "G": hexc("8D9199"), "g": hexc("5E6168"), "H": hexc("6E4A2F"), "h": hexc("4A2E1E"),
        "L": hexc("A8865E"), "l": hexc("7E6342"), "P": hexc("8D9199"), "p": hexc("5E6168"),
        "A": hexc("7A3B2A"), "a": hexc("5A2A1E"), "S": hexc("D8CFB8"), "T": hexc("5E6168"),
        "E": hexc("C9CDD3"), "e": hexc("8D9199")},
    2: {  # Сайжруулсан: steel, bronze, red-lacquer cord
        "W": hexc("E2E6EC"), "B": hexc("B4BAC2"), "b": hexc("8A9099"), "k": hexc("565C68"),
        "G": hexc("B9803F"), "g": hexc("8C5A2B"), "H": hexc("6E4A2F"), "h": hexc("4A2E1E"),
        "L": hexc("A3302A"), "l": hexc("7A1E1E"), "P": hexc("D89A4A"), "p": hexc("8C5A2B"),
        "A": hexc("A3302A"), "a": hexc("7A1E1E"), "S": hexc("E8E4DA"), "T": hexc("B9803F"),
        "E": hexc("E2E6EC"), "e": hexc("B4BAC2")},
    3: {  # Элчин: polished steel, gilt bronze, red lacquer, white horsehair
        "W": hexc("F4F6FA"), "B": hexc("C9CDD3"), "b": hexc("9AA0A8"), "k": hexc("626874"),
        "G": hexc("E6C878"), "g": hexc("B08A3E"), "H": hexc("B8402F"), "h": hexc("8E2420"),
        "L": hexc("C9A04A"), "l": hexc("8C5A2B"), "P": hexc("D8283C"), "p": hexc("8E1420"),
        "A": hexc("B8402F"), "a": hexc("8E2420"), "S": hexc("E8E4DA"), "T": hexc("C9A04A"),
        "E": hexc("F4F6FA"), "e": hexc("C9CDD3")},
    4: {  # Хааны: engraved steel, silver, dark iron, black lacquer, deep red silk, restrained gold
        "W": hexc("EEF1F6"), "B": hexc("C9CDD3"), "b": hexc("9AA0A8"), "k": hexc("3A3D44"),
        "G": hexc("D6D9DE"), "g": hexc("8D9199"), "H": hexc("2B2A2E"), "h": hexc("1B1A1F"),
        "L": hexc("8A2230"), "l": hexc("5A1418"), "P": hexc("C9A04A"), "p": hexc("8C6A2E"),
        "A": hexc("8A2230"), "a": hexc("5A1418"), "S": hexc("E8E4DA"), "T": hexc("5E6168"),
        "E": hexc("FFB050"), "e": hexc("E06A2A")},
    5: {  # Тэнгэрлэг: blued steel, dark bronze, turquoise, bone, dark red leather
        "W": hexc("9DB0D2"), "B": hexc("5C6F94"), "b": hexc("3B4A66"), "k": hexc("252E44"),
        "G": hexc("8C5A2B"), "g": hexc("5A3A1C"), "H": hexc("5A1418"), "h": hexc("3C0E12"),
        "L": hexc("D8CFB8"), "l": hexc("A89E84"), "P": hexc("5FBFB2"), "p": hexc("2E8C86"),
        "A": hexc("2E8C86"), "a": hexc("1E6460"), "S": hexc("D8CFB8"), "T": hexc("5FBFB2"),
        "E": hexc("7FE0D0"), "e": hexc("2E8C86")},
    6: {  # Дээдэс: night-sky steel, silver-white, sky blue, star rivets
        "W": hexc("E6EAF0"), "B": hexc("34507E"), "b": hexc("1E2A44"), "k": hexc("141C30"),
        "G": hexc("E6EAF0"), "g": hexc("9AA6BC"), "H": hexc("1E2A44"), "h": hexc("141C30"),
        "L": hexc("4E7FC0"), "l": hexc("34507E"), "P": hexc("8CC4FF"), "p": hexc("4E7FC0"),
        "A": hexc("4E7FC0"), "a": hexc("34507E"), "S": hexc("F4F1E6"), "T": hexc("6EA8F0"),
        "E": hexc("A8DCFF"), "e": hexc("5A96E0")},
}
OUTLINE = {1: (24, 22, 24), 2: (22, 20, 26), 3: (40, 22, 14), 4: (14, 13, 18), 5: (16, 14, 22), 6: (8, 12, 28)}
ALIAS = {"K": "W"}  # roles that share a colour but not a depth (K: the raised, lit back spine)
# per class tweaks: the shaman's blue khadag silk, horn-and-wood bows, the smith's ember stays forge-orange
CLASS_PAL = {
    ("boo", 1): {"A": hexc("3C78DC"), "a": hexc("2A56A8")},
    ("boo", 2): {"A": hexc("5096F0"), "a": hexc("3468C0")},
    ("boo", 3): {"A": hexc("3C78DC"), "a": hexc("2A56A8")},
    ("mergen", 1): {"H": hexc("9A7048"), "h": hexc("6A4A2C")},
    ("mergen", 2): {"H": hexc("8A5E38"), "h": hexc("5E3E22")},
    ("darkhan", 5): {"E": hexc("FF9A48"), "e": hexc("C85A24")},
    ("khulegchin", 1): {"H": hexc("8A6A48"), "h": hexc("5E4630")},
}

# model thickness (units out of 16) per role: blades are thin with a bevelled edge, guards, gems and grips stand out
DEPTH = {"W": 0.5, "B": 1.0, "b": 1.0, "k": 0.5, "K": 1.5, "S": 0.5, "G": 3.0, "g": 3.0, "P": 3.0, "p": 3.0,
         "A": 1.0, "a": 1.0, "H": 1.5, "h": 1.5, "L": 2.0, "l": 2.0, "T": 1.0, "E": 1.25, "e": 1.25}

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


def uv(x: float, y: float) -> tuple[float, float]:
    dx, dy = x + 0.5 - ORIGIN[0], y + 0.5 - ORIGIN[1]
    return dx * D[0] + dy * D[1], dx * NRM[0] + dy * NRM[1]


def from_uv(u: float, v: float) -> tuple[float, float]:
    return ORIGIN[0] + u * D[0] + v * NRM[0], ORIGIN[1] + u * D[1] + v * NRM[1]


class Sprite:
    def __init__(self) -> None:
        self.g = [["."] * N for _ in range(N)]
        self.clipped = 0  # painted pixels that fell outside the 30x30 drawable area (a design-time check)

    def put(self, x: int, y: int, ch: str) -> None:
        if 1 <= x < N - 1 and 1 <= y < N - 1:
            self.g[y][x] = ch
        else:
            self.clipped += 1

    def fill(self, pred, role) -> None:
        """Paint every pixel whose (u, v) satisfies pred; role is a char or a function (u, v) -> char | None."""
        for y in range(-3, N + 3):
            for x in range(-3, N + 3):
                u, v = uv(x, y)
                if pred(u, v):
                    ch = role(u, v) if callable(role) else role
                    if ch:
                        if 1 <= x < N - 1 and 1 <= y < N - 1:
                            self.g[y][x] = ch
                        else:
                            self.clipped += 1

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


def seg_dist(pu: float, pv: float, a: tuple[float, float], b: tuple[float, float]) -> float:
    au, av = a
    bu, bv = b
    du, dv = bu - au, bv - av
    ln = du * du + dv * dv
    t = 0.0 if ln == 0 else max(0.0, min(1.0, ((pu - au) * du + (pv - av) * dv) / ln))
    return math.hypot(pu - (au + t * du), pv - (av + t * dv))


def stroke(s: Sprite, pts, role, w: float = 0.5, w1: float | None = None) -> None:
    """A polyline of half-width w (tapering to w1 at the last point when given)."""
    segs = list(zip(pts, pts[1:]))
    total = sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in segs) or 1.0

    def pred(u, v):
        acc = 0.0
        for a, b in segs:
            ln = math.hypot(b[0] - a[0], b[1] - a[1])
            d = seg_dist(u, v, a, b)
            if w1 is None:
                if d <= w:
                    return True
            else:
                du, dv = b[0] - a[0], b[1] - a[1]
                t = 0.0 if ln == 0 else max(0.0, min(1.0, ((u - a[0]) * du + (v - a[1]) * dv) / (ln * ln)))
                if d <= lerp(w, w1, (acc + t * ln) / total):
                    return True
            acc += ln
        return False

    s.fill(pred, role)


def blade(s: Sprite, u0: float, u1: float, hwf: float, hwb: float, curve: float = 0.0, recurve: float = 0.0,
          taper: float = 0.74, tip: float = 7.0, tip_pow: float = 0.85, profile=None, fuller: bool = False,
          inlay: str | None = None, inlay_pat=None, spine: bool = False, yelman: float = 0.0, fork: float = 0.0,
          back_light: bool = False, edge_light: bool = True, tilt: float | None = None, edge_n: float = 0.24):
    """A tapering, optionally curved blade, hwf wide on the lit -v side and hwb on the +v side.

    curve bends the blade towards the lit side, so a sabre's convex cutting edge is the +v side and its back is the
    lit -v side; recurve adds an S-bend near the hilt (a double-curve sabre); yelman widens the back near the tip (a
    false edge); spine raises the back as a lit ridge with a groove under it; fork splits the tip into two prongs;
    back_light adds a second bright edge on the +v side.
    tilt cants the whole blade towards +v (a sabre's angled hilt) so that a long curved tip stays inside the sprite;
    by default just enough for the tip to land inside. Returns centre(u) -> v of the blade's mid-line."""
    if tilt is None:
        tilt = max(0.0, u1 + curve - 40.2 + (1.2 if fork else 0.0))

    def geo(u):
        t = (u - u0) / (u1 - u0)
        c = -curve * t * t + recurve * 4.0 * t * (1 - t) ** 2 + tilt * t
        k = lerp(1.0, taper, t) * (profile(t) if profile else 1.0)
        hf, hb = hwf * k, hwb * k
        if yelman:
            hf += yelman * max(0.0, 1.0 - abs(t - 0.8) / 0.16)
        rem = u1 - u
        if rem < tip:
            f = max(0.0, rem / tip) ** tip_pow
            hf, hb = hf * f, hb * f
        return t, c, hf, hb, rem

    def region(u, v):
        if u < u0 or u > u1:
            return False
        t, c, hf, hb, rem = geo(u)
        d = v - c
        if not (-hf <= d <= hb):
            return False
        if fork and rem < fork:
            n = (d + hf) / max(hf + hb, 0.01)
            if abs(n - 0.5) < 0.1 + 0.32 * (1 - rem / fork):
                return False
        return True

    def role(u, v):
        t, c, hf, hb, rem = geo(u)
        d = v - c
        n = (d + hf) / max(hf + hb, 0.01)
        if spine and rem > 1.5 and d < -hf + 0.8:
            return "K"
        if spine and rem > 1.5 and d < -hf + 1.6:
            return "b"
        if back_light and d > hb - 0.7:
            return "W"
        if inlay and abs(n - 0.45) < 0.11 and u0 + 2.5 < u < u1 - tip * 0.7 and (inlay_pat is None or inlay_pat(u)):
            return inlay
        if fuller and abs(n - 0.55) < 0.11 and u0 + 2 < u < u1 - tip * 0.75:
            return "k"
        if n < edge_n and edge_light:
            return "W"
        if n < 0.56:
            return "B"
        if n < 0.82:
            return "b"
        return "k"

    s.fill(region, role)

    def centre(u):
        t, c, hf, hb, rem = geo(u)
        return c + (hb - hf) / 2

    return centre


def shaft(s: Sprite, u0: float, u1: float, hw: float, wrap: tuple[str, str] | None = None, period: float = 1.6,
          wrap_from: float = -99.0, wrap_to: float = 99.0, light: str = "H", dark: str = "h") -> None:
    """A round shaft or grip: light body with a dark lower edge, optional diagonal wrap bands between wrap_from/to."""
    def role(u, v):
        if wrap and wrap_from <= u <= wrap_to and int((u - u0) // (period / 2)) % 2 == 1:
            return wrap[0] if v < 0 else wrap[1]
        return light if v < 0.1 * hw else dark

    s.fill(lambda u, v: u0 <= u <= u1 and abs(v) <= hw, role)


def band(s: Sprite, uc: float, hw_u: float, hw_v: float, role) -> None:
    s.fill(lambda u, v: abs(u - uc) <= hw_u and abs(v) <= hw_v, role)


def disc(s: Sprite, uc: float, vc: float, ru: float, rv: float, role) -> None:
    s.fill(lambda u, v: ((u - uc) / ru) ** 2 + ((v - vc) / rv) ** 2 <= 1.0, role)


def ring(s: Sprite, uc: float, vc: float, r: float, thick: float, role) -> None:
    s.fill(lambda u, v: abs(math.hypot(u - uc, v - vc) - r) <= thick / 2, role)


def gem(s: Sprite, uc: float, vc: float, r: float, light: str = "S") -> None:
    disc(s, uc, vc, r, r, lambda u, v: "p" if (u - uc) - (v - vc) < -r * 0.9 else "P")
    s.at(uc + r * 0.35, vc - r * 0.35, light)


def lit(light: str, dark: str):
    """Role function: light on the lit (-v) half, dark on the other."""
    return lambda u, v: light if v < 0 else dark


def star(s: Sprite, uc: float, vc: float, core: str = "S", ray: str = "E", r: float = 1.0) -> None:
    """A four-ray star rivet (ORIGINAL FICTION)."""
    s.at(uc, vc, core)
    for du, dv in ((r, 0), (-r, 0), (0, r), (0, -r)):
        s.at(uc + du, vc + dv, ray)


def tassel(s: Sprite, u: float, v: float, strands: int, length: float, spread: float, cols, base_ang: float = 0.0):
    """Horsehair strands fanning out backwards (-u) from (u, v); cols(k, j) -> role."""
    for k in range(strands):
        ang = math.radians(base_ang - spread / 2 + (spread * k / max(strands - 1, 1)))
        ln = length * (0.7 + 0.3 * math.cos(math.radians(base_ang) - ang))
        for j in range(1, int(ln * 2) + 1):
            r = j * 0.5
            s.at(u - r * math.cos(ang), v + r * math.sin(ang), cols(k, j))


def stamp(s: Sprite, x0: int, y0: int, art: list[str]) -> None:
    """Hand-placed pixel art (for small figurative motifs), '.' is transparent."""
    for dy, row in enumerate(art):
        for dx, ch in enumerate(row):
            if ch != ".":
                s.put(x0 + dx, y0 + dy, ch)


WOLF_POMMEL = [   # facing left, ears up, a bone muzzle and fang; the grip leaves the back of the head (upper right)
    ".G.G...",
    ".GGGG..",
    "gGGGGG.",
    "GPGGGgg",
    "SGGGggg",
    "SSGgg..",
    ".Sk....",
]


# --- Баатар: curved sabre ------------------------------------------------------------------------------------
def baatar(tier: int) -> Sprite:
    s = Sprite()
    if tier == 1:      # plain iron blade, leather grip, a small oval guard
        shaft(s, 3.4, 11.0, 1.25)
        band(s, 4.0, 0.45, 1.3, "L"); band(s, 10.4, 0.45, 1.3, "L")
        disc(s, 2.4, 0, 1.3, 1.5, lit("G", "g"))
        blade(s, 12.0, 33.5, 1.5, 1.3, curve=3.0, taper=0.8, tip=6.0)
        disc(s, 11.6, 0, 0.9, 2.8, lit("G", "g"))
    elif tier == 2:    # a fuller, bronze guard with up-turned quillons, red cord wrap
        shaft(s, 3.4, 11.0, 1.35, wrap=("L", "l"))
        disc(s, 2.3, 0, 1.6, 1.7, lit("G", "g"))
        s.at(2.3, 0, "P")
        blade(s, 12.0, 35.8, 1.8, 1.5, curve=3.8, fuller=True, tip=7.0)
        disc(s, 11.6, 0, 1.15, 3.5, lit("G", "g"))
        for sgn in (-1, 1):
            for k in range(4):
                s.at(12.2 + k * 0.85, sgn * (3.3 + k * 0.32), "G" if k < 3 else "g")
    elif tier == 3:    # winged gilt guard, red gems, white horsehair tassel, gilt inlay at the forte
        shaft(s, 3.4, 11.0, 1.4, wrap=("L", "l"))
        tassel(s, 1.6, 1.0, 3, 4.2, 30, lambda k, j: "S" if j % 4 else "l", base_ang=145)
        disc(s, 2.2, 0, 1.9, 1.9, "G")
        gem(s, 2.2, 0, 1.05)
        blade(s, 12.0, 37.6, 2.0, 1.6, curve=4.4, fuller=True, inlay="T", inlay_pat=lambda u: u < 20.5, tip=7.5)
        disc(s, 11.6, 0, 1.4, 4.0, lit("G", "g"))
        for sgn in (-1, 1):
            stroke(s, [(11.4, sgn * 3.6), (12.6, sgn * 4.6), (14.2, sgn * 5.0)], "G", 0.55, 0.3)
            stroke(s, [(10.6, sgn * 3.2), (9.8, sgn * 4.0)], "g", 0.5, 0.3)
        gem(s, 11.6, 0, 1.25)
    elif tier == 4:    # long double-curve blade, raised spine, false edge, engraved steel, silver cross-guard
        shaft(s, 3.0, 11.2, 1.45, wrap=("L", "l"), period=1.4)
        disc(s, 2.0, 0, 1.9, 2.0, lit("G", "g"))
        ring(s, 2.0, 0, 1.0, 0.6, "g")
        s.at(2.0, 0, "P")
        blade(s, 12.0, 40.0, 2.0, 1.7, curve=5.0, recurve=2.0, spine=True, yelman=1.0, inlay="T",
              inlay_pat=lambda u: int(u * 1.0) % 3 == 0, tip=7.5)
        band(s, 11.7, 0.85, 4.3, lambda u, v: "G" if abs(v) > 3.4 or u > 12.2 else "g")
        for sgn in (-1, 1):
            disc(s, 11.7, sgn * 4.7, 1.1, 1.1, "G")
            s.at(11.7, sgn * 4.7, "P")
        s.fill(lambda u, v: 12.4 <= u <= 14.2 and abs(v) <= 0.75, "G")       # langet over the blade
        s.at(11.7, 0, "P")
    elif tier == 5:    # wolf-head pommel, dark bronze claw guard, blued steel with turquoise inlay
        shaft(s, 4.4, 11.4, 1.45, wrap=("L", "l"), period=2.4)
        stamp(s, 1, 24, WOLF_POMMEL)   # the wolf head (ORIGINAL FICTION): ears up, snout left, turquoise eye
        blade(s, 12.4, 40.4, 2.1, 1.9, curve=6.2, recurve=0.6, spine=True, yelman=1.2, inlay="T", tip=7.5)
        disc(s, 12.0, 0, 1.3, 3.3, lit("G", "g"))
        for sgn in (-1, 1):                                                  # claws swept back toward the grip
            stroke(s, [(12.0, sgn * 3.0), (11.0, sgn * 4.4), (9.4, sgn * 5.0)], "G", 0.6, 0.3)
            s.at(9.2, sgn * 5.1, "S")
        gem(s, 12.0, 0, 1.15, "E")
    else:              # star-steel blade with a forked tip, silver-white edges, sky glow, star pommel, sky-wing guard
        shaft(s, 4.0, 11.4, 1.4, wrap=("L", "l"), period=1.4)
        tassel(s, 1.8, 1.2, 2, 5.6, 50, lambda k, j: "S" if j < 9 else "E", base_ang=145)
        disc(s, 2.6, 0, 1.25, 1.25, "G")
        for du, dv in ((2.0, 0), (-1.9, 0), (0, 2.0), (0, -2.0)):
            s.at(2.6 + du, dv, "g")
        s.at(2.6, 0, "E")
        centre = blade(s, 12.4, 40.8, 2.5, 2.2, curve=5.6, recurve=0.8, back_light=True, fork=6.5, tip=5.5,
                       tip_pow=0.55, edge_n=0.17)
        for u in (16.0, 21.0, 26.0, 31.0):
            s.at(u, centre(u), "E")
        s.at(23.5, centre(23.5), "T"); s.at(28.5, centre(28.5), "T")
        disc(s, 12.0, 0, 1.3, 3.0, lit("G", "g"))
        for sgn in (-1, 1):
            stroke(s, [(12.0, sgn * 2.8), (13.4, sgn * 4.4), (15.6, sgn * 5.0)], "G", 0.55, 0.25)
            s.at(15.8, sgn * 5.2, "E")
        gem(s, 12.0, 0, 1.2, "E")
    return s


# --- Мэргэн: recurve horn bow ---------------------------------------------------------------------------------
BOW_CENTER_U = 20.0
BOW = {  # half span, belly depth, recurve flick of the ears, limb thickness, how far ear ornament reaches past the tip
    1: dict(half=13.5, depth=6.4, flare=0.6, thick=1.1, ear=0.0),
    2: dict(half=15.2, depth=7.4, flare=2.4, thick=1.4, ear=0.0),
    3: dict(half=16.2, depth=8.2, flare=3.0, thick=1.6, ear=0.6),
    4: dict(half=17.0, depth=8.8, flare=3.4, thick=1.95, ear=1.6),
    5: dict(half=17.2, depth=9.0, flare=3.8, thick=1.8, ear=1.0),
    6: dict(half=16.8, depth=9.4, flare=4.0, thick=1.8, ear=2.2),
}


def bow(tier: int, pull: float = 0.0, arrow: bool = False) -> Sprite:
    s = Sprite()
    p = BOW[tier]
    half, depth, flare, thick = p["half"], p["depth"], p["flare"], p["thick"]
    cu = BOW_CENTER_U
    lo, hi = cu - half, cu + half
    # the whole bow (and its string chord) slides towards +v just enough for the upper ear to stay inside the sprite
    # (u - v <= 41); the lower string end bounds the slide (u - v >= 0.7)
    vs = min(max(0.0, half + flare + p["ear"] - 20.6), 19.3 - half)

    def centre(u):
        sn = (u - cu) / half
        edge = max(0.0, (abs(sn) - 0.68) / 0.32)
        # the belly bulges towards -v (away from the archer); a recurve's ears flick forward again
        return vs - depth * (1 - sn * sn) - flare * edge * edge

    def region(u, v):
        if abs(u - cu) > half + 0.5:
            return False
        sn = abs(u - cu) / half
        t = thick * (1.0 - 0.45 * sn ** 2.2)
        return abs(v - centre(u)) <= max(t, 0.8)

    def role(u, v):
        sn = abs(u - cu) / half
        d = v - centre(u)
        if tier == 1:                               # horn-and-wood stave, a pale sinew back
            return "L" if d > 0.45 and sn < 0.85 else ("H" if d < 0.15 else "h")
        if tier == 2:                               # stiff bone siyahs, bronze joint bands
            if sn > 0.8:
                return "S"
            if abs(sn - 0.76) < 0.04:
                return "G"
            return "H" if d < 0.15 else "h"
        if tier == 3:                               # red-lacquered limbs, gilt bands, bone ears
            if sn > 0.82:
                return "S"
            if abs(sn - 0.36) < 0.04 or abs(sn - 0.62) < 0.04 or abs(sn - 0.8) < 0.03:
                return "G"
            return "H" if d < 0.15 else "h"
        if tier == 4:                               # black lacquer war bow faced with bone plates, silver ears
            if sn > 0.84:
                return "G" if d < 0 else "g"
            if d < -thick * 0.15 and 0.16 < sn < 0.8:
                return "S" if int(sn * 12) % 2 == 0 else "h"
            return "H" if d < 0.3 else "h"
        if tier == 5:                               # dark bronze limbs, a turquoise inlay line, bone ears
            if sn > 0.84:
                return "S"
            if abs(d - 0.05) < 0.35 and 0.15 < sn < 0.78:
                return "T"
            return "G" if d < 0 else "g"
        # tier 6: night-sky steel, silver-white belly edge, a sky-glow line inside
        if d < -thick * 0.62:
            return "W"
        if abs(d - 0.3) < 0.32 and sn < 0.8:
            return "E"
        return "B" if sn < 0.6 else "b"

    s.fill(region, role)

    # ears / ornament beyond the plain limb ends
    for sgn in (-1, 1):
        tip_u = cu + sgn * half
        tv = centre(tip_u)
        if tier == 2:                               # string bridges: small bone bumps on the string side
            u = cu + sgn * half * 0.8
            s.at(u, centre(u) + thick + 0.4, "S")
        if tier == 3:                               # ornate ears curling back towards the string
            stroke(s, [(tip_u, tv), (tip_u + sgn * 0.9, tv + 0.6), (tip_u + sgn * 0.6, tv + 1.8)], "S", 0.5)
        if tier == 4:                               # long silver ears with a nock knob
            stroke(s, [(tip_u, tv), (tip_u + sgn * 1.0, tv - 0.8)], "G", 0.55)
        if tier == 5:                               # eagle-wing feathers along the outer (belly) side
            for k in range(4):
                sn = 0.28 + k * 0.15
                u = cu + sgn * half * sn
                c = centre(u) - thick * (1.0 - 0.45 * sn ** 2.2) + 0.4
                stroke(s, [(u, c), (u + sgn * 0.6, c - 2.0), (u + sgn * 1.6, c - 3.4)], "S", 0.7, 0.25)
                s.at(u + sgn * 1.4, c - 3.2, "A")
            s.at(tip_u + sgn * 0.4, tv - 0.6, "A")
        if tier == 6:                               # forked ears: a second prong splitting outward
            stroke(s, [(tip_u - sgn * 1.4, tv + 0.3), (tip_u + sgn * 0.4, tv - 1.9)], "W", 0.45, 0.2)
            s.at(tip_u + sgn * 0.6, tv - 2.2, "E")

    # grip: wrapped in the middle of the belly
    def grip_role(u, v):
        if tier == 1:
            return "L" if int(u * 1.2) % 2 == 0 else "l"
        if tier == 2:
            return "A" if int(u) % 2 == 0 else "h"
        if tier == 3:
            return "G" if int(u) % 2 == 0 else "g"
        if tier == 4:
            return "g" if abs(u - cu) < gw - 0.8 else "G"
        if tier == 5:
            return "L" if int(u) % 2 == 0 else "H"
        return "l" if abs(u - cu) < gw - 0.8 else "G"

    gw = {1: 2.2, 2: 2.6, 3: 2.8, 4: 2.5, 5: 2.8, 6: 2.8}[tier]
    s.fill(lambda u, v: abs(u - cu) <= gw and abs(v - centre(u)) <= thick + 0.6, grip_role)

    # the string: the chord between the tips (v = 0), drawn towards the archer (+v) at the nock
    def string_v(u):
        return vs + pull * (1 - abs(u - cu) / half)

    string = {1: "L", 6: "E"}.get(tier, "S")
    s.fill(lambda u, v: lo <= u <= hi and abs(v - string_v(u)) <= 0.5, string)
    if arrow:
        tip_v = centre(cu) - 2.0
        nock = vs + pull
        s.fill(lambda u, v: abs(u - cu) <= 0.55 and tip_v <= v <= nock, "H" if tier < 4 else ("g" if tier < 6 else "B"))
        for k in range(4):                          # fletching at the nock
            s.at(cu - 1.1, nock - 0.6 - k * 0.9, "A")
            s.at(cu + 1.1, nock - 0.6 - k * 0.9, "A")
        head = "E" if tier == 6 else "W"
        for k in range(3):                          # the head
            s.at(cu, tip_v - 0.6 - k * 0.4, head if k == 2 else ("B" if tier != 1 else "b"))
        s.at(cu - 0.9, tip_v + 0.5, "B")
        s.at(cu + 0.9, tip_v + 0.5, "B")
    if tier == 3:
        gem(s, cu, centre(cu) - 0.2, 1.1)
        tassel(s, lo + 0.2, vs + 0.8, 3, 3.6, 30, lambda k, j: "S", base_ang=145)   # white horsehair on the lower ear
    if tier == 4:
        s.at(cu, centre(cu), "P")
        tassel(s, lo + 0.2, vs + 0.8, 3, 4.0, 30, lambda k, j: "A" if j % 3 else "a", base_ang=145)
    if tier == 5:
        gem(s, cu, centre(cu), 1.2, "S")
    if tier == 6:
        star(s, cu, centre(cu), "S", "E", 1.0)
        for k in range(4):                          # stars drifting off the limbs
            u = cu - 9 + k * 6.0
            s.at(u, centre(u) - 3.0 - (k % 2) * 0.8, "S")
    return s


# --- Бөө: shaman staff -----------------------------------------------------------------------------------------
def boo(tier: int) -> Sprite:
    s = Sprite()
    if tier == 1:      # a forked wooden staff, rawhide tie, one feather, one strip of blue khadag cloth
        shaft(s, 1.5, 29.5, 0.9)
        stroke(s, [(29.0, 0.0), (31.6, -0.9), (34.6, -3.0)], "H", 0.75, 0.45)
        stroke(s, [(29.6, 0.3), (32.4, 1.4), (35.2, 1.8)], "h", 0.7, 0.4)
        band(s, 28.4, 0.5, 1.3, "L")
        stroke(s, [(28.6, 1.0), (26.6, 3.0), (25.4, 4.2)], "S", 0.62, 0.4)
        s.at(25.2, 4.4, "a")
        stroke(s, [(28.2, -1.0), (26.0, -2.8), (24.2, -2.6)], "A", 0.55, 0.4)
    elif tier == 2:    # bronze rings on the staff; an iron ring head with three jingle rings and khadag ribbons
        shaft(s, 1.5, 30.0, 1.0)
        for u in (7.0, 13.0, 19.0, 25.0):
            band(s, u, 0.55, 1.5, lit("G", "g"))
        ring(s, 33.4, 0, 3.0, 1.3, lit("G", "g"))
        for (u, v) in ((31.2, 3.9), (33.6, 4.4), (35.6, 3.4)):
            ring(s, u, v, 0.75, 0.6, "B")
        band(s, 30.0, 0.6, 1.6, "g")
        for sgn in (-1, 1):
            stroke(s, [(29.6, sgn * 1.4), (27.2, sgn * 3.0), (24.6, sgn * 3.4)], "A" if sgn < 0 else "a", 0.55, 0.35)
    elif tier == 3:    # an ongon head: a carved face crowned with gilt antlers, red-lacquer staff, gilt bands
        shaft(s, 1.5, 30.0, 1.1)
        for u in (8.0, 9.4, 17.0, 18.4, 25.0):
            band(s, u, 0.4, 1.5, "G")
        band(s, 30.0, 0.7, 1.9, lit("G", "g"))
        disc(s, 32.8, 0, 3.4, 3.1, lambda u, v: "S" if v < 1.2 else "l")         # the carved face
        for sgn in (-1, 1):
            s.fill(lambda u, v, sgn=sgn: abs(u - 33.3) <= 0.55 and abs(v - sgn * 1.3) <= 0.55, "h")   # eyes
        s.fill(lambda u, v: abs(u - 31.0) <= 0.4 and abs(v) <= 1.1, "h")       # mouth
        s.fill(lambda u, v: abs(u - 35.3) <= 0.5 and abs(v) <= 2.8, "G")       # brow band
        for sgn in (-1, 1):
            stroke(s, [(35.6, sgn * 1.8), (36.8, sgn * 3.2), (37.6, sgn * 3.0)], "G", 0.5, 0.3)
        s.at(35.4, 0, "P")
        for sgn in (-1, 1):
            stroke(s, [(29.4, sgn * 1.8), (27.0, sgn * 3.6), (23.8, sgn * 4.0)], "A" if sgn < 0 else "H", 0.55, 0.35)
    elif tier == 4:    # the spirit mirror: a polished disc in a silver rim, black-lacquer staff, red silk
        shaft(s, 1.5, 29.0, 1.15)
        for u in (10.0, 20.0):
            band(s, u, 0.55, 1.6, lit("G", "g"))
        band(s, 29.2, 0.8, 2.0, lit("G", "g"))
        uc = 33.0
        disc(s, uc, 0, 4.1, 4.1, "G")
        disc(s, uc, 0, 3.1, 3.1, lambda u, v: "W" if (u - uc) - v > 1.6 else ("B" if (u - uc) - v > -1.6 else "b"))
        s.at(uc + 1.2, -0.8, "S")
        for ang in (90, 210, 330):                                           # three rim bells
            a = math.radians(ang)
            disc(s, uc + 4.4 * math.cos(a), 4.4 * math.sin(a), 0.75, 0.75, "P")
        for sgn in (-1, 1):
            stroke(s, [(28.6, sgn * 1.9), (26.0, sgn * 4.0), (22.2, sgn * 4.8)], "A" if sgn < 0 else "S", 0.55, 0.3)
    elif tier == 5:    # a crown of bone tines with dark bronze bells and turquoise beads
        shaft(s, 1.5, 29.0, 1.15)
        for u in (7.0, 14.0, 21.0):
            band(s, u, 0.5, 1.6, lit("G", "g"))
            band(s, u + 1.3, 0.35, 1.4, "L")
        band(s, 29.3, 0.9, 2.1, lit("G", "g"))
        for ang, ln in ((-70, 5.6), (-35, 7.0), (0, 8.4), (35, 7.0), (70, 5.6)):
            a = math.radians(ang)
            end = (30.6 + ln * math.cos(a), ln * math.sin(a))
            stroke(s, [(30.6, 0.0), end], "S", 0.6, 0.35)
            s.at(end[0], end[1], "P")
        for ang in (-52, 52):                                                 # bells hanging between the tines
            a = math.radians(ang)
            uc, vc = 30.6 + 3.6 * math.cos(a), 3.6 * math.sin(a)
            disc(s, uc, vc, 0.85, 0.85, "G")
            s.at(uc - 0.6, vc, "g")
        disc(s, 31.2, 0, 1.6, 1.6, lit("G", "g"))
        gem(s, 31.2, 0, 0.9)
        for sgn in (-1, 1):
            stroke(s, [(28.6, sgn * 2.0), (26.6, sgn * 3.4)], "A", 0.55, 0.35)
    else:              # the sky gate (ORIGINAL FICTION): two silver posts and a lintel holding a sky-light orb
        shaft(s, 1.5, 27.6, 1.15)
        for u in (6.0, 12.0, 18.0, 24.0):
            band(s, u, 0.45, 1.4, "E")
        s.fill(lambda u, v: abs(u - 27.8) <= 0.8 and abs(v) <= 4.2, lit("G", "g"))   # sill
        for sgn in (-1, 1):
            stroke(s, [(28.0, sgn * 3.5), (35.0, sgn * 3.5)], lit("G", "g") if sgn > 0 else "G", 0.65)
        stroke(s, [(35.6, -4.4), (36.2, -5.0)], "G", 0.5)
        stroke(s, [(35.6, 4.4), (36.2, 5.0)], "g", 0.5)
        s.fill(lambda u, v: abs(u - 35.4) <= 0.75 and abs(v) <= 4.6, lit("G", "g"))   # lintel
        disc(s, 31.6, 0, 2.0, 2.0, lambda u, v: "E" if math.hypot(u - 31.6, v) > 0.9 else "S")
        for (u, v) in ((33.6, -1.6), (29.6, 1.8), (37.4, 0.0)):
            s.at(u, v, "T")
        for sgn in (-1, 1):
            stroke(s, [(27.2, sgn * 2.0), (24.6, sgn * 4.0), (21.0, sgn * 4.4)], "S" if sgn < 0 else "A", 0.55, 0.3)
        star(s, 38.4, 0.0, "S", "E", 1.0)
    return s


# --- Дархан: war hammer ---------------------------------------------------------------------------------------------
def metal(v0: float, v1: float):
    """Head shading across v: lit face, body, shade, dark back."""
    def role(u, v):
        t = (v - v0) / (v1 - v0)
        return "W" if t < 0.14 else ("B" if t < 0.55 else ("b" if t < 0.82 else "k"))
    return role


def darkhan(tier: int) -> Sprite:
    s = Sprite()
    if tier == 1:      # a plain iron block on a wooden haft, rawhide grip
        shaft(s, 2.0, 28.6, 1.2, wrap=("L", "l"), wrap_to=7.5)
        hc = 31.2
        s.fill(lambda u, v: abs(u - hc) <= 2.5 and -4.6 <= v <= 3.0, metal(-4.6, 3.0))
        band(s, 28.4, 0.5, 1.6, "g")
    elif tier == 2:    # a bigger head bound with two bronze bands, a blunt peen, leather grip, bronze collar
        shaft(s, 2.0, 28.4, 1.35, wrap=("L", "l"), wrap_to=8.0)
        disc(s, 1.6, 0, 1.5, 1.5, "g")
        hc = 31.4
        s.fill(lambda u, v: abs(u - hc) <= 3.0 and -5.6 <= v <= 3.2, metal(-5.6, 3.2))
        s.fill(lambda u, v: abs(u - hc) <= 1.8 and 3.2 < v <= 5.4, lambda u, v: "b" if v < 4.4 else "k")
        for vb in (-3.4, 0.9):
            s.fill(lambda u, v, vb=vb: abs(u - hc) <= 3.1 and abs(v - vb) <= 0.55, lit("G", "g"))
        band(s, hc - 3.6, 0.7, 2.0, lit("G", "g"))
    elif tier == 3:    # spiked: a back spike, a top spike, gilt caps, a red gem; red grip with gilt wrap
        shaft(s, 2.0, 27.8, 1.4, wrap=("L", "l"), wrap_to=9.0)
        disc(s, 1.6, 0, 1.6, 1.6, "G")
        hc = 30.8
        s.fill(lambda u, v: abs(u - hc) <= 3.3 and -6.0 <= v <= 3.6, metal(-6.0, 3.6))
        s.fill(lambda u, v: 3.6 < v <= 9.4 and abs(u - hc) <= (9.4 - v) * 0.5 + 0.2, lambda u, v: "b" if v < 6.2 else "k")
        s.fill(lambda u, v: hc + 3.3 < u <= hc + 7.4 and abs(v + 1.2) <= (hc + 7.4 - u) * 0.38 + 0.15,
               lambda u, v: "W" if v < -1.6 else "b")
        for sgn in (-1, 1):
            s.fill(lambda u, v, sgn=sgn: abs(u - (hc + sgn * 2.7)) <= 0.65 and -6.0 <= v <= 3.6, "G")
        band(s, hc - 4.0, 0.75, 2.2, lit("G", "g"))
        gem(s, hc, -1.4, 1.4)
    elif tier == 4:    # the forge face: a flared striking face, an ember core in a slot, silver rivets, langets
        shaft(s, 2.0, 27.6, 1.45, wrap=("L", "l"), period=1.4, wrap_to=9.0)
        disc(s, 1.6, 0, 1.7, 1.7, lit("G", "g"))
        hc = 30.6
        s.fill(lambda u, v: abs(u - hc) <= 3.5 and -5.0 <= v <= 3.4, metal(-5.4, 3.4))
        s.fill(lambda u, v: -7.0 <= v < -5.0 and abs(u - hc) <= 3.5 + (-5.0 - v) * 0.45,
               lambda u, v: "W" if v < -6.2 else "B")
        s.fill(lambda u, v: abs(u - hc) <= 2.1 and 3.4 < v <= 6.4, lambda u, v: "b" if v < 5.0 else "k")
        s.fill(lambda u, v: abs(u - hc) <= (7.8 - v) * 0.6 and 6.4 < v <= 7.8, "k")
        s.fill(lambda u, v: abs(u - hc) <= 1.5 and -3.8 <= v <= 1.6,
               lambda u, v: "E" if abs(u - hc) <= 0.8 and -3.0 <= v <= 0.9 else "e")
        for du in (-2.6, 2.6):
            for vv in (-3.6, 1.8):
                s.at(hc + du, vv, "G")
        for sgn in (-1, 1):
            s.fill(lambda u, v, sgn=sgn: 22.6 <= u <= hc - 3.2 and abs(v - sgn * 1.5) <= 0.4, "G")
        band(s, hc - 3.9, 0.7, 2.3, lit("G", "g"))
    elif tier == 5:    # the wolf maw (ORIGINAL FICTION): blued face, dark bronze wolf head biting towards +v
        shaft(s, 2.0, 27.0, 1.45, wrap=("L", "l"), wrap_to=9.0, period=1.8)
        for u in (14.0, 20.0):
            band(s, u, 0.45, 1.6, lit("G", "g"))
        disc(s, 1.6, 0, 1.7, 1.7, lit("G", "g"))
        hc = 30.0
        s.fill(lambda u, v: -7.0 <= v < -4.4 and abs(u - hc) <= 3.9,
               lambda u, v: "W" if v < -6.2 else ("B" if v < -5.2 else "b"))
        s.fill(lambda u, v: abs(u - hc) <= 3.6 and -4.4 <= v <= 1.8, lit("G", "g"))
        s.fill(lambda u, v: abs(u - hc) <= 3.6 and abs(v + 3.6) <= 0.35, "T")       # turquoise inlay band
        # upper jaw (+u side) and lower jaw, a dark open mouth between them with bone teeth
        s.fill(lambda u, v: 1.8 < v <= 9.0 and hc + 0.3 <= u <= hc + 3.6 - (v - 1.8) * 0.36, lit("G", "g"))
        s.fill(lambda u, v: 1.8 < v <= 7.0 and hc - 3.2 + (v - 1.8) * 0.4 <= u <= hc - 0.9, "g")
        s.fill(lambda u, v: 1.8 < v <= 7.6 and hc - 0.9 < u < hc + 0.3, "k")
        for vt in (2.8, 4.6, 6.4):
            s.at(hc + 0.1, vt, "S")
        for vt in (3.6, 5.4):
            s.at(hc - 0.8, vt, "S")
        s.at(hc + 1.0, 8.6, "k")                                                     # nose
        stroke(s, [(hc + 3.2, -1.2), (hc + 5.2, -0.4)], "G", 0.7, 0.25)                # ears
        stroke(s, [(hc + 3.2, 1.0), (hc + 5.0, 2.0)], "g", 0.6, 0.25)
        s.at(hc + 2.0, 2.2, "P")                                                     # turquoise eye
        band(s, hc - 4.0, 0.7, 2.2, lit("L", "l"))
    else:              # the star anvil (ORIGINAL FICTION): night steel anvil with a horn, sky seams, star rivets
        shaft(s, 2.0, 26.6, 1.45, wrap=("L", "l"), period=1.4, wrap_to=9.0)
        star(s, 1.8, 0, "S", "E", 1.2)
        hc = 29.4

        def anvil(u, v):
            a = u - hc
            if -7.0 <= v < -5.0:                       # the flat top face; the horn sweeps back towards the grip
                if -3.0 <= a <= 3.6:
                    return True
                return -7.8 <= a < -3.0 and v <= -5.0 - (-3.0 - a) * 0.42
            if -5.0 <= v < -2.0:                       # the waist
                return abs(a) <= 1.9 + max(0.0, -4.0 - v) * 1.2
            if -2.0 <= v <= 2.2:                       # the body
                return abs(a) <= 3.0 + max(0.0, v - 0.8) * 0.7
            if 2.2 < v <= 3.4:                         # two feet
                return 1.1 <= abs(a) <= 4.0
            return False

        def anvil_role(u, v):
            a = u - hc
            if v < -6.2 or (a < -3.0 and v < -5.9):
                return "G"
            if v < -5.0:
                return "g"
            if v < -2.0:
                return "b"
            return "W" if a < -2.4 and v < 0.8 else ("B" if a < 0.6 else ("b" if v < 2.2 else "k"))

        s.fill(anvil, anvil_role)
        s.fill(lambda u, v: anvil(u, v) and abs(v + 1.8) <= 0.35, "E")             # glowing seam under the waist
        for (u, v) in ((hc - 1.6, -5.8), (hc + 1.4, -5.8), (hc - 1.4, 0.8), (hc + 1.6, 0.8)):
            s.at(u, v, "S")                                                          # star rivets
        for (u, v) in ((hc + 5.4, -3.2), (hc + 7.0, -1.2), (hc + 5.0, 3.6), (hc - 9.2, -7.6)):
            s.at(u, v, "E")                                                          # sparks
        band(s, hc - 3.5, 0.7, 2.3, lit("G", "g"))
    ring(s, 3.4, 1.9, 0.95, 0.55, "A" if tier < 6 else "E")   # the wrist loop
    return s


# --- Хүлэгчин: rider's spear --------------------------------------------------------------------------------------
def leaf(t: float) -> float:
    """Leaf-blade width profile: narrow at the socket, widest at a third, then a long taper."""
    return 0.45 + 0.55 * math.sin(math.pi / 2 * t / 0.35) if t < 0.35 else 1.0 - (t - 0.35) * 0.5


def khulegchin(tier: int) -> Sprite:
    s = Sprite()
    if tier == 1:      # a small leaf blade on a plain shaft, a rawhide tie
        shaft(s, 1.5, 28.0, 0.9)
        disc(s, 1.0, 0, 1.1, 1.1, "g")
        band(s, 28.4, 0.9, 1.3, lit("G", "g"))
        blade(s, 29.0, 36.4, 2.2, 2.2, taper=1.0, tip=4.2, profile=leaf, edge_light=True)
        band(s, 26.8, 0.45, 1.2, "L")
    elif tier == 2:    # a longer blade with a midrib, bronze socket, red horsehair tassel
        shaft(s, 1.5, 27.6, 1.0, wrap=("L", "l"), wrap_from=3.0, wrap_to=9.0)
        disc(s, 1.0, 0, 1.3, 1.3, "G")
        blade(s, 28.8, 37.8, 2.5, 2.5, taper=1.0, tip=5.0, profile=leaf, fuller=True)
        s.fill(lambda u, v: 26.6 <= u <= 29.2 and abs(v) <= 1.6, lit("G", "g"))
        tassel(s, 26.2, 0, 7, 4.6, 120, lambda k, j: "A" if (k + j) % 4 else "a")
        band(s, 26.2, 0.7, 1.7, "G")
    elif tier == 3:    # winged blade: two side lugs at the base, gilt socket with a gem, white horsehair tassel
        shaft(s, 1.5, 27.2, 1.1)
        for u in (6.0, 12.0, 18.0):
            band(s, u, 0.4, 1.4, "G")
        disc(s, 1.0, 0, 1.4, 1.4, "G")
        blade(s, 29.4, 38.8, 2.7, 2.7, taper=0.9, tip=6.0, profile=leaf, fuller=True)
        for sgn in (-1, 1):
            stroke(s, [(30.0, sgn * 2.2), (30.0, sgn * 4.4), (28.8, sgn * 5.2)], "B" if sgn < 0 else "b", 0.6, 0.35)
        s.fill(lambda u, v: 26.4 <= u <= 29.6 and abs(v) <= 1.8, lit("G", "g"))
        gem(s, 28.0, 0, 1.0)
        tassel(s, 26.0, 0, 9, 5.4, 132, lambda k, j: "S" if j % 4 else "l")
        band(s, 26.0, 0.7, 1.9, "G")
    elif tier == 4:    # hooked blade: a long blade, a back-curving hook and a small spur; silver fittings, red silk
        shaft(s, 1.5, 27.0, 1.15)
        for u in (7.0, 14.0, 21.0):
            band(s, u, 0.45, 1.5, lit("G", "g"))
        disc(s, 1.0, 0, 1.4, 1.4, "G")
        blade(s, 29.6, 39.8, 2.4, 2.4, taper=0.8, tip=6.4, fuller=True)
        stroke(s, [(29.4, 1.4), (30.8, 3.6), (30.0, 5.8), (27.6, 6.6)], "b", 0.75, 0.3)   # the hook
        stroke(s, [(30.6, 3.0), (31.0, 4.2)], "B", 0.4)
        stroke(s, [(29.6, -1.6), (30.8, -4.4)], "W", 0.6, 0.25)                             # the spur
        s.fill(lambda u, v: 26.2 <= u <= 29.8 and abs(v) <= 1.8, lit("G", "g"))
        s.at(28.0, 0, "P")
        tassel(s, 25.8, 0, 9, 5.8, 132, lambda k, j: "A" if (k + j) % 3 else "a")
        band(s, 25.8, 0.7, 1.9, "G")
    elif tier == 5:    # horse-skull socket (ORIGINAL FICTION) of bone with turquoise eyes; blued blade, turquoise inlay
        shaft(s, 1.5, 25.6, 1.15, wrap=("G", "g"), period=4.0)
        disc(s, 1.0, 0, 1.4, 1.4, "G")
        tassel(s, 24.6, 0, 7, 5.0, 120, lambda k, j: "A" if k % 3 else "a")
        band(s, 24.6, 0.6, 1.8, "G")
        blade(s, 31.0, 40.4, 2.6, 2.6, taper=0.8, tip=6.0, profile=leaf, inlay="T")
        disc(s, 26.8, 0, 1.9, 2.6, lit("S", "l"))                                           # cranium
        s.fill(lambda u, v: 26.8 <= u <= 31.6 and abs(v) <= 2.0 - (u - 26.8) * 0.26, lit("S", "l"))   # muzzle
        for sgn in (-1, 1):
            s.fill(lambda u, v, sgn=sgn: math.hypot(u - 27.6, v - sgn * 1.5) <= 0.75, "k")       # eye orbits
            s.at(27.6, sgn * 1.5, "P")
        s.at(31.0, -0.5, "k"); s.at(31.0, 0.5, "k")                                         # nostrils
        s.fill(lambda u, v: 28.8 <= u <= 30.4 and abs(v) <= 0.3, "l")                        # nasal ridge
        stroke(s, [(25.8, -1.6), (24.8, -2.8)], "S", 0.5, 0.25)                            # ears
        stroke(s, [(25.8, 1.6), (24.8, 2.8)], "l", 0.5, 0.25)
    else:              # the comet blade (ORIGINAL FICTION): a bright head near the tip, a tail streaming back
        shaft(s, 1.5, 27.0, 1.15)
        for u in (7.0, 14.0, 21.0):
            band(s, u, 0.4, 1.4, "E")
        disc(s, 1.0, 0, 1.4, 1.4, "G")
        for sgn in (-1, 1):                                                                 # the tail streams back
            stroke(s, [(30.0, sgn * 2.2), (27.4, sgn * 3.4), (24.0, sgn * 4.0)], "E" if sgn < 0 else "e", 0.55, 0.2)
            stroke(s, [(29.0, sgn * 1.4), (25.6, sgn * 2.2), (22.6, sgn * 2.4)], "T", 0.4, 0.15)
        centre = blade(s, 28.8, 40.6, 3.0, 3.0, taper=0.5, tip=4.0, tip_pow=0.7, back_light=True)
        disc(s, 35.4, centre(35.4), 1.7, 1.6,                                                 # the bright nucleus
             lambda u, v: "S" if math.hypot(u - 35.4, v - centre(35.4)) < 0.8 else "E")
        for u in (30.6, 32.6):
            s.at(u, centre(u), "T")
        s.fill(lambda u, v: 26.4 <= u <= 29.0 and abs(v) <= 1.7, lit("G", "g"))
        s.at(27.6, 0, "P")
        for (u, v) in ((20.6, 4.4), (21.6, -4.6), (18.6, -3.0)):
            s.at(u, v, "E")
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
def palette(tier: int, cls: str) -> dict:
    pal = dict(PALETTES[tier])
    pal.update(CLASS_PAL.get((cls, tier), {}))
    for a, b in ALIAS.items():
        pal[a] = pal[b]
    return pal


def paint(rows: list[str], tier: int, cls: str) -> Image.Image:
    pal = palette(tier, cls)
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    filled = set()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), pal[ch] + (255,))
                filled.add((x, y))
    for (x, y) in sorted(filled):
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


def write_previews(out_dir: str, previews: dict, pulls: dict) -> None:
    """Contact sheets: every class x tier (dark and inventory-grey backgrounds), a greyscale readability sheet and
    all bow pulling frames."""
    os.makedirs(out_dir, exist_ok=True)
    scale = 4
    cell = N * scale + 12
    for name, bg, grey in (("weapons.png", (44, 48, 60, 255), False), ("weapons_slot.png", (139, 139, 139, 255), False),
                           ("weapons_grey.png", (139, 139, 139, 255), True)):
        sheet = Image.new("RGBA", (len(TIERS) * cell, len(CLASSES) * cell), bg)
        for ci, cls in enumerate(CLASSES):
            for ti, tier in enumerate(TIERS):
                im = previews[(cls, tier)]
                if grey:
                    im = Image.merge("RGBA", (*([im.convert("L")] * 3), im.getchannel("A")))
                big = im.resize((N * scale, N * scale), Image.NEAREST)
                sheet.paste(big, (ti * cell + 6, ci * cell + 6), big)
        sheet.save(os.path.join(out_dir, name))
    sheet = Image.new("RGBA", (4 * cell, len(TIERS) * cell), (44, 48, 60, 255))
    for ti, tier in enumerate(TIERS):
        for fi, im in enumerate([previews[("mergen", tier)]] + pulls[tier]):
            big = im.resize((N * scale, N * scale), Image.NEAREST)
            sheet.paste(big, (fi * cell + 6, ti * cell + 6), big)
    sheet.save(os.path.join(out_dir, "bow_frames.png"))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    table = {}
    previews = {}
    pulls = {t: [] for t in TIERS}
    for ci, cls in enumerate(CLASSES):
        for tier in TIERS:
            frames = frames_for(cls, tier)
            name = f"{cls}_{tier}"
            for fname, sprite in frames.items():
                if sprite.clipped:
                    print(f"  note: {fname} has {sprite.clipped} painted pixel(s) outside the 30x30 drawable area")
                rows = sprite.rows()
                img = paint(rows, tier, cls)
                img.save(os.path.join(TEX, fname + ".png"), optimize=False)
                model = voxel_model(rows, f"suld:item/weapon/{fname}", BOW_DISPLAY if cls == "mergen" else HANDHELD)
                with open(os.path.join(MODELS, fname + ".json"), "w", encoding="utf-8") as fh:
                    json.dump(model, fh, separators=(",", ":"))
                if fname == name:
                    previews[(cls, tier)] = img
                else:
                    pulls[tier].append(img)
            table[name] = {"base_item": "minecraft:" + BASE_ITEM[cls], "custom_model_data": 871000 + ci * 10 + tier,
                           "model": f"suld:item/weapon/{name}", "bow": cls == "mergen"}
    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump(table, fh, indent=1)
        fh.write("\n")
    if args.preview:
        write_previews(args.preview, previews, pulls)
    print(f"{len(table)} class weapons (+ bow frames) written")


if __name__ == "__main__":
    main()
