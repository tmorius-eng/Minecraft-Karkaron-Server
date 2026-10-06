# SÜLD — Project State

> Living status doc for continuity across sessions. Update it whenever state
> changes. Dates are UTC.

**Last updated:** 2026-10-06
**Build:** `./gradlew build` green (24 tests). Asset validations green.
**Current phase:** Foundation + asset-pipeline proven. Starting Vertical Slice 1.

---

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
  - **Meshy** — `api.meshy.ai` reachable; credential injected by the cloud egress
    proxy (no key in env/repo); balance 1100; text-to-3D task created and
    **SUCCEEDED**.
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
- **Meshy asset download**: model files are served from `assets.meshy.ai`, which
  the egress policy **denies (403)**. Generation works; retrieval does not.
  → Allow `assets.meshy.ai` to complete Meshy→Blender ingestion.
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
2. Dungeon → party → boss → loot → progression.
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
