package mn.suld.plugin.auth;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped LuckPerms baseline ({@code permissions/luckperms.txt}): what every player gets must stay inside the
 * design. GAME-001: travel between the historic sites is part of the game (ovoo, relays), so the default group has
 * one home and no teleport requests; those nodes are explicitly revoked for servers set up before the change.
 */
class PermissionFileTest {

    private static final Pattern NODE = Pattern.compile("^lp group (\\S+) permission (set|unset) (\\S+)(?: (true|false))?$");

    /** Nodes an ordinary player must never hold (GAME-001). */
    private static final Set<String> FORBIDDEN_DEFAULT = Set.of(
            "essentials.sethome.multiple", "essentials.tpa", "essentials.tpaccept", "essentials.tpdeny", "essentials.tpacancel",
            "essentials.tp", "essentials.tphere", "essentials.back", "essentials.*");

    static List<String> lines() throws IOException {
        List<String> out = new ArrayList<>();
        try (InputStream in = PermissionFileTest.class.getResourceAsStream("/permissions/luckperms.txt")) {
            assertNotNull(in, "permissions/luckperms.txt is a plugin resource");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.strip();
                    if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
                }
            }
        }
        return out;
    }

    @Test
    void defaultGroupHasOneHomeAndNoTeleportRequests() throws IOException {
        Set<String> granted = new java.util.HashSet<>();
        Set<String> revoked = new java.util.HashSet<>();
        for (String l : lines()) {
            Matcher m = NODE.matcher(l);
            if (!m.matches() || !m.group(1).equals("default")) continue;
            if (m.group(2).equals("set") && !"false".equals(m.group(4))) granted.add(m.group(3));
            else revoked.add(m.group(3));
        }
        assertTrue(granted.contains("essentials.home") && granted.contains("essentials.sethome"), "one home stays");
        for (String node : FORBIDDEN_DEFAULT) assertFalse(granted.contains(node), node + " must not be granted to everyone");
        for (String node : List.of("essentials.sethome.multiple", "essentials.tpa", "essentials.tpaccept", "essentials.tpdeny", "essentials.tpacancel"))
            assertTrue(revoked.contains(node), node + " is revoked for servers set up before GAME-001");
    }

    @Test
    void everyLineIsALuckPermsConsoleCommand() throws IOException {
        for (String l : lines()) assertTrue(l.startsWith("lp "), l);
        assertEquals(PermissionSetup.sha256(lines()), PermissionSetup.sha256(lines()), "the marker hash is deterministic");
        assertEquals(64, PermissionSetup.sha256(lines()).length());
    }
}
