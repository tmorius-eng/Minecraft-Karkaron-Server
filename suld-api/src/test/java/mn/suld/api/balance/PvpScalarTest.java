package mn.suld.api.balance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** PVP-01: a consented fight runs the hit formula at a share below one, never below half (GAME_DESIGN «PvP policy»). */
class PvpScalarTest {

    @Test
    void aDuelHitLandsLighterThanAMobHit() {
        assertTrue(CombatRules.PVP_SCALAR < 1.0 && CombatRules.PVP_SCALAR >= 0.5);
    }
}
