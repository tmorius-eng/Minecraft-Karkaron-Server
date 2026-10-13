# Pre-generation

The border is a **playable limit, not a generation order**. All 390 625 chunks would take about 2–3 GB of region files
(about 7 KB per chunk on this world) and hours of CPU. SÜLD therefore generates ahead only the places players are sent
to. The wilderness generates as it is explored, under Paper's per-player rate limits (docs/perf/WORLD_50.md).

## Priority map (automatic, `world.pregenerate.levels`, default P0–P3)

| Level | What | Size |
|---|---|---|
| P0 | Spawn + Kharkhorum | `spawn-radius` 384 blocks around the spawn |
| P1 | Routes from the city to the 16 inner areas (rings 1–2, out to 1 500 blocks) | corridors ±2 chunks |
| P2 | The ground of the 10 dungeon gates (registered by the dungeon halls) | 96 blocks around each |
| P3 | The heart of every named area (40), where its ovoo stands | `area-radius` 128 blocks |
| P4 | Routes to the 24 outer areas (rings 3–5, 1 500–5 000 blocks) | corridors ±2 chunks (off by default) |
| P5 | Everything inside the border | only `/suldworld pregen full confirm` |

The default plan (P0–P3) is 67 jobs and **14 936 distinct chunks**: 2 401 for P0 alone, and 21 876 with P4 as well
(counted with the pure `PregenPlan` over the bundled content, 2026-10-13). Overlapping jobs never generate a chunk
twice: the pure `PregenPlan` skips any chunk an earlier job covers, and chunks outside the border.

The plan starts only once Kharkhorum is built: it is centred on the plaza, where the city moves the spawn. On a fresh
world the first boot builds the city first, then the plan.

## Throttling

* Every request goes through Paper's asynchronous chunk system (`getChunkAtAsync`). Nothing loads a chunk
  synchronously, and nothing waits on a future on the main thread.
* At most `max-per-tick` (6) new requests per tick and `max-in-flight` (12) outstanding.
* The in-flight allowance follows the measured tick time over the last 20 ticks:
  * halved above `target-mspt` (30);
  * zero above `pause-mspt` (45);
  * one more after 10 calm ticks.
* After each chunk `unloadChunkRequest` lets it go again, unless a player holds it.

## Persistence

Progress is a cursor (job, centre, spiral step) in `plugins/SULD/pregen.yml`:
* written asynchronously every 15 s, with an atomic replace;
* written synchronously on shutdown.

After a restart the run resumes at the cursor. If the plan changed (the spawn moved, or the levels changed), it starts
over. Chunks that already exist are then only re-read, not generated again.

## Commands (`suld.admin.world`)

* `/suldworld pregen status|start|pause|resume|cancel|restart`
* `/suldworld pregen full confirm`: queue P5 after the priority jobs.
* `/suldworld report`:
  * border;
  * generated chunks (read from the region-file headers off the main thread) out of the total;
  * region files and MB;
  * estimated size of the full world;
  * loaded chunks, entities and tile entities.

## Measured (dev server, 4 cores, no players, 2026-10-07)

| Phase | Rate | Tick (p95 / max over 10 s) |
|---|---|---|
| Existing chunks (re-read) | ~108 chunks/s | 6.0 / 29.7 ms |
| New terrain | 13–45 chunks/s | 4.5 / 28.7 ms |

* Loaded chunks after the run: 1.
* The plan was 9 033 distinct chunks then, when the world had 24 named areas.
* Chunky is no longer driven by SÜLD. It stays an optional manual tool and must not run alongside.
