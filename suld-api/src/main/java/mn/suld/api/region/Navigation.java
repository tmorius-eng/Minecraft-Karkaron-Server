package mn.suld.api.region;

/**
 * Pure helpers for the quest tracker: a waypoint inside a region (offsets from the world spawn), compass bearings
 * (0 = north/−Z, 90 = east/+X) and the arrow that points from where a player faces to a target.
 */
public final class Navigation {

    /** Arrows by relative direction, clockwise from straight ahead. */
    private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    /** Distance from the plaza at which a sector region's waypoint sits (just outside the city walls). */
    public static final double SECTOR_WAYPOINT = 220;

    private Navigation() {
    }

    /** A representative point of a region, as {dx, dz} from the world spawn. */
    public static double[] waypoint(RegionShape shape) {
        return switch (shape) {
            case RegionShape.Circle c -> new double[]{c.cx(), c.cz()};
            case RegionShape.Square s -> new double[]{0, 0};
            case RegionShape.Sector s -> {
                double from = (s.fromDeg() % 360 + 360) % 360;
                double to = (s.toDeg() % 360 + 360) % 360;
                double span = from <= to ? to - from : to + 360 - from;
                double mid = Math.toRadians(from + span / 2);
                double r = Math.max(s.minRadius(), Math.min(s.maxRadius(), SECTOR_WAYPOINT));
                yield new double[]{Math.sin(mid) * r, -Math.cos(mid) * r};
            }
        };
    }

    /** Compass bearing of the vector (dx, dz), 0..360. */
    public static double bearing(double dx, double dz) {
        double b = Math.toDegrees(Math.atan2(dx, -dz));
        return (b % 360 + 360) % 360;
    }

    /** Compass bearing a player faces, from a Minecraft yaw (0 = south, 90 = west). */
    public static double facing(float yaw) {
        return ((yaw + 180) % 360 + 360) % 360;
    }

    /** The arrow pointing to {@code targetBearing} for a player facing {@code facingBearing}. */
    public static String arrow(double targetBearing, double facingBearing) {
        double rel = ((targetBearing - facingBearing) % 360 + 360) % 360;
        return ARROWS[(int) Math.round(rel / 45.0) % 8];
    }

    /** Mongolian compass word for a bearing (хойд/зүүн/өмнөд/баруун and the diagonals). */
    public static String compass(double bearing) {
        String[] names = {"хойд", "зүүн хойд", "зүүн", "зүүн өмнөд", "өмнөд", "баруун өмнөд", "баруун", "баруун хойд"};
        return names[(int) Math.round(((bearing % 360) + 360) % 360 / 45.0) % 8];
    }
}
