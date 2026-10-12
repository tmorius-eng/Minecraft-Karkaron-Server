package mn.suld.plugin.persistence;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Small YAML state files under {@code plugins/SULD/} (dungeon fatigue, farming fatigue, the trade mailbox), written
 * whole and atomically: the text goes to a sibling {@code .tmp} and is moved over the file, so a crash mid-write
 * leaves the previous file, never a torn one. Off the main thread while the plugin runs, inline while it disables.
 */
public final class AtomicYaml {

    private AtomicYaml() {
    }

    public static void write(Plugin plugin, Path file, YamlConfiguration yaml) {
        String text = yaml.saveToString(); // serialised on the caller's thread: the state is consistent at this moment
        Runnable write = () -> {
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, text);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().warning(file.getFileName() + ": " + e.getMessage());
            }
        };
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    /** Like {@link #write} but on the caller's thread, reporting failure: for small files whose order matters. */
    public static void writeNow(Path file, YamlConfiguration yaml) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, yaml.saveToString());
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** The file's contents, or an empty configuration when it does not exist. */
    public static YamlConfiguration read(Path file) {
        return Files.exists(file) ? YamlConfiguration.loadConfiguration(file.toFile()) : new YamlConfiguration();
    }
}
