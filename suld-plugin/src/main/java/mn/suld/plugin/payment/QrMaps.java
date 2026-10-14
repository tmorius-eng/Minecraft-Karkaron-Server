package mn.suld.plugin.payment;

import mn.suld.api.payment.qr.QrCode;
import mn.suld.api.payment.qr.QrRaster;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The payment QR in the player's hand (directive Phase 7: «render the Bonum QR onto a Minecraft map»): a filled map
 * whose only renderer draws the code SÜLD encoded itself ({@link QrCode}) at a whole scale with its quiet zone.
 *
 * <ul>
 *   <li>One new map per intent, locked, never reused: a copy made of it can never show another player's code. When the
 *   intent closes the renderer is removed, so the map (and any copy) goes blank.</li>
 *   <li>The item is the player's alone and short-lived: it cannot be dropped, framed, crafted with or put in another
 *   inventory; it is taken away when the intent closes, at quit, at death and at join.</li>
 * </ul>
 */
public final class QrMaps implements Listener {

    private final Plugin plugin;
    private final NamespacedKey key;
    /** Intent → its map (main thread). */
    private final Map<UUID, MapView> views = new ConcurrentHashMap<>();

    public QrMaps(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "pay_qr");
    }

    /** Draws {@code code} on a new map and puts it in {@code p}'s hand (or the first free slot): false when the bag is full. */
    public boolean give(Player p, UUID intent, QrCode code, List<Component> lore, Component name) {
        MapView view = Bukkit.createMap(p.getWorld());
        view.setLocked(true);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        for (MapRenderer r : List.copyOf(view.getRenderers())) view.removeRenderer(r);
        view.addRenderer(new QrRenderer(QrRaster.fit(code, QrRaster.MAP)));
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, intent.toString());
        item.setItemMeta(meta);
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        if (inv.getItemInMainHand().getType().isAir()) slot = inv.getHeldItemSlot();
        for (int i = 0; i < 9 && slot < 0; i++) {
            ItemStack at = inv.getItem(i);
            if (at == null || at.getType().isAir()) slot = i; // a free hotbar slot: the map goes straight to the hand
        }
        if (slot < 0) slot = inv.firstEmpty();
        if (slot < 0) {
            view.getRenderers().forEach(view::removeRenderer);
            return false;
        }
        views.put(intent, view);
        inv.setItem(slot, item);
        if (slot < 9) inv.setHeldItemSlot(slot);
        return true;
    }

    /** Takes the intent's map from {@code p} (if online) and blanks it. */
    public void close(UUID intent, Player p) {
        MapView view = views.remove(intent);
        if (view != null) for (MapRenderer r : List.copyOf(view.getRenderers())) view.removeRenderer(r);
        if (p != null) strip(p, intent.toString());
    }

    /** Every QR map {@code p} holds, taken and blanked. */
    public void closeAll(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            String id = tag(inv.getItem(i));
            if (id != null) {
                inv.setItem(i, null);
                blank(id);
            }
        }
        String cursor = tag(p.getItemOnCursor());
        if (cursor != null) {
            p.setItemOnCursor(null);
            blank(cursor);
        }
    }

    /** At shutdown: every map blanked (renderers are not kept across a restart anyway). */
    public void shutdown() {
        for (UUID id : List.copyOf(views.keySet())) close(id, null);
        for (Player p : Bukkit.getOnlinePlayers()) closeAll(p);
    }

    private void blank(String id) {
        try {
            close(UUID.fromString(id), null);
        } catch (IllegalArgumentException ignored) {
            // not an intent id: nothing to blank
        }
    }

    private void strip(Player p, String id) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (id.equals(tag(inv.getItem(i)))) inv.setItem(i, null);
        if (id.equals(tag(p.getItemOnCursor()))) p.setItemOnCursor(null);
    }

    /** The intent id a QR map carries, or null for anything else. */
    String tag(ItemStack it) {
        if (it == null || it.getType() != Material.FILLED_MAP || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ the map stays the player's, and short-lived

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (tag(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent e) {
        if (tag(e.getPlayer().getInventory().getItem(e.getHand())) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING || e.getView().getTopInventory().getType() == InventoryType.CREATIVE) return;
        if (tag(e.getCurrentItem()) != null || tag(e.getCursor()) != null) e.setCancelled(true); // not into a chest, a table, a frame
        if (e.getHotbarButton() >= 0 && tag(e.getWhoClicked().getInventory().getItem(e.getHotbarButton())) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (tag(e.getOldCursor()) != null && e.getView().getTopInventory().getType() != InventoryType.CRAFTING) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (tag(it) != null) {
                e.getInventory().setResult(null); // no copies, no map extension
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(it -> {
            String id = tag(it);
            if (id != null) blank(id);
            return id != null;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        closeAll(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        closeAll(e.getPlayer()); // a map left in the player file by a crash
    }

    /** Draws the raster once per viewer (it never changes). */
    private static final class QrRenderer extends MapRenderer {
        private final QrRaster raster;
        private final java.util.Set<UUID> drawn = ConcurrentHashMap.newKeySet();

        QrRenderer(QrRaster raster) {
            super(true);
            this.raster = raster;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (!drawn.add(player.getUniqueId())) return;
            for (int y = 0; y < QrRaster.MAP; y++) {
                for (int x = 0; x < QrRaster.MAP; x++) canvas.setPixelColor(x, y, raster.dark(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
    }

    /** A grey hint line for the map's lore. */
    static Component hint(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }
}
