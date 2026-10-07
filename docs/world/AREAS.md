# Named areas (газар нутаг)

**Status:** FUNCTIONAL_BUT_INCOMPLETE. Names, discovery and banners work. The terrain is still vanilla world generation, and no area has its own mobs, landmarks or builds yet.

The four wild regions around Kharkhorum used to be four endless slices: everything east of the capital was "Хэрлэнгийн Тал". Each region is now split into 6 named areas: 3 rings (51–700, 700–1500 and 1500–5000 blocks from the capital), each split into 2 halves of the region's bearing range. That makes 24 areas, named after real places in roughly that direction from Kharkhorum. The map is compressed: real distances are hundreds of kilometres.

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
| 16 | Туулын Хөндий — Туул голын бургастай хөндий | Хэрлэнгийн Тал (зүүн) | 51–700 | 45–90 | 1–3 | 60 | VERIFIED |
| 17 | Хэрлэнгийн Тал — Хэрлэн голын өргөн тал | Хэрлэнгийн Тал (зүүн) | 51–700 | 90–135 | 1–3 | 60 | VERIFIED |
| 18 | Бурхан Халдуны Бэл — Хэнтийн ариун уулын бэл | Хэрлэнгийн Тал (зүүн) | 700–1500 | 45–90 | 3–6 | 120 | VERIFIED |
| 19 | Хөдөө Арал — Хэрлэн, Цэнхэрийн бэлчир — их хуралдайн газар | Хэрлэнгийн Тал (зүүн) | 700–1500 | 90–135 | 3–6 | 120 | VERIFIED |
| 20 | Онон Голын Хөндий — Онон гол — Дэлүүн Болдогийн нутаг | Хэрлэнгийн Тал (зүүн) | 1500–5000 | 45–90 | 6–8 | 200 | VERIFIED |
| 21 | Буйр Нуурын Тал — Татаруудын нутаг байсан алс зүүн тал | Хэрлэнгийн Тал (зүүн) | 1500–5000 | 90–135 | 6–8 | 200 | INSPIRED |
| 22 | Онгийн Гол — Говь руу урсах Онгийн гол | Говь (өмнө) | 51–700 | 135–180 | 5–8 | 60 | VERIFIED |
| 23 | Таацын Хөндий — Таацын голын хуурай хөндий | Говь (өмнө) | 51–700 | 180–225 | 5–8 | 60 | VERIFIED |
| 24 | Өмнийн Говь — Өмнөд говийн хайрган тал | Говь (өмнө) | 700–1500 | 135–180 | 8–12 | 120 | VERIFIED |
| 25 | Говь Гурван Сайхан — Говийн гурван сайхан нуруу | Говь (өмнө) | 700–1500 | 180–225 | 8–12 | 120 | VERIFIED |
| 26 | Галбын Говь — Галбын элсэн говь | Говь (өмнө) | 1500–5000 | 135–180 | 12–15 | 200 | VERIFIED |
| 27 | Тангудын Хил — Тангуд улсын хил рүү тэмүүлэх зам | Говь (өмнө) | 1500–5000 | 180–225 | 12–15 | 200 | INSPIRED |
| 28 | Орхоны Хөндий — Орхон голын урсгал доош — Хархорумын хойд хөндий | Хангай (хойд) | 51–700 | 0–45 | 10–13 | 60 | VERIFIED |
| 29 | Хар Балгас — Уйгурын эртний хотын балгас | Хангай (хойд) | 51–700 | 315–360 | 10–13 | 60 | VERIFIED |
| 30 | Сэлэнгэ Мөрөн — Хойд зүг урсах их мөрөн | Хангай (хойд) | 700–1500 | 0–45 | 13–17 | 120 | VERIFIED |
| 31 | Идэрийн Гол — Ойт уулсын дундах Идэр гол | Хангай (хойд) | 700–1500 | 315–360 | 13–17 | 120 | VERIFIED |
| 32 | Мэргидийн Тайга — Мэргид аймгийн байсан хойд ой | Хангай (хойд) | 1500–5000 | 0–45 | 17–20 | 200 | INSPIRED |
| 33 | Хөвсгөл Нуур — Хойт зүгийн их цэнгэг нуур | Хангай (хойд) | 1500–5000 | 315–360 | 17–20 | 200 | VERIFIED |
| 34 | Тамирын Гол — Хойд, Өмнөд Тамирын бэлчир | Алтай (баруун) | 51–700 | 270–315 | 18–22 | 60 | VERIFIED |
| 35 | Хангайн Нуруу — Хархорумаас баруун өмнөх их нуруу | Алтай (баруун) | 51–700 | 225–270 | 18–22 | 60 | VERIFIED |
| 36 | Завхан Гол — Баруун зүгийн Завхан гол | Алтай (баруун) | 700–1500 | 270–315 | 22–26 | 120 | VERIFIED |
| 37 | Отгонтэнгэр — Хангайн хамгийн өндөр цаст оргил | Алтай (баруун) | 700–1500 | 225–270 | 22–26 | 120 | VERIFIED |
| 38 | Хархираа Уул — Увсын мөсөн оргилууд | Алтай (баруун) | 1500–5000 | 270–315 | 26–30 | 200 | VERIFIED |
| 39 | Найманы Нутаг — Найман аймгийн байсан Алтайн нутаг | Алтай (баруун) | 1500–5000 | 225–270 | 26–30 | 200 | INSPIRED |
