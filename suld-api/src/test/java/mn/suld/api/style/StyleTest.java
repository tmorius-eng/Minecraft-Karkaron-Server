package mn.suld.api.style;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleTest {

    @Test
    void rankLadderNeedsLevelThenCoins() {
        assertEquals(Rank.Check.LEVEL_TOO_LOW, Rank.canRankUp(Rank.ARD, 4, 10_000));
        assertEquals(Rank.Check.NOT_ENOUGH_COINS, Rank.canRankUp(Rank.ARD, 5, 149));
        assertEquals(Rank.Check.OK, Rank.canRankUp(Rank.ARD, 5, 150));
        assertEquals(Rank.Check.MAX_RANK, Rank.canRankUp(Rank.KHAAN, 60, Long.MAX_VALUE));
        int lastLevel = 0;
        long lastCost = -1;
        for (Rank r : Rank.LADDER) { // the ladder only goes up
            assertTrue(r.requiredLevel() > lastLevel || r == Rank.ARD);
            assertTrue(r.cost() > lastCost);
            lastLevel = r.requiredLevel();
            lastCost = r.cost();
        }
        assertEquals(Rank.ZUUT, Rank.byId("ZUUT"));
        assertEquals(Rank.ARD, Rank.byId("nonsense"));
    }

    @Test
    void catalogIdsAreUniqueAsciiAndTemplatesComplete() {
        Set<String> ids = new HashSet<>();
        for (Cosmetic c : CosmeticCatalog.ALL) {
            assertTrue(ids.add(c.id()), c.id());
            assertTrue(c.id().matches("[a-z_]+\\.[a-z_]+"), "ascii id " + c.id());
            switch (c.category()) {
                case NAME_COLOR, CHAT_COLOR -> assertTrue(c.style().contains("{}"), c.id());
                case JOIN_MESSAGE -> assertTrue(c.style().contains("{name}"), c.id());
                case EMOJI -> assertTrue(c.style().matches(":[a-z]+:"), c.id());
                case TAG -> assertFalse(c.style().isBlank(), c.id());
            }
        }
        for (LevelRewards.Reward r : LevelRewards.ALL) {
            if (r.cosmeticId() != null) assertTrue(CosmeticCatalog.byId(r.cosmeticId()).isPresent(), r.cosmeticId());
        }
    }

    @Test
    void equipOnlyWhatIsOwned() {
        PlayerStyle s = new PlayerStyle(UUID.randomUUID());
        assertTrue(s.owns("emoji.zurkh")); // default emoji
        assertFalse(s.equip(Cosmetic.Category.TAG, "tag.chono"));
        assertTrue(s.grant("tag.chono"));
        assertFalse(s.grant("tag.chono"));
        assertFalse(s.equip(Cosmetic.Category.NAME_COLOR, "tag.chono")); // wrong category
        assertTrue(s.equip(Cosmetic.Category.TAG, "tag.chono"));
        assertEquals("tag.chono", s.equipped(Cosmetic.Category.TAG).orElseThrow().id());
        assertTrue(s.equip(Cosmetic.Category.TAG, null));
        assertTrue(s.equipped(Cosmetic.Category.TAG).isEmpty());
        assertFalse(s.grant("tag.does_not_exist"));
    }

    @Test
    void snapshotRoundTripDropsUnknownAndUnownedIds() {
        UUID id = UUID.randomUUID();
        PlayerStyle s = new PlayerStyle(id);
        s.grant("tag.burged");
        s.equip(Cosmetic.Category.TAG, "tag.burged");
        s.rank(Rank.ARAVT);
        s.claimLevel(5);
        PlayerStyle.Snapshot snap = s.snapshotAndClean();
        assertFalse(s.isDirty());
        PlayerStyle back = PlayerStyle.restore(snap);
        assertEquals(Rank.ARAVT, back.rank());
        assertEquals("tag.burged", back.equipped(Cosmetic.Category.TAG).orElseThrow().id());
        assertTrue(LevelRewards.claimed(back.claimedLevels(), 5));

        Set<String> owned = new HashSet<>(snap.owned());
        owned.add("tag.removed_from_catalog");
        PlayerStyle tampered = PlayerStyle.restore(new PlayerStyle.Snapshot(id, Rank.ARD, owned, "tag.chono", null, null, null, 0, 0, 0, 0, 0));
        assertFalse(tampered.owns("tag.removed_from_catalog"));
        assertTrue(tampered.equipped(Cosmetic.Category.TAG).isEmpty()); // not owned -> not equipped
    }

    @Test
    void creditsNeverGoNegativeAndOnlyCosmeticsCostCredits() throws Exception {
        mn.suld.api.persistence.InMemoryStyleRepository repo = new mn.suld.api.persistence.InMemoryStyleRepository();
        UUID id = UUID.randomUUID();
        assertEquals(-1L, repo.addCredits(id, -1).get());
        assertEquals(100L, repo.addCredits(id, 100).get());
        assertEquals(-1L, repo.addCredits(id, -101).get());
        assertEquals(0L, repo.addCredits(id, -100).get());
        repo.addCredits(id, 40).get();
        PlayerStyle s = new PlayerStyle(id);
        s.grant("tag.chono");
        repo.save(s.snapshotAndClean()).get(); // a style save never touches credits
        assertEquals(40L, repo.load(id).get().orElseThrow().credits());
        Cosmetic store = CosmeticCatalog.byId("tag.chingis").orElseThrow();
        assertFalse(store.sold()); // not for coins
        assertEquals(400, store.creditPrice());
        assertEquals(35, CosmeticCatalog.byId("tag.chono").orElseThrow().creditPrice());
        assertEquals(0, CosmeticCatalog.byId("tag.anchin").orElseThrow().creditPrice()); // level reward only
    }

    @Test
    void levelRewardsAreClaimedOnce() {
        long mask = 0;
        assertEquals(3, LevelRewards.claimable(5, mask).size()); // 2, 3, 5
        mask = LevelRewards.withClaimed(mask, 2);
        mask = LevelRewards.withClaimed(mask, 3);
        assertEquals(1, LevelRewards.claimable(5, mask).size());
        assertTrue(LevelRewards.claimed(mask, 3));
        assertFalse(LevelRewards.claimed(mask, 5));
        assertTrue(LevelRewards.claimable(1, 0).isEmpty());
    }
}
