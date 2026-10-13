package mn.suld.api.training;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One trainee's numbers on the training grounds (docs/TRAINING_GROUNDS.md): the damage of the last ten seconds as DPS,
 * the total, the biggest hit, hits and critical hits, the share of each source (a spell, a passive, an ultimate, basic
 * hits, arrows, burning) and how often each passive fired against its designed chance. Pure and clock-free: every call
 * takes the time in milliseconds. Owned by one thread (the server's main thread).
 */
public final class TrainingStats {

    /** The DPS window. */
    public static final long WINDOW_MS = 10_000;
    /** A pause this long starts a new fight: the DPS then counts from the next hit, not over an empty window. */
    public static final long PAUSE_MS = 5_000;

    /** Where a hit came from. */
    public enum Kind {
        BASIC("Энгийн цохилт"), ARROW("Сум"), SPELL("Ид шид"), PASSIVE("Идэвхгүй чадвар"), ULTIMATE("Их чадвар"),
        BURN("Түлэгдэлт"), OTHER("Бусад");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        /** Hits of this kind can be critical (the SÜLD hit formula rolls crits for weapon hits only). */
        public boolean critable() {
            return this == BASIC || this == ARROW;
        }
    }

    /** One source: its kind and the name a player knows it by (a spell's, a passive's, an ultimate's). */
    public record Source(Kind kind, String name) {
        public Source {
            Objects.requireNonNull(kind, "kind");
            name = name == null || name.isBlank() ? kind.label : name;
        }

        public static Source of(Kind kind) {
            return new Source(kind, kind.label);
        }
    }

    /** A source's part of the total. */
    public record Share(Source source, double damage, int hits, double percent) {
    }

    /**
     * How often a passive fired: {@code rolls} are the times its trigger happened while it was ready, {@code fired} how
     * many of them it went off, {@code designChance} its chance in the data (percent).
     */
    public record PassiveRate(String name, int rolls, int fired, double designChance) {
        /** Percent of the rolls that fired (0 before the first roll). */
        public double observed() {
            return rolls == 0 ? 0 : 100.0 * fired / rolls;
        }
    }

    private record Hit(long at, double amount) {
    }

    private final ArrayDeque<Hit> window = new ArrayDeque<>();
    private double windowSum;
    private long fightStart = -1, lastHit = -1;
    private double total, maxHit;
    private Source maxSource;
    private int hits, critable, crits;
    private final Map<Source, double[]> sources = new LinkedHashMap<>();
    private final Map<String, double[]> passives = new LinkedHashMap<>();

    /** A hit of {@code amount} (after every modifier) from {@code source} at {@code now}. */
    public void hit(long now, Source source, double amount, boolean crit) {
        if (!(amount > 0) || !Double.isFinite(amount) || source == null) return;
        if (lastHit < 0 || now - lastHit >= PAUSE_MS) {
            fightStart = now;
            window.clear();
            windowSum = 0;
        }
        lastHit = Math.max(lastHit, now);
        window.addLast(new Hit(now, amount));
        windowSum += amount;
        prune(now);
        total += amount;
        hits++;
        if (source.kind().critable()) {
            critable++;
            if (crit) crits++;
        }
        if (amount > maxHit) {
            maxHit = amount;
            maxSource = source;
        }
        double[] s = sources.computeIfAbsent(source, k -> new double[2]);
        s[0] += amount;
        s[1]++;
    }

    private void prune(long now) {
        while (!window.isEmpty() && now - window.peekFirst().at() >= WINDOW_MS) windowSum -= window.pollFirst().amount();
        if (window.isEmpty()) windowSum = 0;
    }

    /**
     * Damage per second over the last ten seconds, or since the fight began when that was more recently (at least one
     * second): a player sees their real rate from the first hits on, and 0 again ten seconds after the last.
     */
    public double dps(long now) {
        prune(now);
        if (window.isEmpty()) return 0;
        long span = Math.min(WINDOW_MS, now - fightStart);
        return windowSum * 1000.0 / Math.max(1000, span);
    }

    /** A passive's trigger happened while it was ready; {@code fired} = it went off. */
    public void passiveRolled(String name, double designChance, boolean fired) {
        if (name == null) return;
        double[] p = passives.computeIfAbsent(name, k -> new double[3]);
        p[0]++;
        if (fired) p[1]++;
        p[2] = designChance;
    }

    public double total() {
        return total;
    }

    public double maxHit() {
        return maxHit;
    }

    /** The source of the biggest hit, or null before the first. */
    public Source maxSource() {
        return maxSource;
    }

    public int hits() {
        return hits;
    }

    /** Percent of the weapon hits (basic hits and arrows) that were critical. */
    public double critRate() {
        return critable == 0 ? 0 : 100.0 * crits / critable;
    }

    public int crits() {
        return crits;
    }

    /** Every source with its part of the total, biggest first. */
    public List<Share> shares() {
        List<Share> out = new ArrayList<>();
        sources.forEach((k, v) -> out.add(new Share(k, v[0], (int) v[1], total <= 0 ? 0 : 100.0 * v[0] / total)));
        out.sort(Comparator.comparingDouble(Share::damage).reversed());
        return out;
    }

    /** Every passive that rolled, in the order they first did. */
    public List<PassiveRate> passives() {
        List<PassiveRate> out = new ArrayList<>();
        passives.forEach((k, v) -> out.add(new PassiveRate(k, (int) v[0], (int) v[1], v[2])));
        return out;
    }

    /** Back to nothing (the trainee asked for a fresh count, or changed class). */
    public void reset() {
        window.clear();
        windowSum = 0;
        fightStart = lastHit = -1;
        total = maxHit = 0;
        maxSource = null;
        hits = critable = crits = 0;
        sources.clear();
        passives.clear();
    }
}
