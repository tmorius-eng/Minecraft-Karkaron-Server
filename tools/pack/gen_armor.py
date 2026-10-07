#!/usr/bin/env python3
"""Generate PLACEHOLDER worn-armour layers for all five class armours, tiers T1..T6 (equipment assets).

Every pixel is painted procedurally on the vanilla 64x32 humanoid armour UV layout (worked out from the box UV
rule, not read from any texture): a box at (u, v) of size w x h x d unfolds into top (u+d, v), bottom (u+d+w, v)
and a side strip at row v+d running right side · front · left side · back, so rows of lamellae wrap around a body
part without a seam. Boxes used:  head (0,0) 8x8x8 + hat shell (32,0) · body (16,16) 8x12x4 · arm (40,16) 4x12x4
(the left arm mirrors it) · leg (0,16) 4x12x4 (the left leg mirrors it). Unused areas stay fully transparent; the
run checks that nothing is painted outside these boxes and that alpha is only 0 or 255.

  humanoid/<class>_tN.png           helmet (head + hat shell), chest (body + arms) and boots (leg region from the
                                    boot-top row 7 down; transparent above)
  humanoid_leggings/<class>_tN.png  legs + body waist rows 8-11
  equipment/<class>_tN.json         both layers -> suld:<class>_tN (ArmorRules.assetId)

Seam grid (ARMOR_PROGRESSION_VISUAL_SPEC §2.1): belt on body rows 9-11, boot top at leg row 7, shoulder edge at arm
row 4, the same rows for every tier and class. Gold stays at 0 % for T1-T3 and inside the spec budget above (checked
on run against every gold swatch of every palette). Tiers (ArmorTier): T1 Эхлэл · T2 Сайжруулсан · T3 Элчин ·
T4 Хааны · T5 Тэнгэрлэг · T6 Дээдэс. Every class climbs the same material ladder: felt/rawhide → iron + bronze →
steel + bronze → steel + silver inlay (+ a little gold) → blued steel + turquoise (+ aged gold) → night-sky steel,
silver-white edges and star rivets.

  baatar      heavy lamellar warrior (spec §3 palettes: felt, iron, red lacquer, steel/silver, blued, night sky)
    T1 Эхлэл        quilted felt deel, one row of rawhide lamellae, felt cap with a fur brim and four iron strips
    T2 Сайжруулсан  iron lamellar cuirass with red-lacquer edging, shoulder lames, bronze belt boss, aventail
    T3 Элчин        full red-lacquer lamellar coat with bronze-edged rows, knee skirt with a horn-scroll hem,
                    tall pointed helmet with cheek and nape guards and a white horsehair tassel
    T4 Хааны        large steel lames on dark iron with silver inlay, round chest disc, rank-plaque belt, half visor
    T5 Тэнгэрлэг    blued steel with turquoise inlay, bone-and-steel split skirt, wolf-mask visor, eagle disc
    T6 Дээдэс       night-sky steel with silver-white edges and star rivets, sky-blue disc, crest of white tassels
  mergen      light layered archer: green felt and leather, a bandolier across the chest to a quiver on the back,
              fur-trimmed hood (leather hood-cap and face scarf T2, iron cap T3, lacquer cap with nape flap and
              finial T4, wolf-hide hood with a blued half-mask T5, sky-steel brow and swept horsehair T6), bracers
              (the 64x32 layout mirrors the arm, so the bow-arm bracer shows on both arms), soft wrapped boots,
              coat tails that grow into a long split coat
  boo         shaman: long indigo felt robe with ochre wrap edge, hanging ribbons, a cord belt with small bells,
              a mirror disc on the chest (bone T1, bronze T2-T3, bronze in silver T4, turquoise in aged gold T5,
              sky T6), a headband with an eye fringe and feathers (antler-like band T3, tall felt headdress with
              discs T4, abstract bone half-mask T5, white crown with sky discs T6), fringed cuffs and robe hem;
              no plate at any tier
  darkhan     forge warrior: soot deel under a leather smith apron, heavy gauntlets, a riveted skullcap that
              becomes a visored mask with heat slits (T3), riveted chest plates (T2) and massive pauldrons (T3),
              anvil-and-hammer emblem (T4), painted ember cracks (T5), white-hot seams on sky-iron (T6), double-
              soled boots with an iron toe cap
  khulegchin  rider: sky-blue deel wrapped to the right and edged in white, a red sash knotted on the left hip,
              a pointed cap with fur-lined ear flaps and two red cap ribbons (iron band T2, steel with long nape
              guard and pennon tube T3, horsehair ring T4, swept cheek wings T5, flat horsehair crest T6), light
              scale on the shoulders (T2) and chest (T3+), a split riding skirt, riding boots to the knee (the
              boot shaft continues on the leggings layer), a streaming mantle from T5

All motifs are ORIGINAL FICTION drawn from scratch here (the eagle disc, rank plaques, star-rivet constellations,
horn scroll, the shaman's spirit appliqué and sky gate, the smith's anvil emblem, the cap ribbons); no Soyombo, no
real tamga, no seal text, no copied ritual object. The shaman pieces are INSPIRED only and need a knowledgeable
reviewer before public use (spec §4.2). These are placeholders until the spec's 128x64 hand-painted layers exist;
the files are 64x32, the gate-0 fallback size of ARMOR_ASSET_PIPELINE §3. Output is deterministic (no randomness;
felt flecks come from a fixed hash).

    python3 tools/pack/gen_armor.py [--class baatar|mergen|boo|darkhan|khulegchin ...] [--preview DIR]
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
# ArmorTier display names T1 Эхлэл · T2 Сайжруулсан · T3 Элчин · T4 Хааны · T5 Тэнгэрлэг · T6 Дээдэс (ASCII labels)
TIER_NAME = {1: "Ekhlel", 2: "Saijruulsan", 3: "Elchin", 4: "Khaany", 5: "Tengerleg", 6: "Deedes"}
# gold budget per tier as a share of painted pixels over both layers (spec §2.1 rule 5)
GOLD_BUDGET = {1: 0.0, 2: 0.0, 3: 0.0, 4: 0.05, 5: 0.05, 6: 0.04}

Color = tuple[int, int, int, int]


def hx(s: str) -> Color:
    return int(s[1:3], 16), int(s[3:5], 16), int(s[5:7], 16), 255


# --- Баатар palettes (ARMOR_PROGRESSION_VISUAL_SPEC §3, ASSET_STYLE_GUIDE §2) ----------------------------------------
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




# --- the other four classes: shared helpers -------------------------------------------------------------------
def hsh(*vals: int) -> int:
    """Small deterministic hash (FNV-1a) for felt and leather flecks; no random module, so re-runs are byte-equal."""
    h = 2166136261
    for v in vals:
        h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    return h


def cloth(b: Box, r0: int, r1: int, ramp, faces=None, salt: int = 0, rate: int = 7) -> None:
    """Matte felt, leather or fur: mid tone with sparse light and dark flecks."""
    dk, md, lt = ramp
    for s in b.cols(faces):
        for r in range(r0, r1 + 1):
            k = hsh(s, r, salt) % rate
            b.put(s, r, lt if k == 0 else dk if k == 1 else md)


def cap(b: Box, name: str, fn) -> None:
    """Paint a top or bottom face from fn(x, y) -> colour or None (y = 0 is the back edge, y = d-1 the front)."""
    for y in range(b.d):
        for x in range(b.w):
            b.face(name, x, y, fn(x, y))


def rect(b: Box, name: str, x0: int, y0: int, x1: int, y1: int, c: Color | None) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            b.face(name, x, y, c)


def band(b: Box, r0: int, r1: int, ramp, faces=None, rivet: Color | None = None, step: int = 3,
         phase: int = 0) -> None:
    """A wrapped strap or plate band: lit top row, dark bottom row, rivets on the middle row."""
    dk, md, lt = ramp
    b.fill(r0, r1, md, faces)
    b.fill(r0, r0, lt, faces)
    if r1 > r0:
        b.fill(r1, r1, dk, faces)
    if rivet is not None:
        studs(b, (r0 + r1 + 1) // 2 if r1 - r0 >= 2 else r0, rivet, step, phase, faces)


def plate(b: Box, name: str, x0: int, y0: int, x1: int, y1: int, ramp, rivet: Color | None = None,
          edge: Color | None = None) -> None:
    """A rectangular plate on one face: lit top and left edge, dark right and bottom edge, corner rivets."""
    dk, md, lt = ramp
    rect(b, name, x0, y0, x1, y1, md)
    for x in range(x0, x1 + 1):
        b.face(name, x, y0, edge if edge is not None else lt)
        b.face(name, x, y1, dk)
    for y in range(y0 + 1, y1):
        b.face(name, x0, y, lt)
        b.face(name, x1, y, dk)
    if rivet is not None:
        for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            b.face(name, x, y, rivet)


def chain(b: Box, r0: int, r1: int, ramp, faces=None) -> None:
    """Riveted mail: a ring lattice of light ring tops, mid links and dark holes."""
    dk, md, lt = ramp
    for s in b.cols(faces):
        for r in range(r0, r1 + 1):
            b.put(s, r, lt if s % 2 == 0 and r % 2 == 0 else md if (s + r) % 2 == 0 else dk)


def scales(b: Box, r0: int, r1: int, ramp, edge: Color | None = None, faces=None) -> None:
    """Small rounded scales, 4 px wide and 2 px tall, every other row offset by half a scale."""
    dk, md, lt = ramp
    cols = set(b.cols(faces))
    for r in range(r0, r1 + 1):
        k, lr = divmod(r - r0, 2)
        off = (k % 2) * 2
        for s in range(b.sw):
            if s not in cols:
                continue
            x = (s + off) % 4
            if lr == 0:
                c = (dk, edge if edge is not None else lt, lt, md)[x]
            else:
                c = (dk, md, md, dk)[x]
            b.put(s, r, c)


def fringe(b: Box, r0: int, r1: int, colours, faces=None, step: int = 2, phase: int = 0,
           bead: Color | None = None, ragged: bool = True) -> None:
    """Hanging strands (fringe, cords, ribbons): one strand every `step` columns, alternate strands one row shorter,
    an optional bead or pendant at the tip. Columns between strands are left as they are (often transparent)."""
    for s in b.cols(faces):
        if (s + phase) % step:
            continue
        k = (s + phase) // step
        end = r1 - (1 if ragged and k % 2 else 0)
        for r in range(r0, end + 1):
            b.put(s, r, colours[k % len(colours)])
        if bead is not None:
            b.put(s, end, bead)


def cone_top(head: Box, ramp, seam: Color | None, tip: Color, rings: int = 4) -> None:
    """A pointed cap seen from above: four panels whose seams run from the corners to a light tip in the middle.
    rings < 4 paints only the inner rings (a narrower crown whose open corners keep the outline pointed)."""
    dk, md, lt = ramp
    for y in range(8):
        for x in range(8):
            ring = max(abs(2 * x - 7), abs(2 * y - 7)) // 2
            if ring >= rings:
                continue
            c = (lt, lt, md, dk)[ring] if x >= y else (md, md, md, dk)[ring]
            if seam is not None and (x == y or x == 7 - y):
                c = seam
            head.face("top", x, y, c)
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        head.face("top", x, y, tip)


def peak(hat: Box, c: Color, shade: Color, rows: int = 2) -> None:
    """The point of a cap rising above the crown line, painted on the hat shell's four sides (rows 0..rows-1)."""
    for name in ("front", "back", "right", "left"):
        for y in range(rows):
            half = y + 1
            for x in range(4 - half, 4 + half):
                hat.face(name, x, y, shade if x >= 4 else c)


