# The dungeon ladder (1–10)

Ten dungeons, each with a gate in the open world and its own themed hall (docs/world/DUNGEON_HALLS.md). A dungeon
opens when **both** hold:
1. the player's level is at least the dungeon's minimum;
2. the previous dungeon on the ladder has been cleared once (stored in the player's data, `suld:dungeon_clears`).

`suld.admin.world` skips the ladder gate. `/dungeon` opens **Агуйн Шат**, the dungeon window (`gui/DungeonMenu`,
background `gui_dungeons` in `tools/pack/gen_ui.py`): ten plinths on a stone stair climbing from the steppe to the sky
palace, one per dungeon (the stack count is its rung). Each tooltip shows:
* its state: ✔ cleared (glowing), ▶ open, 🔒 locked (and why);
* the place, level, party size, waves and boss;
* the gate's compass direction and distance.

A click within 16 blocks of the gate enters. Anywhere else it starts a purple way-finder bar to the gate
(`dungeon/DungeonGuide`: arrow, direction, distance; it ends at the gate, on entering a run or after 30 minutes).
The bottom row has the main menu, the party, the player's progress (cleared x/10), leave-the-run or stop-the-guide,
and close. The chat list is gone.

| # | Dungeon | Level | Party | Gate (bearing / distance from the spawn) | Hall | Waves | Boss |
|---|---|---|---|---|---|---|---|
| 1 | Хасарын Агуй | 2+ | 1–4 | 75° / 380 | DEN | 2 | Хасар — Агуйн Эзэн (rig with its own brain) |
| 2 | Говийн Булш | 8+ | 1–4 | 165° / 650 | TOMB | 3 | Элсний Хаан — Булшны Эзэн |
| 3 | Баавгайн Үүр | 14+ | 1–4 | 20° / 1000 | LAIR | 3 | Хар Баавгай — Ойн Эзэн |
| 4 | Мөсөн Оргил | 22+ | 1–4 | 255° / 1400 | PEAK | 3 | Мөсөн Хаан — Оргилын Сахиул |
| 5 | **Далайн Гүн** | 29+ | 1–4 | 335° / 2600 (Хөвсгөл) | DEEP | 3 | Лусын Хаан — Далайн Эзэн |
| 6 | **Хар Хотын Балгас** | 36+ | 1–4 | 200° / 3000 (the Tangut border) | RUIN | 3 | Хар Жанжин — Балгасны Эзэн |
| 7 | **Улаан Хадны Хүрээ** | 42+ | 1–4 | 110° / 3300 | REDROCK | 3 | Улаан Хадны Ноён |
| 8 | **Бурхан Халдуны Агуй** | 48+ | 1–4 | 60° / 3700 (Хэнтий) | SACRED | 3 | Хангай Савдаг — Уулын Эзэн |
| 9 | **Тэнгэрийн Шат** | 54+ | 1–4 | 285° / 4100 (high Altai) | SKYSTAIR | 3 | Хөх Тэнгэрийн Элч |
| 10 | **Тэнгэрийн Ордон** (raid) | 60 | 2–4 | 0° / 4500 (the northern edge) | PALACE | 4 | Хөх Сүлдийн Сахиул |

## New creatures (dungeons 5–10)

Усны Лус, Далайн Чоно, Тангудын Сүнс, Балгасны Аварга Хилэнц, Хүрээний Харуул, Дайны Чоно, Уулын Савдаг, Агуйн
Баавгай, Тэнгэрийн Цэрэг, Тэнгэрийн Чоно and Ордны Сахиул, plus the six bosses. Every one has a model
(docs/models/MOB_RIGS.md) and loot tables:
* mobs drop a regional material;
* bosses drop 3–4 materials (a Тэнгэрийн Чулуу from the last two);
* a completion chest holds one gear piece at the player's level, plus materials and a rare.

Stats continue the live curve of dungeons 1–4:

| Dungeon | Base HP of a normal mob | Boss base HP (× the BOSS tier) | Completion EXP | Coins |
|---|---|---|---|---|
| 5 | 52 | 54 | 8 000 | 560 |
| 10 | 92 (soldiers) | 110 | 42 000 | 1 400 |

Enrage arrives after 255–315 s, as in the spec's ladder.

## History and fiction

* **Real places, simplified:** Хөвсгөл ("Далай ээж"), Хар Хот (the Tangut city of Khara-Khoto), the Хэнтий and
  Бурхан Халдун, the Altai.
* **Folk-belief names used as INSPIRED creature types:** лус (water spirits) and савдаг (mountain spirits).
* **INSPIRED:** Хар Жанжин, after the Khara-Khoto legend of the Black General.
* **SÜLD fiction:** the Хөх Сүлд palace and its guardian. No sacred symbol is reproduced: the palace uses blue and
  gold, not the Soyombo.

## Not done yet (honest scope)

* The spec's story-chapter and gear-power gates are not applied. Only 15 story chapters exist, while the spec
  numbers chapters up to 42, and the gear-power scale is not verified against live items.
* Heroic and mythic versions are not built.
* Bosses 2–10 use the generic boss phases (health and damage multipliers per phase) with no special abilities yet.
  Only Хасар has a brain.
