package mn.suld.api.style;

import mn.suld.api.style.Cosmetic.Category;
import mn.suld.api.style.Cosmetic.Rarity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every SÜLD cosmetic — Mongolian themed. Prices in SÜLD coins, or in credits for the store ones (cosmetics only). */
public final class CosmeticCatalog {

    private CosmeticCatalog() {
    }

    private static Cosmetic tag(String id, String name, String mm, long price, Rarity r) {
        return new Cosmetic("tag." + id, Category.TAG, name, price, mm, r, "shop");
    }

    private static Cosmetic name(String id, String name, String open, String close, long price, Rarity r) {
        return new Cosmetic("name." + id, Category.NAME_COLOR, name, price, open + "{}" + close, r, "shop");
    }

    private static Cosmetic chat(String id, String name, String open, String close, long price, Rarity r) {
        return new Cosmetic("chat." + id, Category.CHAT_COLOR, name, price, open + "{}" + close, r, "shop");
    }

    private static Cosmetic join(String id, String name, String mm, long price, Rarity r) {
        return new Cosmetic("join." + id, Category.JOIN_MESSAGE, name, price, mm, r, "shop");
    }

    private static Cosmetic emoji(String id, String name, String code, long price, Rarity r) {
        return new Cosmetic("emoji." + id, Category.EMOJI, name, price, code, r, price == 0 ? "default" : "shop");
    }

    /** Kept in the catalog (owners keep it) but never sold; see {@link Cosmetic#withheld()}. */
    private static Cosmetic withheld(Cosmetic c) {
        return new Cosmetic(c.id(), c.category(), c.name(), c.price(), c.style(), c.rarity(), "withheld");
    }

    /** Credits-only (store) cosmetic; {@code credits} is its price in credits. */
    private static Cosmetic store(String id, Category cat, String name, String style, long credits, Rarity r) {
        return new Cosmetic(id, cat, name, credits, style, r, "store");
    }

    /** A particle effect (aura, trail, kill effect); {@code key} names the effect in the plugin's CosmeticEffects. */
    private static Cosmetic fx(Category cat, String id, String name, String key, long price, Rarity r) {
        return new Cosmetic(id, cat, name, price, key, r, "shop");
    }

    /** Earned by the referral loop (docs/REFERRAL.md, {@code badge-after} paid invitations): never sold. */
    private static Cosmetic referral(Cosmetic c) {
        return new Cosmetic(c.id(), c.category(), c.name(), 0, c.style(), c.rarity(), "referral");
    }

    /** Earned by an achievement (docs/ACHIEVEMENTS_AND_PASS.md): never sold, for coins or credits. */
    private static Cosmetic earned(Cosmetic c) {
        return new Cosmetic(c.id(), c.category(), c.name(), 0, c.style(), c.rarity(), "achievement");
    }

    /** A reward of the season's pass, «Хуралдайн Зам» (docs/ACHIEVEMENTS_AND_PASS.md): never sold. */
    private static Cosmetic pass(Cosmetic c) {
        return new Cosmetic(c.id(), c.category(), c.name(), 0, c.style(), c.rarity(), "pass");
    }

    private static Cosmetic levelTag(String id, String name, String mm, int level, Rarity r) {
        return new Cosmetic("tag." + id, Category.TAG, name, 0, mm, r, "level:" + level);
    }

