# 01: Feature and logic matrix

- **Audited commit:** `main` @ `50d5138` (2026-10-11 09:22 UTC).
- **Phase A:** read-only for application code. Nothing in the repository was changed apart from this folder.

Column meanings:

| Column | Meaning |
|---|---|
| Configured | Present in `config.yml` or content JSON. |
| Implemented | Code exists and is wired in. |
| Tested | Automated tests cover it (`suld-api` and `suld-plugin` unit tests, the JDBC ITs, `simTest`). |
| Runtime-verified | **This audit** saw it work on a live Paper server driven by Mineflayer bots. This is not a human client. "REPORTED" means an earlier repo document claims it; it was not re-checked here. |

The evidence for each **E-nn** reference is in `11-evidence-and-reproducibility.md`. Finding IDs (ONB-, PROG-, SEC-, …) are detailed in `02-critical-findings.md` and the topic reports.

> Compilation is not counted as verification. Everything marked "No" or "REPORTED" under Runtime-verified still needs a human client pass. The owner's own status files mark most UI as MANUAL_QA_REQUIRED.

## Core platform

| Feature | Configured | Implemented | Tested | Runtime-verified | Known gaps | Evidence |
|---|---|---|---|---|---|---|
| Build + test suite | – | Gradle 8.14.3, Java 21 | 493 tests (381 api, 99 plugin, 13 simTest), 0 failed, 0 skipped | Yes: `./gradlew build --rerun-tasks`, 59 s | No CI (`.github/` absent, though `tools/validation/run_all.py:3` mentions it); doc test counts stale | E-01 |
| PostgreSQL persistence (V1–V15) | `database.*` | `persistence/*`, `SchemaMigrator` | 11 `Jdbc*IT` classes / 19 tests; on H2 by default | Yes. ITs **against real PostgreSQL 16.15**: 19/0. Live boot: "migrations applied: 15", 13 tables | MySQL DDL is not transactional (`11` §runtime list) | E-02, E-05 |
| Asset / pack validation | `assets/registry/pack_budget.json` | `tools/validation/*.py` | 4 validators | Yes: ALL VALIDATIONS PASSED | Validators check structure, not how it looks in a client | E-03 |
| Account identity / auth | `auth.*` | `auth/AuthenticationService`, `OwnerService`; api `identity/*` | `SessionRegistryTest`, `AuthPolicyTest`, `PlayerIdentityTest` | Partial: offline-dev mode only. Banner shown; owners **not** auto-opped while insecure. Online mode could not be tested (`api.minecraftservices.com` not resolvable) | SEC-001 owner OP can't be revoked; SEC-002 default owner `qeevr_` | E-05, E-14 |
| First join → profile → reconnect | – | `PlayerLifecycleListener`, `DefaultProfileService` | `DefaultProfileServiceTest` (10) | Yes: class, EXP 17 %, 20 ₮ and quest 1/42 persisted across a relog | — | E-07 |
| Crash recovery (kill -9) | `AUTOSAVE_SECONDS=300` (code) | profile autosave + per-event saves | No crash test in the suite | Yes, **loss shown**: +150 EXP and a level-up (1→2) given 9 s before `kill -9` were lost; class, coins and quest were already saved | PERS-003 (and PERS-001/002/005 by code reading) | E-09 |
| Shutdown / startup | – | `SuldPlugin.onDisable`, `PoolShutdown` | `PoolShutdownTest` | Yes: clean `stop` and restart; re-boot "migrations applied: 0" | OPS-005 partial-construction leak (code) | E-05 |

## Onboarding and class

