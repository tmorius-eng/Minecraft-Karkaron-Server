# SÜLD — Project State

> Living status doc for continuity across sessions. Update it whenever state
> changes. Dates are UTC.

**Last updated:** 2026-10-07
**Build:** `./gradlew build` green (289 api + 53 plugin + 12 simulator tests). Asset validations green. Live Paper 1.21.11 + PostgreSQL verified.
**Current phase:** Vertical Slice 4 (world relic → discovery → global uniqueness → broadcast → ownership) implemented and live-tested with bots on PostgreSQL (docs/RELICS.md). Auth done (docs/AUTHENTICATION.md). Slices 1–4 await one manual Minecraft-client pass.

**Since the slices (all live-tested with bots on Paper 1.21.11):** hardcore death (DeathService), the 18-chapter
storyline + quest tracker, four dungeons, region discovery rewards (V6), daily reward (V7), leaderboards, steppe
horses, chat guard, MOTD/icon, tips, operator guide (docs/OPERATIONS.md), Bedrock UDP port in the firewall.

**Progression master plan (2026-10-07), Stages A + B done, awaiting owner approval of the numbers:**
forensic audit (`docs/PROGRESSION_EXPLOIT_AUDIT.md`, `audit/*-status.json`), the progression simulator
(`./gradlew :suld-plugin:simulate`, `docs/PROGRESSION_SIMULATION.md`: live game = level 60 in 17 h; proposed = 206 h,
12/12 compliance checks) and the spec set (`docs/PROGRESSION_BALANCE_SPEC.md` and the dungeon, gear, ascension,
mastery, difficulty, economy, class-armour, active-playtime, class-gear, death, instant-revive (deferred), payment
(deferred) and visual-pipeline docs). Nothing of the proposed balance is in the game yet. Next: Stage C (Баатар
vertical slice: death/recovery → class gear → armour + ActivePlaytime → assets).

**Overnight 2026-10-07/08 (all on `main`, bot-tested on the dev Paper; visuals MANUAL_QA_REQUIRED):**
* **World:** 10 000-block native border centred on the spawn (docs/world/WORLD_BORDER.md); SÜLD's throttled
  pre-generator, priority areas only (docs/world/PREGENERATION.md); every SÜLD teleport is async.
* **Dungeons:**
  * a gate in the open world plus a themed hall (10 themes) in the void world `suld_halls`, with 12 instance slots
    per theme (docs/world/DUNGEON_HALLS.md);
  * a 10-dungeon ladder, with the previous dungeon required (docs/world/DUNGEON_LADDER.md);
  * archetype boss brains (cleave, slam, charge, barrage, nova, summon) for the 9 bosses without a hand-written
    brain.
* **Models:** 29 new Display-Entity rigs. Every SÜLD mob and boss now has a model (docs/models/MOB_RIGS.md).
* **Chat channels and the `/chat` dialog screen** with player heads (docs/CHAT.md).
* **Lock-on and class attack decals** (docs/COMBAT_FEEL.md).
* **Persistent 7-step tutorial** (docs/TUTORIAL.md).
* **NPC dialogue** with history topics (docs/NPC_DIALOGUE.md).
* **50-player benchmarks:**
  * best on the 4-core bench: F50, TPS 11.8, mean tick 92 ms, p95 139 ms;
  * measured Paper tuning applied by deploy.sh;
  * view 6 / simulation 4;
  * the release gate is **not** passed on this bench (docs/perf/WORLD_50.md).
* **Наадам festival:** archery outside the south gate (`/naadam`), a horse race around Kharkhorum (`/uraldaan`),
  and 24 ovoo to circle three times clockwise for a blessing (docs/world/NAADAM.md, docs/world/OVOO.md).
