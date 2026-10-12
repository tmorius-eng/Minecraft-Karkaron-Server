package mn.suld.api.clazz;

import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.profile.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClassMasteryLevelTest {

    @Test
    void theClassInPlayAndTheSavedOnesAreCountedOnce() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Bat", Instant.EPOCH);
        assertTrue(p.selectClass(PlayerClass.BAATAR));
        p.classGear(ClassMasteryTest.gear(60, ArmorTier.T5, 7));
        assertTrue(p.switchClass(PlayerClass.MERGEN, 1));
        p.classGear(ClassMasteryTest.gear(34, ArmorTier.T3, 2));
        var levels = ClassMastery.levels(p);
        assertEquals(60, levels.get(PlayerClass.BAATAR));
        assertEquals(34, levels.get(PlayerClass.MERGEN));
        assertFalse(levels.containsKey(PlayerClass.BOO), "a class never played has no mastery");
        assertEquals(1, ClassMastery.masters(p));
        assertNull(ClassMastery.title(1));
    }

    @Test
    void allFiveMasteredGiveTheTitle() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Bat", Instant.EPOCH);
        assertTrue(p.selectClass(PlayerClass.BAATAR));
        PlayerClass[] all = PlayerClass.values();
        for (int i = 0; i < all.length; i++) {
            if (i > 0) assertTrue(p.switchClass(all[i], i));
            p.classGear(ClassMasteryTest.gear(60, ArmorTier.T6, 10));
        }
        assertEquals(5, ClassMastery.masters(p));
        assertEquals("Таван Ангийн Эзэн", ClassMastery.title(5));
    }

    @Test
    void milestonesAreThePassedOnes() {
        assertEquals(0, ClassMastery.milestone(5, 19));
        assertEquals(20, ClassMastery.milestone(19, 20));
        assertEquals(40, ClassMastery.milestone(18, 45), "a jump over two shows the higher");
        assertEquals(60, ClassMastery.milestone(59, 60));
        assertEquals(0, ClassMastery.milestone(60, 60));
    }
}