def bandolier(body: Box, c: Color, shade: Color) -> None:
    """A strap over the left shoulder: across the chest to the right hip, round the right side, up the back."""
    for y in range(9):
        fx = 7 - (y * 7) // 8
        body.face("front", fx, y, c)
        body.face("front", fx - 1, y, shade)
        bx = (y * 7) // 8
        body.face("back", bx, y, c)
        body.face("back", bx + 1, y, shade)
    body.fill(8, 8, c, ("right",))
    for y in range(4):
        body.face("top", 7, y, c)
        body.face("top", 6, y, shade)


def LADDER(t: int) -> dict:
    """The material escalation every class shares (the Баатар ladder): rawhide → iron/bronze → steel/bronze →
    steel/silver (+ a little gold) → blued steel/turquoise (+ aged gold) → night-sky steel/silver-white/star rivets."""
    return {
        1: {"metal": (hx("#3A3D44"), hx("#5E6168"), hx("#8D9199")), "edge": hx("#A8865E"), "rivet": hx("#8D9199")},
        2: {"metal": (hx("#3A3D44"), hx("#5E6168"), hx("#8D9199")), "edge": hx("#C9CDD3"), "rivet": hx("#B9803F"),
            "bronze": (hx("#6E4522"), hx("#8C5A2B"), hx("#B9803F"))},
        3: {"metal": (hx("#4A4D54"), hx("#8D9199"), hx("#C9CDD3")), "edge": hx("#B9803F"), "rivet": hx("#B9803F"),
            "bronze": (hx("#6E4522"), hx("#8C5A2B"), hx("#B9803F"))},
        4: {"metal": (hx("#5E6168"), hx("#9AA0A8"), hx("#C9CDD3")), "edge": hx("#D6D9DE"), "rivet": hx("#D6D9DE"),
            "bronze": (hx("#6E4522"), hx("#8C5A2B"), hx("#B9803F")), "sil": (hx("#C9CDD3"), hx("#D6D9DE")),
            "gold": (hx("#9A7A36"), hx("#C9A04A"))},
        5: {"metal": (hx("#26304A"), hx("#3B4A66"), hx("#5C6F94")), "edge": hx("#5FBFB2"), "rivet": hx("#5FBFB2"),
            "turq": (hx("#2E8C86"), hx("#5FBFB2")), "sil": (hx("#9AA6B8"), hx("#C9CDD3")),
            "gold": (hx("#8A6A2E"), hx("#B08A3E"))},
        6: {"metal": (hx("#141C30"), hx("#1E2A44"), hx("#34507E")), "edge": hx("#E6EAF0"), "rivet": hx("#F4F1E6"),
            "star": hx("#F4F1E6"), "sil": (hx("#9AA6B8"), hx("#C9CDD3")), "white": hx("#E6EAF0"),
            "sky": (hx("#34507E"), hx("#4E7FC0")), "hair": (hx("#BDB6A6"), hx("#E8E4DA")),
            "gold": (hx("#9A7A36"), hx("#C9A04A"))},
    }[t]


def R(a: str, b: str, c: str) -> tuple[Color, Color, Color]:
    return hx(a), hx(b), hx(c)


def P(a: str, b: str) -> tuple[Color, Color]:
    return hx(a), hx(b)


LEATHER = R("#2E1C12", "#4A2E1E", "#6E4A2F")
BONE = P("#A89F88", "#D8CFB8")
WOLF = R("#4A4A4E", "#7A7A7E", "#B0AEA8")


# --- МЭРГЭН: light layered archer (greens, birch-bark ochre, leather) ------------------------------------------
_MG_CLOTH = {1: R("#2F4024", "#4E6B3A", "#6F8A52"), 2: R("#2F4024", "#4E6B3A", "#6F8A52"),
             3: R("#26361E", "#3E5530", "#5A7444"), 4: R("#22301C", "#36502E", "#4E6B3A"),
             5: R("#1E2A1A", "#2F4024", "#4A6236"), 6: R("#16241E", "#22382E", "#36543F")}
_MG_PLATE = {1: R("#4A2E1E", "#6E4A2F", "#8E6440"), 2: R("#3A2416", "#6E4A2F", "#8E6440"),
             3: R("#2E1C12", "#4A2E1E", "#6A4630"), 4: R("#1A2618", "#2C4428", "#46663A"),
             5: R("#1E2A1A", "#3A2416", "#5A3A24"), 6: R("#141C30", "#1E2A44", "#34507E")}
PAL_MERGEN = {t: {**LADDER(t), "cloth": _MG_CLOTH[t], "plate": _MG_PLATE[t], "leather": LEATHER,
                  "rawhide": hx("#A8865E"), "birch": R("#7E6A44", "#B89A5E", "#D8CFB8"), "mark": hx("#2B2A2E"),
                  "fur": R("#5A4A38", "#8A7658", "#B8AA92"), "wolf": WOLF, "lace": hx("#2E1C12"),
                  "fletch": (hx("#E8E4DA"), hx("#7A1E1E") if t < 5 else hx("#2E8C86") if t == 5 else hx("#4E7FC0")),
                  "under": R("#2E2A22", "#433C30", "#5A5040"), "belt": P("#2E1C12", "#4A2E1E"), "boot": LEATHER}
              for t in TIERS}


def mergen_helmet(t: int, p: dict, head: Box, hat: Box) -> None:
    hood = p["wolf"] if t == 5 else p["cloth"]
    h_dk, h_md, h_lt = hood
    sides = ("right", "left", "back")
    # the hood: crown, sides and back down to the jaw, framing an open face
    cap(head, "top", lambda x, y: h_lt if hsh(x, y, 3) % 6 == 0 else (h_dk if y == 0 else h_md))
    cloth(head, 0, 7, hood, sides, salt=1)
    head.fill(7, 7, h_dk, sides)
    rect(head, "front", 0, 0, 7, 1, h_md)
    for y in range(2, 8):
        head.face("front", 0, y, h_dk)
        head.face("front", 7, y, h_md)
    for y in range(8):                       # hood seam down the back
        head.face("back", 3, y, h_dk)
    f_dk, f_md, f_lt = p["fur"] if t != 5 else p["wolf"]
    if t == 1:
        # fur-trimmed felt hood: fur round the face and along the brow, laced ear flaps
        for x in range(8):
            hat.face("front", x, 1, f_lt if x % 3 == 0 else f_md)
            hat.face("front", x, 2, f_md if x % 3 != 1 else f_dk)
        for y in range(3, 7):
            hat.face("front", 0, y, f_md if y % 2 else f_lt)
            hat.face("front", 7, y, f_md if y % 2 == 0 else f_dk)
        for name in ("right", "left"):
            head.face(name, 6, 6, p["rawhide"])
            head.face(name, 6, 7, p["rawhide"])
        return
    # T2+: a face scarf over the mouth (left open at T5, where the half-mask sits)
    s_dk, s_md, s_lt = p["cloth"] if t != 6 else p["metal"]
    if t != 5:
        for x in range(1, 7):
            head.face("front", x, 5, s_lt if x % 2 else s_md)
            head.face("front", x, 6, s_md)
            head.face("front", x, 7, s_dk if x % 3 else s_md)
    if t == 2:
        # leather hood-cap over the hood, stitched, with a bronze buckle at each side
        l_dk, l_md, l_lt = p["leather"]
        cap(head, "top", lambda x, y: l_lt if (x + y) % 7 == 0 else (l_dk if x in (0, 7) or y in (0, 7) else l_md))
        band(head, 0, 1, (l_dk, l_md, l_lt))
        studs(head, 1, p["rawhide"], 2)
        for x in range(8):
            hat.face("front", x, 2, f_md if x % 2 else f_lt)
        for name in ("right", "left"):
            head.face(name, 3, 1, p["bronze"][2])
        return
    if t == 3:
        # light iron cap with bronze rivets over the hood
        helmet_bowl(head, p["metal"], p["metal"][0], p["metal"][2], rows=2)
        for s in range(hat.sw):
            hat.put(s, 2, p["bronze"][2] if s % 3 == 0 else p["bronze"][1])
        for name in ("right", "left"):
            head.face(name, 6, 6, p["bronze"][2])
        return
    if t == 4:
        # dark-green lacquer cap, silver seams, lacquer nape flap, small silver finial with a gold bead
        helmet_bowl(head, p["plate"], p["sil"][1], p["sil"][1], rows=2)
        lamellae(head, 3, 7, p["plate"], pw=2, rh=2, edge=p["edge"], faces=("back",))
        for s in range(hat.sw):
            hat.put(s, 2, p["sil"][1] if s % 4 == 0 else p["plate"][1])
        for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
            hat.face("top", x, y, p["sil"][1])
        hat.face("top", 3, 3, p["gold"][1])
        hat.face("front", 3, 2, p["gold"][1])
        return
    if t == 5:
        # wolf-hide hood, blued-steel half mask with turquoise studs and breathing slits
        m_dk, m_md, m_lt = p["metal"]
        for y in range(4, 8):
            for x in range(1, 7):
                head.face("front", x, y, m_lt if y == 4 else (m_dk if (y == 6 and x in (2, 3, 4, 5) and x % 2)
                                                             else m_md))
        head.face("front", 1, 4, p["turq"][1])
        head.face("front", 6, 4, p["turq"][1])
        head.face("front", 3, 7, p["gold"][1])
        for x in range(8):
            hat.face("front", x, 1, f_lt if x % 2 else f_md)
        for y in range(2, 7):
            hat.face("front", 0, y, f_lt if y % 2 else f_md)
            hat.face("front", 7, y, f_md)
        for s in range(hat.sw):
            if s % 8 in (2, 5):
                hat.put(s, 0, f_lt)
        return
    # t == 6: pine-night hood, sky-steel brow with star rivets, white horsehair tassels swept back
    band(head, 0, 2, p["metal"])
    head.fill(2, 2, p["sil"][1])
    studs(head, 1, p["star"], 4, 1)
    hd, hl = p["hair"]
    for y in range(8):
        for x in (1, 3, 5) if y < 4 else (0, 2, 4, 6):
            hat.face("back", x, y, hl if (x + y) % 2 else hd)
    for y in range(0, 4):
        hat.face("top", 3, y, hl)
        hat.face("top", 4, y, hd)
    hat.face("front", 3, 2, p["sky"][1])
    hat.face("front", 4, 2, p["gold"][1])


