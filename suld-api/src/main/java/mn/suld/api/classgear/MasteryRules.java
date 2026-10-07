package mn.suld.api.classgear;

import mn.suld.api.mob.MobTier;

/**
 * Armour mastery (docs/MASTERY_SPEC.md "Class", docs/ARMOR_PROGRESSION.md "Armour mastery"): a long-term layer that
 * grows only with meaningful class activity — kills at or above the player's level band, class spells, dungeon clears
 * and first bosses, first discoveries and class objectives — never with time alone. Diminishing returns are by
 * activity, never by calendar: the level-gap rule, dungeon repeat fatigue, per-minute caps on spells and objectives,
 * and the farming factor on kills. Each rank also needs a milestone (player level ≥ 6 × rank; from rank 4, rank − 2
 * different dungeons cleared), so rank 10 needs level 60: out of reach in 7 days even at 10 h/day (simulation C2/C13).
 */
public final class MasteryRules {

    public static final int MAX_RANK = 10;
    /** Mastery XP for the cast of a class spell; at most {@link #CASTS_PER_MINUTE} count per minute. */
    public static final double XP_CAST = 0.5;
    public static final int CASTS_PER_MINUTE = 12;
    /** Mastery XP for a class objective (docs: per class); at most {@link #OBJECTIVES_PER_MINUTE} per minute. */
    public static final double XP_OBJECTIVE = 1;
    public static final int OBJECTIVES_PER_MINUTE = 10;
    public static final double XP_DUNGEON = 40;
    public static final double XP_FIRST_BOSS = 100;
    public static final double XP_DISCOVERY = 30;
    /** Kills count only when the mob is at most this many levels below the player. */
    public static final int GAP = 3;

    private MasteryRules() {
    }

    /** Mastery XP from rank r (0-based) to r + 1: 300 · (r + 1)^1.9 (MASTERY_SPEC, ProposedRules.masteryNeed). */
    public static long need(int rank) {
        if (rank >= MAX_RANK) return 0;
        return Math.round(300 * Math.pow(rank + 1, 1.9));
    }

    /** Mastery XP of a kill (before the farming factor); 0 for mobs too far below the player. */
    public static double killXp(MobTier tier, int mobLevel, int playerLevel) {
        if (mobLevel < playerLevel - GAP) return 0;
        return switch (tier) {
            case NORMAL -> 1;
            case ELITE -> 4;
            default -> 10; // champion, mythic, boss, world boss
        };
    }

    /** The milestone of a rank: what else, besides XP, reaching it needs. */
    public static boolean milestone(int rank, int playerLevel, int distinctDungeons) {
        if (playerLevel < Math.min(60, 6 * rank)) return false;
        return rank < 4 || distinctDungeons >= rank - 2;
    }

    public record Gain(ClassGear after, int ranks) {
    }

    /** Add mastery XP; ranks are taken while XP and milestones allow (XP above a blocked milestone is kept, one rank's worth). */
    public static Gain gain(ClassGear g, double xp, int playerLevel) {
        if (!(xp > 0)) return settle(g, playerLevel);
        int r = g.mastery();
        double have = g.masteryXp() + xp;
        int ranks = 0;
        while (r < MAX_RANK && have >= need(r) && milestone(r + 1, playerLevel, g.cleared().size())) {
            have -= need(r);
            r++;
            ranks++;
        }
        if (r >= MAX_RANK) have = 0;
        else have = Math.min(have, need(r));
        return new Gain(g.withMastery(r, have), ranks);
    }

    /** Ranks a newly met milestone allows from XP already held. */
    public static Gain settle(ClassGear g, int playerLevel) {
        int r = g.mastery();
        double have = g.masteryXp();
        int ranks = 0;
        while (r < MAX_RANK && have >= need(r) && need(r) > 0 && milestone(r + 1, playerLevel, g.cleared().size())) {
            have -= need(r);
            r++;
            ranks++;
        }
        return new Gain(ranks == 0 ? g : g.withMastery(r, have), ranks);
    }

    /** +0.25 % class-armour power per rank (MASTERY_SPEC "Power"). */
    public static double powerFactor(int rank) {
        return 1 + 0.0025 * Math.max(0, Math.min(MAX_RANK, rank));
    }

    /**
     * A per-minute counter for capped sources. {@code count(minute)} returns true while the cap for that minute is not
     * reached. Mutable, one per player and source; main thread.
     */
    public static final class MinuteCap {
        private final int cap;
        private long minute = Long.MIN_VALUE;
        private int used;

        public MinuteCap(int cap) {
            this.cap = cap;
        }

        public boolean take(long nowMillis) {
            long m = nowMillis / 60_000;
            if (m != minute) {
                minute = m;
                used = 0;
            }
            if (used >= cap) return false;
            used++;
            return true;
        }
    }
}
