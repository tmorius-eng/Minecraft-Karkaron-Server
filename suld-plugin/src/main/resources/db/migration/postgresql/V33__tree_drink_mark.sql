-- SÜLD schema V33 (PostgreSQL): the Silver Tree's daily drink is a progress mark (docs/world/OVOO.md).
--   kind  TREE_DRINK  key = drink id  value = the epoch day (server zone) it was last drunk
ALTER TABLE suld_player_marks DROP CONSTRAINT IF EXISTS ck_mark_kind;
ALTER TABLE suld_player_marks ADD CONSTRAINT ck_mark_kind CHECK (kind IN ('DUNGEON_CLEAR', 'SITE_SEEN', 'OVOO_DAY', 'TREE_DRINK'));
