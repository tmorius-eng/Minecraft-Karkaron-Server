package mn.suld.plugin.gui;

import mn.suld.plugin.ui.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The hub grid partitions the chest, and the title's shifts land every glyph where the art expects it. */
class HubLayoutTest {

    @Test
    void everySlotBelongsToExactlyOneRegion() {
        int[] owner = new int[54];
        for (int t = 0; t < HubLayout.TABS; t++) owner[HubLayout.tabSlot(t)]++;
        owner[HubLayout.ORNAMENT_SLOT]++;
        for (int c = 0; c < HubLayout.CARDS; c++) for (int s : HubLayout.cardSlots(c)) owner[s]++;
        for (int row = 1; row <= 4; row++) owner[row * 9 + HubLayout.GUTTER_COL]++;
        for (int s : HubLayout.BACK_SLOTS) owner[s]++;
        for (int s : HubLayout.CLOSE_SLOTS) owner[s]++;
        owner[HubLayout.PREV_SLOT]++;
        owner[HubLayout.NEXT_SLOT]++;
        owner[47]++;
        owner[51]++;
        for (int s = 0; s < 54; s++) assertEquals(1, owner[s], "slot " + s);
        for (int c = 0; c < HubLayout.CARDS; c++) for (int s : HubLayout.cardSlots(c)) assertEquals(c, HubLayout.cardOf(s));
        assertEquals(-1, HubLayout.cardOf(0));
        assertEquals(-1, HubLayout.cardOf(13));
        assertEquals(-1, HubLayout.cardOf(49));
    }

    /** Walks the title the way the client advances: each glyph's x must be the x the kit painted it at. */
    @Test
    void titleGlyphsLandOnTheArt() throws Exception {
        Map<String, Integer> advance = new HashMap<>();
        for (int n : new int[]{1, 2, 4, 8, 16, 32, 64, 128}) {
            advance.put((String) Glyphs.class.getField("NEG_" + n).get(null), -n);
            advance.put((String) Glyphs.class.getField("POS_" + n).get(null), n);
        }
        advance.put(Glyphs.HUB_FRAME, HubLayout.W + 1);
        advance.put(Glyphs.PAGE_HUB, HubLayout.W + 1);
        advance.put(Glyphs.PAGE_PROFILE, HubLayout.W + 1);
        advance.put(Glyphs.HUB_TAB_ON, HubLayout.TAB_ON_W + 1);
        advance.put(Glyphs.HUB_BACK, HubLayout.BACK_W + 1);
        advance.put(Glyphs.HUB_PREV, HubLayout.ARROW_W + 1);
        advance.put(Glyphs.HUB_NEXT, HubLayout.ARROW_W + 1);
        for (String g : new String[]{Glyphs.HUB_CARD_ON_R1, Glyphs.HUB_CARD_ON_R2, Glyphs.HUB_CARD_ON_R3, Glyphs.HUB_CARD_ON_R4}) {
            advance.put(g, HubLayout.CARD_ON_W + 1);
        }
        Map<String, String> names = new HashMap<>();
        for (Field f : Glyphs.class.getFields()) if (f.getType() == String.class) names.put((String) f.get(null), f.getName());

        Component dyn = Component.text("Lv 12 · 340 зоос", NamedTextColor.GRAY);
        Component title = HubLayout.title(Glyphs.PAGE_PROFILE, 3, true, true, true, dyn);
        List<String> drawn = new ArrayList<>();
        int[] cursor = {HubLayout.TITLE_X};
        walk(title, advance, names, drawn, cursor);
        assertEquals(List.of("HUB_FRAME@" + HubLayout.X0, "PAGE_PROFILE@" + HubLayout.X0, "HUB_TAB_ON@" + (8 + 18 * 3 - 1),
                "HUB_BACK@" + HubLayout.BACK_X, "HUB_PREV@" + HubLayout.PREV_X, "HUB_NEXT@" + HubLayout.NEXT_X,
                "text@" + (HubLayout.PANEL_CENTRE - HubLayout.textWidth("Lv 12 · 340 зоос") / 2)), drawn);

        drawn.clear();
        cursor[0] = HubLayout.TITLE_X;
        walk(HubLayout.title(Glyphs.PAGE_HUB, -1, false, false, false, null), advance, names, drawn, cursor);
        assertEquals(List.of("HUB_FRAME@" + HubLayout.X0, "PAGE_HUB@" + HubLayout.X0), drawn);

        drawn.clear();
        cursor[0] = HubLayout.TITLE_X;
        walk(HubLayout.title(Glyphs.PAGE_HUB, -1, 0b1001, false, false, false, null), advance, names, drawn, cursor);
        assertEquals(List.of("HUB_FRAME@" + HubLayout.X0, "PAGE_HUB@" + HubLayout.X0, "HUB_CARD_ON_R1@7", "HUB_CARD_ON_R2@97"), drawn);
    }

    private static void walk(Component c, Map<String, Integer> advance, Map<String, String> names, List<String> drawn, int[] cursor) {
        if (c instanceof TextComponent t && !t.content().isEmpty()) {
            if (Glyphs.FONT.equals(t.font())) {
                for (char ch : t.content().toCharArray()) {
                    String g = String.valueOf(ch);
                    Integer adv = advance.get(g);
                    assertNotNull(adv, "unknown glyph " + names.getOrDefault(g, Integer.toHexString(ch)));
                    if (!names.get(g).startsWith("NEG_") && !names.get(g).startsWith("POS_")) drawn.add(names.get(g) + "@" + cursor[0]);
                    cursor[0] += adv;
                }
            } else {
                drawn.add("text@" + cursor[0]);
            }
        }
        for (Component child : c.children()) walk(child, advance, names, drawn, cursor);
    }

    @Test
    void textWidthFollowsTheHdFontsGlyphClasses() {
        assertEquals((60 + 52 + 28 + 60 + 60 + 5) / 10, HubLayout.textWidth("Lv 12"));
        assertEquals(3, HubLayout.textWidth("·"));
        assertTrue(HubLayout.textWidth("ММММ") > HubLayout.textWidth("iiii"));
    }
}
