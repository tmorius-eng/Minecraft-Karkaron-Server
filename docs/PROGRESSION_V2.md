# Progression v2: what is in the game

The proposed balance (`docs/PROGRESSION_BALANCE_SPEC.md` and the specs it links) is now the live game, except for the
parts listed under **Deferred**. This page is the status of each part and where its code lives. The specs keep the
full design; the rules are in `suld-api/src/main/java/mn/suld/api/balance/`, and the simulator measures the live game
with the same code (`LiveRules` extends `ProposedRules`).

Status of everything below: **MANUAL_QA_REQUIRED** (bots and simulation, not yet a full human playtest).

## Measured (quick simulate, `./gradlew :suld-plugin:simulate -Pquick`)

* Live hardcore p50: **207 active hours to level 60**, against 196 h for the proposed design (5 % apart; L1 needs
  180–220 h and ≤ 10 %). The live curve base is 190 (`Balance.CURVE_BASE`) because the live game has 18 story chapters
  where the design has 42; Act II will move it back towards 315 (`Balance.DESIGN_BASE`).
* Every proposed criterion C1–C13 passes, and every live criterion L1–L10 (`Sim.liveChecks`) passes: no dead levels,
  loot clamped to the band, coin sinks ≥ 60 %, a milestone on ≥ 90 % of the first 30 days, at most 40 % of the ladder
  at level 20, Ascension I reachable.
* `SimulationTest.liveAndProposedReachSixtyAtTheSamePace` keeps the parity in the test suite.

## In the game

| Part | Where |
|---|---|
| EXP curve 190·L^2.2, fair migration of old bars (config v5, `progression.legacy-curve`) | `Balance`, `CurveMigration`, `ProfileUpgrade`, `SuldPlugin.migrateConfig` |
| Level-gap EXP, party share (alive, in range), capped boosts, catch-up, farm factor | `ExpRules`, `CombatListener` kill path, `ProgressionBoosts` |
| Rested EXP pool (offline hours, capped) | `RestedPool`, `Endgame.restedExp` |
| Mob numbers from level and tier; host swing speed; level-gap damage both ways | `MobScaling`, `MobDefinition.designed`, `CombatRules` |
| Armour mitigation by attacker level; class base health with growth | `CombatRules.mitigation`, `CombatRules.classHealth`, `SkillTreeService.applyBaseHealth` |
| Mobs above the 1024 max-health ceiling (scaled hits, design numbers shown) | `MobService.hpScale` |
| Dungeon ladder gates: previous rung, story chapter, gear power | `DungeonLadder`, `DungeonService.v2Gate` |
| Boss health sized for the dungeon's party | `MobScaling.bossPartyScale` |
| Completion rewards with repeat and carry factors; loot level clamped to the band | `Rewards`, `DungeonRules`, `DungeonService.completionReward` |
| Gear power | `GearPower`, `DungeonService.gearPower` |
| Vendor prices, reforge cost | `Economy`, `ItemEconomy` |
| Skill points: levels, chapters, regions, Ascension | `SkillPoints`, `SkillEngine.total` |
| Outer lands 27–60 (four regions, 16 areas), mobs fitted to the local level | `content/world.json`, `WorldContent.localLevel`, `RegionSpawner` |
| Daily tasks, daily login EXP, landmark and discovery rewards, world-event floor | `Rewards`, `DailyTasks`, `OvooService`, `WorldEventService` |
| Тэнгэрийн Зэрэг I–III: оноо, gates, rite, +1 point, +1 % attack, T6 at III, longest death lock | `Ascension`, `AscensionMenu`, `docs/ASCENSION_SPEC.md` |
| Death lock and wound use the Ascension rank | `DeathLock`, `DeathService` |
| Endgame record (rank, оноо, rested, curve version, palace clears), V15 | `Endgame`, `suld_profiles.endgame` |
| Content as data (mobs, regions, dungeons, story, events) | `docs/CONTENT_DATA.md` |

## Deferred (designed, simulated, not in the game yet)

| Part | Why it waits | Stand-in today |
|---|---|---|
| Act II story (chapters 19–42) | content to write and build | 18 chapters; curve base 190 instead of 315 |
| Heroic dungeon mode | needs the re-statted runs and their rewards | Тэнгэрийн Ордон clears count for the Ascension gates |
| World boss | needs the world event, the arena and the boss | Ордон clears (Ascension III) |
| Mastery tracks other than armour (combat, dungeon, boss, explore, craft, collect) | new trackers and storage | armour mastery stands in for the Ascension gates |
| Ascension IV–X | wait for the modes and tracks above | ranks I–III |
| Mythic tiers, sigils, tempering | new systems and sinks | coin sinks: repair, reforge, rite, shop |
| Boss enrage wipe | the enrage multiplier is in; the wipe at the hard timer is not | `BossService.ENRAGE_MULTIPLIER` |
| Loot fatigue by recent clears instead of a 2-hour window | the reward side already uses recent clears | `DungeonService.FATIGUE_WINDOW_MS` for gear drops |

## Checks to run after a change

1. `./gradlew :suld-api:test :suld-plugin:test :suld-plugin:simTest`
2. `./gradlew :suld-plugin:simulate -Pquick` (C1–C13 and L1–L10 must pass); the full `simulate` before a release.
3. If content changes the pace (Act II, new regions): `simTune` against the live rules and set the base in
   `config.yml` and `Balance`.

## Human QA still needed

* Lethality at +5 levels over a mob; boss health at the recommended party size.
* The curve migration on a copy of the production database.
* The /ascend window and rite, the sky priest at level 60, +1 skill point, T6 open at rank III.
* The dungeon ladder gates (chapter and gear power messages) on the /dungeon window.
