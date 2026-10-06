package mn.suld.plugin.persistence;

import mn.suld.api.config.DatabaseSettings;
import mn.suld.api.config.StorageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceFactoryTest {

    private DatabaseSettings db(StorageType type, boolean ssl) {
        return new DatabaseSettings(type, "db.local", type == StorageType.POSTGRESQL ? 5432 : 3306,
                "suld", "user", "pw", 10, 10_000L, ssl);
    }

    @Test
    void mysqlUrl() {
        String url = DataSourceFactory.jdbcUrl(db(StorageType.MYSQL, false));
        assertTrue(url.startsWith("jdbc:mysql://db.local:3306/suld"), url);
        assertTrue(url.contains("useSSL=false"), url);
    }

    @Test
    void postgresUrl() {
        String plain = DataSourceFactory.jdbcUrl(db(StorageType.POSTGRESQL, false));
        assertTrue(plain.equals("jdbc:postgresql://db.local:5432/suld"), plain);
        String ssl = DataSourceFactory.jdbcUrl(db(StorageType.POSTGRESQL, true));
        assertTrue(ssl.endsWith("?ssl=true"), ssl);
    }

    @Test
    void memoryHasNoUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> DataSourceFactory.jdbcUrl(db(StorageType.MEMORY, false)));
    }
}
