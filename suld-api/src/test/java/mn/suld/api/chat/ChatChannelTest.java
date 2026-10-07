package mn.suld.api.chat;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChatChannelTest {

    @Test
    void parsesMongolianLabelsTagsAndShortcuts() {
        assertEquals(Optional.of(ChatChannel.LOCAL), ChatChannel.parse("Ойр"));
        assertEquals(Optional.of(ChatChannel.LOCAL), ChatChannel.parse(" l "));
        assertEquals(Optional.of(ChatChannel.GLOBAL), ChatChannel.parse("нийт"));
        assertEquals(Optional.of(ChatChannel.GLOBAL), ChatChannel.parse("g"));
        assertEquals(Optional.of(ChatChannel.PARTY), ChatChannel.parse("бүлэг"));
        assertEquals(Optional.of(ChatChannel.CLAN), ChatChannel.parse("ов"));
        assertEquals(Optional.of(ChatChannel.TRADE), ChatChannel.parse("trade"));
        assertTrue(ChatChannel.parse("бүгд").isEmpty(), "Бүгд is a view, one cannot speak in it");
        assertTrue(ChatChannel.parse("").isEmpty());
        assertTrue(ChatChannel.parse(null).isEmpty());
        assertTrue(ChatChannel.parse("xyz").isEmpty());
    }

    @Test
    void tagsAreUniqueAndShort() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ChatChannel c : ChatChannel.values()) {
            if (!c.speakable()) continue;
            assertTrue(c.tag().length() <= 2, c + " tag too long");
            assertTrue(seen.add(c.tag()), "duplicate tag " + c.tag());
        }
    }
}