* **Fixes from two code reviews:** see the commit log.
* **Gameplay logic review (11 fixes):**
  * melee follows the attack charge; a sweep hits for 30 %;
  * farming fatigue survives a relog, and clan EXP scales with it;
  * the smith upgrades single gear only;
  * a trade offer counts toward the death loss;
  * coins and inventory are saved together on sell and trade;
  * single refunds and build loads pay the respec price (a refund within 2 minutes stays free);
  * spell and ultimate cooldowns survive a relog;
  * the steppe horse's saddle is locked;
  * souls cannot trade or join parties;
  * the death loss is rounded at random;
  * the respec orb no longer charges coins by surprise.
* **Functional bots:** 16/16 passed.

---

## Vertical Slice 1 (join→class→HUD→quest→combat→mob→loot→tooltip→EXP→level→save→reconnect)
- Domain (suld-api, unit-tested): combat calculator, loot tables/roller, item instance/definition/stats,
  mob definitions+tiers, quest engine, profile currency+quest persistence.
- Plugin (suld-plugin, compiles+links+enabled live): ItemFactory (PDC + rarity tooltip), ResourcePackService
  + /suldpack, ClassSelectionGui (mandatory first-login), HudService (scoreboard), MobService (custom wolf),
  CombatListener (damage/EXP/loot/quest/level-up), QuestService, V2 migration (currency+quest columns).
- LIVE SMOKE (automated, verified): Paper 1.21.11 boots (Done 25.3s); SULD remapped→loaded→ENABLED→disabled
  cleanly; plugin `libraries:` JDBC drivers download+load at runtime; commands registered.
- LIVE DB (automated, verified): with storage=postgresql, HikariCP connected to PostgreSQL 16, V1+V2
  migrations applied (6 tables incl. suld_profiles with currency+quest columns), clean pool shutdown.
  Also verified graceful fail-fast + self-disable on a bad DB config.
- REMAINING: ONE manual Minecraft-client test (join→pack→class GUI→in-world combat/loot→reconnect).
  Not headlessly automatable (no MC client); all that logic is unit-tested (combat/loot/quest/persistence).

## Vertical Slice 4 (world relic → discovery → global uniqueness → broadcast → ownership) — docs/RELICS.md
- Relics **Хөх Сүлд** (lvl 10, +25% EXP) and **Алтан Гэрэгэ** (lvl 6, +15%): exist once; DB row is the only truth.
- V4 migration: shrine columns, owner name, history actor/detail; DB CHECK (OWNED ⇔ owner) + UNIQUE(owner) (one
  relic per bearer). Every ownership change = version compare-and-set + history in one transaction.
- Pure: `RelicValidator` (forged/stale/extra/foreign/container copies removed, bearer re-issued), `RelicRules`,
  `RelicHints`. Plugin: `RelicService` (auto-built shrines, 10s ritual, validator loop, death→seize/return,
  72h offline expiry, admin return/give/recover/setshrine/tp), `RelicListener` (drop/container/bundle/frame/
  stand/allay/hopper/item-entity/explosion guards), `/relic`, central `ProgressionBoosts` (clan + relic EXP).
- Live (Paper + PostgreSQL + bots): ritual claim, forged + exact-duplicate copies removed, container stash purged,
  death return, admin grant, **PvP seize** — all as specified. Real-PostgreSQL IT: 24 concurrent claims → 1 winner.
- Bugs found & fixed: `ItemInstance` crashed on statless items (EnumMap copy of an empty Map) — caught live.
  **Resource pack was incompatible with 1.21.11** (pack_format 34 + legacy `overrides`, removed in 1.21.4):
  migrated to format 75 (`min_format`/`max_format`) + `assets/minecraft/items/*.json` model definitions;
  validator now enforces both and cross-checks registry CMD → model mappings.

## Authentication & identity (docs/AUTHENTICATION.md)
- Identity = Minecraft/Microsoft-authenticated UUID (online-mode). No /register, /login or passwords. Name is
  display-only; renames keep the same character (audited). Fail closed when accounts aren't verified.
