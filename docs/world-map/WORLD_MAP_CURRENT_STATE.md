# World map — current state of the repository

> **SUPERSEDED (2026-10-13).** The owner chose real data only (no predicted terrain) and a map drawn from map items; the current design and what is built are in [`docs/WORLD_MAP.md`](../WORLD_MAP.md). This file is kept as the record of the first proposal.

**Status: INSPECTION REPORT (2026-10-13), no application code changed.** Read-only inspection of `main` @ `cd2000b`
for the world-map feature (the owner's master prompt of 2026-10-13 and three reference pictures: a parchment map
frame, an open atlas book with hand-drawn terrain and marker tabs, and in-world waypoint labels with distances).
Every fact below cites the file it was read from; nothing here was measured unless it says so.

## 1. Versions and client policy

| Fact | Evidence |
|---|---|
| Paper API `1.21.11-R0.1-SNAPSHOT`, Minecraft 1.21.11, Java 21 toolchain, Gradle 8.14.3 | `suld-plugin/build.gradle.kts:21-35`, `build.gradle.kts:28-53`, `gradle/wrapper/gradle-wrapper.properties:3` |
| Storage: H2 by default (MySQL mode), MySQL and PostgreSQL 16 supported; schema at **V25** | `config.yml:37-46`, `persistence/SchemaMigrator.java:34-65` |
| **Written policy: no client mods** — "Everything renders through vanilla 1.21.11 resource-pack features" | `docs/VISUAL_CONTENT_MASTER_PLAN.md:20`, `docs/HUD.md:6` |
| No client module, keybind code, Fabric/NeoForge/mixin, or plugin-message channel anywhere in the repo | search of the whole repo (only prose mentions "forge") |
| No map code: no `MapView`, `MapRenderer`, `MapCanvas`, `MapCursor`, `sendMap`; `FILLED_MAP` is only a menu icon | search of `suld-plugin` (`QuestMenu:72,167,357`, `Menus:455,525`) |

## 2. The world a map has to cover

| Fact | Evidence |
|---|---|
| Native vanilla border on the main world only: **diameter 10 000**, centre = spawn block + 0.5, re-applied whenever the city moves the spawn | `config.yml:342-348`, `worldbuild/WorldBorderService.java:45-78`, `SuldPlugin.java:444-448` |
| Spawn = city point `spawn` (city-local 0,2,18); the city anchor is stored in `plugins/SULD/worldbuild/slice-1.json` | `WorldBuildService.java:823-853`, `assets/world/kharkhorum/points.json:17-27` |
| **Bounds depend on the spawn**: x ∈ [sx+0.5−5000, sx+0.5+5000], z likewise. Dev example: x −5015.5…4984.5, z −5045.5…4954.5. A fixed `center: "x z"` stops the border following the spawn but the regions still follow it | `BorderSpec.java:44-51`, `docs/world/WORLD_BORDER.md:32` |
| All content is radial around the spawn: regions to r 5000, gates and sites validated to r ≤ 4900. The square's corners beyond r 5000 (~21.5 % of the square) have no region or area ("outside the map" in `QuestTracker`) | `content/world.json:4-5`, `DungeonSite.java:15`, `HistoricSite.java:15`, `QuestTracker.java:212` |
| Other worlds: `suld_halls` (void; dungeon halls, class-trial arenas) — not part of a world map | `dungeon/DungeonHalls.java:72-145`, `clazz/ClassTrialService.java:55-65` |
| **Pre-generation covers ~3.8 % of the square by default**: 14 940 chunks (P0 spawn 49×49, P1 corridors, P2 the 10 gates, P3 the 40 area hearts); the full 390 625 chunks only on `/suldworld pregen full confirm`. 13 of 16 historic sites and both world-boss spots are outside the default plan | `worldbuild/PregenService.java:130-158`, `PregenPlan.java:10-57`, `docs/world/PREGENERATION.md:3-13`, `docs/remediation/P1_ALPHA_STABILIZATION.md:101` |
| Paper exposes the generator's biome noise without generating chunks: `WorldInfo#vanillaBiomeProvider()` → `BiomeProvider#getBiome(info, x, y, z)` | `javap` of `paper-api-1.21.11-R0.1-SNAPSHOT.jar` (this inspection) |

## 3. Sources of map-worthy places (all already authoritative somewhere)

