# Asset budget

Status: **design document, 2026-10-07.** The pack budget limits in
[`assets/registry/pack_budget.json`](../assets/registry/pack_budget.json) are enforced by
`tools/validation/validate_pack_budget.py`. Everything else here (runtime, geometry and per-asset budgets) is a
target that the Баатар slice will measure.

## 1. Current pack (measured 2026-10-07)

| Measure | Value | Source |
|---|---|---|
| Built pack (owner's figure) | **285.8 KiB** | owner / earlier build |
| ZIP on disk now | 349,224 B (341.0 KiB), built 2026-10-07 08:57 | `suld-plugin/build/pack/suld-resourcepack.zip` (Gradle `resourcePackZip`, `suld-plugin/build.gradle.kts:106`) |
| Validator's ZIP estimate | 346.2 KB | `validate_pack_budget.py` (builds a ZIP in memory) |
| Uncompressed | 1,294,396 B (1.2 MB) | file sizes under `resourcepack/` |
| Files | 368: 268 PNG, 99 JSON, `pack.mcmeta` | validator |
| Download at 10 Mbps | 0.28 s | validator |

Where the bytes are (uncompressed):

| Part | Files | Bytes | Share |
|---|---|---|---|
| Class weapon models (`models/item/weapon/`, voxel extrusion, already minified) | 32 | 1,104,132 | **85 %** |
| Font textures (HUD, badges, icons, GUI, logo) | 126 | 106,848 | 8 % |
| Other item models | 35 | 7,296 | < 1 % |
| Item textures (incl. weapon 32×32) | 68 | 24,558 | 2 % |
| Vanilla overrides (`assets/minecraft/`) | 78 | 23,820 | 2 % |
| Fonts (`hud.json`, `ui.json`) | 2 | 18,613 | 1 % |
| NPC skins | 7 | 4,540 | < 1 % |

The largest single files are `weapon/baatar_4.json` (50.1 KB), `baatar_3.json` (44.3 KB) and `khulegchin_4.json`
(42.8 KB). They compress well, which is why the ZIP is about a quarter of the raw size.

**The budget validator fails today.** It reports 102 findings: 73 duplicate textures and 29 textures over the 128 px
limit. All of them are in the HUD and GUI (`assets/suld/textures/font/hud/`, `assets/minecraft/textures/gui/sprites/hud/`):
intentional transparent duplicates and wide strips (e.g. `fill_hp.png` 896×12, `fill_tgt_chip.png` 2048×10) that
are not in `justified_large`. Because `deploy/build-pack.sh` runs `tools/validation/run_all.py` first, the deploy
pack build stops until the allowlist and duplicate rules are fixed. That fix is outside this document set and
should come before the slice's pack work.

## 2. Hard limits (`pack_budget.json`)

| Limit | Value |
|---|---|
| Ordinary texture | ≤ 128 px on the longest side |
| Hero texture | ≤ 256 px, only for ids listed in `hero_textures` (today: `suld:item/suld_ild_tenger`) |
| Exceptions | `justified_large` with a written reason (today: the five 176 px GUI backgrounds) |
| One file | ≤ 512 KB |
| Whole pack | ≤ 20 MB |
| Forbidden in the pack | `.glb .fbx .obj .blend .stl .usdz` (`.psd` should be added, per the directive) |
| Duplicates | identical textures fail (the HUD needs an allowlist) |

## 3. Pack size targets

| Milestone | ZIP target | Reason |
|---|---|---|
| Today | 0.34 MB | — |
| After the Баатар slice | ≤ 1.0 MB | One class of armour + boss + elite + relic + decals |
| All five classes, slice-level content | ≤ 4 MB | 30 armour sets, 5 weapon lines, 4–6 bosses |
| Full game with sounds | ≤ 8 MB (hard cap 20 MB) | < 7 s at 10 Mbps; mobile hotspots stay usable |
| Sounds alone | ≤ 3 MB | mono .ogg, ≤ 60 KB per effect |

## 4. Texture sizes per category

| Category | Size | Why |
|---|---|---|
| Ordinary item sprite | 16×16 | Vanilla density; the inventory reads at 16 px |
| Class weapon (voxel) | 32×32 (existing) | Extrusion source; more pixels give the silhouette and edge detail |
| Hero weapon model (T6) | 64×64 | A cuboid model seen in first person and close up |
| Armour equipment layer | **128×64** | 2× vanilla 64×32: lacing and rivets at 1 px while the large shapes stay at skin density. 256×128 is not planned (pixels 4× smaller than the skin's would clash) |
| Helmet item model | 32×32 (T1–T3), 64×64 (T4–T6) | Higher tiers have more cuboids and ornament |
| Attachment model | 32×32, or 64×64 at T5–T6 | Small on screen |
| Inventory icons (chest, legs, boots) | 32×32 | Match the class weapons' density |
| NPC skin | 64×64 | Vanilla skin format (1×) |
| Horse barding layer | 64×64 | Vanilla `horse_body` size |
| Elite atlas | 128×128 | ≤ 15 bones; 1 texel per model pixel |
| Boss atlas | 2 × 128×128 | Head at 2×, body at 1× |
| World or endgame boss | up to 4 × 128×128, or one 256×256 via `justified_large` | Landmark scale; justify in writing |
| VFX decal | 32×32 | A soft ring or crack; scaled by the display transform |
| UI glyph icon | 8×8 or 12×12 | The existing font-glyph grid |

## 5. Runtime budgets

| Topic | Budget | Notes |
|---|---|---|
| Player attachments (`ItemDisplay`) | 0 (T1–T2), 1 (T3–T4), 2 (T5–T6) per player | Hidden from the wearer; auto-off when > 30 attachment entities are within 48 blocks of a viewer |
| Boss displays | ≤ 30 (dungeon), ≤ 60 (world), ≤ 80 (endgame, split rigs) | §5 of BOSS_VISUAL_SPEC |
| Elite displays | ≤ 15 | quadruped or biped template |
| Rigged mobs alive per player-area | ≤ 6 elites + 1 boss within 48 blocks | Normal mobs stay vanilla-backed |
| NPC Mannequins | ≤ 25 within 48 blocks of spawn | static |
| VFX decals | ≤ 3 per spell, ≤ 8 per ultimate, lifetime ≤ 40 ticks | SKILL_VFX_SPEC §4 |
| Particles | slot 1 ≤ 60, slots 2–3 ≤ 180, slot 4 ≤ 250, ultimate ≤ 400 per cast; peak ≤ 80 per tick | SKILL_VFX_SPEC §4 |
| Network per animated boss | ≤ 20 KB/s per viewer (target, to be measured) | 10 Hz combat, 5 Hz idle, frozen beyond 48 blocks |
| Server tick | 4-player Хасар fight: 20 TPS, mspt < 35 | spark profile in the slice |
| Client | ≥ 60 FPS on a mid-range client with 4 players casting in the arena | QA row |

Texture memory (RGBA, 4 bytes per pixel): a 128×64 layer is 32 KiB. One armour set (2 layers + a 64×64 helmet + 3
icons at 32×32 + one 64×64 attachment) is about 112 KiB of VRAM. Thirty sets come to about 3.3 MiB, which is
small. Item textures share the item atlas, whose size depends on the GPU, so keep item textures ≤ 64 px.

## 6. Per-asset budget: Баатар slice

| Asset | Pack files | Elements / bones | Textures | Pack bytes (target) | Meshy credits | Gemini |
|---|---|---|---|---|---|---|
| `armor.baatar_t1` | equipment JSON, 2 layers, helmet model + item def, 3 icons | helmet ≤ 12 | 2 × 128×64, 32×32, 3 × 32×32 | ≤ 14 KB | 0–30 | 3 × 1K + 1 × 2K |
| `armor.baatar_t2` | same | helmet ≤ 20 | same | ≤ 16 KB | 30 | 3 + 1 |
| `armor.baatar_t3` | + pauldron model | helmet ≤ 30, pauldrons ≤ 24 | + 32×32 | ≤ 22 KB | 30 | 3 + 1 |
| `armor.baatar_t4` | + pauldron model | helmet ≤ 40, pauldrons ≤ 24 | helmet 64×64 | ≤ 28 KB | 30 | 3 + 1 |
| `armor.baatar_t5` | + pauldrons + half-cape | helmet ≤ 50, 2 × ≤ 24 | 64×64 | ≤ 34 KB | 30 | 3 + 1 |
| `armor.baatar_t6` | + pauldrons + back standard | helmet ≤ 60, 2 × ≤ 24 | 64×64 | ≤ 38 KB | 30 | 3 + 1 |
| `weapon.baatar_sabre_t6` | model + item def + texture | ≤ 60 | 64×64 | ≤ 20 KB | 30 | 2 + 1 |
| `mob.khangai_baavgai` | 13 bone models + item defs + rig JSON | ≤ 90 cuboids, 13 displays | 128×128 | ≤ 40 KB | 30 | 2 + 1 |
| `boss.khasar` | 25 bone models + variants + rig JSON + 1 icon | ≤ 260 cuboids (plan ≈ 150), 25 displays | 2 × 128×128 + 12×12 | ≤ 90 KB | 60 | 4 + 2 |
| `relic.khukh_suld_model` | item model + shrine model | ≤ 40 + ≤ 60 | 32×32 + 64×64 | ≤ 20 KB | 30 | 2 + 1 |
| `npc.zuutyn_darga` | skin + cape | — | 64×64 + 64×32 | ≤ 3 KB | 0 | 1 + 1 |
| 3 VFX decals | 3 models + 3 textures | 1–3 each | 3 × 32×32 | ≤ 6 KB | 0 | 3 × 1K |
| Sounds (optional in the slice) | `sounds.json` + ≤ 6 .ogg | — | — | ≤ 400 KB | 0 | — |
| **Slice total** | | | | **≤ 350 KB** without sounds, **≤ 750 KB** with | **≈ 420** (reserve 500) | **≈ 54** |

## 7. Tracking method

1. `python3 tools/validation/run_all.py` on every pack change (`deploy/build-pack.sh` already runs it; there is no `.github/workflows/` in the repo yet, although `run_all.py` mentions CI).
2. `validate_pack_budget.py` prints the total, the ZIP size, the largest five files and texture and model counts.
   Paste the summary into the slice's QA record.
3. `assets/registry.json` holds `fileSize`, `elements`, `triangleCount` and `textureSize` per asset; refresh them
   from disk after each pack change (the 2026-10-07 values were measured).
4. `assets/reports/<asset_id>.json` holds the per-asset Blender, Meshy and Minecraft numbers
   ([ASSET_PRODUCTION_PIPELINE §4](ASSET_PRODUCTION_PIPELINE.md#4-per-asset-report)).
5. Runtime numbers come from spark (TPS, mspt), the `PerfProbe` timers (`skill.cast`, planned `vfx.*` and
   `model.*`), and a packet-byte counter per viewer during the boss QA run.
6. Meshy balance before and after every task, logged in the report. Gemini image count per asset, in the report.

## 8. Status

| Item | Status |
|---|---|
| Pack size limits and validator | FUNCTIONAL_BUT_INCOMPLETE (validator fails on the HUD; `.psd` not forbidden) |
| Texture size policy (§4) | CONCEPT |
| Geometry and runtime budgets (§5) | CONCEPT (not measured) |
| Per-asset slice budget (§6) | CONCEPT |
| Per-asset reports | NOT_IMPLEMENTED |
| Network and FPS measurement method | NOT_IMPLEMENTED |
