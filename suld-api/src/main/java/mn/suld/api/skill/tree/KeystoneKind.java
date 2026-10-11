package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;

/**
 * The class keystones, two per class (a build takes one of the two: the tree makes them exclusive). Each trades
 * something away for a different way to play.
 */
public enum KeystoneKind {
    MUNKH_TESVER(PlayerClass.BAATAR, "Мөнх Тэсвэр",
            "Амь 30%-аас доош бол авах хохирол -35%, гэвч авах эдгээлт 50%-иар буурна."),
    NEG_SUMNII_KHUVI(PlayerClass.MERGEN, "Нэг Сумны Хувь",
            "Энгийн сум +40% хохирол өгнө, гэвч Олон Сум -30% сул болно."),
    TENGERTEI_KHOLBOGDOKH(PlayerClass.BOO, "Тэнгэртэй Холбогдох",
            "Сүнсний нөөц +40, секундэд +3 сэргэнэ, гэвч дээд амь -25%."),
    ALTAN_DOSH(PlayerClass.DARKHAN, "Алтан Дөш",
            "Хуяг зэвсгийн элэгдэл 60%-иар буурч, шидийн хүч +15%, шидийн шатаалт +3 сек."),
    TALYN_SALKHI(PlayerClass.KHULEGCHIN, "Талын Салхи",
            "Морин дээр байхад хурд +60%, гэвч явган үед хурд -10%."),
    // the second keystones (level 40, the other side of the tree)
    KHATUU_ZORIG(PlayerClass.BAATAR, "Хатуу Зориг",
            "Довтолгоо +15%, гэвч ид шид 25% илүү Хил зарцуулна."),
    BURGEDIIN_NUD(PlayerClass.MERGEN, "Бүргэдийн Нүд",
            "Цохилтын магадлал +12%, цохилтын хүч +20%, гэвч дээд амь -15%."),
    ONGONY_GEREE(PlayerClass.BOO, "Онгоны Гэрээ",
            "Ид шидийн хүч +25%, гэвч авах эдгээлт 25%-иар буурна."),
    TUMRIIN_BIYE(PlayerClass.DARKHAN, "Төмрийн Бие",
            "Авах хохирол -15%, гэвч хурд -12%."),
    SALKHINY_KHUU(PlayerClass.KHULEGCHIN, "Салхины Хүү",
            "Гүйж байхад довтолгоо +15%, хурд +10%, гэвч дээд амь -15%.");

    private final PlayerClass clazz;
    private final String displayName;
    private final String description;

    KeystoneKind(PlayerClass clazz, String displayName, String description) {
        this.clazz = clazz;
        this.displayName = displayName;
        this.description = description;
    }

    public PlayerClass clazz() { return clazz; }
    public String displayName() { return displayName; }
    public String description() { return description; }
}
