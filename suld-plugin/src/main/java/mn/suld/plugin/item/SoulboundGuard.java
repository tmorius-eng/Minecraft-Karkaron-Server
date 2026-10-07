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

    public SoulboundGuard(ItemFactory factory) {
        this.factory = factory;
    }

    /** True for a soulbound SÜLD item. */
    public boolean bound(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        ItemInstance i = factory.read(it).orElse(null);
        return i != null && i.soulbound();
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
        if (container || bundle) {
            e.setCancelled(true);
            p.sendMessage(Messages.error("Сүнсэнд холбоотой эд зүйл таны биеэс салахгүй."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!bound(e.getOldCursor())) return;
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
