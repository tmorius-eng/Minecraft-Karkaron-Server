package mn.suld.api.config;

import java.util.List;
import java.util.Optional;

/**
 * A minimal, read-only view over hierarchical configuration.
 *
 * <p>The domain layer defines this interface so configuration parsing is
 * testable without Bukkit: the Paper layer backs it with a Bukkit
 * {@code ConfigurationSection}, while tests use {@link MapConfigView}. Paths are
 * dot-separated (e.g. {@code "database.host"}).
 */
public interface ConfigView {

    boolean contains(String path);

    String getString(String path, String def);

    int getInt(String path, int def);

    long getLong(String path, long def);

    double getDouble(String path, double def);

    boolean getBoolean(String path, boolean def);

    List<String> getStringList(String path);

    /** A nested section, if present. */
    Optional<ConfigView> section(String path);
}
