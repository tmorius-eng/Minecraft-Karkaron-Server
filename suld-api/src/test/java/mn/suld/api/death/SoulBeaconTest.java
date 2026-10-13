package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SoulBeaconTest {

    private final DeathSettings s = DeathSettings.defaults();
    private final UUID p = UUID.randomUUID();

    @Test
    void itStandsTenMinutesAndGivesBackSeventyPercent() {
        SoulBeacon b = SoulBeacon.of(UUID.randomUUID(), p, "world", 100, 64, -20, 1_001, 5_000, s);
        assertEquals(5_000 + 600_000, b.expiresAt());
        assertEquals(700, b.refund(s), "floor(1001 × 0.7)");
        assertTrue(b.open(5_000));
        assertTrue(b.open(604_999));
        assertFalse(b.open(605_000), "gone after ten minutes");
        assertEquals(300_000, b.remaining(305_000));
        assertEquals(0, b.remaining(700_000));
    }

    @Test
    void itIsReachedWithinThreeBlocksInTheSameWorld() {
        SoulBeacon b = SoulBeacon.of(UUID.randomUUID(), p, "world", 100, 64, -20, 500, 0, s);
        assertTrue(b.reached("world", 100.5, 64, -19.5));
        assertTrue(b.reached("world", 102.5, 66, -19.5), "2 blocks out, 2 up");
        assertFalse(b.reached("world", 104.5, 64, -19.5), "4 blocks out");
        assertFalse(b.reached("world", 100.5, 70, -19.5), "6 blocks up");
        assertFalse(b.reached("suld_halls", 100.5, 64, -19.5), "another world");
    }

    @Test
    void itIsClaimedOnce() {
        SoulBeacon b = SoulBeacon.of(UUID.randomUUID(), p, "world", 0, 64, 0, 500, 0, s);
        SoulBeacon c = b.claim(1_000);
        assertTrue(c.claimed());
        assertFalse(c.open(2_000));
        assertThrows(IllegalStateException.class, () -> c.claim(2_000));
        assertThrows(IllegalStateException.class, () -> b.claim(600_000), "a faded beacon");
    }

    @Test
    void badBeaconsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SoulBeacon(UUID.randomUUID(), p, "w", 0, 0, 0, -1, 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new SoulBeacon(UUID.randomUUID(), p, "w", 0, 0, 0, 1, 10, 5, null));
        assertThrows(NullPointerException.class, () -> new SoulBeacon(null, p, "w", 0, 0, 0, 1, 0, 1, null));
    }
}
