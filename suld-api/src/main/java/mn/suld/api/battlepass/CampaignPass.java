package mn.suld.api.battlepass;

import mn.suld.api.achievement.Deed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * «Хуралдайн Зам», the season's pass (docs/ACHIEVEMENTS_AND_PASS.md): {@link #TIERS} tiers of {@link #TIER_POINTS}
 * points each, earned by playing (deeds and achievements). Two tracks of rewards: the free one for everyone, the heroic
 * one for those who opened it this season. Every reward on both is a cosmetic: never an item, a level or power.
 */
public final class CampaignPass {

    /** Tiers in a season. */
    public static final int TIERS = 50;
    /** Points a tier. */
    public static final long TIER_POINTS = 1_000;
    /** The most points a player's deeds give in one day (an achievement's own points are not capped). */
    public static final long DAILY_CAP = 2_500;

    private CampaignPass() {
    }

    /** The two tracks of rewards. */
    public enum Track {
        FREE("Чөлөөт зам"),
        HEROIC("Баатарлаг зам");

        private final String displayName;

        Track(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** A reward: the cosmetic {@code cosmetic} on {@code track} at {@code tier}. */
    public record Reward(int tier, Track track, String cosmetic) {
        public Reward {
            if (tier < 1 || tier > TIERS) throw new IllegalArgumentException("tier " + tier);
            if (track == null || cosmetic == null || cosmetic.isBlank()) throw new IllegalArgumentException("a reward needs a track and a cosmetic");
        }
    }

    /** A season's rewards, at most one per tier and track. */
    public record Layout(List<Reward> rewards) {
        public Layout {
            Set<String> seen = new HashSet<>();
            for (Reward r : rewards) {
                if (!seen.add(r.track() + "/" + r.tier())) throw new IllegalArgumentException("two rewards at " + r.track() + " tier " + r.tier());
            }
            List<Reward> sorted = new ArrayList<>(rewards);
            sorted.sort(Comparator.comparingInt(Reward::tier).thenComparing(Reward::track));
            rewards = List.copyOf(sorted);
        }

        public java.util.Optional<Reward> at(Track track, int tier) {
            return rewards.stream().filter(r -> r.track() == track && r.tier() == tier).findFirst();
        }
    }

    /** The tier {@code points} reach (0 before the first). */
    public static int tier(long points) {
        return (int) Math.min(TIERS, Math.max(0, points) / TIER_POINTS);
    }

    /** Points still needed for the next tier (0 at the last). */
    public static long toNext(long points) {
        int t = tier(points);
        return t >= TIERS ? 0 : (t + 1) * TIER_POINTS - Math.max(0, points);
    }

    /**
     * The rewards a player may claim now: reached, not claimed yet, and on the heroic track only once it is open.
     * {@code claimed} holds the claimed rewards as {@code TRACK/tier}.
     */
    public static List<Reward> claimable(Layout layout, long points, boolean heroicOpen, Set<String> claimed) {
        int reached = tier(points);
        List<Reward> out = new ArrayList<>();
        for (Reward r : layout.rewards()) {
            if (r.tier() > reached || claimed.contains(key(r))) continue;
            if (r.track() == Track.HEROIC && !heroicOpen) continue;
            out.add(r);
        }
        return out;
    }

    /** A reward's claim key: {@code TRACK/tier}. */
    public static String key(Reward r) {
        return r.track().name() + "/" + r.tier();
    }

    // ---- points --------------------------------------------------------------------------------------------------

    private static final Map<Deed, Long> RATE = new EnumMap<>(Deed.class);

    static {
        RATE.put(Deed.LEVEL, 50L);          // a level gained
        RATE.put(Deed.CHAPTER, 200L);       // a chapter finished
        RATE.put(Deed.DUNGEON_CLEAR, 120L); // a clear
        RATE.put(Deed.HEROIC_TIMED, 150L);  // a heroic key level gained in time
        RATE.put(Deed.SITE, 100L);
        RATE.put(Deed.OVOO, 40L);
        RATE.put(Deed.REGION, 100L);
        RATE.put(Deed.MOB_KILL, 2L);
        RATE.put(Deed.WORLD_BOSS, 300L);
        RATE.put(Deed.WEAPON_MASTERY, 20L); // a mastery level gained
        RATE.put(Deed.CRAFT_LEVEL, 20L);    // a craft level gained
        RATE.put(Deed.GATHER, 4L);
        RATE.put(Deed.ASCENSION, 500L);     // a rank gained
        RATE.put(Deed.CLASS_MASTERS, 300L);
        RATE.put(Deed.DUEL_WIN, 25L);
        RATE.put(Deed.TRIAL_WIN, 60L);
        RATE.put(Deed.CAMPAIGN_TIER, 250L); // a campaign tier gained
        RATE.put(Deed.VOTE, 100L);
        RATE.put(Deed.CLAN_JOIN, 100L);
        RATE.put(Deed.DAILY_TASK, 150L);
        RATE.put(Deed.SIDE_QUEST, 60L);     // a side quest handed in (a chapter is 200, a daily task 150)
        RATE.put(Deed.TREE_DRINK, 0L);      // a daily drink: an achievement, never pass points
    }

    /** The points a deed's rise of {@code delta} gives (before the daily cap). */
    public static long points(Deed deed, long delta) {
        return delta <= 0 ? 0 : RATE.getOrDefault(deed, 0L) * delta;
    }

    /** Of {@code add} points earned by deeds, how many fit under the daily cap when {@code today} were given already. */
    public static long underCap(long today, long add) {
        return Math.max(0, Math.min(add, DAILY_CAP - Math.max(0, today)));
    }
}
