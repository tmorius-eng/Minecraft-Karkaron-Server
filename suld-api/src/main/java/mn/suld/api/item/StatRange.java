package mn.suld.api.item;

/** An inclusive numeric range a stat rolls in (at item level 1, before the rarity multiplier). */
public record StatRange(double min, double max) {
    public StatRange {
        if (!Double.isFinite(min) || !Double.isFinite(max) || max < min) {
            throw new IllegalArgumentException("bad range " + min + ".." + max);
        }
    }

    public static StatRange of(double v) {
        return new StatRange(v, v);
    }

    /** min + q·(max − min), q in [0, 1]. */
    public double at(double q) {
        return min + Math.max(0, Math.min(1, q)) * (max - min);
    }

    public boolean fixed() {
        return min == max;
    }
}
