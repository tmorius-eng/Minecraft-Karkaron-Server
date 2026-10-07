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

## 3b. Combat verification suite (in the plugin) — `/skillsadmin qa <player> [stats|mods|procs|wiring|keystones|ultimates|cooldowns|all] [only=<class.node>]`

`suld-plugin/src/main/java/mn/suld/plugin/skill/qa/` (`SkillQa`, `QaKit`). The player is moved to a stone arena 200 blocks up at
(3000, 3000) with 34 inert SÜLD wolves (AI off, max health 1024, chunk tickets, monster spawning off while it runs) and restored
afterwards (location, class, level, tree, inventory hand, game mode). For **every effect of every node** it measures a game value
with the node unlearned and learned (max rank) and compares the change with the data file:

* **stat nodes** — attribute values (max health, movement speed, armor, knockback resistance), break speed, final damage of a hit
  (attack %), share of 4000 hits that crit, crit/normal damage ratio, damage per spell hit, health lost from a 10-damage hit,
  share of 3000 hits dodged, damage returned, health gained per damage dealt, heal size, resource capacity/regeneration/cost,
  cooldown left after a cast, EXP from real kills, items from 300 real boss kills.
* **spell modifiers** — the real spell is cast through the normal cast path (`SkillService.castDirect`): damage per hit, resource
  spent, farthest enemy reached, fire/slowness/weakness ticks on enemies, marked-enemy damage ratio, health gained, resource refunded,
  knock-up/pull velocity, absorption, speed, executions over 150 casts (echo). Olon Sum is measured with real arrows.
* **passive spells** — chance over 300 triggers (4σ), per-node cooldown (second trigger refused, fires again after the cooldown),
  and the effect size (damage to the target/neighbour/area, heal, shield, resource, potion level).
* **wiring** — each trigger fires from the real game event (melee hit, crit, kill, damage taken, low health, sneak, cast, spell hit).
* **keystones** — every upside and downside measured (resistance at low health, healing halved, max health, regen, durability
  over 400 damage events, spell damage/fire, walking and horse speed, normal vs volley arrows).
* **ultimates** — buff levels/durations, damage and reach of every damaging ultimate, heal/cleanse, cooldown 45 s.
* **cooldowns** — every active spell: cooldown left right after the cast equals the spell's cooldown, recast refused, ready again after it.

Every row is written to `plugins/SULD/qa/skill-qa-<time>.json`; `tools/validation/skill_qa_report.py` turns it into
`docs/SKILL_TREE_COMBAT_VERIFICATION.md`. A row passes only when the measured change matches — a code path that merely runs is not a pass.

## 3c. Skill map verification over the protocol (bot, outside the repo)

A mineflayer bot opens `/skills` on the dev server for each class with the `fresh` and `states`/`states0` fixtures and checks
every visible slot against an **independent JavaScript re-implementation of the rules written from the data files** (not from the
Java code): node item, material, name, glint, stack count, state text; connector model and colour for every pair of neighbours;
tooltip contents (cost, level, requirements, red links, rank, click hint); pan (incl. edges), zoom round trip, overview geometry;
unlock / rank up / maxed / rank down / refund / excluded / needs-points clicks with the server's answer; points panel; build save and
listing; the class-specific tree; resource-pack offer, download hash and the presence of every map asset in the downloaded zip.
`SkillPackAssetsTest` (unit) checks that the pack generator produces every model the map can ask for.

## 4. Manual Minecraft client QA (a person with the game)

Cannot be done by bots: how the map looks (background, connector lines, node icons, tooltip layout), whether the
pack-drawn lines join neatly at the player's GUI scale, click feel, and the visual effects of ultimates. Step-by-step guide: [`SKILL_TREE_CLIENT_QA.md`](SKILL_TREE_CLIENT_QA.md).
