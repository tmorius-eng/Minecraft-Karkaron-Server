# Armour asset pipeline

Status: **design document, 2026-10-07. No class armour asset exists.** No `equipment/` JSON, no
`textures/entity/equipment/`, and no code sets the equippable component (`ItemFactory` only sets the legacy
`custom_model_data` int, `ItemFactory.java:91`). This is the armour-specific part of
[ASSET_PRODUCTION_PIPELINE](ASSET_PRODUCTION_PIPELINE.md). The visual targets are in
[ARMOR_PROGRESSION_VISUAL_SPEC](ARMOR_PROGRESSION_VISUAL_SPEC.md).

## 1. Flow

```
spec (tier sheet in ARMOR_PROGRESSION_VISUAL_SPEC)
 → Gemini concept sheet: front / side / back (1K drafts → one 2K reference)
 → Meshy reference: full-body image-to-3D of the approved sheet (proportions and colour only)
 → Blender headless: normalise to 2 blocks tall → orthographic views → colour bake onto the humanoid UV template
 → pixel texture: 2 × 128×64 equipment layers (quantised to the palette, then hand clean-up)
 → Blockbench: helmet item model (+ pauldron / back attachment from T3) → bbmodel_validate
 → resource pack: equipment JSON, layer PNGs, item definitions, models, icons
 → SÜLD: armour definition carries the visual ids → ItemFactory sets EQUIPPABLE (+ assetId) and ITEM_MODEL
 → client QA (§7) → registry status
```

## 2. What each piece is made of

| Piece | Pack files | Runtime |
|---|---|---|
| Helmet | `items/armor/<stem>_helmet.json` → `models/item/armor/<stem>_helmet.json` → `textures/item/armor/<stem>_helmet.png` | `ITEM_MODEL = suld:armor/<stem>_helmet` + `EQUIPPABLE(HEAD)` with **no** asset id, so the item model is drawn on the head |
| Chest | `equipment/<stem>.json` (shared), `textures/entity/equipment/humanoid/<stem>.png`; icon `items/armor/<stem>_chest.json` | `EQUIPPABLE(CHEST).assetId(suld:<stem>)` + `ITEM_MODEL` (icon) |
| Legs | the shared equipment JSON, `textures/entity/equipment/humanoid_leggings/<stem>.png`; icon | `EQUIPPABLE(LEGS).assetId(suld:<stem>)` |
| Boots | the shared `humanoid` PNG (lower leg region); icon | `EQUIPPABLE(FEET).assetId(suld:<stem>)` |
| Pauldrons / back (T3+) | `items/armor/<stem>_pauldrons.json`, `…_back.json` + models + textures | `ItemDisplay` passenger, hidden from the wearer |
| Horse barding (Хүлэгчин T3+) | `equipment/khulegchin_t<n>_horse.json` with a `horse_body` layer | `EQUIPPABLE(BODY)` on a horse armour item, `allowedEntities` horse |

## 3. Gate 0: one-day technical spike

Before any concept art, prove the rendering in a real 1.21.11 client with throwaway test textures (one solid
colour per body part and a 1-px grid):

| Check | Pass if |
|---|---|
| `EQUIPPABLE(CHEST).assetId(suld:test)` with a 64×32 `humanoid` layer | Renders on a player, a Mannequin and a husk |
| The same with 128×64 | Renders at 2× density without blur or offset (HD is common practice but unverified) |
| `humanoid_leggings` at 128×64 | Leg and waist regions line up |
| UV mirroring | Confirm whether the left arm and leg mirror the right ones (the vanilla 64×32 layout). If they do, single-arm details (Мэргэн bracer) must move to an attachment or the helmet |
| Helmet: `EQUIPPABLE(HEAD)` without asset id + `ITEM_MODEL` | The item model is drawn on the head and follows head rotation; the `head` display transform applies |
| `ItemDisplay` passenger on a player, `hideEntity` for the wearer | The wearer does not see it in first person; others see it follow with ≤ 3 ticks of lag on a 90° turn |
| Riding a horse | Attachments and the helmet stay in place on a horse |
| Glint (`setEnchantmentGlintOverride`) on HD armour | Decide on/off for class armour |
| Pack reload | The pack loads with no errors in the client log |

Result goes into `audit/qa/visual/gate0.md` (screenshots in the same folder). If HD layers fail, all armour
textures drop to 64×32 and this spec is revised before art starts.

## 4. File naming

`<stem>` = `<class>_t<n>`, class in `baatar | mergen | boo | darkhan | khulegchin`, `n` in 1–6.

