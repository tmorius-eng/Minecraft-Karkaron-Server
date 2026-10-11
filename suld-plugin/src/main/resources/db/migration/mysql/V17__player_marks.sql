-- SÜLD schema V17: per-player progress marks (docs/KHURALDAI.md §Marks).
--   kind  DUNGEON_CLEAR  key = dungeon id   value = first clear, epoch millis
--   kind  SITE_SEEN      key = site id      value = first visit, epoch millis
--   kind  OVOO_DAY       key = ovoo id      value = last blessing, epoch day (server zone)
-- The player file keeps its own copy for the fast in-game checks; this table is the queryable truth for the chronicle,
-- «Миний түүх» and the leaderboards, and is backfilled from the player file on login (idempotent upsert).
CREATE TABLE IF NOT EXISTS suld_player_marks (
    player_uuid   CHAR(36)     NOT NULL,
    kind          VARCHAR(16)  NOT NULL,
    mark_key      VARCHAR(64)  NOT NULL,
    mark_value    BIGINT       NOT NULL,
    at            BIGINT       NOT NULL,
    PRIMARY KEY (player_uuid, kind, mark_key),
    KEY idx_marks_kind_key (kind, mark_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
