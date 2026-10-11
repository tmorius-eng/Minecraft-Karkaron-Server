package mn.suld.api.balance;

import java.util.List;

/**
 * The ten-dungeon ladder of progression v2 (docs/DUNGEON_PROGRESSION_SPEC.md): each rung's level band, the story
 * chapter it waits for, the party it is tuned for. The game's gates and the simulation read the same table.
 */
public final class DungeonLadder {

    /**
     * @param min         level to enter
     * @param max         highest level its rewards are worth (loot rolls inside min..max)
     * @param band        region band (0 = Хэрлэн … 7 = Отгонтэнгэр)
     * @param chapterGate story chapters (index, 0-based) that must be finished first: chapters done ≥ chapterGate + 1
     * @param party       recommended party size (boss health is tuned for it)
     */
    public record Rung(String id, String name, int min, int max, int band, int chapterGate, int party) {

        /** Level of the dungeon's trash waves. */
        public int contentLevel() {
            return Math.min(Balance.MAX_LEVEL, min + 3);
        }

        public int bossLevel() {
            return Math.min(Balance.MAX_LEVEL, min + 5);
        }
    }

    public static final List<Rung> RUNGS = List.of(
            new Rung("dungeon.khasar_den", "Хасарын Агуй", 3, 10, 0, 3, 1),
            new Rung("dungeon.govi_bulsh", "Говийн Булш", 9, 16, 1, 7, 2),
            new Rung("dungeon.baavgain_uur", "Баавгайн Үүр", 15, 22, 2, 12, 2),
            new Rung("dungeon.mosun_orgil", "Мөсөн Оргил", 22, 30, 3, 15, 3),
            new Rung("dungeon.dalain_gun", "Далайн Гүн", 29, 37, 4, 20, 3),
            new Rung("dungeon.khar_khot", "Хар Хотын Балгас", 36, 44, 5, 26, 3),
            new Rung("dungeon.ulaan_khad", "Улаан Хадны Хүрээ", 42, 50, 6, 31, 4),
            new Rung("dungeon.burkhan_agui", "Бурхан Халдуны Агуй", 48, 56, 6, 34, 4),
            new Rung("dungeon.tengeriin_shat", "Тэнгэрийн Шат", 54, 60, 7, 38, 4),
            new Rung("dungeon.tengeriin_ordon", "Тэнгэрийн Ордон", 60, 60, 7, 41, 4));

    private DungeonLadder() {
    }

    public static Rung rung(String dungeonId) {
        for (Rung r : RUNGS) if (r.id().equals(dungeonId)) return r;
        return null;
    }

    public static int index(String dungeonId) {
        for (int i = 0; i < RUNGS.size(); i++) if (RUNGS.get(i).id().equals(dungeonId)) return i;
        return -1;
    }
}
