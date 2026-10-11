package mn.suld.api.profile;

import mn.suld.api.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Character-wide progression v2 state (docs/ASCENSION_SPEC.md, docs/PROGRESSION_BALANCE_SPEC.md), stored as one
 * versioned JSON document in {@code suld_profiles.endgame}.
 *
 * @param ascension     Тэнгэрийн Зэрэг rank, 0..{@link mn.suld.api.balance.Ascension#MAX_RANK}
 * @param tengeriPoints Тэнгэрийн оноо: EXP earned at the level cap, spent on Ascension ranks
 * @param restedExp     rested-EXP pool (offline hours fill it, kills spend it)
 * @param curveVersion  the level curve the stored bar belongs to (1 = the old 100·L^1.75 curve, 2 = progression v2)
 */
public record Endgame(int ascension, long tengeriPoints, long restedExp, int curveVersion) {

    public static final int VERSION = 1;
    public static final int CURVE_V2 = 2;

    /** A row written before progression v2 (NULL column): its bar is on the old curve. */
    public static final Endgame LEGACY = new Endgame(0, 0, 0, 1);
    /** A brand-new character. */
    public static final Endgame FRESH = new Endgame(0, 0, 0, CURVE_V2);

    public Endgame {
        ascension = Math.max(0, Math.min(mn.suld.api.balance.Ascension.MAX_RANK, ascension));
        tengeriPoints = Math.max(0, tengeriPoints);
        restedExp = Math.max(0, restedExp);
    }

    public Endgame withAscension(int rank) {
        return new Endgame(rank, tengeriPoints, restedExp, curveVersion);
    }

    public Endgame withPoints(long points) {
        return new Endgame(ascension, points, restedExp, curveVersion);
    }

    public Endgame withRested(long rested) {
        return new Endgame(ascension, tengeriPoints, rested, curveVersion);
    }

    public Endgame withCurve(int version) {
        return new Endgame(ascension, tengeriPoints, restedExp, version);
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", VERSION);
        m.put("asc", ascension);
        m.put("pts", tengeriPoints);
        m.put("rest", restedExp);
        m.put("curve", curveVersion);
        return Json.write(m);
    }

    public static Endgame fromJson(String json) {
        if (json == null || json.isBlank()) return LEGACY;
        try {
            Map<String, Object> m = Json.object(Json.parse(json));
            int v = m.get("v") instanceof Number n ? n.intValue() : -1;
            if (v != VERSION) throw new IllegalArgumentException("endgame version " + v + " is not supported (expected " + VERSION + ")");
            return new Endgame((int) num(m, "asc"), num(m, "pts"), num(m, "rest"), (int) num(m, "curve"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("broken endgame data: " + e.getMessage(), e);
        }
    }

    private static long num(Map<String, Object> m, String k) {
        Object o = m.get(k);
        if (!(o instanceof Number n)) throw new IllegalArgumentException("missing " + k);
        return n.longValue();
    }
}
