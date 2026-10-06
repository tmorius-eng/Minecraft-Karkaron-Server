package mn.suld.api.item;

import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A concrete, individual item in the world — the thing a player actually holds.
 *
 * <p>Distinct from {@link ItemDefinition} (the template): every ItemInstance has
 * its own {@link #uuid()} for provenance and anti-duplication, a rolled rarity
 * and item level, and a concrete stat block. Instances serialize to/from a
 * Bukkit {@code PersistentDataContainer} in the plugin layer, so the server is
 * authoritative over item identity rather than trusting raw NBT.
 *
 * <p>Immutable; upgrades/reforges produce a new instance (preserving the uuid).
 */
public final class ItemInstance {

    private final String definitionId;
    private final UUID uuid;
    private final ItemRarity rarity;
    private final int itemLevel;
    private final Map<ItemStat, Double> stats;
    private final boolean soulbound;
    private final int upgradeLevel;
    private final String provenance;

    public ItemInstance(String definitionId, UUID uuid, ItemRarity rarity, int itemLevel,
                        Map<ItemStat, Double> stats, boolean soulbound, int upgradeLevel, String provenance) {
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        if (itemLevel < 1) {
            throw new IllegalArgumentException("itemLevel must be >= 1: " + itemLevel);
        }
        this.itemLevel = itemLevel;
        // new EnumMap<>(map) throws for an empty non-EnumMap (e.g. Map.of()), so copy explicitly.
        this.stats = new EnumMap<>(ItemStat.class);
        if (stats != null) {
            this.stats.putAll(stats);
        }
        this.soulbound = soulbound;
        this.upgradeLevel = Math.max(0, upgradeLevel);
        this.provenance = provenance == null ? "unknown" : provenance;
    }

    public @NotNull String definitionId() {
        return definitionId;
    }

    public @NotNull UUID uuid() {
        return uuid;
    }

    public @NotNull ItemRarity rarity() {
        return rarity;
    }

    public int itemLevel() {
        return itemLevel;
    }

    public @NotNull Map<ItemStat, Double> stats() {
        return java.util.Collections.unmodifiableMap(stats);
    }

    public double stat(ItemStat stat) {
        return stats.getOrDefault(stat, 0.0);
    }

    public boolean soulbound() {
        return soulbound;
    }

    public int upgradeLevel() {
        return upgradeLevel;
    }

    public @NotNull String provenance() {
        return provenance;
    }

    @Override
    public String toString() {
        return "ItemInstance{" + definitionId + " " + rarity + " iLvl" + itemLevel
                + " +" + upgradeLevel + " uuid=" + uuid + '}';
    }
}
