# Named areas (газар нутаг)

**Status:** FUNCTIONAL. Names, banners, discovery EXP, level bands, an ovoo at the heart of every area (40) and the historic sites (16, `docs/world/HISTORIC_SITES.md`) work; each region spawns its own mobs (`content/mobs.json`, roles wild/ladder). The terrain is still vanilla world generation.

The wild lands around Kharkhorum are 8 regions in two belts (`content/world.json`). The 4 inner regions (Хэрлэнгийн Тал, Говь, Хангай, Алтай; 51–2600 blocks) are split into 6 areas each: 3 rings, each in 2 halves of the region's bearing range. The 4 outer regions of Act II (Хэнтийн Нуруу, Зүүнгарын Говь, Отгонтэнгэрийн Оргил, Хөвсгөлийн Тайга; 2600–5000 blocks, levels 27–60) have 4 areas each. That makes **40 areas**, named after real places in roughly that direction from Kharkhorum. The map is compressed: real distances are hundreds of kilometres. The table below is generated from `content/world.json`; that file wins if they ever differ.

* **Regions** (`WorldContent.REGIONS`) keep the gameplay: mobs, safe zone, quest targets and the region discovery.
* **Areas** (`WorldContent.AREAS`, `mn.suld.api.region.Area`) add:
  * a banner when you cross into one;
  * a one-time discovery reward of 60 / 120 / 200 EXP by ring, stored in bits 16–63 of the style row's `discovered` mask (no migration);
  * a level band inside the region's band.

## History classification

| Label | Meaning |
|---|---|
| VERIFIED | A real river, mountain, lake or ruin that lies in that general direction from Kharkhorum. |
| INSPIRED | Lands of a real 12th–13th-century people (Tatar, Merkit, Naiman) or the Tangut border; the placement is simplified. |

Area descriptions are game text, not historical claims. SÜLD's own symbols (Хөх Сүлд and the invented tamga) are fiction (ASSET_STYLE_GUIDE).

## Areas

