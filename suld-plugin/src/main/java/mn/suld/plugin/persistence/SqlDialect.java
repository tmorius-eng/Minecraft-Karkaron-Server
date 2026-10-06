package mn.suld.plugin.persistence;

import mn.suld.api.config.StorageType;

/**
 * SQL dialect differences SULD cares about: JDBC URL/driver, migration folder,
 * UUID binding, and upsert syntax. Keeping these in one enum means the rest of
 * the persistence code is dialect-agnostic.
 */
public enum SqlDialect {

    MYSQL("com.mysql.cj.jdbc.Driver", "mysql"),
    POSTGRESQL("org.postgresql.Driver", "postgresql");

    private final String driverClass;
    private final String migrationFolder;

    SqlDialect(String driverClass, String migrationFolder) {
        this.driverClass = driverClass;
        this.migrationFolder = migrationFolder;
    }

    public String driverClass() {
        return driverClass;
    }

    public String migrationFolder() {
        return migrationFolder;
    }

    public static SqlDialect forStorage(StorageType type) {
        return switch (type) {
            case MYSQL -> MYSQL;
            case POSTGRESQL -> POSTGRESQL;
            case MEMORY -> throw new IllegalArgumentException("MEMORY storage has no SQL dialect");
        };
    }

    /** Upsert statement for the profiles table, keyed on player_uuid. */
    public String profileUpsert() {
        return switch (this) {
            case MYSQL -> """
                    INSERT INTO suld_profiles
                        (player_uuid, name, class_id, level, exp_into_level, created_at, last_seen_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        name = VALUES(name),
                        class_id = VALUES(class_id),
                        level = VALUES(level),
                        exp_into_level = VALUES(exp_into_level),
                        last_seen_at = VALUES(last_seen_at),
                        version = VALUES(version)
                    """;
            case POSTGRESQL -> """
                    INSERT INTO suld_profiles
                        (player_uuid, name, class_id, level, exp_into_level, created_at, last_seen_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (player_uuid) DO UPDATE SET
                        name = EXCLUDED.name,
                        class_id = EXCLUDED.class_id,
                        level = EXCLUDED.level,
                        exp_into_level = EXCLUDED.exp_into_level,
                        last_seen_at = EXCLUDED.last_seen_at,
                        version = EXCLUDED.version
                    """;
        };
    }
}
