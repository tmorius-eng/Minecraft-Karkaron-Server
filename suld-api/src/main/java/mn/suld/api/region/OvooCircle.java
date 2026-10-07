package mn.suld.api.region;

/**
 * Walking around an ovoo (docs/world/OVOO.md): the custom is to circle it three times clockwise ("нар зөв", the way
 * the sun goes), which on a map seen from above means the compass bearing from the cairn to the walker keeps
 * growing. This tracker adds up the signed turn of successive positions; three full turns (1080°) clockwise, staying
 * within the ring, completes it. Walking the other way unwinds the count, and leaving the ring starts over. Pure.
 */
public final class OvooCircle {

    public static final double TURNS = 3, MIN_R = 2.0, MAX_R = 9.0;

    private double total;
    private double lastBearing = Double.NaN;

    /**
     * One position of the walker relative to the cairn (dx east, dz south). Returns true exactly once, when the
     * third clockwise turn closes.
     */
    public boolean step(double dx, double dz) {
        double r = Math.hypot(dx, dz);
        if (r < MIN_R || r > MAX_R) {
            reset();
            return false;
        }
        double b = Navigation.bearing(dx, dz);
        if (!Double.isNaN(lastBearing)) {
            double d = b - lastBearing;
            if (d > 180) d -= 360;
            if (d < -180) d += 360;
            if (Math.abs(d) < 120) total += d; // a jump (teleport, lag) does not count
        }
        lastBearing = b;
        if (total >= 360 * TURNS) {
            reset();
            return true;
        }
        if (total < -360) total = -360; // walking the wrong way does not dig an endless hole
        return false;
    }

    public void reset() {
        total = 0;
        lastBearing = Double.NaN;
    }

    /** Clockwise turns so far (negative while walking the wrong way). */
    public double turns() {
        return total / 360;
    }
}