| Bit | Area | Region | Ring (blocks) | Bearing (°) | Level | EXP | History |
|---|---|---|---|---|---|---|---|
| 16 | Туулын Хөндий — Туул голын бургастай хөндий | Хэрлэнгийн Тал | 51–700 | 45–90 | 1–3 | 60 | VERIFIED |
| 17 | Хэрлэнгийн Тал — Хэрлэн голын өргөн тал | Хэрлэнгийн Тал | 51–700 | 90–135 | 1–3 | 60 | VERIFIED |
| 18 | Бурхан Халдуны Бэл — Хэнтийн ариун уулын бэл | Хэрлэнгийн Тал | 700–1500 | 45–90 | 3–6 | 120 | VERIFIED |
| 19 | Хөдөө Арал — Хэрлэн, Цэнхэрийн бэлчир — их хуралдайн газар | Хэрлэнгийн Тал | 700–1500 | 90–135 | 3–6 | 120 | VERIFIED |
| 20 | Онон Голын Хөндий — Онон гол — Дэлүүн Болдогийн нутаг | Хэрлэнгийн Тал | 1500–2600 | 45–90 | 6–8 | 200 | VERIFIED |
| 21 | Буйр Нуурын Тал — Татаруудын нутаг байсан алс зүүн тал | Хэрлэнгийн Тал | 1500–2600 | 90–135 | 6–8 | 200 | INSPIRED |
| 22 | Онгийн Гол — Говь руу урсах Онгийн гол | Говь | 51–700 | 135–180 | 5–8 | 60 | VERIFIED |
| 23 | Таацын Хөндий — Таацын голын хуурай хөндий | Говь | 51–700 | 180–225 | 5–8 | 60 | VERIFIED |
| 24 | Өмнийн Говь — Өмнөд говийн хайрган тал | Говь | 700–1500 | 135–180 | 8–12 | 120 | VERIFIED |
| 25 | Говь Гурван Сайхан — Говийн гурван сайхан нуруу | Говь | 700–1500 | 180–225 | 8–12 | 120 | VERIFIED |
| 26 | Галбын Говь — Галбын элсэн говь | Говь | 1500–2600 | 135–180 | 12–15 | 200 | VERIFIED |
| 27 | Тангудын Хил — Тангуд улсын хил рүү тэмүүлэх зам | Говь | 1500–2600 | 180–225 | 12–15 | 200 | INSPIRED |
| 28 | Орхоны Хөндий — Орхон голын урсгал доош — Хархорумын хойд хөндий | Хангай | 51–700 | 0–45 | 10–13 | 60 | VERIFIED |
| 29 | Хар Балгас — Уйгурын эртний хотын балгас | Хангай | 51–700 | 315–360 | 10–13 | 60 | VERIFIED |
| 30 | Сэлэнгэ Мөрөн — Хойд зүг урсах их мөрөн | Хангай | 700–1500 | 0–45 | 13–17 | 120 | VERIFIED |
| 31 | Идэрийн Гол — Ойт уулсын дундах Идэр гол | Хангай | 700–1500 | 315–360 | 13–17 | 120 | VERIFIED |
| 32 | Мэргидийн Тайга — Мэргид аймгийн байсан хойд ой | Хангай | 1500–2600 | 0–45 | 17–20 | 200 | INSPIRED |
| 33 | Хөвсгөл Нуур — Хойт зүгийн их цэнгэг нуур | Хангай | 1500–2600 | 315–360 | 17–20 | 200 | VERIFIED |
| 34 | Тамирын Гол — Хойд, Өмнөд Тамирын бэлчир | Алтай | 51–700 | 270–315 | 18–22 | 60 | VERIFIED |
| 35 | Хангайн Нуруу — Хархорумаас баруун өмнөх их нуруу | Алтай | 51–700 | 225–270 | 18–22 | 60 | VERIFIED |
| 36 | Завхан Гол — Баруун зүгийн Завхан гол | Алтай | 700–1500 | 270–315 | 22–26 | 120 | VERIFIED |
| 37 | Отгонтэнгэр — Хангайн хамгийн өндөр цаст оргил | Алтай | 700–1500 | 225–270 | 22–26 | 120 | VERIFIED |
| 38 | Хархираа Уул — Увсын мөсөн оргилууд | Алтай | 1500–2600 | 270–315 | 26–30 | 200 | VERIFIED |
| 39 | Найманы Нутаг — Найман аймгийн байсан Алтайн нутаг | Алтай | 1500–2600 | 225–270 | 26–30 | 200 | INSPIRED |
| 40 | Хэнтийн Тайга — Бурхан Халдуны өндөр тайга | Хэнтийн Нуруу | 2600–3800 | 45–90 | 27–43 | 300 | VERIFIED |
| 41 | Дарьганга — Унтарсан галт уулс, хүн чулуутай тал | Хэнтийн Нуруу | 2600–3800 | 90–135 | 27–43 | 300 | VERIFIED |
| 42 | Улз Гол — Зүүн хойш урсах Улз гол | Хэнтийн Нуруу | 3800–5000 | 45–90 | 44–60 | 300 | VERIFIED |
| 43 | Мэнэнгийн Тал — Дорнын хязгааргүй тал | Хэнтийн Нуруу | 3800–5000 | 90–135 | 44–60 | 300 | VERIFIED |
| 44 | Хонгорын Элс — Дуут манхан — Говийн их элс | Зүүнгарын Говь | 2600–3800 | 135–180 | 27–43 | 300 | VERIFIED |
| 45 | Нэмэгтийн Хөндий — Эртний амьтдын яс хадгалсан хөндий | Зүүнгарын Говь | 2600–3800 | 180–225 | 27–43 | 300 | VERIFIED |
| 46 | Замын Үүд — Өмнөд хилийн жингийн зам | Зүүнгарын Говь | 3800–5000 | 135–180 | 44–60 | 300 | VERIFIED |
| 47 | Хар Хотын Зам — Тангудын балгас руу хөтлөх элсэн зам | Зүүнгарын Говь | 3800–5000 | 180–225 | 44–60 | 300 | INSPIRED |
| 48 | Говь-Алтайн Нуруу — Говь, Алтайг холбосон нуруу | Отгонтэнгэрийн Оргил | 2600–3800 | 225–270 | 27–43 | 300 | VERIFIED |
| 49 | Хар Ус Нуур — Их нууруудын хотгорын нуур | Отгонтэнгэрийн Оргил | 2600–3800 | 270–315 | 27–43 | 300 | VERIFIED |
| 50 | Мөнххайрхан — Мөнх цаст Мөнххайрхан уул | Отгонтэнгэрийн Оргил | 3800–5000 | 225–270 | 44–60 | 300 | VERIFIED |
| 51 | Таван Богд — Алтайн хамгийн өндөр таван оргил | Отгонтэнгэрийн Оргил | 3800–5000 | 270–315 | 44–60 | 300 | VERIFIED |
| 52 | Дархадын Хотгор — Хөвсгөлийн баруун талын нуурт хотгор | Хөвсгөлийн Тайга | 2600–3800 | 315–360 | 27–43 | 300 | VERIFIED |
| 53 | Эгийн Гол — Хөвсгөлөөс урсах Эгийн гол | Хөвсгөлийн Тайга | 2600–3800 | 0–45 | 27–43 | 300 | VERIFIED |
| 54 | Цагаан Нуурын Тайга — Цаа бугачдын хойд тайга | Хөвсгөлийн Тайга | 3800–5000 | 315–360 | 44–60 | 300 | VERIFIED |
| 55 | Сэлэнгийн Адаг — Хойд зүг урсах Сэлэнгийн доод хэсэг | Хөвсгөлийн Тайга | 3800–5000 | 0–45 | 44–60 | 300 | VERIFIED |
