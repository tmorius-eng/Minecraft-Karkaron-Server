# «Сүлдийн Атлас» — the world map

**Status (2026-10-13):** the terrain pipeline (part A), the atlas screen `/map` (part B) and the pins, the waypoint and
the corner minimap (part C) are built and running on the dev server. Parts B and C were seen on a real 1.21.11 client
(the vanilla client run headless on the dev machine: Xvfb + Mesa software rendering, scripted keys and mouse; the
screenshots went to the owner) — that run found and fixed three things a bot cannot see (below). A human check on the
owner's own PC is still owed. Owner's rules for this feature: **real data only** — the map draws the blocks the server
has really generated, never a guess from the world generator; Mongolian look; the whole world visible with `/map`;
pins and waypoints; a minimap at the top left when `/map` is closed.

The earlier proposals in `docs/world-map/` (tile glyphs, predicted biomes) are **superseded** by this document.

## Товч

* Газрын зураг нь **зөвхөн бодит өгөгдөл**: сервер дээр үүссэн chunk бүрийг ванилла газрын зураг шиг уншиж (блокийн
  өнгө, өндрийн сүүдэр, усны гүн) хадгална. Үүсээгүй газар хоосон илгэн цаас хэвээр. Таамаг байхгүй.
* Өвс, навч, ус нь **тухайн газрын жинхэнэ биомын өнгөөр** (Minecraft-ын өөрийн өгөгдлөөс): тал нутаг тод ногоон,
  саванн шаргал, тайга хөхөвтөр, дулаан далай цэнхэр. Бусад блок ванилла газрын зургийн өнгөтэй.
* Одоо байгаа ертөнцийг сервер өөрөө нэг удаа уншина (`/suld map scan`, асаахаас 1 минутын дараа өөрөө эхэлнэ,
  tick-ийн ачааллаар удаашрана). Шинэ газар, барилга, нурсан блок нь автоматаар шинэчлэгдэнэ.
* Ажилтан: `/suld map status | scan [full|stop] | export <zoom> [x z r] | pois | flush | refresh [r]`.
* Тоглогч: `/map` — атлас · `/map pin [нэр]` — энд тэмдэг тавих (30 хүртэл, өгөгдлийн санд) · `/map pins` — жагсаалт
  ([Чиглэх] [Устгах]) · `/map unpin <нэр|#>` · `/map guide <нэр|# | x z>` — чиглэл (дээд талын зурвас, хана нэвт
  харагдах шошго «◆ Нэр · 412 м») · `/map stop` · `/map minimap` — зүүн дээд буланд мини газрын зураг асаах/унтраах
  (мөн «Тохиргоо»-нд; SÜLD дүрс багцтай үед л харагдана).

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

## Part B — the atlas screen `/map` (built)

`/map` (aliases `/atlas`, `/gazar`, `/газар`; again `/map` or Shift closes) opens the atlas in the sky-window engine
of the menu and the skill tree (`AtlasSky`): the player is seated on a private stage 144 blocks over the world's top
and looks at a wall of **5 × 5 maps** — invisible, fixed glow item frames holding filled maps. The frame art is
drawn around them by display quads from the pack, places and labels stand in front of them; everything on the stage
is visible to its owner only and is never saved. A crash while the atlas is open puts the player back where they
stood on the next login.

* **The camera** sits 6 blocks in front of the wall and 0.25 above its middle: at the default 70° field of view one
  map pixel is one screen pixel at 1080p (the sharpest a map can be drawn), and the whole frame — title plaque to
  cartouche — fits between two boss bars and the hotbar at GUI scale 3 and 4.
* **While it is open** the player's weather is clear (no rain or snow across the map; their own weather comes back on
  close), the HUD panel and the target frame step aside (`HudService.quiet`) and so do the waypoint's bar and label.
* **The map plane.** An invisible item frame draws its map flush with the block behind it — the client's item frame
  renderer puts it 0.5 from the block centre, a visible frame's 0.4375 — one map pixel (1/128) in front of that face.
  The parchment, the frame art and the labels are placed against that plane. (Placed against a visible frame's plane,
  the parchment hid all 25 maps on the real client although every map packet arrived — the bot could not tell.)

