# SÜLD — Quest and task audit (audit 2026-10-14)

Sources read: `suld-plugin/src/main/resources/content/quests.json`, `QuestService`, `QuestTracker`, `QuestMenu`,
`QuestChain`, `QuestEngine`, `ContentLoader` (quest section), `TaskService`, `CombatListener.onDeath`,
`DungeonService.soloEntryRefusal`, `NpcService`. The web-session sub-audit `quests.md` referenced by the execution
prompt is not in the repository; QS-01..QS-07 below are taken from the prompt's references and re-verified in code.
QS-06 and QS-08 are named in the prompt's range but never specified, so they are not invented here.

## 1. The 42-chapter story — verification matrix

Type: K = KILL_MOB, C = COLLECT_ITEM, L = REACH_LEVEL, D = COMPLETE_DUNGEON, X = DISCOVER_LOCATION.
Every target was checked against `ContentLoader`'s validation rules (mob in mobs.json, region/site in world.json /
sites.json, dungeon in dungeons.json, item in the catalog); the loader rejects a story with a dangling target, and
`ContentDataTest` loads the bundled pack, so a broken target fails the build.

| # | id | Title | T | Target | n | Giver | Notes |
|---|---|---|---|---|---|---|---|
| 1 | quest.first_hunt | Анхны Ан | K | mob.goviin_chono | 3 | Анчдын Ахлагч | giver aligned with `NpcService` (QS-04) |
| 2 | quest.wolf_pelts | Чонын Арьс | C | item.chonon_arisan | 3 | Анчдын Ахлагч | items taken on completion; file written before SQL |
| 3 | quest.kherlen_bandits | Хэрлэнгийн Дээрэмчид | K | mob.deeremchin | 5 | Худалдаачин | |
| 4 | quest.oath_5 | Цэргийн Тангараг | L | — | 5 | Хотын Ноён | |
| 5 | quest.khasar_den | Саарлын Агуй | D | dungeon.khasar_den | 1 | Хотын Ноён | gate refusal now shown (QS-02) |
| 6 | quest.gobi_road | Говийн Зам | X | region.gobi | 1 | Морьтон | |
| 7 | quest.gobi_scorpions | Хилэнцийн Хор | K | mob.goviin_khilents | 6 | Жингийн Тэргүүн | party credit (QS-01) |
| 8 | quest.sand_spirits | Элсний Сүнснүүд | K | mob.elsnii_suns | 5 | Бөө | spirit: Арц Хүж wards |
| 9 | quest.gobi_tomb | Говийн Булш | D | dungeon.govi_bulsh | 1 | Бөө | |
| 10 | quest.oath_12 | Мянгатын Сорил | L | — | 12 | Мянгатын Ноён | |
| 11 | quest.khangai_forest | Хангайн Ой | X | region.khangai | 1 | Морьтон | |
| 12 | quest.grey_wolves | Саарал Чонын Сүрэг | K | mob.saaral_chono | 8 | Малчин | |
| 13 | quest.khangai_bear | Хангайн Эзэн | K | mob.khangai_baavgai | 2 | Малчин | elite; drops Анчны Нум now |
| 14 | quest.bear_lair | Баавгайн Үүр | D | dungeon.baavgain_uur | 1 | Малчин | |
| 15 | quest.altai_peaks | Алтайн Оргил | X | region.altai | 1 | Бөө | |
| 16 | quest.ice_spirits | Мөсөн Сүнс | K | mob.altai_mosun_suns | 8 | Бөө | spirit |
| 17 | quest.ice_peak | Мөсөн Оргил | D | dungeon.mosun_orgil | 1 | Их Бөө | |
| 18 | quest.altai_giant | Алтайн Аварга | K | mob.altai_avarga | 3 | Их Бөө | elite; drops Алтайн Жад now |
| 19 | quest.uushig | Буган Хөшөөдийн Дуудлага | X | site.uushig | 1 | Их Бөө | Act II opens |
| 20 | quest.khuvsgul_taiga | Хөвсгөлийн Тайга | X | region.khuvsgul | 1 | Анчин | |
| 21 | quest.water_lus | Усны Лус | K | mob.usny_lus | 8 | Анчин | spirit |
| 22 | quest.dalain_gun | Далайн Гүн | D | dungeon.dalain_gun | 1 | Их Бөө | |
| 23 | quest.oath_33 | Түмэнтийн Тангараг | L | — | 33 | Түмэнтийн Ноён | |
| 24 | quest.tangut_tower | Тангудын Хилийн Харуул | X | site.tangut_tower | 1 | Жингийн Тэргүүн | |
| 25 | quest.zuungar_sands | Зүүнгарын Элс | X | region.zuungar | 1 | Жингийн Тэргүүн | |
| 26 | quest.tangut_ghosts | Тангудын Сүнснүүд | K | mob.tangud_suns | 10 | Бөө | spirit |
| 27 | quest.ruin_scorpions | Балгасны Хилэнц | K | mob.balgasny_khilents | 4 | Бөө | |
| 28 | quest.khar_khot | Хар Хотын Балгас | D | dungeon.khar_khot | 1 | Их Бөө | |
| 29 | quest.dariganga | Дарьгангын Хүн Чулуу | X | site.dariganga | 1 | Морьтон | |
| 30 | quest.redrock_raiders | Хүрээний Харуул | K | mob.khureenii_kharuul | 10 | Мянгатын Ноён | |
| 31 | quest.war_wolves | Дайны Чоно | K | mob.dainy_chono | 5 | Мянгатын Ноён | |
| 32 | quest.oath_42 | Түмний Сорил | L | — | 42 | Түмэнтийн Ноён | |
| 33 | quest.ulaan_khad | Улаан Хадны Хүрээ | D | dungeon.ulaan_khad | 1 | Түмэнтийн Ноён | |
| 34 | quest.ogloogchiin_kherem | Өглөгчийн Хэрэм | X | site.ogloogchiin_kherem | 1 | Их Бөө | |
| 35 | quest.mountain_spirits | Уулын Савдаг | K | mob.uulyn_savdag | 10 | Их Бөө | spirit |
| 36 | quest.burkhan_agui | Хэнтийн Гүн Агуй | D | dungeon.burkhan_agui | 1 | Их Бөө | |
| 37 | quest.otgon_peaks | Отгонтэнгэрийн Оргил | X | region.otgon | 1 | Морьтон | |
| 38 | quest.altai_petroglyphs | Алтайн Хадны Зураг | X | site.altai_petroglyphs | 1 | Их Бөө | |
| 39 | quest.sky_soldiers | Тэнгэрийн Цэрэг | K | mob.tengeriin_tsereg | 10 | Их Бөө | |
| 40 | quest.tengeriin_shat | Тэнгэрийн Шат | D | dungeon.tengeriin_shat | 1 | Их Бөө | |
| 41 | quest.burkhan_ovoo | Ариун Уулын Овоо | X | site.burkhan_ovoo | 1 | Их Бөө | candidate for OVOO_RITUAL (roadmap) |
| 42 | quest.oath_60 | Хөх Сүлдний Дуудлага | L | — | 60 | Их Бөө | the story ends at the cap |

