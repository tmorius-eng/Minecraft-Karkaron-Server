# SÜLD analytics

**Status:** FUNCTIONAL (2026-10-11). The database sink and session totals are implemented and tested on PostgreSQL;
the first-five-minutes funnel is emitted end to end (audit ANA-001); `tools/analytics/funnel.py` prints the funnel,
D1/D7 retention and the session summary as Markdown. There is no web dashboard (the query tool is the dashboard v0).

## Funnel (docs/audits/2026-10-11/03-first-five-minutes.md)

| Step | Event (`AnalyticsEventType`) | Emitted where | Attributes |
|---|---|---|---|
| join_attempt | `join_attempt` | `AuthenticationService.onPreLogin`, every outcome | `outcome`: `allowed`, `allowed:new`, `denied:<code>`, `profile_failed:<why>`, `refused_before_suld:<result>` |
| join_success | `session_start` (every join), `first_login` (first) | `PlayerLifecycleListener`, `AuthenticationService` | |
| resource_pack_ready | `resource_pack_status` | `ResourcePackService.onStatus`, every status | `status`, `required`, `ms` since the pack was sent |
| class_selected | `class_selected` | `ClassSelectionGui` | |
| first_action / first_objective | `tutorial_step` | `TutorialService.complete`, every step | `step`, `of`, `name`, `completed` |
| first chapter | `quest_complete` | `QuestService.complete`, every chapter | `quest`, `chapter`, `of`, `level` |
| first_combat | `first_mob_kill` | `CombatListener`, once per session | |
| first_reward | `first_item`, `first_rare_item` | `CombatListener`, `DungeonService` | |
| five_minute_active | `active_5m` | `ActivePlaytimeService.closeMinute` at the 5th **validated active** minute of a session | `level`, `combat_min` |
| retention marks | `retention_15m`, `retention_30m`, `retention_1h` | the same, at 15 / 30 / 60 active minutes | |
| first death | `first_death` | `DeathService`, the player's first stored death (`death_seq == 1`) | `level`, `cause` |
| first boss | `first_boss` | `DungeonService.complete`, the player's **first** clear of that dungeon (every clear is a `boss_participation`) | `dungeon` |
| level up | `level_up` | `DefaultProgressionService` | |
| Их Хуралдай | `vote_cast`, `vote_closed`, `campaign_start`, `campaign_contribution` (tier reached), `campaign_end`, `campaign_reward_claim`, `chronicle_entry`, `world_effect_applied` | docs/KHURALDAI.md | |

```bash
tools/analytics/funnel.py --url postgresql://suld:…@127.0.0.1:5432/suld --days 7
```

prints the funnel as distinct players per step and the share of first logins, the D1/D7 cohort table (first_login day →
seen again by `session_start` 1 and 7 days later) and the session summary from `suld_sessions`. The closed alpha's exit
numbers (`09-public-launch-and-growth.md` §3.3): `join_success → first_mob_kill ≥ 70 %`, `active_5m ≥ 50 %`, D1 ≥ 40 %.

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

* A web dashboard (the Markdown tool above is the dashboard v0).
* The HTTP export.
* Exact combat time (the totals count combat *minutes*).
* Per-spell usage.
