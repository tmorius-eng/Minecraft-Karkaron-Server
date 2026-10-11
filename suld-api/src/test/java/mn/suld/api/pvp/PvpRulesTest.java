package mn.suld.api.pvp;

import mn.suld.api.config.PvpSettings;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner decision 2026-10-11: PvE-only, players fight only by consent. */
class PvpRulesTest {

    @Test
    void theSteppeIsPeacefulUnlessBothAgreed() {
        assertEquals(PvpRules.Verdict.DENIED_NO_CONSENT, PvpRules.judge(false, false, false, false, false));
        assertEquals(PvpRules.Verdict.ALLOWED_DUEL, PvpRules.judge(false, false, true, false, false));
        assertEquals(PvpRules.Verdict.ALLOWED_ARENA, PvpRules.judge(false, false, false, true, false));
        assertEquals(PvpRules.Verdict.ALLOWED_WILD, PvpRules.judge(false, false, false, false, true), "the old open steppe is a config switch");
        assertFalse(PvpRules.reason(PvpRules.Verdict.DENIED_NO_CONSENT).isBlank(), "the attacker is told how to duel");
    }

    @Test
    void theCityAndSoulsWinOverEveryConsent() {
        assertEquals(PvpRules.Verdict.DENIED_CITY, PvpRules.judge(true, false, true, true, true));
        assertEquals(PvpRules.Verdict.DENIED_SOUL, PvpRules.judge(false, true, true, true, true));
    }

    @Test
    void arenasParseAndContain() {
        List<PvpSettings.Arena> arenas = PvpSettings.parseArenas(List.of("world 100 100 -20 -20", "bad line", "world:0:0:x:0"));
        assertEquals(1, arenas.size(), "malformed lines are skipped");
        PvpSettings s = new PvpSettings(false, true, 60, 10, arenas);
        assertTrue(s.inArena("world", 0, 0));
        assertTrue(s.inArena("world", -20, 100), "corners are normalised");
        assertFalse(s.inArena("world", 101, 0));
        assertFalse(s.inArena("nether", 0, 0));
        assertNull(PvpSettings.Arena.parse("world 1 2 3"));
    }

    @Test
    void duelsNeedAnAnswerAndLapse() {
        Instant t0 = Instant.parse("2026-10-11T12:00:00Z");
        Clock[] clock = {Clock.fixed(t0, ZoneOffset.UTC)};
        DuelRegistry r = new DuelRegistry(new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return clock[0].instant(); }
        }, Duration.ofSeconds(60), Duration.ofMinutes(10));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

        assertEquals(DuelRegistry.Result.SELF, r.challenge(a, a));
        assertEquals(DuelRegistry.Result.CHALLENGED, r.challenge(a, b));
        assertEquals(DuelRegistry.Result.ALREADY_PENDING, r.challenge(a, b));
        assertFalse(r.dueling(a, b), "a challenge is not consent yet");
        assertEquals(a, r.pendingFrom(b).orElseThrow());

        clock[0] = Clock.fixed(t0.plusSeconds(61), ZoneOffset.UTC);
        assertTrue(r.accept(b).isEmpty(), "the challenge lapsed after 60 s");

        assertEquals(DuelRegistry.Result.CHALLENGED, r.challenge(a, b));
        assertEquals(DuelRegistry.Result.ACCEPTED, r.challenge(b, a), "a challenge each way is an acceptance");
        assertTrue(r.dueling(a, b));
        assertTrue(r.dueling(b, a));
        assertFalse(r.dueling(a, c));
        assertEquals(DuelRegistry.Result.TARGET_BUSY, r.challenge(c, a), "one duel per player");

        clock[0] = Clock.fixed(t0.plusSeconds(61 + 601), ZoneOffset.UTC);
        assertFalse(r.dueling(a, b), "duels lapse after 10 minutes");
        assertEquals(1, r.expire().size());

        assertEquals(DuelRegistry.Result.CHALLENGED, r.challenge(a, b));
        assertEquals(a, r.accept(b).orElseThrow());
        assertTrue(r.end(b).isPresent(), "death or /duel end closes it for both");
        assertFalse(r.dueling(a, b));
        assertTrue(r.end(a).isEmpty());
    }
}
