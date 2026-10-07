package mn.suld.api.loot;

import mn.suld.api.clazz.PlayerClass;

import java.util.UUID;

/**
 * Who gets the loot and from what.
 *
 * @param level       level the loot scales to (the mob's level, the dungeon's level, the player's level for quests)
 * @param tier        where it comes from (decides the rarity band)
 * @param playerClass class of the receiver, for class weighting (null = none)
 * @param lootBonus   the receiver's loot chance stat in percent points (raises rare-drop chances)
 * @param owner       the receiver (soulbound items are bound to them), or null
 * @param source      provenance written on every generated item ("mob:mob.goviin_chono")
 */
public record LootContext(int level, LootTier tier, PlayerClass playerClass, double lootBonus, UUID owner, String source) {
    public LootContext {
        level = Math.max(1, Math.min(mn.suld.api.item.ItemDefinition.MAX_LEVEL, level));
        tier = tier == null ? LootTier.NORMAL : tier;
        lootBonus = Math.max(0, lootBonus);
        source = source == null ? "unknown" : source;
    }

    public static LootContext of(int level, LootTier tier) {
        return new LootContext(level, tier, null, 0, null, "test");
    }
}
