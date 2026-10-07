-- SÜLD schema V10: the skill tree (one statement, so a failure cannot leave it half applied).
--   * skill_data: the player's versioned skill-tree document (ranks by node id, saved builds, granted points,
--     respec history); NULL = nothing learned yet
ALTER TABLE suld_profiles ADD COLUMN IF NOT EXISTS skill_data TEXT NULL;
