package mn.suld.plugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A window in the «Командын Өргөө» look (docs/UI_HUB.md): the HD frame and page art are drawn through the title
 * ({@link HubLayout}); the tabs, cards and footer buttons are invisible items with tooltips over that art.
 */
public final class HubMenu {

    static final TextColor CYAN = TextColor.fromHexString("#56E2EC");
    static final TextColor GOLD = TextColor.fromHexString("#ECBE4C");

    /** One command card: tooltip name, lore and what a click does (null = shown, not clickable). */
    public record Card(Component name, List<Component> lore, Menu.Click click) {
    }

    /** One tab tile: label, hint and the window it opens (null = not available yet). */
    public record Tab(String label, String hint, Consumer<Player> open) {
    }

    /**
     * @param page      a {@code Glyphs.PAGE_*} glyph
     * @param activeTab 0-7 or -1
     * @param dynamic   the row under the title (null for pages whose subtitle is painted)
     * @param cards     up to 8 cards, in reading order (left, right, next row ...)
     * @param ornament  the tooltip/click of the ninth tile (slot 8), or null
     * @param back      the parent window, or null (no «Буцах» button)
     */
    public record Spec(String page, int activeTab, Component dynamic, List<Card> cards, Card ornament,
                       Consumer<Player> back, Consumer<Player> prev, Consumer<Player> next) {
    }

    private HubMenu() {
    }

    public static Menu open(Player p, Spec spec, List<Tab> tabs) {
        Menu m = new Menu(6, HubLayout.title(spec.page(), spec.activeTab(), spec.back() != null, spec.prev() != null,
                spec.next() != null, spec.dynamic()));
        for (int i = 0; i < Math.min(tabs.size(), HubLayout.TABS); i++) {
            Tab t = tabs.get(i);
            boolean active = i == spec.activeTab();
            List<Component> lore = new ArrayList<>();
            lore.add(Menu.line(t.hint()));
            lore.add(Component.empty());
            lore.add(active ? Component.text("« Та энд байна »", NamedTextColor.GRAY)
                    : t.open() == null ? Component.text("Удахгүй", NamedTextColor.GRAY) : Menu.line("« Дарж нээх »"));
            Consumer<Player> open = t.open();
            m.set(HubLayout.tabSlot(i), Menu.invisible(Menu.title(t.label(), active ? CYAN : GOLD), lore),
                    open == null || active ? null : (pl, c) -> open.accept(pl));
        }
        if (spec.ornament() != null) {
            m.set(HubLayout.ORNAMENT_SLOT, Menu.invisible(spec.ornament().name(), spec.ornament().lore()), spec.ornament().click());
        }
        for (int i = 0; i < Math.min(spec.cards().size(), HubLayout.CARDS); i++) {
            Card c = spec.cards().get(i);
            if (c != null) m.area(HubLayout.cardSlots(i), c.name(), c.lore(), c.click());
        }
        if (spec.back() != null) {
            Consumer<Player> back = spec.back();
            m.area(HubLayout.BACK_SLOTS, Menu.title("« Буцах", GOLD), List.of(Menu.line("Өмнөх цонх руу.")), (pl, c) -> back.accept(pl));
        }
        m.area(HubLayout.CLOSE_SLOTS, Menu.title("✕ Хаах", TextColor.fromHexString("#FF6B6B")), List.of(Menu.line("Цонхыг хаана (Esc).")),
                (pl, c) -> pl.closeInventory());
        if (spec.prev() != null) {
            Consumer<Player> prev = spec.prev();
            m.set(HubLayout.PREV_SLOT, Menu.invisible(Menu.title("« Өмнөх", GOLD), List.of()), (pl, c) -> prev.accept(pl));
        }
        if (spec.next() != null) {
            Consumer<Player> next = spec.next();
            m.set(HubLayout.NEXT_SLOT, Menu.invisible(Menu.title("Дараах »", GOLD), List.of()), (pl, c) -> next.accept(pl));
        }
        m.open(p);
        return m;
    }

    /** A card's tooltip: the command in cyan, its line, a click hint. */
    public static Card card(String title, String subtitle, Menu.Click click) {
        return new Card(Menu.title(title, CYAN), List.of(Menu.line(subtitle), Component.empty(), Menu.line("« Дарж нээх »")), click);
    }
}
