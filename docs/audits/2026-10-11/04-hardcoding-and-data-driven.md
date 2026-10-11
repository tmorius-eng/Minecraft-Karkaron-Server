# 04: Hardcoding and data-driven design

- **Audited commit:** `main` @ `50d5138`.
- **Evidence:** static, file:line.
- **Path prefixes:**

  | Prefix | Path |
  |---|---|
  | `plg/` | `suld-plugin/src/main/java/mn/suld/plugin/` |
  | `api/` | `suld-api/src/main/java/mn/suld/api/` |
  | `content/` | `suld-plugin/src/main/resources/content/` |
  | `items/` | `suld-api/src/main/resources/items/` |

## Verdict

SÜLD is **more data-driven than most custom servers of its age**. These are all JSON with a validating loader that cross-checks references and refuses broken files:
- world, regions and areas
- sites
- mobs
- dungeons
- quests
- items, affixes, sets, loot tables and rarity bands
- skill trees

The remaining hardcoding falls into two groups:
- **Justified.** The balance *formulas* are shared with the offline simulator (`suld-plugin/src/sim`), so code and simulation cannot drift.
- **Not justified, and inconsistent in places.** These are mostly class stats, spells, the dungeon ladder, economy prices, onboarding text and the UI strings.

The three inconsistencies that actively mislead a designer:
1. **PROG-002: the level curve exists twice.**
   - `config.yml:19-23` drives levelling.
   - `api/balance/Balance.java:16` drives chapter, login and task EXP.
   - Editing the config silently changes the 35 % story share.
2. **PROG-003: dead data.**
   - `dungeons.json` `completion` rewards are required by the loader but overridden for all 10 dungeons (`plg/dungeon/DungeonService.java:593-605`).
   - `discoveryExp` is parsed but never read.
3. **DATA-001: a lying javadoc.** `api/clazz/PlayerClass.java:14-17` says class stats are "never hard-coded"; `:21-33` hard-codes them.

## Counts (measured with grep/python, E-04)

| Measure | api | plugin | Total |
|---|---|---|---|
| Cyrillic string literals in Java | 584 (43 files) | 2,401 (99 files) | **2,985** |
| Lines containing them | 425 | 1,899 | 2,324 |
| Legacy `§` colour codes | – | – | 96 on 58 lines |
| Raw `getConfig().get*/is*` calls bypassing the typed `SuldConfigFactory` | – | 61 in 20 files | 61 |
| `locale: mn` read by anything | no (`SuldConfig.locale()` has no caller) | | |
| Message bundle / language file | none (`plg/ui/Messages.java` holds only prefix and colours) | | |

**Biggest text holders:**

| File | Cyrillic literal lines |
|---|---|
| `plg/gui/Menus.java` | 196 |
| `plg/gui/QuestMenu.java` | 89 |
| `plg/command/CityCommands.java` | 86 |
| `plg/command/ItemCommands.java` | 85 |
| `plg/gui/SkillMapMenu.java` | 77 |
| `api/style/CosmeticCatalog.java` | 73 |

## Classification by subsystem

| Rating | Meaning |
|---|---|
| 1 | Correctly data-driven |
| 2 | Partially data-driven |
| 3 | Unnecessarily hardcoded |
| 4 | Hardcoded for a justified reason |
| 5 | Inconsistent across code, config and docs |

