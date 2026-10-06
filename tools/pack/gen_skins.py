#!/usr/bin/env python3
"""Generate the Kharkhorum NPC skins (64x64, classic/wide arms) — Mongolian dress: дээл robe with a бүс sash and a
diagonal collar flap, гутал boots, fur or felt hats, the shaman's tasselled headdress, the lama's robe.

Output: resourcepack/assets/suld/textures/entity/npc/<id>.png, used by Mannequin NPCs through the profile's skin
patch (texture key suld:entity/npc/<id>). Deterministic.

    python3 tools/pack/gen_skins.py [--preview DIR]
"""
from __future__ import annotations

import argparse
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "resourcepack", "assets", "suld", "textures", "entity", "npc")


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


class Skin:
    def __init__(self):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def px(self, x, y, c):
        self.img.putpixel((x, y), tuple(c) + (255,) if len(c) == 3 else tuple(c))

    def rect(self, x, y, w, h, c):
        for i in range(w):
            for j in range(h):
                self.px(x + i, y + j, c)

    def box(self, u, v, w, h, d, c, top=None, bottom=None):
        """Fill the 6 faces of a cuboid laid out at (u, v); sides slightly darker."""
        self.rect(u + d, v, w, d, top or shade(c, 1.1))            # top
        self.rect(u + d + w, v, w, d, bottom or shade(c, 0.7))     # bottom
        self.rect(u, v + d, d, h, shade(c, 0.85))                  # right
        self.rect(u + d, v + d, w, h, c)                           # front
        self.rect(u + d + w, v + d, d, h, shade(c, 0.85))          # left
        self.rect(u + 2 * d + w, v + d, w, h, shade(c, 0.75))      # back

    def face_rows(self, u, v, w, d, row, h, c, faces=("front", "right", "left", "back")):
        """Paint horizontal band rows [row, row+h) on the side faces of a cuboid at (u, v)."""
        off = {"right": (u, d), "front": (u + d, w), "left": (u + d + w, d), "back": (u + 2 * d + w, w)}
        for f in faces:
            x0, width = off[f]
            self.rect(x0, v + d + row, width, h, c)


# body parts (u, v, w, h, d) in the 64x64 layout
HEAD, BODY = (0, 0, 8, 8, 8), (16, 16, 8, 12, 4)
RARM, LARM = (40, 16, 4, 12, 4), (32, 48, 4, 12, 4)
RLEG, LLEG = (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)
HAT = (32, 0, 8, 8, 8)  # head overlay layer


