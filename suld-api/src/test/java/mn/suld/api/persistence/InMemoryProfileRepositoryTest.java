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
}
