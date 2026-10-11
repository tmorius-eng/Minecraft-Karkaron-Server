package mn.suld.api.chronicle;

import mn.suld.api.persistence.InMemoryChronicleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChronicleTest {

    private static ChronicleEntry clear(UUID p, String name, long seconds) {
        return ChronicleEntry.of(ChronicleKind.FASTEST_DUNGEON_CLEAR, "dungeon.khasar_den", p, name, List.of(name, "Б"), "ОВГ", seconds, "", "2026-W41", 1);
    }

    @Test
    void fastestClearIsFewerSeconds() {
        assertTrue(ChronicleRules.better(ChronicleKind.FASTEST_DUNGEON_CLEAR, null, 300L));
        assertTrue(ChronicleRules.better(ChronicleKind.FASTEST_DUNGEON_CLEAR, 300L, 299L));
        assertFalse(ChronicleRules.better(ChronicleKind.FASTEST_DUNGEON_CLEAR, 300L, 300L), "equal does not beat");
        assertFalse(ChronicleRules.better(ChronicleKind.FIRST_DUNGEON_CLEAR, null, 1L), "a first is not a record");
        assertEquals("4:05", ChronicleRules.formatSeconds(245));
    }

    @Test
    void headlinesAreMongolianAndNameTheParty() {
        UUID p = UUID.randomUUID();
        String h = ChronicleRules.headline(ChronicleEntry.of(ChronicleKind.FIRST_DUNGEON_CLEAR, "dungeon.khasar_den", p, "Тэмүүжин",
                List.of("Тэмүүжин", "Бөртэ"), "ОВГ", 245L, "", "2026-W41", 1), "Саарлын Агуй");
        assertTrue(h.contains("Тэмүүжин, Бөртэ"), h);
        assertTrue(h.contains("Саарлын Агуй"), h);
        assertTrue(h.contains("4:05"), h);
        assertFalse(h.contains("FIRST_"), "no raw enum names");
        for (ChronicleKind k : ChronicleKind.values()) assertFalse(ChronicleRules.label(k).isBlank());
    }

    @Test
    void inMemoryRepositoryHonoursFirstsRecordsAndHiding() {
        InMemoryChronicleRepository repo = new InMemoryChronicleRepository();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ChronicleEntry first = ChronicleEntry.of(ChronicleKind.FIRST_DUNGEON_CLEAR, "dungeon.khasar_den", a, "A", List.of("A"), "", 300L, "", "2026-W41", 1);
        assertTrue(repo.claimFirst(first).join().isPresent());
        assertTrue(repo.claimFirst(first.withId(0)).join().isEmpty(), "only one first per subject");
        assertTrue(repo.recordIfBetter(clear(a, "A", 300)).join().isPresent());
        assertTrue(repo.recordIfBetter(clear(b, "B", 400)).join().isEmpty(), "slower does not replace");
        ChronicleEntry faster = repo.recordIfBetter(clear(b, "B", 200)).join().orElseThrow();
        assertEquals(200L, repo.holder(ChronicleKind.FASTEST_DUNGEON_CLEAR, "dungeon.khasar_den").join().orElseThrow().value());
        assertEquals(3, repo.latest(10).join().size());
        assertEquals(faster.id(), repo.latest(1).join().get(0).id(), "newest first");
        assertEquals(2, repo.byPlayer(a, 10).join().size());
        assertTrue(repo.hide(faster.id()).join());
        assertFalse(repo.hide(999).join());
        assertEquals(2, repo.latest(10).join().size(), "hidden entries leave every listing");
        assertEquals(1, repo.byKind(ChronicleKind.FIRST_DUNGEON_CLEAR, 10).join().size());
    }
}
