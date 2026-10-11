package mn.suld.plugin.mob;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.region.RegionDefinition;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.content.WorldContent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
