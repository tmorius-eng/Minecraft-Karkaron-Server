# World map — architecture decision

**Status: PROPOSED (2026-10-13), awaiting the owner's approval.** Facts and citations: `WORLD_MAP_CURRENT_STATE.md`.

## Товч (Mongolian summary)

* **M товч ванилла Minecraft дээр боломжгүй.** Клиент серверт зөвхөн хөдөлгөөн (WASD, үсрэх, Shift, гүйх), хотбар/
  дугуй, хулганы зүүн/баруун, F, Q-г л илгээдэг. M товчийг жинхэнэ болгох цорын ганц арга бол тусдаа client mod
  (Fabric). Төслийн бичсэн дүрэм «client mod хэрэглэхгүй» гэж байгаа тул **одоохондоо mod хийхгүй**.
* **Санал болгож буй шийдэл: «Сүлдийн Атлас» ванилла дээр.** Манай аль хэдийн ажиллаж буй «тэнгэрийн цонх»
  (чадварын мод, цэс) техник дээр газрын зургийг бүтэн дэлгэцээр нээнэ. WASD = чирэх, хулганы дугуй = томруулах,
  загалмай = курсор, зүүн товч = сонгох, баруун товч = тэмдэг тавих, Shift = хаах.
* **Харагдац таны илгээсэн зураг шиг:** нээлттэй атлас ном, гараар зурсан мод, уул, элс, ус. Зүүн талд ангиллын таб
  (Ертөнц, Сүнсний тахилга, Гяндан, Босс, Хот суурин, Миний тэмдэглэгээ), баруун талд хэрэгслүүд байна.
  Газрын хээ нь resource pack-ийн жижиг «хавтан» үсгүүдээс бүтэх тул ертөнц өөрчлөгдөхөд pack дахин татуулахгүй.
* **Бүх 10 000 × 10 000 ертөнц харагдана**, үүсээгүй газар ч гэсэн. Paper-ийн генераторын биомыг урьдчилж уншдаг
  API-гаар зурна. Аль хэдийн үүссэн газар тод бэхээр, хараахан үүсээгүй газар цайвар «тойм» хэв маягаар харагдана.
* **Зургаас гарсан ч чиглүүлнэ:** дээд мөрөнд «📍 Нэр ⬈ зүүн хойд · 712м», мөн ертөнц дээр ханаар нэвт харагдах
  «Ⓐ Нэр · 28м» тэмдэг байна (2-р зураг шиг). Сүүлд үхсэн газар ч гарна. Тэмдэглэгээ DB-д хадгалагдана.
* **Нээх:** `/map` (`/атлас`), «Сүлд Цэс» цагийг барьж байхдаа **F**, эсвэл цэсний «Аялал» хуудас.

## 1. The question

The owner wants: press **M**, a fullscreen interactive map of the whole 10 000 × 10 000 world opens, with pan, zoom,
SÜLD's own places, danger information, personal pins, and on-screen navigation after closing it, in the look of the
three reference pictures.

## 2. What a vanilla client can do

| Input the server receives | API | Free for a map? |
|---|---|---|
| W/A/S/D, jump, sneak, sprint (held flags) | `PlayerInputEvent` | inside a sky window: yes (sneak = dismount = close) |
| hotbar selection and the mouse wheel | `PlayerItemHeldEvent` (cancellable) | yes, as zoom (the sky windows do this) |
| left click (swing), right click (use / interact entity) | `PlayerAnimationEvent`, `PlayerInteract(Entity)Event` | yes |
| F (swap hands) | `PlayerSwapHandItemsEvent` | yes **while the «Сүлд Цэс» clock is held** (F with the class weapon is the ultimate) |
| Q (drop) | `PlayerDropItemEvent` | no — it is lock-on |
| **M, or any other key** | — | **impossible**: nothing is sent |
| typed text | Paper Dialog `DialogInput.text` | yes (pin names) |

A resource pack can draw anything the font and item-model systems can show (glyphs, textures, display models). It
cannot add a key, a new screen, or mouse handling.

## 3. Options evaluated

| | Option | Verdict |
|---|---|---|
| A1 | Chest-window map (a title glyph, slots as cells) | Too small for a 10 000-block world; no free pan/zoom; the 176-px title rule. **Rejected** as the map (kept only as the fallback list when the atlas cannot open). |
| A2 | Vanilla map items (`MapView`, 128×128 canvas per map) | 256-colour palette; no Cyrillic in `MapFont`; contents render only in hand or in item frames, which need supporting blocks. **Rejected.** |
| **A3** | **«Сүлдийн Атлас»: the sky-window engine + a tile-glyph terrain map** | **Chosen.** Details below. |
| B | A project-owned client module (Fabric) | The only way to a real M key, a native screen and a HUD minimap. It contradicts the written no-mod policy, splits the player base (installers, Fabric API, a 1.21.11 lock, updates), and the vanilla path is still needed for everyone else. **Deferred** behind gate D4. |
| C | A web map (BlueMap) | Third-party, renders only generated chunks (~4 % by default), outside the game; the owner ruled out a web dashboard here. **Rejected** for players (it may stay an ops tool). |

