package mn.suld.plugin.hud;

import mn.suld.api.json.Json;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The server lays the HUD out to the pixel from the advances in {@link HudGlyphs}; that only matches the screen if the
 * client really draws every glyph with that advance. Two ways it does not, both checked here against the real pack:
 * <ul>
 *   <li>a bitmap glyph whose cell is over 256 texels on either axis is refused by the client and drawn as the
 *   missing-glyph box with the box's own small advance (the 364-texel target frame did this: the bar fell left of the
 *   name and the numbers, docs/HUD.md §Target frame);</li>
 *   <li>an advance in the table that is not the client's (trimmed ink width × scale, rounded, + 1).</li>
 * </ul>
 */
class HudFontContractTest {

    static final int MAX_GLYPH_TEXELS = 256;

    static Path assets() {
        for (String p : new String[]{"../resourcepack/assets", "resourcepack/assets"}) {
            Path d = Path.of(p);
            if (Files.isDirectory(d.resolve("suld/font"))) return d;
        }
        throw new IllegalStateException("resourcepack/assets not found from " + Path.of("").toAbsolutePath());
    }

    record Cell(BufferedImage sheet, int x0, int w, int h, int height, String file) {
    }

    /** Every bitmap glyph of a font: code point → its cell in the texture. */
    static Map<Character, Cell> cells(String font) throws IOException {
        Path root = assets();
        String[] nsPath = font.split(":");
        Map<String, Object> spec = Json.object(Json.parse(Files.readString(root.resolve(nsPath[0] + "/font/" + nsPath[1] + ".json"),
                StandardCharsets.UTF_8)));
        Map<Character, Cell> out = new HashMap<>();
        for (Object o : Json.array(spec.get("providers"))) {
            Map<String, Object> p = Json.object(o);
            if (!"bitmap".equals(p.get("type"))) continue;
            String[] file = ((String) p.get("file")).split(":");
            BufferedImage img = ImageIO.read(root.resolve(file[0] + "/textures/" + file[1]).toFile());
            List<Object> rows = Json.array(p.get("chars"));
            int cols = ((String) rows.get(0)).length();
            int cw = img.getWidth() / cols, ch = img.getHeight() / rows.size();
            int height = p.containsKey("height") ? Json.integer(p.get("height")) : 8;
            for (int r = 0; r < rows.size(); r++) {
                String row = (String) rows.get(r);
                for (int i = 0; i < row.length(); i++) {
                    BufferedImage band = r == 0 && rows.size() == 1 ? img : img.getSubimage(0, r * ch, img.getWidth(), ch);
                    out.put(row.charAt(i), new Cell(band, i * cw, cw, ch, height, (String) p.get("file")));
                }
            }
        }
        return out;
    }

    /** The client's advance: last column with any alpha, × (height / cell height), rounded, + 1. */
    static int clientAdvance(Cell c) {
        int trim = 0;
        for (int x = c.w() - 1; x >= 0 && trim == 0; x--) {
            for (int y = 0; y < c.h(); y++) {
                if ((c.sheet().getRGB(c.x0() + x, y) >>> 24) != 0) {
                    trim = x + 1;
                    break;
                }
            }
        }
        return (int) (0.5 + trim * (c.height() / (double) c.h())) + 1;
    }

    @Test
    void everyHudGlyphFitsTheClientsGlyphLimit() throws IOException {
        for (Map.Entry<Character, Cell> e : cells("suld:hud").entrySet()) {
            Cell c = e.getValue();
            assertTrue(c.w() <= MAX_GLYPH_TEXELS && c.h() <= MAX_GLYPH_TEXELS,
                    "U+" + Integer.toHexString(e.getKey()) + " (" + c.file() + ") is " + c.w() + "x" + c.h()
                            + " texels: the client draws a missing-glyph box instead and every later glyph shifts");
        }
    }

    @Test
    void everyAdvanceInTheTableIsTheClientsAdvance() throws Exception {
        Map<Character, Cell> cells = cells("suld:hud");
        int checked = 0;
        for (Field f : HudGlyphs.class.getFields()) {
            Object v = f.get(null);
            if (v instanceof HudGlyphs.G g) {
                checked += check(cells, f.getName(), g.ch().charAt(0), g.advance());
            } else if (v instanceof HudGlyphs.G[] arr) {
                for (int i = 0; i < arr.length; i++) checked += check(cells, f.getName() + "[" + i + "]", arr[i].ch().charAt(0), arr[i].advance());
            } else if (v instanceof HudGlyphs.CharSet cs) {
                for (int i = 0; i < cs.chars().length(); i++) {
                    checked += check(cells, f.getName() + ":'" + cs.symbols().charAt(i) + "'", cs.chars().charAt(i), cs.advances()[i]);
                }
            }
        }
        assertTrue(checked > 200, "checked " + checked + " bitmap glyphs");
    }

    private static int check(Map<Character, Cell> cells, String name, char ch, int advance) {
        Cell c = cells.get(ch);
        if (c == null) return 0; // a space-provider glyph (the pen shifts): its advance is in the font as given
        assertEquals(clientAdvance(c), advance, name + " (" + c.file() + ")");
        return 1;
    }

    @Test
    void theTargetFrameHalvesAreBothInTheFontAndTogetherSpanTheFrame() throws IOException {
        Map<Character, Cell> cells = cells("suld:hud");
        for (HudComposer.Tier tier : HudComposer.Tier.values()) {
            HudGlyphs.G[] h = HudComposer.frameHalves(tier);
            Cell l = cells.get(h[0].ch().charAt(0)), r = cells.get(h[1].ch().charAt(0));
            assertNotNull(l, tier + " left half mapped");
            assertNotNull(r, tier + " right half mapped");
            assertEquals(l.height(), r.height(), tier.name());
            int lw = (int) Math.round(l.w() * l.height() / (double) l.h()), rw = (int) Math.round(r.w() * r.height() / (double) r.h());
            assertEquals(HudGlyphs.TARGET_W, lw + rw, tier + ": the halves together are the whole frame in GUI px");
        }
    }
}
