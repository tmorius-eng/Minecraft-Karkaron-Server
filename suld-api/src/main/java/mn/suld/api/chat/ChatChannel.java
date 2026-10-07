package mn.suld.api.chat;

import java.util.Locale;
import java.util.Optional;

/**
 * SÜLD chat channels (docs/CHAT.md). Every chat line goes to exactly one channel; who hears it is decided by the
 * channel (everyone, players nearby, the party, the clan). {@link #ALL} is not a channel one speaks in: it is the
 * "everything I can hear" view of the chat screen.
 */
public enum ChatChannel {
    ALL("Бүгд", "", 0xFFFFFF, false),
    GLOBAL("Нийт", "Н", 0xF2D27A, true),
    LOCAL("Ойр", "О", 0xD8D8D8, true),
    PARTY("Бүлэг", "Б", 0x7FD8FF, true),
    CLAN("Овог", "Ов", 0x6FE07A, true),
    TRADE("Худалдаа", "Х", 0xFFB45A, true);

    /** Blocks within which a LOCAL line is heard. */
    public static final int LOCAL_RADIUS = 80;

    private final String label;
    private final String tag;
    private final int rgb;
    private final boolean speakable;

    ChatChannel(String label, String tag, int rgb, boolean speakable) {
        this.label = label;
        this.tag = tag;
        this.rgb = rgb;
        this.speakable = speakable;
    }

    public String label() { return label; }

    /** The short tag in front of a line ("[Н]"). */
    public String tag() { return tag; }

    public int rgb() { return rgb; }

    public boolean speakable() { return speakable; }

    /** A channel by its Mongolian label, short tag or English id ("ойр", "о", "local", "l"). */
    public static Optional<ChatChannel> parse(String s) {
        if (s == null || s.isBlank()) return Optional.empty();
        String k = s.strip().toLowerCase(Locale.ROOT);
        for (ChatChannel c : values()) {
            if (!c.speakable) continue;
            if (c.label.toLowerCase(Locale.ROOT).equals(k) || c.tag.toLowerCase(Locale.ROOT).equals(k) || c.name().toLowerCase(Locale.ROOT).equals(k)) return Optional.of(c);
        }
        return switch (k) {
            case "g", "global", "n" -> Optional.of(GLOBAL);
            case "l", "local" -> Optional.of(LOCAL);
            case "p", "party", "b" -> Optional.of(PARTY);
            case "c", "clan", "ov" -> Optional.of(CLAN);
            case "t", "trade", "kh" -> Optional.of(TRADE);
            default -> Optional.empty();
        };
    }
}
