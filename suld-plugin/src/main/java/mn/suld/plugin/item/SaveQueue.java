package mn.suld.plugin.item;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * The players whose file {@link PlayerDataSaves#soon} writes, one a tick, in the order they were paid. A player paid
 * twice before their turn is written once. Main thread only.
 */
final class SaveQueue {

    private final LinkedHashSet<UUID> due = new LinkedHashSet<>();

    /** Queues {@code player}; false when they were already waiting. */
    boolean add(UUID player) {
        return due.add(player);
    }

    /** The next player to write, taken off the queue, or null when nobody is waiting. */
    UUID next() {
        Iterator<UUID> it = due.iterator();
        if (!it.hasNext()) return null;
        UUID id = it.next();
        it.remove();
        return id;
    }

    int size() {
        return due.size();
    }
}