## 4. The decision: A3

**Rendering — tile glyphs, not pictures.** The terrain is drawn by **text**: a fixed tileset of hand-drawn 16×16
sprites (forest, steppe, plains, hills, mountains, sand, snow, swamp, water, coast pieces, roads, ruins) lives in a
pack font `suld:atlas`. One `TextDisplay` per map row holds a string of tile glyphs, joined by the pack's negative
space glyphs so the tiles meet without gaps. Because the picture is text the server writes, it is:

* the same pack for every world and seed (no world-specific pack, no pack re-download when the land changes);
* small in the pack (a few 128×128 sheets; to be measured);
* per player (filters, discovery, pins and the cursor are just different text);
* in exactly the style of the second reference (an atlas drawn in ink on parchment, like Antique Atlas).

**Terrain data — the generator, then the real chunks.** A background job classifies every 16×16-block cell of the
bordered square (625 × 625 = 390 625 cells, one byte each) from `World#vanillaBiomeProvider()`, which samples the
world's own biome noise without generating chunks. Cells whose chunks are generated are re-classified from the real
blocks when they load (water, height, built city) and flagged **verified**; the rest are flagged **predicted** and
drawn in a lighter "survey" ink. Nothing is invented: predicted cells are the generator's own output for the seed,
and the map says which ones were not seen yet.

**Screen — the sky window.** The atlas reuses the camera seat, owner-only displays, ray cursor, WASD pan, wheel zoom,
clicks and Shift-to-close that `MenuSky`/`SkillSky` already ship (their duplicated plumbing becomes one shared
`SkyStage`). The open book, tabs, tools, markers, labels and tooltips are display entities, about 150 per open
(SkillSky uses 480).

**Navigation — three layers, all vanilla.**

1. A guide bar in the boss-bar row (the existing `DungeonGuide`, generalised): «📍 Нэр ⬈ зүүн хойд · 712м ▲12».
2. In-world labels like the third reference: owner-only, see-through `TextDisplay`s («Ⓐ Нэр» and the distance),
   placed along the line of sight 24 blocks out when the target is far and on the target when it is near, up to four
   at a time; the `SoulBeaconService` beam when within 160 blocks.
3. The latest death spot (the soul beacon) as an automatic waypoint while it lasts.

**Opening (no M):** `/map` (aliases `/atlas`, `/атлас`, `/газар`); **F while holding the «Сүлд Цэс» clock** (slot 9,
so "9, F" — today that F is swallowed by a guard); a card on the hub's travel page (its free ornament tile). Where
the atlas may not open (combat, dungeon run, vehicle, falling), the player gets the reason and the travel page.

## 5. How the reference pictures map onto A3

| Reference | In A3 |
|---|---|
| Parchment frame with rings, tabs on the left, tools on the right, coordinates under the map | The book frame texture; category tabs on the left page edge; tool buttons on the right; the cursor's world coordinates on the bottom ribbon |
| Open atlas book, hand-drawn trees/mountains/dunes/coast, coloured marker icons | The `suld:atlas` tileset in sepia ink; marker glyphs in shapes and colours; the same tabs as filters |
| In-world «A Awesome Waypoint 28.1m», «Latest Death 51.0m» | The see-through waypoint labels and the death waypoint |
| The minimap in the corner | **Not in the first build.** A glyph minimap in the HUD is technically possible but costs HUD space and per-player updates; it is Phase 5 behind gate D5. |

## 6. Consequences

* **Players install nothing.** The required pack grows by the tileset, icons and frame (estimate a few hundred KB,
  measured in Phase 0); it is already above the 8 MB ZIP soft target, so the frame must stay lean.
* **No route-finding.** Directions are straight lines with distance and height difference; the map never claims a
  path is walkable. **No teleport** from the map; travel stays at the relays.
* **Zoom is in steps** (16, 32, 64, 128, 256 blocks per tile), not continuous.
* The player sits on the seat high above their own column while the atlas is open (as in the skill tree), and
  cannot move or fight; other players see them seated in the sky only if they fly up there.
* A later Fabric module (if gate D4 ever opens) would read the same server data (POI registry, terrain grid, pins)
  through a versioned plugin channel; nothing in A3 has to be thrown away.

## 7. Decision gates for the owner

| Gate | Question | Recommendation |
|---|---|---|
| **D1** | Art style: the atlas-book look of the second picture (sepia ink on parchment)? | Yes; a preview PNG of the tileset and the open book comes first, before any Java (like UI-0 for the hub) |
| **D2** | Terrain source: generator prediction + real chunks (two inks)? | Yes, after the Phase 0 spike shows the prediction matches generated chunks |
| **D3** | Show the whole world from the start (your text: «ертөнцийг бүхэлд нь харуулна») or reveal it by exploring? | Whole world visible; unvisited areas keep a «?» label until discovered (the existing 64-bit area mask) |
| **D4** | A Fabric client module for a real M key? | Not now (policy, player base); revisit after the alpha with data |
| **D5** | A HUD minimap? | Later, after the atlas ships |
