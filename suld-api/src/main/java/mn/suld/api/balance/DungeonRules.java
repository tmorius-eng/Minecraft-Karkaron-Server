package mn.suld.api.balance;

import java.util.List;

/** Dungeon rewards of progression v2: the loot band, repeat fatigue and the carry rule. */
public final class DungeonRules {

    /** How many of the player's last dungeon clears repeat fatigue looks at. */
    public static final int RECENT = 8;

    private DungeonRules() {
    }

    /** Loot is rolled at the player's level, clamped to the dungeon's band. */
    public static int lootLevel(int min, int max, int playerLevel) {
        return Math.max(min, Math.min(max, playerLevel));
    }

    /** −15 % per clear of the same dungeon among the player's last {@link #RECENT} clears (floor 25 %). */
    public static double repeatFactor(List<String> recentClears, String dungeonId) {
        int same = 0;
        int from = Math.max(0, recentClears.size() - RECENT);
        for (int i = from; i < recentClears.size(); i++) if (recentClears.get(i).equals(dungeonId)) same++;
        return Math.max(0.25, 1.0 - 0.15 * same);
    }

    /** Above the dungeon's max level ×0.1; 10+ levels below the party's top ×0.5 (being carried). */
    public static double carryFactor(int memberLevel, int partyTopLevel, int dungeonMaxLevel) {
        if (memberLevel > dungeonMaxLevel) return 0.1;
        return partyTopLevel - memberLevel > 10 ? 0.5 : 1.0;
    }

    /** Enrage timer: 3 minutes for the first dungeon, +15 s per rung, at most 5 m 15 s. */
    public static double enrageSeconds(int ladderIndex) {
        return 180 + 15 * Math.max(0, Math.min(ladderIndex, 9));
    }
}
