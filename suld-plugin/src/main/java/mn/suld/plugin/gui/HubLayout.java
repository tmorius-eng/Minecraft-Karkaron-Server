package mn.suld.plugin.gui;

import mn.suld.plugin.ui.Glyphs;
import mn.suld.plugin.ui.StyleFormat;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The «Командын Өргөө» window grid and title (docs/UI_HUB.md; the art is tools/pack/hub_kit.py, whose constants
 * these mirror). A hub window is a 9 x 6 chest whose title stacks glyphs of the {@code suld:ui} font with
 * negative-space shifts: the frame, the page, the active tab's glow, the footer buttons, then the dynamic text.
 *
 * <p>Chest GUI px, the chest's top-left at (0, 0): the title text starts at x 8; a glyph {@code w} px wide
 * advances {@code w + 1}. Rows: 0 = tabs (slots 0-7, slot 8 an ornament), 1-4 = two cards per row (columns 0-3 and
 * 5-8, column 4 the gutter), 5 = footer («Буцах» 45-46, «Хаах» 48-50, paging 52-53). No Bukkit: unit-tested.
 */
public final class HubLayout {

    /** The canvas of HUB_FRAME / PAGE_*: its left edge and width (hub_kit X0, W). */
    public static final int X0 = -18;
    public static final int W = 212;
    static final int TITLE_X = 8;
    static final int SLOT_X = 8;
    static final int ROW = 18;
    static final int TAB_ON_W = 19;      // the glow tile, drawn one px left of the tab's slot
    static final int BACK_X = 8;
    static final int BACK_W = 38;
    static final int PREV_X = 134;
    static final int NEXT_X = 154;
    static final int ARROW_W = 16;
    static final int PANEL_CENTRE = 88;

    public static final int TABS = 8;
    public static final int CARDS = 8;
    public static final int ORNAMENT_SLOT = 8;
    public static final int GUTTER_COL = 4;
    public static final int[] BACK_SLOTS = {45, 46};
    public static final int[] CLOSE_SLOTS = {48, 49, 50};
    public static final int PREV_SLOT = 52;
    public static final int NEXT_SLOT = 53;

    private HubLayout() {
    }

    /** Slot of tab {@code tab} (0-7). */
    public static int tabSlot(int tab) {
        if (tab < 0 || tab >= TABS) throw new IllegalArgumentException("tab " + tab);
        return tab;
    }

    /** The four slots of card {@code card} (0-7): rows 1-4, left cards in columns 0-3, right cards in 5-8. */
    public static int[] cardSlots(int card) {
        if (card < 0 || card >= CARDS) throw new IllegalArgumentException("card " + card);
        int row = 1 + card / 2;
        int col0 = card % 2 == 0 ? 0 : 5;
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) out[i] = row * 9 + col0 + i;
        return out;
    }

    /** The card a slot belongs to, or -1 (tabs, gutter, footer). */
    public static int cardOf(int slot) {
        int row = slot / 9, col = slot % 9;
        if (row < 1 || row > 4 || col == GUTTER_COL) return -1;
        return (row - 1) * 2 + (col < GUTTER_COL ? 0 : 1);
    }

    /**
     * The window title: frame + page glyphs, the active tab's glow, the footer buttons, then {@code dynamic} centred
     * on the panel (its width estimated with {@link #textWidth}; null or empty = no dynamic text).
     *
     * @param page      a {@code Glyphs.PAGE_*} glyph
     * @param activeTab 0-7, or -1 for none
     */
    public static Component title(String page, int activeTab, boolean back, boolean prev, boolean next, Component dynamic) {
        List<Component> parts = new ArrayList<>();
        int cursor = TITLE_X;
        cursor = draw(parts, cursor, X0, Glyphs.HUB_FRAME, W);
        cursor = draw(parts, cursor, X0, page, W);
        if (activeTab >= 0) cursor = draw(parts, cursor, SLOT_X + ROW * tabSlot(activeTab) - 1, Glyphs.HUB_TAB_ON, TAB_ON_W);
        if (back) cursor = draw(parts, cursor, BACK_X, Glyphs.HUB_BACK, BACK_W);
        if (prev) cursor = draw(parts, cursor, PREV_X, Glyphs.HUB_PREV, ARROW_W);
        if (next) cursor = draw(parts, cursor, NEXT_X, Glyphs.HUB_NEXT, ARROW_W);
        if (dynamic != null) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(dynamic);
            if (!plain.isEmpty()) {
                int x = PANEL_CENTRE - textWidth(plain) / 2;
                parts.add(StyleFormat.shift(x - cursor));
                parts.add(dynamic);
                cursor = x;
            }
        }
        return StyleFormat.join(parts.toArray(new Component[0]));
    }

    private static int draw(List<Component> parts, int cursor, int x, String glyph, int width) {
        if (x != cursor) parts.add(StyleFormat.shift(x - cursor));
        parts.add(StyleFormat.glyph(glyph));
        return x + width + 1;
    }

    /** Width of {@code text} in the client's default font, close enough to centre a short row (px). */
    public static int textWidth(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += switch (c) {
                case ' ' -> 4;
                case 'i', '!', '.', ',', ':', ';', '\'', '|', '·', 'ı' -> 2;
                case 'l', 'І', 'ї' -> 3;
                case 'I', 't', '(', ')', '[', ']', '{', '}', '<', '>' -> 4;
                case 'f', 'k' -> 5;
                case 'м', 'ш', 'щ', 'ы', 'ю', 'ж', 'ф', 'М', 'Ш', 'Щ', 'Ы', 'Ю', 'Ж', 'Ф', 'W', 'M', 'm', 'w', '@', '~' -> 7;
                default -> 6;
            };
        }
        return w;
    }
}
