-- SÜLD schema V14: one row per play session with its aggregate totals (docs/ANALYTICS.md). Written by the database
-- analytics sink from the session_end event, batched off the main thread; purged after analytics.retention-days.
CREATE TABLE IF NOT EXISTS suld_sessions (
    id              BIGSERIAL    PRIMARY KEY,
    player_uuid     UUID         NOT NULL,
    started_at      BIGINT       NOT NULL,
    ended_at        BIGINT       NOT NULL,
    duration_s      BIGINT       NOT NULL,
    active_min      BIGINT       NOT NULL,
    combat_min      BIGINT       NOT NULL,
    mobs            BIGINT       NOT NULL,
    exp             BIGINT       NOT NULL,
    deaths          INT          NOT NULL,
    totals          TEXT         NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_player ON suld_sessions (player_uuid, ended_at);
CREATE INDEX IF NOT EXISTS idx_sessions_ended ON suld_sessions (ended_at);
