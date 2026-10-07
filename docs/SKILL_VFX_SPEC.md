# Skill VFX specification

Status: **design document, 2026-10-07.** Every skill effect today is vanilla particles and vanilla sounds, cast
from `suld-plugin/src/main/java/mn/suld/plugin/skill/SkillService.java` (`run()`, line 320; helpers to line 846)
and `skill/Ultimates.java` (15 ultimates). The pack has no custom particles and no `sounds.json`. Particle counts
below were **read from the code**, not measured in a client.

## 1. Cast flow and feedback that already exist

| Moment | Today | Source |
|---|---|---|
| Combo click 1 / 2 | `UI_BUTTON_CLICK` at pitch 1.4 / 1.7, HUD refresh | `SkillService.click()` |
| Cast | Toast "✦ <spell>" through `HudService` | `castDirect0()` |
| Fail: locked, cooldown, no resource | Toast with the reason + `BLOCK_NOTE_BLOCK_BASS` (0.8, 0.6) | `castDirect0()` |
| Echo modifier | `BLOCK_AMETHYST_BLOCK_RESONATE` (1, 1.6), then **the whole spell runs again** after 12 ticks | `castDirect0()` |
| Cooldown display | HUD spell slots I–IV + ultimate: ready, countdown, locked, no resource | `docs/HUD.md:26` |
| Ultimate | F key, toast "✦✦ <name>", 45 s cooldown (`Ultimate.COOLDOWN_SECONDS`), cost from `skills.ultimate.resource-cost` (default 60) | `SkillTreeService.castUltimate()` |
| Held weapon aura | Tier 2+ particles every 6 ticks | `item/ClassWeapons.auras()` |

Missing: a **"ready" cue** when a slot-4 spell or the ultimate comes off cooldown, and per-spell *impact* accents on
the target.

Base cooldowns by slot: 1.5 s, 4 s, 5 s, 10 s (`Spell.COOLDOWN_SECONDS`); unlock levels 1, 10, 20, 35.

## 2. Visual language per class

| Class (resource) | Theme | Shapes | Colour (code) | Support colour | Sound family | Timing |
|---|---|---|---|---|---|---|
| БААТАР (Rage) | Heavy impact, shockwave | Arcs, rings, ground cracks, debris | `#FF5A46` (255,90,70) | stone and dust, `#FF3C28` war-cry red | Roars, metal, explosions, low | Fast hit, slow settle |
| МЭРГЭН (Focus) | Precision | Thin lines, trails, marks, small bursts | `#78E678` (120,230,120) | gold eye `#FFDC50` | String, horn, whistle; quiet | Sharp, short |
| БӨӨ (Spirit) | Spirit and sky | Rising spirals, rings, slow floating motes | `#AA6EFF` (170,110,255) | sky `#96EBFF` | Chime, drum, soul | Slow, rhythmic |
| ДАРХАН (Heat) | Forge and metal | Sparks, molten sprays, falling mass | `#FF9632` (255,150,50) | iron grey `#6E6E78` | Anvil, lava, fire | Heavy, percussive |
| ХҮЛЭГЧИН (Momentum) | Wind and speed | Speed trails, dust plumes, hoof lines | `#5AAAFF` (90,170,255) | wind `#C8E6FF` | Gallop, wind | Continuous, forward |

Class colours come from `SkillService.color()` (line 473). `ClassWeapons` uses a slightly different Мэргэн green
(110,220,110). Unify on one value when the VFX work starts.

## 3. Spells: today, and what changes

Totals are particles **per cast** (×2 with echo). Peaks are particles per tick. The budget limits are in §4.

### 3.1 БААТАР

| Spell (slot, cost, CD) | Cast | Travel | Impact | Sound | Total / peak | Verdict and change |
|---|---|---|---|---|---|---|
| Тэнгэрийн Цавчилт (1, 25, 1.5 s) | 5 SWEEP_ATTACK on a 90° arc + 20 DUST | — | knockback only | PLAYER_ATTACK_SWEEP | 25 / 25 | OK. **Slice VFX A** (§6) |
| Дайны Хашгираан (2, 35, 4 s) | 8 ANGRY_VILLAGER + 40 DUST | — | taunt (mobs target the caster) | RAVAGER_ROAR | 48 / 48 | OK. Later: a red ring decal at radius 9 to show the taunt range |
| Довтлох Үсрэлт (3, 30, 5 s) | velocity jump | none | 2 EXPLOSION + 40 BLOCK | GOAT_LONG_JUMP, GENERIC_EXPLODE | 42 / 42 | OK. **Slice VFX B** |
| Хааны Хамгаалалт (4, 50, 10 s) | 30 TOTEM_OF_UNDYING | — | — | ITEM_TOTEM_USE | 30 / 30 | OK. Later: a gold ward ring (decal) for 6 s at 1 refresh only |

