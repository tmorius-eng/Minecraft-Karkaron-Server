package mn.suld.plugin.hud;

import mn.suld.api.clazz.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The HUD composition, decoded back glyph by glyph with the same advance table the client uses: where every glyph
 * lands (x), what it is, and its tint. Also writes the sample frames to build/hud-samples for tools/pack/hud_render.py.
 */
class HudComposerTest {

    /** One drawn glyph: its name (G field, "FILL_HP[3]" for pieces, "ROW_A:'7'" for set characters), x, tint. */
    record Drawn(String name, int x, int advance, TextColor color) {
    }

    record Decoded(List<Drawn> glyphs, int total) {
        List<Drawn> named(String prefix) {
            return glyphs.stream().filter(d -> d.name.startsWith(prefix)).toList();
        }

        /** Characters of a set in drawing order, e.g. text("ROW_A") = "184/200…". */
        String text(String set) {
            StringBuilder sb = new StringBuilder();
            for (Drawn d : glyphs) if (d.name.startsWith(set + ":")) sb.append(d.name.charAt(set.length() + 2));
            return sb.toString();
        }

        int fillWidth(String kind) {
            int w = 0;
            for (Drawn d : named("FILL_" + kind + "[")) w += d.advance - 1;
            return w;
        }
    }

    static final Map<Character, String> NAMES = new HashMap<>();
    static final Map<Character, Integer> ADV = new HashMap<>();

