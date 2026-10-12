package mn.suld.plugin.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * SÜLD bows need no arrows: the Мэргэн class bow is never drawn, a right click shoots
 * ({@code SkillService.onBowUse}), so the L-R-L combos are never eaten by a draw. Every SÜLD bow still carries
 * Infinity (a bow from loot shot with bought arrows keeps them). This class also takes back the «Мөнхийн сум» an
 * earlier build put in the bag: an arrow in the bag makes the client draw the bow, and a drawing client sends no left
 * clicks.
 */
public final class BowQuiver implements Listener {

    private static NamespacedKey key;
    private static volatile BowQuiver instance;
    private final Plugin plugin;
    private final ItemFactory items;

    public BowQuiver(Plugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
        key = new NamespacedKey(plugin, "quiver_arrow");
        instance = this;
    }

    /** The class pick, a class change or a recover: Infinity on the new bow. */
    public static void give(Player p) {
        BowQuiver q = instance;
        if (q != null) q.ensure(p);
    }

    public void start() {
        // any way a bow reaches the bag (a loot pick, a trade, a mailbox) is covered within 2 s
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getInventory().contains(Material.BOW) || p.getInventory().contains(Material.ARROW)) ensure(p);
        }, 40L, 40L);
        plugin.getLogger().info("BowQuiver: SÜLD bows have Infinity; the Мэргэн class bow shoots on a right click, no arrows needed.");
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

    /** Infinity on every SÜLD bow in the bag (ones made before it existed); the old «Мөнхийн сум» goes. */
    public void ensure(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < c.length; i++) {
            ItemStack s = c[i];
            if (s == null) continue;
            if (isQuiverArrow(s)) {
                c[i] = null;
                changed = true;
            } else if (s.getType() == Material.BOW && items.isSuldItem(s) && !s.containsEnchantment(Enchantment.INFINITY)) {
                ItemMeta meta = s.getItemMeta();
                infinite(meta);
                s.setItemMeta(meta);
                changed = true;
            }
        }
        if (changed) p.getInventory().setContents(c);
    }

    /** The class change takes any old «Мөнхийн сум» with the bow. */
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

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) ensure(p);
        }, 40L);
    }
}
