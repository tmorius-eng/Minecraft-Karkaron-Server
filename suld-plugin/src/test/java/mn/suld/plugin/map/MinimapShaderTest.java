package mn.suld.plugin.map;

import mn.suld.api.map.MapColors;
import mn.suld.api.map.Minimap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pack's text shader finds the corner minimap by the colours of its first row: they must be the colours the
 * server paints there ({@link Minimap#MARK}), in that order, or the minimap hangs in the air instead (docs/WORLD_MAP.md).
 */
class MinimapShaderTest {

    private static Path shaders() {
        for (String p : new String[]{"../resourcepack/assets/minecraft/shaders/core", "resourcepack/assets/minecraft/shaders/core"}) {
            Path path = Path.of(p);
            if (Files.isDirectory(path)) return path;
        }
        throw new IllegalStateException("resourcepack/assets/minecraft/shaders/core not found from " + Path.of("").toAbsolutePath());
    }

    @Test
    void theShaderLooksForTheMarkerColoursInOrder() throws IOException {
        String vsh = Files.readString(shaders().resolve("rendertype_text.vsh"));
        for (int i = 0; i < Minimap.MARK.length; i++) {
            int c = MapColors.argb(Minimap.MARK[i]);
            String rgb = "suld_mark(" + i + ", vec3(" + ((c >> 16) & 0xFF) + ".0, " + ((c >> 8) & 0xFF) + ".0, " + (c & 0xFF) + ".0))";
            assertTrue(vsh.contains(rgb), "rendertype_text.vsh must test " + rgb);
        }
        assertTrue(vsh.contains("ivec2(" + Minimap.SIZE + ", " + Minimap.SIZE + ")"), "the shader must look at " + Minimap.SIZE + "-wide maps only");
    }

    @Test
    void theMarkerRowIsNeverShown() throws IOException {
        String fsh = Files.readString(shaders().resolve("rendertype_text.fsh"));
        assertTrue(fsh.contains("texCoord0.y < 1.0 / " + Minimap.SIZE + ".0"), "rendertype_text.fsh must discard row 0");
        // and the vanilla path is still there for every other text
        assertTrue(fsh.contains("apply_fog(color"), "rendertype_text.fsh must keep vanilla's fog for other text");
    }
}