def quiver(body: Box, p: dict, t: int) -> None:
    """A quiver on the back (birch bark, later lacquer, bone or night steel) with fletchings over the shoulder."""
    b_dk, b_md, b_lt = p["birch"] if t <= 3 else (p["plate"] if t != 5 else (BONE[0], BONE[1], BONE[1]))
    for y in range(2, 10):
        body.face("back", 4, y, b_lt)
        body.face("back", 5, y, b_md if y % 3 else p["mark"])
        body.face("back", 6, y, b_dk)
    ring = p["rawhide"] if t == 1 else p.get("bronze", (0, 0, p["edge"]))[2] if t <= 3 else p["edge"]
    for y in (3, 8):
        for x in (4, 5, 6):
            body.face("back", x, y, ring)
    fw, fc = p["fletch"]
    for x, c in ((4, fw), (5, fc), (6, fw)):
        body.face("back", x, 0, c)
        body.face("back", x, 1, fc if c == fw else fw)
        body.face("top", x, 0, c)
    if t >= 4:
        body.face("back", 5, 5, p["gold"][1] if t in (4, 6) else p["turq"][1])


def mergen_chest(t: int, p: dict, body: Box, arm: Box) -> None:
    c_dk, c_md, c_lt = p["cloth"]
    pl = p["plate"]
    cloth(body, 0, 8, p["cloth"], salt=11)
    if t == 1:
        deel_flap(body, 0, p["rawhide"], c_dk)
    elif t == 2:
        lamellae(body, 1, 8, pl, pw=4, rh=3)
        body.fill(0, 0, c_lt)
    elif t == 3:
        lamellae(body, 0, 4, pl, pw=2, rh=2)
        body.fill(0, 0, p["bronze"][1])
        band(body, 5, 6, p["leather"])
        cloth(body, 7, 8, p["cloth"], salt=12)
        studs(body, 6, p["rivet"], 3)
    elif t == 4:
        lamellae(body, 0, 8, pl, pw=2, rh=2)
        body.fill(0, 0, p["sil"][0])
        for s in body.cols():               # silver inlay dots through two rows of lames
            if s % 4 == 1:
                body.put(s, 3, p["sil"][1])
                body.put(s + 2, 7, p["sil"][1])
    elif t == 5:
        lamellae(body, 2, 8, pl, pw=2, rh=2, edge=p["turq"][1], edge_period=2)
        cloth(body, 0, 1, p["wolf"], salt=5)          # wolf-hide mantle across the shoulders
        for s in body.cols():
            if s % 3 == 0:
                body.put(s, 2, p["wolf"][2])
    else:
        # night-blue lames alternating with pine-night felt layers, silver every second row, star rivets
        lamellae(body, 0, 8, pl, pw=2, rh=2, alt=p["cloth"])
        body.fill(0, 0, p["sil"][1])
        body.fill(4, 4, p["sil"][0])
        for s, r in ((1, 3), (6, 1), (10, 6), (13, 2), (21, 5)):
            body.put(s, r, p["star"])
    # the bandolier strap and the quiver
    strap = (p["rawhide"], p["leather"][1]) if t in (1, 2) else (p["birch"][1], p["leather"][0]) if t == 3 else \
        (p["plate"][2], p["plate"][0]) if t == 4 else (p["sil"][0], p["metal"][0]) if t == 6 else \
        (p["leather"][2], p["leather"][0])
    body.fill_cap("top", c_md)
    for x in range(8):
        body.face("top", x, 3, c_lt if x % 2 else c_md)
    bandolier(body, *strap)
    if t >= 2:
        buckle = p["bronze"][2] if t <= 3 else p["gold"][1] if t in (4, 5) else p["sky"][1]
        body.face("front", 4, 3, buckle)
        body.face("front", 3, 4, buckle if t != 6 else p["star"])
    quiver(body, p, t)
    # belt and pouch
    belt(body, {"belt": p["belt"], "stud": p["rivet"]}, stud_step=4)
    pouch = p["leather"] if t < 4 else (pl if t != 5 else p["leather"])
    for y in (9, 10, 11):
        body.face("front", 5, y, pouch[2] if y == 9 else pouch[1])
        body.face("front", 6, y, pouch[1] if y == 9 else pouch[0])
    body.face("front", 5, 10, p["rivet"])
    # arms: narrow sleeves, a shoulder piece per tier, the bow-arm bracer (the layout mirrors it onto both arms)
    cloth(arm, 0, 11, p["cloth"], salt=13)
    arm.fill_cap("top", c_md)
    if t == 1:
        studs(arm, 4, p["rawhide"], 2)
    elif t == 2:
        band(arm, 0, 3, p["leather"], rivet=p["rawhide"], step=2)
    elif t == 3:
        scales(arm, 0, 4, p["metal"], edge=p["edge"])
        arm.fill_cap("top", p["metal"][1])
    elif t == 4:
        lamellae(arm, 0, 4, pl, pw=2, rh=2)
        arm.fill(4, 4, p["sil"][1])
        arm.fill_cap("top", pl[1])
    elif t == 5:
        cloth(arm, 0, 3, p["wolf"], salt=7)
        studs(arm, 3, p["wolf"][2], 2, 1)
        arm.fill_cap("top", p["wolf"][1])
    else:
        lamellae(arm, 0, 3, pl, pw=2, rh=2, alt=p["cloth"])
        arm.fill(0, 0, p["sil"][1])
        hd, hl = p["hair"]
        fringe(arm, 4, 6, (hl, hd), step=2, ragged=True)
        arm.fill_cap("top", pl[1])
    br = {1: p["leather"], 2: p["leather"], 3: p["birch"], 4: pl, 5: p["metal"], 6: p["metal"]}[t]
    band(arm, 7, 10, br)
    if t == 1:
        studs(arm, 8, p["rawhide"], 2, 1, ("front", "right", "back"))
        studs(arm, 9, p["rawhide"], 2, 0, ("front", "right", "back"))
    elif t == 2:
        studs(arm, 8, p["rivet"], 2, 1)
        studs(arm, 9, p["metal"][2], 2)
    elif t == 3:
        arm.fill(7, 7, p["bronze"][2])
        arm.fill(10, 10, p["bronze"][1])
        studs(arm, 8, p["mark"], 3)
    elif t == 4:
        for s in arm.cols():
            arm.put(s, 8 + (s % 2), p["sil"][1])
    elif t == 5:
        studs(arm, 8, p["turq"][1], 2)
        studs(arm, 9, p["turq"][0], 2, 1)
    else:
        arm.fill(7, 7, p["sil"][1])
        studs(arm, 9, p["star"], 4, 1)
        arm.fill(10, 10, p["sil"][0])
    arm.fill(11, 11, p["leather"][1])


def mergen_boots(t: int, p: dict, leg: Box) -> None:
    dk, md, lt = p["boot"] if t != 6 else p["metal"]
    leg.fill(7, 10, md)
    # soft boots: wrapped, a little crumpled, no hard toe; ankle wrap and front lacing
    for s in leg.cols():
        if (s + 7) % 4 == 0:
            leg.put(s, 9, dk)
    leg.fill(10, 10, dk, ("right", "left", "back"))
    for y in (8, 9):
        leg.face("front", 1, y, p["rawhide"] if (y + t) % 2 else md)
        leg.face("front", 2, y, md if (y + t) % 2 else p["rawhide"])
    cuff = {1: p["cloth"], 2: p["cloth"], 3: p["birch"], 4: p["plate"], 5: p["wolf"], 6: p["metal"]}[t]
    leg.fill(7, 7, cuff[2] if t != 4 else cuff[1])
    if t == 2:
        studs(leg, 7, p["rivet"], 3)
    elif t == 3:
        studs(leg, 7, p["bronze"][1], 2)
    elif t == 4:
        studs(leg, 7, p["sil"][1], 2)
    elif t == 5:
        studs(leg, 7, p["wolf"][1], 2, 1)
        leg.face("front", 0, 8, p["turq"][1])
        leg.face("front", 3, 8, p["turq"][1])
    elif t == 6:
        leg.fill(7, 7, p["sil"][1])
        leg.face("front", 0, 8, p["star"])
        leg.face("front", 3, 9, p["sky"][1])
    leg.fill(11, 11, LEATHER[0])
    leg.face("front", 1, 11, lt)
    leg.face("front", 2, 11, md)
    leg.fill_cap("bottom", LEATHER[0])


def mergen_waist(t: int, p: dict, body: Box) -> None:
    cloth(body, 8, 11, p["cloth"], salt=17)
    body.fill(8, 8, p["cloth"][2])
    body.fill(11, 11, p["leather"][1])
    body.fill_cap("bottom", p["under"][0])


def mergen_leggings(t: int, p: dict, leg: Box) -> None:
    u_dk, u_md, u_lt = p["under"]
    leg.fill(0, 11, u_md)
    leg.fill(0, 11, u_dk, ("left",))
    leg.fill(11, 11, u_dk)
    leg.fill_cap("bottom", u_dk)
    leg.fill_cap("top", u_md)
    c_dk, c_md, c_lt = p["cloth"]
    outer = ("right", "front", "back")
    tails = ("front", "back")
    for x in (1, 2):                           # leather knee patches
        leg.face("front", x, 6, p["leather"][1])
        leg.face("front", x, 7, p["leather"][0])
    if t == 1:
        cloth(leg, 0, 3, p["cloth"], outer, salt=19)
        studs(leg, 3, p["rawhide"], 2, 0, outer)
    elif t == 2:
        cloth(leg, 0, 1, p["cloth"], outer, salt=19)
        band(leg, 2, 4, p["leather"], outer, rivet=p["rivet"], step=2)
    elif t == 3:
        lamellae(leg, 0, 2, p["plate"], pw=2, rh=3, edge=p["edge"], faces=outer)
        cloth(leg, 3, 5, p["cloth"], outer, salt=19)
        leg.fill(5, 5, p["leather"][1], outer)
    elif t == 4:
        # long split coat tails, front and back only (the sides open over the trousers)
        lamellae(leg, 0, 2, p["plate"], pw=2, rh=3, faces=tails)
        cloth(leg, 3, 8, p["cloth"], tails, salt=19)
        leg.fill(8, 8, p["sil"][1], tails)
        leg.face("front", 1, 8, p["gold"][1])
    elif t == 5:
        cloth(leg, 0, 8, p["plate"], tails, salt=23)
        leg.fill(0, 0, p["wolf"][2], tails)
        leg.fill(8, 8, p["turq"][1], tails)
        plate(leg, "front", 0, 5, 3, 6, p["metal"], rivet=p["turq"][1])
    else:
        lamellae(leg, 0, 2, p["plate"], pw=2, rh=3, faces=tails)
        cloth(leg, 3, 8, p["cloth"], tails, salt=19)
        leg.fill(7, 7, p["sil"][0], tails)
        leg.fill(8, 8, p["white"], tails)
        plate(leg, "front", 0, 5, 3, 6, p["metal"], rivet=p["star"])
        leg.put(13, 4, p["star"])


# --- БӨӨ: shaman (indigo felt, ochre and bone, ribbons and fringe; no plate) -----------------------------------
_BO_CLOTH = {1: R("#1E1A36", "#2E2A52", "#45407A"), 2: R("#1C1838", "#2C2856", "#423E7C"),
             3: R("#1A1634", "#2A2552", "#3E3874"), 4: R("#181434", "#28224E", "#3C3470"),
             5: R("#141230", "#221E48", "#363068"), 6: R("#9AA6B8", "#D2D8E2", "#F4F1E6")}
