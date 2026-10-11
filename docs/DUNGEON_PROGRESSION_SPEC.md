# SÜLD dungeon progression

Part of the proposed balance. **Status: progression v2 is in the game; see `docs/PROGRESSION_V2.md` for what is live and what is deferred.** This page keeps the full design. The ladder table is generated from `ProposedRules`
(`./gradlew :suld-plugin:specTables`). The effects are measured in `docs/PROGRESSION_SIMULATION.md`.

## Problems this solves

From `docs/PROGRESSION_EXPLOIT_AUDIT.md`:

* **DG-1:** loot level follows the player.
* **DG-2:** no lockout and no prerequisites.
* **DG-3:** bosses roll legendary or better.
* **DG-5:** carries are uncapped.
* **DG-7:** enrage never wipes.
* **DG-9:** at level 20, 75 % of the ladder is open.

## The ladder

There are ten dungeons across the eight regions, then nine heroic (level-60) versions with mythic tiers 1–10. Four are
the existing dungeons, kept and re-statted. Six are new, named after real places, with gameplay content that is
original:

* Далайн Гүн
* Хар Хотын Балгас (Khara-Khoto, the ruined Tangut city: INSPIRED)
* Улаан Хадны Хүрээ
* Бурхан Халдуны Агуй
* Тэнгэрийн Шат
* Тэнгэрийн Ордон (raid)

