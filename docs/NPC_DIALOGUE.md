# NPC roster and dialogue

Kharkhorum's people are data: `suld-plugin/src/main/resources/content/npcs.json` (loaded by `ContentLoader`, checked
at start and by `/suld content validate`), their stock in `content/shops.json`. The plugin (`npc/NpcService`,
`npc/NpcDialogue`) only knows how to stand them on a point, talk and run an action. There is no Citizens; an NPC is a
glowing Mannequin with a pack skin and a hologram, spawned for every slice-1 point of
`assets/world/kharkhorum/points.json` that has an entry.

## `npcs.json`

```json
{ "id": "merchant.gate_east", "skin": "merchant", "name": "Будааны Худалдаачин", "line": "Зүүн хаалганы зах · Дэлгүүр",
  "glow": "GREEN", "action": "SHOP", "shop": "shop.grain", "icon": "WHEAT",
  "greetings": ["…"],
  "topics": [{ "title": "Зүүн хаалга", "paragraphs": [{ "text": "…", "history": "VERIFIED" }, { "text": "…", "history": "FICTION" }] }] }
```

| Field | Meaning | Checked |
|---|---|---|
| `id` | a city point id (`points.json`), or a **dynamic role** a campaign's NPC effect places (`"dynamic": true`) | the point exists; a dynamic id is not a point; every campaign NPC effect names an entry |
| `points` | further point ids the same person serves (the four market-street merchants are one entry) | each exists; a point is served once |
| `skin` | `resourcepack/assets/suld/textures/entity/npc/<skin>.png` | `[a-z_]+`; the file exists (`ContentDataTest`) |
| `name`, `line` | the hologram's name and second line | Mongolian; `CulturalNamingTest` blocklist |
| `glow` | a `NamedTextColor` name | one of the sixteen |
| `action` | `CLASS` `TUTORIAL` `QUESTS` `SIDE_QUESTS` `SHOP` `SMITH` `TRAVEL` `SHRINE` `KHURALDAI` `NONE` | `SHOP` needs `shop`, and the shop exists |
| `icon` | the Material beside the greeting | a known Material |
| `greetings` | one is picked per talk; empty = no conversation, the action runs at once | — |
| `topics[].paragraphs[].history` | `VERIFIED` / `INSPIRED` / `FICTION` | a paragraph saying «түүхэн баримт» must be `VERIFIED` |

The history label is shown in grey after every paragraph: «түүхэн баримт» (VERIFIED), «түүхийг сэдэвлэсэн»
(INSPIRED), «SÜLD-ийн домог» (FICTION). Sources for every VERIFIED line: `docs/research/history/KHARKHORUM.md`,
`MATERIAL_CULTURE.md`, `SYMBOLS.md` and the 2026-10-19 research in the plan (Bonn excavations 2000–2005, the 1237/38
coin, Rubruck's 1254 dates, the yam, Khöshöö Tsaidam).

## How a talk goes

* Right-click: a Paper dialog with the speaker's name, their icon, one greeting, the **action button** (Дэлгүүр, Аяны
  зам, Нэмэлт эрэл, Өртөөгөөр явах, Засвар · Хуяг, Анги сонгох, Заавар нээх, Ивээл, Их Хуралдай) and one **💬** button
  per topic. A topic shows its paragraphs with their labels, «« Буцах» and the action again.
* **Shift + right-click** skips the talk and runs the action.
* The action button works only while the player is still within 8 blocks of the NPC and alive (not a soul); callbacks
  are single-use and expire after 5 minutes. `npc.dialogue: false` turns the talk off.

## The roster (2026-10-19)

