# Хөрөнгийн хүсэлт 2026-10: гэр хороолол (Phase 2) ба хамтрагч, морь, ёслол (Phase 6)

Энэ файл нь 3D протоколын дагуу бичигдсэн: доорх хөрөнгө ирэхээс өмнө тэдгээрийн оронд ямар ч түр дүрс
хийхгүй. Хөрөнгө бүрт: хэмжээ, хэв маяг, хэсгийн төсөв, хөдөлгөөн, мөн Gemini Pro эсвэл ChatGPT-д шууд хуулах
англи prompt байна (зураг үүсгэгчид англиар илүү сайн ажилладаг).

## Яаж ашиглах

1. Prompt-ыг хуулаад доор байгаа **ерөнхий хэв маягийн мөр**-ийг төгсгөлд нь залгана.
2. Гарсан 4 талын зургийг `assets/art/source/requests/<id>.png` нэрээр хадгална. Эсвэл Meshy-гээр
   (`tools/meshy/`) GLB гаргаж `assets/sources/<id>.glb` болгоно.
3. Надад хэлнэ. Би `tools/blender/process_and_render.py` → `tools/blockbench/bb_mcp.py` →
   `python3 tools/validation/run_all.py` дамжуулж, загварыг тоглоомд оруулна.

**Ерөнхий хэв маягийн мөр** (`docs/ASSET_STYLE_GUIDE.md` §9-ийг загварт тохируулсан):

> orthographic concept sheet, front view, side view, back view and top view on one canvas, neutral mid-grey
> background, even studio light, clear silhouette, simple chunky low-poly shapes that read at Minecraft scale
> (1 block = 1 metre), flat colours with light hand-painted wear, restrained ornament, no text, no letters, no
> watermark, no Soyombo symbol, no glowing effects, no European or Chinese architecture

Дүрэм (`ASSET_STYLE_GUIDE.md` §6–§8): чимэглэл талбайн ≤ 15 %, өлзий хээ «INSPIRED» шошготой, Соёмбо огт үгүй,
музейн зураг хуулахгүй, түүхий AI гаралт эсвэл 4K бүтэц шууд орохгүй.

Төсөв (`docs/ASSET_BUDGET.md`): энгийн бүтэц ≤ 64 px (item atlas), загвар бүр Display Entity хэсгүүдээр
бүтнэ. Гэрийн бүх загвар нийтдээ нэг гэрт ≤ 24 Display хэсэг.

---

## Phase 2 — «Нүүдэлчдийн Отог»

Гэрийн хана, дээвэр нь блокоор (схемийн зүсмэл) баригдана. Доорх хөрөнгө нь тэдгээр дээр тавигдах Display
загвар болон блокийн бүтэц юм. Хэмжээ: 4 ханатай 7×7 блок, 6 ханатай 9×9, 8 ханатай 11×11, 12 ханатай Их Шар Өргөө 15×15.

| id | Юу | Хэмжээ (блок) | Хэсэг | Хөдөлгөөн |
|---|---|---|---|---|
| `ger.toono` | Тооно (дээврийн цагираг, хэрээстэй) | 1.5 Ø (4 хана) … 3 Ø (12 хана) | ≤ 6 | өрх нээх/хаах (2 төлөв) |
| `ger.bagana` | Хос багана, будаг сийлбэртэй | 0.25 × 3 | ≤ 2 тус бүр | үгүй |
| `ger.door` | Хаалга, улбар шар будагтай | 1 × 2 | ≤ 2 | нээх/хаах |
| `ger.felt` | Эсгий бүрээс: цагаан, бор, хаяавчтай, 3 бүслүүр | блокийн бүтэц 16 px, 3 хувилбар | — | — |
| `ger.felt_gold` | Их Шар Өргөөний шар бүрээс | блокийн бүтэц 16 px | — | — |
| `ger.zuukh` | Гал голомт: төмөр зуух, яндан тооно хүртэл | 0.8 × 0.6 × 0.6 + яндан | ≤ 4 | 3 төлөв: унтарсан, ассан, дүрэлзсэн |
| `ger.furniture` | Авдар, намхан ширээ, 2 ор, зэвсгийн тавиур, цом тавих индэр, туг, зөвлөлдөх ширээ | тус бүр ≤ 1 × 1 × 2 | ≤ 3 тус бүр | үгүй |
| `ger.uyaa` | Адууны уяа: 2 шон, татсан олс | 3 урт | ≤ 3 | үгүй |

### Prompt-ууд

**ger.toono** — `A traditional Mongolian ger roof crown (toono): a round wooden ring with crossed curved bars inside,
painted orange-red with a thin simple border pattern, thin wooden roof poles (uni) attached around its rim, a
folded white felt flap (urkh) on one side. Shown alone as a prop.`

**ger.bagana** — `A pair of square wooden ger pillars (bagana), 3 metres tall, painted orange with a simple
repeating knot-and-scroll border at the top and bottom, slightly worn, standing upright side by side.`

**ger.door** — `A small Mongolian ger wooden double door with a frame, painted bright orange, a simple geometric
border pattern carved and painted in blue and red, wooden handles, sturdy and slightly weathered.`

**ger.felt** — `Seamless flat texture swatches for a Mongolian ger felt cover: plain off-white felt with soft
fibre noise; brown felt; the lower skirt (khayavch) band with a darker border; three horizontal rope bands
(buslu) made of twisted white horsehair rope. Flat square tiles, top-down, no perspective.`

