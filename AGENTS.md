# SÜLD — notes for coding agents

Every coding agent that works in this repository (Claude Code, Gemini, OpenCode, Codex and others) reads this file
first. It describes the repository as it really is. Do not work from a template stack: there is no Redis, ProtocolLib,
Flyway, Citizens, DecentHolograms or ModelEngine here.

## The stack

| | |
|---|---|
| Game | Minecraft Java Edition **1.21.11**; no client mod. Players use the vanilla client, a server resource pack and vanilla input only |
| Server | **Paper 1.21.11** (`io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`, `suld-plugin/build.gradle.kts`) |
| Language | **Java 21**, Gradle (wrapper), JUnit 5 |
| Resource pack | `resourcepack/`, `pack_format` **75**, built into the plugin jar by `:suld-plugin:resourcePackZip` |
| Storage | HikariCP; `database.type` = `h2` (default, embedded, shaded) / `postgresql` / `mysql` / `memory` (`config.yml`) |
| Plugins next to SÜLD | soft-depends only: LuckPerms, PlaceholderAPI, Chunky, GrimAC (`plugin.yml`). All gameplay is our own code |

## Modules and packages

* `suld-api` — package **`mn.suld.api`**. Pure Java domain model and rules (balance, items, loot, quests, classes,
  map maths…). **No Bukkit/Paper import, ever**, so all of it is unit-tested without a server.
* `suld-plugin` — package **`mn.suld.plugin`**. The Paper adapter: `SuldPlugin` (bootstrap), `SuldServices` (the
  service registry), listeners, commands, GUIs, JDBC repositories. Also the progression simulator (`src/sim`,
  `src/simTest`), which never ships in the jar.
* `resourcepack/`, `tools/` (pack, map, content and validation scripts, `tools/loadtest/bots` Mineflayer bots),
  `deploy/`, `docs/`.

## The five classes

`mn.suld.api.clazz.PlayerClass`: **Баатар** (`BAATAR`, tank), **Мэргэн** (`MERGEN`, ranged), **Бөө** (`BOO`, support),
**Дархан** (`DARKHAN`, crafter), **Хүлэгчин** (`KHULEGCHIN`, mobility). There are no other classes.

## Core services (use them; do not write a second copy)

| Concern | Where |
|---|---|
| Combat damage, kills, party kill sharing | `plugin/combat/CombatListener` (`partyNearby` is the one party-share list) |
| Combos, spells, the Мэргэн bow | `plugin/skill/SkillService` |
| Player profiles | `api/service/ProfileService`, `plugin/profile/DefaultProfileService`; loaded in `AsyncPlayerPreLoginEvent` (`plugin/auth/AuthenticationService`), so a profile is cached before the join event |
| Items ↔ coins persistence order | `plugin/item/PlayerDataSaves` (read its javadoc before touching any trade, shop, smith or reward code) |
| Offline item delivery and full bags | `plugin/trade/Mailbox`, one instance from `SuldServices.mailbox(plugin)` |
| Minimap / atlas / screen markers | `plugin/map/MinimapService`, `AtlasSky`, `ScreenMarkers`, `WaypointGuide` |
| "Is a full-screen view open?" | `plugin/ui/UiFocus.isModal`; boss bars only through `plugin/ui/BossBars` |
| Schema migrations | `plugin/persistence/SchemaMigrator` + `suld-plugin/src/main/resources/db/migration/{postgresql,mysql}/V*.sql` (append a new version; never edit a released one) |

## Rules that are not negotiable

* **Main thread.** No JDBC, file or network IO on the server thread: repositories return `CompletableFuture`s on
  their executors, and results come back with the Bukkit scheduler. `Player#saveData()` is synchronous (about 5 ms):
  call `PlayerDataSaves.now` only where the ordering contract needs it, and never once per player inside a loop that
  pays many players in one tick.
* **State.** Key per-player maps by `UUID`, never by `Player` or `Entity`, and clear them on `PlayerQuitEvent`.
  Spawned display or map entities are non-persistent and swept on start.
* **Append-only ids.** Font glyph code points (`resourcepack/assets/suld/font/*.json`, `ui/Glyphs`, `hud/HudGlyphs`) and
  `PlayerSetting` bits are only ever appended. An existing code point or bit never gets a new meaning.
* **Text.** Adventure `Component`s; player-facing text is Mongolian Cyrillic. Legacy `§` codes only through the
  existing helpers that already take them.
* **The client is never trusted.**
* **Secrets** come from the environment (`deploy/suld.env.example` lists the names). Never commit a key, token,
  password or account number, and never log a webhook secret.
* **No model identifiers** in code, docs or commit messages.

## Before every push

1. `./gradlew build` — suld-api and suld-plugin tests, the simulator golden tests (`simTest`) and the shaded jar.
2. The JDBC integration tests on PostgreSQL:
   `SULD_TEST_PG_URL=jdbc:postgresql://127.0.0.1:5432/suld_it SULD_TEST_PG_USER=… SULD_TEST_PG_PASS=… ./gradlew :suld-plugin:test --tests "mn.suld.plugin.persistence.*IT" --rerun`
   (the database name must look like a test database; the ITs drop its schema).
3. `python3 tools/validation/run_all.py` and `python3 -I tools/plugins/test_fetch_plugins.py`.
4. A test that fails is fixed at its cause. Never weaken, skip or delete a test to get green.

Git hygiene: never force-push `main`; never commit conflict markers; one concern per commit, and the message says
what the diff does; delete a branch once it is merged. Scratch files, AI prompts and binaries do not belong in the
repository root; audit material goes under `docs/`. Crash and load tests run on a dev server, never on production data.

## Where to read more

`ARCHITECTURE.md`, `TESTING.md`, `DATABASE.md`, `docs/STATUS.md` (what is built and how it was verified),
`docs/CONTENT_DATA.md`, `docs/WORLD_MAP.md`, `docs/HUD.md`. The owner writes in Mongolian; answer in Mongolian.
