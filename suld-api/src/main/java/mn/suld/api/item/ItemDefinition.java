package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.skill.tree.Effect;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable template for an item, loaded from the item catalog ({@code items/*.json}); the {@link ItemGenerator}
 * turns it into concrete {@link ItemInstance}s.
 *
 * @param id           permanent id ({@code weapon.talyn_ild}); items are identified by it, never by name
 * @param displayName  Mongolian display name
 * @param lore         flavour line (may be empty)
 * @param material     backing vanilla material ({@code minecraft:iron_sword})
 * @param model        resource-pack custom model data, 0 for none
 * @param type         item type (decides the equipment slot and the affix pool)
 * @param rarity       lowest rarity it can have (its only rarity when {@code maxRarity == rarity})
 * @param maxRarity    highest rarity it can roll
 * @param levelReq     character level needed to equip it (an instance needs at least its own item level as well)
 * @param classes      classes that may equip it; empty = every class
 * @param stats        base stat ranges at item level 1
 * @param statPerLevel added per item level above 1
 * @param effects      unique properties: passive spells ({@code proc}) and spell modifiers ({@code mod}) while equipped
 * @param durability   durability at the lowest rarity (×rarity multiplier); 0 = never wears
 * @param binding      binding rule (a rarity may force a stronger one, see {@link ItemRarity#forcedBinding()})
 * @param tradable     may change hands by trade at all
 * @param maxStack     1 for gear; materials stack
 * @param sellValue    base coin value at a merchant (×rarity, ×level)
 * @param setId        equipment set it belongs to, or null
 * @param lootable     may be picked by generic loot pools (class weapons and quest items are not)
 */
public record ItemDefinition(
        String id,
        String displayName,
        String lore,
        String material,
        int model,
        ItemType type,
        ItemRarity rarity,
        ItemRarity maxRarity,
        int levelReq,
        Set<PlayerClass> classes,
        Map<ItemStat, StatRange> stats,
        Map<ItemStat, Double> statPerLevel,
        List<Effect> effects,
        int durability,
        Binding binding,
        boolean tradable,
        int maxStack,
        long sellValue,
        String setId,
        boolean lootable) {

    public static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z0-9_]+){1,3}");
    public static final int MAX_LEVEL = 60;

    public ItemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rarity, "rarity");
        maxRarity = maxRarity == null ? rarity : maxRarity;
        if (maxRarity.ordinal() < rarity.ordinal()) throw new IllegalArgumentException(id + ": maxRarity below rarity");
        lore = lore == null ? "" : lore;
        classes = classes == null || classes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(classes));
        Map<ItemStat, StatRange> st = new EnumMap<>(ItemStat.class);
        if (stats != null) st.putAll(stats);
        stats = java.util.Collections.unmodifiableMap(st);
        Map<ItemStat, Double> pl = new EnumMap<>(ItemStat.class);
        if (statPerLevel != null) pl.putAll(statPerLevel);
        statPerLevel = java.util.Collections.unmodifiableMap(pl);
        effects = effects == null ? List.of() : List.copyOf(effects);
        binding = binding == null ? Binding.NONE : binding;
        maxStack = Math.max(1, Math.min(64, maxStack));
        if (levelReq < 1 || levelReq > MAX_LEVEL) throw new IllegalArgumentException(id + ": levelReq " + levelReq);
    }

    public boolean fixedRarity() {
        return rarity == maxRarity;
    }

    public boolean allows(PlayerClass c) {
        return classes.isEmpty() || (c != null && classes.contains(c));
    }

    public boolean stackable() {
        return maxStack > 1;
    }

    public boolean equippable() {
        return type.equippable();
    }

    public boolean canRoll(ItemRarity r) {
        return r.ordinal() >= rarity.ordinal() && r.ordinal() <= maxRarity.ordinal();
    }

    /** The binding an instance of this rarity follows. */
    public Binding bindingAt(ItemRarity r) {
        return binding.strongest(r.forcedBinding());
    }

    /** Maximum durability at a rarity; 0 = never wears. */
    public int maxDurability(ItemRarity r) {
        if (durability <= 0 || !type.wears() || r.durabilityMultiplier() <= 0) return 0;
        return (int) Math.round(durability * r.durabilityMultiplier());
    }

    /**
     * How much stronger base stats are at {@code r} than at this item's own (lowest) rarity: the stats in the data are
     * what the item has at its base rarity, so fixed-rarity items keep exactly their numbers.
     */
    public double rarityFactor(ItemRarity r) {
        return r.statMultiplier() / rarity.statMultiplier();
    }

    /** Highest base value a stat can have at this rarity and item level (for the impossible-stat check). */
    public double maxStat(ItemStat s, ItemRarity r, int itemLevel) {
        StatRange range = stats.get(s);
        if (range == null) return 0;
        return range.max() * rarityFactor(r) + statPerLevel.getOrDefault(s, 0.0) * Math.max(0, itemLevel - 1);
    }

    public double minStat(ItemStat s, ItemRarity r, int itemLevel) {
        StatRange range = stats.get(s);
        if (range == null) return 0;
        return range.min() * rarityFactor(r) + statPerLevel.getOrDefault(s, 0.0) * Math.max(0, itemLevel - 1);
    }
}
