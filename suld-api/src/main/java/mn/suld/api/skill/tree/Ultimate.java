package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The 15 ultimates (three per class, one chosen), cast with F while holding the class weapon. */
public enum Ultimate {
    // Баатар
    CHINGISIIN_UUR(PlayerClass.BAATAR, "Чингисийн Уур", "12 секунд: Хүч II, Хамгаалалт I, Сэргэлт II."),
    BUKHNII_NURAL(PlayerClass.BAATAR, "Бүхний Нурал", "8 блок хүрээнд газрыг хагалан 6 дахин хүчтэй цохиж, дайснуудыг дээш хөөнө."),
    UKHEL_UNDER(PlayerClass.BAATAR, "Үхэшгүй Эр", "8 секунд: Хамгаалалт IV ба 10 ❤ нэмэлт амь."),
    // Мэргэн
    SUM_BORON(PlayerClass.MERGEN, "Сумын Борооны Цаг", "Зорьсон газарт 5 секунд сум бороо шиг асгарна."),
    KHETIIN_KHARVAACH(PlayerClass.MERGEN, "Хэтийн Харваач", "10 секунд: сум бүр +50% хүчтэй, Хурд II."),
    UKHLIIN_TEMDEG(PlayerClass.MERGEN, "Үхлийн Тэмдэг", "24 блок дотор бүх дайсныг тэмдэглэнэ: 10 секунд +40% хохирол авна."),
    // Бөө
    OVGODIIN_ZALBIRAL(PlayerClass.BOO, "Өвгөдийн Залбирал", "14 блок дотор нөхдийг бүрэн эдгээж, муу нөлөөг арилгана."),
    TENGERIIN_SHIITGEL(PlayerClass.BOO, "Тэнгэрийн Шийтгэл", "20 блок дотор 8 дайсан руу тэнгэрээс аянга буулгана."),
    SUNSNII_KHUL(PlayerClass.BOO, "Сүнсний Хөл", "8 секунд сүнс болно: Хурд III, Хамгаалалт II, Сэргэлт III."),
    // Дархан
    KHAILSAN_DALAI(PlayerClass.DARKHAN, "Хайлсан Далай", "5 секунд 7 блок хүрээнд хайлсан төмрөөр шатаана."),
    BAMBAIN_KHEREM(PlayerClass.DARKHAN, "Бамбайн Хэрэм", "8 секунд: Хамгаалалт III, 8 ❤ нэмэлт амь, авсан хохирлын 30% буцаана."),
    MYANGAN_ALKH(PlayerClass.DARKHAN, "Мянган Алх", "Ойролцоох дайснуудын дээр 10 дөш унана."),
    // Хүлэгчин
    SHUURGA_DAVKHILT(PlayerClass.KHULEGCHIN, "Шуурга Давхилт", "Урагш гурван удаа давхиж замд таарсныг цохино."),
    SALKHINY_GEGEEN(PlayerClass.KHULEGCHIN, "Салхины Гэгээн", "10 секунд: Хурд IV, Хүч I, үсрэлт өндөр."),
    MYANGAN_MORI(PlayerClass.KHULEGCHIN, "Мянган Морь", "Бүх 8 зүг рүү сүнсэн адуу давхиж цохино.");

    private final PlayerClass clazz;
    private final String displayName;
    private final String description;

    Ultimate(PlayerClass clazz, String displayName, String description) {
        this.clazz = clazz;
        this.displayName = displayName;
        this.description = description;
    }

    public PlayerClass clazz() { return clazz; }
    public String displayName() { return displayName; }
    public String description() { return description; }

    /** Seconds between two casts. */
    public static final int COOLDOWN_SECONDS = 45;

    public static List<Ultimate> of(PlayerClass c) {
        List<Ultimate> out = new ArrayList<>();
        for (Ultimate u : values()) if (u.clazz == c) out.add(u);
        return out;
    }

    public static Optional<Ultimate> byName(String name) {
        try {
            return Optional.of(valueOf(name));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
