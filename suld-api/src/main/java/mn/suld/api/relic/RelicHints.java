package mn.suld.api.relic;

/**
 * Coarse "warmer/colder" hints toward a shrine: a distance band and an 8-point direction.
 * Deliberately imprecise so discovery stays an expedition, not a teleport.
 * Minecraft axes: north = -Z, east = +X.
 */
public final class RelicHints {

    private static final String[] DIRECTIONS = {"хойд", "зүүн хойд", "зүүн", "зүүн өмнөд", "өмнөд", "баруун өмнөд", "баруун", "баруун хойд"};
    private static final int BAND = 250;

    private RelicHints() {
    }

    /** 8-point compass direction from (fromX, fromZ) toward (toX, toZ), in Mongolian. */
    public static String direction(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double north = fromZ - toZ;               // positive when the target is to the north
        double degrees = Math.toDegrees(Math.atan2(dx, north)); // 0 = north, 90 = east
        int sector = (int) Math.floorMod(Math.round(degrees / 45.0), 8);
        return DIRECTIONS[sector];
    }

    /** Distance rounded to {@value BAND}-block bands ("<250", "~500", ...). */
    public static String distanceBand(double distance) {
        if (distance < BAND) return "<" + BAND;
        return "~" + (Math.round(distance / BAND) * BAND);
    }

    public static String describe(double fromX, double fromZ, double toX, double toZ) {
        double dist = Math.hypot(toX - fromX, toZ - fromZ);
        if (dist < 24) return "та сүмийн дэргэд байна";
        return distanceBand(dist) + " блок, " + direction(fromX, fromZ, toX, toZ) + " зүгт";
    }
}
