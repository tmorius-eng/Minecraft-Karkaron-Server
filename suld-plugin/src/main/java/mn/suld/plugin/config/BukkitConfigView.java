package mn.suld.plugin.config;

import mn.suld.api.config.ConfigView;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Optional;

/**
 * {@link ConfigView} backed by a Bukkit {@link ConfigurationSection}. This is
 * the only adapter between SULD's dependency-free config model and Bukkit's
 * YAML configuration, so the domain layer never imports Bukkit.
 */
public final class BukkitConfigView implements ConfigView {

    private final ConfigurationSection section;

    public BukkitConfigView(ConfigurationSection section) {
        this.section = section;
    }

    @Override
    public boolean contains(String path) {
        return section.contains(path);
    }

    @Override
    public String getString(String path, String def) {
        return section.getString(path, def);
    }

    @Override
    public int getInt(String path, int def) {
        return section.getInt(path, def);
    }

    @Override
    public long getLong(String path, long def) {
        return section.getLong(path, def);
    }

    @Override
    public double getDouble(String path, double def) {
        return section.getDouble(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return section.getBoolean(path, def);
    }

    @Override
    public List<String> getStringList(String path) {
        return section.getStringList(path);
    }

    @Override
    public Optional<ConfigView> section(String path) {
        ConfigurationSection child = section.getConfigurationSection(path);
        return Optional.ofNullable(child).map(BukkitConfigView::new);
    }
}