    public static final List<Cosmetic> ALL = List.of(
            // --- tags (цол) ---
            tag("aduuchin", "Адуучин", "<#C8A06B>Адуучин", 200, Rarity.COMMON),
            tag("ger", "Гэрийн Эзэн", "<#EAD9B8>Гэрийн Эзэн", 250, Rarity.COMMON),
            tag("chono", "Хөх Чоно", "<gradient:#4F8BFF:#4FD2D2>Хөх Чоно</gradient>", 350, Rarity.RARE),
            tag("burged", "Бүргэд", "<gradient:#FFD24A:#A0642A>Бүргэд</gradient>", 350, Rarity.RARE),
            tag("shonkhor", "Шонхор", "<gradient:#FF5A3C:#FFB43C>Шонхор</gradient>", 400, Rarity.RARE),
            tag("khuleg", "Хүлэг", "<gradient:#A0642A:#FF9A3C>Хүлэг</gradient>", 400, Rarity.RARE),
            tag("salkhi", "Талын Салхи", "<gradient:#6BD06B:#EFFFF0>Талын Салхи</gradient>", 500, Rarity.RARE),
            tag("govi", "Говийн Од", "<gradient:#FFE08A:#FF9A3C>Говийн Од</gradient>", 500, Rarity.RARE),
            tag("morin_khuur", "Морин Хуур", "<gradient:#A0642A:#FFD24A>Морин Хуур</gradient>", 600, Rarity.EPIC),
            withheld(tag("naadam", "Наадамч", "<gradient:#FF4A4A:#FFD24A>Наадамч</gradient>", 700, Rarity.EPIC)), // Naadam was removed from the game
            withheld(tag("tsagaan_sar", "Цагаан Сар", "<gradient:#FFFFFF:#9FF3FF>Цагаан Сар</gradient>", 700, Rarity.EPIC)), // a national holiday: review
            tag("irves", "Алтайн Ирвэс", "<gradient:#FFFFFF:#8C96A8>Алтайн Ирвэс</gradient>", 900, Rarity.EPIC),
            tag("tenger", "Мөнх Тэнгэр", "<gradient:#2A6BFF:#9FF3FF:#2A6BFF>Мөнх Тэнгэр</gradient>", 1_500, Rarity.LEGENDARY),
            withheld(tag("khar_suld", "Хар Сүлд", "<gradient:#3C3C46:#FF4A4A>Хар Сүлд</gradient>", 1_800, Rarity.LEGENDARY)), // a venerated state banner: review
            withheld(tag("tsagaan_suld", "Цагаан Сүлд", "<gradient:#FFFFFF:#C8D2E6>Цагаан Сүлд</gradient>", 1_800, Rarity.LEGENDARY)), // a venerated state banner: review
            withheld(tag("altan_urag", "Алтан Ураг", "<gradient:#FFF0A0:#FFD24A:#FF9A3C>Алтан Ураг</gradient>", 3_000, Rarity.LEGENDARY)), // the Chinggisid lineage: review
            levelTag("anchin", "Анчин", "<#6BD06B>Анчин", 5, Rarity.COMMON),
            levelTag("tal_baatar", "Талын Баатар", "<gradient:#6BD06B:#FFD24A>Талын Баатар</gradient>", 30, Rarity.EPIC),
            levelTag("monkh_baatar", "Мөнхийн Баатар", "<gradient:#FFD24A:#FF4A4A:#B06BFF>Мөнхийн Баатар</gradient>", 60, Rarity.LEGENDARY),
            store("tag.suld_sakhiulsan", Category.TAG, "Сүлд Сахиулсан", "<gradient:#FFD24A:#9FF3FF:#FFD24A>✦ Сүлд Сахиулсан ✦</gradient>", 250, Rarity.LEGENDARY),
            store("tag.tengeriin_khuu", Category.TAG, "Тэнгэрийн Хүү", "<gradient:#9FF3FF:#4F8BFF:#B06BFF>Тэнгэрийн Хүү</gradient>", 200, Rarity.LEGENDARY),
            // "grandson of the Great Khan", sold for real money: withheld until the legal/platform-policy review
            withheld(store("tag.chingis", Category.TAG, "Их Хааны Ач", "<gradient:#FF4A4A:#FFD24A:#FF4A4A>⚜ Их Хааны Ач ⚜</gradient>", 400, Rarity.LEGENDARY)),
            // --- name colours ---
            name("ulaan", "Улаан", "<#FF6B6B>", "", 200, Rarity.COMMON),
            name("nogoon", "Ногоон", "<#7CE07C>", "", 200, Rarity.COMMON),
            name("tsenkher", "Цэнхэр", "<#5AD2FF>", "", 200, Rarity.COMMON),
            name("tal", "Талын", "<gradient:#6BD06B:#D6F07A>", "</gradient>", 450, Rarity.RARE),
            name("tsas", "Цасан", "<gradient:#FFFFFF:#9FD8FF>", "</gradient>", 450, Rarity.RARE),
            name("tenger", "Хөх Тэнгэр", "<gradient:#4F8BFF:#9FF3FF>", "</gradient>", 500, Rarity.RARE),
            name("gal", "Галын", "<gradient:#FF4A3C:#FFB43C>", "</gradient>", 750, Rarity.EPIC),
            name("udesh", "Үдшийн", "<gradient:#B06BFF:#FF6BB0>", "</gradient>", 750, Rarity.EPIC),
            name("altan", "Алтан", "<gradient:#FFF0A0:#FFD24A:#FF9A3C>", "</gradient>", 1_000, Rarity.EPIC),
            name("khaan", "Хааны", "<gradient:#FFD24A:#FF4A4A:#FFD24A>", "</gradient>", 3_000, Rarity.LEGENDARY),
            store("name.solongo", Category.NAME_COLOR, "Солонгын", "<rainbow>{}</rainbow>", 300, Rarity.LEGENDARY),
            store("name.mungun", Category.NAME_COLOR, "Мөнгөн", "<gradient:#FFFFFF:#8C96A8:#FFFFFF>{}</gradient>", 150, Rarity.EPIC),
            // --- chat colours ---
            chat("tsas", "Цасан", "<#EAF6FF>", "", 150, Rarity.COMMON),
            chat("tenger", "Тэнгэрийн", "<#9FD8FF>", "", 300, Rarity.RARE),
            chat("nogoon", "Ногоон", "<#B6F5C0>", "", 300, Rarity.RARE),
            chat("yagaan", "Ягаан", "<#FFC2D6>", "", 300, Rarity.RARE),
            chat("altan", "Алтан", "<#FFE08A>", "", 500, Rarity.EPIC),
            chat("solongo", "Солонго", "<gradient:#9FD8FF:#FFFFFF:#FFE08A>", "</gradient>", 1_200, Rarity.LEGENDARY),
            // --- join messages ---
            join("morin", "Хүлэгт", "<#FFD24A>♞ <white>{name}</white> <#B8C0CC>хүлэг морьтойгоо Хархорумд ирлээ!", 400, Rarity.RARE),
            join("salkhi", "Салхи", "<#9FF3FF>☁ <#B8C0CC>Талын салхи <white>{name}</white>-г авчирлаа.", 400, Rarity.RARE),
            join("burged", "Бүргэд", "<#FFB43C>✦ <#B8C0CC>Бүргэд дүүлэв — <white>{name}</white> ирлээ!", 700, Rarity.EPIC),
            join("khaan", "Хааны зарлиг", "<gradient:#FFD24A:#FF4A4A>⚜ Хааны зарлиг:</gradient> <white>{name}</white> <#FFE08A>морилон ирэв!", 2_500, Rarity.LEGENDARY),
            store("join.tenger", Category.JOIN_MESSAGE, "Тэнгэрийн дуудлага", "<gradient:#9FF3FF:#4F8BFF>☀ Мөнх тэнгэрийн хүчин дор</gradient> <white>{name}</white> <#9FF3FF>буулаа!", 250, Rarity.LEGENDARY),
            // --- emojis (chat) ---
            emoji("zurkh", "Зүрх", ":zurkh:", 0, Rarity.COMMON),
            emoji("zoos", "Зоос", ":zoos:", 150, Rarity.COMMON),
            emoji("od", "Од", ":od:", 150, Rarity.COMMON),
            emoji("ild", "Илд", ":ild:", 250, Rarity.RARE),
            emoji("ulzii", "Өлзий", ":ulzii:", 400, Rarity.EPIC),
            emoji("guul", "Гавал", ":guul:", 400, Rarity.EPIC),
            // --- auras: light that circles the player ---
            fx(Category.AURA, "aura.tal", "Талын Салхи", "tal", 450, Rarity.RARE),
            fx(Category.AURA, "aura.od", "Одот Тэнгэр", "od", 600, Rarity.RARE),
            fx(Category.AURA, "aura.gal", "Галын Бүрхэвч", "gal", 700, Rarity.EPIC),
            fx(Category.AURA, "aura.tsas", "Цасан Шуурга", "tsas", 700, Rarity.EPIC),
            fx(Category.AURA, "aura.altan", "Алтан Гэрэл", "altan", 1_200, Rarity.EPIC),
            fx(Category.AURA, "aura.tenger", "Мөнх Тэнгэр", "tenger", 2_500, Rarity.LEGENDARY),
            store("aura.suld_dul", Category.AURA, "Сүлдний Дөл", "suld", 300, Rarity.LEGENDARY),
            // --- trails: sparkles left behind while moving or riding ---
            fx(Category.TRAIL, "trail.shuurkhai", "Шороон Хөвөрөл", "shuurkhai", 250, Rarity.COMMON),
            fx(Category.TRAIL, "trail.tal", "Талын Дэнгэ", "tal", 400, Rarity.RARE),
            fx(Category.TRAIL, "trail.od", "Одны Мөр", "od", 500, Rarity.RARE),
            fx(Category.TRAIL, "trail.gal", "Галт Мөр", "gal", 650, Rarity.EPIC),
            fx(Category.TRAIL, "trail.tsas", "Цасан Мөр", "tsas", 650, Rarity.EPIC),
            fx(Category.TRAIL, "trail.zurkh", "Зүрхэн Мөр", "zurkh", 800, Rarity.EPIC),
            fx(Category.TRAIL, "trail.altan", "Алтан Мөр", "altan", 1_000, Rarity.EPIC),
            fx(Category.TRAIL, "trail.tenger", "Тэнгэрийн Мөр", "tenger", 2_200, Rarity.LEGENDARY),
            // --- kill effects: what happens where a monster falls ---
            fx(Category.KILL_EFFECT, "kill.salyut", "Салют", "salyut", 500, Rarity.RARE),
            fx(Category.KILL_EFFECT, "kill.suns", "Сүнсний Нисэл", "suns", 600, Rarity.RARE),
            fx(Category.KILL_EFFECT, "kill.gal", "Галын Дэлбэрэлт", "gal", 700, Rarity.EPIC),
            fx(Category.KILL_EFFECT, "kill.tsas", "Цасан Дэлбэрэлт", "tsas", 700, Rarity.EPIC),
            fx(Category.KILL_EFFECT, "kill.altan", "Алтан Бороо", "altan", 1_200, Rarity.EPIC),
            fx(Category.KILL_EFFECT, "kill.tenger", "Тэнгэрийн Тэнхээ", "tenger", 2_500, Rarity.LEGENDARY),
            store("kill.tsakhilgaan", Category.KILL_EFFECT, "Цахилгаан", "tsakhilgaan", 350, Rarity.LEGENDARY),
            // --- earned by the referral loop (docs/REFERRAL.md) ---
            referral(tag("elch", "Элч", "<gradient:#FFD24A:#FFF3C2>Элч</gradient>", 0, Rarity.EPIC)),
            // --- earned by achievements (titles) ---
            earned(tag("ach_taliin_ur", "Талын Үр", "<gradient:#C8A06B:#EAD9B8>Талын Үр</gradient>", 0, Rarity.RARE)),
            earned(tag("ach_khukh_suld", "Хөх Сүлдний Баатар", "<gradient:#2A6BFF:#9FF3FF:#FFD24A>Хөх Сүлдний Баатар</gradient>", 0, Rarity.LEGENDARY)),
            earned(tag("ach_khureenii_tsereg", "Хүрээний Цэрэг", "<gradient:#A0642A:#FF9A3C>Хүрээний Цэрэг</gradient>", 0, Rarity.RARE)),
            earned(tag("ach_arvan_agui", "Арван Агуйн Баатар", "<gradient:#8C96A8:#FFD24A>Арван Агуйн Баатар</gradient>", 0, Rarity.EPIC)),
            earned(tag("ach_khoyor_avarga", "Хоёр Аваргын Ялагч", "<gradient:#FF5A3C:#FFD24A>Хоёр Аваргын Ялагч</gradient>", 0, Rarity.EPIC)),
            earned(tag("ach_gesgeelt_arav", "Гэсгээлтийн Баатар", "<gradient:#9FF3FF:#4F8BFF>Гэсгээлтийн Баатар</gradient>", 0, Rarity.EPIC)),
            earned(tag("ach_gesgeelt_khori", "Тэнгэрийн Гэсгээлт", "<gradient:#FFFFFF:#9FF3FF:#B06BFF>Тэнгэрийн Гэсгээлт</gradient>", 0, Rarity.LEGENDARY)),
            earned(tag("ach_nutgiin_medegch", "Нутгийн Мэдэгч", "<gradient:#6BD06B:#FFE08A>Нутгийн Мэдэгч</gradient>", 0, Rarity.EPIC)),
            earned(tag("ach_ovoo", "Овооны Тахигч", "<gradient:#EAD9B8:#9FD8FF>Овооны Тахигч</gradient>", 0, Rarity.RARE)),
            earned(tag("ach_mungun_mod", "Мөнгөн Модны Дархан", "<gradient:#FFFFFF:#8C96A8:#FFFFFF>Мөнгөн Модны Дархан</gradient>", 0, Rarity.EPIC)),
            earned(tag("ach_durvun_mergejil", "Дөрвөн Мэргэжлийн Эзэн", "<gradient:#FFD24A:#6BD06B:#C8A06B>Дөрвөн Мэргэжлийн Эзэн</gradient>", 0, Rarity.LEGENDARY)),
            earned(tag("ach_khuraldai", "Хуралдайн Төлөөлөгч", "<gradient:#4F8BFF:#EAD9B8>Хуралдайн Төлөөлөгч</gradient>", 0, Rarity.RARE)),
            // --- the season's pass «Хуралдайн Зам»: the free track ---
            pass(tag("pass_ayanchin", "Аянчин", "<#D9B86A>Аянчин", 0, Rarity.COMMON)),
            pass(name("pass_tal", "Талын Шар", "<#E6C77A>", "", 0, Rarity.COMMON)),
            pass(join("pass_mendchilgee", "Хуралдайн Мэндчилгээ", "<#E6C77A>✦ <white>{name}</white> <#B8C0CC>Хуралдайн замд мордлоо.", 0, Rarity.COMMON)),
            pass(tag("pass_morin_elch", "Морин Элч", "<gradient:#C8A06B:#FFE08A>Морин Элч</gradient>", 0, Rarity.RARE)),
            pass(chat("pass_tenger", "Тэнгэрийн Цэнхэр", "<#9FC7FF>", "", 0, Rarity.RARE)),
            pass(tag("pass_urtuuchin", "Өртөөчин", "<gradient:#A0642A:#EAD9B8>Өртөөчин</gradient>", 0, Rarity.RARE)),
            pass(name("pass_ulaan_nar", "Улаан Нар", "<gradient:#E85A4F:#F2A65A>", "</gradient>", 0, Rarity.RARE)),
            pass(join("pass_ayan", "Аяны Дуудлага", "<#F2A65A>♞ <white>{name}</white> <#B8C0CC>аянаас буцаж ирлээ.", 0, Rarity.RARE)),
            pass(tag("pass_myangat", "Мянгатын Ноён", "<gradient:#FFD24A:#A0642A>Мянгатын Ноён</gradient>", 0, Rarity.EPIC)),
            pass(tag("pass_khuraldai_baatar", "Хуралдайн Баатар", "<gradient:#4F8BFF:#FFD24A>Хуралдайн Баатар</gradient>", 0, Rarity.EPIC)),
            // --- the pass's heroic track ---
            pass(tag("pass_h_ayanchin", "Баатарлаг Аянчин", "<gradient:#FFD24A:#FFF0A0>Баатарлаг Аянчин</gradient>", 0, Rarity.RARE)),
            pass(chat("pass_h_altan", "Алтан Үг", "<gradient:#FFD36B:#F2A541>", "</gradient>", 0, Rarity.RARE)),
            pass(name("pass_h_khukh", "Хөх Тэнгэрийн", "<gradient:#4F8BFF:#9BD1FF>", "</gradient>", 0, Rarity.EPIC)),
            pass(join("pass_h_buree", "Бүрээний Дуу", "<gradient:#FFD24A:#FF9A3C>♪ Бүрээ дуугарч</gradient> <white>{name}</white> <#FFE08A>хүрэлцэн ирлээ!", 0, Rarity.EPIC)),
            pass(tag("pass_h_tumen", "Түмэний Ноён", "<gradient:#FF9A3C:#FFD24A:#FF9A3C>Түмэний Ноён</gradient>", 0, Rarity.EPIC)),
            pass(name("pass_h_galt", "Галт Шувуу", "<gradient:#FF6A3D:#FFC15E>", "</gradient>", 0, Rarity.EPIC)),
            pass(chat("pass_h_mungun", "Мөнгөн Үг", "<gradient:#D7DEE8:#9FB3C8>", "</gradient>", 0, Rarity.EPIC)),
            pass(join("pass_h_khurdan", "Хурдан Морины Туурай", "<#9FF3FF>♞ <#B8C0CC>Хурдан морины туурай дуулдаж — <white>{name}</white> <#9FF3FF>ирлээ!", 0, Rarity.EPIC)),
            pass(tag("pass_h_ikh_sayd", "Их Сайд", "<gradient:#B06BFF:#FFD24A>Их Сайд</gradient>", 0, Rarity.EPIC)),
            pass(name("pass_h_monkh", "Мөнх Тэнгэрийн", "<gradient:#7A5CFF:#4FD2D2>", "</gradient>", 0, Rarity.LEGENDARY)),
            pass(tag("pass_h_noyon", "Хуралдайн Ноён", "<gradient:#FFD24A:#FF4A4A:#B06BFF>✦ Хуралдайн Ноён ✦</gradient>", 0, Rarity.LEGENDARY)));

    private static final Map<String, Cosmetic> BY_ID = new LinkedHashMap<>();

    static {
        for (Cosmetic c : ALL) {
            if (BY_ID.put(c.id(), c) != null) throw new IllegalStateException("duplicate cosmetic " + c.id());
        }
    }

    public static Optional<Cosmetic> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static List<Cosmetic> of(Category category) {
        return ALL.stream().filter(c -> c.category() == category).toList();
    }
}
