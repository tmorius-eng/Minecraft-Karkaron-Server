package mn.suld.plugin.skill;

import mn.suld.api.json.Json;
import mn.suld.api.skill.tree.MapAssets;
import mn.suld.plugin.ui.Glyphs;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skill map is only as good as the pack behind it: every pack model, texture and glyph the code names must exist,
 * be well formed, and have the size the GUI expects. A missing file would show as a purple-black cube in the client.
 */
class SkillPackAssetsTest {

    private static Path pack() {
        for (String p : new String[]{"../resourcepack/assets/suld", "resourcepack/assets/suld"}) {
            Path path = Path.of(p);
            if (Files.isDirectory(path)) return path;
        }
        throw new IllegalStateException("resourcepack/assets/suld not found from " + Path.of("").toAbsolutePath());
    }

    private static Map<String, Object> json(Path file) throws IOException {
        return Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
    }

    private static List<String> allPackItems() {
        List<String> out = new ArrayList<>(MapAssets.connectorModels());
        out.add(MapAssets.ORB_MODEL);
        return out;
    }

    @Test
    void connectorAndOrbModelsAreCompleteInThePack() throws IOException {
        Path root = pack();
        assertEquals(16, MapAssets.connectorModels().size());
        for (String m : allPackItems()) {
            Path item = root.resolve("items/" + m + ".json");
            Path model = root.resolve("models/item/" + m + ".json");
            Path texture = root.resolve("textures/item/" + m + ".png");
            assertTrue(Files.isRegularFile(item), "item definition " + m);
            assertTrue(Files.isRegularFile(model), "model " + m);
            assertTrue(Files.isRegularFile(texture), "texture " + m);
            // item definition -> model -> texture chain
            Map<String, Object> def = Json.object(json(item).get("model"));
            assertEquals("suld:item/" + m, def.get("model"), m + " item definition points at its model");
            Map<String, Object> mdl = json(model);
            Map<String, Object> textures = Json.object(mdl.get("textures"));
            assertEquals("suld:item/" + m, textures.get("layer0"), m + " model points at its texture");
            BufferedImage img = ImageIO.read(texture.toFile());
            assertEquals(16, img.getWidth(), m + " width");
            assertEquals(16, img.getHeight(), m + " height");
        }
    }

    @Test
    void connectorModelsFillTheirSlotSoNeighboursJoin() throws IOException {
        for (String m : MapAssets.connectorModels()) {
            Map<String, Object> display = Json.object(json(pack().resolve("models/item/" + m + ".json")).get("display"));
            Map<String, Object> gui = Json.object(display.get("gui"));
            for (Object v : Json.array(gui.get("scale"))) assertEquals(1.125, ((Number) v).doubleValue(), 1e-9, m + " is drawn at 18 px so the 2 px slot gap closes");
        }
    }

    @Test
    void connectorLinesTouchTheEdgesTheyJoin() throws IOException {
        for (String m : MapAssets.connectorModels()) {
            BufferedImage img = ImageIO.read(pack().resolve("textures/item/" + m + ".png").toFile());
            String kind = m.substring("tree_".length(), m.lastIndexOf('_'));
            switch (kind) {
                case "h" -> {
                    assertTrue(opaque(img, 0, 7) && opaque(img, 15, 7), m + " reaches the left and right edge");
                    assertTrue(!opaque(img, 7, 0) && !opaque(img, 7, 15), m + " does not reach the top or bottom");
                }
                case "v" -> {
                    assertTrue(opaque(img, 7, 0) && opaque(img, 7, 15), m + " reaches the top and bottom edge");
                    assertTrue(!opaque(img, 0, 7) && !opaque(img, 15, 7), m + " does not reach the sides");
                }
                case "d1" -> assertTrue(opaque(img, 0, 0) && opaque(img, 15, 15), m + " runs from the top-left to the bottom-right corner");
                case "d2" -> assertTrue(opaque(img, 14, 0) && opaque(img, 0, 15), m + " runs from the top-right to the bottom-left corner");
                default -> throw new AssertionError(kind);
            }
        }
    }

    private static boolean opaque(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >>> 24) != 0;
    }

    @Test
    void skillMapBackgroundIsAWholeChestWideGlyph() throws IOException {
        Path root = pack();
        List<Object> providers = Json.array(json(root.resolve("font/ui.json")).get("providers"));
        Map<String, Object> mine = null;
        for (Object o : providers) {
            Map<String, Object> p = Json.object(o);
            if ("suld:font/gui/skillmap.png".equals(p.get("file"))) mine = p;
        }
        assertTrue(mine != null, "font provider for the skill map background");
        assertEquals(List.of(Glyphs.GUI_SKILLMAP), Json.array(mine.get("chars")), "the code point in Glyphs.java is the one the font maps");
        BufferedImage img = ImageIO.read(root.resolve("textures/font/gui/skillmap.png").toFile());
        assertEquals(176, img.getWidth(), "exactly the width of a chest screen");
        assertEquals(17 + 6 * 18 + 1, img.getHeight(), "six slot rows plus the header");
        assertEquals(Json.integer(mine.get("height")), img.getHeight(), "the glyph height equals the texture height");
    }
}
