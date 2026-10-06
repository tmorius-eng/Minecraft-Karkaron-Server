package mn.suld.api.worldbuild;

/** Clockwise quarter turns seen from above (north → east → south → west). */
public enum Rotation {
    NONE(0), CW_90(1), CW_180(2), CW_270(3);

    public final int quarters;

    Rotation(int quarters) {
        this.quarters = quarters;
    }

    public static Rotation ofDegrees(int degrees) {
        int q = Math.floorMod(Math.round(degrees / 90f), 4);
        return values()[q];
    }
}
