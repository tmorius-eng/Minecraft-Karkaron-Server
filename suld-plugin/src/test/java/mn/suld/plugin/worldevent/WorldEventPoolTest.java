package mn.suld.plugin.worldevent;

import mn.suld.api.worldevent.WorldEventDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The automatic rotation draws from every {@code automatic} event plus the unlocked ones, each once, in content order. */
class WorldEventPoolTest {

    private static WorldEventDefinition def(String id, boolean automatic) {
        return new WorldEventDefinition(id, id, "", Set.of("mob.x"), 10, 600, 0, 0, 1, 0, 0, automatic);
    }

    @Test
    void automaticEventsThenUnlockedOnesEachOnce() {
        List<WorldEventDefinition> all = List.of(def("event.a", true), def("event.b", false), def("event.c", true), def("event.d", false));
        assertEquals(List.of("event.a", "event.c"), WorldEventService.pool(all, Set.of()).stream().map(WorldEventDefinition::id).toList());
        assertEquals(List.of("event.a", "event.c", "event.d"), WorldEventService.pool(all, Set.of("event.d", "event.a")).stream().map(WorldEventDefinition::id).toList(),
                "an unlocked automatic event is not drawn twice as often");
        assertEquals(List.of(), WorldEventService.pool(List.of(def("event.b", false)), Set.of()), "nothing automatic: the service falls back to the wolf raid");
    }

    @Test
    void theOldConstructorMeansNotAutomatic() {
        WorldEventDefinition d = new WorldEventDefinition("event.x", "x", "", Set.of("mob.x"), 10, 600, 0, 0, 1, 0, 0);
        assertEquals(false, d.automatic());
    }
}
