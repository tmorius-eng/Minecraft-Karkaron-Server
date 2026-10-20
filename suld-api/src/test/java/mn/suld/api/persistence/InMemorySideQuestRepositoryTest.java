package mn.suld.api.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The memory store keeps the JDBC rules (JdbcSideQuestIT): one take, one hand-in, abandon only while active. */
class InMemorySideQuestRepositoryTest {

    @Test
    void theSameRulesAsTheDatabase() {
        InMemorySideQuestRepository repo = new InMemorySideQuestRepository();
        UUID p = UUID.randomUUID();
        assertTrue(repo.start(p, "squest.a", 1).join());
        assertFalse(repo.start(p, "squest.a", 2).join());
        repo.progress(p, "squest.a", 4).join();
        assertEquals(4, repo.load(p).join().get("squest.a").progress());
        assertTrue(repo.complete(p, "squest.a", 4, 9).join());
        assertFalse(repo.complete(p, "squest.a", 4, 10).join(), "handed in once");
        assertTrue(repo.load(p).join().get("squest.a").owed());
        assertTrue(repo.claimReward(p, "squest.a", 11).join());
        assertFalse(repo.claimReward(p, "squest.a", 12).join(), "paid once");
        repo.unclaimReward(p, "squest.a", 99).join();
        assertFalse(repo.load(p).join().get("squest.a").owed(), "only the claim's own time undoes it");
        repo.unclaimReward(p, "squest.a", 11).join();
        assertTrue(repo.load(p).join().get("squest.a").owed());
        assertTrue(repo.claimReward(p, "squest.a", 13).join());
        assertFalse(repo.claimReward(p, "squest.b", 13).join(), "no row, nothing owed");
        repo.progress(p, "squest.a", 0).join();
        assertEquals(4, repo.load(p).join().get("squest.a").progress());
        assertFalse(repo.abandon(p, "squest.a").join());
        assertTrue(repo.start(p, "squest.b", 3).join());
        assertTrue(repo.abandon(p, "squest.b").join());
        assertTrue(repo.reset(p, "squest.a").join());
        assertTrue(repo.load(p).join().isEmpty());
    }
}
