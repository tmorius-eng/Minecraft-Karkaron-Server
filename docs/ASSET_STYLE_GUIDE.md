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

## The survival inventory (E)

The source is the owner's mock-up `assets/art/source/gui_inventory.webp`. Gemini made one edit of it,
`gui_inventory_keyed.png` (`tools/art/inventory_prompts.json`): the scene was replaced with flat magenta, and the
player was taken out of the portrait, because the game draws the player there. `tools/pack/hd_art.inventory`
builds three things from that edit.

**The window.** It is `minecraft:textures/gui/container/inventory.png`, the vanilla 256 px sheet at 4x.
- Every slot frame sits on its vanilla slot. The inventory and hot bar are warped column by column; the armour,
  off-hand, crafting, result and portrait wells are pasted onto their vanilla rectangles.
- The clouds and knots are moved into the free parts of the layout.

**The empty-slot marks.** These are the helmet, chestplate, leggings, boots and shield placeholders, lifted off
the painted wells. Next to them is a recipe button in the same style. Both are in `textures/gui/sprites/`, at 4x.

**The decor around the window.** This covers the spires, the crest, the banners, the tassels and the bottom
medallion.
- The client draws the inventory texture only inside its 176 x 166 window, so this decor cannot go there.
- It is one glyph of `minecraft:default` (`suld:font/gui/inventory_decor.png`), drawn through the screen title. The
  lang key `container.crafting` in en_us, en_gb, mn_mn and ru_ru is set to white, a negative space and the glyph.
- Inside the window the glyph is transparent, except in the 7 px margin, so it never covers an item.

A crafting table uses the same lang key, so SÜLD gives crafting tables a plain title, «Урлах ширээ»
(`MenuListener.onWorkbench`). Without that, the decor would appear around the crafting table in the wrong place.

How it looks in a real client is **MANUAL_QA_REQUIRED**. Points to check:
- GUI scale 3 and 4;
- with the recipe book open (the left banner overlaps the book's right edge);
- with potion effects listed beside the window;
- in a language other than the four above (there the title shows the plain word "Crafting" and no decor).

## Card menus (welcome, main menu, cosmetics, list menus)

The source is the owner's welcome window, `assets/art/source/gui_welcome.webp`. It has a header bar and three cards;
each card is a coloured banner over a painting, framed in gold with gems at the corners.

- **Welcome:** this is the painting itself, warped so each card sits on its 3x3 slot block (`hd_art.welcome`). The
  labels are pixel-font text on the banners.
- **The painted «Сүлд Цэс» (six cards, now behind the hub's «/menu» card) and cosmetics (eight portrait cards):** these are built from the painting's pieces
  (`hd_art.CardKit`): the header, the gem-cornered card frame as a 9-slice, and the knotted banner. A banner is
  recoloured per card. If a label does not fit, the banner drops its knots, or the label is drawn at a smaller pixel
  size. The paintings are the existing `card_*.png`, cropped to fill.
- **Generic list menu (`frame`):** the same header and rim, with the inventory's gold wells.

All of these are at 4x and use the same code points as before. Titles on painted backgrounds start 10 px further
right (`Menu.TITLE_INDENT`), so they clear the header's corner knot.

The dungeon ladder, the quest map, the quest book and the skill map keep their own paintings.

## Сүлд Цэс and every chest screen

- **The main menu is the «Командын Өргөө» hub** (`tools/pack/hub_kit.py`, `docs/UI_HUB.md`): the owner's mock-up `gui_hub.png` cut into a 4x kit (frame, pages, tab glow, card glow, footer buttons) layered in the title. The painted Сүлд Цэс below is still in the pack and opens from the hub's «/menu» card (`Menus.classic`).
- **Сүлд Цэс.** The source is the owner's painting `assets/art/source/gui_main.webp`, warped onto the 6-row chest
  (`hd_art.main_menu`).
  - The title and the card labels are painted. Card 3 opens quests and dungeons, so its painted «ЭРДЭН» is painted
    over and re-lettered «ЭРЭЛ».
  - The bottom bar sits on the chest screen's own "Inventory" label row, so the glyph is 138 px tall. The bar's
    corner knots are cleared where that label is drawn.
  - `Menus.classic` draws no title text, because the title is painted.
- **Every chest screen.** `minecraft:textures/gui/container/generic_54.png` (`hd_art.chest`) uses the same
  style: navy cloth and a gold well on every chest slot. The player inventory and hot bar are the survival
  inventory's own, moved 56 px down.
  - This covers SÜLD menus and world chests, double chests, ender chests and barrels.
  - The screen's labels (`container.inventory`, `container.chest`, `chestDouble`, `enderchest`, `barrel`) are gold,
    because the vanilla dark grey does not read on navy. The same gold "Inventory" label also appears on vanilla
    grey screens such as the furnace.
