# NPC visual specification: the reusable Kharkhorum NPC kit

Status: **design document, 2026-10-07.** What exists: 7 procedural skins on Mannequins. Everything else in this
document (3D headgear, armour on NPCs, held items, new categories) is NOT_IMPLEMENTED.

## 1. What exists

`npc/NpcService.java` spawns 1.21.9+ **Mannequin** entities (`NpcService.java:147-160`): persistent false,
invulnerable, immovable, glowing, silent, a gold bold name (`#FFD24A`), a description line ("▶ дарж ярилц"), and a
skin from the resource pack through `ResolvableProfile…skinPatch(body = suld:entity/npc/<skin>)`. No Mojang skin,
no signing. Heads turn toward the nearest player within 8 blocks. Mannequins hold **no items and wear no
equipment** today.

| Skin (`textures/entity/npc/`, 64×64) | Role in `NpcService` | Name | Look (`tools/pack/gen_skins.py`) |
|---|---|---|---|
| `shaman` | `class_selection` | Ангийн Бөө | dark deel, purple sash, tasselled headdress over the eyes |
| `guide` | `tutorial` | Хөтөч | sky-blue deel, gold sash, white beard, felt hat |
| `hunter` | `quest.first_hunt` | Анчдын Ахлагч | leather deel, wolf-fur hat |
| `merchant` | `merchant.*` | Худалдаачин | green silk deel, orange sash, round hat |
| `blacksmith` | `blacksmith` | Дархан | soot-dark deel, leather apron, bare head |
| `rider` | `fast_travel.*` | Өртөөчин | red deel, orange sash, fur hat |
| `lama` | `shrine.sky` | Тэнгэрийн Тахилч | saffron robe, maroon sash, shaved head |

All skins share one body generator (`person()` in `gen_skins.py`): a deel with a sash and a diagonal collar flap,
boots, optional hat on the overlay layer. They differ in colour and headgear only, so their silhouettes are
nearly identical. That is the main thing this kit fixes.

## 2. Technique (Mannequin kit)

| Layer | Technique | Paper API | Budget |
|---|---|---|---|
| Body | 64×64 skin through the skin patch; `model` wide or slim as fits the character | `ResolvableProfile.SkinPatch.body(Key)`, `.model(…)` | 1 PNG, ≤ 2 KB |
| Overlay (sleeves, coat tails, beard) | The skin's second layer; parts can be switched off | `Mannequin#setSkinParts` | in the same PNG |
| Headgear (the main silhouette tool) | Head-slot item with `ITEM_MODEL` and **no** equipment asset id, so it draws as a 3D model | `getEquipment().setHelmet(…)` | ≤ 30 cuboids, 32×32 texture |
| Armour | The SÜLD class armour equipment assets (the same files players wear) | `getEquipment()` chest, legs, feet | 0 extra textures |
| Held items | Class weapons, tools (hammer, staff, ledger), drum | `getEquipment().setItemInMainHand/OffHand`, `setMainHand` | existing item models |
| Cape | The skin patch's `cape` texture (64×32) | `SkinPatch.cape(Key)` | ≤ 1 KB |
| Pose | standing, crouching, sleeping, … (`validPoses()`) | `setPose` | — |
| Back or prop attachment | `ItemDisplay` riding the Mannequin (banner, quiver, bundle) | `addPassenger` | 0–1 per NPC, static (no per-tick updates) |
| Text | Name + `setDescription` (already used); no extra TextDisplay | — | — |

