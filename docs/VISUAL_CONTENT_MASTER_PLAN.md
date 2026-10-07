# SÜLD visual content master plan

Status: **design document, 2026-10-07.** Nothing visual described here as "planned", "target" or "slice" exists yet.
What exists is listed in §1 and in [`assets/registry.json`](../assets/registry.json), measured from `resourcepack/`.

Related specs: [ARMOR_PROGRESSION_VISUAL_SPEC](ARMOR_PROGRESSION_VISUAL_SPEC.md) ·
[BOSS_VISUAL_SPEC](BOSS_VISUAL_SPEC.md) · [NPC_VISUAL_SPEC](NPC_VISUAL_SPEC.md) · [SKILL_VFX_SPEC](SKILL_VFX_SPEC.md) ·
[ASSET_PRODUCTION_PIPELINE](ASSET_PRODUCTION_PIPELINE.md) · [ARMOR_ASSET_PIPELINE](ARMOR_ASSET_PIPELINE.md) ·
[ASSET_STYLE_GUIDE](ASSET_STYLE_GUIDE.md) · [ASSET_BUDGET](ASSET_BUDGET.md).

Owner decisions already made:

* Order: **audit → progression simulation → Баатар vertical slice**. No mass generation before the slice passes
  real-client QA.
* Meshy: the slice may use **about 400–600 of the 1,050 remaining credits**. Report to the owner before more than
  **300** are spent.
* Gemini: **1K** for drafts, **2K** for production references, **4K** only with a written justification.
* No client mods. Everything renders through vanilla 1.21.11 resource-pack features.
* No raw GLB/FBX/OBJ/BLEND/PSD in the shipped pack.

## 1. Where we start (audit summary)

Source: `audit/visual-content-status.json` and the 2026-10-07 visual audit.

| Area | Today | Status |
|---|---|---|
| Class weapons | 30 voxel-extruded models (5 classes × 6 tiers, C3b) + 18 bow frames, `tools/pack/gen_weapons.py`, 60–147 cuboids each | INTEGRATED, not client-tested |
| Other weapons, items | 16×16 sprites for 6 weapons, 2 materials, 3 rings (`gen_item_textures.py`); 18 weapons show vanilla | FUNCTIONAL_BUT_INCOMPLETE |
| Class armour | None. `armor.json` holds 27 vanilla leather/chain/iron/diamond/netherite pieces; no `equipment/` assets | NOT_IMPLEMENTED |
| Mobs, elites, bosses | Vanilla entities with names (Хасар = RAVAGER) | NOT_IMPLEMENTED |
| NPCs | 7 procedural 64×64 skins on Mannequins (`npc/NpcService.java`) | FUNCTIONAL_BUT_INCOMPLETE |
| Relics | Хөх Сүлд and Алтан Гэрэгэ: 16×16 sprites on `nether_star` | PLACEHOLDER |
| Skill VFX | Vanilla particles and sounds, one DUST colour per class | FUNCTIONAL_BUT_INCOMPLETE |
| HUD, GUI art | Font-glyph HUD and menus | INTEGRATED, MANUAL_QA_REQUIRED |
| Kharkhorum | Procedural vanilla-block modules; `schematics.json` empty | FUNCTIONAL_BUT_INCOMPLETE |
| Pipeline tools | Gemini script works; Meshy download verified 2026-10-07; Blender 4.0.2 headless works; Blockbench/Blender MCP servers not connected | partial |

Code facts this plan depends on:

* `ItemFactory` (`suld-plugin/src/main/java/mn/suld/plugin/item/ItemFactory.java:91, :256`) only sets the legacy
  int `custom_model_data`. `DataComponentTypes`, `Equippable`, `ItemDisplay` and `BlockDisplay` are unused.
* No model-owning plugin is installed. `CONTRIBUTING.md:5-12` and `DEPENDENCIES.md:70` forbid them.

## 2. Scope

**In scope:** class armour (5 classes × 6 tiers), class weapons, bosses and elites, NPC kit, relic visuals, skill
VFX, sounds, Kharkhorum props, resource-pack budget and delivery, client QA.

