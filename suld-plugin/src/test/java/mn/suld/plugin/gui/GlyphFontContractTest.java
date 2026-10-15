package mn.suld.plugin.gui;

import mn.suld.api.json.Json;
import mn.suld.plugin.ui.Glyphs;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No ▯ anywhere: every private-use code point the code sends is drawn by the pack, the way the 1.21.11 client draws it.
 * <p>
 * The client stitches each font glyph into a 256 x 256 atlas page. A bitmap cell over 256 texels on either axis does
 * not fit, so the client draws its missing-glyph box (▯) with the box's own 6 px advance. In the live test of
 * 2026-10-15 the 4x welcome background (704 x 288) did this: the chest was empty and the title read
 * «Тавтай морил, &lt;name&gt;!▯», shifted 171 px left. The same happened to every 4x background (main menu, cosmetics,
 * quest book, the hub pages) and to the inventory's decor. tools/pack/glyph_tiles.py cuts such art into tiles; this
 * test holds the pack and the code to it.
 */
class GlyphFontContractTest {

    static final int MAX_GLYPH_TEXELS = 256;
    static final int MISSING = Integer.MIN_VALUE;

    static Path root() {
        for (String p : new String[]{"..", "."}) {
            Path d = Path.of(p);
            if (Files.isDirectory(d.resolve("resourcepack/assets/suld/font"))) return d;
        }
        throw new IllegalStateException("resourcepack/ not found from " + Path.of("").toAbsolutePath());
    }

    /** One font as the client sees it: code point → advance, and the bitmap cells it would refuse. */
    record Font(Map<Character, Integer> advance, List<String> oversized) {
    }

    static Font font(String ns, String name) throws IOException {
        Path assets = root().resolve("resourcepack/assets");
        Map<String, Object> spec = Json.object(Json.parse(Files.readString(assets.resolve(ns + "/font/" + name + ".json"),
                StandardCharsets.UTF_8)));
        Map<Character, Integer> adv = new HashMap<>();
        List<String> oversized = new ArrayList<>();
        for (Object o : Json.array(spec.get("providers"))) {
            Map<String, Object> p = Json.object(o);
            if ("space".equals(p.get("type"))) {
                for (Map.Entry<String, Object> e : Json.object(p.get("advances")).entrySet()) {
                    adv.putIfAbsent(e.getKey().charAt(0), ((Number) e.getValue()).intValue());
                }
            } else if ("bitmap".equals(p.get("type"))) {
                String[] file = ((String) p.get("file")).split(":");
                BufferedImage img = ImageIO.read(assets.resolve(file[0] + "/textures/" + file[1]).toFile());
                List<Object> rows = Json.array(p.get("chars"));
                int cols = ((String) rows.get(0)).length();
                int cw = img.getWidth() / cols, ch = img.getHeight() / rows.size();
                int height = p.containsKey("height") ? Json.integer(p.get("height")) : 8;
                if (cw > MAX_GLYPH_TEXELS || ch > MAX_GLYPH_TEXELS) oversized.add(ns + ":" + name + " " + p.get("file") + " " + cw + "x" + ch);
                for (int r = 0; r < rows.size(); r++) {
                    String row = (String) rows.get(r);
                    for (int i = 0; i < row.length(); i++) {
                        boolean refused = cw > MAX_GLYPH_TEXELS || ch > MAX_GLYPH_TEXELS;
                        adv.putIfAbsent(row.charAt(i), refused ? MISSING : (int) (0.5 + ink(img, i * cw, r * ch, cw, ch) * ((double) height / ch)) + 1);
                    }
                }
            }
        }
        return new Font(adv, oversized);
    }

    /** One past the cell's last column with any alpha (the client's glyph width). */
    static int ink(BufferedImage img, int x0, int y0, int w, int h) {
        for (int x = w - 1; x >= 0; x--) {
            for (int y = 0; y < h; y++) if ((img.getRGB(x0 + x, y0 + y) >>> 24) != 0) return x + 1;
        }
        return 0;
    }

    /** The pen after drawing {@code s} in {@code f}; fails on a code point the font does not draw. */
    static int walk(Font f, String s, String what) {
        int pen = 0;
        for (char c : s.toCharArray()) {
            Integer a = f.advance().get(c);
            assertNotNull(a, what + ": U+" + Integer.toHexString(c).toUpperCase() + " is not in the font: the client draws ▯");
            assertNotEquals(MISSING, a, what + ": U+" + Integer.toHexString(c).toUpperCase() + " is over " + MAX_GLYPH_TEXELS + " texels: the client draws ▯");
            pen += a;
        }
        return pen;
    }

