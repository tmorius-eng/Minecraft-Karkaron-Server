package mn.suld.api.balance;

import mn.suld.api.profile.Endgame;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.LevelCurve;
import mn.suld.api.progression.PolynomialLevelCurve;
import mn.suld.api.progression.Progression;
import mn.suld.api.quest.QuestState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProfileUpgradeTest {

    static final LevelCurve OLD = new PolynomialLevelCurve(100, 1.75, 60);
    static final LevelCurve NEW = Balance.curve();

    private static PlayerProfile stored(Progression p, Endgame eg, Instant lastSeen) {
        return PlayerProfile.restore(UUID.randomUUID(), "Anu", null, p, lastSeen, lastSeen, 3, 0, QuestState.NONE,
                null, null, null, null, eg);
    }

    @Test
    void aLegacyRowKeepsItsLevelAndItsBarFractionOnce() {
        Instant now = Instant.parse("2026-10-11T00:00:00Z");
        PlayerProfile p = stored(new Progression(30, OLD.expForLevel(30) / 4), null, now);
        ProfileUpgrade.onLoad(p, OLD, NEW, now);
        assertEquals(30, p.progression().level());
        assertEquals(NEW.expForLevel(30) / 4.0, p.progression().expIntoLevel(), NEW.expForLevel(30) * 0.001);
        assertEquals(Endgame.CURVE_CURRENT, p.endgame().curveVersion());
        long after = p.progression().expIntoLevel();
        ProfileUpgrade.onLoad(p, OLD, NEW, now); // a second load does not rescale again
        assertEquals(after, p.progression().expIntoLevel());
    }

    /** Act II: a bar on the 190 curve (version 2) is rescaled once to the current curve, level kept. */
    @Test
    void anActOneBarIsRescaledToTheActTwoCurveOnce() {
        Instant now = Instant.parse("2026-10-11T00:00:00Z");
        LevelCurve v2 = Balance.curveV2();
        PlayerProfile p = stored(new Progression(45, v2.expForLevel(45) / 2), new Endgame(0, 0, 0, Endgame.CURVE_V2), now);
        ProfileUpgrade.onLoad(p, OLD, NEW, now);
        assertEquals(45, p.progression().level());
        assertEquals(NEW.expForLevel(45) / 2.0, p.progression().expIntoLevel(), NEW.expForLevel(45) * 0.001);
        assertEquals(Endgame.CURVE_V3, p.endgame().curveVersion());
        assertTrue(NEW.expForLevel(45) > v2.expForLevel(45), "the Act II curve is longer");
        long after = p.progression().expIntoLevel();
        ProfileUpgrade.onLoad(p, OLD, NEW, now);
        assertEquals(after, p.progression().expIntoLevel(), "only once");
    }

    @Test
    void offlineHoursFillTheRestedPool() {
        Instant seen = Instant.parse("2026-10-10T00:00:00Z");
        PlayerProfile p = stored(new Progression(10, 0), Endgame.FRESH, seen);
        ProfileUpgrade.onLoad(p, OLD, NEW, seen.plus(Duration.ofHours(8)));
        assertEquals((long) Math.floor(0.015 * NEW.expForLevel(10) * 8), p.endgame().restedExp());
        assertTrue(p.isDirty());
    }

    @Test
    void endgameRoundTrips() {
        Endgame e = new Endgame(2, 12345, 678, 2);
        assertEquals(e, Endgame.fromJson(e.toJson()));
        assertEquals(Endgame.LEGACY, Endgame.fromJson(null));
        assertThrows(IllegalArgumentException.class, () -> Endgame.fromJson("{\"v\":9}"));
    }
}
