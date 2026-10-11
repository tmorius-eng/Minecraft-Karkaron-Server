package mn.suld.plugin.content;

import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.json.Json;
import mn.suld.api.skill.tree.SkillTreeLoader;
import mn.suld.api.skill.tree.SkillTreeLoader.Issue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** The content files (docs/CONTENT_DATA.md): the bundled set is valid, a broken edit is caught, an addition needs no code. */
class ContentDataTest {

    private static final ItemCatalog CATALOG = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();

    private static ContentLoader.Result load(SkillTreeLoader.Source src) {
        return ContentLoader.load(src, id -> CATALOG.lootTable(id).isPresent(), id -> CATALOG.item(id).isPresent(), new ArrayList<>());
    }

    /** The bundled files with one of them edited as parsed JSON. */
    @SuppressWarnings("unchecked")
    private static SkillTreeLoader.Source edited(String file, Consumer<Map<String, Object>> edit) throws Exception {
        Map<String, String> texts = new HashMap<>();
        for (String f : ContentLoader.FILES) texts.put(f, ContentLoader.classpath().read(f));
        Map<String, Object> root = (Map<String, Object>) Json.parse(texts.get(file));
        edit.accept(root);
        texts.put(file, Json.write(root));
        return texts::get;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> root, String key) {
        return (List<Map<String, Object>>) root.get(key);
    }

    private static boolean has(ContentLoader.Result r, String file, String text) {
        return r.issues().stream().anyMatch(i -> i.file().equals(file) && (i.path() + " " + i.message()).contains(text));
    }

    @Test
    void theBundledContentIsValidAndEveryLootTableExists() {
        ContentLoader.Result r = load(ContentLoader.classpath());
        assertTrue(r.ok(), () -> r.issues().stream().map(Issue::toString).reduce("", (a, b) -> a + "\n" + b));
        ContentPack p = r.pack();
        assertEquals(32, p.mobs().size());
        assertEquals(9, p.regions().size());
        assertEquals(40, p.areas().size());
        assertEquals(10, p.dungeons().size());
        assertEquals(10, p.sites().size());
        assertEquals(18, p.chapters().size());
        assertEquals("khasar", p.models().get("mob.khasar"));
        assertEquals("dungeon.khasar_den", p.dungeons().get(0).id(), "the ladder starts at Хасарын Агуй");
    }

    @Test
    void theContentClassesAreViewsOfTheFiles() {
        ContentPack p = Content.pack();
        assertEquals(p.mob("mob.deeremchin"), WorldContent.BANDIT);
        assertEquals(p.dungeons(), DungeonContent.ALL);
        assertEquals(p.chapters(), QuestContent.STORY.chapters());
        assertEquals(p.region("region.gobi"), WorldContent.GOBI);
        assertTrue(WorldContent.outer(WorldContent.KHENTII));
        assertFalse(WorldContent.outer(WorldContent.GOBI));
        assertEquals("event.chonyn_dovtolgoo", SuldContent.worldEventFor("wolf_raid").id(), "the alias still works");
    }

    @Test
    void chapterExpComesFromTheRulesUnlessTheFileSetsIt() throws Exception {
        var curve = mn.suld.api.balance.Balance.curve();
        var first = Content.pack().chapters().get(0);
        assertEquals(mn.suld.api.balance.Rewards.chapterExp(curve, 2), first.expReward(), "kill chapter: the target mob's level (2)");
        ContentLoader.Result r = load(edited("quests.json", root -> list(root, "story").get(0).put("exp", 1234)));
        assertTrue(r.ok(), r.issues()::toString);
        assertEquals(1234, r.pack().chapters().get(0).expReward());
    }

    @Test
    void aWaveWithAnUnknownMobIsRefused() throws Exception {
        ContentLoader.Result r = load(edited("dungeons.json", root -> {
            @SuppressWarnings("unchecked") List<List<Object>> waves = (List<List<Object>>) list(root, "dungeons").get(1).get("waves");
            waves.get(0).set(0, "mob.no_such_mob");
        }));
        assertFalse(r.ok());
        assertTrue(has(r, "dungeons.json", "dungeons[1].waves[0][0] no mob mob.no_such_mob"), r.issues()::toString);
        assertNull(r.pack(), "a pack with problems is never used");
    }

    @Test
    void removingAMobTheCodeUsesIsRefused() throws Exception {
        ContentLoader.Result r = load(edited("mobs.json", root -> list(root, "mobs").removeIf(m -> "mob.deeremchin".equals(m.get("id")))));
        assertTrue(has(r, "mobs.json", "mob.deeremchin required by the plugin's code"), r.issues()::toString);
        assertTrue(has(r, "world.json", "no mob mob.deeremchin"), "the region that spawns it is reported too");
        assertTrue(has(r, "quests.json", "no mob mob.deeremchin"), "and the chapter that hunts it");
    }