| Feature | Configured | Implemented | Tested | Runtime-verified | Known gaps | Evidence |
|---|---|---|---|---|---|---|
| Class selection (5 classes, permanent) | – | `gui/ClassSelectionGui` | none (gui package untested) | Yes, with bots. Opens at join. **Survives the plaza teleport** (no server close packet). **Reopens 0.1 s after ESC**. Click selects the class and the first quest | ONB-001 pack prompt vs menu (client-only, untested); ONB-006 no confirm; GAME-003 sets not cleared on quit | E-06 |
| Starting health | – | `SkillTreeService.applyBaseHealth:416-423` | – | Yes, **defect**: after the class pick the player stands at 23/43 (Мэргэн), 23/48 (Бөө), 24/82 (Баатар), all 0 armour. Health is only clamped down, never filled | GAME-005, PROG-001 | E-08 |
| Tutorial (7 steps, paid once) | – | `branding/TutorialService`, api `TutorialProgress` | `TutorialProgressTest` | Partial: steps 1–2 fired with "+40 EXP, +20 ₮" | ONB-005 last step points to a closed dungeon; LANG-002/003 typos | E-06 |
| Welcome card, titles, tips | `tips.*`, `ui.welcome-screen` | `OnboardingService`, `TipsService`, `GuideBoards` | – | Yes: card, 2 titles, 2 boss bars in the first 8 s; the "how to play" card **repeats on every join** | ONB-007 overload; ONB-010 stale "18 бүлэг / 4 агуй" | E-06, E-07 |
| Resource pack delivery | `resource-pack.required: true` | `ResourcePackService` (self-host :8164 or URL) | `RigAssetsTest`, `SkillPackAssetsTest` | Partial: offered at join (6,787 KiB, sha1 `13567cef…`); **Mineflayer auto-accepts, so the client prompt was not exercised** | ONB-001, ONB-008 declining disconnects; OPS-004 | E-06 |
| Bedrock (Geyser/Floodgate "core") | `deploy/plugins/plugins.json` | none in the plugin | – | No | ONB-009 | – |

## Gameplay systems

