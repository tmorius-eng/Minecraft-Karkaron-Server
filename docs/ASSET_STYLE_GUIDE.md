# SÜLD asset style guide

Status: **design document, 2026-10-07.** Applies to every new asset: armour, weapons, mobs, bosses, NPCs, relics,
VFX, UI and props. Existing procedural art (class weapons, HUD, skins) is grandfathered until it is reworked.
History labels follow [docs/research/history/README.md](research/history/README.md).

## 1. Visual pillars

| Pillar | Means in practice |
|---|---|
| Mongol imperial | Lamellar, laced construction, deel cuts, sashes, fur and felt hats, paiza-like insignia, horse culture. INSPIRED by verified material culture, never a reconstruction claim |
| Kharkhorum | A multi-faith trading capital: glazed roof tiles (green, yellow, red), mud walls, workshops of bronze, iron, glass and bone (VERIFIED finds) |
| Dark fantasy | Soot, night lighting, worn edges, ominous banners, spirits. Restraint: menace comes from silhouette and value, not from spikes and skulls |
| Steppe | Wide horizons; ochre, dry grass and sky. Assets must read against a bright, open background |
| Materials | Aged iron and steel, leather, red lacquer, bronze, felt, horsehair, bone, birch bark; turquoise and gold as accents |
| Restrained ornament | Ornament at edges and focal points only, drawn from scratch |

## 2. Core palette

Hex values are design targets for painting. In-game textures are quantised to these ramps.

| Role | Swatch hex | Label | Use |
|---|---|---|---|
| Steppe night (darkest) | `#1B1A1F` | — | Deepest shadow, lacquer black |
| Soot | `#2B2A2E` | — | Dark fur, forge soot, shadow cloth |
| Aged iron | `#3A3D44` `#5E6168` `#8D9199` | VERIFIED material (iron) | Common armour metal |
| Steel | `#9AA0A8` `#C9CDD3` | — | Higher-tier metal, edges |
| Silver / inlay | `#D6D9DE` `#E6EAF0` | VERIFIED (iron with silver inlay, Met paiza) | Inlay lines, T4+ edges |
| Bronze | `#6E4522` `#8C5A2B` `#B9803F` | VERIFIED (Karakorum workshops) | Rivets, edging, bosses |
| Gold (restrained) | `#B08A3E` `#C9A04A` `#E6C878` | VERIFIED (gold tablets) | ≤ 5 % of any texture; never a base colour |
| Leather | `#4A2E1E` `#6E4A2F` | — | Straps, boots, grips |
| Rawhide | `#A8865E` | — | Lacing, starter armour |
| Felt | `#6B5A45` `#9A8667` `#B8AA92` | VERIFIED (ger craft) | Coats, hats, starter gear |
| Red lacquer | `#7A1E1E` `#A3302A` `#B8402F` | VERIFIED (red leather armour, Takashima) | Lamellae, armour edging |
| Deep red silk | `#5A1418` `#6E1A22` | — | Imperial under-robes, banners |
| Turquoise | `#2E8C86` `#5FBFB2` | INSPIRED (as an imperial material it is **unverified**) | T5+ inlay, shaman ornament |
| Sky blue (Тэнгэр) | `#34507E` `#4E7FC0` | INSPIRED (Köke Möngke Tngri is a VERIFIED concept; a colour mapping is ours) | T6, sky motifs |
| Хөх Сүлд blue | `#2F5FA8` | **ORIGINAL FICTION** (historic banners were white and black) | The relic only |
| White horsehair | `#E8E4DA` | VERIFIED (white state banner, 1206) | Tassels, crests |
| Bone | `#D8CFB8` | VERIFIED material (bone workshops) | Ornaments, shaman gear |
| Parchment | `#D9C9A3` `#A88F62` | INSPIRED (fantasy UI convention; not a Mongol material claim) | UI panels, maps |
| Steppe ochre / grass | `#B89A5E` `#7E8A4A` | — | Props, terrain-facing art |