| Subsystem | Rating | Representative evidence |
|---|---|---|
| Combat | 4 → 2 | `CombatCalculator(50.0)` `plg/combat/CombatListener.java:47`; +0.75 attack/level `:70`; crit 5 % `:81`; sweep ×0.3 `:119-121`; mitigation K = 10 + 2.5L, cap 0.75 `api/balance/CombatRules.java:8-22`. Shared with the simulator (justified) but there is no override file |
| Classes | **5** | `api/clazz/PlayerClass.java:21-33` HP/ATK/resource; javadoc `:14-17` says the opposite; growth 4 %/level `CombatRules.java:35-37` |
| Spells | 2 | Trees are JSON; spells are a Java enum: names, costs, multipliers `api/skill/Spell.java:17-46`, unlock levels `:48`, per-slot cooldowns `{0,1.5,4,5,10,18}` `:51`, ranks 40/55 `:57`; radii in `plg/skill/SkillService.java:355,403,423,450` |
| Quests | 2 / 5 | `content/quests.json`, validated by `plg/content/ContentLoader.java:345-394`; EXP from the Java curve `:351,386` (PROG-002); hidden chapter-level pricing heuristic `:376-384` (PROG-005) |
| Dungeons | **5** | Waves, boss, site and loot are JSON. The ladder is Java (`api/balance/DungeonLadder.java:30-40`: min, max, band, chapter gate **as an index**, party). `completion` is required but ignored. `minLevel` is duplicated. Fatigue `DungeonService.java:415-438`, 20-min cap `:61`, enrage ×1.25 `plg/dungeon/BossService.java:152` |
| Mobs / bosses | 2 (stat formula 4) | Identity, tier and loot are JSON; stats from `api/balance/MobScaling.java:12-68` (justified). Spawner shares and radii in `plg/mob/RegionSpawner.java:41-42,106-170` |
| Items / loot | **1** (some 3) | `items/*.json`. Rarity multipliers enum `api/item/ItemRarity.java:24-32`; reforge materials by band `api/item/Reforge.java:19-24`; sell and reforge formulas `api/balance/Economy.java:17-26` |
| Economy / shop | 3 / 5 | Supplies and prices `plg/gui/Menus.java:570-579`; sell labels "8 ₮ / 25 ₮" disagree with the formula `:599`; travel 10 / blessing 30 `plg/npc/NpcService.java:81-82`; ranks `api/style/Rank.java:14-21`; level rewards `api/style/LevelRewards.java:15-32`; `/pay` cap `plg/command/CityCommands.java:333` |
| Daily / tasks | 3 / 5 | Login coins `api/reward/DailyReward.java:31-33`; EXP via the Java curve `:38`, `api/reward/DailyTasks.java:43` |
| Onboarding | 3 / 5 | Steps are a Java enum (`api/onboarding/TutorialProgress.java:15-22`). Four separate step lists: `OnboardingService.java:101-114`, `TutorialProgress`, `Menus.java:103-112`, `GuideBoards.java:54-101`. Stale counts «18 бүлэг / 4 агуй / 24 газар» in `Menus.java:106`, `TipsService.java:22`, `ServerListService.java:26` (server-list MOTD), `GuideBoards.java:57,79` |
| Clans / parties / events | 2 | `content/events.json`; clan EXP `plg/content/SuldContent.java:39-40`; clan cap 10 `api/clan/ClanProgression.java:9`; party 4 `plg/party/PartyService.java:24`; event pacing `plg/worldevent/WorldEventService.java:50-53` |
| Death | **1** (docs 5) | All levers in `config.yml:53-68`; pure rules `api/death/*`. Player text `GuideBoards.java:82-83` and `docs/OPERATIONS.md:117-120` disagree with the config |
| Mounts | 3 | `plg/mount/HorseService.java:48-50` (unlock level 5, cooldown, coat by rank) |
| UI text / localisation | 3 / 5 | 2,985 literals; mixed bilingual titles ("Заавар · Tutorial"); `locale` unused |
| Coordinates / world | **1** | `content/world.json` bearings and radii; `assets/world/kharkhorum/points.json`; QA arena constant `plg/skill/qa/QaKit.java:134` (justified). The safe-square half-size of 51 does not match the built city (ONB-004) |

## Legitimate code invariants (leave in code)

- **Boss brains and spell *behaviour*.** These are logic, not numbers (`plg/SuldPlugin.java:613-667`, `SkillService`).
- **The *shape* of `MobScaling`, `CombatRules` and `ExpRules`.** Only their constants should move to data, so the simulator and the server read the same file.
- **The pure death, relic and identity rules.** These are safety invariants with tests.
- **Security limits.** For example, the `/pay` amount range and the token-bucket click limits are safety, not balance. They may be configurable, but must have hard bounds.

## Validation and failure behaviour

