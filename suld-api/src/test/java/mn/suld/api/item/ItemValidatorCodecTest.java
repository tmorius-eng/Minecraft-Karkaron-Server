package mn.suld.api.item;

import mn.suld.api.loot.Rng;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Forged, corrupted and old items. */
class ItemValidatorCodecTest {

    static final ItemCatalog CAT = ItemCatalogLoaderTest.BUNDLED;
    static final ItemValidator VAL = new ItemValidator(CAT);
    static final ItemGenerator GEN = new ItemGenerator(CAT);

    private static ItemInstance genuine() {
        return GEN.generate(CAT.require("weapon.khaany_ild"), ItemRarity.EPIC, 35, Rng.seeded(4), "test", null);
    }

    private static ItemInstance with(ItemInstance i, String id, ItemRarity r, int lvl, Map<ItemStat, Double> stats, List<RolledAffix> affixes, boolean sb, int schema) {
        return new ItemInstance(id, i.uuid(), r, lvl, stats, affixes, sb, null, 0, "forged", schema);
    }

    @Test
    void genuineItemPasses() {
        assertEquals(List.of(), VAL.problems(genuine()));
    }

    @Test
    void forgeriesAreRefused() {
        ItemInstance g = genuine();
        String one;
        one = VAL.problems(with(g, "weapon.not_real", g.rarity(), 35, g.stats(), g.affixes(), false, 2)).toString();
        assertTrue(one.contains("unknown item id"), one);
        one = VAL.problems(with(g, g.definitionId(), ItemRarity.COMMON, 35, g.stats(), List.of(), false, 2)).toString();
        assertTrue(one.contains("rarity common outside rare..mythic"), one);
        one = VAL.problems(with(g, g.definitionId(), ItemRarity.UNIQUE, 35, g.stats(), List.of(), false, 2)).toString();
        assertTrue(one.contains("unique rarity on a non-unique item"), one);
        Map<ItemStat, Double> huge = new EnumMap<>(g.stats());
        huge.put(ItemStat.DAMAGE, 9999.0);
        one = VAL.problems(with(g, g.definitionId(), g.rarity(), 35, huge, g.affixes(), false, 2)).toString();
        assertTrue(one.contains("impossible damage 9999.0"), one);
        Map<ItemStat, Double> foreign = new EnumMap<>(g.stats());
        foreign.put(ItemStat.LIFESTEAL, 1.0);
        one = VAL.problems(with(g, g.definitionId(), g.rarity(), 35, foreign, g.affixes(), false, 2)).toString();
        assertTrue(one.contains("stat lifesteal is not on"), one);
        one = VAL.problems(with(g, g.definitionId(), g.rarity(), 61, g.stats(), g.affixes(), false, 2)).toString();
        assertTrue(one.contains("item level 61"), one);
        one = VAL.problems(with(g, g.definitionId(), g.rarity(), 35, g.stats(), List.of(new RolledAffix("made_up", 1)), false, 2)).toString();
        assertTrue(one.contains("unknown affix made_up"), one);
        one = VAL.problems(with(g, g.definitionId(), g.rarity(), 35, g.stats(), List.of(new RolledAffix("khurts", 500)), false, 2)).toString();
        assertTrue(one.contains("affix khurts value 500.0 outside"), one);
        one = VAL.problems(with(g, g.definitionId(), g.rarity(), 35, g.stats(), List.of(new RolledAffix("khurts", 3), new RolledAffix("khurts", 3)), false, 2)).toString();
        assertTrue(one.contains("affix khurts twice"), one);
        one = VAL.problems(with(g, g.definitionId(), g.rarity(), 35, g.stats(), List.of(new RolledAffix("bat", 5)), false, 2)).toString();
        assertTrue(one.contains("affix bat cannot be on weapon.khaany_ild"), one);
        List<RolledAffix> many = List.of(new RolledAffix("khurts", 3), new RolledAffix("shonkhor", 3), new RolledAffix("shiruun", 10),
                new RolledAffix("shuurkhai", 5));
        one = VAL.problems(with(g, g.definitionId(), ItemRarity.EPIC, 35, g.stats(), many, false, 2)).toString();
        assertTrue(one.contains("4 affixes on epic (max 3)"), one);
        one = VAL.problems(with(g, g.definitionId(), g.rarity(), 35, g.stats(), g.affixes(), false, 7)).toString();
        assertTrue(one.contains("unknown schema version 7"), one);
        ItemInstance cw = GEN.generate(CAT.require("weapon.class.boo.1"), ItemRarity.COMMON, 1, Rng.seeded(1), "t", null);
        one = VAL.problems(with(cw, cw.definitionId(), cw.rarity(), 1, cw.stats(), List.of(), false, 2)).toString();
        assertTrue(one.contains("must be soulbound"), one);
    }

    @Test
    void codecRoundTripsEverything() {
        ItemInstance g = genuine().boundTo(UUID.randomUUID());
        String text = ItemCodec.encode(g);
        ItemInstance back = ItemCodec.decode(text).orElseThrow();
        assertEquals(g, back);
        assertEquals(g.affixes(), back.affixes());
        assertEquals(g.boundTo(), back.boundTo());
        assertEquals(text, ItemCodec.encode(back), "stable");
    }

    @Test
    void codecRefusesNewerAndBrokenData() {
        String text = ItemCodec.encode(genuine());
        assertTrue(ItemCodec.decode(text.replace("\"v\":2", "\"v\":3")).isEmpty(), "a newer server's item is not guessed at");
        assertTrue(ItemCodec.decode("{not json").isEmpty());
        assertTrue(ItemCodec.decode(text.replace("\"r\":\"epic\"", "\"r\":\"godly\"")).isEmpty());
        assertTrue(ItemCodec.decode(text.replace("\"uuid\":\"", "\"uuid\":\"zz")).isEmpty());
        assertTrue(ItemCodec.decode("").isEmpty());
        assertTrue(ItemCodec.decode(null).isEmpty());
    }

    @Test
    void legacyItemsAreMigrated() {
        // what ItemFactory wrote before the item engine: fractions for crit, old stat ids
        ItemInstance old = ItemCodec.legacy("weapon.talyn_ild", UUID.randomUUID().toString(), "rare", 4,
                "attack:12.5;crit_chance:0.065", false, 0).orElseThrow();
        assertEquals(12.5, old.stat(ItemStat.DAMAGE), 1e-9);
        assertEquals(6.5, old.stat(ItemStat.CRIT_CHANCE), 1e-9);
        assertEquals(List.of(), VAL.problems(old), "an item made by the old system is genuine after migration");
        ItemInstance cw = ItemCodec.legacy("weapon.class.baatar.2", UUID.randomUUID().toString(), "rare", 12,
                "attack:9.0;crit_chance:0.07", true, 0).orElseThrow();
        assertEquals(List.of(), VAL.problems(cw));
        assertTrue(ItemCodec.legacy("x.y", "not-a-uuid", "rare", 1, "", false, 0).isEmpty());
    }
}
