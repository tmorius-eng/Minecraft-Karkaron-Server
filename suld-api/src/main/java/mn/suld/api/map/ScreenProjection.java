package mn.suld.api.map;

/**
 * The player's camera as the client draws it, for the waypoint markers on the game screen (docs/WORLD_MAP.md): world
 * point → screen position, and back. Minecraft's conventions: yaw 0 looks south (+z), 90 west (−x); pitch 90 looks
 * straight down. The screen is in normalised device coordinates: x −1 (left edge) … +1 (right edge), y −1 (bottom) …
 * +1 (top), the crosshair at (0, 0).
 * <p>
 * The server does not know the client's field of view or window shape, so {@link Camera#standard} assumes the game's
 * default (a 70° vertical field of view, Minecraft's «FOV» setting) on a 16:9 window. A wider field of view or window
 * only pulls an edge marker in from the edge; {@link #EDGE_INSET} keeps it on screen for a narrower one (sprinting
 * widens the view, a drawn bow narrows it by up to 15 %).
 */
public final class ScreenProjection {

    /** Minecraft's default vertical field of view, in degrees. */
    public static final double DEFAULT_FOV = 70;
    /** The window shape assumed for the client: 16:9. */
    public static final double DEFAULT_ASPECT = 16.0 / 9.0;
    /** Nearer than this along the look (blocks), a point counts as behind the camera. */
    public static final double NEAR_PLANE = 0.05;
    /**
     * How far in from the screen's edge an edge marker sits, as a part of the half-screen: a marker 15 % in still shows
     * through a drawn bow's 15 % narrower view, and its card never runs off the edge.
     */
    public static final double EDGE_INSET = 0.15;

    private ScreenProjection() {
    }

    /**
     * The camera: the eye at (x, y, z) looking along yaw/pitch, {@code fov} the vertical field of view in degrees,
     * {@code aspect} the window's width over its height.
     */
    public record Camera(double x, double y, double z, float yaw, float pitch, double fov, double aspect) {

        public Camera {
            if (!(fov > 0 && fov < 180)) throw new IllegalArgumentException("fov " + fov);
            if (!(aspect > 0)) throw new IllegalArgumentException("aspect " + aspect);
        }

        /** The default camera: {@link #DEFAULT_FOV} on a {@link #DEFAULT_ASPECT} window. */
        public static Camera standard(double x, double y, double z, float yaw, float pitch) {
            return new Camera(x, y, z, yaw, pitch, DEFAULT_FOV, DEFAULT_ASPECT);
        }

        /** tan of half the vertical field of view. */
        double tanV() {
            return Math.tan(Math.toRadians(fov) / 2);
        }

        /** tan of half the horizontal field of view. */
        double tanH() {
            return tanV() * aspect;
        }

        /** The look, a unit vector. */
        double[] forward() {
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            return new double[]{-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)};
        }

        /** The screen's right, a unit vector (level: the camera never rolls). */
        double[] right() {
            double yr = Math.toRadians(yaw);
            return new double[]{-Math.cos(yr), 0, -Math.sin(yr)};
        }

        /** The screen's up, a unit vector: right × forward. */
        double[] up() {
            double[] r = right(), f = forward();
            return new double[]{r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        }
    }

    /**
     * Where a world point falls: {@code sx, sy} its screen position (meaningful only when not {@code behind}; may lie
     * past ±1 when off screen), {@code depth} how far it is along the look (negative behind), {@code right} and
     * {@code up} its offsets across the view in blocks (they still tell left from right behind the camera).
     */
    public record Projection(double sx, double sy, double depth, double right, double up, boolean behind) {

        /** True when the point is in front of the camera and inside the screen. */
        public boolean onScreen() {
            return !behind && Math.abs(sx) <= 1 && Math.abs(sy) <= 1;
        }

        /** True when the point is on screen at least {@code inset} (a part of the half-screen) in from every edge. */
        public boolean inside(double inset) {
            double lim = 1 - inset;
            return !behind && Math.abs(sx) <= lim && Math.abs(sy) <= lim;
        }
    }

