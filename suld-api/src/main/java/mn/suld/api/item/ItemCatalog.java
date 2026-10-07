package mn.suld.api.item;

import mn.suld.api.loot.LootTable;
import mn.suld.api.loot.LootTier;
import mn.suld.api.loot.RarityBand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the item engine knows: definitions, affixes, sets, loot tables, rarity bands per loot tier, recipes and
 * salvage materials. Immutable; built by {@link ItemCatalogLoader} from validated data files (a reload swaps the
 * whole catalog).
 */
public final class ItemCatalog {

    /** Salvage material by rarity band: index 0 for common..uncommon, 1 rare..epic, 2 legendary and above. */
    private final List<String> salvageMaterials;
    private final Map<String, ItemDefinition> items;
    private final Map<String, Affix> affixes;
    private final Map<String, ItemSet> sets;
    private final Map<String, LootTable> loot;
    private final Map<LootTier, RarityBand> bands;
    private final Map<String, Recipe> recipes;

    public ItemCatalog(Collection<ItemDefinition> items, Collection<Affix> affixes, Collection<ItemSet> sets,
                       Collection<LootTable> loot, Collection<RarityBand> bands, Collection<Recipe> recipes,
                       List<String> salvageMaterials) {
        this.items = index(items, ItemDefinition::id);
        this.affixes = index(affixes, Affix::id);
        this.sets = index(sets, ItemSet::id);
        this.loot = index(loot, LootTable::id);
        Map<LootTier, RarityBand> b = new EnumMap<>(LootTier.class);
        for (RarityBand rb : bands) b.put(rb.tier(), rb);
        this.bands = java.util.Collections.unmodifiableMap(b);
        this.recipes = index(recipes, Recipe::id);
        this.salvageMaterials = List.copyOf(salvageMaterials);
    }

    public static final ItemCatalog EMPTY = new ItemCatalog(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    private static <T> Map<String, T> index(Collection<T> values, java.util.function.Function<T, String> key) {
        Map<String, T> m = new LinkedHashMap<>();
        for (T v : values) m.put(key.apply(v), v);
        return java.util.Collections.unmodifiableMap(m);
    }

    public Optional<ItemDefinition> item(String id) {
        return Optional.ofNullable(id == null ? null : items.get(id));
    }

    public ItemDefinition require(String id) {
        ItemDefinition d = items.get(id);
        if (d == null) throw new IllegalArgumentException("unknown item " + id);
        return d;
    }

    public Collection<ItemDefinition> items() {
        return items.values();
    }

    public Optional<Affix> affix(String id) {
        return Optional.ofNullable(affixes.get(id));
    }

    public Collection<Affix> affixes() {
        return affixes.values();
    }

    public Optional<ItemSet> set(String id) {
        return Optional.ofNullable(id == null ? null : sets.get(id));
    }

    public Collection<ItemSet> sets() {
        return sets.values();
    }

    public Optional<LootTable> lootTable(String id) {
        return Optional.ofNullable(id == null ? null : loot.get(id));
    }

    public Collection<LootTable> lootTables() {
        return loot.values();
    }

    public RarityBand band(LootTier tier) {
        RarityBand b = bands.get(tier);
        return b == null ? new RarityBand(tier, Map.of(ItemRarity.COMMON, 1)) : b;
    }

    public Collection<RarityBand> bands() {
        return bands.values();
    }

    public Optional<Recipe> recipe(String id) {
        return Optional.ofNullable(recipes.get(id));
    }

    public Collection<Recipe> recipes() {
        return recipes.values();
    }

    /** The salvage material for an item of this rarity (null when the catalog defines none). */
    public String salvageMaterial(ItemRarity r) {
        if (salvageMaterials.isEmpty()) return null;
        int i = r.ordinal() <= ItemRarity.UNCOMMON.ordinal() ? 0 : r.ordinal() <= ItemRarity.EPIC.ordinal() ? 1 : 2;
        return salvageMaterials.get(Math.min(i, salvageMaterials.size() - 1));
    }

    public List<String> salvageMaterials() {
        return salvageMaterials;
    }

    /** Lootable definitions a pool may choose from. */
    public List<ItemDefinition> lootable(java.util.Set<ItemType.Category> categories, int level) {
        List<ItemDefinition> out = new ArrayList<>();
        for (ItemDefinition d : items.values()) {
            if (!d.lootable() || d.rarity() == ItemRarity.UNIQUE || !d.equippable()) continue;
            if (!categories.isEmpty() && !categories.contains(d.type().category())) continue;
            if (d.levelReq() > level + 2) continue;
            out.add(d);
        }
        return out;
    }

    /** The set an item belongs to, if any. */
    public Optional<ItemSet> setOf(ItemDefinition d) {
        return set(d.setId());
    }
}