Shape: 15 K, 1 C, 5 L, 9 D, 12 X. Nine of the ten dungeon rungs are story chapters; the tenth
(`dungeon.tengeriin_ordon`) is reached after chapter 42 — intended as the post-story goal.

**Narrative evaluation.** Act I walks the four home regions in level order (Хэрлэн → Говь → Хангай → Алтай), each
closing on its dungeon; Act II returns to the outer lands through historic sites and ends on the sacred ovoo before the
final oath. The weakest part is the vocabulary: 27 of 42 chapters are "kill N" or "go to X". The new objective kinds
(§4) exist so the next content pass can replace some of them without a schema break.

## 2. Daily, campaign and tutorial tasks

| Area | Finding | Status |
|---|---|---|
| Daily hunting tasks (`TaskService`) | credited the killer only; party members near the kill got EXP but no task progress | Fixed: `TaskService.credit` runs for the killer and every `CombatListener.partyNearby` member |
| Daily progress durability | "done" progress stored before the reward (`guardThen`, PERS-002) | Verified (read) |
| Campaign KILL_MOBS goal | the task names its campaign when the prey is a campaign target (`TaskService`:72-80) | Verified (read) |
| Tutorial dungeon pointer | uses `soloEntryRefusal` so it points only at an open gate (`TutorialService`:212) | Verified (read); the quest tracker now does the same |

