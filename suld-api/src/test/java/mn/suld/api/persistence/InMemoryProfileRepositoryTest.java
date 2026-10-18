package mn.suld.api.persistence;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryProfileRepositoryTest {

    @Test
    void saveThenFindReturnsEquivalentButDistinctInstance() {
        InMemoryProfileRepository repo = new InMemoryProfileRepository();
        UUID id = UUID.randomUUID();
        PlayerProfile profile = PlayerProfile.createNew(id, "Borte", Instant.now());
        profile.selectClass(PlayerClass.BOO);

        repo.save(profile).join();
        PlayerProfile loaded = repo.find(id).join().orElseThrow();

        assertNotSame(profile, loaded, "find must return a fresh instance (DB round-trip semantics)");
        assertEquals("Borte", loaded.name());
        assertEquals(PlayerClass.BOO, loaded.playerClass().orElseThrow());
    }

    @Test
    void existsAndDelete() {
        InMemoryProfileRepository repo = new InMemoryProfileRepository();
        UUID id = UUID.randomUUID();
        repo.save(PlayerProfile.createNew(id, "Jamukha", Instant.now())).join();

        assertTrue(repo.exists(id).join());
        repo.delete(id).join();
        assertFalse(repo.exists(id).join());
        assertEquals(0, repo.size());
    }

    @Test
    void saveMarksProfileClean() {
        InMemoryProfileRepository repo = new InMemoryProfileRepository();
        PlayerProfile profile = PlayerProfile.createNew(UUID.randomUUID(), "Qulan", Instant.now());
        profile.selectClass(PlayerClass.BAATAR);
        assertTrue(profile.isDirty());

        repo.save(profile).join();
        assertFalse(profile.isDirty());
    }

    @Test
    void saveTogetherStoresBothAndMarksBothClean() {
        InMemoryProfileRepository repo = new InMemoryProfileRepository();
        PlayerProfile a = PlayerProfile.createNew(UUID.randomUUID(), "Payer", Instant.now());
        PlayerProfile b = PlayerProfile.createNew(UUID.randomUUID(), "Payee", Instant.now());
        a.addCurrency(100);
        b.addCurrency(5);

        repo.saveTogether(a, b).join();

        assertEquals(100, repo.find(a.playerId()).join().orElseThrow().currency());
        assertEquals(5, repo.find(b.playerId()).join().orElseThrow().currency());
        assertFalse(a.isDirty());
        assertFalse(b.isDirty());
    }

    @Test
    void ownerOfNameIgnoresCaseAndPrefersTheOldestProfile() {
        InMemoryProfileRepository repo = new InMemoryProfileRepository();
        UUID dorj = UUID.randomUUID();
        UUID twin = UUID.randomUUID();
        repo.save(PlayerProfile.createNew(twin, "dorj", Instant.ofEpochMilli(2_000))).join();
        repo.save(PlayerProfile.createNew(dorj, "Dorj", Instant.ofEpochMilli(1_000))).join();
        assertEquals(java.util.Optional.of(dorj), repo.ownerOfName("DORJ").join());
        assertTrue(repo.ownerOfName("Bat").join().isEmpty());
    }
}
