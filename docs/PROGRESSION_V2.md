# Progression v2: what is in the game

The proposed balance (`docs/PROGRESSION_BALANCE_SPEC.md` and the specs it links) is now the live game, except for the
parts listed under **Deferred**. This page is the status of each part and where its code lives. The specs keep the
full design; the rules are in `suld-api/src/main/java/mn/suld/api/balance/`, and the simulator measures the live game
with the same code (`LiveRules` extends `ProposedRules`).

Status of everything below: **MANUAL_QA_REQUIRED** (bots and simulation, not yet a full human playtest).

## Measured (full simulate, `./gradlew :suld-plugin:simulate`)

* Live hardcore p50: **147 active hours to level 60** (full run of 2026-10-13, after the death reform: no timed lock,
  levels 1–10 free, the beam's 70 % refund; it was 147.4 h with the 30-minute lock cap of 2026-10-12, and 207 h with
  the 24-hour lock). The owner accepted ~146 h as the target; L1 checks 130–170 h (PASS). The proposed design takes
  178 h (C1, a reference model, FAILs its 130–170 h band). With Act II (42 chapters) the live curve base is 242
  (`Balance.CURVE_BASE`, curve version 3); the design's 315 (`Balance.DESIGN_BASE`) also counts band raids and a world
  boss.
* Classes, live: Баатар 148 h, Мэргэн 132 h, Бөө 159 h, Дархан 149 h, Хүлэгчин 136 h (median 148 h: Мэргэн −11 %,
  Бөө +7 %).
* L1–L10 all pass (L9 at 97 %). Of the reference model's checks, C1 and **C9** fail: with the death reform modelled the
  same way (no lock to slow the reference player), 88 % (was 90 %) of its hardcore player's first 30 days bring a
  milestone. C2–C8 and C10–C13 pass.
* The simulator's per-class hours move a few percent with any small change, because the simulated player's choices
  flip. With spells counted as a flat share of hits, a share of 0.2 gave Баатар 201 h, 0.3 gave 214 h, 0.45 gave 198 h
  and 1.0 gave 162 h. M2's spell model (gear and tree spell damage, Бөө's trait, ranks, the fifth spell) raised DPS at
  60 by about 25 % (M2 note; C1 then overshot by 1.7 %, inside that noise).
* `SimulationTest.liveAndProposedReachSixtyAtTheSamePace` keeps the parity in the test suite (passes).

## In the game

| Part | Where |
|---|---|
| EXP curve 242·L^2.2; old bars rescaled once, level kept (config v5 from 100·L^1.75, v6 from 190·L^2.2) | `Balance`, `CurveMigration`, `ProfileUpgrade`, `SuldPlugin.migrateConfig` |
| The story: Act I (1–18) and Act II «Хөх Сүлдний Зам» (19–42): outer lands, historic sites, ladder dungeons 5–9 | `content/quests.json`, `QuestMenu` (quest book with three tabs, two painted maps), `HistoricSiteService` |
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
| M2: five spells per class (the fifth at 50 with Shift+F), spell ranks II/III (40/55), a second keystone per class, Бөө spell trait, a mythic-capable bow (Тэнгэрийн Нум) | `Spell`, `SkillService`, `KeystoneKind`, `skills/*.json` (f5, k2), `CombatRules.spellPassive`, `items/weapons.json`, `docs/SKILL_TREE_DESIGN.md` |
| Outer lands 27–60 (four regions, 16 areas), mobs fitted to the local level | `content/world.json`, `WorldContent.localLevel`, `RegionSpawner` |
| Daily tasks, daily login EXP, landmark and discovery rewards, world-event floor | `Rewards`, `DailyTasks`, `OvooService`, `WorldEventService` |
| Тэнгэрийн Зэрэг I–III: оноо, gates, rite, +1 point, +1 % attack, T6 at III, longest death lock | `Ascension`, `AscensionMenu`, `docs/ASCENSION_SPEC.md` |
| Death lock and wound use the Ascension rank | `DeathLock`, `DeathService` |
| Endgame record (rank, оноо, rested, curve version, palace clears), V15 | `Endgame`, `suld_profiles.endgame` |
| Content as data (mobs, regions, dungeons, story, events) | `docs/CONTENT_DATA.md` |

## Deferred (designed, simulated, not in the game yet)

| Part | Why it waits | Stand-in today |
|---|---|---|
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
