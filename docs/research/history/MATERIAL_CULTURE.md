# Military equipment, ger, ornament, organisation

Part of the SÜLD historical research set. See [README.md](README.md) for method, labels and the source list (`[Sn]`).

---

## 1. Armour and helmets

| Claim | Label | Source |
|---|---|---|
| **Lamellar** armour (small plates laced together) was in regular use across the Mongol Empire by the 13th century, according to literary and pictorial evidence. Nearly all surviving non-excavated examples come from **Tibet**. | HISTORICALLY VERIFIED (Met curatorial text) | [S41] |
| The Met holds a **lamellar helmet** of **iron and leather**, attributed Mongolian or Tibetan, 13th–15th century. Radiocarbon dating of its lacing gives **1271–1431**. The museum calls it exceptionally rare and pristine. | HISTORICALLY VERIFIED (museum record; the exact accession no. was not confirmed in this session) | [S41] |
| A wreck from Kublai's **1281** invasion fleet off **Takashima**, Japan, yielded an intact helmet, iron arrowheads, ceramic **bombs** (*tetsuhau*) and bright red leather armour fragments. | HISTORICALLY VERIFIED (Archaeology magazine, AIA) | [S42] |
| The **Mōko Shūrai Ekotoba** handscrolls (dated 1293; Museum of the Imperial Collections, Tokyo) are among the earliest images of Mongol soldiers in the 1274 and 1281 invasions. Some figures were **added later** and show distortions. | HISTORICALLY VERIFIED | [S43] |

**SÜLD use:** Laced lamellar plates, iron with leather, **red-lacquered leather** and laced seams are verified
visual vocabulary for armour items and **UI frames** ("laced plate" borders). Using exact scroll imagery as
reference for figures is risky because of the later additions. **Do not trace the scroll.**

## 2. Bows and archery

| Claim | Label | Source |
|---|---|---|
| The Mongol bow is a **recurved composite** bow. **Horn** on the belly, **sinew** on the back, a core (bamboo or wood), and animal glue. **Birch bark** is a moisture-proof covering. | HISTORICALLY VERIFIED (secondary; the core material varies by source and period) | [S44] |
| A surviving **13th-century bow from Tsagaan-Khad** and paintings show that medieval Mongol bows had **smaller siyahs** (stiff tips) and **less prominent string bridges** than later Manchu-style bows. | HISTORICALLY VERIFIED (secondary, citing the find) | [S44] |
| "Warriors carried at least two bows" | partly verified (secondary, traditional account) | [S44] |

**SÜLD use:** The Мэргэн class bow models and icons should show a **compact recurve with short tips**, not the
long Qing/Manchu-style bow. Birch bark, horn and sinew are good **texture vocabulary**. Birch-bark working is also
verified in the Karakorum workshops (see [KHARKHORUM.md](KHARKHORUM.md) §6).

## 3. Organisation: the decimal system

| Claim | Label | Source |
|---|---|---|
| From **1206** Chinggis organised people and army in **decimal units**: *arban* (10), *jaghun* (100), *mingghan* (1,000), *tümen* (10,000). This deliberately cut across tribal ties. The system was older (Xiongnu). | HISTORICALLY VERIFIED (secondary) | [S45] |

**SÜLD use:** The existing rank ladder `АРД → ЦЭРЭГ → АРАВТ → ЗУУТ → МЯНГАТ → ТҮМЭН → НОЁН → ХААН`
(`suld-api/.../style/Rank`, glyph badges in `Glyphs.BADGE_RANK_*`) is **INSPIRED** by this verified system. The
level and coin costs are ORIGINAL SULD FICTION.

## 4. Yam relay system

| Claim | Label | Source |
|---|---|---|
| The **yam** was a chain of relay stations with fresh horses, food and shelter, extended especially by **Ögedei**. Messengers carried a paiza. | HISTORICALLY VERIFIED (secondary) | [S32] |
| Station spacing (about 20–40 miles) and daily distances (200–300 km) | secondary figures; treat as approximate | [S32] |

**SÜLD use:** Fast-travel "yam stations" with horses are **INSPIRED**. Costs and unlock rules are ORIGINAL SULD FICTION.