    @Test
    void typosAreReportedWithTheirPath() throws Exception {
        ContentLoader.Result r = load(edited("mobs.json", root -> {
            list(root, "mobs").get(0).put("tier", "LEGEND");
            list(root, "mobs").get(1).put("host", "wolf");
            list(root, "mobs").get(2).put("loot", "loot.nothing_here");
        }));
        assertTrue(has(r, "mobs.json", "mobs[0].tier unknown value 'LEGEND'"), r.issues()::toString);
        assertTrue(has(r, "mobs.json", "mobs[1].host"), r.issues()::toString);
        assertTrue(has(r, "mobs.json", "mobs[2].loot no loot table loot.nothing_here"), r.issues()::toString);
        ContentLoader.Result areas = load(edited("world.json", root -> list(root, "areas").get(1).put("index", 16)));
        assertTrue(has(areas, "world.json", "areas[1].index index 16 is used twice"), areas.issues()::toString);
        ContentLoader.Result json = load(name -> name.equals("quests.json") ? "{ \"format\": 1, \"story\": [ " : ContentLoader.classpath().read(name));
        assertTrue(has(json, "quests.json", "not valid JSON"), json.issues()::toString);
    }

    @Test
    void aNewDungeonNeedsNoCode() throws Exception {
        ContentLoader.Result r = load(edited("dungeons.json", root -> {
            Map<String, Object> copy = new java.util.LinkedHashMap<>(list(root, "dungeons").get(0));
            copy.put("id", "dungeon.test_khoton");
            copy.put("name", "Туршилтын Хотон");
            copy.put("site", Map.of("theme", "DEN", "bearing", 120, "radius", 900));
            list(root, "dungeons").add(copy);
        }));
        assertTrue(r.ok(), r.issues()::toString);
        assertEquals(11, r.pack().dungeons().size());
        assertEquals("dungeon.test_khoton", r.pack().dungeons().get(10).id(), "it joins the end of the ladder");
        assertEquals(11, r.pack().sites().size(), "and gets its gate");
    }

    @Test
    void theServersFileReplacesOnlyThatFile(@TempDir Path dir) throws Exception {
        String mobs = ContentLoader.classpath().read("mobs.json").replace("\"Говийн Чоно\"", "\"Говийн Саарал Чоно\"");
        Files.writeString(dir.resolve("mobs.json"), mobs);
        List<String> overridden = new ArrayList<>();
        ContentLoader.Result r = ContentLoader.load(ContentLoader.serverOrBundled(dir, overridden), null, null, overridden);
        assertTrue(r.ok(), r.issues()::toString);
        assertEquals(List.of("mobs.json"), overridden);
        assertEquals("Говийн Саарал Чоно", r.pack().mob("mob.goviin_chono").displayName());
        assertEquals(18, r.pack().chapters().size(), "the other files are the bundled ones");
    }

    @Test
    void aBrokenServerFileFallsBackToTheBundledContent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("world.json"), "{ broken");
        java.util.logging.Logger log = java.util.logging.Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        List<Issue> issues = Content.install(dir, log);
        assertFalse(issues.isEmpty());
        assertEquals(9, Content.pack().regions().size(), "the bundled regions run instead");
    }

    /** The historic sites stand in their areas, clear of the ovoo, the dungeon gates, each other and the city. */
    @Test
    void historicSitesHaveRoomOfTheirOwn() {
        ContentPack p = Content.bundled();
        assertEquals(16, p.historicSites().size());
        List<double[]> others = new ArrayList<>();
        for (var a : p.areas()) { // the ovoo of each area (OvooService: the middle bearing, half way out, border 5000)
            double span = ((a.toDeg() - a.fromDeg()) % 360 + 360) % 360;
            double mid = Math.toRadians(a.fromDeg() + (span == 0 ? 360 : span) / 2);
            double r = (a.minRadius() + Math.min(a.maxRadius(), 5000 - 64)) / 2;
            others.add(new double[]{Math.sin(mid) * r, -Math.cos(mid) * r});
        }
        for (var g : p.sites()) others.add(new double[]{g.offset()[0], g.offset()[1]});
        List<double[]> placed = new ArrayList<>();
        for (var s : p.historicSites()) {
            int[] o = s.offset();
            assertTrue(Math.hypot(o[0], o[1]) >= 200, s.id() + " is inside the city");
            for (double[] q : others) assertTrue(Math.hypot(o[0] - q[0], o[1] - q[1]) >= 100, s.id() + " is too close to an ovoo or a gate");
            for (double[] q : placed) assertTrue(Math.hypot(o[0] - q[0], o[1] - q[1]) >= 150, s.id() + " is too close to another site");
            placed.add(new double[]{o[0], o[1]});
            assertTrue(p.areas().stream().anyMatch(a -> a.id().equals(s.areaId()) && a.contains(o[0], o[1])), s.id() + " outside its area");
            assertFalse(s.description().isBlank(), s.id());
        }
    }

    @Test
    void aSiteOutsideItsAreaIsRefused() throws Exception {
        ContentLoader.Result r = load(edited("sites.json", root -> list(root, "sites").get(0).put("bearing", 300)));
        assertTrue(has(r, "sites.json", "is outside area.kherlen"), r.issues()::toString);
        ContentLoader.Result k = load(edited("sites.json", root -> list(root, "sites").get(1).put("kind", "CASTLE")));
        assertTrue(has(k, "sites.json", "sites[1].kind unknown value 'CASTLE'"), k.issues()::toString);
    }
}
