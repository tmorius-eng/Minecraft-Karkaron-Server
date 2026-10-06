package mn.suld.plugin.command;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * {@code /revive <player>} — administrative recovery from the hardcore
 * death/soul state. Permission: {@code suld.admin.revive}.
 *
 * <p>Deliberately an admin/support tool, not a monetised mechanic. It restores
 * the player to a clean living state and writes an audit trail. When the full
 * death system lands it will additionally clear any active soul state.
 */
public final class ReviveCommand implements CommandExecutor {

    private final Plugin plugin;
    private final SuldServices services;
    private final mn.suld.plugin.death.DeathService deaths;

    public ReviveCommand(Plugin plugin, SuldServices services, mn.suld.plugin.death.DeathService deaths) {
        this.plugin = plugin;
        this.services = services;
        this.deaths = deaths;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("suld.admin.revive")) {
            sender.sendMessage(Messages.error("Танд энэ үйлдэл хийх эрх алга."));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Messages.info("Хэрэглээ: /revive <тоглогч>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Messages.error("Тоглогч олдсонгүй эсвэл офлайн байна: " + args[0]));
            return true;
        }

        revive(target);

        sender.sendMessage(Messages.success(target.getName() + "-г амилууллаа."));
        target.sendMessage(Messages.accent("Таныг амилууллаа. Тэнгэр таныг ивээг."));

        services.analytics().record(AnalyticsEvent.of("admin_revive", target.getUniqueId(),
                Map.of("by", sender.getName())));
        // Audit trail (the suld_audit_log table persists this in a later phase).
        plugin.getLogger().info("[audit] revive target=" + target.getName() + " by=" + sender.getName());
        return true;
    }

    @SuppressWarnings("deprecation") // getMaxHealth() is version-stable; attribute API churns across 1.21.x
    private void revive(Player player) {
        deaths.revive(player, true);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    }
}