## 5. The ger (гэр)

| Claim | Label | Source |
|---|---|---|
| UNESCO: **Traditional craftsmanship of the Mongol Ger and its associated customs** was inscribed in **2013** (8.COM, element 00872) on the Representative List of the Intangible Cultural Heritage of Humanity. | HISTORICALLY VERIFIED | [S46] |
| The ger is a round structure of walls, poles and a peaked roof covered with canvas and felt and tied with ropes. It is light, folds, and can be taken down and rebuilt. Men carve the wood; women and men paint, sew, stitch and make felt. | HISTORICALLY VERIFIED | [S46] |
| Term details (lattice wall *khana* хана, roof ring *toono* тооно, roof poles *uni* унь, door facing south) | widely documented, but **not verified in this session** | — |

**SÜLD use:** Lattice (khana) patterns, the round roof-ring motif and felt texture are good **UI frame and icon
vocabulary**. Mark them INSPIRED. A toono ring is a good circular frame for minimap or compass widgets.

## 6. Ornament (хээ, khee)

| Claim | Label | Source |
|---|---|---|
| *Khee* (хээ) is the Mongolian word for an ornamental pattern. Ethnographic sources describe a very large repertoire ("over 1,500 ornaments") in geometric, animal, plant, natural and religious groups. | partly verified (secondary, tourism-ethnography sites; the count is unverified) | [S47] |
| **Ulzii (өлзий)**, the endless knot: a symbol of happiness, longevity and interdependence in Mongolian use. It is also one of the **Eight Auspicious Symbols of Buddhism**. | HISTORICALLY VERIFIED as a *Mongolian traditional ornament*. **Not verified as a 13th-century imperial motif**: its prominence is tied to the Buddhist culture that spread mainly from the 16th century. | [S47], [S48] |
| The horn ornament (*ever khee*, эвэр хээ) is said to symbolise livestock growth and nomads' happiness. | partly verified (secondary) | [S47] |
| "Alkhan khee" (hammer/meander pattern) and its date | **unverified** | — |

**SÜLD use:**

- Ulzii, horn scrolls and meander-like interlace are fine as **INSPIRED "Mongolian ornament"** in UI borders and
  icons. The current `ICON_ULZII` glyph and gen_ui.py's "ulzii-style knots in the header corners" are INSPIRED.
- Do **not** label them "imperial 13th-century ornament".
- Every ornament in the pack must be **drawn from scratch**. Ornament books, museum photos and other resource packs
  are references, never trace sources.

## 7. Colour and material vocabulary (verified sources only)

| Material or colour | Evidence | Label |
|---|---|---|
| Bronze, copper (metalworking) | Karakorum workshops, anvils for bronze work [S4] | HISTORICALLY VERIFIED |
| Iron with silver inlay | Met paiza 1993.256 [S28] | HISTORICALLY VERIFIED |
| Gold, silver-gilt, silver tablets | Marco Polo [S30] | HISTORICALLY VERIFIED (source account) |
| Silver, gilt serpents | Silver Tree, Rubruck [S10] | HISTORICALLY VERIFIED |
| Green, yellow and red glazed roof tile; terracotta dragon heads | Karakorum kilns and museum records [S6], [S15], [S16] | HISTORICALLY VERIFIED |
| Mud wall, earth | Rubruck [S10] | HISTORICALLY VERIFIED |
| Felt, wood lattice, canvas, rope | Ger craftsmanship [S46] | HISTORICALLY VERIFIED |
| Red leather armour | Takashima find [S42] | HISTORICALLY VERIFIED |
| Birch bark, bone, glass, gemstones | Karakorum workshops [S4] | HISTORICALLY VERIFIED |
| White (state) and black (war) banners | secondary [S20], [S21] | partly verified |
| Blue = Eternal Heaven | "Köke Möngke Tngri" [S35] | HISTORICALLY VERIFIED (concept) |
| Turquoise as an imperial material | not found in sources consulted | **unverified**: if used in SÜLD, label INSPIRED (steppe sky, later jewellery) |
| Parchment | A European/fantasy UI convention. The writing material of Mongol chancery documents was not verified in this session. | INSPIRED (fantasy UI convention) |