    @Test
    void everyFontGlyphFitsTheClientsAtlasPage() throws IOException {
        List<String> over = new ArrayList<>();
        over.addAll(font("suld", "ui").oversized());
        over.addAll(font("suld", "hud").oversized());
        over.addAll(font("minecraft", "default").oversized());
        assertEquals(List.of(), over, "drawn as ▯ by the client: run tools/pack/glyph_tiles.py");
    }

    @Test
    void everyGlyphsConstantIsDrawnByTheFont() throws Exception {
        Font ui = font("suld", "ui");
        int n = 0;
        for (Field f : Glyphs.class.getFields()) {
            if (f.getType() != String.class || !Modifier.isStatic(f.getModifiers())) continue;
            walk(ui, (String) f.get(null), "Glyphs." + f.getName());
            n++;
        }
        assertTrue(n > 80, "Glyphs constants found: " + n);
    }

    /** Tiled art must end where the whole image ended, or every glyph and letter after it moves. */
    @Test
    void tiledBackgroundsKeepTheirAdvance() throws Exception {
        Font ui = font("suld", "ui");
        for (Field f : Glyphs.class.getFields()) {
            String name = f.getName();
            if (f.getType() != String.class) continue;
            String g = (String) f.get(null);
            if (name.startsWith("GUI_")) assertEquals(176 + 1, walk(ui, g, name), name + ": a chest background is 176 px wide");
            if (name.startsWith("PAGE_") || name.equals("HUB_FRAME")) assertEquals(HubLayout.W + 1, walk(ui, g, name), name);
            if (name.startsWith("HUB_CARD_ON_R")) assertEquals(HubLayout.CARD_ON_W + 1, walk(ui, g, name), name);
        }
    }

    /** {@link Menu}'s painted title: shift −8, the background, shift back; the text lands past the header's knot. */
    @Test
    void theWelcomeTitleLandsOnItsHeader() throws IOException {
        Font ui = font("suld", "ui");
        int pen = -8 + walk(ui, Glyphs.GUI_WELCOME, "GUI_WELCOME") + (-169 + Menu.TITLE_INDENT);
        assertEquals(Menu.TITLE_INDENT, pen, "«Тавтай морил, <name>!» starts right of the painted knot, not 171 px left of the chest");
    }

    @Test
    void theInventoryDecorTitleIsDrawnInEveryLanguage() throws IOException {
        Font def = font("minecraft", "default");
        Path lang = root().resolve("resourcepack/assets/minecraft/lang");
        try (Stream<Path> files = Files.list(lang)) {
            for (Path p : files.filter(x -> x.toString().endsWith(".json")).toList()) {
                String t = (String) Json.object(Json.parse(Files.readString(p, StandardCharsets.UTF_8))).get("container.crafting");
                assertNotNull(t, p.getFileName() + ": container.crafting");
                assertTrue(t.startsWith("§f"), p.getFileName() + ": white, so the decor is not tinted");
                walk(def, t.substring(2), p.getFileName() + " container.crafting");
            }
        }
    }

    private static final Pattern ESCAPE = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

    /** Any private-use code point written in the plugin's or the API's code is drawn by one of the pack's fonts. */
    @Test
    void noPrivateUseCodePointInTheCodeIsUnmapped() throws IOException {
        Font ui = font("suld", "ui"), hud = font("suld", "hud"), def = font("minecraft", "default");
        TreeSet<String> unmapped = new TreeSet<>();
        int seen = 0;
        for (String module : new String[]{"suld-plugin", "suld-api"}) {
            try (Stream<Path> files = Files.walk(root().resolve(module + "/src/main/java"))) {
                for (Path p : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    List<Character> cps = new ArrayList<>();
                    Matcher m = ESCAPE.matcher(src);
                    while (m.find()) cps.add((char) Integer.parseInt(m.group(1), 16));
                    for (char c : src.toCharArray()) cps.add(c);
                    for (char c : cps) {
                        if (c < '' || c > '') continue;
                        seen++;
                        boolean drawn = Stream.of(ui, hud, def).anyMatch(f -> {
                            Integer a = f.advance().get(c);
                            return a != null && a != MISSING;
                        });
                        if (!drawn) unmapped.add(p.getFileName() + ": U+" + Integer.toHexString(c).toUpperCase());
                    }
                }
            }
        }
        assertTrue(seen > 100, "private-use code points found: " + seen);
        assertEquals(new TreeSet<>(), unmapped, "drawn as ▯ by the client");
    }
}
