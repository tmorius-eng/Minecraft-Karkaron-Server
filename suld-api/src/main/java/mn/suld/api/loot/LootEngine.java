package mn.suld.api.loot;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemGenerator;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls {@link LootTable}s into items. Deterministic for a given {@link Rng}; never produces a unique item (they are
 * minted only by the relic system) and never an item the {@link mn.suld.api.item.ItemValidator} would refuse.
 */
public final class LootEngine {

    /** Pool weighting: gear made for the receiver's class is this much more likely; other classes' gear much less. */
    public static final double OWN_CLASS_WEIGHT = 3.0;
    public static final double OTHER_CLASS_WEIGHT = 0.25;
    /** Classless weapons and off-hands of a type the receiver's class does not fight with (a Баатар's bow). */
    public static final double OFF_TYPE_WEIGHT = 0.08;

    /**
     * The weapon / off-hand types each class fights with. Classless gear of these types is weighted like class gear;
     * other weapon and off-hand types become rare, so a Баатар's inventory is not flooded with bows and staves.
     */
    public static java.util.Set<mn.suld.api.item.ItemType> affinity(PlayerClass c) {
        if (c == null) return java.util.Set.of();
        return switch (c) {
            case BAATAR -> java.util.EnumSet.of(mn.suld.api.item.ItemType.SWORD, mn.suld.api.item.ItemType.SHIELD);
            case MERGEN -> java.util.EnumSet.of(mn.suld.api.item.ItemType.BOW);
            case BOO -> java.util.EnumSet.of(mn.suld.api.item.ItemType.STAFF, mn.suld.api.item.ItemType.TOME, mn.suld.api.item.ItemType.TOTEM);
            case DARKHAN -> java.util.EnumSet.of(mn.suld.api.item.ItemType.AXE, mn.suld.api.item.ItemType.SHIELD);
            case KHULEGCHIN -> java.util.EnumSet.of(mn.suld.api.item.ItemType.SPEAR, mn.suld.api.item.ItemType.SHIELD);
        };
    }

    /** Pool weight of a definition for a receiver. */
    static double weightFor(ItemDefinition d, PlayerClass c) {
        if (!d.classes().isEmpty()) return c != null && d.classes().contains(c) ? OWN_CLASS_WEIGHT : OTHER_CLASS_WEIGHT;
        ItemType.Category cat = d.type().category();
        if (c == null || (cat != ItemType.Category.WEAPON && cat != ItemType.Category.OFFHAND)) return 1.0;
        return affinity(c).contains(d.type()) ? OWN_CLASS_WEIGHT : OFF_TYPE_WEIGHT;
    }

    private final ItemCatalog catalog;
    private final ItemGenerator generator;

    public LootEngine(ItemCatalog catalog) {
        this.catalog = catalog;
        this.generator = new ItemGenerator(catalog);
    }

    public ItemGenerator generator() {
        return generator;
    }

    public List<LootDrop> roll(LootTable table, LootContext ctx, Rng rng) {
        List<LootDrop> out = new ArrayList<>();
        if (table == null) return out;
        LootTier tier = ctx.tier() == LootTier.NORMAL ? table.tier() : ctx.tier();
        for (LootTable.Entry e : table.guaranteed()) make(e, ctx, tier, rng, out);
        int rolls = rng.between(table.minRolls(), table.maxRolls());
        for (int r = 0; r < rolls; r++) {
            LootTable.Entry pick = pick(table, ctx.playerClass(), rng);
            if (pick != null) make(pick, ctx, tier, rng, out);
        }
        for (LootTable.Rare rare : table.rare()) {
            if (rng.chance(Math.min(1.0, rare.chance() * (1 + ctx.lootBonus() / 100.0) * rareFactor(rare.entry(), ctx.playerClass())))) make(rare.entry(), ctx, tier, rng, out);
        }
        return out;
    }

    /** A named weapon / off-hand of a type the receiver does not use is a quarter as likely (materials are unchanged). */
    double rareFactor(LootTable.Entry e, PlayerClass c) {
        if (e.itemId() == null || c == null) return 1.0;
        ItemDefinition d = catalog.item(e.itemId()).orElse(null);
        if (d == null || !d.classes().isEmpty()) return 1.0;
        ItemType.Category cat = d.type().category();
        if (cat != ItemType.Category.WEAPON && cat != ItemType.Category.OFFHAND) return 1.0;
        return affinity(c).contains(d.type()) ? 1.0 : 0.25;
    }