PAL_BOO = {t: {**LADDER(t), "cloth": _BO_CLOTH[t], "ochre": P("#8A6A3A", "#B89A5E"), "bone": BONE,
               "purple": P("#4A3560", "#6A4E88"), "red": hx("#8E3A2A"), "blue": hx("#3B4A86"),
               "trim": P("#8A6A3A", "#B89A5E") if t < 6 else (hx("#34507E"), hx("#4E7FC0")),
               "under": R("#141226", "#1E1A36", "#2E2A52") if t < 6 else R("#1E2A44", "#34507E", "#4E7FC0"),
               "boot": R("#1E1A2A", "#3A3048", "#5A4E66") if t < 6 else R("#9AA6B8", "#C9CDD3", "#E6EAF0")}
           for t in TIERS}


def boo_ribbons(p: dict, t: int) -> tuple:
    o_dk, o_lt = p["ochre"]
    b_dk, b_lt = p["bone"]
    if t == 6:
        return p["sky"][1], p["white"], p["sil"][0], p["sky"][0]
    if t == 5:
        return o_lt, p["turq"][1], b_lt, p["red"], p["turq"][0]
    if t >= 2:
        return o_lt, p["red"], b_lt, p["blue"]
    return o_lt, p["red"], b_lt


def boo_helmet(t: int, p: dict, head: Box, hat: Box) -> None:
    o_dk, o_lt = p["ochre"]
    b_dk, b_lt = p["bone"]
    c_dk, c_md, c_lt = p["cloth"]
    rib = boo_ribbons(p, t)
    cord = c_dk if t < 6 else p["sky"][0]
    bead = bead_for(p, t)
    # the headband on the hat shell: stepped ochre and bone, taller every tier
    top = {1: 2, 2: 1, 3: 2, 4: 0, 5: 0, 6: 0}[t]
    for s in range(hat.sw):
        for r in range(top, 4):
            k = (s + r) % 4
            if t == 6:
                c = p["white"] if k else p["sil"][1]
            elif t >= 4 and r < 2:
                c = c_md if k else c_lt
            else:
                c = o_lt if k in (0, 1) else (b_lt if k == 2 else o_dk)
            hat.put(s, r, c)
        hat.put(s, 3, o_dk if t < 6 else p["sky"][0])
    # eye fringe over the face, longer every tier; fringe and cords round the sides and back
    fr_end = {1: 5, 2: 7, 3: 7, 4: 7, 5: 7, 6: 7}[t]
    fringe(head, 3 if t != 5 else 7, fr_end, (cord, rib[0]) if t < 6 else (p["white"], p["sky"][1]),
           ("front",), step=2, bead=bead)
    fringe(head, 3, 7, rib, ("right", "left", "back"), step=2, phase=1, bead=bead if t >= 2 else None)
    if t >= 2:
        # felt crown ring under the band (T2), a closed felt crown (T3+)
        cap(head, "top", lambda x, y: c_md if (t >= 3 or x in (0, 7) or y in (0, 7)) else None)
        head.fill(0, 2, c_md)
        head.fill(2, 2, c_dk)
    if t == 1:
        # a few short feathers standing in the band
        for name in ("front", "right", "left", "back"):
            for x in (1, 4, 6):
                hat.face(name, x, 1, b_lt)
                hat.face(name, x, 0, b_dk if x == 4 else None)
        return
    if t == 2:
        for name in ("front", "right", "left", "back"):
            for x in (0, 2, 5, 7):
                hat.face(name, x, 0, b_lt)
        studs(hat, 3, p["metal"][2], 2)
        return
    if t == 3:
        # antler-like headdress (INSPIRED, unverified): two branching bone beams over the crown
        for name in ("front", "back"):
            for x, y in ((0, 0), (2, 0), (0, 1), (1, 1), (2, 1), (1, 2), (5, 0), (7, 0), (5, 1), (6, 1), (7, 1),
                         (6, 2)):
                hat.face(name, x, y, b_lt if y < 2 else b_dk)
        for name in ("right", "left"):
            for x, y in ((1, 0), (1, 1), (2, 1), (5, 0), (5, 1), (6, 1)):
                hat.face(name, x, y, b_lt if y == 0 else b_dk)
        for i in range(8):
            hat.face("top", 1, i, b_dk if i % 3 else b_lt)
            hat.face("top", 6, i, b_dk if i % 3 else b_lt)
        for x, y in ((0, 2), (7, 2), (0, 5), (7, 5), (2, 0), (5, 0)):
            hat.face("top", x, y, b_lt)
        studs(hat, 3, b_lt, 3)
        return
    if t == 4:
        # tall felt headdress with bronze discs and silver pendants, feather tips round the crown
        for name in ("front", "back", "right", "left"):
            for x0 in (1, 5):
                rect(hat, name, x0, 1, x0 + 1, 2, p["bronze"][1])
                hat.face(name, x0, 1, p["bronze"][2])
                hat.face(name, x0 + 1, 2, p["bronze"][0])
            for x in (0, 3, 4, 7):
                hat.face(name, x, 0, b_lt)
        hat.face("front", 3, 2, p["gold"][1])
        cap(hat, "top", lambda x, y: (b_lt if (x + y) % 2 else o_lt) if (x in (0, 7) or y in (0, 7)) else None)
        studs(head, 6, p["sil"][1], 4, 1, ("right", "left", "back"))
        return
    if t == 5:
        # masked headdress: an abstract bone half-face with dark eye openings, turquoise brow, fringe below
        for y in range(3, 7):
            for x in range(1, 7):
                head.face("front", x, y, b_lt if y < 5 else b_dk)
        for x, y in ((2, 4), (5, 4)):
            head.face("front", x, y, c_dk)
        for x in (2, 3, 4, 5):
            head.face("front", x, 6, o_dk if x % 2 else b_dk)
        for x in range(1, 7):
            head.face("front", x, 3, p["turq"][1] if x in (1, 6) else p["turq"][0])
        for name in ("front", "back", "right", "left"):
            for x0 in (1, 5):
                rect(hat, name, x0, 1, x0 + 1, 2, p["turq"][0])
                hat.face(name, x0, 1, p["turq"][1])
            for x in (0, 3, 4, 7):
                hat.face(name, x, 0, b_lt)
        hat.face("front", 3, 2, p["gold"][1])
        hat.face("front", 4, 2, p["gold"][0])
        return
    # t == 6: crown headdress, white band with crenel points, sky discs rimmed in silver, star rivets
    for name in ("front", "back", "right", "left"):
        for x in range(8):
            hat.face(name, x, 0, p["white"] if x % 2 == 0 else None)
        for x0 in (1, 5):
            rect(hat, name, x0, 1, x0 + 1, 2, p["sky"][1])
            hat.face(name, x0, 1, p["sil"][1])
        hat.face(name, 3, 2, p["star"])
    hat.face("front", 4, 2, p["gold"][1])
    cap(head, "top", lambda x, y: p["white"] if (x + y) % 3 else p["sil"][1])


