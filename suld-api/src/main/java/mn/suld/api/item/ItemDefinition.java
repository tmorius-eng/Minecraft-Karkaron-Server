package mn.suld.api.item;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable template for an item. Data-driven (loaded from config/registry);
 * {@link #roll(UUID, int, String)} mints a concrete {@link ItemInstance}.
 *
 * @param id              stable item id (e.g. "weapon.suld_ild_tenger")
 * @param displayName     Mongolian display name
 * @param baseMaterial    backing Bukkit material id (e.g. "minecraft:netherite_sword")
 * @param rarity          rarity tier
 * @param customModelData resource-pack CustomModelData, or 0 for none
 * @param baseStats       stats at item level 1
 * @param statPerLevel    added per item level above 1
 * @param soulbound       whether minted instances bind on pickup
 */
public record ItemDefinition(
        String id,
        String displayName,
        String baseMaterial,
        ItemRarity rarity,
        int customModelData,
        Map<ItemStat, Double> baseStats,
        Map<ItemStat, Double> statPerLevel,
        boolean soulbound) {

    public ItemDefinition {
        baseStats = baseStats == null ? Map.of() : Map.copyOf(baseStats);
        statPerLevel = statPerLevel == null ? Map.of() : Map.copyOf(statPerLevel);
    }

    /** Mint a concrete instance at the given item level. */
    public ItemInstance roll(UUID uuid, int itemLevel, String provenance) {
        java.util.EnumMap<ItemStat, Double> rolled = new java.util.EnumMap<>(ItemStat.class);
        for (ItemStat stat : ItemStat.values()) {
            double base = baseStats.getOrDefault(stat, 0.0);
            double per = statPerLevel.getOrDefault(stat, 0.0);
            double value = base + per * (itemLevel - 1);
            if (value != 0.0) {
                rolled.put(stat, Math.round(value * 100.0) / 100.0);
            }
        }
        return new ItemInstance(id, uuid, rarity, itemLevel, rolled, soulbound, 0, provenance);
    }
}
