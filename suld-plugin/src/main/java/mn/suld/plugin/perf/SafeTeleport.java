package mn.suld.plugin.perf;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Teleports that never load the destination chunk on the main thread (docs/perf/WORLD_50.md). Paper's
 * {@code teleportAsync} prepares the chunk on its chunk workers and moves the player on the main thread once it is
 * ready; the follow-up ({@code after}, given whether the move happened) always runs on the main thread. Nothing here
 * waits on the future.
 */
public final class SafeTeleport {

    private SafeTeleport() {
    }

    public static void to(Plugin plugin, Player p, Location dest, PlayerTeleportEvent.TeleportCause cause, Consumer<Boolean> after) {
        p.teleportAsync(dest, cause).whenComplete((ok, err) -> {
            boolean moved = err == null && Boolean.TRUE.equals(ok);
            if (err != null) plugin.getLogger().warning("teleport of " + p.getName() + " failed: " + err.getMessage());
            if (after == null) return;
            if (Bukkit.isPrimaryThread()) {
                after.accept(moved);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> after.accept(moved));
            }
        });
    }

    public static void to(Plugin plugin, Player p, Location dest) {
        to(plugin, p, dest, PlayerTeleportEvent.TeleportCause.PLUGIN, null);
    }
}
