package mn.suld.api.clan;

/**
 * Clan level curve and level-based perks (the "social progression" layer).
 * Pure functions; the numbers are the single source of truth for HUD, commands and rewards.
 */
public final class ClanProgression {

    public static final int MAX_LEVEL = 10;
    private static final double BASE = 400.0;
    private static final double EXPONENT = 1.8;

    private ClanProgression() {
    }

    /** Total clan EXP needed to reach {@code level} (level 1 = 0). */
    public static long totalExpFor(int level) {
        if (level <= 1) return 0;
        int capped = Math.min(level, MAX_LEVEL);
        return Math.round(BASE * Math.pow(capped - 1, EXPONENT));
    }

    public static int levelFor(long totalExp) {
        int level = 1;
        while (level < MAX_LEVEL && totalExp >= totalExpFor(level + 1)) {
            level++;
        }
        return level;
    }

    /** EXP still needed for the next level; 0 at max level. */
    public static long expToNext(long totalExp) {
        int level = levelFor(totalExp);
        return level >= MAX_LEVEL ? 0 : totalExpFor(level + 1) - totalExp;
    }

    /** Member capacity: 10 at level 1, +2 per level (28 at level 10). */
    public static int capacity(int level) {
        return 10 + 2 * (Math.max(1, Math.min(level, MAX_LEVEL)) - 1);
    }

    /** Personal EXP bonus for members: +2% per clan level above 1 (max +18%). */
    public static double expBonus(int level) {
        return 0.02 * (Math.max(1, Math.min(level, MAX_LEVEL)) - 1);
    }
}
