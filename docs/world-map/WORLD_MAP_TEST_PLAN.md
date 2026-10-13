# World map — test plan

**Status: PLAN (2026-10-13).** Nothing here has been run yet. A row is "passed" only when its test has been executed
and observed; results go into `docs/STATUS.md` and the feature doc with the commit they ran on.

## 1. Layers

| Layer | Where | Runs in CI |
|---|---|---|
| Pure rules (JUnit) | `suld-api/src/test/java/mn/suld/api/map/*Test`, `region/DangerTierTest` | yes |
| Plugin unit tests | `suld-plugin/src/test/...` (content loader for `landmarks.json` and `atlas.json`, descriptor, permissions) | yes |
| Storage ITs | `JdbcMapPinIT` on H2 (every build) and PostgreSQL 16 (`SULD_TEST_PG_URL`) | yes |
| Pack validation | `tools/validation/run_all.py` (budget, atlas, code points) | yes |
| Bots | `tools/loadtest/bots/atlasbot.js` in `regress.sh` on the dev Paper 1.21.11 | no (dev server) |
| Real client | the owner's checklist (§6) | no |
| Performance | `PerfProbe` probes + spark on the dev server, then the VPS | no |

## 2. Coordinates and drawing (JUnit)

| Test | Checks |
|---|---|
| `MapFrameTest.knownPointsLandOnTheirCells` | the spawn, the four border midpoints and the corners map to the expected cells at every zoom, both ways (world → cell → world within one cell) |
| `MapFrameTest.northIsUpEastIsRight` | a point 100 blocks east of the centre is to the right on the panel; 100 blocks north is up (guards against the camera's +z mirror) |
| `MapFrameTest.panAndZoomKeepTheCursorPoint` | zooming in at a cursor keeps the world point under it within one cell; panning moves the window by whole cells and clamps at the border |
| `MapFrameTest.followsTheBorderCentre` | a moved spawn or a fixed `center` shifts every result by the same offset |
| `TerrainGridTest.downsamplePriorities` | water beats mountain beats city beats the majority; level sizes 625/313/157/79/40 |
| `AutotileTest.coastMasks` | each of the 16 neighbour masks picks its coast glyph; a lake of one cell gets the closed shape |
| `AtlasTextTest.rowIsExactlyFortyTiles` | 80 code points (tile + −1 space), only glyphs that exist in the font registry |
| `AtlasTextTest.variantsAreStableWhilePanning` | the same cell draws the same variant in two different windows |
| `TerrainClassTest.biomeTableCoversEveryBiome` | every `Biome` of 1.21.11 maps to a class (a new biome fails the test, not the map) |

## 3. Places, danger, visibility (JUnit + content)

| Test | Checks |
|---|---|
| `DangerTierTest` | the five words at the exact boundaries −6/−5, −2/−1, +2/+3, +5/+6, and `SAFE` for the city; the HUD gives the same word as before the move |
| `VisibilityTest.relicShrinesNeverPoints` | no rule, input or tab produces a relic shrine coordinate; a hint produces only its band |
| `VisibilityTest.areaNamesNeedDiscovery` | an undiscovered area is «?», a discovered one its name (from the 64-bit mask) |
| `VisibilityTest.partyOnly` | party arrows only for members of the same party |
| `ClustersTest` | two markers in one screen cell form one badge with the right count; filters change counts, never duplicate a marker |
| `ContentDataTest` (landmarks) | the bundled `landmarks.json` is valid; an id outside r 4900, an unknown kind/icon/city point, or a duplicate is refused with its path; the server override is read |
| `PoiRegistryTest` (plugin, fake sources) | one marker per source entry; a source that moves its entry moves the marker after a refresh; a removed entry leaves no marker |

## 4. Pins and the destination

| Test | Checks |
|---|---|
| `PinRulesTest` | name 1–24 after trim, control/formatting characters refused, note ≤ 100, inside the border, main world only, 16 icons only, the 20-a-minute bucket |
| `JdbcMapPinIT.roundTrip` | create, read, rename, move, delete; the destination row set and cleared |
| `JdbcMapPinIT.limitHoldsUnderRaces` | 8 threads inserting the 32nd pin at once end with exactly 32 rows |
| `JdbcMapPinIT.ownersAreSeparate` | a delete with another UUID changes nothing |
| `JdbcMapPinIT.survivesRestart` | a new repository over the same database reads the same pins |
| `GuideServiceTest` (pure part) | arrow and compass word from `Navigation`; ▲/▼ only when y is known; arrival at ≤ 10 m |
| bot: pin flow | `/map pin Тест` → `/map pins` lists it; reconnect → still listed; `/map stop` clears the guide line; setting a destination never changes the bot's position |

## 5. The screen (bot `atlasbot.js`)

1. `/map` → the bot rides the seat above its column (y > 300), the expected entity count appears for it only (a second
   bot sees none of them).
2. The 25 row texts are made of atlas glyphs; the row under the bot's own cell contains the player-arrow glyph at the
   centre column.
3. W/A/S/D (`player_input` packets) change the row texts by whole cells; the wheel (held-slot packets) changes the
   zoom step shown on the ribbon; the hotbar selection never moves.
4. Look + swing on a tab changes which markers exist; look + swing on a dungeon marker sets the guide line
   (`set_action_bar`/boss-bar packets read raw, as `heroicbot.js` does).
5. Right-click on the map → a `show_dialog` packet arrives; the pin is saved by a raw `custom_click_action` carrying the
   name field; it appears as a marker.
6. Shift (raw `player_input {shift:true}`) → back within 2 blocks of the start, 0 atlas entities left.
7. A swing with the class weapon in hand inside the atlas casts nothing (finding 5 of the current state).
8. In combat (a staff `damage` on the bot) `/map` is refused with the reason.
9. A label for an `in_world` pin is shown to the bot only, see-through, and moves when the bot turns.

## 6. Real client (owner) — `docs/qa/` checklist to write with Phase 4

GUI scale 2, 3 and 4 at 1080p and 1440p: the book fits, tiles meet with no seams, the coast reads, labels are legible,
the tooltip never hides the cursor, the predicted land looks lighter, tabs and tools are clickable, the wheel zooms,
WASD pans smoothly, Shift closes; the pin dialog is usable in Mongolian; in the world the labels show through hills,
keep their size, and do not flicker; the guide line matches the in-world label's direction.

## 7. Performance (run, then record; no number is assumed)

The rows of `WORLD_MAP_TECHNICAL_DESIGN.md` §11: the predicted terrain pass on the dev server and on the VPS; 25 bots
with the atlas open and panning (TPS, MSPT p95/p99, packets per second); 25 bots navigating with beacons; heap with
the grid loaded.

## 8. Failure scenarios → tests

| Scenario | Test |
|---|---|
| Malformed or out-of-border coordinates | `PinRulesTest` (refused); `MapFrameTest` (clamped cursor) |
| Invalid world | `PinRulesTest` (only the main world) |
| Too many pins / duplicate clicks | `JdbcMapPinIT.limitHoldsUnderRaces`; bot double-click creates one pin |
| Overlong or control-character names | `PinRulesTest` |
| Deleting another player's pin | `JdbcMapPinIT.ownersAreSeparate`; the service never builds a delete for a foreign UUID |
| Hidden content shown to the wrong player | `VisibilityTest`; bot: two bots, only the hinted one sees the band |
| Stale POI after a move or removal | `PoiRegistryTest`; bot: staff moves a gate (spawn change) → the marker follows after the refresh |
| Database failure while saving | `MapPinService` test with a failing repository: the pin is marked unsaved, the player is told, nothing is lost from memory |
| Disconnect or restart while the atlas is open | bot: kill the bot mid-session → on rejoin it stands at its start point; 0 orphan entities after the sweep |
| Mirrored axes | `MapFrameTest.northIsUpEastIsRight`; bot: a pin east of the bot appears right of the arrow |
| Map and gameplay disagree on a marker | the bot compares the dungeon marker's cell with `DungeonHalls.gate` (console `/suld map poi near`) |
| Missing pack / no client support | bot with the pack prompt unanswered: `/map` gives the plain list, opens no stage |
