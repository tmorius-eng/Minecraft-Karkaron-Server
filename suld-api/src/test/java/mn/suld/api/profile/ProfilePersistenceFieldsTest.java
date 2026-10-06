package mn.suld.api.profile;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.persistence.InMemoryProfileRepository;
import mn.suld.api.progression.Progression;
import mn.suld.api.quest.QuestState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfilePersistenceFieldsTest {

    @Test
    void currencyAndQuestSurviveSaveAndLoad() {
        InMemoryProfileRepository repo = new InMemoryProfileRepository();
        UUID id = UUID.randomUUID();
        PlayerProfile p = PlayerProfile.createNew(id, "Sübötei", Instant.now());
        p.selectClass(PlayerClass.MERGEN);
        p.progression(new Progression(4, 120));
        p.addCurrency(250);
        p.questState(new QuestState("quest.first_hunt", 2, false));

        repo.save(p).join();
        PlayerProfile loaded = repo.find(id).join().orElseThrow();

        assertEquals(PlayerClass.MERGEN, loaded.playerClass().orElseThrow());
        assertEquals(4, loaded.progression().level());
        assertEquals(120, loaded.progression().expIntoLevel());
        assertEquals(250, loaded.currency());
        assertEquals("quest.first_hunt", loaded.questState().questId());
        assertEquals(2, loaded.questState().progress());
    }

    @Test
    void newProfileHasZeroCurrencyAndNoQuest() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Jebe", Instant.now());
        assertEquals(0, p.currency());
        assertEquals(QuestState.NONE, p.questState());
    }
}
