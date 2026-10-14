# Achievements and the season's pass «Хуралдайн Зам»

Directive Phase 5: deeds the server honours, and a season of rewards for playing. Both give **cosmetics only**: a
title, a name or chat colour, a join message. Never an item, a level or power; nothing here is for sale except the
heroic track, and that is not sold yet (see «The heroic track»).

Code: api `mn.suld.api.achievement` (`Deed`, `Criterion`, `DeedBook`, `Achievement`, `AchievementCatalog`) and
`mn.suld.api.battlepass` (`CampaignPass`, `Season`, `PassLayout`); plugin `mn.suld.plugin.achievement.AchievementService`,
`gui/AchievementMenu`, `command/AchievementCommand`. Content: `suld-api/src/main/resources/achievements/achievements.json`
and `pass.json`.

## Deeds

The game records what a player does where it happens. Each deed is kept per subject in one of three ways (`Deed.Keep`):

| Deed | Subject | Kept as | Recorded by |
|---|---|---|---|
| `LEVEL` | — | best | every level-up shown (`Presentation.levelUp`) |
| `CHAPTER` | chapter id | seen | `QuestService` (a chapter completed) |
| `DUNGEON_CLEAR` | dungeon id | count | `DungeonService.complete` (present, not downed) |
| `HEROIC_TIMED` | dungeon id | best key | `HeroicDungeonService` (a key in time) |
| `SITE` | site id | seen | `HistoricSiteService` (discovered) |
| `OVOO` | ovoo id | seen | `OvooService` (walked round) |
| `REGION` | region id | seen | `RegionSpawner` (a region found) |
| `MOB_KILL` | mob id | count | `TaskService` (every SÜLD mob a player kills) |
| `WORLD_BOSS` | boss id | count | `WorldBossService` (paid as a fighter) |
| `WEAPON_MASTERY` | track | best | `WeaponMasteryService` (a level) |
| `CRAFT_LEVEL` / `GATHER` | craft | best / count | `ProfessionService` |
| `ASCENSION` | — | best | `AscensionMenu` (both rites) |
| `CLASS_MASTERS` | — | best | `ClassArmor` (a class mastered) |
| `DUEL_WIN` | — | count | `DuelService` |
| `TRIAL_WIN` | — | count | `DeathTrialService` |
| `CAMPAIGN_TIER` | campaign id | best | `CampaignService` (a tier paid) |
| `VOTE` | — | count | `KhuraldaiService` |
| `CLAN_JOIN` | clan id | seen | `ClanService` (founded or joined) |
| `DAILY_TASK` | — | count | `TaskService` (a task done) |

A training stand-in records nothing.

**What came before.** Once a player's deeds are read at join, what they already did is recorded as a backfill: the
level, the Тэнгэрийн Зэрэг, the mastered classes, the chapters completed, the sites seen, the ovoos walked round, the
regions found, the clan, and (5 s later, once those tables are read) the weapon tracks and the crafts. Each is a best
or a seen deed, so recording it again changes nothing. A backfill unlocks what it completes but gives the pass no points.

## Achievements

37 in seven pages (Түүх, Тулаан, Баатарлаг, Нутаг, Мэргэжил, Алдар, Нийгэм). Each has a criterion over one deed: the
`TOTAL` of its subjects, how many `DISTINCT` subjects were done, the `BEST` value, or `EACH` of a list reaching the target
(all four crafts at 50). An achievement is unlocked once, with a title, a sound and a chat line. It gives its points to
the pass (not capped) and, for 12 of them, a title. A hidden one, or one with a legendary title, is announced to the server.

**Names and history.** Every achievement says how its name relates to history (`basis`, the labels of
`docs/CULTURAL_REVIEW_P1.md`):

* `VERIFIED`: attested in a named source.
  * «Арван гурван хүрээний тулаан»: Rashid al-Din's thirteen kurens; Secret History §129, Dalan Baljut.
  * «Мөнгөн модны нууц»: William of Rubruck, 1254, the silver tree of Karakorum by the goldsmith Guillaume Boucher.
