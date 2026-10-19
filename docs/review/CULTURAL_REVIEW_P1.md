# Cultural, language and history review: P1 changes and open items

- **Status:** **NEEDS NATIVE REVIEW.** The changes below were made by the developer without a Mongolian-language reviewer or historian. Every new name is game fiction (category D) and should be checked before the closed alpha.
- **Audit references:** `docs/audits/2026-10-11/06-history-language-and-culture.md` (CULT-001 in `02`). The audit text is unchanged; this file records what the P1 phase did about it.

## 1. Renamed (display names only; ids and save data unchanged)

| Was | Now | Where | Why |
|---|---|---|---|
| «Хасар — Агуйн Эзэн» (first boss) | «Хөгшин Саарал — Агуйн Эзэн» | `content/mobs.json` `mob.khasar`; boss warnings in `KhasarBrain` | Chinggis Khaan's brother Qasar was the name of a farmed beast (HIST-024) |
| «Хасарын Агуй» | «Саарлын Агуй» | `content/dungeons.json`, `DungeonLadder`, chapter 5 title and description, tutorial step, NPC line | same |
| «Хасарын Агуй хэмээн домоглодог хадан агуй…» | «Тал нутгийн хадан агуйд Хөгшин Саарал хэмээх аварга чоно эзэрхэж…» | chapter 5 story | The line also invented a "legend" |
| «Хасарын Соёо», «Хасарын Зүрх» | «Саарлын Соёо», «Саарлын Зүрх» | `items/weapons.json`, `items/jewelry.json` | same |
| «Бурхан Халдуны Агуй» (monster dungeon) | «Хэнтийн Гүн Агуй» | `content/dungeons.json`, `DungeonLadder`, chapter 36 | The sacred mountain (UNESCO 2015, per search snippet) as a monster dungeon (HIST-023) |
| «Хангай Савдаг — Уулын Эзэн» (boss killed there) | «Хадны Мангас — Агуйн Эзэн» | `content/mobs.json` `mob.khangai_savdag` | A land spirit was slain for loot. The chapter-35 story now says monsters angered the savdag; it no longer places them in Burkhan Khaldun |
| «Чингисийн Малгай/Дээл/Өмд/Гутал», set «Чингисийн Өв» | «Хаадын Малгай/Дээл/Өмд/Гутал», «Хаадын Өв» | `items/armor.json`, `items/sets.json` | Chinggis Khaan's name on mob loot (HIST-025) |
| «Чингисийн Тамга», lore «Их хааны тамга» | «Хаадын Тамга», lore «Эртний хаадын тамга (SÜLD-ийн зохиомол)» | `items/jewelry.json` | same; labelled as fiction |
| Lore «Их хааны өв — …» | «Эртний хаадын өв — …» | `items/armor.json` | same |
| Ultimate «Чингисийн Уур» | «Баатрын Их Уур» | `skill/tree/Ultimate.java`, `skills/baatar.json` | Chinggis Khaan's name on a combat skill |

Kept on purpose:
- The **«Бурхан Халдуны Их Овоо»** site and chapter 41: a respectful ovoo visit, no combat.
- The **«Чингисийн Хэрэм»** site: a real place name, already labelled "built before Chinggis".

## 2. Withheld from sale (owners keep them; not listed to others)

New cosmetic source `withheld` (`Cosmetic.withheld()`): not sold for coins or credits, and hidden in `/cosmetics` unless owned.

| Cosmetic | Was sold for | Reason |
|---|---|---|
| `tag.chingis` «⚜ Их Хааны Ач ⚜» | credits (real money) | "Grandson of the Great Khan". Commercial use of Chinggis Khaan's name has unresolved legal and platform-policy questions (2006 Mongolian proposal; enactment unverified) |
| `tag.khar_suld` «Хар Сүлд», `tag.tsagaan_suld` «Цагаан Сүлд» | credits (LEGENDARY) | venerated state banners (HIST-027) |
| `tag.altan_urag` «Алтан Ураг» | credits (LEGENDARY) | the Chinggisid Golden Lineage |
| `tag.tsagaan_sar` «Цагаан Сар» | credits (EPIC) | a national holiday |
| `tag.naadam` «Наадамч» | credits (EPIC) | Naadam was removed from the game |

**Do not re-enable any of these for sale** until:
1. a Mongolian cultural reviewer has signed off;
2. the owner has checked Mongolian law on commercial use of Chinggis Khaan's name (legalinfo.mn);
3. the owner has read the current Minecraft Usage Guidelines.

