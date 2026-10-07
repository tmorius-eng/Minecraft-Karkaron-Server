package mn.suld.api.worldbuild;

import java.util.Locale;

/**
 * The playable square of the overworld (docs/world/WORLD_BORDER.md): a native world border of {@code diameter} blocks
 * centred on ({@code centerX}, {@code centerZ}). The centre is the world spawn (Kharkhorum's plaza) unless the config
 * names fixed coordinates, so every ring of regions and areas (all measured from the spawn) fits inside it.
 */
public record BorderSpec(double centerX, double centerZ, double diameter) {

    /** 10 000 x 10 000 blocks: radius 5 000 around the centre. */
    public static final double DEFAULT_DIAMETER = 10_000;

    public BorderSpec {
        if (!(diameter >= 16) || diameter > 59_999_968) throw new IllegalArgumentException("diameter out of range: " + diameter);
    }

    public double radius() {
        return diameter / 2;
    }

    /** True when the block column (x, z) lies inside the border. */
    public boolean contains(double x, double z) {
        double r = radius();
        return Math.abs(x - centerX) <= r && Math.abs(z - centerZ) <= r;
    }

    /** The nearest point at least {@code margin} blocks inside the border (the point itself when already there). */
    public double[] clampInside(double x, double z, double margin) {
        double r = Math.max(0, radius() - margin);
        return new double[]{clamp(x, centerX - r, centerX + r), clamp(z, centerZ - r, centerZ + r)};
    }

    /** True when the live border differs from this one by more than half a block (centre or size). */
    public boolean differsFrom(double liveX, double liveZ, double liveDiameter) {
        return Math.abs(liveX - centerX) > 0.5 || Math.abs(liveZ - centerZ) > 0.5 || Math.abs(liveDiameter - diameter) > 0.5;
    }

    /**
     * The configured centre: {@code "spawn"} (or blank) uses the spawn coordinates, otherwise two numbers {@code "x z"}
     * (comma or space separated).
     */
    public static double[] center(String configured, double spawnX, double spawnZ) {
        if (configured == null || configured.isBlank() || configured.strip().toLowerCase(Locale.ROOT).equals("spawn")) {
            return new double[]{spawnX, spawnZ};
        }
        String[] parts = configured.strip().split("[\\s,]+");
        if (parts.length != 2) throw new IllegalArgumentException("world.border.center must be 'spawn' or 'x z', got: " + configured);
        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
