package mn.suld.plugin.worldbuild;

import mn.suld.plugin.ui.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code /suldworld} (admin, {@code suld.admin.world}): the world border and the pre-generator.
 * {@code border [apply]}, {@code pregen status|start|pause|resume|cancel|restart|full confirm}, {@code report}.
 */
public final class SuldWorldCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("border", "pregen", "report", "halls", "sites", "site");
    private static final List<String> PREGEN = List.of("status", "start", "pause", "resume", "cancel", "restart", "full");

    private final WorldBorderService border;
    private final PregenService pregen;
    private final mn.suld.plugin.dungeon.DungeonHalls halls;

    private volatile mn.suld.plugin.region.HistoricSiteService sites;

    /** The historic sites (set once they start). */
    public void sites(mn.suld.plugin.region.HistoricSiteService sites) {
        this.sites = sites;
    }

    public SuldWorldCommand(WorldBorderService border, PregenService pregen, mn.suld.plugin.dungeon.DungeonHalls halls) {
        this.border = border;
        this.pregen = pregen;
        this.halls = halls;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("suld.admin.world")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return true;
        }
        Consumer<String> say = s -> sender.sendMessage(Messages.info(s));
        String sub = args.length == 0 ? "report" : args[0].toLowerCase(Locale.ROOT);
        String op = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "border" -> {
                if (op.equals("apply")) border.apply("admin");
                border.status().forEach(say);
            }
            case "pregen" -> {
                switch (op) {
                    case "status" -> pregen.status().forEach(say);
                    case "start", "resume" -> pregen.run(say);
                    case "pause" -> pregen.pause(say);
                    case "cancel" -> pregen.cancel(say);
                    case "restart" -> pregen.restart(say);
                    case "full" -> {
                        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                            sender.sendMessage(Messages.error("Хилийн доторх БҮХ нутгийг үүсгэнэ (цаг, дискний зай их шаардана). "
                                    + "Эхлээд /suldworld report-оор хэмжээг харна уу. Баталгаажуулах: /suldworld pregen full confirm"));
                        } else {
                            pregen.queueFull(say);
                        }
                    }
                    default -> say.accept("/suldworld pregen " + String.join("|", PREGEN));
                }
            }
            case "halls" -> {
                if (halls == null) say.accept("Танхим алга.");
                else halls.describe(say);
            }
            case "sites" -> {
                if (sites == null) say.accept("Түүхэн газар алга.");
                else sites.describe().forEach(say);
            }
            case "site" -> {
                org.bukkit.Location at = sites == null || args.length < 2 ? null : sites.location(args[1]);
                if (!(sender instanceof org.bukkit.entity.Player p) || at == null) say.accept("/suldworld site <id> (/suldworld sites)");
                else p.teleportAsync(at, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND);
            }
            case "report" -> {
                border.status().forEach(say);
                pregen.report(say);
            }
            default -> say.accept("/suldworld " + String.join("|", SUBS));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("suld.admin.world")) return List.of();
        if (args.length == 1) return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("pregen")) return PREGEN.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("border")) return List.of("apply");
        if (args.length == 3 && args[1].equalsIgnoreCase("full")) return List.of("confirm");
        return List.of();
    }
}
