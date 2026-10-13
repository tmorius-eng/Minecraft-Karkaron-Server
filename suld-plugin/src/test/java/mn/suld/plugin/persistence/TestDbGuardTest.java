package mn.suld.plugin.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The ITs drop the schema of the database they are given: only a test database is accepted (audit 2026-10-13). */
class TestDbGuardTest {

    @Test
    void onlyThrowawayDatabasesAreWiped() {
        assertDoesNotThrow(() -> TestDb.requireTestDatabase("jdbc:postgresql://127.0.0.1:5432/suld_it"));
        assertDoesNotThrow(() -> TestDb.requireTestDatabase("jdbc:postgresql://db:5499/suld_test?ssl=false"));
        assertThrows(IllegalStateException.class, () -> TestDb.requireTestDatabase("jdbc:postgresql://127.0.0.1:5432/suldit"));
        assertThrows(IllegalStateException.class, () -> TestDb.requireTestDatabase("jdbc:postgresql://10.0.0.5/suld"));
    }
}