- Pure (suld-api): `PlayerIdentity`, `AuthPolicy` (+v4-UUID anti-spoof rule), `SessionRegistry`,
  `KeyedSequencer`, `AuditEvent`. Plugin: `AuthenticationService` (pre-login profile acquisition, session
  binding, session-aware release, pending-session sweeper), `JdbcAuditLog` → `suld_audit_log`, `/suld auth`.
- Fixed two real pre-existing bugs: quit→instant-rejoin stale read (progress rollback) and duplicate-login
  orphaned profile (new session lost its profile). Mutation-tested: removing sequencing fails the race test.
- LIVE (Paper 1.21.11 + PostgreSQL 16, mineflayer bots): fail-closed refusal, first join w/o any login prompt,
  reconnect, simulated rename (same UUID), duplicate-session handoff, 15x rapid reconnect — all as specified.
- NOT verified: a genuine Microsoft-authenticated join (needs a real account; sandbox blocks Mojang auth hosts).
- deploy.sh refuses ONLINE_MODE!=true and forces the dev flag off; health-check fails on either.

## Vertical Slice 3 (clan → world event → social progression)
- Domain (suld-api, unit-tested): `Clan`/`ClanMember`/`ClanRank` (Ноён/Түшмэл/Цэрэг), `ClanRegistry` (unique
  case-insensitive names + upper-cased tags incl. Cyrillic, invites w/ expiry, rank permissions, leader must transfer
  before leaving, capacity by level), `ClanProgression` (10 levels; capacity 10→28; member EXP bonus +2%/level),
  `WorldEventRun` (shared goal, per-player contribution, expiry, podium rewards 1.5/1.3/1.2x, min contribution).
- Persistence: **V3 migration** (`suld_clans`, `suld_clan_members`) — DB enforces one clan per player (PK), unique
  name/tag, cascade delete. `JdbcClanRepository` on a single ordered writer thread; structural changes save
  immediately, contribution EXP flushes every 60s + on shutdown. Opt-in real-PostgreSQL integration test
  (`JdbcClanRepositoryIT`, needs `SULD_TEST_PG_URL`) — passed against PostgreSQL 16.
- Plugin: `ClanService`, `/clan …` + `/cc`, async-safe chat tags `[TAG] Name » msg`, HUD clan/event lines,
  `WorldEventService` (**Чонын Довтолгоо**: 30 wolves in 10 min, raiders spawn on the surface around eligible
  players, server boss bar, auto-start every `world-events.interval-minutes`), `/suldevent` (admin start/stop).
- Social progression hooks: +2 clan EXP per SÜLD kill, +150 per dungeon clear, +5 per event kill, +200 per clan on
  event success; clan EXP bonus applied to mob, dungeon and event EXP. Founding a clan costs 500 coins (config).
- Fixed along the way: vanilla wolves are neutral, so Slice 2 dungeon waves never attacked — `MobService.keepHostile`
  now keeps wave/event wolves targeting players. A malformed `plugin.yml` (caught by the live boot) is now guarded
  by `PluginDescriptorTest`.
- LIVE (automated, verified): Paper 1.21.11 + PostgreSQL 16 — migrations V1–V3 applied, a seeded Cyrillic clan
  loaded on the next boot, world event start/stop + analytics, clean disable.
- NOT verified: in-world clan/raid play (needs clients).

## Vertical Slice 2 (party → dungeon → boss → loot → progression)
- Domain (suld-api, unit-tested): `Party`, `PartyRegistry` (invites w/ expiry via injected Clock, leader rules,
  capacity, busy-in-dungeon), `DungeonDefinition`, `DungeonRun` state machine (ENTERING→WAVE→BOSS→COMPLETE/FAILED),
  `BossDefinition`/`BossPhase` (HP-threshold phases, descending order, phases only escalate).
- Plugin: `PartyService` (messaging adapter), `DungeonService` (entry validation, wave spawning, boss encounter,
  per-participant rewards, wipe/timeout/abort/cleanup, boss bar, HUD status line), `BossService` (phase
  escalation, per-phase damage scaling, enrage timer), `DungeonListener`, commands `/party` and `/dungeon`.