## 3. QS findings and fixes

| ID | Finding | Fix | Test |
|---|---|---|---|
| QS-01 | Party members within 48 blocks shared the kill's EXP (`sharers`) but not the story objective or daily tasks | `CombatListener.partyNearby` (one shared list: online, alive, same world, ≤ `ExpRules.PARTY_RANGE` = 48, not a soul, not in training); `quests.onMobKilled` for each; `TaskService.credit` for each | manual (needs players) — `LAPTOP_PLAYTEST_PLAN.md` Q-03 |
| QS-02 | A dungeon chapter's tracker said «энд! Хаалгыг дар» even when the gate would refuse the player | `QuestTracker.gateRefusal` (DungeonService.soloEntryRefusal as plain text, cached 2 s per player); tracker shows «⛔ reason»; quest book card shows «Хаалга: ⛔ reason» | manual — Q-04 |
| QS-03 | `QuestChain.normalise` left an unknown chapter id unchanged: a renamed or removed chapter soft-locked the story and `completedCount` = 0 (chapter skill points lost) | `QuestChain(chapters, aliases)` maps former ids; `normalise(s, reached)` places any other unknown id at the first chapter not yet behind the player; `QuestService.ensure` uses the player's level (chapters priced below it count as done — nothing is paid twice) and logs the move | `QuestChainOrphanTest` |
| QS-04/05 | Chapter 1 (and 2, same NPC) named the giver «Анчин»; the NPC is «Анчдын Ахлагч» (`NpcService.java:64`, `NpcDialogue.java:58`) | quests.json ch. 1–2 giver = «Анчдын Ахлагч» | data; `ContentDataTest` loads it |
| QS-07 | Prompt: save quest state before paying | Already correct: `QuestService.complete` sets the next state before EXP; `onComplete` → `PlayerDataSaves.itemsForCoins` writes the player file (taken items) then the profile row (state + coins + EXP in one row) | verified (read) |

## 4. New objective vocabulary (backward compatible)

`QuestType` gains three kinds; quests.json is unchanged in format and no chapter uses them yet.

| Kind | `target` | Advanced by | Progress | Loader check | Chapter level (EXP) |
|---|---|---|---|---|---|
| `INSPECT_CLUE` | prop id (props.json, e.g. `deer_stone`) | `PropService.onClick` (right-click on the prop's interaction box) | completes on match | id format (props.json is outside the content pack) | the chain's running level |
| `CRAFT_ITEM` | item definition id | `CraftService.craft` after the item is given and saved | adds the amount made | item exists in the catalog | 2 (then the chain's running level) |
| `OVOO_RITUAL` | area id or region id | `OvooService.bless` after a successful (once-a-day) blessing | completes on match | area or region exists in world.json | the region's min level |

Wired through: `QuestEngine.advance`, `QuestService.onInspect/onCrafted/onOvooRitual`, `ContentLoader` (target and
level switches), `QuestTracker.targetRegion`, `QuestMenu` (icon, kind label, objective text), `Menus.questIcon`, and the
simulator (`Engine.chapter` treats them as a short walk; `ProposedRules.chapterLevel`). Tests:
`QuestObjectiveVocabularyTest`.

Spec for a content author:

```json
{"id": "quest.deer_stone_clue", "title": "Буган Хөшөөний Нууц", "type": "INSPECT_CLUE", "target": "deer_stone",
 "count": 1, "giver": "Их Бөө", "hint": "Уушгийн өврийн буган хөшөөг шинжил."}
{"id": "quest.first_forge", "title": "Анхны Давталт", "type": "CRAFT_ITEM", "target": "armor.esgii.helmet",
 "count": 1, "giver": "Дархан"}
{"id": "quest.kherlen_ovoo", "title": "Хэрлэнгийн Овоо", "type": "OVOO_RITUAL", "target": "<area id>", "count": 1}
```

## 5. Open items

* QS-06 / QS-08: unspecified in the execution prompt; the source `quests.md` is absent.
* No unit test drives `partyNearby` / `gateRefusal` (they need a live server); they are on the playtest sheet.
* Act II could move chapter 41 (`site.burkhan_ovoo`, DISCOVER_LOCATION) to `OVOO_RITUAL` — a content decision for the
  owner (cultural review: the site is sacred; CULT-001 rules apply).
