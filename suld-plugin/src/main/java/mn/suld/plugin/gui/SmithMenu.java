package mn.suld.plugin.gui;

import mn.suld.api.classgear.ArmorPiece;
import mn.suld.api.classgear.ArmorRules;
import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.classgear.MasteryPerks;
import mn.suld.api.classgear.MasteryRules;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.item.ClassArmor;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Дархан smith's class-armour screen (docs/CLASS_ARMOR_SYSTEM.md "Lifecycle" 3): the player-facing way to inspect
 * the class set and buy its next tier or an enhancement. Every purchase goes through a confirmation screen, and the
 * confirmation checks everything again (gates, coins, materials, soul state, being at the smith) — a screen left open
 * never buys anything stale. The work itself is {@link ClassArmor#upgrade}/{@link ClassArmor#enhance}; the
 * {@code /classgear upgrade|enhance} commands remain for staff/debug only.
 */
public final class SmithMenu {

    /** The player must stay this close to the smith for a purchase. */
    private static final double REACH = 8;

    private final Plugin plugin;
    private final SuldServices services;

    public SmithMenu(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    private ClassArmor armor() {
        return services.classArmor;
    }

    /** Open the screen; {@code smith} supplies where the smith stands now (null when unknown). */
    public void open(Player p, Supplier<Location> smith) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        PlayerClass c = pr == null ? null : pr.playerClass().orElse(null);
        if (armor() == null || !armor().supports(c)) {
            p.sendMessage(Messages.info("Дархан: «Эхлээд ангиа сонго — тэгээд хуягийг чинь хамт давтъя.»"));
            return;
        }
        ClassGear g = pr.classGear();
        ArmorTier next = g.tier().next().orElse(null);
        Menu m = new Menu(5, "Дархан · Ангийн хуяг", null);

        // the set as it is
        m.set(4, Menu.item(Material.SMITHING_TABLE, Menu.title(g.tier().name() + " " + g.tier().displayName()
                + (g.enhance() > 0 ? " +" + g.enhance() : ""), NamedTextColor.GOLD), List.of(
                Menu.kv("Хуягийн түвшин:", g.armorLevel() + (ArmorRules.need(g.armorLevel()) > 0
                        ? " (" + Math.round(g.armorXp()) + "/" + ArmorRules.need(g.armorLevel()) + " XP)" : ""), NamedTextColor.AQUA),
                Menu.kv("Ур чадвар:", g.mastery() + "/" + MasteryRules.MAX_RANK, NamedTextColor.LIGHT_PURPLE),
                Menu.kv("Хүч:", "×" + Math.round(ArmorRules.powerFactor(g.enhance()) * MasteryRules.powerFactor(g.mastery()) * 1000) / 1000.0,
                        NamedTextColor.GREEN))), null);
        int slot = 10;
        for (ArmorPiece piece : ArmorPiece.values()) {
            m.set(slot++, shown(p, c, g, piece, g.tier()), null);
        }
        ItemStack weapon = findWeapon(p, g);
        m.set(15, weapon != null ? weapon : Menu.item(Material.BARRIER, Menu.title("Ангийн зэвсэг алга", NamedTextColor.RED),
                List.of(Menu.line("/classgear recover"))), null);

        // the next tier
        if (next == null) {
            m.set(22, Menu.item(Material.NETHER_STAR, Menu.title("Хамгийн дээд зэрэг", NamedTextColor.GOLD), List.of()), null);
        } else {
            List<ArmorRules.Gate> gates = ArmorRules.gates(g, armor().holdings(p, pr), armor()::displayName);
            List<Component> lore = new ArrayList<>();
            lore.add(Menu.line("Шаардлага:"));
            for (ArmorRules.Gate gate : gates) {
                lore.add(Component.text((gate.met() ? "✔ " : "✘ ") + gate.label(), gate.met() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            if (next.material() != null) {
                lore.add(Menu.kv("Таньд:", armor().holdings(p, pr).materials() + "/" + next.materials() + " " + armor().displayName(next.material()),
                        NamedTextColor.AQUA));
            }
            lore.add(Menu.kv("Зоос:", pr.currency() + "/" + next.coins() + " ₮", NamedTextColor.GOLD));
            boolean ok = gates.stream().allMatch(ArmorRules.Gate::met);
            m.set(20, shown(p, c, g.withTier(next), ArmorPiece.CHESTPLATE, next), null);
            m.set(22, Menu.item(ok ? Material.ANVIL : Material.BARRIER, Menu.title("Дараагийн зэрэг: " + next.name() + " "
                    + next.displayName(), ok ? NamedTextColor.GREEN : NamedTextColor.RED), lore), !ok ? null
                    : (pl, ct) -> confirm(pl, smith, "Зэрэг ахиулах → " + next.name() + " " + next.displayName(),
                            List.of(next.coins() + " ₮" + (next.material() != null ? " · " + next.materials() + " × " + armor().displayName(next.material()) : ""),
                                    "Хуяг шинэ төрхтэй болж, сайжруулалт 0 болно"), true));
        }

        // enhancement
        if (g.enhance() < ArmorRules.MAX_ENHANCE) {
            long cost = ArmorRules.enhanceCost(g.armorLevel(), g.enhance() + 1, g.tier());
            boolean ok = pr.currency() >= cost;
            m.set(24, Menu.item(ok ? Material.GRINDSTONE : Material.BARRIER, Menu.title("Сайжруулах +" + (g.enhance() + 1) + " · " + cost + " ₮",
                    ok ? NamedTextColor.GREEN : NamedTextColor.RED), List.of(Menu.line("Ангийн хуягийн хүч +2 %"),
                    Menu.kv("Зоос:", pr.currency() + "/" + cost + " ₮", NamedTextColor.GOLD))), !ok ? null
                    : (pl, ct) -> confirm(pl, smith, "Сайжруулах +" + (g.enhance() + 1), List.of(cost + " ₮", "Хүч +2 %"), false));
        } else {
            m.set(24, Menu.item(Material.GRINDSTONE, Menu.title("Сайжруулалт дүүрэн (+" + ArmorRules.MAX_ENHANCE + ")", NamedTextColor.GOLD), List.of()), null);
        }

        // mastery
        List<Component> perks = new ArrayList<>();
        long need = MasteryRules.need(g.mastery());
        perks.add(Menu.kv("Зэрэг:", g.mastery() + (need > 0 ? " (" + Math.round(g.masteryXp()) + "/" + need + ")" : ""), NamedTextColor.LIGHT_PURPLE));
        perks.add(Menu.line("Анги, шид, агуй, бие даасан даалгавраар өснө."));
        for (MasteryPerks.Perk perk : MasteryPerks.of(c)) {
            perks.add(Component.text((g.mastery() >= perk.rank() ? "✔ " : "· ") + perk.rank() + ": " + perk.text(),
                    g.mastery() >= perk.rank() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        m.set(31, Menu.item(Material.ENCHANTED_BOOK, Menu.title("Хуягийн ур чадвар", NamedTextColor.LIGHT_PURPLE), perks), null);
        m.set(40, Menu.item(Material.OAK_DOOR, Menu.title("Хаах", NamedTextColor.GRAY), List.of()), (pl, ct) -> pl.closeInventory());
        m.open(p);
    }

    /** What a piece looks like at a tier: the player's own stack when shown as is, else a display-only rendering. */
    private ItemStack shown(Player p, PlayerClass c, ClassGear g, ArmorPiece piece, ArmorTier tier) {
        UUID id = g.piece(piece).orElse(new UUID(0, piece.ordinal()));
        if (tier == g.tier()) {
            for (ItemStack it : p.getInventory().getContents()) {
                ItemInstance ii = it == null ? null : services.items().read(it).orElse(null);
                if (ii != null && ii.uuid().equals(id)) return it.clone();
            }
        }
        return services.itemService().stack(armor().build(p.getUniqueId(), c, piece, g, id), p, 1);
    }

    private ItemStack findWeapon(Player p, ClassGear g) {
        for (ItemStack it : p.getInventory().getContents()) {
            ItemInstance ii = it == null ? null : services.items().read(it).orElse(null);
            if (ii != null && ii.definitionId().startsWith("weapon.class.") && p.getUniqueId().equals(ii.boundTo())) return it.clone();
        }
        return null;
    }

    /** The confirmation step: nothing is bought by the first click. */
    private void confirm(Player p, Supplier<Location> smith, String what, List<String> lines, boolean tier) {
        Menu m = new Menu(3, "Баталгаажуулах", null);
        List<Component> lore = new ArrayList<>();
        for (String l : lines) lore.add(Menu.line(l));
        m.set(11, Menu.item(Material.LIME_CONCRETE, Menu.title("✔ " + what, NamedTextColor.GREEN), lore), (pl, ct) -> {
            pl.closeInventory();
            Location at = smith == null ? null : smith.get();
            if (at == null || at.getWorld() != pl.getWorld() || at.distance(pl.getLocation()) > REACH) {
                pl.sendMessage(Messages.error("Дархны дэргэд байх хэрэгтэй."));
                return;
            }
            if (tier) {
                ClassArmor.Upgrade r = armor().upgrade(pl);
                pl.sendMessage(switch (r) {
                    case DONE -> Messages.success("Дархан хуягийг чинь шинэ зэрэгт давтлаа!");
                    case GATES -> Messages.error("Шаардлага хангагдахаа больсон — дахин шалга.");
                    case MAX -> Messages.info("Хамгийн дээд зэрэг.");
                    case SOUL -> Messages.error("Сүнс байхдаа боломжгүй.");
                    case NO_CLASS -> Messages.error("Ангийн хуяг алга.");
                });
                if (r == ClassArmor.Upgrade.DONE) {
                    pl.playSound(pl.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 0.9f);
                    services.classArmor.smithWork(pl);
                }
            } else {
                ClassArmor.Enhance r = armor().enhance(pl);
                pl.sendMessage(switch (r) {
                    case DONE -> Messages.success("Ангийн хуяг сайжирлаа (+2 % хүч).");
                    case COINS -> Messages.error("Зоос хүрэлцэхгүй.");
                    case MAX -> Messages.info("Энэ зэрэгт хамгийн их сайжруулалт.");
                    case SOUL -> Messages.error("Сүнс байхдаа боломжгүй.");
                    case NO_CLASS -> Messages.error("Ангийн хуяг алга.");
                });
                if (r == ClassArmor.Enhance.DONE) {
                    pl.playSound(pl.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.3f);
                    services.classArmor.smithWork(pl);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> open(pl, smith));
        });
        m.set(13, Menu.item(Material.PAPER, Menu.title(what, NamedTextColor.GOLD), lore), null);
        m.set(15, Menu.item(Material.RED_CONCRETE, Menu.title("✖ Болих", NamedTextColor.RED), List.of(Menu.line("Юу ч өөрчлөхгүй"))),
                (pl, ct) -> open(pl, smith));
        m.open(p);
    }
}
