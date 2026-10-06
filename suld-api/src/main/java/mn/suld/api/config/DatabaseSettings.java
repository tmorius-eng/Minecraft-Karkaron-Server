package mn.suld.api.config;

/**
 * Connection settings for the persistence backend. Pure data; the Paper layer's
 * {@code DataSourceFactory} turns this into a pooled {@code DataSource}.
 *
 * @param type               backend selector
 * @param host               database host (ignored for {@link StorageType#MEMORY})
 * @param port               database port
 * @param database           schema/database name
 * @param username           login user
 * @param password           login password
 * @param poolSize           maximum pooled connections
 * @param connectionTimeoutMs connection acquisition timeout
 * @param useSsl             whether to require TLS to the database
 */
public record DatabaseSettings(
        StorageType type,
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize,
        long connectionTimeoutMs,
        boolean useSsl) {

    public DatabaseSettings {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be >= 1: " + poolSize);
        }
    }

    public static DatabaseSettings defaults() {
        return new DatabaseSettings(
                StorageType.MEMORY, "127.0.0.1", 3306, "suld",
                "suld", "", 10, 10_000L, false);
    }
}
