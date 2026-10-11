package mn.suld.api.balance;

import mn.suld.api.progression.LevelCurve;

/**
 * Rested EXP: offline time fills a pool with 1.5 % of the current level per hour, up to 1.5 levels' worth. While the
 * pool lasts, kill EXP is doubled (the extra comes out of the pool). Logging in and out does not fill it faster:
 * it only grows with offline hours.
 */
public final class RestedPool {

    public static final double PER_HOUR = 0.015, CAP_LEVELS = 1.5;

    private RestedPool() {
    }

    public static long cap(LevelCurve c, int level) {
        return Math.round(CAP_LEVELS * Balance.need(c, level));
    }

    /** The pool after {@code offlineMillis} away. */
    public static long accrue(LevelCurve c, long pool, int level, long offlineMillis) {
        if (offlineMillis <= 0 || level >= c.maxLevel()) return Math.max(0, pool);
        double hours = offlineMillis / 3_600_000.0;
        long add = (long) Math.floor(PER_HOUR * Balance.need(c, level) * hours);
        return Math.min(cap(c, level), Math.max(0, pool) + add);
    }

    /** The bonus EXP a kill worth {@code killExp} takes from {@code pool}: at most the kill's own EXP. */
    public static long bonus(long pool, long killExp) {
        return Math.max(0, Math.min(pool, killExp));
    }
}
