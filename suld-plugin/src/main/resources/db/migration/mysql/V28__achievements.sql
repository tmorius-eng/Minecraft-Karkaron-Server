-- SÜLD schema V28 (MySQL / MariaDB, H2 in MySQL mode): achievements and the season's pass «Хуралдайн Зам»
-- (docs/ACHIEVEMENTS_AND_PASS.md).
--   * suld_player_deeds: per player and DEED|subject key, a count (writes add) or a best value (writes keep the
--     greater): batches written in any order end at the same values.
--   * suld_player_achievements: one row per unlock; a second insert changes nothing.
--   * suld_pass_points: per player and season, the points (writes add).
--   * suld_pass_claims: one row per reward claimed (FREE/5, HEROIC/50): the primary key makes a claim once.
--   * suld_pass_heroic: the heroic track opened for a season (by whom: staff, credits).
CREATE TABLE IF NOT EXISTS suld_player_deeds (
    player_uuid   CHAR(36)     NOT NULL,
    deed_key      VARCHAR(96)  NOT NULL,
    value         BIGINT       NOT NULL DEFAULT 0,
    updated_at    BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, deed_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS suld_player_achievements (
    player_uuid     CHAR(36)     NOT NULL,
    achievement_id  VARCHAR(64)  NOT NULL,
    unlocked_at     BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, achievement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS suld_pass_points (
    player_uuid   CHAR(36)     NOT NULL,
    season_id     VARCHAR(16)  NOT NULL,
    points        BIGINT       NOT NULL DEFAULT 0,
    updated_at    BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, season_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS suld_pass_claims (
    player_uuid   CHAR(36)     NOT NULL,
    season_id     VARCHAR(16)  NOT NULL,
    reward_key    VARCHAR(16)  NOT NULL,
    claimed_at    BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, season_id, reward_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS suld_pass_heroic (
    player_uuid   CHAR(36)     NOT NULL,
    season_id     VARCHAR(16)  NOT NULL,
    opened_at     BIGINT       NOT NULL,
    source        VARCHAR(32)  NOT NULL,
    PRIMARY KEY (player_uuid, season_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
