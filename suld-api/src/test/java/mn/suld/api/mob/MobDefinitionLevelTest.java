package mn.suld.api.mob;

import mn.suld.api.balance.MobScaling;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobDefinitionLevelTest {

    @Test
    void aMobLiftedToSixtyHasTheNumbersOfALevelSixtyMobOfItsTier() {
        MobDefinition wolf = MobDefinition.designed("mob.goviin_chono", "Говийн Чоно", "WOLF", MobTier.ELITE, 5, "loot.x");
        MobDefinition lifted = wolf.atLevel(60);
        MobDefinition native60 = MobDefinition.designed("mob.other", "Өөр", "WOLF", MobTier.ELITE, 60, "loot.y");
        assertEquals(60, lifted.level());
        assertEquals(native60.scaledHealth(), lifted.scaledHealth(), 1e-9);
        assertEquals(native60.scaledAttack(), lifted.scaledAttack(), 1e-9);
        assertEquals(native60.scaledExp(), lifted.scaledExp());
        assertEquals(wolf.id(), lifted.id());
        assertEquals(wolf.lootTableId(), lifted.lootTableId());
        assertEquals(MobTier.ELITE, lifted.tier());
        assertTrue(lifted.scaledHealth() > wolf.scaledHealth() * 10, "a real lift: " + wolf.scaledHealth() + " -> " + lifted.scaledHealth());
        assertEquals(MobScaling.baseHealth(60), lifted.baseHealth(), 1e-9);
        assertSame(wolf, wolf.atLevel(5), "the same level is the same definition");
    }

    @Test
    void aContentOverrideKeepsItsRatioToTheCurve() {
        MobDefinition curve = MobDefinition.designed("mob.a", "А", "WOLF", MobTier.NORMAL, 5, "loot.x");
        // the loader lets content set "health"/"attack": this one has twice the curve's health and half its attack
        MobDefinition tough = new MobDefinition("mob.a", "А", "WOLF", MobTier.NORMAL, 5,
                curve.baseHealth() * 2, curve.baseAttack() / 2, curve.baseExp(), "loot.x");
        MobDefinition lifted = tough.atLevel(60);
        assertEquals(MobScaling.baseHealth(60) * 2, lifted.baseHealth(), 1e-6);
        assertEquals(MobScaling.baseDamage(60) / 2, lifted.baseAttack(), 1e-6);
        assertEquals(MobScaling.baseExp(60), lifted.baseExp());
    }
}
