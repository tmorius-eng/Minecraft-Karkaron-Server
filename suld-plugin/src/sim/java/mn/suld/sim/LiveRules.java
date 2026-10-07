package mn.suld.sim;

import mn.suld.api.config.ProgressionSettings;
import mn.suld.api.dungeon.BossPhase;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
import mn.suld.api.progression.LevelCurve;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestType;
import mn.suld.api.region.RegionDefinition;
import mn.suld.plugin.content.DungeonContent;
import mn.suld.plugin.content.QuestContent;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.content.WorldContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The game exactly as it is on {@code main}: the real level curve, the real mobs, regions, dungeons and story read
 * from the plugin's content classes, and the real loot catalog. Rule overrides are the defaults of {@link Rules}.
 */
public class LiveRules extends Rules {

    /** Vanilla melee/ranged damage per hit on the server's {@code difficulty=hard} (deploy/templates/server.properties.tpl:8). */
    static final Map<String, double[]> VANILLA_HARD = Map.of(
            // {damage per hit, seconds between hits, ranged 0/1}
            "WOLF", new double[]{6, 1.0, 0},
            "PILLAGER", new double[]{5, 2.5, 1},
            "CAVE_SPIDER", new double[]{5, 1.0, 0}, // 3 + hard poison
            "HUSK", new double[]{4.5, 1.0, 0},
            "POLAR_BEAR", new double[]{9, 1.0, 0},
            "STRAY", new double[]{4.5, 2.0, 1},
            "RAVAGER", new double[]{18, 1.5, 0});

    private static volatile ItemCatalog catalog;

    private final LevelCurve curve = ProgressionSettings.defaults().toCurve();
    private final World world;
    private final Loot loot;

    public LiveRules() {
        this.world = buildWorld();
        this.loot = new Loot(liveCatalog());
    }

    @Override
    public String name() {
        return "live";
    }

