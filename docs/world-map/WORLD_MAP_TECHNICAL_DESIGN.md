# World map — technical design

**Status: DESIGN (2026-10-13).** Player-facing rules: `SULD_WORLD_MAP_GDD.md`. Facts: `WORLD_MAP_CURRENT_STATE.md`.
Numbers marked *estimate* are to be measured in Phase 0; none here is a measurement.

## 1. Where the code goes

| Layer | Package | Contents |
|---|---|---|
| suld-api (pure Java, tested) | `mn.suld.api.map` | `MapFrame` (world ↔ cell ↔ panel transforms), `TerrainClass`, `TerrainGrid` (+ downsampling), `Autotile` (coast masks), `AtlasText` (row → glyph string), `Clusters`, `MapPoi`, `PoiKind`, `Visibility`, `PinRules`, `MapPin`, `Waypoint` |
| suld-api | `mn.suld.api.region.DangerTier` | the HUD's gap → word rule, moved out of `HudService` so the HUD and the atlas share it |
| suld-api | `mn.suld.api.persistence` | `MapPinRepository` + `InMemoryMapPinRepository` |
| suld-plugin | `mn.suld.plugin.map` | `TerrainService` (grid build + refine + file), `PoiRegistry` and its sources, `AtlasSky` (the screen), `GuideService` (the guide line; `DungeonGuide` becomes a client of it), `WaypointBeacons`, `MapPinService`, `MapCommand` |
| suld-plugin | `mn.suld.plugin.gui.SkyStage` | the seat camera, owner-only spawning, ray cursor, input, crash-safe return — extracted from the code `MenuSky` and `SkillSky` copy today |
| suld-plugin | `persistence.JdbcMapPinRepository`, `db/migration/*/V26__map_pins.sql` | storage |
| resource pack | `tools/pack/atlas_kit.py` → `assets/suld/font/atlas.json`, `textures/font/atlas/*.png`, the book frame | tiles, icons, frame |

## 2. Coordinates

* **World → spawn-relative:** `dx = x − (sx + 0.5)`, `dz = z − (sz + 0.5)` with the border's live centre
  (`WorldBorderService`), so the atlas follows a spawn move exactly like the border and the regions do.
* **Cells:** `CELL = 16` blocks; the bordered square of diameter `D` (10 000) is `N = ceil(D / 16) = 625` cells a side;
  cell `(i, j)` covers `dx ∈ [−D/2 + 16i, −D/2 + 16(i+1))`, same for `dz` with `j`. North is `j = 0`.
* **Zoom level `z` (0…4):** one tile = `16 · 2^z` blocks; the level-`z` grid is `ceil(N / 2^z)` cells a side
  (625, 313, 157, 79, 40).
* **Panel:** the atlas plane faces the camera; panel `u` (right) = world **+x (east)**, panel `v` (up) = world **−z
  (north)**. This is set explicitly because the sky camera faces +z, where the raw screen-right is world −x
  (`MenuSky.java:260-263`); a test pins it (a place east of the player must be to the right).
* **Window:** 40 × 25 tiles. Its top-left cell at zoom `z` is `(cu − 20, cv − 12)` around the centre cell `(cu, cv)`;
  panning moves the centre in whole cells of the current zoom and clamps it to the grid.
* **Cursor → world:** the ray from the eye meets the plane at `(u, v)`; `x = x0 + (u / tileWidth) · 16·2^z`,
  `z = z0 − (v / tileHeight) · 16·2^z`. Shown on the ribbon as integers (reference 1 shows `−1803, −4902`).

## 3. Terrain

### 3.1 Classes

One byte per cell: bits 0–4 the class, bit 5 **verified** (classified from real blocks), bit 6 water-edge hint, bit 7
reserved. Classes: `OUTSIDE` (beyond r 5000 or the border), `SEA`, `LAKE`, `RIVER`, `BEACH`, `STEPPE`, `PLAINS`,
`FOREST`, `TAIGA`, `HILLS`, `MOUNTAIN`, `PEAK`, `DESERT`, `BADLANDS`, `SNOW`, `SWAMP`, `CITY`, `RUIN`. The biome →
class table is data (`content/atlas.json`, validated by the content loader), so a new biome needs no code.

### 3.2 Building the grid

1. **Predicted pass (whole square).** `TerrainService` walks all 390 625 cells and samples
   `world.vanillaBiomeProvider().getBiome(world, x, 64, z)` at the cell centre and 4 sub-points (water and mountain win
   ties). Whether that provider may be called off the main thread is checked in the Phase 0 spike; if not, the job runs
   on the main thread in slices of at most 2 ms a tick. Either way it is resumable (a cursor in the grid file).
