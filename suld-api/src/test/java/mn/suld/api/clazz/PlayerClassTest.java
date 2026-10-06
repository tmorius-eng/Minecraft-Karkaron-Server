package mn.suld.api.clazz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerClassTest {

    @Test
    void thereAreExactlyFiveClasses() {
        assertEquals(5, PlayerClass.values().length);
    }

    @Test
    void idsAreUniqueAndLowercase() {
        for (PlayerClass value : PlayerClass.values()) {
            assertEquals(value.id(), value.id().toLowerCase(java.util.Locale.ROOT));
            assertEquals(value, PlayerClass.byId(value.id()).orElseThrow());
        }
    }

    @Test
    void byIdIsCaseInsensitiveAndTrims() {
        assertEquals(PlayerClass.BAATAR, PlayerClass.byId("  BAATAR ").orElseThrow());
    }

    @Test
    void byIdRejectsUnknown() {
        assertTrue(PlayerClass.byId("sorcerer").isEmpty());
        assertTrue(PlayerClass.byId(null).isEmpty());
    }
}
