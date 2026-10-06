package mn.suld.api.event;

import java.util.UUID;

/**
 * Marker for SULD domain events.
 *
 * <p>These are plain data objects, deliberately independent of Bukkit's event
 * system, so domain services can emit them without a server present (and be
 * unit-tested). The Paper layer subscribes an {@link EventDispatcher} that
 * re-publishes them as Bukkit events for other plugins/modules to observe.
 */
public interface SuldEvent {

    /** The player this event concerns. */
    UUID player();
}
