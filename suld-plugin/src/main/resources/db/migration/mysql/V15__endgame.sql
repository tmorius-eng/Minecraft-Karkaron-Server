-- SÜLD schema V15: progression v2 (docs/PROGRESSION_BALANCE_SPEC.md, docs/ASCENSION_SPEC.md).
--   * endgame: one versioned JSON document: Ascension rank, Тэнгэрийн оноо, the rested-EXP pool and the level curve
--     the stored bar belongs to. NULL = a row from before progression v2 (old curve; it is rescaled on its next load).
ALTER TABLE suld_profiles ADD COLUMN endgame TEXT NULL;
