package mn.suld.plugin.command;

import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** {@code /urih [нэр]}: name the friend who brought you, or see where you stand (docs/REFERRAL.md). */
public final class ReferralCommand implements TabExecutor {

    private final SuldServices services;

    public ReferralCommand(SuldServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        if (args.length == 0) {
            services.referrals.status(p);
            return true;
        }
        if (!args[0].matches("[A-Za-z0-9_]{1,16}")) {
            p.sendMessage(Messages.error("Тоглогчийн нэр 1–16 тэмдэгт: A–Z a–z 0–9 _."));
            return true;
        }
        services.referrals.name(p, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> !n.equals(sender.getName()))
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
