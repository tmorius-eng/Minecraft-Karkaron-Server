package mn.suld.plugin.persistence;

import mn.suld.api.map.MapPin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V26 map pins: a player's pins come back in order, the limit holds even when adds race, removing is the owner's. */
class JdbcMapPinRepositoryIT {

    private JdbcMapPinRepository repo;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcMapPinRepository(db.ds(), db.dialect(), pool);
    }

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    private static MapPin pin(UUID owner, String name, long at) {
        return new MapPin(UUID.randomUUID(), owner, name, -30, 69, -79, "gold", at);
    }

    @Test
    void pinsComeBackInOrderAndOnlyTheOwnerRemovesThem() {
        UUID me = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(repo.add(pin(me, "Миний гэр", 2), 30).join());
        assertTrue(repo.add(pin(me, "Нуур", 1), 30).join());
        assertTrue(repo.add(pin(other, "Тэр", 3), 30).join());
        List<MapPin> mine = repo.list(me).join();
        assertEquals(List.of("Нуур", "Миний гэр"), mine.stream().map(MapPin::name).toList(), "oldest first, Cyrillic intact");
        assertEquals(-79, mine.get(0).z());
        assertEquals("gold", mine.get(0).colour());
        assertFalse(repo.remove(other, mine.get(0).id()).join(), "not another player's pin");
        assertTrue(repo.remove(me, mine.get(0).id()).join());
        assertEquals(1, repo.list(me).join().size());
        assertEquals(1, repo.list(other).join().size());
    }

    @Test
    void theLimitHoldsWhenAddsRace() {
        UUID me = UUID.randomUUID();
        List<CompletableFuture<Boolean>> all = new ArrayList<>();
        for (int i = 0; i < 24; i++) all.add(repo.add(pin(me, "p" + i, i), 5));
        long added = all.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        int stored = repo.list(me).join().size();
        assertEquals(stored, added, "every true is a stored row");
        assertEquals(5, stored, "the limit of 5 holds exactly when adds race");
    }

    @Test
    void racingPlayersEachKeepTheirOwnLimit() {
        List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<CompletableFuture<Boolean>> all = new ArrayList<>();
        for (int i = 0; i < 40; i++) all.add(repo.add(pin(players.get(i % players.size()), "p" + i, i), 3));
        all.forEach(CompletableFuture::join);
        for (UUID p : players) assertEquals(3, repo.list(p).join().size(), "each player at their own limit");
    }
}
