-- SÜLD schema V13: class gear + active playtime (one statement, so a failure cannot leave it half applied).
--   * class_gear: the class gear record as one versioned JSON document (armour level and XP, tier, enhancement, the
--     identities of the class armour pieces and weapon, dungeons cleared, the last clears for repeat fatigue);
--     NULL = no record yet. The stacks in the inventory are regenerated from it (docs/CLASS_GEAR_SYSTEM.md).
--   * active_minutes: validated active minutes per category as versioned JSON (docs/ACTIVE_PLAYTIME_SPEC.md).
ALTER TABLE suld_profiles ADD COLUMN class_gear TEXT NULL, ADD COLUMN active_minutes TEXT NULL;
