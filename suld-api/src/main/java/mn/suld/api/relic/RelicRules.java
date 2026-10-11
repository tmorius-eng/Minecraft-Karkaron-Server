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
     *
     * @deprecated since the PvP policy (2026-10-11) a kill seizes only inside a consented fight: use
     * {@link #onBearerDeath(UUID, UUID, int, boolean)}.
     */
    @Deprecated
    public static DeathOutcome onBearerDeath(UUID bearer, @Nullable UUID killer, int killerRelicCount) {
        return onBearerDeath(bearer, killer, killerRelicCount, true);
    }

    /**
     * The bearer died. A different player who killed them in a <em>consented</em> fight (a duel both accepted, or a
     * fighting ground) and bears no relic seizes it; any other death, including a kill the policy should have
     * prevented, returns the relic to its shrine. SÜLD is PvE: the relic makes its bearer famous, not hunted.
     */
    public static DeathOutcome onBearerDeath(UUID bearer, @Nullable UUID killer, int killerRelicCount, boolean consentedFight) {
        if (consentedFight && killer != null && !killer.equals(bearer) && killerRelicCount < MAX_RELICS_PER_BEARER) {
            return DeathOutcome.SEIZE_BY_KILLER;
        }
        return DeathOutcome.RETURN_TO_SHRINE;
    }
}
