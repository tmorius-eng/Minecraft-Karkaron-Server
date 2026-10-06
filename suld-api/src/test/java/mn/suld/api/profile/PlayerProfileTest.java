package mn.suld.api.profile;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.progression.Progression;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfileTest {

    @Test
    void newProfileStartsAtLevelOneWithNoClass() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Ogedei", Instant.now());
        assertFalse(p.hasSelectedClass());
        assertEquals(1, p.progression().level());
        assertEquals(0L, p.version());
    }

    @Test
    void classCanBeSelectedExactlyOnce() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Tolui", Instant.now());
        assertTrue(p.selectClass(PlayerClass.MERGEN));
        assertFalse(p.selectClass(PlayerClass.BAATAR), "second selection must be rejected");
        assertEquals(PlayerClass.MERGEN, p.playerClass().orElseThrow());
    }

    @Test
    void mutationsBumpVersionAndDirtyFlag() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Chagatai", Instant.now());
        long v0 = p.version();
        p.progression(new Progression(2, 10));
        assertTrue(p.version() > v0);
        assertTrue(p.isDirty());

        p.markPersisted(p.version());
        assertFalse(p.isDirty());
    }

    @Test
    void forceClassOverridesExistingSelection() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Guyuk", Instant.now());
        p.selectClass(PlayerClass.BAATAR);
        p.forceClass(PlayerClass.DARKHAN);
        assertEquals(PlayerClass.DARKHAN, p.playerClass().orElseThrow());
    }
}
