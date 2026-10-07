# Boss, elite and champion visual specification

Status: **design document, 2026-10-07. No custom mob or boss visual exists.** Every SÜLD mob is a vanilla entity
with a custom name (`mob/MobService.java`; content in `content/SuldContent.java`, `DungeonContent.java`,
`WorldContent.java`). Хасар is a vanilla RAVAGER. `dungeon/BossService.java` adds phase escalation, phase damage
scaling, an enrage timer and a ravager roar on phase change (`BossService.java:122`), nothing visual beyond that.

**Renderer: decided by the owner (Stage D1).** It is SÜLD's own Display-Entity renderer, specified in
docs/MODEL_RENDERER.md and implemented in `suld-api/.../model` and `suld-plugin/.../model`. BetterModel, ModelEngine,
MythicMobs and any similar runtime model plugin are excluded. Хасар is its first production rig. The status lines below
date from before D1; current statuses are in `audit/visual-content-status.json`.

## 1. Facts from the code

| Fact | Source |
|---|---|
| Tiers: NORMAL ×1, ELITE ×2.5, CHAMPION ×5, MYTHIC ×10, BOSS ×25, WORLD_BOSS ×80 (stat multipliers) | `suld-api/.../mob/MobTier.java` |
| Хасар: `mob.khasar`, "Хасар — Агуйн Эзэн", RAVAGER, BOSS, level 5, base HP 8 (→ 200), attack 0.25 (→ 6.25) | `SuldContent.java:49-51` |
| Хасар phases: Сэрсэн 100 % ×1.0 · Уурласан 60 % ×1.3 · Галзуурсан 30 % ×1.6 attack; enrage after 180 s | `SuldContent.java:53-56` |
| Dungeon Хасарын Агуй: level 2+, 1–4 players, two wolf waves (Говийн Чоно, Орхоны Чоно), then Хасар | `SuldContent.java:58-64` |
| Other dungeon bosses: Элсний Хаан (HUSK), Хар Баавгай — Ойн Эзэн (POLAR_BEAR), Мөсөн Хаан (STRAY) | `DungeonContent.java:31-57` |
| Elites today: Хангайн Баавгай (POLAR_BEAR, ELITE, level 14), Алтайн Аварга (RAVAGER, ELITE, level 23) | `WorldContent.java:35-41` |
| Target frames exist: `tgt_frame.png`, `tgt_frame_elite.png`, `tgt_frame_boss.png` | `resourcepack/assets/suld/textures/font/hud/` |
| Хасар's loot: Хасарын Соёо (16×16 sprite exists), Хасарын Зүрх (`item.khasar_zurkh`, no model) | `items/weapons.json`, `items/jewelry.json` |

## 2. Boss tier ladder: escalation rules

From the least to the most important. Each rung must beat the one below on at least **three** of: size,
silhouette uniqueness, bone and clip count, VFX, sound, UI, death presentation.

