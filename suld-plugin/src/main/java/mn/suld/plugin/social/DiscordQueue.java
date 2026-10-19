package mn.suld.plugin.social;

import java.util.ArrayDeque;
import java.util.Optional;

/**
 * The lines waiting for one webhook, handed out as Discord-sized messages: as many whole lines as fit in one post
 * (Discord takes 2000 characters), a single longer line cut to the limit, and a bounded backlog so a burst (a reset
 * server writing many chronicle firsts at once) drops the oldest-but-unsent lines instead of growing without end;
 * the next message says how many went missing. Thread-safe: gameplay offers on the main thread, the flusher drains
 * off it.
 */
public final class DiscordQueue {

    public static final int MAX_CONTENT = 1900;
    public static final int MAX_PENDING = 50;

    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private int dropped;

    /** @return false for a blank line or a full backlog (counted, reported in the next message) */
    public synchronized boolean offer(String line) {
        if (line == null || line.isBlank()) return false;
        if (pending.size() >= MAX_PENDING) {
            dropped++;
            return false;
        }
        pending.add(line.strip());
        return true;
    }

    /** The next message to post, or empty when nothing waits. */
    public synchronized Optional<String> next() {
        if (pending.isEmpty()) return Optional.empty();
        StringBuilder sb = new StringBuilder();
        while (!pending.isEmpty()) {
            String l = pending.peek();
            if (l.length() > MAX_CONTENT) l = l.substring(0, MAX_CONTENT - 1) + "…";
            if (sb.length() > 0 && sb.length() + 1 + l.length() > MAX_CONTENT) break;
            pending.poll();
            if (sb.length() > 0) sb.append('\n');
            sb.append(l);
        }
        if (dropped > 0 && pending.isEmpty()) {
            String note = "(… " + dropped + " мөр орхигдов)";
            if (sb.length() + 1 + note.length() <= MAX_CONTENT) {
                sb.append('\n').append(note);
                dropped = 0;
            }
        }
        return Optional.of(sb.toString());
    }

    public synchronized int size() {
        return pending.size();
    }

    public synchronized int dropped() {
        return dropped;
    }
}
