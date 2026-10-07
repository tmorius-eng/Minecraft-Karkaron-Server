package mn.suld.api.skill.tree;

import mn.suld.api.skill.Spell;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Equipment joins the skill build: the combat engine reads one build. */
class SkillBuildPlusTest {

    @Test
    void equipmentAddsStatsModsAndProcsWithoutTouchingTheTreeBuild() {
        SkillBuild empty = SkillBuild.EMPTY;
        assertTrue(empty.isEmpty());
        Effect.Proc proc = new Effect.Proc(TriggerEvent.HIT, 10, ProcKind.BONUS, 1, 0, 2);
        SkillBuild with = empty.plus(Map.of(StatKey.CRIT_CHANCE, 5.0, StatKey.HEALTH, 10.0),
                Map.of(Spell.TENGER_TSAVCHILT, Map.of(ModKey.DAMAGE_PCT, 12.0)), List.of(proc), 100_000);
        assertFalse(with.isEmpty(), "gear alone must switch the combat hooks on");
        assertEquals(5.0, with.stat(StatKey.CRIT_CHANCE));
        assertEquals(10.0, with.stat(StatKey.HEALTH));
        assertEquals(12.0, with.mod(Spell.TENGER_TSAVCHILT, ModKey.DAMAGE_PCT));
        assertEquals(1, with.procs(TriggerEvent.HIT).size());
        assertEquals(100_000, with.procs(TriggerEvent.HIT).get(0).node(), "item passives get their own cooldown index");
        assertEquals(0.0, empty.stat(StatKey.CRIT_CHANCE), "the original build is unchanged");
        assertSame(empty, empty.plus(Map.of(), Map.of(), List.of(), 0), "nothing added: same build");
        SkillBuild twice = with.plus(Map.of(StatKey.CRIT_CHANCE, 2.0), Map.of(), List.of(), 200_000);
        assertEquals(7.0, twice.stat(StatKey.CRIT_CHANCE), 1e-9, "values add up");
    }
}