<!-- spec:begin ladder -->
| # | Dungeon | Level band | Story gate (chapter #) | Previous clear | Min gear power | Party | Boss HP / dmg | Enrage | Completion EXP | Coins | Loot level | Boss band |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Хасарын Агуй | 3–10 | 4 | — | 17 | 1 | 2468 / 28.8 | 180 s | 649 | 132 | clamp(L, 3, 10) | BOSS |
| 2 | Говийн Булш | 9–16 | 8 | Хасарын Агуй | 50 | 2 | 6850 / 41.1 | 195 s | 2982 | 204 | clamp(L, 9, 16) | BOSS |
| 3 | Баавгайн Үүр | 15–22 | 13 | Говийн Булш | 89 | 2 | 10725 / 55.8 | 210 s | 7277 | 276 | clamp(L, 15, 22) | BOSS |
| 4 | Мөсөн Оргил | 22–30 | 16 | Баавгайн Үүр | 144 | 3 | 20573 / 75.6 | 225 s | 14991 | 360 | clamp(L, 22, 30) | BOSS |
| 5 | Далайн Гүн | 29–37 | 21 | Мөсөн Оргил | 190 | 3 | 27853 / 98.4 | 240 s | 25805 | 444 | clamp(L, 29, 37) | BOSS |
| 6 | Хар Хотын Балгас | 36–44 | 27 | Далайн Гүн | 250 | 3 | 35685 / 124.1 | 255 s | 39876 | 528 | clamp(L, 36, 44) | BOSS |
| 7 | Улаан Хадны Хүрээ | 42–50 | 32 | Хар Хотын Балгас | 309 | 4 | 52680 / 148.5 | 270 s | 54631 | 600 | clamp(L, 42, 50) | BOSS |
| 8 | Бурхан Халдуны Агуй | 48–56 | 35 | Улаан Хадны Хүрээ | 353 | 4 | 61880 / 175.0 | 285 s | 71949 | 672 | clamp(L, 48, 56) | BOSS |
| 9 | Тэнгэрийн Шат | 54–60 | 39 | Бурхан Халдуны Агуй | 425 | 4 | 71400 / 203.7 | 300 s | 91896 | 744 | clamp(L, 54, 60) | BOSS |
| 10 | Тэнгэрийн Ордон | 60–60 | 42 | Тэнгэрийн Шат | 504 | 4 | 73040 / 208.7 | 315 s | 99139 | 816 | clamp(L, 60, 60) | BOSS |
| H1 | Хасарын Агуй (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |
| H2 | Говийн Булш (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |
| H3 | Баавгайн Үүр (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |
| H4 | Мөсөн Оргил (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |
| H5 | Далайн Гүн (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |
| H6 | Хар Хотын Балгас (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |
| H7 | Улаан Хадны Хүрээ (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |
| H8 | Бурхан Халдуны Агуй (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |
| H9 | Тэнгэрийн Шат (Баатарлаг) | 60–60 | all (42) | Тэнгэрийн Шат | 605 | 4 | 73040 / 208.7 | 315 s | 99139 | 780 | 60 | WORLD_EVENT |

Mythic tiers 1–10 of the heroic dungeons: health and damage ×(1 + 0.12·tier), EXP ×(1 + 0.06·tier), chest from the MYTHIC band, entry sigil 650–2900 ₮ (crafted).
<!-- spec:end ladder -->

Boss health is sized for the recommended party: ×(0.25 + 0.75 × party / 4) of the BOSS tier. Хасарын Агуй is soloable
by a par player inside its 180 s enrage. From Говийн Булш on, a group is expected. Solo players use the group finder
(5 minutes in the model).

## Multi-gate access (directive §7)

A dungeon opens only when **all** of these hold. All values are in the table above.

1. Player level ≥ the dungeon's minimum.
2. The story chapter it belongs to is finished. Act I chapters 4/8/13/16 open the first four dungeons; Act II chapters
   open the rest.
3. The previous dungeon in the ladder has been cleared once.
4. Gear power ≥ 75 % of par at the dungeon's minimum level. Heroics need 90 % of par at 60.
5. Heroics also need Тэнгэрийн Шат cleared.

There is no calendar gate, no lockout, no stamina and no keys bought with time (directive §2).

## Level 20 check (directive §6)

At level 20 a player has 3 of 10 dungeons open: Хасарын Агуй, Говийн Булш, Баавгайн Үүр. The sim shows all 3
realistically clearable (`docs/PROGRESSION_SIMULATION.md` §Level 20). Мөсөн Оргил opens at 22 after chapter 16, and
the rest need levels 29–60.

Live today: 3 of 4 open, i.e. 75 % of everything there is.

## Rewards and replay value without a lockout

| Rule | Value |
|---|---|
| Completion EXP | 4 % of the EXP of the dungeon's recommended level (min + 3); `carryFactor` and repeat fatigue apply |
| Loot level | `clamp(player level, min, max)`. A level-60 player in dungeon I gets band-I loot, which closes DG-1 |
| Chest (every member, personal) | DUNGEON band: uncommon 35 · rare 45 · epic 17 · legendary 3 |
| Boss loot (every member, personal) | BOSS band, re-cut: rare 50 · epic 38 · legendary 10 · ancient 2. Heroic bosses use WORLD_EVENT; mythic chests use MYTHIC |
| Repeat fatigue | −15 % of EXP, coins and loot value per clear of the **same** dungeon among the player's last 8 clears (floor 25 %). Activity-based, not time-based: doing other content restores it |
| First clear | the gate for the next dungeon and for armour tiers. Mastery: dungeon +60 × (index+1), boss first kill +400 |
| Carry | a member above the dungeon's max level gets ×0.1 (materials only). A member more than 10 levels below the party's top gets ×0.5 |
| Party share of kills | every member in range gets (1 + 0.15·(n−1)) / n of each kill. Groups earn ~+23 % per member and are safer (simulated: 183 h to 60 in a party of four vs 203 h solo), while carries are capped |
| Enrage | wipes. 180 s + 15 s per ladder step |

Replay value comes from:

* better rolls (quality decides item power, `docs/GEAR_PROGRESSION_SPEC.md`);
* set pieces;
* band materials for armour tiers;
* dungeon and boss mastery;
* heroic and mythic tiers;
* collection trophies.

## What the simulation shows

See `docs/PROGRESSION_SIMULATION.md`:

* No playstyle levels more than 25 % faster than the generalist (C11).
* No exploit scenario beats normal play by more than ×1.25 EXP per online hour (C12).
* Repeating the first dungeon (E1) ends day 7 at level 19, against 35 for normal play.
* Dungeons are 13–29 % of the EXP to 60 depending on the player, not the majority.

## Implementation notes (for the gameplay phase)

* Gates belong in `DungeonService.start` (`suld-plugin/.../dungeon/DungeonService.java:107-169`). Add story chapter,
  previous clear (persisted per profile), and gear power (`Equipment` + the proposed item-power formula).
* Loot level clamp: `DungeonService.java:352`.
* Personal boss loot: `CombatListener.java:194` currently rolls for the killer only.
* Repeat fatigue needs the last 8 clears per player. This is a small persisted list, not a timer.
* `BossService.java:76-86`: enrage → wipe.
