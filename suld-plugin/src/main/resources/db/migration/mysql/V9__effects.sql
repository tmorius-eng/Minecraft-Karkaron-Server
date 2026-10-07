-- SÜLD schema V9: equipped particle cosmetics (one statement, so a failure cannot leave it half applied).
--   * aura_id, trail_id, kill_effect_id: the equipped aura, trail and kill effect (cosmetic ids, NULL = none)
ALTER TABLE suld_player_style ADD COLUMN aura_id VARCHAR(48) NULL, ADD COLUMN trail_id VARCHAR(48) NULL, ADD COLUMN kill_effect_id VARCHAR(48) NULL;
