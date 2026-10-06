# SÜLD — Project State

> Living status doc for continuity across sessions. Update it whenever state
> changes. Dates are UTC.

**Last updated:** 2026-10-06
**Build:** `./gradlew build` green (72 tests). Asset validations green. Live Paper 1.21.11 + PostgreSQL verified.
**Current phase:** Vertical Slice 2 (party → dungeon → boss → loot → progression) implemented; Slice 1 live-verified. Both await one manual Minecraft-client pass.

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
3. Clan → event → social progression.
4. World relic → discovery → global uniqueness → broadcast → ownership.
5. World regions + spawn + content expansion.

---

## Asset pipeline status
See ASSET_PIPELINE.md. One complete asset proven (`weapon.suld_ild_tenger`).
No mass generation yet (per directive). Registry: `assets/registry/assets.json`.

## Known good commands
- `./gradlew build` — compile + test.
- `python3 tools/validation/run_all.py` — asset/resource-pack validation.
- `scripts/setup-cloud-tools.sh` — install Blender + MCP servers (new session).
- `scripts/run-test-server.sh 1.21.11` — Paper test server (needs CDN hosts).
