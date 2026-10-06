package mn.suld.api.config;

import mn.suld.api.progression.LevelCurve;
import mn.suld.api.progression.PolynomialLevelCurve;

/**
 * Data-driven levelling parameters. See {@link PolynomialLevelCurve} for the
 * meaning of {@code base} and {@code exponent}.
 */
public record ProgressionSettings(int maxLevel, double base, double exponent) {

    public ProgressionSettings {
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be >= 1: " + maxLevel);
        }
    }

    public static ProgressionSettings defaults() {
        return new ProgressionSettings(60, 100.0, 1.75);
    }

    public LevelCurve toCurve() {
        return new PolynomialLevelCurve(base, exponent, maxLevel);
    }
}
