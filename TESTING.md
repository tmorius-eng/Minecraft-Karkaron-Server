# SÜLD Testing

## Layers
1. **Unit tests (`suld-api`, `suld-plugin`)** — pure logic, run on every build
   via `./gradlew build`. No server required.
2. **Asset/pack validation** — `python3 tools/validation/run_all.py`
   (registry, resource pack, size/budget).
3. **Live Paper smoke test** — a real Paper 1.21.11 server started headless in
   the cloud (`scripts/run-test-server.sh`), verifying startup, plugin enable,
   commands, listeners, DB migration, and config load.
4. **Manual client test** — the one step that needs a real Minecraft client
   connecting (join flow, GUI clicks, resource-pack acceptance). Not automatable
   in a headless cloud box; steps listed below for a human tester.
5. **Progression simulation (`suld-plugin/src/sim`)** — `./gradlew :suld-plugin:simTest`
   (part of `check`): golden tests that the live model mirrors the content classes and the
   curve, determinism, loot bands, the 7-day rule, AFK, lock curves.
   `./gradlew :suld-plugin:simulate` re-runs the full matrix and rewrites
   `docs/PROGRESSION_SIMULATION.md`, the spec tables and `audit/progression-balance.json`.

## Critical invariants (unit-tested)
- Progression: level/EXP curve, overflow at cap, multi-level rollover.
- Combat: armor mitigation curve, crit boundary (`roll < chance`), determinism.
- Loot: guaranteed/zero drop chances, item-level bounds, unique UUID per drop.
- Quest: progress advances only on matching mob, completes at threshold, stable.
- Persistence: class + level + EXP + currency + quest survive save→load.
- Config: defaults, overrides, unknown-enum fallback.
- World-unique items: DB PK + UNIQUE constraints (schema); service enforcement
  lands with the relic slice.

## Vertical Slice 1 — verification matrix

| Step | How verified | Status |
|---|---|---|
| Build green | `./gradlew build` (24+ tests) | automated ✅ |
| Plugin compiles+links vs Paper API | build + linkage check | automated ✅ |
| Server starts, plugin enables | live headless Paper server, console log | automated (see latest run) |
| Commands registered (`/suld`, `/revive`, `/suldpack`) | console / plugin load | automated |
| DB layer | migration unit tests + in-memory round-trip; live uses MEMORY by default | automated |
| Combat/EXP/loot/quest/level math | unit tests | automated ✅ |
| Join → resource pack sent | `ResourcePackService` on join | **manual client** |
| Class selection GUI → select | `ClassSelectionGui` click flow | **manual client** |
| First mob kill → loot + EXP + quest | in-world combat | **manual client** (logic unit-tested) |
| Level-up presentation | title/sound on `LevelUpEvent` | **manual client** (logic unit-tested) |
| Disconnect → reconnect → state restored | `ProfileService` + JDBC round-trip | unit-tested; **manual client** confirms end-to-end |

## Manual client test script (for a human with a Minecraft 1.21.11 client)
Point a client at the running server (offline mode) and:
1. Join → class-selection GUI opens; HUD not yet shown.
2. Click a class → starter weapon granted, HUD appears, first quest assigned.
3. `/suld spawnmob` → kill 3 Говийн Чоно → loot drops, EXP gained, quest completes.
4. Gain enough EXP → level-up title + sound.
5. Disconnect, reconnect → class, level, EXP, currency, quest, inventory restored.
6. (With a hosted pack + `resource-pack.enabled: true`) → pack prompt on join.

## Vertical Slice 2 — verification matrix

| Step | How verified | Status |
|---|---|---|
| Party rules (invite/accept/expiry/leader/kick/promote/capacity/busy) | `PartyTest`, `PartyRegistryTest` | automated ✅ |
| Dungeon state machine (waves→boss→complete/fail, illegal transitions) | `DungeonRunTest` | automated ✅ |
| Boss phase selection / config validation | `DungeonRunTest` | automated ✅ |
| Build + 72 tests | `./gradlew build` | automated ✅ |
| Plugin loads, listeners + `/party` `/dungeon` register | live Paper 1.21.11 headless | automated ✅ |
| `/party invite|accept` between two real players | in-world | **manual client (2 players)** |
| `/dungeon enter khasar_den` → waves → boss phases → rewards | in-world | **manual client** |
| Party wipe / leave mid-run / abort cleanup | in-world | **manual client** |

