package mn.suld.plugin.clazz;

import mn.suld.plugin.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * «Хувь Төөргийн Тамга» (docs/CLASS_CHANGE.md): one seal pays a class change instead of the coins and lets it skip
 * the 6-hour wait. Rare (story and Хуралдай rewards, or an administrator); a quiet vanilla item recognised by a
 * persistent tag, like the Orb of Oblivion.
 */
public final class ClassSeal {

    private static final NamespacedKey TAG = new NamespacedKey("suld", "class_change_seal");
    public static final String ID = "item.class_change_seal";
    public static final String NAME = "Хувь Төөргийн Тамга";

    private ClassSeal() {
    }

    public static ItemStack create(int amount) {
        ItemStack it = Menu.item(Material.ECHO_SHARD, Component.text(NAME, TextColor.fromHexString("#E8B84A"), TextDecoration.BOLD),
                List.of(Component.text("Анги солиход зоосны оронд нэгийг зарцуулна.", NamedTextColor.WHITE),
                        Component.text("6 цагийн хүлээлтийг алгасна.", NamedTextColor.WHITE),
                        Component.empty(),
                        Component.text("« Хархорумд Ангийн Бөөд очиж ашиглана »", TextColor.fromHexString("#F2B632"), TextDecoration.BOLD)));
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        it.setAmount(Math.max(1, Math.min(64, amount)));
        return it;
    }

    public static boolean is(ItemStack it) {
        return it != null && it.getType() == Material.ECHO_SHARD && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
    }

    public static int count(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (is(it)) n += it.getAmount();
        return n;
    }

    /** Removes one seal from the bag; false if the player has none. */
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
}
