package mn.suld.api.event;

import mn.suld.api.clazz.PlayerClass;

import java.util.UUID;

/**
 * Fired when a player chooses their class for the first time.
 *
 * @param player        the affected player
 * @param selectedClass the chosen class
 */
public record ClassSelectedEvent(UUID player, PlayerClass selectedClass) implements SuldEvent {
}
