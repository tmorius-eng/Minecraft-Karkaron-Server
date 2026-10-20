package mn.suld.plugin.content;

import mn.suld.api.balance.DungeonLadder;
import mn.suld.api.skill.tree.Ultimate;
import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.CosmeticCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit 2026-10-11 CULT-001: real historical people, the sacred Burkhan Khaldun and the Chinggisid lineage must not
 * name things players kill, farm, loot or buy. History-labelled place names (sites, regions) may keep them; they are
 * not checked here. A new name that needs one of these words goes through a Mongolian cultural review first.
 */
class CulturalNamingTest {

    /** Words that may not appear in a mob, dungeon, quest title, item, set, skill or sellable cosmetic name. */
    static final List<String> BLOCKED = List.of("Чингис", "Хасар", "Бурхан Халдун", "Алтан Ураг", "Их Хаан", "Хар Сүлд", "Цагаан Сүлд");

    private static final Pattern NAME = Pattern.compile("\"(?:name|title)\"\\s*:\\s*\"([^\"]*)\"");

    private static String resource(String path) throws IOException {
        try (InputStream in = CulturalNamingTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> names(String path) throws IOException {
        List<String> out = new ArrayList<>();
        Matcher m = NAME.matcher(resource(path));
        while (m.find()) out.add(path + ": " + m.group(1));
        return out;
    }

    private static void assertClean(List<String> names) {
        for (String n : names) {
            for (String b : BLOCKED) assertTrue(!n.contains(b), "blocked name «" + b + "» in " + n);
        }
    }

    @Test
    void contentAndItemNamesAvoidRealSacredAndHistoricalNames() throws IOException {
        List<String> all = new ArrayList<>();
        for (String f : List.of("/content/mobs.json", "/content/dungeons.json", "/content/quests.json", "/content/events.json", "/content/campaigns.json",
                "/content/npcs.json", "/content/shops.json", "/content/side_quests.json")) all.addAll(names(f));
        for (String f : List.of("armor", "class_armor", "jewelry", "weapons", "materials", "relics", "sets")) all.addAll(names("/items/" + f + ".json"));
        for (String f : List.of("baatar", "boo", "darkhan", "khulegchin", "mergen", "universal")) all.addAll(names("/skills/" + f + ".json"));
        assertTrue(all.size() > 300, "the scan reads the real files (" + all.size() + " names)");
        assertClean(all);
    }

    @Test
    void codeDefinedNamesAvoidThemToo() {
        List<String> all = new ArrayList<>();
        for (DungeonLadder.Rung r : DungeonLadder.RUNGS) all.add("ladder: " + r.name());
        for (Ultimate u : Ultimate.values()) all.add("ultimate: " + u.displayName());
        assertClean(all);
    }

    @Test
    void sensitiveCosmeticsAreWithheldFromSale() {
        int withheld = 0;
        for (Cosmetic c : CosmeticCatalog.ALL) {
            boolean sensitive = BLOCKED.stream().anyMatch(b -> c.name().contains(b))
                    || List.of("tag.naadam", "tag.tsagaan_sar", "tag.chingis").contains(c.id());
            if (!sensitive) continue;
            withheld++;
            assertTrue(c.withheld(), c.id() + " must be withheld");
            assertTrue(!c.sold(), c.id() + " is not sold for coins");
            assertEquals(0, c.creditPrice(), c.id() + " is not sold for credits");
        }
        assertEquals(6, withheld, "naadam, tsagaan_sar, khar_suld, tsagaan_suld, altan_urag, chingis");
    }
}
