package mn.suld.api.mastery;

import mn.suld.api.item.ItemType;

import java.util.Optional;

/**
 * Зэвсгийн ур чадвар (directive 2026-10-13 Phase 4, docs/MASTERY_AND_PROFESSIONS.md): five weapon tracks, levels 1–50,
 * fed by the damage the player deals to SÜLD mobs with that kind of weapon (never to a training dummy). Damage counts in
 * the player's own basic hits ({@link #points}), so a level-10 hit and a level-60 hit weigh the same. The power is small
 * by design (owner 2026-10-13: «бага хүч + нэр хүнд»): {@link #damageBonus} is +2.5 % at 50; the rest is the titles.
 * Pure value rules.
 */
public final class WeaponMastery {

    public static final int MAX_LEVEL = 50;
    /** Damage bonus per mastery level with that weapon kind: +0.05 % (+2.5 % at 50). */
    public static final double BONUS_PER_LEVEL = 0.0005;
    /** The levels that bring a title. */
    public static final int[] TITLE_LEVELS = {10, 25, 50};

    private WeaponMastery() {
    }

    /** The five tracks and the item types they follow. */
    public enum Track {
        SCIMITAR("Илд", ItemType.SWORD),
        COMPOSITE_BOW("Нум", ItemType.BOW),
        SHAMANIC_STAFF("Таяг", ItemType.STAFF),
        HAMMER("Алх", ItemType.AXE),
        LANCE("Жад", ItemType.SPEAR);

        private final String displayName;
        private final ItemType type;

        Track(String displayName, ItemType type) {
            this.displayName = displayName;
            this.type = type;
        }

        public String displayName() {
            return displayName;
        }

        public ItemType itemType() {
            return type;
        }

        /** The track of a weapon type, if it has one (only the five class weapon kinds do). */
        public static Optional<Track> of(ItemType t) {
            for (Track k : values()) if (k.type == t) return Optional.of(k);
            return Optional.empty();
        }

        /** «Илдний Мастер», «Нумны Мастер» … for the titles. */
        public String masterTitle() {
            return switch (this) {
                case SCIMITAR -> "Илдний";
                case COMPOSITE_BOW -> "Нумны";
                case SHAMANIC_STAFF -> "Таягийн";
                case HAMMER -> "Алхны";
                case LANCE -> "Жадны";
            };
        }
    }

    /** Mastery points level {@code level} needs to reach the next: round(40 · L^1.5) (≈ 270 000 basic hits to 50). */
    public static long need(int level) {
        if (level >= MAX_LEVEL) return 0;
        return Math.round(40 * Math.pow(Math.max(1, level), 1.5));
    }

    /** Damage dealt as mastery points: {@code damage ÷ attack}, the player's own basic hit (never negative). */
    public static double points(double damage, double attack) {
        if (!(damage > 0) || !(attack > 0)) return 0;
        return damage / attack;
    }

    /**
     * The state that {@code total} points ever earned make (storage keeps the total: adding to it is the same in any
     * order, so concurrent writes commute).
     */
    public static State fromTotal(double total) {
        return State.START.add(total);
    }

    /** The points ever earned that make {@code s} (the inverse of {@link #fromTotal}, below the top). */
    public static double totalOf(State s) {
        double t = s.points();
        for (int l = 1; l < s.level(); l++) t += need(l);
        return t;
    }

    /** +0.05 % damage per level with the weapon of that track. */
    public static double damageBonus(int level) {
        return BONUS_PER_LEVEL * Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** The title a level brings («Илдний Шавь» at 10, «Илдний Мэргэжилтэн» at 25, «Илдний Мастер» at 50), or null. */
    public static String title(Track t, int level) {
        return switch (level) {
            case 10 -> t.masterTitle() + " Шавь";
            case 25 -> t.masterTitle() + " Мэргэжилтэн";
            case 50 -> t.masterTitle() + " Мастер";
            default -> null;
        };
    }

    /**
     * A track's state: the level (1–50) and the points toward the next one. A value type: {@link #add} returns the new
     * state (several levels at once when the points allow it); at 50 the points stop.
     */
    public record State(int level, double points) {

        public static final State START = new State(1, 0);

        public State {
            level = Math.max(1, Math.min(MAX_LEVEL, level));
            points = level >= MAX_LEVEL ? 0 : Math.max(0, points);
        }

        public State add(double pts) {
            if (!(pts > 0) || level >= MAX_LEVEL) return this;
            int l = level;
            double p = points + pts;
            while (l < MAX_LEVEL && p >= need(l)) {
                p -= need(l);
                l++;
            }
            return new State(l, p);
        }

        /** Share of the way to the next level, 0..1 (1 at 50). */
        public double progress() {
            return level >= MAX_LEVEL ? 1 : Math.min(1, points / need(level));
        }
    }
}
