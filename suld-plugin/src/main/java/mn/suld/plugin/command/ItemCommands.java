package mn.suld.plugin.command;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.item.Binding;
import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.Equipment;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemEconomy;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import mn.suld.api.item.ItemTooltip;
import mn.suld.api.item.ItemType;
import mn.suld.api.item.Recipe;
import mn.suld.api.loot.LootContext;
import mn.suld.api.loot.LootDrop;
import mn.suld.api.loot.LootTable;
import mn.suld.api.loot.LootTier;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.tree.SkillTreeLoader;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.gui.ItemMenus;
import mn.suld.plugin.item.EquipmentService;
import mn.suld.plugin.item.ItemFactory;
import mn.suld.plugin.item.ItemService;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /item}, {@code /items}, {@code /equipment}, {@code /loot} and {@code /itemsadmin}. */
public final class ItemCommands {

    private final SuldServices services;
    private final ItemMenus menus;
    /** Pending confirmations (sell / destroy / bind / salvage) by player, with the item they were asked about. */
    private final Map<UUID, String> pending = new HashMap<>();

    public ItemCommands(SuldServices services, ItemMenus menus) {
        this.services = services;
        this.menus = menus;
    }

    private ItemService items() {
        return services.itemService();
    }

    private EquipmentService equipment() {
        return services.equipment();
    }

    private ItemCatalog catalog() {
        return items().catalog();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }

    private static final List<String> SLOT_ARGS = List.of("head", "chest", "legs", "feet", "offhand", "accessory1", "accessory2");

