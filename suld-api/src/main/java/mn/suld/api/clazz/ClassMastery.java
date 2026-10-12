package mn.suld.api.clazz;

import mn.suld.api.classgear.ArmorRules;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.profile.PlayerProfile;

import java.util.EnumMap;
import java.util.Map;

/**
 * «Ангийн Мастер» (docs/CLASS_CHANGE.md): every class of a character has its own 1–60 track, the class armour level
 * ({@link ClassGear#armorLevel()}, capped at the character level, XP ×2 while it lags more than 3 levels behind —
 * the catch-up a second class gets). A class at 60 is mastered; all five mastered is «Таван Ангийн Эзэн». A name and
 * a goal over what is already stored: no power of its own.
 */
public final class ClassMastery {

    public static final int MASTER_LEVEL = ArmorRules.MAX_ARMOR_LEVEL;
    /** The levels that are announced on the way (and 60). */
    public static final int[] MILESTONES = {20, 40, MASTER_LEVEL};

    private ClassMastery() {
    }

    public static int level(ClassGear g) {
        return g == null ? 0 : Math.max(1, Math.min(MASTER_LEVEL, g.armorLevel()));
    }

    public static boolean isMaster(ClassGear g) {
        return g != null && g.armorLevel() >= MASTER_LEVEL;
    }

    /**
     * The mastery level of every class this character has played: the class in play from {@code classGear()}, the
     * others from their saved progress. Classes never played are absent.
     */
    public static Map<PlayerClass, Integer> levels(PlayerProfile p) {
        Map<PlayerClass, Integer> out = new EnumMap<>(PlayerClass.class);
        ClassMasteries m = p.classMasteries();
        for (PlayerClass c : PlayerClass.values()) m.of(c).ifPresent(s -> out.put(c, level(s.gear())));
        p.playerClass().ifPresent(c -> out.put(c, level(p.classGear())));
        return out;
    }

    public static int masters(PlayerProfile p) {
        int n = 0;
        for (int l : levels(p).values()) if (l >= MASTER_LEVEL) n++;
        return n;
    }

    /** The milestone passed when the level went from {@code before} to {@code after}, the highest one, or 0. */
    public static int milestone(int before, int after) {
        int hit = 0;
        for (int m : MILESTONES) if (before < m && after >= m) hit = m;
        return hit;
    }

    /** The title for {@code masters} mastered classes, or null below five. */
    public static String title(int masters) {
        return masters >= PlayerClass.values().length ? "Таван Ангийн Эзэн" : null;
    }
}
