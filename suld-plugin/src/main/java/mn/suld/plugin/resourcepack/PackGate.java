package mn.suld.plugin.resourcepack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds a screen (the first-join class menu) back until the player's resource-pack prompt is settled.
 * <p>
 * The vanilla client shows the pack prompt as a screen; opening an inventory in the same tick replaces that screen
 * and the request is never answered (audit 2026-10-11, ONB-001). So the action runs only when the client reports a
 * final answer, or when it is clearly not going to:
 * <ul>
 *   <li>a terminal status (loaded, declined, failed, discarded) runs it at once;</li>
 *   <li>ACCEPTED / DOWNLOADED mean the prompt is gone and the download is under way: wait for the result, at most
 *       {@code downloadMs} more (a slow link must not leave the player without a menu);</li>
 *   <li>no answer at all: the prompt may still be on screen, so wait {@code promptMs} (the player is reading it)
 *       before giving up and running the action anyway.</li>
 * </ul>
 * Pure (no Bukkit): the service feeds it statuses and a clock, and runs what {@link #due(long)} returns.
 */
public final class PackGate {

    /** How a client answer moves the gate. */
    public enum Answer { TERMINAL, IN_PROGRESS, OTHER }

    private record Waiting(Runnable action, long deadline, boolean accepted) {
    }

    private final long promptMs;
    private final long downloadMs;
    private final Map<UUID, Waiting> waiting = new ConcurrentHashMap<>();

    public PackGate(long promptMs, long downloadMs) {
        this.promptMs = promptMs;
        this.downloadMs = downloadMs;
    }

    /** Run {@code action} once the pack prompt of {@code id} is settled. Replaces an earlier pending action. */
    public void await(UUID id, long now, Runnable action) {
        waiting.put(id, new Waiting(action, now + promptMs, false));
    }

    /**
     * A status from the client. Returns the action to run now (terminal answer), or null.
     */
    public Runnable answer(UUID id, Answer answer, long now) {
        Waiting w = waiting.get(id);
        if (w == null) return null;
        switch (answer) {
            case TERMINAL -> {
                return waiting.remove(id) != null ? w.action() : null;
            }
            case IN_PROGRESS -> {
                // the prompt is answered; give the download its own budget (once)
                if (!w.accepted()) waiting.put(id, new Waiting(w.action(), now + downloadMs, true));
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** Actions whose deadline has passed (removed from the gate). */
    public List<Runnable> due(long now) {
        List<Runnable> out = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Waiting>> it = waiting.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Waiting> e = it.next();
            if (now >= e.getValue().deadline()) {
                out.add(e.getValue().action());
                it.remove();
            }
        }
        return out;
    }

    public boolean pending(UUID id) {
        return waiting.containsKey(id);
    }

    /** Drop whatever was pending (the player left). */
    public void forget(UUID id) {
        waiting.remove(id);
    }
}
