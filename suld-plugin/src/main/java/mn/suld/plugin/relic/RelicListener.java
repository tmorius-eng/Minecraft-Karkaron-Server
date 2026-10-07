package mn.suld.plugin.relic;

import mn.suld.api.relic.RelicRecord;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Closes every route by which a relic copy could leave its bearer's inventory: dropping,
 * containers (click, shift-click, hotbar-swap, drag, hoppers), bundles, item frames, armor
 * stands, allays, death drops and item entities. Also guards the shrines and runs discovery.
 * Anything that slips through is still caught by {@link RelicService#validate}.
 */
public final class RelicListener implements Listener {

    private final Plugin plugin;
    private final RelicService relics;
    private final RelicItems items;

    public RelicListener(Plugin plugin, RelicService relics) {
        this.plugin = plugin;
        this.relics = relics;
        this.items = relics.items();
    }

    // ------------------------------------------------------- lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) relics.validate(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        relics.interruptRitual(event.getPlayer(), "гарсан");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        relics.onDeath(event.getEntity(), event.getDrops());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            relics.interruptRitual(p, "гэмтэл авлаа");
        }
    }

    // ------------------------------------------------- never leave the bearer

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (items.isRelic(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.error("Сүлдийг хаях боломжгүй."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offhand = event.getClick() == ClickType.SWAP_OFFHAND ? player.getInventory().getItemInOffHand() : null;
        boolean relicInvolved = items.isRelic(current) || items.isRelic(cursor) || items.isRelic(hotbar) || items.isRelic(offhand);
        if (!relicInvolved) {
            return;
        }
        boolean containerOpen = event.getView().getTopInventory().getType() != InventoryType.CRAFTING;
        boolean bundleInvolved = isBundle(current) || isBundle(cursor);
        if (containerOpen || bundleInvolved) {
            event.setCancelled(true);
            player.sendMessage(Messages.error("Сүлд таны биеэс салахгүй."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!items.isRelic(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean containerOpen = event.getView().getTopInventory().getType() != InventoryType.CRAFTING;
        if (containerOpen && event.getRawSlots().stream().anyMatch(s -> s < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (items.isRelic(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** A container being opened is purged of any relic copy (dupe stashes, old backups...). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.CRAFTING || event.getInventory().getType() == InventoryType.PLAYER) {
            return;
        }
        int removed = relics.purgeContainer(event.getInventory(), event.getInventory().getType() + " opened by "
                + event.getPlayer().getName());
        if (removed > 0) {
            event.getPlayer().sendMessage(Messages.error("Хуурамч сүлд устгагдлаа."));
        }
    }

    /** Relics never exist as item entities. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (items.isRelic(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (items.isRelic(item.getItemStack())) {
            event.setCancelled(true);
            item.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        ItemStack hand = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (items.isRelic(hand)) {
            event.setCancelled(true); // item frames, allays, villagers, mounts...
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (items.isRelic(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ shrines

    @EventHandler(ignoreCancelled = true)
    public void onAltar(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Optional<RelicRecord> shrine = relics.shrineAt(event.getClickedBlock(), true);
        if (shrine.isPresent()) {
            event.setCancelled(true); // also stops lodestone-compass linking
            relics.beginRitual(event.getPlayer(), shrine.get());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (relics.shrineAt(event.getBlock(), false).isPresent()
                && !(event.getPlayer().hasPermission("suld.admin.relic") && event.getPlayer().isSneaking())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.error("Сүмд хүрч болохгүй."));
        }
    }

    /** Nothing may be built into the shrine either (blocks around the altar would lock the relic away for good). */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (relics.shrineAt(event.getBlockPlaced(), false).isPresent()
                && !(event.getPlayer().hasPermission("suld.admin.relic") && event.getPlayer().isSneaking())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.error("Сүмд юу ч барьж болохгүй."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (relics.shrineAt(event.getBlock(), false).isPresent()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        org.bukkit.block.BlockFace f = event.getDirection();
        for (org.bukkit.block.Block b : event.getBlocks()) {
            if (relics.shrineAt(b, false).isPresent() || relics.shrineAt(b.getRelative(f), false).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
        if (relics.shrineAt(event.getBlock().getRelative(f), false).isPresent()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        for (org.bukkit.block.Block b : event.getBlocks()) {
            if (relics.shrineAt(b, false).isPresent() || relics.shrineAt(b.getRelative(event.getDirection()), false).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(org.bukkit.event.block.BlockFromToEvent event) {
        if (relics.shrineAt(event.getToBlock(), false).isPresent()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> relics.shrineAt(b, false).isPresent());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> relics.shrineAt(b, false).isPresent());
    }

    private static boolean isBundle(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta() instanceof BundleMeta;
    }
}
