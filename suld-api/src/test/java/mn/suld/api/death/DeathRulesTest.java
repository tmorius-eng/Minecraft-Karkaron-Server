package mn.suld.api.death;

import mn.suld.api.config.DeathSettings;
import mn.suld.api.progression.Progression;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathRulesTest {

    private final DeathSettings s = DeathSettings.defaults(); // 10% exp, 25% wear, drop 50% of stacks

    @Test
    void deathCostsProgressButNeverALevel() {
        assertEquals(new Progression(7, 900), DeathRules.afterDeath(new Progression(7, 1000), s));
        assertEquals(new Progression(7, 0), DeathRules.afterDeath(new Progression(7, 0), s));
        DeathSettings off = new DeathSettings(false, 30, 0.5, 0.5, true, 1, true);
        assertEquals(new Progression(3, 50), DeathRules.afterDeath(new Progression(3, 50), off));
    }

    @Test
    void halfTheDroppableStacksAreLostRoundedDown() {
        List<Integer> lost = DeathRules.lostStacks(List.of(1, 4, 7, 9, 12), s, new Random(42));
        assertEquals(2, lost.size());
        assertTrue(List.of(1, 4, 7, 9, 12).containsAll(lost));
        assertEquals(List.of(), DeathRules.lostStacks(List.of(3), s, new Random(1))); // 0.5 rounds down
        DeathSettings keep = new DeathSettings(true, 30, 0.1, 0.25, false, 0.5, true);
        assertEquals(List.of(), DeathRules.lostStacks(List.of(1, 2, 3, 4), keep, new Random(1)));
    }

    @Test
    void wearNeverBreaksAnItem() {
        assertEquals(63, DeathRules.wear(0, 250, s));
        assertEquals(249, DeathRules.wear(240, 250, s));
        assertEquals(5, DeathRules.wear(5, 0, s));
    }
}
