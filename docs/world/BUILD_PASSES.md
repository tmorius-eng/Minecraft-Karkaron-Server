# Build passes

Every block a module writes is tagged with a pass. The plugin places pass 1 everywhere, then pass 2
everywhere, and so on. Inside a pass it goes chunk by chunk in row order, bottom-up, so a slice grows in
readable layers and a pause or crash leaves a consistent state.

| # | Pass | What | Kharkhorum slice 1 |
|---|---|---|---|
| 1 | TERRAIN | surface blocks, earth fill, clearing above the surface, the feather band | plaza +1, terrace +8, residential +1, shrine hill +4 |
| 2 | WALLS_GATES_ROADS | walls, gates, streets, canal, bridges, stairs, retaining walls | Imperial Gate, wall stubs, avenue, Grand Bridge, canal, grand stair, terrace walls |
| 3 | DISTRICT_FOOTPRINTS | plazas, sidewalks, yard paving | Central Plaza paving, market sidewalks |
| 4 | LANDMARKS | monuments and unique landmarks | Khan's monument, Tenger shrine |
| 5 | SHELLS | building shells | towers, gate halls, stalls, houses, gers, forge |
| 6 | ROOFS_DETAIL | roofs, eaves, ridges | hip and skirt roofs, ger roofs, awnings |
| 7 | DECORATION | hangings, standards, fences, props | SÜLD hangings, tug standards, yard fences, prayer flags |
| 8 | LANDSCAPING | trees, flowers | cherry gardens, avenue elms, larches |
| 9 | INTERIORS | furniture, doors, workstations | stalls' goods, ger furniture, forge tools |
| 10 | LIGHTING | lanterns, braziers, fires | lantern posts, gate braziers, forge fire |
| 11 | GAMEPLAY | gameplay props at points | class stones, relay posts, offering stone |
| 12 | SECRETS | hidden content | (none in slice 1) |

**Budget.** At most 25 ms of placement per server tick, plus `world.build-blocks-per-tick`. Progress is
logged every 10 s with MSPT.

**Resume.** After a restart the plan is recompiled from the saved height map, so it's identical, and then
replayed from the start. Blocks that already match are skipped. This also repairs chunks that a crash lost
before Paper saved them to disk. Measured: a `kill -9` mid-build, then a restart, then the build finished.
See VALIDATION.md for the world diff.
