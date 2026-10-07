# Class armour progression: visual specification

Status: **design document, 2026-10-07. No class armour visual exists yet** (`audit/visual-content-status.json`:
`classArmor` NOT_IMPLEMENTED; no `equipment/` assets in `resourcepack/`). Баатар is specified in full; the other
four classes get art direction only, to be detailed after the Баатар slice passes client QA.

Gameplay numbers come from the armour progression spec and are not changed here. Production steps are in
[ARMOR_ASSET_PIPELINE](ARMOR_ASSET_PIPELINE.md), palettes and labels in [ASSET_STYLE_GUIDE](ASSET_STYLE_GUIDE.md).

## 1. Tiers

| Tier | Name | Unlock (gameplay spec) | Rarity | Rarity colour (`ItemRarity.java`) | Visual theme |
|---|---|---|---|---|---|
| T1 | Эхлэл (beginning) | Armour level 1 | uncommon | `#1eff00` | Practical steppe gear: felt, leather, a little iron |
| T2 | Сайжруулсан (improved) | AL 12 + clear Говийн Булш | rare | `#0070dd` | Hard armour appears: iron lamellar over the deel |
| T3 | Элчин (envoy) | AL 24 + Мөсөн Оргил | epic | `#a335ee` | Elite construction: full lamellar coat, first 3D shoulder pieces |
| T4 | Хааны (royal) | AL 36 + Хар Хотын Балгас | legendary | `#ff8000` | Imperial: steel, silver inlay, layered pauldrons, rank insignia |
| T5 | Тэнгэрлэг (celestial) | AL 48 + Бурхан Халдуны Агуй | ancient | `#e6cc80` | Legendary: figurative ornament, blued steel, turquoise |
| T6 | Дээдэс (supreme) | AL 60 + Тэнгэрийн Ордон raid + Ascension III | mythic | `#ff4040` | Unmistakable silhouette: crest, back standard, night-sky steel |

The rarity colour belongs to the **tooltip name and UI frames only**. It is never the paint scheme of the armour,
because that would make the tiers colour swaps.

## 2. Technique per piece (all classes)

| Piece | Technique | Texture | Geometry budget | Notes |
|---|---|---|---|---|
| Helmet | Head-slot item, `EQUIPPABLE(HEAD)` **without** asset id, `ITEM_MODEL` → Blockbench cuboid model shown with the model's `head` display transform | 32×32 (T1–T3), 64×64 (T4–T6), one atlas per helmet | T1 ≤ 12, T2 ≤ 20, T3 ≤ 30, T4 ≤ 40, T5 ≤ 50, T6 ≤ 60 cuboids | The class silhouette lives here. Follows head rotation on the client. Not visible to the wearer in first person |
| Chest + arms + shoulders | Equipment asset, `humanoid` layer (body and both arms) | 128×64 (2× vanilla 64×32) | vanilla armour cuboids | Shoulder plates are painted on the arm top and upper outer faces. T3+ adds the pauldron attachment |
| Legs | Equipment asset, `humanoid_leggings` layer (waist and legs) | 128×64 | vanilla | Lamellar skirts and tassets are painted on the upper leg faces down to the knee |
| Boots | Equipment asset, `humanoid` layer, lower leg region (the same PNG as the chest) | in the chest's 128×64 | vanilla | Upturned-toe гутал shape is suggested by the paint only, since the boot cuboid cannot change |
| Pauldrons / back piece | `ItemDisplay` passenger on the player, `Player#hideEntity` for the wearer | 32×32 or 64×64 | ≤ 24 cuboids per attachment | T1–T2: 0 · T3–T4: 1 (pauldron pair) · T5–T6: 2 (pauldrons + back piece). Lags body turns slightly; keep shapes compact |
| Inventory icons (chest, legs, boots) | Flat item sprite on the same item via `ITEM_MODEL` | 32×32 | — | The helmet's icon is its 3D model in the GUI |
| Mount barding (Хүлэгчин) | Equipment asset, `horse_body` layer on a horse armour item | 64×64 | vanilla | Optional per tier from T3 |

Equipment JSON (`resourcepack/assets/suld/equipment/baatar_t3.json`):

```json
{ "layers": {
    "humanoid":          [ { "texture": "suld:baatar_t3" } ],
    "humanoid_leggings": [ { "texture": "suld:baatar_t3" } ] } }
```

