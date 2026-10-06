package mn.suld.api.service;

import mn.suld.api.profile.PlayerProfile;
import org.jetbrains.annotations.Nullable;

/**
 * Result of acquiring a player's profile at login.
 *
 * @param created      true only when storage positively reported "no profile for this UUID"
 * @param previousName the stored display name when it differs from the authenticated one
 *                     (an account rename), else null
 */
public record ProfileLoad(PlayerProfile profile, boolean created, @Nullable String previousName) {

    public boolean renamed() {
        return previousName != null;
    }
}