**Content packs: fail-safe to defaults, but quietly.**
- If the server's edited content has *any* issue, the plugin logs warnings and **runs the bundled content instead** (`plg/content/Content.java:48-59`).
- The bundled files must always be valid (`ContentDataTest`); good.
- But an admin who mistyped one id gets all of their edits silently reverted. The only signal is a console warning.
- **Recommendation:**
  - Add `content.strict: true` to refuse start-up on issues, for production.
  - Or show a red notice to OPs on join while bundled fallback content is active.

**Items and skill trees.**
- A broken catalog keeps the previous one (`plg/item/ItemService.java:125`, `plg/skill/SkillTreeService.java:174`).
- On first boot "previous" is the bundled file. That is the same pattern and has the same quietness.

**Typed config.**
- `SuldConfigFactory` falls back to defaults on an unknown enum (`SuldConfigFactoryTest`).
- 61 direct reads bypass it, so their defaults live at the call sites.
- `/suldpack reload` rebuilds only the pack settings (OPS-003).
- `config.yml:3` advertises a `/suld reload` that does not exist.

**Reference checks.**
- These are good:
  - chapter targets, wave mobs, bosses, loot tables and sites-in-areas are all checked (`ContentLoader.java:261,271,331-337,364-373`);
  - code-required ids are enforced (`:50-61`).
- **Missing:**
  - ladder ↔ chapter order (PROG-004);
  - ladder min ↔ JSON `minLevel`;
  - dungeon `region` ↔ site location (DATA-002, wrong for 6 of 10).

**Schema versioning.**
- `quests.json` has a `version` (3) and the DB has V1–V15 migrations.
- The content files have no per-file schema version check beyond the loader's structural validation.

## Ranked migration plan (smallest designs that remove recompiles)

| # | Change | Fixes | Effort |
|---|---|---|---|
| 1 | **One curve everywhere.** Pass the configured `LevelCurve` into `ContentLoader.load`, `DailyReward.exp` and `DailyTasks`. Keep `Balance` only as the default | PROG-002 | S |
| 2 | **`dungeons.json` owns the ladder:** `max`, `band`, `requiresChapter: "quest.…"` (an id), `party`, optional `completion` override. Build `DungeonLadder.RUNGS` from content; the simulator loads the same file. Loader check: `requiresChapter` comes before the chapter that targets the dungeon | PROG-003, PROG-004, duplicated min level | M |
| 3 | **`classes.json`** (hp, hpGrowth, attack, attackPerLevel, resourceMax, difficulty, role text, starter-skill name). The enum keeps identity only | DATA-001 | M |
| 4 | **`spells.json`** (cost, multiplier, cooldown, unlock level, radius) with named behaviour handlers. Copy the existing `SkillTreeLoader` pattern | spell tuning without a release | M |
| 5 | **`balance.yml`**: flat typed overrides for the combat constants, `MobScaling` targets, `ExpRules`, dungeon fatigue and cap, spawner shares and radii, tier multipliers, reforge price. The simulator reads the same file | the largest single cut in recompiles | M |
| 6 | **`economy.json`**: shop, NPC prices, ranks, level rewards, daily coins, `/pay` cap, reforge bands. Sell labels computed from `Economy.sellPrice` | EC-4 | S–M |
| 7 | **`tutorial.json`**: one ordered step list. Card, menu and boards render from it; every count («бүлэг», «агуй», «газар») is generated from the content pack | ONB-010, stale MOTD | S–M |
| 8 | **`lang/mn.yml` message bundle** (MiniMessage + `{placeholders}`). Migrate one file at a time, starting with `Menus`, `QuestMenu`, `CityCommands`, `ItemCommands`. Replace the 96 `§` codes. Wire `locale` | localisation, consistent terminology (06) | L, incremental |

Each step needs:
- a loader with the same `Issue` list and fallback semantics as `ContentLoader`;
- a test that the bundled file is valid;
- for steps 2–5, a simulator parity test (`SimulationTest` already does this for the live model).

**Do not build:**
- a generic scripting engine;
- a database-backed content editor.

The existing JSON + loader + validator pattern is the right size.
