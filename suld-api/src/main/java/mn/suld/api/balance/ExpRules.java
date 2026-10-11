package mn.suld.api.balance;

/** How much of a kill's EXP a player gets: level gap, party share, boost cap, catch-up. */
public final class ExpRules {

    /** Clan + relic + items together give at most +50 % EXP. */
    public static final double BOOST_CAP = 0.5;
    /** Catch-up: +50 % EXP while more than {@link #CATCH_UP_GAP} levels below the server's median active level. */
    public static final double CATCH_UP = 1.5;
    public static final int CATCH_UP_GAP = 10;
    /** Party members share a kill within this many blocks. */
    public static final double PARTY_RANGE = 48;

    private ExpRules() {
    }

    /**
     * Kill EXP by level gap (mob − player): +2.5 % per level above you (cap +20 % at +8), full value from −4 to 0,
     * −15 % per level below that, 10 % at −10 and below.
     */
    public static double gapFactor(int gap) {
        if (gap >= 8) return 1.20;
        if (gap > 0) return 1.0 + 0.025 * gap;
        if (gap >= -4) return 1.0;
        if (gap > -10) return 1.0 - 0.15 * (-gap - 4);
        return 0.10;
    }

    /** Mobs 10 or more levels below the player roll no gear. */
    public static boolean allowsGear(int gap) {
        return gap > -10;
    }

    /** Every party member in range gets (1 + 0.15·(n−1)) / n of each kill. */
    public static double partyShare(int members) {
        int n = Math.max(1, members);
        return (1.0 + 0.15 * (n - 1)) / n;
    }

    public static double capBoost(double bonus) {
        return Math.max(0, Math.min(BOOST_CAP, bonus));
    }

    public static double catchUp(int level, int serverMedianLevel) {
        return serverMedianLevel > 0 && level < serverMedianLevel - CATCH_UP_GAP ? CATCH_UP : 1.0;
    }
}
