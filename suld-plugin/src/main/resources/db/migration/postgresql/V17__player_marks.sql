-- SÜLD schema V17 (PostgreSQL): per-player progress marks (docs/KHURALDAI.md §Marks).
--   kind  DUNGEON_CLEAR  key = dungeon id   value = first clear, epoch millis
--   kind  SITE_SEEN      key = site id      value = first visit, epoch millis
--   kind  OVOO_DAY       key = ovoo id      value = last blessing, epoch day (server zone)
-- The player file keeps its own copy for the fast in-game checks; this table is the queryable truth for the chronicle,
-- «Миний түүх» and the leaderboards, and is backfilled from the player file on login (idempotent upsert).
CREATE TABLE IF NOT EXISTS suld_player_marks (
    player_uuid   UUID         NOT NULL,
    kind          VARCHAR(16)  NOT NULL,
    mark_key      VARCHAR(64)  NOT NULL,
    mark_value    BIGINT       NOT NULL,
    at            BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, kind, mark_key),
    CONSTRAINT ck_mark_kind CHECK (kind IN ('DUNGEON_CLEAR', 'SITE_SEEN', 'OVOO_DAY'))
);
CREATE INDEX IF NOT EXISTS idx_marks_kind_key ON suld_player_marks (kind, mark_key);
