# SÜLD performance baseline (before the production HUD)

Measured 2026-10-07 on `61eca90` plus measurement-only instrumentation (`PerfProbe`, `PerfService`, `/suldperf`).
**Nothing was optimised.** This is the reference the HUD, the later features and the performance audit are compared
against. Raw reports: `audit/perf/baseline/*.json` (per-second samples included); summary: `audit/performance-baseline.json`.

## How it was measured

* `/suldperf start <label>` records every tick's duration (Paper `ServerTickEndEvent`), samples TPS, process/system CPU,
  heap, entities, loaded chunks and players once a second, and times named code paths:
  `combat.hit` (SÜLD hit handler), `combat.damaged` / `combat.damaged_after` (skill-tree damage hooks), `skill.cast`,
  `skill.regen_tick`, `hud.actionbar_tick`, `hud.sidebar_tick` (sidebar + TAB + teams), `gui.click` (menu click
  dispatch), `db.profile.find` / `db.profile.save` (on the storage pool, off the main thread).
  `/suldperf stop` writes the report.
* Item engine costs come from `/suldperf bench 10000`, which calls the engine from outside (the engine code was not
  touched): generation, loot rolls, stack building, validation (first and cached), equipment recompute.
* Scenarios run by `baseline.sh` (dev tooling outside the repo) with mineflayer bots on the same machine.

Environment: Paper 1.21.11 dev server (offline mode, local only), Java 21, `-Xms1G -Xmx2G` (default G1, no tuning
flags), 4 vCPU / 16 GB cloud container **shared with the bots and PostgreSQL 16**, 15 other plugins (EssentialsX,
LuckPerms, WorldGuard, CoreProtect, spark, …). Absolute numbers on production hardware will differ; the value of this
baseline is comparison on the same setup.

## Results

| Scenario | TPS avg | MSPT mean | p95 | p99 | max | ticks >50/100/300 ms | CPU avg/max % | heap avg/max MB | entities | chunks |
|---|---|---|---|---|---|---|---|---|---|---|
| S1 idle, 0 players, 60 s | 19.62 | 0.44 | 1.03 | 1.84 | 4.3 | 0/0/0 | 2.7 / 12.6 | 1368 / 1396 | 0 | 0 |
| S2 1 player, 60 s | 19.94 | 6.59 | 10.42 | 18.18 | 39.2 | 0/0/0 | 12.4 / 44.8 | 1212 / 1540 | 222 | 525 |
| S3 10 players wandering, 120 s | 20.00 | 7.83 | 13.03 | 17.95 | 68.7 | 1/0/0 | 14.4 / 50.0 | 1245 / 1616 | 425 | 749 |
| S4 20 join/quit cycles (+10 online) | 20.00 | 7.83 | 16.57 | 23.19 | 179.0 | 5/2/0 | 11.3 / 39.6 | 1250 / 1625 | 544 | 876 |
| S5 full combat QA suite, 390 s | 19.74 | 6.14 | 8.33 | 30.74 | 1560.2 | 52/30/9 | 7.1 / 90.8 | 1273 / 1654 | 957 | 882 |
| S6 GUI + item actions | 19.95 | 4.41 | 7.27 | 16.13 | 43.5 | 0/0/0 | 7.5 / 47.9 | 1436 / 1629 | 258 | 882 |
| S7 item bench (synthetic) | 19.87 | — | — | — | 698.4 | 1 tick (the bench) | 10.0 / 28.7 | 1283 / 1303 | 152 | 483 |

Targets for comparison (from the performance brief): MSPT < 35 normal, < 45 busy, 50 ceiling. Every gameplay scenario
is well under on mean and p95; p99 stays under 31 ms.

### Code-path costs (µs; main thread unless noted)

