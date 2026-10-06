package mn.suld.plugin.listener;

import mn.suld.api.profile.PlayerProfile;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

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

    /**
     * Onboarding only. Identity and profile loading happen earlier, in
     * {@link mn.suld.plugin.auth.AuthenticationService} (async pre-login), so by now the
     * profile is already cached — the player enters with their state restored.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        PlayerProfile profile = services.profiles().cached(id).orElse(null);
        if (profile == null) {
            return; // the auth pipeline refuses (kicks) joins without a profile
        }
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.SESSION_START, id));
        services.resourcePacks().send(player);
        services.relics().scheduleTease(player, profile);
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
        // Saving/unloading is session-aware and owned by AuthenticationService (runs at MONITOR).
    }
}
