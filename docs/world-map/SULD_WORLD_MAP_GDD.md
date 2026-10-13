# «Сүлдийн Атлас» — the world map (game design)

**Status: DESIGN (2026-10-13), awaiting the owner's approval.** Architecture: `WORLD_MAP_ARCHITECTURE_DECISION.md`.
Look: the owner's references — a parchment map frame, an open atlas book drawn in ink, in-world waypoint labels.

## 1. What the player gets

The atlas is the steppe in one book: where you are, where everything SÜLD put in the world is, how dangerous it is
for you, and your own marks. You choose a destination, close the book, and the way stays with you: a line in the HUD
and a label in the world, until you arrive.

## 2. Opening and controls

| Action | Input |
|---|---|
| Open | `/map` (`/atlas`, `/атлас`, `/газар`) · **F while holding the «Сүлд Цэс» clock** (slot 9: "9, F") · «🗺 Атлас» on the hub's travel page |
| Close | **Shift** · «✕» on the frame · any command |
| Pan | **W A S D** (faster when zoomed out) |
| Zoom | **mouse wheel**: five steps, 16 · 32 · 64 · 128 · 256 blocks per tile |
| Point | the crosshair is the cursor (hover shows the tooltip and the coordinates) |
| Select / use a tab or tool | **left click** |
| Quick pin here | **right click** on the map |
| There is no M key | a vanilla client does not send it (architecture §2) |

When it cannot open (combat in the last 8 s, inside a dungeon run, in a vehicle, gliding or falling, a soul in
«Тэнгэрийн сорил», training), the player is told why in one line.

## 3. The screen

```
 ╔═══════════════════════════════  «СҮЛДИЙН АТЛАС»  ═══════════════════════════════╗
 ║ ▌Ертөнц        ┃                                                     ┃  ✚ Тэмдэг   ║
 ║ ▌Хот суурин    ┃                                                     ┃  ✖ Устгах   ║
 ║ ▌Сүнсний       ┃      the map: 40 × 25 tiles of ink-drawn terrain     ┃  ◉ Над руу  ║
 ║   тахилга      ┃      markers, area names, the player's arrow,        ┃  ＋  ／  －  ║
 ║ ▌Гяндан        ┃      party members, pins, the quest mark             ┃  👁 Шүүлт   ║
 ║ ▌Босс          ┃                                                     ┃  ⨯ Зогсоох  ║
 ║ ▌Түүхэн газар  ┃                 [tooltip near the cursor]           ┃             ║
 ║ ▌Даалгавар     ┃                                                     ┃  ⌖ хойд ↑   ║
 ║ ▌Миний тэмдэг  ┃                                                     ┃             ║
 ╚══ −1803, −4902 · Говь · «Бага» · 1 хавтан = 64 блок ══ Дугуй: томруулах · WASD: чирэх · Shift: хаах ══╝
```

* **The book.** An open atlas on a dark-wood board (reference 2), parchment pages, a stitched spine down the middle.
  A compass rose in a corner names the four directions in Mongolian (хойд, өмнөд, зүүн, баруун). North is always up.
* **Left tabs** filter the markers (§5). The active tab stands out; «Ертөнц» shows everything allowed.
* **Right tools:** new pin, delete pin, centre on me, zoom in/out (with the step shown), the filter panel (show labels,
  show party, show predicted land), stop navigation.
* **Bottom ribbon:** the cursor's world coordinates (as in reference 1), the area under the cursor and its danger word
  for you, the scale, and the control hints.
* **Tooltip:** follows the cursor; never covers the cursor itself.

## 4. The land

* **Ink tiles.** Each tile is a hand-drawn 16×16 sprite in sepia ink on parchment: steppe (sparse grass strokes),
  plains, forest (tree clusters), taiga (pines), hills, mountains (peaks), sand and dunes (Gobi), badlands, snow,
  swamp, lake and river water (wave lines), sea, beach and coast edges (the shore follows the neighbours), ruins.
  Tiles have two or three variants so large areas do not look stamped.
