package mn.suld.api.death;

/**
 * «Тэнгэрийн сорил»: the active way out of a death lock (external audit FIND-05). A soul calls the spirit wolves and
 * fights them; beating all of them in time ends the lock at once (the wound still applies). Pure rules, no Bukkit.
 *
 * @param wolves   how many spirit wolves must fall
 * @param seconds  time limit
 * @param cooldownSeconds wait after a failed trial before the next one
 */
public record DeathTrial(int wolves, int seconds, int cooldownSeconds) {

    public static final DeathTrial DEFAULT = new DeathTrial(3, 180, 300);

    public enum Outcome { RUNNING, WON, TIMED_OUT, FALLEN, LEFT }

    public DeathTrial {
        if (wolves < 1 || seconds < 10 || cooldownSeconds < 0) throw new IllegalArgumentException("bad trial");
    }

    /** The trial's state after {@code killed} wolves, {@code elapsedSeconds}, and whether the soul fell or left. */
    public Outcome outcome(int killed, double elapsedSeconds, boolean fallen, boolean left) {
        if (left) return Outcome.LEFT;
        if (fallen) return Outcome.FALLEN;
        if (killed >= wolves) return Outcome.WON;
        return elapsedSeconds >= seconds ? Outcome.TIMED_OUT : Outcome.RUNNING;
    }

    /** Seconds until a soul may try again after a failed trial that ended {@code sinceEndSeconds} ago (0 = now). */
    public long waitSeconds(double sinceEndSeconds) {
        return (long) Math.max(0, Math.ceil(cooldownSeconds - sinceEndSeconds));
    }

    /** A spirit wolf's health and bite for a soul of {@code level} (a fair 3-minute fight at every level). */
    public static double wolfHealth(int level) {
        return 10 + 1.6 * Math.max(1, level);
    }

    /** Hits of the soul's own attack that a spirit wolf takes (so the fight is the same at every level and gear). */
    public static final double WOLF_HITS = 5;

    /**
     * A spirit wolf's health against a soul whose SÜLD attack is {@code attack}: about {@link #WOLF_HITS} of its
     * hits (crits and spells make it shorter), never less than {@link #wolfHealth(int)}.
     */
    public static double wolfHealth(int level, double attack) {
        return Math.max(wolfHealth(level), WOLF_HITS * Math.max(0, attack));
    }

    public static double wolfBite(int level) {
        return 2 + 0.25 * Math.max(1, level);
    }
}
