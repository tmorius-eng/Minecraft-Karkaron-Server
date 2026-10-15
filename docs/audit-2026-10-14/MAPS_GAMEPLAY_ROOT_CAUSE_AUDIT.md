# SÜLD Atlas `/map(s)`, waypoint, minimap and gameplay: root-cause audit (2026-10-15)

Session: local CLI, one agent, no subagents. The audit was done on local `main` @ `c1fcc62`, which had diverged from
`origin/main` `f4c071b` (1 ahead, 5 behind). In a follow-up session the same day, local `main` was rebased onto
`f4c071b`, and `ArrowScaleTest.java` was restored to its committed test. The full suite then passed in the main working
tree, and this work was committed on top of origin (see `EXECUTION_CHECKPOINT.md`, "Run 2026-10-15" and "Follow-up
2026-10-15").

Legend: **Fixed+tested** = code changed, and an automated test covers it and passed. **Fixed (compile-verified)** =
code changed, the full suite compiles and passes, but no automated test can drive it without a running server.
**Not reproduced** = traced end to end and no defect found in the code. **Open** = documented, not changed.

## 0. What the atlas really is

There is no `./maps` directory in the repository. The in-game atlas is a Paper plugin feature with no client mod. The
pieces:

| Layer | Implementation |
|---|---|
| Command | `plugin.yml` `map` (aliases until now `atlas, gazar, газар`), executor `map/AtlasSky.onCommand`, registered in `SuldPlugin` (`registerTab("map", atlas)`, only with `map.enabled`) |
| Screen | `AtlasSky`: the sky-window engine. The player is seated on an `ItemDisplay` 144 blocks over the world's top, facing a wall of 5 × 5 invisible glow item frames holding filled maps, with display quads (frame art, tabs, markers) and text displays |
| Input | wheel = cancelled `PlayerItemHeldEvent` (`onWheel`); W/A/S/D = `PlayerInputEvent` (`onInput`) → `tick()` pans every 6 ticks while held; left click = arm swing (`onSwing`) on the crosshair's ray–plane hit (`aim`); Shift = dismount = close |
| View maths (pure) | `suld-api` `AtlasView` (panel ↔ world, `zoomAt`, `pan`, `clamp`, `copy`, `tilesOf`), `MapGeometry` (world-border square, tiles) |
| Terrain data | `MapService` (chunk snapshots) → `ChunkColorStore` (region files, `columnBlocking`) → `MapTiles` (tile cache) → `TileComposer` |
| Rendering | `AtlasFrames`: 25 persistent `MapView`s with one per-player `MapRenderer` each, `Player#sendMap` |
| Waypoint | `WaypointGuide`: a yellow boss bar (arrow relative to facing + compass word + metres), a see-through `TextDisplay` label (at the place within 24 blocks, otherwise 24 blocks along the way), and the compass |
| Minimap | `MinimapService`: three owner-only glow item frames out along the look, carrying one map (`suld-api` `Minimap.draw`). The pack's `rendertype_text` core shader detects the marker row and moves the quad to the top-left corner of the screen |
| Shared data | everything reads the same `MapTiles` (zoom 0 for the minimap), `MapPois`, `PinService`, `WaypointGuide.target`, `MinimapService.mates` |

## 1. Reported symptoms → root causes

| # | Symptom | Root cause found | Status |
|---|---|---|---|
| A | Zoom in/out "does not work correctly" | (1) **The atlas could not zoom in past 1 block a map pixel**: `AtlasView` was limited to `MapGeometry` zooms 0…4, the finest the store holds. (2) **At either end a wheel notch or zoom tab did nothing at all** (`zoomAt` returns the same view, `setView` returns early): no sound, no text, no change, so it read as broken. (3) The scale was only shown in the cartouche while the crosshair was *off* the wall. The handler path itself (cancelled `PlayerItemHeldEvent` → `zoomAt` toward the cursor → `redraw`) is correct: it was traced, unit-tested, and `atlasbot.js` drives it with raw packets. | **Fixed+tested**: zooms −1 and −2 (2 × 2 and 4 × 4 map pixels a block, cut from the zoom-0 tiles); one `zoom()` helper for wheel and tabs that shows the new scale on the action bar and says «Хамгийн ойр / Хамгийн хол» at the ends. `AtlasViewTest.closeUpZoomsMagnifyTheOneBlockTiles`, `aMagnifiedFrameDrawsEachBlockAsASquareOfPixels`, `AtlasInputTest.everyZoomHasItsOwnReadOut` |
| B | WASD does not move the viewport | **Not reproduced in code.** `onInput` stores the four keys from `PlayerInputEvent`; `tick()` pans by ¼ of the wall at once and every 6 ticks while held; W = north/up, A = west/left, S = south/down, D = east/right (`pan(du, dv)`: `centreZ − dv·span`). No other listener handles `PlayerInputEvent` (only MenuSky and SkillSky, each gated on its own session). The handler runs only while that player's atlas session exists, so movement is untouched with the atlas closed. Chat and text inputs are not affected (vanilla does not send movement input while a screen is open). Two real limits look like "WASD broken": at the whole-world zoom (4) the world fits the wall, so `clamp` keeps it centred and panning cannot move it (by design); and the opening view was zoomed so far out that a ¼-wall step jumped 640 blocks. | Covered by `AtlasViewTest` pan assertions (now also at zoom −2); behaviour unchanged |
| C | Atlas excessively zoomed out | **`AtlasSky.open` built the view at zoom 2** (4 blocks a map pixel, 2 560 blocks across the wall ≈ 640 screen px at 1080p). The city is a speck at that scale. The map draws real data only, so most of that window outside the pre-generated area (~3.8 % of the world by default) is parchment. | **Fixed+tested**: `OPEN_ZOOM = 0` (640 blocks across, around the player), and two closer zooms below it (`AtlasInputTest.theAtlasOpensCloseEnoughToReadTheLand`) |
| C′ | Distant content invisible | **`ChunkColorStore.columnBlocking` answered 0 ("never generated") when a region read did not finish within 10 s.** The single io thread is shared with flushes, evictions and the scan, so a busy io queue drew real land as blank parchment. `MapTiles` then **cached that tile with the hole** until a chunk under it changed. The far zooms read up to 16 regions per tile through a 32-region LRU, so they were the most exposed. | **Fixed+tested**: the timeout now throws `RegionUnavailable`. The tile future fails and is not cached; an open atlas marks itself dirty and asks again within a second; the minimap retries on its next draw. `ChunkColorStoreTest.aRegionReadThatTimesOutIsNotCachedAsBlankLand` |
| D | Selected waypoint not usable on screen | **Deleting a pin left a waypoint leading to it alive**: `PinService.remove` → callback `refreshFor` redrew only the atlas markers, and `WaypointGuide.Target` carried no pin identity. The bar, the 24-block label, the compass and the minimap's gold diamond kept pointing at a pin that no longer existed. **The compass was never handed back** when a waypoint stopped or was reached. **The bar and the see-through label stayed up in the menu and the skill tree** (`waypoints.busy` checked only the atlas), floating over those windows. The compass target was built in the player's current world rather than the waypoint's. | **Fixed+tested** (pin identity: `AtlasInputTest.aWaypointKnowsThePinItLeadsTo`) / **Fixed (compile-verified)** (compass hand-back, sky-window and death hiding): `Target.pin` + `leadsTo`, `WaypointGuide.pinRemoved` called from both unpin paths (`/map unpin`, the pin dialog); `stop()` resets the compass to the world spawn if it is still ours; `busy` = atlas ∨ menu ∨ skill tree (as the minimap already did); hidden while dead |
| E | Minimap does not appear on the left | **No code defect found.** Traced: registration (`SuldPlugin`, `map.enabled`), lifecycle (`tick` every tick, state dropped on quit, rebuilt on join and respawn), the setting (`PlayerSetting.MINIMAP`, on by default, page 2 of «Тохиргоо»), position (top-left corner, by the pack shader; the HUD panel sits above the hotbar and chat is bottom-left, so nothing collides). It is shown **only** when (a) the server sent the SÜLD pack and the client answered `SUCCESSFULLY_LOADED` for it, (b) the player is in the overworld, alive, not a spectator, (c) no sky window is open, (d) the setting is on. Without (a) the frames would hang in the air, so they are not spawned; the pack HUD panel uses the same gate. Iris shader packs (they replace core shaders) and Sodium-family entity culling hide it, as `docs/WORLD_MAP.md` already states. | **Not reproduced.** Check on the client: `/suldpack status` must be `SUCCESSFULLY_LOADED`, run `/map minimap` (toggle), use vanilla or Fabric without Iris |
| F | Other map defects | `/maps` was not a command at all (unknown command); `/gazar`, `/газар`, `/suld:map` and `/atlas <sub>` while the atlas was open **closed it in `onCommand`, and then the command reopened it** (only `/map`, `/map …` and the bare `/atlas` were exempt) | **Fixed+tested**: alias `maps`; `AtlasSky.isAtlasCommand` checks every name (`AtlasInputTest.everyNameOfTheMapCommand…`) |

Coordinates and world identity, checked and found consistent:
* Atlas marker: `AtlasView.u/v`; player arrow: `s.origin`, the authoritative location saved before the seat.
* Minimap: block coordinates of `p.getLocation()`.
* Waypoint: the target's world name is compared on every update.
* Pins are stored with block x/y/z.
* The atlas and minimap read the same tiles and POIs. Only the overworld has a map (the atlas refuses to open elsewhere,
  the minimap hides). Respawn and teleport rebuild the minimap frames (`place()` re-spawns invalid ones). Reconnect
  rebuilds pins, minimap state and the guide (dropped on quit).

## 2. Additional confirmed gameplay defects

| ID | Pri | Defect | Evidence | Fix | Status |
|---|---|---|---|---|---|
| G-1 | P0 (build) | **Local `HEAD` `c1fcc62` does not compile**: `QuestTracker.line` declares `refusal` twice (a `String` at line 162 and a `Component` at line 171), so `:suld-plugin:compileJava` fails and no plugin test can run | `javac` error in this session | The same one-identifier rename `origin/main` already carries in `2018b2a` (`gateRefusalComponent`); `diff` of the two patches is empty, so a later merge with origin applies cleanly | **Fixed (compile-verified)**. After the rebase onto `f4c071b` it is origin's own commit, with no separate change |
| G-2 | P1 | **A party member inside a sky window is left behind when the leader starts a dungeon.** `DungeonService.begin` calls `teleportAsync` on a player seated on the window's stage. The window's `onDismount` cancels the dismount and schedules `close(p)`, which teleports the member back to where they opened it. The party enters the hall without them, but they count as a participant. | `DungeonService.java` `begin` member loop; `AtlasSky.onDismount` / `close(p, true)`; the same seat pattern in `MenuSky` / `SkillSky` | `DungeonService.closeWindows` (set to the same consumer `TrainingService` uses: closes atlas, menu and skill tree) runs for each member before the teleport | **Fixed (compile-verified)**: needs a live two-player run to observe |
| G-3 | P2 | Waypoint compass, sky-window and death leaks | see D above | see D | Fixed |

## 3. Tests added or changed

* `suld-api` `AtlasViewTest`: +2 tests (close-up zooms, magnified copy). Totals: 632 tests, 0 failures.
* `suld-plugin` `map/AtlasInputTest` (new, 4 tests): command names, zoom read-outs, opening zoom, waypoint pin identity.
* `suld-plugin` `map/ChunkColorStoreTest`: +1 test (a timed-out region read is not cached as blank land). Totals: 215
  tests, 0 failures, before the rebase.
* `suld-api` `QuestChainTest`: when rebasing onto `f4c071b`, the test kept origin's assertion (an unknown id with no
  hint is preserved) and added local `c1fcc62`'s assertion (`q -> false` recovers to the first chapter).
