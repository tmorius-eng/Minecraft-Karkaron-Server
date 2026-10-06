package mn.suld.plugin.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import mn.suld.api.config.DatabaseSettings;
import mn.suld.api.config.StorageType;

/**
 * Builds a pooled {@link javax.sql.DataSource} (HikariCP) from
 * {@link DatabaseSettings}. The JDBC URL builder is static and pure so it can be
 * unit-tested without a database or server.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    /** Build the JDBC URL for the configured backend. */
    public static String jdbcUrl(DatabaseSettings db) {
        return switch (db.type()) {
            case MYSQL -> "jdbc:mysql://" + db.host() + ":" + db.port() + "/" + db.database()
                    + "?useSSL=" + db.useSsl()
                    + "&characterEncoding=utf8&useUnicode=true&autoReconnect=true";
            case POSTGRESQL -> "jdbc:postgresql://" + db.host() + ":" + db.port() + "/" + db.database()
                    + (db.useSsl() ? "?ssl=true" : "");
            case MEMORY -> throw new IllegalArgumentException("MEMORY storage has no JDBC URL");
        };
    }

    /**
     * Create a configured, pooled data source. Must not be called for
     * {@link StorageType#MEMORY}.
     */
    public static HikariDataSource create(DatabaseSettings db) {
        if (db.type() == StorageType.MEMORY) {
            throw new IllegalArgumentException("Cannot create a DataSource for MEMORY storage");
        }
        SqlDialect dialect = SqlDialect.forStorage(db.type());

        HikariConfig config = new HikariConfig();
        config.setPoolName("suld-pool");
        config.setJdbcUrl(jdbcUrl(db));
        config.setUsername(db.username());
        config.setPassword(db.password());
        config.setDriverClassName(dialect.driverClass());
        config.setMaximumPoolSize(db.poolSize());
        config.setConnectionTimeout(db.connectionTimeoutMs());
        config.setPoolName("suld");
        // Fail fast if the DB is unreachable at startup rather than hanging joins.
        config.setInitializationFailTimeout(db.connectionTimeoutMs());
        return new HikariDataSource(config);
    }
}
