package mn.suld.api.config;

/** The Баатрын Хроник: whether new entries are announced to everyone, and how many the Hall of Fame shows. */
public record ChronicleSettings(boolean enabled, boolean broadcast, int hallEntries) {

    public ChronicleSettings {
        hallEntries = Math.max(3, Math.min(30, hallEntries));
    }

    public static ChronicleSettings defaults() {
        return new ChronicleSettings(true, true, 12);
    }
}
