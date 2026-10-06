package mn.suld.api.relic;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RelicRulesAndHintsTest {

    private static final RelicDefinition DEF = new RelicDefinition("relic.test", "K", "lore", "minecraft:nether_star", 1, 10, 0.25);
    private static final ShrineLocation SHRINE = new ShrineLocation("world", 0, 64, 0);
    private static final RelicRecord FREE = new RelicRecord("relic.test", UUID.randomUUID(), RelicState.UNCLAIMED, null, null, null, 0, SHRINE);

    @Test
    void claimRequirements() {
        assertEquals(Optional.empty(), RelicRules.checkClaim(FREE, DEF, true, 10, 0));
        assertEquals(RelicRules.ClaimDenial.LEVEL_TOO_LOW, RelicRules.checkClaim(FREE, DEF, true, 9, 0).orElseThrow());
        assertEquals(RelicRules.ClaimDenial.NO_CLASS, RelicRules.checkClaim(FREE, DEF, false, 20, 0).orElseThrow());
        assertEquals(RelicRules.ClaimDenial.ALREADY_BEARER, RelicRules.checkClaim(FREE, DEF, true, 20, 1).orElseThrow());
        RelicRecord noShrine = new RelicRecord("relic.test", FREE.itemUuid(), RelicState.UNCLAIMED, null, null, null, 0, null);
        assertEquals(RelicRules.ClaimDenial.NO_SHRINE, RelicRules.checkClaim(noShrine, DEF, true, 20, 0).orElseThrow());
        RelicRecord taken = new RelicRecord("relic.test", FREE.itemUuid(), RelicState.OWNED, UUID.randomUUID(), "x", null, 1, SHRINE);
        assertEquals(RelicRules.ClaimDenial.NOT_AVAILABLE, RelicRules.checkClaim(taken, DEF, true, 20, 0).orElseThrow());
    }

    @Test
    void deathOutcomes() {
        UUID bearer = UUID.randomUUID(), killer = UUID.randomUUID();
        assertEquals(RelicRules.DeathOutcome.SEIZE_BY_KILLER, RelicRules.onBearerDeath(bearer, killer, 0));
        assertEquals(RelicRules.DeathOutcome.RETURN_TO_SHRINE, RelicRules.onBearerDeath(bearer, null, 0), "mob/void/fall");
        assertEquals(RelicRules.DeathOutcome.RETURN_TO_SHRINE, RelicRules.onBearerDeath(bearer, bearer, 0), "suicide farming");
        assertEquals(RelicRules.DeathOutcome.RETURN_TO_SHRINE, RelicRules.onBearerDeath(bearer, killer, 1), "killer already bears one");
    }

    @Test
    void transitionsKeepOwnerInvariant() {
        assertThrows(IllegalArgumentException.class, () -> new RelicTransition("relic.test", 0, RelicState.OWNED, null, null,
                RelicEvent.DISCOVERED, "a", ""));
        assertThrows(IllegalArgumentException.class, () -> new RelicTransition("relic.test", 0, RelicState.UNCLAIMED,
                UUID.randomUUID(), "x", RelicEvent.RETURNED, "a", ""));
        assertThrows(IllegalArgumentException.class, () -> new RelicDefinition("bad key", "K", "", "m", 1, 1, 0));
    }

    @Test
    void hintsUseMinecraftAxesAndCoarseBands() {
        assertEquals("хойд", RelicHints.direction(0, 0, 0, -500), "north is -Z");
        assertEquals("зүүн", RelicHints.direction(0, 0, 500, 0), "east is +X");
        assertEquals("өмнөд", RelicHints.direction(0, 0, 0, 500));
        assertEquals("баруун", RelicHints.direction(0, 0, -500, 0));
        assertEquals("зүүн хойд", RelicHints.direction(0, 0, 400, -400));
        assertEquals("баруун өмнөд", RelicHints.direction(0, 0, -400, 400));
        assertEquals("<250", RelicHints.distanceBand(100));
        assertEquals("~750", RelicHints.distanceBand(800));
        assertTrue(RelicHints.describe(0, 0, 3, 3).contains("дэргэд"));
        assertEquals("~1000 блок, хойд зүгт", RelicHints.describe(0, 0, 0, -1010));
    }
}