### 3.2 МЭРГЭН

| Spell | Cast | Travel | Impact | Sound | Total / peak | Verdict and change |
|---|---|---|---|---|---|---|
| Чонын Нүд (1, 20, 1.5 s) | 20 gold DUST at the eyes | — | GLOWING on enemies within 24 | GOAT_HORN_SOUND_0 | 20 / 20 | OK. The glow is a gameplay mark, keep it |
| Олон Сум (2, 35, 4 s) | 5 critical arrows | vanilla crit trail (client) | arrow hit | ARROW_SHOOT | 0 server particles | OK. Add 3 green DUST at the bow |
| Ухрах Үсрэлт (3, 25, 5 s) | 20 CLOUD | — | slowness | BREEZE_JUMP | 20 / 20 | OK |
| Тэнгэрийн Сум (4, 50, 10 s) | — | END_ROD every 0.5 block, ≤ 30 blocks | per-hit damage | ILLUSIONER_CAST_SPELL | ≤ 60 / 60 | OK. Add 4 CRIT at each hit |

### 3.3 БӨӨ

| Spell | Cast | Travel | Impact | Sound | Total / peak | Verdict and change |
|---|---|---|---|---|---|---|
| Сүнсний Залбирал (1, 25, 1.5 s) | — | — | 5 HEART per healed ally | AMETHYST_BLOCK_CHIME | 5 per ally | OK |
| Онгоны Дуудлага (2, 30, 4 s) | — | 2 SOUL_FIRE_FLAME + 2 DUST per tick, ≤ 41 ticks | 15 SOUL | PARTICLE_SOUL_ESCAPE | ≤ 179 / 4 | At the limit. Halve the DUST |
| Хэнгэргийн Дуу (3, 40, 5 s) | — | 3 beats, 15 ticks apart | 24-point DUST ring per beat | NOTE_BLOCK_BASEDRUM per beat | 72 / 24 | OK |
| Тэнгэрийн Хаалга (4, 70, 10 s) | 12 END_ROD per tick for 20 ticks | — | FLASH + 80 DUST | BEACON_ACTIVATE | **321** / 81 | **Over budget.** Use 8 END_ROD per tick and 50 DUST (≈ 211) |

### 3.4 ДАРХАН

| Spell | Cast | Travel | Impact | Sound | Total / peak | Verdict and change |
|---|---|---|---|---|---|---|
| Галын Давталт (1, 25, 1.5 s) | — | — | 60 FLAME + 10 LAVA | ANVIL_LAND | **70** / 70 | Over the slot-1 limit. Use 45 FLAME |
| Ган Бамбай (2, 30, 4 s) | 30 CRIT | — | — | ARMOR_EQUIP_NETHERITE | 30 / 30 | OK |
| Хайлсан Төмөр (3, 35, 5 s) | 3 sprays, 6 ticks apart, 30 FLAME each | cone | ignite | FIRECHARGE_USE ×3 | 90 / 30 | OK |
| Дарханы Дөш (4, 60, 10 s) | 6 grey DUST per tick falling for 16 ticks | the falling anvil | 3 EXPLOSION + 15 LAVA | ANVIL_LAND | 114 / 18 | OK. Later: an anvil item model on an ItemDisplay instead of the dust column |

### 3.5 ХҮЛЭГЧИН

| Spell | Cast | Travel | Impact | Sound | Total / peak | Verdict and change |
|---|---|---|---|---|---|---|
| Хурдан Довтолгоо (1, 20, 1.5 s) | dash | 3 CLOUD per tick for 11 ticks | hits on the way | HORSE_GALLOP | 33 / 3 | OK |
| Салхины Хурд (2, 25, 4 s) | 30 CLOUD | — | — | BREEZE_WIND_BURST | 30 / 30 | OK |
| Жадны Шидэлт (3, 35, 5 s) | — | CRIT every 0.5 block, ≤ 18 blocks | per hit | TRIDENT_THROW | ≤ 36 / 36 | OK. Later: a spear ItemDisplay flying the line (1 decal, 6 ticks) |
| Хүлгийн Дайралт (4, 60, 10 s) | — | 6 CLOUD + 8 DUST per tick for 21 ticks | knockback | HORSE_GALLOP every 4 ticks | **294** / 14 | **Over budget.** Use 4 CLOUD + 5 DUST (189) |

### 3.6 Ultimates (45 s cooldown)

