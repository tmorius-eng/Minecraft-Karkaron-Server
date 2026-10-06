package mn.suld.api.skill;

import mn.suld.api.clazz.PlayerClass;

import java.util.ArrayList;
import java.util.List;

/**
 * The 20 class spells (4 per class), cast with a three-click combo while holding the class weapon (as in
 * Wynncraft). Melee classes start combos with a right click; the archer (Мэргэн) with a left click, because the
 * right click draws the bow. Unlocked at levels 1, 10, 20 and 35; each spends the class resource.
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
    KHULGIIN_DAIRALT(PlayerClass.KHULEGCHIN, 4, "Хүлгийн Дайралт", 60, 3.5, "Сүнсэн адуун сүрэг урагш давхина.");

    public static final int[] UNLOCK_LEVEL = {0, 1, 10, 20, 35};

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
    /** 1..4 */
    public int slot() { return slot; }
    public String displayName() { return displayName; }
    public int cost() { return cost; }
    /** Damage as a multiple of the caster's attack (0 = no direct damage). */
    public double damageMultiplier() { return damageMultiplier; }
    public String description() { return description; }
    public int unlockLevel() { return UNLOCK_LEVEL[slot]; }

    /** The combo for this spell, e.g. "RLR" (melee) or "LRL" (archer). */
    public String combo() {
        return comboFor(clazz, slot);
    }

    /** Spell combos: slot 1 xYx, 2 xxx, 3 xYY, 4 xxY with x = the class's first click. */
    public static String comboFor(PlayerClass c, int slot) {
        char x = c == PlayerClass.MERGEN ? 'L' : 'R';
        char y = x == 'R' ? 'L' : 'R';
        return switch (slot) {
            case 1 -> "" + x + y + x;
            case 2 -> "" + x + x + x;
            case 3 -> "" + x + y + y;
            default -> "" + x + x + y;
        };
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
        for (Spell s : of(c)) if (s.combo().equals(combo)) return java.util.Optional.of(s);
        return java.util.Optional.empty();
    }
}