| File | Path |
|---|---|
| Concept drafts | `assets/art/concepts/armor/<stem>_draft<k>.png` (1K) |
| Production reference | `assets/art/concepts/armor/<stem>_ref2k.png` (git: a ≤ 1 MB downscale only) |
| Meshy reference | `assets/sources/<stem>__<task_id>.glb` (git-ignored, flat folder) |
| Orthographic views | `assets/previews/armor/<stem>_{front,side,back}.png` |
| Blockbench helmet | `assets/models/blockbench/armor/<stem>_helmet.bbmodel` |
| Blockbench attachments | `assets/models/blockbench/armor/<stem>_{pauldrons,back}.bbmodel` |
| Equipment asset | `resourcepack/assets/suld/equipment/<stem>.json` |
| Body layer | `resourcepack/assets/suld/textures/entity/equipment/humanoid/<stem>.png` (128×64) |
| Leggings layer | `resourcepack/assets/suld/textures/entity/equipment/humanoid_leggings/<stem>.png` (128×64) |
| Helmet model and texture | `resourcepack/assets/suld/models/item/armor/<stem>_helmet.json`, `textures/item/armor/<stem>_helmet.png` |
| Item definitions | `resourcepack/assets/suld/items/armor/<stem>_{helmet,chest,legs,boots,pauldrons,back}.json` |
| Icons | `resourcepack/assets/suld/textures/item/armor/<stem>_{chest,legs,boots}.png` (32×32) |
| In-game QA shots | `audit/qa/visual/<stem>/` |
| Report | `assets/reports/armor.<stem>.json` |

Registry id: `armor.<stem>` (one entry per tier covering its four pieces and attachments; `assets/registry.json`).

## 5. Steps in detail

### 5.1 Concept sheet (Gemini)

