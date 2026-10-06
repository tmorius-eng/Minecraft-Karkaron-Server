package mn.suld.api.service;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.progression.ProgressionEngine;

/**
 * Applies experience to profiles and publishes the resulting domain events.
 *
 * <p>Implementations own no persistence; they mutate the in-memory profile and
 * leave saving to the profile service / auto-save. This keeps levelling cheap
 * enough to run on the main thread.
 */
public interface ProgressionService {

    /**
     * Grant experience to a profile, applying levelling and emitting
     * {@code ExpGainedEvent} / {@code LevelUpEvent} as appropriate.
     *
     * @return the levelling result
     */
    ExpGainResult grantExp(PlayerProfile profile, long amount, ExpSource source);

    /** The level curve engine backing this service. */
    ProgressionEngine engine();
}
