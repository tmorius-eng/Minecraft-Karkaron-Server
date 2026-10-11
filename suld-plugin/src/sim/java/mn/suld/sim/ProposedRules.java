package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemType;
import mn.suld.api.loot.LootTable;
import mn.suld.api.loot.LootTier;
import mn.suld.api.loot.RarityBand;
import mn.suld.api.mob.MobTier;
import mn.suld.api.progression.LevelCurve;
import mn.suld.api.progression.PolynomialLevelCurve;
import mn.suld.api.quest.QuestType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * The proposed balance (docs/PROGRESSION_BALANCE_SPEC.md). Every number of the spec is defined here, once, as a
 * formula or a table, so the documents print exactly what the simulation ran. Nothing here is in the game yet.
 *
 * <p>Design rules it follows (directive §2): no calendar locks. Progression is paced by content tiers, multi-gate
 * dungeons, encounter difficulty, the level-gap rules and activity-based (not time-based) diminishing returns.
 */
public class ProposedRules extends Rules {

    // ------------------------------------------------------------------------------------------- tunable constants

    /** EXP to go from L to L+1 = round(CURVE_BASE · L^CURVE_EXP) — plain config for PolynomialLevelCurve. */
    public static final double CURVE_EXP = 2.2;
    public static final double DEFAULT_CURVE_BASE = 315;

    /** Death lock candidates evaluated by the simulation (minutes of real time, by level). */
    public enum LockCurve { NONE_30S, LINEAR, STEP, GEOMETRIC, LOG }

    public static final double LOCK_MIN = 5, LOCK_MAX = 24 * 60;

    private final double curveBase;
    private final LockCurve lockCurve;
    private final LevelCurve curve;
    private final World world;
    private final Loot loot;
    private final ItemCatalog catalog;

    public ProposedRules() {
        this(DEFAULT_CURVE_BASE, LockCurve.GEOMETRIC);
    }

    public ProposedRules(double curveBase, LockCurve lockCurve) {
        this.curveBase = curveBase;
        this.lockCurve = lockCurve;
        this.curve = new PolynomialLevelCurve(curveBase, CURVE_EXP, 60);
        this.world = buildWorld();
        this.catalog = catalog(LiveRules.liveCatalog(), world);
        this.loot = new Loot(catalog);
    }

    public ProposedRules withLock(LockCurve c) {
        return new ProposedRules(curveBase, c);
    }

    public ProposedRules withCurveBase(double b) {
        return new ProposedRules(b, lockCurve);
    }

    public double curveBase() {
        return curveBase;
    }

    public LockCurve lockCurve() {
        return lockCurve;
    }

