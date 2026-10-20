# Ovoo (Овоо)

An ovoo is a cairn of stones, often with a pole and blue silk khadag. It is a sacred place of the steppe and the
mountains, and the custom is to walk around it **three times clockwise** (нар зөв, the way the sun goes) and add a
stone. This custom is VERIFIED as a living tradition. SÜLD's ovoo and their blessing are game fiction built on it.

* **Where:** one ovoo at the heart of each of the 40 named areas (docs/world/AREAS.md), at the same point as the
  pre-generator's P3 area hearts. That is 40 places worth finding. The place is measured from the spawn: on a fresh
  world no cairn is built until Kharkhorum has moved the spawn onto its plaza, and if the spawn moves later
  (`/setworldspawn`) the ovoos move with it.
* **The cairn:**
  * a cone of mixed stones, 5 blocks wide and 4 high;
  * a foundation under it on uneven ground;
  * a spruce pole with light-blue, blue and white wool;
  * a light-blue banner on top.

  It is built the first time a player loads its chunk (never a forced load) and is remembered in
  `plugins/SULD/ovoo.yml`.
* **The walk:** `OvooCircle` (pure, tested) adds up the turn of the compass bearing from the cairn to the walker:
  * three full clockwise turns, between 2 and 9 blocks out, complete it;
  * walking the other way unwinds the count;
  * leaving the ring or teleporting resets it.

  A hint appears on the HUD the first time someone comes within 10 blocks.
* **The blessing «Тэнгэрийн ивээл»:**
  * +5 % EXP for 30 minutes (through `ProgressionBoosts`, kept across relogs);
  * one minute of regeneration;
  * once per ovoo per day (the day turns at midnight Ulaanbaatar time, like the daily rewards);
  * the first visit of each ovoo also pays 40 EXP.
* **Cost:** every 10 ticks, distance checks of the players near loaded ovoo only.

MANUAL_QA_REQUIRED in a real client: the look of the cairn on varied terrain.

## Мөнгөн Модны Найр (the Silver Tree's feast)

Right-clicking the Silver Tree of the palace square (prop `silver_tree`) opens its page: Rubruck's account of the tree
(four silver lions pouring mare's milk, four gilded serpents pouring wine, caracosmos, bal and rice mead —
`docs/research/history/KHARKHORUM.md` §5, history) and the feast, which is SÜLD's own rite. Once a day (the same day
as the ovoo's) a player with a class, alive, within 8 blocks of the tree takes one drink for 20 minutes:

| Drink | id | Boost (`TimedBoosts` kind) |
|---|---|---|
| Дарс | `wine` | damage +3 % (DAMAGE: basic hits, spells, procs and ultimates) |
| Хар айраг | `caracosmos` | EXP +5 % (EXP: the strongest source counts, so it does not stack with an ovoo's blessing) |
| Бал | `bal` | damage taken −10 % (MITIGATION, under the skill tree's 75 % cap) |
| Будааны дарс | `rice_mead` | loot luck +5 (LOOT) |

Order as the ovoo: the day, the drink and its end go into the player file, the file is written, then the boost starts;
a join puts back a drink still running, and a drink still running past midnight must end before the next one. The
reach is measured from the tree's own column. Boosts are not counted for the training yard's stand-in. The mark `TREE_DRINK` (V33) mirrors it in `suld_player_marks`. Achievement
`ach.lands.four_drinks` (all four). Staff: `/suld tree <player> drink <id>|status|reset` (audit `rite.silver_tree`).
The training yard's stand-in cannot drink. MANUAL_QA_REQUIRED: the page in a real client.
