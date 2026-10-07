# Model renderer benchmark (Stage D1)

Measured on the local dev Paper 1.21.11 server: 2 GB heap, one bot viewer standing 6–10 blocks from the rigs.
The command was `/suldperf model <n> 15 <player>`. Each run spawns n Хасар rigs (26 displays each, plus 1 host) for
15 s, then removes them. Raw JSON files are written to `plugins/SULD/perf/model_khasar_<n>.json`.

| Rigs | Displays | New entities | Transforms/s | model.tick mean / p95 / max (µs) | model.spawn mean (µs) | model.despawn mean (µs) | TPS avg | Left after despawn |
|---:|---:|---:|---:|---|---:|---:|---:|---:|
| 1  | 26  | 27  | 237   | 127 / 195 / 2 317  | 7 401 | 1 409 | 19.88 | 0 |
| 5  | 130 | 135 | 1 105 | 285 / 497 / 5 511  | 6 607 | 1 072 | 19.71 | 0 |
| 10 | 260 | 270 | 2 287 | 570 / 989 / 3 426  | 3 711 | 327   | 19.64 | 0 |

**Reading the numbers**

* **Tick cost** grows about linearly: roughly 55 µs per rig per tick. Ten rigs use about 1.1 % of a 50 ms tick at
  mean and about 2 % at p95. This is well inside the ASSET_BUDGET target (a 4-player fight at 20 TPS with mspt < 35).
* **Transforms/s** is how many bone updates are sent to each viewer. It is the network-load proxy: about 230 per rig
  per second at the 10 Hz near tier. A transformation update is about 60–80 bytes on the wire, so one rig costs
  ≈ 15–19 KB/s per viewer, just inside the 20 KB/s per-boss budget. With many rigs on screen the per-viewer load adds
  up: 10 rigs ≈ 160 KB/s. That is acceptable for a benchmark but not for gameplay, so do not put more than ~3
  rigged creatures near one player at once.
* **Spawn** costs 3–9 ms per rig, once. The first spawn also pays class-loading.
* **Despawn** is clean: 0 displays are left in every run. The fight test also showed 26 displays during the death
  clip and 0 a few seconds after it.
* **Particles:** the Хасар brain stays under 80 per tick. The largest burst is 60 (phase change), and it happens once.
* **Memory:** the heap delta could not be measured. A `System.gc()` runs before both readings, but a 2 GB G1 heap
  under bot load moves by tens of MB between samples, which is much more than 260 small display entities use. The
  delta came out −26 to −33 MB. The benchmark-only GC is also why `server.tick` max (≈ 0.6 s) and `ticksOver` show
  2 long ticks in every run. They are not the renderer.

**Live fight test** (bot `khasarbot.js fight`, dungeon `khasar_den`):

* Waves cleared, then the boss spawned with a 26-display rig.
* Phase 2 (Уурласан ×1.3) and phase 3 (Галзуурсан ×1.6) were announced.
* The boss hit the bot (58 hurt events in about 60 s).
* The howl summoned cave wolves (4 alive at the check).
* Death clip: 26 displays, then 0.
* Completion: +400 EXP, +75 coins, and reward items.

Status: **MANUAL_QA_REQUIRED**. The bot verifies the server side only. Smoothness, the look and the network feel
need the owner's client checklist (`docs/qa/CLIENT_QA_KHASAR.md`).
