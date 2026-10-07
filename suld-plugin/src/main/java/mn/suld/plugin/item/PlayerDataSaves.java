package mn.suld.plugin.item;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes a player's data file soon after an item-for-coins change (sell, trade), so a crash cannot leave the coins in
 * SQL and the item still on disk. Many changes in a second (selling item by item) share one write on the next tick
 * but one, instead of a synchronous disk write each.
 */
public final class PlayerDataSaves {

    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private PlayerDataSaves() {
    }

    public static void soon(Plugin plugin, Player p) {
        if (!PENDING.add(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PENDING.remove(p.getUniqueId());
            if (p.isOnline()) p.saveData();
        }, 10L);
    }
}