| Rung | Category | MobTier | Example | Scale (height vs player) | Bones | Silhouette | Clips (minimum) | VFX peak / tick | Sounds | UI frame | Death |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Champion | CHAMPION | pack leader of a wolf pack | 1.2–1.4× | shared template, 8–15 | template + 1 unique part (horns, banner, weapon) | idle, walk, attack, death | 15 | 1 custom or 2 vanilla | `tgt_frame_elite` | 20 ticks |
| 2 | Normal boss (field boss) | CHAMPION, named | region field boss | 1.4–2× | 12–20 | own head and weapon on a shared body | + roar | 25 | 2 | `tgt_frame_boss` | 30 ticks + loot beam |
| 3 | Elite boss | BOSS outside dungeons, rare spawn | rare steppe warlord | 1.6–2.2× | 15–25 | fully own | + telegraph, 1 phase change | 40 | 3 | boss | 40 ticks |
| 4 | Dungeon boss | BOSS | **Хасар**, Элсний Хаан | 1.8–3× | 24–35 | fully own, readable at the arena's full width | full set (§5.3) | 60 | 5–6 | boss + icon | 50 ticks, arena moment |
| 5 | Mythic boss | MYTHIC tier of a heroic dungeon | Хасар (heroic) | same as its dungeon boss | same rig | **variant**: new material layer + 1 new silhouette element (corrupted plates, a spirit crown) | dungeon set + 1 mythic clip | 70 | +1 | boss + mythic accent `#ff4040` | dungeon death + mythic flourish |
| 6 | World boss | WORLD_BOSS | server event | 3–5× | 40–60 | landmark-scale, readable at 64 blocks | + spawn arrival, 2 phase changes | 80 | 6–8 | boss + server-wide bar | 80 ticks, world broadcast |
| 7 | Endgame boss | BOSS / WORLD_BOSS stats, raid | Тэнгэрийн Ордон raid | 3–6× | 50–80 (split into 2–3 rigs if needed) | unique, multi-part, arena integrated | full set + intro, 3+ phases | 100 | 8+ | raid frame | cinematic (100+ ticks) |

Rules:

* Mythic versions reuse the rig and clips. They change **material and one silhouette element**, never only a tint.
* Glow (`setGlowing`) is a gameplay marker (relic bearer, Чонын Нүд) and is never a tier cue.
* Size cue: vanilla-backed mobs use `Attribute.SCALE`. Rigged mobs scale their bones, and the hitbox follows (§4).

## 3. Elite / champion visual categories

For the common mobs (directive §11), escalation must not rely on glow alone.

| Category | Silhouette additions | Armour / material | Size | VFX | Animation | Cheapest technique |
|---|---|---|---|---|---|---|
| Normal | none | base | 1.0× | none | vanilla or template | vanilla entity |
| Elite | 1 addition: horns, a bone mask, a plate collar | worn plates, scars | 1.1–1.2× | 1 ambient particle every 10 ticks, near players only | template | **Equipment layer**: `wolf_body` on wolves; `humanoid` armour + 3D head item on HUSK/STRAY. POLAR_BEAR and RAVAGER have no armour layer, so they need a rig |
| Champion | 2 additions: + banner/totem pole or weapon | darker, ornamented | 1.2–1.4× | ambient + attack accent | template + 1 unique attack | rig template + unique part |
| Mini-boss | own head or weapon | own palette | 1.4–2× | as normal boss | + roar | rig |
| Boss | own silhouette | own | §2 | §2 | §2 | rig |
| Mythic | variant layer | mythic material | as base | + mythic accent | + 1 clip | rig variant |
| World boss | landmark scale | own | 3–5× | §2 | §2 | rig |

Skeleton templates (rigged once, reused): **quadruped** (wolves, bear, Хасар-like beasts), **biped** (warriors,
tomb lords, spirits), **rider + mount** (cavalry bosses), **serpent / long body** (later).

## 4. Rendering contract (either renderer)

| Topic | Rule |
|---|---|
| Host | The vanilla backing entity keeps AI, pathing, hitbox and all `MobService`/`BossService` logic. It is made invisible and silent; SÜLD plays the sounds |
| Bones | One `ItemDisplay` per moving bone; static parts are merged into their parent. Bones are passengers of the host so they move smoothly on the client; each update sends only the `Transformation` (+ interpolation 2–3 ticks) |
| Yaw | Bone rotations include the host's body yaw (`LivingEntity#getBodyYaw`), so the model turns with the body, not the head |
| Update rate | 10 Hz in combat, 5 Hz idle, frozen beyond 48 blocks (LOD); culled per viewer by `Display#setViewRange` |
| Hit flash | Bone faces carry a `tintindex`; on damage the bone stacks get a `custom_model_data` colour tint (red-shifted) for 4 ticks. **Verify in the client** |
| Extra hitboxes | Parts that stick out past the host box (a head, a tail) get an `Interaction` entity that forwards hits to the host |
| Death | On `EntityDeathEvent` the bones are detached from the host and play the death clip on their own, then are removed |
| Name and HP | Host custom name hidden; the HUD target frame (`tgt_frame_boss`) and boss bar show name and HP |
| Network | Target ≤ 20 KB/s per animated boss per viewer (measured with spark/packet counters in the slice) |

