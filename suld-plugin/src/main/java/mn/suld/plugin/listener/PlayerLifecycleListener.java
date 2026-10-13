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
        services.sessionStarted(id);
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.SESSION_START, id));
        services.resourcePacks().send(player);
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        if (services.items().refreshModels(contents) > 0) player.getInventory().setContents(contents);
        org.bukkit.inventory.ItemStack[] ender = player.getEnderChest().getContents();
        if (services.items().refreshModels(ender) > 0) player.getEnderChest().setContents(ender);
        services.relics().scheduleTease(player, profile);
        services.marks().backfill(player); // V17: the player file's clears / sites / ovoo days into the database, once
        services.marks().restore(player); // …and the database's clears / sites back into a file that lost them
        services.campaigns().onJoin(player); // the campaign's boss bar; a tier reward owed from last week
        player.sendMessage(Messages.accent("Тавтай морил, " + player.getName() + "! — SÜLD"));
        if (!profile.hasSelectedClass()) {
            player.sendMessage(Messages.info("Анхны алхам: ангиа сонгоно уу."));
            // not in the same tick as the pack prompt: the menu would replace it and the pack would be lost (ONB-001)
            services.resourcePacks().afterPack(player, () -> {
                if (!player.isOnline()) return;
                PlayerProfile now = services.profiles().cached(id).orElse(null);
                if (now != null && !now.hasSelectedClass()) services.classSelectionGui().open(player);
            });
        } else {
            services.quests().ensure(player, profile);
            player.sendMessage(Messages.info("Анги: "
                    + profile.playerClass().map(c -> c.displayName()).orElse("—")
                    + " · Түвшин " + profile.progression().level()));
            services.hud().update(player, profile);
        }
    }

    /** Session totals from domain events: EXP, quests completed, discoveries. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onDomain(mn.suld.plugin.event.SuldDomainBukkitEvent e) {
        if (e.payload() instanceof mn.suld.api.event.LevelUpEvent lv) {
            // a level is the progress a player notices losing: commit it now (async), not at the next autosave.
            // A kill -9 nine seconds after a level-up used to lose it (audit 2026-10-11 E-09).
            services.profiles().cached(lv.player()).ifPresent(services.profiles()::save);
            return;
        }
        if (!(e.payload() instanceof mn.suld.api.event.ExpGainedEvent g)) return;
        mn.suld.api.analytics.SessionTotals t = services.session(g.player());
        t.exp += g.amount();
        if (g.source() == mn.suld.api.progression.ExpSource.QUEST) t.questsCompleted++;
        if (g.source() == mn.suld.api.progression.ExpSource.DISCOVERY) t.discoveries++;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Location loc = player.getLocation();

        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.LOGOUT_LOCATION, id, Map.of(
                "world", loc.getWorld() == null ? "?" : loc.getWorld().getName(),
                "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ())));
        // one event with the session's aggregate totals (also a suld_sessions row with the database sink)
        mn.suld.api.analytics.SessionTotals totals = services.sessionEnded(id);
        services.analytics().record(totals == null ? AnalyticsEvent.of(AnalyticsEventType.SESSION_END, id)
                : AnalyticsEvent.of(AnalyticsEventType.SESSION_END, id, totals.attributes(System.currentTimeMillis())));
        services.campaigns().onQuit(player);
        // Saving/unloading is session-aware and owned by AuthenticationService (runs at MONITOR).
    }
}
