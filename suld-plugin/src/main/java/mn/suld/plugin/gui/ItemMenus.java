package mn.suld.plugin.gui;

import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.Equipment;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemSet;
import mn.suld.api.item.ItemStat;
import mn.suld.api.item.ItemTooltip;
import mn.suld.api.item.ItemType;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.item.EquipmentService;
import mn.suld.plugin.item.ItemFactory;
import mn.suld.plugin.item.ItemService;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The item menus: {@code /items} (every SÜLD item the player carries, each compared with what is worn in its slot;
 * click to equip, right click to inspect) and {@code /equipment} (the nine slots with the reason an item is inactive,
 * accessory equip/unequip, the stat total, set progress and gear score). Both act on the real inventory and only
 * through {@link EquipmentService}, the same path the commands use; the menu items are copies and clicks are
 * cancelled by {@link MenuListener}, so nothing can be taken out of a menu.
 */
public final class ItemMenus {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final TextColor SKY = TextColor.fromHexString("#9FF3FF");
    private static final TextColor GREEN = TextColor.fromHexString("#7CE07C");
    private static final TextColor RED = TextColor.fromHexString("#FF6B6B");
    private static final TextColor GRAY = TextColor.fromHexString("#A8A8A8");

    /** Where each equipment slot is drawn in the 6-row equipment menu. */
    private static final Map<EquipSlot, Integer> LAYOUT = new EnumMap<>(EquipSlot.class);

    static {
        LAYOUT.put(EquipSlot.HEAD, 10);
        LAYOUT.put(EquipSlot.CHEST, 19);
        LAYOUT.put(EquipSlot.LEGS, 28);
        LAYOUT.put(EquipSlot.FEET, 37);
        LAYOUT.put(EquipSlot.MAIN_HAND, 21);
        LAYOUT.put(EquipSlot.OFF_HAND, 22);
        LAYOUT.put(EquipSlot.ACCESSORY_1, 12);
        LAYOUT.put(EquipSlot.ACCESSORY_2, 13);
        LAYOUT.put(EquipSlot.RELIC, 31);
    }

    private static final int PAGE = 45;

    private final SuldServices services;

    public ItemMenus(SuldServices services) {
        this.services = services;
    }

    private ItemService items() {
        return services.itemService();
    }

    private EquipmentService equipment() {
        return services.equipment();
    }

    private static ItemCatalog catalog() {
        return SuldContent.items();
    }

    private static Component b(String s) {
        return Menu.line(s);
    }

    // ================================================================== /items

    public void items(Player p) {
        list(p, "SÜLD эд зүйлс", s -> true, null, 0);
    }

