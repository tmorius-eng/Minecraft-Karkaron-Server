package mn.suld.api.balance;

import java.util.ArrayList;
import java.util.List;

/**
 * Тэнгэрийн Зэрэг IV–X (directive 2026-10-13 Phase 4, docs/ASCENSION_SPEC.md §IV–X). The owner chose small power and
 * prestige («бага хүч + нэр хүнд»): ranks I–III stay the ones bought with Тэнгэрийн оноо (+1 % power and a skill point
 * each); IV–X cost no оноо and give no skill point. Each needs a count of Тэнгэрийн Од (counted from the оноо that keep
 * coming after III, never spent), a heroic key timed (docs/HEROIC_DUNGEONS.md) and a weapon mastery level, then a rite
 * fee at the shrine. Their perks add at most {@link #MAX_PVE_POWER} of power against mobs (the lightning of VI); the rest
 * is a guard in consented fights, rarity luck, the names, an aura and, at X, the monument. Pure rules.
 */
public final class AscensionHonor {

    public static final int FIRST = Ascension.MAX_RANK + 1;
    public static final int LAST = 10;

    /** IV: damage taken from players in consented fights (duels, arenas) × (1 − this). */
    public static final double PVP_GUARD = 0.03;
    /** V: added to the loot-chance stat (the rarer rolls of a table). */
    public static final double FORTUNE = 0.03;
    /** VI: the chance a hit on a mob calls the sky's lightning… */
    public static final double LIGHTNING_CHANCE = 0.02;
    /** …which strikes the same mob for this share of the hit. */
    public static final double LIGHTNING_SHARE = 0.5;
    /** The most IV–X add to damage against mobs, on average: the lightning (2 % × 50 % = 1 %). */
    public static final double MAX_PVE_POWER = LIGHTNING_CHANCE * LIGHTNING_SHARE;

    private AscensionHonor() {
    }

    /** What a rank gives on top of the ones below it. */
    public enum Perk { PVP_GUARD, FORTUNE, LIGHTNING, AURA, MONUMENT }

    /**
     * The ranks IV–X: name, Тэнгэрийн Од needed, heroic key that must have been timed (in any dungeon), weapon mastery
     * level needed (on any track), and the perk. The stars follow the оноо the proposed design charged for IV–X
     * (docs/ASCENSION_SPEC.md): a star is the cost of IV, 0.6 · need(59) · 4, and rank r costed 0.6 · need(59) · r, so
     * the stars by rank r are ⌈(4 + 5 + … + r) / 4⌉ whatever the curve: the pace to X stays the reference model's. The
     * keys and the mastery levels are first numbers, to tune with alpha data.
     */
    public enum Rank {
        IV(4, "Атаа Тэнгэрийн Сахиус", 1, 4, 10, Perk.PVP_GUARD),
        V(5, "Заяа Тэнгэрийн Хишиг", 3, 6, 20, Perk.FORTUNE),
        VI(6, "Хурмаст Тэнгэрийн Цахилгаан", 4, 8, 25, Perk.LIGHTNING),
        VII(7, "Дээд Тэнгэрийн Баатар", 6, 10, 30, Perk.AURA),
        VIII(8, "Дээд Тэнгэрийн Баатар II", 8, 12, 35, Perk.AURA),
        IX(9, "Дээд Тэнгэрийн Баатар III", 10, 15, 40, Perk.AURA),
        X(10, "Мөнх Тэнгэрийн Элч", 13, 18, 50, Perk.MONUMENT);

        private final int number;
        private final String title;
        private final long stars;
        private final int heroicKey;
        private final int weaponMastery;
        private final Perk perk;

        Rank(int number, String title, long stars, int heroicKey, int weaponMastery, Perk perk) {
            this.number = number;
            this.title = title;
            this.stars = stars;
            this.heroicKey = heroicKey;
            this.weaponMastery = weaponMastery;
            this.perk = perk;
        }

        public int number() {
            return number;
        }

        public String title() {
            return title;
        }

        public long stars() {
            return stars;
        }

        public int heroicKey() {
            return heroicKey;
        }

        public int weaponMastery() {
            return weaponMastery;
        }

        public Perk perk() {
            return perk;
        }

        /** The rank numbered {@code n} (4–10), or null. */
        public static Rank of(int n) {
            for (Rank r : values()) if (r.number == n) return r;
            return null;
        }
    }

