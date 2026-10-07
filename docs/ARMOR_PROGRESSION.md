# SÜLD class armour progression — armour level, tier, mastery (proposed)

Part of the proposed balance (`docs/PROGRESSION_BALANCE_SPEC.md`); simulated in `docs/PROGRESSION_SIMULATION.md`
§Class armour. **Not implemented yet.** Visuals: `docs/ARMOR_PROGRESSION_VISUAL_SPEC.md`. Item integration and binding:
`docs/CLASS_GEAR_SYSTEM.md`.

Every class owns one evolving signature set: helmet, chest (with shoulders and arms), legs, boots, plus the class
weapon. It grows along three separate axes:

| Axis | Grows with | Gives | Cap |
|---|---|---|---|
| **Armour level (AL)** | **active play**: real activity, never idle connection time (`docs/ACTIVE_PLAYTIME_SPEC.md`) | the item level of every class piece (= its stat budget) | the player level (and 60) |
| **Armour tier (T1–T6)** | milestones: AL + a dungeon cleared + coins + band materials | rarity of the class pieces (uncommon → mythic) and **a new silhouette** | T6 (endgame) |
| **Armour mastery** | class mastery (`docs/MASTERY_SPEC.md`): class skills, kills, clears, first bosses | resource-synergy perks at ranks 3/6/9 | rank 10 |

## Armour level

Armour XP comes from:

| Source | Armour XP |
|---|---|
| active minute (validated) | 0.6 |
| normal kill / elite / champion | 0.2 / 1 / 3 |
| dungeon clear | 25 × repeat fatigue |
| story chapter | 20 |
| region discovery | 15 |
| landmark / hidden place | 5 |

**AL needs round(5.6 × a^1.3) armour XP for a → a+1.** At the ~150 armour XP per active hour of normal play, the
cumulative hours follow the level curve, so armour level tracks the player level for a normal player.

AL is capped at the player level. When AL is more than 3 below the level (after a carry, a long break or a
power-levelled alt), armour XP counts double until it catches up. Armour is therefore never "permanently unusable", and
a short break never strands it.

<!-- spec:begin armor-need -->
| Armour level | Armour XP to next | Cumulative | ≈ active hours at 150 XP/h |
|---|---|---|---|
| 1 | 6 | 0 | 0 |
| 5 | 45 | 77 | 1 |
| 10 | 112 | 431 | 3 |
| 15 | 189 | 1141 | 8 |
| 20 | 275 | 2256 | 15 |
| 25 | 368 | 3814 | 25 |
| 30 | 466 | 5847 | 39 |
| 35 | 569 | 8382 | 56 |
| 40 | 677 | 11443 | 76 |
| 45 | 790 | 15053 | 100 |
| 50 | 905 | 19231 | 128 |
| 55 | 1025 | 23996 | 160 |
| 59 | 1123 | 28242 | 188 |
| 60 | 0 | 29365 | 196 |
<!-- spec:end armor-need -->

## Armour tiers

<!-- spec:begin tiers -->
| Tier | Name | Armour level | Must have cleared | Coins | Materials | Class-gear rarity |
|---|---|---|---|---|---|---|
| T1 | Анхан (starter) | 1 | — | 0 | — | uncommon |
| T2 | Бэхжсэн (reinforced) | 12 | Говийн Булш | 2000 | 10 band materials | rare |
| T3 | Сонгомол (elite) | 24 | Мөсөн Оргил | 12000 | 15 band materials | epic |
| T4 | Эзэнт (imperial) | 36 | Хар Хотын Балгас | 45000 | 20 band materials | legendary |
| T5 | Домогт (legendary) | 48 | Бурхан Халдуны Агуй | 120000 | 25 band materials | ancient |
| T6 | Тэнгэрлэг (endgame) | 60 | Тэнгэрийн Ордон + Ascension III | 300000 | 30 band materials | mythic |

Enhancement +1…+5 inside a tier: +2 % item power each, 40 × armour level × step × tier coins; reset by the next tier.
<!-- spec:end tiers -->

The breakpoints were chosen with the simulation so that the tiers land in their bands:

| Player | Day 7 | Day 30 | Day 90 | Day 180 |
|---|---|---|---|---|
| Casual 2 h | AL 16, T2 | AL 28, T3 | AL 53, T5 | AL 60, T5 |
| Active 5 h | AL 24, T2 | AL 50, T5 | AL 60, T5 | AL 60, **T6** (19 of 20 runs) |
| Hardcore 10 h | AL 32, T3 | AL 60, T5 | AL 60, T5 (T6 in 8 of 20 runs) | AL 60, **T6** (20 of 20) |

* **The 7-day rule (directive §17): PASS.** No hardcore run has max armour (AL 60 + T6) in 7 days, or even 30.
* **T6** needs the raid and Ascension III, so it arrives around days 90–180 for a 10 h/day player and about day 180
  for 5 h/day.

## Armour and the class (directive §11, §12)

All effects flow through the existing stat pipeline: the class pieces are real items whose stats reach `SkillBuild`
through `Equipment.compute`. There are no hidden stats.

| Class | Set bonus (2 / 4 pieces) | Mastery perks (ranks 3 / 6 / 9) |
|---|---|---|
| Баатар | +HEALTH / damage reduction | Rage from damage taken +10 % · Rage cap +15 · War cry refunds 20 % |
| Мэргэн | +CRIT_CHANCE / move speed | Focus regen +10 % · first arrow after a dodge +25 % · Volley +1 arrow |
| Бөө | +SPELL_DAMAGE / HEAL_POWER | Spirit cap +20 · heals give 5 % shield · Онгон lasts +2 s |
| Дархан | +ARMOR / thorns | Heat from hits +10 % · overheat threshold +10 · forge slam radius +1 |
| Хүлэгчин | +MOVE_PCT / momentum damage | Momentum decay −15 % · charge stun +0.3 s · mounted damage +5 % |

The class set bonus is counted as ×1.08 item power in the simulation. Perks are build modifiers, worth at most a few
per cent of power.

## After level 60 (directive §18)

The player level stops; the armour keeps progressing:

* T6 (raid + Ascension III);
* enhancement +1…+5 in T6;
* tempering +1…+10;
* armour mastery ranks to 10;
* collection of every tier's look (each tier is a collection entry, and the cosmetic layer can wear any unlocked tier).

Stats stop at T6 +5 +10 temper, and the rest is horizontal.

## Simulation inputs to replace with measurements

The 150 armour XP per active hour and the 0.6 per active minute are desk values. The tracker's real "active minute"
rate (`ACTIVE_PLAYTIME_SPEC`) must be measured on bots and humans before the numbers are final.
