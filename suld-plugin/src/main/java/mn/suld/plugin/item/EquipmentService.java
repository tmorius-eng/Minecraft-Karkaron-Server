package mn.suld.plugin.item;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.item.Binding;
import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.Equipment;
import mn.suld.api.item.EquipmentState;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.relic.RelicRecord;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The nine equipment slots of every player, kept in sync with the game: armour, main hand and off hand are read from
 * the player's own inventory, the two accessories from the profile, the relic slot from the relic the player bears.
 * Any change (equip, unequip, scroll, swap, drop, pickup, damage, level, class) marks the player; once per tick the
 * marked players are read again and, if their equipment really changed, the bonus is recomputed and handed to the
 * stat pipeline (the skill build), which re-applies attributes and refreshes the HUD.
 */
public final class EquipmentService implements Listener {

    private final Plugin plugin;
    private final SuldServices services;
    private final ItemFactory factory;

    private record State(String fingerprint, Map<EquipSlot, ItemInstance> worn, Equipment.Bonus bonus) {
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();

    public EquipmentService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.factory = services.items();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 1L, 1L);
        // a safety net for changes no event announces (plugins editing inventories): a cheap fingerprint check
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::check), 40L, 40L);
    }

    // ------------------------------------------------------------------ queries

    /** What the player's equipment adds (empty before the first computation). */
    public Equipment.Bonus bonus(Player p) {
        State s = states.get(p.getUniqueId());
        return s == null ? Equipment.Bonus.NONE : s.bonus();
    }

    public Map<EquipSlot, ItemInstance> worn(Player p) {
        State s = states.get(p.getUniqueId());
        return s == null ? Map.of() : s.worn();
    }

    /** Definition ids of everything worn (set progress in tooltips). */
    public Set<String> wornDefinitions(Player p) {
        Set<String> out = new HashSet<>();
        for (ItemInstance i : worn(p).values()) out.add(i.definitionId());
        return out;
    }

    // ------------------------------------------------------------------ reading the slots

    private Map<EquipSlot, ItemStack> stacks(Player p) {
        PlayerInventory inv = p.getInventory();
        Map<EquipSlot, ItemStack> m = new EnumMap<>(EquipSlot.class);
        m.put(EquipSlot.HEAD, inv.getHelmet());
        m.put(EquipSlot.CHEST, inv.getChestplate());
        m.put(EquipSlot.LEGS, inv.getLeggings());
        m.put(EquipSlot.FEET, inv.getBoots());
        m.put(EquipSlot.MAIN_HAND, inv.getItemInMainHand());
        m.put(EquipSlot.OFF_HAND, inv.getItemInOffHand());
        ItemStack relic = relicStack(p);
        if (relic != null) m.put(EquipSlot.RELIC, relic);
        return m;
    }

    /** The genuine copy of the relic the player bears, if they carry it. */
    private ItemStack relicStack(Player p) {
        if (services.relics() == null) return null;
        RelicRecord r = services.relics().borneBy(p.getUniqueId()).orElse(null);
        if (r == null) return null;
        for (ItemStack s : p.getInventory().getContents()) {
            var f = s == null ? null : services.relics().items().read(s, -1).orElse(null);
            if (f != null && f.key().equals(r.key()) && f.generation() == r.version() && f.itemUuid().equals(r.itemUuid())) return s;
        }
        return null;
    }

    private Map<EquipSlot, ItemInstance> read(Player p, Set<EquipSlot> broken) {
        Map<EquipSlot, ItemInstance> worn = new EnumMap<>(EquipSlot.class);
        for (Map.Entry<EquipSlot, ItemStack> e : stacks(p).entrySet()) {
            ItemStack s = e.getValue();
            if (s == null || s.isEmpty()) continue;
            ItemService.Checked c = services.itemService().check(s);
            if (c.verdict() != ItemService.Verdict.GENUINE && c.verdict() != ItemService.Verdict.MIGRATED) continue;
            worn.put(e.getKey(), c.item());
            if (factory.broken(s, c.item())) broken.add(e.getKey());
        }
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr != null) pr.equipment().slots().forEach(worn::put);
        return worn;
    }

    private Equipment.Wearer wearer(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        return new Equipment.Wearer(p.getUniqueId(), pr == null ? null : pr.playerClass().orElse(null), pr == null ? 1 : pr.progression().level(),
                services.woundFactor.applyAsDouble(p.getUniqueId()));
    }

    private String fingerprint(Player p, Map<EquipSlot, ItemInstance> worn, Set<EquipSlot> broken) {
        StringBuilder sb = new StringBuilder();
        Equipment.Wearer w = wearer(p);
        sb.append(w.clazz()).append('/').append(w.level()).append('/').append(w.boundFactor());
        for (Map.Entry<EquipSlot, ItemInstance> e : worn.entrySet()) {
            ItemInstance i = e.getValue();
            sb.append('|').append(e.getKey().ordinal()).append(':').append(i.uuid()).append(':').append(i.itemLevel())
                    .append(':').append(i.upgradeLevel()).append(':').append(i.boundTo()).append(broken.contains(e.getKey()) ? "!" : "");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ recomputing

    /** Recompute now (the skill tree calls this whenever it rebuilds a player's stats). */
    public Equipment.Bonus compute(Player p) {
        Set<EquipSlot> broken = EnumSet.noneOf(EquipSlot.class);
        Map<EquipSlot, ItemInstance> worn = read(p, broken);
        Equipment.Bonus bonus = Equipment.compute(SuldContent.items(), worn, wearer(p), broken);
        State before = states.put(p.getUniqueId(), new State(fingerprint(p, worn, broken), worn, bonus));
        bindOnEquip(p, worn, bonus);
        tellInactive(p, before == null ? Map.of() : before.bonus().inactive(), bonus.inactive(), worn);
        return bonus;
    }

    /** Mark a player; flushed once per tick (many events in one tick cost one recomputation). */
    public void dirty(Player p) {
        synchronized (dirty) {
            dirty.add(p.getUniqueId());
        }
    }

    private void flush() {
        Set<UUID> now;
        synchronized (dirty) {
            if (dirty.isEmpty()) return;
            now = new LinkedHashSet<>(dirty);
            dirty.clear();
        }
        for (UUID id : now) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) check(p);
        }
    }

    /** Recompute and hand to the stat pipeline only if the equipment really changed. */
    private void check(Player p) {
        Set<EquipSlot> broken = EnumSet.noneOf(EquipSlot.class);
        Map<EquipSlot, ItemInstance> worn = read(p, broken);
        State s = states.get(p.getUniqueId());
        if (s != null && s.fingerprint().equals(fingerprint(p, worn, broken))) return;
        var tree = services.skillTree();
        if (tree != null) tree.equipmentChanged(p); // rebuilds the stats, which calls compute(p)
        else compute(p);
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr != null) services.hud().update(p, pr);
    }

    private void bindOnEquip(Player p, Map<EquipSlot, ItemInstance> worn, Equipment.Bonus bonus) {
        for (Map.Entry<EquipSlot, ItemInstance> e : worn.entrySet()) {
            ItemInstance i = e.getValue();
            if (i.bound() || bonus.inactive().containsKey(e.getKey())) continue;
            ItemDefinition def = SuldContent.items().item(i.definitionId()).orElse(null);
            if (def == null || def.bindingAt(i.rarity()) != Binding.ON_EQUIP) continue;
            bind(p, e.getKey(), i.boundTo(p.getUniqueId()));
            p.sendMessage(Messages.info(def.displayName() + " танд холбогдлоо (Өмсөхөд холбогдоно)."));
        }
    }

    /** Replace the item in a slot with a changed copy (same identity). */
    void bind(Player p, EquipSlot slot, ItemInstance changed) {
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.bind", changed.definitionId() + "-" + changed.uuid(), slot.name()));
        if (EquipmentState.stored(slot)) {
            PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
            if (pr != null) {
                pr.equipment(pr.equipment().with(slot, changed));
                services.profiles().save(pr);
            }
            return;
        }
        ItemStack s = stacks(p).get(slot);
        if (s == null) return;
        factory.rewrite(s, changed, services.itemService().viewer(p));
        if (slot.vanilla()) setVanilla(p.getInventory(), slot, s);
    }

    private void tellInactive(Player p, Map<EquipSlot, Equipment.Inactive> before, Map<EquipSlot, Equipment.Inactive> now,
                              Map<EquipSlot, ItemInstance> worn) {
        for (Map.Entry<EquipSlot, Equipment.Inactive> e : now.entrySet()) {
            if (Objects.equals(before.get(e.getKey()), e.getValue())) continue;
            if (e.getValue() == Equipment.Inactive.WRONG_SLOT && e.getKey() == EquipSlot.MAIN_HAND) continue; // holding a ring is fine
            if (e.getValue() == Equipment.Inactive.WRONG_SLOT && e.getKey() == EquipSlot.OFF_HAND) continue;
            ItemInstance i = worn.get(e.getKey());
            String name = i == null ? "" : SuldContent.items().item(i.definitionId()).map(ItemDefinition::displayName).orElse(i.definitionId());
            p.sendActionBar(net.kyori.adventure.text.Component.text("✖ " + name + ": " + e.getValue().text() + " — нөлөөгүй", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    // ------------------------------------------------------------------ accessories

    /** Move the item at an inventory slot into an accessory slot (the previous accessory goes back to the inventory). */
    public String equipAccessory(Player p, int invSlot, EquipSlot target) {
        if (!EquipmentState.stored(target)) return "Энэ байрлалд тоглоомын өөрийн нүдийг ашиглана.";
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return "Профайл ачаалагдаагүй.";
        ItemStack s = p.getInventory().getItem(invSlot);
        ItemService.Checked c = s == null ? null : services.itemService().check(s);
        if (c == null || c.item() == null || c.verdict() == ItemService.Verdict.FORGED || c.verdict() == ItemService.Verdict.NOT_SULD) return "SÜLD эд зүйл биш.";
        ItemDefinition def = SuldContent.items().require(c.item().definitionId());
        var why = Equipment.check(SuldContent.items(), target, c.item(), wearer(p), false);
        if (why.isPresent()) return def.displayName() + ": " + why.get().text();
        ItemInstance old = pr.equipment().get(target).orElse(null);
        p.getInventory().setItem(invSlot, old == null ? null : services.itemService().stack(old, p, 1));
        pr.equipment(pr.equipment().with(target, c.item()));
        services.profiles().save(pr);
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.equip", c.item().definitionId() + "-" + c.item().uuid(), target.name()));
        dirty(p);
        return null;
    }

    /**
     * Equip the item at an inventory slot: armour into its armour slot, an off-hand item into the off hand, jewellery
     * into an accessory slot ({@code requested}, else the first free one). Whatever was there goes back to that
     * inventory slot. Returns the slot used, or throws {@link IllegalStateException} with the reason it was refused.
     */
    public EquipSlot equip(Player p, int invSlot, EquipSlot requested) {
        PlayerInventory inv = p.getInventory();
        ItemStack s = inv.getItem(invSlot);
        ItemService.Checked c = s == null ? null : services.itemService().check(s);
        if (c == null || c.item() == null || c.verdict() == ItemService.Verdict.NOT_SULD) throw new IllegalStateException("SÜLD эд зүйл биш.");
        if (c.verdict() == ItemService.Verdict.FORGED) throw new IllegalStateException("Энэ эд зүйл хүчингүй: " + String.join("; ", c.problems()));
        ItemDefinition def = SuldContent.items().require(c.item().definitionId());
        switch (def.type().category()) {
            case JEWELRY -> {
                EquipSlot slot = requested;
                if (slot == null) {
                    PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
                    slot = pr != null && pr.equipment().get(EquipSlot.ACCESSORY_1).isEmpty() ? EquipSlot.ACCESSORY_1 : EquipSlot.ACCESSORY_2;
                }
                String err = equipAccessory(p, invSlot, slot);
                if (err != null) throw new IllegalStateException(err);
                return slot;
            }
            case ARMOR -> {
                EquipSlot slot = def.type().slots().iterator().next();
                ItemStack old = stacks(p).get(slot);
                setVanilla(inv, slot, s);
                inv.setItem(invSlot, old == null || old.isEmpty() ? null : old);
                dirty(p);
                return slot;
            }
            case OFFHAND -> {
                ItemStack old = inv.getItemInOffHand();
                inv.setItemInOffHand(s);
                inv.setItem(invSlot, old.isEmpty() ? null : old);
                dirty(p);
                return EquipSlot.OFF_HAND;
            }
            case WEAPON -> throw new IllegalStateException("Зэвсэг гартаа барихад идэвхждэг.");
            default -> throw new IllegalStateException("Энэ зүйлийг өмсдөггүй.");
        }
    }

    /** Take off whatever is in a slot into the inventory; throws with the reason when refused. */
    public void unequip(Player p, EquipSlot slot) {
        if (EquipmentState.stored(slot)) {
            String err = unequipAccessory(p, slot);
            if (err != null) throw new IllegalStateException(err);
            return;
        }
        if (slot == EquipSlot.MAIN_HAND || slot == EquipSlot.RELIC) throw new IllegalStateException("Энэ нүдийг цүнхнээсээ сольдог.");
        PlayerInventory inv = p.getInventory();
        ItemStack s = stacks(p).get(slot);
        if (s == null || s.isEmpty()) throw new IllegalStateException("Энэ нүд хоосон.");
        if (inv.firstEmpty() < 0) throw new IllegalStateException("Цүнх дүүрэн байна.");
        setVanilla(inv, slot, null);
        inv.addItem(s);
        dirty(p);
    }

    private static void setVanilla(PlayerInventory inv, EquipSlot slot, ItemStack s) {
        switch (slot) {
            case HEAD -> inv.setHelmet(s);
            case CHEST -> inv.setChestplate(s);
            case LEGS -> inv.setLeggings(s);
            case FEET -> inv.setBoots(s);
            case MAIN_HAND -> inv.setItemInMainHand(s);
            case OFF_HAND -> inv.setItemInOffHand(s);
            default -> throw new IllegalArgumentException(slot + " is not a vanilla slot");
        }
    }

    /** Take an accessory off into the inventory (refused when the inventory is full). */
    public String unequipAccessory(Player p, EquipSlot slot) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null) return "Профайл ачаалагдаагүй.";
        ItemInstance i = pr.equipment().get(slot).orElse(null);
        if (i == null) return "Энэ нүд хоосон.";
        if (p.getInventory().firstEmpty() < 0) return "Цүнх дүүрэн байна.";
        pr.equipment(pr.equipment().with(slot, null));
        services.profiles().save(pr);
        p.getInventory().addItem(services.itemService().stack(i, p, 1));
        services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.unequip", i.definitionId() + "-" + i.uuid(), slot.name()));
        dirty(p);
        return null;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            services.itemService().sweepPlayer(p);
            dirty(p);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
        synchronized (dirty) {
            dirty.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmour(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent e) {
        dirty(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        dirty(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        dirty(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) dirty(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) dirty(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) dirty(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        dirty(e.getPlayer());
    }

    /** Bound items stay with their owner; soulbound items are not dropped at all. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        ItemInstance i = factory.read(e.getItemDrop().getItemStack()).orElse(null);
        if (i != null && i.soulbound()) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Messages.error("Сүнсэнд холбоотой эд зүйлийг хаях, устгах боломжгүй — энэ бол таны ангийн эд."));
            return;
        }
        dirty(e.getPlayer());
    }

    /** Nobody picks up an item bound to someone else; items that bind on pickup bind to whoever takes them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack s = e.getItem().getItemStack();
        ItemInstance i = factory.read(s).orElse(null);
        if (i == null) return;
        if (i.boundTo() != null && !i.boundTo().equals(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        ItemDefinition def = SuldContent.items().item(i.definitionId()).orElse(null);
        if (def != null && !i.bound() && def.bindingAt(i.rarity()) == Binding.ON_PICKUP) {
            ItemStack copy = s.clone();
            factory.rewrite(copy, i.boundTo(p.getUniqueId()), services.itemService().viewer(p));
            e.getItem().setItemStack(copy);
            p.sendMessage(Messages.info(def.displayName() + " танд холбогдлоо (Авахад холбогдоно)."));
            services.audit().record(AuditEvent.of(p.getUniqueId().toString(), "item.bind", i.definitionId() + "-" + i.uuid(), "pickup"));
        }
        dirty(p);
    }

    /**
     * SÜLD gear never breaks into nothing: wear stops at the definition's maximum and the item is then broken (gives no
     * stats) until a smith repairs it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent e) {
        ItemStack s = e.getItem();
        ItemInstance i = factory.read(s).orElse(null);
        if (i == null) return;
        int[] d = factory.durability(s, i);
        if (d[1] <= 0) {
            e.setCancelled(true); // never wears
            return;
        }
        if (d[0] <= 0) {
            e.setCancelled(true);
            return;
        }
        boolean breaks = e.getDamage() >= d[0];
        if (breaks) {
            e.setDamage(d[0]);
            ItemDefinition def = SuldContent.items().item(i.definitionId()).orElse(null);
            e.getPlayer().sendMessage(Messages.error((def == null ? "Эд зүйл" : def.displayName()) + " эвдэрлээ — дархнаар засуулна уу."));
        }
        // the lore line is refreshed in 10% steps (not on every hit); the equipment only changes when the item breaks
        int left = Math.max(0, d[0] - e.getDamage());
        boolean step = (left * 10) / d[1] != (d[0] * 10) / d[1];
        if (!breaks && !step) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            factory.rerender(e.getItem(), services.itemService().viewer(p));
            if (breaks) dirty(p);
        });
    }
}
