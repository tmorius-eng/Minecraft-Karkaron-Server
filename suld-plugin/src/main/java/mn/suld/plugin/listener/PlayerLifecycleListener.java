package mn.suld.plugin.listener;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
                    greet(player, profile.hasSelectedClass() ? profile.playerClass().orElse(null) : null,
                            profile.progression().level());
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

    private void greet(Player player, PlayerClass selectedClass, int level) {
        player.sendMessage(Messages.accent("Тавтай морил, " + player.getName() + "!"));
        if (selectedClass == null) {
            player.sendMessage(Messages.info("Анги сонгоогүй байна. Доорх ангиудаас сонгоно уу:"));
            for (PlayerClass clazz : PlayerClass.values()) {
                player.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(clazz.displayName(), Messages.BRAND))
                        .append(Component.text(" — " + clazz.role(), NamedTextColor.GRAY)));
            }
            player.sendMessage(Messages.info("(Ангийн сонголтын цонх удахгүй нэмэгдэнэ.)"));
        } else {
            player.sendMessage(Messages.info("Анги: " + selectedClass.displayName() + " · Түвшин " + level));
        }
    }
}
