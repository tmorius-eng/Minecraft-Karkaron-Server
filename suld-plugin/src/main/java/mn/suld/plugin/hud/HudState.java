package mn.suld.plugin.hud;

import mn.suld.api.clazz.PlayerClass;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Everything the HUD shows for one player at one moment: read from the live game by {@link HudService}, drawn by
 * {@link HudComposer}. Pure data (no Bukkit), so the composition is unit-tested with exact values.
 *
 * @param hp         current health (hit points)
 * @param maxHp      maximum health
 * @param absorption absorption (extra) health
 * @param recentHp   health shown a moment ago (the pale "damage chip" between it and {@code hp}); = hp when none
 * @param lowBar     second-row left bar: food, mount health or breath (whichever applies)
 * @param resource   class resource (rage, focus, …), max and the class (colour); clazz null = no class yet
 * @param level      SÜLD level; expFraction progress into it; maxLevel when no more levels
 * @param slots      the class's four spells in slot order, then the ultimate (if the build has one)
 * @param buffs      status icons, most important first (at most {@link HudComposer#MAX_BUFFS} are drawn)
 * @param line       the text line above the panel (combo in progress, a notice, or the status chips)
 */
public record HudState(double hp, double maxHp, double absorption, double recentHp,
                       LowBar lowBar,
                       int resource, int resourceMax, @Nullable PlayerClass clazz,
                       int level, double expFraction, boolean maxLevel,
                       List<Slot> slots, List<Buff> buffs, List<Run> line) {

    /** Which bar the second row's left side shows. */
    public enum LowKind { FOOD, MOUNT, AIR }

    public record LowBar(LowKind kind, double value, double max) {
    }

    public enum SlotState { READY, COOLDOWN, LOCKED, NO_RESOURCE }

    /**
     * One spell slot.
     *
     * @param numeral   1..4, or 0 for the ultimate
     * @param seconds   cooldown left (shown while COOLDOWN); the unlock level while LOCKED (0 = unknown)
     * @param color     the class colour of the numeral when ready
     */
    public record Slot(int numeral, SlotState state, double seconds, TextColor color) {
    }

    /**
     * A status icon (a {@code HudGlyphs.ICON_*} name) and an optional short label (seconds left, a count).
     */
    public record Buff(String icon, String label) {
    }

    /** A coloured run of the text line. */
    public record Run(String text, TextColor color) {
    }

    public HudState {
        slots = List.copyOf(slots);
        buffs = List.copyOf(buffs);
        line = List.copyOf(line);
    }
}
