package mn.suld.api.event;

import java.util.UUID;

/**
 * Fired when a player crosses one or more level boundaries.
 *
 * @param player    the affected player
 * @param fromLevel the level before the grant
 * @param toLevel   the level after the grant ({@code > fromLevel})
 */
public record LevelUpEvent(UUID player, int fromLevel, int toLevel) implements SuldEvent {

    public int levelsGained() {
        return toLevel - fromLevel;
    }
}
