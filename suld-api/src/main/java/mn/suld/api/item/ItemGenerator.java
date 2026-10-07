package mn.suld.api.item;

import mn.suld.api.loot.Rng;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Makes concrete items from definitions: base stats inside their ranges (scaled by rarity and item level), the
 * rarity's number of affixes chosen by weight among those that fit, binding from the definition and the rarity. The
 * output is always something {@link ItemValidator} accepts — that is tested for every definition and rarity.
 */
public final class ItemGenerator {

    private final ItemCatalog catalog;

    public ItemGenerator(ItemCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * A new item. Base stats roll in their range (higher rarities roll higher in it, {@link ItemRarity#rollFloor()}) and
     * are scaled by {@link ItemDefinition#rarityFactor}, plus the per-level growth.
     *
     * @param rarity    must be within the definition's range and not UNIQUE (relics are minted by the relic system)
     * @param itemLevel clamped to [definition requirement, max level]
     * @param owner     receiver; soulbound items are bound to them at once (may be null)
     */
    public ItemInstance generate(ItemDefinition def, ItemRarity rarity, int itemLevel, Rng rng, String provenance, UUID owner) {
        return generate(def, rarity, itemLevel, rng, provenance, owner, UUID.randomUUID());
    }

    /** Same, keeping an existing identity (an item that turns into its next form, like a class weapon tier). */
    public ItemInstance generate(ItemDefinition def, ItemRarity rarity, int itemLevel, Rng rng, String provenance, UUID owner, UUID uuid) {
        if (rarity == ItemRarity.UNIQUE) throw new IllegalArgumentException("unique items are never generated: " + def.id());
        if (!def.canRoll(rarity)) throw new IllegalArgumentException(def.id() + " cannot be " + rarity);
        int lvl = Math.max(def.levelReq(), Math.min(ItemDefinition.MAX_LEVEL, itemLevel));
        double floor = rarity.rollFloor();
        Map<ItemStat, Double> stats = new EnumMap<>(ItemStat.class);
        for (Map.Entry<ItemStat, StatRange> e : def.stats().entrySet()) {
            double q = floor + rng.nextDouble() * (1 - floor);
            double v = e.getValue().at(q) * def.rarityFactor(rarity) + def.statPerLevel().getOrDefault(e.getKey(), 0.0) * (lvl - 1);
            stats.put(e.getKey(), round(e.getKey(), v));
        }
        List<RolledAffix> affixes = def.stackable() || !def.equippable() ? List.of() : rollAffixes(def, rarity, lvl, rng);
        Binding binding = def.bindingAt(rarity);
        boolean soulbound = binding == Binding.SOULBOUND;
        UUID bound = soulbound ? owner : null;
        return new ItemInstance(def.id(), uuid, rarity, lvl, stats, affixes, soulbound, bound, 0,
                provenance, ItemInstance.SCHEMA_VERSION);
    }

    private List<RolledAffix> rollAffixes(ItemDefinition def, ItemRarity rarity, int lvl, Rng rng) {
        int count = rng.between(rarity.minAffixes(), rarity.maxAffixes());
        List<Affix> pool = new ArrayList<>();
        for (Affix a : catalog.affixes()) if (a.fits(def, rarity)) pool.add(a);
        List<RolledAffix> out = new ArrayList<>();
        Set<String> takenKinds = new HashSet<>();
        while (out.size() < count && !pool.isEmpty()) {
            int total = 0;
            for (Affix a : pool) total += a.weight();
            int x = rng.nextInt(total);
            Affix pick = pool.get(pool.size() - 1);
            for (Affix a : pool) {
                x -= a.weight();
                if (x < 0) {
                    pick = a;
                    break;
                }
            }
            pool.remove(pick);
            // one affix per stat (or per spell modifier): "+5% crit" twice would just be one bigger affix
            String kind = pick.stat() != null ? pick.stat().name() : pick.spell() + "." + pick.modKey();
            if (!takenKinds.add(kind)) continue;
            double q = rarity.rollFloor() + rng.nextDouble() * (1 - rarity.rollFloor());
            double v = pick.low(lvl) + q * (pick.high(lvl) - pick.low(lvl));
            out.add(new RolledAffix(pick.id(), pick.stat() == null ? round(v) : round(pick.stat(), v)));
        }
        return out;
    }

    /** One level up at the smith: same item and rolls, the per-level growth added. */
    public ItemInstance upgrade(ItemInstance i, ItemDefinition def) {
        int lvl = Math.min(ItemDefinition.MAX_LEVEL, i.itemLevel() + 1);
        Map<ItemStat, Double> stats = new EnumMap<>(ItemStat.class);
        stats.putAll(i.stats());
        for (Map.Entry<ItemStat, Double> e : def.statPerLevel().entrySet()) {
            ItemStat st = e.getKey();
            stats.merge(st, e.getValue(), (a, b) -> round(st, a + b));
        }
        return i.reforged(lvl, stats, i.upgradeLevel() + 1);
    }

    /** A stat value as stored: one decimal, or a whole number (rounded down) for {@link ItemStat#integral()} stats. */
    public static double round(ItemStat stat, double v) {
        return stat.integral() ? Math.floor(v + 1e-9) : round(v);
    }

    /** One decimal: what the tooltip shows is what the item has. */
    public static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
