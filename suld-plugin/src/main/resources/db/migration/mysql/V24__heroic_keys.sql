-- SÜLD schema V24 (MySQL / MariaDB, H2 in MySQL mode): «Тэнгэрийн Гэсгээлт Агуй», heroic keys (docs/HEROIC_DUNGEONS.md).
--   * one row per player and dungeon: the highest key the player may open there, and the best key it timed (with the
--     fastest clock at that key). Writes only ever raise them (GREATEST), so concurrent results commute.
--   * every finished heroic run is logged once (append-only), for the boards and the armory.
CREATE TABLE IF NOT EXISTS suld_heroic_keys (
    player_uuid   CHAR(36)     NOT NULL,
    dungeon_id    VARCHAR(64)  NOT NULL,
    open_key      INT          NOT NULL DEFAULT 1,
    best_key      INT          NOT NULL DEFAULT 0,
    best_seconds  INT          NOT NULL DEFAULT 0,
    updated_at    BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, dungeon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suld_heroic_runs (
    run_id        CHAR(36)     NOT NULL,
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
    KEY idx_heroic_runs_board (dungeon_id, outcome, key_level, clock_seconds)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
