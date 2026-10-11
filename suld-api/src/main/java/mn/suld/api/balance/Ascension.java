package mn.suld.api.balance;

import mn.suld.api.progression.LevelCurve;

/**
 * Тэнгэрийн Зэрэг (Ascension, docs/ASCENSION_SPEC.md): ranks I–III after level 60. EXP earned at the cap becomes
 * Тэнгэрийн оноо; a rank costs 0.6 · need(59) · (rank + 1) оноо plus a rite fee in coins, and opens only when the
 * rank's content gates are met. Each rank gives +1 skill point and +1 % power; rank III unlocks T6 armour.
 */
public final class Ascension {

    public static final int MAX_RANK = 3;

    private Ascension() {
    }

    /** Тэнгэрийн оноо for rank {@code rank} → {@code rank + 1}. */
    public static long cost(LevelCurve c, int rank) {
        return Math.round(0.6 * c.expForLevel(Math.min(59, c.maxLevel() - 1)) * (rank + 1));
    }

    public static long riteCoins(int rank) {
        return 25_000L * (rank + 1);
    }

    public static double powerBonus(int rank) {
        return 0.01 * Math.max(0, Math.min(MAX_RANK, rank));
    }

    /**
     * What a player has done, as far as the gates need it.
     *
     * @param distinctClears dungeons cleared at least once
     * @param ladderSize     dungeons on the ladder
     * @param palaceClears   clears of the last dungeon (Тэнгэрийн Ордон); stands in for the heroic and world-boss gates
     * @param gearPower      worn gear power
     * @param chapters       finished story chapters
     * @param storySize      chapters in the story
     * @param armorMastery   armour mastery rank (stands in for the other mastery tracks)
     */
    public record Inputs(int level, int distinctClears, int ladderSize, int palaceClears, double gearPower,
                         int chapters, int storySize, int armorMastery) {
    }

    /** One content gate of a rank, and whether it is met. */
    public record Gate(String label, boolean met) {
    }

    /** The content gates of rank {@code rank} → {@code rank + 1}, in order (the /ascend checklist). */
    public static java.util.List<Gate> gates(int rank, Inputs in) {
        java.util.List<Gate> g = new java.util.ArrayList<>();
        if (rank >= MAX_RANK) return g;
        g.add(new Gate("60-р түвшинд хүр", in.level() >= Balance.MAX_LEVEL));
        switch (rank) {
            case 0 -> {
                int need = Math.min(9, in.ladderSize());
                g.add(new Gate(need + " өөр шорон давах (" + Math.min(in.distinctClears(), need) + "/" + need + ")", in.distinctClears() >= need));
                g.add(new Gate("Түүхийн бүх бүлгийг дуусгах (" + Math.min(in.chapters(), in.storySize()) + "/" + in.storySize() + ")", in.chapters() >= in.storySize()));
                g.add(new Gate("Тоног хэрэгслийн хүч ≥ " + Math.round(0.9 * GearPower.par(60)) + " (" + Math.round(in.gearPower()) + ")", in.gearPower() >= 0.9 * GearPower.par(60)));
            }
            case 1 -> {
                g.add(new Gate("Шатны бүх шоронг давах (" + Math.min(in.distinctClears(), in.ladderSize()) + "/" + in.ladderSize() + ")", in.distinctClears() >= in.ladderSize()));
                g.add(new Gate("Тэнгэрийн Ордныг 2 удаа давах (" + Math.min(in.palaceClears(), 2) + "/2)", in.palaceClears() >= 2));
                g.add(new Gate("Хуягны ур чадвар 5-р зэрэг (" + Math.min(in.armorMastery(), 5) + "/5)", in.armorMastery() >= 5));
            }
            default -> {
                g.add(new Gate("Тэнгэрийн Ордныг 4 удаа давах (" + Math.min(in.palaceClears(), 4) + "/4)", in.palaceClears() >= 4));
                g.add(new Gate("Тоног хэрэгслийн хүч ≥ " + Math.round(GearPower.par(60)) + " (" + Math.round(in.gearPower()) + ")", in.gearPower() >= GearPower.par(60)));
                g.add(new Gate("Хуягны ур чадвар 7-р зэрэг (" + Math.min(in.armorMastery(), 7) + "/7)", in.armorMastery() >= 7));
            }
        }
        return g;
    }

    /** Why rank {@code rank} → {@code rank + 1} is not open yet, or null when only the оноо and coins remain. */
    public static String blocked(int rank, Inputs in) {
        if (rank >= MAX_RANK) return "Тэнгэрийн Зэргийн дээд шат.";
        for (Gate g : gates(rank, in)) if (!g.met()) return g.label() + ".";
        return null;
    }

    public enum Outcome { DONE, MAX_RANK, GATED, NOT_ENOUGH_POINTS, NOT_ENOUGH_COINS }

    /** The rite's result: the new rank, оноо and coins (unchanged unless {@link Outcome#DONE}). */
    public record Rite(Outcome outcome, int rank, long points, long coins, String reason) {
    }

    /** The rite of rank {@code rank} → {@code rank + 1}: gates first, then the оноо and the coin fee are spent. */
    public static Rite rite(LevelCurve c, int rank, long points, long coins, Inputs in) {
        if (rank >= MAX_RANK) return new Rite(Outcome.MAX_RANK, rank, points, coins, "Тэнгэрийн Зэргийн дээд шат.");
        String why = blocked(rank, in);
        if (why != null) return new Rite(Outcome.GATED, rank, points, coins, why);
        long cost = cost(c, rank);
        if (points < cost) return new Rite(Outcome.NOT_ENOUGH_POINTS, rank, points, coins, "Тэнгэрийн оноо хүрэлцэхгүй (" + points + "/" + cost + ").");
        long fee = riteCoins(rank);
        if (coins < fee) return new Rite(Outcome.NOT_ENOUGH_COINS, rank, points, coins, "Ёслолын зоос хүрэлцэхгүй (" + coins + "/" + fee + " ₮).");
        return new Rite(Outcome.DONE, rank + 1, points - cost, coins - fee, null);
    }

    /** Roman numeral of a rank (I–III), "—" for none. */
    public static String roman(int rank) {
        return switch (rank) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "—";
        };
    }
}
