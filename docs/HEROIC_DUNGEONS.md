# «Тэнгэрийн Гэсгээлт Агуй» — heroic dungeons

**Status: BUILT (directive 2026-10-13, Phase 3).** The rules are pure Java in `suld-api`
(`mn.suld.api.dungeon.heroic`, `mn.suld.api.item.AffixReroll`, `mn.suld.api.persistence.HeroicRepository`); the plugin
side is `mn.suld.plugin.dungeon.heroic` on top of the normal runs of `DungeonService`. Storage: schema V24
(`suld_heroic_keys`, `suld_heroic_runs`). Bot: `tools/loadtest/bots/heroicbot.js` (in the sweep).

## What a heroic key is

A level-60 party runs a dungeon of the ladder again, against a clock, with a **key level** (+1 … +20):

| | Rule (`HeroicLadder`) |
|---|---|
| Who | every member level 60; every member has cleared the dungeon once on its normal difficulty |
| Mobs | level 60, then **+10 % max health** and **+8 % damage** per key level (+5: ×1.5 health, ×1.4 damage) |
| Clock | 2 minutes to gather + 75 s a wave + the boss's enrage time (Саарлын Агуй: 7:30); every member's death adds **15 s** |
| Affixes | +2: one, +4: two, +7: three, +10: all four; the order rotates every campaign week (the same for everyone) |
| Keys | the leader picks any key up to the highest it has opened in that dungeon (+1 to start); the others need no key |
| Timed | the next key opens for every member standing at the end (two keys up within 80 % of the limit); each gets **«Эртний Хас Чулуу»**: 1 + key / 4 |
| Over time | cleared but late: nothing new opens, no jade |
| Failed | wiped, left, enraged or stopped: nothing new opens. A key never falls: the hardcore death is the price |

A member who fell or left before the end gets what a normal run gives them (nothing): their keys stay where they were.
The completion reward of a heroic run is a level-60 dungeon's (EXP, coins, loot at 60, no carry reduction; the repeat
and clear-fatigue rules of the ladder still apply). The chronicle's first and fastest clears stay the normal dungeon's;
a heroic clear still counts for a CLEAR_DUNGEON campaign. A +10 or higher timed key is announced to the server.

## The steppe affixes (`SteppeAffix`)

The names are the steppe's own hardships (VERIFIED words: *зуд* is the killing winter, *ган* the drought); what they do
in a hall is the game's (INSPIRED).

| Affix | What it does | How to play it |
|---|---|---|
| **Цагаан Зуд** | every 30 s a blizzard: whoever carries no fire (a torch, lantern or campfire in either hand) and stands within 4 blocks of no open fire (campfire, torch, fire, lava) is slowed by 40 % for 8 s and loses 8 % of max health to frostbite. The halls' own lanterns do not warm (they light every hall a few blocks apart) | hold a torch in the off hand |
| **Галт Ган** | a dying hall mob bursts into embers (3 blocks, half its level-60 attack × the key, SÜLD armour applies, sets on fire); class resources regenerate at half the rate | do not finish mobs in melee piles; pace the spells |
| **Чонын Омог** | a hall mob hits 15 % harder for each other mob of the run within 8 blocks (at most 4 count: ×1.6) | pull them apart |
| **Тэнгэрийн Ниргэлт** | in the boss fight, every 9 s a mark appears under each member; 1.5 s later lightning strikes it (2 blocks, 25 % of max health) | step out of the mark |

## «Эртний Хас Чулуу»

The reward of a timed key (`item.ertnii_khas`: an emerald with no model, epic, not tradable, never in a loot table, no
coin price). At the smith (Дархан → «Засвар · Хуяг») a SÜLD item held in the hand gets a **«Шинж дахин сугалах»** button:
the same item, rarity, level and base stats, its affixes rolled again by the rules of a fresh drop of that rarity and
level (`ItemGenerator.rerollAffixes`). Only rare or better, item level 50+. Cost: rare 1, epic 2, legendary 3, ancient 4,
mythic 5 jade, plus the reforge coins of the item's level (`Economy.reforgeCoins`: 3 685 coins at 60). Audited
(`item.affix_reroll`).

*Naming:* the owner named the stone «Эртний Хас Чулуу». The game already writes jade as «хаш» elsewhere («Хаш Бөгж»);
«хас» is the older word (as in «хас тамга»). Left for the native review (docs/CULTURAL_REVIEW_P1.md).

## Playing it

