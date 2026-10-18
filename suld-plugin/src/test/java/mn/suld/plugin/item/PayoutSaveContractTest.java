package mn.suld.plugin.item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A world boss or a world event pays every winner in one tick. Writing each winner's player file there
 * ({@link PlayerDataSaves#now}, about 5 ms each) held that tick for a quarter of a second with fifty winners (forensic
 * audit 2026-10-18, P1). The payouts spread the writes with {@link PlayerDataSaves#soon}, one a tick. The payout needs
 * a running server, so the contract is checked on the source.
 */
class PayoutSaveContractTest {

    private static String main(String rel) throws IOException {
        for (String root : new String[]{"src/main/java/", "suld-plugin/src/main/java/"}) {
            Path p = Path.of(root + "mn/suld/plugin/" + rel);
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException(rel);
    }

    @Test
    void manyWinnerPayoutsNeverWriteEveryFileInOneTick() throws IOException {
        for (String rel : List.of("worldboss/WorldBossService.java", "worldevent/WorldEventService.java")) {
            String src = main(rel);
            assertFalse(src.contains("PlayerDataSaves.now("), rel + " writes a player file per winner in the payout tick");
            assertTrue(src.contains("PlayerDataSaves.soon(plugin, p)"), rel + " no longer writes the winners' files at all");
        }
    }
}
