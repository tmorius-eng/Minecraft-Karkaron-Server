# SÜLD skill tree — test plan

Four levels, kept apart on purpose. What actually ran is recorded in [`SKILL_TREE_QA.md`](SKILL_TREE_QA.md).

## 1. Unit tests (no server) — `suld-api`

| Area | Test class | Covers |
|---|---|---|
| Graph, content | `SkillTreeTest` | bundled data loads with no issues; 5 distinct trees (36 nodes each); every spell reshaped; 15 ultimates and 5 keystones each taught once; unique names; path/points/level rules; ranks stack and cap; exclusive links; one ultimate only; `requires` convergence; refund keeps connectivity; refund blocked while a dependant stands; bridges; hidden nodes; id codec; `trimmed` after level loss and for broken paths; branch reset; build aggregation (stats × rank, spell mods, procs, keystones, ultimate); generated Mongolian tooltips; writer round trip |
| Economy, engine | `SkillEngineTest` | per-player independent state; unlock spends and bumps the profile version; failed unlock changes nothing; chapters/discovery/grants add points; refund; admin force unlock still respects the graph; reset keeps level/coins/granted; respec cooldown; bounded history; branch reset; builds save/load/delete/slots/validation (cooldown, missing nodes, unaffordable); `normalise`; exploit attempts (forged ranks, negative grants, rivals); state codec, legacy document, unreadable/newer data rejected |
| Data validation | `SkillLoaderTest` | exact file + field reported for: unknown enum value, missing field, bad numbers, foreign-class spell, unsupported modifier, duplicate id, edge to unknown node, far edge, unreachable island, bad `requires`, keystone flag mismatch, broken JSON, missing file, universal problems blocking all classes, ranks on a proc node |

## 2. Integration tests (real PostgreSQL, opt-in `SULD_TEST_PG_URL`) — `suld-plugin`

* `JdbcSkillStateIT`: migration V10 on a fresh schema; a profile with no skill data loads the empty state (what every
  pre-V10 row looks like); save → load round trip of ranks, builds, granted points and respec history; a row written by a
  newer server (or corrupted) makes the load fail instead of being wiped.
* Existing ITs (profile top, style, clan, relic) rerun against the V10 schema; `SqlDialectTest` and `SchemaMigratorTest`
  cover the upsert and the migration list; `PluginDescriptorTest` now also rejects unquoted `: ` in plugin.yml values.

## 3. Live Paper tests (real server, mineflayer bots) — scripts kept outside the repo

1. JOIN → CLASS → LEVEL → `/skills` → map opens → select/unlock node → effect (max health attribute, connector turns gold) → save → QUIT → REJOIN → state and effect restored.
2. Level 1 shows 0 points, level 60 shows 44; unlock/rank-up/refund by click; exclusive refusal message; `/skill`, `/skills info|search|build …`.
3. Map buttons: zoom, filter, pan; search highlights.
4. Orb of Oblivion: given by admin, right click → confirmation → refund, orb consumed.
5. Admin: inspect, grantpoints, unlock, reset, orb, fire, reload, validate.
6. Ultimates (F) for all five classes: effect applied, second press shows the cooldown; HUD shows `F ✦ Ns` and unspent points.
7. Spell combos with spell modifiers learned; no exceptions in the server log.
8. Server restart with the PostgreSQL profile store: state survives.

## 4. Manual Minecraft client QA (a person with the game)

Cannot be done by bots: how the map looks (background, connector lines, node icons, tooltip layout), whether the
pack-drawn lines join neatly at the player's GUI scale, click feel, and the visual effects of ultimates. Checklist in `SKILL_TREE_QA.md`.
