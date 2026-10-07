package mn.suld.api.style;

/**
 * A cosmetic a player can own and equip.
 *
 * @param id       stable id ({@code tag.chono})
 * @param category what it changes
 * @param name     Mongolian display name
 * @param price    SÜLD coins in {@code /shop} ({@code 0} = not sold; obtained otherwise, see {@code source})
 * @param style    MiniMessage template: for TAG the full styled tag text; for NAME/CHAT colours a template with
 *                 {@code {}} where the name/message goes; for JOIN the message with {@code {name}};
 *                 for EMOJI the code typed in chat (e.g. {@code :zurkh:})
 * @param rarity   display tier
 * @param source   how it is obtained: {@code shop} (coins or credits), {@code store} (credits only),
 *                 {@code level:N}, {@code default}
 *
 * <p>Credits (Сүлд Кредит) are the store currency. Cosmetics are the only thing credits buy — never items,
 * levels or power (Minecraft EULA: no pay-to-win).
 */
public record Cosmetic(String id, Category category, String name, long price, String style, Rarity rarity, String source) {

    public enum Category {
        TAG("Цол", "Нэрийн ард харагдах цол"),
        NAME_COLOR("Нэрийн өнгө", "Чат болон TAB дахь нэрний өнгө"),
        CHAT_COLOR("Чатын өнгө", "Таны бичсэн мессежийн өнгө"),
        JOIN_MESSAGE("Мэндчилгээ", "Таныг ороход бүгдэд харагдах мэдэгдэл"),
        EMOJI("Эможи", "Чатад :код: бичихэд гарах дүрс"),
        AURA("Гэрэлт тойрог", "Таны эргэн тойронд эргэлдэх гэрэл"),
        TRAIL("Мөр", "Явах замд тань үлдэх гялбаа"),
        KILL_EFFECT("Ялалтын нөлөө", "Мангас устгахад гарах үзэгдэл");

        private final String displayName;
        private final String description;

        Category(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() { return displayName; }
        public String description() { return description; }
    }

    public enum Rarity {
        COMMON("Энгийн", "#B8C0CC"), RARE("Ховор", "#4F8BFF"), EPIC("Гайхамшигт", "#B06BFF"), LEGENDARY("Домогт", "#FFB43C");

        private final String displayName;
        private final String color;

        Rarity(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String displayName() { return displayName; }
        public String color() { return color; }
    }

    /** Sold for SÜLD coins in {@code /shop}. */
    public boolean sold() {
        return price > 0 && "shop".equals(source);
    }

    /** Price in credits ({@code 0} = not for credits): a tenth of the coin price, or the store price. */
    public long creditPrice() {
        if ("store".equals(source)) return price;
        return sold() ? Math.max(5, Math.round(price / 10.0)) : 0;
    }
}
