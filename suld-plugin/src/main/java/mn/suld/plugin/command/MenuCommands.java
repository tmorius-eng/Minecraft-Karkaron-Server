package mn.suld.plugin.command;

import mn.suld.plugin.SuldServices;
import mn.suld.plugin.gui.Menus;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * GUI commands ({@code /menu /tutorial /cosmetics /shop /buy /rankup /lvlup}) and {@code /credits}
 * (balance; admin/console: give|take — the store's purchase command, e.g. {@code credits give {uuid} 1000}).
 */
public final class MenuCommands {

    private final SuldServices services;
    private final Menus menus;

    public MenuCommands(SuldServices services, Menus menus) {
        this.services = services;
        this.menus = menus;
    }

    private TabExecutor gui(BiConsumer<Menus, Player> open) {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (s instanceof Player p) open.accept(menus, p);
                else s.sendMessage("Players only.");
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                return List.of();
            }
        };
    }

    public TabExecutor menu() { return gui(Menus::main); }
    public TabExecutor tutorial() { return gui(Menus::tutorial); }
    public TabExecutor cosmetics() { return gui(Menus::cosmetics); }
    public TabExecutor shop() { return gui(Menus::shop); }
    public TabExecutor buy() { return gui(Menus::buy); }
    public TabExecutor rankup() { return gui(Menus::rankup); }
    public TabExecutor lvlup() { return gui(Menus::lvlup); }
    public TabExecutor skills() { return gui(Menus::skills); }

    public TabExecutor credits() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (a.length == 0) {
                    if (s instanceof Player p) {
                        s.sendMessage(Messages.info("Сүлд Кредит: " + services.styles().of(p.getUniqueId()).credits() + " ✦ (/buy)"));
                    }
                    return true;
                }
                if (!s.hasPermission("suld.admin.credits")) {
                    s.sendMessage(Messages.error("Эрх алга."));
                    return true;
                }
                if (a.length < 3 || !(a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("take"))) {
                    s.sendMessage("/credits <give|take> <player|uuid> <amount>");
                    return true;
                }
                UUID id = resolve(a[1]);
                long amount;
                try {
                    amount = Long.parseLong(a[2]);
                } catch (NumberFormatException e) {
                    amount = -1;
                }
                if (id == null || amount <= 0 || amount > 10_000_000) {
                    s.sendMessage("unknown player or bad amount (1..10000000)");
                    return true;
                }
                long delta = a[0].equalsIgnoreCase("give") ? amount : -amount;
                Bukkit.getLogger().info("[SULD] credits " + a[0].toLowerCase(Locale.ROOT) + " " + id + " " + amount + " by " + s.getName());
                services.styles().giveCredits(id, delta, s::sendMessage);
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (!s.hasPermission("suld.admin.credits")) return List.of();
                return a.length == 1 ? List.of("give", "take") : a.length == 2 ? null : List.of();
            }
        };
    }

    private static UUID resolve(String who) {
        try {
            return UUID.fromString(who);
        } catch (IllegalArgumentException ignored) {
            // a name
        }
        Player online = Bukkit.getPlayerExact(who);
        if (online != null) return online.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(who);
        return off == null ? null : off.getUniqueId();
    }
}
