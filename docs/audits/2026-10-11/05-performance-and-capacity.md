# 05: Performance and capacity

Every number in this report carries a tag:

| Tag | Meaning |
|---|---|
| **MEASURED** | Taken in this audit's environment. |
| **REPORTED** | From repo docs; not re-checked. |
| **STATIC** | From reading code. |

## Verdict

| Load | Result (MEASURED, this environment) |
|---|---|
| 10 players | Fine. |
| 25 players | Holds 20 TPS on average, with occasional multi-second stalls. |
| 50 players | Does **not** hold 20 TPS: avg 18.5, p95 tick 59 ms, 14 % of ticks over budget. |

**What this run does and does not show:**
- The run was on a shared 4-vCPU container with the bots on the same cores. It is a **lower bound, not a capacity figure**.
- The 50-player claim is **unverified** for the target host.
- The repo's own release gate (`docs/perf/WORLD_50.md`: "NOT PASSED") still stands.

**Where the time goes:**
- SÜLD's own code costs about 1.5–2.6 ms per tick (3–7 % of the 50 ms budget).
- The dominant cost is vanilla **chunk generation and loading**.
- The cheapest capacity win is therefore operational: pre-generate the world and keep players from outrunning it.

## Environment (MEASURED run)

| Item | Value |
|---|---|
| Hardware | Cloud container, 4 vCPU, 15 GB RAM, shared by Paper, PostgreSQL 16 and all Mineflayer bots |
| JVM | OpenJDK 21.0.12, `-Xms2G -Xmx4G -XX:+UseG1GC`, JFR `settings=profile` |
| Server | Paper 1.21.11-132 (`c5eb079`), SÜLD `0.1.0-SNAPSHOT+50d5138`, no other plugins |
| World | Fresh world. City slice built at boot (572 chunks). World pre-generation **paused** for the runs (`/suldworld pregen pause`) |
| Settings | view-distance 6, simulation-distance 4, offline mode (local dev only) |
| Database | PostgreSQL 16.15 on localhost, 15 migrations |
| Player simulation | `mmo.js` (Mineflayer 4.39), 4 min per run. Roles: city walkers with GUIs and chat, explorers (teleports outward), combat bots in the steppe ring, quest bots, idlers |
| Measurement | In-repo `/suldperf start/stop` (`PerfService`): per-tick `server.tick` probe, SÜLD probe histograms, 1 s TPS/CPU/heap samples, plus JFR |

## Results (MEASURED)

| Metric | 10 bots | 25 bots | 50 bots |
|---|---|---|---|
| Duration (s) | 247 | 247 | 247 |
| Joined / failed | 10 / 0 | 25 / 0 | 50 / 0 (51 s to join all) |
| Bots kicked (harness artefact¹) | 0 | 5 | 8 |
| Players avg / max | 9.8 / 10 | 19.7 / 25 | 38.8 / 50 |
| TPS avg (min) | 19.68 (18.71) | 19.20 (16.77) | **18.55 (14.02)** |
| Tick mean (ms) | 20.6 | 23.3 | 37.8 |
| Tick p50 / p95 / p99 (ms) | 19.3 / 28.9 / 46.4 | 21.7 / 30.8 / 45.7 | 37.8 / **58.7 / 88.9** |
| Tick max (ms) | 2,852 | 7,910 | 10,500 |
| Ticks > 50 / 100 / 300 ms | 41 / 5 / 3 | 35 / 6 / 2 | **662** / 31 / 9 (of 4,584) |
| Heap used avg / max (MB) | 1,563 / 1,991 | 1,775 / 2,359 | 1,952 / 2,711 |
| GC collections / total ms | 83 / 2,367 | 61 / 2,243 | 82 / 3,690 |
| Entities avg | 1,129 | 1,503 | 2,076 |
| Loaded chunks avg | 1,787 | 1,805 | 2,266 |
| System CPU avg / max (%) | 46 / 100 | – | 56 / 100 |
| SÜLD probes, ms per tick² | 1.49 | 1.66 | 2.60 |

¹ Explore bots are teleported to y=200 with slow-falling and were kicked by vanilla's "floating too long" check. This is a bot-harness artefact, not a game defect. The 50-player run therefore peaked at about 42 concurrent bots.

² The sum of mean × count across SÜLD probes, divided by ticks. Nested probes (`hud.render` inside `hud.render_event`) are counted twice, so this is an upper bound.

**Over the whole recording (MEASURED, JFR):**
- 612 GC pauses: median 21 ms, p95 74 ms, p99 218 ms, max 390 ms.
- So GC does **not** explain the 2.9–10.5 s stalls.

### Hottest code (MEASURED, JFR execution samples, all runs combined)

**Vanilla world generation** made up the top 25 methods:
- `SimplexNoise.dot` 5.3 %
- `ImprovedNoise.gradDot` 4.4 %
- `Mth.lerp3` 4.1 %
- `PerlinNoise.getValue` 2.9 %
- `NoiseChunk` interpolators
- `Aquifer.computeSubstance`
- `PalettedContainer`
- the biome RTree search

**SÜLD's own hottest probes at 50 bots** (ms per tick):

| Probe | ms/tick |
|---|---|
| `hud.render_event` | 0.38 |
| `hud.sidebar_tick` | 0.37 |
| `model.tick` | 0.34 |
| `hud.panel_tick` | 0.32 |
| `hud.tab` | 0.20 |

These are small but they grow linearly with players; the sidebar and TAB are per-viewer.

**Single-event p99 at 10 bots** (fine):

