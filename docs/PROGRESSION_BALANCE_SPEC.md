# SÜLD progression balance spec (proposed)

**Status: SPEC + SIMULATION. Nothing here is implemented.** The owner approves these numbers before any of them enter
the game (Stage B gate).

* The numbers come from `suld-plugin/src/sim/java/mn/suld/sim/ProposedRules.java`.
* Tables between `spec:` markers are written by `./gradlew :suld-plugin:specTables`.
* The measured effects are in `docs/PROGRESSION_SIMULATION.md` (`./gradlew :suld-plugin:simulate`) and
  `audit/progression-balance.json`.
* The problems this answers are in `docs/PROGRESSION_EXPLOIT_AUDIT.md`.

Owner decisions built in:

* ~200 h of efficient play to level 60.
* Level cap 60, then Тэнгэрийн Зэрэг I–X, then Mastery.
* No artificial time gating (directive §2).
* 10 h/day × 7 days must not reach max level + max gear + endgame.
* The death lock scales by level, chosen by simulation.
* Paid revive is deferred (design only).

## 1. Current architecture and formulas

See the audit §1 for the full table. In short:

* The curve is `100·L^1.75` (2,757,813 EXP to 60).
* Mob EXP ignores the level gap.
* Content stops at level 26, with 4 regions, 4 dungeons and an 18-chapter story.
* Dungeons are level-gated only, have no lockout, and roll loot at the player's level.
* Dungeon bosses roll legendary or better.
* Player health ignores class and level.
* There is no Mastery, Ascension or achievement system.

Simulated, the live game reaches **level 60 in 17 h of hardcore play (day 2)**. A casual 2 h/day player gets there
on day 12. 38 of 60 levels have no content of their own.

## 2. Problems (top of the exploit audit)

1. **DG-1 / GR-1:** dungeon loot level = player level. Low dungeons farmed at 60 give level-60 gear.
2. **DG-2:** no lockout, no diminishing returns, no prerequisite but level.
3. **DG-3:** dungeon bosses roll the BOSS band (legendary 70 / ancient 25 / mythic 5).
4. **DG-9:** level 20 opens 75 % of the dungeon ladder.
5. **XP-1 / XP-2:** no level-gap EXP rule; region bands unenforced; normal mobs deal vanilla damage.
6. **PW-1:** class base health unused; no health growth.
7. **GR-8:** item stats grow faster than levels, so gear is about 3× base attack at 60.
8. **PW-4 / PW-5:** attack cooldown ignored; armour saturates at the vanilla 80 % cap.
9. **EC-1 / EC-5:** no recurring sinks; vendor prices of high rarities (×120) make gear sales the main faucet.
10. Nothing above level 26; nothing after 60.

## 3. The model: layered progression

```
LEVEL ─► CLASS ─► SKILL TREE ─► GEAR (class gear + loot) ─► STORY ─► EXPLORATION ─► DUNGEONS ─► BOSSES
   └────────────── MASTERY (from level 1, 8 tracks) ──────────────┘                     │
                                                         level 60 ─► ASCENSION I–X ─► ENDGAME (heroic, mythic 1–10, raid, world boss)
```

How the layers reinforce each other:

* **Story** gates dungeons.
* **Dungeons** gate armour tiers.
* **Armour tiers** gate the heroic and mythic content, through gear power.
* **Mastery and exploration** gate Ascension ranks.
* **Level** gates everything but no longer *decides* everything: gear power, story and previous clears are checked
  separately.

## 4. Experience

### 4.1 The curve

**EXP to go from L to L+1 = round(315 · L^2.2)**, cap 60. This is plain config for the existing
`PolynomialLevelCurve` (`progression.curve.base: 315`, `exponent: 2.2`).

The base was found by bisection, so that the simulated efficient (hardcore, optimal) player reaches 60 in ≈ 200 active
hours: **205.9 h at p50** in the final run.

<!-- spec:begin curve -->
| Level | EXP to next (proposed) | Cumulative (proposed) | EXP to next (live) | Cumulative (live) |
|---|---|---|---|---|
| 1 | 315 | 0 | 190 | 0 |
| 5 | 10865 | 11944 | 6554 | 7204 |
| 10 | 49924 | 131967 | 30113 | 79599 |
| 15 | 121818 | 511603 | 73477 | 308586 |
| 20 | 229391 | 1321103 | 138363 | 796855 |
| 25 | 374782 | 2743342 | 226059 | 1654714 |
| 30 | 559728 | 4971011 | 337614 | 2998388 |
| 35 | 785706 | 8204926 | 473918 | 4949005 |
| 40 | 1054005 | 12652888 | 635749 | 7631902 |
| 45 | 1365772 | 18528844 | 823799 | 11176128 |
| 50 | 1722045 | 26052250 | 1038694 | 15714056 |
| 55 | 2123775 | 35447573 | 1281007 | 21381077 |
| 59 | 2478477 | 44465393 | 1494955 | 26820397 |
| 60 | 0 | 46943870 | 0 | 28315352 |
| total to 60 | | 46943870 | | 28315352 |
<!-- spec:end curve -->