* **Two inks, honestly.** Land whose chunks exist is drawn in full ink (**verified**). Land not generated yet is drawn
  in light "survey" ink from the world generator's own biome map for the seed (**predicted**: it is what the land will
  be, but a structure or a change by players can differ). The filter panel can hide predicted land.
* **The city.** Kharkhorum is drawn as its own illustrated vignette (walls, the palace, the plaza) at the closer
  zooms, and as a gold city sign at the far ones.
* **Borders and names.** Region borders are dashed ink lines with their names at the far zooms (Хэрлэн, Говь, Хангай,
  Алтай and the four outer lands); the 40 area names appear when zooming in, «?» until the player has discovered the
  area. The world border is the book's ruled edge; the empty corners beyond r 5000 are plain parchment.

## 5. Categories, icons and filters

Every marker has a shape, a colour and a word, so nothing depends on colour alone.

| Tab | Shows | Icon | Count today |
|---|---|---|---|
| «Ертөнц» | everything the player may see | — | — |
| «Хот суурин» | Хархорум; its 5 relays (travel 10 зоос, only at a relay); at close zoom the 14 NPCs by role; the training yard | gold city sign · horse post · small figures | 1 · 5 · 14 · 1 |
| «Сүнсний тахилга» | the 40 ovoo (a soul rises and does its ritual at an ovoo) | stone cairn with a blue khadag | 40 |
| «Гяндан» | the 10 dungeon gates, with the level band and party size; heroic for level 60 | cave mouth + level plate; ✓ when cleared | 10 |
| «Босс» | the 2 world-boss grounds, with the next time | horned skull | 2 |
| «Түүхэн газар» | the 16 historic sites (filled when visited); the label says «Түүх: баталгаатай» or «Сэдэвлэсэн» (the VERIFIED/INSPIRED review labels) | stele | 16 |
| «Даалгавар» | the current chapter's target (a gate, a site, or a region circle) | gold scroll «!» | 1 |
| «Миний тэмдэг» | own pins, the latest death, party members | letters Ⓐ–Ⓗ and 8 symbols in 8 colours · skull · small arrows | ≤ 32 · ≤ 1 · ≤ 3 |

**Clutter rules.** Markers closer than a tile on screen merge into a cluster badge with a number; hovering lists
them, clicking zooms in on them. Names show for the city and the dungeons at the middle zooms, for everything only on
hover. NPC figures show only at the two closest zooms.

## 6. Danger for you

The atlas uses the HUD's own rule, so the sidebar and the map never disagree: the gap between a place's level and the
player's level.

| Gap | Word | Badge |
|---|---|---|
| safe zone (the city) | «Аюулгүй» | white circle |
| ≤ −6 | «Хялбар» | grey dot |
| −5 … −2 | «Бага» | green diamond |
| −1 … +2 | «Тохиромжтой» | yellow triangle |
| +3 … +5 | «Өндөр» | orange double triangle |
| ≥ +6 | «Үхлийн!» | red skull |

* **Areas:** the area's band at that spot (outer lands ramp from 27 at r 2600 to 60 at r 5000).
* **Dungeons:** the lower end of the rung's band (the entry level); plus party size, and why it is closed for you (level,
  the previous rung, a story chapter, gear power from rung 5).
* **World bosses:** the boss's level, «Бүлгээр · 2–20 хүн», and whether it is up now.
* No place is gated by Тэнгэрийн Зэрэг today; when one is, its rank shows the same way.

## 7. What each marker says (tooltip)

| Marker | Lines |
|---|---|
| Dungeon | name · «Гяндан · L22+ · 1–4 хүн» · danger word · «Нээлттэй» or the reason it is locked · ✓ cleared / best heroic key · distance and direction · «Л: Чиглүүлэх · П: Дэлгэрэнгүй» |
| Ovoo | «<area> овоо» · «Сүнс энд амилна» · blessed today? · distance |
| Historic site | name · history label · visited? · the chapter that sends you there · distance |
| World boss | name · level · next time / «Одоо байна!» · group size |
| City / relay / NPC | name · what is there (merchants, smith, shrine, academy; travel from relays) |
| Area (hover the land) | area name or «?» · level band · danger word · its ovoo |
| Pin | name · note · icon · distance · «Чиглүүлэх · Засах · Устгах · Ертөнцөд харуулах» |
| Latest death | «Сүүлд үхсэн газар» · minutes left of the soul beacon · distance |

