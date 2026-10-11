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
     * A bar still on the old curve is rescaled once (level kept, same fraction; {@link CurveMigration}); then the
     * offline time since the last session fills the rested pool ({@link RestedPool}).
     */
    public static void onLoad(PlayerProfile p, LevelCurve legacy, LevelCurve current, Instant now) {
        Endgame eg = p.endgame();
        if (eg.curveVersion() < Endgame.CURVE_V2) {
            p.progression(CurveMigration.rescale(p.progression(), legacy, current));
            eg = eg.withCurve(Endgame.CURVE_V2);
        }
        long offline = Math.max(0, Duration.between(p.lastSeenAt(), now).toMillis());
        eg = eg.withRested(RestedPool.accrue(current, eg.restedExp(), p.progression().level(), offline));
        p.endgame(eg);
    }
}
