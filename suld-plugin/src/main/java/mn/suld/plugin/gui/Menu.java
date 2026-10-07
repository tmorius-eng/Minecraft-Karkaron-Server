package mn.suld.plugin.gui;

import mn.suld.plugin.ui.StyleFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A SÜLD chest menu. Optionally draws a pixel-art background into the title (font glyph, see gen_ui.py); buttons
 * are items (or invisible items over the background art) with click handlers. Clicks are always cancelled.
 */
public final class Menu implements InventoryHolder {

    public interface Click extends BiConsumer<Player, ClickType> {
    }

    private final Inventory inventory;
    private final Map<Integer, Click> clicks = new HashMap<>();

    /**
     * @param rows       1..6
     * @param title      title text (white, bold)
     * @param background a {@code Glyphs.GUI_*} glyph or null for the vanilla chest look
     */
    public Menu(int rows, String title, String background) {
        Component t;
        if (background != null) {
            // shift to the GUI's left edge, draw the 176 px background, come back to the title position
            t = StyleFormat.join(StyleFormat.shift(-8), StyleFormat.glyph(background), StyleFormat.shift(-169),
                    Component.text(title, NamedTextColor.WHITE, TextDecoration.BOLD));
        } else {
            t = Component.text(title, TextColor.fromHexString("#3A2A10"), TextDecoration.BOLD);
        }
        this.inventory = Bukkit.createInventory(this, rows * 9, t);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Menu set(int slot, ItemStack item, Click click) {
        inventory.setItem(slot, item);
        if (click != null) clicks.put(slot, click);
        else clicks.remove(slot);
        return this;
    }

    /** One invisible button covering several slots (the art of the background shows through). */
    public Menu area(int[] slots, Component name, List<Component> lore, Click click) {
        ItemStack hidden = invisible(name, lore);
        for (int s : slots) set(s, hidden, click);
        return this;
    }

    void click(Player p, int slot, ClickType type) {
        Click c = clicks.get(slot);
        if (c != null) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
            c.accept(p, type);
        }
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    // ------------------------------------------------------------------ item helpers

    private static final NamespacedKey BLANK = new NamespacedKey("suld", "gui_blank");

    public static ItemStack invisible(Component name, List<Component> lore) {
        ItemStack it = item(Material.PAPER, name, lore);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(BLANK);
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack item(Material mat, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (Component c : lore) l.add(c.decoration(TextDecoration.ITALIC, false));
        m.lore(l);
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack glow(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    /** Akuma-style tooltip: bold coloured title, white text, an «Information» block and a click hint. */
    public static List<Component> lore(TextColor accent, List<Component> body, List<Component> info, String hint) {
        List<Component> out = new ArrayList<>(body);
        if (!info.isEmpty()) {
            out.add(Component.empty());
            out.add(Component.text("Мэдээлэл", accent, TextDecoration.BOLD));
            for (Component i : info) out.add(Component.text("┃ ", accent).append(i));
        }
        if (hint != null) {
            out.add(Component.empty());
            out.add(Component.text("« " + hint + " »", NamedTextColor.WHITE, TextDecoration.BOLD));
        }
        return out;
    }

    public static Component title(String text, TextColor color) {
        return Component.text(text, color, TextDecoration.BOLD);
    }

    public static Component line(String text) {
        return Component.text(text, NamedTextColor.WHITE, TextDecoration.BOLD);
    }

    public static Component kv(String key, String value, TextColor color) {
        return Component.text(key + " ", NamedTextColor.WHITE, TextDecoration.BOLD).append(Component.text(value, color, TextDecoration.BOLD));
    }

    /** Slots of a 2-wide, 3-tall card in the 4x2 cosmetics grid (card index 0..7, four per row). */
    public static int[] card4(int index) {
        int col0 = (index % 4) * 2, row0 = (index / 4) * 3;
        int[] out = new int[6];
        int n = 0;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 2; c++) out[n++] = (row0 + r) * 9 + col0 + c;
        return out;
    }

    /** Slots of a 3x3 card in a 6-row menu (card index 0..5, laid out 3 per row). */
    public static int[] card(int index) {
        int col0 = (index % 3) * 3, row0 = (index / 3) * 3;
        int[] out = new int[9];
        int n = 0;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) out[n++] = (row0 + r) * 9 + col0 + c;
        return out;
    }

}