### Manual Slice 2 script (needs a lvl 2+ character; give EXP with a few wolf kills first)
1. A: `/party invite B`; B: `/party accept` → both see "багт нэгдлээ".
2. A: `/dungeon enter khasar_den` → both teleported, boss bar "бэлдэж байна", HUD shows `Агуй:` line.
3. After ~3s wave 1 (3 wolves) spawns; kill all → "Давалгаа цэвэрлэгдлээ" → wave 2 (4 wolves).
4. Boss Хасар spawns; at 60% and 30% HP expect roar + phase message; do nothing for 180s to see enrage.
5. Kill boss → banner, +EXP/+coins, reward items in inventory; mobs gone, party returns to FORMING.
6. Repeat and let everyone die / `/dungeon abort` → "Агуй бүтэлгүйтлээ", all mobs removed.

## Vertical Slice 4 (relics) — verification matrix
Details: [docs/RELICS.md](docs/RELICS.md) §5.

| Invariant / flow | How verified | Status |
|---|---|---|
| Validator: forged / stale / extra / foreign / container copies removed; bearer re-issued | `RelicValidatorTest` | automated ✅ |
| Claim rules, death outcomes, hints | `RelicRulesAndHintsTest` | automated ✅ |
| One winner under concurrent claims | `InMemoryRelicRepositoryTest` (64 threads) + `JdbcRelicRepositoryIT` (24 real connections) | automated ✅ |
| DB rejects 2 relics per bearer, duplicate item UUID | `JdbcRelicRepositoryIT` (opt-in PostgreSQL) | automated ✅ |
| Pack: format 75, item definitions resolve, registry CMD mappings | `tools/validation/validate_resourcepack.py` | automated ✅ |
| Ritual claim, forged/duplicate/container copies, death return, admin grant, PvP seize | live Paper + PostgreSQL + bots | live ✅ |
| Relic visuals on a real client; 72h offline expiry | needs a client / time | **manual** |

## Authentication & identity — verification matrix
Full details and live transcript summary: [docs/AUTHENTICATION.md](docs/AUTHENTICATION.md) §7.

| Invariant | How verified | Status |
|---|---|---|
| UUID-only identity, rename = same character, same name ≠ same player | `PlayerIdentityTest`, `DefaultProfileServiceTest` | automated ✅ |
| Fail closed when unverified; v3 UUID rejected on verified servers | `AuthPolicyTest` + live test A | automated ✅ + live ✅ |
| Quit → instant rejoin never reads stale data | `DefaultProfileServiceTest` (mutation-tested) | automated ✅ |
| Storage failure never creates a blank profile | `DefaultProfileServiceTest` | automated ✅ |
| Duplicate / concurrent logins | `SessionRegistryTest`, `DefaultProfileServiceTest` + live test 4 | automated ✅ + live ✅ |
| First join (no /login), reconnect, rename, 15x reconnect | live Paper + PostgreSQL + mineflayer bots | live ✅ |
| Genuine Microsoft-authenticated join | needs a real account/client | **manual, on first real deploy** |

## Vertical Slice 3 — verification matrix

| Step | How verified | Status |
|---|---|---|
| Clan rules (names/tags, invites, ranks, leader transfer, capacity, contribution) | `ClanRegistryTest`, `ClanProgressionTest` | automated ✅ |
| World event (progress, expiry, podium rewards, threshold) | `WorldEventRunTest` | automated ✅ |
| V3 migration + JDBC clans on real PostgreSQL (moves, cascade, DB rejects dup tag / double membership) | `JdbcClanRepositoryIT` (opt-in: `SULD_TEST_PG_URL`) | automated ✅ (run locally) |
| plugin.yml well-formed + all commands declared | `PluginDescriptorTest` | automated ✅ |
| Migrations V1–V3 on boot, clan loaded from DB on restart, event start/stop | live Paper + PostgreSQL | automated ✅ |
| `/clan create` → invite/accept → chat tags → `/cc` | in-world | **manual client (2 players)** |
| `/suldevent start` → raiders spawn & attack → 30 kills → podium rewards + clan EXP | in-world | **manual client** |

Run the DB integration test: `SULD_TEST_PG_URL=jdbc:postgresql://127.0.0.1:5432/<throwaway> SULD_TEST_PG_USER=… SULD_TEST_PG_PASS=… ./gradlew :suld-plugin:test`
(it **drops all suld_ tables** in that database first).

## Latest live run (2026-10-06, automated)
- Paper 1.21.11 (build 132) started headless; SULD remapped → loaded → ENABLED → disabled cleanly.
- `libraries:` JDBC drivers (mysql-connector-j, postgresql, protobuf, checker-qual) downloaded + loaded at runtime.
- PostgreSQL 16 run: HikariCP connected, `migrations applied: 2` (V1+V2), 6 tables created, suld_profiles has
  currency+quest columns, clean pool shutdown. Bad-port config → graceful self-disable with a clear error.
- Not run (needs a MC client): the in-world join/GUI/combat/reconnect flow — unit-tested instead.
