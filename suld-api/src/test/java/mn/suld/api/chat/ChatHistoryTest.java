package mn.suld.api.chat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryTest {

    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test
    void aViewerSeesOnlyWhatTheyCouldHear() {
        ChatHistory h = new ChatHistory(10);
        h.add(new ChatHistory.Line(1, a, "A", ChatChannel.GLOBAL, "сайн уу", null));
        h.add(new ChatHistory.Line(2, a, "A", ChatChannel.PARTY, "бүлэгт", Set.of(a, b)));
        h.add(new ChatHistory.Line(3, c, "C", ChatChannel.LOCAL, "ойр", Set.of(c)));
        assertEquals(2, h.view(ChatChannel.ALL, b, 10).size(), "global + the party line");
        assertEquals(1, h.view(ChatChannel.ALL, c, 10).size() - 1, "c: global + own local line");
        assertTrue(h.view(ChatChannel.PARTY, c, 10).isEmpty(), "a party line never leaks");
        assertEquals("бүлэгт", h.view(ChatChannel.PARTY, a, 10).get(0).text());
    }

    @Test
    void boundedPerChannelAndMergedByTime() {
        ChatHistory h = new ChatHistory(3);
        for (int i = 0; i < 10; i++) h.add(new ChatHistory.Line(i * 2, a, "A", ChatChannel.GLOBAL, "g" + i, null));
        h.add(new ChatHistory.Line(15, a, "A", ChatChannel.TRADE, "t", null));
        assertEquals(3, h.view(ChatChannel.GLOBAL, b, 99).size());
        var all = h.view(ChatChannel.ALL, b, 2);
        assertEquals(2, all.size());
        assertEquals("g9", all.get(1).text(), "newest last");
        assertThrows(IllegalArgumentException.class, () -> h.add(new ChatHistory.Line(0, a, "A", ChatChannel.ALL, "x", null)));
    }

    @Test
    void channelsParseFromMongolianAndShortForms() {
        assertEquals(ChatChannel.LOCAL, ChatChannel.parse("ойр").orElseThrow());
        assertEquals(ChatChannel.PARTY, ChatChannel.parse("Б").orElseThrow());
        assertEquals(ChatChannel.CLAN, ChatChannel.parse("clan").orElseThrow());
        assertEquals(ChatChannel.TRADE, ChatChannel.parse("t").orElseThrow());
        assertTrue(ChatChannel.parse("бүгд").isEmpty(), "ALL is a view, not a channel to speak in");
    }
}
