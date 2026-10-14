package mn.suld.api.achievement;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's deeds: every {@code DEED|subject} key and its value (a count, a best value, or 1 for seen). Not
 * thread-safe: the plugin keeps one per online player on the main thread.
 */
public final class DeedBook {

    private final Map<String, Long> values = new HashMap<>();

    public DeedBook() {
    }

    /** A book restored from storage. */
    public DeedBook(Map<String, Long> stored) {
        stored.forEach((k, v) -> {
            if (v != null && v > 0) values.put(k, v);
        });
    }

    /**
     * Records {@code amount} of {@code deed} for {@code subject} as its {@link Deed.Keep} says. Returns by how much the
     * stored value rose (0 when nothing changed: a best not beaten, a deed already seen, a non-positive amount).
     */
    public long record(Deed deed, String subject, long amount) {
        if (amount <= 0) return 0;
        String key = deed.key(subject);
        long now = values.getOrDefault(key, 0L);
        long next = switch (deed.keep()) {
            case COUNT -> now + amount;
            case BEST -> Math.max(now, amount);
            case SEEN -> 1;
        };
        if (next == now) return 0;
        values.put(key, next);
        return next - now;
    }

    public long value(Deed deed, String subject) {
        return values.getOrDefault(deed.key(subject), 0L);
    }

    /** The number {@code c} is measured by now. */
    public long progress(Criterion c) {
        String prefix = c.deed().name() + "|";
        long total = 0, distinct = 0, best = 0, each = 0;
        if (c.subjects().isEmpty()) {
            for (Map.Entry<String, Long> e : values.entrySet()) {
                if (!e.getKey().startsWith(prefix)) continue;
                long v = e.getValue();
                total += v;
                distinct++;
                best = Math.max(best, v);
            }
        } else {
            for (String s : c.subjects()) {
                long v = values.getOrDefault(prefix + s, 0L);
                total += v;
                if (v > 0) distinct++;
                best = Math.max(best, v);
                if (v >= c.target()) each++;
            }
        }
        return switch (c.measure()) {
            case TOTAL -> total;
            case DISTINCT -> distinct;
            case BEST -> best;
            case EACH -> each;
        };
    }

    public boolean met(Criterion c) {
        return progress(c) >= c.goal();
    }

    /** Every key and value, for storage. */
    public Map<String, Long> snapshot() {
        return Map.copyOf(values);
    }
}
