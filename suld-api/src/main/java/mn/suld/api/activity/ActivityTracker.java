package mn.suld.api.activity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One player's activity, cut into one-minute windows (docs/ACTIVE_PLAYTIME_SPEC.md). Event-driven: callers report
 * signals as they happen and close the window once a minute; there is no per-tick work. Pure and server-free (the
 * plugin's ActivePlaytimeService feeds it), not thread-safe: use it from one thread.
 *
 * <p>A window is active with one strong signal or with signals from two families, unless:
 * <ul>
 *   <li>the player was a soul (death lock) during it;</li>
 *   <li>more than {@value #MACRO_INTERVALS} attack intervals in it were identical within ±10 ms (an auto-clicker);</li>
 *   <li>it is the third or later minute in a row of taking damage while dealing none (an AFK mob farm).</li>
 * </ul>
 * No signal for {@value #AWAY_MINUTES} minutes makes the player "away".
 *
 * <p><b>Farming fatigue</b> ({@link #farmFactor}): kills are remembered with their mob type and position for the
 * last 30 <em>active</em> minutes (idle time does not wash them out). For a new kill,
 * {@code n = same mob type within 48 blocks + 0.5 × other mobs within 48 blocks}; the reward factor is 1 up to
 * {@value #FARM_FREE} and then {@code max(0.25, 1 / (1 + (n − 40) / 60))} — 0.5 at n = 100, 0.33 at 160, the 0.25
 * floor at 220. Moving to another area is a fresh count. It applies to player EXP, armour XP and mastery from normal
 * world kills only (dungeon, world-event and boss kills and quest rewards are never reduced).
 */
public final class ActivityTracker {

    public static final int MACRO_INTERVALS = 120;
    public static final int MACRO_JITTER_MS = 10;
    public static final int DAMAGE_IN_MINUTES = 3;
    public static final int AWAY_MINUTES = 15;
    public static final double MOVE_BLOCKS = 6;
    public static final long MOVE_SAMPLE_MS = 5_000;
    public static final int FARM_FREE = 40;
    public static final double FARM_HALF = 60;
    public static final double FARM_FLOOR = 0.25;
    /** Kills remembered (enough for the floor and then some). */
    public static final int KILL_MEMORY = 600;
    public static final int AREA_WINDOW_MINUTES = 30;
    /** Kills within this many blocks of a new kill count towards it (a radius, so no grid border to stand on). */
    public static final int FARM_RADIUS = 48;

    public enum Reason { ACTIVE, IDLE, TOO_WEAK, SOUL, MACRO, DAMAGE_IN }

    /** The classification of a closed minute. */
    public record Verdict(boolean active, ActivityCategory category, Reason reason) {
    }

    // the open window
    private final Set<ActivitySignal> signals = EnumSet.noneOf(ActivitySignal.class);
    private final List<Long> intervals = new ArrayList<>();
    private long lastAttack = -1;
    private boolean excluded;
    private double startX = Double.NaN;
    private double startZ;
    private double moved;
    private long lastSample = Long.MIN_VALUE / 2; // no overflow in "now - lastSample"

    // across windows
    private int damageInStreak;
    private int idleMinutes;
    private long activeIndex;
    private final Deque<double[]> kills = new ArrayDeque<>(); // {active minute index, x, z, mob type hash}
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;

    public void signal(ActivitySignal s) {
        signals.add(s);
    }

    /** A melee swing / hit, for the auto-clicker check (and a combat signal). */
    public void attack(long nowMs) {
        if (lastAttack >= 0) intervals.add(nowMs - lastAttack);
        lastAttack = nowMs;
        signals.add(ActivitySignal.DAMAGE_DEALT);
    }

    /** A kill at a position (combat signal) of an untyped mob (vanilla); it counts towards the area. */
    public void kill(double x, double z) {
        kill("", x, z);
    }

    /**
     * A kill of mob type {@code mobId} at a position: the combat signal, and the farming factor of this kill
     * (computed from the kills before it, then this kill is remembered).
     */
    public double kill(String mobId, double x, double z) {
        signals.add(ActivitySignal.KILL);
        lastX = x;
        lastZ = z;
        double f = farmFactor(mobId, x, z);
        kills.addLast(new double[] {activeIndex, x, z, mobId.hashCode()});
        if (kills.size() > KILL_MEMORY) kills.removeFirst();
        return f;
    }

    /** The reward factor a kill of this mob type here would get now (see the class comment). */
    public double farmFactor(String mobId, double x, double z) {
        int hash = mobId.hashCode();
        double n = 0, r2 = (double) FARM_RADIUS * FARM_RADIUS;
        for (double[] k : kills) {
            if (k[0] < activeIndex - AREA_WINDOW_MINUTES) continue;
            double dx = k[1] - x, dz = k[2] - z;
            if (dx * dx + dz * dz > r2) continue;
            n += (int) k[3] == hash ? 1 : 0.5;
        }
        return factor(n);
    }

    /** The farming curve. */
    public static double factor(double n) {
        if (n <= FARM_FREE) return 1;
        return Math.max(FARM_FLOOR, 1 / (1 + (n - FARM_FREE) / FARM_HALF));
    }

    /**
     * A position sample, at most one per {@value #MOVE_SAMPLE_MS} ms (extra samples are ignored). {@code passive}
     * marks movement that is not the player's own (vehicle on a rail, water current, elytra glide): it never counts.
     */
    public void move(double x, double z, boolean passive, long nowMs) {
        if (nowMs - lastSample < MOVE_SAMPLE_MS) return;
        lastSample = nowMs;
        if (passive) return;
        lastX = x; // the farming area follows the player: moving on ends it
        lastZ = z;
        if (Double.isNaN(startX)) {
            startX = x;
            startZ = z;
            return;
        }
        moved = Math.max(moved, Math.hypot(x - startX, z - startZ));
    }

    /** This minute does not count at all (soul state, locked out). */
    public void exclude() {
        excluded = true;
    }

    /** Close the current minute and classify it; the next window starts empty. */
    public Verdict closeMinute() {
        if (moved > MOVE_BLOCKS) signals.add(ActivitySignal.MOVE);
        Verdict v = classify();
        boolean dealt = signals.contains(ActivitySignal.DAMAGE_DEALT) || signals.contains(ActivitySignal.KILL) || signals.contains(ActivitySignal.SPELL);
        if (signals.contains(ActivitySignal.DAMAGE_TAKEN) && !dealt) damageInStreak++;
        else damageInStreak = 0;
        if (v.reason() == Reason.DAMAGE_IN || signals.isEmpty()) idleMinutes++;
        else idleMinutes = 0;
        if (v.active()) {
            activeIndex++;
            while (!kills.isEmpty() && kills.peekFirst()[0] < activeIndex - AREA_WINDOW_MINUTES) kills.removeFirst();
        }
        signals.clear();
        intervals.clear();
        excluded = false;
        startX = Double.NaN;
        moved = 0;
        return v;
    }

    private Verdict classify() {
        ActivityCategory cat = category();
        if (excluded) return new Verdict(false, cat, Reason.SOUL);
        if (signals.isEmpty()) return new Verdict(false, cat, Reason.IDLE);
        if (macro()) return new Verdict(false, cat, Reason.MACRO);
        boolean dealt = signals.contains(ActivitySignal.DAMAGE_DEALT) || signals.contains(ActivitySignal.KILL) || signals.contains(ActivitySignal.SPELL);
        if (signals.contains(ActivitySignal.DAMAGE_TAKEN) && !dealt && damageInStreak + 1 >= DAMAGE_IN_MINUTES) {
            return new Verdict(false, cat, Reason.DAMAGE_IN);
        }
        boolean strong = false;
        Set<ActivitySignal.Family> families = EnumSet.noneOf(ActivitySignal.Family.class);
        for (ActivitySignal s : signals) {
            if (s.strength() == ActivitySignal.Strength.STRONG) strong = true;
            families.add(s.family());
        }
        return strong || families.size() >= 2 ? new Verdict(true, cat, Reason.ACTIVE) : new Verdict(false, cat, Reason.TOO_WEAK);
    }

    private ActivityCategory category() {
        for (ActivityCategory c : List.of(ActivityCategory.DUNGEON, ActivityCategory.COMBAT, ActivityCategory.QUEST,
                ActivityCategory.EXPLORATION, ActivityCategory.CRAFTING)) {
            for (ActivitySignal s : signals) if (s.category() == c) return c;
        }
        return ActivityCategory.OTHER;
    }

    /** More than {@value #MACRO_INTERVALS} intervals inside one ±{@value #MACRO_JITTER_MS} ms band. */
    private boolean macro() {
        if (intervals.size() <= MACRO_INTERVALS) return false;
        long[] a = intervals.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(a);
        int best = 0;
        for (int lo = 0, hi = 0; hi < a.length; hi++) {
            while (a[hi] - a[lo] > 2L * MACRO_JITTER_MS) lo++;
            best = Math.max(best, hi - lo + 1);
        }
        return best > MACRO_INTERVALS;
    }

    /** No signal for {@value #AWAY_MINUTES} minutes. */
    public boolean away() {
        return idleMinutes >= AWAY_MINUTES;
    }

    public int idleMinutes() {
        return idleMinutes;
    }

    /** The farming factor of where the player is now (around their last kill until they move on). */
    public double areaFactor() {
        if (Double.isNaN(lastX)) return 1;
        double n = 0, r2 = (double) FARM_RADIUS * FARM_RADIUS;
        for (double[] k : kills) {
            double dx = k[1] - lastX, dz = k[2] - lastZ;
            if (k[0] >= activeIndex - AREA_WINDOW_MINUTES && dx * dx + dz * dz <= r2) n += 1;
        }
        return factor(n);
    }
}