    @Override
    public LevelCurve curve() {
        return curve;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public Loot loot() {
        return loot;
    }

    /** The real item catalog from suld-api's resources (the same files the plugin loads). */
    public static ItemCatalog liveCatalog() {
        ItemCatalog c = catalog;
        if (c == null) {
            synchronized (LiveRules.class) {
                if (catalog == null) {
                    ItemCatalogLoader.Result r = ItemCatalogLoader.load(ItemCatalogLoader.classpath());
                    if (!r.ok()) throw new IllegalStateException("item catalog invalid: " + r.issues());
                    catalog = r.catalog();
                }
                c = catalog;
            }
        }
        return c;
    }

    /** Every SÜLD mob definition in the live content, by id. */
    static Map<String, MobDefinition> liveMobs() {
        Map<String, MobDefinition> m = new HashMap<>();
        for (MobDefinition d : List.of(SuldContent.GOVIIN_CHONO, SuldContent.ORKHON_CHONO, SuldContent.KHASAR)) m.put(d.id(), d);
        for (MobDefinition d : WorldContent.MOBS) m.put(d.id(), d);
        for (MobDefinition d : DungeonContent.BOSSES) m.put(d.id(), d);
        return m;
    }

    /** A live mob: SÜLD health; vanilla damage unless it is a boss (BossService.java:113 uses scaledAttack × phase). */
    static World.Mob liveMob(MobDefinition d, double phaseAverage) {
        double[] v = VANILLA_HARD.getOrDefault(d.backingEntity(), new double[]{5, 1.0, 0});
        boolean boss = d.tier() == MobTier.BOSS;
        double dmg = boss ? d.scaledAttack() * phaseAverage : v[0];
        double interval = boss ? 1.0 : v[1];
        return new World.Mob(d.id(), d.displayName(), d.level(), d.tier(), d.scaledHealth(), dmg, interval, v[2] > 0,
                d.scaledExp(), d.lootTableId(), 0);
    }

    /** Health-weighted average phase damage multiplier of a boss (phases 100/60/30 %: weights 0.4 / 0.3 / 0.3). */
    static double phaseAverage(List<BossPhase> phases) {
        double sum = 0, prev = 1.0;
        for (int i = 0; i < phases.size(); i++) {
            double next = i + 1 < phases.size() ? phases.get(i + 1).healthThresholdPct() : 0.0;
            sum += (prev - next) * phases.get(i).attackMultiplier();
            prev = next;
        }
        return sum;
    }

    private World buildWorld() {
        Map<String, MobDefinition> defs = liveMobs();
        List<World.Zone> zones = new ArrayList<>();
        int idx = 0;
        for (RegionDefinition r : WorldContent.REGIONS) {
            if (r.safeZone() || r.mobIds().isEmpty()) continue;
            List<World.Mob> mobs = new ArrayList<>();
            for (String id : r.mobIds()) mobs.add(liveMob(defs.get(id), 1.0));
            double[] w = new double[mobs.size()];
            java.util.Arrays.fill(w, 1.0 / mobs.size()); // RegionSpawner: uniform pick away from the city
            zones.add(new World.Zone(r.id(), r.displayName(), r.minLevel(), r.maxLevel(), mobs, w, r.discoveryExp(), 0, idx++));
        }
        List<World.Dungeon> dungeons = new ArrayList<>();
        int di = 0;
        for (DungeonDefinition d : DungeonContent.ALL) {
            List<List<World.Mob>> waves = new ArrayList<>();
            for (List<String> wave : d.waveSpawnIds()) {
                List<World.Mob> w = new ArrayList<>();
                for (String id : wave) w.add(liveMob(defs.get(id), 1.0));
                waves.add(w);
            }
            World.Mob boss = liveMob(d.bossDefinition().mob(), phaseAverage(d.bossDefinition().phases()));
            DungeonContent.Completion c = DungeonContent.completion(d.id());
            dungeons.add(new World.Dungeon(d.id(), d.displayName(), d.minLevel(), 60, di++, waves, boss, c.exp(), c.coins(),
                    d.rewardTableId(), d.bossDefinition().enrageSeconds(), -1, -1, 0, false, 0));
        }
        List<World.Chapter> story = new ArrayList<>();
        World tmp = new World(zones, dungeons, List.of(), List.of());
        for (QuestDefinition q : QuestContent.STORY.chapters()) {
            int zone = switch (q.type()) {
                case KILL_MOB -> tmp.zoneOf(q.targetId());
                case DISCOVER_LOCATION -> tmp.zone(q.targetId()) == null ? -1 : tmp.zone(q.targetId()).index();
                case COLLECT_ITEM -> zoneDropping(tmp, q.targetId());
                default -> -1;
            };
            double minutes = q.type() == QuestType.DISCOVER_LOCATION ? 5 : 0;
            story.add(new World.Chapter(q.id(), q.title(), q.type(), q.targetId(), q.requiredCount(), q.expReward(),
                    q.currencyReward(), zone, minutes, 1));
        }
        List<World.Event> events = List.of(new World.Event(SuldContent.WOLF_RAID.id(), 45, 10, 300, 60, false));
        return new World(zones, dungeons, story, events);
    }

    /** The zone whose mobs drop a collect-quest item (the item named in a mob loot table's rare drops). */
    static int zoneDropping(World w, String itemId) {
        ItemCatalog c = liveCatalog();
        for (World.Zone z : w.zones()) {
            for (World.Mob m : z.mobs()) {
                var t = c.lootTable(m.lootTable()).orElse(null);
                if (t != null && t.mayDrop(itemId)) return z.index();
            }
        }
        return 0;
    }

    /** Chance per kill that a mob drops the item (its rare-drop chance), for collect quests. */
    public double dropChance(World.Mob m, String itemId) {
        var t = loot().catalog().lootTable(m.lootTable()).orElse(null);
        if (t == null) return 0;
        for (var r : t.rare()) if (itemId.equals(r.entry().itemId())) return r.chance();
        for (var e : t.guaranteed()) if (itemId.equals(e.itemId())) return 1;
        return 0;
    }
}
