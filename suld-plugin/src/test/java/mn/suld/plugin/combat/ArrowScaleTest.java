package mn.suld.plugin.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** WP-08: an arrow's damage share is its launch force, not the speed the air has left it on impact. */
class ArrowScaleTest {

    @Test
    void theLaunchForceDecidesNotTheImpactDamage() {
        // a full draw that slowed to 60 % of its speed over a long shot: vanilla damage 3.6 instead of 6
        assertEquals(1.0, CombatListener.arrowScale(1.0f, 3.6), 1e-9);
        assertEquals(0.6, CombatListener.arrowScale(0.6f, 2.0), 1e-6);
    }

    @Test
    void anUnmarkedArrowKeepsTheVanillaScale() {
        assertEquals(1.0, CombatListener.arrowScale(null, 6.0), 1e-9);
        assertEquals(0.5, CombatListener.arrowScale(null, 3.0), 1e-9);
    }

    @Test
    void bothAreClamped() {
        assertEquals(0.25, CombatListener.arrowScale(0.05f, 6.0), 1e-9);
        assertEquals(0.25, CombatListener.arrowScale(null, 0.5), 1e-9);
        assertEquals(1.4, CombatListener.arrowScale(null, 20.0), 1e-9);
    }
}
