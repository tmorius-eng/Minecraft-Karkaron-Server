package mn.suld.api.item;

import mn.suld.api.loot.LootTier;
import mn.suld.api.skill.tree.SkillTreeLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemCatalogLoaderTest {

    static final ItemCatalog BUNDLED = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();

    @Test
    void bundledCatalogLoadsWithoutIssues() {
        ItemCatalogLoader.Result r = ItemCatalogLoader.load(ItemCatalogLoader.classpath());
        assertEquals(List.of(), r.issues());
        assertTrue(r.catalog().items().size() >= 80, "item definitions: " + r.catalog().items().size());
        assertTrue(r.catalog().affixes().size() >= 20);
        assertEquals(3, r.catalog().sets().size());
        assertTrue(r.catalog().lootTables().size() >= 20);
        for (LootTier t : LootTier.values()) assertTrue(r.catalog().band(t).weights().size() > 0, t.name());
    }

    @Test
    void bundledCatalogCoversEverySlotEveryRarityAndEveryStat() {
        java.util.Set<EquipSlot> slots = java.util.EnumSet.noneOf(EquipSlot.class);
        java.util.Set<ItemRarity> rarities = java.util.EnumSet.noneOf(ItemRarity.class);
        java.util.Set<ItemStat> stats = java.util.EnumSet.noneOf(ItemStat.class);
        for (ItemDefinition d : BUNDLED.items()) {
            slots.addAll(d.type().slots());
            for (ItemRarity r : ItemRarity.values()) if (d.canRoll(r)) rarities.add(r);
            stats.addAll(d.stats().keySet());
        }
        for (Affix a : BUNDLED.affixes()) if (a.stat() != null) stats.add(a.stat());
        assertEquals(java.util.EnumSet.allOf(EquipSlot.class), slots);
        assertEquals(java.util.EnumSet.allOf(ItemRarity.class), rarities);
        assertEquals(java.util.EnumSet.allOf(ItemStat.class), stats);
    }

    @Test
    void existingItemIdsSurvive() {
        // ids that players already hold must keep resolving
        for (String id : List.of("item.chonon_arisan", "weapon.talyn_ild", "weapon.surgamj_ild", "weapon.surgamj_num", "weapon.khasar_soyo",
                "item.khasar_zurkh", "item.talyn_tuvshin", "item.khilentsiin_khor", "weapon.govi_khutga", "item.baavgain_arisan",
                "weapon.khangai_sukh", "item.mosun_chuluu", "weapon.altai_jad", "weapon.class.baatar.1", "weapon.class.khulegchin.4")) {
            assertTrue(BUNDLED.item(id).isPresent(), id);
        }
        // and every loot table the mob/dungeon content names exists
        for (String id : List.of("loot.goviin_chono", "loot.orkhon_chono", "loot.khasar", "loot.dungeon.khasar_den", "loot.deeremchin",
                "loot.goviin_khilents", "loot.elsnii_suns", "loot.saaral_chono", "loot.khangai_baavgai", "loot.altai_mosun_suns",
                "loot.altai_avarga", "loot.dungeon.govi_bulsh", "loot.dungeon.baavgain_uur", "loot.dungeon.mosun_orgil",
                "loot.elsnii_khaan", "loot.oin_ezen", "loot.mosun_khaan")) {
            assertTrue(BUNDLED.lootTable(id).isPresent(), id);
        }
    }

    @Test
    void uniqueItemsAreOnlyRelicsAndNeverLoot() {
        for (ItemDefinition d : BUNDLED.items()) {
            if (d.rarity() == ItemRarity.UNIQUE) {
                assertEquals(ItemType.RELIC, d.type());
                assertTrue(d.id().startsWith("relic."));
                assertFalse(d.lootable());
            } else {
                assertNotEquals(ItemRarity.UNIQUE, d.maxRarity(), d.id());
            }
        }
        assertTrue(BUNDLED.item("relic.khukh_suld").isPresent());
    }

    // ------------------------------------------------------------------ validation messages

    /** The bundled files with one file replaced. */
    private static SkillTreeLoader.Source with(String file, String text) {
        Map<String, String> over = new HashMap<>(Map.of(file, text));
        SkillTreeLoader.Source base = ItemCatalogLoader.classpath();
        return name -> over.containsKey(name) ? over.get(name) : base.read(name);
    }

    private static String issues(SkillTreeLoader.Source src) {
        return ItemCatalogLoader.load(src).issues().toString();
    }

    private static String oneItem(String json) {
        return "{\"items\": [" + json + "]}";
    }

    @Test
    void reportsExactFileAndField() {
        String s = issues(with("materials.json", oneItem("{\"id\":\"item.x\",\"name\":\"X\",\"material\":\"minecraft:stone\",\"type\":\"MATERIAL\",\"rarity\":\"legendery\",\"level\":1}")));
        assertTrue(s.contains("materials.json: item.x.rarity: unknown rarity 'legendery'"), s);
    }

    @Test
    void refusesStatsOnUnequippableItemsAndUnknownStats() {
        assertTrue(issues(with("materials.json", oneItem("{\"id\":\"item.x\",\"name\":\"X\",\"material\":\"minecraft:stone\",\"type\":\"MATERIAL\",\"rarity\":\"common\",\"level\":1,\"stats\":{\"damage\":5}}")))
                .contains("stats on an item that cannot be equipped"));
        assertTrue(issues(with("materials.json", oneItem("{\"id\":\"weapon.y\",\"name\":\"Y\",\"material\":\"minecraft:iron_sword\",\"type\":\"SWORD\",\"rarity\":\"common\",\"level\":1,\"stats\":{\"power\":5}}")))
                .contains("weapon.y.stats.power: unknown stat"));
    }

    @Test
    void armourMustBeRealArmour() {
        String s = issues(with("armor.json", oneItem("{\"id\":\"armor.z.helmet\",\"name\":\"Z\",\"material\":\"minecraft:stone\",\"type\":\"HELMET\",\"rarity\":\"common\",\"level\":1}")));
        assertTrue(s.contains("minecraft:stone cannot be a HELMET"), s);
    }

    @Test
    void uniqueRulesAreEnforced() {
        String s = issues(with("relics.json", oneItem("{\"id\":\"weapon.u\",\"name\":\"U\",\"material\":\"minecraft:iron_sword\",\"type\":\"SWORD\",\"rarity\":\"unique\",\"level\":1}")));
        assertTrue(s.contains("unique items are relics"), s);
        assertTrue(s.contains("unique items can never be loot"), s);
        assertTrue(s.contains("a unique item's id is its relic key"), s);
        String t = issues(with("weapons.json", oneItem("{\"id\":\"weapon.v\",\"name\":\"V\",\"material\":\"minecraft:iron_sword\",\"type\":\"SWORD\",\"rarity\":\"rare\",\"maxRarity\":\"unique\",\"level\":1}")));
        assertTrue(t.contains("unique items cannot roll"), t);
    }

    @Test
    void lootCannotReferenceUniquesOrUnknownItems() {
        String s = issues(with("loot.json", "{\"tables\": [{\"id\":\"loot.t\",\"rolls\":[1,1],\"entries\":[{\"item\":\"relic.khukh_suld\",\"weight\":1},{\"item\":\"item.nope\",\"weight\":1}]}]}"));
        assertTrue(s.contains("relic.khukh_suld is unique: unique items can never be loot"), s);
        assertTrue(s.contains("unknown item item.nope"), s);
    }

    @Test
    void everyLootTierNeedsABand() {
        String s = issues(with("tiers.json", "{\"NORMAL\": {\"common\": 1}}"));
        assertTrue(s.contains("tiers.json: BOSS: missing"), s);
        assertTrue(issues(with("tiers.json", "{\"NORMAL\": {\"unique\": 1}}")).contains("unique items never drop"));
    }

    @Test
    void setsMustBeCompletableAndNamedByTheirPieces() {
        String s = issues(with("sets.json", "{\"sets\": [{\"id\":\"set.chingis\",\"name\":\"C\",\"pieces\":[\"armor.chingis.helmet\",\"weapon.talyn_ild\"],\"bonuses\":{\"2\":{\"stats\":{\"max_health\":1}}}},"
                + "{\"id\":\"set.talyn_anchin\",\"name\":\"T\",\"pieces\":[\"armor.anchin.helmet\",\"armor.anchin.boots\"],\"bonuses\":{\"2\":{\"stats\":{\"max_health\":1}}}},"
                + "{\"id\":\"set.booi_yosol\",\"name\":\"B\",\"pieces\":[\"armor.booi.chestplate\",\"offhand.boogiin_sudar\"],\"bonuses\":{\"9\":{\"stats\":{\"max_health\":1}}}}]}"));
        assertTrue(s.contains("weapon.talyn_ild does not name this set"), s);
        assertTrue(s.contains("needs 2..2 pieces"), s);
    }

    @Test
    void brokenJsonAndMissingFilesAreReported() {
        assertTrue(issues(with("affixes.json", "{\"affixes\": [")).contains("affixes.json: : broken JSON"));
        SkillTreeLoader.Source missing = name -> {
            if (name.equals("recipes.json")) throw new NoSuchFileException(name);
            return ItemCatalogLoader.classpath().read(name);
        };
        assertTrue(issues(missing).contains("recipes.json: : file is missing"));
    }

    @Test
    void spellModifiersOnlyOnSingleClassItems() throws IOException {
        String s = issues(with("weapons.json", oneItem("{\"id\":\"weapon.m\",\"name\":\"M\",\"material\":\"minecraft:iron_sword\",\"type\":\"SWORD\",\"rarity\":\"rare\",\"level\":1,"
                + "\"effects\":[{\"type\":\"mod\",\"spell\":\"TENGER_TSAVCHILT\",\"key\":\"DAMAGE_PCT\",\"value\":10}]}")));
        assertTrue(s.contains("needs the item to be for exactly one class"), s);
        String k = issues(with("weapons.json", oneItem("{\"id\":\"weapon.k\",\"name\":\"K\",\"material\":\"minecraft:iron_sword\",\"type\":\"SWORD\",\"rarity\":\"rare\",\"level\":1,"
                + "\"effects\":[{\"type\":\"keystone\",\"kind\":\"MUNKH_TESVER\"}]}")));
        assertTrue(k.contains("come from the skill tree"), k);
    }
}
