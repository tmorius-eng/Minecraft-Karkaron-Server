-- SÜLD schema V14: one row per play session with its aggregate totals (docs/ANALYTICS.md). Written by the database
-- analytics sink from the session_end event, batched off the main thread; purged after analytics.retention-days.
CREATE TABLE IF NOT EXISTS suld_sessions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    player_uuid     CHAR(36)     NOT NULL,
    started_at      BIGINT       NOT NULL,
    ended_at        BIGINT       NOT NULL,
    duration_s      BIGINT       NOT NULL,
    active_min      BIGINT       NOT NULL,
    combat_min      BIGINT       NOT NULL,
    mobs            BIGINT       NOT NULL,
    exp             BIGINT       NOT NULL,
    deaths          INT          NOT NULL,
    totals          TEXT         NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sessions_player (player_uuid, ended_at),
    KEY idx_sessions_ended (ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