    /**
     * What the gates of the next rank read.
     *
     * @param rank         the player's rank now (0–10)
     * @param stars        Тэнгэрийн Од ({@link Ascension#stars}, from the unspent оноо)
     * @param heroicKey    the best heroic key the player has timed, in any dungeon (0 = none)
     * @param weaponMastery the highest weapon mastery level of the player's tracks
     */
    public record Inputs(int rank, long stars, int heroicKey, int weaponMastery) {
    }

    /** One gate of the next rank, and whether it is met. */
    public record Gate(String label, boolean met) {
    }

    /** The rank after {@code rank}, or null when it is not an honour rank (below III, or X). */
    public static Rank next(int rank) {
        return rank < Ascension.MAX_RANK ? null : Rank.of(rank + 1);
    }

    /** The gates of the rank after {@code in.rank()} (empty when there is none). */
    public static List<Gate> gates(Inputs in) {
        List<Gate> g = new ArrayList<>();
        Rank n = next(in.rank());
        if (n == null) return g;
        g.add(new Gate("Тэнгэрийн Од " + n.stars() + " (" + Math.min(in.stars(), n.stars()) + "/" + n.stars() + ")", in.stars() >= n.stars()));
        g.add(new Gate("Баатарлаг агуйн +" + n.heroicKey() + "-г хугацаанд нь давах (шилдэг: " + (in.heroicKey() == 0 ? "—" : "+" + in.heroicKey()) + ")",
                in.heroicKey() >= n.heroicKey()));
        g.add(new Gate("Зэвсгийн ур чадвар " + n.weaponMastery() + " (" + Math.min(in.weaponMastery(), n.weaponMastery()) + "/" + n.weaponMastery() + ")",
                in.weaponMastery() >= n.weaponMastery()));
        return g;
    }

    /** Why the next rank is not open, or null when only the rite fee remains. */
    public static String blocked(Inputs in) {
        if (in.rank() < Ascension.MAX_RANK) return "Эхлээд Тэнгэрийн Зэрэг III.";
        if (in.rank() >= LAST) return "Тэнгэрийн Зэргийн дээд шат.";
        for (Gate g : gates(in)) if (!g.met()) return g.label() + ".";
        return null;
    }

    /** The rite fee of the rank after {@code rank}: the same rule as I–III, 25 000 × the new rank (100 000 for IV … 250 000 for X). */
    public static long riteCoins(int rank) {
        return Ascension.riteCoins(rank);
    }

    /** The rite's result: the new rank and the coins left (unchanged unless {@link Ascension.Outcome#DONE}). */
    public record Rite(Ascension.Outcome outcome, int rank, long coins, String reason) {
    }

    /** The rite of the rank after {@code in.rank()}: the gates, then the fee. Nothing else is spent. */
    public static Rite rite(Inputs in, long coins) {
        if (in.rank() >= LAST) return new Rite(Ascension.Outcome.MAX_RANK, in.rank(), coins, "Тэнгэрийн Зэргийн дээд шат.");
        String why = blocked(in);
        if (why != null) return new Rite(Ascension.Outcome.GATED, in.rank(), coins, why);
        long fee = riteCoins(in.rank());
        if (coins < fee) return new Rite(Ascension.Outcome.NOT_ENOUGH_COINS, in.rank(), coins, "Ёслолын зоос хүрэлцэхгүй (" + coins + "/" + fee + " зоос).");
        return new Rite(Ascension.Outcome.DONE, in.rank() + 1, coins - fee, null);
    }

    /** True when rank {@code rank} has the perk (the perks add up: VII keeps IV's guard, V's luck and VI's lightning). */
    public static boolean has(int rank, Perk perk) {
        for (Rank r : Rank.values()) if (r.perk() == perk && rank >= r.number()) return true;
        return false;
    }

    /** IV: what damage taken from a player in a consented fight is multiplied by. */
    public static double pvpTaken(int rank) {
        return has(rank, Perk.PVP_GUARD) ? 1 - PVP_GUARD : 1;
    }

    /** V: added to the loot-chance stat. */
    public static double fortune(int rank) {
        return has(rank, Perk.FORTUNE) ? FORTUNE : 0;
    }

    /** VI: the chance a hit on a mob calls lightning. */
    public static double lightningChance(int rank) {
        return has(rank, Perk.LIGHTNING) ? LIGHTNING_CHANCE : 0;
    }

    /** The name of rank {@code rank}: the honour's own from IV, else null (I–III have none). */
    public static String title(int rank) {
        Rank r = Rank.of(rank);
        return r == null ? null : r.title();
    }
}
