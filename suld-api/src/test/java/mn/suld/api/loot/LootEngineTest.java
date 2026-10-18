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
        Map<ItemRarity, Integer> normalCommonCapable = new EnumMap<>(ItemRarity.class);
        Rng rng = Rng.seeded(5);
        for (int i = 0; i < 3000; i++) {
            ENGINE.roll(t, LootContext.of(40, LootTier.NORMAL), rng).forEach(d -> {
                normal.merge(d.item().rarity(), 1, Integer::sum);
                if (CAT.require(d.item().definitionId()).rarity() == ItemRarity.COMMON) normalCommonCapable.merge(d.item().rarity(), 1, Integer::sum);
            });
            ENGINE.roll(t, LootContext.of(40, LootTier.ELITE), rng).forEach(d -> elite.merge(d.item().rarity(), 1, Integer::sum));
            ENGINE.roll(t, LootContext.of(40, LootTier.BOSS), rng).forEach(d -> boss.merge(d.item().rarity(), 1, Integer::sum));
        }
        // the v2 bands (IT-02, tiers.json = ProposedRules = GEAR_PROGRESSION_SPEC): ELITE uncommon 55 / rare 35 / epic 10,
        // BOSS rare 50 / epic 38 / legendary 10 / ancient 2. An item's own range (Тэнгэрийн armour starts at rare)
        // narrows the band, never takes a drop outside it.
        int eliteAll = elite.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(elite.keySet().stream().allMatch(r -> r.atLeast(ItemRarity.UNCOMMON) && ItemRarity.EPIC.atLeast(r)), "elite band is uncommon..epic; " + elite);
        assertTrue(elite.containsKey(ItemRarity.UNCOMMON) && elite.containsKey(ItemRarity.RARE), "elite " + elite);
        double eliteEpic = elite.getOrDefault(ItemRarity.EPIC, 0) / (double) eliteAll;
        assertTrue(eliteEpic > 0.05 && eliteEpic < 0.20, "epic is the elite's 10% step, not its norm: " + elite);
        int bossAll = boss.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(boss.keySet().stream().allMatch(r -> r.atLeast(ItemRarity.RARE) && ItemRarity.ANCIENT.atLeast(r)), "boss band is rare..ancient, never mythic: " + boss);
        int bossLegendaryPlus = boss.getOrDefault(ItemRarity.LEGENDARY, 0) + boss.getOrDefault(ItemRarity.ANCIENT, 0);
        assertTrue(bossLegendaryPlus > 0, "a boss can still drop legendary gear: " + boss);
        assertTrue(bossLegendaryPlus / (double) bossAll < 0.20, "legendary+ is ~12% of boss gear, not 100% (the pre-v2 band): " + boss);
        // NORMAL common 75 / uncommon 25, measured on the pieces that can be common: at level 40 the pool also holds the
        // uncommon-minimum Ган armour, which makes all-drops common vs uncommon an exact 50/50 tie (seed luck)
        assertTrue(normal.keySet().stream().allMatch(r -> ItemRarity.UNCOMMON.atLeast(r)), "normal band is common..uncommon: " + normal);
        int cc = normalCommonCapable.getOrDefault(ItemRarity.COMMON, 0), cu = normalCommonCapable.getOrDefault(ItemRarity.UNCOMMON, 0);
        double commonShare = cc / (double) (cc + cu);
        assertTrue(commonShare > 0.65 && commonShare < 0.85, "normal band ~75% common where common is possible: " + normalCommonCapable);
        for (Map<ItemRarity, Integer> m : List.of(normal, elite, boss)) assertFalse(m.containsKey(ItemRarity.UNIQUE));
    }

    @Test
    void anOpenWorldEventNeverPaysMoreThanADungeonBoss() {
        // owner, 2026-10-18: WORLD_EVENT (the wolf raid every 45 minutes, the world bosses) used to be ancient 75 /
        // mythic 25 while a dungeon boss paid rare..ancient; it is now rare 52 / epic 38 / legendary 10 — the BOSS
        // band's steps without its ancient share, so an open event is never the better gear source
        LootTable t = table(0, List.of(new LootTable.Entry(null, new LootTable.Pool(Set.of(ItemType.Category.ARMOR)), 1, 1, 1, null, null, 0, null)), List.of(), List.of());
        Rng rng = Rng.seeded(17);
        // at 60 the pool also holds pieces whose own range starts at legendary (they never roll lower), which lifts both
        // bands alike: the event is compared with a boss at the same level, the band's own steps are read at 40
        for (int level : new int[]{40, 60}) {
            Map<ItemRarity, Integer> event = new EnumMap<>(ItemRarity.class), boss = new EnumMap<>(ItemRarity.class);
            for (int i = 0; i < 3000; i++) {
                ENGINE.roll(t, LootContext.of(level, LootTier.WORLD_EVENT), rng).forEach(d -> event.merge(d.item().rarity(), 1, Integer::sum));
                ENGINE.roll(t, LootContext.of(level, LootTier.BOSS), rng).forEach(d -> boss.merge(d.item().rarity(), 1, Integer::sum));
            }
            assertTrue(event.keySet().stream().allMatch(r -> r.atLeast(ItemRarity.RARE) && ItemRarity.LEGENDARY.atLeast(r)),
                    "level " + level + ": the world event band is rare..legendary, never ancient or mythic: " + event);
            assertTrue(event.containsKey(ItemRarity.RARE) && event.containsKey(ItemRarity.EPIC), "rare and epic carry the band: " + event);
            int eventAll = event.values().stream().mapToInt(Integer::intValue).sum(), bossAll = boss.values().stream().mapToInt(Integer::intValue).sum();
            double eventTop = event.getOrDefault(ItemRarity.LEGENDARY, 0) / (double) eventAll;
            double bossTop = (boss.getOrDefault(ItemRarity.LEGENDARY, 0) + boss.getOrDefault(ItemRarity.ANCIENT, 0)) / (double) bossAll;
            assertTrue(eventTop <= bossTop + 0.03, "level " + level + ": legendary+ from an event (" + eventTop + ") never beats a dungeon boss ("
                    + bossTop + ")");
            if (level == 40) assertTrue(eventTop > 0.05 && eventTop < 0.15, "legendary is the event's 10% step: " + event);
        }
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
            assertTrue(d.levelReq() <= 10, d.id() + " needs " + d.levelReq());
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

    /**
     * The only weapons loot may drop (IT-01 / IT-15, audit 2026-10-14): the four regional weapons, each named as a
     * {@code rare} drop of its own region's mob or dungeon. Everything else stays the owner's rule.
     */
    static final Map<String, Set<String>> APPROVED_WEAPON_DROPS = Map.of(
            "loot.goviin_chono", Set.of("weapon.talyn_ild"),
            "loot.deeremchin", Set.of("weapon.talyn_ild"),
            "loot.khasar", Set.of("weapon.khasar_soyo"),
            "loot.dungeon.khasar_den", Set.of("weapon.khasar_soyo"),
            "loot.khangai_baavgai", Set.of("weapon.anchin_num"),
            "loot.altai_avarga", Set.of("weapon.altai_jad"));

    @Test
    void noBundledTableDropsAWeapon() {
        // one weapon per player: the class weapon, which evolves in place; loot never adds another (owner's rule),
        // except the approved regional weapons from their own tables
        Rng rng = Rng.seeded(31);
        Set<String> seen = new java.util.HashSet<>();
        for (LootTable t : CAT.lootTables()) {
            Set<String> approved = APPROVED_WEAPON_DROPS.getOrDefault(t.id(), Set.of());
            for (int i = 0; i < 500; i++) {
                for (PlayerClass c : PlayerClass.values()) {
                    for (LootDrop d : ENGINE.roll(t, new LootContext(40, LootTier.NORMAL, c, 100, null, "t"), rng)) {
                        ItemDefinition def = CAT.require(d.item().definitionId());
                        if (def.type().category() != ItemType.Category.WEAPON) continue;
                        assertTrue(approved.contains(def.id()), t.id() + " dropped " + def.id());
                        assertTrue(def.classes().isEmpty() && !def.id().startsWith("weapon.class."), "never a class weapon: " + def.id());
                        seen.add(t.id() + " " + def.id());
                    }
                }
            }
        }
        // the approved drops are real (no stale entry) and nothing else in the data names a weapon
        for (Map.Entry<String, Set<String>> e : APPROVED_WEAPON_DROPS.entrySet()) {
            for (String w : e.getValue()) assertTrue(seen.contains(e.getKey() + " " + w), e.getKey() + " never dropped " + w);
        }
    }

    @Test
    void weaponsReachLootOnlyAsNamedRareDrops() {
        for (LootTable t : CAT.lootTables()) {
            Set<String> approved = APPROVED_WEAPON_DROPS.getOrDefault(t.id(), Set.of());
            for (LootTable.Entry e : concat(t.guaranteed(), t.entries())) {
                assertFalse(e.pool() != null && e.pool().categories().contains(ItemType.Category.WEAPON), t.id() + " has a weapon pool");
                assertFalse(e.itemId() != null && CAT.require(e.itemId()).type().category() == ItemType.Category.WEAPON,
                        t.id() + " names " + e.itemId() + " as a regular or guaranteed entry");
            }
            for (LootTable.Rare r : t.rare()) {
                String id = r.entry().itemId();
                if (id == null || CAT.require(id).type().category() != ItemType.Category.WEAPON) continue;
                assertTrue(approved.contains(id), t.id() + " names unapproved weapon " + id);
                assertTrue(r.chance() > 0 && r.chance() <= 0.10, t.id() + " " + id + " is a rare drop, chance " + r.chance());
            }
        }
    }

    private static List<LootTable.Entry> concat(List<LootTable.Entry> a, List<LootTable.Entry> b) {
        List<LootTable.Entry> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return out;
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
            assertTrue(lvl >= 17 && lvl <= 20, "level " + lvl + " (the spread only goes down)");
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
