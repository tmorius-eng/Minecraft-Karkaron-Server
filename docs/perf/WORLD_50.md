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
| E50 | tuned, vd 8 / sd 6 | 50 | 168 ms | 167 | 310 | 482 | 1923 | 1096 / 1408 | 7 132 | 17 460 | 8.8 |
| G50 | **new jar** (rigs on every mob, halls, chat, lock-on, tutorial), vd 6 / sd 4 | 50 | 122 ms | 106 | 184 | 307 | 12 454¹ | 1230 / 1944 | 5 896 | 10 405 | 3.1 |
| **F50** | G50 + **entity density** (monster cap 30, monster despawn 30/72, item merge 2.0) | 50 | **92 ms** | **86** | **139** | **213** | 920 | 627 / 2603 | 5 266 | 9 841 | 3.0 |

¹ One 12.4 s stall: `World#addPluginChunkTicket` synchronously loaded and generated a dungeon hall's chunks on the
main thread the first time a slot was claimed. It was found by the Paper watchdog dump and **fixed**: the hall's
chunks are now prepared with `getChunkAtAsync` and ticketed only once loaded, and the hall is handed to the run
only then. The F50 run's worst tick of 0.92 s came after that fix.

In G50/F50 the dungeon bots walk to the gates. Dungeons 2–4 now need the previous dungeon cleared, so most of them
were refused and fought in the open, which means somewhat less wave load than A–E. The Хасар runs (5 bots) took
place in halls.

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
| View/simulation distance | vd 6 / sd 4 gave the best p95/p99 at 50 players (vd 7/5 and 8/6 were worse) | p95 308 → 214 ms, worst tick 1.97 s → 0.94 s versus vd 8 / sd 5; now the deploy template default |
| Vanilla hostiles still dominate at 50 players | monster cap 30, monster soft/hard despawn 30/72, item merge radius 2.0 (`deploy/paper-tuning.conf`) | F50 vs G50: mean −24 %, p95 −25 %, p99 −31 %, TPS 9.5 → 11.8 |
| Main-thread chunk generation when a hall slot was ticketed | async preparation, tickets on loaded chunks only | the 12.4 s stall is gone |
| HUD event redraws | at most one per player every 2 ticks | — |

## Functional checks with the final jar (bots/featbot.js, 16/16 passed)

* **Tutorial:** starts on first join and advances.
* **Chat:** local reaches 80 blocks and not 400; `!` goes global; party reaches the party and not others; `/` stays
  chat; the `[Б]` tag is in front.
* **Lock-on:** Q keeps the weapon and shows the reticle.
* **Dungeon:** the gate is known; the run starts in the hall; waves, boss, rewards; the party is back at the gate
  (2.5 blocks).
* **Models:** all 30 rigs load.

## Memory and join/quit soak (bots/soak.sh, 90 minutes)

**Workload.** 25 mixed bots for 90 minutes, on the jar of 17:05 with the tuning above:
* 6 city, 6 combat, 4 explore, 3 boss, 3 travel and 3 quest;
* plus 10 churn bots that join, play briefly and leave over and over.

The workload totalled:
* 1 838 join/quit cycles and 1 903 teleports;
* 174 dungeon entries, 3 222 commands and 17 deaths;
* 0 failed joins and 0 kicks.

Every 5 minutes the script forced a full GC (`jcmd GC.run`) and sampled the heap and threads.

| Time (UTC) | Heap after full GC | Threads | Note |
|---|---|---|---|
| 17:17 | 777 MB | 116 | before the bots |
| 17:22 | 1 936 MB | 140 | 25 bots spread over the world |
| 17:47 | 2 227 MB | 138 | |
| 18:13 | 2 234 MB | 137 | |
| 18:44 | 2 325 MB | 138 | last sample under load |
| 18:49 | **1 048 MB** | 128 | 30 s after the last bot left |

Single samples peaked at 2.68 and 2.70 GB (17:42 and 18:08). These were spikes: the next sample was back at 2.2 GB.

**Reading.**
* **Under load:** the heap plateaus at about 2.2 GB after the first ramp. There is no upward trend over 80 minutes;
  the spikes are the loaded-chunk count following the explorers.
* **After the last bot leaves:** the heap falls back to 1.05 GB. The 270 MB above the cold baseline is the
  warmed-up chunk cache, JIT code and SÜLD's caches (profile cache, chat history, halls).
* **Entities:** the census shows no SÜLD entity left behind once the players have gone.
* **Threads:** 116 → 127 after the soak, so no thread leak.

**One trend under load (not a leak).** Ambient and water mobs (bats, nautilus, squid) kept rising while 25 players
stayed online:
* nautilus 65 → 459;
* bats 76 → 333.

Two things cause it:
* mobs in chunks that are loaded but outside the simulation distance (view 6 > simulation 4) are frozen: they
  neither tick nor despawn, and they do not count toward the spawn caps;
* nautilus persist like animals.

They cost memory and entity tracking, not AI ticks, and they unload with their chunks. If a long-running production
world shows the same, Paper's `chunks.entity-per-chunk-save-limit` (for example `bat: 4`, `nautilus: 4`) caps what
is saved per chunk. `deploy/tools/paper_tune.py` only edits existing keys, so it would need a small extension to add
them. Not applied tonight.

## Known main-thread work that remains (one-off, documented)

* Relic shrine placement on a brand-new world generates up to 16 chunks synchronously, once ever.
* The city build snapshots its own chunks, once.

## Release gate (docs/DEPLOYMENT.md)

| Check | State |
|---|---|
| 10k × 10k border works, persists, spawn inside, no teleport escape | ✔ verified (docs/world/WORLD_BORDER.md) |
| view/simulation distance benchmark | ✔ done (above) |
| chunk generation / loading benchmark | ✔ pre-generator and exploring bots measured |
| 10-player test | ✔ TPS 19.7 |
| 25-player test | ✘ TPS 13 on this bench |
| 50-player test | ✘ TPS 11.8, mean 92 ms, p95 139 ms on this bench (best config) |
| main-thread DB IO | ✔ none |
| spark profiling | ✔ JFR profiles taken instead (the spark viewer needs upload, which is blocked here) |
| memory stress | ◐ 90-minute soak with 25 bots and 1 838 join/quit cycles: heap plateaus at ~2.2 GB and returns to 1.05 GB after logout, no thread or entity leak (above); a 2 h+ run on the production host is still owed |
| real client QA | ✘ owner |

**Recommendation:** production for 50 players needs a dedicated host with high single-thread speed (for example a
Ryzen 7/9 7000-series at 5 GHz or better: about 2–2.5× this bench), with the load generator on another machine,
then a repeat of this exact benchmark (`bots/mmo50.js`, same mix). Start at vd 6 / sd 4 and the tuning above. Move to
vd 7–8 only if p95 stays under 40 ms.
