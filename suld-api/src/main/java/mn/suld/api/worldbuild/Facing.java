package mn.suld.api.worldbuild;

/** Cardinal directions on Minecraft axes (north = -Z). */
public enum Facing {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    public final int dx;
    public final int dz;

    Facing(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Cardinal direction pointing from (fromX, fromZ) toward (toX, toZ). */
    public static Facing toward(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX, dz = toZ - fromZ;
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? EAST : WEST;
        return dz >= 0 ? SOUTH : NORTH;
    }
}
