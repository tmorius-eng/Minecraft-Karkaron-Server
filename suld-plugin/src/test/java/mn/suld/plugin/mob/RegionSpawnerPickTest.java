package mn.suld.plugin.mob;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.region.RegionDefinition;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.content.WorldContent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit 2026-10-11 ONB-003: the city's main avenue leads south into the Gobi, whose nearest areas are levels 5–8, so the
 * outskirts' level-fitted picks (40 % of spawns) could send level 7–9 mobs at a level-1 player.
 */
class RegionSpawnerPickTest {

    private static List<MobDefinition> mobsOf(String regionId) {
        RegionDefinition r = WorldContent.REGIONS.stream().filter(x -> x.id().equals(regionId)).findFirst().orElseThrow();
        List<MobDefinition> out = new ArrayList<>();
        for (String id : r.mobIds()) out.add(Objects.requireNonNull(SuldContent.mobFor(id), id));
        return out;
    }

    @Test
    void outskirtsNeverSendMobsAboveTheCap() {
        int maxLevel = 1 + RegionSpawner.OUTSKIRTS_LEVEL_MARGIN; // a level-1 player
        for (String region : List.of("region.gobi", "region.kherlen", "region.altai", "region.khangai")) {
            List<MobDefinition> mobs = mobsOf(region);
            for (int want = 1; want <= 25; want++) {
                for (int i = 0; i < 100; i++) {
                    String id = RegionSpawner.pickByLevel(mobs, want, maxLevel, i / 100.0);
                    if (id == null) continue; // the spawner falls back to the first prey (Govi wolf, level 2)
                    int level = SuldContent.mobFor(id).level();
                    assertTrue(level <= maxLevel, region + " want " + want + " picked " + id + " level " + level);
                }
            }
        }
    }

    /** WORLD-01: a spot whose ±5 band holds no mob of its region gets the nearest level, never a random pick. */
    @Test
    void nothingWithinFiveFallsBackToTheNearestLevel() {
        List<MobDefinition> zuungar = mobsOf("region.zuungar"); // 32, 37, 39, 43, 45, 51, 59: the band is covered since the 2026-10-19 fill-in
        assertNull(RegionSpawner.pickByLevel(zuungar, 20, Integer.MAX_VALUE, 0.5), "nothing within 5 of 20 (below the band)");
        assertEquals("mob.zuungaryn_chono", RegionSpawner.nearestByLevel(zuungar, 20));
        assertEquals("mob.tengeriin_kharuul", RegionSpawner.nearestByLevel(zuungar, 70));
        assertNotNull(RegionSpawner.pickByLevel(zuungar, 27, Integer.MAX_VALUE, 0.5), "the inner edge has its own prey now");
        assertNull(RegionSpawner.nearestByLevel(List.of(), 30));
    }

    /**
     * WORLD-01: the four outer regions no longer share one list. Each keeps prey at both ends of its 27–60 band (a mob
     * at or below 40 and one at or above 55); a spot between two of its mobs gets the nearest level (the fallback
     * above), so a thin band — Зүүнгар's desert has nothing below the level-37 Tangut ghosts — never spawns a random one.
     */
    @Test
    void everyOuterRegionHasItsOwnPreyAcrossItsBand() {
        List<List<String>> lists = new ArrayList<>();
        for (String id : List.of("region.khentii", "region.zuungar", "region.otgon", "region.khuvsgul")) {
            List<MobDefinition> mobs = mobsOf(id);
            lists.add(mobs.stream().map(MobDefinition::id).toList());
            assertTrue(mobs.stream().anyMatch(m -> m.level() <= 40), id + " has no prey for its inner edge");
            assertTrue(mobs.stream().anyMatch(m -> m.level() >= 55), id + " has no prey for its far edge");
            for (int level = 27; level <= 60; level++) assertTrue(RegionSpawner.nearestByLevel(mobs, level) != null);
        }
        assertEquals(4, lists.stream().distinct().count(), "the outer regions have distinct prey");
    }

    @Test
    void withoutTheCapTheGobiOutskirtsDoPickHigherMobs() {
        List<MobDefinition> gobi = mobsOf("region.gobi");
        boolean sawHigh = false;
        for (int i = 0; i < 100; i++) {
            String id = RegionSpawner.pickByLevel(gobi, 7, Integer.MAX_VALUE, i / 100.0);
            if (id != null && SuldContent.mobFor(id).level() > 4) sawHigh = true;
        }
        assertTrue(sawHigh, "the uncapped pick is the behaviour the cap exists for");
        assertNull(RegionSpawner.pickByLevel(gobi, 7, 0, 0.5), "nothing fits a cap below every mob");
    }
}
