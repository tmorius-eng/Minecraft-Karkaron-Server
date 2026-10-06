# SULD Architecture

## Guiding principles

1. **Own every gameplay system.** All MMORPG mechanics are written in this
   repo. Third-party libraries are permitted only as infrastructure/utility
   (database, pooling, serialization, networking, testing) and never as a
   substitute for a gameplay system.
2. **Separation of concerns.** Pure game logic is isolated from the Minecraft
   platform so it can be reasoned about and unit-tested without a server.
3. **Data-driven.** Tunable values (level curve, death harshness, loot, …) live
   in configuration, not in code.
4. **Async I/O, safe state.** Database work happens off the main thread;
   per-player state has a single writer and immutable value objects for
   snapshots.
5. **No static global state.** Services are constructed once and passed
   explicitly (a container held by the plugin), never reached through statics.

## Modules

```
suld (root)
├── suld-api      Pure-Java domain: models, game logic, service CONTRACTS.
│                 No Bukkit/Paper/Spigot dependency. Unit-tested in isolation.
└── suld-plugin   Paper adapter: plugin bootstrap, Bukkit config bridge,
                  commands, listeners, JDBC persistence, analytics sinks.
                  Depends on suld-api + Paper API.
```

As the server grows this becomes "a collection of custom Paper plugins/modules
with a shared SULD API": later gameplay domains (skills, mobs, dungeons, clans)
are added as new modules depending on `suld-api`, keeping clean service
boundaries. Phase 1 ships the two foundational modules.

### Why the split matters

`suld-api` compiles and tests with **no Minecraft on the classpath**. The level
curve, progression engine, config mapping, rarity rules, and anti-dup value
objects are therefore testable in milliseconds and portable to a future
web/API service that must agree with the server on the same math.

## Layers & key types

| Layer | Package (module) | Responsibility |
|---|---|---|
| Domain model | `mn.suld.api.*` | `PlayerProfile`, `Progression`, `PlayerClass`, `ItemRarity` |
| Game logic | `mn.suld.api.progression`, `…service` | `ProgressionEngine`, `DefaultProgressionService` |
| Contracts | `mn.suld.api.persistence`, `…analytics`, `…event`, `…config` | `ProfileRepository`, `AnalyticsSink`, `EventDispatcher`, `ConfigView` |
| Platform wiring | `mn.suld.plugin` | `SuldPlugin` (bootstrap), `SuldServices` (container) |
| Adapters | `mn.suld.plugin.{config,persistence,event,analytics,ui}` | Bukkit config bridge, JDBC repo, Bukkit event dispatcher, logging sink, Adventure messages |
| Entry points | `mn.suld.plugin.{command,listener}` | `/suld`, `/revive`, join/quit lifecycle |

## Data flow — player join (representative path)

```
PlayerJoinEvent (main thread)
   → ProfileService.loadOrCreate(uuid, name)
        → cache hit?  → return cached profile
        → else ProfileRepository.find(uuid)      [IO thread / HikariCP]
              → present : update name, cache
              → absent  : create new, save, cache [IO thread]
   → (back on main thread via scheduler)
        → AnalyticsSink.record(FIRST_LOGIN / SESSION_START)
        → greet / prompt class selection
```

EXP flow: gameplay calls `ProgressionService.grantExp(profile, amount, source)`
→ `ProgressionEngine` computes the new immutable `Progression` → profile updated
→ `ExpGainedEvent` / `LevelUpEvent` dispatched → analytics recorded. The engine
is pure, so the rules are unit-tested directly.

## Concurrency model

- **Main thread** owns all Bukkit API calls (entities, messages, scheduler).
- **IO executor** (daemon pool in `SuldServices`) runs all JDBC work;
  `ProfileRepository` returns `CompletableFuture`s that complete off-thread.
  Results hop back to the main thread (`BukkitScheduler.runTask`) before
  touching Bukkit state.
- **`PlayerProfile`** is internally synchronized; composite state (class,
  progression, timestamps) is swapped through guarded methods and each change
  bumps a `version` counter used for optimistic locking.
- **`Progression`** and config records are immutable value objects, safe to
  share freely.
- **Domain events** are plain objects dispatched via `EventDispatcher`; the
  Bukkit adapter re-publishes them on the event bus, hopping to the main thread
  when needed.

## Configuration

`config.yml` (Bukkit) → `BukkitConfigView` (`ConfigView` adapter) →
`SuldConfigFactory` → immutable `SuldConfig` tree. All mapping and defaulting
lives in `SuldConfigFactory` and is unit-tested with `MapConfigView` (no Bukkit).
Reload replaces the whole `SuldConfig` so readers always see a consistent
snapshot.

## Persistence

`ProfileRepository` is the async contract. Two implementations:

- `InMemoryProfileRepository` (`suld-api`) — dev/test, volatile.
- `JdbcProfileRepository` (`suld-plugin`) — HikariCP pooled, dialect-aware
  (`SqlDialect` handles MySQL vs PostgreSQL upsert/UUID differences),
  schema managed by `SchemaMigrator` (forward-only, tracked in
  `suld_schema_version`).

See [DATABASE.md](DATABASE.md).

## Testing strategy

- **Domain (`suld-api`)**: fast JUnit 5 tests for the progression engine, config
  factory, profile invariants, service event/analytics emission, repository
  round-trips. No server required.
- **Plugin (`suld-plugin`)**: JUnit tests for the dialect/URL/migration logic
  that needs no Bukkit; plus a build-time **linkage check** that loads every
  plugin class against the real Paper API jar to catch API drift beyond compile.
- **Integration**: a Paper test server (`runServer` / `scripts/run-test-server.sh`)
  for manual and (later) automated smoke tests.

## Hard rules (enforced in review)

- No ready-made gameplay plugin as a dependency or runtime requirement.
- `suld-api` must not import Bukkit/Paper/Spigot.
- No new Maven repository without the report required by [DEPENDENCIES.md](DEPENDENCIES.md).
- No synchronous database calls on the main thread.
- No gambling/pay-to-win mechanics (see [GAME_DESIGN.md](GAME_DESIGN.md)).