### 2.1 Class accents (VFX and UI only, never armour base colours)

| Class | Accent (code) | Desaturated armour accent |
|---|---|---|
| БААТАР | `#FF5A46` | red lacquer `#A3302A` |
| МЭРГЭН | `#78E678` | birch and horn ochre `#B89A5E`, green felt `#4E6B3A` |
| БӨӨ | `#AA6EFF` | dark purple felt `#4A3560` |
| ДАРХАН | `#FF9632` | bronze `#8C5A2B`, ember `#E06A2A` |
| ХҮЛЭГЧИН | `#5AAAFF` | steel blue `#5C6F94` |

## 3. Readability hierarchy

What a player must read, in order, and at what distance:

| Order | Cue | Read at | Rule |
|---|---|---|---|
| 1 | Silhouette | 32 blocks (bosses, NPC category), 16 blocks (armour tier) | Each category and tier has a distinct outline in greyscale |
| 2 | Value (light vs dark) | 16 blocks | Two dominant values per asset plus one accent; never an even mid-grey |
| 3 | Material | 8 blocks | Metal reads by specular pixels; cloth and leather are matte |
| 4 | Hue | 8 blocks | One dominant hue family per asset; the accent ≤ 15 % |
| 5 | Ornament | 4 blocks | Only at edges, the chest centre and the helmet brow |
| 6 | VFX | on action | Effects frame the action and stop. No permanent glow on gear |

