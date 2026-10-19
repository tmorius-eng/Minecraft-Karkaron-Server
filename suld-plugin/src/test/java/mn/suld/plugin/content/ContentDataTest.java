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

    private static ContentLoader.Result loadWithPoints(SkillTreeLoader.Source src) {
        return ContentLoader.load(src, id -> CATALOG.lootTable(id).isPresent(), id -> CATALOG.item(id).isPresent(), new ArrayList<>(), CityPoints.ids()::contains);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> npc(Map<String, Object> root, String id) {
        return list(root, "npcs").stream().filter(n -> id.equals(n.get("id"))).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------------------------------ npcs.json

    /** content/npcs.json (2026-10-19): every person stands on a city point (or is a campaign's dynamic role) and wears a shipped skin. */
    @Test
    @SuppressWarnings("unchecked")
    void everyNpcStandsOnARealPointAndWearsARealSkin() throws Exception {
        ContentLoader.Result r = loadWithPoints(ContentLoader.classpath());
        assertTrue(r.ok(), () -> r.issues().toString());
        java.util.Set<String> dynamic = new java.util.HashSet<>(); // world-effect roles: never on a city point
        for (var n : list((Map<String, Object>) Json.parse(ContentLoader.classpath().read("npcs.json")), "npcs")) {
            if (Boolean.TRUE.equals(n.get("dynamic"))) dynamic.add((String) n.get("id"));
        }
        for (var c : r.pack().campaigns()) {
            if (c.effect() != null && c.effect().type() == mn.suld.api.campaign.WorldEffect.Type.NPC) assertTrue(dynamic.contains(c.effect().ref()), c.id() + " names a role that is not a dynamic NPC: " + c.effect().ref());
        }
        assertTrue(dynamic.contains("steward.campaign"), "the dev campaigns' steward stays a dynamic role");
        assertTrue(CityPoints.ids().size() >= 30, "the city points are readable from the repository: " + CityPoints.ids().size());
        for (var n : r.pack().npcs()) {
            assertTrue(CityPoints.ids().contains(n.id()) != dynamic.contains(n.id()), n.id() + " is neither a point nor a dynamic role (or both)");
            for (String pt : n.points()) assertTrue(CityPoints.ids().contains(pt), n.id() + " serves an unknown point " + pt);
            assertTrue(Files.isRegularFile(Path.of("../resourcepack/assets/suld/textures/entity/npc/" + n.skin() + ".png")), n.id() + ": no skin " + n.skin());
        }
        // every slice-1 point a player can talk to has its person: the old hard-coded roster, plus the eleven of 2026-10-19
        for (String pt : List.of("class_selection", "tutorial", "quest.first_hunt", "merchant.general", "merchant.food", "blacksmith", "fast_travel.plaza",
                "fast_travel.gate_north", "shrine.sky", "quest.ikh_boo", "merchant.gate_east")) {
            assertTrue(r.pack().npc(pt) != null, "nobody serves " + pt);
        }
    }

    @Test
    void anNpcOnAnUnknownPointIsRefused() throws Exception {
        ContentLoader.Result r = loadWithPoints(edited("npcs.json", root -> npc(root, "blacksmith").put("id", "nowhere.smith")));
        assertTrue(has(r, "npcs.json", "no point nowhere.smith"), () -> r.issues().toString());
    }

    @Test
    void aShopNpcWithoutAShopIsRefused() throws Exception {
        ContentLoader.Result r = load(edited("npcs.json", root -> npc(root, "merchant.general").remove("shop")));
        assertTrue(has(r, "npcs.json", "a SHOP action needs a shop"), () -> r.issues().toString());
        ContentLoader.Result r2 = load(edited("npcs.json", root -> npc(root, "merchant.general").put("shop", "shop.nowhere")));
        assertTrue(has(r2, "npcs.json", "no shop shop.nowhere"), () -> r2.issues().toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aHistoricalClaimOnAnUnverifiedLineIsRefused() throws Exception {
        ContentLoader.Result r = load(edited("npcs.json", root -> {
            Map<String, Object> topic = (Map<String, Object>) ((List<Object>) npc(root, "tutorial").get("topics")).get(0);
            Map<String, Object> para = (Map<String, Object>) ((List<Object>) topic.get("paragraphs")).get(0); // the 1235 line says «түүхэн баримт»
            para.put("history", "FICTION");
        }));
        assertTrue(has(r, "npcs.json", "only a VERIFIED line may say"), () -> r.issues().toString());
    }

    @Test
    void everyCampaignNpcRoleHasAnEntry() throws Exception {
        ContentLoader.Result r = load(edited("npcs.json", root -> list(root, "npcs").removeIf(n -> "merchant.caravan".equals(n.get("id")))));
        assertTrue(has(r, "campaigns.json", "no npc merchant.caravan in npcs.json"), () -> r.issues().toString());
    }

    @Test
    void anNpcMayServeAPointOnlyOnce() throws Exception {
        ContentLoader.Result r = load(edited("npcs.json", root -> npc(root, "blacksmith").put("points", List.of("merchant.food"))));
        assertTrue(has(r, "npcs.json", "served twice"), () -> r.issues().toString());
    }

    // ----------------------------------------------------------------------------------------- shops.json

    @SuppressWarnings("unchecked")
    private static Map<String, Object> shop(Map<String, Object> root, String id) {
        return list(root, "shops").stream().filter(n -> id.equals(n.get("id"))).findFirst().orElseThrow();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aShopWithAnUnknownMaterialIsRefused() throws Exception {
        ContentLoader.Result r = load(edited("shops.json", root -> ((Map<String, Object>) ((List<Object>) shop(root, "shop.general").get("stock")).get(0)).put("material", "NO_SUCH_THING")));
        assertTrue(has(r, "shops.json", "no such material NO_SUCH_THING"), () -> r.issues().toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aShopEntryNeedsExactlyOneOfMaterialOrItem() throws Exception {
        ContentLoader.Result both = load(edited("shops.json", root -> ((Map<String, Object>) ((List<Object>) shop(root, "shop.general").get("stock")).get(0)).put("item", "item.chonon_arisan")));
        assertTrue(has(both, "shops.json", "material or item: exactly one"), () -> both.issues().toString());
        ContentLoader.Result neither = load(edited("shops.json", root -> ((Map<String, Object>) ((List<Object>) shop(root, "shop.general").get("stock")).get(0)).remove("material")));
        assertTrue(has(neither, "shops.json", "material or item: exactly one"), () -> neither.issues().toString());
        ContentLoader.Result unknown = load(edited("shops.json", root -> {
            Map<String, Object> e = (Map<String, Object>) ((List<Object>) shop(root, "shop.general").get("stock")).get(0);
            e.remove("material");
            e.put("item", "item.no_such");
        }));
        assertTrue(has(unknown, "shops.json", "no item item.no_such"), () -> unknown.issues().toString());
    }

    @Test
    void aShopWithNoStockIsRefused() throws Exception {
        ContentLoader.Result r = load(edited("shops.json", root -> shop(root, "shop.grain").put("stock", new ArrayList<>())));
        assertTrue(has(r, "shops.json", "a shop needs stock"), () -> r.issues().toString());
    }

    /** The general shop is the nine supplies the hub sold before shops were data (2026-10-19): /shop looks the same. */
    @Test
    void theGeneralShopKeepsItsNineSupplies() {
        var shop = Content.pack().shop("shop.general");
        assertTrue(shop != null, "shop.general");
        assertEquals(List.of("BREAD", "COOKED_MUTTON", "COOKED_BEEF", "TORCH", "SHIELD", "GOLDEN_CARROT", "GOLDEN_APPLE", "SADDLE", "LEAD"),
                shop.stock().stream().map(mn.suld.api.shop.ShopEntry::material).toList());
        assertEquals(List.of(20L, 40L, 45L, 12L, 120L, 90L, 300L, 250L, 40L), shop.stock().stream().map(mn.suld.api.shop.ShopEntry::price).toList());
        // the four gate markets of Rubruck's account, one per gate
        for (String id : List.of("shop.grain", "shop.sheep", "shop.cattle", "shop.horses")) assertTrue(Content.pack().shop(id) != null, id);
        for (String pt : List.of("merchant.gate_east", "merchant.gate_west", "merchant.gate_south", "merchant.gate_north")) {
            assertTrue(Content.pack().npc(pt) != null && Content.pack().npc(pt).shopId() != null, pt + " sells");
        }
    }

    private static boolean has(ContentLoader.Result r, String file, String text) {
        return r.issues().stream().anyMatch(i -> i.file().equals(file) && (i.path() + " " + i.message()).contains(text));
    }

    @Test
    void everyRegionOwnsItsDiscoveryBitWhereverItStandsInTheList() throws Exception {
        // the bits a player's style row already holds were the list positions: the numbers keep them there
        List<String> order = List.of("region.kharkhorum", "region.kherlen", "region.gobi", "region.khangai", "region.altai",
                "region.khentii", "region.zuungar", "region.otgon", "region.khuvsgul");
        ContentPack bundled = load(ContentLoader.classpath()).pack();
        for (int i = 0; i < order.size(); i++) assertEquals(i, bundled.regionBit(order.get(i)), order.get(i));
        // moving a region to the end of the list moves no discovery (map inspection 2026-10-13: it used to)
        ContentLoader.Result moved = load(edited("world.json", root -> {
            List<Map<String, Object>> regions = list(root, "regions");
            regions.add(regions.remove(1));
        }));
        assertTrue(moved.issues().isEmpty(), () -> moved.issues().toString());
        assertEquals(1, moved.pack().regionBit("region.kherlen"));
        assertEquals(2, moved.pack().regionBit("region.gobi"));
        ContentLoader.Result twice = load(edited("world.json", root -> list(root, "regions").get(2).put("bit", 1)));
        assertTrue(has(twice, "world.json", "bit 1 is used twice"), () -> twice.issues().toString());
        ContentLoader.Result areaBit = load(edited("world.json", root -> list(root, "regions").get(2).put("bit", 16)));
        assertTrue(has(areaBit, "world.json", "16 is outside 0..15"), () -> areaBit.issues().toString());
    }

    @Test
    void aChapterOnAMobOfSeveralRegionsSaysWhereItIsPlayed() throws Exception {
        // Act II's mobs roam all four outer regions: without the chapter's region the quest marker pointed at the first
        // region listing the mob (Хэнтий) for every one of them (map inspection 2026-10-13)
        ContentPack bundled = load(ContentLoader.classpath()).pack();
        assertEquals("region.zuungar", bundled.lore().get("quest.tangut_ghosts").region());
        assertEquals("region.otgon", bundled.lore().get("quest.sky_soldiers").region());
        assertEquals("region.khuvsgul", bundled.lore().get("quest.water_lus").region());
        // WORLD-01: the Tangut ghosts now haunt Зүүнгар alone; the water spirit still lives in three outer regions
        ContentLoader.Result missing = load(edited("quests.json", root -> list(root, "story").stream()
                .filter(c -> "quest.water_lus".equals(c.get("id"))).forEach(c -> c.remove("region"))));
        assertTrue(has(missing, "quests.json", "lives in several regions"), () -> missing.issues().toString());
        ContentLoader.Result wrong = load(edited("quests.json", root -> list(root, "story").stream()
                .filter(c -> "quest.tangut_ghosts".equals(c.get("id"))).forEach(c -> c.put("region", "region.nowhere"))));
        assertTrue(has(wrong, "quests.json", "no region region.nowhere"), () -> wrong.issues().toString());
    }

    @Test
    void theBundledContentIsValidAndEveryLootTableExists() {
        ContentLoader.Result r = load(ContentLoader.classpath());
        assertTrue(r.ok(), () -> r.issues().stream().map(Issue::toString).reduce("", (a, b) -> a + "\n" + b));
        ContentPack p = r.pack();
        assertEquals(39, p.mobs().size());
        assertEquals(9, p.regions().size());
        assertEquals(40, p.areas().size());
        assertEquals(10, p.dungeons().size());
        assertEquals(10, p.sites().size());
        assertEquals(42, p.chapters().size(), "Act I (18) + Act II (24)");
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
    void aDungeonChapterBeforeItsGateIsRefused() throws Exception {
        // PROG-004: moving "clear Саарлын Агуй" to the front of the story would ask for a dungeon that is still closed
        ContentLoader.Result r = load(edited("quests.json", root -> {
            List<Map<String, Object>> story = list(root, "story");
            Map<String, Object> den = story.stream().filter(q -> "dungeon.khasar_den".equals(q.get("target"))).findFirst().orElseThrow();
            story.remove(den);
            story.add(0, den);
        }));
        assertTrue(has(r, "quests.json", "which opens only after"), r.issues()::toString);
    }

    @Test
    void chapterExpNeverCollapsesWhenTheStoryReturnsToEarlyLands() {
        // PROG-005: a chapter is priced at least at the chain's level, so Act II's discovery chapters keep pace
        var chapters = Content.pack().chapters();
        var curve = mn.suld.api.balance.Balance.curve();
        for (int i = 1; i < chapters.size(); i++) {
            long prev = chapters.get(i - 1).expReward(), cur = chapters.get(i).expReward();
            assertTrue(cur * 4 >= prev, chapters.get(i).id() + " pays " + cur + " after " + prev);
        }
        assertTrue(chapters.get(chapters.size() - 1).expReward() >= mn.suld.api.balance.Rewards.chapterExp(curve, 40));
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyNpcEffectStandsOnARealCityPoint() throws Exception {
        // a campaign NPC at a point the city does not have is never placed (the caravan once pointed at merchant.south)
        Map<String, Object> pts = (Map<String, Object>) Json.parse(java.nio.file.Files.readString(
                java.nio.file.Path.of("../assets/world/kharkhorum/points.json")));
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Object o : (List<Object>) pts.get("points")) ids.add((String) ((Map<String, Object>) o).get("id"));
        for (var c : Content.pack().campaigns()) {
            var e = c.effect();
            if (e == null || e.type() != mn.suld.api.campaign.WorldEffect.Type.NPC || e.anchor().startsWith("city:")) continue;
            assertTrue(ids.contains(e.anchor()), c.id() + ": NPC anchor " + e.anchor() + " is not a point in points.json");
        }
    }

    @Test
    void everyDungeonLiesInARegionOfItsLevel() {
        // DATA-002: the tracker sends players to the dungeon's region; it must be where the gate is, at a fitting level
        StringBuilder map = new StringBuilder();
        for (var d : Content.pack().dungeons()) {
            String id = DungeonContent.regionOf(d.id());
            var r = WorldContent.REGIONS.stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
            map.append(d.id()).append(" -> ").append(id).append("; ");
            assertTrue(r.minLevel() <= d.maxLevel() && d.minLevel() <= r.maxLevel(),
                    d.id() + " (" + d.minLevel() + "-" + d.maxLevel() + ") is placed in " + id + " (" + r.minLevel() + "-" + r.maxLevel() + ")");
        }
        System.out.println("[DATA-002] " + map);
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
    void worldBossesAreCheckedAgainstMobsSitesAndLoot() throws Exception {
        ContentLoader.Result ok = load(ContentLoader.classpath());
        assertEquals(2, ok.pack().worldBosses().size());
        assertEquals("mob.khuvsgul_ikh_baavgai", ok.pack().worldBoss("worldboss.ikh_baavgai").mobId());
        assertTrue(ok.pack().mobsOf("boss").stream().noneMatch(m -> m.id().contains("ikh_")), "a world boss is no dungeon boss");
        ContentLoader.Result r = load(edited("events.json", root -> {
            list(root, "worldBosses").get(0).put("mob", "mob.goviin_chono");
            list(root, "worldBosses").get(1).put("site", "site.nowhere");
        }));
        assertTrue(has(r, "events.json", "worldBosses[0].mob mob.goviin_chono must have tier WORLD_BOSS"), r.issues()::toString);
        assertTrue(has(r, "events.json", "worldBosses[1].site no site site.nowhere in sites.json"), r.issues()::toString);
        ContentLoader.Result none = load(edited("events.json", root -> root.remove("worldBosses")));
        assertTrue(none.ok(), "world bosses are optional: " + none.issues());
        assertTrue(none.pack().worldBosses().isEmpty());
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
        assertEquals(42, r.pack().chapters().size(), "the other files are the bundled ones");
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

    /** Every ladder dungeon's chapter comes right after its chapter gate, so no rung can lock its own chapter. */
    @Test
    void dungeonChaptersSitRightAfterTheirLadderGates() {
        var chapters = Content.bundled().chapters();
        for (var rung : mn.suld.api.balance.DungeonLadder.RUNGS) {
            int at = -1;
            for (int i = 0; i < chapters.size(); i++) {
                if (chapters.get(i).type() == mn.suld.api.quest.QuestType.COMPLETE_DUNGEON && chapters.get(i).targetId().equals(rung.id())) at = i;
            }
            if (at < 0) {
                assertEquals("dungeon.tengeriin_ordon", rung.id(), rung.id() + " has no chapter (only the raid after the story may)");
                assertEquals(chapters.size() - 1, rung.chapterGate(), "the raid opens when the whole story is done");
                continue;
            }
            assertEquals(rung.chapterGate() + 1, at, rung.id() + ": its chapter must come right after its gate (chapters done = gate + 1)");
        }
    }
}
