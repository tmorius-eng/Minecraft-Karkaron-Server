package mn.suld.api.event;

import mn.suld.api.clazz.PlayerClass;

import java.util.Objects;
import java.util.UUID;

/** A player changed from one class to another (docs/CLASS_CHANGE.md); never fired for the first class pick. */
public record ClassChangedEvent(UUID player, PlayerClass from, PlayerClass to) implements SuldEvent {
    public ClassChangedEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
