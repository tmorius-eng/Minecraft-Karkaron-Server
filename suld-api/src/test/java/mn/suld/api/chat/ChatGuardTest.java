package mn.suld.api.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatGuardTest {

    private final ChatGuard guard = new ChatGuard(ChatGuard.Settings.defaults());
    private final UUID p = UUID.randomUUID();

    @Test
    void rateLimitAndRepeats() {
        assertTrue(guard.check(p, "сайн уу", 0).allowed());
        assertEquals(ChatGuard.Verdict.REPEAT, guard.check(p, "Сайн уу!!", 1_000).verdict());
        assertTrue(guard.check(p, "сайн уу", 40_000).allowed()); // the repeat window passed
        assertTrue(guard.check(p, "a1", 40_100).allowed());
        assertTrue(guard.check(p, "a2", 40_200).allowed());
        assertTrue(guard.check(p, "a3", 40_300).allowed());
        assertEquals(ChatGuard.Verdict.TOO_FAST, guard.check(p, "a4", 40_400).verdict());
        assertTrue(guard.check(p, "a5", 46_000).allowed());
    }

    @Test
    void advertisingIsBlockedButOwnDomainPasses() {
        assertTrue(guard.advertises("join play.otherserver.net now"));
        assertTrue(guard.advertises("ip: 123.45.67.89:25565"));
        assertTrue(guard.advertises("hypixel (dot) net"));
        assertFalse(guard.advertises("манай сайт suld.mn дээр"));
        assertFalse(guard.advertises("store.suld.mn"));
        assertFalse(guard.advertises("түвшин 1.5 дахин хурдан"));
        assertEquals(ChatGuard.Verdict.ADVERT, guard.check(UUID.randomUUID(), "go to mc.example.com", 0).verdict());
    }

    @Test
    void shoutingIsLoweredNotBlocked() {
        ChatGuard.Result r = guard.check(UUID.randomUUID(), "ХҮМҮҮС ЭНД ИРЭЭРЭЙ", 0);
        assertTrue(r.allowed());
        assertEquals("хүмүүс энд ирээрэй", r.text());
        assertEquals("OK GG", guard.check(UUID.randomUUID(), "OK GG", 0).text()); // too short to count
    }
}
