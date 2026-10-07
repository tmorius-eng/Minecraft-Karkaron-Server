# SÜLD analytics

**Status:** FUNCTIONAL_BUT_INCOMPLETE (Stage C3b). The database sink and session totals are implemented and tested on
PostgreSQL. MySQL migrations are written but not run here. There are no dashboards.

## What is recorded

* **Events** (`AnalyticsEvent`, the types in `AnalyticsEventType`). They are recorded by gameplay code through
  `AnalyticsSink.record`, which never blocks.
* **Session totals** (`SessionTotals`): one object per online player, bumped by hooks that already fire (one add each,
  no per-tick work). When the player leaves they become a single `session_end` event:

| Field | Source |
|---|---|
| `duration_s` | join → quit |
| `active_min`, `combat_min` | ActivePlaytime validated minutes (combat = COMBAT or DUNGEON category) |
| `mobs` | CombatListener (SÜLD mob kills) |
| `dmg_dealt`, `dmg_taken` | ActivePlaytimeService damage listener (mobs only) |
| `quests`, `discoveries`, `exp` | `ExpGainedEvent` (QUEST, DISCOVERY, all sources) |
| `dungeon_runs`, `dungeon_clears`, `bosses` | DungeonService start / completion (credited participants) |
| `armor_xp`, `mastery_xp` | ClassArmor |
| `deaths`, `revives` | DeathService (death; lock ended by timer, admin revive or reset) |

`first_mob_kill`, `first_item` and `first_rare_item` are now emitted once per session. They used to be emitted for every kill or drop, giving 861 `first_item` rows in a short bot test. The `items` and `rare_items` counts are in the totals.

## Sinks (`analytics.sink`)

* **`log` (default):** buffered and written to the server log on flush.
* **`database`** (needs MySQL or PostgreSQL storage), `JdbcAnalyticsSink`:
  * `record` only queues. The queue is bounded by `analytics.queue-limit` (default 10,000): when it is full the oldest
    event is dropped and counted, so a database outage cannot grow memory.
  * The existing async flush timer (`analytics.flush-interval-seconds`, default 30 s) writes the queue as JDBC batches
    of 500 on the shared I/O pool, never on the main thread. A failed batch is logged and dropped; gameplay is never
    blocked.
  * Events go to `suld_analytics_events` (V1, indexed by type and time). Each `session_end` also becomes one
    `suld_sessions` row (V14) with columns for the main totals plus the full totals as JSON.
  * `analytics.skip-types` (default `[exp_gain]`) lists high-volume types that are not stored, because the session
    totals carry them.

## Retention and size

* `analytics.retention-days` (default 90; 0 keeps everything). Once a day an async task deletes older rows from both
  tables in batches of 5,000.
* **Rough size:** a busy player produces about 20–60 stored events per hour (level-ups, items, deaths, bosses) plus 1
  session row. With 100 players × 4 h/day that is about 0.5 M rows over 90 days, under 200 MB with the JSON
  attributes. Per-kill and per-EXP rows are deliberately not stored.

## Not done

* Dashboards and queries.
* The HTTP export.
* Exact combat time (the totals count combat *minutes*).
* Per-spell usage.
