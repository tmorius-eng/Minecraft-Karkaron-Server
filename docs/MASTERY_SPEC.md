# SÜLD mastery (proposed)

Part of the proposed balance. **Not implemented yet.** Numbers come from `ProposedRules` / `Engine.mastery`.

Mastery is long-term progression **separate from level** (directive §16). It starts at level 1, so casual players
always see something move. It continues after 60, gates parts of Ascension, and gives mostly horizontal rewards.

## Tracks

There are eight tracks with ten ranks each, 80 ranks in total.

| Track | Earned by | Not earned by |
|---|---|---|
| **Combat** (Байлдаан) | kills at a level gap ≥ −3 (normal 1, elite 4, champion 10); dungeon kills 1.5 each | farming low content (gap < −3) |
| **Dungeon** (Агуй) | clears: 60 × (ladder index + 1) × repeat fatigue | repeating one dungeon (fatigue applies) |
| **Boss** (Эзэн) | first kill of each boss 400, repeat 40 × fatigue, world boss 80 | — |
| **Exploration** (Аян) | region discovery 300, landmark 100, hidden place 200 (12 per region) | — |
| **Crafting** (Дархан) | crafting 30, salvage 4, mythic-run sigil 60, each temper step 300 | — |
| **Class** (Удам) | kills 0.6, dungeon clears 40, first boss kills 100, active minutes 0.1 | — |
| **Weapon** (Зэвсэг) | kills 0.5, dungeon kills 0.75 | — |
| **Collection** (Цуглуулга) | each item in each rarity 40, each set piece 100, each armour tier 200, mythic tier trophies 500, the first 20 world-boss trophies 80, hidden-place lore 60 | duplicates |

## Ranks: XP plus a varied objective

XP for rank r = 300 × r^1.9:

<!-- spec:begin mastery-need -->
| Rank | Mastery XP for this rank | Cumulative |
|---|---|---|
| 1 | 300 | 300 |
| 2 | 1120 | 1420 |
| 3 | 2419 | 3839 |
| 4 | 4179 | 8017 |
| 5 | 6385 | 14402 |
| 6 | 9028 | 23431 |
| 7 | 12101 | 35531 |
| 8 | 15595 | 51127 |
| 9 | 19507 | 70633 |
| 10 | 23830 | 94463 |
<!-- spec:end mastery-need -->

A rank also needs its **milestone**, so no track can be "the same thing 50,000 times":

| Track | Rank r also needs |
|---|---|
| Dungeon | r different dungeons cleared (max 10); from rank 8 at least one heroic |
| Boss | r different bosses killed (max 12); rank 9+ a world boss |
| Exploration | r regions discovered (max 8) and 4·r landmarks |
| Crafting | 2·r crafts / temper steps (salvage counts 1 per 20) |
| Collection | 6·r distinct collection entries |
| Combat, Class, Weapon | player level ≥ 6·r (rank 10 at level 60) |

## Rewards

These are horizontal by design:

* titles;
* cosmetics (badges, trails, name colours from the existing cosmetic catalog);
* collection log entries;
* Ascension gates (`docs/ASCENSION_SPEC.md`).

The power added is +0.25 % per rank across all ranks, so 80 ranks are worth +20 %. The simulation counts that number.

Class mastery is also the **armour mastery** of the class gear (`docs/ARMOR_PROGRESSION.md`). Ranks 3, 6 and 9
unlock armour mastery perks in the class resource line: Rage, Focus, Spirit, Heat, Momentum.

## Pacing (simulated)

See `docs/PROGRESSION_SIMULATION.md` §Player progression table. Total mastery ranks (p50):

* hardcore: 24 at day 7, 47 at day 30, 62 at day 90, 68 at day 180;
* casual: 11 at day 7, 37 at day 90, 49 at day 180.

No archetype completes all 80 ranks within 180 days. Exploration and Collection are capped by content, so new regions
and items raise them.