| Source | Count | Coordinate origin | Stored in | Read API | Secret? |
|---|---|---|---|---|---|
| City points / NPCs / relays | 30 / 14 / 5 | city-local from the build anchor | jar `points.json`, `worldbuild/slice-1.json` | `WorldBuildService.pointLocation`, `slicePoints`, `NpcService.RELAY_NAMES` | only `secret.*` (6, unbuilt, read by nothing) |
| City landmarks | 7 of 208 placements (`"landmark": true`) | city-local | jar `slices/slice-1.json` | none at runtime (spec is private: parse the JSON) | no |
| Historic sites | 16 (9 VERIFIED, 7 INSPIRED) | bearing + radius from the spawn | `content/sites.json`; built record `plugins/SULD/sites.yml` | `HistoricSiteService.where/location/seen` | no (the quest book shows every site's direction and distance, `QuestMenu.java:236-262`) |
| Dungeon gates (+ heroic) | 10 | bearing + radius from the spawn | `content/dungeons.json`; `plugins/SULD/halls.yml` | `DungeonHalls.gate(id)`, `gateXZ`, `DungeonContent.where` | no (an error message prints the gate's coordinates, `DungeonService.java:202-206`) |
| Ovoo (the soul ritual place of the death reform) | 40, one per area | the area's mid-bearing, mid-radius | computed; `plugins/SULD/ovoo.yml` | `OvooService.positions()` ("for maps"), `nearest` | no |
| Regions / areas | 9 / 40 | shapes around the spawn | `content/world.json` | `RegionIndex.at`, `WorldContent.areaAt`, `localLevel` | no |
| Relic shrines | 2 | random 600–1200 from the first-boot spawn, then absolute | DB `suld_world_unique_items` | `RelicService.all`, `shrineLocation` | **yes** — "deliberately imprecise so discovery stays an expedition" (`RelicHints.java:5`); coordinates are admin-only |
| World-boss spots | 2 (levels 20, 50) | historic site + (14, 6) | `content/events.json` | `WorldBossService.bosses`, `nextText` | no (announced) |
| Soul beacon (death spot) | ≤ 1 per player, 10 min | absolute | `suld_soul_beacons` (V23) | `SoulBeaconService.of(UUID)` | owner only |
| Training yard | 1 | absolute config | `config.yml training.*` | config only | no |
| Props (incl. campaign props) | 5 + campaign | city origin / site + offset | `props.json`, `campaigns.json` | `PropService.where` | no |
| Quest targets | 42 chapters (12 DISCOVER_LOCATION, 9 COMPLETE_DUNGEON, 15 KILL_MOB, 1 COLLECT_ITEM, 5 REACH_LEVEL) | derived from the rows above | `content/quests.json` | `QuestTracker.targetRegion`, `line()` | no |

Per-player discovery already stored: `suld_player_marks` (V17: DUNGEON_CLEAR, SITE_SEEN, OVOO_DAY; `MarksRepository`),
a 64-bit region/area mask in `suld_player_style.discovered_regions` (V6; regions bits 0–15 by list order, areas
16–55 by `Area.index`, 49 bits used; `PlayerStyle.discovered()`), heroic keys (V24), the server chronicle (firsts).

Danger inputs already in data: area/region level bands (`world.json`), the outer-land ramp 27 → 60
(`WorldContent.localLevel`), dungeon minimum levels 3/9/15/22/29/36/42/48/54/60 and party sizes (`dungeons.json`,
`DungeonLadder.java:21-40`), heroic keys (`HeroicLadder.java:14-27`), world-boss levels 20/50 and 2–20 fighters
(`mobs.json`, `WorldBossFight.java`). No location is gated by Ascension today. The HUD already rates danger in five
words from the gap between the spot's level and the player's: ≤ −6 «Хялбар», −5…−2 «Бага», −1…+2 «Тохиромжтой»,
+3…+5 «Өндөр», ≥ +6 «Үхлийн!» (`hud/HudService.java:297-318`).

## 4. Systems a map can reuse

| System | What it gives | Evidence |
|---|---|---|
| **Sky windows** (`gui/MenuSky`, `gui/SkillSky`) | A private stage above the player (Y = max height + 96 / + 48), a seat camera (`ItemDisplay` seat, `RETAIN_PASSENGERS`), owner-only displays (`setVisibleByDefault(false)` + `showEntity`), crosshair cursor by ray–plane intersection, WASD pan (`PlayerInputEvent`), wheel zoom (cancelled `PlayerItemHeldEvent`), left click (arm swing) and right click, Shift = dismount = close, crash-safe return via PDC, orphan sweep, anticheat hold | `MenuSky.java:172-336, 461-675`, `SkillSky.java:167-722` |
| Sky-window measured cost | SkillSky: 480 entities per open (114 nodes) | `docs/SKILL_SKY.md` "Verified live" |
| Drawing pieces | Tinted quad model (`suld:entity/skytree/line`), rotated line segments, a large textured panel (`skytree/bg` 512×288 under a budget exception), `suld:hd` TextDisplays, `ItemDisplay` icons | `MenuSky.java:251-356`, `SkillSky.java:379-394`, `tools/pack/gen_skytree.py:84-125` |
| Navigation maths (pure) | bearing, 8 arrows, 8 Mongolian compass words, region waypoints; `NavigationTest` | `suld-api/.../region/Navigation.java:10-55` |
| Guide bars | `QuestTracker` (yellow bar, every 10 ticks) and **`DungeonGuide`** (purple bar, a target the player chose, ends at ≤ 10 m / run start / 30 min / «Хөтөч зогсоох») | `quest/QuestTracker.java:107-194`, `dungeon/DungeonGuide.java:29-107` |
| World waypoint beam | `SoulBeaconService`: a 40-block `BlockDisplay` beam + a `TextDisplay` label shown to one player within 160 blocks | `death/SoulBeaconService.java` |
| Typed text | Paper Dialog `DialogInput.text(...).maxLength(256)` read in a `customClick` callback, main-thread hop | `clan/ChatScreen.java:58-126` |
| Entry points | «Сүлд Цэс» clock in hotbar slot 9 (right-click → hub); the hub's travel page has one free ornament tile | `listener/MenuListener.java:37,135-186`, `gui/Menus.java:896-937` |
| Settings | `PlayerSetting` bitmask (8 of 8 cards on the page used) | `api/style/PlayerSetting.java:3-48`, `Menus.java:362-380` |
| Persistence pattern | api repository + in-memory + JDBC trio, async executors, main-thread hop, dialect upserts, 23/40 retry, ITs on H2 and PostgreSQL | `persistence/JdbcWeaponMasteryRepository.java`, `JdbcMarksRepository.java`, `TestDb.java` |
| Content pattern | `content/*.json` with `"format": 1`, all-or-nothing validation, server override in `plugins/SULD/content/`, restart to apply | `content/ContentLoader.java:47-129, 93-104` |
| Rate limiting | a token-bucket `api/web/RateLimiter` (unused in main code) | `suld-api/.../web/RateLimiter.java` |

## 5. Hard limits

* **No M key.** A vanilla client sends the server only: movement keys, jump, sneak and sprint (`PlayerInputEvent`),
  hotbar selection and the wheel, swing, use, F (swap hands) and Q (drop). M is not one of them, and the server cannot
  open a new client screen (`docs/SKILL_SKY.md:7`). While seated, Shift always dismounts.
* **Most of the world is not generated** by default (§2), so a map drawn from chunk data alone would be ~96 % empty.
* The sky window refuses to open in combat (8 s), in a dungeon run, in a vehicle, gliding or falling; a click that
  opens a dialog closes MenuSky (`MenuSky.java:172-180, 511-552`).
* Pack: 12.4 MB uncompressed of a 20 MB budget; the ZIP is 8.9 MB, above the 8 MB soft target; textures ≤ 128 px
  unless an exception is written; ≤ 512 KB per file; font code points only ever appended (`assets/registry/pack_budget.json`,
  `tools/validation/validate_pack_budget.py:59-133`, `docs/ASSET_BUDGET.md:60`).
* Boss bars are crowded (about ten systems use them). The settings page is full (8/8).
* `TextDisplay` text uses the pack fonts (Cyrillic works); vanilla map items would not (their `MapFont` has no Cyrillic).

## 6. Defects found while inspecting (relevant to map positions)

| # | Finding | Evidence | Effect on a map |
|---|---|---|---|
| 1 | **`sites.yml` does not read back**: saved with dotted keys (`site.aurag.x`) but read with `getKeys(false)`, which returns only `site`. After a restart sites lose their guard, `where()` reports y = 80, and each site is rebuilt when its chunk loads | `region/HistoricSiteService.java:89-92, 305-314` | x/z stay right (they come from bearing + radius); heights wrong until rebuilt |
| 2 | Ovoo and site positions use the spawn at plugin enable; on the very first boot that is the spawn before the city exists, so they move after the next restart | `OvooService.java:88-93`, `HistoricSiteService.java:84-88` | the map must read the resolved positions, never recompute them |
| 3 | All seven Act II kill chapters point the quest HUD at Khentii (the first region that lists the shared mobs) | `QuestTracker.java:91-94` | a quest marker would inherit the same wrong region |
| 4 | Region discovery bits follow the order of `world.json`'s regions list; reordering it scrambles stored discoveries (areas use explicit indexes) | `RegionSpawner.java:327` | fog/"?" labels would be wrong after a reorder |
| 5 | Possible: spell combos may still fire inside a sky window (the swing is not cancelled and `SkillService.click()` does not check for an open window). From reading only, not tested | `SkillService.java:341-361` | the atlas must cancel swings it consumes |
| 6 | Stale docs: `PREGENERATION.md` (24 areas, ~9 000 chunks, relic shrines in P2), `OVOO.md:7` (24), `WORLD_BORDER.md` (1500–5000 ring); `docs/MASTERY_AND_PROFESSIONS.md` is cited by code but missing, and `STATUS.md` still says schema V24 | the files named | none (documentation) |

## 7. Tests and tools available

* Unit tests: suld-api 517, suld-plugin 155 (H2 ITs included), sim 13 (local results of 2026-10-13); 18 `Jdbc*IT` run on
  PostgreSQL in CI (`.github/workflows/ci.yml:31-64`).
* Bots (`tools/loadtest/`, not in CI): mineflayer cannot read dialogs or render pack glyphs; dialog buttons are pressed
  with a raw `custom_click_action`; sneak and riding need raw packets (`hubbot.js:86`, `heroicbot.js:29-45`,
  `skymenubot.js:84`, `skybot.js:44`). `skymenubot.js` (10 checks) is the model for a sky-window bot.
* Art tools: `tools/pack/*.py` (deterministic glyph/texture generators, preview PNGs), `tools/world/render_plan_map.py`
  (city plan only), `tools/pack/render_textdisplay.py`.
