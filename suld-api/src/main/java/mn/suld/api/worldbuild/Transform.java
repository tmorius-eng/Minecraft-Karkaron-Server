package mn.suld.api.worldbuild;

/**
 * Module transform: optional mirror across the module's local X axis (x → −x), then a clockwise
 * rotation about the local origin. Positions and block states (via {@link BlockStates}) are both
 * transformed, so a door, stair or banner still faces the right way after rotating or mirroring.
 */
public record Transform(Rotation rotation, boolean mirrorX) {

    public static final Transform IDENTITY = new Transform(Rotation.NONE, false);

    public static Transform of(int degrees, boolean mirror) {
        return new Transform(Rotation.ofDegrees(degrees), mirror);
    }

    /** Local (x, z) → transformed (x, z). */
    public int[] apply(int x, int z) {
        if (mirrorX) x = -x;
        return switch (rotation) {
            case NONE -> new int[]{x, z};
            case CW_90 -> new int[]{-z, x};
            case CW_180 -> new int[]{-x, -z};
            case CW_270 -> new int[]{z, -x};
        };
    }

    public Facing apply(Facing f) {
        Facing m = f;
        if (mirrorX && (f == Facing.EAST || f == Facing.WEST)) m = f == Facing.EAST ? Facing.WEST : Facing.EAST;
        return Facing.values()[(m.ordinal() + rotation.quarters) % 4];
    }

    /** Banner/sign 16-step rotation (0 = south, clockwise). */
    public int applyRotation16(int r) {
        int v = r;
        if (mirrorX) v = Math.floorMod(16 - v, 16);
        return Math.floorMod(v + 4 * rotation.quarters, 16);
    }

    public boolean swapsAxes() {
        return rotation.quarters % 2 == 1;
    }
}
