package mn.suld.api.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * When a joined player enters SÜLD's game. Pure; the server thread drives it.
 *
 * <p>With {@link AuthMode#deferredAuthentication()} off a player enters on join. Behind AuthMe a joined player can
 * still be logged out (a wrong password is kicked only after the join, an older client logs in by chat command), so
 * they wait until AuthMe says they are logged in: its login event, or a later check that finds them logged in. Nothing
 * of SÜLD runs for a waiting player. A player who logs out again (AuthMe's {@code /logout}) has to leave: their game
 * was already set up, and setting it up twice is never safe.
 *
 * <p>Every call names the connection by the player's UUID and the session id {@link SessionRegistry} gave it, so a
 * late event of an old connection never moves a newer one.
 */
public final class AuthGate {

    public enum Step { ENTER, WAIT }

    private final boolean deferred;
    private final Map<UUID, Long> waiting = new HashMap<>();
    private final Map<UUID, Long> entered = new HashMap<>();

    public AuthGate(AuthMode mode) {
        this.deferred = mode.deferredAuthentication();
    }

    public boolean deferred() {
        return deferred;
    }

    /** The connection joined the world; {@code loggedIn} is what AuthMe says right now (ignored when not deferred). */
    public synchronized Step joined(UUID id, long session, boolean loggedIn) {
        entered.remove(id);
        waiting.remove(id);
        if (!deferred || loggedIn) {
            entered.put(id, session);
            return Step.ENTER;
        }
        waiting.put(id, session);
        return Step.WAIT;
    }

    /** AuthMe logged the connection in: true when it was waiting and enters now (once). */
    public synchronized boolean loggedIn(UUID id, long session) {
        if (!Long.valueOf(session).equals(waiting.get(id))) return false;
        waiting.remove(id);
        entered.put(id, session);
        return true;
    }

    /** The waiting connections that are logged in by now ({@code loggedIn}); they enter, each once. */
    public synchronized List<UUID> due(Predicate<UUID> loggedIn) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : List.copyOf(waiting.entrySet())) {
            if (loggedIn.test(e.getKey())) {
                waiting.remove(e.getKey());
                entered.put(e.getKey(), e.getValue());
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** AuthMe logged the connection out: true when it had entered, so it must leave. */
    public synchronized boolean loggedOut(UUID id, long session) {
        return Long.valueOf(session).equals(entered.get(id));
    }

    /** The connection ended. */
    public synchronized void quit(UUID id, long session) {
        waiting.remove(id, session);
        entered.remove(id, session);
    }

    public synchronized boolean isWaiting(UUID id) {
        return waiting.containsKey(id);
    }

    public synchronized boolean hasEntered(UUID id) {
        return entered.containsKey(id);
    }

    public synchronized int waitingCount() {
        return waiting.size();
    }
}
