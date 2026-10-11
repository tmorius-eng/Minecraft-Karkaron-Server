package mn.suld.api.pvp;

/**
 * Pure player-versus-player rules (owner decision 2026-10-11): SÜLD is a hardcore <em>PvE</em> game. A player hurts
 * another player only when both agreed to it, by a {@code /duel} they both accepted or by standing on a declared
 * fighting ground, and never inside Kharkhorum or against a soul. {@code wildEnabled} restores the old open steppe.
 */
public final class PvpRules {

    public enum Verdict {
        ALLOWED_DUEL, ALLOWED_ARENA, ALLOWED_WILD,
        DENIED_CITY, DENIED_SOUL, DENIED_NO_CONSENT;

        public boolean allowed() {
            return this == ALLOWED_DUEL || this == ALLOWED_ARENA || this == ALLOWED_WILD;
        }
    }

    private PvpRules() {
    }

    /**
     * @param inCity      either player stands in Kharkhorum
     * @param anySoul     either player is a soul (death lock)
     * @param dueling     the two have an accepted, still-open duel
     * @param bothInArena both stand on a fighting ground
     * @param wildEnabled the server allows open PvP outside the city
     */
    public static Verdict judge(boolean inCity, boolean anySoul, boolean dueling, boolean bothInArena, boolean wildEnabled) {
        if (inCity) return Verdict.DENIED_CITY;
        if (anySoul) return Verdict.DENIED_SOUL;
        if (dueling) return Verdict.ALLOWED_DUEL;
        if (bothInArena) return Verdict.ALLOWED_ARENA;
        if (wildEnabled) return Verdict.ALLOWED_WILD;
        return Verdict.DENIED_NO_CONSENT;
    }

    /** The one-line reason shown to the attacker (Mongolian). */
    public static String reason(Verdict v) {
        return switch (v) {
            case DENIED_CITY -> "Хархорум бол аюулгүй бүс — тоглогчтой тулалдахгүй.";
            case DENIED_SOUL -> "Сүнстэй тулалдахгүй.";
            case DENIED_NO_CONSENT -> "SÜLD бол PvE ертөнц: тоглогчтой зөвхөн зөвшөөрөлтэй тулалдана — /duel <нэр>.";
            default -> "";
        };
    }
}
