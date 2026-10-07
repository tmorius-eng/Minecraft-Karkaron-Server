# 50 players on a 10 000-block world: measured

**Status: RELEASE GATE NOT PASSED on the benchmark machine.** This is measured evidence, not a claim of readiness.

## Test bench (be honest about it)

| | |
|---|---|
| CPU | virtualised Intel Xeon @ 2.80 GHz, **4 cores, no SMT**, shared |
| Load generator | the 50 Mineflayer bots run **on the same 4 cores** (about 1–1.5 cores) |
| JVM | Java 21, `-Xms4G -Xmx4G` + Aikar's G1 flags (deploy/suld.env.example) |
| Server | Paper 1.21.11, SÜLD + the core plugin profile, dev world (spawn −16/−46, border 10 000) |
| Measurement | `/suldperf start/stop` (per-tick durations, TPS, entities, chunks, heap, GC) + a JFR profile (`settings=profile`), after a 120 s warm-up run on a freshly started server |

A dedicated host (8+ fast cores at 5 GHz, the bots elsewhere) has 2–3× the single-thread speed of this bench. The
numbers below are the **pessimistic** end.

## Workload (bots/mmo50.js)

These are not AFK bots. The 50-player mix is:
* 10 in Kharkhorum: walking, opening GUIs, chatting;
* 10 exploring outward into new terrain: sprinting, with a 48-block hop every 20 s like a fast rider;
* 10 fighting region mobs with skills (ring 1);
* 10 in solo wave dungeons, re-entered after each run;
* 5 fighting Хасар (the 26-display boss rig);
* 5 travelling: teleports between area hearts and `/spawn`.

All bots join 5 at a time.

## Results

| Run | Config | Players | Mean tick | p50 | p95 | p99 | Max | Ticks > 100 ms | Entities | Loaded chunks | GC (s) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| A25 | Paper defaults, vd 8 / sd 5 | 24 | 94 ms | 91 | 163 | 235 | 476 | 686 / 1921 | 4 773 | 9 314 | 3.8 |
| B25 | tuned spawning, vd 8 / sd 5 | 25 | 81 ms | 74 | 137 | 231 | 552 | 399 / 2628 | 4 382 | 9 773 | 3.3 |
| B50 | tuned, vd 8 / sd 5 | 50 | 146 ms | 145 | 308 | 451 | 1967 | 1100 / 1555 | 6 479 | 16 512 | 10.7 |
| C50 | tuned, **vd 6 / sd 4** | 50 | 132 ms | 127 | **214** | **357** | 937 | 1561 / 1817 | 6 582 | 12 131 | 4.3 |
| D50 | tuned, vd 7 / sd 5 | 50 | 153 ms | 147 | 256 | 463 | 1880 | 1307 / 1570 | 6 964 | 14 710 | 6.0 |

* **10 players** (earlier, 2 GB heap, before tuning): mean 45 ms, p95 63 ms, TPS 19.7.
* 0 players: 1–3 ms per tick.

## Where the time goes (JFR, main thread)

* **Vanilla does almost all of it.** In total-time order:
  * chunk ticking: random ticks and natural spawning, about 25 %;
  * mob AI and movement (`LivingEntity.aiStep`, `Mob.serverAiStep`, pathfinding goals), about 20 %;
  * entity tracking (`ChunkMap.newTrackerTick`), about 5 %;
  * block-state lookups under them all.
* **The entity count is the driver:** 6 500 at 50 spread-out players. The largest groups:
  * creepers, zombies and skeletons: 500–600 each;
  * dropped items: about 650;
  * passive animals that chunk generation creates and that never despawn;
  * chest minecarts from generated mineshafts.
* **SÜLD's own instrumented code** costs 3–7 ms per tick at 25–50 players, about 5 % of the tick:
  * HUD action-bar redraws on events, 3 ms/tick at 50 players. Capped since then at one redraw per player per
    2 ticks.
  * The target frame, the sidebar and the TAB list together, about 1.7 ms.
  * The model renderer: 0.2 ms.
* Profile saves are asynchronous (`supplyAsync`). No database call runs on the main thread.
* **GC:** G1 young pauses of 30–170 ms on this 4-core bench, because the GC threads compete with the bots.

## What was changed, and why (each measured)

| Problem | Change | Effect |
|---|---|---|
| Vanilla monsters pile up out of sight (70 per player) and bats are pure AI cost | `deploy/paper-tuning.conf`: monster cap 50, monster spawn attempts every 10 ticks, bats ≤ 4, water ambient ≤ 8 | 25 players: mean −14 %, p95 −16 % |
| A player in fresh terrain can generate unlimited chunks per second | `chunk-loading-basic.player-max-chunk-generate-rate: 40` | bounds one player's generation burst; nearest chunks first |
| A 10 000 border is no reason to generate 390 k chunks | own throttled pre-generator, priority areas only (about 9 k chunks), MSPT-adaptive | 13–108 chunks/s at tick p95 ≤ 6 ms with no players |
| Teleports into unloaded chunks loaded them on the main thread | every SÜLD teleport through `teleportAsync` (SafeTeleport) | no synchronous chunk load from SÜLD |
| View/simulation distance | vd 6 / sd 4 gave the best p95/p99 at 50 players | p95 308 → 214 ms, worst tick 1.97 s → 0.94 s versus vd 8 / sd 5 |

## Release gate (docs/DEPLOYMENT.md)

| Check | State |
|---|---|
| 10k × 10k border works, persists, spawn inside, no teleport escape | ✔ verified (docs/world/WORLD_BORDER.md) |
| view/simulation distance benchmark | ✔ done (above) |
| chunk generation / loading benchmark | ✔ pre-generator and exploring bots measured |
| 10-player test | ✔ TPS 19.7 |
| 25-player test | ✘ TPS 13 on this bench |
| 50-player test | ✘ TPS 9–10 on this bench |
| main-thread DB IO | ✔ none |
| spark profiling | ✔ JFR profiles taken instead (the spark viewer needs upload, which is blocked here) |
| memory stress (2 h+) | ✘ not run tonight |
| real client QA | ✘ owner |

**Recommendation:** production for 50 players needs a dedicated host with high single-thread speed (for example a
Ryzen 7/9 7000-series at 5 GHz or better: about 2–2.5× this bench), with the load generator on another machine,
then a repeat of this exact benchmark (`bots/mmo50.js`, same mix). Start at vd 6 / sd 4 and the tuning above. Move to
vd 7–8 only if p95 stays under 40 ms.
