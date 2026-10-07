-- SÜLD schema V8: daily tasks (one statement, so a failure cannot leave it half applied).
--   * task_day:      epoch day (server time zone) the progress belongs to
--   * task_progress: pinned level and kills per task, e.g. "12|3,0,12" (the tasks themselves are derived, not stored)
ALTER TABLE suld_player_style ADD COLUMN IF NOT EXISTS task_day BIGINT NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS task_progress VARCHAR(32) NOT NULL DEFAULT '';
