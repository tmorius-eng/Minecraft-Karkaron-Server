package mn.suld.plugin.command;

import mn.suld.plugin.party.PartyService;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** {@code /party invite|accept|leave|kick|promote|disband|info}. */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("invite", "accept", "leave", "kick", "promote", "disband", "info");

    private final PartyService parties;

    public PartyCommand(PartyService parties) {
        this.parties = parties;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Messages.error("/party invite <тоглогч>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(Messages.error("Тоглогч онлайн биш байна."));
                } else {
                    parties.invite(player, target);
                }
            }
            case "accept" -> parties.accept(player);
            case "leave" -> parties.leave(player, true);
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Messages.error("/party kick <тоглогч>"));
                } else {
                    parties.kick(player, args[1]);
                }
            }
            case "promote" -> {
                if (args.length < 2) {
                    player.sendMessage(Messages.error("/party promote <тоглогч>"));
                } else {
                    parties.promote(player, args[1]);
                }
            }
            case "disband" -> parties.disband(player);
            case "info", "list" -> parties.info(player);
            default -> player.sendMessage(Messages.info("/party <invite|accept|leave|kick|promote|disband|info>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("invite", "kick", "promote").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
