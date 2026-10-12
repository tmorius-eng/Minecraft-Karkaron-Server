package mn.suld.plugin.command;

import mn.suld.api.item.Recipe;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.gui.CraftOrderMenu;
import mn.suld.plugin.ui.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** {@code /order} — the Дархан's order board; {@code post <recipe> <fee>}, {@code withdraw <id>} (docs/CRAFTING.md). */
public final class OrderCommand implements TabExecutor {

    private final Plugin plugin;
    private final SuldServices services;

    public OrderCommand(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Messages.error("Зөвхөн тоглогч."));
            return true;
        }
        if (services.craftOrders == null || services.itemService() == null) {
            p.sendMessage(Messages.error("Дарханы самбар ачаалагдаагүй байна."));
            return true;
        }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "post", "захиалах" -> {
                if (a.length < 3) {
                    p.sendMessage(Messages.error("Хэрэглээ: /order post <жор> <хөлс>  (жор: /item recipes)"));
                    return true;
                }
                String id = a[1].startsWith("recipe.") ? a[1] : "recipe." + a[1];
                Recipe r = services.itemService().catalog().recipe(id).orElse(null);
                if (r == null) {
                    p.sendMessage(Messages.error("Жор олдсонгүй: " + a[1]));
                    return true;
                }
                long fee;
                try {
                    fee = Long.parseLong(a[2]);
                } catch (NumberFormatException e) {
                    p.sendMessage(Messages.error("Хөлс тоо байх ёстой."));
                    return true;
                }
                services.craftOrders.post(p, r, fee);
            }
            case "withdraw", "буцаах" -> {
                if (a.length < 2 || !services.craftOrders.withdraw(p, a[1])) p.sendMessage(Messages.error("Таны ийм захиалга алга. (/order)"));
            }
            default -> new CraftOrderMenu(plugin, services).open(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return java.util.stream.Stream.of("post", "withdraw").filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 2 && a[0].equalsIgnoreCase("post") && services.itemService() != null) {
            return services.itemService().catalog().recipes().stream().map(r -> r.id().substring("recipe.".length()))
                    .filter(x -> x.startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("withdraw") && s instanceof Player p && services.craftOrders != null) {
            return services.craftOrders.open().stream().filter(o -> o.poster().equals(p.getUniqueId())).map(o -> o.id()).toList();
        }
        return List.of();
    }
}
