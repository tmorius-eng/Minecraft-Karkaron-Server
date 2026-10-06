-- SÜLD schema V6 (MySQL): discovered regions.
--   * discovered_regions: bit N set = world region N was discovered (its discovery EXP was paid exactly once)
ALTER TABLE suld_player_style ADD COLUMN discovered_regions BIGINT NOT NULL DEFAULT 0;
