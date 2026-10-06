package mn.suld.api.style;

import java.util.List;
import java.util.Optional;

/**
 * Milestone rewards claimed with {@code /lvlup}: coins at the level, sometimes a cosmetic. Claims are tracked as
 * a bit per level (levels 1..63) so a reward is paid exactly once.
 */
public final class LevelRewards {

    public record Reward(int level, long coins, String cosmeticId, String label) {
    }

    public static final List<Reward> ALL = List.of(
            new Reward(2, 50, null, "Анхны алхам"),
            new Reward(3, 75, null, "Тал руу"),
            new Reward(5, 120, "tag.anchin", "Анчин цол"),
            new Reward(7, 150, null, "Хүлэгт дадлага"),
            new Reward(10, 300, "name.nogoon", "Ногоон нэр"),
            new Reward(12, 300, null, "Аравтын эрх"),
            new Reward(15, 450, "emoji.zoos", "Зоосны эможи"),
            new Reward(18, 500, null, "Зуутын сорил"),
            new Reward(20, 700, "chat.tsas", "Цасан чат"),
            new Reward(25, 900, null, "Хагас зам"),
            new Reward(30, 1_200, "tag.tal_baatar", "Талын Баатар цол"),
            new Reward(35, 1_500, null, "Түмний зам"),
            new Reward(40, 2_000, "emoji.ulzii", "Өлзийн эможи"),
            new Reward(45, 2_500, null, "Ноёны зам"),
            new Reward(50, 3_500, "name.altan", "Алтан нэр"),
            new Reward(55, 4_500, null, "Тэнгэрийн шат"),
            new Reward(60, 8_000, "tag.monkh_baatar", "Мөнхийн Баатар цол"));

    private LevelRewards() {
    }

    public static Optional<Reward> at(int level) {
        return ALL.stream().filter(r -> r.level() == level).findFirst();
    }

    public static boolean claimed(long mask, int level) {
        return level >= 1 && level < 64 && (mask & (1L << level)) != 0;
    }

    public static long withClaimed(long mask, int level) {
        if (level < 1 || level >= 64) throw new IllegalArgumentException("level " + level);
        return mask | (1L << level);
    }

    /** Rewards the player has reached but not claimed yet. */
    public static List<Reward> claimable(int level, long mask) {
        return ALL.stream().filter(r -> r.level() <= level && !claimed(mask, r.level())).toList();
    }
}
