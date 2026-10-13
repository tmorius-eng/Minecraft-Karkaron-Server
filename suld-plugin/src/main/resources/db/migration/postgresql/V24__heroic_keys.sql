-- SÜLD schema V24 (PostgreSQL): «Тэнгэрийн Гэсгээлт Агуй», heroic keys (docs/HEROIC_DUNGEONS.md).
--   * one row per player and dungeon: the highest key the player may open there, and the best key it timed (with the
--     fastest clock at that key). Writes only ever raise them (GREATEST), so concurrent results commute.
--   * every finished heroic run is logged once (append-only), for the boards and the armory.
CREATE TABLE IF NOT EXISTS suld_heroic_keys (
    player_uuid   UUID         NOT NULL,
    dungeon_id    VARCHAR(64)  NOT NULL,
    open_key      INT          NOT NULL DEFAULT 1,
    best_key      INT          NOT NULL DEFAULT 0,
    best_seconds  INT          NOT NULL DEFAULT 0,
    updated_at    BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, dungeon_id),
    CONSTRAINT ck_heroic_open CHECK (open_key BETWEEN 1 AND 20),
    CONSTRAINT ck_heroic_best CHECK (best_key BETWEEN 0 AND 20 AND best_seconds >= 0)
);

CREATE TABLE IF NOT EXISTS suld_heroic_runs (
    run_id        UUID         NOT NULL,
    dungeon_id    VARCHAR(64)  NOT NULL,
    key_level     INT          NOT NULL,
    outcome       VARCHAR(16)  NOT NULL,
    clock_seconds INT          NOT NULL,
    deaths        INT          NOT NULL DEFAULT 0,
    affixes       VARCHAR(128) NOT NULL DEFAULT '',
    members       VARCHAR(400) NOT NULL DEFAULT '',
    week_id       VARCHAR(16)  NOT NULL DEFAULT '',
    finished_at   BIGINT       NOT NULL,
    PRIMARY KEY (run_id),
    CONSTRAINT ck_heroic_outcome CHECK (outcome IN ('TIMED', 'OVERTIME', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_heroic_runs_board ON suld_heroic_runs (dungeon_id, outcome, key_level, clock_seconds);