## 5. Хасар — full specification

Identity (**ORIGINAL FICTION, proposal for owner approval**): *Хасар, Агуйн Эзэн* is the ancient alpha of the
cave wolves of Хэрлэн, as large as a ravager. Its hide carries the remains of the hunters who failed: scraps of
lamellar plates grown into the fur and three broken spear shafts in its back. This fits the dungeon's wolf waves
and its loot (Хасарын Соёо, "fang"; Хасарын Зүрх, "heart").

History note: *Хасар* is also the name of a historical person (Chinggis Khan's brother Qasar, general knowledge;
not in SÜLD's research set). SÜLD's Хасар must never be presented as, or connected to, that person in art or lore.

### 5.1 Silhouette

| Element | Spec |
|---|---|
| Overall | Low, front-heavy wolf-beast. Massive shoulders, head carried below the shoulder line, long tail held low. Reads as a wolf, not a dog or a bear, in the greyscale silhouette at 64 px |
| Size | Nose to tail 3.4 blocks; shoulder height 2.0; head top 2.3 when roaring; width 1.6. Host RAVAGER box is 1.95 × 2.2: the torso fits it and the head sticks out ≤ 0.6 block (the head gets an `Interaction` hitbox) |
| Unique marks | A hackle mane of black, coarse fur; 4–6 rusted lamellar plates on the shoulders and flank; 3 broken spear shafts in the back; a torn ear; pale amber eyes (2 px, never a glow) |
| Palette | fur `#2B2A2E` / `#4A4A50` / `#77767C`, underbelly `#8A7B68`, plates rust `#6E3B22` / `#9A5A32`, spear wood `#5A3E26`, eyes `#E0B24A`, gums `#7A1E1E` |
| Phases (visual) | Сэрсэн: mane flat, plates intact · Уурласан (60 %): **mane bones scale ×1.35**, ears back, eyes brighter variant · Галзуурсан (30 %): **two flank plates fall** (bones hidden, the plates drop as BLOCK crack particles), mouth stays open, breath vapour · Enrage (180 s): red tint pulse every 40 ticks + the howl clip |

### 5.2 Bones

| Bone | Parent | Cuboids | Animated by |
|---|---|---|---|
| `root` | host | 0 | yaw and position |
| `body_front` (shoulders, chest, plates, merged) | root | 24 | breathing, gait |
| `body_rear` (hips, spear shafts, merged) | body_front | 16 | gait |
| `neck` | body_front | 6 | look, bite |
| `head` | neck | 16 | look, bite, roar |
| `jaw` | head | 6 | bite, roar, idle pant |
| `ear_l`, `ear_r` | head | 2 + 2 | idle flick, phase 2 |
| `mane_1`, `mane_2` | body_front / neck | 6 + 6 | phase 2 scale |
| `plates_flank` | body_front | 8 | hidden in phase 3 |
| legs ×4: `upper`, `lower`, `paw` | body_front / body_rear | (4 + 3 + 3) × 4 = 40 | gait |
| `tail_1` … `tail_3` | body_rear | 3 + 3 + 2 | sway, gait |
| **Total** | | **≈ 150 cuboids, 25 displays** (budget ≤ 260 cuboids, ≤ 30 displays) | |

The `root` and the hidden variants are not separate displays, so the display count is 25 including the eye-variant
swap on `head`. The triangle upper bound is about 1,800 (≤ 3,120 at the budget cap).

Textures: two 128×128 atlases (`khasar_body.png`, `khasar_head.png`), 2 texels per model pixel on the head, 1 on the
body (the head is seen up close).

### 5.3 Animations

| Clip | Ticks | Loop | Trigger (code) | Notes |
|---|---|---|---|---|
| `spawn` | 40 | no | boss spawn after the last wave (`DungeonService`) | Climbs out of a fissure: emerges from 1.5 blocks below |
| `idle` | 80 | yes | default | Breathing, ear flick at tick 50, tail sway, slow pant |
| `walk` | 20 | yes | host speed > 0.02 | Playback rate scales with speed |
| `run` | 12 | yes | host speed > 0.2 | Bounding gait, mane flowing |
| `attack_bite` | 12 | no | boss melee hit (`BossService` damage handler, `BossService.java:105-113`) | Wind-up 5 ticks, snap 2, recover 5. Damage stays on the vanilla hit tick |
| `attack_swipe` | 14 | no | alternates with the bite (visual variety only) | Forepaw rake |
| `stagger` | 20 | no | its attack is blocked by a shield (vanilla ravager stun) | Shows the Баатар shield synergy; how the trigger is detected must be verified |
| `roar` | 40 | no | after `stagger`; also inside the phase clips | Head up, jaw open |
| `telegraph_pounce` | 24 | no | **proposed new attack, not in the code** | Crouch with the shock-ring decal at the landing spot; needs a gameplay spec first |
| `phase_2` | 30 | no | `PhaseChange` index 1 (`BossService.java:116-126`) | Mane rises, roar |
| `phase_3` | 30 | no | `PhaseChange` index 2 | Shakes; flank plates fall |
| `enrage` | 30 | no | `PhaseChange` with `enraged = true` | Howl + red pulse starts |
| `hurt` | 4 | no | damage taken | Tint flash only (§4) |
| `death` | 50 | no | `EntityDeathEvent` | Staggers, collapses on its side, the spears fall out, sinks 0.3 block, fades by removal |

### 5.4 VFX (vanilla particles + shared decals)

| Event | Effect | Particles | Decals |
|---|---|---|---|
| `spawn` | Ground breaks open | BLOCK (stone) 40, CLOUD 10 | `shock_ring` 1 |
| Run footfalls | Dust | BLOCK 4 every 5 ticks | — |
| Bite | Snap | SWEEP_ATTACK 1, DUST dark red 8 | — |
| Roar / phase change | Shock wave | CLOUD ring 24, DUST `#7A1E1E` 30 | `shock_ring` 1 |
| Phase 3 | Plates fall, breath | BLOCK (iron) 20, then CLOUD 2 every 10 ticks | — |
| Enrage | Red pulse | DUST red 20 every 40 ticks, ANGRY_VILLAGER 6 once | — |
| Death | Collapse, spirit leaves (ORIGINAL FICTION) | BLOCK 60, SOUL 12 | — |
| Rare loot drop | Loot beam | END_ROD column 12 | — |

Peak ≤ 60 particles per tick; the decal is shared with the Баатар slice VFX (`vfx/shock_ring`).

### 5.5 Sounds

Today only `ENTITY_RAVAGER_ROAR` (`BossService.java:122`). Planned custom events in `assets/suld/sounds.json`:
`suld:boss.khasar.growl`, `.bite`, `.roar`, `.howl`, `.step`, `.death` (mono .ogg, ≤ 60 KB each, ≤ 400 KB total).
Until those exist, use vanilla stand-ins (pitched-down wolf growl and howl, ravager attack, step and death), with
the sound keys checked against 1.21.11 because the wolf sounds became variants in 1.21.5.

### 5.6 UI and loot identity

| Item | Spec |
|---|---|
| Boss icon | 12×12 font glyph `textures/font/icon/boss_khasar.png` (head in profile, amber eye) for the target frame and the dungeon menu |
| Boss bar | Existing white boss bar + `tgt_frame_boss`; the phase name (Сэрсэн / Уурласан / Галзуурсан) shown on change |
| Хасарын Соёо | Re-paint the existing 16×16 sprite from the boss atlas colours, so the fang matches the model |
| Хасарын Зүрх | New 16×16 sprite (dark heart with an amber vein); today it shows the vanilla look |

### 5.7 Budget summary

| Metric | Budget |
|---|---|
| Cuboids / displays | ≤ 260 / ≤ 30 (plan: ≈ 150 / 25) |
| Textures | 2 × 128×128 PNG, ≤ 40 KB together |
| Meshy reference | 1 preview + refine, ≤ 60 credits including one re-roll; ≤ 30,000 tris; never shipped |
| Gemini | 4 drafts at 1K + 2 references at 2K (side view, three-quarter turnaround) |
| Network | ≤ 20 KB/s per viewer in combat (measure) |
| Server | 4 players + boss + waves: 20 TPS, mspt < 35 |

## 6. Production rules for later bosses

1. **Spec before credits.** Every boss gets a one-page spec (§5 format) approved before any Meshy task.
2. **Template first.** Use a skeleton template (§3) where it fits, and spend unique geometry on the head, weapon and
   signature parts.
3. **Silhouette test.** The greyscale silhouette at 64 px must not be confusable with another boss (side-by-side
   sheet in `assets/previews/bosses/`).
4. **Clip set** per §2 rung. Every gameplay attack has a telegraph of at least 10 ticks.
5. **Shared VFX decals** (`shock_ring`, `ground_crack`, `slash_arc`) before new ones.
6. **Credits:** ≤ 60 Meshy credits per dungeon boss, ≤ 120 per world or endgame boss.
7. **History labels** on every motif. Sensitive places: **Бурхан Халдун** is a real sacred mountain (general
   knowledge; not yet in SÜLD's research set). The Бурхан Халдуны Агуй boss needs a sensitivity review before any
   concept art: no burial or tomb imagery tied to real persons.

Directions for the existing bosses (CONCEPT): Элсний Хаан: a dried tomb lord of the Gobi with sand pouring from
lamellar gaps (biped template). Ойн Эзэн: a black forest bear with antler-grown moss and a bone collar
(quadruped). Мөсөн Хаан: an ice-sheathed guardian with frozen banners on its back (biped). All are ORIGINAL FICTION.

## 7. Slice elite: Хангайн Баавгай

| Aspect | Spec |
|---|---|
| Base | Existing ELITE `mob.khangai_baavgai` (POLAR_BEAR backing, level 14) |
| Template | Quadruped, 13 displays (body ×2, neck, head, jaw, 4 × 2 leg bones). Built from the same template as Хасар, so the template is proven twice |
| Elite cues | A bone-plate collar and a scarred muzzle (2 additions), 1.15× scale, ambient breath CLOUD 1 every 10 ticks near players |
| Palette | brown-black fur `#3A2C22` / `#5E4632`, collar bone `#D8CFB8` |
| Clips | idle 60, walk 24, attack 14, rear-up roar 30, death 40 |
| Budget | ≤ 90 cuboids, 1 × 128×128 atlas, ≤ 5 KB/s per viewer idle, ≤ 30 Meshy credits |

## 8. Status

| Item | Status |
|---|---|
| Boss ladder and elite categories | CONCEPT |
| Rendering contract (§4) | CONCEPT; renderer choice OPEN |
| Хасар visual spec | CONCEPT (identity awaiting owner approval) |
| Хасар model, textures, clips | NOT_IMPLEMENTED |
| Хангайн Баавгай elite visuals | NOT_IMPLEMENTED |
| Boss sounds (`sounds.json`) | NOT_IMPLEMENTED |
| Boss icon glyphs | NOT_IMPLEMENTED |
| Хасарын Зүрх sprite | NOT_IMPLEMENTED |
| Client QA | NOT_IMPLEMENTED |
