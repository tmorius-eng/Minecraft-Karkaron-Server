-- SÜLD schema V25 (MySQL / MariaDB, H2 in MySQL mode): weapon mastery, «Зэвсгийн ур чадвар» (docs/MASTERY_AND_PROFESSIONS.md).
--   * one row per player and track (SCIMITAR, COMPOSITE_BOW, SHAMANIC_STAFF, HAMMER, LANCE): the mastery points ever
--     earned with that kind of weapon. The level follows from the total (WeaponMastery.fromTotal).
--   * writes only add (points = points + ?), so batches written in any order end at the same total.
CREATE TABLE IF NOT EXISTS suld_weapon_mastery (
    player_uuid   CHAR(36)     NOT NULL,
    track         VARCHAR(24)  NOT NULL,
    points        DOUBLE       NOT NULL DEFAULT 0,
    updated_at    BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, track)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
