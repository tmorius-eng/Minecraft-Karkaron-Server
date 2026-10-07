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
        return jdbcUrl(db, java.nio.file.Path.of("plugins", "SULD"));
    }

    /** {@code dataFolder}: where an embedded H2 database keeps its file ({@code <dataFolder>/data/<database>.mv.db}). */
    public static String jdbcUrl(DatabaseSettings db, java.nio.file.Path dataFolder) {
        return switch (db.type()) {
            // MySQL mode: the MySQL migrations and ON DUPLICATE KEY upserts run unchanged
            case H2 -> "jdbc:h2:file:" + dataFolder.resolve("data").resolve(db.database()).toAbsolutePath().toString().replace('\\', '/')
                    + ";" + H2_OPTIONS;
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
    /** H2 compatibility options shared by the plugin and the tests. */
    public static final String H2_OPTIONS = "MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=KEY,VALUE,USER";

    public static HikariDataSource create(DatabaseSettings db) {
        return create(db, java.nio.file.Path.of("plugins", "SULD"));
    }

    public static HikariDataSource create(DatabaseSettings db, java.nio.file.Path dataFolder) {
        if (db.type() == StorageType.MEMORY) {
            throw new IllegalArgumentException("Cannot create a DataSource for MEMORY storage");
        }
        SqlDialect dialect = SqlDialect.forStorage(db.type());

        HikariConfig config = new HikariConfig();
        config.setPoolName("suld-pool");
        config.setJdbcUrl(jdbcUrl(db, dataFolder));
        if (db.type() == StorageType.H2) {
            config.setUsername("sa"); // a local file only this server opens; no network listener
            config.setPassword("");
            config.setDriverClassName("org.h2.Driver");
        } else {
            config.setUsername(db.username());
            config.setPassword(db.password());
            config.setDriverClassName(dialect.driverClass());
        }
        config.setMaximumPoolSize(db.poolSize());
        config.setConnectionTimeout(db.connectionTimeoutMs());
        config.setPoolName("suld");
        // Fail fast if the DB is unreachable at startup rather than hanging joins.
        config.setInitializationFailTimeout(db.connectionTimeoutMs());
        return new HikariDataSource(config);
    }
}
