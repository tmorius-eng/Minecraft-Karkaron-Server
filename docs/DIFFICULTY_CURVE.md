# SÜLD difficulty curve (proposed)

Part of the proposed balance (`docs/PROGRESSION_BALANCE_SPEC.md`). **Not implemented yet.** Every number in the tables
is generated from `suld-plugin/src/sim/java/mn/suld/sim/ProposedRules.java` (`./gradlew :suld-plugin:specTables`).

## Why the curve has to change

The live game has no difficulty curve, only a list of hand-set mobs
(`docs/PROGRESSION_EXPLOIT_AUDIT.md`):

* **Gear outweighs levels about 3 : 1 (GR-8).** The item engine scales item stats per level. A level-60 legendary weapon
  rolls ~140–170 damage against a class base attack of ~51. Mob health (16–40 for normal mobs, ≤ 1,000 for bosses) was
  set before gear existed, so everything dies in one or two hits.
* **Player health ignores class and level (PW-1).** Every class has 20 health. `PlayerClass.baseHealth` is display-only.
* **Normal mobs deal vanilla damage (XP-2).** They ignore `baseAttack`.
* **Armour saturates (PW-5).** The ARMOR stat becomes the vanilla armour attribute, whose reduction caps at 80 % around
  20–25 points. Gear armour beyond that is wasted.
* **Attack speed is ignored (PW-4).** SÜLD hit damage ignores the attack-cooldown charge, so every melee weapon hits at
  the 2 hits/s cap. Heavy weapons have no identity.

## How the proposed curve is built

The curve is derived, not guessed:

1. The simulator measures the median ("par") player that the proposed rules produce at every level. It averages hardcore
   runs of all five classes with real generated gear (`mn.suld.sim.Calibrate`).
2. The par curves are fitted:
   * DPS ≈ 3.8·L^1.28
   * max health ≈ 38.5 + 4.8·L + 0.117·L²
   * armour ≈ 8 + 0.95·L
   * regeneration ≈ 0.3 + 0.05·L per second
3. Mobs are set so a fight against them at par always reads the same:
   * **NORMAL**: dies in **2.5 s** and costs **15 %** of max health per fight.
   * **ELITE**: 2.5× health, 1.3× damage (~6 s, ~40 %).
   * **CHAMPION**: 5× health, 1.6× damage. Lethal solo, so players avoid it alone; it is a group target.
   * **BOSS**: 40× health × (0.25 + 0.75 × recommended party / 4), i.e. ×0.44 for a solo dungeon up to ×1.0 for four;
     1.0× damage × phase average 1.27, swings every 2 s.
   * **WORLD BOSS**: 200× health.
4. Mitigation becomes SÜLD's own `a / (a + K)` with **K = 10 + 2.5 × mob level**, capped at 75 %. This is the formula
   `CombatCalculator.mitigation` already implements, with a level-scaled constant. Armour keeps its value at every
   level.

The danger of a fight is its damage ÷ max health. The death hazard per fight is
0.012 % + 0.5 / (1 + e^−(danger − 1)/0.08). Comfortable content (danger ≈ 0.15) kills about once per 30–80 hours of
play, typically through mistakes. Content at danger 1 is a coin flip.

<!-- spec:begin mob-curve -->
| Level | Normal HP | Normal dmg/hit | Normal EXP | Elite HP / dmg / EXP | Champion HP / dmg | Boss HP (party of 4) / dmg | Par DPS | Par HP | Par armour | Mitigation at par |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 32 | 14 | 40 | 80 / 18.2 / 72 | 160 / 22.4 | 1280 / 17.8 | 13 | 51 | 8 | 39 % |
| 2 | 43 | 15 | 50 | 107.5 / 19.5 / 90 | 215 / 24 | 1720 / 19.1 | 17 | 56 | 9 | 37 % |
| 5 | 86 | 18.5 | 81 | 215 / 24.1 / 146 | 430 / 29.6 | 3440 / 23.5 | 35 | 72 | 12 | 34 % |
| 10 | 182 | 25.7 | 135 | 455 / 33.4 / 243 | 910 / 41.1 | 7280 / 32.6 | 73 | 103 | 17 | 32 % |
| 15 | 298 | 34.2 | 191 | 745 / 44.5 / 344 | 1490 / 54.7 | 11920 / 43.4 | 119 | 139 | 22 | 31 % |
| 20 | 429 | 43.9 | 250 | 1072.5 / 57.1 / 450 | 2145 / 70.2 | 17160 / 55.8 | 172 | 182 | 26 | 31 % |
| 25 | 573 | 54.8 | 311 | 1432.5 / 71.2 / 560 | 2865 / 87.7 | 22920 / 69.6 | 229 | 230 | 31 | 30 % |
| 30 | 727 | 66.9 | 375 | 1817.5 / 87.0 / 675 | 3635 / 107.0 | 29080 / 85.0 | 291 | 283 | 36 | 30 % |
| 35 | 890 | 80.2 | 441 | 2225 / 104.3 / 794 | 4450 / 128.3 | 35600 / 101.9 | 356 | 342 | 41 | 30 % |
| 40 | 1063 | 94.7 | 510 | 2657.5 / 123.1 / 918 | 5315 / 151.5 | 42520 / 120.3 | 425 | 407 | 46 | 29 % |
| 45 | 1243 | 110.3 | 581 | 3107.5 / 143.4 / 1046 | 6215 / 176.5 | 49720 / 140.1 | 497 | 478 | 51 | 29 % |
| 50 | 1431 | 127.1 | 655 | 3577.5 / 165.2 / 1179 | 7155 / 203.4 | 57240 / 161.4 | 572 | 554 | 56 | 29 % |
| 55 | 1625 | 145.1 | 731 | 4062.5 / 188.6 / 1316 | 8125 / 232.2 | 65000 / 184.3 | 650 | 636 | 60 | 29 % |
| 60 | 1826 | 164.3 | 810 | 4565 / 213.6 / 1458 | 9130 / 262.9 | 73040 / 208.7 | 730 | 723 | 65 | 29 % |
<!-- spec:end mob-curve -->

