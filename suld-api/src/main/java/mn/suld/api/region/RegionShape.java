package mn.suld.api.region;

/**
 * A 2D (XZ) area expressed relative to the world anchor (Kharkhorum's center), so the same
 * region layout works in any world. Minecraft axes: north = -Z, east = +X.
 */
public sealed interface RegionShape permits RegionShape.Circle, RegionShape.Square, RegionShape.Sector {

    boolean contains(double dx, double dz);

    /** Disc of {@code radius} around the anchor offset ({@code cx}, {@code cz}). */
    record Circle(double cx, double cz, double radius) implements RegionShape {
        public boolean contains(double dx, double dz) {
            double x = dx - cx, z = dz - cz;
            return x * x + z * z <= radius * radius;
        }
    }

    /** Axis-aligned square of half-size {@code half} around the anchor. */
    record Square(double half) implements RegionShape {
        public boolean contains(double dx, double dz) {
            return Math.abs(dx) <= half && Math.abs(dz) <= half;
        }
    }

    /**
     * Ring slice between {@code minRadius} and {@code maxRadius}, from compass bearing
     * {@code fromDeg} clockwise to {@code toDeg} (0 = north, 90 = east). May wrap past 360.
     */
    record Sector(double minRadius, double maxRadius, double fromDeg, double toDeg) implements RegionShape {
        public boolean contains(double dx, double dz) {
            double r = Math.hypot(dx, dz);
            if (r < minRadius || r > maxRadius) return false;
            if (toDeg - fromDeg >= 360) return true; // full ring
            double bearing = Math.toDegrees(Math.atan2(dx, -dz));
            bearing = (bearing % 360 + 360) % 360;
            double from = (fromDeg % 360 + 360) % 360;
            double to = (toDeg % 360 + 360) % 360;
            return from <= to ? bearing >= from && bearing < to : bearing >= from || bearing < to;
        }
    }
}
