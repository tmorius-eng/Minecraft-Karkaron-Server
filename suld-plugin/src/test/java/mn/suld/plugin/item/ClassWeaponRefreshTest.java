package mn.suld.plugin.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IT-04 (a class weapon follows its bearer's level inside a tier) and IT-05 (its rolls are fixed per owner + item + tier). */
class ClassWeaponRefreshTest {

    @Test
    void aWeaponIsRebuiltOnANewTierAndWhenTheLevelOutgrewIt() {
        assertTrue(ClassWeapons.needsRefresh(1, 2, 11, 12)); // tier-up
        assertTrue(ClassWeapons.needsRefresh(2, 2, 12, 13)); // a level inside tier 2
        assertFalse(ClassWeapons.needsRefresh(2, 2, 13, 13)); // already at the level
        assertFalse(ClassWeapons.needsRefresh(3, 2, 30, 20)); // never down a tier
    }

    @Test
    void theRollSeedIsStablePerOwnerItemAndTier() {
        UUID owner = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID item = UUID.fromString("66666666-7777-8888-9999-000000000000");
        assertEquals(ClassWeapons.rollSeed(owner, item, 3), ClassWeapons.rollSeed(owner, item, 3));
        assertNotEquals(ClassWeapons.rollSeed(owner, item, 3), ClassWeapons.rollSeed(owner, item, 4));
        assertNotEquals(ClassWeapons.rollSeed(owner, item, 3), ClassWeapons.rollSeed(UUID.randomUUID(), item, 3));
        assertNotEquals(ClassWeapons.rollSeed(owner, item, 3), ClassWeapons.rollSeed(owner, UUID.randomUUID(), 3));
    }
}