* **Maps.** 25 map views are created once and kept for good (`plugins/SULD/map/views.yml`; marked by a centre no
  real map has, so a stale id is never mistaken for a player's map). Each view's renderer is per player, so every
  open atlas shows its own place in the same 25 ids. A frame is cut from up to four world tiles (`AtlasView.copy`)
  and sent at once (`Player#sendMap`); a tile that changes while the atlas is open (a chunk read) redraws it within
  a second.
* **View.** Opens centred on the player at zoom 2 (4 blocks a pixel, 2 560 blocks across); the wheel zooms toward the
  cursor from 1 block a pixel to the whole world (16 blocks a pixel: the 10 001-block square fits the wall), W/A/S/D
  pan a quarter of the wall (again every 6 ticks while held). The view never leaves the world square.
* **Cursor.** The crosshair: the cartouche reads the block under it — `x · z`, the area and its level band
  (`Хархорум` in the city) — and a place under it shows a tooltip (kind, area, distance, coordinates). A left click on
  a place points the compass at it.
* **Places.** The real places of part A as marker quads (ger, cave, cairn, stele, horned skull, banner, crossed
  swords) with labels where they fit (city, dungeons and bosses always; sites up to zoom 2; ovoos, relays and the
  training yard up to zoom 1); the player's own arrow points the way they faced.
* **The party.** The other members of the player's party where they are now: light blue arrows the way they look,
  with their names, under every tab (they are not a kind of place). Their markers move within a second when one
  moves a block or turns. A click on one starts the waypoint to where that mate stands. Only the player's own party
  shows, and only members online in the same world who are not standing in for themselves in the training yard.
* **Tabs.** Left: Бүгд, Хот+өртөө, Агуй, Овоо, Түүхэн газар, Аварга дайсан, Миний тэмдэг (which places show). Right:
  Бүх ертөнц, Миний байр, Ойртуулах, Холдуулах, Тэмдэг тавих (part C).
* **Where it does not open:** outside the overworld, in a dungeon run, within 8 s of a fight, falling or riding.
* **Art:** `tools/map/atlas_art.py pack` writes the textures at 128 px a block like the maps (sizes multiples of 16),
  the models and the item definitions (`suld:entity/atlas/*`).
* **Region names.** From 8 blocks a pixel out (the two widest views), the eight wild regions are named across
  their lands, as an atlas names its countries: faint gold capitals («ГОВЬ», «ХӨВСГӨЛИЙН ТАЙГА»…) half way out the
  region's ring and half way round its arc, behind the places and their labels. The city keeps its own label.
* **Bot:** `tools/loadtest/bots/atlasbot.js` (in `regress.sh`): the stage, the 25 frames and their map packets with
  real pixels, pan, zoom, no region named close up and all eight at the whole world, the «Агуй» filter, Shift.
  12 checks pass on the dev server and on a second one (2026-10-13).

## Part C — pins, waypoints, minimap (built)

### Pins «Миний тэмдэг»

`/map pin [нэр]` (or «Тэмдэг тавих» on the atlas, then a click on the map: a dialog asks the name) stores a pin at the
block: name up to 24 letters (a blank name becomes «Тэмдэг N»), one of five colours in turn, at most
`MapPin.MAX_PINS` = 30 a player (`suld-api` `MapPin`). Table `suld_map_pins` (V26, PostgreSQL / MySQL); the insert
counts the player's pins in the same statement (`INSERT … SELECT … WHERE (SELECT COUNT(*) …) < 30`), and `PinService`
lets a player have one change in flight, so the limit holds exactly. Pins are read when a player joins and kept for
the atlas (a pin marker with its name; shown under «Бүгд» and «Миний тэмдэг») and the minimap while they are online. `/map pins` lists them with
clickable «[Чиглэх]» «[Устгах]»; a pin on the atlas opens «Чиглэх / Устгах».

### The waypoint

`/map guide <нэр|#|x z>`, a click on a place or a pin in the atlas (`WaypointGuide`): one per player.

* A **yellow boss bar** «◆ Нэр  ↗ зүүн хойд · 412 м» — the arrow relative to where the player faces, the compass point
  and the distance (`api.region.Navigation`, like the dungeon guide); the compass points there too.
* A **label** «◆ Нэр / 412 м», seen through blocks and by its owner only: at the place itself within 24 blocks,
  otherwise 24 blocks along the way at eye height; it grows with the distance (×0.125 a block, ×3 at 24) so it reads
  the same on screen (≈ 20 px at 1080p).
* Within 6 blocks «ирлээ!» and it ends; `/map stop` ends it; it never outlives the session (a crash leaves no label:
  the tag is swept when entities load).

### The latest death

The atlas reads the player's latest death (`deathRepository().recent`) when it opens and marks it «Сүүлд унасан газар»
(under «Бүгд»); a click on it starts the waypoint there. The minimap marks a standing soul beacon.

### The minimap (top left, while `/map` is closed)

* **Picture** (`suld-api` `Minimap`, pure): one 128 × 128 map image — a 120 × 98 window of the real land at one block
  a pixel (the zoom-0 tiles of part A, so it shows exactly what the atlas shows), north up; a black-gold-blue-gold
  frame with a gold north notch; places within 72 blocks (city, dungeons and bosses, ovoos, sites and relays, the
  player's pins, the soul beacon); the party's other members as light blue arrows the way they look, or a light blue
  arrow on the rim toward a mate out of view (a mate who moves or turns redraws it, at the same pace as the player's
  own moves); the waypoint as a gold diamond, or a gold arrow on the rim pointing to it; the
  player's white arrow in the middle, turned the way they look; under the map a navy panel with «X 326  Z 92» and the
  area's name in the pack's 5 × 7 pixel font (`PixelFont`, generated from `tools/pack/pixelfont.py` by
  `tools/map/gen_pixel_font.py`).
* **On the screen:** the map hangs in three invisible glow item frames that only its owner sees, facing up, 7.5
  blocks out, all above the look (`MinimapService.SLOTS`): one 14° up straight ahead, always in view (a 16:9 screen
  sees ±51° across and ±35° up and down), and two 50° to the sides, which come into view while the player turns
  faster than the server moves the frames. None is within reach of a click, and none is on the line of fire: an
  arrow leaves 0.1 under the eye along the look and only drops (a frame snaps to its block, so a side frame stands
  at least 0.33 over the eye). A frame is still a real entity on the server, and Paper lets a projectile hit what its
  shooter can see (a mob sees everything): such a hit is cancelled and the frame steps 4 blocks up for a second, so
  the projectile flies on — a cancelled hit alone would hold a plain arrow on the frame's face — and a frame never
  shields its owner. The pack's own `rendertype_text`
  core shader (written fresh from vanilla 1.21.11's; NMinimap showed the technique, its GPL code is not used) checks
  every 128 × 128 texture it draws for the four marker colours of row 0 (`Minimap.MARK`); that quad is moved into the
  top-left corner in screen space at a whole number of screen pixels per map pixel (2 at 1080p, 3 at 1440p, 4 at
  2160p, 1 below 900 px high), drawn with the map's own colours (no light, no fog), and row 0 is hidden. Every other
  text, sign and map is drawn exactly as vanilla draws it.
* **When:** only with the SÜLD pack loaded (without the shader the map would hang in the air), the «Мини газрын
  зураг» setting on (`PlayerSetting.MINIMAP`, `/map minimap`), in the overworld, alive, and outside the sky windows
  (atlas, menu, skill tree). Redrawn on the map's workers when the player moves to another block or turns (at most
  every 4 ticks, at least every 10 s) and at once when the waypoint or a pin changes; the frames follow the player
  (the server moves them when their block changes).
* **Seen on the 1.21.11 client:** the corner map in the city, by night (no lightmap: it stays bright) and in the
  steppe; hidden while dead and in the atlas, back after; with this layout drawn in all 72 screen grabs taken during
  fast turns (~33° and ~90° a step, looking up and down). A two-frame layout at ±28° blinked in the same test and the
  first layout had a frame on the line of fire, so neither was kept. A modded client that culls entities by occlusion (Sodium's entity culling) or replaces the core shaders
  (an Iris shader pack) may hide it — vanilla and plain Fabric are fine.

### Bots

`tools/loadtest/bots/mapcbot.js` (in `regress.sh`): the three frames facing up out of reach, the marker row, the frames
follow a 30-block teleport and a turn and stay above the line of fire, an arrow shot through a frame flies on past it,
`/map minimap` off and on, hidden in the atlas, the waypoint bar with the distance and the label 24 blocks out,
`/map stop`, a pin stored, still there after a relog (PostgreSQL), removed — 13 checks pass on the dev server
(2026-10-13). `JdbcMapPinRepositoryIT` (the limit under concurrency), api
`MapPinTest`, `MinimapTest`.

`tools/loadtest/bots/partymapbot.js` (in `regress.sh`): two bots in one party, floating over the steppe. A's minimap
shows B as a light blue arrow where B is (none before the party), and an arrow on the east rim when B is 2 000 blocks
east. A's atlas shows B's name at B's place and moves it when B moves. Once B leaves the party, neither the atlas nor
the minimap shows B. 7 checks pass (2026-10-13).
