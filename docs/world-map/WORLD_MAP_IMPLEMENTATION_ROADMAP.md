# World map — implementation roadmap

> **SUPERSEDED (2026-10-13).** The owner chose real data only (no predicted terrain) and a map drawn from map items; the current design and what is built are in [`docs/WORLD_MAP.md`](../WORLD_MAP.md). This file is kept as the record of the first proposal.

**Status: PLAN (2026-10-13), starts when the owner approves the architecture (gate D1–D3).** Efforts are working-day
estimates for one developer; they are not measurements and will be corrected after Phase 0.

## Phase 0 — spikes and the look (1–2 days)

| Step | Output | Gate |
|---|---|---|
| 0.1 Tileset and book frame preview: `tools/pack/atlas_kit.py --preview` renders the open book, the tabs and a sample map from the **dev world's real biome data** (not invented terrain), in the sepia style of the owner's second reference | PNG previews sent to the owner | **D1**: the owner approves the look |
| 0.2 Glyph-tiling spike on the dev server: one 40 × 25 row set in a sky stage; seams, line spacing, scale and brightness at GUI scale 2–4 (a screenshot from a real client, or the owner's check) | numbers for `AtlasText` (ascent, scale, row step) | — |
| 0.3 Terrain spike: time `vanillaBiomeProvider` over the square on and off the main thread; compare predicted vs real classes on the 14 940 pre-generated chunks (agreement %) | measured time, agreement, the thread decision | **D2**: two inks confirmed |
| 0.4 Beacon spike: one see-through owner-only label projected at 24 blocks, updated every 2 ticks, with a second bot watching | flicker/visibility notes | — |

## Phase 1 — the land and the screen (3–5 days)

`SkyStage` extracted from MenuSky/SkillSky (their bots still pass); `MapFrame`, `TerrainClass`, `TerrainGrid`,
`Autotile`, `AtlasText` with tests; `TerrainService` (predicted pass, verified pass, the grid file); the
`suld:atlas` font and frame in the pack (budget exception written for the frame if it needs one); `AtlasSky` with pan,
zoom, the player arrow, region and area names, the ribbon; `/map` and F on the «Сүлд Цэс» clock; the travel-page
card. Bot steps 1–3 and 6–7.

## Phase 2 — places (3–4 days)

`DangerTier` moved to suld-api (the HUD uses it); `MapPoi`, `Visibility`, `Clusters` with tests; `PoiRegistry` with
the sources (gates, sites, ovoo, city points and landmarks, relays, NPCs, world bosses, the yard); tabs, tooltips,
the danger badges, the quest mark; `content/landmarks.json` and `content/atlas.json` in the content loader; `/suld map
poi near`. Bot steps 4 and 8. Fix current-state findings 1 (`sites.yml` read-back) and 3 (the Act II quest region)
first, because the atlas shows those positions.

## Phase 3 — pins and the way (3–4 days)

V26 (`suld_map_pins`, `suld_map_waypoint`), the repository trio and `JdbcMapPinIT`; `PinRules`, `MapPinService`; the
pin dialog (name, note, icon, «ертөнцөд харуулах»); `GuideService` (DungeonGuide becomes a client of it);
`WaypointBeacons`; the latest-death waypoint from the soul beacon; `/map pin|pins|stop`; staff `/suld map pins`;
analytics `map_open`, `map_pin_create`, `waypoint_set`, `waypoint_reached` (and rows in `docs/ANALYTICS.md`). Bot
steps 5 and 9, the pin flow.

## Phase 4 — finish (2–3 days)

Cluster and label tuning per zoom; the filter panel (labels, party, predicted land); party arrows and the leader's
rally pin; the real-client checklist in `docs/qa/`; the performance runs of the test plan §7 on the dev server (then
on the VPS when it exists); `docs/WORLD_MAP.md` (status, rules, concurrency audit, verified table), `docs/STATUS.md`
row and schema V26, `CommandCatalog` rows, `luckperms.txt` if a staff permission is added.

## Phase 5 — optional, behind gates

| Item | Gate | Note |
|---|---|---|
| HUD minimap from the same tileset | **D5** | needs HUD space and per-player updates; a spike first |
| Fabric client module (real M key, native screen) reading the same data over a versioned plugin channel | **D4** | contradicts today's no-mod policy; only with the owner's explicit change of that policy |
| Exploration reveal ("fog") instead of the whole world | **D3** reversed | per-player explored bitmap (~3 KB a player) |

## Dependencies

* The pack pipeline (`tools/pack`, `validate_pack_budget.py`) for the font and the frame.
* The sky-window engine (shared `SkyStage`) and its bots (`skymenubot.js`, `skybot.js`) as regression guards.
* The POI owners: `DungeonHalls`, `HistoricSiteService`, `OvooService`, `WorldBuildService`, `NpcService`,
  `WorldBossService`, `RelicService` (hint bands only), `SoulBeaconService`, `PartyService`, `QuestTracker`.
* Persistence conventions (`SchemaMigrator`, `TestDb`, `KeyedSequencer`, `RateLimiter`).

## Risks

| Risk | Mitigation |
|---|---|
| Glyph tiles show seams or uneven rows on some GUI scales | Phase 0 spike on a real client; fallback: 8 × 8 sub-glyphs or one `TextDisplay` per two rows |
| `vanillaBiomeProvider` is slow or main-thread only | sliced main-thread job with a cursor; the atlas works with what is classified so far |
| Predicted land differs from what generates | the two inks; verified cells replace predictions as chunks appear |
| Pack ZIP already above the 8 MB soft target | the tileset is small; the frame drawn lean (nine-slice); measure before and after |
| Boss-bar crowding | the guide line reuses the dungeon guide's slot; one destination at a time |
| Sky-window side effects (spells firing, others seeing the seated player) | swings consumed by the atlas; bot step 7 |
| Owner expects an M key | stated plainly in the decision; F on the menu clock is the closest vanilla key |
