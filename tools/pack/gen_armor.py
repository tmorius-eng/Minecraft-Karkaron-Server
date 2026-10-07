#!/usr/bin/env python3
"""Generate PLACEHOLDER worn-armour layers for the Баатар class armour, tiers T1..T6 (equipment assets).

Every pixel is painted procedurally on the vanilla 64x32 humanoid armour UV layout (worked out from the box UV
rule, not read from any texture): a box at (u, v) of size w x h x d unfolds into top (u+d, v), bottom (u+d+w, v)
and a side strip at row v+d running right side · front · left side · back, so rows of lamellae wrap around a body
part without a seam. Boxes used:  head (0,0) 8x8x8 + hat shell (32,0) · body (16,16) 8x12x4 · arm (40,16) 4x12x4
(the left arm mirrors it) · leg (0,16) 4x12x4 (the left leg mirrors it). Unused areas stay fully transparent.

  humanoid/baatar_tN.png           helmet (head + hat shell: bowl, brow band, nape guard, crest), chest (body +
                                   arms: lamellar coat, deel wrap edge, shoulder lames, bracers, studded belt) and
                                   boots (leg region from the boot-top row 7 down; transparent above)
  humanoid_leggings/baatar_tN.png  legs (lamellar skirt / deel hem, trousers, knee pieces) + body waist rows 8-11

Seam grid (ARMOR_PROGRESSION_VISUAL_SPEC §2.1): belt on body rows 9-11, boot top at leg row 7, shoulder edge at arm
row 4, the same rows for every tier. Palettes are the per-tier hex values of that spec (§3.1-§3.6) plus darker or
lighter steps of the same ramps; gold stays at 0 % for T1-T3 and inside the spec budget above (checked on run).

  T1 Анхан      quilted felt deel, one row of rawhide lamellae, felt cap with a fur brim and four iron strips
  T2 Бэхжсэн    iron lamellar cuirass with red-lacquer edging, shoulder lames, bronze belt boss, leather aventail
  T3 Сонгомол   full red-lacquer lamellar coat with bronze-edged rows, knee skirt with a horn-scroll hem,
                tall pointed helmet with cheek and nape guards and a white horsehair tassel
  T4 Эзэнт      large steel lames on dark iron with silver inlay, round chest disc, rank-plaque belt, half visor
  T5 Домогт     blued steel with turquoise inlay, bone-and-steel split skirt, wolf-mask visor, eagle chest disc
  T6 Тэнгэрлэг  night-sky steel with silver-white edges and star rivets, sky-blue disc, crest of white tassels

All motifs are ORIGINAL FICTION drawn from scratch here (the eagle disc, rank plaques, star-rivet constellations,
horn scroll); no Soyombo, no real tamga, no seal text. These are placeholders until the spec's 128x64 hand-painted
layers exist; the files are 64x32, the gate-0 fallback size of ARMOR_ASSET_PIPELINE §3. Output is deterministic.

    python3 tools/pack/gen_armor.py [--preview DIR]
"""
from __future__ import annotations

import argparse
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SULD = os.path.join(ROOT, "resourcepack", "assets", "suld")
EQUIPMENT = os.path.join(SULD, "equipment")
TEX_HUMANOID = os.path.join(SULD, "textures", "entity", "equipment", "humanoid")
TEX_LEGGINGS = os.path.join(SULD, "textures", "entity", "equipment", "humanoid_leggings")

W, H = 64, 32
TIERS = (1, 2, 3, 4, 5, 6)
TIER_NAME = {1: "Anhan", 2: "Bekhjsen", 3: "Songomol", 4: "Ezent", 5: "Domogt", 6: "Tengerleg"}
# gold budget per tier as a share of painted pixels over both layers (spec §2.1 rule 5)
GOLD_BUDGET = {1: 0.0, 2: 0.0, 3: 0.0, 4: 0.05, 5: 0.05, 6: 0.04}

Color = tuple[int, int, int, int]


def hx(s: str) -> Color:
    return int(s[1:3], 16), int(s[3:5], 16), int(s[5:7], 16), 255


