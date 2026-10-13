-- SÜLD schema V26 (MySQL / MariaDB, H2 in MySQL mode): players' map pins, «Миний тэмдэг» (docs/WORLD_MAP.md).
--   * one row per pin: its owner, name (cleaned, at most 24 characters), block position in the overworld and colour.
--   * a player keeps at most MapPin.MAX_PINS pins: the insert counts the owner's rows in the same statement.
CREATE TABLE IF NOT EXISTS suld_map_pins (
    pin_id       CHAR(36)     NOT NULL PRIMARY KEY,
    player_uuid  CHAR(36)     NOT NULL,
    name         VARCHAR(48)  NOT NULL,
    x            INT          NOT NULL,
    y            INT          NOT NULL,
    z            INT          NOT NULL,
    colour       VARCHAR(16)  NOT NULL,
    created_at   BIGINT       NOT NULL,
    KEY ix_map_pins_player (player_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
