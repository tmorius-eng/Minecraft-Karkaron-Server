package mn.suld.api.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartyRegistryTest {

    /** Mutable clock so invite expiry is deterministic. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private TestClock clock;
    private PartyRegistry reg;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        reg = new PartyRegistry(clock, 3, Duration.ofSeconds(60));
    }

    @Test
    void inviteAcceptFormsParty() {
        assertEquals(PartyResult.INVITED, reg.invite(a, b).result());
        assertTrue(reg.hasPendingInvite(b));
        PartyRegistry.Action joined = reg.accept(b);
        assertEquals(PartyResult.JOINED, joined.result());
        assertEquals(2, joined.party().size());
        assertTrue(reg.partyOf(a).isPresent());
        assertSame(reg.partyOf(a).get(), reg.partyOf(b).get());
        assertFalse(reg.hasPendingInvite(b));
    }

    @Test
    void inviteExpires() {
        reg.invite(a, b);
        clock.now = clock.now.plusSeconds(61);
        assertFalse(reg.hasPendingInvite(b));
        assertEquals(PartyResult.INVITE_EXPIRED, reg.accept(b).result());
        assertTrue(reg.partyOf(b).isEmpty());
    }

    @Test
    void acceptWithoutInviteFails() {
        assertEquals(PartyResult.NO_INVITE, reg.accept(b).result());
    }

    @Test
    void cannotInviteSelfOrSomeoneInAParty() {
        assertEquals(PartyResult.SELF_TARGET, reg.invite(a, a).result());
        reg.invite(a, b);
        reg.accept(b);
        assertEquals(PartyResult.TARGET_IN_PARTY, reg.invite(c, b).result());
    }

    @Test
    void onlyLeaderCanInviteAndKick() {
        reg.invite(a, b);
        reg.accept(b);
        assertEquals(PartyResult.NOT_LEADER, reg.invite(b, c).result());
        assertEquals(PartyResult.NOT_LEADER, reg.kick(b, a).result());
        assertEquals(PartyResult.KICKED, reg.kick(a, b).result());
        assertTrue(reg.partyOf(b).isEmpty());
    }

    @Test
    void capacityEnforcedAtInviteAndAccept() {
        reg.invite(a, b); reg.accept(b);
        reg.invite(a, c); reg.accept(c); // 3/3
        assertEquals(PartyResult.PARTY_FULL, reg.invite(a, d).result());

        // Two pending invites racing for the last slot: second accept fails.
        PartyRegistry r = new PartyRegistry(clock, 2, Duration.ofSeconds(60));
        r.invite(a, b);
        r.invite(a, c);
        assertEquals(PartyResult.JOINED, r.accept(b).result());
        assertEquals(PartyResult.PARTY_FULL, r.accept(c).result());
    }

    @Test
    void leaderLeavePromotesAndLastLeaveDropsParty() {
        reg.invite(a, b); reg.accept(b);
        reg.leave(a);
        assertTrue(reg.partyOf(b).get().isLeader(b));
        assertEquals(1, reg.partyCount());
        reg.leave(b);
        assertEquals(0, reg.partyCount());
        assertEquals(PartyResult.NOT_IN_PARTY, reg.leave(b).result());
    }

    @Test
    void promoteTransfersLeadership() {
        reg.invite(a, b); reg.accept(b);
        assertEquals(PartyResult.PROMOTED, reg.promote(a, b).result());
        assertTrue(reg.partyOf(a).get().isLeader(b));
        assertEquals(PartyResult.NOT_LEADER, reg.promote(a, b).result());
        assertEquals(PartyResult.NOT_A_MEMBER, reg.promote(b, c).result());
    }

    @Test
    void partyInDungeonRejectsInvitesAndJoins() {
        reg.invite(a, b); reg.accept(b);
        reg.invite(a, c);                     // pending before the run starts
        reg.partyOf(a).get().enterDungeon();
        assertEquals(PartyResult.PARTY_BUSY, reg.invite(a, d).result());
        assertEquals(PartyResult.PARTY_BUSY, reg.accept(c).result());
    }

    @Test
    void disbandClearsMembershipAndInvites() {
        reg.invite(a, b); reg.accept(b);
        reg.invite(a, c);
        Party p = reg.partyOf(a).get();
        reg.disband(p);
        assertTrue(reg.partyOf(a).isEmpty());
        assertTrue(reg.partyOf(b).isEmpty());
        assertEquals(PartyResult.NO_INVITE, reg.accept(c).result());
        assertEquals(0, reg.partyCount());
    }

    @Test
    void ensurePartyCreatesSoloPartyOnce() {
        Party first = reg.ensureParty(a);
        assertTrue(first.isLeader(a));
        assertEquals(1, first.size());
        assertSame(first, reg.ensureParty(a));
        assertEquals(1, reg.partyCount());
    }
}
