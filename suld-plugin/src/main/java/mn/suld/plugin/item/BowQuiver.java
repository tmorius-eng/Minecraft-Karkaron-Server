package mn.suld.plugin.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * SÜLD bows never run out (a new Мэргэн has no arrows and no coins for them): every SÜLD bow carries Infinity, and
 * a player holding one is given one «Мөнхийн сум» when the bag has no arrow at all (the client draws a bow only with
 * an arrow in the bag, Infinity or not). That arrow stays in the bag: it cannot be dropped, stored or lost on death,
 * so it can never be stacked up and sold.
 */
public final class BowQuiver implements Listener {

    private static NamespacedKey key;
    private final Plugin plugin;
    private final ItemFactory items;

    public BowQuiver(Plugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
        key = new NamespacedKey(plugin, "quiver_arrow");
    }

    public void start() {
        // a class change, a recover or a loot pick may put a bow straight into the held slot
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) if (holdsBow(p)) ensure(p);
        }, 40L, 40L);
    }

    /** Infinity on a SÜLD bow, kept out of the tooltip (the SÜLD lore says it). */
    static void infinite(ItemMeta meta) {
        if (!meta.hasEnchant(Enchantment.INFINITY)) meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (!meta.hasEnchantmentGlintOverride()) meta.setEnchantmentGlintOverride(false);
    }

    public static boolean isQuiverArrow(ItemStack s) {
        return s != null && s.getType() == Material.ARROW && key != null && s.hasItemMeta()
                && s.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private boolean holdsBow(Player p) {
        return p.getInventory().getItemInMainHand().getType() == Material.BOW || p.getInventory().getItemInOffHand().getType() == Material.BOW;
    }

    /** Infinity on the held SÜLD bows (ones made before it existed) and one arrow when the bag has none. */
    public void ensure(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack bow : new ItemStack[]{inv.getItemInMainHand(), inv.getItemInOffHand()}) {
            if (bow.getType() != Material.BOW || !items.isSuldItem(bow) || bow.containsEnchantment(Enchantment.INFINITY)) continue;
            ItemMeta meta = bow.getItemMeta();
            infinite(meta);
            bow.setItemMeta(meta);
        }
        if (inv.contains(Material.ARROW) || inv.firstEmpty() < 0) return;
        inv.addItem(arrow());
    }

    /** The class change takes the arrow back with the bow. */
    public static int strip(Player p) {
        int n = 0;
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            if (isQuiverArrow(c[i])) {
                c[i] = null;
                n++;
            }
        }
        if (n > 0) p.getInventory().setContents(c);
        return n;
    }

    private static ItemStack arrow() {
        ItemStack s = new ItemStack(Material.ARROW);
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text("Мөнхийн сум", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        m.lore(java.util.List.of(Component.text("SÜLD нум үүгээр дуусахгүй харвана.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        s.setItemMeta(m);
        return s;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.getInventory().contains(Material.BOW)) ensure(p);
        }, 40L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        ItemStack next = e.getPlayer().getInventory().getItem(e.getNewSlot());
        if (next != null && next.getType() == Material.BOW) ensure(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.BOW) ensure(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isQuiverArrow(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return; // the player's own bag
        ItemStack hotbar = e.getHotbarButton() >= 0 ? e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) : null;
        if (isQuiverArrow(e.getCurrentItem()) || isQuiverArrow(e.getCursor()) || isQuiverArrow(hotbar)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getType() != InventoryType.CRAFTING && isQuiverArrow(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(BowQuiver::isQuiverArrow);
    }
}
