-- SÜLD schema V23 (PostgreSQL): «Сүнсний Гэрэлт» (death reform 2026-10-13, docs/DEATH_AND_RECOVERY.md).
--   * one beacon per death, where the player fell; it stands until expires_at (epoch ms)
--   * the soul that walks back to it gets back a share of exp_lost, once: claimed_at is set by a compare-and-set
--     (UPDATE ... WHERE claimed_at IS NULL AND expires_at > now), and the EXP is paid only after that update
CREATE TABLE IF NOT EXISTS suld_soul_beacons (
    death_id      UUID         NOT NULL,
    player_uuid   UUID         NOT NULL,
    world         VARCHAR(64)  NOT NULL DEFAULT '',
    x             INT          NOT NULL DEFAULT 0,
    y             INT          NOT NULL DEFAULT 0,
    z             INT          NOT NULL DEFAULT 0,
    exp_lost      BIGINT       NOT NULL DEFAULT 0,
    created_at    BIGINT       NOT NULL,
    expires_at    BIGINT       NOT NULL,
    claimed_at    BIGINT       NULL,
    PRIMARY KEY (death_id),
    CONSTRAINT ck_beacon_exp CHECK (exp_lost >= 0),
    CONSTRAINT ck_beacon_time CHECK (expires_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_soul_beacons_player ON suld_soul_beacons (player_uuid, created_at);
