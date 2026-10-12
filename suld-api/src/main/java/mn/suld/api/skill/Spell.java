package mn.suld.api.skill;

import mn.suld.api.clazz.PlayerClass;

import java.util.ArrayList;
import java.util.List;

/**
 * The 25 class spells (5 per class). Spells 1–4 are cast with a three-click combo while holding the class weapon (as in
 * Wynncraft); melee classes start combos with a right click, the archer (Мэргэн) with a left click, because a plain right
 * click shoots the class bow (it is never drawn: a drawing client sends no left clicks). The fifth spell has no combo: Shift+F casts it. Unlocked at levels 1, 10, 20, 35 and 50; each
 * spends the class resource. Spells grow in rank with the caster's level ({@link #rankAt}): rank II hits 15 % harder,
 * rank III 30 % harder with a 10 % shorter cooldown.
 */
public enum Spell {
    // Баатар — Хил / Rage
    TENGER_TSAVCHILT(PlayerClass.BAATAR, 1, "Тэнгэрийн Цавчилт", 25, 1.6, "Урд талын бүх дайснаа цавчина."),
    DAINY_KHASHGIRAAN(PlayerClass.BAATAR, 2, "Дайны Хашгираан", 35, 0.0, "Ойрын дайснууд чам руу дайрч, чи хүч, хамгаалалт авна."),
    DOVTLOKH_USRELT(PlayerClass.BAATAR, 3, "Довтлох Үсрэлт", 30, 1.3, "Урагш үсэрч буухдаа газар доргионо."),
    KHAAN_KHAMGAALALT(PlayerClass.BAATAR, 4, "Хааны Хамгаалалт", 50, 0.0, "Нэмэлт зүрх ба 6 секунд их хамгаалалт."),
    // Мэргэн — Төвлөрөл / Focus
    CHONYN_NUD(PlayerClass.MERGEN, 1, "Чонын Нүд", 20, 0.0, "Ойрын дайснууд гэрэлтэж, дараагийн 3 сум хүчтэй."),
    OLON_SUM(PlayerClass.MERGEN, 2, "Олон Сум", 35, 0.9, "Таван сумыг дэлгэж харвана."),
    UKHRAKH_USRELT(PlayerClass.MERGEN, 3, "Ухрах Үсрэлт", 25, 0.0, "Хойш үсэрч, ойрын дайснуудыг удаашруулна."),
    TENGERIIN_SUM(PlayerClass.MERGEN, 4, "Тэнгэрийн Сум", 50, 3.0, "30 блок нэвт гарах тэнгэрийн туяа."),
    // Бөө — Сүнс / Spirit
    SUNSNII_ZALBIRAL(PlayerClass.BOO, 1, "Сүнсний Залбирал", 25, 0.0, "Өөрийгөө болон ойрын нөхдөө эдгээнэ."),
    ONGONY_DUUDLAGA(PlayerClass.BOO, 2, "Онгоны Дуудлага", 30, 2.0, "Онгоны сүнс дайсныг хөөж цохино."),
    KHENGERGIIN_DUU(PlayerClass.BOO, 3, "Хэнгэргийн Дуу", 40, 0.8, "Гурван удаа цохилох хэнгэргийн долгион."),
    TENGERIIN_KHAALGA(PlayerClass.BOO, 4, "Тэнгэрийн Хаалга", 70, 4.0, "Тэнгэрийн хаалга нээгдэж, дайсныг шатааж, нөхдийг эдгээнэ."),
    // Дархан — Дөл / Heat
    GALYN_DAVTALT(PlayerClass.DARKHAN, 1, "Галын Давталт", 25, 1.5, "Газар цохиж эргэн тойрныг галдана."),
    GAN_BAMBAI(PlayerClass.DARKHAN, 2, "Ган Бамбай", 30, 0.0, "6 секунд гангийн хатуу хамгаалалт."),
    KHAILSAN_TUMUR(PlayerClass.DARKHAN, 3, "Хайлсан Төмөр", 35, 0.6, "Урагш хайлсан төмөр цацаж шатаана."),
    DARKHANY_DARANGUI(PlayerClass.DARKHAN, 4, "Дарханы Дөш", 60, 4.0, "Тэнгэрээс төмөр дөш унагана."),
    // Хүлэгчин — Хурд / Momentum
    KHURDAN_DOVTOLGOO(PlayerClass.KHULEGCHIN, 1, "Хурдан Довтолгоо", 20, 1.3, "Урагш давхиж замд таарсныг цохино."),
    SALKHINY_KHURD(PlayerClass.KHULEGCHIN, 2, "Салхины Хурд", 25, 0.0, "6 секунд салхи шиг хурдан."),
    ZHADNY_SHIDELT(PlayerClass.KHULEGCHIN, 3, "Жадны Шидэлт", 35, 2.2, "Жадыг шидэж шугамаар нэвт хатгана."),
    KHULGIIN_DAIRALT(PlayerClass.KHULEGCHIN, 4, "Хүлгийн Дайралт", 60, 3.5, "Сүнсэн адуун сүрэг урагш давхина."),
    // the fifth spells (level 50, Shift+F)
    SULDIIN_TSOKHILT(PlayerClass.BAATAR, 5, "Сүлдний Цохилт", 60, 3.2, "Сүлдээ газарт шааж ойрын бүх дайсныг өргөн цохиж, 5 секунд хамгаалалт авна."),
    SUMNY_BOROO(PlayerClass.MERGEN, 5, "Сумны Бороо", 60, 0.8, "Онилсон газарт таван давалгаа сум бороо мэт асгарна."),
    KHAR_SALKHI(PlayerClass.BOO, 5, "Хар Салхи", 70, 0.9, "Онгодын хар салхи дайснуудыг татаж таван удаа цохиж, чамайг эдгээнэ."),
    GALT_UUL(PlayerClass.DARKHAN, 5, "Галт Уул", 70, 3.6, "Онилсон газар галт уул дэлбэрч, дайснуудыг шидэж шатаана."),
    KHIIMORIIN_DAVKHILT(PlayerClass.KHULEGCHIN, 5, "Хийморийн Давхилт", 60, 2.8, "Хийморь сэргэж урагш давхин замд таарсан бүхнийг цохиж, 6 секунд хурд авна.");

