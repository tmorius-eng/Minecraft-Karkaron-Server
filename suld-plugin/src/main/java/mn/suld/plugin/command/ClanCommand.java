package mn.suld.plugin.command;

import mn.suld.plugin.clan.ClanService;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /clan create|invite|accept|leave|kick|promote|demote|transfer|disband|info|top|chat}
 * and {@code /cc <message>} (clan chat).
 */
public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("create", "invite", "accept", "leave", "kick", "promote",
            "demote", "transfer", "disband", "info", "top", "chat");

    private final ClanService clans;

    public ClanCommand(ClanService clans) {
        this.clans = clans;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        if (command.getName().equalsIgnoreCase("cc")) {
            if (args.length == 0) {
                player.sendMessage(Messages.error("/cc <мессеж>"));
            } else {
                clans.chat(player, String.join(" ", args));
            }
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Messages.error("/clan create <нэр...> <таг>   жишээ: /clan create Хүрэн Чоно ХЧ"));
                } else {
                    String tag = args[args.length - 1];
                    String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                    clans.create(player, name, tag);
                }
            }
            case "invite" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    player.sendMessage(Messages.error("/clan invite <онлайн тоглогч>"));
                } else {
                    clans.invite(player, target);
                }
            }
            case "accept" -> clans.accept(player);
            case "leave" -> clans.leave(player);
            case "kick", "promote", "demote", "transfer" -> {
                if (args.length < 2) {
                    player.sendMessage(Messages.error("/clan " + sub + " <тоглогч>"));
                } else if (sub.equals("kick")) {
                    clans.kick(player, args[1]);
                } else {
                    clans.rank(player, args[1], sub);
                }
            }
            case "disband" -> clans.disband(player);
            case "info" -> clans.info(player, args.length > 1 ? args[1] : null);
            case "top", "list" -> clans.top(player);
            case "chat" -> {
                if (args.length < 2) {
                    player.sendMessage(Messages.error("/clan chat <мессеж>  (эсвэл /cc)"));
                } else {
                    clans.chat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
            }
            default -> player.sendMessage(Messages.info("/clan <" + String.join("|", SUBS) + ">"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("cc")) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote", "transfer")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
