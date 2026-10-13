package mn.suld.plugin.worldbuild;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What stands by the spawn (the dungeon gates, the ovoos, the historic sites) is built only once the spawn is where it
 * stays: on a fresh world the city moves it onto its plaza when it is built, and the gates built before that stood a
 * second time 18 blocks off (map inspection, 2026-10-13).
 */
class SpawnSettledTest {

    @Test
    void aCityOnItsWayKeepsTheSpawnUnsettled() {
        assertFalse(WorldBuildService.spawnSettled(true, null), "a fresh world whose auto-build has not planned yet");
        assertFalse(WorldBuildService.spawnSettled(true, BuildState.Status.ROLLED_BACK), "an upgraded plan between its rollback and the new plan");
        assertFalse(WorldBuildService.spawnSettled(false, BuildState.Status.PLANNED));
        assertFalse(WorldBuildService.spawnSettled(false, BuildState.Status.BUILDING));
        assertFalse(WorldBuildService.spawnSettled(false, BuildState.Status.PAUSED));
    }

    @Test
    void aBuiltCityOrNoCityAtAllIsSettled() {
        assertTrue(WorldBuildService.spawnSettled(false, null), "no city on its way (auto-build off, or the plan failed early)");
        for (BuildState.Status s : new BuildState.Status[]{BuildState.Status.BUILT, BuildState.Status.APPROVED, BuildState.Status.LOCKED,
                BuildState.Status.ROLLED_BACK, BuildState.Status.FAILED}) {
            assertTrue(WorldBuildService.spawnSettled(false, s), s.name());
        }
    }
}
