#!/usr/bin/env python3
"""Procedural pixel painter for the fauna rigs (tools/model/fauna.py): one painter for every rig, driven by the rig's
palette (hex ramps, dark → light). Drawn from scratch; deterministic (the same rig always paints the same texels).

Light comes from the top left: up faces are brightest, down faces darkest, south/east a little lighter than
north/west, and every face is darker towards its lower edge. Each material family adds its own surface:
fur (strands that darken at the roots), cloth (a fine weave, with the deel's diagonal front flap and a contrasting
trim), metal (bright edge, dark core, a single highlight), ice (bands and sparkles), shell (overlapping plates),
wood (grain) and skin/face (eyes, brows and a mouth on the front of the head).
"""
from __future__ import annotations

import math

from khasar_paint import hexrgb, hsh, vnoise  # shared deterministic noise of the asset pipeline

FUR = {"fur", "back", "belly", "ruff", "tail", "tail_tip", "leg", "head", "muzzle", "jawline", "ear", "jaw", "moss",
       "hat_fur", "loincloth", "paw"}
CLOTH = {"robe", "robe_front", "rag", "sleeve", "trousers", "belt", "shoulder", "hat_top", "hood_open", "tassel"}
METAL = {"steel", "gold", "club_head", "gem", "hat_knot"}
SHELL = {"shell", "plate", "claw", "sting_bulb"}
WOOD = {"twig", "hilt", "shaft"}
DARK = {"nose", "claw_tip", "sting", "leg_tip", "boot", "mouth"}

# when a rig's palette has no ramp of its own for a material, it borrows one
FALLBACK = {"back": "fur", "belly": "fur", "ruff": "fur", "tail": "fur", "tail_tip": "ruff", "leg": "fur", "head": "fur",
            "muzzle": "ruff", "jawline": "belly", "ear": "back", "jaw": "fur", "paw": "back", "moss": "fur",
            "face": "skin", "robe_front": "robe", "sleeve": "robe", "hand": "skin", "trousers": "robe", "rag": "robe",
            "hood_open": "rag", "hat_top": "robe", "tassel": "robe", "boot": "belt", "plate": "shell", "claw": "shell",
            "claw_tip": "plate", "leg_tip": "plate", "sting_bulb": "shell", "sting": "plate", "belt": "robe",
            "shoulder": "robe", "loincloth": "robe", "hat_fur": "fur", "hat_knot": "gold", "gold": "steel", "gem": "steel",
            "hilt": "shaft", "shaft": "belt", "club_head": "steel", "ice": "steel", "twig": "shaft", "glow_eyes": "eye"}
GENERIC = {"steel": ["#4E5258", "#70757C", "#949AA1", "#B8BDC2", "#DDE0E3"], "eye": ["#0A0806", "#3A2A1A", "#806040"],
           "skin": ["#7A5038", "#956448", "#AE7A58", "#C49070"], "mouth": ["#2A0A0A", "#4A1414", "#6A2020"],
           "nose": ["#0E0C0C", "#1C1818", "#2C2626"], "fur": ["#4A4038", "#625448", "#7A6A5A", "#928070"],
           "robe": ["#3A3A3A", "#505050", "#686868"], "shell": ["#4A3A2A", "#62503A", "#7C664A"],
           "shaft": ["#3A2A1C", "#4E3A26", "#634A32"], "belt": ["#3A2A1C", "#4E3A26"]}

FACE_LIGHT = {"up": 0.28, "south": 0.08, "east": 0.04, "west": -0.06, "north": -0.10, "down": -0.30}


