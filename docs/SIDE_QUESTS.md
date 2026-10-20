# Нэмэлт эрэл (side quests)

**Status:** IMPLEMENTED (unit tests and PG ITs; real client: the owner's check, `docs/qa/`). Schema V32. Content
`content/side_quests.json`. Commands `/quest side [page]`, staff `/suld squest`.

The story chapters (`quests.json`) are one line that moves on by itself. Side quests are the city's own errands: a
person of Kharkhorum asks for something, the player takes it, does it, and comes back to hand it in. They teach the
city's history (the craftsmen's quarter the Bonn excavations found, the örtöö roads), lead to places the story skips,
and pay a little EXP, coins and, for the last of a chain, a gift.

## 1. Rules

| Rule | Where |
|---|---|
| A quest is given and handed in by one NPC of `content/npcs.json` (`giver`); never a campaign's dynamic NPC | `ContentLoader` |
| At most **3** active at once (one take in flight per player, so two quick takes cannot both pass the limit) | `SideQuestBook.MAX_ACTIVE` |
| A quest opens at its `minLevel`, for its `classes` (empty = all), after its `requires` (`quest.*` = that chapter done, `squest.*` = that side quest handed in); no loop of requirements | `SideQuestBook.gate`, loader DFS |
| Each is done once (`repeatable` is refused by the loader) | loader |
| Taking and handing in happen within 8 blocks of the giver, alive (a soul cannot), with a class | `SideQuestService.GIVER_REACH` |
| Every story objective hook advances the side quests too: kills (and the party's shared kills), regions and sites, dungeons, inspected props, crafted items, ovoo rites; a COLLECT quest counts the bag | `QuestService.SideHooks` |
| Reward: `exp` (default `round(0.12 × need(minLevel))`), `coins` (default `30 + 6·minLevel`), optional `loot` rolled at tier QUEST | `Rewards.sideQuestExp/Coins`, `SideQuestService.pay` |
| A story line names its source: `history` VERIFIED / INSPIRED / FICTION; «түүхэн баримт» only on VERIFIED | loader (`NpcLine.overclaims`) |

## 2. Hand-in order (PlayerDataSaves)

1. COLLECT: the items leave the bag and the player file is written (`PlayerDataSaves.now`).
2. The row turns DONE with a compare-and-set (`UPDATE … WHERE state = 'ACTIVE'`). A refused write gives the items back
   (the player's current session: bag, else the mailbox). A write that failed may still have committed, so the row is
   read back first: DONE at this hand-in's own time goes on to the claim, ACTIVE (or an earlier hand-in) gives the items back, and an unreadable row holds them (a severe
   log line and an audit row `unconfirmed, items held` for staff) rather than risk both returning and paying.
3. The reward is claimed: `paid_at` goes from 0 to now with a compare-and-set (`WHERE paid_at = 0`), true once.
4. Coins and EXP are added, the gift goes to the bag or the mailbox, the player file is written, then the profile is
   saved (`PlayerDataSaves.itemsForCoins`).

A DONE row with `paid_at = 0` owes its reward. A player who left before step 3 keeps it owed, and the next load pays
it; a player in the training yard is paid when training ends (`TrainingService.defer`), and items given back then go to
the real bag, not the yard's kit. A claim whose answer was an error is read back (`paid_at` = that claim's time → paid); one who left while the claim was in flight gets the claim undone
(`paid_at` back to 0 where it is still that claim's time), so the reward is owed again; if even the undo fails, the
reward is written to the audit log (`claimed, unpaid: …`) for staff. A gift-less reward saves the profile only (no
player-file write). A crash between steps 3 and 4
loses that reward, never pays it twice — the same rule as every reward (PlayerDataSaves). The training yard's
stand-in profile can neither take, advance nor hand in a side quest. Callbacks that land while the plugin stops wait
for `PlayerDataSaves.drainShutdown`, so a hand-in whose items already left the bag gives them back or pays.

Progress is written every 30 s and on quit (one ordered writer thread, `suld-sidequest-io`); a failed write
marks its quest dirty again, and a player who left keeps their record in memory until the quit's write is confirmed,
so the retry writes the record's own count, never an older one (one backend server; the writes are ordered). A failed
load or reward claim is tried again a few times while the session lasts. Every answer is checked against the player's current session: after a relog the old
session's callbacks touch nothing, and a claim the old session held is undone and claimed again for the new one. The events of the ~100 ms
between a join and the rows' arrival are not counted.

## 3. Storage (V32)

`suld_side_quests (player_uuid, quest_id, state ACTIVE|DONE, progress, started_at, completed_at, paid_at, PK
(player_uuid, quest_id))`, index on `(quest_id, state)`. Abandon deletes an ACTIVE row (the quest can be taken again);
staff reset deletes any row. `JdbcSideQuestIT` checks that 24 concurrent hand-ins of one quest turn it DONE once and 24
concurrent claims pay it once.

## 4. Where players see them

* **The giver's conversation:** «✦ Эрэл: …» opens the quest's page (story, objective, reward, «Авах»); «✔ Тушаах: …»
  hands a ready one in. The «Нэмэлт эрэл» action of a SIDE_QUESTS giver opens the book.
* **The book** `/quest side` (also the paper in the story book, slot 52): active quests first, then the file's order,
  eight a page. A card hands in or takes when the giver stands near, says where to go otherwise, shows the refusal of a
  closed one (level, class, requirement, the limit); an active card offers «Орхих».
* **The tracker bar:** a white boss bar for the oldest active quest («✦ title n/need», an arrow to a site), «✔ Тушаах:
  giver» with an arrow to the NPC when ready. Hidden with the «Эрлийн самбар» setting off, in a dungeon, outside the
  overworld.
* **Achievements:** `ach.social.side_helper` (five handed in), `ach.lands.urtuu_road` (the four örtöö quests).
* **Analytics:** `side_quest_accept`, `side_quest_complete`, `side_quest_abandon` (apart from `quest_complete`, which
  the funnel reads as the first chapter).

## 5. The first ten (Багц 1)

| id | Гарчиг | Өгөгч | Зорилго | Lv | requires | history |
|---|---|---|---|---|---|---|
| squest.kk_silver_tree | Мөнгөн Модны Сүүдэр | tutorial | үзэх `silver_tree` | 1 | — | INSPIRED |
| squest.kk_coppersmith | Дархны Дөш | blacksmith | цуглуулах `item.tumriin_kheltarkhii` ×6 | 3 | — | INSPIRED |
| squest.kk_craftsmen_quarter | Урчуудын Хороолол | blacksmith | урлах `weapon.modon_sukh` | 3 | kk_coppersmith | INSPIRED |
| squest.kk_kiln_clay | Зуухны Шавар | blacksmith | дарах `mob.deeremchin` ×6 | 4 | — | FICTION |
| squest.kk_old_coin | Хуучин Зоос | merchant.general | цуглуулах `item.altan_toos` ×1 | 12 | kk_coppersmith | INSPIRED |
| squest.kk_three_faiths | Гурван Шашны Мэтгэлцээн | shrine.sky | үзэх `stone_turtle` | 5 | kk_silver_tree | INSPIRED |
| squest.urtuu_east | Зүүн Замын Өртөө | fast_travel.plaza | хүрэх `site.urtuu_east` | 6 | — | INSPIRED |
| squest.urtuu_south | Өмнөд Замын Өртөө | fast_travel.plaza | хүрэх `site.urtuu_south` | 8 | urtuu_east | INSPIRED |
| squest.urtuu_north | Хойд Замын Өртөө | fast_travel.plaza | хүрэх `site.urtuu_north` | 13 | urtuu_south | INSPIRED |
| squest.urtuu_west | Алтан Гэрэгийн Зам | fast_travel.plaza | хүрэх `site.urtuu_west` | 22 | urtuu_north | INSPIRED, `loot.quest.reward` |

## 5b. The next fourteen (Багц 2): the class paths and the Orkhon valley

| id | Гарчиг | Өгөгч | Зорилго | Lv | Анги | requires | history |
|---|---|---|---|---|---|---|---|
| squest.baatar_shield | Бамбайн Тангараг | quest.khotyn_noyon | дарах `mob.khangai_baavgai` ×3 | 15 | Баатар | — | FICTION |
| squest.baatar_wall | Хэрмийн Хамгаалагч | quest.khotyn_noyon | дарах `mob.tangud_suns` ×12 | 35 | Баатар | baatar_shield | FICTION, gift |
| squest.mergen_eye | Мэргэний Нүд | quest.first_hunt | дарах `mob.saaral_chono` ×10 | 15 | Мэргэн | — | FICTION |
| squest.mergen_wind | Салхины Сум | quest.first_hunt | дарах `mob.balgasny_khilents` ×4 | 35 | Мэргэн | mergen_eye | FICTION, gift |
| squest.boo_ovoo | Овооны Дуудлага | quest.ikh_boo | овоо `region.khangai` | 15 | Бөө | — | INSPIRED |
| squest.boo_spirits | Сүнсний Зам | quest.ikh_boo | овоо `region.khuvsgul` | 35 | Бөө | boo_ovoo | INSPIRED, gift |
| squest.darkhan_hammer | Дархны Алх | blacksmith | урлах `jewel.chonyn_soyo` | 15 | Дархан | — | FICTION |
| squest.darkhan_gold | Алтан Гагнуур | blacksmith | урлах `jewel.altan_bugj` | 35 | Дархан | darkhan_hammer | INSPIRED, gift |
| squest.khulegchin_ride | Урт Давхиа | quest.morton | хүрэх `region.altai` | 18 | Хүлэгчин | — | FICTION |
| squest.khulegchin_steppe | Дарьгангын Давхиа | quest.morton | хүрэх `site.dariganga` | 35 | Хүлэгчин | khulegchin_ride | FICTION, gift |
| squest.orkhon_khar_balgas | Хар Балгасын Хэрэм | quest.ikh_boo | хүрэх `site.khar_balgas` | 10 | — | — | INSPIRED |
| squest.orkhon_turtle_steles | Мэлхий Суурьт Хөшөө | quest.ikh_boo | хүрэх `site.khoshoo_tsaidam` | 11 | — | orkhon_khar_balgas | INSPIRED |
| squest.orkhon_kilns | Хархорумын Зуух | quest.ikh_boo | цуглуулах `item.tumriin_khudar` ×8 | 12 | — | orkhon_turtle_steles | VERIFIED (the kilns, KHARKHORUM.md), gift |
| squest.orkhon_deer_stones | Буган Хөшөөний Нууц | quest.ikh_boo | үзэх `deer_stone` | 17 | — | orkhon_kilns | INSPIRED |

The Orkhon stories are labelled INSPIRED: their sites' facts are not yet in the research files, so no line calls
them history. Achievements `ach.lands.orkhon` (the four) and `ach.social.class_path` (two of a class path).

The sources behind the stories are in `docs/research/history/KHARKHORUM.md` (§10, fact and source table). The items
come from play: iron shards from salvaging commons or refining iron ore, gold dust from salvaging rare/epic gear or
refining; `weapon.modon_sukh` is the level-3 recipe (4 wolf pelts, 3 shards).

## 6. Staff

`/suld squest <player>` lists the rows; `/suld squest <player> accept|ready|done|reset <squest.id>` takes one (no
reach, level or requirement check), completes its objective (not for COLLECT: give the items), hands it in (pays), or
deletes the row. Every use is audited (`squest.admin`).

## 7. Adding a side quest

Append an entry to `content/side_quests.json` (`id squest.<name>`, `title`, `description`, `story`, `history`,
`giver`, `type`, `target`, `count`, `minLevel`, optional `classes`, `requires`, `exp`, `coins`, `loot`), run
`./gradlew build` (ContentDataTest, CulturalNamingTest, LootTableCallersTest, AchievementSubjectsTest) and
`/suld content validate` on the dev server. Ids are never reused: a player's DONE row keeps the old meaning.
