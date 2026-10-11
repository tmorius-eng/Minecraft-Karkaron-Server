package mn.suld.api.world.site;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SiteKindTest {

    @Test
    void everyBuildStaysInsideItsFootprintAndHeight() {
        for (SiteKind k : SiteKind.values()) {
            List<SiteKind.Block> bs = k.blocks();
            assertTrue(bs.size() >= 8, k + " is too small: " + bs.size());
            for (SiteKind.Block b : bs) {
                assertTrue(Math.abs(b.x()) <= k.radius() && Math.abs(b.z()) <= k.radius(), k + " block outside its radius: " + b);
                assertTrue(b.y() >= -1 && b.y() <= k.height(), k + " block outside -1.." + k.height() + ": " + b);
                assertTrue(b.block().matches("minecraft:[a-z_]+(\\[[a-z_]+=[a-z0-9_]+(,[a-z_]+=[a-z0-9_]+)*])?"), k + ": " + b.block());
            }
        }
    }

    @Test
    void buildsAreDeterministicAndHaveOneBlockPerCell() {
        for (SiteKind k : SiteKind.values()) {
            assertEquals(k.blocks(), k.blocks(), k + " changes between calls");
            Set<String> cells = new HashSet<>();
            for (SiteKind.Block b : k.blocks()) assertTrue(cells.add(b.x() + "," + b.y() + "," + b.z()), k + " sets a cell twice: " + b);
        }
    }

    @Test
    void aSiteSitsAtItsBearing() {
        HistoricSite s = new HistoricSite("site.test", "Test", SiteKind.STELE, "area.orkhon", 90, 500, "VERIFIED", "");
        assertArrayEquals(new int[]{500, 0}, s.offset(), "90° is east");
        assertArrayEquals(new int[]{0, -300}, new HistoricSite("site.north", "N", SiteKind.STELE, "area.orkhon", 0, 300, "VERIFIED", "").offset(), "0° is north (-z)");
        assertThrows(IllegalArgumentException.class, () -> new HistoricSite("site.close", "X", SiteKind.STELE, "a", 0, 50, "VERIFIED", ""));
    }
}
