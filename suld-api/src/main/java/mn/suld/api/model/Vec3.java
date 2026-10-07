package mn.suld.api.model;

/** An immutable 3-vector in blocks (model space: +Y up, +Z forward). */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 ONE = new Vec3(1, 1, 1);

    public Vec3 plus(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 minus(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 times(double s) {
        return new Vec3(x * s, y * s, z * s);
    }

    /** Linear interpolation towards {@code o}. */
    public Vec3 lerp(Vec3 o, double t) {
        return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    public double distance(Vec3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
