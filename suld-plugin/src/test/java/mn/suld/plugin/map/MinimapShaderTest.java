package mn.suld.plugin.map;

import mn.suld.api.map.MapColors;
import mn.suld.api.map.Minimap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        int from = 0;
        for (int i = 0; i < Minimap.MARK.length; i++) {
            int c = MapColors.argb(Minimap.MARK[i]);
            String at = i == 0 ? "o" : "o + ivec2(" + i + ", 0)";
            String rgb = "suld_mark(" + at + ", vec3(" + ((c >> 16) & 0xFF) + ".0, " + ((c >> 8) & 0xFF) + ".0, " + (c & 0xFF) + ".0))";
            int k = vsh.indexOf(rgb, from);
            assertTrue(k >= 0, "rendertype_text.vsh must test " + rgb + " after the colour before it");
            from = k;
        }
        assertTrue(vsh.contains("ivec2(" + Minimap.SIZE + ", " + Minimap.SIZE + ")"), "the shader must still find a map in its own " + Minimap.SIZE + "-wide texture");
    }

    /**
     * ImmediatelyFast (in Fabulously Optimized, map_atlas_generation on by default) copies every map into one atlas of
     * 128-texel cells: a shader that only looked at 128 x 128 textures never found the minimap there, and its four
     * frames hung in front of the player as floating maps (2026-10-15 playtest). tools/validation/check_minimap_shader.py
     * renders both cases on a GPU; this pins the parts of the contract a text check can see.
     */
    @Test
    void theShaderFindsTheMinimapInAMapAtlasToo() throws IOException {
        String vsh = Files.readString(shaders().resolve("rendertype_text.vsh"));
        assertFalse(vsh.contains("!= ivec2(" + Minimap.SIZE + ", " + Minimap.SIZE + ")) return false"),
                "a texture that is not " + Minimap.SIZE + " x " + Minimap.SIZE + " must not be ruled out: it may be a map atlas");
        assertTrue(vsh.contains("% " + Minimap.SIZE), "the atlas's cells are " + Minimap.SIZE + " texels");
        // a cell boundary touches four maps: only the quad's vertex order tells which corner a vertex is
        assertTrue(vsh.contains("gl_VertexID % 4"), "the corner must come from the vertex order, not the UV alone");
        assertTrue(vsh.contains("out vec2 suldLocal;"), "the map's own coordinates must reach the fragment shader");
    }

    @Test
    void theMarkerRowIsNeverShown() throws IOException {
        String fsh = Files.readString(shaders().resolve("rendertype_text.fsh"));
        // the map's own row 0: in an atlas texCoord0 is the place in the whole atlas, so it cannot find the row
        assertTrue(fsh.contains("suldLocal.y < 1.0 / " + Minimap.SIZE + ".0"), "rendertype_text.fsh must discard the map's row 0");
        assertFalse(fsh.contains("texCoord0.y < 1.0 / " + Minimap.SIZE), "row 0 must not be found by the atlas coordinate");
        // and the vanilla path is still there for every other text
        assertTrue(fsh.contains("apply_fog(color"), "rendertype_text.fsh must keep vanilla's fog for other text");
    }
}
