package mn.suld.api.death;

import mn.suld.api.region.OvooCircle;

/**
 * «Овооны цацал»: a soul's quiet way back (2026-10-13), the alternative to «Тэнгэрийн сорил» for a soul that will not
 * fight. At an ovoo the soul walks sunwise three times around the cairn ({@link OvooCircle}: 2–9 blocks out, clockwise
 * seen from above, a jump does not count) and then stays for the offering until the ritual has lasted at least
 * {@code death.ritual-seconds}; then it lives again (state RECOVERED, actor "ovoo", the wound still applies). Leaving
 * the ring breaks it, and it starts over. Pure: one per soul, fed the soul's positions.
 */
public final class OvooRitual {

    /** Where a ritual stands after a step. */
    public enum Step {
        /** Outside the ring, nothing started. */
        IDLE,
        /** Walking the three rounds. */
        WALKING,
        /** The rounds are walked; the offering lasts until the ritual's time. */
        OFFERING,
        /** Complete: the soul lives again. */
        DONE,
        /** Left the ring before it was complete: it starts over. */
        BROKEN
    }

    private final long minMillis;
    private final OvooCircle circle = new OvooCircle();
    private long startedAt = -1;
    private boolean rounds;
    private boolean done;

    /** A ritual that lasts at least {@code seconds}. */
    public OvooRitual(int seconds) {
        this.minMillis = Math.max(0, seconds) * 1000L;
    }

    /** One position of the soul relative to the cairn (dx east, dz south) at {@code now} (epoch ms). */
    public Step step(double dx, double dz, long now) {
        if (done) return Step.DONE;
        double r = Math.hypot(dx, dz);
        boolean out = rounds ? r > OvooCircle.MAX_R : r < OvooCircle.MIN_R || r > OvooCircle.MAX_R; // the offering may stand at the cairn
        if (out) {
            boolean was = startedAt >= 0;
            reset();
            return was ? Step.BROKEN : Step.IDLE;
        }
        if (startedAt < 0) startedAt = now;
        if (!rounds && circle.step(dx, dz)) rounds = true;
        if (rounds && now - startedAt >= minMillis) {
            done = true;
            return Step.DONE;
        }
        return rounds ? Step.OFFERING : Step.WALKING;
    }

    /** Back to the start. */
    public void reset() {
        circle.reset();
        startedAt = -1;
        rounds = false;
        done = false;
    }

    public boolean started() {
        return startedAt >= 0;
    }

    public boolean done() {
        return done;
    }

    /** Sunwise rounds walked so far, 0–3 (3 once the rounds are done). */
    public double rounds() {
        return rounds || done ? OvooCircle.TURNS : Math.max(0, Math.min(OvooCircle.TURNS, circle.turns()));
    }

    /** Seconds since the ritual started (0 before). */
    public long elapsedSeconds(long now) {
        return startedAt < 0 ? 0 : Math.max(0, (now - startedAt) / 1000);
    }

    /** The ritual's least length in seconds. */
    public long seconds() {
        return minMillis / 1000;
    }
}
