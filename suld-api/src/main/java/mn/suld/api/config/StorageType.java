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
            return Optional.of(StorageType.valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