# --- palettes (ARMOR_PROGRESSION_VISUAL_SPEC §3, ASSET_STYLE_GUIDE §2) ----------------------------------------
# ramps are (dark, mid, light); single colours are plain
PAL = {
    1: {"cloth": (hx("#4F4233"), hx("#6B5A45"), hx("#9A8667")), "plate": (hx("#7E6244"), hx("#A8865E"), hx("#C4A57A")),
        "lace": hx("#7A3B2A"), "trim": (hx("#7E6244"), hx("#A8865E")), "belt": (hx("#4A2E1E"), hx("#A8865E")),
        "stud": hx("#8D9199"), "boot": (hx("#2E1C12"), hx("#4A2E1E"), hx("#6E4A2F")),
        "metal": (hx("#3A3D44"), hx("#5E6168"), hx("#8D9199")), "fur": (hx("#6B5A45"), hx("#9A8667"), hx("#B8AA92")),
        "under": (hx("#3E3428"), hx("#4F4233"), hx("#6B5A45"))},
    2: {"cloth": (hx("#4F4233"), hx("#6B5A45"), hx("#9A8667")), "plate": (hx("#3A3D44"), hx("#5E6168"), hx("#8D9199")),
        "edge": hx("#C9CDD3"), "lace": hx("#7A1E1E"), "trim": (hx("#7A1E1E"), hx("#A3302A")),
        "belt": (hx("#2E1C12"), hx("#4A2E1E")), "stud": hx("#8C5A2B"), "boss": (hx("#6E4522"), hx("#B9803F")),
        "boot": (hx("#2E1C12"), hx("#4A2E1E"), hx("#6E4A2F")), "metal": (hx("#3A3D44"), hx("#5E6168"), hx("#8D9199")),
        "metal_hi": hx("#C9CDD3"), "leather": (hx("#2E1C12"), hx("#4A2E1E"), hx("#6E4A2F")),
        "under": (hx("#3E3428"), hx("#4F4233"), hx("#6B5A45"))},
    3: {"cloth": (hx("#141114"), hx("#1F1B1E"), hx("#2B2A2E")), "plate": (hx("#7A1E1E"), hx("#8E2420"), hx("#B8402F")),
        "edge": hx("#B9803F"), "lace": hx("#1F1B1E"), "trim": (hx("#6E4522"), hx("#B9803F")),
        "belt": (hx("#141114"), hx("#1F1B1E")), "stud": hx("#B9803F"), "boss": (hx("#8C5A2B"), hx("#B9803F")),
        "boot": (hx("#141114"), hx("#1F1B1E"), hx("#2B2A2E")), "metal": (hx("#5E6168"), hx("#8D9199"), hx("#C9CDD3")),
        "hair": (hx("#BDB6A6"), hx("#E8E4DA")), "under": (hx("#141114"), hx("#1F1B1E"), hx("#2B2A2E"))},
    4: {"cloth": (hx("#4A1016"), hx("#6E1A22"), hx("#8A2A30")), "plate": (hx("#1B1A1F"), hx("#3A3D44"), hx("#9AA0A8")),
        "ground": hx("#3A3D44"), "edge": hx("#C9CDD3"), "ep": 3, "lace": hx("#1B1A1F"), "trim": (hx("#1B1A1F"), hx("#D6D9DE")),
        "belt": (hx("#111015"), hx("#1B1A1F")), "stud": hx("#D6D9DE"), "gold": (hx("#9A7A36"), hx("#C9A04A")),
        "boot": (hx("#111015"), hx("#1B1A1F"), hx("#3A3D44")), "metal": (hx("#5E6168"), hx("#9AA0A8"), hx("#C9CDD3")),
        "silver": hx("#D6D9DE"), "hair": (hx("#BDB6A6"), hx("#E8E4DA")), "under": (hx("#111015"), hx("#1B1A1F"), hx("#2B2A2E"))},
    5: {"cloth": (hx("#3E0E12"), hx("#5A1418"), hx("#74222A")), "plate": (hx("#26304A"), hx("#3B4A66"), hx("#5C6F94")),
        "edge": hx("#5FBFB2"), "ep": 2, "lace": hx("#1B1A1F"), "trim": (hx("#2E8C86"), hx("#5FBFB2")),
        "belt": (hx("#3E0E12"), hx("#5A1418")), "stud": hx("#5FBFB2"), "gold": (hx("#8A6A2E"), hx("#B08A3E")),
        "bone": (hx("#A89F88"), hx("#D8CFB8")), "boot": (hx("#1B1A1F"), hx("#26304A"), hx("#3B4A66")),
        "metal": (hx("#26304A"), hx("#3B4A66"), hx("#5C6F94")), "turq": (hx("#2E8C86"), hx("#5FBFB2")),
        "under": (hx("#1B1A1F"), hx("#26304A"), hx("#3B4A66"))},
    6: {"cloth": (hx("#34507E"), hx("#4E7FC0"), hx("#7AA2D6")), "plate": (hx("#141C30"), hx("#1E2A44"), hx("#34507E")),
        "edge": hx("#9AA6B8"), "lace": hx("#0E1424"), "trim": (hx("#9AA6B8"), hx("#E6EAF0")),
        "belt": (hx("#0E1424"), hx("#1E2A44")), "stud": hx("#F4F1E6"), "gold": (hx("#9A7A36"), hx("#C9A04A")),
        "star": hx("#F4F1E6"), "boot": (hx("#9AA6B8"), hx("#C9CDD3"), hx("#E6EAF0")),
        "metal": (hx("#141C30"), hx("#1E2A44"), hx("#34507E")), "silver": (hx("#9AA6B8"), hx("#C9CDD3")), "white": hx("#E6EAF0"),
        "sky": (hx("#34507E"), hx("#4E7FC0")), "hair": (hx("#BDB6A6"), hx("#E8E4DA")),
        "under": (hx("#0E1424"), hx("#1E2A44"), hx("#34507E"))},
}


# --- the UV boxes ---------------------------------------------------------------------------------------------
class Box:
    """One cube of the armour model on the texture; side strip coordinates (s, r) wrap right · front · left · back."""

    def __init__(self, img: Image.Image, u: int, v: int, w: int, h: int, d: int) -> None:
        self.img, self.u, self.v, self.w, self.h, self.d = img, u, v, w, h, d
        self.sw = 2 * (w + d)
        self.spans = {"right": (0, d), "front": (d, d + w), "left": (d + w, 2 * d + w), "back": (2 * d + w, self.sw)}

    # side strip
    def put(self, s: int, r: int, c: Color | None) -> None:
        if c is None or not (0 <= r < self.h):
            return
        s %= self.sw
        self.img.putpixel((self.u + s, self.v + self.d + r), c)

    def face(self, name: str, x: int, y: int, c: Color | None) -> None:
        if name in ("top", "bottom"):
            if c is None or not (0 <= x < self.w and 0 <= y < self.d):
                return
            ox = self.u + self.d + (self.w if name == "bottom" else 0)
            self.img.putpixel((ox + x, self.v + y), c)
            return
        a, b = self.spans[name]
        if 0 <= x < b - a:
            self.put(a + x, y, c)

    def cols(self, faces=None) -> list[int]:
        if faces is None:
            return list(range(self.sw))
        out = []
        for f in faces:
            a, b = self.spans[f]
            out += list(range(a, b))
        return out

    def fill(self, r0: int, r1: int, c: Color, faces=None) -> None:
        for s in self.cols(faces):
            for r in range(r0, r1 + 1):
                self.put(s, r, c)

    def fill_cap(self, name: str, c: Color) -> None:
        for y in range(self.d):
            for x in range(self.w):
                self.face(name, x, y, c)


def boxes(img: Image.Image) -> dict[str, Box]:
    return {"head": Box(img, 0, 0, 8, 8, 8), "hat": Box(img, 32, 0, 8, 8, 8), "body": Box(img, 16, 16, 8, 12, 4),
            "arm": Box(img, 40, 16, 4, 12, 4), "leg": Box(img, 0, 16, 4, 12, 4)}


