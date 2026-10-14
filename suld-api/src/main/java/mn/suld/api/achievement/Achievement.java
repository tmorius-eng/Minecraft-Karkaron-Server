package mn.suld.api.achievement;

/**
 * A deed the server honours (docs/ACHIEVEMENTS_AND_PASS.md): earned once, never bought, never power.
 *
 * @param id          stable id ({@code ach.<category>.<name>})
 * @param name        Mongolian name
 * @param description what it asks, in Mongolian (and, for a historical name, where the name comes from)
 * @param category    the page it is listed on
 * @param criterion   what unlocks it
 * @param points      the Хуралдайн Зам points it gives once (the season's pass)
 * @param reward      a cosmetic it grants ({@code tag.…}), or null
 * @param basis       how its name relates to history
 * @param source      for a {@link Basis#VERIFIED} or {@link Basis#LEGEND} name, the source it is attested in
 * @param hidden      listed only once unlocked
 */
public record Achievement(String id, String name, String description, Category category, Criterion criterion, int points,
                          String reward, Basis basis, String source, boolean hidden) {

    /** The pages of {@code /achievements}. */
    public enum Category {
        STORY("Түүх"),
        BATTLE("Тулаан"),
        HEROIC("Баатарлаг"),
        LANDS("Нутаг"),
        CRAFTS("Мэргэжил"),
        HONOUR("Алдар"),
        SOCIAL("Нийгэм");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * How a name relates to history (the repo's labels, docs/CULTURAL_REVIEW_P1.md): attested in a named source, a
     * legend told in one, or only in the steppe's spirit. The game never presents an inspired name as history.
     */
    public enum Basis {
        VERIFIED("Түүхэнд тэмдэглэгдсэн"),
        LEGEND("Домогт өгүүлэгдсэн"),
        INSPIRED("Тал нутгийн сэдлээр");

        private final String displayName;

        Basis(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
