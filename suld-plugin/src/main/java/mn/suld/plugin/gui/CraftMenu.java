package mn.suld.plugin.gui;

import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.Recipe;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The Дархан's recipe book (docs/CRAFTING.md): every recipe, lowest level first, with what it needs and what the
 * player has. A click asks once, then crafts through {@link mn.suld.plugin.item.CraftService} (the rules of
 * {@code /item craft}). Opened from the smith or with {@code /item recipes}.
 */
public final class CraftMenu {

    private static final int PER_PAGE = 45;

    private final Plugin plugin;
    private final SuldServices services;

    public CraftMenu(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void open(Player p, int page) {
        var catalog = services.itemService().catalog();
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        int level = pr == null ? 1 : pr.progression().level();
        List<Recipe> all = new ArrayList<>(catalog.recipes());
        all.sort(Comparator.comparingInt(Recipe::levelReq).thenComparing(Recipe::id));
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        Menu m = new Menu(6, "Дархан · Жорын ном" + (pages > 1 ? " " + (pg + 1) + "/" + pages : ""), null);
        int slot = 0;
        for (Recipe r : all.subList(pg * PER_PAGE, Math.min(all.size(), (pg + 1) * PER_PAGE))) {
            ItemDefinition def = catalog.require(r.resultId());
            Component no = services.crafting().refusal(p, r);
            List<Component> lore = new ArrayList<>();
            lore.add(Menu.kv("Зэрэг:", r.levelReq() + "–" + r.levelCap() + (level >= r.levelReq() ? " (таных: " + r.itemLevel(level) + ")" : ""), NamedTextColor.AQUA));
            lore.add(Menu.kv("Чанар:", r.minRarity().displayName() + (r.maxRarity() != r.minRarity() ? " – " + r.maxRarity().displayName() : ""), NamedTextColor.LIGHT_PURPLE));
            for (Map.Entry<String, Integer> e : r.materials().entrySet()) {
                int have = services.crafting().count(p, e.getKey());
                String name = catalog.item(e.getKey()).map(ItemDefinition::displayName).orElse(e.getKey());
                lore.add(Component.text((have >= e.getValue() ? "✔ " : "✘ ") + name + " " + Math.min(have, 999) + "/" + e.getValue(),
                        have >= e.getValue() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            long coins = pr == null ? 0 : pr.currency();
            lore.add(Component.text((coins >= r.coins() ? "✔ " : "✘ ") + "Зоос " + coins + "/" + r.coins(), coins >= r.coins() ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(no == null ? Menu.line("Дарж урлана.") : no);
            Material icon = Material.matchMaterial(def.material());
            m.set(slot++, Menu.item(icon == null || !icon.isItem() ? Material.SMITHING_TABLE : icon,
                    Menu.title(def.displayName(), no == null ? NamedTextColor.GOLD : NamedTextColor.GRAY), lore),
                    no == null ? (pl, c) -> confirm(pl, r, pg) : null);
        }
        if (pg > 0) m.set(45, Menu.item(Material.ARROW, Menu.title("« Өмнөх", NamedTextColor.GOLD), List.of()), (pl, c) -> open(pl, pg - 1));
        m.set(49, Menu.item(Material.BOOK, Menu.title("Дархан", NamedTextColor.GOLD), List.of(
                Menu.line("Материалыг ан, агуй, босс, задлалтаас олно."),
                Menu.line("Урласан зүйлийн зэрэг: таны түвшин, жорын хүрээнд."))), null);
        if (pg < pages - 1) m.set(53, Menu.item(Material.ARROW, Menu.title("Дараах »", NamedTextColor.GOLD), List.of()), (pl, c) -> open(pl, pg + 1));
        m.open(p);
    }

    /** The one confirmation: nothing is spent by the first click. */
    private void confirm(Player p, Recipe r, int page) {
        var catalog = services.itemService().catalog();
        ItemDefinition def = catalog.require(r.resultId());
        Menu m = new Menu(3, "Баталгаажуулах", null);
        List<Component> lore = List.of(Menu.line(services.crafting().describe(r.materials())), Menu.line(r.coins() + " зоос"));
        m.set(11, Menu.item(Material.LIME_CONCRETE, Menu.title("✔ Урлах: " + def.displayName(), NamedTextColor.GREEN), lore), (pl, c) -> {
            pl.closeInventory();
            if (services.crafting().craft(pl, r) != null) pl.playSound(pl.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
            Bukkit.getScheduler().runTask(plugin, () -> open(pl, page));
        });
        m.set(13, Menu.item(Material.PAPER, Menu.title(def.displayName(), NamedTextColor.GOLD), lore), null);
        m.set(15, Menu.item(Material.RED_CONCRETE, Menu.title("✖ Болих", NamedTextColor.RED), List.of()), (pl, c) -> open(pl, page));
        m.open(p);
    }

    /** For callers outside a menu (the smith, /item recipes). */
    public void open(Player p) {
        if (services.itemService() == null) {
            p.sendMessage(Messages.error("Эд зүйлийн систем ачаалагдаагүй байна."));
            return;
        }
        open(p, 0);
    }
}