| Event | p99 |
|---|---|
| `combat.hit` | 0.30 ms |
| `skill.cast` | 3.7 ms |
| `model.spawn` | 11.6 ms |

Database calls ran off the main thread (`db.profile.save` p99 160 ms, `db.profile.find` max 11.9 ms), so they did not block ticks.

### Stalls (PERF-002, cause not isolated)

| Run | Stalls |
|---|---|
| 10 bots | max 2.85 s |
| 25 bots | max 7.9 s |
| 50 bots | max 10.5 s |

All occurred while explorers were being teleported into ungenerated terrain by console `tp`, which loads chunks synchronously.

**Hypothesis (unverified):** synchronous chunk generation on teleport. Players using `/home`, `/tpa`, relays or the dungeon gate into cold chunks would cause the same thing. `SafeTeleport` uses `teleportAsync`; vanilla `/tp`, EssentialsX `/home` and `/tpa` may not.

**To settle it:**
- Run spark `--thread "Server thread"` during a teleport burst.
- Or use JFR `jdk.ThreadDump` / `jdk.ExecutionSample` filtered to the server thread around a stall timestamp.

### Entity observation (PERF-003, unverified)

`/suldperf entities` at the end of the 10-bot run listed:
- `FALLING_BLOCK=256` (the top type)
- `BAT=162`
- `ARROW=112`

256 falling blocks that persist across a run is unusual. They may come from world-generation sand/gravel, or from a SÜLD visual effect. The origin was not traced.

- **Check:** `/execute as @e[type=falling_block] run data get entity @s` on a quiet server.

## Prior evidence (REPORTED, not re-run)

| Source | Claim |
|---|---|
| `docs/perf/PERFORMANCE_BASELINE.md` (2026-10-07) | 10 bots: TPS 20.0, mean 7.8 ms, p95 13 ms. Combat QA max 1,560 ms |
| `docs/perf/WORLD_50.md` (2026-10-07/08) | 50 bots on a 4-core shared Xeon, best config: TPS 11.8, mean 92 ms, p95 139, p99 213, max 920 ms. A 12.4 s `addPluginChunkTicket` stall was fixed. 90-min soak at 25 bots: heap plateau ~2.2 GB, no leak. **Release gate NOT PASSED.** Recommends a Ryzen-7000-class host with the bots elsewhere |
| `docs/perf/MODEL_RENDERER_BENCH.md` | ~55 µs per Хасар rig per tick; 15–19 KB/s per rig per viewer, hence "at most ~3 rigs near a player" |

**Why today's 50-bot result (TPS 18.5) is better than the reported 11.8 (STATIC reasoning):**
- different view/simulation distance;
- pre-generation paused;
- a lighter bot role mix (no dungeon or boss bots: `dungeonsEntered: 0`);
- possibly the fixes since 2026-10-08.

Neither number is a capacity figure for the target host.

## Scalability risks from code reading (STATIC)

- **Main-thread blocking at startup and shutdown only:**
  - `ClanService.java:63` (`.get(30 s)`), `RelicService.java:97,99` (15 s), `StyleService.java:115` (5 s), `DeathService.java:146-147` (5 s), `SuldPlugin.java:452` (15 s shutdown).
  - These are bounded, not per-tick. `StyleService.java:115` and `DeathService.java:146` sit on the login path; check that they run on the async pre-login thread.
- **Per-viewer HUD/TAB rewrites** scale O(players) per tick; the earlier O(n²) sidebar was fixed with staggering (REPORTED).
- **Display-entity rigs:** network cost per viewer (REPORTED 15–19 KB/s per rig) is the risk at boss fights with many viewers, more than CPU.
- **Leaderboard and analytics queries without indexes** (OPS-007): every 60 s and a daily purge. Fine at hundreds of players; review at millions of analytics rows.
- **Resource pack, 6.8 MB:**
  - Self-hosted by a 2-thread JDK `HttpServer` (OPS-004) unless nginx is used (`deploy.sh` sets nginx with 5 r/s; REPORTED).
  - A 25-player join wave pulls ~170 MB.

## Reproducible next steps

1. **Commit the harness.**
   - Move `mmo.js`, `firstjoin.js` and a `bench.sh` into `tools/loadtest/`, with a README.
   - Today they exist only in session scratchpads, so the benchmarks cannot be reproduced from the repo.
   - Harness fixes: no teleports into the sky; pre-load destinations.
2. **Target host, bots elsewhere.**
   - Pre-generate the whole border (`/suldworld pregen full`).
   - Then run 10 / 25 / 50 / 100 for 10 min each, plus a 2 h soak at the intended cap.
   - Record CPU model, JVM flags and plugin set.
3. **Capture per run:**
   - `/suldperf` report (JSON)
   - spark profile (`/spark profiler start --thread "Server thread"`)
   - `jcmd JFR.dump`
   - entity and chunk counts
   - DB latency (`db.*` probes)
   - join/quit counts
   - a save-correctness check (log out every bot, compare the DB rows with the bots' last-known state)

**Launch acceptance criteria** (proposed, with margin; 20 TPS = 50 ms per tick):

| Criterion | Target | Reason |
|---|---|---|
| TPS (1-min average) | ≥ 19.5 | |
| Tick p95 | ≤ 40 ms | 20 % headroom for events and boss fights |
| Tick p99 | ≤ 50 ms | |
| Tick max | ≤ 1 s | |
| Heap after the soak | ≤ 70 % of max | no upward trend |
| Join failures | 0 | |
| Save mismatches | 0 | |

The launch player cap is the highest level that passes these criteria. Until re-measured on the target host, plan for **≤ 25**.
