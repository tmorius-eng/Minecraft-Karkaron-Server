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
 * Applies {@code permissions/luckperms.txt} through the console when LuckPerms is present: the groups and what
 * an ordinary player, helper, moderator and admin may do (one home and messages for everyone; kick, mute, tp,
 * CoreProtect for staff…). The marker file records a hash of the applied file, so the file runs again only when it
 * changed in a release (every line is idempotent: {@code set}/{@code unset} a node, {@code creategroup} of an
 * existing group just fails). Nodes the file does not mention are never touched, so manual LuckPerms edits beyond the
 * shipped baseline survive; the shipped baseline itself is re-asserted (GAME-001 revokes {@code essentials.tpa} this way).
 */
public final class PermissionSetup {

    private final Plugin plugin;

    public PermissionSetup(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("permissions.auto-setup", true)) return;
        File marker = new File(plugin.getDataFolder(), ".permissions-applied");
        List<String> cmds;
        try {
            cmds = commands();
        } catch (IOException ex) {
            plugin.getLogger().warning("Permissions: could not read permissions/luckperms.txt: " + ex.getMessage());
            return;
        }
        String stamp = "applied " + cmds.size() + " commands, sha256 " + sha256(cmds) + "\n";
        if (marker.exists()) {
            try {
                if (Files.readString(marker.toPath()).equals(stamp)) return;
            } catch (IOException ignored) {
                // unreadable marker: apply again, the file is idempotent
            }
            plugin.getLogger().info("Permissions: permissions/luckperms.txt changed since it was applied; re-applying the baseline.");
        }
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().info("Permissions: LuckPerms is not installed — skipping the group setup.");
            return;
        }
        // let LuckPerms finish enabling first
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(marker, cmds, stamp), 100L);
    }

    /** Hex SHA-256 of the command lines, the marker's idea of "this version of the file". */
    static String sha256(List<String> cmds) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (String c : cmds) md.update((c + "\n").getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

    private void apply(File marker, List<String> cmds, String stamp) {
        try {
            for (String c : cmds) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            Files.createDirectories(marker.getParentFile().toPath());
            Files.writeString(marker.toPath(), stamp);
            plugin.getLogger().info("Permissions: applied " + cmds.size() + " LuckPerms commands (groups default/helper/mod/admin/developer/streamer/sponsor).");
        } catch (IOException ex) {
            plugin.getLogger().warning("Permissions: could not apply the LuckPerms setup: " + ex.getMessage());
        }
    }
}