# --- painting helpers -----------------------------------------------------------------------------------------
def lamellae(b: Box, r0: int, r1: int, ramp, pw: int = 2, rh: int = 2, edge: Color | None = None,
             lace: Color | None = None, faces=None, alt=None, phase: int = 0, edge_period: int = 1) -> None:
    """Rows of small overlapping plates: lit top edge, mid body, a dark gap at each plate's right side.

    edge replaces the lit top line (bronze-, silver- or turquoise-edged rows) on every edge_period-th row; lace dots
    the row seam every second plate; alt is a second ramp used on every other row (bone/steel alternation)."""
    cols = set(b.cols(faces))
    for r in range(r0, r1 + 1):
        k, lr = divmod(r - r0, rh)
        dk, md, lt = alt if (alt is not None and k % 2) else ramp
        off = (k % 2) * (pw // 2) + phase
        for s in range(b.sw):
            if s not in cols:
                continue
            col = (s + off) % pw
            if lr == 0:
                c = edge if (edge is not None and k % edge_period == 0) else lt
                if lace is not None and col == 0 and ((s + off) // pw) % 2 == 0:
                    c = lace
            elif col == pw - 1:
                c = dk
            elif lr == rh - 1 and rh > 2:
                c = dk
            else:
                c = md
            b.put(s, r, c)


def edged(p: dict) -> dict:
    """The tier's row-edge colour and how often it repeats, as lamellae() keyword arguments."""
    return {"edge": p["edge"], "edge_period": p.get("ep", 1)}


def quilt(b: Box, r0: int, r1: int, ramp, faces=None, step: int = 3) -> None:
    """Quilted felt: mid-tone pads, a dark stitched seam every `step` rows with light stitch dots, lit pad tops."""
    dk, md, lt = ramp
    for s in b.cols(faces):
        for r in range(r0, r1 + 1):
            lr = (r - r0) % step
            if lr == step - 1:
                c = lt if s % 2 == (r // step) % 2 else dk
            else:
                c = md
            b.put(s, r, c)


def studs(b: Box, r: int, c: Color, step: int = 2, phase: int = 0, faces=None) -> None:
    for s in b.cols(faces):
        if (s + phase) % step == 0:
            b.put(s, r, c)


def belt(b: Box, p: dict, rows=(9, 10, 11), stud_step: int = 2) -> None:
    dk, md = p["belt"]
    b.fill(rows[0], rows[-1], md)
    b.fill(rows[0], rows[0], dk)
    b.fill(rows[-1], rows[-1], dk)
    studs(b, rows[1], p["stud"], stud_step, 0)


def deel_flap(b: Box, top: int, c_edge: Color, c_under: Color | None = None) -> None:
    """The diagonal wrap edge of a deel on the body front: from the neck down to the wearer's right side."""
    for i in range(5):
        x, y = 4 - i, top + i
        b.face("front", x, y, c_edge)
        if c_under is not None and y > top:
            for xx in range(x):
                b.face("front", xx, y - 1, c_under)


def disc(b: Box, cx: int, cy: int, rim: Color, field: Color, centre: Color | None = None, size: int = 4) -> None:
    """A round chest disc on the body front: a 4x4 (or 6x6) circle with corners cut."""
    x0, y0 = cx - size // 2, cy - size // 2
    for y in range(size):
        for x in range(size):
            corner = (x in (0, size - 1)) and (y in (0, size - 1))
            if corner:
                continue
            edge = x in (0, size - 1) or y in (0, size - 1) or (size == 6 and (x, y) in
                                                                   ((1, 1), (4, 1), (1, 4), (4, 4)))
            b.face("front", x0 + x, y0 + y, rim if edge else field)
    if centre is not None:
        b.face("front", cx - 1, cy - 1, centre)


def sole(b: Box, ramp, toe: Color) -> None:
    """Common boot base: dark sole row, upturned toe hinted by a lighter tip on the front face."""
    dk, md, lt = ramp
    b.fill(11, 11, dk)
    b.face("front", 1, 10, toe)
    b.face("front", 2, 10, toe)
    b.face("front", 1, 11, lt)
    b.face("front", 2, 11, lt)
    b.fill_cap("bottom", dk)


# --- helmets (head box + hat shell) ---------------------------------------------------------------------------
def helmet_bowl(head: Box, ramp, seams: Color | None, crown: Color, rows: int = 2) -> None:
    """Bowl over the head: top face with segment seams converging on the crown, side rows 0..rows-1."""
    dk, md, lt = ramp
    for y in range(8):
        for x in range(8):
            ring = max(abs(2 * x - 7), abs(2 * y - 7)) // 2   # 0 centre .. 3 rim
            c = (lt, lt, md, md)[ring] if (x + y) % 2 == 0 or ring < 2 else (md, md, md, dk)[ring]
            if x < y:
                c = md if c == lt else c
            head.face("top", x, y, c)
    if seams is not None:
        for i in range(8):
            head.face("top", i, i, seams)
            head.face("top", 7 - i, i, seams)
    head.face("top", 3, 3, crown)
    head.face("top", 4, 3, crown)
    head.face("top", 3, 4, crown)
    head.face("top", 4, 4, crown)
    for s in range(head.sw):
        for r in range(rows):
            head.put(s, r, lt if r == 0 and s % 8 in (1, 2) else md)
    if seams is not None:
        for s in range(0, head.sw, 8):
            for r in range(rows):
                head.put(s, r, seams)


def helmet(t: int, p: dict, head: Box, hat: Box) -> None:
    m_dk, m_md, m_lt = p["metal"]
    if t == 1:
        # felt cap, four iron strips meeting at the crown, fur brim on the hat shell, felt ear flaps
        f_dk, f_md, f_lt = p["cloth"]
        for y in range(8):
            for x in range(8):
                head.face("top", x, y, f_lt if (x + y) % 5 == 0 else f_md)
        for i in range(8):
            head.face("top", 3, i, m_md)
            head.face("top", 4, i, m_lt if i < 4 else m_md)
            head.face("top", i, 3, m_md)
            head.face("top", i, 4, m_md)
        head.face("top", 3, 3, m_lt)
        head.face("top", 4, 4, m_dk)
        head.fill(0, 1, f_md)
        for s in range(head.sw):
            if s % 8 in (3, 4):
                head.put(s, 0, m_lt if s % 8 == 4 else m_md)
                head.put(s, 1, m_md if s % 8 == 4 else m_dk)
        # ear flaps of leather on the sides, felt at the back
        for name in ("right", "left"):
            for y in (2, 3, 4, 5):
                for x in range(1, 7):
                    head.face(name, x, y, p["boot"][1] if y < 5 else p["boot"][0])
            head.face(name, 3, 5, p["lace"])
        for y in (2, 3, 4):
            for x in range(8):
                head.face("back", x, y, f_md if y < 4 else f_dk)
        fur_dk, fur_md, fur_lt = p["fur"]
        for s in range(hat.sw):
            hat.put(s, 2, fur_lt if s % 3 == 0 else fur_md)
            hat.put(s, 3, fur_md if s % 3 != 1 else fur_dk)
        return

    if t == 2:
        # segmented iron bowl with a short spike, bronze-riveted brow band, leather aventail at back and sides
        helmet_bowl(head, p["metal"], m_dk, p["metal_hi"])
        l_dk, l_md, l_lt = p["leather"]
        for s in head.cols(("right", "left", "back")):
            for r in range(4, 8):
                c = l_md if (s + r) % 4 else l_dk
                if r == 7:
                    c = l_dk
                head.put(s, r, c)
            if s % 2 == 0:
                head.put(s, 5, p["lace"])
        for s in range(hat.sw):
            hat.put(s, 2, m_lt)
            hat.put(s, 3, m_md if s % 3 else p["stud"])
        hat.face("top", 3, 3, p["metal_hi"])
        hat.face("top", 4, 4, m_md)
        return

    if t == 3:
        # tall pointed steel bowl, bronze brow band, red-lacquer cheek and nape guards, white horsehair tassel
        helmet_bowl(head, p["metal"], m_dk, p["metal"][2])
        for s in range(head.sw):
            head.put(s, 1, m_md)
        lam = p["plate"]
        lamellae(head, 4, 7, lam, pw=2, rh=2, **edged(p), faces=("right", "left", "back"))
        for y in range(4, 8):
            head.face("front", 0, y, lam[1] if y < 7 else lam[0])
            head.face("front", 7, y, lam[1] if y < 7 else lam[0])
        head.face("front", 0, 4, p["edge"])
        head.face("front", 7, 4, p["edge"])
        for s in range(hat.sw):
            hat.put(s, 2, p["trim"][1])
            hat.put(s, 3, p["trim"][0] if s % 2 else p["trim"][1])
        hd, hl = p["hair"]
        for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
            hat.face("top", x, y, p["edge"])
        for x, y in ((3, 5), (4, 5), (3, 6), (4, 6), (4, 7), (3, 7)):
            hat.face("top", x, y, hl if (x + y) % 2 else hd)
        for y, xs in ((0, (3, 4)), (1, (3, 4)), (2, (4,)), (4, (3,)), (5, (4,))):
            for x in xs:
                hat.face("back", x, y, hl if y % 2 == 0 else hd)
        return

    if t == 4:
        # steel helmet, riveted silver brow plate, half visor with eye slits, long dark-iron nape guard, finial
        helmet_bowl(head, p["metal"], p["ground"], p["silver"], rows=2)
        lamellae(head, 4, 7, (p["ground"], p["metal"][0], p["metal"][1]), pw=4, rh=2, **edged(p),
                 faces=("right", "left", "back"))
        for x in range(8):
            if x in (1, 2, 5, 6):
                continue
            head.face("front", x, 4, m_md if x in (3, 4) else m_dk)
        head.face("front", 3, 5, m_lt)
        head.face("front", 4, 5, m_md)
        for y in range(4, 8):
            head.face("front", 0, y, m_dk)
            head.face("front", 7, y, m_dk)
        for s in range(hat.sw):
            hat.put(s, 2, p["silver"])
            hat.put(s, 3, m_md if s % 2 else p["ground"])
        hat.face("front", 3, 3, p["gold"][1])
        hat.face("front", 4, 3, p["gold"][1])
        hl = p["hair"][1]
        for x, y in ((3, 2), (4, 2), (2, 3), (5, 3), (2, 4), (5, 4), (3, 5), (4, 5)):
            hat.face("top", x, y, hl if (x + y) % 2 else p["hair"][0])
        hat.face("top", 3, 3, p["gold"][1])
        hat.face("top", 4, 4, p["gold"][0])
        hat.face("top", 3, 4, p["silver"])
        hat.face("top", 4, 3, p["silver"])
        return

    if t == 5:
        # blued steel, wolf-mask visor (brow, eye slits, snout), swept nape lames with turquoise edges, ears
        helmet_bowl(head, p["metal"], p["turq"][0], p["gold"][1])
        lamellae(head, 4, 7, p["plate"], pw=2, rh=2, **edged(p), faces=("right", "left", "back"),
                 alt=(p["bone"][0], p["bone"][1], p["bone"][1]))
        bd, bl = p["bone"]
        for x in range(8):
            if x in (1, 2, 5, 6):
                continue
            head.face("front", x, 4, m_md)
        for y in range(4, 8):
            head.face("front", 0, y, m_dk)
            head.face("front", 7, y, m_dk)
        for x, y, c in ((3, 5, m_lt), (4, 5, m_md), (2, 6, m_md), (3, 6, m_lt), (4, 6, m_md), (5, 6, m_dk),
                        (3, 7, bd), (4, 7, bd), (2, 7, bl), (5, 7, bl)):
            head.face("front", x, y, c)
        for s in range(hat.sw):
            hat.put(s, 2, p["turq"][1] if s % 4 else p["gold"][1])
            hat.put(s, 3, m_dk)
        # wolf ears and a brow ridge on the shell
        for x in (1, 6):
            hat.face("front", x, 0, m_lt)
            hat.face("front", x, 1, m_md)
        hat.face("front", 2, 1, m_md)
        hat.face("front", 5, 1, m_md)
        for x in range(1, 7):
            hat.face("front", x, 3, p["turq"][0] if x not in (3, 4) else m_md)
        hat.face("front", 1, 3, p["turq"][1])
        hat.face("front", 6, 3, p["turq"][1])
        return

    # t == 6: night-sky steel, crown band of star rivets, cheek guards to a narrow face opening, nine-tassel crest
    helmet_bowl(head, p["metal"], p["sky"][0], p["silver"][1])
    lamellae(head, 4, 7, p["plate"], pw=2, rh=2, **edged(p), faces=("right", "left", "back"))
    for y in range(4, 8):
        for x in (0, 1, 6, 7):
            head.face("front", x, y, m_md if x in (0, 7) else (p["silver"][0] if y == 7 else m_lt))
    for x in range(2, 6):
        head.face("front", x, 7, m_md)
    head.face("front", 2, 7, p["silver"][0])
    head.face("front", 5, 7, p["silver"][0])
    for s in range(hat.sw):
        hat.put(s, 2, p["silver"][1])
        hat.put(s, 3, p["star"] if s % 4 == 1 else p["metal"][1])  # 8 star rivets
    hat.face("front", 3, 3, p["sky"][1])
    hat.face("front", 4, 3, p["sky"][1])
    hat.face("front", 3, 2, p["gold"][1])
    hd, hl = p["hair"]
    # crest: a ridge of white tassels front-to-back along the shell top, sweeping down the back
    for y in range(1, 8):
        hat.face("top", 3, y, hl if y % 2 else hd)
        hat.face("top", 4, y, hd if y % 2 else hl)
    hat.face("top", 3, 0, p["gold"][0])
    hat.face("top", 4, 0, p["silver"][1])
    for y in range(0, 7):
        for x in (3, 4) if y < 3 else ((2, 4) if y < 5 else (3, 5)):
            hat.face("back", x, y, hl if (x + y) % 2 else hd)


# --- chest (body + arms) --------------------------------------------------------------------------------------
def chest(t: int, p: dict, body: Box, arm: Box) -> None:
    c_dk, c_md, c_lt = p["cloth"]
    if t == 1:
        quilt(body, 0, 8, p["cloth"])
        deel_flap(body, 0, p["trim"][1], c_dk)
        # one row of rawhide lamellae across the chest, laced
        lamellae(body, 3, 4, p["plate"], pw=2, rh=2, lace=p["lace"], faces=("right", "front", "left"))
        belt(body, p, stud_step=4)
        body.fill_cap("top", c_md)
        for x in range(8):
            body.face("top", x, 0, c_lt if x % 2 else c_md)
        # felt sleeves with a stitched shoulder edge, leather cuff
        quilt(arm, 0, 9, p["cloth"])
        studs(arm, 3, p["lace"], 2)
        arm.fill(9, 9, p["boot"][1])
        arm.fill_cap("top", c_md)
        for x in range(4):
            arm.face("top", x, 3, p["lace"] if x % 2 == 0 else c_md)
        return

    if t == 2:
        # deel under an iron lamellar cuirass with red-lacquer edging; bronze belt boss
        body.fill(0, 8, c_md)
        lamellae(body, 0, 7, p["plate"], pw=2, rh=2, edge=None, lace=p["lace"])
        body.fill(8, 8, p["trim"][1])
        studs(body, 8, p["trim"][0], 2, 1)
        # red-lacquer edge down the side openings
        for s in (0, 3, 12, 15):
            for r in range(0, 8):
                body.put(s, r, p["trim"][0] if r % 2 else p["trim"][1])
        belt(body, p, stud_step=3)
        bd, bl = p["boss"]
        for x, y, c in ((3, 9, bd), (4, 9, bd), (3, 10, bl), (4, 10, bd), (3, 11, bd), (4, 11, bd)):
            body.face("front", x, y, c)
        body.fill_cap("top", p["plate"][1])
        for x in range(8):
            body.face("top", x, 0, p["trim"][1])
            body.face("top", x, 3, p["trim"][0])
        # shoulder lames: two iron rows on the arm tops; felt sleeve; leather bracers
        lamellae(arm, 0, 3, p["plate"], pw=2, rh=2, lace=p["lace"])
        arm.fill(4, 4, p["trim"][0])
        arm.fill(5, 6, c_md)
        l_dk, l_md, l_lt = p["leather"]
        arm.fill(7, 9, l_md)
        arm.fill(7, 7, l_lt)
        studs(arm, 8, p["lace"], 2)
        arm.fill(9, 9, l_dk)
        arm.fill_cap("top", p["plate"][1])
        for x in range(4):
            arm.face("top", x, 0, p["edge"])
            arm.face("top", x, 2, p["plate"][0])
        return

    if t == 3:
        # full red-lacquer lamellar coat with bronze-edged rows, steel chest plaque, black studded belt
        lamellae(body, 0, 8, p["plate"], pw=2, rh=3, **edged(p))
        for x, y, c in ((3, 3, p["metal"][2]), (4, 3, p["metal"][1]), (3, 4, p["metal"][1]),
                        (4, 4, p["metal"][0]), (3, 5, p["metal"][1]), (4, 5, p["metal"][0])):
            body.face("front", x, y, c)
        deel_flap(body, 0, p["trim"][1])
        belt(body, p, stud_step=2)
        body.fill_cap("top", p["plate"][1])
        for x in range(8):
            body.face("top", x, 0, p["edge"])
            body.face("top", x, 3, p["plate"][0])
        # shoulder lames: three rows to the shoulder edge, black cloth sleeve, laced lamellar bracers
        lamellae(arm, 0, 5, p["plate"], pw=2, rh=2, **edged(p))
        arm.fill(6, 6, p["trim"][0])
        arm.fill(7, 9, c_md)
        lamellae(arm, 7, 9, p["plate"], pw=2, rh=3, **edged(p), faces=("right", "front", "back"))
        arm.fill_cap("top", p["plate"][1])
        for x in range(4):
            arm.face("top", x, 0, p["edge"])
            arm.face("top", x, 3, p["plate"][2])
        return

    if t == 4:
        # large steel lames on a dark-iron ground with silver inlay, round chest disc, broad rank-plaque belt
        g = p["ground"]
        body.fill(0, 8, g)
        lamellae(body, 0, 8, p["plate"], pw=4, rh=3, **edged(p), lace=None)
        for s in range(body.sw):     # silver inlay line through the middle of every lame
            for r in (1, 4, 7):
                if (s + (r // 3) * 2) % 8 == 1:
                    body.put(s, r, p["silver"])
        body.fill(0, 0, p["trim"][0], ("front", "back"))
        disc(body, 4, 5, p["silver"], p["lace"], centre=None, size=4)
        body.face("front", 3, 4, p["gold"][1])
        body.face("front", 4, 5, p["gold"][0])
        # broad black lacquer belt with silver edges and gold rank plaques
        dk, md = p["belt"]
        body.fill(9, 11, md)
        body.fill(9, 9, p["silver"])
        body.fill(11, 11, dk)
        for s in body.cols():
            if s % 4 == 1:
                body.put(s, 10, p["silver"])
        for x in (1, 3, 4, 6):
            body.face("front", x, 10, p["gold"][1] if x in (3, 4) else p["silver"])
        body.face("front", 3, 11, p["gold"][0])
        body.face("front", 4, 11, p["gold"][0])
        body.fill_cap("top", p["metal"][1])
        for x in range(8):
            body.face("top", x, 0, p["cloth"][1])
            body.face("top", x, 3, p["silver"])
        # three stacked pauldron lames with silver edges, red silk sleeve, steel bracers
        lamellae(arm, 0, 5, p["plate"], pw=4, rh=2, **edged(p))
        arm.fill(6, 6, p["ground"])
        arm.fill(7, 7, c_md)
        arm.fill(8, 9, p["metal"][1])
        arm.fill(8, 8, p["metal"][2])
        arm.fill(9, 9, p["silver"], ("front",))
        arm.fill(9, 9, p["metal"][0], ("right", "left", "back"))
        arm.fill(10, 10, p["metal"][0], ("right", "front", "back"))
        arm.fill_cap("top", p["plate"][1])
        for x in range(4):
            arm.face("top", x, 0, p["silver"])
            arm.face("top", x, 2, p["plate"][2])
        return

    if t == 5:
        # blued-steel lamellae with turquoise inlay rows, aged-gold disc with a bone eagle (ORIGINAL FICTION)
        lamellae(body, 0, 8, p["plate"], pw=2, rh=3, **edged(p), lace=p["lace"])
        tq, tl = p["turq"]
        disc(body, 4, 5, p["gold"][1], tq, size=6)
        bd, bl = p["bone"]
        for x, y in ((2, 4), (5, 4), (3, 5), (4, 5), (3, 6), (4, 6)):  # eagle: spread wings, body, tail
            body.face("front", x, y, bl if y < 6 else bd)
        body.face("front", 3, 4, p["gold"][0])
        body.face("front", 4, 4, p["gold"][0])
        dk, md = p["belt"]
        body.fill(9, 11, md)
        body.fill(9, 9, p["gold"][0])
        body.fill(11, 11, dk)
        studs(body, 10, tl, 2)
        for x in (3, 4):
            body.face("front", x, 10, p["gold"][1])
        body.fill_cap("top", p["plate"][1])
        for x in range(8):
            body.face("top", x, 0, tl)
            body.face("top", x, 3, p["cloth"][1])
        # swept lames alternating blued steel and bone, dark-red sleeve, greave-like bracer with turquoise studs
        lamellae(arm, 0, 5, p["plate"], pw=2, rh=2, **edged(p), alt=(bd, bl, bl))
        arm.fill(6, 6, p["metal"][0])
        arm.fill(7, 7, p["cloth"][1])
        arm.fill(8, 10, p["plate"][1])
        arm.fill(8, 8, p["plate"][2])
        studs(arm, 9, tl, 2, 1)
        arm.fill(10, 10, p["plate"][0])
        arm.fill_cap("top", p["plate"][1])
        for x in range(4):
            arm.face("top", x, 0, bl)
            arm.face("top", x, 2, tl)
        return

    # t == 6: night-sky lamellae with silver-white edges and star-rivet constellations, sky-blue chest disc
    lamellae(body, 0, 8, p["plate"], pw=2, rh=3, **edged(p), lace=p["lace"])
    for s, r in ((1, 1), (6, 2), (9, 1), (14, 5), (18, 2), (21, 4), (16, 7)):   # star-rivet constellations
        body.put(s, r, p["star"])
    sd, sl = p["sky"]
    disc(body, 4, 5, p["silver"][1], sl, size=6)
    for i in range(1, 5):            # silver-white on the lit (top-left) arc of the rim
        body.face("front", 1 + i, 2, p["white"])
        body.face("front", 1, 2 + i, p["white"])
    body.face("front", 2, 3, p["white"])
    for x, y in ((3, 4), (4, 4), (2, 5), (5, 5), (3, 6), (4, 6)):
        body.face("front", x, y, sd)
    body.face("front", 3, 5, p["star"])
    body.face("front", 4, 5, p["gold"][1])
    dk, md = p["belt"]
    body.fill(9, 11, md)
    body.fill(9, 9, p["silver"][1])
    body.fill(11, 11, p["silver"][0])
    studs(body, 10, p["silver"][1], 3)
    for x in (3, 4):
        body.face("front", x, 10, sl)
    body.face("front", 2, 10, p["silver"][1])
    body.face("front", 5, 10, p["silver"][1])
    body.fill_cap("top", p["plate"][1])
    for x in range(8):
        body.face("top", x, 0, p["silver"][1])
        body.face("top", x, 3, sl)
    lamellae(arm, 0, 5, p["plate"], pw=2, rh=2, **edged(p))
    for s, r in ((1, 1), (10, 1)):
        arm.put(s, r, p["star"])
    arm.fill(6, 6, p["silver"][0])
    arm.fill(7, 7, p["cloth"][1])
    arm.fill(8, 10, p["silver"][1])
    arm.fill(9, 9, p["silver"][0])
    arm.fill(10, 10, p["plate"][1])
    arm.fill_cap("top", p["plate"][1])
    for x in range(4):
        arm.face("top", x, 0, p["silver"][1])
        arm.face("top", x, 2, p["plate"][2])


# --- boots (leg box of the humanoid layer, rows 7..11) --------------------------------------------------------
def boots(t: int, p: dict, leg: Box) -> None:
    dk, md, lt = p["boot"]
    if t == 1:
        leg.fill(7, 10, md)
        leg.fill(7, 7, lt)
        studs(leg, 8, p["lace"], 4, 1, ("front",))
        sole(leg, p["boot"], lt)
        return
    if t == 2:
        leg.fill(7, 10, md)
        leg.fill(7, 7, lt)
        m_dk, m_md, m_lt = p["metal"]
        for y, (a, b) in ((7, (m_lt, m_md)), (8, (m_md, m_dk)), (9, (m_md, m_dk))):
            leg.face("front", 1, y, a)
            leg.face("front", 2, y, b)
        leg.face("front", 1, 7, p["stud"])
        sole(leg, p["boot"], lt)
        return
    if t == 3:
        leg.fill(7, 10, md)
        lamellae(leg, 7, 9, p["plate"], pw=2, rh=3, **edged(p), faces=("right", "front", "left"))
        studs(leg, 9, p["lace"], 2, 1, ("front",))
        sole(leg, p["boot"], p["trim"][0])
        return
    if t == 4:
        leg.fill(7, 10, md)
        leg.fill(7, 7, p["ground"])
        lamellae(leg, 8, 10, p["plate"], pw=4, rh=3, **edged(p), faces=("right", "front", "left"))
        sole(leg, p["boot"], p["silver"])
        leg.face("front", 1, 11, p["silver"])
        leg.face("front", 2, 11, p["silver"])
        return
    if t == 5:
        leg.fill(7, 10, md)
        lamellae(leg, 7, 10, p["plate"], pw=2, rh=2, **edged(p), faces=("right", "front", "left"),
                 alt=(p["bone"][0], p["bone"][1], p["bone"][1]))
        for x in (0, 3):
            leg.face("front", x, 9, p["turq"][1])
        sole(leg, p["boot"], p["bone"][1])
        return
    leg.fill(7, 10, md)
    leg.fill(7, 7, p["plate"][1])
    leg.fill(8, 8, p["silver"][0])
    leg.fill(10, 10, dk)
    leg.face("front", 0, 9, p["star"])
    leg.face("front", 3, 9, p["sky"][1])
    sole(leg, (p["plate"][0], md, lt), lt)
    leg.face("front", 1, 10, p["sky"][1])
    leg.face("front", 2, 10, p["sky"][1])


# --- leggings layer (legs + body waist) -----------------------------------------------------------------------
def waist(t: int, p: dict, body: Box) -> None:
    u_dk, u_md, u_lt = p["under"]
    body.fill(8, 11, u_md)
    body.fill(8, 8, u_lt)
    if t == 1:
        quilt(body, 8, 11, p["cloth"])
        body.fill(10, 10, p["trim"][0])
    elif t == 2:
        body.fill(8, 9, p["cloth"][1])
        body.fill(10, 11, p["plate"][1])
        lamellae(body, 10, 11, p["plate"], pw=2, rh=2)
    else:
        lamellae(body, 9, 11, p["plate"], pw=4 if t == 4 else 2, rh=3, **edged(p))
        body.fill(8, 8, p["cloth"][1] if t in (4, 5, 6) else u_lt)
    body.fill_cap("bottom", u_dk)


def leggings(t: int, p: dict, leg: Box) -> None:
    u_dk, u_md, u_lt = p["under"]
    # trousers everywhere first: mid tone, dark inner face, dark hem
    leg.fill(0, 11, u_md)
    leg.fill(0, 11, u_dk, ("left",))
    leg.fill(11, 11, u_dk)
    leg.fill_cap("bottom", u_dk)
    leg.fill_cap("top", u_md)
    c_dk, c_md, c_lt = p["cloth"]
    if t == 1:
        # deel hem to mid-thigh with a stitched edge, leather knee patches
        quilt(leg, 0, 3, p["cloth"], faces=("right", "front", "back"))
        studs(leg, 4, p["lace"], 2, 0, ("right", "front", "back"))
        for x in (1, 2):
            leg.face("front", x, 6, p["boot"][1])
            leg.face("front", x, 7, p["boot"][0])
        return
    if t == 2:
        # deel below the cuirass and two rows of iron-scaled thigh guards
        leg.fill(0, 1, c_md, ("right", "front", "back"))
        lamellae(leg, 2, 5, p["plate"], pw=2, rh=2, lace=p["lace"], faces=("right", "front", "back"))
        leg.fill(6, 6, p["trim"][0], ("right", "front", "back"))
        for x in (1, 2):
            leg.face("front", x, 7, p["leather"][2])
        return
    if t == 3:
        # lamellar skirt panels to the knee over black cloth, split at the inner face, horn-scroll hem
        lamellae(leg, 0, 5, p["plate"], pw=2, rh=3, **edged(p), faces=("right", "front", "back"))
        tr_dk, tr_lt = p["trim"]
        for s in leg.cols(("right", "front", "back")):   # horn scroll: a repeating hooked curl, 4 px period
            leg.put(s, 6, tr_lt if s % 4 in (0, 1, 2) else tr_dk)
            leg.put(s, 7, tr_lt if s % 4 in (0, 3) else (p["plate"][0] if s % 4 == 2 else tr_dk))
        return
    if t == 4:
        # steel-edged skirt panels, longer, over a deep-red silk under-robe that shows at the hem
        leg.fill(0, 9, c_md, ("right", "front", "back"))
        lamellae(leg, 0, 7, p["plate"], pw=4, rh=2, **edged(p), faces=("right", "front", "back"))
        leg.fill(8, 8, c_lt, ("right", "front", "back"))
        leg.fill(9, 9, c_dk, ("right", "front", "back"))
        for x in (1, 2):
            leg.face("front", x, 8, p["gold"][1] if x == 1 else c_lt)
        return
    if t == 5:
        # long split skirt alternating blued-steel and bone lames, dark-red under-robe
        lamellae(leg, 0, 8, p["plate"], pw=2, rh=2, **edged(p), faces=("right", "front", "back"),
                 alt=(p["bone"][0], p["bone"][1], p["bone"][1]))
        leg.fill(9, 9, c_md, ("right", "front", "back"))
        leg.fill(10, 10, p["turq"][0], ("front",))
        return
    # t == 6: full lamellar skirt with silver hem lames over a sky-blue under-robe
    lamellae(leg, 0, 7, p["plate"], pw=2, rh=2, **edged(p), faces=("right", "front", "back"))
    leg.fill(8, 8, p["white"], ("front",))
    leg.fill(8, 8, p["silver"][1], ("right", "back"))
    leg.fill(9, 9, p["silver"][0], ("right", "front", "back"))
    leg.fill(10, 10, c_md, ("right", "front", "back"))
    for s, r in ((1, 2), (6, 4), (13, 2)):
        leg.put(s, r, p["star"])
    leg.face("front", 1, 8, p["sky"][1])


# --- build ----------------------------------------------------------------------------------------------------
def build(t: int) -> tuple[Image.Image, Image.Image]:
    p = PAL[t]
    hum = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bx = boxes(hum)
    helmet(t, p, bx["head"], bx["hat"])
    chest(t, p, bx["body"], bx["arm"])
    boots(t, p, bx["leg"])
    lgs = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    lx = boxes(lgs)
    waist(t, p, lx["body"])
    leggings(t, p, lx["leg"])
    return hum, lgs


def gold_share(t: int, imgs) -> float:
    gold = set(PAL[t].get("gold", ()))
    painted = hits = 0
    for im in imgs:
        raw = im.tobytes()
        for i in range(0, len(raw), 4):
            px = tuple(raw[i:i + 4])
            if px[3]:
                painted += 1
                hits += px in gold
    return hits / max(painted, 1)


def equipment_json(t: int) -> dict:
    tex = f"suld:baatar_t{t}"
    return {"layers": {"humanoid": [{"texture": tex}], "humanoid_leggings": [{"texture": tex}]}}


# --- preview --------------------------------------------------------------------------------------------------
def doll(hum: Image.Image, lgs: Image.Image, side: str) -> Image.Image:
    """A flat paper-doll (16x32) of the front or back faces: leggings under the humanoid layer, as worn."""
    out = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    fx = {"front": 1, "back": 3}[side]

    def face(img, u, v, w, h, d):
        xs = {1: u + d, 3: u + d + w + d}[fx]
        return img.crop((xs, v + d, xs + w, v + d + h))

    for img in (lgs, hum):
        out.alpha_composite(face(img, 0, 0, 8, 8, 8), (4, 0))
        out.alpha_composite(face(img, 32, 0, 8, 8, 8), (4, 0))
        out.alpha_composite(face(img, 16, 16, 8, 12, 4), (4, 8))
        arm = face(img, 40, 16, 4, 12, 4)
        out.alpha_composite(arm, (0, 8))
        out.alpha_composite(arm.transpose(Image.FLIP_LEFT_RIGHT), (12, 8))
        leg = face(img, 0, 16, 4, 12, 4)
        out.alpha_composite(leg, (4, 20))
        out.alpha_composite(leg.transpose(Image.FLIP_LEFT_RIGHT), (8, 20))
    return out


def checker(w: int, h: int, cell: int) -> Image.Image:
    im = Image.new("RGBA", (w, h), (58, 62, 74, 255))
    d = ImageDraw.Draw(im)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(74, 78, 92, 255))
    return im


def preview(sets, out_dir: str) -> str:
    s = 8
    tex_w, tex_h = W * s, H * s
    doll_w = 16 * s
    gap, label = 12, 14
    row_h = tex_h + label + gap
    sheet_w = gap + 2 * (tex_w + gap) + 2 * (doll_w + gap)
    sheet = Image.new("RGBA", (sheet_w, gap + 6 * row_h), (30, 32, 40, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (t, hum, lgs) in enumerate(sets):
        y = gap + i * row_h
        x = gap
        for name, im in (("humanoid", hum), ("humanoid_leggings", lgs)):
            sheet.paste(checker(tex_w, tex_h, s), (x, y + label))
            big = im.resize((tex_w, tex_h), Image.NEAREST)
            sheet.alpha_composite(big, (x, y + label))
            draw.text((x, y), f"T{t} {TIER_NAME[t]}  {name}/baatar_t{t}.png", fill=(220, 224, 232, 255))
            x += tex_w + gap
        for side in ("front", "back"):
            dl = doll(hum, lgs, side).resize((doll_w, 32 * s), Image.NEAREST)
            bg = Image.new("RGBA", (doll_w, 32 * s), (96, 104, 92, 255))
            sheet.paste(bg, (x, y + label))
            sheet.alpha_composite(dl, (x, y + label))
            draw.text((x, y), side, fill=(220, 224, 232, 255))
            x += doll_w + gap
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "baatar_armor.png")
    sheet.save(path)
    return path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", help="write an x8 preview sheet of all 12 layers (+ paper dolls) to this folder")
    args = ap.parse_args()
    for d in (EQUIPMENT, TEX_HUMANOID, TEX_LEGGINGS):
        os.makedirs(d, exist_ok=True)
    sets = []
    for t in TIERS:
        hum, lgs = build(t)
        share = gold_share(t, (hum, lgs))
        if share > GOLD_BUDGET[t] + 1e-9:
            raise SystemExit(f"baatar_t{t}: gold covers {share:.1%} of painted pixels (budget {GOLD_BUDGET[t]:.0%})")
        hum.save(os.path.join(TEX_HUMANOID, f"baatar_t{t}.png"), optimize=False)
        lgs.save(os.path.join(TEX_LEGGINGS, f"baatar_t{t}.png"), optimize=False)
        with open(os.path.join(EQUIPMENT, f"baatar_t{t}.json"), "w", encoding="utf-8") as fh:
            json.dump(equipment_json(t), fh, indent=1)
            fh.write("\n")
        sets.append((t, hum, lgs))
        print(f"baatar_t{t}: humanoid + humanoid_leggings 64x32, gold {share:.1%}")
    if args.preview:
        print("preview:", preview(sets, args.preview))


if __name__ == "__main__":
    main()