| Where | What |
|---|---|
| `/dungeon` (level 60) | the «Баатарлаг Агуй» button opens the keys window |
| `/dungeon heroic` | the keys window: each plinth shows the open key, the best key and time, the limit, this week's affixes at the open key and the mob numbers; at the gate a left click enters at the open key, a right click one key lower; away from it the way-finder starts |
| `/dungeon heroic <агуй> [түлхүүр]` | a run at that key (the highest open by default), at the gate |
| Gate: Shift + click | a level-60 player enters at the open key (anyone else enters normally) |
| In the run | the HUD line «Баатарлаг +3 · 1:12 / 7:30 · унасан 1 (+15 с) · зуд 12 с»; the name plates read «Lv 60» |
| Staff | `/dungeon heroic <агуй> <түлхүүр> ZUD,GAN` (or `NONE`) runs that key with these affixes instead of the week's (QA; `suld.admin`, audited `dungeon.heroic_affixes`) |

## How the lift works (plugin)

- The run's mobs are spawned with their content definition lifted to level 60 (`MobDefinition.atLevel`: health, attack
  and EXP follow the curves, a content override keeps its ratio to the curve) and the key's health
  (`HeroicRun.health`). The level is stored on the mob (`MobService`, PDC `mob_level`); `MobService.definition(entity)`
  reads it back for the name plate, the HUD target, the hit numbers, SÜLD armour (`CombatListener.attackerLevel`), the
  player's hit gap and the kill's EXP and loot. A boss's add joining a heroic run is resized and stood at 60 too.
- The hits stay computed from the content definition (melee, arrows, the boss's phases, the brains' abilities), and
  one HIGH listener (`HeroicDungeonService.onHeroicHit`) multiplies every hit of a heroic run's mob on a player by the
  lift (level-60 attack ÷ content attack) × the key × «Чонын Омог». Multiplicative, so its order among the other HIGH
  reductions does not matter.
- «Галт Ган»'s regeneration halving is in `SkillService.regenOne`.

## Concurrency audit (Phase 3)

| State | Owner / thread | Why it is safe |
|---|---|---|
| The run (`ActiveRun.heroic`: key, affixes, clock, deaths) | main thread only (DungeonService, its ticker, the listeners) | no other thread touches it |
| The keys cache (per online player) | written on the main thread only: a finished run, and the read at join (hopped back to the main thread) | the read is merged by `Key.max` (each key at its maximum), so a read that returns after a run already raised the cache never lowers it; a player gone by then keeps no cache |
| `suld_heroic_keys` | `JdbcHeroicRepository.raise` on the shared io pool, one transaction: an upsert lifting `open_key` by GREATEST, then a conditional update of both best columns when the result is better | every step only raises, so any order and any interleaving end at the maximum (IT: 13 results on 8 threads in random order end equal to the sequential in-memory rule, H2 and PostgreSQL 16); first-insert races (SQLState 23) and deadlocks (40) retry |
| `suld_heroic_runs` | append-only, `run_id` primary key | a run finishes once: `complete` and `fail` both stop on an inactive run, and its entities leave `runsByEntity` |
| The jade | paid on the main thread through `ItemService.give`, once per finished run per rewarded member | as above, a run finishes once |
| A reroll | main thread; the click re-reads the held item and goes on only if it is the same item (uuid) with the same affixes, still allowed, with the jade and the coins there | a second click (or a swap) finds other affixes or another item and stops; jade and item change in the player file together, the coins are saved after (a crash between them loses coins, never makes jade) |

## Verified

| Check | Result |
|---|---|
| api `HeroicLadderTest`, `HeroicKeyTest`, `AffixRerollTest`, `MobDefinitionLevelTest` | green in `./gradlew build` |
| `JdbcHeroicRepositoryIT` (keys only rise, concurrent results end at their maximum, the board) | green on H2 and on PostgreSQL 16 |
| `heroicbot.js` on the dev server | 19/19: the three gates; a +1 run (Lv 60 hall mob, the clock on the HUD, timed in 0:34 → +3 open, 1 jade, logged, the window shows +3); a +3 run with «Цагаан Зуд» forced (a bot with no fire freezes, a torch in the off hand keeps the next blizzard off, timed → +5); a failed +2 keeps +5; a level-60 rare sword's affixes rerolled at the smith for 1 jade |

Not verified by a person with a real client yet: the feel of the numbers at higher keys (a +10 at ×2.0 health and
×1.8 damage on level-60 mobs), «Галт Ган» and «Тэнгэрийн Ниргэлт» in a real fight (the bot kills from the console).