def person(deel, sash, trim, skin=(214, 168, 124), hair=(40, 30, 24), boots=(70, 44, 28), hat=None, hat_top=None,
           beard=None, eyes=(40, 30, 30)):
    s = Skin()
    # head
    s.box(*HEAD, skin, top=hair, bottom=shade(skin, 0.8))
    s.rect(8, 8, 8, 2, hair)                      # front fringe
    s.rect(0, 8, 8, 3, hair)
    s.rect(16, 8, 8, 3, hair)
    s.rect(24, 8, 8, 5, hair)                     # back hair
    s.px(10, 12, (255, 255, 255)); s.px(11, 12, eyes)
    s.px(13, 12, eyes); s.px(14, 12, (255, 255, 255))
    s.rect(10, 11, 2, 1, shade(hair, 1.2)); s.rect(13, 11, 2, 1, shade(hair, 1.2))   # brows
    s.rect(11, 14, 3, 1, shade(skin, 0.7))        # mouth
    if beard:
        s.rect(9, 14, 6, 2, beard); s.rect(10, 13, 4, 1, beard); s.rect(11, 14, 2, 1, shade(skin, 0.6))
    # deel (body + arms + upper legs), sash, collar flap
    s.box(*BODY, deel)
    s.face_rows(16, 16, 8, 4, 7, 2, sash)                                   # бүс sash
    for i in range(6):                                                       # diagonal collar flap
        s.px(20 + 3 - i // 2 + 1, 20 + i, trim)
    s.rect(20, 20, 8, 1, trim)                                               # collar
    for part in (RARM, LARM):
        s.box(*part, deel)
        u, v, w, h, d = part
        s.face_rows(u, v, w, d, 10, 2, trim)                                 # cuffs
        s.rect(u + d + w, v, w, d, skin)                                     # hands (bottom)
    for part in (RLEG, LLEG):
        s.box(*part, deel)
        u, v, w, h, d = part
        s.face_rows(u, v, w, d, 6, 6, boots)                                 # гутал boots
        s.face_rows(u, v, w, d, 6, 1, shade(boots, 1.4))                    # boot rim
        s.rect(u + d + w, v, w, d, shade(boots, 0.6))                        # soles
    if hat:
        u, v, w, h, d = HAT
        s.rect(u + d, v, w, d, hat_top or hat)                               # hat top
        s.face_rows(u, v, w, d, 0, 3, hat)                                   # brim / fur band
        s.face_rows(u, v, w, d, 3, 1, shade(hat, 0.8), faces=("right", "left", "back"))
    return s


NPCS = {
    # Хөтөч — the guide: sky-blue deel, gold sash, white beard, felt hat
    "guide": person((52, 104, 196), (232, 182, 64), (232, 220, 190), hair=(220, 220, 220), beard=(236, 236, 236),
                    hat=(150, 30, 30), hat_top=(232, 182, 64)),
    # Бөө — the shaman: dark deel, black tasselled headdress over the eyes
    "shaman": person((40, 34, 54), (120, 40, 140), (200, 190, 160), hair=(20, 18, 20)),
    # Анчдын ахлагч — the hunt master: leather deel, wolf-fur hat
    "hunter": person((120, 82, 50), (60, 110, 60), (190, 160, 110), hat=(140, 128, 116), hat_top=(110, 70, 40)),
    # Худалдаачин — the merchant: green silk deel, orange sash, round hat
    "merchant": person((40, 130, 80), (240, 140, 40), (240, 220, 150), hat=(40, 40, 50), hat_top=(200, 40, 40)),
    # Дархан — the blacksmith: soot-dark deel, leather apron (sash), bare head
    "blacksmith": person((64, 60, 60), (130, 90, 50), (150, 140, 130), hair=(60, 40, 30), beard=(80, 56, 40)),
    # Өртөөчин — the relay rider: red deel, orange sash, fur hat
    "rider": person((180, 40, 40), (240, 160, 40), (240, 220, 160), hat=(150, 120, 90), hat_top=(200, 40, 40)),
    # Лам — the shrine keeper: saffron robe with a maroon sash, shaved head
    "lama": person((230, 150, 40), (130, 30, 40), (250, 210, 120), hair=(200, 150, 110)),
}


def shaman_headdress(s: Skin) -> None:
    """Black tassel fringe hanging over the eyes, an eagle-feather crest on the hat layer."""
    u, v, w, h, d = HAT
    s.rect(u + d, v, w, d, (30, 26, 30))
    for x in range(w):
        for y in range(0, 5 if x % 2 == 0 else 4):
            s.px(u + d + x, v + d + y, (16, 14, 18))
    s.face_rows(u, v, w, d, 0, 3, (30, 26, 30), faces=("right", "left", "back"))
    for x in (1, 3, 5):
        s.px(u + d + x, v + d + 5, (200, 40, 60))  # red tassel ends


def apron(s: Skin) -> None:
    s.rect(20, 25, 8, 7, (110, 76, 44))
    s.rect(4, 20, 4, 4, (110, 76, 44)); s.rect(20, 52, 4, 4, (110, 76, 44))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    shaman_headdress(NPCS["shaman"])
    apron(NPCS["blacksmith"])
    os.makedirs(OUT, exist_ok=True)
    for name, s in NPCS.items():
        s.img.save(os.path.join(OUT, name + ".png"), optimize=False)
    if args.preview:
        os.makedirs(args.preview, exist_ok=True)
        sheet = Image.new("RGBA", (len(NPCS) * 70, 70), (60, 60, 70, 255))
        for i, s in enumerate(NPCS.values()):
            sheet.paste(s.img, (i * 70 + 3, 3), s.img)
        sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(os.path.join(args.preview, "skins.png"))
    print(f"{len(NPCS)} NPC skins written")


if __name__ == "__main__":
    main()
