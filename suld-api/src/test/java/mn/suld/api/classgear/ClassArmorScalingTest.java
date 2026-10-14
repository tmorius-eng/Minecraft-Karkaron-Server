package mn.suld.api.classgear;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemStat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT-16 (audit 2026-10-14): the T4 class pieces had no armour growth (a flat proxy fit), so inside the tier the T3
 * piece kept growing and overtook them. Every tier now grows its armour, and no tier is out-armoured by the one below.
 */
class ClassArmorScalingTest {

    static final ItemCatalog C = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();

    /** The mean armour of a definition at an armour level (mid-range roll plus the per-level growth). */
    static double armor(ItemDefinition d, int al) {
        var r = d.stats().get(ItemStat.ARMOR);
        return (r.min() + r.max()) / 2 + d.statPerLevel().getOrDefault(ItemStat.ARMOR, 0.0) * (al - 1);
    }

    @Test
    void everyTierGrowsItsArmour() {
        for (PlayerClass c : PlayerClass.values()) for (ArmorTier t : ArmorTier.values()) for (ArmorPiece p : ArmorPiece.values()) {
            ItemDefinition d = C.require(ArmorRules.definitionId(c, p, t));
            assertTrue(d.statPerLevel().getOrDefault(ItemStat.ARMOR, 0.0) > 0, d.id() + " has no armour growth");
        }
    }

    @Test
    void t3NeverOvertakesT4AndT4NeverOvertakesT5() {
        for (PlayerClass c : PlayerClass.values()) for (ArmorPiece p : ArmorPiece.values()) {
            ItemDefinition t3 = C.require(ArmorRules.definitionId(c, p, ArmorTier.T3));
            ItemDefinition t4 = C.require(ArmorRules.definitionId(c, p, ArmorTier.T4));
            ItemDefinition t5 = C.require(ArmorRules.definitionId(c, p, ArmorTier.T5));
            for (int al = ArmorTier.T4.armorLevel(); al <= 60; al++) {
                assertTrue(armor(t4, al) > armor(t3, al), t4.id() + " out-armoured by T3 at armour level " + al);
            }
            for (int al = ArmorTier.T5.armorLevel(); al <= 60; al++) {
                assertTrue(armor(t5, al) > armor(t4, al), t5.id() + " out-armoured by T4 at armour level " + al);
            }
        }
    }
}