    public static final int[] UNLOCK_LEVEL = {0, 1, 10, 20, 35, 50};

    /** Base cooldown in seconds by slot (1..5): the stronger the spell, the longer before it can be cast again. */
    private static final double[] COOLDOWN_SECONDS = {0, 1.5, 4, 5, 10, 18};

    /** The slot of the spell cast with Shift+F instead of a combo. */
    public static final int SHIFT_F_SLOT = 5;
    /** Levels at which spells 1–4 reach rank II and III; the fifth spell reaches them at 55 and 60. */
    public static final int RANK_II_LEVEL = 40, RANK_III_LEVEL = 55;

    private final PlayerClass clazz;
    private final int slot;
    private final String displayName;
    private final int cost;
    private final double damageMultiplier;
    private final String description;

    Spell(PlayerClass clazz, int slot, String displayName, int cost, double damageMultiplier, String description) {
        this.clazz = clazz;
        this.slot = slot;
        this.displayName = displayName;
        this.cost = cost;
        this.damageMultiplier = damageMultiplier;
        this.description = description;
    }

    public PlayerClass clazz() { return clazz; }
    /** 1..5 */
    public int slot() { return slot; }
    public String displayName() { return displayName; }
    public int cost() { return cost; }
    /** Damage as a multiple of the caster's attack (0 = no direct damage). */
    public double damageMultiplier() { return damageMultiplier; }
    public String description() { return description; }
    public int unlockLevel() { return UNLOCK_LEVEL[slot]; }
    /** Seconds before this spell can be cast again (before cooldown reduction). */
    public double cooldownSeconds() { return COOLDOWN_SECONDS[slot]; }

    /** The spell's rank (1..3) at a caster level: spells 1–4 rank up at 40 and 55, the fifth at 55 and 60. */
    public int rankAt(int level) {
        int two = slot == SHIFT_F_SLOT ? RANK_III_LEVEL : RANK_II_LEVEL, three = slot == SHIFT_F_SLOT ? 60 : RANK_III_LEVEL;
        return level >= three ? 3 : level >= two ? 2 : 1;
    }

    /** Damage and healing factor of a rank: I ×1, II ×1.15, III ×1.30. */
    public static double rankPower(int rank) {
        return 1 + 0.15 * (Math.max(1, Math.min(3, rank)) - 1);
    }

    /** Cooldown factor of a rank: rank III casts 10 % sooner. */
    public static double rankCooldown(int rank) {
        return rank >= 3 ? 0.9 : 1.0;
    }

    public static String roman(int rank) {
        return switch (rank) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    /** The combo for this spell, e.g. "RLR" (melee) or "LRL" (archer); "" for the fifth spell (Shift+F). */
    public String combo() {
        return comboFor(clazz, slot);
    }

    /** How the spell is cast, for the menus: the combo with dashes, or Shift+F. */
    public String keys() {
        return slot == SHIFT_F_SLOT ? "Shift+F" : String.join("-", combo().split(""));
    }

    /** Spell combos: slot 1 xYx, 2 xxx, 3 xYY, 4 xxY with x = the class's first click; slot 5 has none (Shift+F). */
    public static String comboFor(PlayerClass c, int slot) {
        char x = c == PlayerClass.MERGEN ? 'L' : 'R';
        char y = x == 'R' ? 'L' : 'R';
        return switch (slot) {
            case 1 -> "" + x + y + x;
            case 2 -> "" + x + x + x;
            case 3 -> "" + x + y + y;
            case 4 -> "" + x + x + y;
            default -> "";
        };
    }

    /** The class's spell cast with Shift+F. */
    public static java.util.Optional<Spell> shiftF(PlayerClass c) {
        for (Spell s : of(c)) if (s.slot == SHIFT_F_SLOT) return java.util.Optional.of(s);
        return java.util.Optional.empty();
    }

    public static char firstClick(PlayerClass c) {
        return c == PlayerClass.MERGEN ? 'L' : 'R';
    }

    public static List<Spell> of(PlayerClass c) {
        List<Spell> out = new ArrayList<>();
        for (Spell s : values()) if (s.clazz == c) out.add(s);
        return out;
    }

    /** The spell a completed combo casts for this class, if any. */
    public static java.util.Optional<Spell> byCombo(PlayerClass c, String combo) {
        if (combo == null || combo.isEmpty()) return java.util.Optional.empty();
        for (Spell s : of(c)) if (s.combo().equals(combo)) return java.util.Optional.of(s);
        return java.util.Optional.empty();
    }
}
