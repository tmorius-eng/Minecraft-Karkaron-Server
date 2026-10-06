package mn.suld.api.progression;

/**
 * A polynomial level curve: {@code expForLevel(l) = round(base * l^exponent)}.
 *
 * <p>This shape gives a smooth, tunable ramp. {@code base} sets the early-game
 * pace and {@code exponent} controls how sharply later levels slow down
 * ({@code 1.0} is linear, values around {@code 1.5–2.5} are typical for MMORPGs).
 * All parameters are supplied from configuration.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class PolynomialLevelCurve implements LevelCurve {

    private final double base;
    private final double exponent;
    private final int maxLevel;

    public PolynomialLevelCurve(double base, double exponent, int maxLevel) {
        if (base <= 0.0 || !Double.isFinite(base)) {
            throw new IllegalArgumentException("base must be a positive finite number: " + base);
        }
        if (exponent < 0.0 || !Double.isFinite(exponent)) {
            throw new IllegalArgumentException("exponent must be a non-negative finite number: " + exponent);
        }
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be >= 1: " + maxLevel);
        }
        this.base = base;
        this.exponent = exponent;
        this.maxLevel = maxLevel;
    }

    @Override
    public int maxLevel() {
        return maxLevel;
    }

    @Override
    public long expForLevel(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1: " + level);
        }
        if (level >= maxLevel) {
            return 0L;
        }
        double raw = base * Math.pow(level, exponent);
        // At least 1 point of progress is always required to avoid free levels.
        return Math.max(1L, Math.round(raw));
    }

    @Override
    public String toString() {
        return "PolynomialLevelCurve{base=" + base + ", exponent=" + exponent + ", maxLevel=" + maxLevel + '}';
    }
}
