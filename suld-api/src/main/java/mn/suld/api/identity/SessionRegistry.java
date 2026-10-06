package mn.suld.api.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks every live connection per UUID so profile ownership survives reconnects and
 * duplicate logins. A profile may only be unloaded when the <i>last</i> live session for its
 * UUID ends — never because an older, replaced session disconnected.
 *
 * <p>Sessions start PENDING at pre-login (before the player is in the world) and become
 * ACTIVE on join. Pending sessions whose client never arrives (disconnect during the
 * configuration phase, denied by a ban plugin, ...) are expired by {@link #expirePending}.
 * Thread-safe: pre-login runs on async threads, join/quit on the main thread.
 */
public final class SessionRegistry {

    public enum State { PENDING, ACTIVE }

    /** @param duplicate true when another live session already existed for this UUID */
    public record Start(long sessionId, boolean duplicate) {
    }

    /** @param last true when no other live session remains for {@code uuid} */
    public record Ended(UUID uuid, long sessionId, boolean last) {
    }

    private record Session(long id, State state, Instant since) {
    }

    private final AtomicLong ids = new AtomicLong();
    private final Map<UUID, LinkedHashMap<Long, Session>> live = new HashMap<>();

    public synchronized Start begin(UUID uuid, Instant now) {
        LinkedHashMap<Long, Session> sessions = live.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        boolean duplicate = !sessions.isEmpty();
        long id = ids.incrementAndGet();
        sessions.put(id, new Session(id, State.PENDING, now));
        return new Start(id, duplicate);
    }

    /**
     * The player entered the world: promote the newest pending session to ACTIVE.
     * @return its id, or -1 if no pending session exists (join without pre-login: refuse it)
     */
    public synchronized long activateNewest(UUID uuid, Instant now) {
        LinkedHashMap<Long, Session> sessions = live.get(uuid);
        if (sessions == null) return -1;
        long newest = -1;
        for (Session s : sessions.values()) {
            if (s.state() == State.PENDING) newest = s.id();
        }
        if (newest >= 0) sessions.put(newest, new Session(newest, State.ACTIVE, now));
        return newest;
    }

    public synchronized Ended end(UUID uuid, long sessionId) {
        LinkedHashMap<Long, Session> sessions = live.get(uuid);
        if (sessions == null || sessions.remove(sessionId) == null) {
            return new Ended(uuid, sessionId, sessions == null || sessions.isEmpty());
        }
        boolean last = sessions.isEmpty();
        if (last) live.remove(uuid);
        return new Ended(uuid, sessionId, last);
    }

    /** Remove PENDING sessions older than {@code ttl}; ACTIVE sessions end only via quit. */
    public synchronized List<Ended> expirePending(Instant now, Duration ttl) {
        List<Ended> out = new ArrayList<>();
        for (var it = live.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, LinkedHashMap<Long, Session>> e = it.next();
            List<Long> expired = new ArrayList<>();
            for (Session s : e.getValue().values()) {
                if (s.state() == State.PENDING && !s.since().plus(ttl).isAfter(now)) expired.add(s.id());
            }
            for (Long id : expired) {
                e.getValue().remove(id);
                out.add(new Ended(e.getKey(), id, e.getValue().isEmpty()));
            }
            if (e.getValue().isEmpty()) it.remove();
        }
        return out;
    }

    public synchronized int liveSessions(UUID uuid) {
        LinkedHashMap<Long, Session> sessions = live.get(uuid);
        return sessions == null ? 0 : sessions.size();
    }

    public synchronized long activeCount(UUID uuid) {
        LinkedHashMap<Long, Session> sessions = live.get(uuid);
        return sessions == null ? 0 : sessions.values().stream().filter(s -> s.state() == State.ACTIVE).count();
    }
}
