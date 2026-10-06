package mn.suld.plugin.quest;

import mn.suld.api.event.LevelUpEvent;
import mn.suld.api.quest.QuestType;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.event.SuldDomainBukkitEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.Plugin;

/** Feeds level-ups and inventory changes into the storyline (kills, regions and dungeons report directly). */
public final class QuestListener implements Listener {

    private final Plugin plugin;
    private final SuldServices services;

    public QuestListener(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDomain(SuldDomainBukkitEvent e) {
        if (!(e.payload() instanceof LevelUpEvent lu)) return;
        Player p = Bukkit.getPlayer(lu.player());
        if (p == null) return;
        services.profiles().cached(p.getUniqueId()).ifPresent(pr -> services.quests().onLevel(p, pr, lu.toLevel()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) recount(p);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) recount(p);
    }

    private void recount(Player p) {
        services.profiles().cached(p.getUniqueId()).ifPresent(pr -> {
            if (!services.quests().wants(pr, QuestType.COLLECT_ITEM)) return;
            // The picked-up stack lands in the inventory after the event.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) services.quests().onInventory(p, pr);
            });
        });
    }
}