The textures resolve to `assets/suld/textures/entity/equipment/humanoid/baatar_t3.png` and
`…/humanoid_leggings/baatar_t3.png`. HD layers are common practice but **not verified for 1.21.11**; this is the
first gate of the slice ([pipeline §3](ARMOR_ASSET_PIPELINE.md#3-gate-0-one-day-technical-spike)).

### 2.1 Rules every tier follows

1. **Seam grid.** Belt line, boot tops and the shoulder edge sit at the same pixel rows for every tier and class, so
   mixed-tier pieces (a T3 chest with T4 boots) still meet cleanly. Rows in 1× units: belt at body rows 10–12, boot
   top at leg row 7, shoulder edge at arm row 4.
2. **Texel density.** Player skins are 1×. Armour is 2×, so large shapes are painted in 2 × 2 clusters. 1-px detail
   is only for lacing, rivets and inlay lines. This keeps the armour from reading as a different art style.
3. **Silhouette first.** Each tier must change at least two of: helmet outline, shoulder mass, skirt length or
   shape, back piece. A palette change alone never counts as a new tier.
4. **Readability.** At 16 blocks a tier must be told apart from its neighbours with the image greyscaled.
5. **Gold budget.** Gold or gilt covers ≤ 3 % of painted pixels (T1–T3: 0 %), ≤ 5 % at T4–T5 and ≤ 4 % at T6.
6. **No glow on the armour itself.** Vanilla equipment layers are not emissive. Light comes from painted
   highlights; particles are cast-time only (§3.7).
7. **Combat compatibility.** Pauldrons must clear the off-hand shield pose (Баатар) and the bow draw (Мэргэн).
   Helmets must not hide the name tag; the tag sits above the head and crests go back, not straight up, past
   about 1.5 head heights.

## 3. БААТАР: heavy lamellar warrior (Rage)

Identity: the line warrior. A broad, heavy, front-facing silhouette with lamellar construction and reinforced
shoulders, sword-and-shield compatible. Lamellar armour, iron with leather and red-lacquered leather are
**VERIFIED** vocabulary ([MATERIAL_CULTURE §1](research/history/MATERIAL_CULTURE.md#1-armour-and-helmets)).
Every specific design below is **INSPIRED** unless marked otherwise.

### 3.1 T1 Эхлэл: "the recruit"

| Aspect | Spec |
|---|---|
| Silhouette | Almost the base human shape; a slight bulk at the chest from a quilted coat. Low, rounded head |
| Helmet | Felt-and-leather cap with a fur brim and four iron strips meeting at the crown. 8–12 cuboids, 32×32 |
| Chest / shoulders | Quilted felt deel with a diagonal flap, rawhide belt, **one** small row of rawhide lamellae across the chest. Shoulders: plain felt with a stitched edge |
| Legs | Felt trousers, the deel hem to mid-thigh, leather knee patches |
| Boots | Leather гутал, dark sole stripe, upturned toe suggested by a lighter tip |
| Materials | Felt, rawhide, leather, dull iron (strips only) |
| Palette | felt `#6B5A45` / `#9A8667`, rawhide `#A8865E`, leather `#4A2E1E`, iron `#5E6168` / `#8D9199`, lacing `#7A3B2A` |
| Ornament | Stitch lines only. No metal ornament |
| Attachments, VFX | None |

### 3.2 T2 Сайжруулсан: "the soldier" (from T1: soft becomes hard)

| Aspect | Spec |
|---|---|
| Silhouette | Torso visibly squarer: an iron lamellar cuirass over the deel. Head taller |
| Helmet | Segmented iron bowl helmet with a short spike, brow band and **leather aventail** hanging at the back and sides (the first helmet to break the head outline). 14–20 cuboids |
| Chest / shoulders | Iron lamellar cuirass, 5 laced rows, red-lacquer leather edging. **Painted shoulder lames**: 2 rows over the arm tops. Leather bracers |
| Legs | The deel shows below the cuirass; the first iron-scaled thigh guards (2 rows) |
| Boots | Leather boots with an iron shin plate (painted) |
| Materials | Iron lamellar, red-lacquered leather (Takashima find: VERIFIED material), leather, bronze rivets |
| Palette | iron `#5E6168` / `#8D9199` / `#C9CDD3`, red lacquer `#7A1E1E` / `#A3302A`, leather `#4A2E1E`, bronze `#8C5A2B` |
| Ornament | Lacing pattern as ornament; one bronze boss on the belt |
| Attachments, VFX | None |

### 3.3 T3 Элчин: "the chosen" (from T2: torso armour becomes a full coat; first off-body mass)

| Aspect | Spec |
|---|---|
| Silhouette | A long lamellar coat to the knee. **First 3D pauldrons** widen the shoulders by about 30 %. Tall pointed helmet |
| Helmet | Tall pointed iron bowl, cheek guards, nape guard, plume tube with a **white horsehair tassel** (white: the VERIFIED state-banner colour, used as INSPIRED). 22–30 cuboids |
| Chest / shoulders | Red-lacquered lamellae as the main material, bronze-edged rows, a steel chest plaque. Pauldron attachment: 2 × 3 laced lames, about 1.4× the arm's width |
| Legs | Lamellar skirt in front and back panels with a centre split, to the knee; black cloth under it |
| Boots | Boots with laced lamellar greaves |
| Materials | Lacquered leather lamellae, bronze, steel, black cloth, horsehair |
| Palette | lacquer `#8E2420` / `#B8402F`, bronze `#8C5A2B` / `#B9803F`, cloth `#1F1B1E`, steel `#8D9199`, horsehair `#E8E4DA` |
| Ornament | Horn-scroll (эвэр хээ) border on the skirt hem: INSPIRED, drawn from scratch |
| Attachments, VFX | 1 attachment (pauldrons). No VFX |

### 3.4 T4 Хааны: "imperial guard" (from T3: lamellae become plate-and-lamellar; insignia appear)

| Aspect | Spec |
|---|---|
| Silhouette | Heavier and more architectural: wide three-lame pauldrons, a broad belt, longer skirt panels. The helmet adds a visor brow and a tall spike finial |
| Helmet | Steel helmet with a riveted brow plate and half-visor, a long lamellar nape guard and a tall finial with a small horsehair ring. 30–40 cuboids, 64×64 |
| Chest / shoulders | Larger steel lames with silver-inlay plaques on a dark iron ground (iron with silver inlay: VERIFIED material, Met paiza). A round chest disc. Pauldrons: 3 stacked lames with silver edges |
| Legs | Steel-edged skirt panels over a deep-red silk under-robe that shows at the hem |
| Boots | Steel sabatons painted over the boot, silver toe cap |
| Materials | Steel, dark iron with silver inlay, deep-red silk, black lacquer, restrained gold |
| Palette | steel `#9AA0A8` / `#C9CDD3`, dark iron `#3A3D44`, silver `#D6D9DE`, silk `#6E1A22`, lacquer `#1B1A1F`, gold `#C9A04A` (≤ 5 %) |
| Ornament | Belt plaques laid out like a **rank insignia** (INSPIRED by Marco Polo's paiza ranks, which are VERIFIED as his account). No real seal text |
| Attachments, VFX | 1 attachment (pauldrons, larger). No idle VFX |

### 3.5 T5 Тэнгэрлэг: "legend" (from T4: material and figurative ornament change)

| Aspect | Spec |
|---|---|
| Silhouette | The pauldrons become swept, layered lames rising toward the neck (not wings). A **wolf-mask visor** changes the face. A short half-cape at the back (the second attachment) |
| Helmet | Blued-steel helmet, wolf-mask visor (snout and brow), swept nape lames. 40–50 cuboids |
| Chest / shoulders | Blued steel lamellae; **wolf-and-eagle relief** on the chest disc (ORIGINAL FICTION, not a historical emblem); turquoise inlay lines |
| Legs | Long split skirt, alternating blued-steel and bone-white lames |
| Boots | Greaves with turquoise studs |
| Materials | Blued steel, turquoise (INSPIRED; as an imperial material it is **unverified**), aged gold, bone |
| Palette | blued steel `#3B4A66` / `#5C6F94`, turquoise `#2E8C86` / `#5FBFB2`, aged gold `#B08A3E`, bone `#D8CFB8`, dark red `#5A1418` |
| Ornament | Figurative: wolf and eagle. Each must be an original drawing |
| Attachments, VFX | 2 (pauldrons + half-cape). Cast-time accent only (§3.7) |

### 3.6 T6 Дээдэс: "of the sky" (from T5: silhouette becomes unmistakable)

| Aspect | Spec |
|---|---|
| Silhouette | A tall crest of **nine white horsehair tassels** sweeping back (INSPIRED by the VERIFIED nine-tailed white banner of 1206, *Secret History* §202), and a small **tug standard** on the back. Visible as T6 from 30+ blocks |
| Helmet | Night-sky steel, a crown band of star rivets, cheek guards that close to a narrow face opening, the nine-tassel crest. 50–60 cuboids, 64×64 |
| Chest / shoulders | Night-sky steel lamellae with silver-white edges and star rivets; a sky-blue chest disc (sky = INSPIRED by Köke Möngke Tngri, a VERIFIED concept). Pauldrons: the T5 sweep, simplified, so the crest stays the focal point |
| Legs | Full lamellar skirt with silver hem lames; a sky-blue under-robe |
| Boots | Silver-white sabatons |
| Materials | "Sky-forged" steel (ORIGINAL FICTION), silver, white horsehair, sky-blue cloth, minimal gold |
| Palette | sky steel `#1E2A44` / `#34507E`, silver-white `#E6EAF0`, sky `#4E7FC0`, horsehair `#E8E4DA`, star rivets `#F4F1E6`, gold `#C9A04A` (≤ 4 %) |
| Ornament | Star-rivet constellations (ORIGINAL FICTION). **No Soyombo**, no real tamga |
| Attachments, VFX | 2 (pauldrons + back tug standard; the standard replaces the T5 cape). Optional idle mote: 1 END_ROD every 20 ticks, off in crowds |

### 3.7 Tier VFX (Баатар)

Armour never glows. Tier cues come from the **cast** effects in [SKILL_VFX_SPEC](SKILL_VFX_SPEC.md): T1–T2 none,
T3–T4 one extra impact accent per cast (+ ≤ 10 particles), T5–T6 the tier accent colour in the shockwave decal.
The existing held-weapon auras (`ClassWeapons.auras()`, every 6 ticks) are left as they are.

## 4. The other four classes (direction only)

Every class follows §2 and §2.1. One row is one tier; the change from the row above is in **bold**.

### 4.1 МЭРГЭН: light layered archer (Focus)

Asymmetric (bow-arm bracer, quiver side), layered leather and felt, birch bark and horn texture vocabulary
(VERIFIED bow materials). Compact recurve bow with short tips (VERIFIED, 13th-century Tsagaan-Khad bow). If the
equipment layers mirror the left limbs onto the right ones (checked in
[gate 0](ARMOR_ASSET_PIPELINE.md#3-gate-0-one-day-technical-spike)), one-arm details move to an attachment.

| Tier | Silhouette and change | Helmet | Attachment |
|---|---|---|---|
| T1 | Felt hunting deel, belt pouch | Fur cap with ear flaps | — |
| T2 | **Leather lamellar vest, left bracer** | Leather hood-cap, face scarf | — |
| T3 | **Layered leather with iron scale on the draw shoulder only** | Light iron cap over the hood | **Quiver on the back** |
| T4 | **Lacquered leather lamellae, silver-inlay bracer, long split coat tails** | Cap with a nape flap and a small finial | Quiver |
| T5 | **Wolf-hide mantle over one shoulder, turquoise** | Half-mask hood | Quiver + mantle |
| T6 | **White horsehair at the shoulder, night-blue layers** | Hood with a sky-steel brow and back-swept tassels | Quiver of sky arrows + mantle |

### 4.2 БӨӨ: shaman (Spirit)

Ritual fabrics, ribbons and fringes, bone and iron pendants, bronze discs, a fringed headdress. The existing
`shaman` NPC skin (`tools/pack/gen_skins.py`, tasselled headdress) sets the base look. **Shaman dress is not covered
by SÜLD's history research.** Mongolian shamanism is a living tradition: treat every element as INSPIRED, avoid
caricature, never copy specific ritual objects, and get a knowledgeable reviewer before public use.

| Tier | Silhouette and change | Helmet / headdress | Attachment |
|---|---|---|---|
| T1 | Felt robe, a few hanging ribbons | Headband with an eye fringe | — |
| T2 | **Ribbon rows down the coat and legs, iron pendants** | Taller band, longer fringe | — |
| T3 | **Bone ornaments, layered fringe skirt** | **Antler-like headdress** (INSPIRED, unverified) | **Back talisman cluster** |
| T4 | **Bronze discs on the chest and back** | Tall headdress with pendants | Talisman cluster |
| T5 | **Spirit motifs; ongon figures as stylised appliqué (ORIGINAL FICTION)** | Masked headdress | Talismans + ribbon mantle |
| T6 | **White and sky-blue ritual coat, sky-gate motif** | Crown headdress with sky discs | Sky-disc frame on the back |

### 4.3 ДАРХАН: forge warrior (Heat)

Heavy riveted plates, leather apron, gauntlets, soot. Bronze and metal workshops are VERIFIED for Karakorum
([KHARKHORUM.md](research/history/KHARKHORUM.md)). Forge motifs are ORIGINAL FICTION. "Glow" is painted only.

| Tier | Silhouette and change | Helmet | Attachment |
|---|---|---|---|
| T1 | Leather apron over a deel, heavy gloves | Leather cap | — |
| T2 | **Riveted iron plates on the chest and forearms** | Iron skullcap with a soot guard | — |
| T3 | **Massive riveted pauldrons, apron plates on the legs** | **Visored mask helmet with heat slits** | Pauldrons |
| T4 | **Bronze-and-steel layering, anvil and hammer emblems** | Mask helmet with a crest ridge | Pauldrons |
| T5 | **Molten-crack painting along the seams** | Helmet with ember slits | Pauldrons + tool rack on the back |
| T6 | **Dark "sky-iron" (ORIGINAL FICTION) with white-hot seams** | Crowned mask helmet | Pauldrons + small anvil-standard on the back |

### 4.4 ХҮЛЭГЧИН: cavalry (Momentum)

Built for the saddle: a long **split** lamellar skirt (so the riding pose does not clip visually), a lighter upper
body, a long nape guard, a lance pennon. The mounted look is a QA row of its own. The `horse_body` barding tiers
follow the rider's tier from T3.

| Tier | Silhouette and change | Helmet | Attachment / mount |
|---|---|---|---|
| T1 | Riding deel, leather boots to the knee | Fur cap | — |
| T2 | **Split lamellar riding skirt** | Iron cap with a short nape guard | — |
| T3 | **Lamellar torso, shoulder lames** | **Helmet with a long nape guard and pennon tube** | Pauldrons · **horse barding T3** |
| T4 | **Imperial cavalry: silver-edged lames, sash** | Tall helmet with a horsehair ring | Pauldrons · barding T4 |
| T5 | **Wind-swept cut, turquoise, mantle streaming back** | Helmet with swept cheek wings | Pauldrons + mantle · barding T5 |
| T6 | **Sky rider: night-sky steel, white horsehair** | Crest helmet (smaller than Баатар's, swept flat for speed) | Pauldrons + pennon standard · barding T6 |

## 5. Texture and asset sizes (per set)

| File | Size | Count per set |
|---|---|---|
| `textures/entity/equipment/humanoid/<class>_t<n>.png` | 128×64 | 1 |
| `textures/entity/equipment/humanoid_leggings/<class>_t<n>.png` | 128×64 | 1 |
| `models/item/armor/<class>_t<n>_helmet.json` + texture | 32×32 (T1–T3) / 64×64 (T4–T6) | 1 + 1 |
| Icons `textures/item/armor/<class>_t<n>_{chest,legs,boots}.png` | 32×32 | 3 |
| Attachment model(s) + texture | 32×32 or 64×64 | 0–2 |
| `textures/entity/equipment/horse_body/khulegchin_t<n>.png` | 64×64 | Хүлэгчин T3–T6 only |

256×128 layers are **not** planned. They would need a `justified_large` entry in `assets/registry/pack_budget.json`
and a client test that shows a visible gain.

## 6. Open questions for the owner

1. RESOLVED (owner, Stage C3b): the armour tiers were renamed Эхлэл · Сайжруулсан · Элчин · Хааны · Тэнгэрлэг · Дээдэс so no tier name equals a rarity name (a unit test enforces it). Originally: the T5 name **Домогт** was also the display name of the EPIC rarity (`ItemRarity.EPIC`, "Домогт"), and T5 is
   *ancient* rarity. Keep it, or rename the tier (for example "Домгийн" belongs to MYTHIC, so a third word is needed)?
2. Pieces upgrade together, or one at a time? This decides how much the seam grid (§2.1.1) matters.
3. May the T6 crest and back standard hide during combat in crowded areas (LOD), or must they always show?

## 7. Status

| Item | Status |
|---|---|
| Tier visual rules (§2) | CONCEPT |
| Баатар T1–T6 specs | CONCEPT |
| Мэргэн, Бөө, Дархан, Хүлэгчин direction | CONCEPT |
| Equipment assets, helmet models, attachments | NOT_IMPLEMENTED |
| HD equipment-layer behaviour on 1.21.11 | NOT_IMPLEMENTED (unverified, gate 0) |
| Mounted look (Хүлэгчин), horse barding | NOT_IMPLEMENTED |
| Client QA | NOT_IMPLEMENTED |
