package mn.suld.plugin.anticheat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * deploy/paper-tuning.conf's anti-xray: in engine mode 2 Paper ignores tile entities in hidden-blocks, so the
 * containers and the spawner must be in replacement-blocks to be hidden at all (Paper world configuration docs).
 */
class AntiXrayTuningTest {

    private static final List<String> TILE_ENTITIES = List.of("chest", "trapped_chest", "barrel", "ender_chest", "spawner");

    private static List<String> lines() throws IOException {
        for (String p : new String[]{"../deploy/paper-tuning.conf", "deploy/paper-tuning.conf"}) {
            Path f = Path.of(p);
            if (Files.exists(f)) return Files.readAllLines(f, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("deploy/paper-tuning.conf");
    }

    private static List<String> list(String key) throws IOException {
        String prefix = "config/paper-world-defaults.yml: anticheat.anti-xray." + key + "=";
        for (String l : lines()) {
            if (l.startsWith(prefix)) {
                String v = l.substring(prefix.length()).trim();
                assertTrue(v.startsWith("[") && v.endsWith("]"), key + " is a flow list");
                return Arrays.stream(v.substring(1, v.length() - 1).split(",")).map(String::trim).toList();
            }
        }
        throw new AssertionError(key + " is not set");
    }

    @Test
    void engineModeTwoHidesTileEntitiesThroughReplacementBlocks() throws IOException {
        assertTrue(lines().contains("config/paper-world-defaults.yml: anticheat.anti-xray.engine-mode=2"));
        List<String> hidden = list("hidden-blocks"), replacement = list("replacement-blocks");
        for (String t : TILE_ENTITIES) {
            assertFalse(hidden.contains(t), t + " in hidden-blocks is ignored in mode 2");
            assertTrue(replacement.contains(t), t + " must be in replacement-blocks");
        }
        assertTrue(replacement.containsAll(List.of("stone", "oak_planks", "deepslate")), "Paper's default replacements stay");
    }
}