**ger.felt_gold** — `Seamless flat texture swatch of a large ceremonial ger cover in deep yellow silk over felt,
with a narrow dark blue border band, subtle fabric weave. Flat square tile, no perspective.`

**ger.zuukh** — `A traditional Mongolian ger iron stove (zuukh) on short legs, square body with a round cooking
hole and a small front door, a straight black stovepipe (yandan) rising upward, three versions side by side:
cold and dark, lit with a warm glow in the door, roaring with flames visible.`

**ger.furniture** — `A set of Mongolian ger furniture props in the same style: a painted wooden storage chest
(avdar) in orange with blue and red ornament, a low round wooden table, two low painted wooden beds, a wooden
weapon rack holding a sword and a bow, a short wooden trophy pedestal, a tall clan standard pole with white horse
hair tufts (tug), a long low wooden council table.`

**ger.uyaa** — `A Mongolian horse hitching line (uyaa): two sturdy wooden posts with a long twisted horsehair rope
stretched between them, a few ribbons tied to the rope, on short grass.`

---

## Phase 6 — хамтрагч, морины гоёл, ёслол

Хамтрагч бүр тоглогчийг дагах Display загвар: эзэндээ харагдахгүй (харцаа хаахгүй), 48 блокоос гадна хөлдөнө,
30-аас олон хавсралт ойр байвал автоматаар унтарна (`ASSET_BUDGET.md` §5).

| id | Юу | Хэмжээ (блок) | Хэсэг | Хөдөлгөөн |
|---|---|---|---|---|
| `pet.shonkhor` | Шонхор шувуу, мөрөн дээр | суухад 0.5 өндөр, далавч 1 | ≤ 8 | суух (толгой эргэх), далавч сунгах, нисэх, буух, тойрох |
| `pet.bankhar` | Монгол банхар гөлөг | 0.6 өндөр, 0.9 урт | ≤ 10 | алхах, суух, сүүл шарвах, хэвтэх |
| `pet.irves` | Цасны ирвэс (ховор) | 0.7 өндөр, сүүлтэй 1.8 | ≤ 12 | сэм алхах, суух, сүүл савах, хэвтэх |
| `mount.arvaikheer` | Дайны морины хуяг: арьс, төмөр ялтас | морины бүтэц 64 px + цээжний 1 хэсэг | ≤ 1 | үгүй |
| `mount.khukh_zos` | Хөх зост морь: хөх саарал зүс, туурайн доор сүнсний тоос | морины бүтэц 64 px + бөөм | 0 | бөөм |
| `emote.tsatsal_spoon` | Цацлын есөн нүдтэй халбага (цацлын ёслолд) | 0.4 | 1 | гарт барих |

### Prompt-ууд

**pet.shonkhor** — `A saker falcon in a stylised blocky low-poly game style: brown back with buff streaked chest,
dark eye stripe, yellow feet with short leather jesses, shown perched (front, side, back) and with wings spread in
flight (top view).`

**pet.bankhar** — `A Mongolian bankhar dog puppy in a stylised blocky low-poly game style: thick fluffy black coat
with tan legs, tan chest patch and two tan spots above the eyes, fluffy curled tail, sturdy and friendly, shown
standing and sitting.`

**pet.irves** — `A snow leopard in a stylised blocky low-poly game style: smoky grey-cream fur with dark rosettes,
very long thick tail, broad paws, calm proud pose, shown standing, sitting and lying down.`

**mount.arvaikheer** — `Horse barding for a Mongolian war horse in a stylised blocky game style: dark leather
covering the neck and chest, laced iron lamellar plates on the chest and flanks, red lacquer accents, simple
leather headstall, no plate armour of European type; shown on a plain brown horse, front, side and back.`

**mount.khukh_zos** — `A mythic steppe horse in a stylised blocky game style: blue-grey dappled coat, long silver-
white mane and tail, faint pale blue mist around the hooves only (no glow on the body), shown front, side and back.`

**emote.tsatsal_spoon** — `A traditional Mongolian libation spoon (tsatsal) used to offer milk to the sky: a
wooden spoon with a small square head pierced by nine holes in a 3 by 3 grid, a long handle with a simple carved
end and a blue silk ribbon (khadag) tied to it.`

---

## Түүхийн шошго (тоглоомд бичигдэнэ)

| Сэдэв | Шошго |
|---|---|
| Гэр, тооно, багана, эсгий, уяа | VERIFIED уламжлал, загвар нь INSPIRED |
| Их Шар Өргөө | INSPIRED (их хааны өргөөг сэдэвлэсэн, хэмжээ, өнгө нь зохиомол) |
| Шонхор агнуур, банхар | VERIFIED уламжлал |
| Цацал (сүүн өргөл) | VERIFIED уламжлал, тоглоомд хүндэтгэлтэйгээр |
| Арвайхээр, Хөх Зост нэр | INSPIRED (морины уламжлалыг сэдэвлэсэн, тодорхой түүхэн морь биш) |

## Төлөв

| id | Төлөв |
|---|---|
| бүгд | ХҮЛЭЭГДЭЖ БАЙНА: эзэмшигчээс лавлах зураг эсвэл GLB |
