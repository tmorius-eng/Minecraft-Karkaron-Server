package mn.suld.api.balance;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * One player's timed boosts (an ovoo's blessing, a drink at the Silver Tree), by kind and source. Within a kind the
 * strongest live source counts and the others wait: two EXP blessings never add up. A new boost from the same source
 * replaces the old one. Thread-safe (read on the server thread, written from it).
 */
public final class TimedBoosts {

    /** What a boost raises: EXP gained, damage dealt, damage taken off (share), loot luck (percent points). */
    public enum Kind { EXP, DAMAGE, MITIGATION, LOOT }

    private record Boost(long until, double bonus) {
    }

    private final Map<Kind, Map<String, Boost>> boosts = new EnumMap<>(Kind.class);

    /** Sets {@code source}'s boost of {@code kind} until {@code untilMillis} (a bonus ≤ 0 or a past time removes it). */
    public synchronized void put(Kind kind, String source, long untilMillis, double bonus) {
        if (bonus <= 0) {
            Map<String, Boost> of = boosts.get(kind);
            if (of != null) of.remove(source);
            return;
        }
        boosts.computeIfAbsent(kind, k -> new HashMap<>()).put(source, new Boost(untilMillis, bonus));
    }

    /** The strongest boost of {@code kind} live at {@code now}, or 0. Expired ones are dropped. */
    public synchronized double strongest(Kind kind, long now) {
        Map<String, Boost> of = boosts.get(kind);
        if (of == null) return 0;
        of.values().removeIf(b -> now >= b.until());
        double best = 0;
        for (Boost b : of.values()) best = Math.max(best, b.bonus());
        return best;
    }

    /** When {@code source}'s boost of {@code kind} ends, or 0 when it has none live at {@code now}. */
    public synchronized long until(Kind kind, String source, long now) {
        Map<String, Boost> of = boosts.get(kind);
        Boost b = of == null ? null : of.get(source);
        return b == null || now >= b.until() ? 0 : b.until();
    }

    /** Whether no boost is live at {@code now}. */
    public synchronized boolean isEmpty(long now) {
        for (Kind k : Kind.values()) if (strongest(k, now) > 0) return false;
        return true;
    }
}
