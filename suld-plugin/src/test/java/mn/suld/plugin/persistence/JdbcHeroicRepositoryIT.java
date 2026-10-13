package mn.suld.plugin.persistence;

import mn.suld.api.dungeon.heroic.HeroicLadder;
import mn.suld.api.persistence.HeroicRepository;
import mn.suld.api.persistence.InMemoryHeroicRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V24 heroic keys: every write only raises, so any order (and any interleaving of threads) ends the same. */
class JdbcHeroicRepositoryIT {

    static final String D = "dungeon.khasar_den";
    private JdbcHeroicRepository repo;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcHeroicRepository(db.ds(), db.dialect(), pool);
    }

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    @Test
    void keysOnlyEverRiseAndTheBestTimeFollowsTheBestKey() {
        UUID p = UUID.randomUUID();
        assertTrue(repo.keys(p).join().isEmpty(), "never run: nothing stored (the plugin reads it as +1 open)");
        repo.raise(p, D, 4, 3, 400, 1).join();
        assertEquals(new HeroicRepository.Key(D, 4, 3, 400), repo.keys(p).join().get(D));
        repo.raise(p, D, 2, 2, 100, 2).join(); // a lower key, even a faster one, changes nothing
        assertEquals(new HeroicRepository.Key(D, 4, 3, 400), repo.keys(p).join().get(D));
        repo.raise(p, D, 4, 3, 350, 3).join(); // the same best key, faster: the time improves
        assertEquals(new HeroicRepository.Key(D, 4, 3, 350), repo.keys(p).join().get(D));
        repo.raise(p, D, 4, 3, 420, 4).join(); // the same key, slower: kept
        assertEquals(new HeroicRepository.Key(D, 4, 3, 350), repo.keys(p).join().get(D));
        repo.raise(p, D, 6, 5, 440, 5).join(); // a new best key: its own time, even if slower
        assertEquals(new HeroicRepository.Key(D, 6, 5, 440), repo.keys(p).join().get(D));
        repo.raise(p, D, 7, 0, 0, 6).join(); // only the open key moves (an untimed result never writes a best)
        assertEquals(new HeroicRepository.Key(D, 7, 5, 440), repo.keys(p).join().get(D));
    }

    @Test
    void concurrentResultsEndAsTheirMaximumWhateverTheOrder() {
        UUID p = UUID.randomUUID();
        List<int[]> writes = new ArrayList<>();
        for (int k = 1; k <= 12; k++) writes.add(new int[]{k + 1, k, 500 - 10 * k});
        writes.add(new int[]{13, 12, 300}); // the best key, timed faster again
        Collections.shuffle(writes, new java.util.Random(42));
        InMemoryHeroicRepository sequential = new InMemoryHeroicRepository();
        List<CompletableFuture<Void>> all = new ArrayList<>();
        for (int[] w : writes) {
            all.add(repo.raise(p, D, w[0], w[1], w[2], 1));
            sequential.raise(p, D, w[0], w[1], w[2], 1).join();
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();
        assertEquals(new HeroicRepository.Key(D, 13, 12, 300), repo.keys(p).join().get(D));
        assertEquals(sequential.keys(p).join().get(D), repo.keys(p).join().get(D), "the JDBC and the in-memory rule agree");
    }

    @Test
    void theBoardShowsTimedRunsHighestKeyFirstThenFastest() {
        long t = 1000;
        repo.logRun(new HeroicRepository.Run(UUID.randomUUID(), D, 5, HeroicLadder.Outcome.TIMED, 400, 0, "ZUD", "A,B", "2026-W42", t)).join();
        repo.logRun(new HeroicRepository.Run(UUID.randomUUID(), D, 7, HeroicLadder.Outcome.TIMED, 430, 1, "ZUD,GAN", "C", "2026-W42", t)).join();
        repo.logRun(new HeroicRepository.Run(UUID.randomUUID(), D, 7, HeroicLadder.Outcome.TIMED, 410, 0, "ZUD,GAN", "D", "2026-W42", t)).join();
        repo.logRun(new HeroicRepository.Run(UUID.randomUUID(), D, 9, HeroicLadder.Outcome.OVERTIME, 600, 2, "", "E", "2026-W42", t)).join();
        repo.logRun(new HeroicRepository.Run(UUID.randomUUID(), "dungeon.govi_bulsh", 12, HeroicLadder.Outcome.TIMED, 300, 0, "", "F", "2026-W42", t)).join();
        List<HeroicRepository.Run> board = repo.board(D, 10).join();
        assertEquals(List.of("D", "C", "A,B"), board.stream().map(HeroicRepository.Run::members).toList(),
                "+7 in 410 s, +7 in 430 s, +5; the overtime +9 and another dungeon are not on it");
        assertEquals(1, repo.board(D, 1).join().size());
    }
}
