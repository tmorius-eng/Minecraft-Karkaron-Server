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

## Latest live run
See PROJECT_STATE.md → "Live smoke test" for the most recent result and console
excerpt.
