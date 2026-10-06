package mn.suld.api.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerIdentityTest {

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void identityIsTheUuidNotTheName() {
        UUID id = UUID.randomUUID();
        PlayerIdentity before = PlayerIdentity.of(id, "OldName");
        PlayerIdentity after = PlayerIdentity.of(id, "NewName");
        assertEquals(before, after, "a renamed account is the same player");
        assertEquals(before.hashCode(), after.hashCode());
        assertTrue(after.isRenameOf("OldName"));
        assertFalse(after.isRenameOf("NewName"));
    }

    @Test
    void sameNameDifferentUuidIsADifferentPlayer() {
        assertNotEquals(PlayerIdentity.of(UUID.randomUUID(), "qeevr_"), PlayerIdentity.of(UUID.randomUUID(), "qeevr_"),
                "usernames never identify a player");
    }

    @Test
    void authenticatedUuidShape() {
        assertTrue(PlayerIdentity.of(UUID.randomUUID(), "Steve").hasAuthenticatedUuidShape(), "Mojang UUIDs are v4");
        assertFalse(PlayerIdentity.of(offlineUuid("Steve"), "Steve").hasAuthenticatedUuidShape(), "offline UUIDs are v3");
    }

    @Test
    void nameValidation() {
        assertTrue(PlayerIdentity.isValidName("qeevr_"));
        assertTrue(PlayerIdentity.isValidName("a"), "legacy short names exist");
        assertFalse(PlayerIdentity.isValidName("seventeen_chars_x"));
        assertFalse(PlayerIdentity.isValidName("bad name"));
        assertFalse(PlayerIdentity.isValidName("§cAdmin"));
        assertFalse(PlayerIdentity.isValidName(""));
        assertFalse(PlayerIdentity.isValidName(null));
        assertThrows(IllegalArgumentException.class, () -> PlayerIdentity.of(UUID.randomUUID(), "x y"));
        assertThrows(NullPointerException.class, () -> PlayerIdentity.of(null, "ok"));
    }
}
