package mn.suld.plugin.item;

import mn.suld.api.item.ItemInstance;
import mn.suld.plugin.gui.ClassSelectionHolder;
import mn.suld.plugin.gui.Menu;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

/**
 * "Never leaves the owner" (docs/CLASS_GEAR_SYSTEM.md): a soulbound SÜLD item — the class weapon, later the class
 * armour — cannot be put into any container, bundle, hopper, item frame, armour stand or allay, whatever the click
 * path. The player's own inventory and SÜLD's menus (which control their own clicks) stay usable. Dropping and pickup
 * are guarded by {@link EquipmentService}; trade by {@code TradeService}; death keeps them ({@code DeathService});
 * {@code /item destroy} refuses them; anything else that loses one is answered by {@code /classgear recover}.
 */
public final class SoulboundGuard implements Listener {

    private final ItemFactory factory;

    private java.util.function.Consumer<Player> restorer = p -> { };

    public SoulboundGuard(ItemFactory factory) {
        this.factory = factory;
    }

    /** What rebuilds a player's missing class gear (weapon and armour, same identities); wired by the plugin. */
    public void restorer(java.util.function.Consumer<Player> restorer) {
        this.restorer = restorer;
    }

    /** True for a soulbound SÜLD item. */
    public boolean bound(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        ItemInstance i = factory.read(it).orElse(null);
        return i != null && soulbound(i);
    }

    /**
     * Soulbound by the instance flag OR by its definition. Items made before the flag existed (or decoded from the
     * legacy format) carry soulbound=false; the definition still says SOULBOUND, and that is what counts.
     */
    public static boolean soulbound(ItemInstance i) {
        if (i.soulbound() || i.definitionId().startsWith("weapon.class.") || i.definitionId().startsWith("armor.class.")) return true;
        mn.suld.api.item.ItemDefinition d = mn.suld.plugin.content.SuldContent.items().item(i.definitionId()).orElse(null);
        return d != null && d.bindingAt(i.rarity()) == mn.suld.api.item.Binding.SOULBOUND;
    }

