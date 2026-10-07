package mn.suld.api.model;

/** A unit quaternion (x, y, z, w). Euler angles are degrees composed as q = qz · qy · qx (docs/MODEL_RENDERER.md §2). */
public record Quat(double x, double y, double z, double w) {

    public static final Quat IDENTITY = new Quat(0, 0, 0, 1);

    public static Quat axisAngle(double ax, double ay, double az, double degrees) {
        double h = Math.toRadians(degrees) / 2, s = Math.sin(h);
        return new Quat(ax * s, ay * s, az * s, Math.cos(h));
    }

    /** X turns first, then Y, then Z (all about the parent axes). */
    public static Quat euler(double xDeg, double yDeg, double zDeg) {
        return axisAngle(0, 0, 1, zDeg).times(axisAngle(0, 1, 0, yDeg)).times(axisAngle(1, 0, 0, xDeg));
    }

    public static Quat euler(Vec3 deg) {
        return euler(deg.x(), deg.y(), deg.z());
    }

    /** Hamilton product: applying the result = applying {@code o} first, then this. */
    public Quat times(Quat o) {
        return new Quat(
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w,
                w * o.w - x * o.x - y * o.y - z * o.z);
    }

    public Vec3 rotate(Vec3 v) {
        // v' = q v q*, expanded
        double tx = 2 * (y * v.z() - z * v.y()), ty = 2 * (z * v.x() - x * v.z()), tz = 2 * (x * v.y() - y * v.x());
        return new Vec3(v.x() + w * tx + (y * tz - z * ty), v.y() + w * ty + (z * tx - x * tz), v.z() + w * tz + (x * ty - y * tx));
    }

    public Quat normalized() {
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        return n == 0 ? IDENTITY : new Quat(x / n, y / n, z / n, w / n);
    }

    /** The smallest angle between two orientations, degrees (change detection). */
    public double angleTo(Quat o) {
        double d = Math.abs(x * o.x + y * o.y + z * o.z + w * o.w);
        return Math.toDegrees(2 * Math.acos(Math.min(1, d)));
    }
}
