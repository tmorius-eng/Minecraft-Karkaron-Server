-- SÜLD schema V22: class change (docs/CLASS_CHANGE.md).
--   * class_mastery: one versioned JSON document with the skill tree and class gear of every class the player is NOT
--     playing now (the played class keeps its own in skill_data / class_gear). NULL = never changed class.
--   * last_class_change: epoch millis of the last change (the 6-hour cooldown); 0 = never.
ALTER TABLE suld_profiles ADD COLUMN IF NOT EXISTS class_mastery TEXT NULL;
ALTER TABLE suld_profiles ADD COLUMN IF NOT EXISTS last_class_change BIGINT NOT NULL DEFAULT 0;
