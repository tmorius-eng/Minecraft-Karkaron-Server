package mn.suld.plugin.content;

import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.dungeon.BossPhase;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.hall.DungeonSite;
import mn.suld.api.dungeon.hall.HallTheme;
import mn.suld.api.json.Json;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestType;
import mn.suld.api.region.Area;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.region.RegionShape;
import mn.suld.api.relic.RelicDefinition;
import mn.suld.api.skill.tree.SkillTreeLoader;
import mn.suld.api.skill.tree.SkillTreeLoader.Issue;
import mn.suld.api.worldevent.WorldEventDefinition;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static mn.suld.api.skill.tree.SkillTreeLoader.intIn;
import static mn.suld.api.skill.tree.SkillTreeLoader.str;

/**
 * Reads and checks the content files (docs/CONTENT_DATA.md). Every problem is reported with its file and path; a pack
 * with any problem is not used. Cross-references are checked (a wave's mobs, a region's mobs, a quest's target, a
 * boss), and so are the ids the plugin's code relies on by name ({@link #REQUIRED}).
 */
public final class ContentLoader {

    public static final int FORMAT_VERSION = 1;
    public static final List<String> FILES = List.of("mobs.json", "world.json", "dungeons.json", "quests.json", "events.json");

    /** Ids the code names directly (tutorial, first dungeon, boss brains, story constants): they must exist. */
    public static final Set<String> REQUIRED = Set.of(
            "mob.goviin_chono", "mob.orkhon_chono", "mob.khasar",
            "mob.deeremchin", "mob.goviin_khilents", "mob.elsnii_suns", "mob.saaral_chono", "mob.khangai_baavgai",
            "mob.altai_mosun_suns", "mob.altai_avarga", "mob.altai_tsasan_chono", "mob.tengeriin_kharuul",
            "mob.elsnii_khaan", "mob.oin_ezen", "mob.mosun_khaan",
            "mob.usny_lus", "mob.dalain_chono", "mob.lusyn_khaan", "mob.tangud_suns", "mob.balgasny_khilents", "mob.khar_janjin",
            "mob.khureenii_kharuul", "mob.dainy_chono", "mob.ulaan_khadny_noyon", "mob.uulyn_savdag", "mob.aguin_baavgai",
            "mob.khangai_savdag", "mob.tengeriin_tsereg", "mob.tengeriin_chono", "mob.khukh_tengeriin_elch", "mob.ordny_sakhiul",
            "mob.khukh_suldiin_sakhiul",
            "region.kharkhorum", "region.kherlen", "region.gobi", "region.khangai", "region.altai",
            "region.khentii", "region.zuungar", "region.otgon", "region.khuvsgul",
            "dungeon.khasar_den", "dungeon.govi_bulsh", "dungeon.baavgain_uur", "dungeon.mosun_orgil", "dungeon.dalain_gun",
            "dungeon.khar_khot", "dungeon.ulaan_khad", "dungeon.burkhan_agui", "dungeon.tengeriin_shat", "dungeon.tengeriin_ordon",
            "quest.first_hunt", "event.chonyn_dovtolgoo", "relic.khukh_suld", "relic.altan_gerege");

    private static final Pattern MOB_ID = Pattern.compile("mob\\.[a-z0-9_]{2,40}");
    private static final Pattern DUNGEON_ID = Pattern.compile("dungeon\\.[a-z0-9_]{2,40}");
    private static final Pattern QUEST_ID = Pattern.compile("quest\\.[a-z0-9_]{2,40}");
    private static final Pattern AREA_ID = Pattern.compile("area\\.[a-z0-9_]{2,40}");
    private static final Pattern HOST = Pattern.compile("[A-Z][A-Z_]{1,40}");
    private static final Set<String> ROLES = Set.of("core", "wild", "ladder", "boss");
    private static final Set<String> HISTORY = Set.of("VERIFIED", "INSPIRED", "FICTION");

    private ContentLoader() {
    }

    public record Result(ContentPack pack, List<Issue> issues, List<String> overridden) {
        public boolean ok() {
            return issues.isEmpty() && pack != null;
        }
    }