    /**
     * The carried SÜLD items matching {@code filter}. With {@code target} set (the accessory picker) a click puts the
     * item into that slot; otherwise a click equips it where it belongs.
     */
    private void list(Player p, String title, Predicate<ItemDefinition> filter, EquipSlot target, int page) {
        Map<EquipSlot, ItemInstance> worn = equipment().worn(p);
        List<Integer> found = new ArrayList<>();
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack s = contents[slot];
            if (s == null || s.isEmpty()) continue;
            ItemService.Checked c = items().check(s);
            if (c.item() == null || c.verdict() == ItemService.Verdict.NOT_SULD) continue;
            ItemDefinition def = catalog().item(c.item().definitionId()).orElse(null);
            if (def == null || !filter.test(def)) continue;
            found.add(slot);
        }
        int pages = Math.max(1, (found.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(6, title + (pages > 1 ? " (" + (pg + 1) + "/" + pages + ")" : ""), null);
        for (int n = 0; n < PAGE && pg * PAGE + n < found.size(); n++) {
            int invSlot = found.get(pg * PAGE + n);
            ItemStack s = contents[invSlot];
            ItemService.Checked c = items().check(s);
            m.set(n, card(p, s, c, worn, target), (pl, click) -> clicked(pl, invSlot, c, target, click, title, filter, pg));
        }
        if (found.isEmpty()) {
            m.set(22, Menu.item(Material.BARRIER, Menu.title("Хоосон", GRAY),
                    List.of(b(target == null ? "Цүнхэнд SÜLD эд зүйл алга." : "Зүүх эд зүйл алга."))), null);
        }
        if (pg > 0) m.set(45, Menu.item(Material.ARROW, Menu.title("← Өмнөх", SKY), List.of()), (pl, c) -> list(pl, title, filter, target, pg - 1));
        m.set(49, Menu.item(Material.ARMOR_STAND, Menu.title("Хуяг дуулга", GOLD), List.of(b("Өмссөн зүйлс, нийт чадвар."))), (pl, c) -> equipment(pl));
        if (pg < pages - 1) m.set(53, Menu.item(Material.ARROW, Menu.title("Дараах →", SKY), List.of()), (pl, c) -> list(pl, title, filter, target, pg + 1));
        m.open(p);
    }

    /** A copy of the carried stack with the comparison against the worn item and the click hint appended. */
    private ItemStack card(Player p, ItemStack s, ItemService.Checked c, Map<EquipSlot, ItemInstance> worn, EquipSlot target) {
        ItemStack copy = s.clone();
        ItemMeta meta = copy.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (c.verdict() == ItemService.Verdict.FORGED) {
            lore.add(Component.empty());
            lore.add(Component.text("✖ Хүчингүй эд зүйл: " + String.join("; ", c.problems()), RED));
        } else {
            ItemDefinition def = catalog().require(c.item().definitionId());
            EquipSlot slot = target != null ? target : compareSlot(def, worn);
            ItemInstance other = slot == null ? null : worn.get(slot);
            if (other != null && !other.uuid().equals(c.item().uuid())) {
                lore.add(Component.empty());
                lore.add(Component.text("Өмссөнтэй харьцуулбал (" + slot.label() + "):", SKY));
                for (ItemTooltip.Line l : ItemTooltip.compare(catalog(), c.item(), other)) {
                    if (!l.text().isEmpty()) lore.add(ItemFactory.component(l, GRAY));
                }
            }
            lore.add(Component.empty());
            lore.add(Menu.line(def.type().equippable() && def.type() != ItemType.MATERIAL ? "« Дарж өмсөх · Баруун: дэлгэрэнгүй »" : "« Баруун: дэлгэрэнгүй »"));
        }
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    /** The worn slot an item would replace: its own slot, or the first filled accessory/hand slot it fits. */
    private static EquipSlot compareSlot(ItemDefinition def, Map<EquipSlot, ItemInstance> worn) {
        EquipSlot first = null;
        for (EquipSlot s : def.type().slots()) {
            if (first == null) first = s;
            if (worn.containsKey(s)) return s;
        }
        return first;
    }

    private void clicked(Player p, int invSlot, ItemService.Checked c, EquipSlot target, ClickType click, String title,
                         Predicate<ItemDefinition> filter, int page) {
        // the inventory may have changed since the menu was drawn: act only if the same item is still in that slot
        ItemStack now = p.getInventory().getItem(invSlot);
        ItemService.Checked current = now == null ? null : items().check(now);
        if (current == null || current.item() == null || !current.item().uuid().equals(c.item().uuid())) {
            p.sendMessage(Messages.error("Эд зүйл байрлалаа өөрчилсөн байна."));
            list(p, title, filter, target, page);
            return;
        }
        if (click.isRightClick()) {
            inspect(p, now, current);
            return;
        }
        try {
            EquipSlot used = equipment().equip(p, invSlot, target);
            ItemDefinition def = catalog().require(current.item().definitionId());
            p.sendMessage(Messages.success(def.displayName() + " → " + used.label()));
            equipment(p);
        } catch (IllegalStateException e) {
            p.sendMessage(Messages.error(e.getMessage()));
        }
    }

    /** The full tooltip and identity of one item in chat. */
    public void inspect(Player p, ItemStack stack, ItemService.Checked c) {
        if (c.verdict() == ItemService.Verdict.FORGED || c.item() == null) {
            p.sendMessage(Messages.error("Энэ эд зүйл хүчингүй: " + String.join("; ", c.problems())));
            return;
        }
        ItemInstance i = c.item();
        ItemDefinition def = catalog().require(i.definitionId());
        int[] d = items().factory().durability(stack, i);
        for (ItemTooltip.Line line : ItemTooltip.lines(catalog(), def, i, items().viewer(p), d[0], d[1], null)) {
            if (!line.text().isEmpty()) p.sendMessage(ItemFactory.component(line, ItemFactory.rarityColor(i.rarity())));
        }
        p.sendMessage(Component.text("id " + def.id() + " · " + i.uuid() + " · эх сурвалж " + i.provenance() + " · хувилбар " + i.schemaVersion()
                + (c.verdict() == ItemService.Verdict.MIGRATED ? " (хуучнаас шилжүүлсэн)" : ""), NamedTextColor.DARK_GRAY));
    }

    // ================================================================== /equipment

    public void equipment(Player p) {
        EquipmentService eq = equipment();
        eq.compute(p); // the menu shows the state right now, not the last debounced one
        Map<EquipSlot, ItemInstance> worn = eq.worn(p);
        Equipment.Bonus bonus = eq.bonus(p);
        Menu m = new Menu(6, "Хуяг дуулга", null);
        ItemStack filler = Menu.item(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int s = 0; s < 54; s++) m.set(s, filler, null);
        for (Map.Entry<EquipSlot, Integer> e : LAYOUT.entrySet()) {
            EquipSlot slot = e.getKey();
            m.set(e.getValue(), slotIcon(p, slot, worn.get(slot), bonus.inactive().get(slot)), (pl, c) -> slotClicked(pl, slot, worn.get(slot), c));
        }
        m.set(15, stats(bonus), null);
        m.set(24, sets(bonus), null);
        int score = Equipment.gearScore(worn, bonus.inactive());
        m.set(33, Menu.item(Material.NETHER_STAR, Menu.title("Хуягийн оноо: " + score, GOLD),
                List.of(b("Идэвхтэй эд зүйлсийн түвшин × зэрэглэл."))), null);
        m.set(48, Menu.item(Material.CHEST, Menu.title("Эд зүйлс", SKY), List.of(b("Цүнхэн дэх SÜLD эд зүйлс."))), (pl, c) -> items(pl));
        m.set(50, Menu.item(Material.BARRIER, Menu.title("Хаах", RED), List.of()), (pl, c) -> pl.closeInventory());
        m.open(p);
    }

    private ItemStack slotIcon(Player p, EquipSlot slot, ItemInstance i, Equipment.Inactive inactive) {
        if (i == null) {
            String hint = switch (slot) {
                case ACCESSORY_1, ACCESSORY_2 -> "« Дарж бөгж, зүүлт зүүх »";
                case RELIC -> "Өөрийн эзэмшдэг дурсгалыг биедээ авч яваарай.";
                case MAIN_HAND -> "Зэвсгээ гартаа барина.";
                default -> "Цүнхнээс өмсөнө (/items).";
            };
            return Menu.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Menu.title(slot.label() + " — хоосон", GRAY), List.of(b(hint)));
        }
        ItemStack s = items().stack(i, p, 1);
        ItemMeta meta = s.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        if (inactive != null) lore.add(Component.text("✖ Нөлөөгүй: " + inactive.text(), RED));
        else lore.add(Component.text("✔ Идэвхтэй — " + slot.label(), GREEN));
        if (slot != EquipSlot.MAIN_HAND && slot != EquipSlot.RELIC) lore.add(Menu.line("« Дарж тайлах »"));
        meta.lore(lore);
        s.setItemMeta(meta);
        return s;
    }

    private void slotClicked(Player p, EquipSlot slot, ItemInstance i, ClickType click) {
        if (i == null) {
            if (slot == EquipSlot.ACCESSORY_1 || slot == EquipSlot.ACCESSORY_2) {
                list(p, slot.label() + " — сонгох", d -> d.type().slots().contains(slot), slot, 0);
            }
            return;
        }
        if (slot == EquipSlot.MAIN_HAND || slot == EquipSlot.RELIC) return;
        try {
            equipment().unequip(p, slot);
            p.sendMessage(Messages.success(slot.label() + " тайлагдлаа."));
        } catch (IllegalStateException e) {
            p.sendMessage(Messages.error(e.getMessage()));
        }
        equipment(p);
    }

    private static ItemStack stats(Equipment.Bonus bonus) {
        List<Component> lore = new ArrayList<>();
        for (ItemStat s : ItemStat.values()) {
            double v = bonus.stat(s);
            if (Math.abs(v) < 1e-9) continue;
            lore.add(Menu.kv(s.label(), (v > 0 ? "+" : "") + s.format(v), GREEN));
        }
        if (lore.isEmpty()) lore.add(b("Идэвхтэй эд зүйлгүй."));
        return Menu.item(Material.BOOK, Menu.title("Эд зүйлсийн чадвар", SKY), lore);
    }

    private static ItemStack sets(Equipment.Bonus bonus) {
        List<Component> lore = new ArrayList<>();
        for (Equipment.SetProgress sp : bonus.sets()) {
            ItemSet set = sp.set();
            lore.add(Menu.kv(set.name(), sp.worn() + "/" + set.pieces().size(), GOLD));
            for (Map.Entry<Integer, ItemSet.Bonus> e : set.bonuses().entrySet()) {
                boolean on = sp.worn() >= e.getKey();
                lore.add(Component.text((on ? "  ✔ " : "  ✖ ") + e.getKey() + ": " + ItemTooltip.bonusText(e.getValue()), on ? GREEN : GRAY));
            }
        }
        if (lore.isEmpty()) lore.add(b("Иж бүрдлийн хэсэг өмсөөгүй."));
        return Menu.item(Material.GOLDEN_HELMET, Menu.title("Иж бүрдэл", GOLD), lore);
    }

    /** True when the player's profile is loaded (the menus need it for requirements). */
    public boolean ready(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        return pr != null && services.itemService() != null && services.equipment() != null;
    }
}
