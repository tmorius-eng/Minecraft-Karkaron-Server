package mn.suld.plugin.branding;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveMotdTest {

    private static final ZoneId UB = ZoneId.of("Asia/Ulaanbaatar");
    private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z"); // Saturday 16:00 in Ulaanbaatar

    @Test
    void aStandingBossBeatsEverything() {
        String line = LiveMotd.pick(Optional.of("Хөх Чоно"), Optional.of(NOW.plusSeconds(3600)), Optional.of("X"), Optional.of(NOW.plusSeconds(60)), NOW, UB, "static");
        assertEquals("⚔ «Хөх Чоно» босоод байна — ирээрэй!", line);
    }

    @Test
    void anOpenVoteNamesItsClose() {
        Instant closes = Instant.parse("2026-10-25T12:00:00Z"); // Sunday 20:00 UB
        assertEquals("✦ Их Хуралдай: санал хураалт Ня 20:00 хүртэл",
                LiveMotd.pick(Optional.empty(), Optional.of(closes), Optional.of("X"), Optional.of(NOW.plusSeconds(60)), NOW, UB, "static"));
        assertEquals("static", LiveMotd.pick(Optional.empty(), Optional.of(NOW.minusSeconds(1)), Optional.empty(), Optional.empty(), NOW, UB, "static"),
                "a vote that already closed is nothing");
    }

    @Test
    void aBossWithinSixHoursIsAnnouncedLaterOnesAreNot() {
        Instant at = Instant.parse("2026-10-24T12:00:00Z"); // Saturday (Бямба) 20:00 UB, four hours away
        assertEquals("⚔ «Хөх Чоно» Бя 20:00 босно", LiveMotd.pick(Optional.empty(), Optional.empty(), Optional.of("Хөх Чоно"), Optional.of(at), NOW, UB, "static"));
        assertEquals("static", LiveMotd.pick(Optional.empty(), Optional.empty(), Optional.of("Хөх Чоно"), Optional.of(NOW.plusSeconds(7 * 3600)), NOW, UB, "static"));
    }

    @Test
    void theWeekdayAbbreviationsAreMongolian() {
        assertEquals("Да 09:05", LiveMotd.when(Instant.parse("2026-10-19T01:05:00Z"), UB));
        assertEquals("Ня 00:00", LiveMotd.when(Instant.parse("2026-10-24T16:00:00Z"), UB));
    }
}
