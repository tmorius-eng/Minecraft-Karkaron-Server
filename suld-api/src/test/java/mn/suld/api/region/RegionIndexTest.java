package mn.suld.api.region;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegionIndexTest {

    private static final RegionDefinition TOWN = new RegionDefinition("region.town", "Town", "", new RegionShape.Square(50),
            100, 1, 60, true, true, List.of(), 0);
    private static final RegionDefinition EAST = new RegionDefinition("region.east", "East", "",
            new RegionShape.Sector(0, 1000, 45, 135), 10, 1, 8, false, false, List.of("mob.a"), 100);
    private static final RegionDefinition NORTH = new RegionDefinition("region.north", "North", "",
            new RegionShape.Sector(0, 1000, 315, 45), 10, 10, 20, false, false, List.of("mob.b"), 200);

    private final RegionIndex index = new RegionIndex(List.of(EAST, TOWN, NORTH));

    @Test
    void higherPriorityWinsOnOverlap() {
        assertEquals("region.town", index.at(40, 0).orElseThrow().id(), "town sits on top of the steppe");
        assertEquals("region.east", index.at(60, 0).orElseThrow().id());
    }

    @Test
    void sectorsUseCompassBearingsOnMinecraftAxes() {
        assertEquals("region.north", index.at(0, -500).orElseThrow().id(), "north is -Z");
        assertEquals("region.east", index.at(500, 0).orElseThrow().id(), "east is +X");
        assertTrue(index.at(0, 500).isEmpty(), "south not covered here");
    }

    @Test
    void sectorWrapsPastNorth() {
        assertEquals("region.north", index.at(-200, -400).orElseThrow().id(), "bearing ~333°");
        assertEquals("region.north", index.at(200, -400).orElseThrow().id(), "bearing ~27°");
    }

    @Test
    void radiusBoundsAndCircles() {
        assertTrue(new RegionShape.Sector(100, 200, 0, 360).contains(150, 0));
        assertFalse(new RegionShape.Sector(100, 200, 0, 360).contains(50, 0));
        assertFalse(new RegionShape.Sector(100, 200, 0, 360).contains(250, 0));
        assertTrue(new RegionShape.Circle(10, 10, 5).contains(13, 13));
        assertFalse(new RegionShape.Circle(10, 10, 5).contains(0, 0));
    }

    @Test
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new RegionIndex(List.of(TOWN, TOWN)));
        assertThrows(IllegalArgumentException.class, () -> new RegionDefinition("region.x", "X", "", new RegionShape.Square(1),
                0, 1, 5, true, false, List.of("mob.a"), 0), "safe zones cannot spawn hostiles");
        assertThrows(IllegalArgumentException.class, () -> new RegionDefinition("bad", "X", "", new RegionShape.Square(1),
                0, 1, 5, false, false, List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new RegionDefinition("region.x", "X", "", new RegionShape.Square(1),
                0, 9, 5, false, false, List.of(), 0));
    }

    @Test
    void discoveryIsFirstTimeOnly() {
        DiscoveryLedger ledger = new DiscoveryLedger(Set.of("region.town"));
        assertFalse(ledger.discover("region.town"), "loaded from storage");
        assertTrue(ledger.discover("region.east"));
        assertFalse(ledger.discover("region.east"));
        assertEquals(Set.of("region.town", "region.east"), ledger.snapshot());
    }
}
