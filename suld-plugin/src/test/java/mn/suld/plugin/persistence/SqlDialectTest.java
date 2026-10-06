package mn.suld.plugin.persistence;

import mn.suld.api.config.StorageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlDialectTest {

    @Test
    void mysqlUpsertUsesOnDuplicateKey() {
        assertTrue(SqlDialect.MYSQL.profileUpsert().contains("ON DUPLICATE KEY UPDATE"));
    }

    @Test
    void postgresUpsertUsesOnConflict() {
        assertTrue(SqlDialect.POSTGRESQL.profileUpsert().contains("ON CONFLICT (player_uuid) DO UPDATE"));
    }

    @Test
    void memoryHasNoDialect() {
        assertThrows(IllegalArgumentException.class, () -> SqlDialect.forStorage(StorageType.MEMORY));
    }
}
