package mn.suld.api.relic;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** Pure gameplay rules for relics. */
public final class RelicRules {

    /** At most one relic per bearer: holding one makes you the hunted, not a collector. */
    public static final int MAX_RELICS_PER_BEARER = 1;

    public enum ClaimDenial { NOT_AVAILABLE, NO_SHRINE, NO_CLASS, LEVEL_TOO_LOW, ALREADY_BEARER }

    public enum DeathOutcome { SEIZE_BY_KILLER, RETURN_TO_SHRINE }

    private RelicRules() {
    }

    public static Optional<ClaimDenial> checkClaim(RelicRecord r, RelicDefinition def, boolean hasClass,
                                                   int level, int relicsAlreadyBorne) {
        if (!r.isAvailable()) return Optional.of(ClaimDenial.NOT_AVAILABLE);
        if (r.shrine() == null) return Optional.of(ClaimDenial.NO_SHRINE);
        if (!hasClass) return Optional.of(ClaimDenial.NO_CLASS);
        if (level < def.minLevel()) return Optional.of(ClaimDenial.LEVEL_TOO_LOW);
        if (relicsAlreadyBorne >= MAX_RELICS_PER_BEARER) return Optional.of(ClaimDenial.ALREADY_BEARER);
        return Optional.empty();
    }

    /**
     * The bearer died. A different player who killed them (and bears no relic) seizes it;
     * any other death returns the relic to its shrine.
     */
    public static DeathOutcome onBearerDeath(UUID bearer, @Nullable UUID killer, int killerRelicCount) {
        if (killer != null && !killer.equals(bearer) && killerRelicCount < MAX_RELICS_PER_BEARER) {
            return DeathOutcome.SEIZE_BY_KILLER;
        }
        return DeathOutcome.RETURN_TO_SHRINE;
    }
}
