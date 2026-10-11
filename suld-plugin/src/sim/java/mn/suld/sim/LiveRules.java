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
 * from the plugin's content classes, and the real loot catalog. Since progression v2 the live game runs the same rule
 * functions as the proposal (suld-api {@code mn.suld.api.balance}: level gap, party share, boost cap, rested and
 * catch-up EXP, overflow, mitigation, class health, dungeon band/repeat/carry, skill points, coins), so the rules are
 * inherited from {@link ProposedRules}; what differs is the content (this world) and what live does not have yet
 * (enrage wipes, heroic/mythic dungeons, the world boss, Act II).
 */
public class LiveRules extends ProposedRules {

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

    private final LevelCurve curve;
    /** Curve-relative rewards of the live content (chapters, completions) scale with the base: need ∝ base. */
    private final double scale;
    private final World world;
    private final Loot loot;

    public LiveRules() {
        this(ProgressionSettings.defaults().base());
    }

    /** The live game on another curve base (tuning: {@code Sim --tune-live}). */
    public LiveRules(double base) {
        super(base, LockCurve.GEOMETRIC, false);
        this.curve = new mn.suld.api.progression.PolynomialLevelCurve(base, ProgressionSettings.defaults().exponent(), 60);
        this.scale = base / mn.suld.api.balance.Balance.CURVE_BASE; // the content's EXP was computed on Balance.curve()
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
        for (MobDefinition d : mn.suld.plugin.content.LadderContent.MOBS) m.put(d.id(), d);
        for (MobDefinition d : DungeonContent.BOSSES) m.put(d.id(), d);
        return m;
    }

