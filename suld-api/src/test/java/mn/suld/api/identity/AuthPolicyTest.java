package mn.suld.api.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
    void hybridAcceptsOnlyTheExactOfflineUuidOfTheName() {
        AuthPolicy p = new AuthPolicy(AuthMode.HYBRID_AUTHME, false);
        assertTrue(p.acceptsLogins());
        assertEquals(AuthDecision.Code.ALLOWED_HYBRID, p.evaluate(OFFLINE, "Steve").code());
        assertEquals(AuthDecision.Code.DENIED_UNAUTHENTICATED_UUID, p.evaluate(MOJANG, "Steve").code(),
                "a v4 UUID was not made by the offline proxy");
        assertEquals(AuthDecision.Code.DENIED_UNAUTHENTICATED_UUID, p.evaluate(OFFLINE, "Alex").code(),
                "another name's offline UUID is a spoofed forwarding");
        assertEquals(AuthDecision.Code.DENIED_UNAUTHENTICATED_UUID, p.evaluate(OFFLINE, "steve").code(),
                "the name's case is part of its offline UUID");
    }

    @Test
    void hybridIgnoresTheDevFlag() {
        AuthDecision d = new AuthPolicy(AuthMode.HYBRID_AUTHME, true).evaluate(MOJANG, "Steve");
        assertFalse(d.allowed(), "the dev escape hatch never widens the hybrid rule");
        assertEquals(AuthDecision.Code.ALLOWED_HYBRID, new AuthPolicy(AuthMode.HYBRID_AUTHME, true).evaluate(OFFLINE, "Steve").code());
    }

    @Test
    void onlyHybridDefersAuthentication() {
        for (AuthMode mode : AuthMode.values()) {
            assertEquals(mode == AuthMode.HYBRID_AUTHME, mode.deferredAuthentication(), mode.name());
        }
        assertTrue(AuthMode.HYBRID_AUTHME.verified());
    }

    @Test
    void aNameBelongsToItsStoredOwner() {
        UUID dorj = PlayerIdentity.offlineUuid("Dorj");
        UUID twin = PlayerIdentity.offlineUuid("dorj");
        assertTrue(AuthDecision.checkNameOwner(dorj, Optional.empty()).allowed(), "a free name");
        assertTrue(AuthDecision.checkNameOwner(dorj, Optional.of(dorj)).allowed(), "the owner returns");
        AuthDecision d = AuthDecision.checkNameOwner(twin, Optional.of(dorj));
        assertFalse(d.allowed(), "a lower-case twin never shadows the stored profile");
        assertEquals(AuthDecision.Code.DENIED_NAME_TAKEN, d.code());
        assertFalse(d.reason().isBlank());
        assertEquals(AuthDecision.Code.DENIED_AUTH_BRIDGE_UNAVAILABLE, AuthDecision.bridgeUnavailable().code());
        assertFalse(AuthDecision.bridgeUnavailable().allowed());
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