## 3. Text corrected

| ID | Was | Now | Basis | Still to check |
|---|---|---|---|---|
| HIST-002 | Khödöö Aral: «1206 онд … өргөмжилж, есөн хөлт цагаан туг мандуулсан гэж Монголын нууц товчоонд өгүүлдэг», labelled VERIFIED | «… уламжлалаар их хуралдайтай холбогддог газар.», labelled INSPIRED | Per search snippets, *Secret History* §202 places the 1206 enthronement at the source of the Onon; the project's own `docs/research/history/README.md` agrees. No replacement claim was added | A historian confirms §202 and the colophon wording before any richer text is written |
| HIST-007 | Deer stones «Чингисээс олон мянган жилийн өмнө» | «хүрэл зэвсгийн үед, Чингисээс 2000 орчим жилийн өмнө» | UNESCO serial listing per search snippet: c. 1200–600 BCE | Historian confirmation of the wording |
| LANG-001 | Баатар resource «Хил / Rage» (*хил* = border) | «Уур хилэн / Rage» (HUD word «Уур») | – | Native speaker: «Уур хилэн» vs «Догшин» |
| LANG-002 | «Хүдэлмэрчин NPC эсвэл /class» | «Ангийн Бөөтэй ярилц эсвэл /class» | The NPC is «Ангийн Бөө» | – |
| LANG-003 | «Байгаа түгжих» | «Дайснаа онил (Q)» | – | Native speaker |
| LANG-009 | «Шошго: Түүхэн газар (VERIFIED)» | «Шошго: Түүхэн газар» | – | – |
| – | «Act II «Хөх Сүлдний Зам»» in story text | «II бүлэг «Хөх Сүлдний Зам»» | – | – |

## 4. Not changed in P1: needs a reviewer, a historian or an artist

| ID | Item | Why not changed now |
|---|---|---|
| New names | «Хөгшин Саарал», «Саарлын Агуй», «Хэнтийн Гүн Агуй», «Хадны Мангас», «Хаадын …», «Баатрын Их Уур» | Invented. A native speaker should confirm they read naturally and carry no unintended meaning |
| HIST-003 | Aurag «Чингис хааны их ордон» | The 2020 study says "more likely than not" a base camp. The wording change needs historian sign-off |
| HIST-019 | «Хар Жанжин — Балгасны Эзэн» (the legend's defender turned villain) | Rename or reframe is a story decision |
| HIST-021 | Region names (Отгонтэнгэр over Altai peaks; Хангай features under «Алтай») | **Resolved 2026-10-19** (owner decision): the inner-west region is «Отгонтэнгэрийн Оргил» and the far-west one «Алтай» in `world.json`; ids and discovery bits unchanged; the three inner mobs are «Отгоны …». A historian still reviews the wording |
| Savdag mobs | «Уулын Савдаг» and other spirit-masters fought as regular enemies | Whether any spirit-master may be an enemy is a cultural judgement |
| `tag.tengeriin_khuu` | «Тэнгэрийн Хүү» (credits) | Possible imperial-title connotation; not flagged by the audit; for review |
| LANG-004/005/007/008 | «түмэн» spellings, «Сүлдний/Сүлдийн», rarity ladder, overloaded «Цол/Зэрэг/Түвшин» | Terminology decisions; P2 |
| VIS-001/002/004/005/006/007 | Pagoda-style towers and blue three-prong banners in the painted menus; the generic emblem and ⚜; cherry groves; raw AI cards with Steve and a pumpkin; the unnamed "Khan" statue at spawn | Need an artist or repaint; not code. The menus still show this imagery |
| HIST-009/011/020/028 | Smaller wording nuances (petroglyph dates, örtöö "expanded by Ögedei", Otgontenger worship from 1778, folk etymologies) | Low; historian pass |

## 5. Guard against regressions

`suld-plugin/src/test/java/mn/suld/plugin/content/CulturalNamingTest.java` fails if any of these words appears in a mob, dungeon, quest title, item, set, skill, ladder or ultimate name: «Чингис», «Хасар», «Бурхан Халдун», «Алтан Ураг», «Их Хаан», «Хар Сүлд», «Цагаан Сүлд». It also fails if the six sensitive cosmetics become sellable again. Sites and regions are not scanned; history-labelled place names may keep real names.
