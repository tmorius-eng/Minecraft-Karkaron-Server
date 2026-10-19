package mn.suld.plugin.trade;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * External audit FIND-08: a mailbox file that cannot be removed after its stacks were delivered must never deliver
 * them a second time. The file steps are injected because the test runs as a user who can delete anything.
 */
class MailboxStuckFileTest {

    private static final Logger LOG = Logger.getLogger("test");
    private final UUID player = UUID.randomUUID();
    private final Set<UUID> stuck = new HashSet<>();

    @Test
    void theDeliveredFileIsRemoved() throws IOException {
        Path f = Files.createTempFile("mailbox", ".yml");
        String how = Mailbox.clearDelivered(f, player, stuck, LOG, () -> Files.deleteIfExists(f), () -> { throw new IOException("not reached"); });
        assertEquals("removed", how);
        assertFalse(Files.exists(f));
        assertTrue(stuck.isEmpty(), "a removed file leaves nothing stuck");
    }

    @Test
    void aFileThatCannotGoIsEmptiedInstead() throws IOException {
        Path f = Files.createTempFile("mailbox", ".yml");
        Files.writeString(f, "items:\n- AAAA\n");
        String how = Mailbox.clearDelivered(f, player, stuck, LOG, () -> { throw new IOException("busy"); }, () -> Files.writeString(f, "items: []\n"));
        assertEquals("emptied", how);
        assertEquals("items: []\n", Files.readString(f), "the stacks are gone from the file");
        assertTrue(stuck.isEmpty(), "an emptied file is safe to read again");
    }

    @Test
    void aFileThatCanNeitherGoNorBeEmptiedIsNeverReadAgain() {
        Path f = Path.of("/nowhere/" + player + ".yml");
        String how = Mailbox.clearDelivered(f, player, stuck, LOG, () -> { throw new IOException("read-only"); }, () -> { throw new IOException("read-only"); });
        assertEquals("stuck", how);
        assertTrue(stuck.contains(player), "deliver() returns 0 for a stuck player until the restart");
    }
}
