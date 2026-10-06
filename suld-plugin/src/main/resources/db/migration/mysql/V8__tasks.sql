-- SÜLD schema V8 (MySQL): daily tasks.
--   * task_day:      epoch day (server time zone) the progress belongs to
--   * task_progress: kills per task, e.g. "3,0,12" (the tasks themselves are derived, not stored)
ALTER TABLE suld_player_style ADD COLUMN task_day BIGINT NOT NULL DEFAULT 0;
ALTER TABLE suld_player_style ADD COLUMN task_progress VARCHAR(32) NOT NULL DEFAULT '';
