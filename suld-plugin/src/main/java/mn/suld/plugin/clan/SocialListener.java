package mn.suld.plugin.clan;

import mn.suld.plugin.worldevent.WorldEventService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Join/death hooks for clans and world events. */
public final class SocialListener implements Listener {

    private final ClanService clans;
    private final WorldEventService events;

    public SocialListener(ClanService clans, WorldEventService events) {
        this.clans = clans;
        this.events = events;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        clans.onJoin(event.getPlayer());
        events.onJoin(event.getPlayer());
    }

    /** MONITOR: SÜLD combat has already granted kill EXP/loot. */
    /** Set by SuldPlugin: the week's campaign counts SÜLD kills too. */
    public volatile java.util.function.BiConsumer<org.bukkit.entity.Player, String> campaignKills = (p, id) -> { };
    public volatile java.util.function.Function<org.bukkit.entity.LivingEntity, java.util.Optional<String>> mobIds = e -> java.util.Optional.empty();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!(event instanceof PlayerDeathEvent)) {
            events.onMobDeath(event.getEntity());
            org.bukkit.entity.Player killer = event.getEntity().getKiller();
            if (killer != null) mobIds.apply(event.getEntity()).ifPresent(id -> campaignKills.accept(killer, id));
        }
    }
}
