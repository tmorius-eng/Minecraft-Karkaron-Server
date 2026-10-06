package mn.suld.api.reward;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DailyTasksTest {

    private static final List<DailyTasks.Prey> POOL = List.of(
            new DailyTasks.Prey("mob.wolf", "Wolf", "Kherlen", 2, 50, 1),
            new DailyTasks.Prey("mob.bandit", "Bandit", "Kherlen", 4, 70, 1),
            new DailyTasks.Prey("mob.scorpion", "Scorpion", "Gobi", 7, 90, 5),
            new DailyTasks.Prey("mob.giant", "Giant", "Altai", 23, 300, 18));

    @Test
    void stablePerDayAndLimitedToReachableRegions() {
        UUID p = UUID.randomUUID();
        List<DailyTasks.Task> a = DailyTasks.tasks(p, 20_000, 2, POOL);
        assertEquals(a, DailyTasks.tasks(p, 20_000, 2, POOL));
        assertEquals(3, a.size());
        assertTrue(a.stream().noneMatch(t -> t.prey().mobId().equals("mob.giant")), "level 2 never gets Altai giants");
        assertTrue(a.stream().allMatch(t -> t.count() >= 5 && t.count() <= 12 && t.coins() > 0 && t.exp() > 0));
    }

    @Test
    void killsAdvanceAndCompleteOnce() {
        List<DailyTasks.Task> tasks = List.of(new DailyTasks.Task(POOL.get(0), 2, 10, 10), new DailyTasks.Task(POOL.get(1), 1, 10, 10),
                new DailyTasks.Task(POOL.get(0), 1, 10, 10));
        int[] pr = DailyTasks.progress("");
        assertEquals(-1, DailyTasks.kill(tasks, pr, "mob.wolf"));
        assertEquals(0, DailyTasks.kill(tasks, pr, "mob.wolf"));   // first task done
        assertEquals(2, DailyTasks.kill(tasks, pr, "mob.wolf"));   // then the next wolf task
        assertEquals(-2, DailyTasks.kill(tasks, pr, "mob.wolf"));  // all wolf tasks finished
        assertEquals(-2, DailyTasks.kill(tasks, pr, "mob.bear"));
        assertEquals("2,0,1", DailyTasks.format(pr));
        assertArrayEquals(new int[]{2, 0, 1}, DailyTasks.progress("2,0,1"));
        assertArrayEquals(new int[]{0, 0, 0}, DailyTasks.progress("x,,-3"));
    }
}
