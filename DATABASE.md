# SULD Database

SULD supports **MySQL/MariaDB** and **PostgreSQL** in production, and an
in-memory backend for development/tests. All access is asynchronous (HikariCP
pool + an IO executor). Schema is applied by a forward-only migrator
(`SchemaMigrator`) and tracked in `suld_schema_version`.

Conventions:

- All timestamps are stored as **epoch milliseconds (`BIGINT`)** to avoid
  timezone/dialect ambiguity.
- UUIDs are `CHAR(36)` on MySQL and native `UUID` on PostgreSQL (`SqlDialect`
  binds them appropriately).
- Every mutable aggregate carries a `version BIGINT` column for **optimistic
  locking**, a cornerstone of the anti-duplication strategy.
- Table prefix: `suld_`.

## Phase 1 schema (shipped: `V1__init.sql`)

### `suld_profiles`
One row per character.

| column | type | notes |
|---|---|---|
| `player_uuid` | UUID / CHAR(36) | PK |
| `name` | VARCHAR(16) | last known in-game name |
| `class_id` | VARCHAR(32) NULL | `PlayerClass.id()`, null until chosen |
| `level` | INT | authoritative level (`>= 1`) |
| `exp_into_level` | BIGINT | progress toward next level |
| `created_at` | BIGINT | epoch millis |
| `last_seen_at` | BIGINT | epoch millis |
| `version` | BIGINT | optimistic lock |

Level + progress are stored (not a single cumulative total) so rebalancing the
curve never demotes a player.

### `suld_world_unique_items`
The storage backbone of the **world-unique item system** (e.g. *Хөх Сүлд / The
Blue Standard*). One row per unique item identity.

| column | type | notes |
|---|---|---|
| `item_key` | VARCHAR(64) | **PK** — the unique identity (e.g. `khukh_suld`) |
| `item_uuid` | UUID / CHAR(36) | **UNIQUE** — the one minted instance |
| `owner_uuid` | UUID NULL | current owner, null if unclaimed/lost |
| `state` | VARCHAR(16) | `UNCLAIMED` / `OWNED` / `LOST` |
| `acquired_at` | BIGINT NULL | epoch millis |
| `version` | BIGINT | optimistic lock |

The `item_key` primary key makes a second copy **impossible at the storage
level**; the unique `item_uuid` guarantees a single minted instance. Acquisition
is a single transactional `UPDATE … WHERE item_key = ? AND state = 'UNCLAIMED'`
(or owner/version compare-and-set), so even under a crash/restart race only one
winner is possible — no duplicate creation.

### `suld_world_unique_history`
Append-only ownership/event history for unique items (`GRANTED`, `TRANSFERRED`,
`RECOVERED`, `LOST`), enabling recovery and admin audit.

### `suld_analytics_events`
Append-only analytics stream (`type`, optional `player_uuid`, `at`, JSON
`attributes`). Indexed on `(type, at)`. Backs the retention funnel and a future
dashboard.

### `suld_audit_log`
Administrative/security audit trail (`actor`, `action`, `target`, `detail`,
`at`). `/revive` and world-unique recoveries write here.

### `suld_schema_version`
Migration bookkeeping (`version`, `description`, `applied_at`).

## Anti-duplication & integrity strategy

1. **Single minted instance** per unique item via PK + UNIQUE constraints.
2. **Transactional acquisition** (compare-and-set on `state`/`owner`/`version`).
3. **Optimistic locking** on every mutable aggregate (`version`), so a stale
   write is rejected rather than clobbering newer state.
4. **Server-authoritative item identity**: items carry a persistent UUID and
   provenance; the server validates item UUIDs against the database rather than
   trusting NBT alone (planned with the item system — see ROADMAP).
5. **Audit everything** sensitive (grants, transfers, admin recoveries).
6. **Crash safety**: because uniqueness is a DB constraint, a restart or crash
   mid-acquisition can never yield two owners or two copies.

## Target schema (later phases — not yet shipped)

Planned tables, added by future migrations as each system lands:

- `suld_items` — item instances: `item_uuid` PK, owner, `base_item`, rarity,
  item level, soulbound flag, durability, upgrade level, JSON stats/affixes,
  provenance, `version`.
- `suld_skill_trees`, `suld_player_skills` — class skill trees and unlocks.
- `suld_quests`, `suld_player_quests`, `suld_achievements`,
  `suld_player_achievements`.
- `suld_clans`, `suld_clan_members`, `suld_parties` (parties may be
  memory-only).
- `suld_dungeon_runs`, `suld_boss_participation`, `suld_world_events`.
- `suld_deaths` — hardcore death ledger (soul state, losses, recovery).
- `suld_discoveries` — exploration/discovery unlocks.
- `suld_leaderboards` (or materialized views) and `suld_player_stats`.

Each ships with both MySQL and PostgreSQL DDL under
`suld-plugin/src/main/resources/db/migration/<dialect>/`.