    /** Projects the world point (tx, ty, tz) through the camera. */
    public static Projection project(Camera c, double tx, double ty, double tz) {
        double dx = tx - c.x(), dy = ty - c.y(), dz = tz - c.z();
        double[] f = c.forward(), r = c.right(), u = c.up();
        double depth = dx * f[0] + dy * f[1] + dz * f[2];
        double across = dx * r[0] + dy * r[1] + dz * r[2];
        double upward = dx * u[0] + dy * u[1] + dz * u[2];
        boolean behind = depth < NEAR_PLANE;
        double d = Math.max(depth, NEAR_PLANE);
        return new Projection(across / (d * c.tanH()), upward / (d * c.tanV()), depth, across, upward, behind);
    }

    /**
     * The point {@code dist} blocks from the eye that shows at screen position (sx, sy): where a marker is put so the
     * client draws it there. {x, y, z}.
     */
    public static double[] unproject(Camera c, double sx, double sy, double dist) {
        double[] f = c.forward(), r = c.right(), u = c.up();
        double ax = sx * c.tanH(), ay = sy * c.tanV();
        double vx = f[0] + r[0] * ax + u[0] * ay, vy = f[1] + r[1] * ax + u[1] * ay, vz = f[2] + r[2] * ax + u[2] * ay;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        return new double[]{c.x() + vx / len * dist, c.y() + vy / len * dist, c.z() + vz / len * dist};
    }

    /**
     * The part of the screen an edge marker may use, in screen coordinates: {@code left < 0 < right},
     * {@code bottom < 0 < top}.
     */
    public record Bounds(double left, double right, double bottom, double top) {

        public Bounds {
            if (!(left < 0 && right > 0 && bottom < 0 && top > 0)) throw new IllegalArgumentException("bounds around the crosshair");
        }

        /** The whole screen less {@code inset} (a part of the half-screen) at every edge. */
        public static Bounds inset(double inset) {
            return new Bounds(-1 + inset, 1 - inset, -1 + inset, 1 - inset);
        }

        public boolean contains(double sx, double sy) {
            return sx >= left && sx <= right && sy >= bottom && sy <= top;
        }
    }

    /**
     * Where an off-screen point's marker sits on the edge of {@code b}, and which way its arrow points:
     * {@code {sx, sy, angle}}, the angle in radians clockwise from straight up as seen on the real (wide) screen. In
     * front of the camera, the marker sits where the line from the crosshair to the point leaves {@code b} (a point
     * inside {@code b} stays where it is). Behind the camera, the marker goes to the side the player must turn to
     * (the left or the right edge; dead behind = right), at the height the point is above or below the look, and the
     * arrow points mostly sideways: turn that way.
     */
    public static double[] edge(Camera c, Projection p, Bounds b) {
        if (!p.behind()) {
            double sx = p.sx(), sy = p.sy();
            double t = 1;
            if (sx > b.right()) t = Math.min(t, b.right() / sx);
            if (sx < b.left()) t = Math.min(t, b.left() / sx);
            if (sy > b.top()) t = Math.min(t, b.top() / sy);
            if (sy < b.bottom()) t = Math.min(t, b.bottom() / sy);
            // exactly on the bound that stopped it (sy × top / sy can land a hair past it)
            double ex = Math.max(b.left(), Math.min(b.right(), sx * t)), ey = Math.max(b.bottom(), Math.min(b.top(), sy * t));
            return new double[]{ex, ey, Math.atan2(ex * c.aspect(), ey)};
        }
        boolean left = p.right() < 0;
        double ex = left ? b.left() : b.right();
        // the height as the look would see it turned that way: blocks up over blocks out
        double out = Math.max(Math.abs(p.right()), Math.abs(p.depth()));
        double h = out < 1e-9 ? 0 : p.up() / (out * c.tanV());
        double ey = Math.max(b.bottom(), Math.min(b.top(), h));
        double tilt = ey >= 0 ? ey / b.top() : -ey / b.bottom();
        return new double[]{ex, ey, Math.atan2(left ? -1 : 1, tilt * 0.5)};
    }

    /** The straight-line distance between two points; a {@code ty} of NaN (height unknown) measures across the ground only. */
    public static double distance(double fx, double fy, double fz, double tx, double ty, double tz) {
        double dx = tx - fx, dz = tz - fz;
        if (Double.isNaN(ty)) return Math.hypot(dx, dz);
        double dy = ty - fy;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
