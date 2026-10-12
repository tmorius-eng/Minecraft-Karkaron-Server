package mn.suld.plugin.gui;

import mn.suld.api.craft.CraftOrder;
import mn.suld.api.craft.CraftOrderRules;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.Recipe;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.item.CraftOrderService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Дархан's order board (docs/CRAFTING.md, {@code /order}): every open order, oldest first. Someone else's order
 * shows what it needs from you and fills on a confirmed click; your own withdraws on a confirmed click.
 */
public final class CraftOrderMenu {

    private final Plugin plugin;
    private final SuldServices services;

    public CraftOrderMenu(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    private CraftOrderService orders() {
        return services.craftOrders;
    }

    public void open(Player p) {
        var catalog = services.itemService().catalog();
        Menu m = new Menu(6, "Дархан · Захиалгын самбар", null);
        int slot = 0;
        for (CraftOrder o : orders().open()) {
            if (slot >= 45) break;
            Recipe r = orders().recipe(o);
            if (r == null) continue;
            ItemDefinition def = catalog.require(r.resultId());
            boolean mine = o.poster().equals(p.getUniqueId());
            List<Component> lore = new ArrayList<>();
            lore.add(Menu.kv("Захиалагч:", o.posterName(), NamedTextColor.WHITE));
            lore.add(Menu.kv("Хөлс:", o.fee() + " зоос", NamedTextColor.GOLD));
            lore.add(Menu.kv("Зэрэг:", String.valueOf(r.itemLevel(o.posterLevel())), NamedTextColor.AQUA));
            lore.add(Menu.kv("Чанар:", r.minRarity().displayName() + (r.maxRarity() != r.minRarity() ? " – " + r.maxRarity().displayName() : ""), NamedTextColor.LIGHT_PURPLE));
            long left = Math.max(0, (o.createdAt() + CraftOrderRules.EXPIRY_MILLIS - System.currentTimeMillis()) / 3_600_000);
            lore.add(Menu.kv("Хугацаа:", left >= 24 ? left / 24 + " өдөр" : left + " цаг", NamedTextColor.GRAY));
            Component no = null;
            if (mine) {
                lore.add(Menu.line("Таны захиалга — дарж буцаана (хөлс буцна)."));
            } else {
                for (Map.Entry<String, Integer> e : r.materials().entrySet()) {
                    int have = services.crafting().count(p, e.getKey());
                    String name = catalog.item(e.getKey()).map(ItemDefinition::displayName).orElse(e.getKey());
                    lore.add(Component.text((have >= e.getValue() ? "✔ " : "✘ ") + name + " " + Math.min(have, 999) + "/" + e.getValue(),
                            have >= e.getValue() ? NamedTextColor.GREEN : NamedTextColor.RED));
                }
                lore.add(Menu.kv("Жорын зоос:", String.valueOf(r.coins()), NamedTextColor.GOLD));
                no = services.crafting().refusalFor(p, r, false);
                lore.add(no == null ? Menu.line("Дарж биелүүлнэ: эд зүйл захиалагчид, хөлс танд.") : no);
            }
            Material icon = Material.matchMaterial(def.material());
            boolean clickable = mine || no == null;
            m.set(slot++, Menu.item(icon == null || !icon.isItem() ? Material.SMITHING_TABLE : icon,
                    Menu.title(def.displayName(), clickable ? NamedTextColor.GOLD : NamedTextColor.GRAY), lore),
                    !clickable ? null : (pl, c) -> confirm(pl, o, def.displayName(), mine));
        }
        if (slot == 0) {
            m.set(22, Menu.item(Material.PAPER, Menu.title("Захиалга алга", NamedTextColor.GRAY), List.of(
                    Menu.line("Жорын номоос сонгоод: /order post <жор> <хөлс>"))), null);
        }
        m.set(49, Menu.item(Material.BOOK, Menu.title("Захиалах", NamedTextColor.GOLD), List.of(
                Menu.line("/order post <жор> <хөлс> — хөлс таниас хадгалагдана."),
                Menu.line("Хэн нэгэн урлавал эд зүйл танд, хөлс түүнд."),
                Menu.line(CraftOrderRules.EXPIRY_MILLIS / 86_400_000 + " хоногт биелэхгүй бол хөлс буцна."))),
                (pl, c) -> new CraftMenu(plugin, services).open(pl));
        m.open(p);
    }

    private void confirm(Player p, CraftOrder o, String itemName, boolean mine) {
        Menu m = new Menu(3, "Баталгаажуулах", null);
        String what = mine ? "Захиалгыг буцаах: " + itemName : "Биелүүлэх: " + itemName + " → " + o.posterName();
        m.set(11, Menu.item(Material.LIME_CONCRETE, Menu.title("✔ " + what, NamedTextColor.GREEN),
                List.of(Menu.line(mine ? o.fee() + " зоос буцна" : "+" + o.fee() + " зоос"))), (pl, c) -> {
            pl.closeInventory();
            if (mine) orders().withdraw(pl, o.id());
            else orders().fill(pl, o.id());
            Bukkit.getScheduler().runTask(plugin, () -> open(pl));
        });
        m.set(15, Menu.item(Material.RED_CONCRETE, Menu.title("✖ Болих", NamedTextColor.RED), List.of()), (pl, c) -> open(pl));
        m.open(p);
    }
}
