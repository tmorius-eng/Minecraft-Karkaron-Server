package mn.suld.api.hologram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Template lines filled with live values: escaped values, expanded lists, and a height a board above can trust. */
class HologramTextTest {

    private static final java.util.function.UnaryOperator<String> ESCAPE = s -> s.replace("<", "\\<");

    @Test
    void valuesAreFilledAndEscapedAndUnknownKeysStay() {
        List<String> out = HologramText.render(List.of("<b>{name}</b> {level}", "{other}"),
                Map.of("name", "<red>Bat</red>", "level", "12"), Map.of(), Map.of(), Map.of(), ESCAPE);
        assertEquals(List.of("<b>\\<red>Bat\\</red></b> 12", "{other}"), out);
    }

    @Test
    void aListLineBecomesItsHeaderAndOneLinePerEntry() {
        List<String> out = HologramText.render(List.of("Тэргүүн", "{top}", "төгсгөл"), Map.of(),
                Map.of("top", List.of(Map.of("n", "1", "name", "Bat"), Map.of("n", "2", "name", "Dorj"))),
                Map.of("top", "{n}. {name}"), Map.of("top", List.of("", "Тэргүүлэгчид:")), ESCAPE);
        assertEquals(List.of("Тэргүүн", "", "Тэргүүлэгчид:", "1. Bat", "2. Dorj", "төгсгөл"), out);
    }

    @Test
    void anEmptyListLeavesNothingNotEvenItsHeader() {
        List<String> out = HologramText.render(List.of("a", "{top}", "b"), Map.of(), Map.of("top", List.of()),
                Map.of("top", "{name}"), Map.of("top", List.of("Тэргүүлэгчид:")), ESCAPE);
        assertEquals(List.of("a", "b"), out);
    }

    @Test
    void heightCountsWrappedRowsAndScale() {
        HologramStyle s = HologramStyle.DEFAULT;
        assertEquals((2 * 10 + 2) * 0.025, HologramText.height(new int[]{50, 120}, s), 1e-9);
        // a 450 px line wraps into three rows at the 200 px default width
        assertEquals((4 * 10 + 2) * 0.025, HologramText.height(new int[]{450, 10}, s), 1e-9);
        HologramStyle big = new HologramStyle(s.billboard(), 2f, s.background(), s.lineWidth(), s.align(), s.shadow(), s.viewRange());
        assertEquals(2 * HologramText.height(new int[]{50}, s), HologramText.height(new int[]{50}, big), 1e-9);
    }

    @Test
    void fontWidthGrowsWithBoldAndWideLetters() {
        int plain = HologramText.defaultFontWidth("АНГИ", false);
        assertTrue(HologramText.defaultFontWidth("АНГИ", true) > plain);
        assertTrue(HologramText.defaultFontWidth("ЖЖЖЖ", false) > plain);
        assertTrue(HologramText.defaultFontWidth("i i", false) < plain);
    }
}
