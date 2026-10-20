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
        assertFalse(repo.complete(p, "squest.a", 4, 10).join(), "paid once");
        repo.progress(p, "squest.a", 0).join();
        assertEquals(4, repo.load(p).join().get("squest.a").progress());
        assertFalse(repo.abandon(p, "squest.a").join());
        assertTrue(repo.start(p, "squest.b", 3).join());
        assertTrue(repo.abandon(p, "squest.b").join());
        assertTrue(repo.reset(p, "squest.a").join());
        assertTrue(repo.load(p).join().isEmpty());
    }
}
