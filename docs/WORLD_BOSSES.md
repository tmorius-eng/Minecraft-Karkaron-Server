# World bosses

Plan Phase 3 (`docs/STATUS.md`). A world boss is a WORLD_BOSS-tier mob that rises at a historic site at a fixed weekly
hour, for the whole server to fight together. It reuses the dungeon boss engine and pays everyone who took part.

## Content

`content/events.json` → `worldBosses` (optional). Each entry:

| Field | Meaning |
|---|---|
| `id` | `worldboss.<name>` |
| `mob` | a mob in `mobs.json` with `tier: WORLD_BOSS` and `role: worldboss` (no region spawns it, it is no dungeon boss) |
| `site`, `dx`, `dz` | the historic site (`sites.json`) it rises at, and the offset from the site's heart |
| `phases` | `{at, attack, name}`: first `at` 1.0, strictly descending, as a dungeon boss |
| `enrageSeconds` | the boss enrages (×1.25); still standing a minute later, it withdraws |
| `durationSeconds` | still standing after this long, it withdraws (the fight is lost) |
| `leash` | blocks it can be pulled from where it rose before it walks back |
| `loot` | rolled once per rewarded fighter (`items/loot.json`, tier WORLD_EVENT, can roll nothing: PROG-006). The WORLD_EVENT band is rare 52 / epic 38 / legendary 10 (2026-10-18): never above a dungeon boss's rare 50 / epic 38 / legendary 10 / ancient 2 |
| `rewardExp`, `rewardCoins`, `clanBonus` | every rewarded fighter's EXP floor and coins; clan EXP once per clan |

`ContentLoader` checks the mob's tier and role, the site, the loot table and the phase order. The boss's abilities are
an `ArchetypeBrain` flavour in `SuldPlugin.registerBossBrains` (summons are removed with the boss).

Shipped:

| Boss | Level | Site | Abilities |
|---|---|---|---|
| Хөвсгөлийн Их Баавгай | 20 | Уушгийн Өвөр (Хөвсгөл) | cleave, charge, slam, summons grey wolves |
| Алтайн Их Аварга | 50 | Алтайн Хадны Зураг (Таван Богд) | slam, charge, barrage, nova, summons ice spirits |

## When

`config.yml` `worldboss.times` (`"<WEEKDAY> HH:mm"` in `daily.timezone`, default Wednesday 21:00 and Saturday 17:00).
The bosses take turns in content order (`WorldBossSchedule`). The server is told `announce-minutes` (10) before, and
again a minute before. An hour the server was down for is skipped; one held back by an admin's boss for more than five
minutes is skipped. Only one world boss stands at a time, and no automatic world event starts while it does.

Admins: `/suldevent boss [id]` raises one now, `/suldevent boss stop` sends it back without rewards.
`/suldevent status` names the standing boss or the next one.

## The fight

- Health: the tier's full health (200×) is a crowd of twenty. The boss is sized at its rising for the online players
  with a class within ten levels below it or above (at least 2), and grows when more fighters stand at the fight
  (never shrinks; it keeps the share of health it has left). `WorldBossFight.healthScale`.
- At the fight = within 48 blocks, alive, not a soul, with a class. Damage counts in design units and at most the
  health the boss had (an overkill counts once).
- Rewarded: at the fight for a third of it (at most 60 s asked), and either 0.5 % of the damage or there for half the
  fight. So a healer or a shield who deals little gets the **full** reward; someone passing by gets nothing.
- Podium: the top three by damage get ×1.5 / ×1.3 / ×1.2 EXP and coins on top (not without damage).
- Everyone rewarded: EXP (at least `rewardExp`, at least the level floor of `Rewards.worldEventExp`), coins, a loot
  roll, clan EXP once per clan. Offline or a soul at the payout: nothing (the world-event rule, no mail-in rewards).
- The kill is no one's last hit: `CombatListener` leaves it to `WorldBossService`.
- Hardcore: the boss kills for real, as any mob. A duel near it is still a duel.
- Хроник: `FIRST_WORLD_BOSS_KILL` (the first kill of each boss) and `FASTEST_WORLD_BOSS_KILL`, for the top three.
  `/chronicle boss`.
- Analytics: `world_boss_spawn`, `world_boss_end` (slain, seconds, fighters, rewarded, sized_for).

## Restarts

The boss is never saved with the world (`setPersistent(false)`, its chunk held by a plugin ticket while it stands).
Shutdown removes it and its summons; anything tagged `suld_world_boss` that outlived a crash is removed when its chunk
loads. A restart ends the fight without rewards.

## Tests

- `WorldBossTest` (api): definition rules, crowd scaling, eligibility (healer, passer-by, late arrival), podium,
  timeout, the schedule and the turns.
- `ContentDataTest.worldBossesAreCheckedAgainstMobsSitesAndLoot`.
- `tools/loadtest/bots/world-boss.js` on the dev server: raise, both fighters rewarded (one dealt all the damage, one
  only stood there), chronicle line, `boss stop` withdraws without rewards, nothing left behind.
