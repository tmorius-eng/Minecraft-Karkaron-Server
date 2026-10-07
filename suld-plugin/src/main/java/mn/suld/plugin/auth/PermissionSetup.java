package mn.suld.plugin.auth;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies {@code permissions/luckperms.txt} through the console once, when LuckPerms is present: the groups and what
 * an ordinary player, helper, moderator and admin may do (homes, teleport requests, messages for everyone; kick,
 * mute, tp, CoreProtect for staff…). A marker file keeps it from running again, so later manual edits in LuckPerms
 * are never overwritten.
 */
public final class PermissionSetup {

    private final Plugin plugin;

    public PermissionSetup(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("permissions.auto-setup", true)) return;
        File marker = new File(plugin.getDataFolder(), ".permissions-applied");
        if (marker.exists()) return;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("Permissions: LuckPerms is not installed — skipping the group setup.");
            return;
        }
        // let LuckPerms finish enabling first
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(marker), 100L);
    }

    List<String> commands() throws IOException {
        List<String> out = new ArrayList<>();
        try (InputStream in = plugin.getResource("permissions/luckperms.txt")) {
            if (in == null) return out;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.strip();
                    if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
                }
            }
        }
        return out;
    }

    private void apply(File marker) {
        try {
            List<String> cmds = commands();
            for (String c : cmds) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            Files.createDirectories(marker.getParentFile().toPath());
            Files.writeString(marker.toPath(), "applied " + cmds.size() + " commands\n");
            plugin.getLogger().info("Permissions: applied " + cmds.size() + " LuckPerms commands (groups default/helper/mod/admin/developer/streamer/sponsor).");
        } catch (IOException ex) {
            plugin.getLogger().warning("Permissions: could not apply the LuckPerms setup: " + ex.getMessage());
        }
    }
}