class Painter:
    def __init__(self, rig):
        self.rig = rig
        self.pal = rig.get("palette", {})

    def ramp(self, mat):
        seen = set()
        m = mat
        while m not in self.pal and m in FALLBACK and m not in seen:
            seen.add(m)
            m = FALLBACK[m]
        if m in self.pal:
            return self.pal[m]
        return GENERIC.get(m) or GENERIC.get(FALLBACK.get(mat, ""), GENERIC["fur"])

    @staticmethod
    def pick(ramp, t):
        t = max(0.0, min(0.999, t))
        return ramp[int(t * len(ramp))]

    def paint_face(self, face, texel_pos):
        w, h = face["w"], face["h"]
        rows = []
        for j in range(h):
            rows.append([hexrgb(self.texel(face, i, j, w, h, texel_pos(i, j))) for i in range(w)])
        return rows

    # ------------------------------------------------------------------ one texel

    def texel(self, face, i, j, w, h, p):
        mat, name = face["mat"], face["name"]
        ramp = self.ramp(mat)
        light = FACE_LIGHT.get(name, 0.0)
        # vertical faces darken towards their lower edge (j grows downwards on side faces)
        grad = 0.0 if name in ("up", "down") else 0.10 - 0.20 * (j + 0.5) / h
        base = 0.55 + light + grad
        key = face["key"]
        if mat == "glow_eyes":
            return self.pick(ramp, 0.6 + 0.4 * hsh(key, i, j)) if name == "south" else self.pick(self.ramp("skin"), 0.2)
        if mat in ("eye",):
            if name == "south":
                return ramp[-1] if (i == 0 and j == 0) else ramp[1] if (i + j) % 2 == 0 else ramp[min(1, len(ramp) - 1)]
            return ramp[0]
        if mat == "face" and name == "south":
            return self.face(ramp, i, j, w, h, base)
        if mat == "hood_open" and name == "south":
            # the open front of a hood: darkness inside, the rag rim around it
            inner = 1 <= i < w - 1 and 1 <= j < h - 1
            return "#0C0A08" if inner else self.pick(ramp, base - 0.1)
        if mat in FUR:
            # strands: per-column phase, darker at the root (top of a strand), a light tip
            col = (i + int(hsh(key, "c", i // 2) * 3)) // 2
            strand = ((j + int(hsh(key, "s", col) * 4)) % 4) / 3.0
            n = vnoise(p, 9.0, 3) - 0.5
            t = base + 0.18 * (strand - 0.5) + 0.25 * n
            if mat == "moss":
                t += 0.15 * (vnoise(p, 14.0, 9) - 0.5)
            return self.pick(ramp, t)
        if mat in CLOTH:
            weave = 0.06 if (i + j) % 2 == 0 else -0.04
            n = vnoise(p, 6.0, 5) - 0.5
            t = base + weave + 0.18 * n
            if mat == "rag" and hsh(key, "tear", i, j) < 0.06:
                return self.pick(ramp, 0.05)                        # holes and frayed threads
            if mat == "robe_front" and name == "south":
                # the deel's front flap crosses diagonally to the right side; a contrasting trim follows its edge
                edge = int((h - 1 - j) * (w / max(1, h)) * 0.6)
                if abs(i - edge) <= 0:
                    return self.pick(self.ramp("gold") if "gold" in self.pal else self.ramp("belt"), 0.75)
                if i < edge:
                    t -= 0.08
            if mat == "belt" and name in ("south", "north", "east", "west") and i % 5 == 2:
                return self.pick(self.ramp("gold") if "gold" in self.pal else ramp, 0.8)  # belt plaques
            if mat == "shoulder" and j == h - 1 and name != "up":
                return self.pick(ramp, 0.15)                        # the shoulder piece's shadowed hem
            return self.pick(ramp, t)
        if mat in METAL or mat == "ice":
            edge = i == 0 or j == 0 or i == w - 1 or j == h - 1
            t = base + (0.2 if edge else -0.05) + 0.1 * (vnoise(p, 12.0, 7) - 0.5)
            if mat == "ice":
                t += 0.2 * math.sin((p[1] * 16 + p[0] * 5) * 0.9)
                if hsh(key, "spark", i, j) < 0.05:
                    return ramp[-1]
            if i == 1 and j == 1 and w > 3 and h > 3:
                return ramp[-1]                                     # one specular highlight
            return self.pick(ramp, t)
        if mat in SHELL:
            band = (j % 3 == 0) if name not in ("up", "down") else (i % 4 == 0)
            t = base + (-0.22 if band else 0.06) + 0.12 * (vnoise(p, 10.0, 11) - 0.5)
            return self.pick(ramp, t)
        if mat in WOOD:
            grain = 0.12 * math.sin(i * 1.7 + 3 * vnoise(p, 5.0, 13))
            return self.pick(ramp, base + grain)
        if mat in DARK:
            return self.pick(ramp, 0.25 + 0.3 * vnoise(p, 8.0, 17))
        # skin, hands and anything else: smooth with a little noise
        return self.pick(ramp, base + 0.12 * (vnoise(p, 7.0, 19) - 0.5))

    def face(self, ramp, i, j, w, h, base):
        """The front of a head: hair line, brows, eyes, nose shade and mouth (8 × 8 at density 1)."""
        u, v = i / max(1, w - 1), j / max(1, h - 1)
        if v < 0.18:
            return self.pick(self.ramp("hat_fur") if "hat_fur" in self.pal else ramp, base - 0.3)
        if 0.30 <= v <= 0.38 and (0.15 <= u <= 0.4 or 0.6 <= u <= 0.85):
            return self.pick(ramp, 0.05)                            # brows
        if 0.42 <= v <= 0.52 and (0.2 <= u <= 0.38 or 0.62 <= u <= 0.8):
            eyes = self.pal.get("glow_eyes")
            if eyes:
                return eyes[-1]
            return "#F0F0F0" if (u < 0.3 or 0.7 < u) else "#1A1410"  # whites and pupils
        if 0.55 <= v <= 0.7 and 0.43 <= u <= 0.57:
            return self.pick(ramp, base - 0.25)                     # nose shadow
        if 0.78 <= v <= 0.84 and 0.3 <= u <= 0.7:
            return self.pick(GENERIC["mouth"], 0.5)
        return self.pick(ramp, base + 0.05 * math.sin(i * 2.1 + j))


def painter(rig):
    return Painter(rig)