Hierarchy across the screen: **hostiles and telegraphs > the player's own effects > allies > NPCs > props**. A
boss telegraph must never be drowned out by player particles (see the budgets in
[SKILL_VFX_SPEC §4](SKILL_VFX_SPEC.md#4-budgets-and-performance-rules)).

## 4. Pixel rules

1. **Texel density.** Item sprites 16 px per block (32 px for class weapons). Skins 1×. Equipment layers 2×
   (128×64), painted in 2 × 2 clusters with 1-px detail only for lacing, rivets and inlay.
2. **Light from the top left**, as vanilla. Shadows shift cool (toward blue-grey), highlights warm.
3. **3–5 shades per material ramp.** No gradients wider than the ramp; no dithering noise; no photo texture.
4. **Outlines.** Item sprites keep the dark auto-outline of `tools/pack/gen_item_textures.py`. Models and entity
   textures have no black outline; separation comes from value.
5. **No AI artefacts.** Generated images are references. Every shipped pixel is placed or quantised on purpose.
   No smeared detail, no fake text, no asymmetry that is not designed.
6. **Bright pixels are rare.** Values above `#E6E6E6` cover ≤ 3 % of a texture (highlights, eyes, rivets).

## 5. Rarity presentation

Rarity is presented by the UI and effects, **not by repainting the model**. Colours and names from
`suld-api/.../item/ItemRarity.java`:

| Rarity | Name (game) | Colour | Presentation |
|---|---|---|---|
| common | Энгийн | `#9d9d9d` | Name colour |
| uncommon | Ховор | `#1eff00` | Name colour |
| rare | Нандин | `#0070dd` | Name colour |
| epic | Домогт | `#a335ee` | Name colour; planned: tooltip frame (1.21.2+ `tooltip_style`, client test needed) |
| legendary | Алдарт | `#ff8000` | Name colour; enchantment glint (already forced by `ItemFactory`); drop announcement |
| ancient | Эртний | `#e6cc80` | As legendary; planned: own tooltip frame |
| mythic | Домгийн | `#ff4040` | As legendary; planned: own tooltip frame and a drop beam |
| unique | Цор ганц | `#00ffd0` | Relic presentation (shrine, glint) |

Armour **tiers** map to rarities (T1 uncommon … T6 mythic), but the tier is shown by the armour's construction
([ARMOR_PROGRESSION_VISUAL_SPEC](ARMOR_PROGRESSION_VISUAL_SPEC.md)). The glint on legendary-and-above armour should
be tested in the client: on HD armour it may hide the painting. If it does, turn it off for class armour.

## 6. Ornament

* Families: horn scroll (эвэр хээ), meander-like interlace, knots (өлзий-style), lacing patterns, star rivets.
  All are INSPIRED or ORIGINAL; the өлзий is **not** an imperial 13th-century motif.
* Coverage ≤ 15 % of a texture's area; on starter tiers ≤ 5 %.
* Drawn from scratch. Never traced from museum photos, ornament books, scrolls or other packs.
* Figurative motifs (wolf, eagle, horse) are ORIGINAL FICTION when used as emblems.

## 7. History labels for motifs

| Motif | Label | Allowed use |
|---|---|---|
| Lamellar armour, laced plates, iron with leather | VERIFIED | Armour construction |
| Red-lacquered leather armour | VERIFIED (Takashima 1281) | Armour material |
| Compact recurve bow with short tips | VERIFIED (Tsagaan-Khad bow) | Мэргэн bows |
| White nine-tailed banner (1206) | VERIFIED (*Secret History* §202) | INSPIRED crests, tug standards |
| Tug / sülde banner | VERIFIED concept | INSPIRED standards; the Хөх Сүлд relic |
| Хөх Сүлд blue | ORIGINAL FICTION | The relic only; never "the Mongol banner colour" |
| Paiza tiers (silver → gold → lion → gerfalcon) | VERIFIED as Marco Polo's account | INSPIRED insignia; no real ʼPhags-pa text |
| Güyük's seal (1246) | VERIFIED | Square-seal stamp motif only; never its text or layout |
| Tamga | VERIFIED concept | Every clan tamga is ORIGINAL; never labelled historical |
| Uyghur-Mongolian script | VERIFIED | Decorative only after a literate reader proofreads it |
| Glazed tiles, dragon-head finials | VERIFIED (Karakorum kilns) | Kharkhorum props and roofs |
| Өлзий knot | VERIFIED as a traditional ornament; not as imperial | UI and ornament, labelled INSPIRED |
| Turquoise | unverified as an imperial material | INSPIRED accent |
| Shaman headdress, ribbons, mirrors | not covered by the research set | INSPIRED, respectful, reviewed before public use |
| **Soyombo** | 1686, a modern national symbol | **Never** in empire-period art. Recommendation: not in game art at all |

## 8. Do not

* Do not make everything gold, glowing or oversized.
* Do not use the rarity colour as the armour's paint.
* Do not put the Soyombo on any item, banner or UI presented as empire-period.
* Do not present invented tamga, seals or inscriptions as historical. Item names that suggest a real emblem (for
  example `jewel.chingis_tamga` in `items/jewelry.json`) must use an original design, labelled ORIGINAL FICTION.
* Do not claim blue was the imperial banner colour.
* Do not use European plate silhouettes (full visored great helms, fluted Gothic plate) or Qing/Manchu long bows.
* Do not trace or copy reference images.
* Do not ship raw AI output, a raw mesh, or a 4K texture.
* Do not use real-person likenesses for NPCs or bosses.

## 9. Gemini concept-sheet style string

The existing `tools/art/prompts.json` style asks for a "Minecraft screenshot" look, which is right for menu cards
and wrong for production sheets. Concept sheets use a separate prompt file with a style string like:

> Character concept sheet, front view, side view and back view on one canvas, orthographic, neutral mid-grey
> background, even studio light, full body, clear silhouette, readable shapes, 13th-century-inspired Mongol steppe
> armour, lamellar plates laced with leather, worn iron and red lacquer, restrained ornament, no text, no letters,
> no watermark, no Soyombo symbol, no European plate armour, no glowing effects.

## 10. Status

| Item | Status |
|---|---|
| Pillars, palette, hierarchy, pixel rules | CONCEPT |
| History label table | CONCEPT (from the 2026-10-07 research set) |
| Tooltip frames per rarity | NOT_IMPLEMENTED |
| Concept-sheet prompt file | NOT_IMPLEMENTED |
| Style review of existing procedural art | NOT_IMPLEMENTED |