    private static EquipSlot slotArg(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "head" -> EquipSlot.HEAD;
            case "chest" -> EquipSlot.CHEST;
            case "legs" -> EquipSlot.LEGS;
            case "feet" -> EquipSlot.FEET;
            case "offhand" -> EquipSlot.OFF_HAND;
            case "accessory1", "a1" -> EquipSlot.ACCESSORY_1;
            case "accessory2", "a2" -> EquipSlot.ACCESSORY_2;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ /item

    public TabExecutor item() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (!(s instanceof Player p)) {
                    s.sendMessage(Messages.error("Зөвхөн тоглогч."));
                    return true;
                }
                String sub = a.length == 0 ? "inspect" : a[0].toLowerCase(Locale.ROOT);
                switch (sub) {
                    case "inspect", "info" -> inspect(p);
                    case "compare" -> compare(p);
                    case "equip" -> equip(p, a.length > 1 ? slotArg(a[1]) : null);
                    case "unequip" -> {
                        EquipSlot slot = a.length > 1 ? slotArg(a[1]) : null;
                        if (slot == null) p.sendMessage(Messages.error("Хэрэглээ: /item unequip <" + String.join("|", SLOT_ARGS) + ">"));
                        else unequip(p, slot);
                    }
                    case "sell" -> confirmable(p, "sell", a, () -> sell(p));
                    case "destroy" -> {
                        if (!undestroyable(p)) confirmable(p, "destroy", a, () -> destroy(p));
                    }
                    case "bind" -> confirmable(p, "bind", a, () -> bind(p));
                    case "salvage" -> {
                        if (a.length > 1 && a[1].equalsIgnoreCase("all")) salvageAll(p, a);
                        else confirmable(p, "salvage", a, () -> salvage(p));
                    }
                    case "filter" -> filterCommand(p, a);
                    case "recipes" -> recipes(p);
                    case "craft" -> {
                        if (a.length < 2) p.sendMessage(Messages.error("Хэрэглээ: /item craft <жор> (/item recipes)"));
                        else craft(p, a[1]);
                    }
                    default -> p.sendMessage(Messages.info("/item [inspect|compare|equip [accessory1|accessory2]|unequip <нүд>|sell|destroy|bind|salvage [all <зэрэглэл>]|filter [off|common|uncommon|rare]|recipes|craft <жор>]"));
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (a.length == 1) return filter(List.of("inspect", "compare", "equip", "unequip", "sell", "destroy", "bind", "salvage", "filter", "recipes", "craft"), a[0]);
                if (a.length == 2 && a[0].equalsIgnoreCase("filter")) return filter(List.of("off", "common", "uncommon", "rare"), a[1]);
                if (a.length == 2 && a[0].equalsIgnoreCase("salvage")) return filter(List.of("all", "confirm"), a[1]);
                if (a.length == 3 && a[0].equalsIgnoreCase("salvage") && a[1].equalsIgnoreCase("all")) return filter(List.of("common", "uncommon", "rare"), a[2]);
                if (a.length == 2 && a[0].equalsIgnoreCase("unequip")) return filter(SLOT_ARGS, a[1]);
                if (a.length == 2 && a[0].equalsIgnoreCase("equip")) return filter(List.of("accessory1", "accessory2"), a[1]);
                if (a.length == 2 && a[0].equalsIgnoreCase("craft")) return filter(catalog().recipes().stream().map(Recipe::id).toList(), a[1]);
                if (a.length == 2 && List.of("sell", "destroy", "bind", "salvage").contains(a[0].toLowerCase(Locale.ROOT))) return filter(List.of("confirm"), a[1]);
                return List.of();
            }
        };
    }

    /** Held item (validated); null with a message when there is none or it is not genuine. */
    private ItemService.Checked held(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemService.Checked c = items().check(hand);
        if (c.verdict() == ItemService.Verdict.NOT_SULD) {
            p.sendMessage(Messages.error("Гартаа SÜLD эд зүйл барина уу."));
            return null;
        }
        if (c.verdict() == ItemService.Verdict.FORGED) {
            p.sendMessage(Messages.error("Энэ эд зүйл хүчингүй: " + String.join("; ", c.problems())));
            return null;
        }
        return c;
    }

    private void inspect(Player p) {
        ItemService.Checked c = held(p);
        if (c == null) return;
        ItemInstance i = c.item();
        ItemDefinition def = catalog().require(i.definitionId());
        int[] d = items().factory().durability(p.getInventory().getItemInMainHand(), i);
        for (ItemTooltip.Line line : ItemTooltip.lines(catalog(), def, i, items().viewer(p), d[0], d[1], null)) {
            if (!line.text().isEmpty()) p.sendMessage(ItemFactory.component(line, ItemFactory.rarityColor(i.rarity())));
        }
        p.sendMessage(Component.text("id " + def.id() + " · " + i.uuid() + " · эх сурвалж " + i.provenance() + " · хувилбар " + i.schemaVersion()
                + (c.verdict() == ItemService.Verdict.MIGRATED ? " (хуучнаас шилжүүлсэн)" : ""), NamedTextColor.DARK_GRAY));
    }

    private void compare(Player p) {
        ItemService.Checked c = held(p);
        if (c == null) return;
        ItemDefinition def = catalog().require(c.item().definitionId());
        List<EquipSlot> slots = new ArrayList<>(def.type().slots());
        if (slots.isEmpty()) {
            p.sendMessage(Messages.info("Энэ зүйлийг өмсдөггүй."));
            return;
        }
        Map<EquipSlot, ItemInstance> worn = equipment().worn(p);
        boolean any = false;
        for (EquipSlot slot : slots) {
            ItemInstance other = worn.get(slot);
            if (other == null || other.uuid().equals(c.item().uuid())) continue;
            any = true;
            p.sendMessage(Messages.info(slot.label() + ": " + ItemTooltip.name(catalog(), catalog().require(other.definitionId()), other)));
            for (ItemTooltip.Line line : ItemTooltip.compare(catalog(), c.item(), other)) {
                if (!line.text().isEmpty()) p.sendMessage(ItemFactory.component(line, NamedTextColor.GRAY));
            }
        }
        if (!any) p.sendMessage(Messages.info("Харьцуулах өмссөн зүйл алга: " + def.type().label() + "-н нүд хоосон."));
    }

    private void equip(Player p, EquipSlot requested) {
        ItemService.Checked c = held(p);
        if (c == null) return;
        ItemDefinition def = catalog().require(c.item().definitionId());
        try {
            EquipSlot slot = equipment().equip(p, p.getInventory().getHeldItemSlot(), requested);
            p.sendMessage(Messages.success(def.displayName() + " → " + slot.label()));
        } catch (IllegalStateException e) {
            p.sendMessage(Messages.error(e.getMessage()));
        }
    }

    private void unequip(Player p, EquipSlot slot) {
        try {
            equipment().unequip(p, slot);
            p.sendMessage(Messages.success(slot.label() + " тайлагдлаа."));
        } catch (IllegalStateException e) {
            p.sendMessage(Messages.error(e.getMessage()));
        }
    }

    /** Two-step actions: the first call explains, "confirm" within 30 seconds on the same item does it. */
    private void confirmable(Player p, String action, String[] a, Runnable run) {
        ItemService.Checked c = held(p);
        if (c == null) return;
        String key = action + ":" + c.item().uuid() + ":" + p.getInventory().getHeldItemSlot() + ":" + (System.currentTimeMillis() / 30_000);
        if (a.length > 1 && a[1].equalsIgnoreCase("confirm") && key.equals(pending.get(p.getUniqueId()))) {
            pending.remove(p.getUniqueId());
            run.run();
            return;
        }
        pending.put(p.getUniqueId(), key);
        ItemDefinition def = catalog().require(c.item().definitionId());
        String what = switch (action) {
            case "sell" -> {
                long price = ItemEconomy.sellPrice(def, c.item()) * p.getInventory().getItemInMainHand().getAmount();
                yield price <= 0 ? null : "Зарах уу? " + price + " ₮";
            }
            case "destroy" -> "Устгах уу? Буцаах боломжгүй.";
            case "bind" -> c.item().bound() ? null : "Өөртөө холбох уу? Холбосон зүйлийг бусад авч, арилжиж чадахгүй.";
            default -> {
                Map<String, Integer> mats = ItemEconomy.salvage(catalog(), def, c.item());
                yield mats.isEmpty() ? null : "Задлах уу? → " + describe(mats);
            }
        };
        if (what == null) {
            pending.remove(p.getUniqueId());
            p.sendMessage(Messages.error(switch (action) {
                case "sell" -> "Энэ зүйлийг зарах боломжгүй.";
                case "bind" -> "Аль хэдийн холбогдсон.";
                default -> "Энэ зүйлийг задлах боломжгүй.";
            }));
            return;
        }
        p.sendMessage(Messages.info(ItemTooltip.name(catalog(), def, c.item()) + ": " + what + "  (/item " + action + " confirm)"));
    }

    private String describe(Map<String, Integer> mats) {
        StringBuilder sb = new StringBuilder();
        mats.forEach((id, n) -> sb.append(sb.length() == 0 ? "" : ", ").append(catalog().item(id).map(ItemDefinition::displayName).orElse(id)).append(" ×").append(n));
        return sb.toString();
    }

    private void sell(Player p) {
        ItemService.Checked c = held(p);
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (c == null || pr == null) return;
        ItemDefinition def = catalog().require(c.item().definitionId());
        int amount = p.getInventory().getItemInMainHand().getAmount();
        long price = ItemEconomy.sellPrice(def, c.item()) * amount;
        if (price <= 0) {
            p.sendMessage(Messages.error("Энэ зүйлийг зарах боломжгүй."));
            return;
        }
        p.getInventory().setItemInMainHand(null);
        pr.addCurrency(price);
        services.profiles().save(pr);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.sell", def.id() + "-" + c.item().uuid(), amount + " for " + price));
        p.sendMessage(Messages.success(def.displayName() + (amount > 1 ? " ×" + amount : "") + " зарагдлаа: +" + price + " ₮"));
        services.hud().update(p, pr);
    }

    /** Relics and soulbound (class) gear are refused up front, before the confirmation is even asked. */
    private boolean undestroyable(Player p) {
        if (services.relics().items().isRelic(p.getInventory().getItemInMainHand())) {
            p.sendMessage(Messages.error("Дурсгалыг устгах боломжгүй."));
            return true;
        }
        ItemInstance i = items().factory().read(p.getInventory().getItemInMainHand()).orElse(null);
        if (i != null && ItemEconomy.classGear(i.definitionId())) {
            p.sendMessage(Messages.error("Ангийн зэвсэг, хуягийг устгах боломжгүй."));
            return true;
        }
        return false;
    }

    private void destroy(Player p) {
        ItemService.Checked c = held(p);
        if (c == null || undestroyable(p)) return;
        p.getInventory().setItemInMainHand(null);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.destroy", c.item().definitionId() + "-" + c.item().uuid(), ""));
        p.sendMessage(Messages.success("Устгагдлаа."));
        equipment().dirty(p);
    }

    private void bind(Player p) {
        ItemService.Checked c = held(p);
        if (c == null) return;
        if (c.item().bound()) {
            p.sendMessage(Messages.info("Аль хэдийн холбогдсон."));
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        items().factory().rewrite(hand, c.item().boundTo(p.getUniqueId()), items().viewer(p));
        p.getInventory().setItemInMainHand(hand);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.bind", c.item().definitionId() + "-" + c.item().uuid(), "command"));
        p.sendMessage(Messages.success("Танд холбогдлоо."));
    }

    private void salvage(Player p) {
        ItemService.Checked c = held(p);
        if (c == null) return;
        ItemDefinition def = catalog().require(c.item().definitionId());
        Map<String, Integer> mats = ItemEconomy.salvage(catalog(), def, c.item());
        if (mats.isEmpty()) {
            p.sendMessage(Messages.error("Энэ зүйлийг задлах боломжгүй."));
            return;
        }
        p.getInventory().setItemInMainHand(null);
        List<LootDrop> out = new ArrayList<>();
        mats.forEach((id, n) -> {
            ItemDefinition md = catalog().require(id);
            out.add(new LootDrop(items().generate(md, md.rarity(), 1, null, "salvage"), n));
        });
        items().give(p, out, "salvage");
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.salvage", def.id() + "-" + c.item().uuid(), describe(mats)));
        p.sendMessage(Messages.success(def.displayName() + " задлагдлаа → " + describe(mats)));
        equipment().dirty(p);
    }

    /** /item filter [off|common|uncommon|rare]: gear at or below the rarity is salvaged into materials as it drops. */
    private void filterCommand(Player p, String[] a) {
        if (a.length < 2) {
            ItemRarity f = items().filter(p);
            p.sendMessage(Messages.info("Олзны шүүлтүүр: " + (f == null ? "унтраалттай" : f.displayName() + " ба түүнээс доош задлагдана")
                    + ". /item filter <off|common|uncommon|rare>"));
            return;
        }
        String v = a[1].toLowerCase(Locale.ROOT);
        ItemRarity r = v.equals("off") ? null : ItemRarity.byId(v).orElse(null);
        if (!v.equals("off") && (r == null || r.ordinal() > ItemRarity.RARE.ordinal())) {
            p.sendMessage(Messages.error("off, common, uncommon эсвэл rare."));
            return;
        }
        items().setFilter(p, r);
        p.sendMessage(Messages.success(r == null ? "Шүүлтүүр унтарлаа: бүх олз хэвээр унана."
                : "Шүүлтүүр: " + r.displayName() + " ба түүнээс доош хуяг, зэвсэг унахдаа түүхий эд болно."));
    }

    /**
     * /item salvage all [rarity] [confirm]: salvage every unbound, unequipped piece of gear in the bag at or below the
     * rarity (default Ховор/uncommon). Soulbound, bound, unique, materials and worn items are never touched. Asks once.
     */
    private void salvageAll(Player p, String[] a) {
        ItemRarity upTo = a.length > 2 && !a[2].equalsIgnoreCase("confirm") ? ItemRarity.byId(a[2].toLowerCase(Locale.ROOT)).orElse(null) : ItemRarity.UNCOMMON;
        if (upTo == null || upTo.ordinal() > ItemRarity.RARE.ordinal()) {
            p.sendMessage(Messages.error("Хэрэглээ: /item salvage all [common|uncommon|rare]"));
            return;
        }
        boolean confirm = a[a.length - 1].equalsIgnoreCase("confirm");
        ItemStack[] inv = p.getInventory().getStorageContents();
        Map<String, Integer> mats = new java.util.LinkedHashMap<>();
        List<Integer> slots = new ArrayList<>();
        for (int k = 0; k < inv.length; k++) {
            if (inv[k] == null) continue;
            ItemService.Checked c = items().check(inv[k]);
            if (c.verdict() == ItemService.Verdict.NOT_SULD || c.verdict() == ItemService.Verdict.FORGED) continue;
            ItemInstance i = c.item();
            ItemDefinition def = catalog().item(i.definitionId()).orElse(null);
            if (def == null || def.stackable() || i.bound() || mn.suld.plugin.item.SoulboundGuard.soulbound(i)
                    || i.rarity().ordinal() > upTo.ordinal()) continue;
            Map<String, Integer> m = ItemEconomy.salvage(catalog(), def, i);
            if (m.isEmpty()) continue;
            slots.add(k);
            m.forEach((id, n) -> mats.merge(id, n, Integer::sum));
        }
        if (slots.isEmpty()) {
            p.sendMessage(Messages.info("Задлах " + upTo.displayName() + " ба түүнээс доош хуяг, зэвсэг алга."));
            return;
        }
        String key = "salvageall:" + upTo + ":" + slots + ":" + (System.currentTimeMillis() / 30_000);
        if (!confirm || !key.equals(pending.get(p.getUniqueId()))) {
            pending.put(p.getUniqueId(), key);
            p.sendMessage(Messages.info(slots.size() + " зүйлийг задлах уу? → " + describe(mats)
                    + ". Батлах: /item salvage all " + upTo.name().toLowerCase(Locale.ROOT) + " confirm"));
            return;
        }
        pending.remove(p.getUniqueId());
        for (int k : slots) {
            ItemInstance i = items().check(inv[k]).item();
            services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.salvage", i.definitionId() + "-" + i.uuid(), "bulk"));
            p.getInventory().setItem(k, null);
        }
        List<LootDrop> out = new ArrayList<>();
        mats.forEach((id, n) -> {
            ItemDefinition md = catalog().require(id);
            out.add(new LootDrop(items().generate(md, md.rarity(), 1, null, "salvage"), n));
        });
        items().give(p, out, "salvage");
        p.sendMessage(Messages.success(slots.size() + " зүйл задлагдлаа → " + describe(mats)));
        equipment().dirty(p);
    }

    private void recipes(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        int level = pr == null ? 1 : pr.progression().level();
        p.sendMessage(Messages.info("Жорууд (/item craft <жор>):"));
        for (Recipe r : catalog().recipes()) {
            ItemDefinition def = catalog().require(r.resultId());
            boolean ok = level >= r.levelReq();
            p.sendMessage(Component.text("  " + r.id() + " → " + def.displayName() + " (" + r.minRarity().displayName() + ".." + r.maxRarity().displayName()
                    + ") · түвшин " + r.levelReq() + " · " + r.coins() + " ₮ · " + describe(r.materials()), ok ? NamedTextColor.WHITE : NamedTextColor.GRAY));
        }
    }

    private int count(Player p, String defId) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            ItemInstance i = s == null ? null : items().factory().read(s).orElse(null);
            if (i != null && i.definitionId().equals(defId)) n += s.getAmount();
        }
        return n;
    }

    private void take(Player p, String defId, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int k = 0; k < inv.length && amount > 0; k++) {
            ItemInstance i = inv[k] == null ? null : items().factory().read(inv[k]).orElse(null);
            if (i == null || !i.definitionId().equals(defId)) continue;
            int use = Math.min(amount, inv[k].getAmount());
            amount -= use;
            if (use == inv[k].getAmount()) inv[k] = null;
            else inv[k].setAmount(inv[k].getAmount() - use);
        }
        p.getInventory().setStorageContents(inv);
    }

    private void craft(Player p, String recipeId) {
        Recipe r = catalog().recipe(recipeId).orElse(null);
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (r == null || pr == null) {
            p.sendMessage(Messages.error("Жор олдсонгүй: " + recipeId + " (/item recipes)"));
            return;
        }
        int level = pr.progression().level();
        if (level < r.levelReq()) {
            p.sendMessage(Messages.error("Түвшин " + r.levelReq() + " хэрэгтэй."));
            return;
        }
        if (pr.currency() < r.coins()) {
            p.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + pr.currency() + "/" + r.coins() + " ₮)."));
            return;
        }
        for (Map.Entry<String, Integer> m : r.materials().entrySet()) {
            if (count(p, m.getKey()) < m.getValue()) {
                p.sendMessage(Messages.error("Материал дутуу: " + describe(Map.of(m.getKey(), m.getValue()))));
                return;
            }
        }
        if (p.getInventory().firstEmpty() < 0) {
            p.sendMessage(Messages.error("Цүнх дүүрэн байна."));
            return;
        }
        r.materials().forEach((id, n) -> take(p, id, n));
        pr.addCurrency(-r.coins());
        ItemDefinition def = catalog().require(r.resultId());
        ItemRarity rarity = catalog().band(LootTier.CRAFT).pick(mn.suld.api.loot.Rng.threadLocal(), r.minRarity(), r.maxRarity());
        ItemInstance made = items().generate(def, rarity, level, p.getUniqueId(), "craft:" + r.id());
        items().give(p, List.of(new LootDrop(made, 1)), "craft");
        services.profiles().save(pr);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.craft", def.id() + "-" + made.uuid(), r.id()));
        p.sendMessage(Messages.success("Урлалаа: " + ItemTooltip.name(catalog(), def, made) + " (" + rarity.displayName() + ")"));
    }

    // ------------------------------------------------------------------ /items, /equipment, /loot

    public TabExecutor itemsMenu() {
        return simple((p, a) -> menus.items(p));
    }

    public TabExecutor equipmentMenu() {
        return simple((p, a) -> menus.equipment(p));
    }

    private TabExecutor simple(java.util.function.BiConsumer<Player, String[]> run) {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (s instanceof Player p) run.accept(p, a);
                else s.sendMessage(Messages.error("Зөвхөн тоглогч."));
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                return List.of();
            }
        };
    }

    /** /loot [mob|table]: what a mob or table can drop, with the odds. */
    public TabExecutor loot() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (a.length == 0) {
                    s.sendMessage(Messages.info("Олзны хүснэгт (/loot <нэр>):"));
                    for (LootTable t : catalog().lootTables()) s.sendMessage(Component.text("  " + t.id() + " · " + t.tier(), NamedTextColor.GRAY));
                    return true;
                }
                LootTable t = catalog().lootTable(a[0]).orElse(null);
                if (t == null) {
                    MobDefinition mob = SuldContent.mobFor(a[0].startsWith("mob.") ? a[0] : "mob." + a[0]);
                    t = mob == null ? null : catalog().lootTable(mob.lootTableId()).orElse(null);
                }
                if (t == null) {
                    s.sendMessage(Messages.error("Олдсонгүй: " + a[0]));
                    return true;
                }
                describeTable(s, t);
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                return a.length == 1 ? filter(catalog().lootTables().stream().map(LootTable::id).toList(), a[0]) : List.of();
            }
        };
    }

    private void describeTable(CommandSender s, LootTable t) {
        s.sendMessage(Messages.info(t.id() + " — " + t.tier() + " (" + rarities(t.tier()) + "), " + t.minRolls() + ".." + t.maxRolls() + " удаа"));
        for (LootTable.Entry e : t.guaranteed()) s.sendMessage(Component.text("  ✔ заавал: " + entryName(e) + qty(e), NamedTextColor.GREEN));
        double total = t.nothingWeight();
        for (LootTable.Entry e : t.entries()) total += e.weight();
        for (LootTable.Entry e : t.entries()) {
            s.sendMessage(Component.text(String.format(Locale.ROOT, "  %5.1f%% %s%s", 100.0 * e.weight() / total, entryName(e), qty(e)), NamedTextColor.WHITE));
        }
        for (LootTable.Rare r : t.rare()) {
            s.sendMessage(Component.text(String.format(Locale.ROOT, "  %5.2f%% (тусдаа) %s%s", 100.0 * r.chance(), entryName(r.entry()), qty(r.entry())), NamedTextColor.AQUA));
        }
    }

    private String rarities(LootTier tier) {
        StringBuilder sb = new StringBuilder();
        catalog().band(tier).weights().forEach((r, w) -> sb.append(sb.length() == 0 ? "" : ", ").append(r.displayName()));
        return sb.toString();
    }

    private String entryName(LootTable.Entry e) {
        if (e.itemId() != null) return catalog().item(e.itemId()).map(ItemDefinition::displayName).orElse(e.itemId());
        return "хэрэгсэл (" + e.pool().categories() + ", түвшин ±" + e.levelSpread() + ")";
    }

    private static String qty(LootTable.Entry e) {
        return e.maxQty() > 1 ? " ×" + e.minQty() + (e.maxQty() > e.minQty() ? ".." + e.maxQty() : "") : "";
    }

    // ------------------------------------------------------------------ /itemsadmin

    public TabExecutor admin() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (a.length == 0) {
                    s.sendMessage(Messages.info("/itemsadmin inspect <тоглогч> | give <тоглогч> <id> [rarity] [level] | roll <хүснэгт> [level] [tier] [тоглогч]"
                            + " | validate | reload | sweep <тоглогч> | quarantine [list|restore <№> <тоглогч>]"));
                    return true;
                }
                switch (a[0].toLowerCase(Locale.ROOT)) {
                    case "inspect" -> {
                        Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                        if (t == null) s.sendMessage(Messages.error("Тоглогч онлайн биш."));
                        else adminInspect(s, t);
                    }
                    case "give" -> adminGive(s, a);
                    case "roll" -> adminRoll(s, a);
                    case "validate" -> report(s, items().validate(), "шалгалт");
                    case "reload" -> {
                        List<SkillTreeLoader.Issue> issues = items().reload();
                        report(s, issues, "дахин ачаалал");
                        if (issues.isEmpty()) Bukkit.getOnlinePlayers().forEach(equipment()::dirty);
                    }
                    case "sweep" -> {
                        Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                        if (t == null) s.sendMessage(Messages.error("Тоглогч онлайн биш."));
                        else {
                            java.util.Set<UUID> seen = new java.util.HashSet<>();
                            int n = items().sweep(t, t.getInventory(), "inventory", seen) + items().sweep(t, t.getEnderChest(), "enderchest", seen);
                            s.sendMessage(Messages.success(t.getName() + ": " + n + " эд зүйл хураагдлаа."));
                        }
                    }
                    case "quarantine" -> quarantine(s, a);
                    default -> s.sendMessage(Messages.error("Мэдэгдэхгүй дэд тушаал."));
                }
                return true;
            }

            @Override
            public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
                if (a.length == 1) return filter(List.of("inspect", "give", "roll", "validate", "reload", "sweep", "quarantine"), a[0]);
                String sub = a[0].toLowerCase(Locale.ROOT);
                if (a.length == 2 && List.of("inspect", "give", "sweep").contains(sub)) return null; // player names
                if (a.length == 3 && sub.equals("give")) return filter(catalog().items().stream().map(ItemDefinition::id).toList(), a[2]);
                if (a.length == 4 && sub.equals("give")) return filter(java.util.Arrays.stream(ItemRarity.values()).map(ItemRarity::id).toList(), a[3]);
                if (a.length == 2 && sub.equals("roll")) return filter(catalog().lootTables().stream().map(LootTable::id).toList(), a[1]);
                if (a.length == 4 && sub.equals("roll")) return filter(java.util.Arrays.stream(LootTier.values()).map(Enum::name).toList(), a[3]);
                if (a.length == 2 && sub.equals("quarantine")) return filter(List.of("list", "restore"), a[1]);
                return List.of();
            }
        };
    }

    private void report(CommandSender s, List<SkillTreeLoader.Issue> issues, String what) {
        if (issues.isEmpty()) {
            ItemCatalog c = catalog();
            s.sendMessage(Messages.success("Эд зүйлийн өгөгдөл зөв (" + what + "): " + c.items().size() + " зүйл, " + c.affixes().size() + " шинж, "
                    + c.sets().size() + " иж бүрдэл, " + c.lootTables().size() + " олзны хүснэгт, " + c.recipes().size() + " жор."));
            return;
        }
        s.sendMessage(Messages.error(issues.size() + " алдаа (" + what + "; хуучин каталог хэвээр):"));
        for (int k = 0; k < Math.min(30, issues.size()); k++) s.sendMessage(Component.text("  " + issues.get(k), NamedTextColor.RED));
    }

    private void adminInspect(CommandSender s, Player t) {
        Equipment.Bonus b = equipment().bonus(t);
        s.sendMessage(Messages.info(t.getName() + " — тоноглол (GS " + Equipment.gearScore(equipment().worn(t), b.inactive()) + ")"));
        for (EquipSlot slot : EquipSlot.values()) {
            ItemInstance i = equipment().worn(t).get(slot);
            String line = slot.name() + ": " + (i == null ? "—" : ItemTooltip.name(catalog(), catalog().require(i.definitionId()), i) + " [" + i.rarity().id() + " L" + i.itemLevel()
                    + " " + i.uuid().toString().substring(0, 8) + "]" + (b.inactive().containsKey(slot) ? " ✖ " + b.inactive().get(slot).text() : ""));
            s.sendMessage(Component.text("  " + line, i == null ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE));
        }
        StringBuilder st = new StringBuilder();
        for (Map.Entry<ItemStat, Double> e : b.itemStats().entrySet()) st.append(st.length() == 0 ? "" : ", ").append(e.getKey().format(e.getValue())).append(' ').append(e.getKey().id());
        s.sendMessage(Component.text("  статистик: " + (st.length() == 0 ? "—" : st), NamedTextColor.AQUA));
        s.sendMessage(Component.text("  хохирол (flat): " + b.flatDamage() + " · SÜLD хүч: " + String.format(Locale.ROOT, "%.1f", mn.suld.plugin.combat.CombatListener.attackOf(services, t))
                + " · идэвхгүй чадвар: " + b.procs().size() + " · шид өөрчлөлт: " + b.mods().size(), NamedTextColor.AQUA));
        for (Equipment.SetProgress p : b.sets()) {
            s.sendMessage(Component.text("  иж бүрдэл " + p.set().name() + ": " + p.worn() + "/" + p.set().pieces().size() + ", идэвхтэй урамшуулал " + p.active().size(), NamedTextColor.YELLOW));
        }
        int suld = 0, forged = 0;
        for (ItemStack it : t.getInventory().getContents()) {
            ItemService.Checked c = it == null ? null : items().check(it);
            if (c == null || c.verdict() == ItemService.Verdict.NOT_SULD) continue;
            suld++;
            if (c.verdict() == ItemService.Verdict.FORGED) forged++;
        }
        s.sendMessage(Component.text("  цүнхэнд SÜLD эд зүйл: " + suld + (forged > 0 ? ", хүчингүй: " + forged : ""), NamedTextColor.GRAY));
    }

    private void adminGive(CommandSender s, String[] a) {
        if (a.length < 3) {
            s.sendMessage(Messages.error("/itemsadmin give <тоглогч> <id> [rarity] [level]"));
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        ItemDefinition def = catalog().item(a[2]).orElse(null);
        if (t == null || def == null) {
            s.sendMessage(Messages.error(t == null ? "Тоглогч онлайн биш." : "Ийм эд зүйл алга: " + a[2]));
            return;
        }
        if (def.rarity() == ItemRarity.UNIQUE) {
            s.sendMessage(Messages.error("Цор ганц эд зүйл (дурсгал) нь зөвхөн дурсгалын системээр: /relic give " + def.id() + " " + t.getName()));
            return;
        }
        ItemRarity rarity = a.length > 3 ? ItemRarity.byId(a[3]).orElse(null) : def.rarity();
        if (rarity == null || !def.canRoll(rarity)) {
            s.sendMessage(Messages.error(def.id() + " нь зөвхөн " + def.rarity().id() + ".." + def.maxRarity().id() + " байна."));
            return;
        }
        PlayerProfile pr = services.profiles().cached(t.getUniqueId()).orElse(null);
        int level;
        try {
            level = a.length > 4 ? Integer.parseInt(a[4]) : Math.max(def.levelReq(), pr == null ? 1 : pr.progression().level());
        } catch (NumberFormatException e) {
            s.sendMessage(Messages.error("Түвшин тоо байх ёстой."));
            return;
        }
        ItemInstance i = items().generate(def, rarity, level, t.getUniqueId(), "admin:" + s.getName());
        items().give(t, List.of(new LootDrop(i, def.stackable() ? def.maxStack() : 1)), "admin");
        services.audit().record(AuditEvent.of(s.getName(), "item.admin_give", def.id() + "-" + i.uuid(), t.getName() + " " + rarity.id() + " L" + i.itemLevel()));
        s.sendMessage(Messages.success(t.getName() + " ← " + ItemTooltip.name(catalog(), def, i) + " (" + rarity.id() + ", L" + i.itemLevel() + ", " + i.affixes().size() + " шинж)"));
    }

    private void adminRoll(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(Messages.error("/itemsadmin roll <хүснэгт> [level] [tier] [тоглогч]"));
            return;
        }
        LootTable t = catalog().lootTable(a[1]).orElse(null);
        if (t == null) {
            s.sendMessage(Messages.error("Хүснэгт алга: " + a[1]));
            return;
        }
        int level = 10;
        try {
            if (a.length > 2) level = Integer.parseInt(a[2]);
        } catch (NumberFormatException e) {
            s.sendMessage(Messages.error("Түвшин тоо байх ёстой."));
            return;
        }
        LootTier tier = a.length > 3 ? LootTier.byName(a[3]).orElse(t.tier()) : t.tier();
        Player to = a.length > 4 ? Bukkit.getPlayerExact(a[4]) : null;
        PlayerProfile pr = to == null ? null : services.profiles().cached(to.getUniqueId()).orElse(null);
        LootContext ctx = new LootContext(level, tier, pr == null ? null : pr.playerClass().orElse(null), 0, to == null ? null : to.getUniqueId(), "admin-roll:" + t.id());
        List<LootDrop> drops = items().roll(t, ctx);
        s.sendMessage(Messages.info(t.id() + " (L" + level + ", " + tier + "): " + drops.size() + " зүйл"));
        for (LootDrop d : drops) {
            ItemDefinition def = catalog().require(d.item().definitionId());
            s.sendMessage(Component.text("  " + ItemTooltip.name(catalog(), def, d.item()) + (d.amount() > 1 ? " ×" + d.amount() : "") + " — " + d.item().rarity().id()
                    + " L" + d.item().itemLevel() + " · " + d.item().affixes().size() + " шинж", ItemFactory.rarityColor(d.item().rarity())));
        }
        if (to != null) {
            items().give(to, drops, "admin-roll");
            services.audit().record(AuditEvent.of(s.getName(), "item.admin_roll", t.id(), to.getName() + ": " + drops.size() + " items"));
        }
    }

    private void quarantine(CommandSender s, String[] a) {
        List<Path> files = items().quarantineFiles();
        if (a.length < 2 || a[1].equalsIgnoreCase("list")) {
            s.sendMessage(Messages.info("Хураасан эд зүйл: " + files.size()));
            for (int k = 0; k < Math.min(20, files.size()); k++) s.sendMessage(Component.text("  " + (k + 1) + ". " + files.get(k).getFileName(), NamedTextColor.GRAY));
            return;
        }
        if (a[1].equalsIgnoreCase("restore") && a.length >= 4) {
            Player t = Bukkit.getPlayerExact(a[3]);
            int n;
            try {
                n = Integer.parseInt(a[2]) - 1;
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (t == null || n < 0 || n >= files.size()) {
                s.sendMessage(Messages.error("/itemsadmin quarantine restore <№> <онлайн тоглогч>"));
                return;
            }
            try {
                var doc = mn.suld.api.json.Json.object(mn.suld.api.json.Json.parse(Files.readString(files.get(n))));
                String item = doc.get("item") instanceof String x ? x : "";
                ItemInstance i = items().recover(item).orElse(null);
                if (i == null) {
                    s.sendMessage(Messages.error("Энэ эд зүйл одоо ч хүчингүй (хуурамч эсвэл каталогт байхгүй): сэргээх боломжгүй."));
                    return;
                }
                int amount = doc.get("amount") instanceof Number num ? num.intValue() : 1;
                items().give(t, List.of(new LootDrop(i, amount)), "quarantine-restore");
                Files.move(files.get(n), files.get(n).resolveSibling(files.get(n).getFileName() + ".restored"));
                services.audit().record(AuditEvent.of(s.getName(), "item.quarantine_restore", i.definitionId() + "-" + i.uuid(), t.getName()));
                s.sendMessage(Messages.success("Сэргээлээ → " + t.getName()));
            } catch (Exception e) {
                s.sendMessage(Messages.error("Уншиж чадсангүй: " + e.getMessage()));
            }
            return;
        }
        s.sendMessage(Messages.error("/itemsadmin quarantine [list|restore <№> <тоглогч>]"));
    }

    /** Silence unused-import warnings for types used only in signatures of future GUIs. */
    @SuppressWarnings("unused")
    private static final Class<?>[] USED = {Binding.class, ItemType.class};
}
