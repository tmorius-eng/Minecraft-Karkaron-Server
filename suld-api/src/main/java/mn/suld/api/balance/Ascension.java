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

    /** Why rank {@code rank} → {@code rank + 1} is not open yet, or null when only the оноо and coins remain. */
    public static String blocked(int rank, Inputs in) {
        if (rank >= MAX_RANK) return "Тэнгэрийн Зэргийн дээд шат.";
        if (in.level() < Balance.MAX_LEVEL) return "60-р түвшинд хүр.";
        switch (rank) {
            case 0 -> {
                if (in.distinctClears() < Math.min(9, in.ladderSize())) return "9 өөр шорон нэг удаа давах.";
                if (in.chapters() < in.storySize()) return "Түүхийн бүх бүлгийг дуусга.";
                if (in.gearPower() < 0.9 * GearPower.par(60)) return "Тоног хэрэгслийн хүч хангалтгүй.";
            }
            case 1 -> {
                if (in.distinctClears() < in.ladderSize()) return "Шатны бүх шоронг дав.";
                if (in.palaceClears() < 2) return "Тэнгэрийн Ордныг 2 удаа дав.";
                if (in.armorMastery() < 5) return "Хуягны ур чадвар 5-р зэрэгт хүр.";
            }
            default -> {
                if (in.palaceClears() < 4) return "Тэнгэрийн Ордныг 4 удаа дав.";
                if (in.gearPower() < GearPower.par(60)) return "Тоног хэрэгслийн хүч хангалтгүй.";
                if (in.armorMastery() < 7) return "Хуягны ур чадвар 7-р зэрэгт хүр.";
            }
        }
        return null;
    }
}
