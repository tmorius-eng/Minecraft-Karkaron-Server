package mn.suld.api.relic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RelicValidatorTest {

    private static final UUID ITEM = UUID.randomUUID();
    private static final UUID BEARER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final ShrineLocation SHRINE = new ShrineLocation("world", 0, 64, 0);

    private static Map<String, RelicRecord> owned(long version) {
        return Map.of("relic.test", new RelicRecord("relic.test", ITEM, RelicState.OWNED, BEARER, "B", null, version, SHRINE));
    }

    private static RelicValidator.Found f(int slot, UUID item, long gen, int amount) {
        return new RelicValidator.Found(slot, "relic.test", item, gen, amount);
    }

    @Test
    void bearerKeepsExactlyOneCurrentCopy() {
        RelicValidator.Plan p = RelicValidator.plan(BEARER, List.of(f(3, ITEM, 5, 1)), owned(5));
        assertTrue(p.clean());
    }

    @Test
    void dupedCopiesInTheBearersInventoryAreRemoved() {
        RelicValidator.Plan p = RelicValidator.plan(BEARER, List.of(f(1, ITEM, 5, 1), f(2, ITEM, 5, 1)), owned(5));
        assertEquals(List.of(new RelicValidator.Removal(2, "relic.test", RelicValidator.Reason.EXTRA_COPY)), p.removals());
        assertTrue(p.issue().isEmpty());
    }

    @Test
    void stackedCopyIsTrimmedToOne() {
        RelicValidator.Plan p = RelicValidator.plan(BEARER, List.of(f(0, ITEM, 5, 2)), owned(5));
        assertEquals(List.of(0), p.fixAmount());
        assertTrue(p.removals().isEmpty());
    }

    @Test
    void staleGenerationFromBeforeAnOwnershipChangeIsDestroyedAndReissued() {
        RelicValidator.Plan p = RelicValidator.plan(BEARER, List.of(f(4, ITEM, 3, 1)), owned(5));
        assertEquals(RelicValidator.Reason.STALE_GENERATION, p.removals().get(0).reason());
        assertEquals(Set.of("relic.test"), p.issue(), "real bearer gets a fresh copy");
    }

    @Test
    void copyInSomeoneElsesInventoryIsDestroyed() {
        RelicValidator.Plan p = RelicValidator.plan(OTHER, List.of(f(0, ITEM, 5, 1)), owned(5));
        assertEquals(RelicValidator.Reason.NOT_BEARER, p.removals().get(0).reason());
        assertTrue(p.issue().isEmpty());
    }

    @Test
    void copyOfAnUnclaimedRelicIsDestroyed() {
        Map<String, RelicRecord> free = Map.of("relic.test",
                new RelicRecord("relic.test", ITEM, RelicState.UNCLAIMED, null, null, null, 6, SHRINE));
        assertEquals(RelicValidator.Reason.NOT_BEARER,
                RelicValidator.plan(BEARER, List.of(f(0, ITEM, 5, 1)), free).removals().get(0).reason());
    }

    @Test
    void forgedItemUuidIsCounterfeit() {
        RelicValidator.Plan p = RelicValidator.plan(BEARER, List.of(f(0, UUID.randomUUID(), 5, 1)), owned(5));
        assertEquals(RelicValidator.Reason.COUNTERFEIT_UUID, p.removals().get(0).reason());
        assertEquals(Set.of("relic.test"), p.issue());
    }

    @Test
    void unknownRelicKeyIsRemoved() {
        RelicValidator.Found fake = new RelicValidator.Found(0, "relic.fake", ITEM, 0, 1);
        assertEquals(RelicValidator.Reason.UNKNOWN_RELIC,
                RelicValidator.plan(BEARER, List.of(fake), owned(5)).removals().get(0).reason());
    }

    @Test
    void relicsNeverLiveInContainersEvenTheRealOne() {
        RelicValidator.Plan p = RelicValidator.plan(null, List.of(f(7, ITEM, 5, 1)), owned(5));
        assertEquals(RelicValidator.Reason.IN_CONTAINER, p.removals().get(0).reason());
        assertTrue(p.issue().isEmpty(), "containers never trigger issuing");
    }

    @Test
    void bearerWithoutCopyGetsOneIssued() {
        assertEquals(Set.of("relic.test"), RelicValidator.plan(BEARER, List.of(), owned(5)).issue());
    }
}
