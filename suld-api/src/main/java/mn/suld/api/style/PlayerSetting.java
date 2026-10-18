package mn.suld.api.style;

/**
 * A per-player on/off setting of the «Тохиргоо» page (docs/UI_HUB.md). The player's style stores, as a bitmask
 * ({@link PlayerStyle#settingsOff()}), the settings changed from their default: for a setting that starts on its bit
 * means "turned off", for one that starts off ({@link #onByDefault()} false) it means "turned on". New settings need no
 * migration of existing rows. A bit, once used, is never given to another setting.
 */
public enum PlayerSetting {
    QUEST_TRACKER(0, "Эрлийн самбар", "Дэлгэцийн дээд талын эрлийн зурвас (мөн /quest track)."),
    SIDEBAR(1, "Хажуугийн самбар", "Баруун талын SÜLD самбар: дүр, хөрөнгө, эрэл, газар."),
    TIPS(2, "Зөвлөмж", "Үе үе чатад гарах зөвлөмжийн мөрүүд."),
    TOASTS(3, "HUD мэдэгдэл", "Үйлдлийн мөрөн дээрх богино мэдэгдлүүд."),
    MENU_SOUNDS(4, "Цэсний дуу", "Цонхны товч дарахад гарах дуу."),
    CHAT_HEADS(5, "Чатын толгой", "Чат дахь тоглогчийн толгойн зураг."),
    PARTY_INVITES(6, "Багийн урилга", "Бусдаас багийн урилга хүлээн авах."),
    DUEL_REQUESTS(7, "Тулааны урилга", "Бусдаас тулааны урилга хүлээн авах."),
    /*
     * Bit 8 was the minimap while it started on (until 2026-10-18): never reuse it. The minimap starts off now, because
     * a client whose core shader is not applied (a shader pack, some optimisation mods) sees its four maps hang in the
     * middle of the screen instead of in the corner; the players who want it turn it on (bit 10).
     */
    MINIMAP(10, false, "Мини газрын зураг", "Дэлгэцийн зүүн дээд буланд жижиг газрын зураг (SÜLD дүрс багцтай үед). "
            + "Анхдагчаар унтраалттай: shader pack-тай client дээр зураг дэлгэцийн дунд хөвж харагдана."),
    SCREEN_MARKERS(9, "Дэлгэцийн тэмдэг", "Миний тэмдэг, сүүлд унасан газар дэлгэц дээр нэр, зайтай (м); харагдахгүй үед захад сум.");

    private final int bit;
    private final boolean onByDefault;
    private final String label;
    private final String description;

    PlayerSetting(int bit, String label, String description) {
        this(bit, true, label, description);
    }

    PlayerSetting(int bit, boolean onByDefault, String label, String description) {
        this.bit = bit;
        this.onByDefault = onByDefault;
        this.label = label;
        this.description = description;
    }

    public int bit() {
        return bit;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Whether a player who never touched this setting has it on. */
    public boolean onByDefault() {
        return onByDefault;
    }

    /** Whether this setting is on given the player's mask of settings changed from their default. */
    public boolean enabled(long changedMask) {
        return onByDefault != ((changedMask & (1L << bit)) != 0);
    }

    /** The mask with this setting flipped. */
    public long toggled(long changedMask) {
        return changedMask ^ (1L << bit);
    }
}
