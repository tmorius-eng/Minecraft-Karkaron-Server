package mn.suld.api.relic;

import mn.suld.api.persistence.InMemoryRelicRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRelicRepositoryTest {

    @Test
    void ensureIsIdempotentAndKeepsTheFirstItemUuid() {
        InMemoryRelicRepository repo = new InMemoryRelicRepository();
        UUID first = UUID.randomUUID();
        RelicRecord a = repo.ensure("relic.test", first).join();
        RelicRecord b = repo.ensure("relic.test", UUID.randomUUID()).join();
        assertEquals(first, a.itemUuid());
        assertEquals(first, b.itemUuid(), "a restart never mints a second identity");
        assertEquals(1, repo.loadAll().join().size());
    }

    @Test
    void casRejectsStaleVersionAndBumpsGeneration() {
        InMemoryRelicRepository repo = new InMemoryRelicRepository();
        RelicRecord r = repo.ensure("relic.test", UUID.randomUUID()).join();
        UUID p = UUID.randomUUID();
        CasResult ok = repo.apply(RelicTransition.claim(r, p, "P", RelicEvent.DISCOVERED, "p", "")).join();
        assertTrue(ok.success());
        assertEquals(1, ok.current().version());
        assertNotNull(ok.current().acquiredAt());
        CasResult stale = repo.apply(RelicTransition.claim(r, UUID.randomUUID(), "Q", RelicEvent.DISCOVERED, "q", "")).join();
        assertFalse(stale.success(), "a decision based on the old state is rejected");
        assertEquals(p, stale.current().owner());
        CasResult back = repo.apply(RelicTransition.release(ok.current(), RelicEvent.RETURNED, "server", "")).join();
        assertTrue(back.success());
        assertNull(back.current().owner());
        assertEquals(3, repo.history("relic.test", 10).join().size(), "CREATED, DISCOVERED, RETURNED");
        assertEquals(RelicEvent.RETURNED, repo.history("relic.test", 10).join().get(0).event(), "newest first");
    }

    @Test
    void sixtyFourSimultaneousClaimsProduceExactlyOneBearer() throws Exception {
        InMemoryRelicRepository repo = new InMemoryRelicRepository();
        RelicRecord r = repo.ensure("relic.test", UUID.randomUUID()).join();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> tries = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            UUID p = UUID.randomUUID();
            tries.add(pool.submit(() -> {
                go.await();
                return repo.apply(RelicTransition.claim(r, p, "p", RelicEvent.DISCOVERED, "p", "")).join().success();
            }));
        }
        go.countDown();
        int winners = 0;
        for (Future<Boolean> t : tries) {
            if (t.get(10, TimeUnit.SECONDS)) winners++;
        }
        pool.shutdown();
        assertEquals(1, winners);
    }
}
