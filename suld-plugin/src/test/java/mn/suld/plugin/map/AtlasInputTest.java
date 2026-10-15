package mn.suld.plugin.map;

import mn.suld.api.map.AtlasView;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The atlas command's names, the zoom read-out and the waypoint's pin identity (docs/WORLD_MAP.md). */
class AtlasInputTest {

    @Test
    void everyNameOfTheMapCommandTogglesInsteadOfClosingAndReopening() {
        // before: only "/map", "/map …" and the bare "/atlas" were let through, so "/gazar" closed the atlas and the
        // command then opened it again, and "/atlas pins" closed it
        for (String m : new String[]{"/map", "/MAP", "/map pins", "/maps", "/atlas", "/atlas pins", "/gazar", "/газар", "/suld:map", "/suld:atlas guide 1"}) {
            assertTrue(AtlasSky.isAtlasCommand(m), m);
        }
        for (String m : new String[]{"/mapping", "/menu", "/spawn", "map", "/suld map status", "/"}) {
            assertFalse(AtlasSky.isAtlasCommand(m), m);
        }
    }

    @Test
    void everyZoomHasItsOwnReadOut() {
        Set<String> seen = new HashSet<>();
        for (int z = AtlasView.MIN_ZOOM; z <= AtlasView.MAX_ZOOM; z++) seen.add(AtlasSky.zoomLabel(z));
        assertEquals(AtlasView.MAX_ZOOM - AtlasView.MIN_ZOOM + 1, seen.size(), "no two zooms read the same: " + seen);
        assertEquals("4:1", AtlasSky.zoomLabel(AtlasView.MIN_ZOOM));
        assertEquals("1:16", AtlasSky.zoomLabel(AtlasView.MAX_ZOOM));
    }

    @Test
    void theAtlasOpensCloseEnoughToReadTheLand() {
        // one block a map pixel: 640 blocks across the wall (it opened at 4 blocks a pixel, 2 560 across)
        assertEquals(0, AtlasSky.OPEN_ZOOM);
        assertTrue(AtlasSky.OPEN_ZOOM > AtlasView.MIN_ZOOM, "there is still room to zoom in");
    }

    @Test
    void aWaypointKnowsThePinItLeadsTo() {
        UUID pin = UUID.randomUUID();
        WaypointGuide.Target toPin = new WaypointGuide.Target("Худаг", "◆", "world", 10.5, 70, -4.5, pin);
        assertTrue(toPin.leadsTo(pin), "removing this pin ends the waypoint");
        assertFalse(toPin.leadsTo(UUID.randomUUID()), "another pin's removal leaves it");
        WaypointGuide.Target place = new WaypointGuide.Target("Хархорум", "◆", "world", 0.5, WaypointGuide.GROUND, 0.5);
        assertNull(place.pin());
        assertFalse(place.leadsTo(pin), "a place, a death or typed coordinates never end with a pin");
        assertFalse(place.leadsTo(null));
    }
}