**Out of scope:** client mods, shaders (fragile, and the pipeline changed in 1.21.2, 1.21.5 and 1.21.6), Bedrock
parity. Geyser is listed as optional in `deploy/plugins/plugins.json`. Bedrock players do not get Java item models,
equipment assets or display bones without a separate Geyser mapping pack. That is a risk (§8), not a deliverable.

## 3. Rendering technique per asset category

| Category | Technique | Runtime API | Pack location |
|---|---|---|---|
| Armour body, arms, legs, boots | Equipment asset with HD `humanoid` / `humanoid_leggings` layers | `DataComponentTypes.EQUIPPABLE` with `.assetId(Key)` | `assets/suld/equipment/<id>.json`, `textures/entity/equipment/humanoid(_leggings)/<id>.png` |
| Helmet (class silhouette) | Head-slot item with **no** asset id, so the client draws the item model on the head (true 3D cuboids) | `EQUIPPABLE(HEAD)` without assetId + `ITEM_MODEL` | `assets/suld/items/armor/<id>_helmet.json` → `models/item/armor/…` |
| Pauldrons, back banner (T3+) | `ItemDisplay` passenger on the player, hidden from the wearer, at most 2 per player | `addPassenger`, `Player#hideEntity`, interpolated `Transformation` | `models/item/armor/<id>_pauldrons.json` |
| Weapons, relics | Item model definition on the held item | `ITEM_MODEL` (new assets); the legacy `custom_model_data` stays for the existing 32 | `assets/suld/items/…` |
| Bosses, elites | **Open decision (§5)**: SÜLD ItemDisplay-bone renderer (default) or BetterModel | `ItemDisplay` bones on an invisible host entity | `models/entity/<mob>/<bone>.json` |
| Cheap elite variants | Equipment layers on vanilla-backed mobs: `wolf_body` for wolves, `humanoid` for husk/stray | `EQUIPPABLE(BODY)` with `allowedEntities` | `textures/entity/equipment/wolf_body/<id>.png` |
| Cavalry mounts | `horse_body` equipment layer (barding) | `EQUIPPABLE(BODY)` on horse armour items | `textures/entity/equipment/horse_body/<id>.png` |
| Human NPCs | Mannequin + `ResolvableProfile` skin patch (already used) + SÜLD armour, 3D hats, held items | `Mannequin#setProfile`, `getEquipment()` | `textures/entity/npc/<id>.png` |
| Skill VFX | Vanilla particles + 1–3 short-lived `ItemDisplay` decals (ring, arc, crack) per cast | `World#spawnParticle`, `ItemDisplay` interpolation | `models/vfx/<id>.json` |
| Sounds | Vanilla sounds now; custom `suld:` sound events in phase 9 | `Player#playSound(String key, …)` | `assets/suld/sounds.json`, `sounds/…/*.ogg` |