| Point | Name | Action | Topics (label) |
|---|---|---|---|
| `class_selection` | Ангийн Бөө | CLASS | Таван зам (F) · Бөөгийн эрэл (F) |
| `tutorial` | Хөтөч | TUTORIAL | Хархорум (V/F) · Эхний алхам (F) |
| `quest.first_hunt` | Анчдын Ахлагч | QUESTS | Их ан (V/F) · Мэргэний сорил (F) |
| `merchant.general` + food, cloth, weapons | Худалдаачин | SHOP `shop.general` | Торгоны зам (V/F) · Хоёр хороолол (V/F) · Дөрвөн хаалга (V/I) |
| `blacksmith` | Дархан | SMITH | Дархан цол (V/F) · Зэсчний газар (V/I) · Хуучин зоос (V/F) |
| `fast_travel.plaza` + the four gates | Өртөөчин | TRAVEL | Өртөө (V/F) · Гэрэгэ (V/I) |
| `shrine.sky` | Тэнгэрийн Тахилч | SHRINE | Мөнх Хөх Тэнгэр (V/F) · Гурван шашин (V/F) · Рубрукийн зун (V/I) |
| `quest.ikh_boo` | Их Бөө | SIDE_QUESTS | Орхоны хөндий (V/F) · Буган хөшөө (V/F) · Сүлдний зам (F) |
| `quest.morton` | Морьтон | SIDE_QUESTS | Урт зам (V/F) · Хүлэгчний сургууль (F) |
| `quest.jin_terguun` | Жингийн Тэргүүн | SIDE_QUESTS | Жингийн зам (V/F) · Хилэнц ба элс (F) |
| `quest.khotyn_noyon` | Хотын Ноён | SIDE_QUESTS | Аравт, зуут, мянгат, түмэт (V/F) · Тангараг (F) |
| `quest.myangatyn_noyon` | Мянгатын Ноён | SIDE_QUESTS | Мянгат (V/F) |
| `quest.tumentiin_noyon` | Түмэнтийн Ноён | SIDE_QUESTS | Түмэт (V/F) |
| `quest.malchin` | Малчин | SIDE_QUESTS | Хангайн мал (F) · Баруун хаалганы зах (V/I) |
| `merchant.gate_east` | Будааны Худалдаачин | SHOP `shop.grain` | Зүүн хаалга (V/I) |
| `merchant.gate_west` | Хонины Худалдаачин | SHOP `shop.sheep` | Баруун хаалга (V/I) |
| `merchant.gate_south` | Үхрийн Худалдаачин | SHOP `shop.cattle` | Өмнөд хаалга (V/I) |
| `merchant.gate_north` + `merchant.horse_gear` | Морины Худалдаачин | SHOP `shop.horses` | Хойд хаалга (V/I) |
| dynamic `merchant.caravan` | Жингийн Худалдаачин | SHOP `shop.general` | — (a campaign's NPC effect) |
| dynamic `steward.campaign` | Хуралдайн Түшмэл | KHURALDAI | — (the dev campaigns' NPC effect) |

The `SIDE_QUESTS` givers open their quest book until the side-quest service registers its opener
(`NpcService.sideQuestOpener`); the `quest.*` points were added on 2026-10-19 and are `required: false` in the plan.
The four gate merchants follow Rubruck's four gate markets (east grain and millet, west sheep and goats, south cattle
and wagons, north horses — VERIFIED); their goods are SÜLD's own.

## `shops.json`

```json
{ "id": "shop.grain", "name": "Зүүн хаалганы зах · тариа, будаа",
  "stock": [{ "material": "BREAD", "amount": 8, "price": 20, "name": "Боов x8" },
            { "item": "item.some_catalog_id", "amount": 1, "price": 300, "minLevel": 10 }] }
```

A card is a vanilla `material` with its Mongolian `name`, or a catalog `item` id (named by the catalog, generated at
COMMON rarity and `minLevel`), exactly one of the two; `amount` 1–64, `price` in coins, `minLevel` 1–100 (the card
is red and refuses below it). `shop.general` is the market street's and `/shop`; the window (`Menus.shop`) shows eight
cards per page on the Дэлгүүр page and the services (selling, cosmetics, credits, the smith's book and board, the
wallet) on its last page. A purchase charges first and gives once the coins are in the database
(`PlayerDataSaves.coinsForItem`).

## History safety

A «түүхэн баримт» paragraph is limited to well-attested facts: Ögedei's walls and palace (1235); the battue hunt as
military training; safe roads and the paiza; the darkhan title; the örtöö network Ögedei extended; Мөнх Хөх Тэнгэр; Rubruck's
four gate markets and two quarters; the Bonn excavations' craftsmen's quarter and the 1237/38 coin; the 1254 debate
of three faiths; Kül Tegin (732) and Bilge Khagan (735) on turtle bases; the decimal army. SÜLD's own story (the
reconstruction of the city, the Blue Banner, the Silver Tree's drinks, every side quest's events) is `FICTION` and told
as belief or as SÜLD's tale. Unverified claims (five hundred carts a day, the city's population, remains of the
Silver Tree, four stone turtles at four gates) are never spoken as fact. No sacred symbol is shown.

Tests: `ContentDataTest` (every NPC on a real point with a real skin, unknown point refused, SHOP without shop
refused, a «түүхэн баримт» line that is not VERIFIED refused, every campaign NPC role has an entry, a point served
once, shop material/item rules, the general shop's nine supplies), `CulturalNamingTest` (both files). Bot:
`tools/loadtest/bots/shopbot.js`. MANUAL_QA_REQUIRED in a real client: the dialog layout, text length per line, icons.
