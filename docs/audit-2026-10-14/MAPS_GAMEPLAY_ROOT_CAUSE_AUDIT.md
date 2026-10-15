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
| Minimap | `MinimapService`: four owner-only glow item frames around the look (three until §5), carrying one map (`suld-api` `Minimap.draw`). The pack's `rendertype_text` core shader detects the marker row and moves the quad to the top-left corner of the screen |
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

## 5. Follow-up 2026-10-15 (2): atlas framing, boss bars, minimap blinking and lag, accidental `/skills`

Session: local CLI, one agent, no subagents, on `main` = `origin/main` `0825c8f` (nothing to reconcile: the earlier
blockers, the ArrowScaleTest key and the QuestTracker merge, were already resolved and pushed in `0825c8f`/`f56dea3`).
Live reports from the owner's client. None of the fixes below was seen on a real client yet (§4 still applies).

| # | Sev | Symptom | Root cause | Fix | Status |
|---|---|---|---|---|---|
| H-1 | P1 | `/maps`: the atlas is small in a sea of navy; labels, pins, cartouche hard to read | The camera sat `DIST = 6.0` blocks out, `EYE_UP = +0.25`: at 70° FOV the 6.75-block frame filled ~80% of the screen height and ~41% of a 16:9 width, and the title plaque's top sat at y = 0.70, where the boss bars hang. The distance had been chosen so the frame fit *between* the boss bars and the hotbar | Boss bars step aside (H-2), so the camera moves in to `DIST = 5.25`, `EYE_UP = −0.29`: plaque top at y = 0.97, cartouche text at y = −0.77, above the hotbar + XP + hearts (39 GUI px at scale 3); the frame is 14% larger on screen. Labels 0.42 → 0.5, cartouche 0.7 → 0.8, tooltip 0.6 → 0.7, markers 24/32 → 28/38 px (label boxes scaled with them). `AtlasSky.screenY/screenX` model the framing | **Fixed+tested** (`AtlasFramingTest`) |
| H-2 | P1 | Boss bars «Жингийн Зам», «Заавар 4/7», «Анхны Ан» cover «СҮЛДИЙН АТЛАС» | 11 services called `Player#showBossBar` directly; only the waypoint bar knew about sky windows | `ui/BossBars`: every show/hide goes through one gate. While the atlas, the menu or the skill tree is open the bars are hidden and later shows are only remembered; on close the bars still wanted come back, and a bar its service hid meanwhile (a quest handed in) stays hidden. A 1-tick task follows the windows; `stop()` on disable | **Fixed+tested** (`BossBarsTest`) |
| H-3 | P1 | The corner minimap blinks off and on while moving, turning and fighting | (a) The client draws the map only while one of its frames is in view, and the server places the frames for the look it knows, a round trip behind the camera. All three frames were 10–14° *above* the look, so a look *down* of only ~21° within that lag (a mob at the feet) took every frame out of a view 35° tall. A drawn bow narrows the view by 15%, which makes it worse. (b) A projectile that clipped a frame parked it 4 blocks up (~42°, out of view) for 20 ticks: every skeleton arrow or fireball that touched the middle frame blinked the map for a second | (a) A fourth frame 24° *under* the look (`SLOTS`); all frames stay ≥ 10° off the look and > 5.5 blocks out. Modelled: turns of ±90°, looks of 45° down / 40° up (bow: 40°/30°) keep a frame in view; the old layout fails at 30° down. (b) The park is 2 blocks up (~15°, still in view) for 5 ticks | **Fixed+tested** (`MinimapStabilityTest`, model only); bot `mapcbot.js` updated to 4 frames |
| H-4 | P2 | Minimap and atlas cause lag | (a) Frames teleported on *any* block change, so a look resting near a block edge hopped a frame between two blocks every tick, one packet each. (b) The minimap redrew and sent a full 128×128 map (~16 KB) on every block moved, at most every 4 ticks (~5/s sprinting). (c) `MapPois.collect` (relays, dungeons, sites, every ovoo's nearest area, bosses) ran on the main thread for every minimap picture and every atlas pan. (d) Each atlas redraw (also once a second while tiles load) re-sent all 25 frames even when only one tile changed. Tile reads were already cached and composed on the map workers; none blocks the main thread | (a) `frameMoves`: a frame follows only once its spot is 0.3 past its block's edge; standing still and not turning, `place` returns before any maths. (b) `redrawDue`: a move of 2 blocks or a turn redraws at most every 5 ticks, a single step after 20, marks at once, a refresh at 200; identical pictures are still not sent. (c) `MapPois.cached` keeps the list 5 s. (d) `AtlasFrames.unchanged`: a frame already showing the same bytes is not sent | **Fixed+tested** (`MinimapStabilityTest`, `AtlasFramingTest`); not measured on the dev server |
| H-5 | P1 | `/skills` (the skill tree stage) pops open mid-fight while spamming W A S D, attacking, casting, Shift + Ctrl | `SkillService.mapShortcut`: **sneak + right click with the class weapon** opened the skill map, from `onInteract` (right click air/block) and `onInteractEntity` (right click on a mob). Players hold Shift through fights (Shift + Space dodge, Shift + F fifth spell, Shift + Q lock cycle; Ctrl + Shift is sprint + sneak), so any combo R, bow draw or right click on a mob while sneaking opened it. No other key, sneak, sprint, swap, drop or hotbar listener opens it (all `@EventHandler`s on input events were scanned) | The shortcut is removed: a click while sneaking is a plain combo click, and a Мэргэн can draw while sneaking. The tree opens from `/skills` and the menu's «Ур чадвар» card only; the join hint, `docs/HUD.md`, `HUD_MANUAL_QA.md` #14, `SKILL_SKY.md` and `hudbot.js` now say so | **Fixed+tested** (`NoAccidentalWindowTest`: no class that listens to keys, clicks or moves references a skill-tree opener; SkillService's click handlers do not branch on sneaking) |

