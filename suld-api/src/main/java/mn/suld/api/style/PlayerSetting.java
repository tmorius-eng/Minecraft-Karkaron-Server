package mn.suld.api.style;

/**
 * A per-player on/off setting of the «Тохиргоо» page (docs/UI_HUB.md). Every setting is on by default; the
 * player's style stores the ones turned <em>off</em> as a bitmask ({@link PlayerStyle#settingsOff()}), so new
 * settings need no migration of existing rows.
 */
public enum PlayerSetting {
    QUEST_TRACKER(0, "Эрлийн самбар", "Дэлгэцийн дээд талын эрлийн зурвас (мөн /quest track)."),
    SIDEBAR(1, "Хажуугийн самбар", "Баруун талын SÜLD самбар: дүр, хөрөнгө, эрэл, газар."),
    TIPS(2, "Зөвлөмж", "Үе үе чатад гарах зөвлөмжийн мөрүүд."),
    TOASTS(3, "HUD мэдэгдэл", "Үйлдлийн мөрөн дээрх богино мэдэгдлүүд."),
    MENU_SOUNDS(4, "Цэсний дуу", "Цонхны товч дарахад гарах дуу."),
    CHAT_HEADS(5, "Чатын толгой", "Чат дахь тоглогчийн толгойн зураг."),
    PARTY_INVITES(6, "Багийн урилга", "Бусдаас багийн урилга хүлээн авах."),
    DUEL_REQUESTS(7, "Тулааны урилга", "Бусдаас тулааны урилга хүлээн авах.");

    private final int bit;
    private final String label;
    private final String description;

    PlayerSetting(int bit, String label, String description) {
        this.bit = bit;
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

    /** Whether this setting is on given the player's off-mask. */
    public boolean enabled(long offMask) {
        return (offMask & (1L << bit)) == 0;
    }

    /** The off-mask with this setting flipped. */
    public long toggled(long offMask) {
        return offMask ^ (1L << bit);
    }
}