    static {
        try {
            for (Field f : HudGlyphs.class.getFields()) {
                Object v = f.get(null);
                if (v instanceof HudGlyphs.G g) {
                    NAMES.put(g.ch().charAt(0), f.getName());
                    ADV.put(g.ch().charAt(0), g.advance());
                } else if (v instanceof HudGlyphs.G[] arr) {
                    for (int i = 0; i < arr.length; i++) {
                        NAMES.put(arr[i].ch().charAt(0), f.getName() + "[" + i + "]");
                        ADV.put(arr[i].ch().charAt(0), arr[i].advance());
                    }
                } else if (v instanceof HudGlyphs.CharSet cs) {
                    for (int i = 0; i < cs.chars().length(); i++) {
                        NAMES.put(cs.chars().charAt(i), f.getName() + ":'" + cs.symbols().charAt(i) + "'");
                        ADV.put(cs.chars().charAt(i), cs.advances()[i]);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static Decoded decode(Component c, int half) {
        List<Drawn> out = new ArrayList<>();
        int[] pen = {-half};
        walk(c, out, pen);
        return new Decoded(out, pen[0] + half);
    }

    private static void walk(Component c, List<Drawn> out, int[] pen) {
        if (c instanceof TextComponent t) {
            assertEquals(HudGlyphs.FONT, t.font() == null ? HudGlyphs.FONT : t.font(), "every run is in the HUD font");
            for (char ch : t.content().toCharArray()) {
                Integer a = ADV.get(ch);
                assertNotNull(a, "unknown glyph U+" + Integer.toHexString(ch));
                String name = NAMES.get(ch);
                if (!name.startsWith("NEG_") && !name.startsWith("POS_")) out.add(new Drawn(name, pen[0], a, t.color()));
                pen[0] += a;
            }
        }
        for (Component child : c.children()) walk(child, out, pen);
    }

    static final TextColor W = TextColor.color(0xFFFFFF);

    static HudState state(double hp, double max, PlayerClass c, int res, int resMax, int level, double exp) {
        return new HudState(hp, max, 0, hp, new HudState.LowBar(HudState.LowKind.FOOD, 18, 20), res, resMax, c, level, exp, false,
                List.of(new HudState.Slot(1, HudState.SlotState.READY, 0, W), new HudState.Slot(2, HudState.SlotState.COOLDOWN, 3.2, W),
                        new HudState.Slot(3, HudState.SlotState.NO_RESOURCE, 0, W), new HudState.Slot(4, HudState.SlotState.LOCKED, 0, W),
                        new HudState.Slot(0, HudState.SlotState.READY, 0, W)),
                List.of(new HudState.Buff("SPEED", "12"), new HudState.Buff("POISON", "4")),
                List.of(new HudState.Run("Баатар", W), new HudState.Run("  ₮ 1,250", TextColor.color(0xFFD24A))));
    }

    @Test
    void theLineIsAlwaysExactlyTheCanvasWide_soTheClientCentresItOnTheScreen() {
        for (HudState s : List.of(state(20, 20, PlayerClass.BAATAR, 50, 100, 1, 0),
                state(0, 20, PlayerClass.BOO, 0, 140, 60, 0.999),
                state(184, 200, null, 0, 0, 7, 0.5),
                state(3, 200, PlayerClass.MERGEN, 140, 140, 33, 0.12))) {
            for (boolean pulse : new boolean[]{true, false}) {
                assertEquals(2 * HudComposer.HALF, decode(HudComposer.panel(s, pulse), HudComposer.HALF).total());
            }
        }
    }

    @Test
    void fillsAreExactPixelWidthsOfTheValues() {
        Decoded d = decode(HudComposer.panel(state(184, 200, PlayerClass.DARKHAN, 75, 140, 12, 0.57), false), HudComposer.HALF);
        int inner = HudGlyphs.BAR_W - 2;
        assertEquals(Math.round(184 / 200.0 * inner), d.fillWidth("HP"));
        assertEquals(Math.round(75 / 140.0 * inner), d.fillWidth("RES_DARKHAN"), "the class's own resource colour");
        assertEquals(0, d.fillWidth("RES_BAATAR"));
        assertEquals(Math.round(18 / 20.0 * inner), d.fillWidth("FOOD"));
        assertEquals(Math.round(0.57 * inner), d.fillWidth("EXP"));
        // the HP fill starts right inside the left frame and runs contiguously
        List<Drawn> hp = d.named("FILL_HP[");
        int x = HudComposer.LEFT + 1;
        for (Drawn p : hp) {
            assertEquals(x, p.x(), "pieces butt together");
            x += p.advance() - 1;
        }
        // the right-hand fills grow from the outer end (mirrored)
        List<Drawn> res = d.named("FILL_RES_DARKHAN[");
        Drawn last = res.get(res.size() - 1);
        assertEquals(HudComposer.RIGHT + 1 + inner, last.x() + last.advance() - 1, "resource fill ends at the outer end");
    }

    @Test
    void numbersReadBack() {
        Decoded d = decode(HudComposer.panel(state(184, 200, PlayerClass.MERGEN, 75, 140, 12, 0.57), false), HudComposer.HALF);
        assertEquals("184/20075/140", d.text("ROW_A"));
        assertEquals("18/2057%", d.text("ROW_B"));
        assertEquals("12", d.text("LEVEL"));
        assertEquals("БААТАР  ₮ 1,250", d.text("LINE"));
        assertEquals(1, d.named("BI_A_HEART").size());
        assertEquals(1, d.named("DIAMOND").size());
        // the level is centred on the diamond
        List<Drawn> lv = d.named("LEVEL:");
        int left = lv.get(0).x(), right = lv.get(lv.size() - 1).x() + lv.get(lv.size() - 1).advance() - 1;
        assertTrue(Math.abs(left + right) <= 1, "level digits centred: " + left + ".." + right);
    }

    @Test
    void zeroAndOverflowValuesClamp() {
        assertEquals(0, HudComposer.px(-5, 20, 84));
        assertEquals(0, HudComposer.px(5, 0, 84));
        assertEquals(84, HudComposer.px(50, 20, 84));
        Decoded d = decode(HudComposer.panel(state(0, 20, PlayerClass.BOO, 0, 140, 1, 0), false), HudComposer.HALF);
        assertEquals(0, d.fillWidth("HP"));
        assertEquals("0/200/140", d.text("ROW_A"));
    }

    @Test
    void absorptionAndTheDamageChip() {
        HudState s = new HudState(100, 200, 40, 160, new HudState.LowBar(HudState.LowKind.MOUNT, 24, 30), 10, 100, PlayerClass.BAATAR,
                5, 0, false, List.of(), List.of(), List.of());
        Decoded d = decode(HudComposer.panel(s, false), HudComposer.HALF);
        int inner = HudGlyphs.BAR_W - 2;
        assertEquals(Math.round(0.5 * inner), d.fillWidth("HP"));
        assertEquals(Math.round(0.8 * inner) - Math.round(0.5 * inner), d.fillWidth("HP_CHIP"), "pale chip from hp to the recent value");
        assertEquals(Math.round(0.2 * inner), d.fillWidth("ABSORB"));
        assertTrue(d.text("ROW_A").startsWith("100+40/200"));
        assertEquals(1, d.named("BI_B_HORSE").size(), "riding: the low bar is the mount's health");
        assertTrue(d.fillWidth("MOUNT") > 0);
    }

    @Test
    void lowHealthPulsesTheFrame() {
        HudState s = state(30, 200, PlayerClass.BAATAR, 0, 100, 1, 0);
        assertEquals(1, decode(HudComposer.panel(s, true), HudComposer.HALF).named("FRAME_A_L_LOW").size());
        assertEquals(0, decode(HudComposer.panel(s, false), HudComposer.HALF).named("FRAME_A_L_LOW").size());
        assertEquals(0, decode(HudComposer.panel(state(150, 200, PlayerClass.BAATAR, 0, 100, 1, 0), true), HudComposer.HALF).named("FRAME_A_L_LOW").size());
    }

    @Test
    void slotsShowStateCooldownAndUltimate() {
        Decoded d = decode(HudComposer.panel(state(20, 20, PlayerClass.BAATAR, 50, 100, 20, 0), false), HudComposer.HALF);
        assertEquals(2, d.named("SLOT_READY").size() + d.named("SLOT_ULT").size());
        assertEquals(1, d.named("SLOT_CD").size());
        assertEquals("4", d.text("SLOT"), "3.2 s left shows 4 (rounded up)");
        assertEquals(1, d.named("SLOT_LOCKED").size());
        assertEquals(1, d.named("NUM_LOCK").size());
        assertEquals(1, d.named("SLOT_NORES").size());
        assertEquals(TextColor.color(0xFF6B6B), d.named("NUM_III").get(0).color(), "not enough resource: red numeral");
        assertEquals(1, d.named("SLOT_ULT").size());
        // the slots end at the right edge of the bars
        List<Drawn> slots = d.glyphs().stream().filter(g -> g.name().startsWith("SLOT_")).toList();
        Drawn lastSlot = slots.get(slots.size() - 1);
        assertEquals(HudComposer.RIGHT + HudGlyphs.BAR_W, lastSlot.x() + HudGlyphs.SLOT_W);
        assertEquals(".5", HudComposer.seconds(0.45));
        assertEquals("1", HudComposer.seconds(0.95), "just under a second is 1, not .10 (found live)");
        assertEquals(".1", HudComposer.seconds(0.01));
        assertEquals("2M", HudComposer.seconds(61));
    }

    @Test
    void buffsAreCappedAndLabelled() {
        List<HudState.Buff> many = new ArrayList<>();
        for (String n : List.of("SPEED", "SLOW", "FIRE", "POISON", "REGEN", "SHIELD", "WITHER", "SOUL")) many.add(new HudState.Buff(n, "9"));
        HudState s = new HudState(20, 20, 0, 20, new HudState.LowBar(HudState.LowKind.FOOD, 20, 20), 0, 100, PlayerClass.BOO, 1, 0, false,
                List.of(), many, List.of());
        Decoded d = decode(HudComposer.panel(s, false), HudComposer.HALF);
        assertEquals(HudComposer.MAX_BUFFS, d.glyphs().stream().filter(g -> g.name().startsWith("ICON_")).count());
        assertEquals(HudComposer.LEFT, d.named("ICON_SPEED").get(0).x(), "buffs start at the left edge of the bars");
        assertNull(HudComposer.icon("NO_SUCH_ICON"));
    }

    @Test
    void aLongLineIsCutToTheCanvas() {
        HudState s = new HudState(20, 20, 0, 20, new HudState.LowBar(HudState.LowKind.AIR, 5, 10), 0, 100, PlayerClass.BOO, 1, 0, false,
                List.of(), List.of(), List.of(new HudState.Run("Энэ бол маш урт мэдэгдэл бөгөөд дэлгэцэнд багтахгүй ".repeat(4), W)));
        Decoded d = decode(HudComposer.panel(s, false), HudComposer.HALF);
        List<Drawn> line = d.named("LINE:");
        int left = line.get(0).x(), right = line.get(line.size() - 1).x() + line.get(line.size() - 1).advance();
        assertTrue(left >= -HudComposer.HALF && right <= HudComposer.HALF, left + ".." + right);
        String text = d.text("LINE");
        assertTrue(text.endsWith(".."), "a cut line ends with '..': " + text);
        assertTrue(text.substring(0, text.length() - 2).endsWith("БАГТАХГҮЙ") || text.charAt(text.length() - 3) != ' ', "cut at a word boundary: " + text);
        assertFalse(text.contains("  .."));
        assertEquals(1, d.named("BI_B_AIR").size(), "under water: the low bar is breath");
    }

    @Test
    void undrawableCharactersAreDroppedAndTheLineTrimmed() {
        HudState s = new HudState(20, 20, 0, 20, new HudState.LowBar(HudState.LowKind.FOOD, 20, 20), 0, 100, PlayerClass.BOO, 1, 0, false,
                List.of(), List.of(), List.of(new HudState.Run("🛡 Хот хамгаалагдсан ", W)));
        Decoded d = decode(HudComposer.panel(s, false), HudComposer.HALF);
        assertEquals("ХОТ ХАМГААЛАГДСАН", d.text("LINE"));
        List<Drawn> line = d.named("LINE:");
        int left = line.get(0).x(), right = line.get(line.size() - 1).x() + line.get(line.size() - 1).advance() - 1;
        assertTrue(Math.abs(left + right) <= 2, "centred after trimming: " + left + ".." + right);
    }

    @Test
    void targetFrame() {
        HudComposer.Target t = new HudComposer.Target("Хасар — Агуйн Эзэн", 5, 1500, 3000, 2100, HudComposer.Tier.BOSS, true);
        Decoded d = decode(HudComposer.target(t), HudComposer.TARGET_HALF);
        assertEquals(2 * HudComposer.TARGET_HALF, d.total());
        assertEquals(1, d.named("TGT_FRAME_BOSS").size());
        assertEquals(-HudGlyphs.TARGET_W / 2, d.named("TGT_FRAME_BOSS").get(0).x(), "frame centred under the title");
        assertEquals(Math.round(0.5 * (HudGlyphs.TARGET_W - 2)), d.fillWidth("TGT"));
        assertEquals("1500/3000", d.text("TARGET"));
        assertEquals("LV 5  ХАСАР — АГУЙН ЭЗЭН", d.text("LINE"));
        assertEquals(1, decode(HudComposer.target(new HudComposer.Target("Чоно", 2, 10, 10, 10, HudComposer.Tier.NORMAL, true)),
                HudComposer.TARGET_HALF).named("TGT_FRAME").stream().filter(g -> g.name().equals("TGT_FRAME")).count());
    }

    @Test
    void writeSamplesForTheRenderer() throws Exception {
        Path dir = Path.of("build", "hud-samples");
        Files.createDirectories(dir);
        Map<String, Component> samples = new java.util.LinkedHashMap<>();
        samples.put("baatar_full", HudComposer.panel(state(184, 200, PlayerClass.BAATAR, 75, 140, 12, 0.57), false));
        samples.put("boo_low", HudComposer.panel(state(30, 200, PlayerClass.BOO, 20, 140, 35, 0.05), true));
        samples.put("mergen_combat", HudComposer.panel(new HudState(120, 160, 20, 150, new HudState.LowBar(HudState.LowKind.MOUNT, 22, 30), 64, 100,
                PlayerClass.MERGEN, 27, 0.83, false,
                List.of(new HudState.Slot(1, HudState.SlotState.READY, 0, HudComposer.resourceColor(PlayerClass.MERGEN)),
                        new HudState.Slot(2, HudState.SlotState.COOLDOWN, 2.4, W), new HudState.Slot(3, HudState.SlotState.READY, 0,
                                HudComposer.resourceColor(PlayerClass.MERGEN)), new HudState.Slot(4, HudState.SlotState.LOCKED, 0, W)),
                List.of(new HudState.Buff("EMPOWER", "x3"), new HudState.Buff("SPEED", "8"), new HudState.Buff("DANGER", "")),
                List.of(new HudState.Run("Мэргэн", TextColor.color(0x7CE07C)), new HudState.Run("  ·  ₮ 12,340  ·  ◆ 3  ·  ⚔ 57", W))), false));
        samples.put("target_boss", HudComposer.target(new HudComposer.Target("Хасар — Агуйн Эзэн", 5, 1500, 3000, 2100, HudComposer.Tier.BOSS, true)));
        for (Map.Entry<String, Component> e : samples.entrySet()) {
            StringBuilder sb = new StringBuilder("[");
            List<String[]> runs = new ArrayList<>();
            collect(e.getValue(), runs);
            for (int i = 0; i < runs.size(); i++) {
                String[] r = runs.get(i);
                sb.append(i > 0 ? "," : "").append("{\"text\":\"").append(escape(r[0])).append("\",\"color\":\"").append(r[1]).append("\"}");
            }
            Files.writeString(dir.resolve(e.getKey() + ".json"), sb.append("]").toString());
        }
    }

    private static void collect(Component c, List<String[]> out) {
        if (c instanceof TextComponent t && !t.content().isEmpty()) {
            out.add(new String[]{t.content(), t.color() == null ? "#FFFFFF" : t.color().asHexString()});
        }
        for (Component child : c.children()) collect(child, out);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) sb.append(ch < 128 && ch != '"' && ch != '\\' ? String.valueOf(ch) : String.format("\\u%04x", (int) ch));
        return sb.toString();
    }
}
