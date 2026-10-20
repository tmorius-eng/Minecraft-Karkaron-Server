package mn.suld.plugin.skill;

import mn.suld.api.balance.CombatRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SKILL-01 and PVP-01: what a spell's or an ultimate's damage is multiplied by, by target. */
class SpellScaleTest {

    @Test
    void aPlayerInAConsentedFightTakesThePvpScalarLikeABasicHit() {
        assertEquals(CombatRules.PVP_SCALAR, SkillService.spellScale(true, null, 1), 1e-9);
        assertEquals(CombatRules.PVP_SCALAR, SkillService.spellScale(true, null, 60), 1e-9);
    }

    @Test
    void aMobAboveTheCasterIsSuppressedByTheLevelGap() {
        assertEquals(1.0, SkillService.spellScale(false, 5, 5), 1e-9);
        assertEquals(1.0, SkillService.spellScale(false, 3, 10), 1e-9, "a mob below the caster takes the full amount");
        assertEquals(0.72, SkillService.spellScale(false, 12, 5), 1e-9);
        assertEquals(0.4, SkillService.spellScale(false, 60, 1), 1e-9, "the floor");
    }

    @Test
    void aTargetWithoutAContentDefinitionTakesTheFullAmount() {
        assertEquals(1.0, SkillService.spellScale(false, null, 30), 1e-9);
    }
}
