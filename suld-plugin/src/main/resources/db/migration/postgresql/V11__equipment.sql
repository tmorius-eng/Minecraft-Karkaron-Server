-- SÜLD schema V11: equipment (one statement, so a failure cannot leave it half applied).
--   * equipment_data: the accessory slots the game does not store itself, as one versioned JSON document of SÜLD
--     items (identity, rarity, rolls, binding); NULL = nothing equipped there. Armour, hands and the relic are in the
--     player's own inventory.
ALTER TABLE suld_profiles ADD COLUMN IF NOT EXISTS equipment_data TEXT NULL;