Existing players need a migration: run `grant(progression, 0)` on load to re-normalise the bar. Under the new curve
early levels cost more, so no one gains levels from the migration.

### 4.2 Sources (simulated shares of the generalist's EXP to 60)

| Source | Rule | Share |
|---|---|---|
| Mob kills | base EXP = round(30 + 10·L + 0.05·L²) × tier (normal 1, elite 1.8, champion 3, boss 10); × the gap factor | 23–32 % |
| Dungeons | completion 4 % of the recommended level's EXP + party share of kills | 13 % (casual) – 29 % (hardcore) |
| Story | each chapter 35 % of its level's EXP. Act I keeps the 18 chapters; Act II adds 24 (investigation, escort, puzzle, dialogue, boss, discovery) | ~25 % |
| World events | band raid every 45 min: 2 % of a level + ~10 min of fighting; world boss every 3 h at 55+ | 5–12 % |
| Daily tasks + login | 3 tasks × 4 % of a level per day; login 2 %·day/7 of a level | 8 % (hardcore) – 28 % (casual: the catch-up) |
| Exploration | discovery 5 % of the region's first level; 12 landmarks / hidden places per region at 2 % each | ~3 % |
| Rested ("Амралт") | 1.5 % of a level per offline hour, pool ≤ 1.5 levels, doubles kill EXP until used | catch-up |
| Catch-up | +50 % while more than 10 levels below the server's median active level | late joiners |

* **Boost cap:** clan + relic + items together at most +50 % (XP-5).
* **EXP at the cap** becomes **Тэнгэрийн оноо**, the Ascension currency, instead of being discarded (XP-6).
* **Gap rules and level suppression:** `docs/DIFFICULTY_CURVE.md`. Kill EXP falls to 10 % at 10 levels below and
  gives no gear rolls. Fighting more than ~5 levels up is lethal.

### 4.3 Level breakpoints

Each band unlocks something (directive §4):

| Levels | Band | Hours (hardcore p50) | Unlocks |
|---|---|---|---|
| 1–10 | Хэрлэн · Говь — survival, first class identity | 0 → 3.7 h | first build points, dungeon I, armour T1 → (T2 at 12) |
| 11–20 | Говь · Хангай — core build | → 20.4 h | dungeons II–III, armour T2, skill tree tiers 14/20, keystones |
| 21–30 | Алтай — specialisation, elites, champions | → 49.7 h | dungeon IV (Мөсөн Оргил), armour T3 at 24, ultimates (tree 24–36) |
| 31–40 | Хөвсгөл · Зүүнгар — serious dungeons | → 83.2 h | dungeons V–VI, armour T4 at 36, Act II |
| 41–50 | Зүүнгар · Бурхан Халдун — high-tier bosses | → 119.2 h | dungeons VII–VIII, armour T5 at 48, new tree nodes 42/48 (spec, §6) |
| 51–59 | Отгонтэнгэр — endgame preparation | → ~195 h | dungeon IX, world boss at 55, tree node 54 |
| 60 | cap | 205.9 h | raid, heroics, Ascension I, mythic tiers, T6 path |

### 4.x Farming fatigue (IMPLEMENTED, Stage C3b)

The rule stops "find one spawn, stand there for 10 hours, reach max level". It reduces the reward of **normal
open-world kills only**. Dungeon waves and bosses, world-event mobs, BOSS-tier mobs, quests, dungeon completions and
discoveries are never reduced. Exploring and moving between areas never meet it.

* **Memory:** for each player, the last kills with their mob type and position (`ActivityTracker`, up to 600
  kills). A kill is counted for **30 active minutes**. The window advances only with validated active minutes, so
  logging off or idling does not wash the count out. Doing something else, somewhere else, does.
* **Count:** for a new kill of mob type *m*,
  n = (kills of *m* within 48 blocks) + 0.5 × (kills of other types within 48 blocks); a radius, so there is no grid border to stand on.
* **Factor:** f(n) = 1 for n ≤ 40, otherwise **f(n) = max(0.25, 1 / (1 + (n − 40) / 60))**.

