package mn.suld.api.training;

import mn.suld.api.skill.Spell;
import mn.suld.api.skill.tree.Effect;
import mn.suld.api.skill.tree.ProcKind;
import mn.suld.api.skill.tree.TriggerEvent;
import mn.suld.api.skill.tree.Ultimate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The numbers and words of the training grounds (docs/TRAINING_GROUNDS.md). */
class TrainingStatsTest {

    static final TrainingStats.Source BASIC = TrainingStats.Source.of(TrainingStats.Kind.BASIC);
    static final TrainingStats.Source CLEAVE = new TrainingStats.Source(TrainingStats.Kind.SPELL, "Тэнгэрийн Цавчилт");
    static final TrainingStats.Source RAGE = new TrainingStats.Source(TrainingStats.Kind.PASSIVE, "Уур Хилэн");

    @Test
    void dpsCountsFromTheFirstHitThenOverTenSeconds() {
        TrainingStats s = new TrainingStats();
        assertEquals(0, s.dps(0));
        s.hit(0, BASIC, 100, false);
        assertEquals(100, s.dps(0), 1e-9, "at least one second");
        s.hit(2000, BASIC, 100, false);
        assertEquals(100, s.dps(2000), 1e-9, "200 over two seconds");
        for (int t = 3000; t <= 12_000; t += 1000) s.hit(t, BASIC, 100, false);
        assertEquals(100, s.dps(12_000), 1e-9, "the last ten seconds: hits at 3..12 s");
        assertEquals(0, s.dps(22_000), 1e-9, "nothing in the last ten seconds");
    }

    @Test
    void aPauseStartsANewFight() {
        TrainingStats s = new TrainingStats();
        s.hit(0, BASIC, 500, false);
        s.hit(1000, BASIC, 500, false);
        s.hit(1000 + TrainingStats.PAUSE_MS, BASIC, 50, false);
        assertEquals(50, s.dps(1000 + TrainingStats.PAUSE_MS), 1e-9, "the old hits do not linger in a fresh fight");
        assertEquals(1050, s.total(), 1e-9, "the total keeps everything");
    }

    @Test
    void totalsSharesMaxHitAndCrits() {
        TrainingStats s = new TrainingStats();
        s.hit(0, BASIC, 100, true);
        s.hit(100, BASIC, 100, false);
        s.hit(200, CLEAVE, 600, false);
        s.hit(300, RAGE, 200, false);
        s.hit(400, BASIC, -5, true); // ignored
        s.hit(400, BASIC, Double.NaN, true); // ignored
        assertEquals(1000, s.total(), 1e-9);
        assertEquals(4, s.hits());
        assertEquals(600, s.maxHit(), 1e-9);
        assertEquals(CLEAVE, s.maxSource());
        assertEquals(50, s.critRate(), 1e-9, "one of the two weapon hits; spells never crit");
        List<TrainingStats.Share> sh = s.shares();
        assertEquals(CLEAVE, sh.get(0).source());
        assertEquals(60, sh.get(0).percent(), 1e-9);
        assertEquals(BASIC, sh.get(1).source());
        assertEquals(2, sh.get(1).hits());
        assertEquals("Тэнгэрийн Цавчилт 60%", TrainingText.share(sh.get(0)));
        s.reset();
        assertEquals(0, s.total());
        assertNull(s.maxSource());
        assertTrue(s.shares().isEmpty());
    }

    @Test
    void passiveRatesAgainstTheirDesign() {
        TrainingStats s = new TrainingStats();
        for (int i = 0; i < 40; i++) s.passiveRolled("Уур Хилэн", 25, i % 4 == 0);
        TrainingStats.PassiveRate r = s.passives().get(0);
        assertEquals(40, r.rolls());
        assertEquals(10, r.fired());
        assertEquals(25, r.observed(), 1e-9);
        assertEquals("Уур Хилэн 10× 25% / 25%", TrainingText.rate(r));
    }

    @Test
    void numbersReadTheMongolianWay() {
        assertEquals("48 210", TrainingText.number(48_210));
        assertEquals("1 842", TrainingText.number(1842.4));
        assertEquals("1 000 000", TrainingText.number(999_999.6));
        assertEquals("640", TrainingText.number(640));
        assertEquals("4.5", TrainingText.number(4.5));
        assertEquals("1.4", TrainingText.seconds(1.35));
        assertEquals("18", TrainingText.seconds(18.0));
        assertEquals("2.08", TrainingText.factor(2.083));
        assertEquals("1.6", TrainingText.factor(1.60));
        assertEquals("3", TrainingText.factor(3.0));
    }

    @Test
    void explanationsComeFromTheData() {
        String cast = TrainingText.spell(Spell.TENGER_TSAVCHILT, 3, 2.08, 25, "Уур", 1.35, 3, 1930);
        assertEquals("Тэнгэрийн Цавчилт III (R-L-R): Урд талын бүх дайснаа цавчина. ATK ×2.08 · 25 Уур · 1.4 сек — 3 бай, 1 930 хохирол", cast);
        assertTrue(TrainingText.spell(Spell.TENGER_TSAVCHILT, 1, 1.6, 25, "Уур", 1.5, 0, 0).endsWith("бай оносонгүй"));
        String buff = TrainingText.spell(Spell.KHAAN_KHAMGAALALT, 3, 0, 50, "Уур", 9, 0, 0);
        assertFalse(buff.contains("ATK"), buff);
        assertFalse(buff.contains("бай"), "a spell without damage says nothing about targets: " + buff);

        Effect.Proc rage = new Effect.Proc(TriggerEvent.HIT, 25, ProcKind.RESOURCE, 8, 0, 2);
        String fired = TrainingText.passive("Уур Хилэн", rage, new TrainingStats.PassiveRate("Уур Хилэн", 37, 9, 25));
        assertTrue(fired.startsWith("Уур Хилэн: "), fired);
        assertTrue(fired.contains("25% магадлалтайгаар 8 нөөц сэргээнэ"), fired);
        assertTrue(fired.endsWith("Хүлээлт 2 сек. Энд 9/37 (24%)"), fired);
        assertEquals("Баатрын Их Уур (F): " + Ultimate.CHINGISIIN_UUR.description() + " Хүлээлт 45 сек.", TrainingText.ultimate(Ultimate.CHINGISIIN_UUR, 45));
        for (String t : List.of(cast, buff, fired)) assertFalse(t.matches(".*[A-Z]{3,}_.*"), "no enum names: " + t);
    }
}
