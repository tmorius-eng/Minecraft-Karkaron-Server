-- SÜLD schema V7 (PostgreSQL): daily login reward.
--   * daily_day:    epoch day (server time zone) of the last claim, 0 = never
--   * daily_streak: streak day paid by that claim (1..7)
ALTER TABLE suld_player_style ADD COLUMN IF NOT EXISTS daily_day BIGINT NOT NULL DEFAULT 0;
ALTER TABLE suld_player_style ADD COLUMN IF NOT EXISTS daily_streak INT NOT NULL DEFAULT 0;
