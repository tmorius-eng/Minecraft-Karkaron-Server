package mn.suld.api.skill;

import mn.suld.api.clazz.PlayerClass;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTest {

    @Test
    void everyClassHasFiveSpells_fourCombosAndShiftF() {
        for (PlayerClass c : PlayerClass.values()) {
            Set<String> combos = new HashSet<>();
            assertEquals(5, Spell.of(c).size(), c.name());
            for (Spell s : Spell.of(c)) {
                if (s.slot() == Spell.SHIFT_F_SLOT) {
                    // the fifth spell has no combo: Shift+F casts it, and no click sequence reaches it
                    assertEquals("", s.combo());
                    assertEquals("Shift+F", s.keys());
                    assertEquals(s, Spell.shiftF(c).orElseThrow());
                    assertTrue(Spell.byCombo(c, "").isEmpty());
                    continue;
                }
                assertTrue(combos.add(s.combo()), s.name());
                assertEquals(Spell.firstClick(c), s.combo().charAt(0));
                assertEquals(s, Spell.byCombo(c, s.combo()).orElseThrow());
            }
        }
        assertEquals("RLR", Spell.TENGER_TSAVCHILT.combo());
        assertEquals("LRL", Spell.CHONYN_NUD.combo());
        assertEquals(35, Spell.TENGERIIN_SUM.unlockLevel());
        assertEquals(50, Spell.SUMNY_BOROO.unlockLevel());
    }

    @Test
    void spellsRankUpWithTheLevel() {
        assertEquals(1, Spell.TENGER_TSAVCHILT.rankAt(39));
        assertEquals(2, Spell.TENGER_TSAVCHILT.rankAt(40));
        assertEquals(3, Spell.TENGER_TSAVCHILT.rankAt(55));
        // the fifth spell opens at 50, so it ranks up later
        assertEquals(1, Spell.GALT_UUL.rankAt(50));
        assertEquals(2, Spell.GALT_UUL.rankAt(55));
        assertEquals(3, Spell.GALT_UUL.rankAt(60));
        assertEquals(1.0, Spell.rankPower(1), 1e-9);
        assertEquals(1.15, Spell.rankPower(2), 1e-9);
        assertEquals(1.30, Spell.rankPower(3), 1e-9);
        assertEquals(1.0, Spell.rankCooldown(2), 1e-9);
        assertEquals(0.9, Spell.rankCooldown(3), 1e-9);
        assertEquals("III", Spell.roman(3));
    }

    @Test
    void everySpellHasACooldownThatGrowsWithItsSlot() {
        for (PlayerClass c : PlayerClass.values()) {
            double previous = 0;
            for (Spell s : Spell.of(c)) {
                assertTrue(s.cooldownSeconds() > previous, s.name());
                previous = s.cooldownSeconds();
            }
        }
        assertEquals(1.5, Spell.TENGER_TSAVCHILT.cooldownSeconds());
        assertEquals(10, Spell.TENGERIIN_SUM.cooldownSeconds());
    }

    @Test
    void comboStartsOnlyWithTheFirstClickAndExpires() {
        ComboTracker t = new ComboTracker(PlayerClass.BAATAR);
        assertEquals(ComboTracker.Kind.IGNORED, t.click('L', 0).kind()); // a normal attack doesn't start a combo
        assertEquals(ComboTracker.Kind.PROGRESS, t.click('R', 100).kind());
        assertEquals(ComboTracker.Kind.PROGRESS, t.click('L', 300).kind());
        ComboTracker.Result r = t.click('R', 500);
        assertEquals(ComboTracker.Kind.COMPLETE, r.kind());
        assertEquals(Spell.TENGER_TSAVCHILT, t.spellFor(r.combo()).orElseThrow());
        assertEquals("", t.current(600));
        t.click('R', 1_000);
        assertEquals("R", t.current(1_500));
        assertEquals("", t.current(2_100)); // expired after the window
        assertEquals(ComboTracker.Kind.IGNORED, t.click('L', 2_200).kind());
        ComboTracker archer = new ComboTracker(PlayerClass.MERGEN);
        assertEquals(ComboTracker.Kind.IGNORED, archer.click('R', 0).kind()); // drawing the bow
        assertEquals(ComboTracker.Kind.PROGRESS, archer.click('L', 10).kind());
    }

    @Test
    void resourcePoolSpendsOnlyWhatItHas() {
        ResourcePool p = new ResourcePool(100);
        assertTrue(p.spend(60));
        assertFalse(p.spend(41));
        assertEquals(40, p.value());
        p.gain(1_000);
        assertEquals(100, p.value());
        assertFalse(p.spend(-5));
    }
}