Tests added: `ui/BossBarsTest` (4), `map/AtlasFramingTest` (6), `map/MinimapStabilityTest` (7),
`skill/NoAccidentalWindowTest` (3). Validation results: `EXECUTION_CHECKPOINT.md`, "Follow-up 2026-10-15 (2)".

Still open from this round:
* No client check: the atlas at 5.25 blocks (does the cartouche clear the hearts at the owner's GUI scale? at GUI
  scale 4 on 1080p the HUD is 18% of the height and the cartouche line would sit behind the hearts; scale 3 and
  lower are fine), the boss bars leaving and coming back, the four-frame minimap in fast fights, Shift + right click
  doing nothing. Steps: open `/maps` with a quest, the tutorial and an event bar showing; close it with Shift; fight
  skeletons while flicking the view down; sneak + right click with the class weapon.
* A modded client with occlusion entity culling (Sodium, Entity Culling) may still hide a frame that sits behind
  terrain; the extra frame makes that rarer but cannot rule it out.
* Atlas pans still respawn every marker entity (`placeMarkers`); not changed.

## 6. Follow-up 2026-10-15 (3): true screen waypoints, HUD projection, minimap marks

Session: local CLI, one agent, no subagents, on `main` = `origin/main` `6f2162b`, working tree clean. The owner's
request: every waypoint selected in the atlas shown on the live game screen like JourneyMap / Xaero's Minimap
(«Awesome Waypoint · 28.1m»), with edge arrows when out of view; atlas, minimap and screen in sync; pins and nearby
entities on the minimap. As before, SÜLD has no client mod: everything is done by the server with owner-only display
entities, so "projection" means the server places each card where the client's own camera will draw it.