    /**
     * A live mob: SÜLD health and SÜLD damage (CombatListener.onMobHitsPlayer: scaledAttack per hit; bosses scaledAttack
     * × phase, BossService); the vanilla entity still sets how often it hits.
     */
    static World.Mob liveMob(MobDefinition d, double phaseAverage) {
        boolean boss = d.tier() == MobTier.BOSS;
        // CombatListener/BossService: each hit × MobScaling.hitScale, the host's real swing interval
        double interval = mn.suld.api.balance.MobScaling.hostInterval(d.backingEntity());
        double dmg = d.scaledAttack() * mn.suld.api.balance.MobScaling.hitScale(d.backingEntity(), boss) * (boss ? phaseAverage : 1.0);
        boolean ranged = mn.suld.api.balance.MobScaling.rangedHost(d.backingEntity());
        return new World.Mob(d.id(), d.displayName(), d.level(), d.tier(), d.scaledHealth(), dmg, interval, ranged,
                d.scaledExp(), d.lootTableId(), d.coins());
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
            int landmarks = (int) WorldContent.AREAS.stream().filter(a -> a.regionId().equals(r.id())).count();
            long disc = mn.suld.api.balance.Rewards.regionDiscoveryExp(curve, r.minLevel());
            if (WorldContent.outer(r)) {
                // the outer lands' danger follows the distance (RegionSpawner.localLevel): one zone per third of the band,
                // its mobs those within the spawner's ±5 levels
                int span = r.maxLevel() - r.minLevel();
                for (int k = 0; k < 3; k++) {
                    int lo = r.minLevel() + span * k / 3, hi = r.minLevel() + span * (k + 1) / 3;
                    List<World.Mob> mobs = new ArrayList<>();
                    for (String id : r.mobIds()) {
                        MobDefinition m = defs.get(id);
                        if (m.level() >= lo - 3 && m.level() <= hi + 3) mobs.add(liveMob(m, 1.0));
                    }
                    if (mobs.isEmpty()) continue;
                    zones.add(new World.Zone(r.id() + "." + k, r.displayName() + " " + (k + 1), lo, hi, mobs, weights(mobs),
                            k == 0 ? disc : 0, landmarks / 3, idx++));
                }
                continue;
            }
            List<World.Mob> mobs = new ArrayList<>();
            for (String id : r.mobIds()) mobs.add(liveMob(defs.get(id), 1.0));
            zones.add(new World.Zone(r.id(), r.displayName(), r.minLevel(), r.maxLevel(), mobs, weights(mobs), disc, landmarks, idx++));
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
            // DungeonService: the ladder's band, completion from the curve, chapter gate, previous clear, gear power from
            // the fifth rung (v2Gate); boss health for the recommended party
            mn.suld.api.balance.DungeonLadder.Rung rung = mn.suld.api.balance.DungeonLadder.rung(d.id());
            double ps = mn.suld.api.balance.MobScaling.bossPartyScale(d.recommendedParty());
            World.Mob b = new World.Mob(boss.id(), boss.name(), boss.level(), boss.tier(), Math.round(boss.hp() * ps), boss.dmg(),
                    boss.interval(), boss.ranged(), boss.exp(), boss.lootTable(), boss.coins());
            long cexp = mn.suld.api.balance.Rewards.dungeonExp(curve, rung.contentLevel());
            long ccoins = mn.suld.api.balance.Rewards.dungeonCoins(rung.contentLevel());
            double gp = di >= 4 ? 0.75 * mn.suld.api.balance.GearPower.par(rung.min()) : 0;
            dungeons.add(new World.Dungeon(d.id(), d.displayName(), d.minLevel(), d.maxLevel(), di, waves, b, cexp, ccoins,
                    d.rewardTableId(), d.bossDefinition().enrageSeconds(), rung.chapterGate(), di - 1, gp, false, 0));
            di++;
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
            story.add(new World.Chapter(q.id(), q.title(), q.type(), q.targetId(), q.requiredCount(), Math.round(q.expReward() * scale),
                    q.currencyReward(), zone, minutes, 1));
        }
        List<World.Event> events = List.of(new World.Event(SuldContent.WOLF_RAID.id(), 45, 10, 300, 60, true));
        return new World(zones, dungeons, story, events);
    }

    // ------------------------------------------------------------------ live rules that moved on since Stage B

    private static final mn.suld.api.config.DeathSettings DEATH = mn.suld.api.config.DeathSettings.defaults();

    static double[] weights(List<World.Mob> mobs) {
        // RegionSpawner.pickByLevel: elites half as often as normal mobs
        double[] w = new double[mobs.size()];
        double t = 0;
        for (int i = 0; i < w.length; i++) t += w[i] = mobs.get(i).tier() == MobTier.NORMAL ? 1.0 : 0.5;
        for (int i = 0; i < w.length; i++) w[i] /= t;
        return w;
    }

    /** DungeonService.start + v2Gate: level, previous clear, the story chapter (capped at the story), gear power. */
    @Override
    public boolean dungeonOpen(SimPlayer p, World.Dungeon d) {
        if (p.level < d.min() || d.heroic()) return false;
        if (d.previous() >= 0 && p.clears.getOrDefault(world.dungeons().get(d.previous()).id(), 0) == 0) return false;
        if (p.chapters < Math.min(d.chapterGate() + 1, world.story().size())) return false;
        return p.gear.gearPower() >= d.gpMin();
    }

    @Override
    public boolean enrageWipes() {
        return false; // live: enrage only hits harder (BossService.ENRAGE_MULTIPLIER)
    }

    @Override
    public double deathLockMinutes(int level, int ascension) {
        return mn.suld.api.death.DeathLock.minutes(DEATH, level, ascension);
    }

    @Override
    public double deathBarLoss() {
        return DEATH.expLossFraction();
    }

    @Override
    public double woundPerDeath() {
        return DEATH.woundPerDeath();
    }

    @Override
    public double woundMax() {
        return DEATH.woundMax();
    }

    @Override
    public double woundHealHours() {
        return DEATH.woundHealMinutes() / 60.0;
    }

    @Override
    public double deathMaterialLoss() {
        return DEATH.lootLossFraction();
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
