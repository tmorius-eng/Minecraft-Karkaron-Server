package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.loot.Rng;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemGeneratorTest {

    static final ItemCatalog CAT = ItemCatalogLoaderTest.BUNDLED;
    static final ItemGenerator GEN = new ItemGenerator(CAT);
    static final ItemValidator VAL = new ItemValidator(CAT);

    @Test
    void everyDefinitionAtEveryRarityAndManyLevelsIsGenuine() {
        int made = 0;
        for (ItemDefinition d : CAT.items()) {
            for (ItemRarity r : ItemRarity.values()) {
                if (!d.canRoll(r) || r == ItemRarity.UNIQUE) continue;
                for (int seed = 0; seed < 25; seed++) {
                    int level = 1 + (seed * 7) % 60;
                    ItemInstance i = GEN.generate(d, r, level, Rng.seeded(seed * 31L + d.id().hashCode()), "test", UUID.randomUUID());
                    assertEquals(List.of(), VAL.problems(i), d.id() + " " + r + " lvl " + level + " " + i.affixes());
                    assertTrue(i.itemLevel() >= d.levelReq());
                    made++;
                }
            }
        }
        assertTrue(made > 3000, "generated " + made);
    }

    @Test
    void affixCountFollowsRarityAndKindsNeverRepeat() {
        ItemDefinition sword = CAT.require("weapon.khaany_ild");
        for (ItemRarity r : List.of(ItemRarity.RARE, ItemRarity.EPIC, ItemRarity.LEGENDARY, ItemRarity.ANCIENT, ItemRarity.MYTHIC)) {
            for (int seed = 0; seed < 50; seed++) {
                ItemInstance i = GEN.generate(sword, r, 40, Rng.seeded(seed), "t", null);
                assertTrue(i.affixes().size() >= r.minAffixes() && i.affixes().size() <= r.maxAffixes(), r + " " + i.affixes());
                Set<String> kinds = new HashSet<>();
                for (RolledAffix ra : i.affixes()) {
                    Affix a = CAT.affix(ra.affixId()).orElseThrow();
                    assertTrue(kinds.add(a.stat() != null ? a.stat().name() : a.spell() + "." + a.modKey()), "repeated kind " + i.affixes());
                }
            }
        }
        assertEquals(0, GEN.generate(sword, ItemRarity.RARE, 30, Rng.seeded(3), "t", null).affixes().stream()
                .filter(ra -> CAT.affix(ra.affixId()).orElseThrow().spell() != null).count(), "class spell affixes need a class item");
    }

    @Test
    void classSpellAffixesAppearOnlyOnThatClassesWeapons() {
        ItemDefinition cw = CAT.require("weapon.class.baatar.4");
        boolean seen = false;
        for (int seed = 0; seed < 200; seed++) {
            for (RolledAffix ra : GEN.generate(cw, ItemRarity.LEGENDARY, 50, Rng.seeded(seed), "t", null).affixes()) {
                Affix a = CAT.affix(ra.affixId()).orElseThrow();
                if (a.spell() != null) {
                    assertEquals(PlayerClass.BAATAR, a.clazz());
                    seen = true;
                }
            }
        }
        assertTrue(seen, "a baatar spell affix should roll on a baatar weapon");
    }

    @Test
    void higherRarityRollsHigherAndScales() {
        ItemDefinition d = CAT.require("weapon.khaany_ild"); // rare..mythic, damage 22..27 + 1.8 per level
        double growth = 1.8 * (30 - 1);
        double rare = 0, mythic = 0;
        for (int s = 0; s < 400; s++) {
            rare += GEN.generate(d, ItemRarity.RARE, 30, Rng.seeded(s), "t", null).stat(ItemStat.DAMAGE) - growth;
            mythic += GEN.generate(d, ItemRarity.MYTHIC, 30, Rng.seeded(s), "t", null).stat(ItemStat.DAMAGE) - growth;
        }
        double ratio = mythic / rare;
        // the base part scales by 2.0 / 1.25 = 1.6 and mythic rolls in the top 30% of the range (floor 0.7 vs 0.1)
        assertTrue(ratio > 1.6 && ratio < 1.75, "ratio " + ratio);
    }

    @Test
    void fixedRarityItemsKeepTheirExactNumbers() {
        ItemInstance i = GEN.generate(CAT.require("weapon.class.baatar.4"), ItemRarity.LEGENDARY, 45, Rng.seeded(1), "t", null);
        assertEquals(21.0, i.stat(ItemStat.DAMAGE), 1e-9, "class weapons keep the damage they had before the item engine");
        assertEquals(11.0, i.stat(ItemStat.CRIT_CHANCE), 1e-9);
    }

    @Test
    void sameSeedSameItemDifferentUuid() {
        ItemDefinition d = CAT.require("armor.tengeriin.chestplate");
        ItemInstance a = GEN.generate(d, ItemRarity.EPIC, 40, Rng.seeded(9), "t", null);
        ItemInstance b = GEN.generate(d, ItemRarity.EPIC, 40, Rng.seeded(9), "t", null);
        assertEquals(a.stats(), b.stats());
        assertEquals(a.affixes(), b.affixes());
        assertNotEquals(a.uuid(), b.uuid(), "every item has its own identity");
    }

    @Test
    void soulboundItemsAreBoundToTheirReceiverAndUniquesAreNeverGenerated() {
        UUID owner = UUID.randomUUID();
        ItemInstance cw = GEN.generate(CAT.require("weapon.class.mergen.1"), ItemRarity.COMMON, 1, Rng.seeded(1), "starter", owner);
        assertTrue(cw.soulbound());
        assertEquals(owner, cw.boundTo());
        assertThrows(IllegalArgumentException.class,
                () -> GEN.generate(CAT.require("relic.khukh_suld"), ItemRarity.UNIQUE, 10, Rng.seeded(1), "t", owner));
        assertThrows(IllegalArgumentException.class,
                () -> GEN.generate(CAT.require("weapon.talyn_ild"), ItemRarity.COMMON, 10, Rng.seeded(1), "t", owner), "below its rarity range");
    }

    @Test
    void levelIsClampedToTheRequirementAndTheCap() {
        ItemDefinition d = CAT.require("weapon.khaany_ild");
        assertEquals(30, GEN.generate(d, ItemRarity.RARE, 3, Rng.seeded(1), "t", null).itemLevel());
        assertEquals(60, GEN.generate(d, ItemRarity.RARE, 99, Rng.seeded(1), "t", null).itemLevel());
    }

    @Test
    void materialsHaveNoAffixes() {
        ItemInstance m = GEN.generate(CAT.require("item.chonon_arisan"), ItemRarity.COMMON, 5, Rng.seeded(1), "t", null);
        assertTrue(m.affixes().isEmpty());
        assertTrue(m.stats().isEmpty());
        assertEquals(Map.of(), m.stats());
    }

    @Test
    void wholeNumberStatsRollWhole_andOldFractionsCountAndShowWhole() {
        ItemDefinition tome = CAT.require("offhand.boogiin_sudar");
        for (int seed = 0; seed < 200; seed++) {
            for (ItemRarity r : List.of(ItemRarity.UNCOMMON, ItemRarity.EPIC, ItemRarity.LEGENDARY)) {
                ItemInstance i = GEN.generate(tome, r, 1 + seed % 60, Rng.seeded(seed), "t", null);
                Map<ItemStat, Double> all = Equipment.itemStats(CAT, i);
                double res = all.get(ItemStat.RESOURCE_MAX);
                assertEquals(Math.rint(res), res, "resource capacity is an integer in game: " + i);
                assertEquals(List.of(), VAL.problems(i));
            }
        }
        // an item rolled before the rule (+10.1) gives and shows +10, and is still genuine (not quarantined)
        ItemInstance old = GEN.generate(tome, ItemRarity.UNCOMMON, 10, Rng.seeded(1), "t", null);
        Map<ItemStat, Double> stats = new java.util.EnumMap<>(old.stats());
        stats.put(ItemStat.RESOURCE_MAX, 10.1);
        ItemInstance legacy = old.reforged(old.itemLevel(), stats, 0);
        assertEquals(List.of(), VAL.problems(legacy));
        assertEquals(10.0, Equipment.itemStats(CAT, legacy).get(ItemStat.RESOURCE_MAX));
        assertEquals("+10", ItemStat.RESOURCE_MAX.format(10.1));
        assertEquals("+10.1", ItemStat.ARMOR.format(10.1));
    }
}
