package mn.suld.api.balance;

import mn.suld.api.progression.LevelCurve;

/** Non-kill EXP and coins of progression v2, all measured in the current level's need. */
public final class Rewards {

    private Rewards() {
    }

    /** Daily login: 2 % of a level per streak day / 7 (day 7 = 2 % of a level). */
    public static long loginExp(LevelCurve c, int day, int level) {
        return Math.round(0.02 * Math.max(1, day) * Balance.need(c, level) / 7.0);
    }

    public static long loginCoins(int day) {
        int d = Math.max(1, day);
        return 40L * d + (d == 7 ? 200 : 0);
    }

    /** One of the three daily tasks: 4 % of a level and 40 + 8·L coins. */
    public static long dailyTaskExp(LevelCurve c, int level) {
        return Math.round(0.04 * Balance.need(c, level));
    }

    public static long dailyTaskCoins(int level) {
        return 40 + 8L * Math.max(1, level);
    }

    /** First visit of a region: 5 % of a level at the region's minimum. */
    public static long regionDiscoveryExp(LevelCurve c, int regionMinLevel) {
        return Math.round(0.05 * Balance.need(c, regionMinLevel));
    }

    /** First visit of a landmark (area, ovoo, hidden place): 2 % of a level at its level × the gap factor. */
    public static long landmarkExp(LevelCurve c, int landmarkLevel, int playerLevel) {
        return Math.round(0.02 * Balance.need(c, landmarkLevel) * ExpRules.gapFactor(landmarkLevel - playerLevel));
    }

    /** Dungeon completion: 4 % of a level at the dungeon's content level (min + 3), coins 60 + 12·that level. */
    public static long dungeonExp(LevelCurve c, int contentLevel) {
        return Math.round(0.04 * Balance.need(c, contentLevel));
    }

    public static long dungeonCoins(int contentLevel) {
        return 60 + 12L * contentLevel;
    }

    /** A story chapter: 35 % of a level at the chapter's level. */
    public static long chapterExp(LevelCurve c, int chapterLevel) {
        return Math.round(0.35 * Balance.need(c, chapterLevel));
    }

    /** A world event's payout per participant: 2 % of a level. */
    public static long worldEventExp(LevelCurve c, int level) {
        return Math.round(0.02 * Balance.need(c, level));
    }
}
