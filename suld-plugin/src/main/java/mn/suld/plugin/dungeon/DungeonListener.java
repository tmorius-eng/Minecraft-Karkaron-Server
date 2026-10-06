package mn.suld.plugin.dungeon;

import mn.suld.plugin.party.PartyService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Bridges Bukkit death/quit events into the dungeon and party services. */
public final class DungeonListener implements Listener {

    private final DungeonService dungeons;
    private final PartyService parties;

    public DungeonListener(DungeonService dungeons, PartyService parties) {
        this.dungeons = dungeons;
        this.parties = parties;
    }

    /** MONITOR so SÜLD's combat listener has already granted kill EXP and loot. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        if (event instanceof PlayerDeathEvent) {
            return;
        }
        LivingEntity entity = event.getEntity();
        dungeons.onEntityDeath(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        dungeons.onParticipantDown(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Leaving the server leaves the party (and the run, via the removal hook).
        parties.leave(event.getPlayer(), false);
    }
}