| # | Sev | Gap found | Root cause | Fix | Status |
|---|---|---|---|---|---|
| W-1 | P1 | Only the one followed waypoint had anything on the game screen; pins and the latest death showed only on the atlas and the minimap | `WaypointGuide` drew a single label for its target; nothing else knew how to put a mark on the screen | `map/ScreenMarkers` (new): the waypoint, the 6 nearest pins within 1 500 blocks and a standing soul beacon each get a see-through, owner-only card «A · Агуй / 28.1 м»: the name's first letter in the pin's colour («◆» a place, «☠» the death). A waypoint to a pin or to the death wears its mark and is not shown twice. Pins and the death follow a new setting «Дэлгэцийн тэмдэг» (`PlayerSetting.SCREEN_MARKERS`, bit 9, on by default, `/map markers`, page 2 of «Тохиргоо») | **Fixed+tested** (`WaypointHudTest`, `ScreenMarkersTest`) |
| W-2 | P1 | The label was not on the place on screen: it stood 24 blocks out *level* at eye height + 0.6, so a place up a hill or down a valley showed at the wrong height, and a waypoint behind the player showed nothing | The direction ignored height (`new Vector(dx, 0, dz)`), and nothing handled out-of-view targets | `api.map.ScreenProjection` (new, pure): Minecraft's camera (yaw 0 = south, pitch 90 = down, level), the default 70° vertical FOV on 16:9, `project` / `unproject` / `edge`. **In view** (hysteresis: in at 12% inside the screen, out at 3%), the card stands on the true 3D line from the eye to 2.2 blocks over the place: at the place within 24 blocks, else 24 out, scaled ×0.125 a block. The client's camera then puts it on the right pixel every frame at any FOV, window or GUI scale, with no turn lag. **Out of view**, an edge card «➡ A 28.1 м» 10 blocks out where the crosshair-to-place line leaves the edge area. Behind the player it goes on the side to turn to, with one of 8 arrows. The edge area is 15% in from the sides, under three boss bars, over the hotbar and HUD panel, and off the corner minimap. Cards are spread so none overlap, including at corners (a full top or bottom spills to the sides), and the waypoint keeps its place first | **Fixed+tested** (`ScreenProjectionTest` 9 tests incl. project ∘ unproject on 500 random cameras; `WaypointHudTest` layout: the in-view card projects onto the place's own pixel, the edge card projects onto its edge spot, 300 random layouts keep every card on screen, apart and off the minimap) |
| W-3 | P2 | The distance read «412 м» everywhere: whole metres across the ground, the same at 28 m as at 28.9, and wrong for a pin far above or below | `Math.round(Math.hypot(dx, dz))` | `WaypointHud.meters` on the straight 3D line from the feet (across the ground only when the height is unknown): «28.1 м» to the tenth under 100 m (cut, never «100.0 м»), whole metres under 10 km, «12.4 км» beyond; the bar uses the same number. Arrival stays across the ground (a pin set from the atlas over an unloaded chunk stores a guessed y = 64) | **Fixed+tested** (`WaypointHudTest.distancesReadLikeTheReferenceCard`, `ScreenMarkersTest.theReferenceCardReads28Point1Metres`) |
| W-4 | P2 | The atlas did not show which place is the current waypoint, and did not change when one was set from the atlas, by command or by arriving | `WaypointGuide` had no listeners; `AtlasSky.placeMarkers` never read the guide | `WaypointGuide.onChange`: set, stop and arrival notify listeners at once. `AtlasSky.waypointChanged` redraws an open atlas on the next tick (not inside the click), with a gold «▼ Чиглэл · 412 м» flag, plus a pin marker for typed coordinates or a mate's spot, which have none | **Fixed (compile-verified)**: needs a live atlas |
| W-5 | P2 | The minimap could miss a pin change: it redrew on a new pin *count*, so removing one pin and adding another between two of its ticks (the dialog, `/map pin` right after `/map unpin`) left the old picture for up to 10 s | `MinimapService` compared `pins(id).size()` | `PinService.version(id)` counts every read, add and remove; the minimap compares that | **Fixed (compile-verified)** |
| W-6 | P2 | Minimap pins were all green whatever their colour; no nearby entities were shown except party mates | `Minimap.Kind.PIN` had one colour; nothing collected mobs | `Minimap.Marker.colour` + `Marker.pin(x, z, colour)`: the five pin colours. `Kind.MOB`: hostile mobs within 48 blocks (SÜLD's own and every vanilla `Enemy`, the nearest 24) as a 1-pixel red dot with a shadow, under places, pins, mates, the waypoint and the player's arrow. Looked up every 10 ticks; their moving redraws at most every 10 ticks (`redrawDue` 5-argument form; `mobsKey` ignores order). Every mark sits on its block's pixel (unchanged, now tested) | **Fixed+tested** (`MinimapTest.pinsShowInTheirOwnColoursOnTheirBlock`, `hostileMobsAreSmallRedDots…`, `MinimapStabilityTest.movingMobsRedrawTheMapButNotEveryTick`) |

Sync, as built (all one server, no extra packets beyond the entities'):

| Event | Atlas | Minimap | Screen cards | Bar / compass |
|---|---|---|---|---|
| Waypoint set (atlas click, `/map guide`, pin dialog, mate click) | flag on the next tick (`onChange`) | next tick (target identity) | next tick | at once |
| Waypoint stopped / reached / its pin removed | flag gone on the next tick | next tick | next tick | at once |
| Pin added / removed | at once (existing `refreshFor`) | next tick (`PinService.version`) | next tick | — |
| Death beacon opens / closes | on the next opening (unchanged) | next redraw | next tick | — |

Not changed: the compass and arrival logic, the boss bar's place (it is gated by `ui/BossBars`), the 24-block / ×0.125
sizing of the in-view card (kept from the label, seen on a client at 1080p in 2026-10-13), the latest death on the
atlas (still `deathRepository().recent`; the screen card uses the soul beacon, which has a height and a world).

Still open from this round:
* **No client check.** The server cannot know the client's FOV or window. An edge card assumes 70° on 16:9 and sits
  15% in from the sides, which covers a drawn bow's 15% zoom. A spyglass, or a FOV setting below about 55°, can push
  an edge card off the screen until the player turns. Steps: `/map guide 200 0`, face east (the card at the place's
  pixel, 24 blocks out), turn around (an edge card on the side to turn to, with an arrow), look straight up and down,
  `/map pin a` at several places, walk away, `/map markers` off and on, open `/maps` (the gold flag), sprint with a
  bow drawn.
* Edge cards follow the look the server knows, one round trip late: in a fast turn an edge card swings across for a
  moment before it settles. The in-view cards have no such lag. The minimap frames have the same limit.
* `mapcbot.js` was updated for the new card (in view 24 blocks out toward the place; turned away, an edge card under
  13 blocks with an arrow), but not run: it needs the dev server, and `node` is not installed here.

## 7. Follow-up 2026-10-15 (4): the welcome screen regression, ▯ glyphs, one HUD policy, cross-system audit

**Symptom (live test, 15:49, client 1.21.11 Fabric with no mods, GUI scale 3, en_us).** After «Дүрс багц амжилттай
ачаалагдлаа.» the welcome chest opened empty, titled «Тавтай морил, dekushdee!▯» and shifted left. Three boss bars,
the minimap, a waypoint card and the «✦ SÜLD ✦» sidebar all stayed on top of it.

**History.** Nothing since `f4c071b` touched the welcome menu, `ui.json` or its art, and no branch or stash holds a
welcome fix. The art itself was never drawn on 1.21.11. `f4c071b` added the 256-texel glyph check, but it made only
`hud.json` strict. The validator printed 21 warnings for `ui.json` and `minecraft:default`, and the pack shipped anyway.

**Root cause (W4-1), checked in the 1.21.11 client bytecode.**
- The font atlas page is 256 × 256 (`class_380`: `new Node(0, 0, 256, 256)`).
- A bitmap glyph that does not fit makes `method_2022` return null. The stitch callback (`class_377$2`) then
  substitutes the whole missing glyph, advance included (`requireNonNullElse(..., missing)`).
- So the 704 × 288 welcome art became one ▯ with a 6 px advance. The title, positioned after it by `shift(-159)`,
  started 171 px left, and the ▯ appeared right after the «!».
- The cards are invisible items over the painting, so the chest looked empty.
- The same happened to the main menu, cosmetics, frame, the three quest-book tabs, all hub pages and their glow
  frames, and the E-inventory decor (880 × 884, `container.crafting`).

**Fix.**
- **New `tools/pack/glyph_tiles.py`, run by `gen_ui.py`, idempotent.**
  - It cuts every cell over 256 texels into tiles of at most 256. Each tile gets a code point: `suld:ui` from U+E400,
    `minecraft:default` from U+F800, with ±2ⁿ spaces at U+F8E0.
  - Each `Glyphs` constant and the `container.crafting` lang strings become tiles plus shifts. Each string ends at the
    art's full width + 1, so `Menu`/`HubLayout` need no change.
  - Transparent tiles are dropped and identical tiles are shared: 21 glyphs became 143 tiles.
  - A script check confirmed that all 21 reassemble pixel-exact against `HEAD` (visible pixels, exact advance).
- **`validate_resourcepack.py`:** every font is now strict.
- **Latent defect found along the way:** the hub page art is transparent at its right edge, so the client's trimmed
  advance (189) was 24 px short of the `W + 1` that `HubLayout` counts. The tiled strings end at `W + 1`.

**One HUD policy (W4-2).** Before this, only the three sky windows hid anything, and the sidebar never stepped aside.
- **New `ui/UiFocus`.** It counts as modal: dead, `/map`, `/menu` or `/skills` open, or a SÜLD chest open (`Menu` or
  `ClassSelectionHolder`).
  - It is polled each tick, so ESC, menu-to-menu clicks, death, respawn, teleport, world change and quit all resolve
    without per-path restore code.
  - It fires `onChange` the tick the state flips. The map of last states is pruned on quit.
- **Wired into:**
  - the `BossBars` gate;
  - `MinimapService.busy` and `ScreenMarkers.busy`;
  - `HudService.update`, which takes the sidebar out of the slot while a view is up and puts it back on the flip;
  - the welcome trigger, which no longer opens over a sky window, the class choice or the death screen.
- `WaypointGuide.busy` and the atlas-only `hud().quiet` are unchanged.
- **The world behind the chest is unchanged.** The Envoy plaque «…Анхны баатрын нэр энд бичигдэнэ» (5 blocks
  ahead of the spawn, billboard) and the GuideBoards are world text, not HUD. With the sidebar now hidden they no
  longer collide with it.

**Cross-system audit (W4-3), with read-only sub-audits.**

Fixed:
- **`DungeonService.onEntityDeath`:** «Галт Ган» embers could kill the last player. That failed the run, and then
  `complete()` threw: the boss was dead and no reward was given. There is now a re-check after `mobDied`.
- **`SkillService.hostile`:** another soul's trial wolves counted as foes, so a player got refunds, heals and procs off
  hits that are always cancelled. A trial wolf is now only its own soul's foe.
- **Quit leaks:** `SkillService.lastShot` and `lastDropTick`, `ResourcePackService.sentAt`, and
  `QuestTracker.GATE_CACHE`.

Checked and clean:
- No listener is registered twice or left unregistered.
- No per-player task leaks.
- The PackGate covers decline, failure, timeout and quit, and runs each deferred action once.
- Every sky window cleans up on quit.

Open (not fixed this round, documented for the next one):
- A dungeon reward that overflows a full inventory drops in the hall, and `DungeonHalls.release` deletes it 7 s
  later. Fix: drop at `exitFor`.
- A player downed in a dungeon stays "in a run" at the gate (no `/spawn`) until the run ends.
- Heroic runs ended by shutdown are not logged, and forced affixes can leak when `begin()` fails late.
- The sell confirmation (`Menus.sellArmed`) holds a bag slot index with no item identity or expiry.
- Delayed spells keep running after a teleport or the training swap (no world/epoch check).
- `GuideBoards.ensure` duplicates the last board when one is skipped (index shift).
- Small cooldown maps with no quit prune (`CityProtectionListener`, `TrainingCoach`, `DeathService.wounds`).
- The pin-limit race (`JdbcMapPinRepositoryIT`) remains flaky. It passed in this run.

**Tests.**
- `GlyphFontContractTest`, five checks:
  - every bitmap cell of every font is ≤ 256;
  - every `Glyphs` constant is drawn;
  - tiled art keeps the 176 + 1 and `W + 1` advances;
  - the welcome title lands at `TITLE_INDENT`;
  - `container.crafting` is drawn in every language. A sixth check covers the code: every private-use code point in
    `suld-plugin`/`suld-api` is mapped by a pack font.
- `UiFocusTest`: the modal rule, the edge detector (rejoin starts fresh) and the wiring of every HUD element.
- `HubLayoutTest` now walks whole glyph strings.

**Not verified:** a real client (the welcome painting and title, the hub pages, the E-inventory decor, the HUD hiding
and returning around the welcome chest and death).
