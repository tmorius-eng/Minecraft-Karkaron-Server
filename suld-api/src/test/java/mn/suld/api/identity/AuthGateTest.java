package mn.suld.api.identity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthGateTest {

    private static final UUID A = PlayerIdentity.offlineUuid("Dorj");
    private static final UUID B = PlayerIdentity.offlineUuid("Bat");

    @Test
    void verifiedModesEnterOnJoin() {
        for (AuthMode mode : new AuthMode[]{AuthMode.ONLINE, AuthMode.VELOCITY_FORWARDED, AuthMode.INSECURE}) {
            AuthGate g = new AuthGate(mode);
            assertEquals(AuthGate.Step.ENTER, g.joined(A, 1, false), mode + ": nothing to wait for");
            assertTrue(g.hasEntered(A));
            assertFalse(g.loggedIn(A, 1), "already in: never a second entry");
        }
    }

    @Test
    void behindAuthMeAJoinedPlayerWaitsForTheLogin() {
        AuthGate g = new AuthGate(AuthMode.HYBRID_AUTHME);
        assertEquals(AuthGate.Step.WAIT, g.joined(A, 1, false));
        assertTrue(g.isWaiting(A));
        assertFalse(g.hasEntered(A));
        assertTrue(g.loggedIn(A, 1), "the login lets them in");
        assertFalse(g.loggedIn(A, 1), "once");
        assertTrue(g.hasEntered(A));
        assertEquals(AuthGate.Step.ENTER, g.joined(B, 2, true), "already logged in at the join (a restored session)");
    }

    @Test
    void theCheckLetsInOnlyWhoIsLoggedInEachOnce() {
        AuthGate g = new AuthGate(AuthMode.HYBRID_AUTHME);
        g.joined(A, 1, false);
        g.joined(B, 2, false);
        assertEquals(List.of(), g.due(id -> false));
        assertEquals(List.of(A), g.due(Set.of(A)::contains));
        assertEquals(List.of(), g.due(Set.of(A)::contains), "A entered already");
        assertEquals(1, g.waitingCount());
        assertFalse(g.loggedIn(A, 1), "the late login event of A does nothing");
    }

    @Test
    void anOldConnectionNeverMovesANewerOne() {
        AuthGate g = new AuthGate(AuthMode.HYBRID_AUTHME);
        g.joined(A, 1, false);
        g.joined(A, 2, false); // reconnected
        assertFalse(g.loggedIn(A, 1), "the login of session 1 is not session 2's");
        g.quit(A, 1);
        assertTrue(g.isWaiting(A), "the old connection's quit leaves the new one waiting");
        assertTrue(g.loggedIn(A, 2));
        assertFalse(g.loggedOut(A, 1));
        assertTrue(g.loggedOut(A, 2), "logged out after entering: must leave");
        g.quit(A, 2);
        assertFalse(g.hasEntered(A));
        assertFalse(g.isWaiting(A));
    }

    @Test
    void aWaitingPlayerWhoLogsOutJustKeepsWaiting() {
        AuthGate g = new AuthGate(AuthMode.HYBRID_AUTHME);
        g.joined(A, 1, false);
        assertFalse(g.loggedOut(A, 1));
        assertTrue(g.isWaiting(A));
    }
}
