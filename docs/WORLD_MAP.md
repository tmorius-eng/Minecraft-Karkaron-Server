# «Сүлдийн Атлас» — the world map

**Status (2026-10-13):** the terrain pipeline (part A) is built and running on the dev server; the atlas screen (B),
pins, waypoints and the corner minimap (C) are next. Owner's rules for this feature: **real data only** — the map
draws the blocks the server has really generated, never a guess from the world generator; Mongolian look; the whole
world visible with `/map`; pins and waypoints; a minimap at the top left when `/map` is closed.

The earlier proposals in `docs/world-map/` (tile glyphs, predicted biomes) are **superseded** by this document.

## Товч

* Газрын зураг нь **зөвхөн бодит өгөгдөл**: сервер дээр үүссэн chunk бүрийг ванилла газрын зураг шиг уншиж (блокийн
  өнгө, өндрийн сүүдэр, усны гүн) хадгална. Үүсээгүй газар хоосон илгэн цаас хэвээр. Таамаг байхгүй.
* Өвс, навч, ус нь **тухайн газрын жинхэнэ биомын өнгөөр** (Minecraft-ын өөрийн өгөгдлөөс): тал нутаг тод ногоон,
  саванн шаргал, тайга хөхөвтөр, дулаан далай цэнхэр. Бусад блок ванилла газрын зургийн өнгөтэй.
* Одоо байгаа ертөнцийг сервер өөрөө нэг удаа уншина (`/suld map scan`, асаахаас 1 минутын дараа өөрөө эхэлнэ,
  tick-ийн ачааллаар удаашрана). Шинэ газар, барилга, нурсан блок нь автоматаар шинэчлэгдэнэ.
* Ажилтан: `/suld map status | scan [full|stop] | export <zoom> [x z r] | pois | flush | refresh [r]`.

## Part A — terrain (built)

### What a pixel is

A tile is the 128 × 128 pixels of one vanilla map. At zoom `z` (0 … 4) one pixel is `2^z` blocks, so a tile spans
`128·2^z` blocks; tiles are aligned to the world's own coordinates (tile 0 starts at block 0), so a moved spawn or
border never invalidates a tile. The playable square is the world border (`world.border`, 10 001 × 10 001 blocks on
the dev server); the whole world fits in 5 × 5 tiles at zoom 4.

Each column of the world is stored as one `int` (`suld-api` `MapColumn`): the map byte it shows on flat ground
(colour id × 4 + shade), the water depth (0 for land) and the height of its top block. A pixel is drawn the way
Minecraft's own map item draws it (`TileComposer`, `ColumnShading`): the top block's colour, one shade brighter when
the column is higher than the one a pixel north of it and one darker when lower (with the vanilla checkerboard
dither), water one shade brighter or darker by depth. A column the server has no data for, and anything outside the
border, is byte 0 — transparent — so the atlas page (parchment) shows through: that is how unexplored land looks.

### Where the colours come from

* The block's map colour is Minecraft's (`BlockData#getMapColor()`); the top block is the highest one with a map
  colour, looking from two blocks above the MOTION_BLOCKING height map (so grass, flowers, snow layers and carpets
  count, as on a vanilla map) down at most 24 blocks.
* **Natural colours** (`map.colors: natural`, the default): grass, leaves, vines, lily pads and water take the colour
  of the biome they really stand in (`NaturalPalette`, `map/colors.json`). The table is Minecraft's own data, written
  by `tools/map/gen_map_colors.py` from the 1.21.11 client jar: each biome's grass colour (its `grass_color`, or the
  `colormap/grass.png` pixel at its temperature and downfall, with the dark-forest and swamp modifiers), foliage and
  water colour, and each tinted block's tint and texture brightness. `tint × brightness × gain` is matched to the
  nearest of the palette's colours at shade LOW, NORMAL or HIGH (CIE L*a*b*), so the relief can still step one shade
  either way. The gains (grass 1.55, foliage 1.1, water 1.3) lift the dark textures to a vanilla map's brightness.
* `map.colors: vanilla` draws a vanilla map's colours instead. Changing the mode deletes the stored columns
  (`FORMAT` file) and the scan reads the world again.
* At start the server checks the suld-api palette table against its own map palette and logs `palette checked`
  (0 differences on Paper 1.21.11).

### How the world is read (`MapService`)

All reads of the world happen on the main thread within `map.snapshot-budget-ms` (2 ms) per tick: a chunk snapshot
(heights and biomes) is taken and handed to the map's worker threads (`map.workers`, 2), which work out the 256
columns and store them. Sources:

| Source | When |
|---|---|
| A chunk loads and is not on the map yet | new land a player walks into, old land from before the map existed |
| Block events mark a chunk's surface as changed (place, break, explode, burn, fire, form/fade/spread, fluids, trees, crops, pistons, buckets, endermen…) | read again within 5 s, or as the chunk unloads |
| The chunks around every player, once a minute | changes no event reports (a city build by the WorldBuilder) |
| **The scan** of the world on disk | the region files' chunk tables name every chunk that exists; each one not yet on the map is loaded **without generating anything** (`getChunkAtAsync(x, z, false)`), read and let go; region by region, nearest the centre first. Throttled by the tick time like the pre-generator (`map.scan.*`: up to 8 loads in flight, halved above 30 ms, paused above 45 ms). Starts by itself `map.scan.auto-delay-seconds` after start; after the first full pass it only reads new chunks. A chunk that exists on disk but never finished generating is skipped (nothing to draw). |

Measured on the dev server (4 shared vCPU, 2026-10-13): 122 823 chunks on disk, read at ~100 chunks/s with the tick
at 3–5 ms (no players online).

### Storage

`plugins/SULD/map/columns/r.<rx>.<rz>.bin`: one file per 32 × 32 chunks (512 × 512 blocks), the 262 144 column ints
deflated (about 1 MB in memory, much less on disk); `present.bin`: one bit per chunk of the bordered square;
`FORMAT`: the layout and colour mode. Regions live in an LRU of `map.max-regions` (32); a dirty region is written
when it leaves memory, every minute, and at shutdown (bounded waits). Writes are atomic (temp file + move). One I/O
thread does all file work, so a write always lands before a later read of the same region.

Tiles (`MapTiles`) are composed on the workers and cached (`map.max-tiles`, 512 × 16 KB); a changed chunk drops the
tiles over it at every zoom and tells the listeners (open atlases, minimaps).

### Staff commands (`suld.admin`)

| Command | Does |
|---|---|
| `/suld map status` | the square, chunks on the map, regions on disk / in memory, queue, tiles, the scan's progress and speed |
| `/suld map scan [full\|stop]` | read the world on disk (`full`: also chunks already on the map) |
| `/suld map export <zoom> [x z radius]` | a PNG of the map (`plugins/SULD/map/export/`), one pixel per map pixel; at most 32 million pixels |
| `/suld map pois` | the atlas's places as JSON (`export/pois.json`) |
| `/suld map flush` | write every region now |
| `/suld map refresh [r]` | read the chunks within `r` (default 4) of you again |

### Places (`MapPois`)

Where the game really puts its places, from the same data and services that place them: Kharkhorum (the spawn), the
five relays (city blueprint points), the ten dungeon gates (`DungeonHalls.gateXZ`), the forty ovoos (`OvooService`,
with their area and region names), the historic sites (`sites.json` offsets from the spawn), the world bosses (their
site + offset) and the training yard (`training.x/z`). On the dev server: 75 places.

### Previews

`tools/map/atlas_art.py` draws the atlas frame (dark wood, the deep-blue band with the gold square-wave «алхан хээ»,
red-and-gold «өлзий» knots in the corners, pennant tabs, the title plaque, the coordinate cartouche) and composes it
over a real export and the real places: `world` (the whole world at zoom 4), `local` (a part), `minimap` (the corner
map at its real size). The same pieces become the pack's atlas textures in part B.

## Part B — the atlas screen (next)

`/map` opens the atlas in the sky-window engine the menu and the skill tree use (`MenuSky`): the player is seated on
a private stage; the map is a wall of invisible fixed item frames holding filled maps (one pooled `MapView` per
frame, a per-player renderer paints the tile), the frame art is a display model behind it, markers and labels are
text displays in front. The crosshair is the cursor (the coordinates under it in the cartouche, a tooltip on a
marker), the wheel zooms (whole world … 1 block per pixel), W/A/S/D pan, left tabs filter the kinds of places, right
tabs: whole world, centre on me, zoom, place a pin. Shift closes.

## Part C — pins, waypoints, minimap (next)

Personal pins (name, icon, colour; stored in the database), a waypoint with a guide line and see-through world labels
«Нэр (123 м)», the latest death as a marker, and a minimap at the top left of the screen when `/map` is closed: a map
in an item frame placed in the corner of the screen by a core shader in the resource pack (the technique of the
open-source NMinimap, written fresh for SÜLD), with the coordinates and the area drawn into it.
