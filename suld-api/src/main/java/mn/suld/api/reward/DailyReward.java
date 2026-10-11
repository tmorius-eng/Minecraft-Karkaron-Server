package mn.suld.api.reward;

/**
 * The daily login reward: claimable once per calendar day; claiming on consecutive days builds a streak of up to
 * seven days (day 7 pays the most), and a missed day starts again at day 1. Pure — the plugin supplies "today" as
 * an epoch day in the server's time zone and stores {@code lastDay}/{@code streak}.
 */
public final class DailyReward {

    public static final int CYCLE = 7;

    private DailyReward() {
    }

    /** Outcome of a claim attempt. {@code day} is the streak day being paid (1..7) or, if not allowed, the next one. */
    public record Claim(boolean allowed, int day, long coins, long exp) {
    }

    /** The streak day a claim made {@code today} would pay (1..7). */
    public static int nextDay(long lastDay, int streak, long today) {
        if (lastDay == today) return Math.max(1, Math.min(CYCLE, streak));
        if (lastDay == today - 1 && streak > 0) return streak % CYCLE + 1;
        return 1;
    }

    public static boolean claimable(long lastDay, long today) {
        return lastDay != today;
    }

    /** Coins for a streak day. */
    public static long coins(int day) {
        return 40L * day + (day == CYCLE ? 200 : 0);
    }

    /** EXP for a streak day: 2 % of the player's level per streak day / 7 on the progression v2 curve (Rewards). */
    public static long exp(int day, int level) {
        // at least 25 EXP per streak day, so the first levels' claims are not a single digit
        return Math.max(25L * day, mn.suld.api.balance.Rewards.loginExp(mn.suld.api.balance.Balance.curve(), day, level));
    }

    public static Claim claim(long lastDay, int streak, long today, int level) {
        int day = nextDay(lastDay, streak, today);
        if (!claimable(lastDay, today)) return new Claim(false, day, 0, 0);
        return new Claim(true, day, coins(day), exp(day, level));
    }
}
