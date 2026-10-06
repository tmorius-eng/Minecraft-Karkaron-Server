package mn.suld.api.event;

import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;

import java.util.UUID;

/**
 * Fired whenever a player gains experience, after the grant has been applied.
 *
 * @param player the affected player
 * @param amount the experience granted ({@code >= 0})
 * @param source where the experience came from
 * @param result the full levelling result (before/after, levels gained, etc.)
 */
public record ExpGainedEvent(UUID player, long amount, ExpSource source, ExpGainResult result)
        implements SuldEvent {
}