    /** The bundled files in the jar ({@code /content/}). */
    public static SkillTreeLoader.Source classpath() {
        return name -> {
            try (InputStream in = ContentLoader.class.getResourceAsStream("/content/" + name)) {
                if (in == null) throw new NoSuchFileException("/content/" + name);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        };
    }

    /** The server's copy of a file where it has one ({@code plugins/SULD/content/<file>}), else the bundled one. */
    public static SkillTreeLoader.Source serverOrBundled(Path dir, List<String> overridden) {
        SkillTreeLoader.Source bundled = classpath();
        return name -> {
            Path f = dir.resolve(name);
            if (Files.isRegularFile(f)) {
                overridden.add(name);
                return Files.readString(f, StandardCharsets.UTF_8);
            }
            return bundled.read(name);
        };
    }

    public static Result load(SkillTreeLoader.Source src) {
        return load(src, null, null, List.of());
    }

    /**
     * @param lootExists checks loot table ids against the item catalog (null: not checked)
     * @param itemExists checks the COLLECT_ITEM quests' items (null: not checked)
     */
    public static Result load(SkillTreeLoader.Source src, Predicate<String> lootExists, Predicate<String> itemExists, List<String> overridden) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Map<String, Object>> files = new LinkedHashMap<>();
        for (String f : FILES) {
            try {
                Map<String, Object> root = Json.object(Json.parse(src.read(f)));
                int v = intIn(f, "", root, "format", 1, 99, true, 0, issues);
                if (v != FORMAT_VERSION && v != 0) issues.add(new Issue(f, "format", "format " + v + " is not supported (expected " + FORMAT_VERSION + ")"));
                files.put(f, root);
            } catch (NoSuchFileException e) {
                issues.add(new Issue(f, "", "file missing"));
            } catch (Exception e) {
                issues.add(new Issue(f, "", "not valid JSON: " + e.getMessage()));
            }
        }
        if (files.size() < FILES.size()) return new Result(null, issues, overridden);

        // ------------------------------------------------------------------------------------------------ mobs
        String f = "mobs.json";
        Map<String, MobDefinition> mobs = new LinkedHashMap<>();
        Map<String, String> roles = new HashMap<>();
        Map<String, String> models = new HashMap<>();
        List<Object> mobList = list(f, "", files.get(f), "mobs", issues);
        for (int i = 0; i < mobList.size(); i++) {
            String at = "mobs[" + i + "]";
            Map<String, Object> m = obj(f, at, mobList.get(i), issues);
            if (m == null) continue;
            String id = str(f, at, m, "id", true, issues);
            String name = str(f, at, m, "name", true, issues);
            String host = str(f, at, m, "host", true, issues);
            MobTier tier = SkillTreeLoader.enumOf(MobTier.class, str(f, at, m, "tier", true, issues), f, at + ".tier", issues);
            int level = intIn(f, at, m, "level", 1, 100, true, 1, issues);
            String loot = str(f, at, m, "loot", true, issues);
            String role = str(f, at, m, "role", false, issues);
            String model = str(f, at, m, "model", false, issues);
            if (id == null || name == null || host == null || tier == null || loot == null) continue;
            if (!MOB_ID.matcher(id).matches()) issues.add(new Issue(f, at + ".id", "must look like mob.some_name"));
            if (mobs.containsKey(id)) issues.add(new Issue(f, at + ".id", "duplicate id " + id));
            if (!HOST.matcher(host).matches() || !livingHost(host)) issues.add(new Issue(f, at + ".host", "'" + host + "' is not a living entity type (WOLF, HUSK, PILLAGER ...)"));
            if (role != null && !ROLES.contains(role)) issues.add(new Issue(f, at + ".role", "one of " + ROLES));
            if (lootExists != null && !lootExists.test(loot)) issues.add(new Issue(f, at + ".loot", "no loot table " + loot + " in the item catalog"));
            MobDefinition def = MobDefinition.designed(id, name, host, tier, level, loot);
            // a hand-set number overrides the level's (rarely needed: the curve is tuned by the simulator)
            if (m.containsKey("health") || m.containsKey("attack") || m.containsKey("exp")) {
                double hp = m.containsKey("health") ? SkillTreeLoader.num(f, at, m, "health", 1, 1e7, issues) : def.baseHealth();
                double atk = m.containsKey("attack") ? SkillTreeLoader.num(f, at, m, "attack", 0, 1e6, issues) : def.baseAttack();
                long exp = m.containsKey("exp") ? Math.round(SkillTreeLoader.num(f, at, m, "exp", 0, 1e9, issues)) : def.baseExp();
                def = new MobDefinition(id, name, host, tier, level, hp, atk, exp, loot);
            }
            mobs.put(id, def);
            roles.put(id, role == null ? "wild" : role);
            if (model != null) models.put(id, model);
        }

        // ----------------------------------------------------------------------------------------------- world
        f = "world.json";
        Map<String, Object> world = files.get(f);
        double inner = SkillTreeLoader.num(f, "", world, "innerEdge", 100, 100_000, issues);
        double edge = SkillTreeLoader.num(f, "", world, "worldEdge", 100, 100_000, issues);
        if (inner >= edge) issues.add(new Issue(f, "innerEdge", "must be less than worldEdge"));
        List<RegionDefinition> regions = new ArrayList<>();
        Set<String> outer = new HashSet<>();
        List<Object> regionList = list(f, "", world, "regions", issues);
        for (int i = 0; i < regionList.size(); i++) {
            String at = "regions[" + i + "]";
            Map<String, Object> r = obj(f, at, regionList.get(i), issues);
            if (r == null) continue;
            String id = str(f, at, r, "id", true, issues);
            RegionShape shape = shape(f, at + ".shape", r.get("shape"), issues);
            List<String> ids = strings(f, at, r, "mobs", issues);
            for (int k = 0; k < ids.size(); k++) {
                if (!mobs.containsKey(ids.get(k))) issues.add(new Issue(f, at + ".mobs[" + k + "]", "no mob " + ids.get(k) + " in mobs.json"));
            }
            if (id == null || shape == null) continue;
            if (regions.stream().anyMatch(x -> x.id().equals(id))) issues.add(new Issue(f, at + ".id", "duplicate id " + id));
            try {
                regions.add(new RegionDefinition(id, str(f, at, r, "name", true, issues), r.get("description") instanceof String desc ? desc : "", shape, intIn(f, at, r, "priority", 0, 1000, true, 10, issues),
                        intIn(f, at, r, "minLevel", 1, 100, true, 1, issues), intIn(f, at, r, "maxLevel", 1, 100, true, 1, issues),
                        SkillTreeLoader.bool(r, "safeZone"), SkillTreeLoader.bool(r, "buildProtected"), ids,
                        intIn(f, at, r, "discoveryExp", 0, 1_000_000, false, 0, issues)));
                if (SkillTreeLoader.bool(r, "outer")) outer.add(id);
            } catch (IllegalArgumentException e) {
                issues.add(new Issue(f, at, e.getMessage()));
            }
        }
        List<Area> areas = new ArrayList<>();
        Set<Integer> bits = new HashSet<>();
        List<Object> areaList = list(f, "", world, "areas", issues);
        for (int i = 0; i < areaList.size(); i++) {
            String at = "areas[" + i + "]";
            Map<String, Object> a = obj(f, at, areaList.get(i), issues);
            if (a == null) continue;
            String id = str(f, at, a, "id", true, issues);
            String region = str(f, at, a, "region", true, issues);
            String history = str(f, at, a, "history", true, issues);
            int index = intIn(f, at, a, "index", 16, 63, true, 16, issues);
            if (id != null && !AREA_ID.matcher(id).matches()) issues.add(new Issue(f, at + ".id", "must look like area.some_name"));
            if (!bits.add(index)) issues.add(new Issue(f, at + ".index", "index " + index + " is used twice (each area owns one discovery bit 16..63)"));
            if (region != null && regions.stream().noneMatch(x -> x.id().equals(region))) issues.add(new Issue(f, at + ".region", "no region " + region));
            if (history != null && !HISTORY.contains(history)) issues.add(new Issue(f, at + ".history", "one of " + HISTORY));
            if (id == null || region == null) continue;
            try {
                areas.add(new Area(index, id, str(f, at, a, "name", true, issues), region,
                        SkillTreeLoader.num(f, at, a, "minRadius", 0, 100_000, issues), SkillTreeLoader.num(f, at, a, "maxRadius", 0, 100_000, issues),
                        SkillTreeLoader.num(f, at, a, "fromDeg", 0, 360, issues), SkillTreeLoader.num(f, at, a, "toDeg", 0, 360, issues),
                        intIn(f, at, a, "minLevel", 1, 100, true, 1, issues), intIn(f, at, a, "maxLevel", 1, 100, true, 1, issues),
                        intIn(f, at, a, "discoveryExp", 0, 1_000_000, false, 0, issues),
                        a.get("description") instanceof String s ? s : "", history));
            } catch (IllegalArgumentException e) {
                issues.add(new Issue(f, at, e.getMessage()));
            }
        }

        // -------------------------------------------------------------------------------------------- dungeons
        f = "dungeons.json";
        List<DungeonDefinition> dungeons = new ArrayList<>();
        List<DungeonSite> sites = new ArrayList<>();
        Map<String, DungeonContent.Completion> completions = new HashMap<>();
        Map<String, String> dRegion = new HashMap<>();
        Map<String, String> dWhere = new HashMap<>();
        List<Object> dungeonList = list(f, "", files.get(f), "dungeons", issues);
        for (int i = 0; i < dungeonList.size(); i++) {
            String at = "dungeons[" + i + "]";
            Map<String, Object> d = obj(f, at, dungeonList.get(i), issues);
            if (d == null) continue;
            String id = str(f, at, d, "id", true, issues);
            String name = str(f, at, d, "name", true, issues);
            String loot = str(f, at, d, "loot", true, issues);
            String region = str(f, at, d, "region", true, issues);
            int minParty = intIn(f, at, d, "minParty", 1, 10, true, 1, issues);
            int maxParty = intIn(f, at, d, "maxParty", 1, 10, true, 4, issues);
            if (minParty > maxParty) issues.add(new Issue(f, at + ".maxParty", "less than minParty"));
            if (id != null && !DUNGEON_ID.matcher(id).matches()) issues.add(new Issue(f, at + ".id", "must look like dungeon.some_name"));
            if (id != null && dungeons.stream().anyMatch(x -> x.id().equals(id))) issues.add(new Issue(f, at + ".id", "duplicate id " + id));
            if (region != null && regions.stream().noneMatch(x -> x.id().equals(region))) issues.add(new Issue(f, at + ".region", "no region " + region));
            if (loot != null && lootExists != null && !lootExists.test(loot)) issues.add(new Issue(f, at + ".loot", "no loot table " + loot + " in the item catalog"));
            List<List<String>> waves = new ArrayList<>();
            List<Object> waveList = list(f, at, d, "waves", issues);
            if (waveList.isEmpty()) issues.add(new Issue(f, at + ".waves", "a dungeon needs at least one wave"));
            for (int w = 0; w < waveList.size(); w++) {
                if (!(waveList.get(w) instanceof List<?> wave) || wave.isEmpty()) {
                    issues.add(new Issue(f, at + ".waves[" + w + "]", "must be a non-empty list of mob ids"));
                    continue;
                }
                List<String> ids = new ArrayList<>();
                for (int k = 0; k < wave.size(); k++) {
                    Object o = wave.get(k);
                    if (!(o instanceof String s) || !mobs.containsKey(s)) issues.add(new Issue(f, at + ".waves[" + w + "][" + k + "]", "no mob " + o + " in mobs.json"));
                    else ids.add(s);
                }
                waves.add(ids);
            }
            BossDefinition boss = null;
            Map<String, Object> b = obj(f, at + ".boss", d.get("boss"), issues);
            if (b != null) {
                String bossId = str(f, at + ".boss", b, "mob", true, issues);
                MobDefinition bossMob = bossId == null ? null : mobs.get(bossId);
                if (bossId != null && bossMob == null) issues.add(new Issue(f, at + ".boss.mob", "no mob " + bossId + " in mobs.json"));
                List<BossPhase> phases = new ArrayList<>();
                List<Object> phaseList = list(f, at + ".boss", b, "phases", issues);
                for (int k = 0; k < phaseList.size(); k++) {
                    String pat = at + ".boss.phases[" + k + "]";
                    Map<String, Object> ph = obj(f, pat, phaseList.get(k), issues);
                    if (ph == null) continue;
                    try {
                        phases.add(new BossPhase(SkillTreeLoader.num(f, pat, ph, "at", 0, 1, issues), SkillTreeLoader.num(f, pat, ph, "attack", 0.01, 100, issues),
                                str(f, pat, ph, "name", true, issues)));
                    } catch (IllegalArgumentException e) {
                        issues.add(new Issue(f, pat, e.getMessage()));
                    }
                }
                if (bossMob != null) {
                    try {
                        boss = new BossDefinition(bossMob, phases, intIn(f, at + ".boss", b, "enrageSeconds", 10, 3600, true, 180, issues));
                    } catch (IllegalArgumentException e) {
                        issues.add(new Issue(f, at + ".boss.phases", e.getMessage()));
                    }
                }
            }
            Map<String, Object> c = obj(f, at + ".completion", d.get("completion"), issues);
            if (c != null && id != null) completions.put(id, new DungeonContent.Completion(intIn(f, at + ".completion", c, "exp", 0, 10_000_000, true, 0, issues),
                    intIn(f, at + ".completion", c, "coins", 0, 10_000_000, true, 0, issues)));
            Map<String, Object> s = d.get("site") == null ? null : obj(f, at + ".site", d.get("site"), issues);
            if (s != null && id != null) {
                HallTheme theme = SkillTreeLoader.enumOf(HallTheme.class, str(f, at + ".site", s, "theme", true, issues), f, at + ".site.theme", issues);
                try {
                    if (theme != null) sites.add(new DungeonSite(id, theme, SkillTreeLoader.num(f, at + ".site", s, "bearing", 0, 360, issues),
                            SkillTreeLoader.num(f, at + ".site", s, "radius", 64, 4900, issues)));
                } catch (IllegalArgumentException e) {
                    issues.add(new Issue(f, at + ".site", e.getMessage()));
                }
            }
            if (id == null || name == null || boss == null) continue;
            if (region != null) dRegion.put(id, region);
            String where = str(f, at, d, "where", false, issues);
            if (where != null) dWhere.put(id, where);
            dungeons.add(new DungeonDefinition(id, name, intIn(f, at, d, "minLevel", 1, 100, true, 1, issues), minParty, maxParty, waves, boss, loot));
        }

        // ---------------------------------------------------------------------------------------------- quests
        f = "quests.json";
        List<QuestDefinition> chapters = new ArrayList<>();
        Map<String, QuestContent.Lore> lore = new HashMap<>();
        Map<String, String> story = new HashMap<>();
        List<Object> questList = list(f, "", files.get(f), "story", issues);
        if (questList.isEmpty()) issues.add(new Issue(f, "story", "the story needs at least one chapter"));
        var curve = mn.suld.api.balance.Balance.curve();
        for (int i = 0; i < questList.size(); i++) {
            String at = "story[" + i + "]";
            Map<String, Object> q = obj(f, at, questList.get(i), issues);
            if (q == null) continue;
            String id = str(f, at, q, "id", true, issues);
            QuestType type = SkillTreeLoader.enumOf(QuestType.class, str(f, at, q, "type", true, issues), f, at + ".type", issues);
            String target = q.get("target") instanceof String t ? t : "";
            int count = intIn(f, at, q, "count", 1, 1_000_000, true, 1, issues);
            if (id != null && !QUEST_ID.matcher(id).matches()) issues.add(new Issue(f, at + ".id", "must look like quest.some_name"));
            if (id != null && chapters.stream().anyMatch(x -> x.id().equals(id))) issues.add(new Issue(f, at + ".id", "duplicate id " + id));
            if (type == null || id == null) continue;
            String missing = switch (type) {
                case KILL_MOB -> mobs.containsKey(target) ? null : "no mob " + target + " in mobs.json";
                case COMPLETE_DUNGEON -> dungeons.stream().anyMatch(x -> x.id().equals(target)) ? null : "no dungeon " + target + " in dungeons.json";
                case DISCOVER_LOCATION -> regions.stream().anyMatch(x -> x.id().equals(target)) ? null : "no region " + target + " in world.json";
                case COLLECT_ITEM -> itemExists == null || itemExists.test(target) ? null : "no item " + target + " in the item catalog";
                case REACH_LEVEL -> count <= 100 ? null : "level " + count + " is above 100";
            };
            if (missing != null) issues.add(new Issue(f, at + ".target", missing));
            // the rules set the EXP: 35 % of a level at the chapter's level (progression v2), unless the file gives it
            int lv = switch (type) {
                case REACH_LEVEL -> count;
                case COMPLETE_DUNGEON -> dungeons.stream().filter(x -> x.id().equals(target)).findFirst().map(x -> x.minLevel() + 2).orElse(1);
                case DISCOVER_LOCATION -> regions.stream().filter(x -> x.id().equals(target)).findFirst().map(RegionDefinition::minLevel).orElse(1);
                case KILL_MOB -> mobs.containsKey(target) ? mobs.get(target).level() : 1;
                case COLLECT_ITEM -> 2;
            };
            long exp = q.containsKey("exp") ? intIn(f, at, q, "exp", 0, 100_000_000, true, 0, issues) : mn.suld.api.balance.Rewards.chapterExp(curve, lv);
            try {
                chapters.add(new QuestDefinition(id, str(f, at, q, "title", true, issues), q.get("description") instanceof String s ? s : "",
                        type, target, count, exp, intIn(f, at, q, "coins", 0, 10_000_000, false, 0, issues)));
            } catch (IllegalArgumentException e) {
                issues.add(new Issue(f, at, e.getMessage()));
            }
            lore.put(id, new QuestContent.Lore(q.get("giver") instanceof String g ? g : "Хархорум", q.get("hint") instanceof String h ? h : ""));
            if (q.get("story") instanceof String s) story.put(id, s);
        }

        // ---------------------------------------------------------------------------------------------- events
        f = "events.json";
        List<WorldEventDefinition> events = new ArrayList<>();
        Map<String, String> aliases = new HashMap<>();
        List<Object> eventList = list(f, "", files.get(f), "worldEvents", issues);
        for (int i = 0; i < eventList.size(); i++) {
            String at = "worldEvents[" + i + "]";
            Map<String, Object> e = obj(f, at, eventList.get(i), issues);
            if (e == null) continue;
            String id = str(f, at, e, "id", true, issues);
            List<String> targets = strings(f, at, e, "targetMobs", issues);
            for (String t : targets) if (!mobs.containsKey(t)) issues.add(new Issue(f, at + ".targetMobs", "no mob " + t + " in mobs.json"));
            if (id == null) continue;
            try {
                events.add(new WorldEventDefinition(id, str(f, at, e, "name", true, issues), e.get("description") instanceof String s ? s : "",
                        new LinkedHashSet<>(targets), intIn(f, at, e, "targetKills", 1, 100_000, true, 1, issues),
                        intIn(f, at, e, "durationSeconds", 10, 86_400, true, 600, issues), intIn(f, at, e, "rewardExp", 0, 10_000_000, true, 0, issues),
                        intIn(f, at, e, "rewardCoins", 0, 10_000_000, true, 0, issues), intIn(f, at, e, "minContribution", 0, 100_000, true, 0, issues),
                        intIn(f, at, e, "clanExpPerKill", 0, 100_000, true, 0, issues), intIn(f, at, e, "clanSuccessBonus", 0, 10_000_000, true, 0, issues)));
            } catch (IllegalArgumentException ex) {
                issues.add(new Issue(f, at, ex.getMessage()));
            }
            for (String alias : strings(f, at, e, "aliases", issues)) aliases.put(alias, id);
        }
        List<RelicDefinition> relics = new ArrayList<>();
        List<Object> relicList = list(f, "", files.get(f), "relics", issues);
        for (int i = 0; i < relicList.size(); i++) {
            String at = "relics[" + i + "]";
            Map<String, Object> r = obj(f, at, relicList.get(i), issues);
            if (r == null) continue;
            try {
                relics.add(new RelicDefinition(str(f, at, r, "key", true, issues), str(f, at, r, "name", true, issues),
                        r.get("lore") instanceof String s ? s : "", str(f, at, r, "material", true, issues),
                        intIn(f, at, r, "customModelData", 0, Integer.MAX_VALUE, true, 0, issues), intIn(f, at, r, "minLevel", 1, 100, true, 1, issues),
                        SkillTreeLoader.num(f, at, r, "expBonus", 0, 10, issues)));
            } catch (IllegalArgumentException | NullPointerException ex) {
                issues.add(new Issue(f, at, String.valueOf(ex.getMessage())));
            }
        }

        // --------------------------------------------------------------------------------- names the code uses
        Set<String> known = new HashSet<>(mobs.keySet());
        regions.forEach(r -> known.add(r.id()));
        dungeons.forEach(d -> known.add(d.id()));
        chapters.forEach(q -> known.add(q.id()));
        events.forEach(e -> known.add(e.id()));
        relics.forEach(r -> known.add(r.key()));
        for (String id : new java.util.TreeSet<>(REQUIRED)) {
            if (!known.contains(id)) issues.add(new Issue(fileFor(id), id, "required by the plugin's code: do not remove or rename it"));
        }
        for (DungeonDefinition d : dungeons) {
            if (sites.stream().noneMatch(s -> s.dungeonId().equals(d.id()))) issues.add(new Issue("dungeons.json", d.id() + ".site", "a dungeon needs a gate site"));
            if (!completions.containsKey(d.id())) issues.add(new Issue("dungeons.json", d.id() + ".completion", "missing"));
        }
        if (!issues.isEmpty()) return new Result(null, issues, overridden);
        return new Result(new ContentPack(mobs, roles, models, inner, edge, regions, outer, areas, dungeons, sites, completions,
                dRegion, dWhere, chapters, lore, story, events, aliases, relics), issues, overridden);
    }