* A new prompt file, `tools/art/prompts_armor.json`, with the concept-sheet style string from
  [ASSET_STYLE_GUIDE §9](ASSET_STYLE_GUIDE.md#9-gemini-concept-sheet-style-string), `"size": "1K"` for drafts.
* One prompt per tier, built from that tier's spec table: silhouette, helmet, chest, legs, boots, materials,
  palette words, and **what changed from the tier below**.
* ≤ 3 drafts, one 2K reference. Check against the history labels before approval (no Soyombo, no real script).

### 5.2 Meshy reference

* Image-to-3D from the 2K sheet's front view (the client sub-command is still to be written; see
  [pipeline §7](ASSET_PRODUCTION_PIPELINE.md#7-missing-tools-phase-3)), or text-to-3D from the approved prompt.
* ≤ 30 credits per tier plus at most one re-roll. Skip Meshy for T1 if the sheet is clear enough to paint from.
* Purpose: correct proportions and the side and back views the sheet may lack; colour regions for the bake.

### 5.3 Blender (headless)

1. `process_and_render.py --size 2.0` (the player is 1.8 tall; 2.0 leaves room for the helmet), and read the
   `SULD_QC` line.
2. Orthographic front, side and back at 16 px per block (planned `ortho_views.py`). Overlay the Minecraft player
   outline (head 8 px, body 12 px, legs 12 px) to see where each feature lands.
3. Colour bake (planned `bake_layer.py`): project the mesh colour onto a humanoid UV template at 128×64 and
   write a reference PNG. The real UV regions at 2×: head (0,0)–(64,32) (unused, the helmet is a model), body
   (32,32)–(80,64), right arm (80,32)–(112,64), right leg (0,32)–(32,64).

### 5.4 Pixel texture

* Quantise the bake to the tier palette (planned `tools/art/pixelize.py`), then paint by hand to the rules of
  ARMOR_PROGRESSION_VISUAL_SPEC §2.1: seam grid, 2 × 2 clusters, gold budget, no glow.
* The leggings layer carries the skirt and tassets. The body layer's leg region carries only the boots, and is
  transparent above the boot-top row.

### 5.5 Blockbench (helmet and attachments)

* Java item model, cuboids only, inside −16…32. Element budget by tier (T1 ≤ 12 … T6 ≤ 60).
* Set display transforms: `head` (on the body), `gui` (inventory icon, three-quarter view), `thirdperson_righthand`
  and `ground` (sensible even though class gear cannot be dropped).
* Attachments: model origin at the passenger anchor (above the head); a `fixed` display transform translates them
  down to the shoulders or back.
* `bbmodel_validate` must report 0 errors. Preview with `tools/blender/render_mc_model.py`.

### 5.6 Resource pack and SÜLD integration

Equipment JSON (one per tier):

```json
{ "layers": {
    "humanoid":          [ { "texture": "suld:baatar_t3" } ],
    "humanoid_leggings": [ { "texture": "suld:baatar_t3" } ] } }
```

Item stack set-up (a proposal for the armour definition; the field names belong to the class-gear spec, not this
document):

```java
// chest, legs, boots
stack.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.CHEST)
        .assetId(Key.key("suld", "baatar_t3"))
        .equipSound(tierEquipSound)              // T1 leather … T5–T6 netherite (vanilla keys) until suld: sounds exist
        .allowedEntities(playersAndMannequins)   // class gear never goes on armour stands
        .dispensable(false));
stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("suld", "armor/baatar_t3_chest"));   // icon
// helmet: no asset id, so the 3D item model is drawn on the head
helmet.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD).dispensable(false));
helmet.setData(DataComponentTypes.ITEM_MODEL, Key.key("suld", "armor/baatar_t3_helmet"));
```

* The Paper data-component API is `@ApiStatus.Experimental` in 1.21.11. Keep the calls in one helper
  (`ItemFactory`), not scattered.
* Keep the vanilla armour base materials (`leather_helmet`, …) so that other systems still see armour.
  `ItemFactory` already removes the vanilla attributes.
* `refreshModels()` (`ItemFactory.java:256`) must also re-apply EQUIPPABLE and ITEM_MODEL on old stacks.
* Attachments: one SÜLD service owns them (spawn on equip, remove on unequip, death or logout, hidden from the
  wearer), with a server-wide cap and per-viewer distance culling.
* Tooltip: name in the rarity colour, the tier name, armour level, set, stats from the existing stat pipeline; no
  vanilla attribute lines.

## 6. Run order for the Баатар set

1. Gate 0 (§3).
2. T1 and T6 first: the two ends of the progression, which set the range.
3. T3 next (the first attachment), then T2, T4, T5 to fill the steps.
4. All six side by side in the client (the master plan's acceptance a), then the full checklist per tier.

## 7. QA checklist per armour set

Every row is checked in a real Minecraft 1.21.11 client with the server's pack, and recorded with screenshots in
`audit/qa/visual/<stem>/`. A set is CLIENT_TESTED only when every row passes.

| # | Area | Check |
|---|---|---|
| 1 | Pack | The pack loads with no missing-texture or model errors in the client log |
| 2 | Silhouette | Third-person front, side and back at 4, 16 and 32 blocks; the tier is identifiable in greyscale at 16 blocks |
| 3 | Helmet fit | Full head rotation (yaw ±90°, pitch ±90°), crouch, swim pose; no gap at the neck; the name tag stays readable |
| 4 | Chest fit | Seams at the belt row; no z-fighting with the leggings layer |
| 5 | Arm fit | Swing, bow draw (Мэргэн), shield block (Баатар), spear hold (Хүлэгчин); shoulder paint lines up with the pauldrons |
| 6 | Leg fit | Walk, sprint, crouch; the skirt split reads; no stretched pixels |
| 7 | Boot fit | The boot top sits on the seam row; the upturned-toe paint reads |
| 8 | Clipping | Helmet vs collar; pauldrons vs head turn; attachment vs off-hand shield and held weapon |
| 9 | Proportions | Matches the skin's density; no "sticker" look |
| 10 | Texture quality | No blur, no seams at UV borders, acceptable at distance (mipmaps) |
| 11 | Held weapon | Class weapon alignment in the main hand; shield in the off-hand |
| 12 | Attachments | Follow body turns with ≤ 3 ticks of visible lag; invisible to the wearer; removed on death and logout |
| 13 | First person | No attachment or helmet geometry in view; the sleeve on the first-person arm looks right |
| 14 | Mounted | On a horse: helmet, attachments, skirt; **Хүлэгчин**: the split skirt and horse barding of the same tier |
| 15 | Other wearers | On a Mannequin NPC |
| 16 | Tooltip | Rarity colour, tier name, armour level, set, stats; no vanilla attribute lines |
| 17 | Rarity presentation | Glint decision applied; tooltip frame (if any) correct |
| 18 | Icons | Inventory, hotbar and the armour slots |
| 19 | Lighting | Day, night, cave, and the Хасарын Агуй arena |
| 20 | Performance | 10 players in the set (T5–T6 with attachments) in one area: client ≥ 60 FPS, server mspt within budget |
| 21 | History labels | Every motif on the sheet carries its label; no Soyombo; no real seal or script text |

## 8. Status

| Item | Status |
|---|---|
| Armour pipeline design (this doc) | CONCEPT |
| Gate 0 spike | NOT_IMPLEMENTED |
| Concept prompt file (`prompts_armor.json`) | NOT_IMPLEMENTED |
| Ortho, bake and pixelize tools | NOT_IMPLEMENTED |
| Equipment assets and layers | NOT_IMPLEMENTED |
| Helmet and attachment models | NOT_IMPLEMENTED |
| `ItemFactory` EQUIPPABLE + ITEM_MODEL | NOT_IMPLEMENTED |
| Attachment service | NOT_IMPLEMENTED |
| QA checklist runs | NOT_IMPLEMENTED |
