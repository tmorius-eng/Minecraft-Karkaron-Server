package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.Equipment;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.skill.tree.StatKey;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The state of one simulated player. Plain fields: the engine is the only writer. */
public final class SimPlayer {

    public static final String[] MASTERY = {"combat", "dungeon", "boss", "exploration", "crafting", "class", "weapon", "collection"};
    public static final int M_COMBAT = 0, M_DUNGEON = 1, M_BOSS = 2, M_EXPLORE = 3, M_CRAFT = 4, M_CLASS = 5, M_WEAPON = 6, M_COLLECT = 7;

    public final UUID id;
    public final PlayerClass clazz;
    public final Gear gear;

    // progression
    public int level = 1;
    public long expInto;
    public long totalExp;
    public long overflowExp; // EXP earned at the cap (proposed: Тэнгэрийн оноо)
    public int chapters; // finished story chapters
    public int chapterProgress;
    public final Set<String> regions = new HashSet<>();
    public final Map<String, Integer> landmarks = new HashMap<>();
    public final Map<String, Integer> clears = new HashMap<>();
    public final ArrayDeque<String> recentClears = new ArrayDeque<>();
    public final Set<String> heroicClears = new HashSet<>();
    public int mythicTier; // highest mythic-dungeon tier cleared
    public final Map<String, Integer> bossKills = new HashMap<>();
    public int worldBossKills;
    public final Set<String> collection = new HashSet<>(); // distinct item definitions seen

    // economy
    public long coins;
    public long coinsEarned;
    public long coinsSpent;
    public final Map<String, Integer> materials = new HashMap<>();
    public long bagValue; // sale value of unequipped drops waiting for a town visit
    public int salvaged, crafted, reforges, tempers;
    public int temper; // proposed post-60 tempering on all worn gear, 0..10

    // class armour (proposed)
    public int armorLevel = 1;
    public double armorXp;
    public int armorTier = 1;
    public int armorEnhance;

    // mastery (proposed)
    public final double[] masteryXp = new double[MASTERY.length];
    public final int[] mastery = new int[MASTERY.length];

    // ascension (proposed)
    public int ascension;

    // death and time
    public int deaths;
    public final Map<String, Integer> deathsBy = new HashMap<>();
    public double lockUntil; // absolute minutes
    public double lockedMinutes; // play minutes lost to death locks
    public int wound; // stacks
    public double woundHeal; // active minutes toward healing a stack
    public double activeMinutes;
    public double restedExp;
    public long lastLoginDay = -10;
    public int loginStreak;
    public long expLostToDeath;

    // first-time markers, in active hours (NaN = not yet)
    public final double[] firstRarity = new double[ItemRarity.values().length];
    public final Map<Integer, Double> hoursAtLevel = new HashMap<>();
    /** Power at each level reached: {dps, max health, armour, regen} (for the difficulty-curve calibration). */
    public final Map<Integer, double[]> powerAtLevel = new HashMap<>();
    public double firstEndgameDungeon = Double.NaN;
    public final double[] hoursAtAscension = new double[11];

    // per-day history (index = day number - 1)
    public final java.util.List<Integer> dayLevel = new java.util.ArrayList<>();
    public final java.util.List<Integer> dayMilestones = new java.util.ArrayList<>();
    public final java.util.List<Double> dayLevelFraction = new java.util.ArrayList<>();
    public int milestonesToday;
    public int lockStreak, lockStreakMax; // whole sessions lost to a death lock in a row
    /** At the moment level 20 is first reached: dungeons open / realistically clearable / total. */
    public int[] atLevel20;

    // bookkeeping for the policy and reports
    public long kills, eliteKills;
    public int dungeonRuns, dungeonFails;
    public final Map<String, Double> expBySource = new HashMap<>(); // until level 60
    public final Map<String, Double> overflowBySource = new HashMap<>(); // at the cap
    public double lastGpMilestone = 1;
    public final Map<String, Double> minutesBySource = new HashMap<>();
    public int zoneHere = -1;

    public SimPlayer(UUID id, PlayerClass clazz, Gear gear) {
        this.id = id;
        this.clazz = clazz;
        this.gear = gear;
        java.util.Arrays.fill(firstRarity, Double.NaN);
        java.util.Arrays.fill(hoursAtAscension, Double.NaN);
        hoursAtLevel.put(1, 0.0);
    }

    public double activeHours() {
        return activeMinutes / 60.0;
    }

    public Equipment.Bonus bonus() {
        return gear.bonus(id, clazz, level);
    }

    public double stat(StatKey k) {
        return bonus().statKeys().getOrDefault(k, 0.0);
    }

    public int masteryTotal() {
        int t = 0;
        for (int m : mastery) t += m;
        return t;
    }

    public int masteryMin() {
        int t = Integer.MAX_VALUE;
        for (int m : mastery) t = Math.min(t, m);
        return t;
    }

    public void addMaterial(String id, int qty) {
        materials.merge(id, qty, Integer::sum);
    }

    public boolean takeMaterial(String id, int qty) {
        int have = materials.getOrDefault(id, 0);
        if (have < qty) return false;
        materials.put(id, have - qty);
        return true;
    }

    public final Map<String, Long> coinsBySource = new HashMap<>();
    public String coinSource = "other";

    public void earn(long c) {
        coins += c;
        coinsEarned += c;
        if (c > 0) coinsBySource.merge(coinSource, c, Long::sum);
    }

    public boolean spend(long c) {
        if (coins < c) return false;
        coins -= c;
        coinsSpent += c;
        return true;
    }

    public int distinctClears() {
        int n = 0;
        for (int v : clears.values()) if (v > 0) n++;
        return n;
    }
}
