package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.loot.Rng;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RarityTooltipEconomyTest {

    static final ItemCatalog CAT = ItemCatalogLoaderTest.BUNDLED;
    static final ItemGenerator GEN = new ItemGenerator(CAT);

    @Test
    void everyRarityStepIsMechanicallyBetter() {
        ItemRarity[] r = ItemRarity.values();
        for (int i = 1; i < r.length - 1; i++) { // UNIQUE is outside the ladder
            assertTrue(r[i].maxAffixes() >= r[i - 1].maxAffixes(), r[i].name());
            assertTrue(r[i].statMultiplier() > r[i - 1].statMultiplier(), r[i].name());
            assertTrue(r[i].rollFloor() >= r[i - 1].rollFloor(), r[i].name());
            assertTrue(r[i].durabilityMultiplier() > r[i - 1].durabilityMultiplier(), r[i].name());
            assertTrue(r[i].sellMultiplier() > r[i - 1].sellMultiplier(), r[i].name());
            assertTrue(r[i].salvageYield() >= r[i - 1].salvageYield(), r[i].name());
            assertTrue(r[i].defaultWeight() < r[i - 1].defaultWeight(), r[i].name());
        }
        assertEquals(0, ItemRarity.COMMON.maxAffixes());
        assertEquals(5, ItemRarity.MYTHIC.minAffixes());
        assertEquals(Binding.ON_EQUIP, ItemRarity.LEGENDARY.forcedBinding());
        assertEquals(Binding.ON_PICKUP, ItemRarity.ANCIENT.forcedBinding());
        assertEquals(Binding.SOULBOUND, ItemRarity.UNIQUE.forcedBinding());
        assertFalse(ItemRarity.EPIC.announced());
        assertTrue(ItemRarity.LEGENDARY.announced());
        assertEquals(0, ItemRarity.UNIQUE.defaultWeight());
    }

    @Test
    void bindingStrictnessOrder() {
        assertEquals(Binding.ON_PICKUP, Binding.ON_EQUIP.strongest(Binding.ON_PICKUP));
        assertEquals(Binding.ON_PICKUP, Binding.ON_PICKUP.strongest(Binding.ON_EQUIP));
        assertEquals(Binding.SOULBOUND, Binding.NONE.strongest(Binding.SOULBOUND));
        assertEquals(Binding.ON_EQUIP, Binding.NONE.strongest(Binding.ON_EQUIP));
    }

    @Test
    void durabilityScalesWithRarityAndUniqueNeverWears() {
        ItemDefinition d = CAT.require("weapon.khaany_ild"); // 600
        assertEquals(900, d.maxDurability(ItemRarity.RARE));
        assertEquals(1800, d.maxDurability(ItemRarity.MYTHIC));
        assertEquals(0, CAT.require("relic.khukh_suld").maxDurability(ItemRarity.UNIQUE));
        assertEquals(0, CAT.require("jewel.khash_bugj").maxDurability(ItemRarity.RARE), "jewellery does not wear");
    }

    private static List<ItemTooltip.Line> tip(ItemInstance i, ItemTooltip.Viewer v, ItemInstance compare) {
        ItemDefinition def = CAT.require(i.definitionId());
        return ItemTooltip.lines(CAT, def, i, v, 100, def.maxDurability(i.rarity()), compare);
    }

    private static String text(List<ItemTooltip.Line> lines) {
        return lines.stream().map(ItemTooltip.Line::text).collect(Collectors.joining("\n"));
    }

    @Test
    void tooltipShowsEverythingTheItemIs() {
        ItemInstance i = GEN.generate(CAT.require("weapon.class.baatar.3"), ItemRarity.EPIC, 25, Rng.seeded(2), "starter", UUID.randomUUID());
        List<ItemTooltip.Line> lines = tip(i, new ItemTooltip.Viewer(null, PlayerClass.MERGEN, 10, Set.of()), null);
        String t = text(lines);
        assertEquals(ItemTooltip.Style.NAME, lines.get(0).style());
        assertTrue(lines.get(0).text().contains("Баатрын Алтан Илд"));
        assertTrue(t.contains("Домогт · Илд · Зэрэг 25"), t);
        assertTrue(lines.stream().anyMatch(l -> l.style() == ItemTooltip.Style.FAIL && l.text().equals("Шаардлагатай түвшин: 25")), "level too low is red");
        assertTrue(lines.stream().anyMatch(l -> l.style() == ItemTooltip.Style.FAIL && l.text().startsWith("Анги: Баатар")), "other class is red");
        assertTrue(t.contains("+14 Хохирол"), t);
        assertEquals(3, lines.stream().filter(l -> l.style() == ItemTooltip.Style.AFFIX).count(), "epic = 3 affixes");
        assertTrue(t.contains("✦ Сүнсэнд холбоотой"), t);
        assertTrue(t.contains("Арилжаалах боломжгүй"), t);
    }

    @Test
    void tooltipShowsSetProgressAndBrokenState() {
        ItemDefinition helm = CAT.require("armor.chingis.helmet");
        ItemInstance i = GEN.generate(helm, ItemRarity.LEGENDARY, 45, Rng.seeded(1), "t", null);
        List<ItemTooltip.Line> lines = ItemTooltip.lines(CAT, helm, i,
                new ItemTooltip.Viewer(null, PlayerClass.BAATAR, 50, Set.of("armor.chingis.helmet", "armor.chingis.boots")), 0, helm.maxDurability(i.rarity()), null);
        String t = text(lines);
        assertTrue(t.contains("Иж бүрдэл: Чингисийн Өв (2/5)"), t);
        assertEquals(1, lines.stream().filter(l -> l.style() == ItemTooltip.Style.SET_ACTIVE).count(), "only the 2-piece bonus is active");
        assertEquals(3, lines.stream().filter(l -> l.style() == ItemTooltip.Style.SET_INACTIVE).count());
        assertTrue(t.contains("— эвдэрсэн"), t);
        assertTrue(t.contains("Өмсөхөд холбогдоно"), "legendary binds on equip: " + t);
    }

    @Test
    void comparisonHighlightsIncreasesAndDecreases() {
        ItemInstance worn = new ItemInstance("weapon.tumur_ild", UUID.randomUUID(), ItemRarity.UNCOMMON, 12, Map.of(ItemStat.DAMAGE, 10.0, ItemStat.ATTACK_SPEED, 4.0),
                List.of(), false, null, 0, "t", 2);
        ItemInstance hovered = new ItemInstance("weapon.khaany_ild", UUID.randomUUID(), ItemRarity.RARE, 30, Map.of(ItemStat.DAMAGE, 25.0, ItemStat.CRIT_CHANCE, 5.0),
                List.of(), false, null, 0, "t", 2);
        List<ItemTooltip.Line> c = ItemTooltip.compare(CAT, hovered, worn);
        assertTrue(c.stream().anyMatch(l -> l.style() == ItemTooltip.Style.UP && l.text().contains("+15 Хохирол")), text(c));
        assertTrue(c.stream().anyMatch(l -> l.style() == ItemTooltip.Style.UP && l.text().contains("+5% Чухал цохилтын магадлал")), text(c));
        assertTrue(c.stream().anyMatch(l -> l.style() == ItemTooltip.Style.DOWN && l.text().contains("-4% Довтолгооны хурд")), text(c));
        assertTrue(ItemTooltip.compare(CAT, worn, worn).stream().anyMatch(l -> l.style() == ItemTooltip.Style.SAME));
    }

    @Test
    void nameCarriesPrefixAndSuffixAffixes() {
        ItemDefinition d = CAT.require("weapon.khaany_ild");
        ItemInstance i = new ItemInstance(d.id(), UUID.randomUUID(), ItemRarity.RARE, 30, Map.of(ItemStat.DAMAGE, 22.0),
                List.of(new RolledAffix("khurts", 9), new RolledAffix("shonkhor", 3)), false, null, 2, "t", 2);
        assertEquals("Хурц Хааны Илд · Шонхорын +2", ItemTooltip.name(CAT, d, i));
    }

    @Test
    void economy() {
        ItemDefinition d = CAT.require("weapon.khaany_ild"); // sell 60
        ItemInstance rare = new ItemInstance(d.id(), UUID.randomUUID(), ItemRarity.RARE, 30, Map.of(), false, 0, "t");
        assertEquals(Math.round(60 * Math.min(5, ItemRarity.RARE.sellMultiplier()) * (1 + 30 / 30.0)), ItemEconomy.sellPrice(d, rare));
        ItemInstance leg = new ItemInstance(d.id(), UUID.randomUUID(), ItemRarity.LEGENDARY, 30, Map.of(), false, 0, "t");
        assertEquals(0, ItemEconomy.sellPrice(d, leg), "legendary and above: salvage only");
        ItemInstance sb = new ItemInstance(d.id(), UUID.randomUUID(), ItemRarity.RARE, 30, Map.of(), true, 0, "t");
        assertEquals(0, ItemEconomy.sellPrice(d, sb), "soulbound items are not bought");
        Map<String, Integer> salvage = ItemEconomy.salvage(CAT, d, rare);
        assertEquals(Map.of("item.altan_toos", 2 + 3), salvage);
        ItemInstance myth = new ItemInstance(d.id(), UUID.randomUUID(), ItemRarity.MYTHIC, 30, Map.of(), false, 0, "t");
        assertEquals(Map.of("item.tengeriin_chuluu", 10 + 3), ItemEconomy.salvage(CAT, d, myth));
        assertTrue(ItemEconomy.salvage(CAT, CAT.require("item.chonon_arisan"), new ItemInstance("item.chonon_arisan", UUID.randomUUID(), ItemRarity.COMMON, 1, Map.of(), false, 0, "t")).isEmpty());
        assertEquals(0, ItemEconomy.repairCost(rare, 0));
        assertTrue(ItemEconomy.repairCost(myth, 100) > ItemEconomy.repairCost(rare, 100));
    }
}
