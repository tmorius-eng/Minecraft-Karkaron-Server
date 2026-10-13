-- SÜLD schema V27 (MySQL / MariaDB, H2 in MySQL mode): the crafts, «Мэргэжил» (docs/MASTERY_AND_PROFESSIONS.md).
--   * one row per player and craft (TOMRIIN_DARKHAN, EMIIN_BARIACH, ADUUCHIN, ESGIICH): the points ever earned by
--     gathering. The level follows from the total (Profession.fromTotal).
--   * writes only add (points = points + ?), so batches written in any order end at the same total.
CREATE TABLE IF NOT EXISTS suld_professions (
    player_uuid   CHAR(36)     NOT NULL,
    craft         VARCHAR(24)  NOT NULL,
    points        BIGINT       NOT NULL DEFAULT 0,
    updated_at    BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, craft)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
