package mn.suld.api.clazz;

import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.tree.SkillState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClassMasteryTest {

    static ClassGear gear(int armorLevel, ArmorTier tier, int mastery) {
        return new ClassGear(armorLevel, 0, tier, 0, Map.of(), UUID.randomUUID(), Set.of(), List.of(), mastery, 0);
    }

    @Test
    void eachClassKeepsItsOwnTreeAndGearAcrossSwitches() {
        PlayerProfile p = PlayerProfile.createNew(UUID.randomUUID(), "Bat", Instant.EPOCH);
        assertTrue(p.selectClass(PlayerClass.BAATAR));
        SkillState baatarTree = SkillState.NONE.withRanks("b.root:1,b.str:2");
        p.skillState(baatarTree);
        ClassGear baatarGear = gear(14, ArmorTier.T3, 4);
        p.classGear(baatarGear);
        long coins = p.currency(), level = p.progression().level();

        assertTrue(p.switchClass(PlayerClass.MERGEN, 1000));
        assertEquals(PlayerClass.MERGEN, p.playerClass().orElseThrow());
        assertTrue(p.skillState().isEmpty(), "a first-time class starts with an empty tree");
        assertEquals(ClassGear.NONE, p.classGear(), "a first-time class starts with fresh gear");
        assertEquals(1000, p.lastClassChange());
        p.skillState(SkillState.NONE.withRanks("m.root:1"));
        p.classGear(gear(3, ArmorTier.T1, 1));

        assertTrue(p.switchClass(PlayerClass.BAATAR, 2000));
        assertEquals(baatarTree.ranks(), p.skillState().ranks(), "Баатар's tree comes back");
        assertEquals(baatarGear, p.classGear(), "Баатар's armour level, tier and mastery come back");
        assertEquals("m.root:1", p.classMasteries().of(PlayerClass.MERGEN).orElseThrow().skills().ranks());
        assertTrue(p.classMasteries().of(PlayerClass.BAATAR).isEmpty(), "the class being played is not stored twice");
        assertEquals(coins, p.currency());
        assertEquals(level, p.progression().level());
        assertFalse(p.switchClass(PlayerClass.BAATAR, 3000), "no switch to the class already played");
    }

    @Test
    void theDocumentRoundTrips() {
        ClassMasteries m = ClassMasteries.NONE
                .with(PlayerClass.BOO, new ClassMasteries.Saved(SkillState.NONE.withRanks("o.root:1"), gear(9, ArmorTier.T2, 2), 77))
                .with(PlayerClass.DARKHAN, new ClassMasteries.Saved(SkillState.NONE, ClassGear.NONE, 5));
        ClassMasteries back = ClassMasteries.fromJson(m.toJson());
        assertEquals(m, back);
        assertEquals(ClassMasteries.NONE, ClassMasteries.fromJson(null));
        assertEquals(ClassMasteries.NONE, ClassMasteries.fromJson("{}"));
        assertThrows(IllegalArgumentException.class, () -> ClassMasteries.fromJson("{\"v\":9}"));
        assertThrows(IllegalArgumentException.class, () -> ClassMasteries.fromJson("{\"v\":1,\"classes\":{\"nobody\":{}}}"));
    }
}
