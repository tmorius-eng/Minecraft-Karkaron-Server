package mn.suld.plugin.worldbuild;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;

/**
 * Pre-generates the overworld around Kharkhorum with Chunky (pop4959's trusted pre-generator), so players
 * never wait on (or lag the server with) fresh chunk generation while exploring.
 * <p>
 * Once the city is built (the spawn sits on the plaza), the first start queues
 * {@code chunky world/center/radius/start}; later starts send {@code chunky continue}, so an interrupted
 * run picks up where it stopped. Nothing happens without Chunky installed. Config: {@code world.pregenerate}.
 */
public final class PregenService {

    private final Plugin plugin;
    private final WorldBuildService city;
    private final File stateFile;
    private BukkitTask waiting;

    public PregenService(Plugin plugin, WorldBuildService city) {
        this.plugin = plugin;
        this.city = city;
        this.stateFile = new File(plugin.getDataFolder(), "pregen.yml");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("world.pregenerate.enabled", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("Chunky")) {
            plugin.getLogger().info("Pre-generation: Chunky is not installed; skipping (install the core plugin profile).");
            return;
        }
        // Wait for the city: the pre-generation is centred on the plaza, and must not race the build.
        waiting = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!city.isBuilt()) return;
            waiting.cancel();
            run();
        }, 20L * 30, 20L * 30);
    }

    public void stop() {
        if (waiting != null) waiting.cancel();
    }

    private void run() {
        World world = Bukkit.getWorlds().get(0);
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        int radius = Math.max(256, plugin.getConfig().getInt("world.pregenerate.radius", 2000));
        if (st.getBoolean("queued", false) && st.getInt("radius") == radius && world.getName().equals(st.getString("world"))) {
            plugin.getLogger().info("Pre-generation: resuming the Chunky task (chunky continue).");
            console("chunky continue");
            return;
        }
        Location c = world.getSpawnLocation();
        plugin.getLogger().info("Pre-generation: Chunky, world " + world.getName() + ", centre " + c.getBlockX() + " "
                + c.getBlockZ() + ", radius " + radius + " blocks (" + (radius / 8) * (radius / 8) + " chunks). "
                + "Progress: /chunky progress");
        console("chunky world " + world.getName());
        console("chunky center " + c.getBlockX() + " " + c.getBlockZ());
        console("chunky shape square");
        console("chunky radius " + radius);
        console("chunky start");
        st.set("queued", true);
        st.set("world", world.getName());
        st.set("radius", radius);
        st.set("center", c.getBlockX() + " " + c.getBlockZ());
        st.set("queuedAt", System.currentTimeMillis());
        try {
            st.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Pre-generation: cannot save pregen.yml: " + e.getMessage());
        }
    }

    private static void console(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
