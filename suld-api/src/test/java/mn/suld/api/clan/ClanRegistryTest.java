package mn.suld.api.clan;

import mn.suld.api.persistence.InMemoryClanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClanRegistryTest {

    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private TestClock clock;
    private ClanRegistry reg;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        reg = new ClanRegistry(clock, Duration.ofMinutes(5));
    }

    private Clan clanWith(UUID leader, UUID... members) {
        Clan clan = reg.create(leader, "lead", "Хүрэн Чоно", "XЧ").clan();
        for (UUID m : members) {
            reg.invite(leader, m);
            assertEquals(ClanResult.JOINED, reg.accept(m, "m").result());
        }
        return clan;
    }

    @Test
    void createValidatesAndNormalises() {
        assertEquals(ClanResult.INVALID_NAME, reg.create(a, "a", "ab", "AB").result());
        assertEquals(ClanResult.INVALID_NAME, reg.create(a, "a", "bad$name", "AB").result());
        assertEquals(ClanResult.INVALID_TAG, reg.create(a, "a", "Good Name", "A").result());
        assertEquals(ClanResult.INVALID_TAG, reg.create(a, "a", "Good Name", "TOOLONG").result());
        ClanRegistry.Action ok = reg.create(a, "a", "  Хөх   Тэнгэр  ", "хт1");
        assertEquals(ClanResult.CREATED, ok.result());
        assertEquals("Хөх Тэнгэр", ok.clan().name());
        assertEquals("ХТ1", ok.clan().tag(), "tags are upper-cased, Cyrillic included");
        assertEquals(ClanRank.LEADER, ok.clan().member(a).orElseThrow().rank());
        assertEquals(ClanResult.ALREADY_IN_CLAN, reg.create(a, "a", "Other", "OT").result());
    }

    @Test
    void namesAndTagsAreUniqueCaseInsensitively() {
        reg.create(a, "a", "Хөх Тэнгэр", "ХТ");
        assertEquals(ClanResult.NAME_TAKEN, reg.create(b, "b", "хөх тэнгэр", "QQ").result());
        assertEquals(ClanResult.TAG_TAKEN, reg.create(b, "b", "Another", "хт").result());
        assertTrue(reg.byTag("хт").isPresent());
    }

    @Test
    void inviteAcceptAndExpiry() {
        clanWith(a);
        assertEquals(ClanResult.INVITED, reg.invite(a, b).result());
        assertEquals("XЧ", reg.pendingInviteTag(b).orElseThrow());
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        assertEquals(ClanResult.INVITE_EXPIRED, reg.accept(b, "b").result());
        assertTrue(reg.clanOf(b).isEmpty());
        assertEquals(ClanResult.NO_INVITE, reg.accept(b, "b").result());
    }

    @Test
    void membersCannotInviteOrKick() {
        clanWith(a, b, c);
        assertEquals(ClanResult.NO_PERMISSION, reg.invite(b, d).result());
        assertEquals(ClanResult.NO_PERMISSION, reg.kick(b, c).result());
    }

    @Test
    void officersKickOnlyBelowThemselves() {
        clanWith(a, b, c, d);
        reg.promote(a, b);
        reg.promote(a, c);
        assertEquals(ClanResult.INVITED, reg.invite(b, UUID.randomUUID()).result(), "officers may invite");
        assertEquals(ClanResult.NO_PERMISSION, reg.kick(b, c).result(), "officer vs officer");
        assertEquals(ClanResult.NO_PERMISSION, reg.kick(b, a).result(), "officer vs leader");
        assertEquals(ClanResult.KICKED, reg.kick(b, d).result());
        assertTrue(reg.clanOf(d).isEmpty());
        assertEquals(ClanResult.KICKED, reg.kick(a, c).result(), "leader may kick officers");
    }

    @Test
    void promoteDemoteTransferAreLeaderOnly() {
        clanWith(a, b, c);
        assertEquals(ClanResult.NO_PERMISSION, reg.promote(b, c).result());
        assertEquals(ClanResult.PROMOTED, reg.promote(a, b).result());
        assertEquals(ClanResult.ALREADY_MAX_RANK, reg.promote(a, b).result());
        assertEquals(ClanResult.DEMOTED, reg.demote(a, b).result());
        assertEquals(ClanResult.ALREADY_MIN_RANK, reg.demote(a, b).result());
        assertEquals(ClanResult.TRANSFERRED, reg.transfer(a, c).result());
        Clan clan = reg.clanOf(a).orElseThrow();
        assertEquals(c, clan.leader().playerId());
        assertEquals(ClanRank.OFFICER, clan.member(a).orElseThrow().rank(), "old leader becomes officer");
        assertEquals(1, clan.members().stream().filter(m -> m.rank() == ClanRank.LEADER).count());
    }

    @Test
    void leaderMustTransferBeforeLeavingUnlessAlone() {
        clanWith(a, b);
        assertEquals(ClanResult.LEADER_MUST_TRANSFER, reg.leave(a).result());
        assertEquals(ClanResult.LEFT, reg.leave(b).result());
        assertEquals(ClanResult.DISBANDED, reg.leave(a).result());
        assertTrue(reg.all().isEmpty());
        assertEquals(ClanResult.CREATED, reg.create(b, "b", "Хүрэн Чоно", "XЧ").result(), "name/tag freed");
    }

    @Test
    void disbandFreesEveryoneAndInvites() {
        clanWith(a, b);
        reg.invite(a, c);
        assertEquals(ClanResult.NO_PERMISSION, reg.disband(b).result());
        assertEquals(ClanResult.DISBANDED, reg.disband(a).result());
        assertTrue(reg.clanOf(b).isEmpty());
        assertEquals(ClanResult.CLAN_GONE.equals(reg.accept(c, "c").result())
                || ClanResult.NO_INVITE.equals(reg.accept(c, "c").result()), true);
    }

    @Test
    void capacityGrowsWithLevel() {
        Clan clan = clanWith(a);
        for (int i = 1; i < 10; i++) {
            UUID p = UUID.randomUUID();
            reg.invite(a, p);
            reg.accept(p, "p" + i);
        }
        assertEquals(10, clan.size());
        UUID extra = UUID.randomUUID();
        assertEquals(ClanResult.CLAN_FULL, reg.invite(a, extra).result());
        ClanRegistry.Contribution contribution = reg.contribute(a, ClanProgression.totalExpFor(2)).orElseThrow();
        assertEquals(1, contribution.levelsGained());
        assertEquals(12, clan.capacity());
        assertEquals(ClanResult.INVITED, reg.invite(a, extra).result());
    }

    @Test
    void contributionCreditsMemberAndIgnoresClanless() {
        Clan clan = clanWith(a, b);
        reg.contribute(b, 40);
        reg.contribute(b, 2);
        assertEquals(42, clan.member(b).orElseThrow().contribution());
        assertEquals(42, clan.exp());
        assertTrue(reg.contribute(c, 10).isEmpty());
        assertTrue(reg.contribute(b, 0).isEmpty());
    }

    @Test
    void snapshotRoundTripsThroughRepositoryAndRegister() {
        Clan clan = clanWith(a, b);
        reg.promote(a, b);
        reg.contribute(b, 900);
        InMemoryClanRepository repo = new InMemoryClanRepository();
        repo.save(clan.snapshot()).join();

        List<Clan> loaded = repo.loadAll().join();
        assertEquals(1, loaded.size());
        ClanRegistry fresh = new ClanRegistry(clock, Duration.ofMinutes(5));
        fresh.register(loaded.get(0));
        Clan back = fresh.clanOf(b).orElseThrow();
        assertEquals(clan.tag(), back.tag());
        assertEquals(900, back.exp());
        assertEquals(ClanRank.OFFICER, back.member(b).orElseThrow().rank());
        assertEquals(a, back.leader().playerId());
        assertThrows(IllegalStateException.class, () -> fresh.register(loaded.get(0)), "duplicate load rejected");
    }

    @Test
    void restoreRejectsLeaderlessClans() {
        ClanMember m = new ClanMember(a, "a", ClanRank.MEMBER, 0, Instant.EPOCH);
        assertThrows(IllegalStateException.class,
                () -> Clan.restore(UUID.randomUUID(), "Name", "TG", Instant.EPOCH, 0, 0, List.of(m)));
    }
}