| n | 40 | 70 | 100 | 160 | 220+ |
|---|---|---|---|---|---|
| f | 1.00 | 0.67 | 0.50 | 0.33 | 0.25 (floor; some reward always stays) |

* **What f multiplies:**
  * the kill's player EXP (`CombatListener`, after boosts and the skill-tree EXP bonus);
  * its armour XP;
  * its armour mastery XP.
  * Active-minute armour XP uses the factor of the area the player is in.
* **Player feedback:** the EXP message says the area is over-hunted, and `/classgear` shows the area factor.
* **Simulator:** the AFK/one-spot exploit uses the same curve. C6 and C12 still PASS: AFK EXP/hour is 12,720 against
  120,935 for active play.

## 5. Player tables (directive §5, §25, §26, §38)

The full tables are generated in `docs/PROGRESSION_SIMULATION.md`. Headline numbers (p50, mid calibration, all five
classes):

| Player | Day 7 | Day 14 | Day 30 | Day 60 | Day 90 | Day 180 |
|---|---|---|---|---|---|---|
| Casual 2 h/day | L17 · T2 · 2 dungeons | L21 | L31 · T3 | L46 · T4 | L60 · T5 | L60 · T5 · Asc 2 |
| Active 5 h/day | L26 · T2 · 4 dungeons | L37 · T3 | L57 · T5 | L60 · Asc 3 | L60 · Asc 4 | Asc 7 · T6 |
| Hardcore 10 h/day | **L35** · T3 · 5 dungeons | L51 · T4 | L60 · Asc 2 | Asc 4 | Asc 8 | Asc 10 · T6 |
| *Live, hardcore* | *L60, everything done* | | | | | |

* **The 7-day rule (C2): PASS.** The hardcore day-7 p90 is level 38.7, or 42.0 at **high** calibration. Max gear is
  29 % and endgame 12 %.
* No scenario reaches 60 in 7 days: not any exploit, not even **4× the EXP rate** (sensitivity table).
* The hardcore player feels strong (level 35, epic gear, armour T3, 5 of 10 dungeons) and sees that half the ladder,
  Act II, 3 regions, the raid and all of Ascension are still ahead.

## 6. Skill points (directive §12)

**Proposed:** 1 per level from 2 to 60 (59), + 1 per 3 story chapters (max 14), + 1 per 2 regions discovered (4),
+ 1 per Ascension rank (10).

* That is **77 at 60 with everything done**, 87 at Ascension X, against ~95 to max a class tree plus universal, so
  builds remain choices.
* Live gives 49, with the discovery source dead.

| Feeling | When |
|---|---|
| "my build has started" | level 6–10: first notables of tier 6/10 |
| "I have a real specialisation" | level 20–24: tier 20 / first keystone |
| "I have a complete build" | level 36+ (tree top) and Ascension. The tree stays as it is (directive §12) |

Three new nodes per class at tree levels 42/48/54 give the late levels something to buy. They are spec'd as data only,
in the implementation phase.

Respec:

* Free until level 10.
* Then 25 ₮ per point × (1 + level/20).
* The Orb of Oblivion recipe moves from vanilla materials to band materials, closing SP-3.

## 7. Dungeons, gear, mastery, Ascension, difficulty, economy

Each has its own spec:

* `docs/DUNGEON_PROGRESSION_SPEC.md`: the 10-dungeon ladder, multi-gates, level-20 check (3 of 10 open), loot clamp,
  repeat fatigue, carry rules, personal loot.
* `docs/GEAR_PROGRESSION_SPEC.md`: item power with roll quality, rarity bands, acquisition targets, vendor rules.
* `docs/CLASS_GEAR_SYSTEM.md` and `docs/ARMOR_PROGRESSION.md`: soulbound class armour and weapon, armour level / tier /
  mastery.
* `docs/MASTERY_SPEC.md`: 8 tracks × 10 ranks with varied objectives.
* `docs/ASCENSION_SPEC.md`: ranks I–X, each a different kind of requirement, none time-based.
* `docs/DIFFICULTY_CURVE.md`: mob curves derived from the par player, gap rules, mechanics.
* `docs/ECONOMY_BALANCE_SPEC.md`: faucets and sinks (sinks take 84–96 % of income by day 90).
* `docs/DEATH_AND_RECOVERY.md`: the level-scaled death lock (geometric, 5 min → 24 h) and the wound.

### Rarity acquisition targets

These are hardcore active hours, p50, first usable drop. Live values are in brackets.

