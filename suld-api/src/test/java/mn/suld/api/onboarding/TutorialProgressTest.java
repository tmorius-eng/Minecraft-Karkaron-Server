package mn.suld.api.onboarding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TutorialProgressTest {

    @Test
    void walksEveryStepThenCompletesAndPaysEachOnce() {
        TutorialProgress t = TutorialProgress.FRESH;
        assertEquals(TutorialProgress.State.NOT_STARTED, t.state());
        assertNull(t.current());
        t = t.start();
        int paid = 0;
        while (t.state() == TutorialProgress.State.IN_PROGRESS) {
            assertTrue(t.unpaid(t.step()), "first walk: every step pays");
            t = t.advance();
            paid++;
        }
        assertEquals(TutorialProgress.STEPS.size(), paid);
        assertEquals(TutorialProgress.State.COMPLETED, t.state());
        assertFalse(t.finishUnpaid());
        // a replay walks the steps again, but nothing is unpaid any more
        TutorialProgress r = t.start();
        assertEquals(0, r.step());
        while (r.state() == TutorialProgress.State.IN_PROGRESS) {
            assertFalse(r.unpaid(r.step()), "replay never pays");
            r = r.advance();
        }
        assertFalse(r.finishUnpaid());
    }

    @Test
    void survivesEncoding() {
        TutorialProgress t = TutorialProgress.FRESH.start().advance().advance();
        assertEquals(t, TutorialProgress.decode(t.encode()));
        assertEquals(TutorialProgress.FRESH, TutorialProgress.decode(null));
        assertEquals(TutorialProgress.FRESH, TutorialProgress.decode("garbage"));
        assertEquals(TutorialProgress.Step.OPEN_SKILLS, t.current());
    }
}
