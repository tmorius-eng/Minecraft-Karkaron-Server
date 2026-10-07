package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemType;
import mn.suld.api.loot.LootContext;
import mn.suld.api.loot.LootDrop;
import mn.suld.api.loot.LootEngine;
import mn.suld.api.loot.LootTable;
import mn.suld.api.loot.LootTier;
import mn.suld.api.loot.Rng;

import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loot through the real {@link LootEngine} and {@link mn.suld.api.item.ItemGenerator}: for every (table, level, tier,
 * class) a fixed set of real rolls is generated once (seeded by the key, so the result does not depend on thread
 * order) and the simulation then draws from it. Drawing from {@value #SAMPLES} real rolls instead of rolling each
 * kill keeps a 180-day hardcore run to milliseconds without changing any probability.
 *
 * <p>The proposed loot is the same engine over a catalog whose loot tables and rarity bands are replaced
 * ({@link ProposedRules#catalog}), i.e. exactly the data change that would be ported to {@code items/*.json}.
 */
public final class Loot {

    public static final int SAMPLES = 1500;

    /** One stack of a roll. Gear when the definition is equippable, otherwise a material/consumable. */
    public record Drop(ItemDefinition def, ItemInstance item, int qty) {
        public boolean gear() {
            return def.equippable();
        }
    }

    private static final Drop[] NONE = new Drop[0];

    private final ItemCatalog catalog;
    private final LootEngine engine;
    private final ConcurrentHashMap<String, Drop[][]> cache = new ConcurrentHashMap<>();

    public Loot(ItemCatalog catalog) {
        this.catalog = catalog;
        this.engine = new LootEngine(catalog);
    }

    public ItemCatalog catalog() {
        return catalog;
    }

    /** One roll of a table at a loot level and tier, for a class; empty when the table does not exist. */
    public Drop[] roll(String tableId, int level, LootTier tier, PlayerClass clazz, SplittableRandom rng) {
        if (tableId == null) return NONE;
        int lv = Math.max(1, Math.min(60, level));
        String key = tableId + '|' + lv + '|' + tier + '|' + clazz;
        Drop[][] samples = cache.computeIfAbsent(key, k -> sample(tableId, lv, tier, clazz, k.hashCode()));
        if (samples.length == 0) return NONE;
        return samples[rng.nextInt(samples.length)];
    }

    private Drop[][] sample(String tableId, int level, LootTier tier, PlayerClass clazz, long seed) {
        LootTable table = catalog.lootTable(tableId).orElse(null);
        if (table == null) return new Drop[0][];
        Rng rng = Rng.seeded(0x5EEDL ^ seed);
        LootContext ctx = new LootContext(level, tier, clazz, 0, null, "sim");
        Drop[][] out = new Drop[SAMPLES][];
        for (int i = 0; i < SAMPLES; i++) {
            List<LootDrop> drops = engine.roll(table, ctx, rng);
            Drop[] row = new Drop[drops.size()];
            for (int j = 0; j < row.length; j++) {
                LootDrop d = drops.get(j);
                row[j] = new Drop(catalog.require(d.item().definitionId()), d.item(), d.amount());
            }
            out[i] = row.length == 0 ? NONE : row;
        }
        return out;
    }

    /** Whether a definition is a material the economy counts (sellable / salvage / upgrade input). */
    public static boolean material(ItemDefinition d) {
        return d.type() == ItemType.MATERIAL;
    }
}