def boo_chest(t: int, p: dict, body: Box, arm: Box) -> None:
    c_dk, c_md, c_lt = p["cloth"]
    o_dk, o_lt = p["ochre"]
    b_dk, b_lt = p["bone"]
    tr_dk, tr_lt = p["trim"]
    rib = boo_ribbons(p, t)
    # long felt robe over the whole torso, ochre (T6: sky) wrap edge and collar
    cloth(body, 0, 11, p["cloth"], salt=29)
    deel_flap(body, 0, tr_lt, None)
    body.fill(0, 0, tr_dk)
    body.fill_cap("top", c_md)
    for x in range(8):
        body.face("top", x, 3, tr_lt if x % 2 else tr_dk)
    # cord belt with a knot, small bells from T2
    body.fill(9, 9, tr_lt)
    body.face("front", 3, 9, tr_dk)
    body.face("front", 3, 10, tr_lt)
    body.face("front", 3, 11, tr_dk)
    # hanging ribbons: a few at T1, rows of them from T2
    xs = (1, 6) if t == 1 else (0, 7)
    for i, x in enumerate(xs):
        for y in range(3, 9 if t == 1 else 12):
            body.face("front", x, y, rib[i % len(rib)])
    for i, x in enumerate(xs):
        for y in range(1, 12):
            body.face("back", x, y, rib[(i + 1) % len(rib)])
    for s in (1, 13):
        for y in range(2, 12):
            body.put(s, y, rib[(s // 4) % len(rib)])
    if t >= 2:
        bell = p["bronze"] if t < 5 else (p["gold"][0], p["gold"][1], p["gold"][1]) if t == 5 else \
            (p["sil"][0], p["sil"][1], p["white"])
        for x in (1, 4, 6):
            body.face("front", x, 10, bell[2] if t < 5 else bell[1])
            body.face("front", x, 11, bell[0])
        for x in (1, 6):
            body.face("back", x + (1 if x == 1 else -2), 10, bell[2] if t < 5 else bell[1])
    if t == 1:
        # a small bone disc on a cord
        for x, y, c in ((3, 3, b_lt), (4, 3, b_dk), (3, 4, b_dk), (4, 4, b_lt)):
            body.face("front", x, y, c)
        body.face("front", 3, 2, o_dk)
        body.face("front", 4, 2, o_dk)
    elif t == 2:
        # a bronze mirror disc and iron pendants on a cord across the chest
        for x in range(1, 7):
            body.face("front", x, 2, o_lt)
        for x in (1, 6):
            body.face("front", x, 3, p["metal"][2])
            body.face("front", x, 4, p["metal"][1])
        disc(body, 4, 5, p["bronze"][0], p["bronze"][1], centre=None, size=4)
        body.face("front", 3, 4, p["bronze"][2])
    elif t == 3:
        # bone bead chains, a bronze mirror, a talisman cluster on the back
        for y in (2, 7):
            for x in range(8):
                body.face("front", x, y, b_lt if x % 2 else b_dk)
        disc(body, 4, 5, p["bronze"][0], p["bronze"][1], centre=None, size=4)
        body.face("front", 3, 4, p["bronze"][2])
        for x, y, c in ((3, 2, b_lt), (4, 2, b_lt), (2, 3, p["bronze"][2]), (5, 3, b_dk), (3, 4, o_lt),
                        (4, 4, p["metal"][1]), (3, 5, b_lt), (4, 5, b_dk), (2, 6, o_dk), (5, 6, p["bronze"][1]),
                        (3, 7, b_lt), (4, 7, p["metal"][2])):
            body.face("back", x, y, c)
    elif t == 4:
        # bronze discs rimmed in silver on the chest and back, purple felt panels, a gold centre bead
        for x in range(8):
            body.face("front", x, 2, p["purple"][1] if x % 2 else p["purple"][0])
        disc(body, 4, 5, p["sil"][1], p["bronze"][1], centre=None, size=6)
        for x, y in ((3, 4), (4, 4), (3, 5), (4, 5)):
            body.face("front", x, y, p["bronze"][2] if (x + y) % 2 else p["bronze"][0])
        body.face("front", 3, 4, p["gold"][1])
        for x in range(2, 6):
            for y in range(3, 7):
                corner = x in (2, 5) and y in (3, 6)
                body.face("back", x, y, None if corner else (p["sil"][1] if x in (2, 5) or y in (3, 6)
                                                              else p["bronze"][1]))
    elif t == 5:
        # spirit appliqué (ORIGINAL FICTION): two abstract figures, a turquoise disc in an aged-gold rim
        for x0 in (0, 5):
            for x, y, c in ((x0 + 1, 3, b_lt), (x0, 4, o_lt), (x0 + 1, 4, b_lt), (x0 + 2, 4, o_lt),
                            (x0 + 1, 5, b_lt), (x0, 6, b_dk), (x0 + 2, 6, b_dk)):
                body.face("front", x, y, c)
        disc(body, 4, 5, p["gold"][0], p["turq"][0], centre=None, size=4)
        body.face("front", 3, 4, p["turq"][1])
        for x, y, c in ((3, 2, b_lt), (4, 2, b_lt), (2, 3, o_lt), (3, 3, b_lt), (4, 3, b_lt), (5, 3, o_lt),
                        (3, 4, b_lt), (4, 4, b_lt), (2, 5, b_dk), (5, 5, b_dk), (3, 6, p["turq"][1]),
                        (4, 6, p["turq"][1])):
            body.face("back", x, y, c)
    else:
        # white and sky ritual coat: a sky-gate motif on the back (ORIGINAL FICTION), a sky disc, star rivets
        disc(body, 4, 5, p["sil"][1], p["sky"][1], centre=None, size=6)
        for x, y in ((3, 4), (4, 4), (3, 5), (4, 5)):
            body.face("front", x, y, p["sky"][0])
        body.face("front", 3, 4, p["star"])
        body.face("front", 4, 5, p["gold"][1])
        for y in range(2, 9):
            body.face("back", 2, y, p["sky"][1])
            body.face("back", 5, y, p["sky"][1])
        for x in range(1, 7):
            body.face("back", x, 2, p["sky"][0])
        for x in (2, 3, 4, 5):
            body.face("back", x, 1, p["sil"][1] if x in (3, 4) else None)
        for s, r in ((1, 4), (9, 2), (19, 3), (22, 6), (14, 7)):
            body.put(s, r, p["star"])
    # wide sleeves, ribbon stripe down the sleeve, ochre cuff, fringe over the hands
    cloth(arm, 0, 9, p["cloth"], salt=31)
    arm.fill_cap("top", c_md)
    arm.fill(9, 9, tr_lt)
    arm.fill(8, 8, tr_dk)
    if t >= 2:
        for r in range(1, 8):                 # one ribbon down the outer side of the sleeve
            arm.put(1, r, rib[1])
    if t == 3:
        studs(arm, 4, b_lt, 2)
    elif t == 4:
        for x in (1, 2):
            arm.face("front", x, 2, p["bronze"][2] if x == 1 else p["bronze"][1])
            arm.face("front", x, 3, p["bronze"][1] if x == 1 else p["bronze"][0])
        studs(arm, 4, p["sil"][1], 2)
    elif t == 5:
        studs(arm, 4, p["turq"][1], 2)
        studs(arm, 3, b_lt, 4, 2)
    elif t == 6:
        arm.fill(4, 4, p["sky"][1])
        studs(arm, 4, p["star"], 4, 1)
    fringe(arm, 10, 11, rib, step=2, ragged=True, bead=bead_for(p, t))


def bead_for(p: dict, t: int) -> Color:
    """The bead or pendant at a fringe tip: bone, iron, bone, bronze, turquoise, silver."""
    if t in (1, 3):
        return p["bone"][1]
    if t == 2:
        return p["metal"][2]
    if t == 4:
        return p["bronze"][2]
    return p["turq"][1] if t == 5 else p["sil"][1]


def boo_boots(t: int, p: dict, leg: Box) -> None:
    dk, md, lt = p["boot"]
    cloth(leg, 7, 10, p["boot"], salt=37)
    tr_dk, tr_lt = p["trim"]
    leg.fill(7, 7, tr_lt)
    for s in leg.cols():
        leg.put(s, 8, tr_dk if s % 2 else tr_lt)
    if t >= 2:
        studs(leg, 7, bead_for(p, t), 2, 1)
    if t >= 4:
        for x in (0, 3):
            leg.face("front", x, 9, p["bronze"][2] if t == 4 else p["turq"][1] if t == 5 else p["star"])
    sole(leg, (LEATHER[0], md, lt), tr_lt)


def boo_waist(t: int, p: dict, body: Box) -> None:
    cloth(body, 8, 11, p["cloth"], salt=41)
    body.fill(9, 9, p["trim"][1])
    body.fill_cap("bottom", p["under"][0])


def boo_leggings(t: int, p: dict, leg: Box) -> None:
    u_dk, u_md, u_lt = p["under"]
    leg.fill(0, 11, u_md)
    leg.fill(11, 11, u_dk)
    leg.fill_cap("bottom", u_dk)
    leg.fill_cap("top", p["cloth"][1])
    tr_dk, tr_lt = p["trim"]
    rib = boo_ribbons(p, t)
    # the robe skirt all round to the knee, an ochre hem and the fringe below it
    cloth(leg, 0, 4, p["cloth"], salt=43)
    leg.fill(4, 4, tr_lt)
    fringe(leg, 5, 7, rib, step=2, phase=t % 2, bead=bead_for(p, t))
    if t >= 2:
        for s in (1, 13):
            for r in range(0, 4):
                leg.put(s, r, rib[(s // 4) % len(rib)])
    if t >= 3:
        # a second, layered fringe skirt over the robe
        fringe(leg, 1, 3, (p["bone"][1], p["ochre"][1]) if t < 6 else (p["white"], p["sky"][1]), step=2, phase=0)
        leg.fill(0, 0, tr_dk)
    if t >= 4:
        studs(leg, 4, p["bronze"][2] if t == 4 else p["turq"][1] if t == 5 else p["star"], 2, 1)
    if t == 6:
        leg.put(6, 2, p["star"])


# --- ДАРХАН: forge warrior (soot, iron, leather apron, painted ember) ------------------------------------------
_DK_PLATE = {1: R("#3A3D44", "#5E6168", "#8D9199"), 2: R("#3A3D44", "#5E6168", "#8D9199"),
             3: R("#4A4D54", "#6E7178", "#9AA0A8"), 4: R("#3A3D44", "#70747C", "#A4A9B0"),
             5: R("#1E2230", "#2E3448", "#4A5470"), 6: R("#0E1220", "#1A2034", "#2E3A58")}
PAL_DARKHAN = {t: {**LADDER(t), "cloth": R("#1B1A1F", "#2B2A2E", "#3E3C40"), "plate": _DK_PLATE[t],
                   "apron": R("#3A2416", "#5A3A24", "#7A5236") if t < 5 else R("#24180F", "#3A2416", "#5A3A24"),
                   "glove": R("#24180F", "#3E2A1A", "#5E4028"), "scorch": hx("#24180F"),
                   "ember": R("#A8401E", "#E06A2A", "#F2A040"), "hot": P("#F2C080", "#F4F1E6"),
                   "brz": R("#6E4522", "#8C5A2B", "#B9803F"), "boot": R("#1B1410", "#2E2018", "#4A3424"),
                   "under": R("#141316", "#1B1A1F", "#2B2A2E"), "belt": P("#1B1410", "#3A2416"),
                   # forge rivets stay dull at the top tiers (turquoise and stars only as accents)
                   **({4: {"rivet": hx("#B9803F")}, 5: {"rivet": hx("#7A86A8")}, 6: {"rivet": hx("#9AA6B8")}}.get(t, {}))}
               for t in TIERS}


def seam_glow(p: dict, t: int) -> Color | None:
    """Painted heat along the plate seams: ember at T5, white-hot at T6 (no emissive layer exists)."""
    return p["ember"][1] if t == 5 else p["hot"][0] if t == 6 else None


def darkhan_helmet(t: int, p: dict, head: Box, hat: Box) -> None:
    pl = p["plate"]
    if t == 1:
        # riveted leather skullcap with a rolled soot brim
        a_dk, a_md, a_lt = p["apron"]
        cap(head, "top", lambda x, y: a_lt if (x in (3, 4) or y in (3, 4)) else a_md)
        band(head, 0, 2, p["apron"])
        studs(head, 1, p["rivet"], 2)
        for s in range(hat.sw):
            hat.put(s, 2, p["cloth"][2] if s % 3 else p["cloth"][1])
        return
    # T2+: an iron (later steel, blued, sky-iron) skullcap with riveted bands, chain guard at sides and back
    helmet_bowl(head, pl, pl[0], p["rivet"], rows=3)
    for i in range(1, 7):
        head.face("top", i, 3 if i != 3 else 3, pl[2] if i % 2 else pl[1])
        head.face("top", 3, i, pl[2])
    studs(head, 2, p["rivet"], 2)
    chain(head, 3, 6, p["metal"] if t < 5 else p["plate"], ("right", "left", "back"))
    head.fill(7, 7, p["cloth"][0], ("right", "left", "back"))
    for s in range(hat.sw):
        hat.put(s, 2, pl[2])
        hat.put(s, 3, p["rivet"] if s % 3 == 1 else pl[1])
    if t == 2:
        # soot guard: a leather flap over the chain at the back
        for y in range(3, 7):
            for x in range(1, 7):
                head.face("back", x, y, p["cloth"][1] if (x + y) % 3 else p["cloth"][2])
        return
    # T3+: a visored mask with heat slits
    glow = p["ember"][1] if t <= 4 else p["ember"][2] if t == 5 else p["hot"][1]
    for y in range(3, 8):
        for x in range(8):
            head.face("front", x, y, pl[2] if x == 0 or y == 3 else pl[0] if x == 7 or y == 7 else pl[1])
    for x in (1, 2, 5, 6):
        head.face("front", x, 4, pl[0])
    for x in (2, 5):
        head.face("front", x, 4, glow if t >= 5 else p["ember"][0])
    for x in (2, 3, 4, 5):
        head.face("front", x, 6, pl[0] if x % 2 else glow if t >= 5 else pl[0])
    for x, y in ((0, 3), (7, 3), (0, 7), (7, 7), (3, 7), (4, 7)):
        head.face("front", x, y, p["rivet"])
    if t == 4:
        # crest ridge: bronze from brow to nape, gold at its front
        for y in range(8):
            hat.face("top", 3, y, p["brz"][2])
            hat.face("top", 4, y, p["brz"][1])
        for y in range(0, 3):
            hat.face("front", 3, y, p["brz"][2])
            hat.face("front", 4, y, p["brz"][1])
            hat.face("back", 3, y, p["brz"][2])
            hat.face("back", 4, y, p["brz"][1])
        hat.face("front", 3, 1, p["gold"][1])
        hat.face("front", 4, 1, p["gold"][0])
        return
    if t == 5:
        # molten cracks across the mask, aged-gold rivets, turquoise brow bead
        for x, y in ((1, 5), (6, 6)):
            head.face("front", x, y, p["ember"][1])
        head.face("front", 3, 3, p["turq"][1])
        head.face("front", 4, 3, p["gold"][1])
        hat.face("front", 3, 3, p["gold"][1])
        return
    if t == 6:
        # crowned mask: crenel points of silver with star rivets, a sky stone at the brow, white-hot cracks
        for name in ("front", "back", "right", "left"):
            for x in range(8):
                hat.face(name, x, 0, p["sil"][1] if x % 3 == 0 else None)
                hat.face(name, x, 1, p["sil"][0] if x % 3 != 2 else p["sil"][1])
            hat.face(name, 4, 1, p["star"])
        hat.face("front", 3, 2, p["sky"][1])
        hat.face("front", 4, 2, p["gold"][1])
        for x, y in ((1, 5), (6, 6), (6, 5)):
            head.face("front", x, y, p["hot"][0])


def darkhan_chest(t: int, p: dict, body: Box, arm: Box) -> None:
    c_dk, c_md, c_lt = p["cloth"]
    pl = p["plate"]
    a_dk, a_md, a_lt = p["apron"]
    glow = seam_glow(p, t)
    cloth(body, 0, 11, p["cloth"], salt=47)
    # chest plates under the apron (T2+): riveted plates on the back and sides, a breast plate at the top
    if t >= 2:
        for x0, x1 in ((0, 3), (4, 7)):
            plate(body, "back", x0, 0, x1, 4, pl, rivet=p["rivet"])
            plate(body, "back", x0, 5, x1, 8, pl, rivet=p["rivet"])
        band(body, 0, 4, pl, ("right", "left"), rivet=p["rivet"], step=2)
        if t >= 3:
            band(body, 5, 8, pl, ("right", "left"))
        else:
            chain(body, 5, 8, p["metal"], ("right", "left"))
        plate(body, "front", 0, 0, 7, 3, pl, rivet=p["rivet"])
        if glow is not None:
            for x in (1, 2, 5, 6):
                body.face("back", x, 4, glow)
            for y in (1, 2, 6, 7):
                body.face("back", 3, y, glow)
    # the smith's apron: bib, neck strap, full-width skirt over the belt, ties round the waist
    for y in range(1, 12):
        x0, x1 = (2, 5) if y < 4 else (0, 7)
        for x in range(x0, x1 + 1):
            k = hsh(x, y, 53) % 9
            body.face("front", x, y, p["scorch"] if k == 0 else a_lt if k == 1 else a_md)
    for y in range(4, 12):
        body.face("front", 0, y, a_lt)
        body.face("front", 7, y, a_dk)
    body.face("front", 1, 0, a_md)
    body.face("front", 6, 0, a_md)
    body.fill(6, 6, a_dk, ("right", "left", "back"))
    # pocket with a hammer handle
    for x, y in ((5, 6), (6, 6), (5, 7), (6, 7)):
        body.face("front", x, y, a_dk)
    body.face("front", 5, 5, p["brz"][1])
    body.face("front", 5, 4, p["metal"][2])
    # heavy belt on the sides and back, iron buckle
    dk, md = p["belt"]
    body.fill(9, 11, md, ("right", "left", "back"))
    body.fill(9, 9, dk, ("right", "left", "back"))
    studs(body, 10, p["rivet"], 3, 0, ("right", "left", "back"))
    if t == 2:
        plate(body, "front", 2, 1, 5, 3, pl, rivet=p["rivet"])
    elif t >= 3:
        plate(body, "front", 2, 1, 5, 4, pl, rivet=p["rivet"])
    if t == 4:
        # anvil-and-hammer emblem (ORIGINAL FICTION) on the apron, silver on bronze
        for x in range(1, 7):
            body.face("front", x, 6, p["brz"][1])
        for x, y in ((2, 7), (3, 7), (4, 7), (5, 7), (3, 8), (4, 8), (2, 9), (3, 9), (4, 9), (5, 9)):
            body.face("front", x, y, p["sil"][1])
        for x, y in ((3, 5), (4, 5)):
            body.face("front", x, y, p["gold"][1])
        body.face("front", 4, 6, p["gold"][0])
        body.fill_cap("top", pl[1])
    elif t == 5:
        # molten-crack painting along the apron edge and the bib plate seams
        for x, y in ((0, 5), (1, 6), (1, 7), (0, 8), (6, 9), (7, 10), (2, 4), (5, 4)):
            body.face("front", x, y, p["ember"][1])
        body.face("front", 3, 2, p["turq"][1])
        body.face("front", 4, 2, p["gold"][1])
        for x, y in ((1, 1), (6, 1), (1, 5), (6, 5)):     # tool rack on the back: tongs and a hammer
            body.face("back", x, y, p["metal"][2])
        for y in range(1, 8):
            body.face("back", 1, y, p["metal"][2] if y < 7 else p["metal"][0])
            body.face("back", 6, y, p["brz"][1])
        body.face("back", 5, 1, p["metal"][2])
        body.face("back", 7, 1, p["metal"][2])
    elif t == 6:
        # sky-iron with white-hot seams, star rivets, a small anvil-standard on the back
        for s, r in ((1, 2), (13, 3), (18, 6), (21, 1)):
            body.put(s, r, p["star"])
        for x, y in ((0, 5), (1, 7), (7, 9), (2, 4), (5, 4)):
            body.face("front", x, y, p["hot"][1])
        for y in range(0, 9):
            body.face("back", 3, y, p["sil"][1] if y > 2 else p["star"])
        for x, y in ((2, 1), (3, 1), (4, 1), (5, 1), (3, 2), (4, 2)):
            body.face("back", x, y, p["sil"][1])
        body.face("back", 4, 0, p["gold"][1])
        body.face("front", 3, 2, p["sky"][1])
        body.fill_cap("top", pl[1])
    else:
        body.fill_cap("top", c_md if t == 1 else pl[1])
    # arms: soot sleeves, heavy gauntlets; plates on the forearm (T2+), massive riveted pauldrons (T3+)
    cloth(arm, 0, 11, p["cloth"], salt=59)
    arm.fill_cap("top", c_md)
    g_dk, g_md, g_lt = p["glove"] if t < 4 else pl
    arm.fill(7, 11, g_md)
    arm.fill(7, 7, g_lt)
    arm.fill(8, 8, g_dk)
    arm.fill(11, 11, g_dk)
    studs(arm, 10, p["glove"][2] if t < 4 else (p["brz"][2] if t == 4 else p["rivet"]), 2, 1)
    if t == 2:
        band(arm, 5, 7, pl, rivet=p["rivet"], step=2)
        band(arm, 0, 2, p["apron"], rivet=p["rivet"], step=2)
    elif t >= 3:
        band(arm, 0, 4, pl, rivet=p["rivet"], step=2)
        arm.fill(2, 2, pl[2])
        studs(arm, 2, p["rivet"], 2, 1)
        arm.fill(4, 4, pl[0] if t != 4 else p["brz"][1])
        band(arm, 6, 7, pl if t != 4 else p["brz"], rivet=p["rivet"], step=2)
        arm.fill_cap("top", pl[2])
        for x in range(4):
            arm.face("top", x, 0, p["rivet"] if x % 3 == 0 else pl[2])
            arm.face("top", x, 3, p["rivet"] if x % 3 == 0 else pl[1])
        if glow is not None:
            arm.fill(3, 3, glow)
            studs(arm, 3, p["ember"][0], 3)
            if t == 6:
                studs(arm, 1, p["star"], 4, 2)


def darkhan_boots(t: int, p: dict, leg: Box) -> None:
    dk, md, lt = p["boot"]
    leg.fill(7, 9, md)
    leg.fill(7, 7, lt)
    leg.fill(10, 11, dk)          # thick double sole
    leg.fill(10, 10, p["cloth"][2])
    leg.fill_cap("bottom", dk)
    if t >= 2:
        pl = p["plate"]
        for x in range(4):        # iron toe cap
            leg.face("front", x, 9, pl[1])
            leg.face("front", x, 10, pl[2] if x in (1, 2) else pl[1])
        leg.face("front", 0, 9, p["rivet"])
        leg.face("front", 3, 9, p["rivet"])
    if t >= 3:
        band(leg, 7, 8, p["plate"] if t != 4 else p["brz"], rivet=p["rivet"], step=2)
    if t >= 5:
        leg.face("front", 1, 8, seam_glow(p, t))
    if t == 6:
        leg.face("front", 1, 7, p["star"])


def darkhan_waist(t: int, p: dict, body: Box) -> None:
    cloth(body, 8, 11, p["cloth"], salt=61)
    if t >= 2:
        chain(body, 8, 11, p["metal"] if t < 5 else p["plate"], ("right", "left", "back"))
    rect(body, "front", 0, 8, 7, 11, p["apron"][1])
    body.fill_cap("bottom", p["under"][0])


def darkhan_leggings(t: int, p: dict, leg: Box) -> None:
    u_dk, u_md, u_lt = p["under"]
    leg.fill(0, 11, u_md)
    leg.fill(0, 11, u_dk, ("left",))
    leg.fill(11, 11, u_dk)
    leg.fill_cap("bottom", u_dk)
    leg.fill_cap("top", u_md)
    pl = p["plate"]
    a_dk, a_md, a_lt = p["apron"]
    glow = seam_glow(p, t)
    if t >= 2:
        chain(leg, 0, 6, p["metal"] if t < 5 else pl, ("right", "back"))
    # the apron skirt down the front of both legs to the knee
    for y in range(0, 7):
        for x in range(4):
            k = hsh(x, y, 67) % 9
            leg.face("front", x, y, p["scorch"] if k == 0 else a_lt if k == 1 else a_md)
    for x in range(4):
        leg.face("front", x, 6, a_dk)
    if t == 2:
        plate(leg, "front", 0, 5, 3, 7, pl, rivet=p["rivet"])
    elif t >= 3:
        # riveted apron plates over the leather, knee plate
        plate(leg, "front", 0, 0, 3, 2, pl, rivet=p["rivet"])
        plate(leg, "front", 0, 3, 3, 5, pl if t != 4 else p["brz"], rivet=p["rivet"])
        plate(leg, "front", 0, 6, 3, 7, pl, rivet=p["rivet"])
        band(leg, 0, 2, pl, ("right",), rivet=p["rivet"], step=2)
        if glow is not None:
            leg.face("front", 1, 3, glow)
            leg.face("front", 2, 3, p["ember"][0])
            leg.face("front", 2, 6, glow)
            if t == 6:
                leg.face("front", 2, 1, p["star"])
                leg.put(2, 1, p["star"])


# --- ХҮЛЭГЧИН: rider (sky-blue deel, white trim, red sash, knee boots, light scale) -----------------------------
_KH_DEEL = {1: R("#3E6A9E", "#5A8CC4", "#86B0DC"), 2: R("#3E6A9E", "#5A8CC4", "#86B0DC"),
            3: R("#386496", "#5486BE", "#7EA8D6"), 4: R("#2E5A8E", "#4A7CB6", "#76A2D2"),
            5: R("#2A5486", "#4676AE", "#6E9ACA"), 6: R("#4E7FC0", "#7AA2D6", "#A8C4E6")}
_KH_SCALE = {1: R("#3A3D44", "#5E6168", "#8D9199"), 2: R("#3A3D44", "#5E6168", "#8D9199"),
             3: R("#5E6168", "#8D9199", "#C9CDD3"), 4: R("#3B4A66", "#5C6F94", "#9AA6B8"),
             5: R("#26304A", "#3B4A66", "#5C6F94"), 6: R("#141C30", "#1E2A44", "#34507E")}
PAL_KHULEGCHIN = {t: {**LADDER(t), "cloth": _KH_DEEL[t], "scale": _KH_SCALE[t], "trim": P("#C9CDD3", "#E6EAF0"),
                      "sash": R("#7A1E1E", "#A3302A", "#C8463A") if t < 4 else R("#6E1A22", "#A3302A", "#C8463A"),
                      "fur": R("#B8AA92", "#D8CFB8", "#E8E4DA"), "capfelt": R("#1E2A44", "#2A3A5A", "#3E5272"),
                      "boot": LEATHER, "under": R("#1E2A44", "#2A3A5A", "#3E5272"), "lace": hx("#1E2A44")}
                  for t in TIERS}


def khulegchin_helmet(t: int, p: dict, head: Box, hat: Box) -> None:
    f_dk, f_md, f_lt = p["fur"]
    capr = p["capfelt"] if t <= 2 else p["scale"]
    k_dk, k_md, k_lt = capr
    s_dk, s_md, s_lt = p["sash"]
    # pointed cap: crown panels rising to a tip; cap sides rows 0..3
    # the cap narrows to its point above the crown line: full rows 2..3, a triangle on rows 0..1 of every side,
    # and only the middle of the top face (the corners stay open, so the outline reads pointed)
    cone_top(head, capr, k_dk, s_lt if t <= 2 else (p["edge"] if t != 4 else p["gold"][1]), rings=3)
    if t <= 2:
        cloth(head, 2, 3, capr, salt=71)
    else:
        band(head, 2, 3, capr)
    peak(head, k_md, k_dk, rows=2)
    peak(hat, k_lt, k_md, rows=2)
    # ear flaps hanging from the sides, lined with fur (lamellae at T3+), short at the back
    for name in ("right", "left"):
        for y in range(4, 8):
            for x in range(1, 7):
                if t <= 2:
                    c = f_lt if x == 6 or y == 7 else (k_md if (x + y) % 4 else k_lt)
                else:
                    c = (k_lt if (x + y) % 2 == 0 else k_md) if y < 7 else k_dk
                head.face(name, x, y, c)
    # the upturned brim round the face (white fur at T1-T2, a metal band later)
    for s in range(hat.sw):
        if t <= 2:
            hat.put(s, 2, f_lt if s % 3 else f_md)
            hat.put(s, 3, f_md if s % 2 else f_dk)
        else:
            hat.put(s, 2, p["edge"] if t != 6 else p["sil"][1])
            hat.put(s, 3, k_md if s % 3 else p["rivet"])
    # two red ribbons from the back of the cap (a rider's cap ribbon; plain cloth, no symbol)
    for y in range(3, 8):
        hat.face("back", 3, y, s_md if y % 2 else s_lt)
        hat.face("back", 4, y, s_dk if y % 2 else s_md)
    if t == 1:
        return
    if t == 2:
        # iron brow band with rivets and a short iron nape guard
        head.fill(3, 3, p["metal"][1])
        studs(head, 3, p["rivet"], 2)
        lamellae(head, 4, 6, p["metal"], pw=2, rh=2, faces=("back",))
        for x, y in ((3, 0), (4, 0)):
            hat.face("front", x, y, p["metal"][2])
        return
    # T3+: a long lamellar nape guard at the back
    lamellae(head, 2, 7, capr, pw=2, rh=2, edge=p["edge"], faces=("back",))
    if t == 3:
        # pennon tube with a short red pennon behind the peak
        for y in range(0, 3):
            hat.face("back", 6, y, p["bronze"][2])
        hat.face("back", 5, 0, s_md)
        hat.face("back", 4, 0, s_lt)
        hat.face("back", 5, 1, s_dk)
        return
    if t == 4:
        # a ring of white horsehair round the helmet above the silver brow
        for s in range(hat.sw):
            if s % 2:
                hat.put(s, 1, hhair(s))
        hat.face("front", 3, 3, p["gold"][1])
        return
    if t == 5:
        # swept cheek wings: blued-steel feathers sweeping back, turquoise edges
        for name in ("right", "left"):
            for i in range(5):
                for x in range(1 + i, 7):
                    head.face(name, x, 2 + i, p["turq"][1] if x == 1 + i else (k_lt if (x + i) % 2 else k_md))
        hat.face("front", 3, 3, p["gold"][1])
        hat.face("front", 4, 3, p["turq"][1])
        return
    # t == 6: white horsehair crest swept flat back along the crown, star rivets, a sky stone at the brow
    hd, hl = p["hair"]
    for y in range(8):
        hat.face("top", 3, y, hl if y % 2 else hd)
        hat.face("top", 4, y, hd if y % 2 else hl)
    for y in range(0, 6):
        hat.face("back", 3, y, hl if y % 2 else hd)
        hat.face("back", 4, y, hd if y % 2 else hl)
    studs(hat, 3, p["star"], 4, 1)
    hat.face("front", 3, 3, p["sky"][1])
    hat.face("front", 4, 3, p["gold"][1])


def hhair(s: int) -> Color:
    return hx("#E8E4DA") if s % 4 == 1 else hx("#BDB6A6")


def khulegchin_chest(t: int, p: dict, body: Box, arm: Box) -> None:
    c_dk, c_md, c_lt = p["cloth"]
    t_dk, t_lt = p["trim"]
    s_dk, s_md, s_lt = p["sash"]
    sc = p["scale"]
    cloth(body, 0, 8, p["cloth"], salt=73, rate=9)
    # the deel wraps from the neck to the right armpit, edged in white
    for i in range(6):
        x, y = 5 - i, i
        body.face("front", x, y, t_lt)
        body.face("front", x - 1, y, t_dk)
        for xx in range(x + 1, 8):
            body.face("front", xx, y, c_lt if (xx + y) % 5 == 0 else c_md)
    for y in range(6, 9):
        body.face("front", 0, y, t_dk)
    body.fill(0, 0, t_lt, ("back", "right", "left"))
    if t >= 3:
        # light scale over the chest (front and back panels), the wrap edge left showing at the collar
        for name in ("front", "back"):
            for y in range(2, 9):
                for x in range(8):
                    if name == "front" and x < 6 - y:
                        continue
                    k = (x + (y // 2 % 2) * 2) % 4
                    lt = p["sil"][1] if k == 1 and t == 4 else sc[2]
                    body.face(name, x, y, (sc[0], lt, sc[2], sc[1])[k] if y % 2 == 0 else
                              (sc[0], sc[1], sc[1], sc[0])[k])
    if t >= 5:
        # a mantle streaming back from the shoulders: white and sky cloth with wind lines
        m = (p["sil"][0], p["sil"][1], p["white"] if t == 6 else hx("#E6EAF0"))
        for y in range(0, 12):
            for x in range(8):
                if y > 2 and x < y - 4:
                    continue
                c = m[2] if (x + y) % 4 == 0 else m[1]
                if (x - y) % 5 == 0:
                    c = m[0]
                body.face("back", x, y, c)
        if t == 5:
            body.face("back", 6, 2, p["turq"][1])
            body.face("back", 1, 2, p["turq"][1])
        else:
            for x, y in ((2, 3), (6, 6), (4, 9)):
                body.face("back", x, y, p["star"])
    if t == 6:
        for s, r in ((1, 3), (14, 5)):
            body.put(s, r, p["star"])
    # the red sash: three folds, knotted on the left hip, tails down the left side
    body.fill(9, 9, s_lt)
    body.fill(10, 10, s_md)
    body.fill(11, 11, s_dk)
    for y in (9, 10, 11):
        body.face("left", 1, y, s_lt)
        body.face("left", 2, y, s_dk)
    body.face("front", 6, 10, s_lt)
    body.face("front", 7, 10, s_dk)
    if t >= 4:
        body.face("front", 6, 9, p["gold"][1])
        body.fill(10, 10, s_lt, ("back",))
        studs(body, 10, p["sil"][1] if t != 5 else p["turq"][1], 4, 2, ("front", "back"))
    body.fill_cap("top", c_md)
    for x in range(8):
        body.face("top", x, 3, t_lt)
    # sleeves: long deel sleeves, white cuff, a horse-hoof cuff over the hand; scale on the shoulders (T2+)
    cloth(arm, 0, 11, p["cloth"], salt=79, rate=9)
    arm.fill_cap("top", c_md)
    arm.fill(9, 9, t_lt)
    arm.fill(10, 10, t_dk)
    arm.fill(11, 11, c_dk)
    if t >= 2:
        scales(arm, 0, 3 if t < 4 else 4, sc, edge=p["edge"] if t >= 3 else None)
        arm.fill_cap("top", sc[1])
        arm.fill(4 if t < 4 else 5, 4 if t < 4 else 5, t_lt if t != 6 else p["sil"][1])
    if t >= 4:
        studs(arm, 9, p["sil"][1] if t == 4 else p["turq"][1] if t == 5 else p["star"], 2)
    if t == 6:
        studs(arm, 1, p["star"], 4, 1)


def khulegchin_boots(t: int, p: dict, leg: Box) -> None:
    dk, md, lt = p["boot"]
    leg.fill(7, 10, md)
    for s in leg.cols():
        if s % 4 == 3:
            leg.put(s, 8, lt)
    # felt boot top (white at T1-T3), turned up toe, heel; spur from T2
    cuff = p["trim"][1] if t <= 3 else p["turq"][1] if t == 5 else p["sil"][1]
    leg.fill(7, 7, cuff)
    if t >= 3:
        for y in (8, 9):
            for x in (1, 2):
                leg.face("front", x, y, p["scale"][2] if (x + y) % 2 else p["scale"][1])
    if t >= 2:
        leg.face("back", 1, 10, p["bronze"][2] if t <= 3 else p["edge"])
        leg.face("back", 2, 10, p["bronze"][1] if t <= 3 else p["edge"])
    sole(leg, p["boot"], lt if t < 4 else (p["sil"][1] if t != 5 else p["turq"][1]))
    if t == 6:
        leg.face("front", 0, 8, p["star"])
        leg.face("front", 3, 8, p["star"])


def khulegchin_waist(t: int, p: dict, body: Box) -> None:
    cloth(body, 8, 11, p["cloth"], salt=83, rate=9)
    body.fill(9, 9, p["sash"][1])
    body.fill_cap("bottom", p["under"][0])


def khulegchin_leggings(t: int, p: dict, leg: Box) -> None:
    u_dk, u_md, u_lt = p["under"]
    leg.fill(0, 11, u_md)
    leg.fill(0, 11, u_dk, ("left",))
    leg.fill_cap("bottom", u_dk)
    leg.fill_cap("top", u_md)
    # riding boots to the knee: the boot shaft rises under the deel (rows 4..6, the boot top sits at row 7)
    b_dk, b_md, b_lt = p["boot"]
    leg.fill(4, 11, b_md)
    leg.fill(4, 4, b_lt)
    leg.fill(11, 11, b_dk)
    for s in leg.cols():
        if s % 4 == 3:
            leg.put(s, 5, b_dk)
    t_dk, t_lt = p["trim"]
    panels = ("front", "back")
    if t == 1:
        cloth(leg, 0, 3, p["cloth"], ("right", "front", "back"), salt=89, rate=9)
        leg.fill(3, 3, t_lt, ("right", "front", "back"))
        return
    # T2+: split riding skirt: lamellar panels front and back, open at the sides over the trousers
    rows = {2: 4, 3: 5, 4: 5, 5: 6, 6: 6}[t]
    lamellae(leg, 0, rows, p["scale"], pw=2 if t != 4 else 4, rh=2, edge=p["edge"] if t in (3, 4) else None,
             edge_period=2 if t == 4 else 1, lace=p["lace"] if t == 2 else None, faces=panels)
    leg.fill(rows, rows, t_lt if t < 4 else (p["sil"][1] if t != 5 else p["turq"][1]), panels)
    leg.fill(0, 1, p["cloth"][1], ("right",))
    if t >= 4:
        leg.face("front", 1, rows, p["gold"][1] if t == 4 else p["edge"])
    if t == 6:
        for s, r in ((5, 1), (14, 3)):
            leg.put(s, r, p["star"])


# --- build ----------------------------------------------------------------------------------------------------
# class id -> (palettes per tier, helmet, chest, boots, waist, leggings painters)
CLASSES = {
    "baatar": (PAL, helmet, chest, boots, waist, leggings),
    "mergen": (PAL_MERGEN, mergen_helmet, mergen_chest, mergen_boots, mergen_waist, mergen_leggings),
    "boo": (PAL_BOO, boo_helmet, boo_chest, boo_boots, boo_waist, boo_leggings),
    "darkhan": (PAL_DARKHAN, darkhan_helmet, darkhan_chest, darkhan_boots, darkhan_waist, darkhan_leggings),
    "khulegchin": (PAL_KHULEGCHIN, khulegchin_helmet, khulegchin_chest, khulegchin_boots, khulegchin_waist,
                   khulegchin_leggings),
}
CLASS_LABEL = {"baatar": "Baatar", "mergen": "Mergen", "boo": "Boo", "darkhan": "Darkhan", "khulegchin": "Khulegchin"}


def build(c: str, t: int) -> tuple[Image.Image, Image.Image]:
    pal, f_helmet, f_chest, f_boots, f_waist, f_leggings = CLASSES[c]
    p = pal[t]
    hum = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bx = boxes(hum)
    f_helmet(t, p, bx["head"], bx["hat"])
    f_chest(t, p, bx["body"], bx["arm"])
    f_boots(t, p, bx["leg"])
    lgs = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    lx = boxes(lgs)
    f_waist(t, p, lx["body"])
    f_leggings(t, p, lx["leg"])
    return hum, lgs


def all_gold() -> set:
    """Every gold swatch of every palette: a pixel of any of them counts as gold in any class and tier."""
    out = set()
    for pal, *_ in CLASSES.values():
        for p in pal.values():
            out.update(p.get("gold", ()))
    return out


def gold_share(imgs) -> float:
    gold = all_gold()
    painted = hits = 0
    for im in imgs:
        raw = im.tobytes()
        for i in range(0, len(raw), 4):
            px = tuple(raw[i:i + 4])
            if px[3]:
                painted += 1
                hits += px in gold
    return hits / max(painted, 1)


def uv_mask(parts) -> set:
    """Texture pixels covered by the given boxes (side strips + top and bottom faces)."""
    geo = {"head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "arm": (40, 16, 4, 12, 4),
           "leg": (0, 16, 4, 12, 4)}
    out = set()
    for name in parts:
        u, v, w, h, d = geo[name]
        out.update((x, y) for x in range(u, u + 2 * (w + d)) for y in range(v + d, v + d + h))
        out.update((x, y) for x in range(u + d, u + d + 2 * w) for y in range(v, v + d))
    return out


MASK_HUMANOID = uv_mask(("head", "hat", "body", "arm", "leg")) - {(x, y) for x in range(0, 16) for y in range(20, 27)} \
    - {(x, y) for x in range(4, 8) for y in range(16, 20)}       # leg rows 0-6 and the leg top: boots start at row 7
MASK_LEGGINGS = uv_mask(("body", "leg")) - {(x, y) for x in range(16, 40) for y in range(20, 28)} \
    - {(x, y) for x in range(20, 28) for y in range(16, 20)}     # body: only the waist rows 8-11 and the bottom face


def check_layers(name: str, hum: Image.Image, lgs: Image.Image) -> None:
    for layer, im, mask in (("humanoid", hum, MASK_HUMANOID), ("humanoid_leggings", lgs, MASK_LEGGINGS)):
        for y in range(H):
            for x in range(W):
                a = im.getpixel((x, y))[3]
                if a not in (0, 255):
                    raise SystemExit(f"{layer}/{name}: alpha {a} at ({x},{y}); only 0 or 255 allowed")
                if a and (x, y) not in mask:
                    raise SystemExit(f"{layer}/{name}: painted pixel outside its UV area at ({x},{y})")


def equipment_json(c: str, t: int) -> dict:
    tex = f"suld:{c}_t{t}"
    return {"layers": {"humanoid": [{"texture": tex}], "humanoid_leggings": [{"texture": tex}]}}


# --- preview --------------------------------------------------------------------------------------------------
def doll(hum: Image.Image, lgs: Image.Image, side: str) -> Image.Image:
    """A flat paper-doll (16x32) of the front or back faces, or an 8x32 right-side view: leggings under the
    humanoid layer, as worn."""
    if side == "side":
        out = Image.new("RGBA", (8, 32), (0, 0, 0, 0))
        for img in (lgs, hum):
            out.alpha_composite(img.crop((0, 8, 8, 16)), (0, 0))
            out.alpha_composite(img.crop((32, 8, 40, 16)), (0, 0))
            out.alpha_composite(img.crop((16, 20, 20, 32)), (2, 8))
            out.alpha_composite(img.crop((40, 20, 44, 32)), (2, 8))
            out.alpha_composite(img.crop((0, 20, 4, 32)), (2, 20))
        return out
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


DOLL_BG = (96, 104, 92, 255)


def preview(c: str, sets, out_dir: str) -> str:
    """One sheet per class: both layers at x8 and front, back and side paper dolls for every tier."""
    s = 8
    tex_w, tex_h = W * s, H * s
    gap, label = 12, 14
    row_h = tex_h + label + gap
    sheet_w = gap + 2 * (tex_w + gap) + 2 * (16 * s + gap) + 8 * s + gap
    sheet = Image.new("RGBA", (sheet_w, gap + len(sets) * row_h), (30, 32, 40, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (t, hum, lgs) in enumerate(sets):
        y = gap + i * row_h
        x = gap
        for name, im in (("humanoid", hum), ("humanoid_leggings", lgs)):
            sheet.paste(checker(tex_w, tex_h, s), (x, y + label))
            sheet.alpha_composite(im.resize((tex_w, tex_h), Image.NEAREST), (x, y + label))
            draw.text((x, y), f"{CLASS_LABEL[c]} T{t} {TIER_NAME[t]}  {name}/{c}_t{t}.png", fill=(220, 224, 232, 255))
            x += tex_w + gap
        for side in ("front", "back", "side"):
            dl = doll(hum, lgs, side)
            dl = dl.resize((dl.width * s, dl.height * s), Image.NEAREST)
            sheet.paste(Image.new("RGBA", dl.size, DOLL_BG), (x, y + label))
            sheet.alpha_composite(dl, (x, y + label))
            draw.text((x, y), side, fill=(220, 224, 232, 255))
            x += dl.width + gap
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"{c}_armor.png")
    sheet.save(path)
    return path


def overview(built: dict, out_dir: str) -> str:
    """All classes side by side: one row per class, front and back dolls per tier (x5)."""
    s, gap, label = 5, 10, 14
    cell_w = 2 * 16 * s + 4
    sheet = Image.new("RGBA", (110 + len(TIERS) * (cell_w + gap), gap + label + len(built) * (32 * s + gap)),
                      (30, 32, 40, 255))
    draw = ImageDraw.Draw(sheet)
    for j, t in enumerate(TIERS):
        draw.text((110 + j * (cell_w + gap), gap // 2), f"T{t} {TIER_NAME[t]}", fill=(220, 224, 232, 255))
    for i, (c, sets) in enumerate(built.items()):
        y = gap + label + i * (32 * s + gap)
        draw.text((gap, y + 16 * s), CLASS_LABEL[c], fill=(220, 224, 232, 255))
        for j, (t, hum, lgs) in enumerate(sets):
            x = 110 + j * (cell_w + gap)
            for k, side in enumerate(("front", "back")):
                dl = doll(hum, lgs, side).resize((16 * s, 32 * s), Image.NEAREST)
                sheet.paste(Image.new("RGBA", dl.size, DOLL_BG), (x + k * (16 * s + 4), y))
                sheet.alpha_composite(dl, (x + k * (16 * s + 4), y))
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, "all_classes_overview.png")
    sheet.save(path)
    return path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--class", dest="classes", nargs="+", choices=sorted(CLASSES), metavar="CLASS",
                    help="only these classes (default: all five): " + ", ".join(CLASSES))
    ap.add_argument("--preview", help="write an x8 preview sheet per class (+ paper dolls) and an overview here")
    args = ap.parse_args()
    for d in (EQUIPMENT, TEX_HUMANOID, TEX_LEGGINGS):
        os.makedirs(d, exist_ok=True)
    built = {}
    for c in (k for k in CLASSES if not args.classes or k in args.classes):
        sets = []
        for t in TIERS:
            hum, lgs = build(c, t)
            name = f"{c}_t{t}"
            check_layers(name, hum, lgs)
            share = gold_share((hum, lgs))
            if share > GOLD_BUDGET[t] + 1e-9:
                raise SystemExit(f"{name}: gold covers {share:.1%} of painted pixels (budget {GOLD_BUDGET[t]:.0%})")
            hum.save(os.path.join(TEX_HUMANOID, f"{name}.png"), optimize=False)
            lgs.save(os.path.join(TEX_LEGGINGS, f"{name}.png"), optimize=False)
            with open(os.path.join(EQUIPMENT, f"{name}.json"), "w", encoding="utf-8") as fh:
                json.dump(equipment_json(c, t), fh, indent=1)
                fh.write("\n")
            sets.append((t, hum, lgs))
            print(f"{name}: humanoid + humanoid_leggings 64x32, gold {share:.1%}")
        built[c] = sets
        if args.preview:
            print("preview:", preview(c, sets, args.preview))
    if args.preview and len(built) > 1:
        print("overview:", overview(built, args.preview))


if __name__ == "__main__":
    main()
