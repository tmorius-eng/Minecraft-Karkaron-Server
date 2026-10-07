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
 * No signal for {@value #AWAY_MINUTES} minutes makes the player "away". More than {@value #AREA_KILLS} kills in one
 * 64-block area during the last 30 active minutes is area fatigue: armour XP stops until they move on.
 */
public final class ActivityTracker {

    public static final int MACRO_INTERVALS = 120;
    public static final int MACRO_JITTER_MS = 10;
    public static final int DAMAGE_IN_MINUTES = 3;
    public static final int AWAY_MINUTES = 15;
    public static final double MOVE_BLOCKS = 6;
    public static final long MOVE_SAMPLE_MS = 5_000;
    public static final int AREA_KILLS = 150;
    public static final int AREA_WINDOW_MINUTES = 30;
    public static final int AREA_CELL = 64;

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
    private long lastCell = Long.MIN_VALUE;
    private final Deque<long[]> kills = new ArrayDeque<>(); // {active minute index, cell}

    public void signal(ActivitySignal s) {
        signals.add(s);
    }

    /** A melee swing / hit, for the auto-clicker check (and a combat signal). */
    public void attack(long nowMs) {
        if (lastAttack >= 0) intervals.add(nowMs - lastAttack);
        lastAttack = nowMs;
        signals.add(ActivitySignal.DAMAGE_DEALT);
    }

    /** A kill at a position (combat signal + area fatigue). */
    public void kill(double x, double z) {
        signals.add(ActivitySignal.KILL);
        long cell = cell(x, z);
        lastCell = cell;
        kills.addLast(new long[] {activeIndex, cell});
        if (kills.size() > 4 * AREA_KILLS) kills.removeFirst();
    }

    /**
     * A position sample, at most one per {@value #MOVE_SAMPLE_MS} ms (extra samples are ignored). {@code passive}
     * marks movement that is not the player's own (vehicle on a rail, water current, elytra glide): it never counts.
     */
    public void move(double x, double z, boolean passive, long nowMs) {
        if (nowMs - lastSample < MOVE_SAMPLE_MS) return;
        lastSample = nowMs;
        if (passive) return;
        lastCell = cell(x, z); // area fatigue follows the player: moving on ends it
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

    /** Too many kills in the area of the latest kill during the last 30 active minutes. */
    public boolean areaFatigued() {
        if (lastCell == Long.MIN_VALUE) return false;
        int n = 0;
        for (long[] k : kills) if (k[1] == lastCell && k[0] >= activeIndex - AREA_WINDOW_MINUTES) n++;
        return n > AREA_KILLS;
    }

    static long cell(double x, double z) {
        long cx = (long) Math.floor(x / AREA_CELL);
        long cz = (long) Math.floor(z / AREA_CELL);
        return (cx << 32) ^ (cz & 0xffffffffL);
    }
}