## 8. Who sees what

| Thing | Visible to |
|---|---|
| The land (both inks), region names | everyone |
| Area names | after the player discovered the area (else «?») |
| City, relays, NPCs, training yard | everyone |
| Ovoo (40) | everyone (a soul needs to find one) |
| Dungeon gates (10) | everyone, with the player's own status |
| Historic sites (16) | everyone (the quest book already gives their way); visited ones filled |
| World-boss grounds (2) | everyone; glowing while a boss is up |
| Relic shrines (2) | **never as a point.** After `/relic hint`, that player sees a shaded band (the hint's distance band and 45° sector) for 10 minutes |
| Unbuilt city secrets | nobody |
| Quest mark | the player, for the current chapter |
| Pins, latest death | their owner |
| Party members' positions | their party |

## 9. Personal pins

1. In the atlas, **right-click** a spot (or «✚ Тэмдэг», then left-click).
2. A small dialog opens over the atlas: name (1–24 letters), note (up to 100), one of 16 icons (Ⓐ–Ⓗ and 8 symbols:
   ★ flag, tent, pick, tree, horse, skull, heart) and «Ертөнцөд харуулах» (show it in the world).
3. «Хадгалах» saves it; «Хадгалаад чиглүүлэх» also makes it the destination; «Болих» cancels.
4. Hover a pin and left-click to open it: «Чиглүүлэх», «Засах» (the same dialog, filled in), «Устгах» (asks once).
5. Outside the atlas: `/map pin <нэр>` marks where you stand; `/map pins` lists them; `/map stop` ends navigation.

Limits: 32 pins per player, the main world only, inside the border; at most 20 pin edits a minute. Pins belong to
the player's UUID and survive reconnects and restarts. A training stand-in cannot make pins.

## 10. Navigation after closing

* One destination at a time: any marker or pin («Чиглүүлэх»), or the current quest.
* **The guide line** (the boss-bar row, the slot the dungeon guide already uses):
  «📍 Хар Хотын хаалга ⬈ зүүн хойд · 712м ▲12» — arrow relative to where you look, compass word, horizontal metres,
  height difference when known. Updated twice a second.
* **In the world** (reference 3): the destination, pins marked «Ертөнцөд харуулах» (up to three) and the latest death
  float as labels («Ⓐ Нэр» and the distance) that show through hills, only to you; far ones sit at the edge of your
  view in their direction, near ones stand on the spot; a light beam rises from the spot within 160 blocks.
* **Arrival:** within 10 m, «✔ Хүрлээ: <нэр>» and a sound; a reached destination clears (the pin stays).
* **Honesty:** the arrow is a straight line, not a road; the first time, the tooltip says «Шулуун чиг — зам биш».
  The map never teleports anyone.
* The destination survives reconnects; `/map stop` or «⨯ Зогсоох» ends it.

## 11. Party

Party members appear as small coloured arrows with their names. Phase 4: the party leader may place one shared
«Цугларах газар» (rally) pin that every member sees and can navigate to; it disappears when the party ends. Members
cannot edit or delete each other's personal pins.

## 12. States

* **First start:** the land is classified in the background; the atlas opens at once and fills in («Газрын зураг
  зурагдаж байна… 37 %»).
* **Database down:** pins are read-only for the moment; a save that fails says so and keeps the dialog's text.
* **Pack not loaded:** the atlas cannot draw; `/map` says so and offers the plain list of places with directions.

## 13. Not in this design

Route-finding; teleport or fast travel from the map; a HUD minimap (Phase 5, gate D5); a web map; a client mod
(gate D4).
