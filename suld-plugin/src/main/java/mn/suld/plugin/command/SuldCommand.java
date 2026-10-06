package mn.suld.plugin.command;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.progression.ProgressionEngine;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /suld info} — server/plugin status. {@code /suld profile} — the
 * caller's character summary (class, level, XP bar).
 */
public final class SuldCommand implements CommandExecutor {

    private final Plugin plugin;
    private final SuldServices services;

    public SuldCommand(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "profile" -> profile(sender);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Messages.accent("SULD — Монгол Hardcore MMORPG"));
        sender.sendMessage(Messages.info("/suld info — серверийн мэдээлэл"));
        sender.sendMessage(Messages.info("/suld profile — таны дүрийн мэдээлэл"));
    }

    private void info(CommandSender sender) {
        sender.sendMessage(Messages.accent("SULD v" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Messages.info("Онлайн: " + Bukkit.getOnlinePlayers().size()
                + "/" + Bukkit.getMaxPlayers()));
        sender.sendMessage(Messages.info("Хадгалалт: " + services.config().database().type()));
        sender.sendMessage(Messages.info("Дээд түвшин: " + services.config().progression().maxLevel()));
    }

    private void profile(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Энэ командыг зөвхөн тоглогч ашиглана."));
            return;
        }
        PlayerProfile profile = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (profile == null) {
            sender.sendMessage(Messages.error("Профайл ачааллагдаагүй байна. Түр хүлээнэ үү."));
            return;
        }
        ProgressionEngine engine = services.progression().engine();
        Progression progression = profile.progression();

        sender.sendMessage(Messages.accent("Таны дүр"));
        sender.sendMessage(Messages.info("Анги: "
                + profile.playerClass().map(c -> c.displayName()).orElse("сонгоогүй")));
        sender.sendMessage(Messages.info("Түвшин: " + progression.level()
                + "  (дараагийн түвшин хүртэл " + engine.expToNextLevel(progression) + " EXP)"));
        sender.sendMessage(Messages.PREFIX.append(bar(engine.progressFraction(progression))));
    }

    /** A 20-segment XP progress bar as an Adventure component. */
    private Component bar(double fraction) {
        int total = 20;
        int filled = (int) Math.round(fraction * total);
        Component filledPart = Component.text("█".repeat(Math.max(0, filled)), Messages.BRAND);
        Component emptyPart = Component.text("█".repeat(Math.max(0, total - filled)), NamedTextColor.DARK_GRAY);
        return filledPart.append(emptyPart)
                .append(Component.text(" " + Math.round(fraction * 100) + "%", NamedTextColor.GRAY));
    }
}