- Content (`SuldContent`): dungeon **Хасарын Агуй** (`dungeon.khasar_den`, lvl 2+, 1-4 players, 2 waves + boss
  Хасар, RAVAGER, 3 phases, 180s enrage); new items (Хасарын Соёо epic, Хасарын Зүрх legendary, Талын Түшиг).
- Rules: reward only to participants still online and not downed (hardcore); run ends on boss kill, party
  wipe, all leave, abort, or 20-minute cap; all spawned mobs are removed on any end.
- LIVE SMOKE (automated, verified): plugin loads on Paper 1.21.11 with new listeners; `/party`, `/dungeon` registered.
- NOT verified: the in-world run (needs a client). Logic is unit-tested; instancing is *logical* (per-party tracked
  mobs at the leader's location), not a separate world — world-level instancing comes with WorldBuilder.
- No V3 migration: parties/runs are transient by design; persistence only when needed (run history/leaderboards).

## Repository audit (per master directive §49)

### 1. What already works
- **Gradle multi-module build** (`suld-api` pure domain, `suld-plugin` Paper
  adapter), Kotlin DSL, Java 21 toolchain, Gradle 8.14 wrapper. `./gradlew build`
  compiles and passes 24 unit tests. Mirror-based repo config survives Central
  rate-limiting; Paper API `1.21.11-R0.1-SNAPSHOT` resolves and the plugin
  **compiles and links** against it.
- **Domain layer** (`suld-api`): player profiles (versioned, synchronized),
  data-driven level/EXP engine, five classes, item rarity tiers, config model +
  factory, async `ProfileRepository` (+ in-memory impl), analytics interface,
  domain events + dispatcher, service contracts.
- **Plugin layer** (`suld-plugin`): bootstrap + `SuldServices` container, Bukkit
  config bridge, HikariCP datasource factory, dialect-aware JDBC profile repo
  (MySQL/PostgreSQL), forward-only schema migrator, world-unique-item storage
  constraints, join/quit lifecycle with analytics, `/suld` and admin `/revive`.
- **Asset pipeline (verified end-to-end this session):**
  - **Meshy** — FULLY PROVEN. `api.meshy.ai` + `assets.meshy.ai` allowed;
    credential proxy-injected (no key in env/repo). Preview -> refine
    (meshy-7.1, 4K, PBR) SUCCEEDED; real textured GLB (28.6MB, 30147 tris,
    baseColor+metalRough+normal) downloaded and rendered in Blender.
  - **Blender 4.0.2** headless — Cycles CPU render + glTF/OBJ export working
    (numpy installed, OIDN denoiser disabled). djeada **Blender MCP** (27 tools)
    verified; `blender_python_exec` headless transport runs `blender -b`.
  - **Blockbench headless MCP** (23 tools) — created + validated `.bbmodel`s;
    authored, textured, validated (0/0) and exported the legendary sword to a
    Minecraft model JSON.
  - **Proven asset:** `weapon.suld_ild_tenger` (legendary saber) — bbmodel →
    MC model JSON → resource pack → Blender render `assets/previews/`.
  - Validation scripts (`tools/validation/`) green.

### 2. What is broken / blocked (not faked)
- **Meshy** download now works (`assets.meshy.ai` allowed). RESOLVED.
  Remaining nuance (not a bug): vanilla Minecraft Java item models are box-only,
  so a high-poly Meshy mesh is the promo/NPC/reference asset; the in-game item
  model is a Blockbench low-poly box model guided by the Meshy silhouette.
- **Blockbench built-in renderer** needs a GPU (WebGPU/Dawn) — none in the
  container. Workaround in use: render exported geometry via Blender (CPU). Not
  a blocker.
- **Live Paper test server**: `fill-data.papermc.io` + Mojang hosts
  (`piston-data`/`piston-meta.mojang.com`, `libraries.minecraft.net`) are denied,
  so the server jar can't be downloaded. Plugin is compile+link verified; a live
  smoke test needs those hosts allowed. Harness ready (`scripts/run-test-server.sh`,
  `runServer`).
- **MCP tools in-session**: MCP servers load at **session start**. `.mcp.json`
  + `scripts/setup-cloud-tools.sh` make them reproducible, but their tools appear
  only in a **new** session after setup runs.

### 3. What should be preserved
All of the above — the Phase-1 foundation is sound and the pipeline is proven.
Nothing here should be discarded.

### 4. What should be refactored (planned, not yet done)
- Module split will grow (combat, items, mobs, UI, …) as vertical slices land;
  keep boundaries dependency-driven, not speculative.
- `JdbcProfileRepository` optimistic-lock enforcement (compare-and-set on
  `version`) to be hardened during the anti-dup slice.

### 5. What is missing (the bulk of the game — roadmapped)
(Update: the class skill tree is built — see docs/SKILL_TREE_ARCHITECTURE.md and audit/skill-tree-status.json
for what is complete, what needs a person with the game client, and what is not implemented.)
Combat engine, skills, full item system, loot, mobs/bosses, dungeons, quests,
clans, economy, achievements, the custom UI/HUD/TAB/rank/tag stack, world
regions, spawn, resource-pack delivery service, web/API. See ROADMAP.md and the
vertical-slice plan below.

### 6. Dependency risks
Only infrastructure deps (Paper API compileOnly, HikariCP, JDBC drivers, JUnit).
No gameplay plugins. Central rate-limiting mitigated by the GCS mirror. Audit in
DEPENDENCIES.md.

### 7. Security risks
Anti-duplication is designed (DB constraints + optimistic locking + audit) but
only the storage layer exists; enforcement lands with the item/relic slices.
Meshy credential is never in env/repo (proxy-injected). No secrets committed.

### 8. Performance risks
None measured yet (no live server). All DB I/O is async off-thread by design.
Benchmarks come with the combat/mob slices.

### 9. Exact implementation order (vertical slices)
1. **Slice 1 (next):** join → resource-pack delivery → class selection → HUD →
   first quest → combat → mob death → loot → tooltip → EXP → level-up → save →
   reconnect. Build the HUD/Scoreboard/TAB/Rank/Tag/ResourcePack/Gui services as
   custom code.
2. ~~Dungeon → party → boss → loot → progression.~~ **Done (Slice 2).**
3. ~~Clan → event → social progression.~~ **Done (Slice 3).**
4. ~~World relic → discovery → global uniqueness → broadcast → ownership.~~ **Done (Slice 4).**
5. World regions + spawn + content expansion.

---

## Asset pipeline status
See ASSET_PIPELINE.md. One complete asset proven (`weapon.suld_ild_tenger`).
No mass generation yet (per directive). Registry: `assets/registry/assets.json`.

## Deployment toolkit (ready, NOT deployed — no VPS exists yet)
`deploy/` + DEPLOYMENT.md: deploy.sh (idempotent, two-run, --dry-run), update.sh (build while running,
auto-rollback), backup.sh (online-consistent, rotated, checksummed), start/stop/restart/console/op.sh,
health-check.sh, firewall.sh (ufw), build-pack.sh (reproducible, hash-named), systemd unit + daily backup
timer, nginx pack hosting. `ADMIN_PLAYERS="qeevr_"` is opped on first boot. Verified in the sandbox against a
real Paper server + PostgreSQL 16 (see DEPLOYMENT.md "What has been verified"); apt/nginx/ufw/systemd lifecycle
are verified only on the first real deploy.

## Known good commands
- `./gradlew build` — compile + test.
- `python3 tools/validation/run_all.py` — asset/resource-pack validation.
- `scripts/setup-cloud-tools.sh` — install Blender + MCP servers (new session).
- `scripts/run-test-server.sh 1.21.11` — Paper test server (needs CDN hosts).
