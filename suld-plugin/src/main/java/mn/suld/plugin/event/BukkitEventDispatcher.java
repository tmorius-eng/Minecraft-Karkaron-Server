package mn.suld.plugin.event;

import mn.suld.api.event.EventDispatcher;
import mn.suld.api.event.SuldEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * {@link EventDispatcher} that republishes domain events on the Bukkit event
 * bus. Bukkit events must be fired on the main thread, so dispatches from other
 * threads are hopped onto the main thread via the scheduler.
 */
public final class BukkitEventDispatcher implements EventDispatcher {

    private final Plugin plugin;

    public BukkitEventDispatcher(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void dispatch(SuldEvent event) {
        SuldDomainBukkitEvent bukkitEvent = new SuldDomainBukkitEvent(event);
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(bukkitEvent);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(bukkitEvent));
        }
    }
}