Hard constraint: **Java item and equipment geometry is cuboids only.** Item-model elements live in a −16…32 box
(3 × 3 × 3 blocks) with limited element rotation. Meshy meshes are therefore references and bake sources, never
shipped geometry (see [ASSET_PRODUCTION_PIPELINE §2](ASSET_PRODUCTION_PIPELINE.md#2-the-cuboid-constraint)).

## 4. The Баатар vertical slice

One complete, client-tested slice proves every stage of the pipeline before anything is scaled.

| # | Deliverable | Registry id(s) | Technique | Acceptance criteria |
|---|---|---|---|---|
| 1 | Баатар armour, **T1 Эхлэл → T6 Дээдэс** (helmet, chest with shoulder/arm, legs, boots for each tier) | `armor.baatar_t1` … `armor.baatar_t6` | Equipment layers at 128×64, 3D helmet item models, pauldron passengers on T3–T6 only | (a) Six tiers that read as different gear at 16 blocks in a side-by-side screenshot with no HUD, judged on silhouette, not colour; (b) every row of the [armour QA checklist](ARMOR_ASSET_PIPELINE.md#7-qa-checklist-per-armour-set) passes in a real 1.21.11 client; (c) items built by `ItemFactory` with Equippable + ITEM_MODEL, with the tooltip and rarity colour right; (d) within the [budget](ASSET_BUDGET.md#6-per-asset-budget-баатар-slice) |
| 2 | Signature weapon: Баатар sabre, **T6 Дээдэс** hero model | `weapon.baatar_sabre_t6` | Blockbench cuboid item model, 64×64 texture, `ITEM_MODEL` | Reads as a sabre in first person, third person and the GUI. No clipping with the T6 gauntlet. Matches the T6 armour materials. The 4 existing Баатар voxel weapons also pass a client check |
| 3 | Elite mob: **Хангайн Баавгай** (existing ELITE, `WorldContent.BEAR`) | `mob.khangai_baavgai` | Quadruped bone rig, 12–15 bones | Idle, walk, attack and death play; the hitbox is visually honest (±15 %); the elite cues of [BOSS_VISUAL_SPEC §3](BOSS_VISUAL_SPEC.md#3-elite--champion-visual-categories) are visible; under 5 KB/s per viewer when idle (measured) |
| 4 | Dungeon boss: **Хасар — Агуйн Эзэн** | `boss.khasar` | 24–30 display bones on the invisible RAVAGER host | All clips in [BOSS_VISUAL_SPEC §5](BOSS_VISUAL_SPEC.md#5-хасар--full-specification) play. The phase changes at 60 % and 30 % and the 180 s enrage are visible without reading chat. The death clip finishes. 4 players in the arena hold 20 TPS with mspt < 35 (spark) |
| 5 | Relic visual: **Хөх Сүлд** | `relic.khukh_suld_model` | 3D tug-standard item model (inventory and hand) + shrine `ItemDisplay` | Distinct inventory icon, held model, shrine presentation and acquisition moment. The blue is labelled ORIGINAL FICTION. No Soyombo |
| 6 | NPC: **Зуутын Дарга** (Warrior kit) | `npc.zuutyn_darga` | Mannequin, new 64×64 skin, Баатар T2 armour, T2 helmet item, held sabre | Recognisable as a warrior at 20 blocks; the armour fits on a Mannequin; the name and description are readable |
| 7 | 3 skill VFX: **Тэнгэрийн Цавчилт**, **Довтлох Үсрэлт**, **Бүхний Нурал** | `vfx.baatar_*` | Vanilla particles + ItemDisplay decals | Within the budgets of [SKILL_VFX_SPEC §4](SKILL_VFX_SPEC.md#4-budgets-and-performance-rules). A tester can tell each one apart with eyes closed (sound) and with the sound off (visual) |
| 8 | Pack integration | — | `tools/validation/run_all.py` green | All validators pass (the budget validator fails today, see [ASSET_BUDGET §1](ASSET_BUDGET.md#1-current-pack-measured-2026-10-07)). The pack stays under 3 MB zipped |

Slice exit gate: every row above is **CLIENT_TESTED** in `assets/registry.json` with date and tester, plus a
written retrospective of the style, geometry, texture, size, integration and client problems found. The four
other classes start only after the owner signs off.

## 5. Open decision: boss and elite rendering (owner to decide)

Neither option is implemented. Both put the bones on `ItemDisplay` entities. The difference is who owns the code.

| | **A. SÜLD bone renderer** (recommended default) | **B. BetterModel** (MIT plugin) |
|---|---|---|
| What it is | A small SÜLD module: a rig loader, a bone-to-ItemDisplay mapping on an invisible host entity, a keyframe player, LOD and culling | `io.github.toxicity188:bettermodel-bukkit-api` 3.5.x; `.bbmodel` files are loaded at runtime |
| 1.21.11 support | Our own code against the Paper API (`ItemDisplay`, `Transformation`, interpolation) | 3.5.0 (2026-09-20) lists 1.21.4–1.21.11 on Hangar |
| Project rules | **Keeps** `CONTRIBUTING.md:5-12` ("build the gameplay yourself") and `DEPENDENCIES.md:70` ("plugins that would own a SÜLD layer are never installed") | **Breaks** both rules. Needs a written amendment like the 2026-10-06 Axiom/WorldEdit one |
| Animation features | What we build: linear, step and Catmull-Rom keyframes; blend in and out; no IK at first | Mature: Molang, IK, bezier curves, automatic idle/walk/death, per-player animation, hitboxes |
| Resource pack | Bones exported into **our** pack by our tool; one pack, one SHA-1 | Builds its own pack at runtime. That means a second server pack (multi-pack since 1.20.3) or copying its folder output into ours. It ships a core-shader override for player animation |
| Network cost | Unmeasured. Estimate about 10–20 KB/s per animated boss per viewer at 10 Hz with 24 bones; only changed bones are sent | Author's benchmark: about 9 KB/s per model per viewer (biased source, older versions) |
| Effort | About 1.5–3 weeks for the renderer, the exporter and tests before Хасар can move | Days to first animation, but rule changes, pack merging and upgrade risk |
| Risk | Our bugs; animation quality limited at first | Third-party lifecycle; shader breakage; namespace clashes (`leather_horse_armor` base item) |

**Recommended default: A.** The slice's needs are modest: one boss and one elite, a handful of clips each. A
keeps the project's defining rule and a single pack. If A's measured network cost or animation quality fails the
slice gate, the owner can still pick B with real numbers in hand. Build A behind an interface
(`ModelRenderer.attach(LivingEntity, rigId)` → `play(clip)`) so that B could be added later without touching
`BossService` or `MobService`.

## 6. Phases 0–13 (directive §29) mapped to deliverables

| Phase | Directive name | Concrete deliverables | Exit gate | Status |
|---|---|---|---|---|
| 0 | Audit existing visuals | `audit/visual-content-status.json`; `assets/registry.json` with real sizes | Every pack asset classified | COMPLETE (2026-10-07) |
| 1 | Style guide | `docs/ASSET_STYLE_GUIDE.md`, palettes, history labels | Owner review | CONCEPT (this set) |
| 2 | Build the Баатар slice | §4 items 1–7: concept sheets, Meshy refs, bakes, Blockbench models | All assets reach BLOCKBENCH_VALIDATED | NOT_IMPLEMENTED |
| 3 | Validate Gemini → Meshy → Blender → Blockbench | Missing tools: Meshy image-to-3D + refine commands, `tools/blender/bake_layer.py`, `tools/blender/mesh_report.py`, a `.bbmodel` → bone exporter, an `assets/registry.json` validator; MCP servers connected | Each stage writes a JSON report (§4 of the pipeline doc) | NOT_IMPLEMENTED |
| 4 | Integrate with SÜLD systems | `ItemFactory`: Equippable + ITEM_MODEL from a visual-asset field; mob renderer bound to `MobDefinition`; NPC role; spell VFX hooks in `SkillService.run()` | Unit tests + `run_all.py` green | NOT_IMPLEMENTED |
| 5 | Client QA | QA runs per checklist, screenshots in `audit/qa/visual/` | All slice rows CLIENT_TESTED | NOT_IMPLEMENTED |
| 6 | Scale to the other 4 classes | 24 armour sets, weapons per class | Each class passes the same checklist | NOT_IMPLEMENTED |
| 7 | Boss ecosystem | Элсний Хаан, Ойн Эзэн, Мөсөн Хаан, later bosses | Rules in BOSS_VISUAL_SPEC §6 | NOT_IMPLEMENTED |
| 8 | NPC ecosystem | 11-category kit, 3D hats | NPC_VISUAL_SPEC | NOT_IMPLEMENTED |
| 9 | Skill VFX | 20 spells + 15 ultimates to spec; `sounds.json` | Budgets met, measured | FUNCTIONAL_BUT_INCOMPLETE (vanilla) |
| 10 | Kharkhorum environment kit | Prop item models on ItemDisplays (banners, tug, braziers, Silver Tree), `.schem` modules | Placement via WorldBuilder | NOT_IMPLEMENTED |
| 11 | Pack optimisation | Deduplication, atlas review, size report | Under the budget in ASSET_BUDGET | NOT_IMPLEMENTED |
| 12 | Final client QA | Full checklist pass across classes | All PRODUCTION_READY | NOT_IMPLEMENTED |
| 13 | Performance audit | spark profiles, entity and particle counts, KB/s per viewer | Within ASSET_BUDGET §5 | NOT_IMPLEMENTED |

## 7. Meshy and Gemini budget for the slice

Observed costs from repo records: a text-to-3D **preview cost 20 credits** (ASSET_PIPELINE.md, 2026-10-07
download test) and a preview plus 4K refine cost 30 in total. The balance is **1,050**. Run
`python3 tools/meshy/meshy_client.py balance` before and after every task and log the delta in the asset's report.

| Asset | Meshy tasks (planned) | Credits (est.) | Gemini images (1K drafts + 2K refs) |
|---|---|---|---|
| Баатар armour T1–T6, full-body references | 6 × (preview + refine) | 180 | 6 × (3 + 1) = 24 |
| Re-roll reserve for armour (style misses) | up to 3 re-rolls | 90 | 6 extra drafts |
| Хасар | preview + refine, 1 re-roll | 60 | 4 + 2 (side and turnaround) |
| Хангайн Баавгай | preview + refine | 30 | 2 + 1 |
| T6 sabre | preview + refine | 30 | 2 + 1 |
| Хөх Сүлд standard | preview + refine | 30 | 2 + 1 |
| Зуутын Дарга NPC | none (skin is painted) | 0 | 1 + 1 |
| 3 VFX | none | 0 | 3 drafts (mood only) |
| **Total** | | **≈ 420** (reserve to 500) | **≈ 54** (no 4K) |

Checkpoint: after the T1, T3 and T6 armour references plus Хасар (≈ 150–210 credits), stop and report to the owner
if the running total is near **300**. Meshy is skipped wherever a pixel-painted texture is faster (NPC skins, VFX
decals, T1 armour if the draft is clear enough).

## 8. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| HD equipment textures (128×64) look wrong or blur next to 64×64 skins | Armour reads as a sticker | Test T1 at 64×32 vs 128×64 in the client before the other tiers; paint in 2-px clusters |
| Head-slot item fallback does not draw as expected on 1.21.11 | Helmets fail | One-day spike with a test helmet before any helmet art |
| `ItemDisplay` attachments drift on body turns (the client predicts remote body yaw) | Pauldrons float | T3+ only, low-mass shapes, interpolation 2–3 ticks, auto-off when crowded |
| Own renderer costs more network than estimated | Lag in boss fights | Measure in the slice; LOD; decide A vs B on data |
| Meshy outputs off-style (European plate, fantasy spikes) | Wasted credits | Spec before generation; image-to-3D from approved sheets only; per-asset credit cap |
| Pack budget validator already fails (102 findings in HUD textures) | `build-pack.sh` aborts | Fix the allowlist and duplicates in phase 11 or earlier (not part of this doc set) |
| Historical mislabelling (Soyombo, invented tamga, "blue banner") | Public criticism | Labels on every motif; the style guide's "do not" list; review before public use |
| Bedrock players via Geyser see vanilla shapes | Inconsistent look | Document; consider a Geyser mapping pack later |
| RESOLVED: tiers renamed (Эхлэл…Дээдэс); was a naming clash: tier "Домогт" (T5) vs the EPIC rarity display name "Домогт" (`ItemRarity.java`) | A confusing tooltip | Owner question; see the open questions in ARMOR_PROGRESSION_VISUAL_SPEC |

## 9. Status

| Item | Status |
|---|---|
| This master plan | CONCEPT |
| Баатар armour T1–T6 | NOT_IMPLEMENTED |
| Signature weapon (T6 sabre) | NOT_IMPLEMENTED; existing W1–W4 voxel weapons INTEGRATED, not client-tested |
| Elite (Хангайн Баавгай visuals) | NOT_IMPLEMENTED |
| Хасар visuals | NOT_IMPLEMENTED |
| Хөх Сүлд 3D relic | NOT_IMPLEMENTED (16×16 sprite PLACEHOLDER) |
| Зуутын Дарга NPC | NOT_IMPLEMENTED |
| 3 slice VFX | FUNCTIONAL_BUT_INCOMPLETE (vanilla effects exist, spec not met) |
| Boss rendering decision | OPEN (owner) |
| Equipment rendering (`EQUIPPABLE`) | NOT_IMPLEMENTED |
| Client QA of any asset | NOT_IMPLEMENTED (nothing is CLIENT_TESTED) |