Mannequins have **no pathfinding** (they are `LivingEntity`, not `Mob`). Moving NPCs need scripted movement and are
out of scope here. Non-human NPCs (spirits, an ongon, a talking wolf) use the bone renderer of
[BOSS_VISUAL_SPEC §4](BOSS_VISUAL_SPEC.md#4-rendering-contract-either-renderer), not a Mannequin.

Reconsider `setGlowing(true)` on every NPC (`NpcService.java:151`): the glow outline flattens the new silhouettes
and costs readability in crowded plazas. Proposal: glow only for NPCs with an available quest. That is a gameplay
change for the owner.

## 3. The kit: 11 categories

Each category has a **silhouette key**: the one feature that identifies it at 20 blocks before any colour is seen.

| Category | Silhouette key | Body skin | Headgear (3D) | Armour / held | Palette anchor | Exists today |
|---|---|---|---|---|---|---|
| Khan | Tall, layered, broad-shouldered robe; the tallest hat | Brocade deel, wide sleeves, sash with plaques | Tall brimmed hat with a finial (design **needs research**; ORIGINAL until verified) | No weapon; a seal box or ledger in hand | deep red `#6E1A22`, gold ≤ 5 %, black `#1B1A1F` | none |
| Noble (ноён) | Coat with a stiff collar, belt plaques | Silk deel, belt plaques | Fur-trimmed hat with a small finial | Paiza-like plaque at the belt (INSPIRED, no real script) | blue-grey `#3B4A66`, silver `#D6D9DE` | none (quest lore names a "Хотын Ноён" giver in `QuestContent.java:71`) |
| Warrior | Armour mass at the shoulders | Under-deel visible at the cuffs | Class helmet (Баатар T1–T3 models) | Class armour; sabre or spear; shield optional | iron + red lacquer | none (**slice NPC**, §5) |
| Blacksmith | Apron and bare forearms, hammer | `blacksmith` skin, upgraded | Leather cap or none | Hammer in hand; tongs off-hand | soot `#2B2A2E`, leather, ember `#E06A2A` | `blacksmith` (skin only) |
| Shaman | Fringed headdress, ribbons | `shaman` skin | Fringed headdress model (taller than the painted one) | Drum or staff | dark felt, purple sash (existing) | `shaman` (skin only) |
| Merchant | Round hat, a bundle on the back | `merchant` skin | Round hat with a red top (existing, now 3D) | Ledger or scale; back bundle attachment | green silk, orange sash (existing) | `merchant` (skin only) |
| Scout / rider | Fur hat, quiver, bow | `rider` / `hunter` skins | Fur hat with ear flaps | Bow in hand; quiver attachment | red deel (rider), leather (hunter) | `rider`, `hunter` (skins only) |
| Elder | A stooped pose is not available, so: long white beard, staff, felt hat | `guide` skin | Felt hat | Staff | sky-blue deel, white beard (existing) | `guide` (skin only) |
| Quest NPC | Category look + a **quest marker** above (font glyph, not glow) | per role | per role | per role | — | `hunter` acts as one |
| Dungeon NPC | Travel-worn, lantern or torch; seated or crouching pose at the entrance | worn deel, bandages | Hood | Lantern in the off-hand | desaturated | none |
| Lore NPC | Scholar or monk: robe and scroll | `lama` skin for the temple; new scribe skin | Shaved head (lama) or a scribe cap | Scroll | saffron/maroon (existing), parchment | `lama` (skin only) |

History labels: the deel, sash, felt and fur hats and boots are traditional Mongolian dress, used as **INSPIRED**
(SÜLD's research covers the ger and materials, but not 13th-century dress in detail; verify before lore text). The
Buddhist temple setting is grounded in Rubruck's report of temples in Karakorum (VERIFIED, see
[KHARKHORUM.md](research/history/KHARKHORUM.md)). All named NPCs and their stories are ORIGINAL FICTION. No Soyombo,
no real seal text, no invented tamga presented as historical.

## 4. Skin production rules

1. Keep `gen_skins.py` as the generator. Extend `person()` with silhouette options: coat length (hip, knee, ankle
   painted on the leg cuboids), sleeve width (overlay layer), beard, apron, and a layered collar.
2. 64×64, classic layout, 1× density (the same as players). Mannequin skins are **not** HD.
3. Every skin must survive with its overlay layer off (`setSkinParts`): the base layer alone still reads.
4. Headgear moves from the painted overlay to a 3D head item for every category that has one; the painted hat stays
   as a fallback for Bedrock and low-end clients.
5. Faces: 2-px eyes, a 1-px mouth (as now). No real-person likeness.

## 5. Slice NPC: Зуутын Дарга

| Aspect | Spec |
|---|---|
| Concept | Captain of a hundred (зуут: the jaghun is a VERIFIED decimal unit, [MATERIAL_CULTURE §3](research/history/MATERIAL_CULTURE.md#3-organisation-the-decimal-system)); the person and the role are ORIGINAL FICTION. A Баатар veteran stationed at the south gate |
| Purpose in the slice | Proves the full kit: skin + **Баатар T2 armour** (equipment assets on a Mannequin) + **T2 helmet item model** + held sabre (`weapon.class.baatar.2` visual) + one cape |
| Skin | New `zuutyn_darga.png`, 64×64: weathered face, short beard, red-lacquer under-deel cuffs, scarred forearm |
| Placement | A new world point next to the existing `fast_travel.gate_south` point (`assets/world/kharkhorum/points.json`) and a new `NpcService` role. Interaction can stay ambient in the slice; becoming the Хасарын Агуй quest giver is an owner choice |
| Acceptance | Reads as a warrior at 20 blocks; armour layers and helmet sit correctly on the Mannequin; the head-turn does not clip the helmet; name and description readable |
| Budget | 1 skin (≤ 2 KB) + 0 new armour files (reuses `armor.baatar_t2`) + 1 cape (≤ 1 KB) + 0 attachments |

## 6. Budgets

| Metric | Budget |
|---|---|
| Skin | 64×64, ≤ 2 KB each (existing: 612–679 B) |
| 3D hats | ≤ 30 cuboids, 32×32; ≤ 15 hat models for the whole kit |
| Attachments | ≤ 1 per NPC, static, and only on NPCs in key locations |
| NPCs visible at once (plaza) | ≤ 25 Mannequins within 48 blocks of spawn |

## 7. Status

| Item | Status |
|---|---|
| Mannequin + resource-pack skins (7) | INTEGRATED (MANUAL_QA_REQUIRED) |
| Silhouette kit (11 categories) | CONCEPT |
| 3D headgear | NOT_IMPLEMENTED |
| Armour and held items on NPCs | NOT_IMPLEMENTED |
| `gen_skins.py` silhouette options | NOT_IMPLEMENTED |
| Slice NPC Зуутын Дарга | NOT_IMPLEMENTED |
| Non-human NPCs (rigged) | NOT_IMPLEMENTED |
| Client QA | NOT_IMPLEMENTED |