    /** A weighted pick among the entries, or null for "nothing". */
    LootTable.Entry pick(LootTable table, PlayerClass c, Rng rng) {
        double total = table.nothingWeight();
        for (LootTable.Entry e : table.entries()) total += e.weightFor(c);
        if (total <= 0) return null;
        double x = rng.nextDouble() * total;
        for (LootTable.Entry e : table.entries()) {
            x -= e.weightFor(c);
            if (x < 0) return e;
        }
        return null;
    }

    private void make(LootTable.Entry e, LootContext ctx, LootTier tier, Rng rng, List<LootDrop> out) {
        int qty = rng.between(e.minQty(), e.maxQty());
        if (e.itemId() != null) {
            ItemDefinition def = catalog.item(e.itemId()).orElse(null);
            if (def == null || def.rarity() == ItemRarity.UNIQUE) return;
            if (def.stackable()) {
                out.add(new LootDrop(one(def, e, ctx, tier, rng), Math.min(qty, def.maxStack())));
            } else {
                for (int k = 0; k < qty; k++) out.add(new LootDrop(one(def, e, ctx, tier, rng), 1));
            }
            return;
        }
        for (int k = 0; k < qty; k++) {
            ItemDefinition def = fromPool(e, ctx, tier, rng);
            if (def != null) out.add(new LootDrop(one(def, e, ctx, tier, rng), 1));
        }
    }

    private ItemInstance one(ItemDefinition def, LootTable.Entry e, LootContext ctx, LootTier tier, Rng rng) {
        ItemRarity rarity;
        if (def.fixedRarity()) {
            rarity = def.rarity();
        } else {
            ItemRarity lo = max(def.rarity(), e.minRarity());
            ItemRarity hi = min(def.maxRarity(), e.maxRarity());
            if (hi.ordinal() < lo.ordinal()) hi = lo;
            rarity = catalog.band(tier).pick(rng, lo, hi);
        }
        // the spread only goes down: loot is never above its level, so a reward rolled at the player's level is wearable
        int level = ctx.level() - (e.levelSpread() == 0 ? 0 : rng.between(0, e.levelSpread()));
        return generator.generate(def, rarity, level, rng, ctx.source(), ctx.owner());
    }

    /**
     * A lootable definition near the loot level, weighted toward the receiver's class. Items that can reach the tier's
     * rarity band are preferred (a boss drops gear that can be legendary), falling back to everything that fits.
     */
    ItemDefinition fromPool(LootTable.Entry e, LootContext ctx, LootTier tier, Rng rng) {
        List<ItemDefinition> all = catalog.lootable(e.pool().categories(), ctx.level());
        java.util.Set<ItemRarity> band = catalog.band(tier).weights().keySet();
        List<ItemDefinition> reaching = new ArrayList<>();
        for (ItemDefinition d : all) {
            for (ItemRarity r : band) {
                if (d.canRoll(r) && (e.minRarity() == null || r.atLeast(e.minRarity())) && (e.maxRarity() == null || e.maxRarity().atLeast(r))) {
                    reaching.add(d);
                    break;
                }
            }
        }
        List<ItemDefinition> candidates = reaching.isEmpty() ? all : reaching;
        List<ItemDefinition> fit = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (ItemDefinition d : candidates) {
            ItemRarity lo = max(d.rarity(), e.minRarity());
            ItemRarity hi = min(d.maxRarity(), e.maxRarity());
            if (hi.ordinal() < lo.ordinal()) continue; // the entry's rarity restriction excludes this item
            double w = weightFor(d, ctx.playerClass());
            fit.add(d);
            weights.add(w);
            total += w;
        }
        if (fit.isEmpty()) return null;
        double x = rng.nextDouble() * total;
        for (int i = 0; i < fit.size(); i++) {
            x -= weights.get(i);
            if (x < 0) return fit.get(i);
        }
        return fit.get(fit.size() - 1);
    }

    private static ItemRarity max(ItemRarity a, ItemRarity b) {
        return b == null || a.ordinal() >= b.ordinal() ? a : b;
    }

    private static ItemRarity min(ItemRarity a, ItemRarity b) {
        return b == null || a.ordinal() <= b.ordinal() ? a : b;
    }
}
