package mn.suld.plugin.listener;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Loads/saves profiles around the connection lifecycle and records the
 * retention-funnel analytics for first login and session start/end.
 */
public final class PlayerLifecycleListener implements Listener {

    private final Plugin plugin;
    private final SuldServices services;

    public PlayerLifecycleListener(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        String name = player.getName();

        services.profiles().loadOrCreate(id, name).whenComplete((profile, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || profile == null) {
                        plugin.getLogger().warning("Failed to load profile for " + name + ": " + error);
                        player.sendMessage(Messages.error("Профайл ачааллахад алдаа гарлаа. Админд мэдэгдэнэ үү."));
                        return;
                    }
                    recordLoginAnalytics(profile.createdAt(), id);
                    services.resourcePacks().send(player);
                    player.sendMessage(Messages.accent("Тавтай морил, " + player.getName() + "! — SÜLD"));
                    if (!profile.hasSelectedClass()) {
                        player.sendMessage(Messages.info("Анхны алхам: ангиа сонгоно уу."));
                        services.classSelectionGui().open(player);
                    } else {
                        services.quests().startFirstQuestIfNeeded(profile);
                        player.sendMessage(Messages.info("Анги: "
                                + profile.playerClass().map(c -> c.displayName()).orElse("—")
                                + " · Түвшин " + profile.progression().level()));
                        services.hud().update(player, profile);
                    }
                }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Location loc = player.getLocation();

        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.LOGOUT_LOCATION, id, Map.of(
                "world", loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ())));
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.SESSION_END, id));

        services.profiles().saveAndUnload(id).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to save profile on quit for " + player.getName() + ": " + ex);
            return null;
        });
    }

    private void recordLoginAnalytics(Instant createdAt, UUID id) {
        boolean freshlyCreated = Duration.between(createdAt, Instant.now()).toSeconds() < 5;
        if (freshlyCreated) {
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.FIRST_LOGIN, id));
        }
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.SESSION_START, id));
    }
}
