package mn.suld.api.dungeon.heroic;

import java.util.ArrayList;
import java.util.List;

/**
 * The numbers of a heroic key («Тэнгэрийн Гэсгээлт Агуй», docs/HEROIC_DUNGEONS.md): a level-60 hall run against a
 * clock, its mobs {@link #HP_PER_LEVEL} sturdier and {@link #DAMAGE_PER_LEVEL} harder-hitting per key level, with the
 * week's {@link SteppeAffix steppe affixes}. A run inside the time limit opens the next keys and pays «Эртний Хас
 * Чулуу». Every player starts with +1 open in each dungeon. Pure: no Bukkit, no clock of its own.
 */
public final class HeroicLadder {

    public static final int MIN_KEY = 1;
    public static final int MAX_KEY = 20;
    /** Mob max health: +10 % per key level. */
    public static final double HP_PER_LEVEL = 0.10;
    /** Mob damage: +8 % per key level. */
    public static final double DAMAGE_PER_LEVEL = 0.08;
    /** Every death of a party member costs this many seconds of the clock. */
    public static final int DEATH_PENALTY_SECONDS = 15;
    /** A heroic hall is an endgame hall: its mobs stand at this level before the key's multipliers. */
    public static final int MOB_LEVEL = 60;
    /** The player level a heroic key needs. */
    public static final int PLAYER_LEVEL = 60;
    /** A run within this share of the limit raises the key by two. */
    public static final double FAST_SHARE = 0.80;

    private HeroicLadder() {
    }

    public static int clamp(int key) {
        return Math.max(MIN_KEY, Math.min(MAX_KEY, key));
    }

    public static double healthMultiplier(int key) {
        return 1.0 + HP_PER_LEVEL * clamp(key);
    }

    public static double damageMultiplier(int key) {
        return 1.0 + DAMAGE_PER_LEVEL * clamp(key);
    }

    /** How many affixes a key brings: none at +1, one from +2, two from +4, three from +7, all four from +10. */
    public static int affixCount(int key) {
        int k = clamp(key);
        return k >= 10 ? 4 : k >= 7 ? 3 : k >= 4 ? 2 : k >= 2 ? 1 : 0;
    }

    /**
     * The affixes of a key in week {@code weekId} (the campaign week id, e.g. "2026-W42"): the four rotate weekly, the
     * first {@link #affixCount} of the week's order apply. The same week, the same affixes for everyone.
     */
    public static List<SteppeAffix> affixes(int key, String weekId) {
        SteppeAffix[] all = SteppeAffix.values();
        int start = Math.floorMod(weekId == null ? 0 : weekId.hashCode(), all.length);
        List<SteppeAffix> out = new ArrayList<>();
        for (int i = 0; i < affixCount(key); i++) out.add(all[(start + i) % all.length]);
        return List.copyOf(out);
    }

    /** The time limit of a run: two minutes to gather, 75 seconds a wave, and the boss's enrage time. */
    public static int timeLimitSeconds(int waves, int enrageSeconds) {
        return 120 + 75 * Math.max(0, waves) + Math.max(60, enrageSeconds);
    }

    /** The clock a run is judged by: the time it took plus the death penalties. */
    public static double clock(double elapsedSeconds, int deaths) {
        return Math.max(0, elapsedSeconds) + DEATH_PENALTY_SECONDS * (double) Math.max(0, deaths);
    }

    /** How a heroic run ended. */
    public enum Outcome {
        /** Cleared within the time limit: higher keys open, the jade is paid. */
        TIMED,
        /** Cleared, but over the limit: nothing new opens, no jade. */
        OVERTIME,
        /** Not cleared (wiped, left, enraged, closed): nothing new opens. */
        FAILED
    }

    public static Outcome outcome(boolean cleared, double elapsedSeconds, int deaths, int limitSeconds) {
        if (!cleared) return Outcome.FAILED;
        return clock(elapsedSeconds, deaths) <= limitSeconds ? Outcome.TIMED : Outcome.OVERTIME;
    }

    /**
     * The highest key a player may open in a dungeon after a run of {@code key} there, from {@code open} before it:
     * a timed run opens the next key (two keys up within {@link #FAST_SHARE} of the limit); a late or failed run
     * changes nothing. A key never falls: the hardcore death is the price of a failed run, not a lost key.
     */
    public static int openAfter(int open, int key, Outcome o, double elapsedSeconds, int deaths, int limitSeconds) {
        int before = clamp(open);
        if (o != Outcome.TIMED) return before;
        int up = clock(elapsedSeconds, deaths) <= FAST_SHARE * limitSeconds ? 2 : 1;
        return Math.max(before, clamp(clamp(key) + up));
    }

    /** True when {@code key} may be opened by a player whose highest open key there is {@code open}. */
    public static boolean mayOpen(int key, int open) {
        return key >= MIN_KEY && key <= clamp(open);
    }

    /** «Эртний Хас Чулуу» each member gets: a timed run pays 1 + key / 4 (1 at +1, 2 at +4, 6 at +20); else none. */
    public static int jade(int key, Outcome o) {
        return o == Outcome.TIMED ? 1 + clamp(key) / 4 : 0;
    }
}
