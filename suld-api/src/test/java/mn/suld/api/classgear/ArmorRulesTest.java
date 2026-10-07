package mn.suld.api.classgear;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemGenerator;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemStat;
import mn.suld.api.item.ItemValidator;
import mn.suld.api.loot.Rng;
import mn.suld.api.progression.ExpSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorRulesTest {

    static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void needMatchesTheSpecTable() {
        // docs/ARMOR_PROGRESSION.md armor-need table
        assertEquals(6, ArmorRules.need(1));
        assertEquals(45, ArmorRules.need(5));
        assertEquals(112, ArmorRules.need(10));
        assertEquals(1123, ArmorRules.need(59));
        assertEquals(0, ArmorRules.need(60));
        long cum = 0;
        for (int a = 1; a < 60; a++) cum += ArmorRules.need(a);
        assertEquals(29365, cum);
    }

    @Test
    void armourLevelIsCappedAtThePlayerLevelAndCatchesUpDouble() {
        ArmorRules.Gain g = ArmorRules.gain(ClassGear.NONE, 1000, 3);
        assertEquals(3, g.after().armorLevel(), "capped at player level 3");
        assertEquals(ArmorRules.need(3), g.after().armorXp(), 1e-9, "one level banked at the cap");
        // the player levels up: the banked XP is taken at once
        assertEquals(4, ArmorRules.settle(g.after(), 10).after().armorLevel());
        // far behind the player level: double XP
        ArmorRules.Gain behind = ArmorRules.gain(ClassGear.NONE, 3, 20);
        assertEquals(6, behind.added(), 1e-9);
        assertEquals(2, behind.after().armorLevel());
        assertEquals(0, ArmorRules.gain(ClassGear.NONE, 0, 20).added());
        assertEquals(0, ArmorRules.gain(ClassGear.NONE, Double.NaN, 20).added());
    }

    @Test
    void sourcesAndRepeatFatigue() {
        assertEquals(0.2, ArmorRules.xpFor(ExpSource.MOB_KILL));
        assertEquals(3, ArmorRules.xpFor(ExpSource.BOSS_KILL));
        assertEquals(0, ArmorRules.xpFor(ExpSource.ADMIN));
        ClassGear g = ClassGear.NONE;
        assertEquals(25, ArmorRules.dungeonXp(g, "dungeon.x"), 1e-9);
        for (int i = 0; i < 3; i++) g = g.withCleared("dungeon.x");
        assertEquals(25 * 0.55, ArmorRules.dungeonXp(g, "dungeon.x"), 1e-9);
        for (int i = 0; i < 10; i++) g = g.withCleared("dungeon.x");
        assertEquals(8, g.recent().size(), "only the last 8 clears");
        assertEquals(25 * 0.25, ArmorRules.dungeonXp(g, "dungeon.x"), 1e-9, "floor 25 %");
        for (int i = 0; i < 8; i++) g = g.withCleared("dungeon.y");
        assertEquals(25, ArmorRules.dungeonXp(g, "dungeon.x"), 1e-9, "other content restores it");
    }

    @Test
    void tierGatesNeedEverything() {
        ClassGear g = ClassGear.NONE.withProgress(12, 0);
        ArmorRules.Holdings rich = new ArmorRules.Holdings(10_000, 10, 0);
        assertFalse(ArmorRules.canUpgrade(g, rich), "the dungeon is not cleared");
        g = g.withCleared("dungeon.govi_bulsh");
        assertTrue(ArmorRules.canUpgrade(g, rich));
        assertFalse(ArmorRules.canUpgrade(g, new ArmorRules.Holdings(1_999, 10, 0)));
        assertFalse(ArmorRules.canUpgrade(g, new ArmorRules.Holdings(10_000, 9, 0)));
        assertFalse(ArmorRules.canUpgrade(g.withProgress(11, 0), rich));
        ClassGear t5 = ClassGear.NONE.withProgress(60, 0).withTier(ArmorTier.T5).withCleared("dungeon.tengeriin_ordon");
        assertFalse(ArmorRules.canUpgrade(t5, new ArmorRules.Holdings(1_000_000, 99, 2)), "T6 needs Ascension III");
        assertTrue(ArmorRules.canUpgrade(t5, new ArmorRules.Holdings(1_000_000, 99, 3)));
        assertTrue(ArmorRules.gates(t5.withTier(ArmorTier.T6), rich).isEmpty());
        assertEquals(0, ClassGear.NONE.withEnhance(3).withTier(ArmorTier.T2).enhance(), "a new tier resets enhancement");
    }

    @Test
    void idsAndEnhancement() {
        String id = ArmorRules.definitionId(PlayerClass.BAATAR, ArmorPiece.CHESTPLATE, ArmorTier.T3);
        assertEquals("armor.class.baatar.chestplate.t3", id);
        assertTrue(ItemDefinition.ID.matcher(id).matches());
        ArmorRules.Parsed p = ArmorRules.parse(id).orElseThrow();
        assertEquals(new ArmorRules.Parsed(PlayerClass.BAATAR, ArmorPiece.CHESTPLATE, ArmorTier.T3), p);
        assertTrue(ArmorRules.parse("armor.gan.helmet").isEmpty());
        assertEquals("baatar_t3", ArmorRules.assetId(PlayerClass.BAATAR, ArmorTier.T3));
        assertEquals(1.10, ArmorRules.powerFactor(5), 1e-9);
        assertEquals(1.10, ArmorRules.powerFactor(9), 1e-9);
        assertEquals(40L * 30 * 2 * 3, ArmorRules.enhanceCost(30, 2, ArmorTier.T3));
    }

    @Test
    void recordRoundTripsAndRefusesBrokenData() {
        ClassGear g = ClassGear.NONE.withProgress(17, 42.5).withTier(ArmorTier.T2).withEnhance(2)
                .withPiece(ArmorPiece.HELMET, UUID.randomUUID()).withPiece(ArmorPiece.BOOTS, UUID.randomUUID())
                .withWeapon(UUID.randomUUID()).withCleared("dungeon.khasar_den");
        assertEquals(g, ClassGear.fromJson(g.toJson()));
        assertEquals(ClassGear.NONE, ClassGear.fromJson(null));
        assertTrue(ClassGear.NONE.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ClassGear.fromJson("{\"v\":99}"));
        assertThrows(IllegalArgumentException.class, () -> ClassGear.fromJson("{\"v\":1,\"p\":{\"cape\":\"x\"}}"));
        assertThrows(IllegalArgumentException.class, () -> ClassGear.fromJson("not json"));
    }

    @Test
    void theBaatarSetIsGenuineAtEveryLevelAndTierAndFollowsTheGenericBudget() {
        ItemCatalog cat = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();
        ItemGenerator gen = new ItemGenerator(cat);
        ItemValidator val = new ItemValidator(cat);
        for (ArmorTier t : ArmorTier.values()) {
            Set<String> ids = new java.util.HashSet<>();
            for (ArmorPiece piece : ArmorPiece.values()) {
                ItemDefinition d = cat.require(ArmorRules.definitionId(PlayerClass.BAATAR, piece, t));
                ids.add(d.id());
                assertEquals(t.rarity(), d.rarity());
                assertTrue(d.fixedRarity());
                assertEquals(1, d.levelReq(), "the armour level never blocks equipping");
                assertEquals(Set.of(PlayerClass.BAATAR), d.classes());
                assertFalse(d.lootable());
                for (int al : List.of(1, t.armorLevel(), 60)) {
                    ItemInstance i = gen.generate(d, d.rarity(), al, Rng.seeded(ArmorRules.seed(ME, piece, t)), "class", ME, UUID.randomUUID());
                    assertTrue(i.soulbound() && ME.equals(i.boundTo()));
                    assertEquals(List.of(), val.problems(i), d.id() + " at " + al);
                }
            }
            assertEquals(ids, Set.copyOf(cat.set("set.class.baatar_t" + t.number()).orElseThrow().pieces()));
        }
        // the same seed rolls the same piece: a level change only adds the per-level growth
        ItemDefinition chest = cat.require("armor.class.baatar.chestplate.t3");
        ItemInstance a = gen.generate(chest, chest.rarity(), 24, Rng.seeded(ArmorRules.seed(ME, ArmorPiece.CHESTPLATE, ArmorTier.T3)), "class", ME, UUID.randomUUID());
        ItemInstance b = gen.generate(chest, chest.rarity(), 25, Rng.seeded(ArmorRules.seed(ME, ArmorPiece.CHESTPLATE, ArmorTier.T3)), "class", ME, UUID.randomUUID());
        assertEquals(a.affixes().stream().map(x -> x.affixId()).toList(), b.affixes().stream().map(x -> x.affixId()).toList());
        assertTrue(b.stats().get(ItemStat.ARMOR) >= a.stats().get(ItemStat.ARMOR));
        // budget: the T3 chest at its entry level is within 20 % of the generic proxy (armor.gan.chestplate, epic, 24)
        double armor = chest.stats().get(ItemStat.ARMOR).at(0.625) + chest.statPerLevel().getOrDefault(ItemStat.ARMOR, 0.0) * 23;
        assertEquals(10.15, armor, 10.15 * 0.2);
        Map<ItemStat, Double> unused = Map.of();
        assertTrue(unused.isEmpty());
    }
}