    /** A shulker box or bundle with a soulbound item inside (trade and container checks). */
    public boolean holdsBound(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        var meta = it.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
            for (ItemStack in : box.getInventory().getContents()) if (bound(in)) return true;
        }
        if (meta instanceof BundleMeta bundle) {
            for (ItemStack in : bundle.getItems()) if (bound(in)) return true;
        }
        return false;
    }

    static boolean isBundle(ItemStack it) {
        return it != null && isBundle(it.getType());
    }

    /** BUNDLE and all 16 dyed bundles. */
    static boolean isBundle(Material m) {
        return m.name().endsWith("BUNDLE");
    }

    /** The views where moving a bound item is allowed: only the player's own inventory and SÜLD menus. */
    static boolean ownView(InventoryType top, InventoryHolder holder) {
        return ownView(top.name(), holder instanceof Menu || holder instanceof ClassSelectionHolder);
    }

    /** By type name, so the rule is testable without a server (InventoryType needs the registries to load). */
    static boolean ownView(String topType, boolean suldMenu) {
        return suldMenu || topType.equals("CRAFTING") || topType.equals("PLAYER");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack current = e.getCurrentItem(), cursor = e.getCursor();
        ItemStack hotbar = e.getHotbarButton() >= 0 ? p.getInventory().getItem(e.getHotbarButton()) : null;
        ItemStack offhand = e.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : null;
        boolean boundInvolved = bound(current) || bound(cursor) || bound(hotbar) || bound(offhand);
        if (!boundInvolved) return;
        boolean container = !ownView(e.getView().getTopInventory().getType(), e.getView().getTopInventory().getHolder());
        boolean bundle = isBundle(current) || isBundle(cursor);
        // throwing it out of the inventory screen: Q / Ctrl+Q over a slot, or a click outside the window with it on
        // the cursor (also the creative inventory's "throw"), or parking it in the 2x2 grid (dropped if the bag is full)
        boolean throwing = switch (e.getClick()) {
            case DROP, CONTROL_DROP, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT -> true;
            default -> e.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE
                    || e.getAction().name().startsWith("DROP_");
        };
        boolean grid = e.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.CRAFTING && (bound(cursor) || bound(hotbar));
        if (throwing || grid) {
            e.setCancelled(true);
            p.sendMessage(Messages.error("Ангийн эд зүйлийг хаях боломжгүй — энэ бол таны сүнсний зэвсэг."));
            return;
        }
        if (container || bundle) {
            e.setCancelled(true);
            p.sendMessage(Messages.error("Сүнсэнд холбоотой эд зүйл таны биеэс салахгүй."));
        }
    }

    /**
     * Closing the inventory with a bound item on the cursor would drop it: put it back into the bag instead. In
     * creative mode the client owns the inventory screen (a piece dropped on the "destroy" slot never reaches the
     * server), so on close whatever class gear is missing is rebuilt.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) restorer.accept(p);
        ItemStack cursor = p.getItemOnCursor();
        if (!bound(cursor)) return;
        p.setItemOnCursor(null);
        giveBack(p, cursor);
    }

    /** Leaving creative mode: the same reconcile, in case the inventory was never closed. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(org.bukkit.event.player.PlayerGameModeChangeEvent e) {
        if (e.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) restorer.accept(e.getPlayer());
    }

    /**
     * The last line: whatever path made a soulbound item into an item entity on the ground (a drop path nobody
     * thought of, a full inventory, a plugin), it never spawns there; it goes back into its owner's bag. With no owner
     * online it simply does not appear (/classgear recover restores class gear).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent e) {
        ItemStack it = e.getEntity().getItemStack();
        if (!bound(it)) return;
        e.setCancelled(true);
        ItemInstance i = factory.read(it).orElse(null);
        Player owner = i == null || i.boundTo() == null ? null : org.bukkit.Bukkit.getPlayer(i.boundTo());
        if (owner == null) {
            // legacy items without an owner: the nearest player within 4 blocks threw it
            for (Player near : e.getLocation().getNearbyPlayers(4)) {
                owner = near;
                break;
            }
        }
        // already rebuilt (creative reconcile): the dropped copy would be a duplicate of the same identity
        if (owner != null && (i == null || !carries(owner, i.uuid()))) giveBack(owner, it.clone());
    }

    private boolean carries(Player p, java.util.UUID id) {
        for (ItemStack in : p.getInventory().getContents()) {
            if (in != null && factory.read(in).map(x -> x.uuid().equals(id)).orElse(false)) return true;
        }
        return false;
    }

    /** Into the bag; if it is full, the bound item takes a hotbar/bag slot of an unbound item, which drops instead. */
    private void giveBack(Player p, ItemStack it) {
        var left = p.getInventory().addItem(it);
        if (left.isEmpty()) return;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int k = inv.length - 1; k >= 0; k--) {
            if (inv[k] != null && !bound(inv[k])) {
                ItemStack out = inv[k];
                p.getInventory().setItem(k, left.values().iterator().next());
                p.getWorld().dropItemNaturally(p.getLocation(), out);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!bound(e.getOldCursor())) return;
        // the 2x2 crafting grid (raw slots 1-4 of the player's own view) is no place for it either
        if (e.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
                && e.getRawSlots().stream().anyMatch(s -> s >= 1 && s <= 4)) {
            e.setCancelled(true);
            return;
        }
        if (ownView(e.getView().getTopInventory().getType(), e.getView().getTopInventory().getHolder())) return;
        int topSize = e.getView().getTopInventory().getSize();
        if (e.getRawSlots().stream().anyMatch(s -> s < topSize)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (bound(e.getItem()) || holdsBound(e.getItem())) e.setCancelled(true);
    }

    /** A hopper or hopper minecart sucking up a bound item lying on the ground. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (bound(e.getItem().getItemStack()) || holdsBound(e.getItem().getItemStack())) e.setCancelled(true);
    }

    /** Item frames, allays, villagers, mounts: a bound item is never handed to an entity. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) return;
        if ((bound(hand) || holdsBound(hand)) && handsOver(e.getRightClicked().getType())) {
            e.setCancelled(true);
            p.sendMessage(Messages.error("Сүнсэнд холбоотой эд зүйлийг өгөх боломжгүй."));
        }
    }

    private static boolean handsOver(org.bukkit.entity.EntityType t) {
        String n = t.name();
        return n.contains("ITEM_FRAME") || n.equals("ALLAY") || n.equals("ARMOR_STAND") || n.equals("FOX") || n.equals("DOLPHIN");
    }

    /**
     * The 2x2 grid is part of the player's own view, so a bound item may sit there — but it is never an ingredient:
     * the vanilla repair recipe (two damaged tools → a fresh one) would otherwise consume it.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack in : e.getInventory().getMatrix()) {
            if (bound(in) || holdsBound(in)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    /** A decorated pot takes any item on right-click: not a bound one. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPot(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.DECORATED_POT) return;
        if (bound(e.getItem()) || holdsBound(e.getItem())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Messages.error("Сүнсэнд холбоотой эд зүйл таны биеэс салахгүй."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (bound(e.getPlayerItem())) e.setCancelled(true);
    }
}