| Rarity | Target | Proposed |
|---|---|---|
| Legendary | 30–60 h | 24 h, from a legendary-only jewel (`item.khasar_zurkh`); without it ~40 h (live: 17 h) |
| Ancient | 100–150 h | 117.8 h |
| Mythic | ≈ level 60 | 242 h (live: reached by day 12 even for a casual) |

## 8. Exploits after the change (directive §33)

From `docs/PROGRESSION_SIMULATION.md` §Exploit scenarios, hardcore day 7:

| Scenario | Live | Proposed |
|---|---|---|
| Normal optimal play | L60 | L35 |
| E1 repeat the first dungeon | L46 | L19 (fatigue, loot clamp, gap factor) |
| E2 rush the richest zone | L60 | L6 (level suppression kills you) |
| E3 carried by a level-60 friend | L60 | L19 (carry factor ×0.5, ×0.1 above the band) |
| E4 one activity for 70 h | L60 | L33 (≤ normal play) |
| E5 AFK auto-clicker | L60 (live has no tracker) | L12.5, **armour level 1** (no activity signal) |

All twelve compliance checks pass (C1–C12, `docs/PROGRESSION_SIMULATION.md` §Compliance).

## 9. Class-by-class balance risks (directive §13, §21)

Hours to 60 range from 188 h (Хүлэгчин) to 227 h (Бөө), within ±10 % of the median. A party of four reaches 60 in 183 h, against 203 h solo.

| Class | Simulated | Risk |
|---|---|---|
| Баатар | 208 h; most health | low; the melee baseline |
| Мэргэн | 196 h; fewest deaths (kiting) | **content gap**: no bow definition rolls above legendary, so DPS at 60 is the lowest of the damage classes. Add an ancient/mythic-capable bow |
| Бөө | **227 h, slowest solo** | lowest solo DPS; its healing helps groups more than the solo model credits. Raise spell-damage scaling or give the class a solo self-buff; re-check after calibration |
| Дархан | 208 h | the heavy-hit identity (hit weight 1.55 at 1.0/s) depends on the attack-cooldown fix (PW-4) |
| Хүлэгчин | **188 h, fastest** | mobility (shorter seek and travel) makes it the best grinder; keep the mount bonus but watch it after calibration |

## 10. Systems requiring rebalance (implementation backlog, in order)

1. **Curve + gap rules + boost cap**: config, `CombatListener` kill EXP, `ProgressionBoosts`.
2. **Mob stats by formula**: `MobService` applies proposed health; damage for every mob (not only bosses);
   `CombatCalculator` mitigation with level-scaled K; class base health with growth.
3. **Dungeon gates, loot clamp, personal loot, repeat fatigue, carry factor, enrage wipe**: `DungeonService`,
   `BossService`, `CombatListener`.
4. **Rarity bands and loot tables** (`items/tiers.json`, `items/loot.json`), vendor prices (`ItemEconomy`).
5. **Class gear + armour level/tier + ActivePlaytime**, with a V12 migration (`docs/CLASS_GEAR_SYSTEM.md`).
6. **Death lock + wound**, persisted (`docs/DEATH_AND_RECOVERY.md`).
7. **Content 27–60**: four regions, six dungeons, Act II. Content production and visual assets
   (`docs/VISUAL_CONTENT_MASTER_PLAN.md`).
8. **Mastery, Ascension, mythic tiers, world boss, tempering.**
9. **Economy sinks**: temper, sigils, rite, scaled repairs.

## 11. Tests required

* `./gradlew :suld-plugin:simTest`: golden tests that the live model mirrors the content classes and curve, plus
  determinism, the loot band check, 7-day, AFK and lock monotonicity.
* For every implementation step:
  * unit tests of the new pure rules in `suld-api` (gap factor, gates, fatigue, lock curve, wound, armour XP);
  * JDBC integration tests of the V12 tables;
  * a regression run of the existing suites;
  * a re-run of the simulation with the implemented numbers. The live model must then converge to the proposed one.

## 12. Manual QA required

* **Calibration**:
  * bot sessions per region and level band (kills/min, deaths/h, dungeon times from the analytics log);
  * 2–3 human sessions.
  * Then replace the desk constants in `Engine` (seek time, hit share, efficiency) and re-run.
* **Feel**: real-client checks of boss mechanics, telegraphs, and lethality of +5 levels.

## 13. Implementation plan

After owner approval of these numbers:

* **Stage C1–C4** of the master plan, in order: death/recovery, class gear, armour + ActivePlaytime, assets. They reuse
  the existing managers (`DeathService`, `EquipmentService`, `ItemFactory`, `SkillTreeService`, the profile
  repository).
* Then the backlog above, one system per commit, each re-simulated.
