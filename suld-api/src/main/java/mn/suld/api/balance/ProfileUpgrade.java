package mn.suld.api.balance;

import mn.suld.api.profile.Endgame;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.CurveMigration;
import mn.suld.api.progression.LevelCurve;

import java.time.Duration;
import java.time.Instant;

/** What happens to a profile read from storage, before the player is let in (progression v2). */
public final class ProfileUpgrade {

    private ProfileUpgrade() {
    }

    /**
     * A bar on an older curve is rescaled once to the current one (level kept, same fraction; {@link CurveMigration}):
     * version 1 from {@code legacy} (config progression.legacy-curve), version 2 from the Act I live curve
     * ({@link Balance#curveV2()}). Then the offline time since the last session fills the rested pool
     * ({@link RestedPool}).
     */
    public static void onLoad(PlayerProfile p, LevelCurve legacy, LevelCurve current, Instant now) {
        onLoad(p, legacy, Balance.curveV2(), current, now);
    }

    public static void onLoad(PlayerProfile p, LevelCurve legacy, LevelCurve v2, LevelCurve current, Instant now) {
        Endgame eg = p.endgame();
        if (eg.curveVersion() < Endgame.CURVE_CURRENT) {
            LevelCurve from = eg.curveVersion() < Endgame.CURVE_V2 ? legacy : v2;
            p.progression(CurveMigration.rescale(p.progression(), from, current));
            eg = eg.withCurve(Endgame.CURVE_CURRENT);
        }
        long offline = Math.max(0, Duration.between(p.lastSeenAt(), now).toMillis());
        eg = eg.withRested(RestedPool.accrue(current, eg.restedExp(), p.progression().level(), offline));
        p.endgame(eg);
    }
}
