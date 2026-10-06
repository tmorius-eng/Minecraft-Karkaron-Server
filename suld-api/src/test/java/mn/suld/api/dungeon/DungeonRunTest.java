package mn.suld.api.dungeon;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DungeonRunTest {

    private static BossDefinition boss() {
        MobDefinition mob = new MobDefinition("mob.test_boss", "Test", "RAVAGER", MobTier.BOSS,
                10, 100.0, 10.0, 500, "loot.test");
        return new BossDefinition(mob, List.of(
                new BossPhase(1.0, 1.0, "Эхлэл"),
                new BossPhase(0.6, 1.3, "Уур"),
                new BossPhase(0.3, 1.6, "Галзуу")), 120);
    }

    @Test
    void fullHappyPathThroughWavesBossToComplete() {
        DungeonRun run = new DungeonRun("dungeon.test", UUID.randomUUID(), 2);
        assertEquals(DungeonRunState.ENTERING, run.state());

        run.startWave(2);
        assertEquals(DungeonRunState.WAVE, run.state());
        assertEquals(1, run.currentWave());
        assertFalse(run.isOnLastWave());
        assertFalse(run.recordWaveKill());
        assertTrue(run.recordWaveKill(), "second kill clears a 2-mob wave");

        run.startWave(1);
        assertEquals(2, run.currentWave());
        assertTrue(run.isOnLastWave());
        assertTrue(run.recordWaveKill());

        run.enterBoss();
        assertEquals(DungeonRunState.BOSS, run.state());
        run.complete();
        assertTrue(run.isTerminal());
        assertFalse(run.isActive());
        assertNotNull(run.completedAt());
    }

    @Test
    void cannotCompleteWithoutBoss() {
        DungeonRun run = new DungeonRun("dungeon.test", UUID.randomUUID(), 1);
        run.startWave(1);
        assertThrows(IllegalStateException.class, run::complete);
    }

    @Test
    void failIsOnlyValidWhileActive() {
        DungeonRun run = new DungeonRun("dungeon.test", UUID.randomUUID(), 1);
        run.fail();
        assertEquals(DungeonRunState.FAILED, run.state());
        assertThrows(IllegalStateException.class, run::fail);
    }

    @Test
    void waveKillsIgnoredOutsideWaveState() {
        DungeonRun run = new DungeonRun("dungeon.test", UUID.randomUUID(), 1);
        assertFalse(run.recordWaveKill());
        assertEquals(0, run.waveKillCount());
    }

    @Test
    void cannotStartWaveAfterBoss() {
        DungeonRun run = new DungeonRun("dungeon.test", UUID.randomUUID(), 1);
        run.startWave(1);
        run.enterBoss();
        assertThrows(IllegalStateException.class, () -> run.startWave(1));
    }

    @Test
    void bossPhaseSelectionByHpFraction() {
        BossDefinition b = boss();
        assertEquals(0, b.activePhaseIndex(1.0));
        assertEquals(0, b.activePhaseIndex(0.61));
        assertEquals(1, b.activePhaseIndex(0.6), "threshold is inclusive");
        assertEquals(1, b.activePhaseIndex(0.45));
        assertEquals(2, b.activePhaseIndex(0.3));
        assertEquals(2, b.activePhaseIndex(0.01));
        assertEquals(1.6, b.activePhase(0.1).attackMultiplier());
        assertEquals(2, b.finalPhaseIndex());
    }

    @Test
    void bossRejectsBadPhaseConfiguration() {
        MobDefinition mob = boss().mob();
        assertThrows(IllegalArgumentException.class, () -> new BossDefinition(mob, List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new BossDefinition(mob,
                List.of(new BossPhase(0.8, 1.0, "x")), 0), "first phase must be 1.0");
        assertThrows(IllegalArgumentException.class, () -> new BossDefinition(mob,
                List.of(new BossPhase(1.0, 1.0, "a"), new BossPhase(0.3, 1.2, "b"), new BossPhase(0.6, 1.4, "c")), 0),
                "thresholds must descend");
        assertThrows(IllegalArgumentException.class, () -> new BossPhase(1.5, 1.0, "bad"));
        assertThrows(IllegalArgumentException.class, () -> new BossPhase(0.5, 0.0, "bad"));
    }
}
