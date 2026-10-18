package mn.suld.api.item;

import mn.suld.api.clazz.PlayerClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three weapon questions answer differently, each from one rule. */
class ClassWeaponRuleTest {

    static final ItemCatalog CAT = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();

    private static boolean fights(String id, PlayerClass c) {
        return ClassWeaponRule.fightsWith(id, CAT.item(id).orElse(null), c);
    }

    @Test
    void theClassWeaponIsTheClasssOwnLine() {
        assertTrue(ClassWeaponRule.isClassWeapon("weapon.class.mergen.3"));
        assertTrue(ClassWeaponRule.isClassWeapon("weapon.class.mergen.3", PlayerClass.MERGEN));
        assertFalse(ClassWeaponRule.isClassWeapon("weapon.class.mergen.3", PlayerClass.BAATAR));
        assertFalse(ClassWeaponRule.isClassWeapon("weapon.class.mergenx.3", PlayerClass.MERGEN), "the class id ends at its dot");
        assertFalse(ClassWeaponRule.isClassWeapon("weapon.khurdan_num"));
        assertFalse(ClassWeaponRule.isClassWeapon(null));
    }

    @Test
    void aClassFightsWithItsWeaponAndItsAffinityTypes() {
        assertTrue(fights("weapon.class.baatar.1", PlayerClass.BAATAR));
        assertTrue(fights("weapon.khurdan_num", PlayerClass.MERGEN), "a crafted bow is a Мэргэн weapon");
        assertFalse(fights("weapon.khurdan_num", PlayerClass.BAATAR), "a bow is not a Баатар weapon");
        assertTrue(fights("weapon.talyn_ild", PlayerClass.BAATAR));
        assertFalse(fights("weapon.class.mergen.1", PlayerClass.BAATAR), "another class's weapon");
        assertFalse(fights("item.chonon_arisan", PlayerClass.BAATAR), "a material");
    }

    @Test
    void anySuldWeaponCountsForLockOn() {
        assertTrue(ClassWeaponRule.isWeapon("weapon.khurdan_num", CAT.item("weapon.khurdan_num").orElse(null)));
        assertTrue(ClassWeaponRule.isWeapon("weapon.class.boo.2", null), "a class weapon even without its definition");
        assertFalse(ClassWeaponRule.isWeapon("item.chonon_arisan", CAT.item("item.chonon_arisan").orElse(null)));
        assertFalse(ClassWeaponRule.isWeapon("x", null));
    }
}
