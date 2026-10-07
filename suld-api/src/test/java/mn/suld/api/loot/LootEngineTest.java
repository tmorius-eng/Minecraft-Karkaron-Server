package mn.suld.api.loot;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemType;
import mn.suld.api.item.ItemValidator;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LootEngineTest {

    static final ItemCatalog CAT = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();
    static final LootEngine ENGINE = new LootEngine(CAT);

    private static LootTable table(int nothing, List<LootTable.Entry> entries, List<LootTable.Entry> guaranteed, List<LootTable.Rare> rare) {
        return new LootTable("loot.test", LootTier.NORMAL, 1, 1, nothing, guaranteed, entries, rare);
    }

    @Test
    void sameSeedSameLoot() {
        LootTable t = CAT.lootTable("loot.dungeon.khasar_den").orElseThrow();
        LootContext ctx = new LootContext(7, LootTier.DUNGEON, PlayerClass.BAATAR, 0, null, "t");
        List<LootDrop> a = ENGINE.roll(t, ctx, Rng.seeded(42));
        List<LootDrop> b = ENGINE.roll(t, ctx, Rng.seeded(42));
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).item().definitionId(), b.get(i).item().definitionId());
            assertEquals(a.get(i).item().stats(), b.get(i).item().stats());
            assertEquals(a.get(i).amount(), b.get(i).amount());
        }
    }

    @Test
    void weightsAreHonoured() {
        LootTable t = table(50, List.of(LootTable.Entry.item("item.chonon_arisan", 30, 1, 1), LootTable.Entry.item("item.khilentsiin_khor", 20, 1, 1)), List.of(), List.of());
        Map<String, Integer> n = new HashMap<>();
        int rolls = 20_000;
        Rng rng = Rng.seeded(7);
        for (int i = 0; i < rolls; i++) for (LootDrop d : ENGINE.roll(t, LootContext.of(5, LootTier.NORMAL), rng)) n.merge(d.item().definitionId(), 1, Integer::sum);
        assertEquals(0.30, n.get("item.chonon_arisan") / (double) rolls, 4 * Math.sqrt(0.3 * 0.7 / rolls));
        assertEquals(0.20, n.get("item.khilentsiin_khor") / (double) rolls, 4 * Math.sqrt(0.2 * 0.8 / rolls));
    }

    @Test
    void guaranteedAlwaysAndQuantityInRange() {
        LootTable t = table(1, List.of(), List.of(LootTable.Entry.item("item.chonon_arisan", 1, 2, 5)), List.of());
        Rng rng = Rng.seeded(3);
        for (int i = 0; i < 500; i++) {
            List<LootDrop> d = ENGINE.roll(t, LootContext.of(5, LootTier.NORMAL), rng);
            assertEquals(1, d.size());
            assertTrue(d.get(0).amount() >= 2 && d.get(0).amount() <= 5);
        }
    }

    @Test
    void rareDropsUseTheirChanceRaisedByTheLootBonus() {
        LootTable t = table(1, List.of(), List.of(), List.of(new LootTable.Rare(LootTable.Entry.item("weapon.talyn_ild", 1, 1, 1), 0.05)));
        int n = 40_000;
        int plain = 0, lucky = 0;
        Rng rng = Rng.seeded(11);
        for (int i = 0; i < n; i++) {
            plain += ENGINE.roll(t, new LootContext(5, LootTier.NORMAL, null, 0, null, "t"), rng).size();
            lucky += ENGINE.roll(t, new LootContext(5, LootTier.NORMAL, null, 100, null, "t"), rng).size();
        }
        assertEquals(0.05, plain / (double) n, 4 * Math.sqrt(0.05 * 0.95 / n));
        assertEquals(0.10, lucky / (double) n, 4 * Math.sqrt(0.1 * 0.9 / n), "+100% loot chance doubles a rare drop's chance");
    }

    @Test
    void tierBandsDecideRarityWithinTheItemsRange() {
        LootTable t = table(0, List.of(new LootTable.Entry(null, new LootTable.Pool(Set.of(ItemType.Category.ARMOR)), 1, 1, 1, null, null, 0, null)), List.of(), List.of());
        Map<ItemRarity, Integer> normal = new EnumMap<>(ItemRarity.class), elite = new EnumMap<>(ItemRarity.class), boss = new EnumMap<>(ItemRarity.class);
        Rng rng = Rng.seeded(5);
        for (int i = 0; i < 3000; i++) {
            ENGINE.roll(t, LootContext.of(40, LootTier.NORMAL), rng).forEach(d -> normal.merge(d.item().rarity(), 1, Integer::sum));
            ENGINE.roll(t, LootContext.of(40, LootTier.ELITE), rng).forEach(d -> elite.merge(d.item().rarity(), 1, Integer::sum));
            ENGINE.roll(t, LootContext.of(40, LootTier.BOSS), rng).forEach(d -> boss.merge(d.item().rarity(), 1, Integer::sum));
        }
        // the band can be lifted by an item's own minimum rarity (Тэнгэрийн armour starts at rare), never lowered below it
        assertFalse(elite.containsKey(ItemRarity.COMMON));
        assertFalse(elite.containsKey(ItemRarity.EPIC), "elite band is uncommon..rare; " + elite);
        assertTrue(boss.keySet().stream().allMatch(r -> r.atLeast(ItemRarity.LEGENDARY)), "a boss drops gear that can be legendary, at legendary or above: " + boss);
        assertTrue(normal.getOrDefault(ItemRarity.COMMON, 0) > normal.getOrDefault(ItemRarity.UNCOMMON, 0), "normal " + normal);
        for (Map<ItemRarity, Integer> m : List.of(normal, elite, boss)) assertFalse(m.containsKey(ItemRarity.UNIQUE));
    }

    @Test
    void entryRarityRestrictionsHold() {
        LootTable t = table(0, List.of(new LootTable.Entry(null, new LootTable.Pool(Set.of(ItemType.Category.WEAPON)), 1, 1, 1, ItemRarity.EPIC, ItemRarity.EPIC, 0, null)), List.of(), List.of());
        Rng rng = Rng.seeded(9);
        for (int i = 0; i < 500; i++) for (LootDrop d : ENGINE.roll(t, LootContext.of(35, LootTier.NORMAL), rng)) assertEquals(ItemRarity.EPIC, d.item().rarity());
    }

    @Test
    void poolsPreferTheReceiversClassAndRespectLevel() {
        LootTable.Entry pool = new LootTable.Entry(null, new LootTable.Pool(Set.of(ItemType.Category.WEAPON)), 1, 1, 1, null, null, 2, null);
        int own = 0, other = 0, total = 0;
        Rng rng = Rng.seeded(13);
        for (int i = 0; i < 6000; i++) {
            ItemDefinition d = ENGINE.fromPool(pool, new LootContext(10, LootTier.NORMAL, PlayerClass.MERGEN, 0, null, "t"), LootTier.NORMAL, rng);
            assertNotNull(d);
            assertTrue(d.levelReq() <= 12, d.id() + " needs " + d.levelReq());
            assertTrue(d.lootable());
            total++;
            if (!d.classes().isEmpty()) {
                if (d.classes().contains(PlayerClass.MERGEN)) own++;
                else other++;
            }
        }
        assertTrue(total > 0);
        assertEquals(0, own + other, "the bundled lootable weapons are for every class; class weighting is tested with fixtures below");
    }

    @Test
    void classlessWeaponsFollowTheClassWeaponType() {
        LootTable.Entry pool = new LootTable.Entry(null, new LootTable.Pool(Set.of(ItemType.Category.WEAPON)), 1, 1, 1, null, null, 2, null);
        Rng rng = Rng.seeded(21);
        for (PlayerClass c : PlayerClass.values()) {
            int own = 0, n = 4000;
            for (int i = 0; i < n; i++) {
                ItemDefinition d = ENGINE.fromPool(pool, new LootContext(30, LootTier.NORMAL, c, 0, null, "t"), LootTier.NORMAL, rng);
                if (LootEngine.affinity(c).contains(d.type())) own++;
            }
            // a Баатар is not flooded with bows: at least 3 in 4 weapons are his own weapon type
            assertTrue(own / (double) n > 0.75, c + " got its own weapon type " + own + "/" + n);
        }
        assertTrue(LootEngine.affinity(null).isEmpty());
    }

    @Test
    void namedOffTypeWeaponsAreRarerForOtherClasses() {
        LootTable t = table(1000, List.of(), List.of(), List.of(new LootTable.Rare(LootTable.Entry.item("weapon.talyn_ild", 1, 1, 1), 0.4)));
        int baatar = 0, mergen = 0, n = 8000;
        Rng rng = Rng.seeded(23);
        for (int i = 0; i < n; i++) {
            baatar += ENGINE.roll(t, new LootContext(10, LootTier.NORMAL, PlayerClass.BAATAR, 0, null, "t"), rng).size();
            mergen += ENGINE.roll(t, new LootContext(10, LootTier.NORMAL, PlayerClass.MERGEN, 0, null, "t"), rng).size();
        }
        assertEquals(0.4, baatar / (double) n, 0.03);
        assertEquals(0.1, mergen / (double) n, 0.02, "a sword is a quarter as likely for an archer");
    }

    @Test
    void bundledTablesDoNotFloodTheBag() {
        // per kill, a normal open-world mob yields about one piece of non-stacking gear in 15-30 kills
        Rng rng = Rng.seeded(29);
        for (LootTable t : CAT.lootTables()) {
            if (t.tier() != LootTier.NORMAL || t.id().equals("loot.world.rare")) continue;
            int gear = 0, n = 4000;
            for (int i = 0; i < n; i++) {
                for (LootDrop d : ENGINE.roll(t, new LootContext(10, LootTier.NORMAL, PlayerClass.BAATAR, 0, null, "t"), rng)) {
                    if (!CAT.require(d.item().definitionId()).stackable()) gear++;
                }
            }
            assertTrue(gear / (double) n < 0.07, t.id() + " gear per kill " + gear / (double) n);
        }
        // a dungeon clear gives exactly one gear piece plus rare extras, not three
        LootTable den = CAT.lootTable("loot.dungeon.khasar_den").orElseThrow();
        double gear = 0;
        int n = 4000;
        for (int i = 0; i < n; i++) {
            for (LootDrop d : ENGINE.roll(den, new LootContext(6, LootTier.DUNGEON, PlayerClass.BAATAR, 0, null, "t"), rng)) {
                if (!CAT.require(d.item().definitionId()).stackable()) gear++;
            }
        }
        assertTrue(gear / n >= 1.0 && gear / n < 1.4, "gear per clear " + gear / n);
    }

    @Test
    void classWeightingOnEntries() {
        LootTable.Entry bow = new LootTable.Entry("weapon.evertei_num", null, 10, 1, 1, null, null, 0, Map.of(PlayerClass.MERGEN, 3.0));
        LootTable.Entry sword = LootTable.Entry.item("weapon.tumur_ild", 10, 1, 1);
        LootTable t = table(0, List.of(bow, sword), List.of(), List.of());
        int bows = 0, n = 8000;
        Rng rng = Rng.seeded(17);
        for (int i = 0; i < n; i++) {
            for (LootDrop d : ENGINE.roll(t, new LootContext(15, LootTier.NORMAL, PlayerClass.MERGEN, 0, null, "t"), rng)) {
                if (d.item().definitionId().equals("weapon.evertei_num")) bows++;
            }
        }
        assertEquals(0.75, bows / (double) n, 4 * Math.sqrt(0.75 * 0.25 / n), "weight 30 vs 10 for a mergen");
    }

    @Test
    void levelScalingStaysInTheSpreadAndAboveTheRequirement() {
        LootTable.Entry e = new LootTable.Entry("weapon.tumur_ild", null, 1, 1, 1, null, null, 3, null);
        LootTable t = table(0, List.of(e), List.of(), List.of());
        Rng rng = Rng.seeded(19);
        for (int i = 0; i < 1000; i++) {
            int lvl = ENGINE.roll(t, LootContext.of(20, LootTier.NORMAL), rng).get(0).item().itemLevel();
            assertTrue(lvl >= 17 && lvl <= 23, "level " + lvl);
            int low = ENGINE.roll(t, LootContext.of(5, LootTier.NORMAL), rng).get(0).item().itemLevel();
            assertEquals(12, Math.max(12, low), "never below the definition's level 12");
            assertTrue(low >= 12);
        }
    }

    @Test
    void everyBundledTableProducesOnlyGenuineNonUniqueItems() {
        ItemValidator val = new ItemValidator(CAT);
        Rng rng = Rng.seeded(23);
        int drops = 0;
        for (LootTable t : CAT.lootTables()) {
            for (LootTier tier : LootTier.values()) {
                for (int i = 0; i < 40; i++) {
                    for (LootDrop d : ENGINE.roll(t, new LootContext(1 + (i * 3) % 60, tier, PlayerClass.values()[i % 5], i, null, "t"), rng)) {
                        assertNotEquals(ItemRarity.UNIQUE, d.item().rarity());
                        assertEquals(List.of(), val.problems(d.item()), t.id() + " " + d.item());
                        drops++;
                    }
                }
            }
        }
        assertTrue(drops > 1000, "drops " + drops);
    }

    @Test
    void nothingWeightMeansSometimesNothing() {
        LootTable t = table(9, List.of(LootTable.Entry.item("item.chonon_arisan", 1, 1, 1)), List.of(), List.of());
        int got = 0, n = 10_000;
        Rng rng = Rng.seeded(29);
        for (int i = 0; i < n; i++) got += ENGINE.roll(t, LootContext.of(1, LootTier.NORMAL), rng).size();
        assertEquals(0.1, got / (double) n, 4 * Math.sqrt(0.09 / n));
    }
}