2. **Verified pass (as chunks exist).** On `ChunkLoadEvent` for a generated chunk whose cell is not verified, a
   `ChunkSnapshot` (heightmap + biome, taken on the main thread) is classified off-thread: real water surface, height
   above sea level (hills/mountain/peak), snow, and the built city footprint (`SuldServices.inCity`). The cell's byte
   gets bit 5. Pre-generation therefore verifies its 14 940 chunks for free.
3. **Downsampling** to levels 1–4 by priority (water > mountain > city > the majority), recomputed for the affected
   parent cells when a cell changes.

### 3.3 Storage

`plugins/SULD/map/terrain-<world-uuid>.bin`: a header (magic `SULDATLS`, format 1, world UUID, seed hash, border centre
and diameter, `CELL`, the predicted-pass cursor, CRC) followed by the 625 × 625 bytes (≈ 390 KB) and the downsampled
levels (≈ 130 KB). Written atomically (temp file + move, the project's `AtomicYaml` pattern) off the main thread, at
most once a minute and at shutdown. **Invalidation:** a different seed hash, border centre or diameter rebuilds it;
block changes by players are not tracked in the first version (the scale is 16 blocks a cell); `/suld map terrain
refresh <radius>` re-verifies loaded chunks around a staff member.

## 4. Drawing with glyphs

* **Font `suld:atlas`:** bitmap providers of 128×128 sheets (8 × 8 glyphs of 16 px each, so no texture exceeds the
  128-px budget rule), ascent tuned in the spike; its own code-point sequence from U+E000 (a separate font, so it does
  not touch `suld:ui`'s append-only list). Glyphs: ~18 classes × 3 variants, 16 coast masks per water class, the
  survey (light) ink set, 32 marker icons, digits for clusters, the player arrow in 16 directions. *Estimate: under
  200 glyphs, 4–6 sheets, well under 200 KB.*
* **Tiling:** a bitmap glyph advances its width + 1, so every tile is followed by the existing −1 px space glyph;
  a row of 40 tiles is 80 code points. One `TextDisplay` per row (25), `lineWidth` above the row width, left
  alignment, no background, no shadow, brightness 15, one shared scale (≈ 0.6 so the page is ~10 blocks wide at the
  panel distance; tuned in the spike).
* **Autotile:** for water classes the glyph is chosen by a 4-bit mask of land neighbours (N, E, S, W) — the coast
  follows the land; for land, the variant is a hash of the cell so it is stable while panning.
* **Updates:** a pan or zoom recomputes the 25 strings (pure `AtlasText`, microseconds) and resends only the rows that
  changed; at most 10 updates a second per viewer.
* **On top of the rows** (slightly in front of the plane): marker glyphs as small `TextDisplay`s (billboard fixed),
  the player arrow, party arrows, labels in `suld:hd`, the tooltip, and the frame (one large `ItemDisplay` texture plus
  the tab and tool buttons). *Estimate per open: 25 rows + ≤ 60 markers + ≤ 30 labels + ~25 frame and buttons ≈ 140
  entities* (SkillSky: 480, measured).

## 5. The screen: `AtlasSky` on `SkyStage`

* `SkyStage` (extracted, no behaviour change for MenuSky and SkillSky): stage above the player's column, seat and
  `RETAIN_PASSENGERS`, `setVisibleByDefault(false)` + `showEntity`, the eye-ray hit, held WASD flags, wheel steps with
  the held slot cancelled, swing/use clicks with debounce, Shift → close, PDC return point, orphan sweep, the anticheat
  hold. The atlas **cancels the swings and interacts it consumes**, so a class weapon in hand cannot fire a spell combo
  (current-state finding 5).
* **Dialogs keep the stage.** A pin dialog opens over the atlas; the seat stays; input is paused while it is open and
  the stage redraws when it closes (MenuSky's "a dialog closes the sky" routing does not apply here).
* **Refusals:** the sky windows' own (combat 8 s, dungeon run, vehicle, gliding, falling, dead) plus souls and
  trainees; the fallback is the chest travel page and a chat reason.
* **Opening paths:** `MapCommand` (`/map`, aliases in `plugin.yml`), `MenuListener`'s swap guard on the «Сүлд Цэс»
  clock becomes «F → atlas», and a card on `Menus.travel`'s free tile.

## 6. Places: `PoiRegistry`

* `MapPoi(String id, PoiKind kind, String name, String detail, int x, Integer y, int z, int level, int partyMin,
  int partyMax, Visibility visibility, String icon, String refId)` — ids are the authoritative ones with a kind prefix
  (`dungeon.khasar_den`, `site.aurag`, `ovoo.area.tuul`, `npc.blacksmith`, `relay.gate_south`,
  `boss.worldboss.ikh_baavgai`, `landmark.*`, `city.kharkhorum`).
* **Sources** read the owners, never copy coordinates: `DungeonHalls.gate`, `HistoricSiteService.where`,
  `OvooService.positions`, `WorldBuildService.pointLocation` (+ the 7 slice landmarks parsed once from the jar's
  `slice-1.json`), `NpcService` relays and roles, `WorldBossService.bosses`, `config training.*`, the content file
  `landmarks.json` (§9). The registry rebuilds its snapshot on start, after the city is built, after any of the
  owners re-places something (gates on a spawn move, a site built), and every 60 s; a moved boss or gate is therefore
  never stale for more than a minute.
* **Per-player view:** `Visibility` rules from the GDD §8 evaluated in `mn.suld.api.map.Visibility` from plain
  inputs (discovered mask, marks, quest target, party, hint band), so they are unit-tested. Relic shrine coordinates
  are never put in a `MapPoi`; the relic source emits only the band shape the player's own hint revealed.
* **Clusters:** markers are bucketed by the screen cell of the current zoom (`Clusters`, pure); a bucket with more
  than one becomes a numbered badge.

## 7. Danger

`DangerTier.of(gap)` returns `EASY (≤ −6) / LOW (−5…−2) / FAIR (−1…+2) / HIGH (+3…+5) / DEADLY (≥ +6)` with the
words «Хялбар, Бага, Тохиромжтой, Өндөр, Үхлийн!», plus `SAFE` for the city. `HudService.danger` calls it (behaviour
unchanged, its test moves with it). Inputs per kind: area → `WorldContent.localLevel` at the point; dungeon → the
rung's lower band (`DungeonLadder`); world boss → its mob level; ovoo → its area.

## 8. Pins and the destination: storage

`V26__map_pins.sql` (PostgreSQL shown; the MySQL file uses `CHAR(36)`, inline keys, InnoDB utf8mb4):

```sql
CREATE TABLE IF NOT EXISTS suld_map_pins (
    player_uuid UUID        NOT NULL,
    pin_id      UUID        NOT NULL,
    world       VARCHAR(64) NOT NULL,
    x           INT         NOT NULL,
    y           INT,
    z           INT         NOT NULL,
    icon        VARCHAR(24) NOT NULL,
    name        VARCHAR(24) NOT NULL,
    note        VARCHAR(100) NOT NULL DEFAULT '',
    in_world    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  BIGINT      NOT NULL,
    updated_at  BIGINT      NOT NULL,
    PRIMARY KEY (player_uuid, pin_id)
);
CREATE TABLE IF NOT EXISTS suld_map_waypoint (
    player_uuid UUID        PRIMARY KEY,
    kind        VARCHAR(16) NOT NULL CHECK (kind IN ('PIN', 'POI', 'QUEST', 'DEATH')),
    ref         VARCHAR(64) NOT NULL,
    world       VARCHAR(64) NOT NULL,
    x           INT         NOT NULL,
    y           INT,
    z           INT         NOT NULL,
    label       VARCHAR(48) NOT NULL,
    set_at      BIGINT      NOT NULL
);
```

* `MapPinRepository`: `load(UUID)`, `upsert(MapPin)`, `delete(UUID, UUID)`, `count(UUID)`, `setWaypoint`,
  `clearWaypoint` — CompletableFutures on the shared io pool with a per-player `KeyedSequencer` (a player's writes stay
  in order), dialect upserts, the 23/40 retry; the in-memory twin is the oracle in `JdbcMapPinIT`.
* `MapPinService` follows `WeaponMasteryService`: read at join (main-thread hop, `loaded` guard), write-through on each
  edit (rare and small), nothing cached after quit. The 32-pin limit is checked in `PinRules` and again in the
  repository (`count` in the same transaction as the insert), so a double click cannot make a 33rd pin. A failed
  write leaves the in-memory pin marked unsaved and tells the player.
* `PinRules`: name 1–24 code points after trimming, no control or formatting characters, note ≤ 100; coordinates
  inside the live border; world = the main world; icon from the tileset's list; a token bucket (`api/web/RateLimiter`)
  of 20 edits a minute per player. Training stand-ins and souls cannot edit.
* Staff: `/suld map pins <player> list|clear` (`suld.admin.map`, audited as `map.admin.pins_clear`).

## 9. Custom landmarks (admin)

`content/landmarks.json` (`"format": 1`), loaded by `ContentLoader` like `sites.json`: id `landmark\.[a-z0-9_]{2,40}`,
name, description, kind (one of the categories), position as plaza bearing + radius (the content convention) or a
city point id, optional level, party size and lore note, `visibility` (`public`, `discovered`, `hidden`), icon. The
loader refuses positions outside r 4900, unknown kinds, icons or city points, and duplicates. Edits go into
`plugins/SULD/content/landmarks.json`, are checked with `/suld content validate`, and apply on restart (the project
has no live content reload). `/suld map poi near [r]` lists the registry around a staff member with ids and sources.

## 10. Guide line and in-world labels

* **`GuideService`** generalises `DungeonGuide`: one target per player (`Waypoint`: kind, ref, label, x, y, z), one
  boss bar in the slot the dungeon guide uses, text from `Navigation` (arrow, compass word, metres) plus «▲/▼ N» when
  `y` is known, every 10 ticks; ends on arrival (≤ 10 m), `/map stop`, a run start (as today), or when the target
  disappears (a pin deleted, a beacon expired). The current target is stored in `suld_map_waypoint`.
* **`WaypointBeacons`:** per player at most 4 owner-only `TextDisplay`s (icon glyph + name + distance), billboard
  centre, **see-through**, brightness 15; each 2 ticks: for a target farther than 24 blocks the label is placed 24
  blocks from the eye along the bearing (height from the eye towards the target's height, clamped) at a scale that
  keeps its size; within 24 blocks it stands on the target. Within 160 blocks the soul-beacon style beam marks the
  spot. Teleport duration 2 for smooth motion. Removed on quit, world change and plugin disable; tagged and swept like
  the sky entities.

## 11. Performance plan (what to measure, not results)

| Item | Budget (target) | How measured |
|---|---|---|
| Predicted terrain pass, whole square | runs once; must not lift MSPT above 40 ms p95 | `PerfProbe` `map.terrain.predict` per slice; spark during the job on the dev server and the VPS |
| Verified pass per chunk | < 0.1 ms main-thread (snapshot only) | `PerfProbe` `map.terrain.snapshot` during pregen |
| Atlas open | < 5 ms main thread | `PerfProbe` `map.open` |
| Pan / zoom step | < 1 ms main thread, ≤ 25 metadata packets | `PerfProbe` `map.pan`; packet count from the bot |
| Bytes per open | *estimate 10–20 KB* | Paper's network stats with one viewer |
| Beacons | < 0.05 ms per player per update | `PerfProbe` `map.beacons` with 25 bots navigating |
| Pins | 1 read per join, 1 write per edit | query counts in the IT |
| Memory | grid ≈ 0.5 MB per world; ≤ 32 pins per online player | heap histogram |
| Concurrent viewers | 25 bots with the atlas open and panning: TPS and MSPT unchanged within noise | `tools/loadtest` run on the VPS class host |

## 12. Security and correctness

* Every input is server-side: the cursor is the server's own ray; text arrives only through dialog callbacks and is
  validated by `PinRules`; there is no client-supplied coordinate.
* Hidden data never leaves the server: relic shrine coordinates and unbuilt `secret.*` points are not in any packet;
  undiscovered area names are sent as «?».
* A player can touch only rows with their own UUID; staff tools check `suld.admin.map` in code and are audited.
* Per-viewer entities are invisible to everyone else (`setVisibleByDefault(false)`).

## 13. Failure and recovery

| Failure | Behaviour |
|---|---|
| Terrain file missing or wrong header | rebuilt in the background; the atlas opens with what is known |
| Terrain job interrupted by a restart | resumes from the stored cursor |
| Database down at join | the pins are not loaded (the atlas says so); no write is attempted over unknown state |
| Write fails | the pin stays marked unsaved; the player is told; retried on the next edit |
| Disconnect or crash with the atlas open | the existing PDC return point puts the player back on the next join; tagged entities are swept |
| Spawn or border moved | the grid rebuilds (header mismatch); POI sources re-place their own things; the registry refreshes |
| Pack not loaded | the atlas does not open; the plain chat list of places with directions is shown |

## 14. Concurrency audit

| State | Owner / thread | Why it is safe |
|---|---|---|
| Terrain grid bytes | `TerrainService`; written by the predicted job and the snapshot classifier | single writer per cell through a lock-free `AtomicIntegerArray`-backed store; readers tolerate a cell changing between frames |
| Grid file | the io pool | one write at a time (a flag), atomic replace |
| POI snapshot | main thread | rebuilt and swapped as an immutable list |
| Pins of a player | main thread cache; DB on the io pool | per-player `KeyedSequencer`; the count check in the insert's transaction |
| Atlas sessions, beacons | main thread | Bukkit entities only touched on the main thread |
| Waypoint | main thread; DB write async | last write wins per player (one row, primary key) |
