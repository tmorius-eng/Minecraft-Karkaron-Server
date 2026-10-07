# SÜLD mastery (proposed)

Part of the proposed balance. Numbers come from `ProposedRules` / `Engine.mastery`.

**Status:**
* The **Class track is IMPLEMENTED as Armour Mastery** (Stage C3b, `suld-api/.../classgear/MasteryRules`,
  `MasteryPerks`, stored in the profile's `class_gear` record), FUNCTIONAL_BUT_INCOMPLETE. It is described under
  "Armour mastery (as built)" below.
* The other seven tracks are SPEC.

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


## Armour mastery (as built, Stage C3b)

Ranks 0–10 per character. Rank r → r + 1 needs 300·(r + 1)^1.9 mastery XP (94,463 in total).

| Source | Mastery XP | Diminishing return |
|---|---|---|
| Kill | 1 / 4 / 10 (normal / elite / champion and up) | none if the mob is more than 3 levels below the player; × the farming factor |
| Own class spell cast (not QA casts) | 0.5 | at most 12 count per minute |
| Class objective | 1 | at most 10 per minute |
| Dungeon clear | 40 × repeat fatigue | −15 % per same-dungeon clear in the last 8 (floor 25 %) |
| First clear of a dungeon (its boss) | +100 | once per dungeon |
| Region discovery (first visit) | 30 | once per region |

Class objectives:
* Баатар: every 40 damage taken from mobs.
* Мэргэн: a projectile kill from 16 blocks or more.
* Бөө: a healing prayer (Сүнсний Залбирал / Тэнгэрийн Хаалга) cast while someone within 8 blocks is below 80 % HP.
* Дархан: crafting, and the smith's repairs and upgrades.
* Хүлэгчин: a kill while mounted.

* **Milestones:** rank r needs player level ≥ 6r. From rank 4 it also needs r − 2 different dungeons cleared. Rank
  10 therefore needs level 60 and 8 dungeons, which no player can have in 7 days (simulation C2 and C13).
* **Power:** +0.25 % class-armour power per rank, through `Equipment.Wearer.armorFactor`.
* **Tier gates:** T3 needs rank 1, T4 rank 3, T5 rank 5, T6 rank 7.
* **Perks at ranks 3 / 6 / 9** (`MasteryPerks`): stats and spell modifiers merged into the SkillBuild by
  `SkillTreeService.refreshRuntime`, plus resource gain/regeneration multipliers read by `SkillService`. Where the spec
  wording has no exact hook, the closest existing modifier is used; these are marked (≈) in-game:
  * Мэргэн r6: first arrow after a dodge → Чонын Нүд +25 % damage.
  * Мэргэн r9: Volley +1 arrow → 20 % echo.
  * Бөө r9: Онгон +2 s → 15 % echo.
  * Баатар r9: War cry refund → −20 % cost.
  * Хүлэгчин r3: momentum decay → +15 % regeneration.
  * Хүлэгчин r6: charge stun → 1 s slow.
  * Хүлэгчин r9: mounted damage → +5 % attack.
* **Simulation:** day-7 armour mastery is rank 5 at p90 for a 10 h/day player. No run reaches maximum armour power in 7
  days (C13 PASS).
