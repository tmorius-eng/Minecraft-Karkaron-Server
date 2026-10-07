package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;

/**
 * How long a death keeps a player out of normal play, in real-world time, by level (docs/DEATH_AND_RECOVERY.md).
 * The approved curve is {@link Curve#GEOMETRIC}: {@code min · (max/min)^((L−1)/59)} — 5 min at level 1, doubling about
 * every 7 levels, 24 h at 60 and in Ascension. Pure and stateless.
 */
public final class DeathLock {

    private DeathLock() {
    }

    /** The curves the simulation compared ({@code docs/PROGRESSION_SIMULATION.md} §Death lock curves). */
    public enum Curve { GEOMETRIC, LINEAR, STEP, FIXED }

    /** Lock length in minutes for a death at {@code level} (Ascension counts as the top of the curve). */
    public static double minutes(DeathSettings s, int level, int ascension) {
        double min = s.lockMinMinutes(), max = s.lockMaxMinutes();
        if (ascension > 0) return max;
        double f = Math.max(0, Math.min(1, (level - 1) / 59.0));
        return switch (s.lockCurve()) {
            case GEOMETRIC -> min <= 0 ? max * f : min * Math.pow(max / min, f);
            case LINEAR -> min + (max - min) * f;
            case STEP -> level < 10 ? min : Math.min(max, level < 20 ? 30 : level < 30 ? 120 : level < 40 ? 360
                    : level < 50 ? 720 : level < 60 ? 1080 : max);
            case FIXED -> min;
        };
    }

    /** Lock length in milliseconds (at least one second). */
    public static long millis(DeathSettings s, int level, int ascension) {
        return Math.max(1000L, Math.round(minutes(s, level, ascension) * 60_000));
    }

    /** "3 цаг 05 мин", "12 мин 30 сек" — for the death screen and the admin commands. */
    public static String format(long millis) {
        long sec = Math.max(0, (millis + 999) / 1000);
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return h + " цаг " + String.format("%02d", m) + " мин";
        if (m > 0) return m + " мин " + String.format("%02d", s) + " сек";
        return s + " сек";
    }
}
