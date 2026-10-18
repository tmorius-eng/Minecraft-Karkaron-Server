package mn.suld.plugin.map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The atlas's tooltips (owner, 2026-10-18: "every tooltip is empty"): the text faces the camera, and every tab says what
 * it is, what it gives and what a click does, in lines that fit on the wall.
 */
class AtlasTooltipTest {

    /** AtlasSky.showTooltip keeps a tooltip within the wall and its frame (COLS / 2 + 0.4 either side of the middle). */
    static final double MAX_W = AtlasSky.COLS + 0.8, MAX_H = AtlasSky.ROWS;

    @Test
    void everyTabSaysWhatItIsAndWhatAClickDoes() {
        for (AtlasSky.Filter f : AtlasSky.Filter.values()) check(f.label, AtlasSky.tipLines(f.about, f.use()));
        for (AtlasSky.Tool t : AtlasSky.TOOLS) check(t.label(), AtlasSky.tipLines(t.about(), t.use()));
    }

    private static void check(String name, List<Component> lore) {
        assertTrue(lore.size() >= 3, name + ": two lines or more on what it is, and the click");
        List<String> lines = new ArrayList<>();
        lines.add(name);
        for (Component c : lore) {
            String l = PlainTextComponentSerializer.plainText().serialize(c);
            assertTrue(l.chars().anyMatch(ch -> Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CYRILLIC), name + ": " + l);
            lines.add(l);
        }
        assertEquals(AtlasSky.CYAN, lore.get(lore.size() - 1).color(), name + ": the last line, what a click does, is cyan");
        double[] box = AtlasSky.box(lines, AtlasSky.TIP_SCALE);
        assertTrue(box[0] <= MAX_W, name + "'s tooltip is " + box[0] + " blocks wide");
        assertTrue(box[1] <= MAX_H, name + "'s tooltip is " + box[1] + " blocks high");
    }

    @Test
    void everyTabHasItsArtInThePack() {
        Path root = Files.exists(Path.of("../resourcepack")) ? Path.of("../resourcepack") : Path.of("resourcepack");
        List<String> icons = new ArrayList<>();
        for (AtlasSky.Filter f : AtlasSky.Filter.values()) icons.add(f.icon);
        for (AtlasSky.Tool t : AtlasSky.TOOLS) icons.add(t.icon());
        for (String icon : icons) {
            for (String state : new String[]{"", "_hover", "_on"}) {
                String n = "tab_" + icon + state;
                for (String f : new String[]{"textures/entity/atlas/" + n + ".png", "models/entity/atlas/" + n + ".json", "items/entity/atlas/" + n + ".json"}) {
                    assertTrue(Files.exists(root.resolve("assets/suld/" + f)), f + " (python3 tools/map/atlas_art.py pack .)");
                }
            }
        }
    }

    @Test
    void aTooltipIsMovedWithoutTurningItsTextAway() throws IOException {
        // teleport(at(...)) gives a text display the stage's yaw 0: its one-sided text then faces away from the camera
        for (String rel : List.of("map/AtlasSky.java", "gui/MenuSky.java")) {
            String src = main(rel);
            assertFalse(src.contains("tooltip.teleport(at("), rel + " turns its tooltip's text away from the camera");
        }
    }

    private static String main(String rel) throws IOException {
        for (String root : new String[]{"src/main/java/", "suld-plugin/src/main/java/"}) {
            Path p = Path.of(root + "mn/suld/plugin/" + rel);
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException(rel);
    }
}
