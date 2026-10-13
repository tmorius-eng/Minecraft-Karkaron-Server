-- SÜLD schema V26 (PostgreSQL): players' map pins, «Миний тэмдэг» (docs/WORLD_MAP.md).
--   * one row per pin: its owner, name (cleaned, at most 24 characters), block position in the overworld and colour.
--   * a player keeps at most MapPin.MAX_PINS pins: the insert counts the owner's rows in the same statement.
CREATE TABLE IF NOT EXISTS suld_map_pins (
    pin_id       UUID         NOT NULL PRIMARY KEY,
    player_uuid  UUID         NOT NULL,
    name         VARCHAR(48)  NOT NULL,
    x            INTEGER      NOT NULL,
    y            INTEGER      NOT NULL,
    z            INTEGER      NOT NULL,
    colour       VARCHAR(16)  NOT NULL,
    created_at   BIGINT       NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_map_pins_player ON suld_map_pins (player_uuid, created_at);
