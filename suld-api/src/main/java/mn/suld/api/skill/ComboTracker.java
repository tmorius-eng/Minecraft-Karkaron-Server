package mn.suld.api.skill;

import mn.suld.api.clazz.PlayerClass;

import java.util.Optional;

/**
 * One player's click combo in progress (pure; the plugin feeds it clicks with timestamps). A combo must start with
 * the class's first click (R for melee, L for the archer — so ordinary attacks or bow shots don't start combos),
 * has three clicks, and resets after {@link #WINDOW_MS} without a click.
 */
public final class ComboTracker {

    public static final long WINDOW_MS = 1_000;

    private final PlayerClass clazz;
    private final StringBuilder clicks = new StringBuilder(3);
    private long lastAt;

    public ComboTracker(PlayerClass clazz) {
        this.clazz = clazz;
    }

    public enum Kind { IGNORED, PROGRESS, COMPLETE }

    public record Result(Kind kind, String combo) {
    }

    /** Feed a click ('L' or 'R') at {@code now} ms. A completed combo resets the tracker. */
    public Result click(char c, long now) {
        if (clicks.length() > 0 && now - lastAt > WINDOW_MS) clicks.setLength(0);
        if (clicks.length() == 0 && c != Spell.firstClick(clazz)) return new Result(Kind.IGNORED, "");
        clicks.append(c);
        lastAt = now;
        String combo = clicks.toString();
        if (clicks.length() == 3) {
            clicks.setLength(0);
            return new Result(Kind.COMPLETE, combo);
        }
        return new Result(Kind.PROGRESS, combo);
    }

    /** Clicks so far (empty if none or expired). */
    public String current(long now) {
        if (clicks.length() > 0 && now - lastAt > WINDOW_MS) clicks.setLength(0);
        return clicks.toString();
    }

    public Optional<Spell> spellFor(String combo) {
        return Spell.byCombo(clazz, combo);
    }

    public PlayerClass clazz() {
        return clazz;
    }
}
