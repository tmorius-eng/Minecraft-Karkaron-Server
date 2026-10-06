package mn.suld.api.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationTest {

    @Test
    void sectorWaypointsSitInTheirRegion() {
        RegionShape east = new RegionShape.Sector(0, 3000, 45, 135);
        double[] w = Navigation.waypoint(east);
        assertEquals(Navigation.SECTOR_WAYPOINT, w[0], 1e-6);
        assertEquals(0, w[1], 1e-6);
        assertTrue(east.contains(w[0], w[1]));
        RegionShape north = new RegionShape.Sector(0, 3000, 315, 45); // wraps through 0
        double[] n = Navigation.waypoint(north);
        assertEquals(0, n[0], 1e-6);
        assertEquals(-Navigation.SECTOR_WAYPOINT, n[1], 1e-6);
        assertTrue(north.contains(n[0], n[1]));
    }

    @Test
    void bearingsAndArrows() {
        assertEquals(0, Navigation.bearing(0, -10), 1e-9);   // north
        assertEquals(90, Navigation.bearing(10, 0), 1e-9);   // east
        assertEquals(180, Navigation.facing(0f), 1e-9);      // yaw 0 faces south
        assertEquals(270, Navigation.facing(90f), 1e-9);     // yaw 90 faces west
        assertEquals("⬆", Navigation.arrow(90, 90));
        assertEquals("➡", Navigation.arrow(180, 90));
        assertEquals("⬇", Navigation.arrow(270, 90));
        assertEquals("⬅", Navigation.arrow(0, 90));
        assertEquals("зүүн", Navigation.compass(90));
        assertEquals("хойд", Navigation.compass(359));
    }
}
