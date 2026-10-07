package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;
import mn.suld.api.persistence.InMemoryDeathRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathLockTest {

    static final DeathSettings S = DeathSettings.defaults();

    @Test
    void geometricCurveMatchesTheSpec() {
        assertEquals(5, DeathLock.minutes(S, 1, 0), 1e-9);
        assertEquals(1440, DeathLock.minutes(S, 60, 0), 1e-6);
        assertEquals(31, DeathLock.minutes(S, 20, 0), 1.0);      // docs/DEATH_AND_RECOVERY.md: 31 min at 20
        assertEquals(3.5 * 60, DeathLock.minutes(S, 40, 0), 6);   // 3.5 h at 40
        assertEquals(1440, DeathLock.minutes(S, 30, 2), 1e-9, "Ascension = the top of the curve");
        double prev = 0;
        for (int lv = 1; lv <= 60; lv++) {
            double m = DeathLock.minutes(S, lv, 0);
            assertTrue(m > prev, "strictly growing at " + lv);
            prev = m;
        }
    }

    @Test
    void otherCurvesAndBounds() {
        DeathSettings lin = new DeathSettings(true, 30, 0.05, 0.25, true, 0.25, true, DeathLock.Curve.LINEAR, 5, 1440, 0.05, 0.15, 180);
        assertEquals(5 + (1440 - 5) * 29 / 59.0, DeathLock.minutes(lin, 30, 0), 1e-9);
        DeathSettings fixed = new DeathSettings(true, 30, 0.05, 0.25, true, 0.25, true, DeathLock.Curve.FIXED, 0.5, 0.5, 0.05, 0.15, 180);
        assertEquals(30_000, DeathLock.millis(fixed, 50, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DeathSettings(true, 30, 0, 0, true, 0, true, DeathLock.Curve.LINEAR, 10, 5, 0.05, 0.15, 180));
        assertEquals("3 цаг 05 мин", DeathLock.format(3 * 3_600_000 + 5 * 60_000));
        assertEquals("12 мин 30 сек", DeathLock.format(12 * 60_000 + 30_000));
    }

    @Test
    void woundStacksCapsAndHeals() {
        Wound w = Wound.NONE.add(S).add(S).add(S).add(S);
        assertEquals(3, w.stacks(), "capped at 15 % / 5 %");
        assertEquals(0.85, w.factor(S), 1e-9);
        assertEquals(15, w.percent(S));
        Wound h = w.heal(S, 179);
        assertEquals(3, h.stacks());
        h = h.heal(S, 1 + 180 + 30);
        assertEquals(1, h.stacks());
        assertEquals(30, h.healMinutes(), 1e-9);
        assertEquals(Wound.NONE, h.heal(S, 1000));
        assertEquals(1.0, Wound.NONE.factor(S));
    }

    @Test
    void repositoryKeepsOneOpenLockAndCasUpdates() {
        InMemoryDeathRepository repo = new InMemoryDeathRepository();
        UUID p = UUID.randomUUID();
        DeathRecord a = repo.insert(locked(p, 1000)).join();
        assertEquals(1, a.seq());
        assertEquals(1, a.version());
        CompletionException ex = assertThrows(CompletionException.class, () -> repo.insert(locked(p, 2000)).join());
        assertTrue(ex.getMessage().contains("already locked"));
        DeathRecord closed = a.closed(DeathRecord.State.RECOVERED, 5000, 1);
        assertTrue(repo.update(closed).join());
        assertFalse(repo.update(closed).join(), "a stale version is refused (no double recovery)");
        assertTrue(repo.openLock(p).join().isEmpty());
        DeathRecord b = repo.insert(locked(p, 9000)).join();
        assertEquals(2, b.seq());
        assertEquals(2, repo.recent(p, 5).join().size());
        assertEquals(b.deathId(), repo.recent(p, 5).join().get(0).deathId(), "newest first");
        repo.saveWound(p, new Wound(2, 10)).join();
        assertEquals(new Wound(2, 10), repo.wound(p).join());
    }

    static DeathRecord locked(UUID p, long at) {
        return new DeathRecord(UUID.randomUUID(), p, 0, at, at + 60_000, "world", 1, 64, 2, "ENTITY_ATTACK:WOLF", 12, 0, 0,
                DeathRecord.State.LOCKED, null, 0);
    }
}
