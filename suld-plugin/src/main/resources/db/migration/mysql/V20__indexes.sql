-- SÜLD schema V20 (audit OPS-007): the leaderboards (/top, every 60 s) sort suld_profiles by level and by coins, and
-- the analytics purge deletes by time alone; until now only the primary key and (type, at) existed.
CREATE INDEX idx_profiles_level ON suld_profiles (level, exp_into_level);
CREATE INDEX idx_profiles_currency ON suld_profiles (currency);
CREATE INDEX idx_analytics_at ON suld_analytics_events (at);
