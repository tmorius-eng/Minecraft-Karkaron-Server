# 06: History, language and culture

- **Audited commit:** `main` @ `50d5138`.
- **Scope:** static review, 2026-10-11.
- **Reviewer stance:** Mongolian historical-authenticity editor. This is not a substitute for native-speaker and historian sign-off, which the §5 list at the end still requires.

## Verdict: authentic in research, uneven in content

**The research base is unusually careful.** `docs/research/history/` has:
- source tables;
- a "do NOT use" list (Soyombo, Erdene Zuu, Borjigin tamga, Qing bows, blue-as-imperial);
- A–D-style labels.

**The shipped game respects most of it:**
- No Soyombo, Erdene Zuu, Borjigin tamga or Qing bow appears in code, content or the pack.
- Most «түүхэн баримт» lines are supported: Kharkhorum 1235, Khar Balgas, Khöshöö Tsaidam, the nerge, darkhan, Tengri, the decimal army.
- Ovoo circling is a real, respectful mechanic.

**The identity is more than surface-level** where the research reaches the game:
- the city;
- the site plates;
- the NPC history lines;
- the class names.

**It breaks down where naming, art and monetisation were done quickly:**
- a historical person (Хасар) and the most sacred mountain (Бурхан Халдун) are monsters to farm;
- Chinggis's name is on loot and a paid tag;
- sacred banners are sold as chat tags;
- the shipped menu art carries pagoda towers and blue trident banners;
- the first-session UI has misspelled or wrong words («Хил», «Хүдэлмэрчин», «Байгаа түгжих»).

**One site claim is a false citation:** HIST-002, presented as VERIFIED.

All of these are cheap data or text changes. None requires new systems. They should be done **before** any Mongolian player sees the server (see `10`, P1).

Severity scale used here:
- **High:** the content prominently presents a falsehood as fact, or is likely to be received as disrespectful.
- **Critical:** reserved for content that is clearly offensive or illegal. Nothing reached Critical on the available evidence. Native reviewers may raise HIST-023 to 025.

## Method and evidence rules (read first)

**Static review only.**
- No Minecraft client was available.
- The painted GUI textures were checked by viewing the PNGs in the pack. They were not seen in a running client.

**Network reality.**
- Direct page fetches failed. The exact WebFetch errors were:
  - `https://whc.unesco.org/en/list/1081` → `getaddrinfo ENOTFOUND whc.unesco.org`
  - `https://www.minecraft.net/en-us/usage-guidelines` → `getaddrinfo ENOTFOUND www.minecraft.net`
- No page was visited. Every external fact below comes from the WebSearch tool, cited as "per WebSearch result snippet, <URL>, retrieved 2026-10-11".
- The tool returns a summary that it writes itself from result snippets. A citation therefore means only "the search tool reported this for that URL". It is not a reading of the page, and it is not a page number or section number.
- I give a section number (for example *Secret History* §202) only where the snippet itself named it.

**Claims not checked.** Anything not covered by a search is marked **UNVERIFIED-IN-ENVIRONMENT**, with the source that would settle it.

**Category key** (from the brief):

| Category | Meaning |
|---|---|
| A | Well-supported fact |
| B | Reasonable interpretation, or a fact that needs a nuance |
| C | Folklore or living tradition |
| D | Game fiction |

**Severity key:**
- **Critical** is used only for content that is offensive, or that prominently presents a falsehood as verified fact.
- None of the findings below reaches that bar on the evidence I have.
- HIST-002, HIST-023, HIST-024 and HIST-025 should go to a native cultural reviewer, who may raise them.

---

## Summary table