| Feature | Configured | Implemented | Tested | Runtime-verified | Known gaps | Evidence |
|---|---|---|---|---|---|---|
| Combat (mitigation, gap scaling, charge) | – (code constants) | `combat/CombatListener` (1,045 LOC) | api `CombatCalculatorTest`, `BalanceRulesTest`; **plugin combat 0 tests** | Load only: 985 hits / 1,350 damage events in 4 min at 10 bots, `combat.hit` p99 0.3 ms | PROG-001 newbie lethality; GAME-002 classless kills | E-10 |
| Spells / skill tree (25 spells, 575 nodes) | `skills.*` | `skill/*` (5,408 LOC), JSON trees | `SkillTreeTest` (22), `SkillEngineTest` (20), loader; `/skillsadmin qa` harness (REPORTED 312 pass) | Load only: 105 casts, p99 3.7 ms | SP-3 free orb respec; spells hardcoded in an enum (04) | E-10 |
| Story quests (42 chapters) | `content/quests.json` | `quest/*` | `QuestChainTest`, `QuestEngineTest`, `ContentIntegrityTest` | Partial: chapter 1 assigned, "1/42 Анхны Ан 0/3" persisted | ONB-011 silent start; PROG-002/005 EXP pricing | E-07 |
| Dungeon ladder (10) + halls world | `content/dungeons.json` | `dungeon/*` (2,485 LOC) | `DungeonRunTest`, `HallBlueprintTest`, loot band test; **plugin 0 tests** | Partial: `suld_halls` world created at boot; no run executed (bots never entered) | PROG-003 JSON rewards ignored; PROG-004 gates by index; DG-7 no enrage wipe | E-05 |
| Bosses (3D Display-Entity rigs) | `content/mobs.json` | `model/*`, `dungeon/brain/*` | `RigAssetsTest` | Partial: 30 rigs loaded; `model.spawn` p99 11.6 ms at load | Visual check needs a client | E-05, E-10 |
| Region mobs / spawner | `world.*` | `mob/RegionSpawner` | – | Load only | ONB-003 south exit into L7–9; ONB-004 tracker says "wait" in town | – |
| Death: loot/EXP loss, soul lock | `death.*` | `death/*` (V12) | `DeathRulesTest`, `DeathLockTest`, `JdbcDeathRepositoryIT` | No | ONB-002 5-min lock at L1; stale death text | – |
| Items, loot, reforge, salvage | `items/*.json` | `item/*` | many api tests (`LootEngineTest`, `ItemValidatorCodecTest`, …) | Partial: catalog loaded (220 items, 22 affixes, 33 sets, 44 tables) | DU-1 same-inventory dup caught only at join; PERS-001 armour upgrade crash window | E-05 |
| Relics (2 world-unique) | `items/relics.json` | `relic/*` | `JdbcRelicRepositoryIT` (24 concurrent claims) | Partial: shrines placed at boot | — | E-05 |
| Economy: shop, sell, `/pay`, trade | partly | `gui/Menus`, `trade/*`, `CityCommands` | `JdbcProfileTransferIT` (3) | No | PERS-004 trade revert; EC-4 labels | – |
| Daily reward / tasks | `daily.*` | `reward/*` | `DailyRewardTest`, `DailyTasksTest` | Opened the GUI only (3 rapid `/daily` opened 3 windows; claim not clicked) | PERS-002 guard and reward written separately | E-07 |
| Clans, parties, chat | `clan.*` | `clan/*`, `party/*` | `ClanRegistryTest`, `JdbcClanRepositoryIT` | No | THR-001 async chat reads Bukkit state; PERS-006 | – |
| World events (wolf raid) | `content/events.json` | `worldevent/*` | `WorldEventRunTest` | No | PROG-006 max-rarity loot | – |
| Ovoo / historic sites | `content/sites.json` | `region/*` | `OvooCircleTest` | No | PERS-002 crash double-pay; HIST-002 false citation | – |
| Mounts | – (code) | `mount/*` | – | No | Hardcoded (04) | – |
| Cosmetics / ranks / credits | `cosmetics.*`, `store.*` | `style/*` | `StyleTest`, `JdbcStyleRepositoryIT` | Yes, earlier session (credits could not buy without balance; commit history) | SEC-003 `/credits give` not idempotent; HIST-025/027 sacred and Chinggis names sold | – |
| Ascension I–III | – | `AscensionMenu`, api `Ascension` | `BalanceRulesTest` | No | — | – |
| Analytics funnel | – | `analytics/*` | `JdbcAnalyticsIT` (1) | Yes: `session_start`/`session_end` rows logged with stats | `resource_pack_ready`, `first_objective`, `five_minute_active` missing; `RETENTION_*` never emitted; `FIRST_BOSS` fires every clear (`DungeonService.java:580`) | E-06, 03 |
| GrimAC integration | `anticheat.grim.*` | `anticheat/*` | `MovementExemptionsTest`, `TokenBucketTest` | REPORTED (earlier session, dev server) | OPS-006 kicks only | – |
| Payment / instant revive | `store.*` | **not built** (console `/credits give` only) | – | No | Design-only; correctly gated | – |

## Advertised but absent

These are declared in the docs but have no code:
- Resurrection quests and soul shards
- Achievements
- Cosmetic battle pass, emotes, pets, mount skins
- Web dashboard

See `09` and the contradiction table C1–C22 in `06`/`09`.

## Coverage gap that matters most

The plugin packages that carry the riskiest state transitions have **no test files**:

| Package | Lines of code |
|---|---|
| combat | 1,045 |
| dungeon | 2,485 |
| relic (service) | 1,148 |
| trade | 652 |
| death (service) | 652 |
| clan | 1,018 |
| quest | 515 |
| gui | 4,240 |
| command | 3,517 |

Their pure rules are tested in `suld-api`, but the Bukkit glue (ordering, persistence calls, events) is exercised only by hand or by the in-game QA harness. Every PERS-series finding sits in this glue.
