package mn.suld.api.classgear;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemGenerator;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import mn.suld.api.loot.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT-04 (audit 2026-10-14): the class weapon "grows with its bearer" between the tier levels too, never drops at a
 * tier-up, and stays in reach of a classless weapon of the same level.
 */
class ClassWeaponScalingTest {

    static final ItemCatalog C = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();
    static final ItemGenerator GEN = new ItemGenerator(C);

    static double damage(String id, ItemRarity r, int level) {
        ItemDefinition d = C.require(id);
        return GEN.generate(d, r == null ? d.rarity() : r, level, Rng.seeded(7), "test", null).stats().getOrDefault(ItemStat.DAMAGE, 0.0);
    }

    static String id(PlayerClass c, int tier) {
        return "weapon.class." + c.id() + "." + tier;
    }

    @Test
    void everyClassWeaponGrowsWithinItsTier() {
        for (PlayerClass c : PlayerClass.values()) {
            for (int t = 1; t < WeaponTiers.MAX_TIER; t++) {
                int first = Math.max(WeaponTiers.LEVEL[t], C.require(id(c, t)).levelReq());
                int last = WeaponTiers.LEVEL[t + 1] - 1;
                assertTrue(damage(id(c, t), null, last) > damage(id(c, t), null, first), id(c, t) + " stands still from " + first + " to " + last);
            }
        }
    }

    @Test
    void aTierUpNeverLowersTheDamage() {
        for (PlayerClass c : PlayerClass.values()) {
            for (int t = 1; t < WeaponTiers.MAX_TIER; t++) {
                int at = WeaponTiers.LEVEL[t + 1];
                assertTrue(damage(id(c, t + 1), null, at) >= damage(id(c, t), null, at - 1), id(c, t + 1) + " at level " + at);
            }
        }
    }

    @Test
    void aLevel20ClasslessSwordIsNoLongerThreeTimesTheClassWeapon() {
        double classless = damage("weapon.talyn_ild", ItemRarity.RARE, 20);
        double klass = damage(id(PlayerClass.BAATAR, WeaponTiers.tierFor(20)), null, 20);
        assertTrue(classless / klass < 1.6, "classless " + classless + " vs class " + klass);
    }
}