| Class | Ultimate | Effect today | Total / peak | Verdict |
|---|---|---|---|---|
| Баатар | Чингисийн Уур | aura: 50 DUST + 1 FLASH, RAVAGER_ROAR | 51 / 51 | OK |
| Баатар | Бүхний Нурал | 14 EXPLOSION ring (r 8) + 80 BLOCK, GENERIC_EXPLODE | 94 / 94 | Peak over 80. **Slice VFX C** |
| Баатар | Үхэшгүй Эр | gold aura 51, TOTEM_USE | 51 / 51 | OK |
| Мэргэн | Сумын Борооны Цаг | 20 volleys × 3 points × (4 CRIT + 6 ENCHANTED_HIT), 5 ticks apart, ARROW_HIT | **600** / 30 | **Over.** Use 2 CRIT + 3 ENCHANTED_HIT (300) |
| Мэргэн | Хэтийн Харваач | green aura 51, ILLUSIONER_CAST_SPELL | 51 / 51 | OK |
| Мэргэн | Үхлийн Тэмдэг | GLOWING on ≤ 30 enemies, WARDEN_SONIC_CHARGE | 0 | OK. Add a mark glyph over the targets later |
| Бөө | Өвгөдийн Залбирал | 8 HEART per ally + 30 END_ROD ring (r 14), BEACON_POWER_SELECT | 30 + 8/ally | OK |
| Бөө | Тэнгэрийн Шийтгэл | ≤ 8 `strikeLightningEffect`, 6 ticks apart | 8 bolts | Heavy on screen and sound; keep, but never chain other ultimates' FLASH with it |
| Бөө | Сүнсний Хөл | purple aura 51, SOUL_ESCAPE | 51 / 51 | OK |
| Дархан | Хайлсан Далай | 10 pulses × (24 FLAME ring + 4 LAVA), LAVA_POP | 280 / 28 | OK |
| Дархан | Бамбайн Хэрэм | grey aura 51, ARMOR_EQUIP_NETHERITE | 51 / 51 | OK |
| Дархан | Мянган Алх | 10 anvils × (15 DUST + 1 EXPLOSION), ANVIL_LAND ×10 | 160 / 16 | OK |
| Хүлэгчин | Шуурга Давхилт | 3 dashes, 4 CLOUD every 2 ticks (27 steps) | 108 / 4 | OK |
| Хүлэгчин | Салхины Гэгээн | wind aura 51, BREEZE_WIND_BURST | 51 / 51 | OK |
| Хүлэгчин | Мянган Морь | 8 directions × 15 ticks × (3 CLOUD + 2 DUST) | **600** / 40 | **Over.** Use 2 CLOUD + 1 DUST (360) |

## 4. Budgets and performance rules

| Kind | Total particles per cast | Peak per tick | ItemDisplay decals | Decal lifetime | Sounds per cast |
|---|---|---|---|---|---|
| Slot 1 (spam) | ≤ 60 | ≤ 30 | ≤ 1 | ≤ 8 ticks | ≤ 2 |
| Slots 2–3 | ≤ 180 | ≤ 40 | ≤ 2 | ≤ 20 ticks | ≤ 3 |
| Slot 4 | ≤ 250 | ≤ 60 | ≤ 3 | ≤ 20 ticks | ≤ 3 |
| Ultimate | ≤ 400 | ≤ 80 | ≤ 8 | ≤ 40 ticks | ≤ 4 |
| Persistent aura (weapon, armour T6) | ≤ 1 per tick per player on average | — | 0 | — | 0 |

Rules:

1. **Echo counts.** A spell with the echo modifier runs twice. The budget is for the single run; the echo copy
   uses half the counts (a `scale` argument on the helpers).
2. **Crowd LOD.** When more than 6 SÜLD players are within 32 blocks of the cast point, all counts are halved and
   decals are skipped on slots 1–2.
3. **No `force`.** Particles use the default send range; never `force = true`.
4. **Screen noise.** ≤ 1 FLASH per cast. ≤ 1 EXPLOSION per impact point. Lightning only on Тэнгэрийн Шийтгэл.
5. **Decals** are `ItemDisplay`s with an item model from `assets/suld/models/vfx/`. They are spawned with
   `setInterpolationDuration`, changed once (scale or rotation), removed by a scheduled task, and never
   persistent (`setPersistent(false)`). Brightness override 15 for a lit look. Semi-transparent textures on display
   items must be **verified in the client**.
6. **Measure.** The slice adds a perf probe `vfx.<spell>` beside `skill.cast` (`PerfProbe`) and records
   particles and decals per cast in the QA run.