* `LEGEND`: a legend told in a source. «Бөртэ Чино, Гуа Маралын үр сад»: Secret History §1.
* `INSPIRED`: the steppe's spirit only, never presented as history.

The catalog refuses a `VERIFIED` or `LEGEND` name without its source. The three historical names, and every title, are
on the list for the native review (they are earned, never sold).

## The pass «Хуралдайн Зам»

* **Seasons.** A season is 8 Хуралдай weeks (Monday to Sunday, the server's zone) counted from `pass.epoch`
  (2026-10-05): season `S1` runs 2026-10-05 to 2026-11-29. Each season starts at 0 points.
* **Tiers.** 50 tiers of 1 000 points.
* **Points.**
  * Deeds give points (`CampaignPass.points`): a dungeon clear 120, a world boss 300, a chapter 200, a region or site
    100, a daily task 150, a vote 100, a level 50, a kill 2, a gather 4…
  * Deeds give at most **2 500 a day**. The cap is kept in memory across relogs; a restart forgets it.
  * An achievement's own points are not capped.
  * A backfill gives none.
* **Tracks.** Two tracks of cosmetics (`pass.json`):
  * The free track: a reward every 5 tiers, 10 in all.
  * The heroic track: tier 1 and every 5 tiers, 11 in all.
  * Every reward is a cosmetic of the pass's own (`source: pass`, never sold).
* **Claiming.** `/pass` shows 8 tiers a page. A reached reward is claimed by a click, or every one at once with
  `/pass claim`.
  * A claim is a row of its own (`suld_pass_claims`, primary key), so it is made once, even when two arrive at once.
  * The cosmetic follows the row.

### The heroic track

It is opened per season by staff (`/suld achievement <player> heroic`, audited). `pass.heroic-credits` is the price
`/pass` shows (0 shows «not sold yet»). Selling it for Сүлд Кредит comes with the store (directive Phase 7, Bonum);
until then no money path exists for it. Its rewards are cosmetics like the free track's: the heroic track never gives
power.

## Storage (V28)

| Table | Row | Write |
|---|---|---|
| `suld_player_deeds` | player, `DEED\|subject`, value | counts add; bests keep the greater (`GREATEST`) |
| `suld_player_achievements` | player, achievement, when | inserted once |
| `suld_pass_points` | player, season, points | adds |
| `suld_pass_claims` | player, season, `FREE/5` | inserted once |
| `suld_pass_heroic` | player, season, when, source | inserted once |

Every write commutes, so batches written in any order end at the same state. Deeds, unlocks and points are written
every 30 s, at quit and at shutdown (SuldServices waits within its budget). A failed write is kept for the next try. A
record made before the player's read is back waits and is replayed after it.

## Commands

* `/achievements [category]` (also `/amjilt`): the pages. Each achievement shows its description, its progress, its
  points, its title and where its name comes from.
* `/pass [claim]` (also `/zam`): the season, the tiers and both tracks.
* Staff (audited `admin.achievement`):
  * `/suld achievement <player>` prints the line the bot reads: unlocked, season, points, tier, heroic, claimable.
  * `… deed <DEED> <subject|-> <amount>` records a deed as the game would.
  * `… points <n>` adds points straight to the pass.
  * `… heroic` opens the heroic track.
  * `… reset` forgets the player's deeds, unlocks and passes.

## Tests

* api: `AchievementTest` covers the three keeps, the four measures, the bundled catalog, the titles that are theirs
  alone, an unlock only when completed, and a broken file refused. `CampaignPassTest` covers the tiers, claiming, the
  bundled layout, the points and the cap, and the seasons.
* plugin: `JdbcAchievementIT` (H2, PostgreSQL 16) covers counts and bests, 48 concurrent batches, and 16 claims at once
  making one.
* Bot: `tools/loadtest/bots/achievebot.js`, in `regress.sh`.
