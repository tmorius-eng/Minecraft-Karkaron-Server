package mn.suld.api.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossTargetsTest {

    @Test
    void aLivingSurvivalPlayerIsATarget() {
        assertTrue(BossTargets.eligible(true, false, false));
    }

    @Test
    void aSpectatorOrCreativeBuilderIsNot() {
        assertFalse(BossTargets.eligible(false, false, false));
    }

    @Test
    void theDeadAreNot() {
        assertFalse(BossTargets.eligible(true, true, false));
    }

    @Test
    void aSoulIsNot() {
        assertFalse(BossTargets.eligible(true, false, true));
    }
}
