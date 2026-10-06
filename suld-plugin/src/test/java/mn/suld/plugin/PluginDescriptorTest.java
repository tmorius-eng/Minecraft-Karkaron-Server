package mn.suld.plugin;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards plugin.yml: a malformed descriptor makes Paper refuse to load SÜLD at all, which no
 * other unit test would notice. Checks structure without a YAML library (none on the test
 * classpath): consistent 2-space indentation, no tabs, and every command we register exists.
 */
class PluginDescriptorTest {

    private static List<String> lines() throws Exception {
        try (InputStream in = PluginDescriptorTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml on the classpath");
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
        }
    }

    @Test
    void indentationIsWellFormed() throws Exception {
        int previousIndent = 0;
        boolean previousOpensBlock = false;
        int lineNo = 0;
        for (String raw : lines()) {
            lineNo++;
            String line = raw.stripTrailing();
            assertFalse(line.contains("\t"), "tab at line " + lineNo);
            if (line.isBlank() || line.trim().startsWith("#") || line.trim().startsWith("- ")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            assertEquals(0, indent % 2, "odd indentation at line " + lineNo + ": " + line);
            if (indent > previousIndent) {
                assertTrue(previousOpensBlock, "unexpected indent at line " + lineNo + ": " + line);
                assertEquals(previousIndent + 2, indent, "indent jumps more than one level at line " + lineNo);
            }
            previousOpensBlock = line.endsWith(":");
            previousIndent = indent;
        }
    }

    @Test
    void everyRegisteredCommandIsDeclared() throws Exception {
        List<String> declared = new ArrayList<>();
        boolean inCommands = false;
        for (String line : lines()) {
            if (!line.startsWith(" ")) {
                inCommands = line.startsWith("commands:");
                continue;
            }
            if (inCommands && line.startsWith("  ") && !line.startsWith("   ") && line.trim().endsWith(":")) {
                declared.add(line.trim().replace(":", ""));
            }
        }
        for (String cmd : List.of("suld", "revive", "suldpack", "party", "dungeon", "clan", "cc", "suldevent", "relic")) {
            assertTrue(declared.contains(cmd), "plugin.yml must declare /" + cmd + " (SuldPlugin registers it)");
        }
    }
}
