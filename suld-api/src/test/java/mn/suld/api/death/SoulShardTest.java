package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SoulShardTest {

    private final DeathSettings s = DeathSettings.defaults();

    @Test
    void aShardIsBoundFadesAfterAWeekAndHealsAThirdOfAStep() {
        UUID p = UUID.randomUUID();
        SoulShard sh = SoulShard.won(SoulShard.Tier.FAINT, p, 0);
        assertEquals(7 * 86_400_000L, sh.expiresAt());
        Wound w = new Wound(2, 150);
        assertTrue(sh.usable(p, 1_000, w));
        assertFalse(sh.usable(UUID.randomUUID(), 1_000, w), "bound to its winner");
        assertFalse(sh.usable(p, sh.expiresAt(), w), "faded");
        assertFalse(sh.usable(p, 1_000, Wound.NONE), "no wound to heal");
        Wound healed = sh.apply(w, s);
        assertEquals(1, healed.stacks(), "150 + 60 ≥ 180: one step healed");
        assertEquals(30, healed.healMinutes(), 1e-9);
        assertEquals(3, SoulShard.Tier.RADIANT.healMinutes() / SoulShard.Tier.BRIGHT.healMinutes());
    }
}
