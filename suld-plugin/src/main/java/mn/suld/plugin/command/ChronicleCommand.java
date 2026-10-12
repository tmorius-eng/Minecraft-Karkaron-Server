package mn.suld.plugin.command;

import mn.suld.api.chronicle.ChronicleEntry;
import mn.suld.api.chronicle.ChronicleKind;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.gui.Menus;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** {@code /chronicle [mine|dungeon|relic|site|khuraldai]} opens the Хроник; admin {@code hide <id>}. */
public final class ChronicleCommand implements TabExecutor {

    private final SuldServices services;
    private final Menus menus;

    public ChronicleCommand(SuldServices services, Menus menus) {
        this.services = services;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("hide")) {
            if (!sender.hasPermission("suld.admin.campaign")) {
                sender.sendMessage(Messages.error("Эрх алга."));
                return true;
            }
            long id;
            try {
                id = Long.parseLong(args[1]);
            } catch (RuntimeException ex) {
                sender.sendMessage(Messages.error("/chronicle hide <id>"));
                return true;
            }
            services.chronicle().hide(id, sender.getName()).whenComplete((ok, err) -> Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("SULD"), () -> sender.sendMessage(err != null ? Messages.error("Алдаа: " + err.getMessage())
                            : ok ? Messages.success("Хроникийн " + id + "-р мөрийг нуулаа.") : Messages.error("Ийм мөр алга."))));
            return true;
        }
        if (!(sender instanceof Player p)) {
            for (ChronicleEntry e : services.chronicle().latest()) sender.sendMessage(Component.text(services.chronicle().headline(e)));
            return true;
        }
        ChronicleKind kind = switch (sub) {
            case "dungeon", "агуй" -> ChronicleKind.FIRST_DUNGEON_CLEAR;
            case "record", "амжилт" -> ChronicleKind.FASTEST_DUNGEON_CLEAR;
            case "relic", "сүлд" -> ChronicleKind.RELIC_CLAIMED;
            case "site", "газар" -> ChronicleKind.FIRST_SITE_DISCOVERY;
            case "khuraldai", "campaign", "аян" -> ChronicleKind.CAMPAIGN_RESULT;
            case "boss", "босс" -> ChronicleKind.FIRST_WORLD_BOSS_KILL;
            default -> null;
        };
        if (sub.equals("mine") || sub.equals("миний")) menus.chronicleMine(p);
        else menus.chronicle(p, kind);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        List<String> subs = new java.util.ArrayList<>(List.of("mine", "dungeon", "record", "relic", "site", "khuraldai", "boss"));
        if (sender.hasPermission("suld.admin.campaign")) subs.add("hide");
        return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
