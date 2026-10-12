package mn.suld.api.balance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AscensionStepsTest {

    private static final int LADDER = 10, STORY = 42;

    private static Ascension.Inputs in(int clears, int palace, double gear, int chapters, int mastery) {
        return new Ascension.Inputs(60, clears, LADDER, palace, gear, chapters, STORY, mastery);
    }

    @Test
    void everyGateStepCounts() {
        for (int rank = 0; rank < Ascension.MAX_RANK; rank++) {
            int last = -1;
            // one more of each thing at a time: the steps never fall and rise until the gates open
            for (int k = 0; k <= 45; k++) {
                int s = Ascension.steps(rank, in(Math.min(k, LADDER), Math.min(k, 6), k >= 40 ? 2 * GearPower.par(60) : 0, Math.min(k, STORY), Math.min(k, 8)));
                assertTrue(s >= last, "rank " + rank + " step " + k);
                if (k <= 4) assertTrue(s > last, "each early step shows, rank " + rank + " step " + k);
                last = s;
            }
        }
    }

    @Test
    void stepsEqualTheTotalWhenTheRankIsOpen() {
        Ascension.Inputs done = in(LADDER, 6, 2 * GearPower.par(60), STORY, 8);
        for (int rank = 0; rank < Ascension.MAX_RANK; rank++) {
            assertNull(Ascension.blocked(rank, done));
            assertEquals(Ascension.totalSteps(rank, LADDER, STORY), Ascension.steps(rank, done));
        }
        assertEquals(0, Ascension.steps(Ascension.MAX_RANK, done));
    }

    @Test
    void starsCountTheOnooAtTheLastRankOnly() {
        var c = Balance.curve();
        long star = Ascension.starCost(c);
        assertEquals(0, Ascension.stars(c, Ascension.MAX_RANK - 1, 50 * star));
        assertEquals(0, Ascension.stars(c, Ascension.MAX_RANK, star - 1));
        assertEquals(3, Ascension.stars(c, Ascension.MAX_RANK, 3 * star + 7));
    }
}
