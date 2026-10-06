package mn.suld.api.style;

import mn.suld.api.style.Cosmetic.Category;
import mn.suld.api.style.Cosmetic.Rarity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every SÜLD cosmetic — Mongolian themed. Prices in SÜLD coins; nothing is sold for real money. */
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

    /** Credits-only (store) cosmetic; {@code credits} is its price in credits. */
    private static Cosmetic store(String id, Category cat, String name, String style, long credits, Rarity r) {
        return new Cosmetic(id, cat, name, credits, style, r, "store");
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
            tag("naadam", "Наадамч", "<gradient:#FF4A4A:#FFD24A>Наадамч</gradient>", 700, Rarity.EPIC),
            tag("tsagaan_sar", "Цагаан Сар", "<gradient:#FFFFFF:#9FF3FF>Цагаан Сар</gradient>", 700, Rarity.EPIC),
            tag("irves", "Алтайн Ирвэс", "<gradient:#FFFFFF:#8C96A8>Алтайн Ирвэс</gradient>", 900, Rarity.EPIC),
            tag("tenger", "Мөнх Тэнгэр", "<gradient:#2A6BFF:#9FF3FF:#2A6BFF>Мөнх Тэнгэр</gradient>", 1_500, Rarity.LEGENDARY),
            tag("khar_suld", "Хар Сүлд", "<gradient:#3C3C46:#FF4A4A>Хар Сүлд</gradient>", 1_800, Rarity.LEGENDARY),
            tag("tsagaan_suld", "Цагаан Сүлд", "<gradient:#FFFFFF:#C8D2E6>Цагаан Сүлд</gradient>", 1_800, Rarity.LEGENDARY),
            tag("altan_urag", "Алтан Ураг", "<gradient:#FFF0A0:#FFD24A:#FF9A3C>Алтан Ураг</gradient>", 3_000, Rarity.LEGENDARY),
            levelTag("anchin", "Анчин", "<#6BD06B>Анчин", 5, Rarity.COMMON),
            levelTag("tal_baatar", "Талын Баатар", "<gradient:#6BD06B:#FFD24A>Талын Баатар</gradient>", 30, Rarity.EPIC),
            levelTag("monkh_baatar", "Мөнхийн Баатар", "<gradient:#FFD24A:#FF4A4A:#B06BFF>Мөнхийн Баатар</gradient>", 60, Rarity.LEGENDARY),
            store("tag.suld_sakhiulsan", Category.TAG, "Сүлд Сахиулсан", "<gradient:#FFD24A:#9FF3FF:#FFD24A>✦ Сүлд Сахиулсан ✦</gradient>", 250, Rarity.LEGENDARY),
            store("tag.tengeriin_khuu", Category.TAG, "Тэнгэрийн Хүү", "<gradient:#9FF3FF:#4F8BFF:#B06BFF>Тэнгэрийн Хүү</gradient>", 200, Rarity.LEGENDARY),
            store("tag.chingis", Category.TAG, "Их Хааны Ач", "<gradient:#FF4A4A:#FFD24A:#FF4A4A>⚜ Их Хааны Ач ⚜</gradient>", 400, Rarity.LEGENDARY),
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
            emoji("guul", "Гавал", ":guul:", 400, Rarity.EPIC));

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
