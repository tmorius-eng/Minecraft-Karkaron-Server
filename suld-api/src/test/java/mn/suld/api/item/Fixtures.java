package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.loot.LootTable;
import mn.suld.api.loot.LootTier;
import mn.suld.api.loot.RarityBand;
import mn.suld.api.skill.tree.Effect;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small hand-made catalogs for tests (the bundled catalog is tested separately). */
final class Fixtures {

    private Fixtures() {
    }

    static final class Def {
        String id;
        ItemType type = ItemType.SWORD;
        String material = "minecraft:iron_sword";
        ItemRarity rarity = ItemRarity.COMMON, max = ItemRarity.MYTHIC;
        int level = 1;
        Set<PlayerClass> classes = Set.of();
        Map<ItemStat, StatRange> stats = new EnumMap<>(ItemStat.class);
        Map<ItemStat, Double> per = new EnumMap<>(ItemStat.class);
        List<Effect> effects = new ArrayList<>();
        int durability = 100;
        Binding binding = Binding.NONE;
        int stack = 1;
        long sell = 10;
        String set;
        boolean lootable = true;

        Def(String id) {
            this.id = id;
        }

        Def type(ItemType t, String mat) { type = t; material = mat; return this; }
        Def rarity(ItemRarity lo, ItemRarity hi) { rarity = lo; max = hi; return this; }
        Def level(int l) { level = l; return this; }
        Def classes(PlayerClass... c) { classes = Set.of(c); return this; }
        Def stat(ItemStat s, double lo, double hi) { stats.put(s, new StatRange(lo, hi)); return this; }
        Def per(ItemStat s, double v) { per.put(s, v); return this; }
        Def effect(Effect e) { effects.add(e); return this; }
        Def binding(Binding b) { binding = b; return this; }
        Def stack(int s) { stack = s; return this; }
        Def set(String s) { set = s; return this; }
        Def lootable(boolean b) { lootable = b; return this; }
        Def durability(int d) { durability = d; return this; }

        ItemDefinition build() {
            return new ItemDefinition(id, id, "", material, 0, type, rarity, max, level, classes, stats, per, effects,
                    type.wears() ? durability : 0, binding, true, stack, sell, set, lootable);
        }
    }

    static Def def(String id) {
        return new Def(id);
    }

    static Affix statAffix(String id, ItemStat s, double lo, double hi, ItemRarity minR, ItemType.Category... cats) {
        return new Affix(id, id, true, s, null, null, lo, hi, 0, minR, Set.of(cats), 1);
    }

    /** Six weapon stat affixes, enough for a mythic item. */
    static List<Affix> weaponAffixes() {
        return List.of(
                statAffix("a_dmg", ItemStat.DAMAGE, 2, 4, ItemRarity.UNCOMMON, ItemType.Category.WEAPON),
                statAffix("a_crit", ItemStat.CRIT_CHANCE, 2, 4, ItemRarity.UNCOMMON, ItemType.Category.WEAPON),
                statAffix("a_cdmg", ItemStat.CRIT_DAMAGE, 8, 14, ItemRarity.RARE, ItemType.Category.WEAPON),
                statAffix("a_as", ItemStat.ATTACK_SPEED, 4, 8, ItemRarity.UNCOMMON, ItemType.Category.WEAPON),
                statAffix("a_ls", ItemStat.LIFESTEAL, 1, 3, ItemRarity.RARE, ItemType.Category.WEAPON),
                statAffix("a_sp", ItemStat.SPELL_DAMAGE, 4, 8, ItemRarity.UNCOMMON, ItemType.Category.WEAPON),
                statAffix("a_hp", ItemStat.MAX_HEALTH, 4, 8, ItemRarity.UNCOMMON, ItemType.Category.ARMOR, ItemType.Category.JEWELRY));
    }

    static List<RarityBand> bands() {
        List<RarityBand> out = new ArrayList<>();
        out.add(new RarityBand(LootTier.NORMAL, Map.of(ItemRarity.COMMON, 75, ItemRarity.UNCOMMON, 25)));
        out.add(new RarityBand(LootTier.ELITE, Map.of(ItemRarity.UNCOMMON, 65, ItemRarity.RARE, 35)));
        out.add(new RarityBand(LootTier.BOSS, Map.of(ItemRarity.LEGENDARY, 70, ItemRarity.ANCIENT, 25, ItemRarity.MYTHIC, 5)));
        return out;
    }

    static ItemCatalog catalog(List<ItemDefinition> items, List<Affix> affixes, List<ItemSet> sets, List<LootTable> tables) {
        return new ItemCatalog(items, affixes, sets, tables, bands(), List.of(), List.of("mat.a", "mat.b", "mat.c"));
    }
}