## Level gap rules

Rushing high-level zones and farming low ones are both closed by the same rules. EXP gives a small bonus for fighting
up. Level suppression makes fighting more than ~5 levels up dangerous. Fighting 10+ levels down pays 10 % EXP and no
gear.

<!-- spec:begin gap-rules -->
| Mob level − player level | Kill EXP | Gear rolls | Damage you deal | Damage you take |
|---|---|---|---|---|
| -12 | ×0.10 | no (materials only) | ×1.00 | ×1.00 |
| -10 | ×0.10 | no (materials only) | ×1.00 | ×1.00 |
| -9 | ×0.25 | yes | ×1.00 | ×1.00 |
| -7 | ×0.55 | yes | ×1.00 | ×1.00 |
| -5 | ×0.85 | yes | ×1.00 | ×1.00 |
| -4 | ×1.00 | yes | ×1.00 | ×1.00 |
| 0 | ×1.00 | yes | ×1.00 | ×1.00 |
| +2 | ×1.05 | yes | ×0.92 | ×1.16 |
| +4 | ×1.10 | yes | ×0.84 | ×1.32 |
| +6 | ×1.15 | yes | ×0.76 | ×1.48 |
| +8 | ×1.20 | yes | ×0.68 | ×1.64 |
| +10 | ×1.20 | yes | ×0.60 | ×1.80 |
| +15 | ×1.20 | yes | ×0.40 | ×2.20 |
<!-- spec:end gap-rules -->

## Difficulty from mechanics, not only numbers (directive §31)

The fight model already charges an unavoidable "mechanics" share on every boss: +15 % damage for skilled players and
+30 % for typical ones. Implementation must earn that share with telegraphs, not hidden health. Per boss tier:

| Tier | Mechanics required (in addition to the numbers above) |
|---|---|
| Normal / elite mob | none; elites get one telegraphed heavy attack (1.5 s wind-up, ×2.5 damage, dodgeable) |
| Champion | two abilities + a positional one (frontal cone or ground ring); enrage at 120 s |
| Dungeon boss | 3 phases (100/60/30 %), one new ability per phase, adds in phase 2, **enrage wipes** at the dungeon's enrage time (180–315 s) |
| Heroic / mythic | the dungeon boss plus one affix per mythic tier band (e.g. tier 1–3 "fortified" adds, 4–6 "bursting", 7–10 "tyrannical" boss) |
| World boss | party-of-many: target swaps, area denial, a soft enrage that raises damage taken 5 %/30 s |
| Endgame raid boss (Тэнгэрийн Хаан) | 4 phases, role checks (tank swap, a healing check, a DPS check), hard enrage |

## Class readability

The classes keep different risk profiles, as the classes table in `docs/PROGRESSION_SIMULATION.md` shows:

* Мэргэн kites (hit share 0.30) but is the frailest.
* Баатар blocks (hit share 0.55) with the most health.
* Бөө out-heals (+0.6 HP/s).
* Дархан and Мэргэн hit hardest per swing:
  * hit weight ×1.55 on the axe/hammer at 1.0 hits/s;
  * hit weight ×1.45 on a fully drawn bow at 1.0 shots/s;
  * against the sword's ×1.0 at 1.6 hits/s.

These are design intentions, simulated at desk values. The bot calibration of the implementation phase must confirm
them.
