package mn.suld.api.config;

import java.util.Locale;
import java.util.Optional;

/** Supported persistence backends. */
public enum StorageType {
    /** Volatile, no database — for local development and tests only. */
    MEMORY,
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
