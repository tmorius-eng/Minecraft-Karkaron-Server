package mn.suld.api.chronicle;

import java.util.Locale;

/** Pure rules of the Хроник: when a record is beaten, and the Mongolian headline of an entry. */
public final class ChronicleRules {

    private ChronicleRules() {
    }

    /** For a RECORD kind: is {@code candidate} better than {@code current}? (fastest clear: fewer seconds). */
    public static boolean better(ChronicleKind kind, Long current, Long candidate) {
        if (!kind.isRecord() || candidate == null) return false;
        if (current == null) return true;
        return switch (kind) {
            case FASTEST_DUNGEON_CLEAR, FASTEST_WORLD_BOSS_KILL -> candidate < current;
            default -> candidate > current;
        };
    }

    /** {@code m:ss} for a clear time. */
    public static String formatSeconds(long seconds) {
        return (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    /**
     * The one line the server hears and the Hall shows. {@code subjectName} is the display name of the subject
     * (the dungeon, site, relic or campaign); the entry's detail carries the rest.
     */
    public static String headline(ChronicleEntry e, String subjectName) {
        String who = e.party().size() > 1 ? String.join(", ", e.party()) : e.playerName();
        String clan = e.clanTag().isBlank() ? "" : " [" + e.clanTag() + "]";
        return switch (e.kind()) {
            case FIRST_DUNGEON_CLEAR -> "✦ Хроник: " + who + clan + " «" + subjectName + "»-ийг анх цэвэрлэв"
                    + (e.value() == null ? "" : " (" + formatSeconds(e.value()) + ")") + ".";
            case FASTEST_DUNGEON_CLEAR -> "✦ Хроник: " + who + clan + " «" + subjectName + "»-ийн дээд амжилт — " + formatSeconds(e.value() == null ? 0 : e.value()) + ".";
            case RELIC_CLAIMED -> "✦ Хроник: " + e.playerName() + clan + " «" + subjectName + "»-ийг олж авав.";
            case FIRST_SITE_DISCOVERY -> "✦ Хроник: " + e.playerName() + clan + " «" + subjectName + "»-д анх хүрэв.";
            case CAMPAIGN_RESULT -> "✦ Хроник: " + e.detail();
            case KHURALDAI_RESULT -> "✦ Хуралдай: " + e.detail();
            case FIRST_WORLD_BOSS_KILL -> "✦ Хроник: " + who + clan + " тэргүүтэй баатрууд «" + subjectName + "»-г анх дарав"
                    + (e.value() == null ? "" : " (" + formatSeconds(e.value()) + ")") + ".";
            case FASTEST_WORLD_BOSS_KILL -> "✦ Хроник: «" + subjectName + "»-г хамгийн хурдан дарсан — " + formatSeconds(e.value() == null ? 0 : e.value())
                    + " (" + who + clan + " тэргүүтэй).";
        };
    }

    /** The short label of a kind for menus. */
    public static String label(ChronicleKind k) {
        return switch (k) {
            case FIRST_DUNGEON_CLEAR -> "Анхны цэвэрлэгээ";
            case FASTEST_DUNGEON_CLEAR -> "Дээд амжилт";
            case RELIC_CLAIMED -> "Сүлд олсон";
            case FIRST_SITE_DISCOVERY -> "Анхны нээлт";
            case CAMPAIGN_RESULT -> "Аяны үр дүн";
            case KHURALDAI_RESULT -> "Хуралдайн шийдвэр";
            case FIRST_WORLD_BOSS_KILL -> "Анхны ялалт";
            case FASTEST_WORLD_BOSS_KILL -> "Хурдан ялалт";
        };
    }
}
