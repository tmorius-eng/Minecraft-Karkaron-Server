-- SÜLD schema V23 (MySQL / MariaDB, H2 in MySQL mode): «Сүнсний Гэрэлт» (death reform 2026-10-13).
--   * one beacon per death, where the player fell; it stands until expires_at (epoch ms)
--   * the soul that walks back to it gets back a share of exp_lost, once: claimed_at is set by a compare-and-set
--     (UPDATE ... WHERE claimed_at IS NULL AND expires_at > now), and the EXP is paid only after that update
CREATE TABLE IF NOT EXISTS suld_soul_beacons (
    death_id      CHAR(36)     NOT NULL,
    player_uuid   CHAR(36)     NOT NULL,
    world         VARCHAR(64)  NOT NULL DEFAULT '',
    x             INT          NOT NULL DEFAULT 0,
    y             INT          NOT NULL DEFAULT 0,
    z             INT          NOT NULL DEFAULT 0,
    exp_lost      BIGINT       NOT NULL DEFAULT 0,
    created_at    BIGINT       NOT NULL,
    expires_at    BIGINT       NOT NULL,
    claimed_at    BIGINT       NULL,
    PRIMARY KEY (death_id),
    KEY idx_soul_beacons_player (player_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
