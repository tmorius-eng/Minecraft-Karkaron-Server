package mn.suld.plugin.profile;

import mn.suld.api.identity.PlayerIdentity;
import mn.suld.api.persistence.ProfileRepository;
import mn.suld.api.persistence.RepositoryException;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.service.ProfileLoad;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Identity/lifecycle invariants of the profile service, with a deliberately slow store. */
class DefaultProfileServiceTest {

    /**
     * Stores copies (like a real database). Saves are slow, finds are fast — the worst case for
     * a quit-then-instant-rejoin race.
     */
    static final class SlowRepo implements ProfileRepository {
        final Map<UUID, Long> storedCurrency = new ConcurrentHashMap<>();
        final Map<UUID, String> storedName = new ConcurrentHashMap<>();
        final AtomicInteger creates = new AtomicInteger();
        final AtomicBoolean failFinds = new AtomicBoolean();
        final ExecutorService io = Executors.newFixedThreadPool(4);
        volatile long saveDelayMs = 200;

        @Override
        public CompletableFuture<Optional<PlayerProfile>> find(UUID id) {
            return CompletableFuture.supplyAsync(() -> {
                if (failFinds.get()) throw new RepositoryException("db down", null);
                if (!storedName.containsKey(id)) return Optional.<PlayerProfile>empty();
                PlayerProfile p = PlayerProfile.createNew(id, storedName.get(id), java.time.Instant.EPOCH);
                p.addCurrency(storedCurrency.getOrDefault(id, 0L));
                return Optional.of(p);
            }, io);
        }

        @Override
        public CompletableFuture<Boolean> exists(UUID id) {
            return CompletableFuture.completedFuture(storedName.containsKey(id));
        }

        @Override
        public CompletableFuture<PlayerProfile> save(PlayerProfile profile) {
            long currency = profile.currency();
            String name = profile.name();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(saveDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (storedName.putIfAbsent(profile.playerId(), name) == null) creates.incrementAndGet();
                storedName.put(profile.playerId(), name);
                storedCurrency.put(profile.playerId(), currency);
                return profile;
            }, io);
        }

        @Override
        public CompletableFuture<Void> delete(UUID id) {
            storedName.remove(id);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static ProfileLoad acquire(DefaultProfileService svc, UUID id, String name) throws Exception {
        return svc.acquire(PlayerIdentity.of(id, name)).get(10, TimeUnit.SECONDS);
    }

    @Test
    void firstJoinCreatesAndReturningJoinRestoresSameProfile() throws Exception {
        SlowRepo repo = new SlowRepo();
        repo.saveDelayMs = 0;
        DefaultProfileService svc = new DefaultProfileService(repo);
        UUID id = UUID.randomUUID();

        ProfileLoad first = acquire(svc, id, "qeevr_");
        assertTrue(first.created());
        first.profile().addCurrency(77);
        svc.saveAndUnload(id).get(5, TimeUnit.SECONDS);
        assertTrue(svc.cached(id).isEmpty());

        ProfileLoad back = acquire(svc, id, "qeevr_");
        assertFalse(back.created(), "returning player is never re-created");
        assertEquals(77, back.profile().currency());
        assertEquals(1, repo.creates.get());
    }

    @Test
    void quitThenInstantRejoinNeverReadsStaleData() throws Exception {
        SlowRepo repo = new SlowRepo();
        DefaultProfileService svc = new DefaultProfileService(repo);
        UUID id = UUID.randomUUID();
        repo.saveDelayMs = 0;
        acquire(svc, id, "Racer").profile().addCurrency(10);
        repo.saveDelayMs = 300;                       // the quit save is slow...

        svc.cached(id).orElseThrow().addCurrency(500); // progress made during the session
        CompletableFuture<Void> quitSave = svc.saveAndUnload(id);
        ProfileLoad rejoin = acquire(svc, id, "Racer"); // ...and the player reconnects instantly
        assertTrue(quitSave.isDone(), "load waited for the save");
        assertEquals(510, rejoin.profile().currency(), "no rollback to the pre-session value");
    }

    @Test
    void renameKeepsTheSameCharacter() throws Exception {
        SlowRepo repo = new SlowRepo();
        repo.saveDelayMs = 0;
        DefaultProfileService svc = new DefaultProfileService(repo);
        UUID id = UUID.randomUUID();
        acquire(svc, id, "OldName").profile().addCurrency(42);
        svc.saveAndUnload(id).get(5, TimeUnit.SECONDS);

        ProfileLoad renamed = acquire(svc, id, "NewName");
        assertFalse(renamed.created(), "a username change must not create a new character");
        assertEquals("OldName", renamed.previousName());
        assertEquals("NewName", renamed.profile().name());
        assertEquals(42, renamed.profile().currency());
        assertEquals(1, repo.creates.get());
    }

    @Test
    void sameNameOnAnotherUuidIsAnotherCharacter() throws Exception {
        SlowRepo repo = new SlowRepo();
        repo.saveDelayMs = 0;
        DefaultProfileService svc = new DefaultProfileService(repo);
        UUID a = UUID.randomUUID();
        UUID impostor = UUID.randomUUID();
        acquire(svc, a, "qeevr_").profile().addCurrency(1000);
        ProfileLoad other = acquire(svc, impostor, "qeevr_");
        assertTrue(other.created(), "taking someone's old name never grants their profile");
        assertEquals(0, other.profile().currency());
    }

    @Test
    void storageFailureNeverCreatesABlankProfile() throws Exception {
        SlowRepo repo = new SlowRepo();
        repo.saveDelayMs = 0;
        DefaultProfileService svc = new DefaultProfileService(repo);
        UUID id = UUID.randomUUID();
        acquire(svc, id, "Victim").profile().addCurrency(999);
        svc.saveAndUnload(id).get(5, TimeUnit.SECONDS);

        repo.failFinds.set(true);
        assertThrows(Exception.class, () -> acquire(svc, id, "Victim"));
        assertTrue(svc.cached(id).isEmpty(), "nothing cached on failure");
        assertEquals(999, repo.storedCurrency.get(id), "real progress untouched");
        assertEquals(1, repo.creates.get());
    }

    @Test
    void duplicateSessionSharesTheLiveInstance() throws Exception {
        SlowRepo repo = new SlowRepo();
        repo.saveDelayMs = 0;
        DefaultProfileService svc = new DefaultProfileService(repo);
        UUID id = UUID.randomUUID();
        PlayerProfile first = acquire(svc, id, "Dup").profile();
        first.addCurrency(5);
        PlayerProfile second = acquire(svc, id, "Dup").profile();
        assertSame(first, second, "one authoritative instance per UUID, never two diverging copies");
    }

    @Test
    void concurrentFirstLoginsCreateExactlyOnce() throws Exception {
        SlowRepo repo = new SlowRepo();
        repo.saveDelayMs = 50;
        DefaultProfileService svc = new DefaultProfileService(repo);
        UUID id = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        java.util.List<CompletableFuture<ProfileLoad>> loads = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            loads.add(CompletableFuture.supplyAsync(() -> svc.acquire(PlayerIdentity.of(id, "Twin")), pool)
                    .thenCompose(f -> f));
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        long created = loads.stream().filter(f -> f.join().created()).count();
        assertEquals(1, created, "exactly one creation");
        assertEquals(1, repo.creates.get());
        PlayerProfile any = loads.get(0).join().profile();
        assertTrue(loads.stream().allMatch(f -> f.join().profile() == any));
        pool.shutdown();
    }
}
