package mn.suld.api.combat;

/**
 * Deterministic combat math, isolated from Bukkit so it is fully unit-tested.
 *
 * <p>Armor mitigation uses the standard diminishing-returns curve
 * {@code mitigation = armor / (armor + K)}, where {@code K} is the armor
 * constant (scales the value of a point of armor). Crit is decided by an
 * injected roll in {@code [0,1)} so tests are deterministic — no hidden RNG.
 */
public final class CombatCalculator {

    private final double armorConstant;

    public CombatCalculator(double armorConstant) {
        if (armorConstant <= 0) {
            throw new IllegalArgumentException("armorConstant must be > 0: " + armorConstant);
        }
        this.armorConstant = armorConstant;
    }

    /** Mitigation fraction in {@code [0,1)} for a given armor value. */
    public double mitigation(double armor) {
        double a = Math.max(0.0, armor);
        return a / (a + armorConstant);
    }

    /**
     * Compute a hit.
     *
     * @param attackPower   base damage
     * @param critChance    probability of a crit, clamped to {@code [0,1]}
     * @param critMultiplier damage multiplier on crit ({@code >= 1})
     * @param targetArmor   defender armor
     * @param roll          injected uniform roll in {@code [0,1)}; crit when {@code roll < critChance}
     */
    public DamageResult compute(double attackPower, double critChance, double critMultiplier,
                                double targetArmor, double roll) {
        double raw = Math.max(0.0, attackPower);
        double cc = Math.max(0.0, Math.min(1.0, critChance));
        boolean crit = roll < cc;
        double afterCrit = crit ? raw * Math.max(1.0, critMultiplier) : raw;
        double mit = mitigation(targetArmor);
        double finalDamage = afterCrit * (1.0 - mit);
        finalDamage = Math.max(0.0, Math.round(finalDamage * 100.0) / 100.0);
        return new DamageResult(raw, finalDamage, crit, afterCrit - finalDamage);
    }
}
