package mn.suld.plugin.gui;

import mn.suld.plugin.SuldServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Menu clicks (always cancelled), the «Сүлд Цэс» hotbar item (slot 9: right-click opens the main menu; it cannot
 * be dropped or moved) and the welcome screen shown on join.
 */
public final class MenuListener implements Listener {

    private static final int MENU_SLOT = 8;

    private final Plugin plugin;
    private final SuldServices services;
    private final Menus menus;
    private final NamespacedKey key;

    public MenuListener(Plugin plugin, SuldServices services, Menus menus) {
        this.plugin = plugin;
        this.services = services;
        this.menus = menus;
        this.key = new NamespacedKey(plugin, "menu_item");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu menu) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p && e.getClickedInventory() == e.getView().getTopInventory()) {
                menu.click(p, e.getSlot(), e.getClick());
            }
            return;
        }
        if (isMenuItem(e.getCurrentItem()) || isMenuItem(e.getCursor())
                || e.getHotbarButton() == MENU_SLOT && e.getClickedInventory() != null) {
            e.setCancelled(true);
            if (isMenuItem(e.getCurrentItem()) && e.getWhoClicked() instanceof Player p) {
                Bukkit.getScheduler().runTask(plugin, () -> menus.main(p));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu || isMenuItem(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isMenuItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isMenuItem(e.getOffHandItem()) || isMenuItem(e.getMainHandItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isMenuItem(e.getItem())) return;
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            menus.main(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        giveMenuItem(p);
        // first join: the class choice opens instead (PlayerLifecycleListener); afterwards a welcome screen
        boolean hasClass = services.profiles().cached(p.getUniqueId()).map(pr -> pr.hasSelectedClass()).orElse(false);
        if (hasClass && plugin.getConfig().getBoolean("ui.welcome-screen", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)) menus.welcome(p);
            }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isMenuItem);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> giveMenuItem(e.getPlayer()));
    }

    public void giveMenuItem(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isMenuItem(it)) return;
        }
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text("Сүлд Цэс", TextColor.fromHexString("#FFD24A"), TextDecoration.BOLD)
                .append(Component.text(" (баруун товч)", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Дүр, анги, эрэл, цол, дэлгүүр, заавар.", NamedTextColor.WHITE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(m);
        ItemStack current = p.getInventory().getItem(MENU_SLOT);
        if (current != null && !current.getType().isAir()) {
            if (!p.getInventory().addItem(current).isEmpty()) return; // inventory full: leave the slot alone
        }
        p.getInventory().setItem(MENU_SLOT, item);
    }

    public boolean isMenuItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