* Main working tree after the rebase, `.\gradlew.bat test --rerun simTest --rerun --no-daemon`, exit 0: suld-api 632,
  suld-plugin 221 (with origin's HUD tests), simTest 13. 0 failures, 0 errors. The Python validators and
  `git diff --check` pass. An earlier run of the same command failed once in the pre-existing flaky
  `JdbcMapPinRepositoryIT.theLimitHoldsWhenAddsRace`, which also fails on clean `origin/main` (§4).
* Bots (not in CI, not run here: they need the dev server): `atlasbot.js` now takes three more notches to reach the
  whole world from the new opening zoom; `partymapbot.js` keeps the mate 200 blocks out (on the wall at 640 blocks
  across).

## 4. Remaining / untested / unresolved

* **No real client was available.** Nothing here was seen on a 1.21.11 client: the new zooms, the action-bar read-out,
  the waypoint end on unpin, the dungeon window close, and the minimap. Steps: open `/maps`; wheel up three notches
  (expect «Zoom 2:1», «Zoom 4:1», «Хамгийн ойр (4:1)»); hold W/A/S/D at «1:1»; `/map pin a`, `/map guide a`,
  `/map unpin a` (bar and label vanish, «Тэмдэг устсан…»); a second player opens `/map` while the leader runs
  `/dungeon enter` (both arrive in the hall).
* B and E: not reproduced in code. If they persist on the owner's PC, capture the exact steps, the client mods and
  shader pack, `/suldpack status`, and the server log lines `Resource pack: <name> -> …`.
* Per-move cost: every pan or zoom removes and respawns all marker entities (`placeMarkers`) and asks for up to 36
  tiles, and superseded tile requests are not cancelled. Measure on the dev server before changing it. Not changed.
* Other cross-system teleports of a seated player (for example a world-boss or class-trial move started by someone
  else) were not audited one by one. The dungeon start is the one confirmed path that moves *other* players.
* `AtlasSky.onJoin` (crash recovery) always clears invulnerability; it does not restore the pre-atlas state. P3, not
  changed.
* Resolved in the follow-up: `ArrowScaleTest.java` was restored to its committed test (the owner had revoked the key),
  and the main working tree builds and passes.
* Pin limit race (pre-existing, P2, not changed): `JdbcMapPinRepositoryIT.theLimitHoldsWhenAddsRace` fails about half
  the time on `origin/main` too. 24 adds racing a limit of 5 stored 9–10 rows, where the test allows 8. So
  `JdbcMapPinRepository.add`'s `INSERT … SELECT … WHERE (SELECT COUNT(*) …) < ?` does not serialise concurrent adds.
  Fixing it needs a per-player lock or a serialisable transaction.
