package mn.suld.api.item;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A concrete, individual item in the world — the thing a player actually holds.
 *
 * <p>Distinct from {@link ItemDefinition} (the template): every instance has its own {@link #uuid()} (identity for
 * provenance and duplicate detection, independent of any player), a rolled rarity and item level, rolled base stats
 * and affixes, and its binding. The plugin stores it in the stack's PersistentDataContainer through
 * {@link ItemCodec}; the server is authoritative, and {@link ItemValidator} rejects anything the catalog could not
 * have produced.
 *
 * <p>Immutable; upgrades and binding produce a new instance with the same uuid.
 */
public final class ItemInstance {

    /** Version of the stored form. 1 = before the item engine (fraction crit values, no affixes). */
    public static final int SCHEMA_VERSION = 2;

    private final String definitionId;
    private final UUID uuid;
    private final ItemRarity rarity;
    private final int itemLevel;
    private final Map<ItemStat, Double> stats;
    private final List<RolledAffix> affixes;
    private final boolean soulbound;
    private final UUID boundTo;
    private final int upgradeLevel;
    private final String provenance;
    private final int schemaVersion;

    public ItemInstance(String definitionId, UUID uuid, ItemRarity rarity, int itemLevel, Map<ItemStat, Double> stats,
                        List<RolledAffix> affixes, boolean soulbound, @Nullable UUID boundTo, int upgradeLevel,
                        String provenance, int schemaVersion) {
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
        this.affixes = affixes == null ? List.of() : List.copyOf(affixes);
        this.soulbound = soulbound;
        this.boundTo = boundTo;
        this.upgradeLevel = Math.max(0, upgradeLevel);
        this.provenance = provenance == null ? "unknown" : provenance;
        this.schemaVersion = schemaVersion;
    }

    /** An instance without affixes or binding owner (materials, fixed items, tests). */
    public ItemInstance(String definitionId, UUID uuid, ItemRarity rarity, int itemLevel,
                        Map<ItemStat, Double> stats, boolean soulbound, int upgradeLevel, String provenance) {
        this(definitionId, uuid, rarity, itemLevel, stats, List.of(), soulbound, null, upgradeLevel, provenance, SCHEMA_VERSION);
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

    /** Rolled base stats (affix values are separate, see {@link #affixes()}). */
    public @NotNull Map<ItemStat, Double> stats() {
        return java.util.Collections.unmodifiableMap(stats);
    }

    public double stat(ItemStat stat) {
        return stats.getOrDefault(stat, 0.0);
    }

    public @NotNull List<RolledAffix> affixes() {
        return affixes;
    }

    /** Kept on death, never dropped or traded. */
    public boolean soulbound() {
        return soulbound;
    }

    /** The player this item is bound to, or null while unbound. */
    public @Nullable UUID boundTo() {
        return boundTo;
    }

    public boolean bound() {
        return boundTo != null;
    }

    public int upgradeLevel() {
        return upgradeLevel;
    }

    public @NotNull String provenance() {
        return provenance;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public ItemInstance boundTo(UUID owner) {
        return new ItemInstance(definitionId, uuid, rarity, itemLevel, stats, affixes, soulbound, owner, upgradeLevel, provenance, schemaVersion);
    }

    /** Same item (uuid, rarity, affixes, binding) at another level with other base stats. */
    public ItemInstance reforged(int newItemLevel, Map<ItemStat, Double> newStats, int newUpgradeLevel) {
        return new ItemInstance(definitionId, uuid, rarity, newItemLevel, newStats, affixes, soulbound, boundTo, newUpgradeLevel, provenance, schemaVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemInstance that)) return false;
        return itemLevel == that.itemLevel && soulbound == that.soulbound && upgradeLevel == that.upgradeLevel
                && definitionId.equals(that.definitionId) && uuid.equals(that.uuid) && rarity == that.rarity
                && stats.equals(that.stats) && affixes.equals(that.affixes) && Objects.equals(boundTo, that.boundTo)
                && provenance.equals(that.provenance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definitionId, uuid, rarity, itemLevel, stats, affixes, soulbound, boundTo, upgradeLevel);
    }

    @Override
    public String toString() {
        return "ItemInstance{" + definitionId + " " + rarity + " iLvl" + itemLevel
                + " +" + upgradeLevel + " affixes=" + affixes.size() + " uuid=" + uuid + '}';
    }
}
