package mn.suld.api.party;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartyTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void creatorIsLeaderAndOnlyMember() {
        Party p = Party.create(a, 4);
        assertTrue(p.isLeader(a));
        assertEquals(1, p.size());
        assertEquals(PartyState.FORMING, p.state());
    }

    @Test
    void addMemberRespectsCapacityAndDuplicates() {
        Party p = Party.create(a, 2);
        assertTrue(p.addMember(b));
        assertFalse(p.addMember(b), "duplicate rejected");
        assertTrue(p.isFull());
        assertFalse(p.addMember(c), "full party rejects new members");
        assertEquals(2, p.size());
    }

    @Test
    void leaderLeavingPromotesNextMember() {
        Party p = Party.create(a, 4);
        p.addMember(b);
        p.addMember(c);
        assertTrue(p.removeMember(a));
        assertTrue(p.isLeader(b));
        assertEquals(2, p.size());
        assertFalse(p.isDisbanded());
    }

    @Test
    void lastMemberLeavingDisbands() {
        Party p = Party.create(a, 4);
        p.removeMember(a);
        assertTrue(p.isDisbanded());
        assertFalse(p.addMember(b), "disbanded party accepts nobody");
    }

    @Test
    void removingNonMemberIsNoOp() {
        Party p = Party.create(a, 4);
        assertFalse(p.removeMember(b));
        assertEquals(1, p.size());
    }

    @Test
    void setLeaderReordersAndRejectsNonMembers() {
        Party p = Party.create(a, 4);
        p.addMember(b);
        assertFalse(p.setLeader(c));
        assertTrue(p.setLeader(b));
        assertTrue(p.isLeader(b));
        assertTrue(p.contains(a));
        assertEquals(2, p.size());
    }

    @Test
    void dungeonStateTransitions() {
        Party p = Party.create(a, 4);
        p.enterDungeon();
        assertEquals(PartyState.IN_DUNGEON, p.state());
        p.exitDungeon();
        assertEquals(PartyState.FORMING, p.state());
        p.disband();
        p.enterDungeon();
        assertEquals(PartyState.DISBANDED, p.state(), "disbanded party cannot re-enter");
    }

    @Test
    void rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> Party.create(a, 0));
    }
}
