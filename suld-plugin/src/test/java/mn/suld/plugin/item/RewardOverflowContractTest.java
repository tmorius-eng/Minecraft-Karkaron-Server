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
 * A full bag never puts a reward or a displaced item on the ground (research gap GAP-02/03/04): a dungeon hall is
 * cleared seconds after the win, and anything else on the ground can be taken or despawn. What does not fit goes to
 * the mailbox. The stacks need a running server, so the contract is checked on the source.
 */
class RewardOverflowContractTest {

    private static String main(String rel) throws IOException {
        for (String root : new String[]{"src/main/java/", "suld-plugin/src/main/java/"}) {
            Path p = Path.of(root + "mn/suld/plugin/" + rel);
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException(rel);
    }

    @Test
    void rewardAndDisplacementPathsNeverDropOnTheGround() throws IOException {
        for (String rel : List.of("dungeon/DungeonService.java", "worldboss/WorldBossService.java",
                "worldevent/WorldEventService.java", "relic/RelicService.java", "gui/ClassSelectionGui.java",
                "clazz/ClassChangeService.java")) {
            assertFalse(main(rel).contains("dropItemNaturally"), rel + " drops an item on the ground");
        }
    }

    @Test
    void theOverflowGoesToTheMailbox() throws IOException {
        for (String rel : List.of("dungeon/DungeonService.java", "worldboss/WorldBossService.java",
                "worldevent/WorldEventService.java", "relic/RelicService.java", "gui/ClassSelectionGui.java")) {
            assertTrue(main(rel).contains(".keepOverflow("), rel + " keeps its overflow");
        }
        assertTrue(main("clazz/ClassChangeService.java").contains(".giveOrKeep("));
        assertTrue(main("SuldPlugin.java").contains("soulbound().overflow("), "the soulbound give-back is wired to the mailbox");
    }

    @Test
    void classSelectionAndRelicIssueWriteThePlayerFile() throws IOException {
        assertTrue(main("gui/ClassSelectionGui.java").contains("PlayerDataSaves.now(player)"));
        assertTrue(main("relic/RelicService.java").contains("PlayerDataSaves.now(player)"));
    }
}
