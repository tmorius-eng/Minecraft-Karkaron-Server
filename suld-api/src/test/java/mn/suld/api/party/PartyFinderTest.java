package mn.suld.api.party;

import mn.suld.api.clazz.PlayerClass;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyFinderTest {

    @Test
    void joiningNeedsARecruitingPartyWithRoom() {
        PartyRegistry r = new PartyRegistry(Clock.systemUTC(), 3, Duration.ofSeconds(60));
        UUID leader = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        assertEquals(PartyResult.PARTY_GONE, r.join(a, leader).result(), "no party yet");
        Party p = r.ensureParty(leader);
        assertEquals(PartyResult.NOT_RECRUITING, r.join(a, leader).result());
        p.recruit(true, "dungeon.khasar_den");
        p.role(leader, PartyRole.TANK);
        assertEquals(PartyResult.SELF_TARGET, r.join(leader, leader).result());
        assertEquals(PartyResult.JOINED, r.join(a, leader).result());
        assertEquals(PartyResult.ALREADY_IN_PARTY, r.join(a, leader).result());
        assertTrue(PartyFinder.listings(r.all(), null).get(0).missingRoles().containsAll(Set.of(PartyRole.HEALER, PartyRole.DPS)));
        assertEquals(PartyResult.JOINED, r.join(b, leader).result());
        assertFalse(p.isRecruiting(), "a full party leaves the board");
        assertEquals(PartyResult.NOT_RECRUITING, r.join(c, leader).result());
        p.recruit(true, "");
        assertEquals(PartyResult.PARTY_FULL, r.join(c, leader).result());
        p.enterDungeon();
        r.leave(b);
        p.recruit(true, "");
        assertEquals(PartyResult.PARTY_BUSY, r.join(c, leader).result());
        assertTrue(PartyFinder.listings(r.all(), null).isEmpty(), "busy parties are not listed");
    }

    @Test
    void listingsFilterAndOrder() {
        PartyRegistry r = new PartyRegistry(Clock.systemUTC(), 4, Duration.ofSeconds(60));
        UUID l1 = UUID.randomUUID(), l2 = UUID.randomUUID(), l3 = UUID.randomUUID();
        Party p1 = r.ensureParty(l1);
        p1.recruit(true, "dungeon.govi_bulsh");
        p1.role(l1, PartyRole.TANK);
        Party p2 = r.ensureParty(l2);
        p2.recruit(true, "dungeon.govi_bulsh");
        p2.role(l2, PartyRole.TANK);
        r.join(UUID.randomUUID(), l2);
        p2.role(p2.firstNonLeader().orElseThrow(), PartyRole.HEALER);
        Party p3 = r.ensureParty(l3);
        p3.recruit(true, "dungeon.khasar_den");
        List<PartyFinder.Listing> all = PartyFinder.listings(r.all(), null);
        assertEquals(3, all.size());
        assertEquals(l3, all.get(0).leader(), "the party missing every role comes first");
        assertEquals(l1, all.get(1).leader(), "then the one missing two roles");
        assertEquals(l2, all.get(2).leader());
        assertEquals(3, all.get(1).freeSlots());
        List<PartyFinder.Listing> tomb = PartyFinder.listings(r.all(), "dungeon.govi_bulsh");
        assertEquals(2, tomb.size());
        assertEquals(PartyRole.TANK, PartyRole.defaultFor(PlayerClass.BAATAR));
        assertEquals(PartyRole.HEALER, PartyRole.defaultFor(PlayerClass.BOO));
        assertEquals(PartyRole.DPS, PartyRole.parse("довтлогч"));
        r.leave(l1);
        assertTrue(p1.roles().isEmpty(), "a leaver drops their role");
    }
}
