package mn.suld.plugin.death;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SULD-F09 (trial wolves are hurt by their soul only) and SULD-F11 (they stand on the soul's floor, not a cave roof). */
class DeathTrialServiceTest {

    /** A column: {@code solid} heights are blocks, everything else is air. */
    static int floor(int soulY, Set<Integer> solid) {
        IntPredicate s = solid::contains;
        return DeathTrialService.floorY(soulY, s, s.negate());
    }

    @Test
    void flatGroundIsTheSoulsOwnHeight() {
        assertEquals(40, floor(40, Set.of(39)));
    }

    @Test
    void aCaveRoofAboveIsNotChosenOverTheCaveFloor() {
        // a cave at y 30..33 under a roof whose top is y 70: the old heightmap put the wolf at y 71
        Set<Integer> col = new java.util.HashSet<>();
        for (int y = 0; y < 30; y++) col.add(y);
        for (int y = 34; y <= 70; y++) col.add(y);
        assertEquals(30, floor(31, col)); // the soul stands a block up a slope: the floor is one down
    }

    @Test
    void aStepWithinSixBlocksIsFound() {
        assertEquals(44, floor(40, Set.of(43, 30)));
        assertEquals(34, floor(40, Set.of(33)));
    }

    @Test
    void noFloorWithinSixBlocksFallsBackToTheSoulsHeight() {
        assertEquals(40, floor(40, Set.of(20)));
        assertEquals(40, floor(40, Set.of()));
    }

    @Test
    void aOneBlockGapIsNoSpot() {
        // y 39 solid, 41 solid: only one air block at 40; the next spot is on top at 42
        assertEquals(42, floor(40, Set.of(39, 41)));
    }

    @Test
    void theWorldCannotKillATrialWolf() {
        for (DamageCause c : new DamageCause[] {DamageCause.LAVA, DamageCause.FIRE, DamageCause.FIRE_TICK, DamageCause.CONTACT,
                DamageCause.DROWNING, DamageCause.FALL, DamageCause.SUFFOCATION}) {
            assertTrue(DeathService.shieldsTrialWolf(true, false, c), c.name());
        }
    }

    @Test
    void aBiteOrABlowAndTheVoidStillReachATrialWolf() {
        assertFalse(DeathService.shieldsTrialWolf(true, true, DamageCause.ENTITY_ATTACK)); // its soul's blow
        assertFalse(DeathService.shieldsTrialWolf(true, true, DamageCause.PROJECTILE));
        assertFalse(DeathService.shieldsTrialWolf(true, false, DamageCause.VOID));
        assertFalse(DeathService.shieldsTrialWolf(true, false, DamageCause.KILL));
    }

    @Test
    void anOrdinaryEntityIsNotShielded() {
        assertFalse(DeathService.shieldsTrialWolf(false, false, DamageCause.LAVA));
    }
}
