package mn.suld.api.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthPolicyTest {

    private static final UUID MOJANG = UUID.randomUUID();
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));

    @Test
    void onlineModeAcceptsAuthenticatedAccounts() {
        AuthPolicy p = new AuthPolicy(AuthMode.ONLINE, false);
        assertTrue(p.acceptsLogins());
        assertEquals(AuthDecision.Code.ALLOWED, p.evaluate(MOJANG, "Steve").code());
    }

    @Test
    void verifiedModesRejectOfflineShapedUuidsAsSpoofing() {
        for (AuthMode mode : new AuthMode[]{AuthMode.ONLINE, AuthMode.VELOCITY_FORWARDED}) {
            AuthDecision d = new AuthPolicy(mode, true).evaluate(OFFLINE, "Steve");
            assertFalse(d.allowed(), mode + " must reject a v3 UUID even if the dev flag is set");
            assertEquals(AuthDecision.Code.DENIED_UNAUTHENTICATED_UUID, d.code());
        }
    }

    @Test
    void insecureServerFailsClosed() {
        AuthPolicy p = new AuthPolicy(AuthMode.INSECURE, false);
        assertFalse(p.acceptsLogins());
        AuthDecision d = p.evaluate(MOJANG, "Steve");
        assertFalse(d.allowed(), "even a real-looking UUID is untrusted when nothing verified it");
        assertEquals(AuthDecision.Code.DENIED_SERVER_INSECURE, d.code());
    }

    @Test
    void devEscapeHatchIsExplicitAndLabelled() {
        AuthDecision d = new AuthPolicy(AuthMode.INSECURE, true).evaluate(OFFLINE, "Steve");
        assertTrue(d.allowed());
        assertEquals(AuthDecision.Code.ALLOWED_INSECURE_DEV, d.code(), "dev logins are always distinguishable");
    }

    @Test
    void invalidNamesRejectedInEveryMode() {
        for (AuthMode mode : AuthMode.values()) {
            assertEquals(AuthDecision.Code.DENIED_INVALID_NAME,
                    new AuthPolicy(mode, true).evaluate(MOJANG, "§4Admin").code());
            assertEquals(AuthDecision.Code.DENIED_INVALID_NAME, new AuthPolicy(mode, true).evaluate(null, "Steve").code());
        }
    }
}