| Probe | Scenario | count | mean | p95 | p99 | max |
|---|---|---|---|---|---|---|
| `combat.hit` (SÜLD hit handler) | S5 | 89 762 | 4.2 | 4.8 | 16.1 | 25 568 |
| `combat.damaged` | S5 | 90 567 | 0.3 | 0.5 | 1.2 | 486 |
| `combat.damaged_after` | S5 | 90 338 | 0.3 | 0.4 | 0.7 | 728 |
| `skill.cast` | S5 | 1 633 | 207.6 | 697.3 | 3 906.7 | 19 138 |
| `skill.regen_tick` (all players, 1/s) | S3 (10 pl) | 120 | 42.0 | 88.8 | 193.2 | 210.7 |
| `hud.actionbar_tick` (all players, every 10 ticks) | S3 (10 pl) | 240 | 328.7 | 709.1 | 1 354.2 | 2 770 |
| `hud.sidebar_tick` (sidebar + TAB + teams, every 40 ticks) | S2 (1 pl) | 30 | 1 812.8 | 3 627.7 | 5 333.5 | 5 333 |
| `hud.sidebar_tick` | S3 (10 pl) | 60 | 4 508.0 | 6 823.4 | 39 718.7 | 39 719 |
| `gui.click` (menu dispatch, incl. opening the next menu) | S6 | 5 | 3 499.4 | 6 124.3 | 6 124.3 | 6 124 |
| `db.profile.find` (storage pool) | S4 | 20 | 1 260.9 | 2 629.8 | 2 777.0 | 2 777 |
| `db.profile.save` (storage pool) | S4 | 70 | 2 656.6 | 6 053.0 | 12 011.0 | 12 011 |
| `bench.item.generate` | S7 | 10 000 | 6.0 | 9.5 | 16.7 | 422 |
| `bench.loot.roll` | S7 | 10 000 | 10.3 | 28.9 | 63.6 | 311 |
| `bench.item.stack_build` (ItemStack + tooltip) | S7 | 2 000 | 80.3 | 181.8 | 269.7 | 4 797 |
| `bench.item.check_uncached` (decode + validate) | S7 | 2 000 | 30.0 | 64.2 | 122.0 | 1 050 |
| `bench.item.check_cached` | S7 | 10 000 | 10.8 | 14.6 | 39.6 | 330 |
| `bench.equipment.compute` | S7 | 2 000 | 11.8 | 17.2 | 49.9 | 165 |

Database latency is measured on the storage pool (it never blocks a tick); PostgreSQL ran on the same loaded host.

### Resource pack

`suld-resourcepack.zip` (built by Gradle, self-hosted): **292 666 bytes (285.8 KiB)**, 225 files, 1 252 569 bytes
uncompressed.

## Observations for the later audit (not acted on)

1. `hud.sidebar_tick` grows with players × players (`refreshTeams` rewrites every online player's team on every
   viewer's board): 1.8 ms at 1 player, 4.5 ms mean / 39.7 ms p99 at 10. It runs once per 40 ticks, so it is one
   expensive tick every 2 s, not a steady load. The first candidate for the performance audit.
2. S5's ticks over 100/300 ms (30 / 9) fall during the QA harness's own work (sky-arena build, dummy spawning, chunk
   tickets, teleports). They need attribution with spark before any conclusion; S1–S4 and S6 have no 300 ms ticks.
3. `skill.cast` p99 3.9 ms / max 19 ms: some spells spawn many entities/particles in the cast tick (volleys, stampede).
4. `combat.hit` max 25.6 ms on one hit (p99 16 µs): a rare outlier (first use of a code path / GC); watch in the audit.
5. Heap stays 1.2–1.65 GB of a 2 GB max with no growth across S1–S6 (no leak visible over ~15 minutes; the 100-profile
   join/quit memory test belongs to the audit).

## After the production HUD (A/B, same session)

Run-to-run comparisons proved unreliable on this setup: wandering bots keep loading chunks and the steppe keeps
spawning wolves, so MSPT follows the world, not the code (HUD off: 7.5 ms at 323 entities, 14.4 ms at 634). The HUD's
cost was therefore measured A/B in one session with the same 10 bots, HUD drawing switched off/on every 60 s
(`/suldperf hud off|on`, measurement-only). Raw: `audit/perf/hud/ab_*.json`.

| Window | entities | MSPT mean | p95 | p99 | max | HUD µs/tick (probes) |
|---|---|---|---|---|---|---|
| off 1 | 323 | 7.52 | 12.09 | 17.76 | 54.7 | 5 |
| on 1 | 591 | 13.18 | 18.40 | 25.24 | 90.0 | 249 |
| off 2 | 634 | 14.39 | 20.13 | 26.40 | 64.6 | 4 |
| on 2 | 638 | 14.96 | 20.74 | 29.94 | 75.2 | 213 |

Paired at equal world size (off 2 → on 2): **+0.57 ms mean MSPT for 10 players**; the probes put the HUD itself at
0.21–0.25 ms per tick (`hud.render` 78–92 µs per player per 4-tick pass, `hud.target` crosshair ray-trace 12–19 µs).
The old text action bar cost ~33 µs/tick for 10 players; the new HUD draws far more (two bar rows, slots, buffs,
target frame) for about 0.2 ms more per tick. Well inside the 35 ms budget; noted for the audit.
