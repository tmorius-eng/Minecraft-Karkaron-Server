package mn.suld.plugin.content;

import mn.suld.api.region.Area;
import mn.suld.api.region.RegionDefinition;
import mn.suld.api.region.RegionIndex;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AreaContentTest {

    @Test
    void areasTileTheWildWithoutGapsOrOverlaps() {
        RegionIndex regions = new RegionIndex(WorldContent.REGIONS);
        Set<Integer> bits = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Area a : WorldContent.AREAS) {
            assertTrue(bits.add(a.index()), "bit " + a.index());
            assertTrue(names.add(a.name()), "name " + a.name());
            assertTrue(WorldContent.REGIONS.stream().anyMatch(r -> r.id().equals(a.regionId())), a.id());
            assertTrue(List.of("VERIFIED", "INSPIRED", "FICTION").contains(a.history()), a.id());
        }
        assertTrue(WorldContent.REGIONS.size() <= 16, "regions own bits 0..15");
        for (double r = 60; r < 3000; r += 37) {
            for (double deg = 0; deg < 360; deg += 2.5) {
                double dx = Math.sin(Math.toRadians(deg)) * r, dz = -Math.cos(Math.toRadians(deg)) * r;
                long n = WorldContent.AREAS.stream().filter(a -> a.contains(dx, dz)).count();
                assertEquals(1, n, "areas at r=" + r + " bearing=" + deg);
                Area a = Area.at(WorldContent.AREAS, dx, dz).orElseThrow();
                RegionDefinition reg = regions.at(dx, dz).orElseThrow();
                if (reg.safeZone()) continue; // the city square's corners reach past r=51; areas are hidden there
                assertEquals(reg.id(), a.regionId(), a.name() + " lies in " + reg.displayName());
                assertTrue(a.minLevel() >= reg.minLevel() && a.maxLevel() <= reg.maxLevel(), a.name() + " band inside " + reg.displayName());
            }
        }
    }

    @Test
    void theEastIsNoLongerOneEndlessSteppe() {
        long east = WorldContent.AREAS.stream().filter(a -> a.regionId().equals("region.kherlen")).count();
        assertEquals(6, east);
        assertEquals(24, WorldContent.AREAS.size());
    }
}
