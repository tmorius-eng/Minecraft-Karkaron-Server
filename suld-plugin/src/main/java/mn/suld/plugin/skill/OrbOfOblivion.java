package mn.suld.plugin.skill;

import mn.suld.plugin.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The Мартагдлын Бөмбөрцөг (Orb of Oblivion): a consumable that refunds the whole skill tree for free and without the
 * respec cooldown. Crafted from a diamond and four ender pearls, or given by an administrator. The item is a quiet
 * vanilla material (it must do nothing when used) wearing a pack model; it is recognised by a persistent tag.
 */
public final class OrbOfOblivion {

    private static final NamespacedKey TAG = new NamespacedKey("suld", "orb_of_oblivion");
    public static final String NAME = "Мартагдлын Бөмбөрцөг";

    private OrbOfOblivion() {
    }

    public static ItemStack create(int amount) {
        ItemStack it = Menu.item(Material.HEART_OF_THE_SEA, Component.text(NAME, TextColor.fromHexString("#C070FF"), TextDecoration.BOLD),
                List.of(Component.text("Чадварын модны бүх оноог буцаана.", NamedTextColor.WHITE, TextDecoration.BOLD),
                        Component.text("Үнэгүй, хүлээлтгүй дахин тохируулна.", NamedTextColor.WHITE, TextDecoration.BOLD),
                        Component.empty(),
                        Component.text("« Баруун товшиж ашиглана »", TextColor.fromHexString("#F2B632"), TextDecoration.BOLD)));
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("suld", "orb_oblivion"));
        m.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        it.setAmount(Math.max(1, Math.min(64, amount)));
        return it;
    }

    public static boolean is(ItemStack it) {
        return it != null && it.getType() == Material.HEART_OF_THE_SEA && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
    }

    public static int count(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (is(it)) n += it.getAmount();
        return n;
    }

    /** Removes one orb from the inventory; false if the player has none. */
    public static boolean consumeOne(Player p) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length; i++) {
            if (!is(inv[i])) continue;
            if (inv[i].getAmount() <= 1) inv[i] = null;
            else inv[i].setAmount(inv[i].getAmount() - 1);
            p.getInventory().setStorageContents(inv);
            return true;
        }
        return false;
    }

    public static void registerRecipe(Plugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "orb_of_oblivion");
        plugin.getServer().removeRecipe(key);
        ShapedRecipe r = new ShapedRecipe(key, create(1));
        r.shape(" E ", "EDE", " E ");
        r.setIngredient('E', Material.ENDER_PEARL);
        r.setIngredient('D', Material.DIAMOND);
        plugin.getServer().addRecipe(r);
    }
}