7. **Sounds.** Vanilla until `assets/suld/sounds.json` exists. Each custom sound is mono .ogg ≤ 60 KB; all skill
   sounds together ≤ 1.5 MB.

## 5. Decal assets (shared)

| Decal | Model | Texture | Used by |
|---|---|---|---|
| `vfx/slash_arc` | 1 thin curved strip (3 cuboids), 2 blocks wide | 32×32 white-steel to class edge | Тэнгэрийн Цавчилт; later every melee slot 1 |
| `vfx/shock_ring` | 1 flat cuboid, 16×16 face, scaled by `Transformation` | 32×32 ring with a soft edge | Довтлох Үсрэлт, Бүхний Нурал, Хасар's roar and phases |
| `vfx/ground_crack` | 1 flat cuboid | 32×32 crack | Бүхний Нурал; later Дархан impacts |

## 6. The three Баатар slice VFX

Chosen to prove three different things: the most-cast spell (feel of the core loop), a travel-plus-impact spell,
and an ultimate. None of them changes damage, radius or timing; visuals only.

### A. Тэнгэрийн Цавчилт: core-loop slash

| Tick | Visual | Sound |
|---|---|---|
| 0 | `slash_arc` at chest height, facing the caster's yaw, rotating 90° over 4 ticks (interpolated), scale 1.0 → 1.3. 3 SWEEP_ATTACK along the arc (was 5). 10 class-red DUST (was 20) | PLAYER_ATTACK_SWEEP (1.0, 0.9) |
| 0 | Per enemy hit (cap 5): 4 red DUST + 2 CRIT at the target's chest | 1 × PLAYER_ATTACK_STRONG at the first target |
| 6 | Decal removed | — |

Budget: ≤ 43 particles, 1 decal for 6 ticks. Tier accent: T3–T4 + 6 bronze DUST; T5–T6 a tier-coloured arc edge
(a texture variant, no extra particles).

### B. Довтлох Үсрэлт: leap and shockwave

| Tick | Visual | Sound |
|---|---|---|
| 0 | 6 CLOUD at the feet on take-off | GOAT_LONG_JUMP |
| 1 → landing | 1 red DUST per tick behind the caster (≤ 20) | — |
| Landing | `shock_ring` at ground level, scale 0.5 → 7.6 over 6 ticks, so the ring edge matches the real 3.8-block hit radius. 30 BLOCK of the block below (was 40). 1 EXPLOSION (was 2) | GENERIC_EXPLODE (0.6, 1.4) + PLAYER_ATTACK_KNOCKBACK |
| +8 | Ring removed | — |

Budget: ≤ 57 particles, 1 decal for 8 ticks.

### C. Бүхний Нурал: ultimate ground shatter

| Tick | Visual | Sound |
|---|---|---|
| 0 | `shock_ring` scale 1 → 16 over 8 ticks (radius 8 = the hit radius). 6 `ground_crack` decals at radius 2–6, random yaw, flat on the ground. 40 BLOCK. 8 EXPLOSION on the ring (was 14) | GENERIC_EXPLODE (1.2, 0.6) + RAVAGER_ROAR (0.6, 0.7) |
| 2–10 | 30 class-red DUST rising from the cracks (3–4 per tick) | — |
| 4 | 20 BLOCK second burst | — |
| 40 | Cracks removed | — |
| Cooldown end | **New ready cue**: `BLOCK_NOTE_BLOCK_CHIME` (0.6, 0.8) to the caster only + HUD ✦ slot state | — |

Budget: ≤ 98 particles, peak ≤ 48 per tick, 7 decals for ≤ 40 ticks, 2 sounds + 1 cue.

Acceptance (all three): budgets met as measured by the perf probe; with sound off, a tester names each effect
from a 3-second clip; with eyes closed, from the sound; no FPS drop under 60 on a mid-range client with 4 Баатар
players casting together.

## 7. Status

| Item | Status |
|---|---|
| 20 spells + 15 ultimates with vanilla VFX | FUNCTIONAL_BUT_INCOMPLETE |
| Class visual language (§2) | CONCEPT |
| Budgets (§4) and the 5 over-budget effects | CONCEPT (no code change yet) |
| Decal models (`slash_arc`, `shock_ring`, `ground_crack`) | NOT_IMPLEMENTED |
| Slice VFX A, B, C | NOT_IMPLEMENTED (vanilla versions exist) |
| Ready cue for slot 4 and the ultimate | NOT_IMPLEMENTED |
| `sounds.json` and custom sounds | NOT_IMPLEMENTED |
| VFX perf probe | NOT_IMPLEMENTED |
| Client QA | NOT_IMPLEMENTED |
