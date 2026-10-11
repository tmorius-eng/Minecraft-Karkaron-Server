package mn.suld.plugin.command;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.DungeonRun;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code /dungeon} opens the dungeon window ({@link mn.suld.plugin.gui.DungeonMenu}); {@code enter [id]|status|leave|abort}. */
public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("list", "enter", "status", "leave", "abort");

    private final SuldServices services;
    private final mn.suld.plugin.gui.DungeonMenu menu;

    public DungeonCommand(SuldServices services, mn.suld.plugin.gui.DungeonMenu menu) {
        this.services = services;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("grant")) { // /dungeon grant <player> <dungeon|all>: support
            if (!sender.hasPermission("suld.admin.world")) {
                sender.sendMessage(Messages.error("Эрх алга."));
                return true;
            }
            Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Messages.error("Тоглогч онлайн биш."));
                return true;
            }
            int n = 0;
            for (DungeonDefinition d : mn.suld.plugin.content.DungeonContent.ALL) {
                if (args[2].equalsIgnoreCase("all") || shortId(d).equalsIgnoreCase(args[2]) || d.id().equalsIgnoreCase(args[2])) {
                    services.dungeons().grantClear(target, d.id());
                    n++;
                }
            }
            sender.sendMessage(n == 0 ? Messages.error("Ийм агуй алга.") : Messages.success(target.getName() + ": " + n + " агуй давсанд тооцлоо."));
            services.audit().record(mn.suld.api.audit.AuditEvent.of(sender.getName(), "dungeon.grant", target.getName(), args[2]));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list", "menu" -> menu.open(player);
            case "enter" -> enter(player, args.length > 1 ? args[1] : "khasar_den");
            case "status" -> status(player);
            case "leave" -> services.parties().leave(player, true);
            case "abort" -> {
                boolean admin = player.hasPermission("suld.admin");
                if (!services.dungeons().abort(player.getUniqueId(), admin)) {
                    player.sendMessage(Messages.error("Зогсоох агуй алга (эсвэл та ахлагч биш)."));
                }
            }
            default -> menu.open(player);
        }
        return true;
    }

    private void enter(Player player, String rawId) {
        String id = rawId.startsWith("dungeon.") ? rawId : "dungeon." + rawId;
        DungeonDefinition def = SuldContent.dungeonFor(id);
        if (def == null) {
            player.sendMessage(Messages.error("Ийм агуй олдсонгүй — /dungeon цонхноос сонго."));
            return;
        }
        Component error = services.dungeons().start(player, def);
        if (error != null) {
            player.sendMessage(error);
        }
    }

    private static String shortId(DungeonDefinition d) {
        return d.id().startsWith("dungeon.") ? d.id().substring("dungeon.".length()) : d.id();
    }

    private void status(Player player) {
        Optional<DungeonRun> run = services.dungeons().runFor(player.getUniqueId());
        if (run.isEmpty()) {
            player.sendMessage(Messages.info("Та агуйд байхгүй байна."));
            return;
        }
        DungeonRun r = run.get();
        player.sendMessage(Messages.accent("Агуй: " + r.dungeonId()));
        player.sendMessage(Messages.info("Төлөв: " + services.dungeons().statusLine(player.getUniqueId()).orElse("—")
                + " · " + r.elapsedSeconds() + "с"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("enter")) {
            return mn.suld.plugin.content.DungeonContent.ALL.stream().map(DungeonCommand::shortId)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
