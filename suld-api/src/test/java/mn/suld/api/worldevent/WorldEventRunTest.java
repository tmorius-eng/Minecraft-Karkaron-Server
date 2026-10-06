package mn.suld.api.worldevent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldEventRunTest {

    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");

    private static WorldEventDefinition def(int target) {
        return new WorldEventDefinition("event.test", "Test", "desc", Set.of("mob.wolf"),
                target, 600, 100, 20, 2, 5, 200);
    }

    @Test
    void onlyTargetMobsCountAndCompletionFlips() {
        WorldEventRun run = new WorldEventRun(def(3), T0);
        UUID p = UUID.randomUUID();
        assertFalse(run.recordKill(p, "mob.other", T0));
        assertTrue(run.recordKill(p, "mob.wolf", T0));
        assertTrue(run.recordKill(p, "mob.wolf", T0.plusSeconds(1)));
        assertTrue(run.isActive());
        assertTrue(run.recordKill(p, "mob.wolf", T0.plusSeconds(2)));
        assertEquals(WorldEventState.SUCCEEDED, run.state());
        assertFalse(run.recordKill(p, "mob.wolf", T0.plusSeconds(3)), "no counting after success");
        assertEquals(3, run.kills());
        assertEquals(1.0, run.progress());
    }

    @Test
    void expiresAtDeadlineAndKillsAfterDoNotCount() {
        WorldEventRun run = new WorldEventRun(def(10), T0);
        assertFalse(run.expireIfDue(T0.plusSeconds(599)));
        assertEquals(1, run.remainingSeconds(T0.plusSeconds(599)));
        assertFalse(run.recordKill(UUID.randomUUID(), "mob.wolf", T0.plusSeconds(600)));
        assertEquals(WorldEventState.FAILED, run.state());
        assertFalse(run.expireIfDue(T0.plusSeconds(700)), "transition reported once");
        assertTrue(run.rewards().isEmpty(), "failed events pay nothing");
    }

    @Test
    void rewardsRankedWithPodiumAndThreshold() {
        WorldEventRun run = new WorldEventRun(def(10), T0);
        UUID top = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID(),
                fourth = UUID.randomUUID(), freeloader = UUID.randomUUID();
        for (int i = 0; i < 3; i++) run.recordKill(top, "mob.wolf", T0);
        for (int i = 0; i < 2; i++) run.recordKill(second, "mob.wolf", T0);
        for (int i = 0; i < 2; i++) run.recordKill(third, "mob.wolf", T0);
        run.recordKill(freeloader, "mob.wolf", T0);          // 1 kill < minContribution 2
        run.recordKill(fourth, "mob.wolf", T0);
        assertTrue(run.recordKill(fourth, "mob.wolf", T0)); // 10th kill completes
        assertEquals(WorldEventState.SUCCEEDED, run.state());
        List<EventReward> rewards = run.rewards();
        assertEquals(4, rewards.size());
        assertEquals(top, rewards.get(0).playerId());
        assertEquals(150, rewards.get(0).exp());
        assertEquals(30, rewards.get(0).currency());
        assertEquals(130, rewards.get(1).exp());
        assertEquals(120, rewards.get(2).exp());
        assertEquals(100, rewards.get(3).exp());
        assertEquals(4, rewards.get(3).rank());
        assertTrue(rewards.stream().noneMatch(r -> r.playerId().equals(freeloader)));
    }

    @Test
    void cancelFails() {
        WorldEventRun run = new WorldEventRun(def(5), T0);
        run.cancel();
        assertEquals(WorldEventState.FAILED, run.state());
    }

    @Test
    void definitionValidation() {
        assertThrows(IllegalArgumentException.class, () -> new WorldEventDefinition("e", "n", "d",
                Set.of("m"), 0, 60, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WorldEventDefinition("e", "n", "d",
                Set.of("m"), 5, 60, -1, 1, 1, 1, 1));
    }
}