    private static String fileFor(String id) {
        if (id.startsWith("mob.")) return "mobs.json";
        if (id.startsWith("region.")) return "world.json";
        if (id.startsWith("dungeon.")) return "dungeons.json";
        if (id.startsWith("quest.")) return "quests.json";
        return "events.json";
    }

    /** A living vanilla entity type, checked against the server's types when they are on the class path. */
    private static boolean livingHost(String host) {
        try {
            Class<?> type = Class.forName("org.bukkit.entity.EntityType");
            Object t = type.getMethod("valueOf", String.class).invoke(null, host);
            Object living = type.getMethod("isAlive").invoke(t);
            return Boolean.TRUE.equals(living);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return false; // valueOf refused it: no such entity type
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return true; // no server classes (the simulator, unit tests): only the name's form is checked
        }
    }

    private static RegionShape shape(String f, String at, Object o, List<Issue> issues) {
        Map<String, Object> s = obj(f, at, o, issues);
        if (s == null) return null;
        String type = str(f, at, s, "type", true, issues);
        if (type == null) return null;
        try {
            return switch (type) {
                case "square" -> new RegionShape.Square(SkillTreeLoader.num(f, at, s, "half", 1, 100_000, issues));
                case "sector" -> new RegionShape.Sector(SkillTreeLoader.num(f, at, s, "minRadius", 0, 100_000, issues),
                        SkillTreeLoader.num(f, at, s, "maxRadius", 1, 100_000, issues), SkillTreeLoader.num(f, at, s, "fromDeg", 0, 360, issues),
                        SkillTreeLoader.num(f, at, s, "toDeg", 0, 360, issues));
                case "circle" -> new RegionShape.Circle(SkillTreeLoader.num(f, at, s, "cx", -100_000, 100_000, issues),
                        SkillTreeLoader.num(f, at, s, "cz", -100_000, 100_000, issues), SkillTreeLoader.num(f, at, s, "radius", 1, 100_000, issues));
                default -> {
                    issues.add(new Issue(f, at + ".type", "one of square, sector, circle"));
                    yield null;
                }
            };
        } catch (IllegalArgumentException e) {
            issues.add(new Issue(f, at, e.getMessage()));
            return null;
        }
    }

    private static Map<String, Object> obj(String f, String at, Object o, List<Issue> issues) {
        if (o instanceof Map<?, ?>) return Json.object(o);
        issues.add(new Issue(f, at, o == null ? "missing" : "must be an object"));
        return null;
    }

    private static List<Object> list(String f, String at, Map<String, Object> m, String key, List<Issue> issues) {
        Object v = m.get(key);
        if (v instanceof List<?>) return Json.array(v);
        issues.add(new Issue(f, (at.isEmpty() ? "" : at + ".") + key, v == null ? "missing" : "must be a list"));
        return List.of();
    }

    private static List<String> strings(String f, String at, Map<String, Object> m, String key, List<Issue> issues) {
        Object v = m.get(key);
        if (v == null) return List.of();
        List<String> out = new ArrayList<>();
        if (!(v instanceof List<?> l)) {
            issues.add(new Issue(f, at + "." + key, "must be a list of text"));
            return out;
        }
        for (Object o : l) {
            if (o instanceof String s) out.add(s);
            else issues.add(new Issue(f, at + "." + key, "must be a list of text"));
        }
        return out;
    }
}
