package mn.suld.api.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Daily tasks (Өдрийн даалгавар): three hunting tasks per player per day, picked deterministically from the mobs of
 * the regions the player's level can handle (so they need no storage of their own — only the progress is stored).
 * A task pays coins and EXP the moment its count is reached.
 */
public final class DailyTasks {

    public static final int COUNT = 3;

    private DailyTasks() {
    }

    /** A huntable mob of a region, with the region's level band. */
    public record Prey(String mobId, String mobName, String regionName, int mobLevel, long mobExp, int regionMinLevel) {
    }

    public record Task(Prey prey, int count, long coins, long exp) {
    }

    /** The day's tasks for a player; stable for the same (player, day, level band). */
    public static List<Task> tasks(UUID player, long epochDay, int level, List<Prey> pool) {
        List<Prey> fit = new ArrayList<>();
        for (Prey p : pool) if (p.regionMinLevel() <= level + 2) fit.add(p);
        if (fit.isEmpty()) fit.addAll(pool);
        if (fit.isEmpty()) return List.of();
        int band = Math.max(1, level / 5); // re-roll only when the player moves into a new band of five levels
        Random r = new Random(player.getMostSignificantBits() ^ player.getLeastSignificantBits() ^ (epochDay * 31 + band));
        List<Task> out = new ArrayList<>();
        List<Prey> bag = new ArrayList<>(fit);
        for (int i = 0; i < COUNT; i++) {
            if (bag.isEmpty()) bag.addAll(fit);
            Prey p = bag.remove(r.nextInt(bag.size()));
            int count = 5 + r.nextInt(8); // 5..12
            // progression v2 (Rewards): 4 % of the (pinned) level and 40 + 8·L coins per task
            long coins = mn.suld.api.balance.Rewards.dailyTaskCoins(level);
            long exp = mn.suld.api.balance.Rewards.dailyTaskExp(mn.suld.api.balance.Balance.curve(), level);
            out.add(new Task(p, count, coins, exp));
        }
        return out;
    }

    /** The level the day's tasks were rolled at ("12|3,0,12" stores 12), or {@code fallback} on a fresh day. */
    public static int pinnedLevel(String stored, int fallback) {
        int bar = stored == null ? -1 : stored.indexOf('|');
        if (bar <= 0) return fallback;
        try {
            return Math.max(1, Integer.parseInt(stored.substring(0, bar).trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Progress together with the pinned level, so levelling up mid-day never re-rolls tasks that are half done. */
    public static String store(int pinnedLevel, int[] progress) {
        return pinnedLevel + "|" + format(progress);
    }

    /** Parse stored progress ("12|3,0,12" or "3,0,12"); anything malformed counts as no progress. */
    public static int[] progress(String stored) {
        int[] out = new int[COUNT];
        if (stored == null || stored.isBlank()) return out;
        int bar = stored.indexOf('|');
        String[] parts = (bar >= 0 ? stored.substring(bar + 1) : stored).split(",");
        for (int i = 0; i < COUNT && i < parts.length; i++) {
            try {
                out[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    public static String format(int[] progress) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            if (i > 0) sb.append(',');
            sb.append(i < progress.length ? progress[i] : 0);
        }
        return sb.toString();
    }

    /**
     * Count a kill: returns the index of the task it completed (paid now), or -1. Advances the first unfinished task
     * hunting {@code mobId}; finished tasks are never advanced again.
     */
    public static int kill(List<Task> tasks, int[] progress, String mobId) {
        for (int i = 0; i < tasks.size() && i < progress.length; i++) {
            Task t = tasks.get(i);
            if (!t.prey().mobId().equals(mobId) || progress[i] >= t.count()) continue;
            progress[i]++;
            return progress[i] >= t.count() ? i : -1;
        }
        return -2; // no task hunts this mob
    }
}
