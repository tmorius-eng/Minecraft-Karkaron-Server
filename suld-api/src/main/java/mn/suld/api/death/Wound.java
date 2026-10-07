package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;

/**
 * The death wound (docs/DEATH_AND_RECOVERY.md): each recovered death adds a step that lowers the effective stats of the
 * class gear; a step heals after {@code woundHealMinutes} of active play. A value type — every change returns a new one.
 *
 * @param stacks      open wound steps (0 = healthy)
 * @param healMinutes active minutes accumulated toward healing the next step
 */
public record Wound(int stacks, double healMinutes) {

    public static final Wound NONE = new Wound(0, 0);

    public Wound {
        stacks = Math.max(0, stacks);
        healMinutes = Math.max(0, healMinutes);
    }

    /** The maximum number of steps the settings allow. */
    public static int maxStacks(DeathSettings s) {
        return s.woundPerDeath() <= 0 ? 0 : (int) Math.round(s.woundMax() / s.woundPerDeath());
    }

    /** One more step (capped); the healing progress starts over. */
    public Wound add(DeathSettings s) {
        return new Wound(Math.min(maxStacks(s), stacks + 1), 0);
    }

    /** {@code minutes} more active play: whole steps heal, the rest is kept. */
    public Wound heal(DeathSettings s, double minutes) {
        if (stacks == 0 || minutes <= 0) return stacks == 0 ? NONE : this;
        double m = healMinutes + minutes;
        int st = stacks;
        while (st > 0 && m >= s.woundHealMinutes()) {
            m -= s.woundHealMinutes();
            st--;
        }
        return new Wound(st, st == 0 ? 0 : m);
    }

    /** Multiplier of the class gear's stats: 1 − min(max, stacks × perDeath). */
    public double factor(DeathSettings s) {
        return 1.0 - Math.min(s.woundMax(), stacks * s.woundPerDeath());
    }

    /** Whole percent shown to the player (e.g. 10 for a two-step wound at 5 %). */
    public int percent(DeathSettings s) {
        return (int) Math.round(100 * (1 - factor(s)));
    }
}