    @Override
    public String name() {
        return "proposed";
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

    public long need(int level) {
        return curve.expForLevel(Math.max(1, Math.min(59, level)));
    }

    // ------------------------------------------------------------------------------------------------ mob formulas

    /** Base EXP of a mob before its tier multiplier: close to the live values up to level 26. */
    public static long mobBaseExp(int level) {
        return Math.round(30 + 10.0 * level + 0.05 * level * level);
    }

    // The difficulty curve is derived from the median ("par") player the simulation produces under these rules
    // (Calibrate: hardcore, all five classes), so a fight at par always reads the same: a NORMAL mob dies in
    // ~2.5 s and one fight costs ~15 % of max health. docs/DIFFICULTY_CURVE.md prints the tables.

    /** Par damage per second at a level (fit of the median player: 10 + 2.75·L^1.36). */
    public static double parDps(int level) {
        return 10 + 2.75 * Math.pow(level, 1.36);
    }

    /** Par max health (fit: 46.5 + 4.5·L + 0.113·L²). */
    public static double parHp(int level) {
        return 46.5 + 4.5 * level + 0.113 * level * level;
    }

    /** Par armour (fit: 7 + 0.97·L). */
    public static double parArmor(int level) {
        return 7 + 0.97 * level;
    }

    public static double parRegen(int level) {
        return 0.3 + 0.05 * level;
    }

    /** Armour constant of the SÜLD mitigation a/(a+K): K grows with the attacker's level. */
    public static double armorK(int mobLevel) {
        return 10 + 2.5 * mobLevel;
    }

    static final double TTK_NORMAL = 2.5, DANGER_NORMAL = 0.15, SHARE = 0.5, ENGAGED = 1.2, INTERVAL = 1.5;

    /** Health of a NORMAL mob; tiers multiply. */
    public static double mobHealth(int level) {
        return Math.round(TTK_NORMAL * parDps(level));
    }

    /** Damage per hit of a NORMAL mob, from the danger target at par. */
    public static double mobDamage(int level) {
        double inc = (DANGER_NORMAL * parHp(level) / TTK_NORMAL + parRegen(level)) / ENGAGED;
        double mit = parArmor(level) / (parArmor(level) + armorK(level));
        return Math.round(inc * INTERVAL / ((1 - mit) * SHARE) * 10) / 10.0;
    }

    static double tierDamage(MobTier t) {
        return switch (t) {
            case NORMAL -> 1.0;
            case ELITE -> 1.3;
            case CHAMPION -> 1.6;
            case MYTHIC -> 2.0;
            case BOSS -> 1.0;
            case WORLD_BOSS -> 1.2;
        };
    }

    static double tierHealth(MobTier t) {
        return switch (t) {
            case NORMAL -> 1.0;
            case ELITE -> 2.5;
            case CHAMPION -> 5.0;
            case MYTHIC -> 10.0;
            case BOSS -> 40.0;
            case WORLD_BOSS -> 200.0;
        };
    }

    static World.Mob mob(String id, String name, int level, MobTier tier, boolean ranged, String table) {
        double hp = mobHealth(level) * tierHealth(tier);
        double dmg = mobDamage(level) * tierDamage(tier) * (tier == MobTier.BOSS || tier == MobTier.WORLD_BOSS ? 1.27 : 1.0);
        long exp = Math.round(mobBaseExp(level) * tier.rewardMultiplier());
        long coins = Math.round((1 + 0.4 * level) * (tier == MobTier.NORMAL ? 1 : tier.rewardMultiplier()));
        boolean boss = tier == MobTier.BOSS || tier == MobTier.WORLD_BOSS;
        return new World.Mob(id, name, level, tier, hp, dmg, boss || ranged ? 2.0 : INTERVAL, ranged, exp, table, coins);
    }

    // ---------------------------------------------------------------------------------------------- the world model

    /** Bands: the live four (re-statted) plus four new regions to level 60. Names: real Mongolian geography. */
    record BandSpec(String id, String name, int min, int max, int[] normals, int elite, int champion, String material) {
    }

    static final List<BandSpec> BANDS = List.of(
            new BandSpec("region.kherlen", "Хэрлэнгийн Тал", 1, 8, new int[]{2, 4, 6}, 7, 0, "item.chonon_arisan"),
            new BandSpec("region.gobi", "Говь", 5, 15, new int[]{7, 9, 12}, 13, 0, "item.khilentsiin_khor"),
            new BandSpec("region.khangai", "Хангай", 10, 20, new int[]{12, 15, 18}, 14, 19, "item.baavgain_arisan"),
            new BandSpec("region.altai", "Алтай", 18, 30, new int[]{19, 24, 28}, 23, 29, "item.mosun_chuluu"),
            new BandSpec("region.khuvsgul", "Хөвсгөл", 27, 38, new int[]{28, 32, 36}, 34, 37, "item.mosun_chuluu"),
            new BandSpec("region.zuungar", "Зүүнгарын Говь", 35, 46, new int[]{36, 40, 44}, 42, 45, "item.altan_toos"),
            new BandSpec("region.burkhan", "Бурхан Халдун", 43, 54, new int[]{44, 48, 52}, 50, 53, "item.altan_toos"),
            new BandSpec("region.otgon", "Отгонтэнгэр", 51, 60, new int[]{52, 56, 59}, 57, 60, "item.tengeriin_chuluu"));

    /** Dungeon ladder: id, name, min, max, band, chapter gate (act-wide index), previous dungeon. */
    record DungeonSpec(String id, String name, int min, int max, int band, int chapterGate, int party) {
    }

    static final List<DungeonSpec> DUNGEONS = List.of(
            new DungeonSpec("dungeon.khasar_den", "Хасарын Агуй", 3, 10, 0, 3, 1),
            new DungeonSpec("dungeon.govi_bulsh", "Говийн Булш", 9, 16, 1, 7, 2),
            new DungeonSpec("dungeon.baavgain_uur", "Баавгайн Үүр", 15, 22, 2, 12, 2),
            new DungeonSpec("dungeon.mosun_orgil", "Мөсөн Оргил", 22, 30, 3, 15, 3),
            new DungeonSpec("dungeon.dalain_gun", "Далайн Гүн", 29, 37, 4, 20, 3),
            new DungeonSpec("dungeon.khar_khot", "Хар Хотын Балгас", 36, 44, 5, 26, 3),
            new DungeonSpec("dungeon.ulaan_khad", "Улаан Хадны Хүрээ", 42, 50, 6, 31, 4),
            new DungeonSpec("dungeon.burkhan_agui", "Бурхан Халдуны Агуй", 48, 56, 6, 34, 4),
            new DungeonSpec("dungeon.tengeriin_shat", "Тэнгэрийн Шат", 54, 60, 7, 38, 4),
            new DungeonSpec("dungeon.tengeriin_ordon", "Тэнгэрийн Ордон", 60, 60, 7, 41, 4));

    public static final int MYTHIC_TIERS = 10;

    private World buildWorld() {
        List<World.Zone> zones = new ArrayList<>();
        for (int b = 0; b < BANDS.size(); b++) {
            BandSpec s = BANDS.get(b);
            List<World.Mob> mobs = new ArrayList<>();
            List<Double> w = new ArrayList<>();
            String base = "loot.p." + s.id().substring(7);
            for (int lv : s.normals()) {
                mobs.add(mob("mob.p." + s.id().substring(7) + ".n" + lv, s.name() + " N" + lv, lv, MobTier.NORMAL, lv % 2 == 1, base + ".normal"));
                w.add(0.82 / s.normals().length);
            }
            mobs.add(mob("mob.p." + s.id().substring(7) + ".e" + s.elite(), s.name() + " E" + s.elite(), s.elite(), MobTier.ELITE, false, base + ".elite"));
            w.add(s.champion() > 0 ? 0.15 : 0.18);
            if (s.champion() > 0) {
                mobs.add(mob("mob.p." + s.id().substring(7) + ".c" + s.champion(), s.name() + " C" + s.champion(), s.champion(), MobTier.CHAMPION, false, base + ".champion"));
                w.add(0.03);
            }
            double[] weights = new double[w.size()];
            for (int i = 0; i < weights.length; i++) weights[i] = w.get(i);
            long discovery = Math.round(0.05 * curve.expForLevel(Math.max(1, s.min())));
            zones.add(new World.Zone(s.id(), s.name(), s.min(), s.max(), mobs, weights, discovery, 12, b)); // 8 landmarks + 4 hidden places
        }
        List<World.Dungeon> dungeons = new ArrayList<>();
        for (int i = 0; i < DUNGEONS.size(); i++) {
            DungeonSpec d = DUNGEONS.get(i);
            dungeons.add(dungeon(d, i, zones.get(d.band()), false, 0));
        }
        // level-60 heroic versions of the first nine (mythic tiers scale them further at run time)
        for (int i = 0; i < 9; i++) {
            DungeonSpec d = DUNGEONS.get(i);
            DungeonSpec h = new DungeonSpec(d.id() + ".heroic", d.name() + " (Баатарлаг)", 60, 60, 7, 41, 4);
            dungeons.add(dungeon(h, DUNGEONS.size() + i, zones.get(7), true, 0));
        }
        List<World.Chapter> story = buildStory(zones, dungeons);
        List<World.Event> events = List.of(
                new World.Event("event.band_raid", 45, 10, 0, 0, true),
                new World.Event("event.world_boss", 180, 15, 0, 0, true));
        return new World(zones, dungeons, story, events);
    }

    private World.Dungeon dungeon(DungeonSpec d, int index, World.Zone z, boolean heroic, int mythic) {
        int lv = heroic ? 60 : d.min() + 3;
        List<List<World.Mob>> waves = new ArrayList<>();
        String t = "loot.p." + z.id().substring(7);
        for (int w = 0; w < 3; w++) {
            List<World.Mob> wave = new ArrayList<>();
            for (int k = 0; k < 4 + w; k++) wave.add(mob("mob.p.wave", "wave", lv, k == 3 + w && w > 0 ? MobTier.ELITE : MobTier.NORMAL, k % 2 == 1, t + ".normal"));
            waves.add(wave);
        }
        int bossLevel = heroic ? 60 : Math.min(60, d.min() + 5);
        World.Mob b0 = mob(d.id() + ".boss", d.name() + " — эзэн", bossLevel, MobTier.BOSS, false, "loot.p.boss." + index);
        // boss health is sized for the dungeon's recommended party: ×0.44 for a solo dungeon … ×1.0 for four
        double partyScale = 0.25 + 0.75 * d.party() / 4.0;
        World.Mob boss = new World.Mob(b0.id(), b0.name(), b0.level(), b0.tier(), Math.round(b0.hp() * partyScale), b0.dmg(), b0.interval(),
                b0.ranged(), b0.exp(), b0.lootTable(), b0.coins());
        long completion = Math.round(0.04 * curve.expForLevel(Math.min(59, lv)));
        long coins = 60 + 12L * lv;
        int previous = heroic ? -1 : index > 0 ? index - 1 : -1;
        double gpMin = heroic ? 0.9 * parGearPower(60) : 0.75 * parGearPower(d.min());
        double enrage = 180 + 15 * Math.min(index, 9);
        return new World.Dungeon(d.id(), d.name(), heroic ? 60 : d.min(), d.max(), index, waves, boss, completion, coins,
                "loot.p.chest." + (heroic ? "heroic" : String.valueOf(index)), enrage, d.chapterGate(), previous, gpMin, heroic, mythic);
    }

    /**
     * Act I keeps the 18 live chapters with EXP rescaled to the new curve; Act II adds 24 chapters across the new
     * bands with the objective types the directive asks for (investigation, escort, puzzle, dialogue, boss, discovery).
     */
    private List<World.Chapter> buildStory(List<World.Zone> zones, List<World.Dungeon> dungeons) {
        List<World.Chapter> out = new ArrayList<>();
        World live = new LiveRules().world();
        for (World.Chapter c : live.story()) {
            int lv = chapterLevel(c, live);
            long exp = Math.round(0.35 * curve.expForLevel(Math.max(1, Math.min(59, lv))));
            String target = c.target();
            int zone = c.zone();
            if (c.type() == QuestType.KILL_MOB || c.type() == QuestType.COLLECT_ITEM) {
                // live targets map onto the re-statted band of the same region
                zone = Math.max(0, zone);
                target = null; // any mob of the zone counts in the proposed model (per-mob counts are content detail)
            }
            out.add(new World.Chapter(c.id(), c.title(), c.type(), target, c.count(), exp, Math.round(c.coins() * 2.5), zone, c.minutes(), 1));
        }
        String[] kinds = {"investigation", "escort", "puzzle", "dialogue", "boss", "discovery"};
        for (int b = 4; b < 8; b++) {
            World.Zone z = zones.get(b);
            for (int k = 0; k < 6; k++) {
                int lv = z.min() + (z.max() - z.min()) * k / 6;
                long exp = Math.round(0.35 * curve.expForLevel(Math.min(59, lv)));
                String kind = kinds[k];
                QuestType type = switch (kind) {
                    case "boss" -> QuestType.COMPLETE_DUNGEON;
                    case "discovery" -> QuestType.DISCOVER_LOCATION;
                    default -> QuestType.KILL_MOB;
                };
                String target = type == QuestType.COMPLETE_DUNGEON ? DUNGEONS.get(b == 7 ? 8 : b).id() : type == QuestType.DISCOVER_LOCATION ? z.id() : null;
                int count = type == QuestType.KILL_MOB ? 6 : 1;
                double minutes = switch (kind) {
                    case "investigation" -> 14;
                    case "escort" -> 12;
                    case "puzzle" -> 16;
                    case "dialogue" -> 8;
                    default -> 6;
                };
                out.add(new World.Chapter("quest.act2." + z.id().substring(7) + "." + kind, kind, type, target, count, exp,
                        60 + 15L * lv, b, minutes, 2));
            }
        }
        return out;
    }

    static int chapterLevel(World.Chapter c, World w) {
        return switch (c.type()) {
            case REACH_LEVEL -> c.count();
            case COMPLETE_DUNGEON -> w.dungeon(c.target()) == null ? 1 : w.dungeon(c.target()).min() + 2;
            case DISCOVER_LOCATION -> w.zone(c.target()) == null ? 1 : w.zone(c.target()).min();
            case KILL_MOB -> w.mob(c.target()) == null ? 1 : w.mob(c.target()).level();
            case COLLECT_ITEM -> c.zone() < 0 ? 2 : w.zones().get(c.zone()).min() + 1;
        };
    }

    // ------------------------------------------------------------------------------------------------- the catalog

    /**
     * The live item catalog with the proposed loot tables and rarity bands: the data that would replace
     * {@code items/loot.json} and {@code items/tiers.json}. Items, affixes, sets and recipes are unchanged.
     */
    static ItemCatalog catalog(ItemCatalog live, World world) {
        List<RarityBand> bands = new ArrayList<>();
        bands.add(band(LootTier.NORMAL, 70, 25, 5, 0, 0, 0, 0));
        bands.add(band(LootTier.ELITE, 0, 55, 35, 10, 0, 0, 0));
        bands.add(band(LootTier.CHAMPION, 0, 0, 55, 35, 10, 0, 0));
        bands.add(band(LootTier.MYTHIC, 0, 0, 0, 30, 50, 15, 5));          // mythic-tier heroic chests
        bands.add(band(LootTier.BOSS, 0, 0, 50, 38, 10, 2, 0));            // dungeon bosses (was L70/An25/My5)
        bands.add(band(LootTier.WORLD_EVENT, 0, 0, 0, 40, 45, 12, 3));     // world boss + heroic bosses
        bands.add(band(LootTier.DUNGEON, 0, 35, 45, 17, 3, 0, 0));         // completion chest
        bands.add(band(LootTier.QUEST, 0, 50, 40, 10, 0, 0, 0));
        bands.add(band(LootTier.CHEST, 50, 35, 15, 0, 0, 0, 0));
        bands.add(band(LootTier.CRAFT, 0, 60, 30, 10, 0, 0, 0));
        List<LootTable> tables = new ArrayList<>(live.lootTables());
        LootTable.Pool gear = new LootTable.Pool(EnumSet.of(ItemType.Category.WEAPON, ItemType.Category.ARMOR,
                ItemType.Category.JEWELRY, ItemType.Category.OFFHAND));
        for (BandSpec s : BANDS) {
            String base = "loot.p." + s.id().substring(7);
            tables.add(table(base + ".normal", LootTier.NORMAL, 94, gear, s.material(), 0.5, 1, 1));
            tables.add(table(base + ".elite", LootTier.ELITE, 75, gear, s.material(), 1.0, 1, 2));
            tables.add(table(base + ".champion", LootTier.CHAMPION, 40, gear, s.material(), 1.0, 2, 3));
        }
        for (int i = 0; i < DUNGEONS.size() + 9; i++) {
            BandSpec s = BANDS.get(i < DUNGEONS.size() ? DUNGEONS.get(i).band() : 7);
            boolean heroic = i >= DUNGEONS.size();
            tables.add(table("loot.p.boss." + i, heroic ? LootTier.WORLD_EVENT : LootTier.BOSS, 0, gear,
                    heroic ? "item.tengeriin_chuluu" : s.material(), 1.0, 2, 4));
            if (!heroic) tables.add(table("loot.p.chest." + i, LootTier.DUNGEON, 30, gear, s.material(), 1.0, 2, 3));
        }
        tables.add(table("loot.p.chest.heroic", LootTier.WORLD_EVENT, 30, gear, "item.tengeriin_chuluu", 1.0, 2, 3));
        tables.add(table("loot.p.chest.mythic", LootTier.MYTHIC, 20, gear, "item.tengeriin_chuluu", 1.0, 3, 5));
        tables.add(table("loot.p.world_boss", LootTier.WORLD_EVENT, 0, gear, "item.tengeriin_chuluu", 1.0, 3, 5));
        return new ItemCatalog(live.items(), live.affixes(), live.sets(), tables, bands, live.recipes(), live.salvageMaterials());
    }

    private static RarityBand band(LootTier t, int c, int u, int r, int e, int l, int a, int m) {
        Map<ItemRarity, Integer> w = new EnumMap<>(ItemRarity.class);
        int[] v = {c, u, r, e, l, a, m};
        for (int i = 0; i < v.length; i++) if (v[i] > 0) w.put(ItemRarity.values()[i], v[i]);
        return new RarityBand(t, w);
    }

    private static LootTable table(String id, LootTier tier, int nothing, LootTable.Pool pool, String material, double matChance,
                                   int matMin, int matMax) {
        List<LootTable.Entry> entries = new ArrayList<>();
        if (nothing < 100) entries.add(new LootTable.Entry(null, pool, 100 - nothing, 1, 1, null, null, 2, null));
        List<LootTable.Rare> rare = List.of(new LootTable.Rare(new LootTable.Entry(material, null, 1, matMin, matMax, null, null, 0, null), matChance));
        return new LootTable(id, tier, 1, 1, nothing, List.of(), entries, rare);
    }

    // ------------------------------------------------------------------------------------------- rule overrides

    /**
     * Kill EXP by level gap (mob − player): a small bonus for fighting up, full value from −4 to 0, falling to 10 %
     * at −10 and below (no gear rolls there). Fighting far above is limited by danger, not by this factor.
     */
    @Override
    public double gapExpFactor(int gap) {
        if (gap >= 8) return 1.20;
        if (gap > 0) return 1.0 + 0.025 * gap;
        if (gap >= -4) return 1.0;
        if (gap > -10) return 1.0 - 0.15 * (-gap - 4);
        return 0.10;
    }

    @Override
    public boolean gapAllowsGear(int gap) {
        return gap > -10;
    }

    /** Level suppression: −4 % damage dealt per level above you (floor 40 %), +8 % damage taken per level. */
    @Override
    public double gapDamageDealt(int gap) {
        return gap <= 0 ? 1.0 : Math.max(0.4, 1.0 - 0.04 * gap);
    }

    @Override
    public double mitigation(double armor, double dmg, int mobLevel) {
        double a = Math.max(0, armor);
        return Math.min(0.75, a / (a + armorK(mobLevel)));
    }

    @Override
    public double gapDamageTaken(int gap) {
        return gap <= 0 ? 1.0 : 1.0 + 0.08 * gap;
    }

    @Override
    public double boostCap() {
        return 0.5; // clan + relic + items together at most +50 %
    }

    /** Party share: every member within range gets (1 + 0.15·(n−1)) / n of each kill. */
    @Override
    public double partyKillShare(int n) {
        return (1.0 + 0.15 * (n - 1)) / n;
    }

    @Override
    public long loginExp(int day, int level) {
        return Math.round(0.02 * day * curve.expForLevel(Math.max(1, Math.min(59, level))) / 7.0);
    }

    @Override
    public long loginCoins(int day) {
        return 40L * day + (day == 7 ? 200 : 0);
    }

    /** Rested EXP: 1.5 % of the current level per offline hour, pool capped at 1.5 levels, doubles kill EXP. */
    @Override
    public double restedPerOfflineHour(int level) {
        return 0.015 * curve.expForLevel(Math.max(1, Math.min(59, level)));
    }

    /** Catch-up: +50 % EXP while more than 10 levels behind the server's median active level. */
    @Override
    public double catchUpFactor(int level, int serverLevel) {
        return level < serverLevel - 10 ? 1.5 : 1.0;
    }

    @Override
    public boolean keepsOverflow() {
        return true;
    }

    /** Class base health finally applied, growing 4 % per level (Баатар 40 → 134 at 60). */
    @Override
    public double baseHealth(PlayerClass c, int level) {
        return c.baseHealth() * (1 + 0.04 * (level - 1));
    }

    /** The attack-cooldown charge is respected: heavy weapons swing slower but harder. */
    @Override
    public double hitsPerSecond(PlayerClass c) {
        return switch (c) {
            case BAATAR -> 1.6;
            case MERGEN -> 1.0;
            case BOO -> 1.2;
            case DARKHAN -> 1.0;
            case KHULEGCHIN -> 1.3;
        };
    }

    @Override
    public double hitWeight(PlayerClass c) {
        return switch (c) {
            case BAATAR -> 1.0;
            case MERGEN -> 1.45;
            case BOO -> 1.15;
            case DARKHAN -> 1.55;
            case KHULEGCHIN -> 1.2;
        };
    }

    // ------------------------------------------------------------------------------------------------- dungeons

    @Override
    public boolean dungeonOpen(SimPlayer p, World.Dungeon d) {
        if (p.level < d.min()) return false;
        if (p.chapters < Math.min(d.chapterGate() + 1, world.story().size())) return false;
        if (d.previous() >= 0 && p.clears.getOrDefault(world.dungeons().get(d.previous()).id(), 0) == 0) return false;
        if (d.heroic() && p.clears.getOrDefault("dungeon.tengeriin_shat", 0) == 0) return false;
        return p.gear.gearPower() >= d.gpMin();
    }

    @Override
    public int dungeonLootLevel(World.Dungeon d, int playerLevel) {
        return Math.max(d.min(), Math.min(d.max(), playerLevel));
    }

    /** Repeat fatigue: −15 % per clear of the same dungeon among the player's last 8 clears (floor 25 %). */
    @Override
    public double repeatFactor(SimPlayer p, World.Dungeon d) {
        int same = 0;
        for (String s : p.recentClears) if (s.equals(d.id())) same++;
        return Math.max(0.25, 1.0 - 0.15 * same);
    }

    /** Carried: above the dungeon's max level nothing but materials; 10+ below the party's top → ×0.5. */
    @Override
    public double carryFactor(int memberLevel, int partyMax, World.Dungeon d) {
        if (memberLevel > d.max()) return 0.1;
        return partyMax - memberLevel > 10 ? 0.5 : 1.0;
    }

    @Override
    public boolean enrageWipes() {
        return true;
    }

    // ---------------------------------------------------------------------------------------------------- death

    @Override
    public double deathLockMinutes(int level, int ascension) {
        double f = Math.max(0, Math.min(1, (level - 1) / 59.0));
        return switch (lockCurve) {
            case NONE_30S -> 0.5;
            case LINEAR -> LOCK_MIN + (LOCK_MAX - LOCK_MIN) * f;
            case STEP -> level < 10 ? 5 : level < 20 ? 30 : level < 30 ? 120 : level < 40 ? 360 : level < 50 ? 720 : level < 60 ? 1080 : 1440;
            case GEOMETRIC -> LOCK_MIN * Math.pow(LOCK_MAX / LOCK_MIN, f);
            case LOG -> Math.max(LOCK_MIN, LOCK_MAX * Math.log(level) / Math.log(60));
        };
    }

    @Override
    public double deathBarLoss() {
        return 0.05;
    }

    @Override
    public double woundPerDeath() {
        return 0.05;
    }

    @Override
    public double woundMax() {
        return 0.15;
    }

    /** One wound step heals after 3 active hours of play (the wound is pressure, not destruction). */
    @Override
    public double woundHealHours() {
        return 3;
    }

    @Override
    public double deathMaterialLoss() {
        return 0.25;
    }

    // ------------------------------------------------------------------------------------------------ skill points

    /**
     * 1 per level to 60 (59), 1 per 3 story chapters (cap 14 with 42 chapters), 1 per 2 discovered regions (4),
     * 1 per Ascension rank (10). 77 at 60 with everything done, 87 at Ascension X.
     */
    @Override
    public int skillPoints(int level, int chapters, int regions, int ascension) {
        return Math.max(0, level - 1) + Math.min(14, chapters / 3) + Math.min(4, regions / 2) + ascension;
    }

    // ---------------------------------------------------------------------------------------------------- economy

    @Override
    public long mobCoins(World.Mob m) {
        return m.coins();
    }

    @Override
    public double repairPerHour(int level) {
        return 15 + 4.0 * level;
    }

    /**
     * Merchant price: legendary and above cannot be sold (salvage only — high rarities feed upgrades, not inflation);
     * the rarity multiplier is capped at ×5 and the level factor flattened to 1 + L/30.
     */
    @Override
    public long sellPrice(mn.suld.api.item.ItemDefinition def, mn.suld.api.item.ItemInstance i) {
        if (def.sellValue() <= 0 || i.soulbound() || i.rarity().ordinal() >= ItemRarity.LEGENDARY.ordinal()) return 0;
        return Math.round(def.sellValue() * Math.min(5, i.rarity().sellMultiplier()) * (1 + i.itemLevel() / 30.0));
    }

    /** Reforge cost grows with the square of the item level (was linear). */
    @Override
    public long reforgeCost(int itemLevel) {
        return Math.round(0.6 * itemLevel * itemLevel + 25 * itemLevel + 25);
    }

    @Override
    public boolean hasMastery() {
        return true;
    }

    @Override
    public boolean hasAscension() {
        return true;
    }

    @Override
    public boolean hasClassArmor() {
        return true;
    }

    // --------------------------------------------------------------------------------------- class armour numbers

    /** Armour XP to go from armour level a to a+1 (≈ 150 armour XP per active hour of normal play). */
    public static long armorNeed(int a) {
        return Math.round(5.6 * Math.pow(a, 1.3));
    }

    /** Armour tier gates: armour level, dungeon to have cleared, coins. Tier 6 = endgame (also Ascension III). */
    public static final int[] TIER_ARMOR_LEVEL = {0, 1, 12, 24, 36, 48, 60};
    public static final String[] TIER_DUNGEON = {null, null, "dungeon.govi_bulsh", "dungeon.mosun_orgil", "dungeon.khar_khot",
            "dungeon.burkhan_agui", "dungeon.tengeriin_ordon"};
    /** Band whose material each tier upgrade takes (the band of its dungeon). */
    public static final int[] TIER_BAND = {0, 0, 1, 3, 5, 6, 7};
    public static final long[] TIER_COINS = {0, 0, 2_000, 12_000, 45_000, 120_000, 300_000};
    public static final ItemRarity[] TIER_RARITY = {null, ItemRarity.UNCOMMON, ItemRarity.RARE, ItemRarity.EPIC,
            ItemRarity.LEGENDARY, ItemRarity.ANCIENT, ItemRarity.MYTHIC};
    /** Enhancement +1..+5 inside a tier: +2 % item power each; coins = 40 · armour level · (step) · tier. */
    public static final int MAX_ENHANCE = 5;

    // -------------------------------------------------------------------------------------------- mastery numbers

    /** Mastery XP for rank r → r+1 (8 tracks × 10 ranks). */
    public static double masteryNeed(int r) {
        return 300 * Math.pow(r + 1, 1.9);
    }

    // ----------------------------------------------------------------------------------------- ascension numbers

    /** Coins of the Ascension rite for rank r → r+1 (endgame sink). */
    public static long ascensionCoins(int r) {
        return 25_000L * (r + 1);
    }

    /** Coins to temper all worn gear from t to t+1 (plus 3·(t+1) Тэнгэрийн чулуу). */
    public static long temperCoins(int t) {
        return 15_000L * (t + 1);
    }

    /** Entry sigil of a mythic-tier run. */
    public static long mythicFee(int tier) {
        return 400L + 250L * tier;
    }

    /** Тэнгэрийн оноо (EXP earned at the cap) spent on rank r → r+1. */
    public double ascensionCost(int r) {
        return 0.6 * curve.expForLevel(59) * (r + 1);
    }
}