| ID | Severity | Cat. | One line |
|---|---|---|---|
| HIST-001 | Low | A | NPC: "Ögedei built the walls and palace of Kharkhorum in 1235" (түүхэн баримт) is supported. You could add "founded c. 1220". |
| HIST-002 | **High** | B/C shown as A | Khödöö Aral site credits the *Secret History* with the 1206 enthronement and banner there. The snippet for §202 says "source of the Onon". Shown as «Түүхэн газар (VERIFIED)». |
| HIST-003 | Low | B | Aurag/Avraga "Chinggis's great palace": archaeology says "more likely than not" his base camp (ordu). "Palace" is the heritage-listing wording. |
| HIST-004 | Info | A | Delüün Boldog: text attributes the birthplace to the *Secret History* and calls the location disputed. Correct. |
| HIST-005 | Info | A | Khar Balgas as the Uyghur capital, 8th–9th c. Correct. |
| HIST-006 | Info | A | Khöshöö Tsaidam Turkic inscriptions, 8th c. Correct (732/735). |
| HIST-007 | **Medium** | A, with an error | Deer stones "many thousands of years before Chinggis" (quest, labelled as history). Snippet: c. 1200–600 BCE, about 1,800–2,400 years before him. |
| HIST-008 | Low | A/B | "Chinggis's Wall" was built before Chinggis. Supported; snippet says likely Khitan-Liao, Jin debated. |
| HIST-009 | Low | A | Altai petroglyphs "many thousands of years old" (бодит). Plausible; UNVERIFIED-IN-ENVIRONMENT. |
| HIST-010 | Info | A | Dariganga stone men: text says dating and meaning are debated. Matches the snippet (a Turkic/Mongol-era mix). |
| HIST-011 | Low | B | Örtöö "established under Ögedei" (түүхэн баримт). Better: Ögedei expanded and formalised it; it had precursors under Chinggis. |
| HIST-012 | Info | A | Great hunt (nerge) as military training with strict discipline (түүхэн баримт). Supported. |
| HIST-013 | Info | A | Safe roads and merchants from China to Persia (түүхэн баримт). Supported (secondary). |
| HIST-014 | Low | A | Paiza/gerege line is fine but vague. The relic "khan's-seal golden gerege" is labelled INSPIRED in the docs; OK. |
| HIST-015 | Info | A | Darkhan as a tax-exempt title (түүхэн баримт). Supported. |
| HIST-016 | Info | A/B | Veneration of Möngke Tengri and of earth and water (түүхэн баримт). Supported. |
| HIST-017 | Info | A | Decimal army aravt/zuut/myangat/tümen (бодит түүх). Supported, from 1206. Spelling issue: LANG-004. |
| HIST-018 | Low | A | Tangut/Xi Xia as the southern neighbour (бодит түүх). UNVERIFIED-IN-ENVIRONMENT; uncontroversial. |
| HIST-019 | **Medium** | A + C | Khara-Khoto "real Tangut ruin" is right. But the legend's Black General defended the city against the Ming, and SÜLD makes him a boss to kill. |
| HIST-020 | Low | A/B | Otgontenger as the highest Khangai peak, "anciently worshipped" (бодит). Highest: yes. State worship is documented from 1778/1779. |
| HIST-021 | **Medium** | D vs geography | Region "Отгонтэнгэрийн Оргил" holds Altai peaks, while Otgontenger, the Khangai range and the Tamir sit under region "Алтай". A Khangai savdag lives in a Khentii mountain. |
| HIST-022 | Info | C (living) | Ovoo circled clockwise three times (бодит). Supported. The code really checks the direction. |
| HIST-023 | **High** | C, sensitive | Burkhan Khaldun, a UNESCO sacred landscape: a monster dungeon "Бурхан Халдуны Агуй" whose angered mountain spirit is killed as a boss. |
| HIST-024 | **High** | D using a real name | "Хасар — Агуйн Эзэн": Chinggis's brother's name on a beast boss that players farm. Its heart and fang drop as loot. |
| HIST-025 | **High** | D using a real name | Loot set "Чингисийн Өв" (Chinggis's helmet, deel, trousers, boots, tamga). Store tags "Их Хааны Ач" and "Алтан Ураг". Possible Mongolian legal sensitivity (UNVERIFIED). |
| HIST-026 | Low | B/D | Top rank «Хаан» bought with in-game coins at level 60. It gives a mount-speed perk. Not real money. |
| HIST-027 | Medium | C, sensitive | Sacred banners «Хар Сүлд» and «Цагаан Сүлд», and the holiday «Цагаан Сар», sold as LEGENDARY credit-only (real-money) chat tags. |
| HIST-028 | Low | B/C | "Kherlen is the cradle of the Great Mongol State"; "Altai = Golden mountain". Interpretation and folk etymology, unlabelled. |
| HIST-029 | Low | A | Kharkhorum master plan: throne hall "after Ögedei's 64-column hall". Superseded by the project's own research (the Great Hall was a Buddhist temple). |
| HIST-030 | Low | A/D | README "makes no historical claims" vs the in-game «түүхэн баримт» lines and VERIFIED site plates. Docs contradiction (see `09`). |
| LANG-001 | **High** | – | Баатар's resource "Хил / Rage". *хил* means "border"; rage is *уур / хилэн*. Shown on HUD and class GUI. |
| LANG-002 | Medium | – | Tutorial: "Хүдэлмэрчин NPC" is misspelled (*хөдөлмөрчин*) and names an NPC that does not exist (the class NPC is «Ангийн Бөө»). |
| LANG-003 | Medium | – | Tutorial step "Байгаа түгжих" is ungrammatical. Meant: "Байгаа (дайснаа) түгж" / "Дайсандаа түгжигд". |
| LANG-004 | Medium | – | Four spellings of the tümen unit: Түмэний / Түмэнтийн / түмт- / Түмний. |
| LANG-005 | Low | – | Genitive of сүлд: «Сүлдний» (59 uses) vs «Сүлдийн» (5). |
| LANG-006 | Low | – | «Хөшөө Цайдамын» should probably be «Хөшөө Цайдмын» (vowel elision). |
| LANG-007 | Medium | – | Rarity names: «Ховор» (= rare) is used for Uncommon. «Домогт» (Epic) and «Домгийн» (Mythic) are near-identical. |
| LANG-008 | Medium | – | Overloaded terms. «Цол» is both rank and cosmetic tag. «Зэрэг» is Ascension, item level and tier. «Түвшин» is level and node rank. |
| LANG-009 | Low | – | Mixed Mongolian/English UI. Raw «(VERIFIED)» in chat; «Act II» inside Mongolian story text; bilingual menu titles. |
| LANG-010 | Low | – | First quest: kill "Говийн чоно" (Gobi wolves) on the Kherlen steppe. |
| LANG-011 | Low | – | Latin IDs: gobi/govi, ö→o vs ö→u, English vs Mongolian quest IDs, id/name drift. |
| LANG-012 | Low | – | «Ангийн Бөө» can read as "shaman of the hunt" (ан → ангийн). |
| LANG-013 | Low | – | Unclear cosmetic names (Шороон Хөвөрөл, Талын Дэнгэ, emoji Гавал with id `guul`); «нэрний» vs «нэрийн». |
| LANG-014 | Low | – | «Хойт зүгийн» vs «Хойд» elsewhere; «Пайз гэдэг гэрэгэ» word order. |
| LANG-015 | Low | – | "Мөнх Хөх Тэнгэр", "Хөх Мөнх Тэнгэр" and "Мөнх Тэнгэр" are mixed. |
| VIS-001 | **Medium** | – | Shipped HD GUI art shows pagoda-style multi-eave towers and Chinese palace roofs (welcome, main menu). The project's own guide says to avoid "pagoda stacks". |
| VIS-002 | Medium | – | Blue banners with three-prong finials are the dominant imperial/city banner in the GUI art. The guide says not to imply blue was the imperial colour, and to keep the three-pronged finial out of 13th-c. claims. |
| VIS-003 | Low | – | The source logo still shows the modern Mongolian flag with the Soyombo, and an AI prompt still asks for a "soyombo emblem". Not shipped today. |
| VIS-004 | Medium | – | The shipped emblem (pack icon, server icon, TAB logo) reads as generic baroque fleur-de-lis fantasy. Store tag uses ⚜ (European heraldry). |
| VIS-005 | Medium | – | Cherry-blossom trees in 8 of 10 groves of Kharkhorum slice 1 are a strong Japanese signifier and are not Orkhon flora. |
| VIS-006 | Medium | – | Menu cards are Gemini outputs (tools/art/prompts.json). Guide: "Do not ship raw AI output". Cosmetics cards show vanilla Steve and a Halloween jack-o'-lantern. |
| VIS-007 | Low | – | Unnamed "Khan on a rearing horse" monument at spawn: players will read it as Chinggis. Guide: no real-person likenesses. |

Do-not-use grep (task b): **no Soyombo, Erdene Zuu, Borjigin tamga or Qing bow in code, content or shipped pack.**
- One Soyombo item remains in the art sources and one AI prompt (VIS-003).
- "Blue banner" is present as imagery (VIS-002), not as a text claim.
- The inline grep table is in §3.

---

## 1. In-game lines that present themselves as history

### How labels reach the player (context for severity)

**Historic sites.**
- `HistoricSiteService.java:282-289`: on first visit the player gets:
  - a title;
  - the site description in chat;
  - `Шошго: Түүхэн газар (VERIFIED)`, with the raw English label shown.
- The label text comes from `HistoricSiteService.java:241-247`: VERIFIED → «Түүхэн газар», INSPIRED → «Түүхийг сэдэвлэсэн», other → «Домог».
- The quest menu shows the same label (`QuestMenu.java:239,248`).
- The plate over each site shows it as well (`HistoricSiteService.java:234-235`).

**Areas.**
- `world.json` area `history` labels (35 VERIFIED, 5 INSPIRED) are **not shown** to players.
- The only uses of `.history()` in the plugin are sites and props.
- The labels are data quality, not player-facing claims.
- `Area.java:13` defines VERIFIED as "a real place in that direction from Kharkhorum".

**NPC lines and quests.**
- NPC «түүхэн баримт» lines appear in Paper dialogs (`NpcDialogue.java`).
- Quest «бодит / бодит түүх» lines appear in the quest book and on chapter start.

### HIST-001 — Kharkhorum founding

| Field | Content |
|---|---|
| Wording | «Хархорум Орхоны хөндийд байдаг. Өгэдэй хаан 1235 онд энд хэрэм, ордноо бариулжээ (түүхэн баримт).» — `suld-plugin/src/main/java/mn/suld/plugin/npc/NpcDialogue.java:51` |
| Category | A |
| Severity / confidence | Low / high |
| Evidence | Per WebSearch result snippet, https://en.wikipedia.org/wiki/Karakorum, retrieved 2026-10-11: until 1235 the place was "little more than a yurt town"; after the defeat of the Jin, Ögedei "erect[ed] city walls and buil[t] a fixed palace"; founding "usually said to have occurred only in 1220"; capital 1235–1260. Same direction per WebSearch result snippet, https://www.worldhistory.org/Ogedei_Khan/.md, retrieved 2026-10-11 ("ordering the building of a walled capital in 1235"). Orkhon Valley Cultural Landscape inscribed in 2004, criteria ii/iii/iv, 121,967 ha, includes Kharkhorum and Khar Balgas: per WebSearch result snippet, https://en.wikipedia.org/wiki/Orkhon_Valley and https://www.worldheritagesite.org/list/id/1081, retrieved 2026-10-11. |
| Assessment | Correct and well chosen. Line 53 rightly says SÜLD's city is a fictional reconstruction. |
| Recommendation | Optional: «…1220 онд Чингис хаан энд цэргээ төвлөрүүлэх бааз байгуулсан гэдэг; 1235 онд Өгэдэй хаан хэрэм, ордон бариулжээ.» Re-verify against the UNESCO 1081 page before public use. |
| Effort | XS |

### HIST-002 — Khödöö Aral and 1206 (High)

| Field | Content |
|---|---|
| Wording | «Хэрлэн, Цэнхэрийн бэлчирийн Хөдөө Арал — их хуралдай болдог байсан газар. 1206 онд Тэмүжинийг Чингис хаан хэмээн өргөмжилж, есөн хөлт цагаан туг мандуулсан гэж Монголын нууц товчоонд өгүүлдэг.» — `suld-plugin/src/main/resources/content/sites.json:23`. The site is labelled `"history": "VERIFIED"` (line 22), so the player sees «Түүхэн газар (VERIFIED)». The area description «…их хуралдайн газар» is at `content/world.json:244`. |
| Category | The text says A ("the *Secret History* says"). The evidence supports B/C at best (a modern tradition linking Khödöö Aral to 1206). |
| Severity / confidence | High / medium-high |
| Evidence | (1) Per WebSearch result snippet, https://en.wikipedia.org/wiki/Kurultai, retrieved 2026-10-11, quoting a translation: "The year of the tiger (1206) they all gathered at the source of the Onan river. They hoisted the white standard with nine tails…". (2) Per WebSearch result snippet, https://dsr.nii.ac.jp/toyobunko/III-2-F-c-104/V-2/page/0265.html.en, retrieved 2026-10-11: §202 describes the "white standard with nine tails" (yäsün költü čaqa'an tuq). (3) Per WebSearch result snippet, https://library.schlagergroup.com/chapter/9781961844247-book-part-019, retrieved 2026-10-11: "at the source of the Onan river a white banner with nine pendants… (1206)". (4) Khödöö Aral is named in the colophon as the place the book was finished: per WebSearch result snippet, https://en.wikipedia.org/wiki/Secret_History_of_the_Mongols, retrieved 2026-10-11 ("finished in the Year of the Mouse, on the banks of the Kherlen River at Khodoe Aral"). See also https://toyo-bunko.repo.nii.ac.jp/records/5537, retrieved 2026-10-11, which reads the Sequel's colophon as Köde'e Aral, after a Great Quriltai. (5) The project's own research agrees with the snippets: `docs/research/history/README.md` ("1206 white banner … raised at the Onon source, 1206 (*Secret History* §202)") and `docs/research/history/SYMBOLS_AND_SCRIPTS.md:12`. |
| Assessment | The line credits the *Secret History* with something it does not say. As the snippets report it, the source puts the 1206 enthronement at the source of the Onon. It ties Khödöö Aral to the later colophon and quriltai. The modern association of Khödöö Aral with 1206 is a tradition or interpretation. It is shown on a discovery title with the VERIFIED label. |
| Recommendation | Rewrite: «Хөдөө Арал — Хэрлэн, Цэнхэрийн бэлчир. Монголын нууц товчоог энд, хулгана жил их хуралдайн үеэр бичиж дуусгасан гэж төгсгөлд нь өгүүлдэг. 1206 оны их хуралдай Онон голын эхэнд болж, есөн хөлт цагаан туг мандуулсан гэж нууц товчоо өгүүлдэг; энд буй тахилын тавцан нь тоглоомын хувилбар.» Alternative: keep the 1206 scene here, but relabel the site INSPIRED and drop "нууц товчоонд өгүүлдэг". Have a historian confirm the colophon wording. |
| Effort | S |

### HIST-003 — Aurag palace

| Field | Content |
|---|---|
| Wording | «Хэрлэн голын хөндийд Чингис хааны их ордон (Аураг) байсан гэж археологичид үздэг газар.» — `content/sites.json:13` (VERIFIED) |
| Category | B |
| Severity / confidence | Low / medium |
| Evidence | Per WebSearch result snippet, https://earthsciences.anu.edu.au/news-events/news/researchers-confirm-site-genghis-khans-winter-home, retrieved 2026-10-11: a 2020 ANU and Mongolian Institute of Archaeology study argued Avraga was the khan's winter base camp (ordu). Lead archaeologist: "no conclusive link", "more likely than not". Li Narangoa: at least four ordu. Per WebSearch result snippet, https://www.worldheritagesite.org/tentative/id/5952, retrieved 2026-10-11: the tentative-list entry names the "ruins of the city of Aurag with the palace of Genghis Khan". Per https://www.heritagedaily.com/2020/07/study-suggests-avraga-was-genghis-khans-winter-home/134119, retrieved 2026-10-11, the site is at the Kherlen–Tsenker confluence. |
| Assessment | The hedge "археологичид үздэг" is fair. "Их ордон" overstates what the 2020 study says. |
| Recommendation | «…Чингис хааны өвөлжөө ордны (орд/өргөө) нэг байсан байх магадлалтай гэж археологичид үздэг газар.» |
| Effort | XS |

### HIST-004 to HIST-006 — Delüün Boldog, Khar Balgas, Khöshöö Tsaidam (no change needed)

**HIST-004, Delüün Boldog**
- Wording: «Тэмүжин Онон голын Дэлүүн Болдогт төрсөн гэж нууц товчоонд өгүүлдэг. Яг аль газар болох нь маргаантай…» (`sites.json:43`, INSPIRED).
- Category A. Correct.
- Per WebSearch result snippet, https://en.wikipedia.org/wiki/Del%C3%BC%C3%BCn_Boldog, retrieved 2026-10-11: the *Secret History* places the birth at Delüün Boldog on the Onon. The location is disputed (Dadal, Khentii, or Agin-Buryat, Russia).

**HIST-005, Khar Balgas**
- Wording: «…Уйгурын хаант улсын нийслэлийн (VIII–IX зуун) балгас» (`sites.json:53`).
- Category A.
- Per WebSearch result snippet, https://en.wikipedia.org/wiki/Ordu-Baliq, retrieved 2026-10-11: capital of the Uyghur Khaganate, founded 744 or 751; one tourism source gives 840 for its fall to the Yenisei Kyrgyz.
- The description sentence «Шавар дэлдэж барьсан хэрэм» (rammed earth) is UNVERIFIED-IN-ENVIRONMENT. An excavation report would settle it.

**HIST-006, Khöshöö Tsaidam**
- Wording: «Орхоны хөндийд Түрэгийн үеийн (VIII зуун) бичээстэй гэрэлт хөшөөнүүд байдаг» (`sites.json:63`).
- Category A.
- Per WebSearch result snippet, https://en.wikipedia.org/wiki/Orkhon_inscriptions, retrieved 2026-10-11: monuments erected between 732 and 735 for Kül Tigin and Bilge Qaghan, in Chinese and Old Turkic.
- Optional enrichment: «Күлтегин (732), Билгэ хаан (735)».
- The text correctly says the in-game stele copies no inscription.

### HIST-007 — Deer stones "many thousands of years before Chinggis" (Medium)

| Field | Content |
|---|---|
| Wording | «…Эдгээр хөшөөд Чингисээс олон мянган жилийн өмнө босгогдсон.» — `content/quests.json:231` (Act II opener, quest `quest.uushig`). Site text «хүрэл зэвсгийн үеийн буган хөшөөний цогцолбор… хиргисүүрийн чулуун хүрээ тойрдог» — `sites.json:73` (VERIFIED). |
| Category | A for the site text. The quest line has a factual error. |
| Severity / confidence | Medium / high |
| Evidence | Per WebSearch result snippet, https://whc.unesco.org/en/list/1621/lother=es, retrieved 2026-10-11: deer-stone monuments including Uushigiin Övör date "from approximately 1200 to 600 BCE"; serial listing in 2014. Per https://the-past.com/feature/conjuring-mongolian-deer-stones-biographical-statuary-of-bronze-age-central-asia-and-south-siberia, retrieved 2026-10-11: "c.1300-700 BC". With Temüjin born c. 1155–1167 (per the Delüün Boldog snippet above), the gap is about 1,800–2,400 years. |
| Assessment | "Олон мянган жил" (many thousands of years) roughly doubles the real gap. Players who know the site will notice. |
| Recommendation | «Эдгээр хөшөөд Чингисээс 2000 орчим жилийн өмнө, хүрэл зэвсгийн сүүл үед босгогдсон.» |
| Effort | XS |

### HIST-008 — Chinggis's Wall

| Field | Content |
|---|---|
| Wording | «Ард түмэн «Чингисийн хэрэм» гэж нэрлэдэг ч Чингис хаанаас өмнөх үед баригдсан гэж судлаачид үздэг.» — `sites.json:93` (VERIFIED) |
| Category | A/B |
| Severity / confidence | Low / high |
| Evidence | Per WebSearch result snippet, https://thewall.huji.ac.il/node/3136711 and https://www.afhu.org/2020/06/09/new-section-of-the-great-wall-of-china-mapped-for-first-time/, retrieved 2026-10-11: the "Northern Line" (about 740 km), sometimes called the "Great Wall of Chinggis Khan". A 2020 Hebrew University and Yale study says it was "likely built in a single, organized phase, likely during the Khitan-Liao Empire (907-1125)". A Jin attribution (Wang Guowei) is debated. |
| Assessment | Correct and well hedged. |
| Recommendation | Optional: «…Хятан (Ляо) улсын үед (X–XII зуун) баригдсан байх магадлалтай гэж судлаачид үздэг; Алтан (Жин) улсынх гэх үзэл ч бий.» |
| Effort | XS |

### HIST-009 — Altai petroglyphs

| Field | Content |
|---|---|
| Wording | «Монгол Алтайд олон мянган жилийн өмнөх хадны сийлмэл зургийн цогцолборууд бий.» — `sites.json:103`. «Монгол Алтайн хадны зургийг олон мянган жилийн өмнө сийлжээ (бодит).» — `quests.json:459` |
| Category | A (likely) |
| Severity / confidence | Low / medium |
| Evidence | UNVERIFIED-IN-ENVIRONMENT (not searched). The UNESCO WHC entry "Petroglyphic Complexes of the Mongolian Altai" would settle the date range. |
| Recommendation | Keep. Verify the date span before public lore text. |
| Effort | XS |

### HIST-010 — Dariganga stone men

| Field | Content |
|---|---|
| Wording | «Дарьганга нутагт эртний хүн чулуун хөшөөд олноор бий. Тэдгээрийн үе, утгыг судлаачид янз бүрээр тайлбарладаг.» — `sites.json:83` |
| Category | A |
| Severity / confidence | Info / medium |
| Evidence | Per WebSearch result snippet, https://www.lonelyplanet.com/mongolia/eastern-mongolia/dariganga/attractions/toroi-bandi-statue/a/poi-sig/1406033/357034 and https://tourmongolia.com/?p=8470, retrieved 2026-10-11: a mix of Turkic-era balbals and stones a tourism source dates to the 13th–14th c. The tool flagged that source as informal. |
| Assessment | The hedge is exactly right. |
| Effort | – |

### HIST-011 — Örtöö relay "established under Ögedei"

| Field | Content |
|---|---|
| Wording | «Өгэдэй хааны үед өртөөний сүлжээ байгуулагдаж, элч нар өртөө бүрт морио сольж хурдан давхидаг байжээ (түүхэн баримт).» — `NpcDialogue.java:79`. Also «Их Монгол улсын өртөөний (ям) систем элч, мэдээг морь сольж хурдан хүргэдэг байв (бодит түүх).» — `sites.json:133`; and `quests.json:75`. |
| Category | B |
| Severity / confidence | Low / medium |
| Evidence | Per WebSearch result snippet, https://en.wikipedia.org/wiki/Yam_(route), retrieved 2026-10-11: "extensively used and expanded by Ögedei". The *Secret History* is cited as crediting him with founding it, while "a rudimentary postal system had already existed during the reign of Genghis Khan". Per https://avesis.ktu.edu.tr/yayin/6081e7f4-9f1b-4eaa-8165-ecbbddabb2a1/postal-organisation-yam-in-the-golden-horde-pochtovaya-organizatsiya-yam-v-zolotoi-orde, retrieved 2026-10-11: full development "only with the postal reforms of Ögedei". |
| Assessment | Defensible, since it follows the *Secret History*'s own attribution. Stated flatly as «түүхэн баримт», it hides the precursor. |
| Recommendation | «Чингис хааны үеэс эхтэй өртөөний сүлжээг Өгэдэй хаан өргөжүүлэн цэгцэлж…» |
| Effort | XS |

### HIST-012 to HIST-016 — other NPC «түүхэн баримт» lines (supported)

**HIST-012, the great hunt**
- Wording: «Эрт цагт их ан буюу ав хомрого нь цэргийн сургууль байсан…» (`NpcDialogue.java:61`) and «Ангийн дэг журам хатуу байсан…» (`:62`).
- Category A.
- Per WebSearch result snippet, https://worldhistory.org/article/1458, retrieved 2026-10-11: the nerge was "an army training exercise"; under Chinggis there were "severe punishments for commanders… who let animals escape".
- Per https://cyberleninka.ru/article/n/mongol-warfare-in-the-pre-dissolution-period, retrieved 2026-10-11: other Inner Asian peoples also used hunting as training.
- "Олон зуун хүн" understates it: sources describe the whole army. Optional change: «олон мянган цэрэг».

**HIST-013, safe roads**
- Wording: «Эзэнт гүрний үед зам харгуй аюулгүй болж, худалдаачид Хятадаас Персийн нутаг хүртэл аялдаг байжээ» (`NpcDialogue.java:67`).
- Category A.
- Per WebSearch result snippet, https://en.wikipedia.org/wiki/Pax_Mongolica, retrieved 2026-10-11: routes became "safe enough for growth in trade from China to Britain".

**HIST-014, paiza**
- Wording: «Пайз гэдэг гэрэгэ зам дагуух эрх, хамгаалалтыг баталдаг байсан.» (`NpcDialogue.java:68`, unlabelled). Relic lore «Хааны тамгатай алтан гэрэгэ…» (`suld-api/src/main/resources/items/relics.json:55`).
- Category A.
- Per WebSearch result snippet, https://projecthimalayanart.rubinmuseum.org/essays/mongol-messengers-badge-paiza-or-gerege-in-pakpa-script and https://en.wikipedia.org/wiki/Paiza, retrieved 2026-10-11: a tablet carried by officials and envoys giving power to requisition (horses, food, lodging at yam stations). *Gerege* is roughly "that which bears witness". Ögedei banned nobles from issuing paizas.
- Suggested wording: «Гэрэгэ буюу пайз нь элч, түшмэлд өртөөнөөс морь, хүнс, хоноглох эрх олгодог байв.»

**HIST-015, darkhan**
- Wording: «Дархан гэдэг нь урлаач гэсэн утгаас гадна татвар, албанаас чөлөөлөгдсөн хүний цол байсан» (`NpcDialogue.java:73`).
- Category A.
- Per WebSearch result snippet, https://en.wikipedia.org/wiki/Darkhan and https://ojs3.caucasushistory.ru/2618-6772/article/view/14044, retrieved 2026-10-11: the title (darkhan/tarkhan) came mainly to mean tax immunity, and was hereditary in the Mongol Empire. Juvayni is cited for the related privileges (booty share, nine pardons).

**HIST-016, Tengri**
- Wording: «Эртний монголчууд Мөнх Хөх Тэнгэрийг дээдэлж, газар, уснаа шүтэж байжээ» (`NpcDialogue.java:85`).
- Category A/B.
- Per WebSearch result snippet, https://www.worldhistory.org/article/1469/religion-in-the-mongol-empire/ and https://en.wikipedia.org/wiki/Etugen_Eke, retrieved 2026-10-11: sky deity (Möngke / Köke Möngke Tngri), earth goddess Etügen, and spirits of fire, earth and water.
- Line 86 correctly labels the Хөх Сүлд legend as SÜLD's story.

### HIST-017 — Decimal army

| Field | Content |
|---|---|
| Wording | «Манай цэрэг аравт, зуут, мянгатаар зохион байгуулагддаг.» — `quests.json:123`. «Монголын цэрэг аравт, зуут, мянгат, түмтээр зохион байгуулагддаг байв (бодит түүх).» — `quests.json:279`. Rank ladder `suld-api/src/main/java/mn/suld/api/style/Rank.java:14-21`. |
| Category | A |
| Severity / confidence | Info / high |
| Evidence | Per WebSearch result snippet, https://en.wikipedia.org/wiki/Mingghan and https://www.medievalists.net/2022/02/reintroduction-mongols/, retrieved 2026-10-11: reorganised in 1206 into arban, jaghun, mingghan and tümen. The mingghan was 1,000 *households*. The Xiongnu used a similar system. |
| Recommendation | Optional nuance: «…мянган өрх…». Spelling is covered in LANG-004. |

### HIST-018 — Tangut / Xi Xia

| Field | Content |
|---|---|
| Wording | «Тангуд (Си Ся) улс Монголын өмнөд хөрш байв (бодит түүх).» — `quests.json:291` |
| Category | A |
| Severity / confidence | Low / high |
| Evidence | UNVERIFIED-IN-ENVIRONMENT, but uncontroversial. Any standard history of Western Xia (1038–1227) settles it. |

### HIST-019 — Khara-Khoto and the Black General (Medium)

| Field | Content |
|---|---|
| Wording | «Хар Хот бол Тангудын бодит балгас хот. Хар Жанжингийн домогоос сэдэвлэсэн энэ шоронд ялсан хүнд…» — `quests.json:339`. Boss «Хар Жанжин — Балгасны Эзэн» — `content/mobs.json:251`; dungeon «Хар Хотын Балгас» — `content/dungeons.json:136`. |
| Category | A (city) plus C (legend) used as D |
| Severity / confidence | Medium / medium |
| Evidence | Per WebSearch result snippet, https://www.heritagedaily.com/2021/05/khara-khoto-the-black-city/138942 and https://www.amusingplanet.com/2022/06/the-ruins-of-khara-khoto.html, retrieved 2026-10-11: built 1032, thrived under the Tangut Western Xia, in Ejin Banner, Inner Mongolia. The siege legend: Ming forces cut the river, and the defending general "Khara Bator" either killed his family and himself, or escaped through a breach in the north-west wall. A Ming annal records the 1372 campaign and the surrender of the defender Buyan-temür. |
| Assessment | The city fact is fine. In the legend the Black General is the city's tragic defender against the Ming, i.e. on the post-Yuan Mongol side. SÜLD turns him into a villain boss that players kill and farm. The site is also in Inner Mongolia (PRC), not in a Mongolian aimag. That is fine as compressed geography (labelled INSPIRED), but worth knowing for Mongolian players. |
| Recommendation | Keep the place. Rename the boss to an invented spirit, e.g. «Балгасны Хар Сүнс», and present the general as a tragic ghost whose spirit the player lays to rest. That fits the quest verb «тайвшруул» already used at `quests.json:315`. |
| Effort | S |

### HIST-020 — Otgontenger

| Field | Content |
|---|---|
| Wording | «Отгонтэнгэр бол Хангайн хамгийн өндөр оргил, эртнээс тахидаг уул (бодит).» — `quests.json:447`; area «Хангайн хамгийн өндөр цаст оргил» — `world.json:514` |
| Category | A (highest) / B ("anciently worshipped") |
| Severity / confidence | Low / medium |
| Evidence | Per WebSearch result snippet, https://whc.unesco.org/en/tentativelists/6068 and https://www.summitpost.org/otgon-tenger-uul/151248, retrieved 2026-10-11: the highest peak in the Khangai. Elevation varies by source (4,008 / 4,021 / 4,031 / 3,905 m). State worship was declared in 1778, and the Qianlong decree of 1779 began Ochirvaani worship. One source says worship goes back to "Khunnu times". |
| Recommendation | «…төрийн тахилгат уул (1778 оноос)». Avoid giving an exact height. |
| Effort | XS |

### HIST-021 — Region geography transposed (Medium)

| Field | Content |
|---|---|
| Wording | Region `region.otgon` is named «Отгонтэнгэрийн Оргил» (`world.json:132-134`), but its areas are the Altai ones: Говь-Алтай, Хар Ус Нуур, Мөнххайрхан, Таван Богд. Meanwhile `region.altai` («Алтай», `world.json:66`) holds Otgontenger (`:508-516`), «Хангайн Нуруу» (`:484`) and «Хойд, Өмнөд Тамирын бэлчир» (`:469`), which are all Khangai features. The Burkhan Khaldun dungeon (Khentii) has the boss «Хангай Савдаг — Уулын Эзэн» (`mobs.json:269`; `dungeons.json:188-198`). |
| Category | D (map compression), but it reads as an error |
| Severity / confidence | Medium / high (verified in the data) |
| Assessment | Mongolian players know these mountains. A sign that says "Altai" over the Khangai and Tamir, and "Otgontenger" over Tavan Bogd, will read as a mistake, not as compression. |
| Recommendation | Rename `region.otgon` to «Өндөр Алтай». Rename `region.altai` to «Хангай–Алтай» or split it. Rename the Burkhan boss «Хэнтий Савдаг» or «Уулын Савдаг». |
| Effort | S (data only) |

### HIST-022 — Ovoo circumambulation

| Field | Content |
|---|---|
| Wording | Toast «Овоо — нар зөв (цагийн зүүний дагуу) гурвантаа тойрвол Тэнгэр ивээнэ» — `suld-plugin/src/main/java/mn/suld/plugin/region/OvooService.java:192`; «Овоог гурав тойрох заншил бодит.» — `quests.json:495`; `sites.json:33` |
| Category | C (living tradition), correctly called real |
| Severity / confidence | Info / high |
| Evidence | Per WebSearch result snippet, https://en.wikipedia.org/wiki/Ovoo and https://www.asiaculturaltravel.co.uk/the-mongolian-ovoo/, retrieved 2026-10-11: travellers "circle it three times clockwise", add stones and leave offerings. Banned under communism, revived since 1990. Practice varies by region. |
| Assessment | Respectful, and the mechanic checks clockwise travel (`OvooCircleTest`). Turning a devotional act into "+5 % EXP for 30 min" is mild gamification. Acceptable, but a native reviewer should confirm the tone. |
| Recommendation | Optional: have the toast mention adding a stone; never let players break ovoo (already protected per `docs/world/HISTORIC_SITES.md`). |

### HIST-023 — Burkhan Khaldun as a monster dungeon (High, sensitive)

| Field | Content |
|---|---|
| Wording | Dungeon «Бурхан Халдуны Агуй» (`dungeons.json:188`, min level 48, theme `SACRED`). Quest «Савдаг бол уул усны эзэн гэж ардын итгэл үнэмшилд үздэг. SÜLD-д тэд Бурхан Халдуны агуйгаас гарч ирсэн уурсан сүнснүүд.» (`quests.json:423`). «Ариун уулын агуйд Хангай Савдаг сэржээ. Түүнийг тайвшруулсан хүнд…» (`quests.json:435`). Boss `mob.khangai_savdag` is fought and killed. |
| Category | C (sacred) used as D |
| Severity / confidence | High / medium (a judgement call, so a native reviewer should decide) |
| Evidence | Per WebSearch result snippet, https://whc.unesco.org/ja/list/1440 and https://whc.unesco.org/document/152684, retrieved 2026-10-11: "Great Burkhan Khaldun Mountain and its surrounding sacred landscape" was inscribed on 4 July 2015, tied to worship of sacred mountains, rivers and ovoo. A 2013 ministerial order regulates travelling and practising customs there. Burkhan Khaldun was also among the mountains declared state-worshipped in 1778, per the Otgontenger snippet above (https://whc.unesco.org/en/tentativelists/6068). |
| Assessment | This is the most sacred mountain in Mongolian tradition, closely tied to Chinggis. Making its master spirit a hostile boss that players kill and farm for loot is the kind of thing Mongolian players and press may object to. The text says "pacify", but the gameplay is slaying. |
| Recommendation | Rename the dungeon to an invented place, e.g. «Хар Хадны Агуй» under the Khentii range. Keep Burkhan Khaldun only as the respectful great-ovoo site (already non-combat). If the setting stays, the boss should be an invader corrupting the mountain, and the savdag should be freed, not killed. |
| Effort | S–M |

### HIST-024 — "Хасар" as a beast boss (High)

| Field | Content |
|---|---|
| Wording | Boss «Хасар — Агуйн Эзэн» (`mobs.json:25`). Dungeon «Хасарын Агуй» (`dungeons.json:7`), the first dungeon and a tutorial target (`suld-api/.../onboarding/TutorialProgress.java:22`, `NpcDialogue.java:57`). Quest story «Хасарын Агуй хэмээн домоглодог хадан агуйд араатан эзэрхэж…» (`quests.json:63`). Loot: «Хасарын Соёо» "blade from Khasar's fang" (`suld-api/src/main/resources/items/weapons.json:68,95`) and «Хасарын Зүрх» (Khasar's Heart) (`items/jewelry.json:25`). |
| Category | D, using a famous historical name |
| Severity / confidence | High / medium-high |
| Evidence | Per WebSearch result snippet, https://en.wikipedia.org/wiki/Qasar, retrieved 2026-10-11: Qasar (Hasar/Khasar) was Chinggis Khan's full brother and a renowned archer ("Хавт Хасар"). He commanded the Left Wing. Per https://bigthink.com/?p=422109, retrieved 2026-10-11, he was famous for archery. Internally, the asset registry says "an ancient cave wolf-beast, not the historical Qasar" (`assets/registry.json:2356`) and `docs/bosses/KHASAR.md` says "ORIGINAL FICTION". Players never see that disclaimer. |
| Assessment | Every Mongolian player's first dungeon has them kill, and repeatedly farm, "Khasar", and wear his heart and fang. The quest also invents a "legend" («хэмээн домоглодог»). Many players will take this as mocking a national historical figure. |
| Recommendation | Rename the boss before the alpha. Options: «Хөгшин Саарал» (an old grey wolf), «Агуйн Хар Соёот», «Шар Шүдэн». Rename the dungeon «Хадан Агуй» / «Чонын Агуй». Item renames follow. IDs can stay (`mob.khasar`) if a migration is costly; the visible name is what matters. |
| Effort | S (display names) |

### HIST-025 — Chinggis items and lineage titles (High)

| Field | Content |
|---|---|
| Wording | Legendary loot «Чингисийн Малгай / Дээл / Өмд / Гутал» (`suld-api/src/main/resources/items/armor.json:525,551,577,603`), lore «Их хааны өв — тав бүрдвэл тэнгэр дэмжинэ». Amulet «Чингисийн Тамга», lore «Их хааны тамга» (`items/jewelry.json:175,195`). Set «Чингисийн Өв» (`items/sets.json:5`). Real-money store tag «⚜ Их Хааны Ач ⚜» ("Grandson of the Great Khan", id `tag.chingis`) (`suld-api/src/main/java/mn/suld/api/style/CosmeticCatalog.java:74`). Coin tag «Алтан Ураг» (the Golden Lineage) (`:68`). |
| Category | D, presented with a real person's name |
| Severity / confidence | High / medium |
| Evidence | The project's own style guide says item names that suggest a real emblem, "for example `jewel.chingis_tamga`", must use an original design labelled ORIGINAL FICTION (`docs/ASSET_STYLE_GUIDE.md:136-137`). Also: "Do not use real-person likenesses" (`:142`), and a "Borjigin tamga" shape is unverified (`docs/research/history/SYMBOLS_AND_SCRIPTS.md:66`). Legal context: per WebSearch result snippet, https://www.aljazeera.com/news/2006/10/5/genghis-khan-to-get-respect and https://www.historynewsnetwork.org/article/30563, retrieved 2026-10-11, Mongolia proposed in 2006 to license commercial use of the name and ban "degrading or insulting" uses. Whether it was enacted is **UNVERIFIED-IN-ENVIRONMENT**; the State Great Khural legal database (legalinfo.mn) would settle it. |
| Assessment | Dropping "Chinggis's trousers" from a mob and selling "the Great Khan's grandson" for real money is likely to draw criticism in Mongolia, and may carry legal risk. |
| Recommendation | Rename the set to an invented hero or to «Их Хааны Харуулын Өв» (Khan's guard), or «Хаадын Өв». Rename the amulet «Хааны Тамга (зохиомол)» and give it an original design. Retire «Их Хааны Ач». Have a Mongolian lawyer check whether name-use rules apply to a commercial game server. |
| Effort | S |

### HIST-026 — The «Хаан» rank

| Field | Content |
|---|---|
| Wording | `Rank.java:21` `KHAAN("khaan", "Хаан", …, 60, 35_000, "Мөнх тэнгэрийн хүчинд — хаан ширээ.")`, bought with 35,000 in-game coins at level 60 (`Rank.canRankUp`, `:68-74`) |
| Category | B/D |
| Severity / confidence | Low / high |
| Assessment | Not purchasable with real money: ranks use `profile.currency()` coins (`StyleService.java:350`). The rank does give gameplay value: mount speed `0.24 + rank.ordinal()·0.013` (`suld-plugin/.../mount/*.java:65-67`). Many players holding the title «Хаан» dilutes it, but it is common in games. «Мөнх тэнгэрийн хүчинд» echoes the imperial edict formula. |
| Recommendation | Keep. Consider «Хан» / «Их Ноён» as the top, and keep «Хаан» for a single seasonal title. Never sell ranks for credits (see `09`, monetisation). |

### HIST-027 — Sacred banners and holidays as paid tags (Medium)

| Field | Content |
|---|---|
| Wording | `CosmeticCatalog.java:62-67`: «Наадамч», «Цагаан Сар», «Хар Сүлд», «Цагаан Сүлд». With `cosmetics.coin-max-rarity: COMMON` (`config.yml:297`), the EPIC and LEGENDARY ones are credit-only, i.e. real money (commit `70a1fdc`). |
| Category | C used as D |
| Severity / confidence | Medium / medium |
| Evidence | The project's research says the black banner is "linked to war and to the spirit of the ruler" and the white banners to state ceremony (`docs/research/history/SYMBOLS_AND_SCRIPTS.md:14`). |
| Assessment | Selling «Хар Сүлд» (the venerated war banner) as a paid chat tag can read as commodifying a sacred state symbol. |
| Recommendation | Make these earnable only (an event or achievement), or rename them («Хар Тугт», «Цагаан Тугт»). Remove «Наадамч», since Naadam was removed (`09`). |
| Effort | XS |

### HIST-028 — Unlabelled interpretation and folk etymology

**Kherlen as cradle**
- Wording: «Хэрлэн гол бол Их Монгол Улсын өлгий нутаг.» (`quests.json:15`).
- Category B. A common framing (Onon–Kherlen–Tuul headwaters).

**Altai as "Golden mountain"**
- Wording: «Алтай бол «Алтан уул».» (`quests.json:183`).
- Category C, a folk etymology. UNVERIFIED-IN-ENVIRONMENT; an etymological dictionary would settle it.
- Recommendation: «…гэж ардын тайлбарт гардаг».

**Orkhon source**
- Wording: «Хангайн нуруунаас Орхон гол эх авдаг…» (`quests.json:135`).
- Category A.

### HIST-029 — Master plan uses a superseded palace claim

- `docs/world/KHARKHORUM_MASTER_PLAN.md`, district table row 1: "throne hall (8 × 8 columns, after Ögedei's 64-column hall)".
- The project's research says the Great Hall was reinterpreted as a Buddhist temple. It lists "The 64-column hall was the khan's palace" under **Do NOT use** (`docs/research/history/README.md`).
- `KHARKHORUM_RESEARCH_BRIEF.md:18-19` still states the 64-column palace.
- Docs only, so Low.
- Recommendation: "8 × 8 grid inspired by the Great Hall (a Buddhist temple)".

### HIST-030 — "No historical claims" disclaimer vs in-game «түүхэн баримт»

**The claims in the docs**
- `README.md:14-15`: "SULD keeps fantasy lore separate from real history and makes no historical claims".
- `GAME_DESIGN.md:3-7` is more accurate: it separates fantasy lore from historical notes.

**What the game actually does**
- The game makes at least 6 «түүхэн баримт» NPC claims.
- 6 quest «бодит / бодит түүх» claims (`quests.json:279,291,339,447,459,495`).
- 10 VERIFIED site plates.

**Fix:** make README match GAME_DESIGN, e.g. "History shown in game is labelled; everything else is fiction".

---

## 2. Language: Mongolian Cyrillic and transliteration

### 2.1 Sample checked (61 player-facing strings)

**Correct or acceptable unless flagged:**
- Classes (5): Баатар, Мэргэн, Бөө, Дархан, Хүлэгчин.
- Ranks (8): Ард, Цэрэг, Аравтын дарга, Зуутын дарга, Мянгатын ноён, **Түмэний ноён (LANG-004)**, Их ноён, Хаан.
- Rarities (8): Энгийн, **Ховор (LANG-007)**, Нандин, **Домогт / Домгийн (LANG-007)**, Алдарт, Эртний, Цор ганц.
- Resources (5): **Хил (LANG-001)**, Төвлөрөл, Сүнс, Дөл, Хурд.
- Spells (10 of 25): Тэнгэрийн Цавчилт, Дайны Хашгираан, Чонын Нүд, Олон Сум, Онгоны Дуудлага, Хэнгэргийн Дуу, Ган Бамбай, Дарханы Дөш, Хүлгийн Дайралт, Хийморийн Давхилт.
- Menu titles (10): Сүлд Цэс, Ур чадвар · Skills, Дэлгүүр · Shop, Гоёл · Cosmetics, Агуйн Шат, Тэнгэрийн Зэрэг, Өртөө · Хурдан аялал, Хуяг дуулга, Цол · Rank, Кредит дэлгүүр · Store.
- Quest titles (8): Анхны Ан, Хэрлэнгийн Дээрэмчид, Мянгатын Сорил, **Түмэнтийн Тангараг (LANG-004)**, Буган Хөшөөдийн Дуудлага, Хар Хотын Балгас, Отгонтэнгэрийн Оргил, Ариун Уулын Овоо.
- Tutorial and NPC (5): Ангиа сонго, **Байгаа түгжих (LANG-003)**, **Хүдэлмэрчин NPC (LANG-002)**, **Ангийн Бөө (LANG-012)**, Өртөөчин.
- Tips (2): «Үхвэл юмныхаа хагасыг алдана» (wording fine, but factually stale, see `09`), «5-р түвшнээс өөрийн Монгол морьтой».

**Result:** 12 of 61 have issues, about 20 %.
- Spelling and grammar: 4.
- Terminology inconsistency: 5.
- Unclear words: 3.

Painted GUI labels (ДҮР, АНГИ, ЭРЭЛ, ЦОЛ, ДЭЛГҮҮР, ЗААВАР, Сүлд Цэс) were inspected at 4x zoom. Ү/ү are drawn correctly. No typo was found in the painted labels.

### 2.2 LANG findings

| ID | Severity / conf. | Wording + file:line | Issue | Correction | Effort |
|---|---|---|---|---|---|
| LANG-001 | **High** / high | `BAATAR(… "Хил / Rage" …)` — `suld-api/src/main/java/mn/suld/api/clazz/PlayerClass.java:21`; shown at `HudPanel.java:554` (first word «Хил»), `ClassSelectionGui.java:99`, `Menus.java:274` | *хил* = border/frontier. Rage is *уур*, *хилэн* or *догшин*. | «Уур хилэн / Rage» (HUD word «Уур») | XS |
| LANG-002 | Medium / high | `CHOOSE_CLASS("Ангиа сонго", "Хүдэлмэрчин NPC эсвэл /class — …")` — `suld-api/src/main/java/mn/suld/api/onboarding/TutorialProgress.java:16` | Misspelling of *хөдөлмөрчин* (worker). The class NPC is «Ангийн Бөө» (`NpcDialogue.java:41`), so the hint names the wrong NPC. | «Ангийн Бөөтэй ярилц эсвэл /class — …» | XS |
| LANG-003 | Medium / medium-high | `LOCK_ON("Байгаа түгжих", …)` — `TutorialProgress.java:19` | *байгаа* = "existing/being". The intent is "lock on to a target" (*бай* = target; accusative *байг*). | «Дайсандаа түгжигд» or «Байгаа онил (Q)» | XS |
| LANG-004 | Medium / medium | `Rank.java:19` «Түмэний ноён»; `quests.json:271` «Түмэнтийн Тангараг», `:279` «Түмэнтийн ноён» and «түмтээр», `:379` «Түмний Сорил», `:387` «Түмэнтийн ноён» | Four forms of one term. The standard genitive of *түмэн* is *түмний*. «Түмэнтийн» is non-standard. A native editor should pick one. | «Түмний ноён», «Түмний Тангараг»; the unit «түмэн» (аравт, зуут, мянгат, түмэн) | XS |
| LANG-005 | Low / medium | «Сүлдний» 59× (e.g. «Сүлдний Зам») vs «Сүлдийн» 5× (e.g. `mobs.json:287` «Хөх Сүлдийн Сахиул») | Inconsistent genitive | Pick «Сүлдний» (the majority form) everywhere | XS |
| LANG-006 | Low / medium | «Хөшөө Цайдамын Гэрэлт Хөшөө» — `sites.json:57` | Vowel elision: *Цайдам → Цайдмын* is likely the standard form | «Хөшөө Цайдмын…» (native check) | XS |
| LANG-007 | Medium / high | `suld-api/src/main/java/mn/suld/api/item/ItemRarity.java:24-30`: UNCOMMON «Ховор», RARE «Нандин», EPIC «Домогт», LEGENDARY «Алдарт», MYTHIC «Домгийн» | *ховор* literally means "rare", but is used for Uncommon. *Домогт* ("legendary") and *Домгийн* ("of legend") are nearly the same word, for two different tiers, which hurts loot readability. | e.g. Энгийн · Сайн · Ховор · Нандин · Алдарт · Эртний · Домогт · Цор ганц | S (also update tooltips and docs) |
| LANG-008 | Medium / high | «Цол» = rank ladder (`Menus` "Цол · Rank") **and** cosmetic tag category (`suld-api/.../style/Cosmetic.java:23`). «Зэрэг» = Ascension («Тэнгэрийн Зэрэг»), item level («…, Зэрэг N]» `DungeonService.java:571`) and smith tiers. «Түвшин» = character level and skill-node rank (`SkillMapMenu.java:289`). | Overloaded words confuse new players. | Rank «Цол»; cosmetic tag «Хочит нэр» or «Тэмдэг»; item level «Эдийн түвшин»; tier «Шат»; node rank «Зэрэглэл» | S |
| LANG-009 | Low / high | Raw «(VERIFIED)» in chat (`HistoricSiteService.java:289`); «Act II «Хөх Сүлдний Зам»» inside story text (`quests.json:231`); bilingual titles «Заавар · Tutorial», «Кредит дэлгүүр · Store», «Lv», «EXP» | Mixed-language UI. Bilingual titles are defensible for a mixed audience; English inside story prose is not. | Show only «Түүхэн газар»; «II бүлэг»; decide one policy (e.g. English only in a secondary grey suffix) | XS–S |
| LANG-010 | Low / high | First quest «Говийн 3 чоныг устгаж…» while the hint sends you to the Kherlen (`quests.json:8,14`; mob «Говийн Чоно» `mobs.json:7`) | "Gobi wolf" on the Kherlen steppe | Rename the mob «Талын Чоно» | XS |
| LANG-011 | Low / high | IDs: `region.gobi`, `quest.gobi_*` vs `area.omnii_govi`, `mob.goviin_*`, `dungeon.govi_bulsh`. ө→o (`khodoo`, `mosun`) vs ө→u (`khuvsgul`, `munkh`); ү→u (`tumen`). English quest IDs (`quest.ice_spirits`, `quest.wolf_pelts`) vs Mongolian (`quest.khar_khot`). Spell id `DARKHANY_DARANGUI` vs name «Дарханы Дөш» (`Spell.java:35`). Emoji id `guul` vs name «Гавал» (`CosmeticCatalog.java:107`). `item.chonon_arisan` (`quests.json:21`). | Inconsistent romanisation. Not player-visible, but it bites tooling and content authors. | Adopt one table (see §4): ө→o, ү→u, х→kh, ц→ts, ж→j, govi. Do not rename existing IDs (save data); apply the rule to new ones. | XS (rule) |
| LANG-012 | Low / medium | «Ангийн Бөө» (`NpcDialogue.java:41`) and «Ангийн дэг журам» (= hunt's discipline, `:62`) in the same file | *анги* (class) + ийн and *ан* (hunt) + гийн give the same string | «Замын Бөө» or «Анги Сонгуулагч Бөө» | XS |
| LANG-013 | Low / low | «Шороон Хөвөрөл», «Талын Дэнгэ» (`CosmeticCatalog.java:117-118`); emoji «Гавал» (skull) `:107`; «нэрний өнгө» (`Cosmetic.java:24`) next to «Нэрийн өнгө» | Unclear or non-standard words | Native review; «нэрийн» | XS |
| LANG-014 | Low / medium | «Хойт зүгийн их цэнгэг нуур» (`world.json:454`) vs «Хойд» elsewhere; «Пайз гэдэг гэрэгэ…» (`NpcDialogue.java:68`) | Variant spelling; awkward order | «Хойд»; «Гэрэгэ буюу пайз…» | XS |
| LANG-015 | Low / medium | «Мөнх Хөх Тэнгэр» (`NpcDialogue.java:83-85`), «Мөнх Тэнгэр» (cosmetics), «Хөх Мөнх Тэнгэр» (research) | Harmonise | «Мөнх Хөх Тэнгэр» everywhere in game | XS |

---

## 3. Visual identity (task b)

### 3.1 Grep for the project's own "do not use" list

Scope: code, content, pack and tools, excluding `.git` and `build`.

| Term | Files with hits (whole repo, research docs included) | Where outside docs/research | Verdict |
|---|---|---|---|
| Soyombo / Соёмбо | 17 files | `assets/registry.json` (many notes saying "no Soyombo"); `tools/art/prompts.json:50` ("small pixelated Mongolian soyombo emblem" in the `logo_suld` prompt); `tools/art/khasar_prompts.json:4` ("no Soyombo") | **VIS-003**: the prompt and its output `assets/art/source/logo_suld.png` (which shows the modern national flag with the Soyombo) remain. The shipped logo was replaced by commit `4b3b86a` ("…in place of the modern flag…"). |
| Erdene (Zuu) | 0 in code or pack | docs only (`KHARKHORUM_RESEARCH_BRIEF.md:51,62`) | OK |
| Borjigin | 0 in code or pack | research docs only | OK |
| Qing / Manchu bow | 0 in code or pack | style-guide "do not" line only | OK |
| tamga / тамга | 41 files (case variants summed) | item lore («Хааны тамгатай…», «Чингисийн Тамга», «Алтан тамгатай хүнд сүх» `weapons.json:306`), Хөх Сүлд «тамга» story seals (`quests.json:267` etc.) | Text only, with no attested shape claimed. «Чингисийн Тамга» needs an original design and a label (HIST-025). |
| pagoda | 0 in code | `docs/world/kharkhorum/reference-analysis.md:68` "Avoid … pagoda stacks" | Text OK; **the art breaks it (VIS-001)** |
| ⚜ (fleur-de-lis) | 1 | `CosmeticCatalog.java:74` | VIS-004 |
| cherry | groves | `assets/world/kharkhorum/slices/slice-1.json` (8 of 10 groves list "cherry"); `Kit.java:368-380` | VIS-005 |
| Blue banner | imagery | painted GUIs | VIS-002 |

### 3.2 VIS findings

| ID | Severity / conf. | Evidence | Issue | Recommendation | Effort |
|---|---|---|---|---|---|
| VIS-001 | **Medium** / high (viewed the PNGs) | `resourcepack/assets/suld/textures/font/gui/welcome.png`: middle «ЦЭС» card and right «ТОГЛОХ» card show multi-tiered towers with up-turned eaves (Chinese pagoda and gate-tower style). `gui/main.png`: «ЦОЛ» card is a Chinese-style palace gate; «ДЭЛГҮҮР» card has tiered roofs. Source: the owner's painting (`assets/art/source/gui_welcome.webp`, commit `820b00f`). | Contradicts `docs/world/kharkhorum/reference-analysis.md:60` ("Proportions stay low and broad to avoid 'generic East-Asian fantasy'") and `:68` ("Avoid … Japanese torii and pagoda stacks"). Karakorum had Chinese-style roof tiles (verified, `ASSET_STYLE_GUIDE.md:125`), but tall stacked towers read as China or Japan to Mongolian players. | Repaint the cards toward gers, mud walls, low single-eave halls and stupa or turtle-stele motifs; or crop to non-tower parts. | M (art) |
| VIS-002 | Medium / high | `welcome.png`, `main.png`: royal-blue banners with a gold three-prong finial flank the palace, gates and stele | `ASSET_STYLE_GUIDE.md:138` "Do not claim blue was the imperial banner colour"; `SYMBOLS_AND_SCRIPTS.md:19-20` keep the three-pronged finial out of 13th-c. claims. Not a text claim, but the visual reads as "the Mongol imperial banner = blue trident". | Keep blue for the Хөх Сүлд relic. Give city and palace banners white or black horsehair *tug* with neutral finials. | M |
| VIS-003 | Low / high | `assets/art/source/logo_suld.png` (modern flag with Soyombo); `tools/art/prompts.json:50` | Not shipped now. The prompt would bring it back on regeneration. | Edit the prompt ("no Soyombo, no flag") and archive the old logo. | XS |
| VIS-004 | Medium / medium | `assets/art/source/server_icon_emblem.jpg` → `resourcepack/pack.png`, `textures/font/logo.png`, `suld-plugin/src/main/resources/server-icon.png` (commits `3e4cc9d`, `4b3b86a`); `CosmeticCatalog.java:74` uses «⚜» | A jade and gold emblem with fleur-de-lis-like crests, acanthus scrolls and hanging gems. Generic baroque or Southeast-Asian fantasy, not Mongol ornament (khee, ulzii, horns, cloud scroll). It is the server's first impression in the server list. ⚜ is European heraldry. | Commission an original emblem from verified ornament vocabulary (`MATERIAL_CULTURE.md`, ulzii, horn scroll, a white tug), with a native designer. Replace ⚜ with ✦ or a custom glyph. | M |
| VIS-005 | Medium / high | `assets/world/kharkhorum/slices/slice-1.json` (species lists: cherry in 8 of 10 groves); `KHARKHORUM_MASTER_PLAN.md` district 11 "blossom groves, ponds, pavilions" | Cherry blossom is the strongest Japanese signifier in vanilla Minecraft, and is not Orkhon-valley vegetation | Larch, birch, poplar or elm only; replace "pavilions" with gers or a garden ger | XS (data) |
| VIS-006 | Medium / medium | `tools/art/prompts.json` (model `gemini-3-pro-image`, ids `card_*`) → `assets/art/source/card_*.png`, cropped into the main and cosmetics menus (`ASSET_STYLE_GUIDE.md` "Card menus": "The paintings are the existing `card_*.png`"); `gui/cosmetics.png` shows vanilla Steve, villagers, a jack-o'-lantern and fireworks | Style guide §8: "Do not ship raw AI output". The cards mix a painted HD frame with vanilla-looking screenshots, and include a Halloween motif. I could not tell from the files alone whether the cards were edited after generation. | Paint-over pass or commissioned cards; drop the pumpkin; one rendering style per screen | M |
| VIS-007 | Low / medium | `suld-api/src/main/java/mn/suld/api/worldbuild/kharkhorum/Plaza.java:110-117` "Хааны Морьт Хөшөө: the Khan on a rearing horse, raising the SÜLD spear" at spawn | Will be read as Chinggis. Guide `ASSET_STYLE_GUIDE.md:142`: no real-person likeness. | Make the figure an anonymous warrior or a horse-and-banner monument, or name it a fictional founder | S |

Positive notes:
- Lamellar armour, red lacquer, felt deel and short siyah bows are specified in the armour and weapon notes (`assets/registry.json` T1–T6 notes; `MATERIAL_CULTURE.md`).
- The notes avoid European plate and Qing bows.
- Ulzii corner knots and green-glazed roofs are labelled INSPIRED.
- The research system (labels, do-not-use list, source table) is unusually careful. The gap is between the research and the art pipeline.

---

## 4. Terminology and style guide (task d)

Usage rules apply to in-game text. Preferred Latin is for IDs and docs; it does not change existing IDs.

| Term | Cyrillic | Preferred Latin | Meaning | Usage rule | Cat. |
|---|---|---|---|---|---|
| Kharkhorum | Хархорум | Kharkhorum | Mongol imperial capital | Historic facts only with dates (1220 / 1235); SÜLD's city is "a fictional reconstruction" | A |
| Chinggis Khaan | Чингис хаан | Chinggis | Founder (r. 1206–1227) | Never on loot, bosses or paid items; history lines only | A |
| Ögedei | Өгэдэй хаан | Ogedei | 2nd Great Khan | Walls/palace 1235; expanded the örtöö | A |
| Khasar | Хасар | Khasar | Chinggis's brother, archer | Do not use as a monster name (HIST-024) | A |
| Secret History | Монголын нууц товчоо | Secret History | 13th-c. chronicle | Cite only what it says; 1206 = source of the Onon | A |
| Khödöö Aral | Хөдөө Арал | Khodoo Aral | Kherlen–Tsenker plain | "Tradition links…"; *Secret History* colophon place | B/C |
| Delüün Boldog | Дэлүүн Болдог | Deluun Boldog | Birthplace in the *Secret History* | Always "location disputed" | A |
| Burkhan Khaldun | Бурхан Халдун | Burkhan Khaldun | Sacred mountain (UNESCO 2015) | Worship and ovoo only; no combat or loot | C |
| Otgontenger | Отгонтэнгэр | Otgontenger | Highest Khangai peak, state-worshipped | Khangai, never "Altai" | A |
| Ovoo | овоо | ovoo | Sacred cairn | Clockwise, three times, add a stone; never breakable | C |
| Möngke Köke Tengri | Мөнх Хөх Тэнгэр | Munkh Khukh Tenger | Eternal Blue Sky | One spelling in game | A |
| Etügen / gazar us | Этүгэн, газар ус | – | Earth and water spirits | Belief, not "fact" phrasing | C |
| Savdag / Lus | савдаг / лус | savdag, lus | Land / water spirit-masters | Folk belief; spirits are freed, not slain, at sacred places | C |
| Süld (banner spirit) | сүлд | suld | Banner, vital force | Genitive «сүлдний» | A |
| Khar / Tsagaan Süld | Хар / Цагаан сүлд | khar/tsagaan suld | Black war / white state banners | Not as paid cosmetics | C |
| Khökh Süld | Хөх Сүлд | Khukh Suld | SÜLD's blue relic | Always "SÜLD story" (blue is fiction) | D |
| Nine White Banners | Есөн хөлт цагаан туг | yesun költ tsagaan tug | 1206 white standard | §202; modern form ≠ 13th c. | A |
| Tamga | тамга | tamga | Clan stamp, seal | Only original designs, labelled fiction | A/D |
| Paiza / gerege | гэрэгэ, пайз | gerege (paiza) | Tablet of authority for envoys | «Гэрэгэ буюу пайз» | A |
| Örtöö / yam | өртөө (ям) | urtuu / yam | Relay stations | "Expanded by Ögedei" | A/B |
| Darkhan | дархан | darkhan | Smith; tax-exempt title | Both senses OK | A |
| Nerge (great hunt) | ав хомрого, их ан | nerge | Battue hunt, military drill | OK as fact | A |
| Aravt / zuut / myangat / tümen | аравт, зуут, мянгат, түмэн | aravt, zuut, myangat, tumen | Decimal units | Genitive «түмний» | A |
| Noyon | ноён | noyon | Lord / commander | Rank word | A |
| Khaan | хаан | khaan | Ruler | Top rank; avoid "Их Хаан" for players | B |
| Altan Urag | Алтан ураг | Altan Urag | Chinggisid Golden Lineage | Not a buyable tag | A |
| Ger | гэр | ger | Felt dwelling | Prefer to "yurt" in English UI | A |
| Deel | дээл | deel | Robe | Not "Chinggis's deel" | A |
| Khar Balgas | Хар Балгас | Khar Balgas | Uyghur capital (744/751–840) | OK | A |
| Khöshöö Tsaidam | Хөшөө Цайдам | Khoshoo Tsaidam | Turkic stelae 732/735 | Genitive «Цайдмын» (check) | A |
| Uushigiin Övör | Уушгийн Өвөр | Uushig | Deer stones, c. 1200–600 BCE | "about 2,000 years before Chinggis" | A |
| Chinggis's Wall | Чингисийн хэрэм | Chingis wall | Earthen rampart, likely Liao | Always "built before Chinggis" | A/B |
| Khara-Khoto | Хар Хот | Khar Khot | Tangut city (1032), Inner Mongolia | Black General = tragic defender | A/C |
| Tangut / Xi Xia | Тангуд (Си Ся) | Tangut | Western Xia state | OK | A |
| Govi | Говь | govi | Gobi | Latin `govi` in new IDs (MNS) | A |
| Class names | Баатар, Мэргэн, Бөө, Дархан, Хүлэгчин | baatar, mergen, boo, darkhan, khulegchin | Real words, fictional kits | Keep | D |
| Rage (Baatar resource) | Уур хилэн | uur | Rage | Not «Хил» | D |
| Rank vs tag | Цол / Хочит нэр | tsol / – | Rank vs cosmetic title | Do not reuse «Цол» for tags | D |
| Level / tier / ascension | Түвшин / Шат / Тэнгэрийн Зэрэг | level / tier / zereg | Progression words | One meaning each | D |
| Coins vs MNT | Зоос (in-game) vs ₮ (real tögrög) | zoos | Currencies | Do not use ₮ for in-game coins (`09`) | D |

Romanisation rule for new IDs: х→kh, ц→ts, ч→ch, ж→j, ш→sh, ө→o, ү→u, ы/ий→ii, ь dropped. This follows the existing `khodoo` / `tsereg` majority.

---

## 5. What a native reviewer must still check (not settled here)

1. Native-speaker sign-off on LANG-003, LANG-004, LANG-006 and LANG-013, and on the tone of HIST-022, HIST-023, HIST-024, HIST-025 and HIST-027.
2. Primary-text check of *Secret History* §202 and the colophon (§282). A Mongolian Cyrillic edition or the de Rachewiltz translation would settle HIST-002.
3. Mongolian law on commercial use of Chinggis Khaan's name or image. Status after the 2006 proposal is UNVERIFIED-IN-ENVIRONMENT; check legalinfo.mn.
4. UNESCO WHC 1081 and 1440 page text, once a working network path exists (both fetches failed; see Method).
