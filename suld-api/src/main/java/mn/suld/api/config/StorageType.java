package mn.suld.api.config;

import java.util.Locale;
import java.util.Optional;

/** Supported persistence backends. */
public enum StorageType {
    /** Volatile, no database — for local development and tests only. */
    MEMORY,
    /** Embedded H2 file database in the plugin folder: persistent, no database server to install (local/test servers). */
    H2,
    MYSQL,
    POSTGRESQL;

    public static Optional<StorageType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            String key = id.trim().toUpperCase(Locale.ROOT);
            if (key.equals("POSTGRES")) key = "POSTGRESQL";
            if (key.equals("MARIADB")) key = "MYSQL";
            return Optional.of(StorageType.valueOf(key));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
