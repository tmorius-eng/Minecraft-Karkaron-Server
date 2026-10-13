package mn.suld.api.map;

import mn.suld.api.persistence.InMemoryMapPinRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins: names made safe, the default name, finding a pin, the limit, storage. */
class MapPinTest {

    private static final UUID ME = UUID.randomUUID();

    private static MapPin pin(String name, long at) {
        return new MapPin(UUID.randomUUID(), ME, name, 1, 70, 2, "green", at);
    }

    @Test
    void namesAreCleaned() {
        assertEquals("Миний гэр", MapPin.clean("  Миний   гэр  "));
        assertEquals("гэр", MapPin.clean("§cгэр"), "no colour codes");
        assertEquals("гэр", MapPin.clean("&4гэр"));
        assertEquals("ab", MapPin.clean("a\u0000​b"), "no control or invisible characters");
        assertNull(MapPin.clean("   "));
        assertNull(MapPin.clean(null));
        assertEquals(MapPin.MAX_NAME, MapPin.clean("x".repeat(60)).length());
        assertThrows(IllegalArgumentException.class, () -> pin(" bad", 0), "a stored name is already clean");
        assertThrows(IllegalArgumentException.class, () -> new MapPin(UUID.randomUUID(), ME, "a", 0, 0, 0, "pink", 0));
    }

    @Test
    void defaultNamesAndFinding() {
        List<MapPin> pins = new ArrayList<>(List.of(pin("Тэмдэг 1", 1), pin("Миний гэр", 2)));
        assertEquals("Тэмдэг 2", MapPin.defaultName(pins));
        assertEquals("Миний гэр", MapPin.find(pins, "2").orElseThrow().name());
        assertEquals("Миний гэр", MapPin.find(pins, "МИНИЙ ГЭР").orElseThrow().name(), "any case");
        assertTrue(MapPin.find(pins, "3").isEmpty());
        assertTrue(MapPin.find(pins, "нөгөө").isEmpty());
    }

    @Test
    void theStoreKeepsTheLimitAndTheOrder() {
        InMemoryMapPinRepository repo = new InMemoryMapPinRepository();
        for (int i = 0; i < 3; i++) assertTrue(repo.add(pin("p" + i, 10 - i), 3).join());
        assertFalse(repo.add(pin("p3", 20), 3).join(), "the fourth pin of three");
        List<MapPin> l = repo.list(ME).join();
        assertEquals(List.of("p2", "p1", "p0"), l.stream().map(MapPin::name).toList(), "oldest first");
        assertTrue(repo.remove(ME, l.get(0).id()).join());
        assertFalse(repo.remove(ME, l.get(0).id()).join());
        assertEquals(2, repo.list(ME).join().size());
        assertTrue(repo.list(UUID.randomUUID()).join().isEmpty());
    }
}
