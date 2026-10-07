package mn.suld.api.dungeon.hall;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class HallBlueprintTest {

    private static final Pattern BLOCK = Pattern.compile("minecraft:[a-z_]+(\\[[a-z_]+=[a-z0-9_]+(,[a-z_]+=[a-z0-9_]+)*])?");

    private static Map<Long, String> grid(List<HallBlueprint.Placement> ps) {
        Map<Long, String> m = new HashMap<>();
        for (HallBlueprint.Placement p : ps) m.put(key(p.x(), p.y(), p.z()), p.block());
        return m;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    private static boolean solid(String b) {
        return b != null && !b.equals("minecraft:air") && !b.startsWith("minecraft:lantern") && !b.startsWith("minecraft:soul_lantern") && !b.contains("candle") && !b.contains("end_rod") && !b.contains("campfire") && !b.contains("carpet") && !b.contains("snow[")
                && !b.contains("fern") && !b.contains("bush") && !b.contains("mushroom") && !b.contains("cobweb");
    }

    @Test
    void everyThemeBuildsAClosedLitHallWithClearSpawns() {
        for (HallTheme t : HallTheme.values()) {
            List<HallBlueprint.Placement> ps = HallBlueprint.build(t);
            assertEquals(ps, HallBlueprint.build(t), "deterministic");
            Map<Long, String> g = grid(ps);
            assertEquals(ps.size(), g.size(), t + ": one placement per block");
            for (HallBlueprint.Placement p : ps) {
                assertTrue(BLOCK.matcher(p.block()).matches(), t + ": bad block data " + p.block());
                assertTrue(Math.abs(p.x()) <= HallBlueprint.extent() && Math.abs(p.z()) <= HallBlueprint.extent(), t + ": outside extent");
                assertTrue(p.y() >= -2 && p.y() <= HallBlueprint.HEIGHT + 1, t + ": y out of range");
            }
            // enclosed: on every column just outside the arena (not the doorway) the wall stands from the floor to the roof
            for (int x = -HallBlueprint.R - 2; x <= HallBlueprint.R + 2; x++) {
                for (int z = -HallBlueprint.R - 2; z <= HallBlueprint.R + 2; z++) {
                    double r = Math.hypot(x, z);
                    if (r <= HallBlueprint.R + 0.5 || r > HallBlueprint.R + HallBlueprint.WALL + 0.5) continue;
                    boolean doorway = z > 0 && Math.abs(x) <= HallBlueprint.CORRIDOR_HALF;
                    int from = doorway ? HallBlueprint.CORRIDOR_HEIGHT + 1 : 1;
                    for (int y = from; y <= HallBlueprint.HEIGHT + 1; y++) {
                        assertTrue(solid(g.get(key(x, y, z))), t + ": gap in the wall at " + x + "," + y + "," + z);
                    }
                }
            }
            // a roof (stone or barrier) over the whole arena
            for (int x = -HallBlueprint.R; x <= HallBlueprint.R; x++) {
                for (int z = -HallBlueprint.R; z <= HallBlueprint.R; z++) {
                    if (Math.hypot(x, z) > HallBlueprint.R + 0.5) continue;
                    assertNotNull(g.get(key(x, HallBlueprint.HEIGHT + 1, z)), t + ": no roof at " + x + "," + z);
                }
            }
            for (HallBlueprint.Anchor a : List.of(HallBlueprint.PLAYER_SPAWN, HallBlueprint.WAVE_CENTER, HallBlueprint.BOSS_SPAWN)) {
                int ax = (int) Math.floor(a.x()), ay = (int) Math.floor(a.y()), az = (int) Math.floor(a.z());
                assertTrue(solid(g.get(key(ax, ay - 1, az))), t + ": nothing to stand on at " + a);
                assertEquals("minecraft:air", g.get(key(ax, ay, az)), t + ": spawn blocked at " + a);
                assertEquals("minecraft:air", g.get(key(ax, ay + 1, az)), t + ": head blocked at " + a);
            }
            long lights = ps.stream().filter(p -> p.block().equals(t.lamp()) || p.block().equals(t.lightBlock())).count();
            assertTrue(lights >= 16, t + ": too dark (" + lights + " lights)");
        }
    }

    @Test
    void entranceHasADoorwayAndAClearApproach() {
        for (HallTheme t : HallTheme.values()) {
            Map<Long, String> g = grid(EntranceBlueprint.build(t));
            assertEquals("minecraft:black_concrete", g.get(key(0, 3, -1)), t + ": doorway");
            HallBlueprint.Anchor s = EntranceBlueprint.STAND;
            assertEquals("minecraft:air", g.get(key((int) Math.floor(s.x()), 1, (int) Math.floor(s.z()))));
            assertTrue(solid(g.get(key((int) Math.floor(s.x()), 0, (int) Math.floor(s.z())))));
        }
    }

    @Test
    void sitesSitInsideTheBorder() {
        DungeonSite s = new DungeonSite("dungeon.govi_bulsh", HallTheme.TOMB, 180, 650);
        assertArrayEquals(new int[]{0, 650}, s.offset());
        assertEquals("govi_bulsh", s.shortId());
        assertThrows(IllegalArgumentException.class, () -> new DungeonSite("x", HallTheme.DEN, 0, 5200));
    }
}
