package mn.suld.api.worldevent;

import mn.suld.api.worldevent.Shagai.Face;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShagaiTest {

    @Test
    void sideChancesSumToOneAndHorsesAreRare() {
        double sum = 0;
        for (Face f : Face.values()) sum += f.chance();
        assertEquals(1.0, sum, 1e-9);
        Random r = new Random(5);
        int horses = 0, n = 100_000;
        for (int i = 0; i < n; i++) if (Shagai.face(r) == Face.HORSE) horses++;
        assertEquals(0.10, horses / (double) n, 0.01);
    }

    @Test
    void readingsFollowTheRules() {
        assertEquals(0.10, Shagai.read(new Face[]{Face.HORSE, Face.HORSE, Face.HORSE, Face.HORSE}).bonus());
        assertEquals(0.08, Shagai.read(new Face[]{Face.HORSE, Face.HORSE, Face.SHEEP, Face.HORSE}).bonus());
        assertEquals(0.05, Shagai.read(new Face[]{Face.GOAT, Face.GOAT, Face.GOAT, Face.GOAT}).bonus());
        assertEquals(0.05, Shagai.read(new Face[]{Face.HORSE, Face.CAMEL, Face.SHEEP, Face.GOAT}).bonus());
        assertEquals(0.03, Shagai.read(new Face[]{Face.HORSE, Face.HORSE, Face.SHEEP, Face.GOAT}).bonus());
        assertEquals(0.02, Shagai.read(new Face[]{Face.HORSE, Face.SHEEP, Face.SHEEP, Face.GOAT}).bonus());
        Shagai.Fortune none = Shagai.read(new Face[]{Face.SHEEP, Face.SHEEP, Face.GOAT, Face.CAMEL});
        assertEquals(0, none.bonus());
        assertEquals(0, none.minutes());
    }

    @Test
    void theAverageBlessingStaysSmall() {
        Random r = new Random(11);
        double total = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) total += Shagai.read(Shagai.cast(r)).bonus();
        double mean = total / n;
        assertTrue(mean > 0.005 && mean < 0.03, "mean bonus " + mean); // a daily nudge, not a power source
    }
}
